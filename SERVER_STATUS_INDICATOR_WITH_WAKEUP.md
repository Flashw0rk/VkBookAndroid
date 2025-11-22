# 🔴 Индикатор состояния сервера с учетом пробуждения (Render.com)

**Дата:** 21.11.2025  
**Особенность:** Сервер на Render.com постоянно спит и пробуждается в течение некоторого времени после запроса

---

## 🎯 Проблема

**Особенности сервера Render.com:**
- Сервер "засыпает" при отсутствии активности (cold start)
- Первый запрос после пробуждения может занять **до 3 минут**
- Нужно различать состояния:
  - 🌙 **Спящий** - сервер не отвечает, но это нормально
  - ⏳ **Пробуждается** - сервер обрабатывает первый запрос (до 3 минут)
  - ✅ **Доступен** - сервер активен и отвечает быстро
  - ❌ **Недоступен** - реальная проблема с сервером

**Текущая реализация:**
- Уже есть `waitForServerWakeup()` с 6 попытками по 5 секунд
- Есть `isRenderSleepException()` для определения спящего сервера
- Нужно интегрировать это в индикатор состояния

---

## ✅ Решение: Улучшенный NetworkStatusManager

### 1. Расширенная модель состояний сервера

```kotlin
// app/src/main/java/com/example/vkbookandroid/network/NetworkStatusManager.kt
package com.example.vkbookandroid.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Менеджер состояния сети и сервера
 * Учитывает особенности серверов с cold start (Render.com)
 */
class NetworkStatusManager(private val context: Context) {
    
    private val tag = "NetworkStatusManager"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    // Состояние сети
    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.UNKNOWN)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()
    
    // Состояние сервера (расширенное)
    private val _serverStatus = MutableStateFlow<ServerStatus>(ServerStatus.UNKNOWN)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()
    
    // Дополнительная информация о состоянии сервера
    private val _serverStatusDetails = MutableStateFlow<ServerStatusDetails?>(null)
    val serverStatusDetails: StateFlow<ServerStatusDetails?> = _serverStatusDetails.asStateFlow()
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var serverCheckJob: Job? = null
    private val serverCheckScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Настройки проверки сервера
    private val serverCheckInterval = 30_000L // 30 секунд
    private val wakeupCheckTimeout = 180_000L // 3 минуты для пробуждения
    private val wakeupCheckInterval = 5_000L // 5 секунд между попытками
    
    /**
     * Статус сети
     */
    enum class NetworkStatus {
        ONLINE,      // Есть интернет
        OFFLINE,     // Нет интернета
        UNKNOWN      // Неизвестно (инициализация)
    }
    
    /**
     * Статус сервера (расширенный)
     */
    enum class ServerStatus {
        AVAILABLE,       // Сервер доступен и отвечает быстро
        WAKING_UP,       // Сервер пробуждается (cold start)
        SLEEPING,        // Сервер спит (нормальное состояние для Render)
        UNAVAILABLE,     // Сервер недоступен (реальная проблема)
        CHECKING,        // Проверка в процессе
        UNKNOWN          // Неизвестно
    }
    
    /**
     * Детали состояния сервера
     */
    data class ServerStatusDetails(
        val lastCheckTime: Long = System.currentTimeMillis(),
        val wakeupAttempt: Int = 0,
        val maxWakeupAttempts: Int = 36, // 36 попыток * 5 сек = 3 минуты
        val estimatedWakeupTime: Long? = null, // Оценка времени пробуждения
        val lastResponseTime: Long? = null, // Время последнего успешного ответа
        val isRenderServer: Boolean = false
    )
    
    /**
     * Инициализация отслеживания сети
     */
    fun startMonitoring() {
        if (networkCallback != null) {
            Log.w(tag, "Network monitoring already started")
            return
        }
        
        // Проверяем текущее состояние
        checkNetworkStatus()
        
        // Регистрируем callback для отслеживания изменений
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(tag, "Network available: $network")
                checkNetworkStatus()
            }
            
            override fun onLost(network: Network) {
                Log.d(tag, "Network lost: $network")
                _networkStatus.value = NetworkStatus.OFFLINE
                _serverStatus.value = ServerStatus.UNAVAILABLE
                _serverStatusDetails.value = null
                serverCheckJob?.cancel()
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                Log.d(tag, "Network capabilities changed")
                checkNetworkStatus()
            }
        }
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        
        // Запускаем периодическую проверку сервера
        startPeriodicServerCheck()
        
        Log.d(tag, "Network monitoring started")
    }
    
    /**
     * Остановка отслеживания
     */
    fun stopMonitoring() {
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
            networkCallback = null
        }
        serverCheckJob?.cancel()
        Log.d(tag, "Network monitoring stopped")
    }
    
    /**
     * Проверить текущее состояние сети
     */
    fun checkNetworkStatus() {
        val network = connectivityManager.activeNetwork
        if (network == null) {
            _networkStatus.value = NetworkStatus.OFFLINE
            _serverStatus.value = ServerStatus.UNAVAILABLE
            _serverStatusDetails.value = null
            return
        }
        
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        if (capabilities == null) {
            _networkStatus.value = NetworkStatus.OFFLINE
            _serverStatus.value = ServerStatus.UNAVAILABLE
            _serverStatusDetails.value = null
            return
        }
        
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                         capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        
        if (hasInternet) {
            _networkStatus.value = NetworkStatus.ONLINE
            // Проверяем доступность сервера в фоне
            checkServerStatus()
        } else {
            _networkStatus.value = NetworkStatus.OFFLINE
            _serverStatus.value = ServerStatus.UNAVAILABLE
            _serverStatusDetails.value = null
        }
    }
    
    /**
     * Периодическая проверка сервера
     */
    private fun startPeriodicServerCheck() {
        serverCheckJob?.cancel()
        serverCheckJob = serverCheckScope.launch {
            while (isActive) {
                if (_networkStatus.value == NetworkStatus.ONLINE) {
                    checkServerStatus()
                }
                delay(serverCheckInterval)
            }
        }
    }
    
    /**
     * Проверить доступность сервера с учетом пробуждения
     */
    fun checkServerStatus() {
        if (_networkStatus.value != NetworkStatus.ONLINE) {
            return
        }
        
        serverCheckScope.launch {
            _serverStatus.value = ServerStatus.CHECKING
            
            val baseUrl = NetworkModule.getCurrentBaseUrl()
            val isRenderServer = baseUrl.contains("onrender.com", ignoreCase = true)
            
            val startTime = System.currentTimeMillis()
            
            try {
                // Быстрая проверка (таймаут 5 секунд)
                val quickCheck = withTimeoutOrNull(5_000L) {
                    NetworkModule.testConnection(baseUrl)
                }
                
                if (quickCheck == true) {
                    // Сервер отвечает быстро - доступен
                    val responseTime = System.currentTimeMillis() - startTime
                    _serverStatus.value = ServerStatus.AVAILABLE
                    _serverStatusDetails.value = ServerStatusDetails(
                        lastCheckTime = System.currentTimeMillis(),
                        lastResponseTime = responseTime,
                        isRenderServer = isRenderServer
                    )
                    Log.d(tag, "Server is available (response time: ${responseTime}ms)")
                    return@launch
                }
                
                // Сервер не ответил быстро - возможно спит
                if (isRenderServer) {
                    // Для Render сервера - проверяем пробуждение
                    checkServerWakeup(baseUrl, startTime)
                } else {
                    // Для других серверов - просто недоступен
                    _serverStatus.value = ServerStatus.UNAVAILABLE
                    _serverStatusDetails.value = ServerStatusDetails(
                        lastCheckTime = System.currentTimeMillis(),
                        isRenderServer = false
                    )
                }
                
            } catch (e: Exception) {
                Log.e(tag, "Error checking server status", e)
                
                if (isRenderServer && isRenderSleepException(e)) {
                    // Это Render спит - нормальное состояние
                    _serverStatus.value = ServerStatus.SLEEPING
                    _serverStatusDetails.value = ServerStatusDetails(
                        lastCheckTime = System.currentTimeMillis(),
                        isRenderServer = true
                    )
                } else {
                    _serverStatus.value = ServerStatus.UNAVAILABLE
                    _serverStatusDetails.value = ServerStatusDetails(
                        lastCheckTime = System.currentTimeMillis(),
                        isRenderServer = isRenderServer
                    )
                }
            }
        }
    }
    
    /**
     * Проверить пробуждение сервера (для Render)
     */
    private suspend fun checkServerWakeup(baseUrl: String, startTime: Long) {
        _serverStatus.value = ServerStatus.WAKING_UP
        
        val maxAttempts = 36 // 36 попыток * 5 сек = 3 минуты
        var attempt = 0
        
        val details = ServerStatusDetails(
            lastCheckTime = System.currentTimeMillis(),
            wakeupAttempt = 0,
            maxWakeupAttempts = maxAttempts,
            isRenderServer = true
        )
        _serverStatusDetails.value = details
        
        while (attempt < maxAttempts && isActive) {
            attempt++
            
            // Обновляем детали
            val elapsed = System.currentTimeMillis() - startTime
            val estimatedRemaining = (maxAttempts - attempt) * wakeupCheckInterval
            _serverStatusDetails.value = details.copy(
                wakeupAttempt = attempt,
                estimatedWakeupTime = System.currentTimeMillis() + estimatedRemaining
            )
            
            delay(wakeupCheckInterval)
            
            if (!isActive) break
            
            try {
                val isReady = withTimeoutOrNull(10_000L) {
                    NetworkModule.testConnection(baseUrl)
                }
                
                if (isReady == true) {
                    // Сервер проснулся!
                    val responseTime = System.currentTimeMillis() - startTime
                    _serverStatus.value = ServerStatus.AVAILABLE
                    _serverStatusDetails.value = ServerStatusDetails(
                        lastCheckTime = System.currentTimeMillis(),
                        lastResponseTime = responseTime,
                        isRenderServer = true
                    )
                    Log.d(tag, "Server woke up after ${responseTime}ms (attempt $attempt)")
                    return
                }
            } catch (e: Exception) {
                if (!isRenderSleepException(e)) {
                    // Не Render sleep exception - реальная ошибка
                    _serverStatus.value = ServerStatus.UNAVAILABLE
                    _serverStatusDetails.value = details.copy(
                        lastCheckTime = System.currentTimeMillis()
                    )
                    Log.e(tag, "Server check failed with non-sleep error", e)
                    return
                }
            }
        }
        
        // Превышено время ожидания
        if (attempt >= maxAttempts) {
            _serverStatus.value = ServerStatus.SLEEPING
            _serverStatusDetails.value = details.copy(
                wakeupAttempt = attempt,
                lastCheckTime = System.currentTimeMillis()
            )
            Log.w(tag, "Server wakeup timeout after ${maxAttempts} attempts")
        }
    }
    
    /**
     * Определить, является ли исключение признаком спящего Render сервера
     */
    private fun isRenderSleepException(e: Exception): Boolean {
        return when (e) {
            is SocketTimeoutException -> true
            is java.net.ConnectException -> true
            is SSLException -> {
                // SSL ошибки при пробуждении Render
                e.message?.contains("timeout", ignoreCase = true) == true ||
                e.message?.contains("connection", ignoreCase = true) == true
            }
            is UnknownHostException -> false // Это реальная проблема
            else -> {
                // Проверяем по сообщению
                val message = e.message?.lowercase() ?: ""
                message.contains("timeout") ||
                message.contains("connection") ||
                message.contains("timed out")
            }
        }
    }
    
    /**
     * Принудительная проверка сервера (для ручной проверки)
     */
    fun forceServerCheck() {
        checkServerStatus()
    }
    
    /**
     * Получить тип сети (Wi-Fi, мобильная, и т.д.)
     */
    fun getNetworkType(): String {
        val network = connectivityManager.activeNetwork ?: return "Нет сети"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Неизвестно"
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Мобильная сеть"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Неизвестно"
        }
    }
    
    /**
     * Проверить, есть ли интернет (синхронно)
     */
    fun isOnline(): Boolean {
        return _networkStatus.value == NetworkStatus.ONLINE
    }
    
    /**
     * Проверить, доступен ли сервер (синхронно)
     */
    fun isServerAvailable(): Boolean {
        return _serverStatus.value == ServerStatus.AVAILABLE
    }
    
    /**
     * Проверить, пробуждается ли сервер
     */
    fun isServerWakingUp(): Boolean {
        return _serverStatus.value == ServerStatus.WAKING_UP
    }
    
    /**
     * Проверить, спит ли сервер
     */
    fun isServerSleeping(): Boolean {
        return _serverStatus.value == ServerStatus.SLEEPING
    }
}
```

---

### 2. Обновление UI индикатора в MainActivity

```kotlin
// В MainActivity.kt обновить метод updateNetworkStatusUI:

/**
 * Обновление UI индикатора сети с учетом состояний сервера
 */
private fun updateNetworkStatusUI(
    networkStatus: NetworkStatusManager.NetworkStatus,
    serverStatus: NetworkStatusManager.ServerStatus,
    serverDetails: NetworkStatusManager.ServerStatusDetails?
) {
    when {
        // Онлайн и сервер доступен
        networkStatus == NetworkStatusManager.NetworkStatus.ONLINE &&
        serverStatus == NetworkStatusManager.ServerStatus.AVAILABLE -> {
            networkStatusIndicator.visibility = View.VISIBLE
            ivNetworkStatus.setImageResource(R.drawable.ic_network_online)
            ivNetworkStatus.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            
            val responseTime = serverDetails?.lastResponseTime
            val statusText = if (responseTime != null && responseTime < 1000) {
                "Онлайн (${responseTime}мс)"
            } else {
                "Онлайн"
            }
            tvNetworkStatus.text = statusText
            tvNetworkStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            networkStatusIndicator.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.holo_green_light)
            )
            btnSync.isEnabled = true
        }
        
        // Онлайн, но сервер пробуждается
        networkStatus == NetworkStatusManager.NetworkStatus.ONLINE &&
        serverStatus == NetworkStatusManager.ServerStatus.WAKING_UP -> {
            networkStatusIndicator.visibility = View.VISIBLE
            ivNetworkStatus.setImageResource(R.drawable.ic_network_waking)
            ivNetworkStatus.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            
            val details = serverDetails
            val statusText = if (details != null) {
                val remaining = details.estimatedWakeupTime?.let {
                    val seconds = ((it - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                    if (seconds > 0) " (~${seconds}с)" else ""
                } ?: ""
                "Пробуждение сервера${remaining} (${details.wakeupAttempt}/${details.maxWakeupAttempts})"
            } else {
                "Пробуждение сервера..."
            }
            tvNetworkStatus.text = statusText
            tvNetworkStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
            networkStatusIndicator.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.holo_orange_light)
            )
            btnSync.isEnabled = false
            updateSyncStatus("Сервер пробуждается...", 0)
        }
        
        // Онлайн, но сервер спит
        networkStatus == NetworkStatusManager.NetworkStatus.ONLINE &&
        serverStatus == NetworkStatusManager.ServerStatus.SLEEPING -> {
            networkStatusIndicator.visibility = View.VISIBLE
            ivNetworkStatus.setImageResource(R.drawable.ic_network_sleeping)
            ivNetworkStatus.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
            tvNetworkStatus.text = "Сервер спит (Render)"
            tvNetworkStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
            networkStatusIndicator.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.holo_blue_light)
            )
            btnSync.isEnabled = true // Можно попробовать разбудить
            updateSyncStatus("Сервер спит. Нажмите синхронизацию для пробуждения", 0)
        }
        
        // Онлайн, но сервер недоступен
        networkStatus == NetworkStatusManager.NetworkStatus.ONLINE &&
        serverStatus == NetworkStatusManager.ServerStatus.UNAVAILABLE -> {
            networkStatusIndicator.visibility = View.VISIBLE
            ivNetworkStatus.setImageResource(R.drawable.ic_network_offline)
            ivNetworkStatus.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            tvNetworkStatus.text = "Сервер недоступен"
            tvNetworkStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            networkStatusIndicator.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.holo_red_light)
            )
            btnSync.isEnabled = false
            updateSyncStatus("Сервер недоступен", 0)
        }
        
        // Офлайн
        networkStatus == NetworkStatusManager.NetworkStatus.OFFLINE -> {
            networkStatusIndicator.visibility = View.VISIBLE
            ivNetworkStatus.setImageResource(R.drawable.ic_network_offline)
            ivNetworkStatus.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            tvNetworkStatus.text = "Офлайн"
            tvNetworkStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            networkStatusIndicator.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.holo_red_light)
            )
            btnSync.isEnabled = false
            updateSyncStatus("Офлайн режим", 0)
        }
        
        // Проверка сервера
        networkStatus == NetworkStatusManager.NetworkStatus.ONLINE &&
        serverStatus == NetworkStatusManager.ServerStatus.CHECKING -> {
            networkStatusIndicator.visibility = View.VISIBLE
            ivNetworkStatus.setImageResource(R.drawable.ic_network_online)
            ivNetworkStatus.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
            tvNetworkStatus.text = "Проверка..."
            tvNetworkStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
            btnSync.isEnabled = false
        }
        
        else -> {
            networkStatusIndicator.visibility = View.GONE
        }
    }
}

// Обновить observeNetworkStatus:
private fun observeNetworkStatus() {
    networkStatusJob = uiScope.launch {
        // Объединяем три StateFlow
        combine(
            networkStatusManager.networkStatus,
            networkStatusManager.serverStatus,
            networkStatusManager.serverStatusDetails
        ) { networkStatus, serverStatus, serverDetails ->
            Triple(networkStatus, serverStatus, serverDetails)
        }.collect { (networkStatus, serverStatus, serverDetails) ->
            updateNetworkStatusUI(networkStatus, serverStatus, serverDetails)
        }
    }
}
```

---

### 3. Добавить новые иконки

```xml
<!-- app/src/main/res/drawable/ic_network_waking.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF9800"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2L11,7h2v2z"/>
    <!-- Анимация пульсации -->
    <path
        android:fillColor="#FF9800"
        android:fillAlpha="0.3"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z"/>
</vector>

<!-- app/src/main/res/drawable/ic_network_sleeping.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#2196F3"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,17c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1 1,0.45 1,1 -0.45,1 -1,1zM13,13h-2L11,7h2v6z"/>
    <!-- Луна -->
    <path
        android:fillColor="#2196F3"
        android:pathData="M19,10.5c0,0.83 -0.67,1.5 -1.5,1.5S16,11.33 16,10.5s0.67,-1.5 1.5,-1.5S19,9.67 19,10.5z"/>
</vector>
```

---

### 4. Анимация для состояния "Пробуждается"

```xml
<!-- app/src/main/res/drawable/network_waking_animation.xml -->
<?xml version="1.0" encoding="utf-8"?>
<animation-list xmlns:android="http://schemas.android.com/apk/res/android"
    android:oneshot="false">
    <item
        android:drawable="@drawable/ic_network_waking"
        android:duration="500" />
    <item
        android:drawable="@drawable/ic_network_online"
        android:duration="500" />
</animation-list>
```

```kotlin
// В updateNetworkStatusUI для состояния WAKING_UP:
if (serverStatus == NetworkStatusManager.ServerStatus.WAKING_UP) {
    val animation = ContextCompat.getDrawable(this, R.drawable.network_waking_animation) as? AnimationDrawable
    ivNetworkStatus.setImageDrawable(animation)
    animation?.start()
}
```

---

### 5. Интеграция с существующей логикой waitForServerWakeup

```kotlin
// В MainActivity.kt обновить startSync:

private fun startSync() {
    if (syncJob?.isActive == true) return
    
    // Проверяем состояние сервера
    when (networkStatusManager.serverStatus.value) {
        NetworkStatusManager.ServerStatus.SLEEPING -> {
            // Сервер спит - запускаем пробуждение
            syncJob = uiScope.launch {
                wakeUpServerAndSync()
            }
        }
        
        NetworkStatusManager.ServerStatus.WAKING_UP -> {
            // Сервер уже пробуждается - ждем
            Toast.makeText(this, "Сервер уже пробуждается, подождите...", Toast.LENGTH_SHORT).show()
        }
        
        NetworkStatusManager.ServerStatus.AVAILABLE -> {
            // Сервер доступен - обычная синхронизация
            syncJob = uiScope.launch {
                performSync()
            }
        }
        
        else -> {
            // Недоступен или проверка - используем существующую логику
            syncJob = uiScope.launch {
                val serverReady = waitForServerWakeup()
                if (serverReady) {
                    performSync()
                } else {
                    updateSyncStatus("Сервер недоступен", 0)
                    hideSyncProgress()
                    resetSyncButtonState()
                }
            }
        }
    }
}

private suspend fun wakeUpServerAndSync() {
    // Принудительно запускаем проверку пробуждения
    networkStatusManager.forceServerCheck()
    
    // Ждем, пока сервер проснется (максимум 3 минуты)
    var attempts = 0
    val maxAttempts = 36
    
    while (attempts < maxAttempts && isActive) {
        val status = networkStatusManager.serverStatus.value
        
        when (status) {
            NetworkStatusManager.ServerStatus.AVAILABLE -> {
                // Сервер проснулся - синхронизируем
                performSync()
                return
            }
            
            NetworkStatusManager.ServerStatus.WAKING_UP -> {
                // Показываем прогресс пробуждения
                val details = networkStatusManager.serverStatusDetails.value
                val progress = details?.let { 
                    (it.wakeupAttempt * 100) / it.maxWakeupAttempts 
                } ?: 0
                updateSyncStatus(
                    "Пробуждение сервера... ${details?.wakeupAttempt ?: 0}/${details?.maxWakeupAttempts ?: 36}",
                    progress
                )
                delay(5000)
                attempts++
            }
            
            NetworkStatusManager.ServerStatus.UNAVAILABLE -> {
                // Сервер недоступен
                updateSyncStatus("Сервер недоступен", 0)
                hideSyncProgress()
                resetSyncButtonState()
                return
            }
            
            else -> {
                delay(1000)
                attempts++
            }
        }
    }
    
    // Превышено время ожидания
    updateSyncStatus("Сервер не проснулся за отведенное время", 0)
    hideSyncProgress()
    resetSyncButtonState()
}

private suspend fun performSync() {
    // Обычная логика синхронизации (существующий код)
    btnSync.isEnabled = false
    btnSync.text = syncButtonDefaultText
    showSyncProgress()
    updateSyncStatus("Подключение к серверу...", 0)
    
    val result = withContext(Dispatchers.IO) {
        syncService.syncAll { percent, type ->
            withContext(Dispatchers.Main) {
                updateSyncStatus(type, percent)
            }
        }
    }
    
    // ... остальная логика обработки результата ...
}
```

---

## 📊 Визуальная схема состояний

```
┌─────────────────────────────────────────────────────────┐
│              СОСТОЯНИЯ СЕРВЕРА                          │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  🌙 SLEEPING (Спящий)                                   │
│     └─> Сервер не отвечает, но это нормально           │
│     └─> Индикатор: 🔵 "Сервер спит (Render)"           │
│     └─> Кнопка синхронизации: ВКЛЮЧЕНА                 │
│                                                          │
│  ⏳ WAKING_UP (Пробуждается)                            │
│     └─> Сервер обрабатывает первый запрос              │
│     └─> Индикатор: 🟠 "Пробуждение... (X/36)"          │
│     └─> Кнопка синхронизации: ОТКЛЮЧЕНА                │
│     └─> Показывается прогресс пробуждения              │
│                                                          │
│  ✅ AVAILABLE (Доступен)                                │
│     └─> Сервер активен и отвечает быстро               │
│     └─> Индикатор: 🟢 "Онлайн (XXXмс)"                 │
│     └─> Кнопка синхронизации: ВКЛЮЧЕНА                 │
│                                                          │
│  ❌ UNAVAILABLE (Недоступен)                            │
│     └─> Реальная проблема с сервером                   │
│     └─> Индикатор: 🔴 "Сервер недоступен"              │
│     └─> Кнопка синхронизации: ОТКЛЮЧЕНА                │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## 🎯 Преимущества решения

1. **Понятность для пользователя:**
   - Видно, что сервер спит (это нормально)
   - Видно прогресс пробуждения
   - Не путает "спящий" с "недоступен"

2. **Эффективность:**
   - Не делает лишних запросов к спящему серверу
   - Периодическая проверка только при наличии сети
   - Умное определение состояния сервера

3. **Интеграция:**
   - Использует существующую логику `waitForServerWakeup()`
   - Совместим с `isRenderSleepException()`
   - Не нарушает текущую работу приложения

---

*Документ содержит полную реализацию индикатора с учетом особенностей Render.com сервера.*



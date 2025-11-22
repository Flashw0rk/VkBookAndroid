# 📱 Детальное описание: Индикатор офлайн/онлайн, Очередь изменений, Конфликт-резолюшн

**Дата:** 21.11.2025  
**Приложение:** VkBookAndroid v1.0

---

## 1. 🔴 ИНДИКАТОР ОФЛАЙН/ОНЛАЙН РЕЖИМА

### 🎯 Проблема

**Текущая ситуация:**
- Пользователь не видит, работает ли приложение офлайн или онлайн
- При отсутствии сети показывается только "Сервер недоступен" при попытке синхронизации
- Нет визуального индикатора состояния сети в реальном времени
- Пользователь не понимает, почему некоторые функции недоступны

**Последствия:**
- Путаница: "Почему не работает синхронизация?"
- Попытки синхронизации при отсутствии сети
- Непонимание, когда данные актуальны

---

### ✅ Решение: Менеджер состояния сети

#### 1.1. Создание NetworkStatusManager

```kotlin
// app/src/main/java/com/example/vkbookandroid/network/NetworkStatusManager.kt
package com.example.vkbookandroid.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Менеджер состояния сети
 * Отслеживает подключение к интернету и доступность сервера
 */
class NetworkStatusManager(private val context: Context) {
    
    private val tag = "NetworkStatusManager"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    // Состояние сети
    private val _networkStatus = MutableStateFlow<NetworkStatus>(NetworkStatus.UNKNOWN)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()
    
    // Состояние сервера
    private val _serverStatus = MutableStateFlow<ServerStatus>(ServerStatus.UNKNOWN)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    /**
     * Статус сети
     */
    enum class NetworkStatus {
        ONLINE,      // Есть интернет
        OFFLINE,     // Нет интернета
        UNKNOWN      // Неизвестно (инициализация)
    }
    
    /**
     * Статус сервера
     */
    enum class ServerStatus {
        AVAILABLE,   // Сервер доступен
        UNAVAILABLE, // Сервер недоступен
        CHECKING,    // Проверка в процессе
        UNKNOWN      // Неизвестно
    }
    
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
        Log.d(tag, "Network monitoring started")
    }
    
    /**
     * Остановка отслеживания
     */
    fun stopMonitoring() {
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
            networkCallback = null
            Log.d(tag, "Network monitoring stopped")
        }
    }
    
    /**
     * Проверить текущее состояние сети
     */
    fun checkNetworkStatus() {
        val network = connectivityManager.activeNetwork
        if (network == null) {
            _networkStatus.value = NetworkStatus.OFFLINE
            _serverStatus.value = ServerStatus.UNAVAILABLE
            return
        }
        
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        if (capabilities == null) {
            _networkStatus.value = NetworkStatus.OFFLINE
            _serverStatus.value = ServerStatus.UNAVAILABLE
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
        }
    }
    
    /**
     * Проверить доступность сервера
     */
    private fun checkServerStatus() {
        _serverStatus.value = ServerStatus.CHECKING
        
        // Проверяем в фоне (не блокируя UI)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val isAvailable = com.example.vkbookandroid.network.NetworkModule.testConnection(
                    com.example.vkbookandroid.network.NetworkModule.getCurrentBaseUrl()
                )
                _serverStatus.value = if (isAvailable) {
                    ServerStatus.AVAILABLE
                } else {
                    ServerStatus.UNAVAILABLE
                }
            } catch (e: Exception) {
                Log.e(tag, "Error checking server status", e)
                _serverStatus.value = ServerStatus.UNAVAILABLE
            }
        }
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
}
```

---

#### 1.2. Обновление layout для индикатора

```xml
<!-- app/src/main/res/layout/activity_main.xml -->
<!-- Добавить после tvProgressPercent, перед закрывающим тегом LinearLayout панели синхронизации -->

<!-- Индикатор состояния сети -->
<LinearLayout
    android:id="@+id/networkStatusIndicator"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginStart="8dp"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:padding="4dp"
    android:background="@drawable/network_status_background"
    android:visibility="visible">

    <!-- Иконка состояния -->
    <ImageView
        android:id="@+id/ivNetworkStatus"
        android:layout_width="16dp"
        android:layout_height="16dp"
        android:src="@drawable/ic_network_online"
        android:tint="#4CAF50"
        android:contentDescription="Статус сети" />

    <!-- Текст статуса -->
    <TextView
        android:id="@+id/tvNetworkStatus"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="4dp"
        android:text="Онлайн"
        android:textSize="10sp"
        android:textColor="#4CAF50"
        android:maxLines="1" />

</LinearLayout>
```

**Создать drawable ресурсы:**

```xml
<!-- app/src/main/res/drawable/network_status_background.xml -->
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#E8F5E9" />
    <corners android:radius="4dp" />
    <stroke
        android:width="1dp"
        android:color="#4CAF50" />
</shape>

<!-- app/src/main/res/drawable/ic_network_online.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#4CAF50"
        android:pathData="M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.07,2.93 1,9zM9,17l3,3 3,-3c-1.65,-1.66 -4.34,-1.66 -6,0zM5,13l2,2c2.76,-2.76 7.24,-2.76 10,0l2,-2C15.14,9.14 8.87,9.14 5,13z"/>
</vector>

<!-- app/src/main/res/drawable/ic_network_offline.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#F44336"
        android:pathData="M23.64,7c-0.45,-0.34 -4.93,-4 -11.64,-4 -1.5,0 -2.89,0.19 -4.15,0.48L18.18,13.8 23.64,7zM17.04,15.22L3.27,1.44 2,2.72l2.05,2.06C1.91,5.76 0.59,6.82 0.36,7l11.63,14.49 0.01,0.01 0.01,-0.01 3.9,-4.86 3.32,3.32 1.27,-1.27 -3.46,-3.46z"/>
</vector>
```

---

#### 1.3. Интеграция в MainActivity

```kotlin
// В MainActivity.kt добавить:

class MainActivity : AppCompatActivity() {
    
    // ... существующие поля ...
    
    // Менеджер состояния сети
    private lateinit var networkStatusManager: NetworkStatusManager
    private lateinit var networkStatusIndicator: LinearLayout
    private lateinit var ivNetworkStatus: ImageView
    private lateinit var tvNetworkStatus: TextView
    
    // Job для наблюдения за состоянием сети
    private var networkStatusJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ... существующий код ...
        
        // Инициализация менеджера сети
        networkStatusManager = NetworkStatusManager(this)
        networkStatusManager.startMonitoring()
        
        // Инициализация UI элементов
        networkStatusIndicator = findViewById(R.id.networkStatusIndicator)
        ivNetworkStatus = findViewById(R.id.ivNetworkStatus)
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus)
        
        // Наблюдаем за изменениями состояния сети
        observeNetworkStatus()
    }
    
    /**
     * Наблюдение за состоянием сети
     */
    private fun observeNetworkStatus() {
        networkStatusJob = uiScope.launch {
            // Объединяем два StateFlow для отслеживания обоих состояний
            combine(
                networkStatusManager.networkStatus,
                networkStatusManager.serverStatus
            ) { networkStatus, serverStatus ->
                Pair(networkStatus, serverStatus)
            }.collect { (networkStatus, serverStatus) ->
                updateNetworkStatusUI(networkStatus, serverStatus)
            }
        }
    }
    
    /**
     * Обновление UI индикатора сети
     */
    private fun updateNetworkStatusUI(
        networkStatus: NetworkStatusManager.NetworkStatus,
        serverStatus: NetworkStatusManager.ServerStatus
    ) {
        when {
            // Онлайн и сервер доступен
            networkStatus == NetworkStatusManager.NetworkStatus.ONLINE &&
            serverStatus == NetworkStatusManager.ServerStatus.AVAILABLE -> {
                networkStatusIndicator.visibility = View.VISIBLE
                ivNetworkStatus.setImageResource(R.drawable.ic_network_online)
                ivNetworkStatus.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                tvNetworkStatus.text = "Онлайн"
                tvNetworkStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                networkStatusIndicator.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, android.R.color.holo_green_light)
                )
                btnSync.isEnabled = true
            }
            
            // Онлайн, но сервер недоступен
            networkStatus == NetworkStatusManager.NetworkStatus.ONLINE &&
            serverStatus == NetworkStatusManager.ServerStatus.UNAVAILABLE -> {
                networkStatusIndicator.visibility = View.VISIBLE
                ivNetworkStatus.setImageResource(R.drawable.ic_network_offline)
                ivNetworkStatus.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                tvNetworkStatus.text = "Сервер недоступен"
                tvNetworkStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                networkStatusIndicator.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, android.R.color.holo_orange_light)
                )
                btnSync.isEnabled = false
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
    
    override fun onDestroy() {
        super.onDestroy()
        networkStatusJob?.cancel()
        networkStatusManager.stopMonitoring()
    }
}
```

**Добавить импорты:**
```kotlin
import androidx.core.content.ContextCompat
import android.content.res.ColorStateList
import android.view.View
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
```

---

#### 1.4. Баннер при переходе в офлайн

```kotlin
// Добавить в MainActivity.kt

private var offlineBanner: View? = null

private fun showOfflineBanner() {
    if (offlineBanner != null) return
    
    val banner = layoutInflater.inflate(R.layout.offline_banner, findViewById(android.R.id.content), false)
    offlineBanner = banner
    
    findViewById<ViewGroup>(android.R.id.content).addView(banner, 0)
    
    // Автоматически скрыть через 5 секунд
    banner.postDelayed({
        hideOfflineBanner()
    }, 5000)
}

private fun hideOfflineBanner() {
    offlineBanner?.let {
        findViewById<ViewGroup>(android.R.id.content).removeView(it)
        offlineBanner = null
    }
}

// В updateNetworkStatusUI добавить:
if (networkStatus == NetworkStatusManager.NetworkStatus.OFFLINE) {
    showOfflineBanner()
} else {
    hideOfflineBanner()
}
```

```xml
<!-- app/src/main/res/layout/offline_banner.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:background="#F44336"
    android:padding="12dp"
    android:gravity="center_vertical">

    <ImageView
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:src="@drawable/ic_network_offline"
        android:tint="@android:color/white" />

    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:layout_marginStart="12dp"
        android:text="Работа в офлайн режиме. Изменения будут синхронизированы при восстановлении связи."
        android:textColor="@android:color/white"
        android:textSize="14sp" />

    <ImageButton
        android:id="@+id/btnCloseBanner"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:src="@android:drawable/ic_menu_close_clear_cancel"
        android:tint="@android:color/white"
        android:background="?attr/selectableItemBackgroundBorderless" />

</LinearLayout>
```

---

## 2. 📝 ОЧЕРЕДЬ ИЗМЕНЕНИЙ (OFFLINE CHANGE QUEUE)

### 🎯 Проблема

**Текущая ситуация:**
- При редактировании данных офлайн изменения сохраняются только локально
- При синхронизации изменения могут быть потеряны
- Нет механизма отслеживания изменений, которые нужно отправить на сервер
- Пользователь не знает, какие изменения ожидают синхронизации

**Пример проблемы:**
1. Пользователь редактирует арматуру офлайн
2. Сохраняет изменения локально
3. Включает интернет и синхронизирует
4. **Проблема:** Изменения не отправляются на сервер, так как нет механизма отслеживания

---

### ✅ Решение: Система очереди изменений

#### 2.1. Модель данных для изменений

```kotlin
// app/src/main/java/com/example/vkbookandroid/offline/OfflineChange.kt
package com.example.vkbookandroid.offline

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Изменение, которое нужно синхронизировать с сервером
 */
data class OfflineChange(
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @SerializedName("type")
    val type: ChangeType,
    
    @SerializedName("entity")
    val entity: EntityType,
    
    @SerializedName("entityId")
    val entityId: String? = null, // ID сущности (если есть)
    
    @SerializedName("data")
    val data: Map<String, Any>, // Данные для синхронизации
    
    @SerializedName("retryCount")
    val retryCount: Int = 0,
    
    @SerializedName("lastError")
    val lastError: String? = null
) {
    enum class ChangeType {
        CREATE,  // Создание новой сущности
        UPDATE,  // Обновление существующей
        DELETE   // Удаление
    }
    
    enum class EntityType {
        ARMATURE_COORDS,  // Изменения в armature_coords.json
        EXCEL_FILE,       // Изменения в Excel файле
        REMINDER,         // Напоминание
        SETTINGS          // Настройки (если нужно синхронизировать)
    }
    
    fun toJson(): String = Gson().toJson(this)
    
    companion object {
        fun fromJson(json: String): OfflineChange? {
            return try {
                Gson().fromJson(json, OfflineChange::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
```

---

#### 2.2. Менеджер очереди изменений

```kotlin
// app/src/main/java/com/example/vkbookandroid/offline/OfflineChangeQueue.kt
package com.example.vkbookandroid.offline

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Менеджер очереди изменений для офлайн-режима
 */
class OfflineChangeQueue(private val context: Context) {
    
    private val tag = "OfflineChangeQueue"
    private val gson = Gson()
    private val queueFile = File(context.filesDir, "offline_changes_queue.json")
    private val lock = ReentrantLock()
    private val maxRetries = 3
    
    /**
     * Добавить изменение в очередь
     */
    fun addChange(change: OfflineChange) {
        lock.withLock {
            try {
                val changes = loadChanges().toMutableList()
                changes.add(change)
                saveChanges(changes)
                Log.d(tag, "Added change to queue: ${change.type} ${change.entity} (ID: ${change.id})")
            } catch (e: Exception) {
                Log.e(tag, "Failed to add change to queue", e)
            }
        }
    }
    
    /**
     * Получить все изменения из очереди
     */
    fun getAllChanges(): List<OfflineChange> {
        return lock.withLock {
            loadChanges()
        }
    }
    
    /**
     * Получить изменения определенного типа
     */
    fun getChangesByType(type: OfflineChange.ChangeType): List<OfflineChange> {
        return getAllChanges().filter { it.type == type }
    }
    
    /**
     * Получить изменения определенной сущности
     */
    fun getChangesByEntity(entity: OfflineChange.EntityType): List<OfflineChange> {
        return getAllChanges().filter { it.entity == entity }
    }
    
    /**
     * Удалить изменение из очереди (после успешной синхронизации)
     */
    fun removeChange(changeId: String) {
        lock.withLock {
            try {
                val changes = loadChanges().toMutableList()
                changes.removeAll { it.id == changeId }
                saveChanges(changes)
                Log.d(tag, "Removed change from queue: $changeId")
            } catch (e: Exception) {
                Log.e(tag, "Failed to remove change from queue", e)
            }
        }
    }
    
    /**
     * Увеличить счетчик попыток и обновить ошибку
     */
    fun markChangeFailed(changeId: String, error: String) {
        lock.withLock {
            try {
                val changes = loadChanges().toMutableList()
                val change = changes.find { it.id == changeId }
                if (change != null) {
                    val updatedChange = change.copy(
                        retryCount = change.retryCount + 1,
                        lastError = error
                    )
                    changes.remove(change)
                    if (updatedChange.retryCount < maxRetries) {
                        changes.add(updatedChange)
                    } else {
                        Log.w(tag, "Change $changeId exceeded max retries, removing from queue")
                    }
                    saveChanges(changes)
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to mark change as failed", e)
            }
        }
    }
    
    /**
     * Очистить очередь
     */
    fun clear() {
        lock.withLock {
            try {
                queueFile.delete()
                Log.d(tag, "Queue cleared")
            } catch (e: Exception) {
                Log.e(tag, "Failed to clear queue", e)
            }
        }
    }
    
    /**
     * Получить количество изменений в очереди
     */
    fun getPendingChangesCount(): Int {
        return getAllChanges().size
    }
    
    /**
     * Загрузить изменения из файла
     */
    private fun loadChanges(): List<OfflineChange> {
        if (!queueFile.exists()) {
            return emptyList()
        }
        
        return try {
            val json = queueFile.readText()
            if (json.isBlank()) {
                emptyList()
            } else {
                val type = object : TypeToken<List<OfflineChange>>() {}.type
                gson.fromJson<List<OfflineChange>>(json, type) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load changes from queue", e)
            emptyList()
        }
    }
    
    /**
     * Сохранить изменения в файл
     */
    private fun saveChanges(changes: List<OfflineChange>) {
        try {
            val json = gson.toJson(changes)
            queueFile.writeText(json)
        } catch (e: Exception) {
            Log.e(tag, "Failed to save changes to queue", e)
            throw e
        }
    }
}
```

---

#### 2.3. Синхронизатор изменений

```kotlin
// app/src/main/java/com/example/vkbookandroid/offline/OfflineChangeSyncer.kt
package com.example.vkbookandroid.offline

import android.content.Context
import android.util.Log
import com.example.vkbookandroid.network.NetworkModule
import com.example.vkbookandroid.service.SyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Синхронизатор изменений из очереди
 */
class OfflineChangeSyncer(
    private val context: Context,
    private val queue: OfflineChangeQueue
) {
    
    private val tag = "OfflineChangeSyncer"
    private val syncService = SyncService(context)
    
    /**
     * Синхронизировать все изменения из очереди
     */
    suspend fun syncPendingChanges(): SyncResult {
        return withContext(Dispatchers.IO) {
            val changes = queue.getAllChanges()
            if (changes.isEmpty()) {
                Log.d(tag, "No pending changes to sync")
                return@withContext SyncResult(
                    success = true,
                    syncedCount = 0,
                    failedCount = 0
                )
            }
            
            Log.d(tag, "Starting sync of ${changes.size} pending changes")
            
            var syncedCount = 0
            var failedCount = 0
            val errors = mutableListOf<String>()
            
            // Группируем изменения по типу сущности для более эффективной синхронизации
            val changesByEntity = changes.groupBy { it.entity }
            
            for ((entity, entityChanges) in changesByEntity) {
                try {
                    when (entity) {
                        OfflineChange.EntityType.ARMATURE_COORDS -> {
                            val result = syncArmatureChanges(entityChanges)
                            syncedCount += result.syncedCount
                            failedCount += result.failedCount
                            errors.addAll(result.errors)
                        }
                        
                        OfflineChange.EntityType.EXCEL_FILE -> {
                            val result = syncExcelChanges(entityChanges)
                            syncedCount += result.syncedCount
                            failedCount += result.failedCount
                            errors.addAll(result.errors)
                        }
                        
                        OfflineChange.EntityType.REMINDER -> {
                            // Напоминания синхронизируются через другой механизм
                            // Пока просто помечаем как синхронизированные
                            entityChanges.forEach { queue.removeChange(it.id) }
                            syncedCount += entityChanges.size
                        }
                        
                        else -> {
                            Log.w(tag, "Unknown entity type: $entity")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error syncing changes for entity $entity", e)
                    entityChanges.forEach { change ->
                        queue.markChangeFailed(change.id, e.message ?: "Unknown error")
                    }
                    failedCount += entityChanges.size
                    errors.add("Error syncing $entity: ${e.message}")
                }
            }
            
            Log.d(tag, "Sync completed: $syncedCount synced, $failedCount failed")
            
            SyncResult(
                success = failedCount == 0,
                syncedCount = syncedCount,
                failedCount = failedCount,
                errors = errors
            )
        }
    }
    
    /**
     * Синхронизировать изменения арматуры
     */
    private suspend fun syncArmatureChanges(changes: List<OfflineChange>): SyncResult {
        // Объединяем все изменения в один JSON файл
        val mergedData = mutableMapOf<String, Any>()
        
        for (change in changes) {
            when (change.type) {
                OfflineChange.ChangeType.CREATE,
                OfflineChange.ChangeType.UPDATE -> {
                    // Объединяем данные
                    mergedData.putAll(change.data)
                }
                OfflineChange.ChangeType.DELETE -> {
                    // Удаляем из данных
                    change.entityId?.let { mergedData.remove(it) }
                }
            }
        }
        
        // Загружаем текущий файл и объединяем с изменениями
        val currentFile = java.io.File(context.filesDir, "data/armature_coords.json")
        if (currentFile.exists()) {
            try {
                val currentJson = currentFile.readText()
                val currentData = com.google.gson.Gson().fromJson<Map<String, Any>>(
                    currentJson,
                    object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                )
                mergedData.putAll(currentData)
            } catch (e: Exception) {
                Log.w(tag, "Failed to load current armature coords", e)
            }
        }
        
        // Сохраняем объединенные данные
        val mergedJson = com.google.gson.Gson().toJson(mergedData)
        currentFile.writeText(mergedJson)
        
        // Синхронизируем через SyncService
        val syncResult = syncService.syncArmatureCoords(
            com.example.vkbookandroid.service.SyncService.SyncResult()
        )
        
        if (syncResult) {
            // Удаляем синхронизированные изменения из очереди
            changes.forEach { queue.removeChange(it.id) }
            return SyncResult(
                success = true,
                syncedCount = changes.size,
                failedCount = 0
            )
        } else {
            // Помечаем как неудачные
            changes.forEach { change ->
                queue.markChangeFailed(change.id, "Sync failed")
            }
            return SyncResult(
                success = false,
                syncedCount = 0,
                failedCount = changes.size,
                errors = listOf("Failed to sync armature changes")
            )
        }
    }
    
    /**
     * Синхронизировать изменения Excel файлов
     */
    private suspend fun syncExcelChanges(changes: List<OfflineChange>): SyncResult {
        // Excel файлы синхронизируются через EditorUploadService
        // Здесь нужно интегрировать с существующим механизмом загрузки
        
        var syncedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()
        
        for (change in changes) {
            try {
                val filename = change.data["filename"] as? String
                if (filename == null) {
                    queue.markChangeFailed(change.id, "Missing filename")
                    failedCount++
                    continue
                }
                
                val file = java.io.File(context.filesDir, "data/$filename")
                if (!file.exists()) {
                    queue.markChangeFailed(change.id, "File not found: $filename")
                    failedCount++
                    continue
                }
                
                // Загружаем файл через EditorUploadService
                val uploadService = com.example.vkbookandroid.editor.EditorUploadService()
                val result = when {
                    filename.endsWith(".json") -> uploadService.uploadJson(file)
                    filename.endsWith(".xlsx") -> uploadService.uploadExcel(file)
                    else -> {
                        queue.markChangeFailed(change.id, "Unsupported file type")
                        failedCount++
                        continue
                    }
                }
                
                if (result?.isSuccessful == true) {
                    queue.removeChange(change.id)
                    syncedCount++
                } else {
                    queue.markChangeFailed(change.id, "Upload failed: ${result?.code()}")
                    failedCount++
                    errors.add("Failed to upload $filename")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error syncing Excel change ${change.id}", e)
                queue.markChangeFailed(change.id, e.message ?: "Unknown error")
                failedCount++
                errors.add("Error: ${e.message}")
            }
        }
        
        return SyncResult(
            success = failedCount == 0,
            syncedCount = syncedCount,
            failedCount = failedCount,
            errors = errors
        )
    }
    
    data class SyncResult(
        val success: Boolean,
        val syncedCount: Int,
        val failedCount: Int,
        val errors: List<String> = emptyList()
    )
}
```

---

#### 2.4. Интеграция в EditorFragment

```kotlin
// В EditorFragment.kt при сохранении изменений:

private fun saveChangesToEditorOut() {
    // ... существующий код сохранения ...
    
    // Добавляем изменение в очередь
    val changeQueue = OfflineChangeQueue(requireContext())
    val change = OfflineChange(
        type = OfflineChange.ChangeType.UPDATE,
        entity = OfflineChange.EntityType.ARMATURE_COORDS,
        entityId = currentPdfName,
        data = mapOf(
            "pdfName" to (currentPdfName ?: ""),
            "markers" to editorOverlay.getMarkers().map { marker ->
                mapOf(
                    "id" to marker.id,
                    "x" to marker.x,
                    "y" to marker.y,
                    "label" to marker.label
                )
            }
        )
    )
    changeQueue.addChange(change)
    
    // Пытаемся синхронизировать сразу, если есть интернет
    if (NetworkStatusManager(requireContext()).isOnline()) {
        lifecycleScope.launch {
            val syncer = OfflineChangeSyncer(requireContext(), changeQueue)
            val result = syncer.syncPendingChanges()
            if (result.success) {
                Toast.makeText(context, "Изменения синхронизированы", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Изменения сохранены, будут синхронизированы позже", Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        Toast.makeText(context, "Изменения сохранены офлайн, будут синхронизированы при подключении", Toast.LENGTH_LONG).show()
    }
}
```

---

#### 2.5. Автоматическая синхронизация при восстановлении связи

```kotlin
// В MainActivity.kt добавить в observeNetworkStatus:

private fun observeNetworkStatus() {
    networkStatusJob = uiScope.launch {
        var wasOffline = false
        
        combine(
            networkStatusManager.networkStatus,
            networkStatusManager.serverStatus
        ) { networkStatus, serverStatus ->
            Pair(networkStatus, serverStatus)
        }.collect { (networkStatus, serverStatus) ->
            updateNetworkStatusUI(networkStatus, serverStatus)
            
            // Автоматическая синхронизация при восстановлении связи
            if (wasOffline && 
                networkStatus == NetworkStatusManager.NetworkStatus.ONLINE &&
                serverStatus == NetworkStatusManager.ServerStatus.AVAILABLE) {
                
                // Синхронизируем изменения из очереди
                syncPendingChanges()
            }
            
            wasOffline = networkStatus == NetworkStatusManager.NetworkStatus.OFFLINE
        }
    }
}

private fun syncPendingChanges() {
    uiScope.launch {
        try {
            val queue = OfflineChangeQueue(this@MainActivity)
            val pendingCount = queue.getPendingChangesCount()
            
            if (pendingCount > 0) {
                updateSyncStatus("Синхронизация $pendingCount изменений...", 0)
                showSyncProgress()
                
                val syncer = OfflineChangeSyncer(this@MainActivity, queue)
                val result = withContext(Dispatchers.IO) {
                    syncer.syncPendingChanges()
                }
                
                hideSyncProgress()
                
                if (result.success) {
                    updateSyncStatus("Синхронизировано $pendingCount изменений", 100)
                    Toast.makeText(
                        this@MainActivity,
                        "Синхронизировано $pendingCount изменений",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    updateSyncStatus("Ошибка синхронизации ${result.failedCount} изменений", 0)
                    Toast.makeText(
                        this@MainActivity,
                        "Ошибка синхронизации ${result.failedCount} из ${result.syncedCount + result.failedCount} изменений",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error syncing pending changes", e)
            hideSyncProgress()
        }
    }
}
```

---

#### 2.6. UI для отображения очереди изменений

```kotlin
// Добавить в настройки или отдельный экран:

// app/src/main/java/com/example/vkbookandroid/ui/PendingChangesActivity.kt
class PendingChangesActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PendingChangesAdapter
    private lateinit var queue: OfflineChangeQueue
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pending_changes)
        
        queue = OfflineChangeQueue(this)
        recyclerView = findViewById(R.id.recyclerView)
        
        adapter = PendingChangesAdapter { change ->
            // Показать детали изменения
            showChangeDetails(change)
        }
        
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        loadChanges()
    }
    
    private fun loadChanges() {
        val changes = queue.getAllChanges()
        adapter.submitList(changes)
        
        findViewById<TextView>(R.id.tvEmptyState).visibility = 
            if (changes.isEmpty()) View.VISIBLE else View.GONE
    }
}
```

---

## 3. ⚖️ КОНФЛИКТ-РЕЗОЛЮШН (CONFLICT RESOLUTION)

### 🎯 Проблема

**Текущая ситуация:**
- Если данные изменились и на сервере, и локально, нет механизма разрешения конфликтов
- При синхронизации локальные изменения могут перезаписать серверные (или наоборот)
- Пользователь не знает о конфликтах
- Нет возможности выбрать, какую версию использовать

**Пример проблемы:**
1. Пользователь редактирует арматуру офлайн (локальная версия A)
2. Другой пользователь редактирует ту же арматуру на сервере (серверная версия B)
3. При синхронизации версия A перезаписывает версию B
4. **Проблема:** Изменения другого пользователя потеряны

---

### ✅ Решение: Система разрешения конфликтов

#### 3.1. Модель конфликта

```kotlin
// app/src/main/java/com/example/vkbookandroid/conflict/DataConflict.kt
package com.example.vkbookandroid.conflict

import com.google.gson.Gson
import java.util.UUID

/**
 * Конфликт данных между локальной и серверной версиями
 */
data class DataConflict(
    val id: String = UUID.randomUUID().toString(),
    val entityType: EntityType,
    val entityId: String,
    val localVersion: ConflictVersion,
    val serverVersion: ConflictVersion,
    val conflictType: ConflictType,
    val detectedAt: Long = System.currentTimeMillis()
) {
    enum class EntityType {
        ARMATURE_COORDS,
        EXCEL_FILE,
        REMINDER
    }
    
    enum class ConflictType {
        CONTENT_CHANGED,  // Содержимое изменилось в обеих версиях
        LOCAL_DELETED,    // Локально удалено, на сервере изменено
        SERVER_DELETED,   // На сервере удалено, локально изменено
        BOTH_DELETED      // Удалено в обеих версиях (не конфликт, но нужно обработать)
    }
    
    data class ConflictVersion(
        val data: Map<String, Any>,
        val timestamp: Long,
        val hash: String? = null
    )
    
    fun toJson(): String = Gson().toJson(this)
    
    companion object {
        fun fromJson(json: String): DataConflict? {
            return try {
                Gson().fromJson(json, DataConflict::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
```

---

#### 3.2. Детектор конфликтов

```kotlin
// app/src/main/java/com/example/vkbookandroid/conflict/ConflictDetector.kt
package com.example.vkbookandroid.conflict

import android.content.Context
import android.util.Log
import com.example.vkbookandroid.FileHashManager
import com.google.gson.Gson
import java.io.File

/**
 * Детектор конфликтов данных
 */
class ConflictDetector(private val context: Context) {
    
    private val tag = "ConflictDetector"
    private val hashManager = FileHashManager(context)
    private val gson = Gson()
    
    /**
     * Обнаружить конфликты при синхронизации
     */
    suspend fun detectConflicts(
        localFile: File,
        serverData: String,
        entityType: DataConflict.EntityType
    ): List<DataConflict> {
        val conflicts = mutableListOf<DataConflict>()
        
        try {
            // Загружаем локальные данные
            val localData = if (localFile.exists()) {
                localFile.readText()
            } else {
                null
            }
            
            // Парсим данные
            val localMap = localData?.let { parseData(it, entityType) } ?: emptyMap()
            val serverMap = parseData(serverData, entityType)
            
            // Сравниваем по ключам (ID сущностей)
            val allKeys = (localMap.keys + serverMap.keys).distinct()
            
            for (key in allKeys) {
                val localItem = localMap[key]
                val serverItem = serverMap[key]
                
                when {
                    // Оба существуют - проверяем изменения
                    localItem != null && serverItem != null -> {
                        if (hasChanges(localItem, serverItem)) {
                            conflicts.add(DataConflict(
                                entityType = entityType,
                                entityId = key,
                                localVersion = DataConflict.ConflictVersion(
                                    data = localItem,
                                    timestamp = getTimestamp(localItem),
                                    hash = calculateHash(localItem)
                                ),
                                serverVersion = DataConflict.ConflictVersion(
                                    data = serverItem,
                                    timestamp = getTimestamp(serverItem),
                                    hash = calculateHash(serverItem)
                                ),
                                conflictType = DataConflict.ConflictType.CONTENT_CHANGED
                            ))
                        }
                    }
                    
                    // Локально удалено, на сервере есть
                    localItem == null && serverItem != null -> {
                        conflicts.add(DataConflict(
                            entityType = entityType,
                            entityId = key,
                            localVersion = DataConflict.ConflictVersion(
                                data = emptyMap(),
                                timestamp = 0
                            ),
                            serverVersion = DataConflict.ConflictVersion(
                                data = serverItem,
                                timestamp = getTimestamp(serverItem),
                                hash = calculateHash(serverItem)
                            ),
                            conflictType = DataConflict.ConflictType.LOCAL_DELETED
                        ))
                    }
                    
                    // На сервере удалено, локально есть
                    localItem != null && serverItem == null -> {
                        conflicts.add(DataConflict(
                            entityType = entityType,
                            entityId = key,
                            localVersion = DataConflict.ConflictVersion(
                                data = localItem,
                                timestamp = getTimestamp(localItem),
                                hash = calculateHash(localItem)
                            ),
                            serverVersion = DataConflict.ConflictVersion(
                                data = emptyMap(),
                                timestamp = 0
                            ),
                            conflictType = DataConflict.ConflictType.SERVER_DELETED
                        ))
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(tag, "Error detecting conflicts", e)
        }
        
        return conflicts
    }
    
    /**
     * Парсить данные в зависимости от типа
     */
    private fun parseData(json: String, entityType: DataConflict.EntityType): Map<String, Map<String, Any>> {
        return when (entityType) {
            DataConflict.EntityType.ARMATURE_COORDS -> {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, Map<String, Any>>>() {}.type
                gson.fromJson(json, type) ?: emptyMap()
            }
            else -> emptyMap()
        }
    }
    
    /**
     * Проверить, есть ли изменения между версиями
     */
    private fun hasChanges(local: Map<String, Any>, server: Map<String, Any>): Boolean {
        // Сравниваем по ключам и значениям
        if (local.keys != server.keys) return true
        
        for (key in local.keys) {
            val localValue = local[key]
            val serverValue = server[key]
            
            if (localValue != serverValue) {
                // Проверяем timestamp - если локальная версия новее, это изменение
                if (key == "timestamp" || key == "lastModified") {
                    val localTime = (localValue as? Number)?.toLong() ?: 0
                    val serverTime = (serverValue as? Number)?.toLong() ?: 0
                    if (localTime != serverTime) return true
                } else {
                    return true
                }
            }
        }
        
        return false
    }
    
    private fun getTimestamp(data: Map<String, Any>): Long {
        return (data["timestamp"] as? Number)?.toLong() 
            ?: (data["lastModified"] as? Number)?.toLong() 
            ?: System.currentTimeMillis()
    }
    
    private fun calculateHash(data: Map<String, Any>): String {
        val json = gson.toJson(data)
        return hashManager.calculateStringHash(json) ?: ""
    }
}
```

---

#### 3.3. Резолвер конфликтов

```kotlin
// app/src/main/java/com/example/vkbookandroid/conflict/ConflictResolver.kt
package com.example.vkbookandroid.conflict

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File

/**
 * Резолвер конфликтов данных
 */
class ConflictResolver(private val context: Context) {
    
    private val tag = "ConflictResolver"
    private val gson = Gson()
    
    enum class ResolutionStrategy {
        SERVER_WINS,    // Всегда использовать серверную версию
        LOCAL_WINS,     // Всегда использовать локальную версию
        MERGE,          // Попытаться объединить изменения
        ASK_USER        // Спросить пользователя
    }
    
    /**
     * Разрешить конфликт
     */
    suspend fun resolveConflict(
        conflict: DataConflict,
        strategy: ResolutionStrategy
    ): ResolutionResult {
        return when (strategy) {
            ResolutionStrategy.SERVER_WINS -> {
                ResolutionResult(
                    success = true,
                    resolvedData = conflict.serverVersion.data,
                    message = "Использована серверная версия"
                )
            }
            
            ResolutionStrategy.LOCAL_WINS -> {
                ResolutionResult(
                    success = true,
                    resolvedData = conflict.localVersion.data,
                    message = "Использована локальная версия"
                )
            }
            
            ResolutionStrategy.MERGE -> {
                mergeVersions(conflict)
            }
            
            ResolutionStrategy.ASK_USER -> {
                // Возвращаем обе версии для показа пользователю
                ResolutionResult(
                    success = false,
                    needsUserInput = true,
                    localData = conflict.localVersion.data,
                    serverData = conflict.serverVersion.data,
                    message = "Требуется выбор пользователя"
                )
            }
        }
    }
    
    /**
     * Объединить версии
     */
    private fun mergeVersions(conflict: DataConflict): ResolutionResult {
        return try {
            val merged = mutableMapOf<String, Any>()
            
            // Берем все ключи из обеих версий
            val allKeys = (conflict.localVersion.data.keys + conflict.serverVersion.data.keys).distinct()
            
            for (key in allKeys) {
                val localValue = conflict.localVersion.data[key]
                val serverValue = conflict.serverVersion.data[key]
                
                when {
                    // Оба значения одинаковые
                    localValue == serverValue -> {
                        merged[key] = localValue!!
                    }
                    
                    // Только локальное значение
                    localValue != null && serverValue == null -> {
                        merged[key] = localValue
                    }
                    
                    // Только серверное значение
                    localValue == null && serverValue != null -> {
                        merged[key] = serverValue
                    }
                    
                    // Оба значения разные - берем более новое
                    else -> {
                        val localTime = conflict.localVersion.timestamp
                        val serverTime = conflict.serverVersion.timestamp
                        
                        if (localTime > serverTime) {
                            merged[key] = localValue!!
                        } else {
                            merged[key] = serverValue!!
                        }
                    }
                }
            }
            
            ResolutionResult(
                success = true,
                resolvedData = merged,
                message = "Версии объединены"
            )
        } catch (e: Exception) {
            Log.e(tag, "Error merging versions", e)
            ResolutionResult(
                success = false,
                message = "Ошибка объединения: ${e.message}"
            )
        }
    }
    
    data class ResolutionResult(
        val success: Boolean,
        val resolvedData: Map<String, Any>? = null,
        val localData: Map<String, Any>? = null,
        val serverData: Map<String, Any>? = null,
        val needsUserInput: Boolean = false,
        val message: String
    )
}
```

---

#### 3.4. UI для разрешения конфликтов

```kotlin
// app/src/main/java/com/example/vkbookandroid/ui/ConflictResolutionDialog.kt
class ConflictResolutionDialog(
    private val conflict: DataConflict,
    private val onResolved: (Map<String, Any>) -> Unit
) : DialogFragment() {
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_conflict_resolution, null)
        
        builder.setView(view)
        builder.setTitle("Конфликт данных")
        
        // Показываем информацию о конфликте
        view.findViewById<TextView>(R.id.tvConflictInfo).text = 
            "Обнаружен конфликт для: ${conflict.entityId}\n" +
            "Локальная версия: ${formatDate(conflict.localVersion.timestamp)}\n" +
            "Серверная версия: ${formatDate(conflict.serverVersion.timestamp)}"
        
        // Кнопки выбора
        view.findViewById<Button>(R.id.btnUseServer).setOnClickListener {
            val resolver = ConflictResolver(requireContext())
            lifecycleScope.launch {
                val result = resolver.resolveConflict(
                    conflict,
                    ConflictResolver.ResolutionStrategy.SERVER_WINS
                )
                if (result.success) {
                    onResolved(result.resolvedData!!)
                    dismiss()
                }
            }
        }
        
        view.findViewById<Button>(R.id.btnUseLocal).setOnClickListener {
            val resolver = ConflictResolver(requireContext())
            lifecycleScope.launch {
                val result = resolver.resolveConflict(
                    conflict,
                    ConflictResolver.ResolutionStrategy.LOCAL_WINS
                )
                if (result.success) {
                    onResolved(result.resolvedData!!)
                    dismiss()
                }
            }
        }
        
        view.findViewById<Button>(R.id.btnMerge).setOnClickListener {
            val resolver = ConflictResolver(requireContext())
            lifecycleScope.launch {
                val result = resolver.resolveConflict(
                    conflict,
                    ConflictResolver.ResolutionStrategy.MERGE
                )
                if (result.success) {
                    onResolved(result.resolvedData!!)
                    dismiss()
                }
            }
        }
        
        return builder.create()
    }
    
    private fun formatDate(timestamp: Long): String {
        val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date(timestamp))
    }
}
```

```xml
<!-- app/src/main/res/layout/dialog_conflict_resolution.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/tvConflictInfo"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Конфликт данных"
        android:textSize="14sp"
        android:paddingBottom="16dp" />

    <Button
        android:id="@+id/btnUseServer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Использовать серверную версию"
        android:layout_marginBottom="8dp" />

    <Button
        android:id="@+id/btnUseLocal"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Использовать локальную версию"
        android:layout_marginBottom="8dp" />

    <Button
        android:id="@+id/btnMerge"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Объединить версии" />

</LinearLayout>
```

---

#### 3.5. Интеграция в SyncService

```kotlin
// В SyncService.kt при синхронизации:

suspend fun syncArmatureCoords(result: SyncResult): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val serverData = getArmatureRepository().loadArmatureCoordsFromServer()
            if (serverData == null) {
                return@withContext false
            }
            
            val localFile = File(context.filesDir, "data/armature_coords.json")
            
            // Обнаруживаем конфликты
            val detector = ConflictDetector(context)
            val conflicts = detector.detectConflicts(
                localFile,
                gson.toJson(serverData),
                DataConflict.EntityType.ARMATURE_COORDS
            )
            
            if (conflicts.isNotEmpty()) {
                // Есть конфликты - нужно разрешить
                Log.w(tag, "Detected ${conflicts.size} conflicts")
                
                // Сохраняем конфликты для разрешения
                val conflictManager = ConflictManager(context)
                conflictManager.saveConflicts(conflicts)
                
                // Показываем диалог разрешения конфликтов (в UI потоке)
                withContext(Dispatchers.Main) {
                    showConflictResolutionDialog(conflicts) { resolvedData ->
                        // Сохраняем разрешенные данные
                        saveResolvedData(resolvedData, localFile)
                    }
                }
            } else {
                // Конфликтов нет - обычная синхронизация
                saveArmatureCoords(serverData, localFile)
            }
            
            true
        } catch (e: Exception) {
            Log.e(tag, "Error syncing armature coords", e)
            false
        }
    }
}
```

---

## 📊 Итоговая схема работы

```
┌─────────────────────────────────────────────────────────────┐
│                    ОФЛАЙН РЕЖИМ                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. Индикатор сети                                          │
│     └─> Показывает: Онлайн / Офлайн / Сервер недоступен     │
│                                                              │
│  2. Очередь изменений                                       │
│     └─> Сохраняет изменения → Синхронизирует при связи      │
│                                                              │
│  3. Конфликт-резолюшн                                       │
│     └─> Обнаруживает → Показывает диалог → Разрешает        │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

*Документ содержит полные примеры кода для интеграции в существующее приложение.*



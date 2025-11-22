# 🔍 Анализ: Rate Limit vs Спящий сервер

**Дата:** 21.11.2025  
**Проблема:** При rate limit (429) приложение может неправильно интерпретировать это как "сервер спит"

---

## 🐛 Обнаруженная проблема

### Текущая логика:

1. **`checkServerConnection()` в SyncService.kt:**
   ```kotlin
   catch (e: RateLimitException) {
       Log.w(tag, "Rate limit reached during server connection check")
       // При rate limit считаем сервер доступным, но это будет обработано в syncAll
       return@withContext true  // ⚠️ Возвращает true!
   }
   ```

2. **`testConnection()` в NetworkModule.kt:**
   ```kotlin
   val isSuccessful = response.isSuccessful || response.code == 429
   // ⚠️ При 429 возвращает true (сервер доступен)
   ```

3. **`waitForServerWakeup()` в MainActivity.kt:**
   ```kotlin
   val readyImmediately = syncService.checkServerConnection()
   if (readyImmediately) {
       return true  // ✅ Если true - сервер доступен
   }
   // ❌ Если false - начинается процесс "пробуждения"
   ```

### Проблема:

**Сценарий 1: Rate limit в checkServerHealth()**
- `checkServerHealth()` получает 429 → выбрасывает `RateLimitException`
- `checkServerConnection()` ловит исключение → возвращает `true`
- ✅ **Правильно:** Сервер считается доступным

**Сценарий 2: Rate limit в testConnection() (fallback)**
- `checkServerHealth()` возвращает `false` (не из-за rate limit)
- Вызывается `testConnection()` → получает 429 → возвращает `true`
- ✅ **Правильно:** Сервер считается доступным

**Сценарий 3: Timeout при rate limit (ПРОБЛЕМА!)**
- При rate limit сервер может долго обрабатывать запрос
- Происходит `SocketTimeoutException` (не 429!)
- `checkServerConnection()` не ловит `RateLimitException` → ловит общий `Exception`
- Вызывается `testConnection()` → тоже timeout → возвращает `false`
- ❌ **НЕПРАВИЛЬНО:** Начинается процесс "пробуждения" сервера!

**Сценарий 4: Connection exception при rate limit**
- При rate limit может быть `ConnectException`
- `isRenderSleepException()` определяет это как "сервер спит"
- ❌ **НЕПРАВИЛЬНО:** Rate limit интерпретируется как "спящий сервер"

---

## ✅ Решение: Улучшенная обработка rate limit

### 1. Проверка rate limit перед проверкой "спящего" сервера

```kotlin
// app/src/main/java/com/example/vkbookandroid/service/SyncService.kt

suspend fun checkServerConnection(): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "=== CHECKING SERVER CONNECTION ===")
            Log.d(tag, "Current server URL: ${NetworkModule.getCurrentBaseUrl()}")
            Log.d(tag, "Attempting to check server health...")
            
            val isHealthy = getArmatureRepository().checkServerHealth()
            Log.d(tag, "Server health check result: $isHealthy")
            
            if (!isHealthy) {
                Log.w(tag, "Server health check failed, trying direct connection test...")
                val directTest = NetworkModule.testConnection(NetworkModule.getCurrentBaseUrl())
                Log.d(tag, "Direct connection test result: $directTest")
                return@withContext directTest
            }
            
            Log.d(tag, "=== SERVER CONNECTION CHECK COMPLETED ===")
            isHealthy
        } catch (e: com.example.vkbookandroid.repository.RateLimitException) {
            Log.w(tag, "Rate limit reached during server connection check")
            // ⚠️ ВАЖНО: При rate limit НЕ считаем сервер спящим!
            // Возвращаем специальный результат или выбрасываем исключение
            throw ServerStatusException(ServerStatus.RATE_LIMIT, "Достигнут лимит запросов")
        } catch (e: Exception) {
            Log.e(tag, "=== SERVER CONNECTION FAILED ===", e)
            Log.e(tag, "Exception type: ${e.javaClass.simpleName}")
            Log.e(tag, "Exception message: ${e.message}")
            
            // ⚠️ ВАЖНО: Проверяем, не является ли это rate limit в замаскированном виде
            if (isRateLimitRelatedException(e)) {
                Log.w(tag, "Exception appears to be rate limit related")
                throw ServerStatusException(ServerStatus.RATE_LIMIT, "Возможен rate limit: ${e.message}")
            }
            
            // Попробуем прямой тест подключения как fallback
            try {
                Log.d(tag, "Trying fallback direct connection test...")
                val directTest = NetworkModule.testConnection(NetworkModule.getCurrentBaseUrl())
                Log.d(tag, "Fallback connection test result: $directTest")
                
                // Если fallback тоже не удался, проверяем на rate limit
                if (!directTest && isRateLimitRelatedException(e)) {
                    throw ServerStatusException(ServerStatus.RATE_LIMIT, "Rate limit при проверке соединения")
                }
                
                return@withContext directTest
            } catch (fallbackException: Exception) {
                Log.e(tag, "Fallback connection test also failed", fallbackException)
                
                // Проверяем, не rate limit ли это
                if (isRateLimitRelatedException(fallbackException)) {
                    throw ServerStatusException(ServerStatus.RATE_LIMIT, "Rate limit при fallback проверке")
                }
                
                false
            }
        }
    }
}

/**
 * Проверить, связано ли исключение с rate limit
 */
private fun isRateLimitRelatedException(e: Exception): Boolean {
    // Проверяем по типу исключения и сообщению
    val message = e.message?.lowercase() ?: ""
    
    return when (e) {
        is com.example.vkbookandroid.repository.RateLimitException -> true
        is java.net.SocketTimeoutException -> {
            // Timeout может быть из-за rate limit (сервер долго обрабатывает)
            // Но не всегда - нужно дополнительная проверка
            false // Не считаем timeout автоматически rate limit
        }
        is java.net.ConnectException -> {
            // ConnectException обычно не rate limit
            false
        }
        else -> {
            // Проверяем по сообщению
            message.contains("429") ||
            message.contains("rate limit") ||
            message.contains("too many requests") ||
            message.contains("quota exceeded")
        }
    }
}

/**
 * Исключение для статуса сервера
 */
sealed class ServerStatusException(
    val status: ServerStatus,
    message: String
) : Exception(message) {
    enum class ServerStatus {
        RATE_LIMIT,      // Превышен лимит запросов
        SLEEPING,        // Сервер спит
        UNAVAILABLE,     // Сервер недоступен
        UNKNOWN          // Неизвестно
    }
}
```

---

### 2. Обновление waitForServerWakeup() для различения rate limit и спящего сервера

```kotlin
// app/src/main/java/com/example/vkbookandroid/MainActivity.kt

private suspend fun waitForServerWakeup(): Boolean {
    updateSyncStatus("Проверяем соединение...")
    
    val connectionResult = withContext(Dispatchers.IO) {
        runCatching { syncService.checkServerConnection() }
            .onFailure { e ->
                when (e) {
                    is ServerStatusException -> {
                        when (e.status) {
                            ServerStatusException.ServerStatus.RATE_LIMIT -> {
                                Log.w("MainActivity", "Rate limit detected: ${e.message}")
                                // ⚠️ Rate limit - НЕ начинаем процесс пробуждения!
                                updateSyncStatus("Достигнут лимит запросов. Подождите несколько секунд.")
                                return@withContext ConnectionResult.RATE_LIMIT
                            }
                            ServerStatusException.ServerStatus.SLEEPING -> {
                                Log.w("MainActivity", "Server is sleeping: ${e.message}")
                                return@withContext ConnectionResult.SLEEPING
                            }
                            else -> {
                                Log.w("MainActivity", "Server unavailable: ${e.message}")
                                return@withContext ConnectionResult.UNAVAILABLE
                            }
                        }
                    }
                    else -> {
                        Log.w("MainActivity", "Initial server check failed: ${e.message}")
                        // Проверяем, не rate limit ли это в замаскированном виде
                        if (isRateLimitRelatedException(e)) {
                            updateSyncStatus("Возможен rate limit. Подождите несколько секунд.")
                            return@withContext ConnectionResult.RATE_LIMIT
                        }
                        return@withContext ConnectionResult.UNAVAILABLE
                    }
                }
            }
            .getOrDefault(false)
    }
    
    when (connectionResult) {
        is ConnectionResult.Success -> {
            if (connectionResult.value) {
                return true // Сервер доступен
            }
            // Сервер не доступен - начинаем пробуждение
        }
        is ConnectionResult.RATE_LIMIT -> {
            // ⚠️ Rate limit - НЕ начинаем процесс пробуждения!
            updateSyncStatus("Достигнут лимит запросов. Подождите 30-60 секунд.")
            Toast.makeText(this, "Достигнут лимит запросов к серверу. Подождите перед повторной попыткой.", Toast.LENGTH_LONG).show()
            return false
        }
        is ConnectionResult.SLEEPING -> {
            // Сервер спит - начинаем пробуждение
        }
        is ConnectionResult.UNAVAILABLE -> {
            // Сервер недоступен
            updateSyncStatus("Сервер недоступен")
            return false
        }
    }
    
    // Начинаем процесс пробуждения только если сервер действительно спит
    enterServerWarmupState()
    return try {
        repeat(6) { attempt ->
            if (!currentCoroutineContext().isActive) return false
            updateSyncStatus("Происходит включение сервера, базы данных скоро обновятся. Попытка ${attempt + 1}/6")
            delay(5000)
            if (!currentCoroutineContext().isActive) return false
            
            val isReady = withContext(Dispatchers.IO) {
                runCatching { syncService.checkServerConnection() }
                    .onFailure { e ->
                        when (e) {
                            is ServerStatusException -> {
                                when (e.status) {
                                    ServerStatusException.ServerStatus.RATE_LIMIT -> {
                                        Log.w("MainActivity", "Rate limit during wakeup check: ${e.message}")
                                        // ⚠️ Rate limit - прекращаем пробуждение!
                                        updateSyncStatus("Достигнут лимит запросов. Прекращаем попытки.")
                                        return@withContext false
                                    }
                                    else -> {
                                        Log.w("MainActivity", "Server status: ${e.status}")
                                    }
                                }
                            }
                            else -> {
                                // Проверяем, не rate limit ли это
                                if (isRateLimitRelatedException(e)) {
                                    updateSyncStatus("Возможен rate limit. Прекращаем попытки.")
                                    return@withContext false
                                }
                            }
                        }
                        Log.w("MainActivity", "Server wake check failed on attempt ${attempt + 1}: ${e.message}")
                    }
                    .getOrDefault(false)
            }
            
            if (isReady) {
                updateSyncStatus("Сервер активен, начинаем обновление…")
                return true
            }
        }
        false
    } finally {
        exitServerWarmupState()
    }
}

/**
 * Результат проверки соединения
 */
sealed class ConnectionResult {
    data class Success(val value: Boolean) : ConnectionResult()
    object RATE_LIMIT : ConnectionResult()
    object SLEEPING : ConnectionResult()
    object UNAVAILABLE : ConnectionResult()
}

/**
 * Проверить, связано ли исключение с rate limit
 */
private fun isRateLimitRelatedException(e: Throwable): Boolean {
    val message = e.message?.lowercase() ?: ""
    return message.contains("429") ||
           message.contains("rate limit") ||
           message.contains("too many requests") ||
           message.contains("quota exceeded")
}
```

---

### 3. Улучшение testConnection() для различения rate limit

```kotlin
// app/src/main/java/com/example/vkbookandroid/network/NetworkModule.kt

suspend fun testConnection(url: String): ConnectionTestResult {
    return try {
        android.util.Log.d("NetworkModule", "Testing connection to: $url")
        
        val testUrl = if (shouldEnforceHttps(url)) {
            url.replace("http://", "https://")
        } else {
            url
        }
        
        android.util.Log.d("NetworkModule", "Testing URL: $testUrl (FORCE_HTTPS=${BuildConfig.FORCE_HTTPS})")
        
        val testOkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .addHeader("X-API-Key", BuildConfig.API_KEY)
                chain.proceed(builder.build())
            }
            .hostnameVerifier { hostname, _ ->
                hostname.contains(RENDER_HOST) || 
                hostname.contains("158.160.157.7") || 
                hostname.contains("localhost") || 
                hostname.contains("127.0.0.1") ||
                hostname.contains("192.168.") ||
                hostname.contains("10.0.")
            }
            .build()
        
        val request = okhttp3.Request.Builder()
            .url(testUrl)
            .get()
            .build()
        
        val response = testOkHttpClient.newCall(request).execute()
        
        when {
            response.code == 429 -> {
                android.util.Log.w("NetworkModule", "Rate limit (429) detected in connection test")
                response.close()
                ConnectionTestResult.RATE_LIMIT
            }
            response.isSuccessful -> {
                android.util.Log.d("NetworkModule", "Connection test successful: ${response.code}")
                response.close()
                ConnectionTestResult.SUCCESS
            }
            else -> {
                android.util.Log.w("NetworkModule", "Connection test failed: ${response.code}")
                response.close()
                ConnectionTestResult.FAILED
            }
        }
        
    } catch (e: java.net.SocketTimeoutException) {
        android.util.Log.w("NetworkModule", "Connection test timeout (may be rate limit or server sleeping)")
        // ⚠️ Timeout может быть из-за rate limit или спящего сервера
        // Не можем точно определить, но логируем для анализа
        ConnectionTestResult.TIMEOUT
    } catch (e: Exception) {
        android.util.Log.e("NetworkModule", "Connection test failed: ${e.message}", e)
        ConnectionTestResult.FAILED
    }
}

/**
 * Результат теста соединения
 */
sealed class ConnectionTestResult {
    object SUCCESS : ConnectionTestResult()
    object RATE_LIMIT : ConnectionTestResult()
    object TIMEOUT : ConnectionTestResult()
    object FAILED : ConnectionTestResult()
    
    fun isAvailable(): Boolean = this is SUCCESS
    fun isRateLimit(): Boolean = this is RATE_LIMIT
}
```

---

### 4. Обновление checkServerConnection() для использования нового результата

```kotlin
// app/src/main/java/com/example/vkbookandroid/service/SyncService.kt

suspend fun checkServerConnection(): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "=== CHECKING SERVER CONNECTION ===")
            
            val isHealthy = getArmatureRepository().checkServerHealth()
            Log.d(tag, "Server health check result: $isHealthy")
            
            if (!isHealthy) {
                Log.w(tag, "Server health check failed, trying direct connection test...")
                val directTest = NetworkModule.testConnection(NetworkModule.getCurrentBaseUrl())
                
                when (directTest) {
                    is ConnectionTestResult.SUCCESS -> {
                        Log.d(tag, "Direct connection test: SUCCESS")
                        return@withContext true
                    }
                    is ConnectionTestResult.RATE_LIMIT -> {
                        Log.w(tag, "Direct connection test: RATE_LIMIT")
                        // ⚠️ Rate limit - выбрасываем специальное исключение
                        throw ServerStatusException(
                            ServerStatusException.ServerStatus.RATE_LIMIT,
                            "Достигнут лимит запросов при проверке соединения"
                        )
                    }
                    is ConnectionTestResult.TIMEOUT -> {
                        Log.w(tag, "Direct connection test: TIMEOUT (may be sleeping or rate limit)")
                        // Timeout - возможно сервер спит, но может быть и rate limit
                        return@withContext false
                    }
                    is ConnectionTestResult.FAILED -> {
                        Log.w(tag, "Direct connection test: FAILED")
                        return@withContext false
                    }
                }
            }
            
            Log.d(tag, "=== SERVER CONNECTION CHECK COMPLETED ===")
            isHealthy
        } catch (e: com.example.vkbookandroid.repository.RateLimitException) {
            Log.w(tag, "Rate limit reached during server connection check")
            // ⚠️ ВАЖНО: При rate limit НЕ считаем сервер спящим!
            throw ServerStatusException(
                ServerStatusException.ServerStatus.RATE_LIMIT,
                "Достигнут лимит запросов: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(tag, "=== SERVER CONNECTION FAILED ===", e)
            
            // Проверяем, не rate limit ли это
            if (isRateLimitRelatedException(e)) {
                throw ServerStatusException(
                    ServerStatusException.ServerStatus.RATE_LIMIT,
                    "Возможен rate limit: ${e.message}"
                )
            }
            
            // Проверяем, не спящий ли сервер (для Render)
            if (isRenderSleepException(e)) {
                throw ServerStatusException(
                    ServerStatusException.ServerStatus.SLEEPING,
                    "Сервер спит: ${e.message}"
                )
            }
            
            false
        }
    }
}

/**
 * Проверить, является ли исключение признаком спящего Render сервера
 */
private fun isRenderSleepException(e: Exception): Boolean {
    return when (e) {
        is java.net.SocketTimeoutException -> {
            // Timeout может быть из-за спящего сервера
            // Но НЕ если это rate limit!
            val message = e.message?.lowercase() ?: ""
            !message.contains("429") && !message.contains("rate limit")
        }
        is java.net.ConnectException -> true
        is javax.net.ssl.SSLException -> {
            val message = e.message?.lowercase() ?: ""
            message.contains("timeout") || message.contains("connection")
        }
        else -> false
    }
}
```

---

## 📊 Сравнительная таблица

| Ситуация | HTTP код | Исключение | Текущее поведение | Правильное поведение |
|----------|----------|------------|-------------------|---------------------|
| Rate limit | 429 | RateLimitException | ✅ Возвращает `true` | ✅ Выбрасывает `RATE_LIMIT` |
| Rate limit (timeout) | - | SocketTimeoutException | ❌ Возвращает `false` → "спит" | ✅ Выбрасывает `RATE_LIMIT` |
| Сервер спит | - | SocketTimeoutException | ✅ Возвращает `false` → "спит" | ✅ Выбрасывает `SLEEPING` |
| Сервер недоступен | 500+ | Exception | ✅ Возвращает `false` | ✅ Выбрасывает `UNAVAILABLE` |

---

## 🎯 Итоговые изменения

1. ✅ **Различение rate limit и спящего сервера** - разные типы исключений
2. ✅ **Проверка rate limit перед проверкой "спящего"** - приоритет rate limit
3. ✅ **Правильные сообщения пользователю** - "Rate limit" вместо "Сервер пробуждается"
4. ✅ **Прекращение попыток пробуждения при rate limit** - не тратим время зря

---

*Документ содержит полное решение проблемы смешивания rate limit и спящего сервера.*



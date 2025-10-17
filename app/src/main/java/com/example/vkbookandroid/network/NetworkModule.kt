package com.example.vkbookandroid.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Модуль для настройки сетевых компонентов
 */
object NetworkModule {
    
    // Базовый URL сервера (можно вынести в конфигурацию)
    // private const val BASE_URL = "http://localhost:8082/" // Для локального сервера
    // private const val BASE_URL = "http://10.0.2.2:8082/" // Для эмулятора
    // private const val BASE_URL = "http://192.168.1.100:8082/" // Для локальной сети
    // private const val BASE_URL = "https://your-server-domain.com/" // Для интернет-сервера
    
    // Динамический URL из настроек
    private var currentBaseUrl = "http://158.160.157.7/" // Значение по умолчанию
    
    private val okHttpClient = OkHttpClient.Builder()
        // Глобальный API Key для всех запросов
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
                .addHeader("X-API-Key", "vkbook-2024-secret-key-abc123")
            chain.proceed(builder.build())
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .addInterceptor { chain ->
            val request = chain.request()
            android.util.Log.d("NetworkModule", "Making request to: ${request.url}")
            android.util.Log.d("NetworkModule", "Request method: ${request.method}")
            android.util.Log.d("NetworkModule", "Request headers: ${request.headers}")
            
            try {
                val response = chain.proceed(request)
                android.util.Log.d("NetworkModule", "Response code: ${response.code}")
                android.util.Log.d("NetworkModule", "Response headers: ${response.headers}")
                response
            } catch (e: Exception) {
                android.util.Log.e("NetworkModule", "Request failed: ${e.message}", e)
                throw e
            }
        }
        // Гибкая проверка SSL сертификатов
        .hostnameVerifier { hostname, session ->
            try {
                android.util.Log.d("NetworkModule", "Verifying hostname: $hostname")
                
                // Список доверенных хостов для внешних серверов
                val trustedHosts = listOf(
                    "158.160.157.7", // VkBook Server на Yandex Cloud
                    "localhost",
                    "127.0.0.1",
                    "192.168.1.", // Локальная сеть (начинается с)
                    "192.168.0.", // Локальная сеть (начинается с)
                    "10.0.0.", // Локальная сеть (начинается с)
                    "172.16.", // Локальная сеть (начинается с)
                    "ngrok.io", // ngrok туннели
                    "ngrok-free.app", // ngrok бесплатные туннели
                    "localhost.run", // localhost.run туннели
                    "tunnelto.dev" // tunnelto.dev туннели
                )
                
                // Для пользовательских серверов - более мягкая проверка
                val isCustomServer = currentBaseUrl.contains("192.168.") || 
                                   currentBaseUrl.contains("10.0.") || 
                                   currentBaseUrl.contains("172.16.") ||
                                   currentBaseUrl.contains("localhost") ||
                                   currentBaseUrl.contains("127.0.0.1") ||
                                   currentBaseUrl.contains("ngrok.io") ||
                                   currentBaseUrl.contains("ngrok-free.app") ||
                                   currentBaseUrl.contains("localhost.run") ||
                                   currentBaseUrl.contains("tunnelto.dev")
                
                if (isCustomServer) {
                    android.util.Log.d("NetworkModule", "Custom server detected: $hostname - allowing connection")
                    return@hostnameVerifier true
                }
                
                // Проверяем, является ли хост доверенным
                val isTrusted = trustedHosts.any { trustedHost ->
                    if (trustedHost.endsWith(".")) {
                        // Для подсетей (например, "192.168.1.")
                        hostname.startsWith(trustedHost)
                    } else {
                        // Для точных совпадений
                        hostname.equals(trustedHost, ignoreCase = true)
                    }
                }
                
                if (isTrusted) {
                    android.util.Log.d("NetworkModule", "Trusted hostname: $hostname")
                    return@hostnameVerifier true
                }
                
                // Для остальных хостов используем стандартную проверку
                val defaultVerifier = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
                val result = defaultVerifier.verify(hostname, session)
                android.util.Log.d("NetworkModule", "Default verification for $hostname: $result")
                result
                
            } catch (e: Exception) {
                android.util.Log.w("NetworkModule", "SSL verification failed for hostname: $hostname", e)
                // В случае ошибки разрешаем подключение для локальных адресов
                hostname.contains("localhost") || hostname.contains("127.0.0.1") || hostname.contains("158.160.157.7")
            }
        }
        .build()
    
    private var retrofit = Retrofit.Builder()
        .baseUrl(currentBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private var armatureApiService: ArmatureApiService = retrofit.create(ArmatureApiService::class.java)
    
    // Gson instance для использования в других местах
    val gson = com.google.gson.Gson()
    
    /**
     * Получить API сервис
     */
    fun getArmatureApiService(): ArmatureApiService = armatureApiService
    
    /**
     * Обновить базовый URL сервера
     */
    fun updateBaseUrl(newBaseUrl: String) {
        android.util.Log.d("NetworkModule", "updateBaseUrl called. Old URL: '$currentBaseUrl', New URL: '$newBaseUrl'")
        android.util.Log.d("NetworkModule", "Device info: Model=${android.os.Build.MODEL}, Manufacturer=${android.os.Build.MANUFACTURER}, Product=${android.os.Build.PRODUCT}")
        currentBaseUrl = newBaseUrl
        retrofit = Retrofit.Builder()
            .baseUrl(currentBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        armatureApiService = retrofit.create(ArmatureApiService::class.java)
        android.util.Log.d("NetworkModule", "NetworkModule updated with new base URL: '$currentBaseUrl'")
    }
    
    /**
     * Получить текущий базовый URL
     */
    fun getCurrentBaseUrl(): String = currentBaseUrl
    
    /**
     * Получить текущий базовый URL (для доступа из других классов)
     */
    val baseUrl: String get() = currentBaseUrl
    
    /**
     * Тестировать подключение к серверу
     */
    suspend fun testConnection(url: String): Boolean {
        return try {
            android.util.Log.d("NetworkModule", "Testing connection to: $url")
            
            // Создаем специальный клиент для тестирования с обновленным hostnameVerifier
            val testOkHttpClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
                .addInterceptor { chain ->
                    val builder = chain.request().newBuilder()
                        .addHeader("X-API-Key", "vkbook-2024-secret-key-abc123")
                    val request = builder.build()
                    android.util.Log.d("NetworkModule", "Making request to: ${request.url}")
                    android.util.Log.d("NetworkModule", "Request method: ${request.method}")
                    android.util.Log.d("NetworkModule", "Request headers: ${request.headers}")
                    
                    try {
                        val response = chain.proceed(request)
                        android.util.Log.d("NetworkModule", "Response code: ${response.code}")
                        android.util.Log.d("NetworkModule", "Response headers: ${response.headers}")
                        response
                    } catch (e: Exception) {
                        android.util.Log.e("NetworkModule", "Request failed: ${e.message}", e)
                        throw e
                    }
                }
                // Специальный hostnameVerifier для тестирования
                .hostnameVerifier { hostname, session ->
                    try {
                        android.util.Log.d("NetworkModule", "Testing hostname verification for: $hostname")
                        
                        // Список доверенных хостов для тестирования
                        val trustedHosts = listOf(
                            "158.160.157.7", // VkBook Server на Yandex Cloud
                            "localhost",
                            "127.0.0.1",
                            "192.168.1.", // Локальная сеть (начинается с)
                            "192.168.0.", // Локальная сеть (начинается с)
                            "10.0.0.", // Локальная сеть (начинается с)
                            "172.16." // Локальная сеть (начинается с)
                        )
                        
                        // Для пользовательских серверов - более мягкая проверка
                        val isCustomServer = url.contains("192.168.") || 
                                           url.contains("10.0.") || 
                                           url.contains("172.16.") ||
                                           url.contains("localhost") ||
                                           url.contains("127.0.0.1")
                        
                        if (isCustomServer) {
                            android.util.Log.d("NetworkModule", "Custom server detected for testing: $hostname - allowing connection")
                            return@hostnameVerifier true
                        }
                        
                        // Проверяем, является ли хост доверенным
                        val isTrusted = trustedHosts.any { trustedHost ->
                            if (trustedHost.endsWith(".")) {
                                // Для подсетей (например, "192.168.1.")
                                hostname.startsWith(trustedHost)
                            } else {
                                // Для точных совпадений
                                hostname.equals(trustedHost, ignoreCase = true)
                            }
                        }
                        
                        if (isTrusted) {
                            android.util.Log.d("NetworkModule", "Trusted hostname for testing: $hostname")
                            return@hostnameVerifier true
                        }
                        
                        // Для остальных хостов используем стандартную проверку
                        val defaultVerifier = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
                        val result = defaultVerifier.verify(hostname, session)
                        android.util.Log.d("NetworkModule", "Default verification for testing $hostname: $result")
                        result
                        
                    } catch (e: Exception) {
                        android.util.Log.w("NetworkModule", "SSL verification failed for testing hostname: $hostname", e)
                        // В случае ошибки разрешаем подключение для локальных адресов
                        hostname.contains("localhost") || hostname.contains("127.0.0.1") || hostname.contains("192.168.") || hostname.contains("10.0.")
                    }
                }
                .build()
            
            // Попробуем простой HTTP запрос напрямую сначала
            try {
                android.util.Log.d("NetworkModule", "Trying direct HTTP request to: $url")
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = testOkHttpClient.newCall(request).execute()
                android.util.Log.d("NetworkModule", "Direct HTTP response: ${response.code} - ${response.message}")
                
                // 429 означает сервер доступен, просто rate limit
                if (response.isSuccessful || response.code == 429) {
                    android.util.Log.d("NetworkModule", "Connection test successful via direct HTTP request (code: ${response.code})")
                    response.close()
                    return true
                }
                response.close()
            } catch (e: Exception) {
                android.util.Log.d("NetworkModule", "Direct HTTP request failed", e)
            }
            
            // Если прямой запрос не сработал, пробуем через Retrofit
            val testRetrofit = Retrofit.Builder()
                .baseUrl(url)
                .client(testOkHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            
            val testService = testRetrofit.create(ArmatureApiService::class.java)
            
            // Пробуем несколько endpoints для тестирования
            try {
                // Сначала пробуем корневой endpoint
                android.util.Log.d("NetworkModule", "Trying root endpoint: $url")
                val rootResponse = testService.getRoot()
                if (rootResponse.isSuccessful || rootResponse.code() == 429) {
                    android.util.Log.d("NetworkModule", "Connection test successful via root endpoint (code: ${rootResponse.code()})")
                    return true
                }
                android.util.Log.d("NetworkModule", "Root endpoint response: ${rootResponse.code()} - ${rootResponse.message()}")
            } catch (e: Exception) {
                android.util.Log.d("NetworkModule", "Root endpoint failed, trying health endpoint", e)
            }
            
            // Если корневой не работает, пробуем health endpoint
            try {
                android.util.Log.d("NetworkModule", "Trying health endpoint: ${url}actuator/health")
                val healthResponse = testService.getHealth()
                if (healthResponse.isSuccessful || healthResponse.code() == 429) {
                    android.util.Log.d("NetworkModule", "Connection test successful via health endpoint (code: ${healthResponse.code()})")
                    return true
                }
                android.util.Log.d("NetworkModule", "Health endpoint response: ${healthResponse.code()} - ${healthResponse.message()}")
            } catch (e: Exception) {
                android.util.Log.d("NetworkModule", "Health endpoint failed, trying info endpoint", e)
            }
            
            // Если health не работает, пробуем info endpoint
            try {
                android.util.Log.d("NetworkModule", "Trying info endpoint: ${url}actuator/info")
                val infoResponse = testService.getInfo()
                if (infoResponse.isSuccessful || infoResponse.code() == 429) {
                    android.util.Log.d("NetworkModule", "Connection test successful via info endpoint (code: ${infoResponse.code()})")
                    return true
                }
                android.util.Log.d("NetworkModule", "Info endpoint response: ${infoResponse.code()} - ${infoResponse.message()}")
            } catch (e: Exception) {
                android.util.Log.d("NetworkModule", "Info endpoint failed", e)
            }
            
            // Если все endpoints не работают, но нет исключений - сервер отвечает
            android.util.Log.d("NetworkModule", "Connection test: server responds but endpoints not available")
            false
            
        } catch (e: Exception) {
            android.util.Log.e("NetworkModule", "Connection test failed: ${e.message}", e)
            false
        }
    }
}

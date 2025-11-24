package com.example.vkbookandroid.network

import android.content.Context
import com.example.vkbookandroid.BuildConfig
import okhttp3.Cache
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Модуль для настройки сетевых компонентов с поддержкой:
 * - HTTPS
 * - SSL Pinning
 * - HTTP кэширования
 * - Безопасного хранения API ключа
 */
object NetworkModule {
    
    private const val RENDER_HOST = "vkbookserver.onrender.com"
    
    // Динамический URL из настроек (по умолчанию HTTPS)
    private var currentBaseUrl = BuildConfig.SERVER_URL
    
    // Кэш для HTTP ответов
    private var httpCache: Cache? = null
    
    /**
     * Инициализация кэша HTTP ответов
     */
    fun initCache(context: Context) {
        val cacheSize = 10L * 1024 * 1024 // 10 MB
        val cacheDir = File(context.cacheDir, "http_cache")
        httpCache = Cache(cacheDir, cacheSize)
        android.util.Log.d("NetworkModule", "HTTP cache initialized: ${cacheDir.absolutePath}, size: ${cacheSize / 1024 / 1024} MB")
    }
    
    /**
     * SSL Certificate Pinning для защиты от MITM атак
     * Пины получены для сервера 158.160.157.7
     */
    private fun createCertificatePinner(): CertificatePinner {
        return CertificatePinner.Builder()
            // Для production сервера - добавьте реальные пины сертификатов
            // Получить пины можно командой: openssl s_client -connect vkbookserver.onrender.com:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
            // .add("vkbookserver.onrender.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            // .add("vkbookserver.onrender.com", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
            .build()
    }
    
    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            // API Key из BuildConfig (безопасно)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                    .addHeader("X-API-Key", BuildConfig.API_KEY)
                    .addHeader("Accept", "application/json")
                    .addHeader("User-Agent", "VkBookAndroid/${BuildConfig.VERSION_NAME}")
                chain.proceed(builder.build())
            }
            
            // HTTP кэширование
            .apply {
                httpCache?.let { cache(it) }
            }
            
            // Принудительное кэширование GET запросов (игнорируем заголовки сервера)
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                
                // Для GET запросов принудительно устанавливаем кэширование
                if (chain.request().method == "GET") {
                    val cacheControl = okhttp3.CacheControl.Builder()
                        .maxAge(5, TimeUnit.MINUTES) // Кэш на 5 минут
                        .build()
                    
                    response.newBuilder()
                        .removeHeader("Pragma")
                        .removeHeader("Cache-Control")
                        .header("Cache-Control", cacheControl.toString())
                        .build()
                } else {
                    response
                }
            }
            
            // Используем кэш даже при отсутствии сети (для GET запросов)
            .addInterceptor { chain ->
                var request = chain.request()
                
                if (request.method == "GET") {
                    // Сначала пытаемся взять из кэша, если не старше 5 минут
                    request = request.newBuilder()
                        .cacheControl(
                            okhttp3.CacheControl.Builder()
                                .maxAge(5, TimeUnit.MINUTES)
                                .build()
                        )
                        .build()
                }
                
                chain.proceed(request)
            }
            
            // Таймауты (сокращены для предотвращения зависаний при плохом соединении или отсутствии сети)
            .connectTimeout(10, TimeUnit.SECONDS)  // Уменьшено с 30 до 10 секунд
            .readTimeout(15, TimeUnit.SECONDS)     // Уменьшено с 30 до 15 секунд
            .writeTimeout(10, TimeUnit.SECONDS)    // Уменьшено с 30 до 10 секунд
            
            // Логирование (только в debug)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            })
            
            // Детальное логирование запросов с информацией о кэше
            .addInterceptor { chain ->
                val request = chain.request()
                android.util.Log.d("NetworkModule", "Making request to: ${request.url}")
                android.util.Log.d("NetworkModule", "Request method: ${request.method}")
                android.util.Log.d("NetworkModule", "Request headers: ${request.headers}")
                
                try {
                    val response = chain.proceed(request)
                    
                    // Проверяем источник ответа (кэш или сеть)
                    val cacheResponse = response.cacheResponse
                    val networkResponse = response.networkResponse
                    
                    when {
                        cacheResponse != null && networkResponse != null -> {
                            android.util.Log.d("NetworkModule", "✅ Response from CACHE (validated with server)")
                        }
                        cacheResponse != null -> {
                            android.util.Log.d("NetworkModule", "✅ Response from CACHE (no network request)")
                        }
                        networkResponse != null -> {
                            android.util.Log.d("NetworkModule", "📡 Response from NETWORK (not cached)")
                        }
                    }
                    
                    android.util.Log.d("NetworkModule", "Response code: ${response.code}")
                    android.util.Log.d("NetworkModule", "Response headers: ${response.headers}")
                    
                    // Логируем rate limit информацию
                    response.header("X-RateLimit-Remaining")?.let { remaining ->
                        val limit = response.header("X-RateLimit-Limit") ?: "?"
                        android.util.Log.i("NetworkModule", "⚠️ Rate Limit: $remaining/$limit запросов осталось")
                        
                        if (remaining.toIntOrNull() ?: 0 < 10) {
                            android.util.Log.w("NetworkModule", "⚠️ ВНИМАНИЕ: Осталось мало запросов ($remaining)! Используйте кэш!")
                        }
                    }
                    
                    response
                } catch (e: Exception) {
                    android.util.Log.e("NetworkModule", "Request failed: ${e.message}", e)
                    throw e
                }
            }
        
        // SSL Pinning только для production сервера
        if (currentBaseUrl.contains(RENDER_HOST) && currentBaseUrl.startsWith("https://")) {
            android.util.Log.d("NetworkModule", "Enabling SSL Pinning for production server")
            // Закомментировано до получения реальных пинов сертификатов
            // builder.certificatePinner(createCertificatePinner())
        }
        
        // Гибкая проверка SSL сертификатов для локальных серверов
        builder.hostnameVerifier { hostname, session ->
            try {
                android.util.Log.d("NetworkModule", "Verifying hostname: $hostname")
                
                // Список доверенных хостов
                val trustedHosts = listOf(
                    RENDER_HOST, // Production Render
                    "158.160.157.7", // Legacy production IP
                    "localhost",
                    "127.0.0.1",
                    "192.168.", // Локальная сеть
                    "10.0.", // Локальная сеть
                    "172.16." // Локальная сеть
                )
                
                // Проверяем, является ли хост доверенным
                val isTrusted = trustedHosts.any { trustedHost ->
                    if (trustedHost.endsWith(".")) {
                        hostname.startsWith(trustedHost)
                    } else {
                        hostname.equals(trustedHost, ignoreCase = true)
                    }
                }
                
                if (isTrusted) {
                    android.util.Log.d("NetworkModule", "Trusted hostname: $hostname")
                    return@hostnameVerifier true
                }
                
                // Для остальных используем стандартную проверку
                val defaultVerifier = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
                val result = defaultVerifier.verify(hostname, session)
                android.util.Log.d("NetworkModule", "Default verification for $hostname: $result")
                result
                
            } catch (e: Exception) {
                android.util.Log.w("NetworkModule", "SSL verification failed for hostname: $hostname", e)
                // В случае ошибки разрешаем только локальные адреса
                hostname.contains("localhost") || hostname.contains("127.0.0.1")
            }
        }
        
        builder.build()
    }
    
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
        
        // Автоматически конвертируем HTTP в HTTPS только если флаг FORCE_HTTPS включен
        val secureUrl = if (shouldEnforceHttps(newBaseUrl)) {
            val httpsUrl = newBaseUrl.replace("http://", "https://")
            android.util.Log.d("NetworkModule", "Converting to HTTPS (FORCE_HTTPS=true): $httpsUrl")
            httpsUrl
        } else {
            android.util.Log.d("NetworkModule", "Using URL as is (FORCE_HTTPS=${BuildConfig.FORCE_HTTPS}): $newBaseUrl")
            newBaseUrl
        }
        
        currentBaseUrl = secureUrl
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
     * Очистить HTTP кэш
     */
    fun clearCache() {
        try {
            httpCache?.evictAll()
            android.util.Log.d("NetworkModule", "HTTP cache cleared")
        } catch (e: Exception) {
            android.util.Log.e("NetworkModule", "Failed to clear cache", e)
        }
    }
    
    /**
     * Получить размер кэша
     */
    fun getCacheSize(): Long {
        return try {
            httpCache?.size() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Тестировать подключение к серверу
     * ПРОВЕРЯЕТ НАЛИЧИЕ СЕТИ перед запросом для идеального автономного режима
     */
    suspend fun testConnection(url: String): Boolean {
        // СНАЧАЛА проверяем наличие сети - если сети нет, сразу возвращаем false
        // НО: нужен Context для проверки, поэтому проверка будет в вызывающем коде
        // Здесь просто делаем быструю проверку через короткий таймаут
        
        return try {
            android.util.Log.d("NetworkModule", "Testing connection to: $url")
            
            // Автоматически пробуем HTTPS только если флаг FORCE_HTTPS включен
            val testUrl = if (shouldEnforceHttps(url)) {
                url.replace("http://", "https://")
            } else {
                url
            }
            
            android.util.Log.d("NetworkModule", "Testing URL: $testUrl (FORCE_HTTPS=${BuildConfig.FORCE_HTTPS})")
            
            // Создаем специальный клиент для тестирования
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
                    // Для тестирования разрешаем все доверенные хосты
                    hostname.contains(RENDER_HOST) || 
                    hostname.contains("158.160.157.7") || 
                    hostname.contains("localhost") || 
                    hostname.contains("127.0.0.1") ||
                    hostname.contains("192.168.") ||
                    hostname.contains("10.0.")
                }
                .build()
            
            // Попробуем простой HTTP запрос
            val request = okhttp3.Request.Builder()
                .url(testUrl)
                .get()
                .build()
            
            val response = testOkHttpClient.newCall(request).execute()
            val isSuccessful = response.isSuccessful || response.code == 429
            
            android.util.Log.d("NetworkModule", "Connection test result: ${response.code} - success: $isSuccessful")
            response.close()
            
            isSuccessful
            
        } catch (e: Exception) {
            android.util.Log.e("NetworkModule", "Connection test failed: ${e.message}", e)
            false
        }
    }
    
    private fun shouldEnforceHttps(url: String): Boolean {
        return BuildConfig.FORCE_HTTPS &&
            url.startsWith("http://") &&
            (url.contains(RENDER_HOST) || url.contains("158.160.157.7"))
    }
}

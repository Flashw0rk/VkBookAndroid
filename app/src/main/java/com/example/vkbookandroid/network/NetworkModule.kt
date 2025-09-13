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
    private var currentBaseUrl = "http://10.0.2.2:8082/" // Значение по умолчанию
    
    private val okHttpClient = OkHttpClient.Builder()
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
                
                // Список доверенных хостов для локальной разработки
                val trustedHosts = listOf(
                    "localhost",
                    "10.0.2.2", // Эмулятор Android
                    "127.0.0.1",
                    "192.168.1.", // Локальная сеть (начинается с)
                    "192.168.0.", // Локальная сеть (начинается с)
                    "10.0.0.", // Локальная сеть (начинается с)
                    "172.16.", // Локальная сеть (начинается с)
                    "your-server-domain.com" // Замените на ваш реальный домен
                )
                
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
                hostname.contains("localhost") || hostname.contains("127.0.0.1") || hostname.contains("10.0.2.2")
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
        currentBaseUrl = newBaseUrl
        retrofit = Retrofit.Builder()
            .baseUrl(currentBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        armatureApiService = retrofit.create(ArmatureApiService::class.java)
    }
    
    /**
     * Получить текущий базовый URL
     */
    fun getCurrentBaseUrl(): String = currentBaseUrl
    
    /**
     * Тестировать подключение к серверу
     */
    suspend fun testConnection(url: String): Boolean {
        return try {
            val testRetrofit = Retrofit.Builder()
                .baseUrl(url)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            
            val testService = testRetrofit.create(ArmatureApiService::class.java)
            val response = testService.getHealth()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}

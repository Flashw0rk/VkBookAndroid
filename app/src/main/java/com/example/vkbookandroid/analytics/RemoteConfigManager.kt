package com.example.vkbookandroid.analytics

import android.content.Context
import android.util.Log

/**
 * Менеджер Remote Config
 * Безопасная обёртка над Firebase Remote Config (работает даже если Firebase не подключен)
 */
object RemoteConfigManager {
    
    private const val TAG = "RemoteConfigManager"
    private var remoteConfig: Any? = null
    private var isFirebaseAvailable = false
    
    // Значения по умолчанию
    private val defaults = mapOf(
        "min_supported_version" to 1,
        "force_update_required" to false,
        "maintenance_mode" to false,
        "show_promo_banner" to false,
        "promo_message" to "",
        "max_search_results" to 1000,
        "enable_experimental_features" to false
    )
    
    /**
     * Инициализация (вызывается в Application.onCreate или MainActivity.onCreate)
     */
    fun initialize(context: Context) {
        try {
            val clazz = Class.forName("com.google.firebase.remoteconfig.FirebaseRemoteConfig")
            val method = clazz.getMethod("getInstance")
            remoteConfig = method.invoke(null)
            
            // Устанавливаем значения по умолчанию
            val setDefaultsMethod = remoteConfig?.javaClass?.getMethod("setDefaultsAsync", Map::class.java)
            setDefaultsMethod?.invoke(remoteConfig, defaults)
            
            isFirebaseAvailable = true
            Log.d(TAG, "Firebase Remote Config инициализирован")
            
            // Загружаем свежие значения в фоне
            fetchAndActivate()
        } catch (e: Exception) {
            Log.d(TAG, "Firebase Remote Config недоступен (это нормально если не подключен): ${e.message}")
            isFirebaseAvailable = false
        }
    }
    
    /**
     * Получить значение типа Boolean
     */
    fun getBoolean(key: String): Boolean {
        if (!isFirebaseAvailable) {
            return defaults[key] as? Boolean ?: false
        }
        
        return try {
            val method = remoteConfig?.javaClass?.getMethod("getBoolean", String::class.java)
            method?.invoke(remoteConfig, key) as? Boolean ?: (defaults[key] as? Boolean ?: false)
        } catch (e: Exception) {
            defaults[key] as? Boolean ?: false
        }
    }
    
    /**
     * Получить значение типа Long
     */
    fun getLong(key: String): Long {
        if (!isFirebaseAvailable) {
            return (defaults[key] as? Number)?.toLong() ?: 0L
        }
        
        return try {
            val method = remoteConfig?.javaClass?.getMethod("getLong", String::class.java)
            method?.invoke(remoteConfig, key) as? Long ?: ((defaults[key] as? Number)?.toLong() ?: 0L)
        } catch (e: Exception) {
            (defaults[key] as? Number)?.toLong() ?: 0L
        }
    }
    
    /**
     * Получить значение типа String
     */
    fun getString(key: String): String {
        if (!isFirebaseAvailable) {
            return defaults[key] as? String ?: ""
        }
        
        return try {
            val method = remoteConfig?.javaClass?.getMethod("getString", String::class.java)
            method?.invoke(remoteConfig, key) as? String ?: (defaults[key] as? String ?: "")
        } catch (e: Exception) {
            defaults[key] as? String ?: ""
        }
    }
    
    /**
     * Загрузить свежие значения с сервера и активировать их
     */
    private fun fetchAndActivate() {
        if (!isFirebaseAvailable) return
        
        try {
            val method = remoteConfig?.javaClass?.getMethod("fetchAndActivate")
            method?.invoke(remoteConfig)
            Log.d(TAG, "Remote Config обновлён")
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось обновить Remote Config: ${e.message}")
        }
    }
    
    /**
     * Проверить, требуется ли обновление приложения
     */
    fun isUpdateRequired(currentVersion: Int): Boolean {
        val minVersion = getLong("min_supported_version").toInt()
        return currentVersion < minVersion
    }
    
    /**
     * Проверить, включен ли режим обслуживания
     */
    fun isMaintenanceMode(): Boolean {
        return getBoolean("maintenance_mode")
    }
}






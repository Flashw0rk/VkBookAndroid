package com.example.vkbookandroid.analytics

import android.util.Log

/**
 * Менеджер Crashlytics
 * Безопасная обёртка над Firebase Crashlytics (работает даже если Firebase не подключен)
 */
object CrashlyticsManager {
    
    private const val TAG = "CrashlyticsManager"
    private var crashlytics: Any? = null
    private var isFirebaseAvailable = false
    
    /**
     * Инициализация (вызывается в Application.onCreate или MainActivity.onCreate)
     */
    fun initialize() {
        try {
            val clazz = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
            val method = clazz.getMethod("getInstance")
            crashlytics = method.invoke(null)
            isFirebaseAvailable = true
            Log.d(TAG, "Firebase Crashlytics инициализирован")
        } catch (e: Exception) {
            Log.d(TAG, "Firebase Crashlytics недоступен (это нормально если не подключен): ${e.message}")
            isFirebaseAvailable = false
        }
    }
    
    /**
     * Логирование не-фатальной ошибки
     */
    fun recordException(throwable: Throwable) {
        if (!isFirebaseAvailable) {
            Log.e(TAG, "Exception (Crashlytics недоступен)", throwable)
            return
        }
        
        try {
            val method = crashlytics?.javaClass?.getMethod("recordException", Throwable::class.java)
            method?.invoke(crashlytics, throwable)
            Log.d(TAG, "Exception записано в Crashlytics: ${throwable.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось записать exception: ${e.message}")
        }
    }
    
    /**
     * Установка пользовательского ID (для отслеживания конкретных пользователей)
     */
    fun setUserId(userId: String) {
        if (!isFirebaseAvailable) {
            Log.d(TAG, "User ID: $userId (Crashlytics недоступен)")
            return
        }
        
        try {
            val method = crashlytics?.javaClass?.getMethod("setUserId", String::class.java)
            method?.invoke(crashlytics, userId)
            Log.d(TAG, "User ID установлен: $userId")
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось установить User ID: ${e.message}")
        }
    }
    
    /**
     * Добавление кастомного ключа (для контекста краша)
     */
    fun setCustomKey(key: String, value: String) {
        if (!isFirebaseAvailable) {
            Log.d(TAG, "Custom key: $key = $value (Crashlytics недоступен)")
            return
        }
        
        try {
            val method = crashlytics?.javaClass?.getMethod("setCustomKey", String::class.java, String::class.java)
            method?.invoke(crashlytics, key, value)
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось установить custom key: ${e.message}")
        }
    }
    
    /**
     * Логирование сообщения (будет видно в краш-репорте)
     */
    fun log(message: String) {
        if (!isFirebaseAvailable) {
            Log.d(TAG, "Log: $message (Crashlytics недоступен)")
            return
        }
        
        try {
            val method = crashlytics?.javaClass?.getMethod("log", String::class.java)
            method?.invoke(crashlytics, message)
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось залогировать сообщение: ${e.message}")
        }
    }
}






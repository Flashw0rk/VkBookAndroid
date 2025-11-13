package com.example.vkbookandroid.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log

/**
 * Менеджер аналитики
 * Безопасная обёртка над Firebase Analytics (работает даже если Firebase не подключен)
 */
object AnalyticsManager {
    
    private const val TAG = "AnalyticsManager"
    private var firebaseAnalytics: Any? = null
    private var isFirebaseAvailable = false
    
    /**
     * Инициализация (вызывается в Application.onCreate или MainActivity.onCreate)
     */
    fun initialize(context: Context) {
        try {
            val clazz = Class.forName("com.google.firebase.analytics.FirebaseAnalytics")
            val method = clazz.getMethod("getInstance", Context::class.java)
            firebaseAnalytics = method.invoke(null, context)
            isFirebaseAvailable = true
            Log.d(TAG, "Firebase Analytics инициализирован")
        } catch (e: Exception) {
            Log.d(TAG, "Firebase Analytics недоступен (это нормально если не подключен): ${e.message}")
            isFirebaseAvailable = false
        }
    }
    
    /**
     * Логирование события открытия вкладки
     */
    fun logTabOpened(tabName: String) {
        logEvent("tab_opened", Bundle().apply {
            putString("tab_name", tabName)
        })
    }
    
    /**
     * Логирование смены темы
     */
    fun logThemeChanged(themeName: String) {
        logEvent("theme_changed", Bundle().apply {
            putString("theme_name", themeName)
        })
    }
    
    /**
     * Логирование синхронизации
     */
    fun logSyncCompleted(filesCount: Int, success: Boolean) {
        logEvent("sync_completed", Bundle().apply {
            putInt("files_count", filesCount)
            putBoolean("success", success)
        })
    }
    
    /**
     * Логирование добавления напоминания
     */
    fun logReminderAdded(ruleType: String) {
        logEvent("reminder_added", Bundle().apply {
            putString("rule_type", ruleType)
        })
    }
    
    /**
     * Логирование поиска
     */
    fun logSearchPerformed(query: String, resultsCount: Int) {
        logEvent("search_performed", Bundle().apply {
            putString("query_length", query.length.toString())
            putInt("results_count", resultsCount)
        })
    }
    
    /**
     * Универсальный метод логирования события
     */
    private fun logEvent(eventName: String, params: Bundle?) {
        if (!isFirebaseAvailable) {
            Log.d(TAG, "Event: $eventName, params: $params")
            return
        }
        
        try {
            val method = firebaseAnalytics?.javaClass?.getMethod(
                "logEvent",
                String::class.java,
                Bundle::class.java
            )
            method?.invoke(firebaseAnalytics, eventName, params)
            Log.d(TAG, "Firebase event logged: $eventName")
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось залогировать событие: ${e.message}")
        }
    }
}






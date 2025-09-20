package com.example.vkbookandroid.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Управление настройками автосинхронизации
 */
object AutoSyncSettings {
    private const val PREFS_NAME = "auto_sync_settings"
    private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
    private const val KEY_SYNC_ON_STARTUP = "sync_on_startup"
    private const val KEY_SYNC_ON_SETTINGS_CHANGE = "sync_on_settings_change"
    private const val KEY_BACKGROUND_SYNC_ENABLED = "background_sync_enabled"
    private const val KEY_SYNC_INTERVAL_HOURS = "sync_interval_hours"
    
    // Интервалы синхронизации в часах
    val AVAILABLE_INTERVALS = listOf(1, 3, 6, 12, 24)
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Включены ли автообновления вообще (мастер-переключатель)
     */
    fun isAutoSyncEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_SYNC_ENABLED, false) // По умолчанию ОТКЛЮЧЕНО
    }
    
    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enabled).apply()
    }
    
    /**
     * Синхронизация при запуске приложения
     */
    fun isSyncOnStartupEnabled(context: Context): Boolean {
        return isAutoSyncEnabled(context) && 
               getPrefs(context).getBoolean(KEY_SYNC_ON_STARTUP, false)
    }
    
    fun setSyncOnStartupEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SYNC_ON_STARTUP, enabled).apply()
    }
    
    /**
     * Синхронизация при изменении настроек сервера
     */
    fun isSyncOnSettingsChangeEnabled(context: Context): Boolean {
        return isAutoSyncEnabled(context) && 
               getPrefs(context).getBoolean(KEY_SYNC_ON_SETTINGS_CHANGE, false)
    }
    
    fun setSyncOnSettingsChangeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SYNC_ON_SETTINGS_CHANGE, enabled).apply()
    }
    
    /**
     * Фоновая синхронизация по расписанию
     */
    fun isBackgroundSyncEnabled(context: Context): Boolean {
        return isAutoSyncEnabled(context) && 
               getPrefs(context).getBoolean(KEY_BACKGROUND_SYNC_ENABLED, false)
    }
    
    fun setBackgroundSyncEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BACKGROUND_SYNC_ENABLED, enabled).apply()
    }
    
    /**
     * Интервал фоновой синхронизации в часах
     */
    fun getSyncIntervalHours(context: Context): Int {
        return getPrefs(context).getInt(KEY_SYNC_INTERVAL_HOURS, 6) // По умолчанию 6 часов
    }
    
    fun setSyncIntervalHours(context: Context, hours: Int) {
        getPrefs(context).edit().putInt(KEY_SYNC_INTERVAL_HOURS, hours).apply()
    }
    
    /**
     * Сброс всех настроек к значениям по умолчанию (все отключено)
     */
    fun resetToDefaults(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
    
    /**
     * Получить текстовое описание текущих настроек
     */
    fun getSettingsSummary(context: Context): String {
        if (!isAutoSyncEnabled(context)) {
            return "Автообновления отключены"
        }
        
        val parts = mutableListOf<String>()
        if (isSyncOnStartupEnabled(context)) parts.add("при запуске")
        if (isSyncOnSettingsChangeEnabled(context)) parts.add("при изменении настроек")
        if (isBackgroundSyncEnabled(context)) {
            parts.add("в фоне каждые ${getSyncIntervalHours(context)} ч")
        }
        
        return if (parts.isEmpty()) {
            "Автообновления включены, но все опции отключены"
        } else {
            "Автообновления: ${parts.joinToString(", ")}"
        }
    }
}


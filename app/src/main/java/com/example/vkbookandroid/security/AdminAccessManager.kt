package com.example.vkbookandroid.security

import android.content.Context

/**
 * Централизованный менеджер доступа к режиму администратора.
 * Позволяет переиспользовать одно место хранения флага доступа.
 */
object AdminAccessManager {
    private const val PREFS_NAME = "server_settings"
    private const val KEY_EDITOR_ACCESS = "editor_access_enabled"

    /**
     * Проверяет, включен ли режим администратора.
     */
    fun isAdminModeEnabled(context: Context): Boolean {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_EDITOR_ACCESS, false)
    }
}


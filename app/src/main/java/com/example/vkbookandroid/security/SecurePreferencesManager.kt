package com.example.vkbookandroid.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Менеджер для безопасного хранения чувствительных данных
 * Использует EncryptedSharedPreferences с AES256_GCM шифрованием
 */
class SecurePreferencesManager(context: Context) {
    
    companion object {
        private const val TAG = "SecurePreferencesManager"
        private const val PREFS_NAME = "vkbook_secure_prefs"
        private const val KEY_ADMIN_LOGIN = "admin_login"
        private const val KEY_ADMIN_PASSWORD = "admin_password"
        private const val KEY_ADMIN_REMEMBER = "admin_remember"
        
        @Volatile
        private var instance: SecurePreferencesManager? = null
        
        fun getInstance(context: Context): SecurePreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: SecurePreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            // Создаем MasterKey с AES256_GCM схемой
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            // Создаем EncryptedSharedPreferences
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences", e)
            // Fallback на обычные SharedPreferences в случае ошибки
            // (например, на устройствах с проблемами KeyStore)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
    
    /**
     * Сохранить учетные данные администратора
     */
    fun saveAdminCredentials(login: String, password: String, remember: Boolean) {
        try {
            encryptedPrefs.edit().apply {
                if (remember) {
                    putString(KEY_ADMIN_LOGIN, login)
                    putString(KEY_ADMIN_PASSWORD, password)
                    putBoolean(KEY_ADMIN_REMEMBER, true)
                } else {
                    remove(KEY_ADMIN_LOGIN)
                    remove(KEY_ADMIN_PASSWORD)
                    putBoolean(KEY_ADMIN_REMEMBER, false)
                }
                apply()
            }
            Log.d(TAG, "Admin credentials saved (remember=$remember)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save admin credentials", e)
        }
    }
    
    /**
     * Получить логин администратора
     */
    fun getAdminLogin(): String? {
        return try {
            encryptedPrefs.getString(KEY_ADMIN_LOGIN, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get admin login", e)
            null
        }
    }
    
    /**
     * Получить пароль администратора
     */
    fun getAdminPassword(): String? {
        return try {
            encryptedPrefs.getString(KEY_ADMIN_PASSWORD, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get admin password", e)
            null
        }
    }
    
    /**
     * Проверить, нужно ли запоминать учетные данные
     */
    fun isRememberEnabled(): Boolean {
        return try {
            encryptedPrefs.getBoolean(KEY_ADMIN_REMEMBER, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get remember flag", e)
            false
        }
    }
    
    /**
     * Очистить все сохраненные учетные данные
     */
    fun clearAdminCredentials() {
        try {
            encryptedPrefs.edit().apply {
                remove(KEY_ADMIN_LOGIN)
                remove(KEY_ADMIN_PASSWORD)
                remove(KEY_ADMIN_REMEMBER)
                apply()
            }
            Log.d(TAG, "Admin credentials cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear admin credentials", e)
        }
    }
    
    /**
     * Миграция из старых незащищенных SharedPreferences
     */
    fun migrateFromLegacyPrefs(context: Context) {
        try {
            val oldPrefs = context.getSharedPreferences("vkbook_prefs", Context.MODE_PRIVATE)
            val login = oldPrefs.getString("admin_login", null)
            val password = oldPrefs.getString("admin_password", null)
            val remember = oldPrefs.getBoolean("admin_remember", false)
            
            if (!login.isNullOrEmpty() && !password.isNullOrEmpty()) {
                // Переносим в зашифрованное хранилище
                saveAdminCredentials(login, password, remember)
                
                // Удаляем из старого хранилища
                oldPrefs.edit().apply {
                    remove("admin_login")
                    remove("admin_password")
                    remove("admin_remember")
                    apply()
                }
                
                Log.d(TAG, "Successfully migrated credentials from legacy prefs")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate from legacy prefs", e)
        }
    }
}



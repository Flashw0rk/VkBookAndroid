package com.example.vkbookandroid

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.LazyThreadSafetyMode

/**
 * Менеджер для работы с хешами файлов
 * Обеспечивает проверку целостности и подлинности файлов
 */
class FileHashManager(private val context: Context) {
    
    companion object {
        private const val TAG = "FileHashManager"
        private const val HASH_ALGORITHM = "SHA-256"
        private const val PREFS_NAME = "file_hashes"
        private const val KEY_HASH_PREFIX = "hash_"
    }
    
    private val appContext = context.applicationContext
    private val sharedPrefs by lazy(LazyThreadSafetyMode.NONE) {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Вычисляет SHA-256 хеш файла
     */
    fun calculateFileHash(file: File): String? {
        if (!file.exists() || !file.isFile) {
            Log.w(TAG, "File does not exist or is not a file: ${file.absolutePath}")
            return null
        }
        
        return try {
            val digest = MessageDigest.getInstance(HASH_ALGORITHM)
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            
            val hashBytes = digest.digest()
            bytesToHex(hashBytes)
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "Hash algorithm not available: $HASH_ALGORITHM", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating hash for file: ${file.absolutePath}", e)
            null
        }
    }
    
    /**
     * Вычисляет SHA-256 хеш потока данных
     */
    fun calculateStreamHash(inputStream: InputStream): String? {
        return try {
            val digest = MessageDigest.getInstance(HASH_ALGORITHM)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            
            val hashBytes = digest.digest()
            bytesToHex(hashBytes)
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "Hash algorithm not available: $HASH_ALGORITHM", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating stream hash", e)
            null
        }
    }
    
    /**
     * Сохраняет хеш файла в SharedPreferences
     */
    fun saveFileHash(fileName: String, hash: String) {
        try {
            sharedPrefs.edit()
                .putString("$KEY_HASH_PREFIX$fileName", hash)
                .apply()
            Log.d(TAG, "Saved hash for file: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving hash for file: $fileName", e)
        }
    }
    
    /**
     * Получает сохраненный хеш файла
     */
    fun getSavedFileHash(fileName: String): String? {
        return try {
            sharedPrefs.getString("$KEY_HASH_PREFIX$fileName", null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting saved hash for file: $fileName", e)
            null
        }
    }
    
    /**
     * Проверяет целостность файла
     * @param file Файл для проверки
     * @param expectedHash Ожидаемый хеш (если null, используется сохраненный)
     * @return true если файл целостен, false если поврежден
     */
    fun verifyFileIntegrity(file: File, expectedHash: String? = null): Boolean {
        val currentHash = calculateFileHash(file) ?: return false
        val hashToCompare = expectedHash ?: getSavedFileHash(file.name)
        
        if (hashToCompare == null) {
            Log.w(TAG, "No expected hash available for file: ${file.name}")
            // Если нет ожидаемого хеша, сохраняем текущий как эталон
            saveFileHash(file.name, currentHash)
            return true
        }
        
        val isValid = currentHash.equals(hashToCompare, ignoreCase = true)
        
        if (isValid) {
            Log.d(TAG, "File integrity verified: ${file.name}")
        } else {
            Log.w(TAG, "File integrity check failed: ${file.name}")
            Log.w(TAG, "Expected: $hashToCompare")
            Log.w(TAG, "Actual: $currentHash")
        }
        
        return isValid
    }
    
    /**
     * Проверяет целостность потока данных
     */
    fun verifyStreamIntegrity(inputStream: InputStream, expectedHash: String): Boolean {
        val currentHash = calculateStreamHash(inputStream) ?: return false
        
        val isValid = currentHash.equals(expectedHash, ignoreCase = true)
        
        if (isValid) {
            Log.d(TAG, "Stream integrity verified")
        } else {
            Log.w(TAG, "Stream integrity check failed")
            Log.w(TAG, "Expected: $expectedHash")
            Log.w(TAG, "Actual: $currentHash")
        }
        
        return isValid
    }
    
    /**
     * Обновляет хеш файла после успешной загрузки
     */
    fun updateFileHashAfterDownload(file: File) {
        val newHash = calculateFileHash(file)
        if (newHash != null) {
            saveFileHash(file.name, newHash)
            Log.i(TAG, "Updated hash for downloaded file: ${file.name}")
        }
    }
    
    /**
     * Получает список всех файлов с сохраненными хешами
     */
    fun getAllFilesWithHashes(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        
        try {
            val allPrefs = sharedPrefs.all
            for ((key, value) in allPrefs) {
                if (key.startsWith(KEY_HASH_PREFIX) && value is String) {
                    val fileName = key.removePrefix(KEY_HASH_PREFIX)
                    result[fileName] = value
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all file hashes", e)
        }
        
        return result
    }
    
    /**
     * Очищает хеш для удаленного файла
     */
    fun removeFileHash(fileName: String) {
        try {
            sharedPrefs.edit()
                .remove("$KEY_HASH_PREFIX$fileName")
                .apply()
            Log.d(TAG, "Removed hash for file: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing hash for file: $fileName", e)
        }
    }
    
    /**
     * Очищает все сохраненные хеши
     */
    fun clearAllHashes() {
        try {
            val editor = sharedPrefs.edit()
            val allPrefs = sharedPrefs.all
            for (key in allPrefs.keys) {
                if (key.startsWith(KEY_HASH_PREFIX)) {
                    editor.remove(key)
                }
            }
            editor.apply()
            Log.i(TAG, "Cleared all file hashes")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all hashes", e)
        }
    }
    
    /**
     * Преобразует массив байтов в hex строку
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val result = StringBuilder()
        for (byte in bytes) {
            result.append(String.format("%02x", byte))
        }
        return result.toString()
    }
    
    /**
     * Получает информацию о хеше для отображения пользователю
     */
    fun getHashInfo(fileName: String): String {
        val hash = getSavedFileHash(fileName)
        return if (hash != null) {
            "SHA-256: ${hash.take(16)}... (${hash.length} символов)"
        } else {
            "Хеш не найден"
        }
    }
}




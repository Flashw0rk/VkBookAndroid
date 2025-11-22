package com.example.vkbookandroid.service

import android.util.Log
import com.example.vkbookandroid.FileHashManager
import com.example.vkbookandroid.network.model.UpdateFileMetadata
import java.io.File

object FileDownloadSecurity {
    
    private const val TAG = "FileDownloadSecurity"
    
    fun verifyAndPersistFileHash(
        filename: String,
        file: File,
        hashManager: FileHashManager,
        metadata: UpdateFileMetadata? = null,
        errorCollector: MutableList<String>? = null,
        deleteCorruptedFile: Boolean = true
    ): Boolean {
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "Downloaded file is missing or empty: $filename")
            errorCollector?.add("Файл $filename отсутствует или пуст после загрузки")
            return false
        }
        
        val calculatedHash = hashManager.calculateFileHash(file)
        if (calculatedHash.isNullOrBlank()) {
            Log.e(TAG, "Failed to calculate SHA-256 for $filename")
            errorCollector?.add("Не удалось вычислить хэш для $filename")
            return false
        }
        
        val expectedHash = metadata?.hash?.takeIf { it.isNotBlank() }?.let { normalizeHash(it) }
        val normalizedCalculatedHash = normalizeHash(calculatedHash)
        if (expectedHash != null && !normalizedCalculatedHash.equals(expectedHash, ignoreCase = true)) {
            Log.e(TAG, "Hash mismatch for $filename. Expected: ${expectedHash.take(16)}..., actual: ${normalizedCalculatedHash.take(16)}...")
            errorCollector?.add("Файл $filename не прошёл проверку SHA-256")
            hashManager.removeFileHash(filename)
            if (deleteCorruptedFile) {
                val deleted = file.delete()
                Log.w(TAG, "Corrupted file $filename removed: $deleted")
            }
            return false
        }
        
        hashManager.saveFileHash(filename, normalizedCalculatedHash)
        Log.d(TAG, "File integrity verified: $filename (SHA-256: ${normalizedCalculatedHash.take(16)}...)")
        return true
    }
    
    /**
     * Нормализует хэш, убирая префикс "sha256:" если он есть
     */
    private fun normalizeHash(hash: String): String {
        return hash.trim().removePrefix("sha256:").trim()
    }
}




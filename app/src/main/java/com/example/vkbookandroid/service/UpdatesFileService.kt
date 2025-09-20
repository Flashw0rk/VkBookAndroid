package com.example.vkbookandroid.service

import android.content.Context
import android.util.Log
import com.example.vkbookandroid.network.ArmatureApiService
import com.example.vkbookandroid.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream

/**
 * Сервис для работы с файлами из папки /opt/vkbook-server/updates
 * Поддерживает автоматическое определение типа файла и размещение в соответствующих папках
 */
class UpdatesFileService(private val context: Context) {
    
    private val tag = "UpdatesFileService"
    private val apiService: ArmatureApiService = NetworkModule.getArmatureApiService()
    
    /**
     * Синхронизировать все файлы из папки updates
     */
    suspend fun syncUpdatesFiles(): UpdatesSyncResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "=== STARTING UPDATES FILES SYNC ===")
                Log.d(tag, "Server URL: ${NetworkModule.getCurrentBaseUrl()}")
                
                val result = UpdatesSyncResult()
                
                // Пробуем разные варианты получения списка файлов
                val files = getUpdatesFilesList()
                
                if (files.isEmpty()) {
                    Log.w(tag, "No files found in updates directory")
                    return@withContext result
                }
                
                Log.d(tag, "Found ${files.size} files in updates directory: $files")
                
                // Обрабатываем каждый файл
                files.forEach { filename ->
                    try {
                        val success = downloadAndPlaceFile(filename)
                        if (success) {
                            result.successfulFiles.add(filename)
                            Log.d(tag, "Successfully synced: $filename")
                        } else {
                            result.failedFiles.add(filename)
                            Log.w(tag, "Failed to sync: $filename")
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Error syncing file: $filename", e)
                        result.failedFiles.add(filename)
                    }
                }
                
                result.totalFiles = files.size
                result.overallSuccess = result.failedFiles.isEmpty()
                
                Log.d(tag, "=== UPDATES SYNC COMPLETED ===")
                Log.d(tag, "Total: ${result.totalFiles}, Success: ${result.successfulFiles.size}, Failed: ${result.failedFiles.size}")
                
                result
            } catch (e: Exception) {
                Log.e(tag, "=== UPDATES SYNC ERROR ===", e)
                UpdatesSyncResult().apply { 
                    overallSuccess = false
                    errorMessage = e.message
                }
            }
        }
    }
    
    /**
     * Получить список файлов используя РАБОЧИЙ endpoint
     */
    private suspend fun getUpdatesFilesList(): List<String> {
        // 🚀 ИСПОЛЬЗУЕМ РАБОЧИЙ ENDPOINT: /api/files/list
        try {
            Log.d(tag, "🚀 Using WORKING endpoint: /api/files/list")
            val response = apiService.getAllFiles()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.containsKey("data")) {
                    val filesData = body["data"] as? List<*>
                    if (!filesData.isNullOrEmpty()) {
                        val fileNames = mutableListOf<String>()
                        for (fileInfo in filesData) {
                            if (fileInfo is Map<*, *>) {
                                val filename = fileInfo["filename"] as? String
                                if (filename != null) {
                                    fileNames.add(filename)
                                }
                            }
                        }
                        Log.d(tag, "✅ Got ${fileNames.size} files from WORKING endpoint: $fileNames")
                        return fileNames
                    }
                }
            } else {
                Log.e(tag, "❌ Working endpoint failed: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Exception with working endpoint", e)
        }
        
        Log.w(tag, "❌ Working endpoint failed to get files list")
        return emptyList()
    }
    
    /**
     * Скачать файл и разместить в соответствующей папке
     */
    private suspend fun downloadAndPlaceFile(filename: String): Boolean {
        return try {
            Log.d(tag, "=== DOWNLOADING FILE: $filename ===")
            
            // Определяем тип файла и целевую папку
            val targetDir = getTargetDirectory(filename)
            val targetFile = File(targetDir, filename)
            
            // Создаем папку, если не существует
            if (!targetDir.exists()) {
                targetDir.mkdirs()
                Log.d(tag, "Created directory: ${targetDir.absolutePath}")
            }
            
            // Пробуем скачать из updates
            val responseBody = downloadFileFromUpdates(filename)
            
            if (responseBody != null) {
                // Сохраняем файл
                FileOutputStream(targetFile).use { outputStream ->
                    responseBody.byteStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                Log.d(tag, "File saved: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                
                // Проверяем целостность
                if (targetFile.exists() && targetFile.length() > 0) {
                    Log.d(tag, "File integrity OK: $filename")
                    true
                } else {
                    Log.e(tag, "File integrity check failed: $filename")
                    false
                }
            } else {
                Log.e(tag, "Failed to download file: $filename")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Error downloading file: $filename", e)
            false
        }
    }
    
    /**
     * Скачать файл используя РАБОЧИЙ универсальный endpoint
     */
    private suspend fun downloadFileFromUpdates(filename: String): ResponseBody? {
        return try {
            // 🚀 ИСПОЛЬЗУЕМ РАБОЧИЙ УНИВЕРСАЛЬНЫЙ ENDPOINT: /api/files/download?filename=
            Log.d(tag, "🚀 Using WORKING universal download endpoint for: $filename")
            val response = apiService.downloadFileByName(filename)
            if (response.isSuccessful) {
                Log.d(tag, "✅ Downloaded via WORKING universal endpoint: $filename")
                Log.d(tag, "Response size: ${response.body()?.contentLength()} bytes")
                return response.body()
            } else {
                Log.e(tag, "❌ Universal download failed for $filename: HTTP ${response.code()}")
                Log.e(tag, "Error message: ${response.message()}")
                return null
            }
        } catch (e: Exception) {
            Log.e(tag, "💥 Exception in universal download for $filename", e)
            null
        }
    }
    
    /**
     * Определить целевую папку для файла на основе его расширения
     */
    private fun getTargetDirectory(filename: String): File {
        val dataDir = File(context.filesDir, "data")
        
        return when {
            filename.endsWith(".pdf", ignoreCase = true) -> {
                // PDF файлы в папку data (для схем)
                dataDir
            }
            filename.endsWith(".xlsx", ignoreCase = true) -> {
                // Excel файлы в папку data
                dataDir
            }
            filename.endsWith(".json", ignoreCase = true) -> {
                // JSON файлы в папку data
                dataDir
            }
            else -> {
                // Остальные файлы в общую папку data
                dataDir
            }
        }
    }
    
    /**
     * Проверить доступность файлов через РАБОЧИЙ endpoint
     */
    suspend fun checkUpdatesAvailability(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "🚀 Checking file availability via WORKING endpoint...")
                val response = apiService.getAllFiles()
                val available = response.isSuccessful
                Log.d(tag, "✅ Files available via working endpoint: $available")
                available
            } catch (e: Exception) {
                Log.e(tag, "❌ Error checking files availability", e)
                false
            }
        }
    }
}

/**
 * Результат синхронизации файлов из папки updates
 */
data class UpdatesSyncResult(
    var totalFiles: Int = 0,
    var successfulFiles: MutableList<String> = mutableListOf(),
    var failedFiles: MutableList<String> = mutableListOf(),
    var overallSuccess: Boolean = false,
    var errorMessage: String? = null
) {
    fun getSuccessCount(): Int = successfulFiles.size
    fun getFailedCount(): Int = failedFiles.size
    
    fun getSummary(): String {
        return "Синхронизация: ${getSuccessCount()}/${totalFiles} файлов обновлено"
    }
}

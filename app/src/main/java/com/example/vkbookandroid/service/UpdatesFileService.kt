package com.example.vkbookandroid.service

import android.content.Context
import android.util.Log
import com.example.vkbookandroid.network.ArmatureApiService
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
            
            Log.d(tag, "Target directory: ${targetDir.absolutePath}")
            Log.d(tag, "Target file: ${targetFile.absolutePath}")
            
            // Создаем папку, если не существует
            if (!targetDir.exists()) {
                val created = targetDir.mkdirs()
                Log.d(tag, "Created directory: ${targetDir.absolutePath} (success=$created)")
            } else {
                Log.d(tag, "Target directory already exists")
            }
            
            // Пробуем скачать из updates
            val responseBody = downloadFileFromUpdates(filename)
            
            if (responseBody != null) {
                // Сохраняем файл
                FileOutputStream(targetFile).use { outputStream ->
                    responseBody.byteStream().use { inputStream ->
                        val bytes = inputStream.copyTo(outputStream)
                        Log.d(tag, "Wrote $bytes bytes to file")
                    }
                }
                
                Log.d(tag, "✅ File saved: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                
                // Специальное логирование для "График проверок.xlsx"
                if (filename.contains("График проверок", ignoreCase = true)) {
                    Log.d(tag, "🎯 ГРАФИК ПРОВЕРОК successfully downloaded!")
                    Log.d(tag, "   Path: ${targetFile.absolutePath}")
                    Log.d(tag, "   Size: ${targetFile.length()} bytes")
                    Log.d(tag, "   Exists: ${targetFile.exists()}")
                    Log.d(tag, "   Readable: ${targetFile.canRead()}")
                }
                
                // Проверяем целостность
                if (targetFile.exists() && targetFile.length() > 0) {
                    Log.d(tag, "✅ File integrity OK: $filename")
                    true
                } else {
                    Log.e(tag, "❌ File integrity check failed: $filename")
                    false
                }
            } else {
                Log.e(tag, "❌ Failed to download file: $filename")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "💥 Error downloading file: $filename", e)
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Скачать файл используя РАБОЧИЙ универсальный endpoint
     */
    private suspend fun downloadFileFromUpdates(filename: String): ResponseBody? {
        return try {
            Log.d(tag, "🚀 Trying multiple strategies to download: $filename")

            // Q) Безопасная сборка абсолютного URL через HttpUrl.Builder c сырым именем файла
            runCatching {
                val base = NetworkModule.getCurrentBaseUrl().trimEnd('/')
                val baseUrl = base.toHttpUrlOrNull()
                if (baseUrl != null) {
                    val built = baseUrl.newBuilder()
                        .addPathSegment("api")
                        .addPathSegment("files")
                        .addPathSegment("download")
                        .addQueryParameter("filename", filename)
                        .build()
                        .toString()
                    val r = apiService.downloadByUrl(built)
                    if (r.isSuccessful) {
                        Log.d(tag, "✅ Downloaded via HttpUrl.Builder query: $built")
                        return r.body()
                    } else {
                        Log.w(tag, "HttpUrl.Builder attempt failed: code=${r.code()}")
                    }
                } else {
                    Log.w(tag, "HttpUrl.parse returned null for base: $base")
                }
            }

            // A) Попытка по абсолютному URL, если есть в списке файлов
            runCatching {
                val list = apiService.getAllFiles()
                if (list.isSuccessful) {
                    val body = list.body()
                    val data = body?.get("data") as? List<*>
                    val match = data?.firstOrNull {
                        val name = when (it) {
                            is com.example.vkbookandroid.model.FileInfo -> it.filename
                            is Map<*, *> -> it["filename"] as? String
                            else -> null
                        }
                        name?.equals(filename, ignoreCase = false) == true
                    }
                    val url = when (match) {
                        is com.example.vkbookandroid.model.FileInfo -> null // нет поля downloadUrl в модели
                        is Map<*, *> -> (match["downloadUrl"] as? String) ?: (match["path"] as? String)
                        else -> null
                    } ?: run {
                        // Собираем по умолчанию путь скачивания через filename
                        val encoded = java.net.URLEncoder.encode(filename, Charsets.UTF_8.name()).replace("+", "%20")
                        "/api/files/download/$encoded"
                    }
                    if (!url.isNullOrBlank()) {
                        val abs = if (url.startsWith("http")) url else NetworkModule.getCurrentBaseUrl().trimEnd('/') + "/" + url.trimStart('/')
                        val r = apiService.downloadByUrl(abs)
                        if (r.isSuccessful) {
                            Log.d(tag, "✅ Downloaded via downloadUrl: $abs")
                            return r.body()
                        }
                    }
                }
            }

            // B) Вариант с path-параметром (encoded path): /api/files/download/{filename}
            runCatching {
                val encodedPath = java.net.URLEncoder.encode(filename, Charsets.UTF_8.name())
                    .replace("+", "%20") // пробелы как %20
                val r = apiService.downloadFileByPath(encodedPath)
                if (r.isSuccessful) {
                    Log.d(tag, "✅ Downloaded via path endpoint: $filename")
                    return r.body()
                }
            }

            // C) Вариант с query: сначала raw, потом строго закодированный, затем строгая ручная экранизация (' '->%20, '+'->%2B)
            runCatching {
                val r1 = apiService.downloadFileByName(filename)
                if (r1.isSuccessful) {
                    Log.d(tag, "✅ Downloaded via query raw: $filename")
                    return r1.body()
                }
                val encoded = java.net.URLEncoder.encode(filename, Charsets.UTF_8.name())
                val r2 = apiService.downloadFileByName(encoded)
                if (r2.isSuccessful) {
                    Log.d(tag, "✅ Downloaded via query encoded: $filename -> $encoded")
                    return r2.body()
                }
                val strict = filename
                    .replace(" ", "%20")
                    .replace("+", "%2B")
                val r3 = apiService.downloadFileByName(strict)
                if (r3.isSuccessful) {
                    Log.d(tag, "✅ Downloaded via query strict: $filename -> $strict")
                    return r3.body()
                }
            }

            // D) Вариант updates с encoded path
            runCatching {
                val encodedUpd = java.net.URLEncoder.encode(filename, Charsets.UTF_8.name())
                    .replace("+", "%20")
                val upd = apiService.downloadUpdatesFile(encodedUpd)
                if (upd.isSuccessful) {
                    Log.d(tag, "✅ Downloaded via updates encoded path: $filename")
                    return upd.body()
                }
                val updRaw = apiService.downloadUpdatesFile(filename)
                if (updRaw.isSuccessful) {
                    Log.d(tag, "✅ Downloaded via updates raw path: $filename")
                    return updRaw.body()
                }
            }

            Log.e(tag, "❌ All download strategies failed for: $filename")
            null
        } catch (e: Exception) {
            Log.e(tag, "💥 Exception in download for $filename", e)
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

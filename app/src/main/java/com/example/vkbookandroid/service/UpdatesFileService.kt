package com.example.vkbookandroid.service

import android.content.Context
import android.util.Log
import com.example.vkbookandroid.network.ArmatureApiService
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.example.vkbookandroid.network.NetworkModule
import com.example.vkbookandroid.network.model.UpdateFileMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonArray

/**
 * Сервис для работы с файлами из папки /opt/vkbook-server/updates
 * Поддерживает автоматическое определение типа файла и размещение в соответствующих папках
 */
class UpdatesFileService(private val context: Context) {
    
    private val tag = "UpdatesFileService"
    private val apiService: ArmatureApiService = NetworkModule.getArmatureApiService()
    private val gson = NetworkModule.gson
    
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
                
                Log.d(tag, "Found ${files.size} files in updates directory")
                
                // Обрабатываем каждый файл
                files.forEach { metadata ->
                    val filename = metadata.filename
                    try {
                        val success = downloadAndPlaceFile(metadata)
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
     * Получить список файлов используя новый endpoint /api/updates/check
     */
    private suspend fun getUpdatesFilesList(): List<UpdateFileMetadata> {
        return try {
            Log.d(tag, "🚀 Requesting /api/updates/check for metadata")
            val response = apiService.checkUpdates()
            if (!response.isSuccessful) {
                Log.e(tag, "❌ /api/updates/check failed: HTTP ${response.code()}")
                if (response.code() == 429) {
                    Log.w(tag, "⚠️ Rate limit exceeded, will retry later")
                }
                return emptyList()
            }
            val body = response.body()
            if (body == null) {
                Log.w(tag, "❌ /api/updates/check returned null body")
                return emptyList()
            }
            // Читаем body до того, как response будет закрыт автоматически
            val json = body.string()
            if (json.isBlank()) {
                Log.w(tag, "❌ /api/updates/check returned empty body")
                return emptyList()
            }
            val files = parseUpdatesCheckPayload(json)
            Log.d(tag, "✅ Parsed ${files.size} entries from /api/updates/check")
            files
        } catch (e: Exception) {
            Log.e(tag, "❌ Exception while parsing /api/updates/check", e)
            emptyList()
        }
    }
    
    /**
     * Скачать файл и разместить в соответствующей папке
     */
    private suspend fun downloadAndPlaceFile(fileMetadata: UpdateFileMetadata): Boolean {
        val filename = fileMetadata.filename
        return try {
            Log.d(tag, "=== DOWNLOADING FILE: $filename ===")
            fileMetadata.size?.let { Log.d(tag, "Reported size: $it bytes") }
            fileMetadata.hash?.let { Log.d(tag, "Reported hash: $it") }
            
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
     * Скачать файл используя новый endpoint /api/updates/download
     * Использует URLEncoder UTF-8 для корректной обработки кириллицы и спецсимволов
     */
    private suspend fun downloadFileFromUpdates(filename: String): ResponseBody? {
        return try {
            // Проверяем безопасность пути (защита от path traversal)
            if (filename.contains("..") || filename.startsWith("/")) {
                Log.e(tag, "❌ Invalid filename (security check failed): $filename")
                return null
            }
            
            // Нормализуем путь: заменяем обратные слэши на прямые
            val normalizedFilename = filename.replace("\\", "/")
            
            // Пробуем прямой вызов через Retrofit (он сам кодирует query параметры)
            val direct = apiService.downloadUpdatesFile(normalizedFilename)
            if (direct.isSuccessful) {
                Log.d(tag, "✅ Downloaded via /api/updates/download query parameter")
                return direct.body()
            }
            Log.w(tag, "Primary /api/updates/download failed: HTTP ${direct.code()}")
            
            // Fallback: явное URL-кодирование через HttpUrl.Builder
            val base = NetworkModule.getCurrentBaseUrl().trimEnd('/')
            val baseUrl = base.toHttpUrlOrNull()
            if (baseUrl != null) {
                // Используем URLEncoder UTF-8 как указано в техзадании
                val encodedFilename = java.net.URLEncoder.encode(normalizedFilename, Charsets.UTF_8.name())
                val built = baseUrl.newBuilder()
                    .addPathSegment("api")
                    .addPathSegment("updates")
                    .addPathSegment("download")
                    .addQueryParameter("filename", encodedFilename)
                    .build()
                    .toString()
                val fallback = apiService.downloadByUrl(built)
                if (fallback.isSuccessful) {
                    Log.d(tag, "✅ Downloaded via absolute URL builder with explicit encoding: $built")
                    return fallback.body()
                } else {
                    Log.w(tag, "Absolute URL builder failed: HTTP ${fallback.code()}")
                }
            } else {
                Log.w(tag, "HttpUrl.parse returned null for base: $base")
            }
            
            Log.e(tag, "❌ Download failed for: $filename")
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
     * Проверить доступность файлов через новый endpoint /api/updates/check
     */
    suspend fun checkUpdatesAvailability(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "🚀 Checking availability via /api/updates/check ...")
                val response = apiService.checkUpdates()
                val available = response.isSuccessful || response.code() == 429
                if (response.code() == 429) {
                    Log.w(tag, "⚠️ Rate limit exceeded, but endpoint is available")
                }
                Log.d(tag, "✅ /api/updates/check available: $available (HTTP ${response.code()})")
                available
            } catch (e: Exception) {
                Log.e(tag, "❌ Error checking /api/updates/check", e)
                false
            }
        }
    }

    private fun parseUpdatesCheckPayload(json: String?): List<UpdateFileMetadata> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val element = gson.fromJson(json, JsonElement::class.java) ?: return emptyList()
            when {
                element.isJsonArray -> parseMetadataArray(element.asJsonArray)
                element.isJsonObject -> parseMetadataObject(element.asJsonObject)
                else -> emptyList()
            }
        }.getOrElse {
            Log.e(tag, "Failed to parse updates payload", it)
            emptyList()
        }
    }

    private fun parseMetadataArray(array: JsonArray): List<UpdateFileMetadata> {
        return array.mapNotNull { parseMetadataElement(it) }
    }

    private fun parseMetadataObject(obj: JsonObject): List<UpdateFileMetadata> {
        val arrayKeys = listOf("data", "files", "items", "result", "updates")
        arrayKeys.forEach { key ->
            if (obj.has(key) && obj.get(key).isJsonArray) {
                return parseMetadataArray(obj.getAsJsonArray(key))
            }
        }

        if (obj.has("file") && obj.get("file").isJsonObject) {
            return listOfNotNull(parseMetadataElement(obj.getAsJsonObject("file")))
        }

        return listOfNotNull(parseMetadataElement(obj))
    }

    private fun parseMetadataElement(element: JsonElement?): UpdateFileMetadata? {
        if (element == null || !element.isJsonObject) return null
        val obj = element.asJsonObject
        val filename = when {
            obj.has("filename") -> obj.get("filename").asString
            obj.has("name") -> obj.get("name").asString
            else -> ""
        }
        if (filename.isBlank()) return null

        return UpdateFileMetadata(
            filename = filename,
            size = obj.get("size")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong,
            lastModified = obj.get("lastModified")?.asString,
            hash = obj.get("hash")?.asString,
            version = obj.get("version")?.asString,
            hasUpdates = obj.get("hasUpdates")?.asBoolean,
            etag = obj.get("etag")?.asString,
            contentType = obj.get("contentType")?.asString
        )
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

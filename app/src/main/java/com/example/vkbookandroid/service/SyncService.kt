package com.example.vkbookandroid.service

import android.content.Context
import android.util.Log
import com.example.vkbookandroid.network.NetworkModule
import com.example.vkbookandroid.repository.ArmatureRepository
import com.example.vkbookandroid.FileHashManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Сервис для синхронизации данных с сервером
 */
class SyncService(private val context: Context) {
    
    // Менеджер хешей для проверки целостности загружаемых файлов
    private val fileHashManager = FileHashManager(context)
    private val tag = "SyncService"
    
    init {
        Log.d(tag, "SyncService initialized. Current NetworkModule base URL: ${NetworkModule.baseUrl}")
    }
    
    /**
     * Получить актуальный ArmatureRepository с обновленным ApiService
     */
    private fun getArmatureRepository(): ArmatureRepository {
        Log.d(tag, "Getting ArmatureRepository with current base URL: ${NetworkModule.getCurrentBaseUrl()}")
        return ArmatureRepository(context, NetworkModule.getArmatureApiService())
    }
    
    /**
     * Проверить доступность сервера
     */
    suspend fun checkServerConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "=== CHECKING SERVER CONNECTION ===")
                Log.d(tag, "Current server URL: ${NetworkModule.getCurrentBaseUrl()}")
                Log.d(tag, "Attempting to check server health...")
                
                val isHealthy = getArmatureRepository().checkServerHealth()
                Log.d(tag, "Server health check result: $isHealthy")
                
                if (!isHealthy) {
                    Log.w(tag, "Server health check failed, trying direct connection test...")
                    val directTest = NetworkModule.testConnection(NetworkModule.getCurrentBaseUrl())
                    Log.d(tag, "Direct connection test result: $directTest")
                    return@withContext directTest
                }
                
                Log.d(tag, "=== SERVER CONNECTION CHECK COMPLETED ===")
                isHealthy
            } catch (e: Exception) {
                Log.e(tag, "=== SERVER CONNECTION FAILED ===", e)
                Log.e(tag, "Exception type: ${e.javaClass.simpleName}")
                Log.e(tag, "Exception message: ${e.message}")
                e.printStackTrace()
                
                // Попробуем прямой тест подключения как fallback
                try {
                    Log.d(tag, "Trying fallback direct connection test...")
                    val directTest = NetworkModule.testConnection(NetworkModule.getCurrentBaseUrl())
                    Log.d(tag, "Fallback connection test result: $directTest")
                    return@withContext directTest
                } catch (fallbackException: Exception) {
                    Log.e(tag, "Fallback connection test also failed", fallbackException)
                    false
                }
            }
        }
    }
    
    /**
     * Синхронизировать armature_coords.json с сервера
     */
    suspend fun syncArmatureCoords(result: SyncResult): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "=== STARTING ARMATURE COORDS SYNC ===")
                Log.d(tag, "Current server URL: ${NetworkModule.getCurrentBaseUrl()}")
                Log.d(tag, "Attempting to load armature coords from server...")
                
                val serverData = getArmatureRepository().loadArmatureCoordsFromServer()
                Log.d(tag, "Server data received: $serverData")
                
                if (serverData != null) {
                    Log.d(tag, "Server data markers count: ${serverData.size}")
                    Log.d(tag, "Server data markers: $serverData")
                    
                    // Сохраняем в filesDir (постоянное хранилище)
                    val jsonFile = File(context.filesDir, "data/armature_coords.json")
                    val json = com.google.gson.Gson().toJson(serverData)
                    Log.d(tag, "Serialized JSON: $json")
                    jsonFile.writeText(json)
                    
                    // Проверяем целостность JSON файла
                    if (jsonFile.exists() && jsonFile.length() > 0) {
                        val fileHash = fileHashManager.calculateFileHash(jsonFile)
                        if (fileHash != null) {
                            fileHashManager.saveFileHash("armature_coords.json", fileHash)
                            Log.d(tag, "JSON file integrity verified (SHA-256: ${fileHash.take(16)}...)")
                        } else {
                            Log.w(tag, "Failed to calculate hash for armature_coords.json")
                        }
                    }
                    
                    Log.d(tag, "Armature coords synced successfully")
                    result.updatedFiles.add("armature_coords.json")
                    
                    // Дополнительная диагностика для JSON файла
                    Log.d(tag, "=== JSON FILE DIAGNOSTICS ===")
                    Log.d(tag, "JSON file exists: ${jsonFile.exists()}")
                    Log.d(tag, "JSON file size: ${jsonFile.length()} bytes")
                    Log.d(tag, "JSON file path: ${jsonFile.absolutePath}")
                    Log.d(tag, "JSON content length: ${json.length}")
                    Log.d(tag, "JSON content preview: ${json.take(200)}...")
                    Log.d(tag, "=== END JSON FILE DIAGNOSTICS ===")
                    
                    true
                } else {
                    Log.w(tag, "Failed to load armature coords from server")
                    false
                }
            } catch (e: com.example.vkbookandroid.repository.RateLimitException) {
                Log.w(tag, "Rate limit reached while syncing armature coords")
                result.rateLimitReached = true
                false
            } catch (e: Exception) {
                Log.e(tag, "Error syncing armature coords", e)
                false
            }
        }
    }
    
    /**
     * Синхронизировать Excel файлы с сервера
     */
    suspend fun syncExcelFiles(result: SyncResult): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "=== STARTING EXCEL FILES SYNC ===")
                Log.d(tag, "Current server URL: ${NetworkModule.getCurrentBaseUrl()}")
                
                val excelFiles = getArmatureRepository().getExcelFilesFromServer()
                Log.d(tag, "Found ${excelFiles.size} Excel files on server: $excelFiles")
                
                if (excelFiles.isEmpty()) {
                    Log.w(tag, "WARNING: No Excel files found on server!")
                    Log.w(tag, "This could mean:")
                    Log.w(tag, "1. Server doesn't have Excel files")
                    Log.w(tag, "2. API endpoint /api/files/excel is not working")
                    Log.w(tag, "3. Server is not properly configured")
                    return@withContext false
                }
                
                // Диагностика: проверяем, есть ли нужные файлы в списке
                val hasArmatures = excelFiles.contains("Armatures.xlsx")
                val hasBschu = excelFiles.contains("Oborudovanie_BSCHU.xlsx")
                Log.d(tag, "Armatures.xlsx found in server list: $hasArmatures")
                Log.d(tag, "Oborudovanie_BSCHU.xlsx found in server list: $hasBschu")
                
                // Получаем детальную информацию о файлах с сервера (с размером и датой)
                val serverFileInfos = try {
                    val response = getArmatureRepository().getArmatureApiService().getExcelFiles()
                    if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
                } catch (e: Exception) {
                    Log.w(tag, "Could not get Excel file info from server: ${e.message}")
                    emptyList()
                }
                
                var successCount = 0
                var skippedCount = 0
                for ((index, filename) in excelFiles.withIndex()) {
                    try {
                        // Добавляем задержку между запросами для избежания Rate Limit (429)
                        if (index > 0) {
                            kotlinx.coroutines.delay(800) // 800ms между файлами
                            Log.d(tag, "Delay 800ms before next file to avoid rate limit")
                        }
                        
                        Log.d(tag, "=== PROCESSING EXCEL FILE: $filename ===")
                        
                        // Проверяем, нужно ли загружать файл (сравниваем size и lastModified)
                        val localFile = File(context.filesDir, "data/$filename")
                        val serverFileInfo = serverFileInfos.find { it.filename == filename }
                        
                        if (localFile.exists() && serverFileInfo != null) {
                            val localSize = localFile.length()
                            val serverSize = serverFileInfo.size
                            
                            // Сравниваем размер
                            if (localSize == serverSize) {
                                Log.d(tag, "File $filename unchanged (size match: $localSize bytes), skipping download")
                                skippedCount++
                                successCount++
                                continue
                            } else {
                                Log.d(tag, "File $filename changed: local=$localSize bytes, server=$serverSize bytes")
                            }
                        }
                        
                        Log.d(tag, "Requesting file from server...")
                        
                        val responseBody = getArmatureRepository().downloadExcelFile(filename)
                        if (responseBody != null) {
                            Log.d(tag, "Server response received for: $filename")
                            Log.d(tag, "Response body size: ${responseBody.contentLength()} bytes")
                            Log.d(tag, "Response content type: ${responseBody.contentType()}")
                            
                            // Сохраняем в filesDir/data/ (постоянное хранилище)
                            val dataDir = File(context.filesDir, "data")
                            dataDir.mkdirs()
                            val file = File(dataDir, filename)
                            
                            Log.d(tag, "Target file path: ${file.absolutePath}")
                            
                            // Удаляем старый файл, если он существует
                            if (file.exists()) {
                                val oldSize = file.length()
                                val deleted = file.delete()
                                Log.d(tag, "Deleted old file: ${file.absolutePath} (old size: $oldSize bytes, success: $deleted)")
                            }
                            
                            // Также удаляем старые версии файлов с разными именами
                            dataDir.listFiles()?.forEach { existingFile ->
                                if (existingFile.name.contains(filename.substringBefore('.'), ignoreCase = true) && 
                                    existingFile.name.endsWith(".xlsx", ignoreCase = true) && 
                                    existingFile != file) {
                                    val deleted = existingFile.delete()
                                    Log.d(tag, "Deleted old version: ${existingFile.name} (success: $deleted)")
                                }
                            }
                            
                            Log.d(tag, "Starting file download...")
                            val inputStream: InputStream = responseBody.byteStream()
                            val outputStream = FileOutputStream(file)
                            
                            var bytesDownloaded = 0L
                            inputStream.use { input ->
                                outputStream.use { output ->
                                    val buffer = ByteArray(8192)
                                    var bytesRead: Int
                                    while (input.read(buffer).also { bytesRead = it } != -1) {
                                        output.write(buffer, 0, bytesRead)
                                        bytesDownloaded += bytesRead
                                    }
                                }
                            }
                            
                            // Проверяем целостность загруженного файла
                            if (file.exists() && file.length() > 0) {
                                val fileHash = fileHashManager.calculateFileHash(file)
                                if (fileHash != null) {
                                    fileHashManager.saveFileHash(filename, fileHash)
                                    Log.d(tag, "File integrity verified: $filename (SHA-256: ${fileHash.take(16)}...)")
                                } else {
                                    Log.w(tag, "Failed to calculate hash for: $filename")
                                }
                            }
                            
                            Log.d(tag, "Download completed: $filename")
                            Log.d(tag, "Bytes downloaded: $bytesDownloaded")
                            Log.d(tag, "File size after download: ${file.length()} bytes")
                            Log.d(tag, "File exists: ${file.exists()}")
                            Log.d(tag, "File readable: ${file.canRead()}")
                            
                            // Проверяем целостность файла
                            if (file.length() == 0L) {
                                Log.e(tag, "ERROR: Downloaded file is empty!")
                            } else if (file.length() < 1000L) {
                                Log.w(tag, "WARNING: Downloaded file is very small (${file.length()} bytes)")
                            }
                            
                            result.updatedFiles.add(filename)
                            successCount++
                            
                            // Дополнительная диагностика для Armatures.xlsx
                            if (filename == "Armatures.xlsx") {
                                Log.d(tag, "=== ARMATURES.XLSX DETAILED DIAGNOSTICS ===")
                                Log.d(tag, "File exists: ${file.exists()}")
                                Log.d(tag, "File size: ${file.length()} bytes")
                                Log.d(tag, "File path: ${file.absolutePath}")
                                Log.d(tag, "File readable: ${file.canRead()}")
                                Log.d(tag, "File writable: ${file.canWrite()}")
                                Log.d(tag, "File last modified: ${file.lastModified()}")
                                
                                // Попробуем прочитать первые несколько байт для проверки
                                try {
                                    val firstBytes = file.inputStream().use { it.readBytes().take(10).toByteArray() }
                                    Log.d(tag, "First 10 bytes (hex): ${firstBytes.joinToString(" ") { "%02X".format(it) }}")
                                } catch (e: Exception) {
                                    Log.e(tag, "Error reading first bytes: ${e.message}")
                                }
                                
                                Log.d(tag, "=== END ARMATURES.XLSX DIAGNOSTICS ===")
                            }
                        } else {
                            Log.e(tag, "Server returned null response body for: $filename")
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Error downloading Excel file: $filename", e)
                        Log.e(tag, "Exception type: ${e.javaClass.simpleName}")
                        Log.e(tag, "Exception message: ${e.message}")
                        e.printStackTrace()
                    }
                }
                
                Log.d(tag, "=== EXCEL SYNC COMPLETED ===")
                Log.d(tag, "Total files: ${excelFiles.size}")
                Log.d(tag, "Downloaded: ${successCount - skippedCount}")
                Log.d(tag, "Skipped (unchanged): $skippedCount")
                Log.d(tag, "Updated files: ${result.updatedFiles.filter { it.endsWith(".xlsx") }}")
                
                if (successCount == 0) {
                    Log.e(tag, "ERROR: No Excel files were processed successfully!")
                    Log.e(tag, "This means either:")
                    Log.e(tag, "1. Server doesn't have the files")
                    Log.e(tag, "2. Download endpoints are not working")
                    Log.e(tag, "3. Network/connection issues")
                }
                
                successCount > 0
            } catch (e: com.example.vkbookandroid.repository.RateLimitException) {
                Log.w(tag, "Rate limit reached while syncing Excel files")
                result.rateLimitReached = true
                false
            } catch (e: Exception) {
                Log.e(tag, "=== EXCEL SYNC ERROR ===", e)
                Log.e(tag, "Exception type: ${e.javaClass.simpleName}")
                Log.e(tag, "Exception message: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Синхронизировать PDF файлы с сервера
     */
    suspend fun syncPdfFiles(result: SyncResult): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "=== STARTING PDF FILES SYNC ===")
                Log.d(tag, "Current server URL: ${NetworkModule.getCurrentBaseUrl()}")
                
                val pdfFiles = getArmatureRepository().getPdfFilesFromServer()
                Log.d(tag, "Found ${pdfFiles.size} PDF files on server: $pdfFiles")
                
                if (pdfFiles.isEmpty()) {
                    Log.w(tag, "WARNING: No PDF files found on server!")
                    Log.w(tag, "This could mean:")
                    Log.w(tag, "1. Server doesn't have PDF files")
                    Log.w(tag, "2. API endpoint /api/files/pdf is not working")
                    Log.w(tag, "3. Server is not properly configured")
                    return@withContext false
                }
                
                // Получаем детальную информацию о PDF файлах с сервера
                val serverPdfInfos = try {
                    val response = getArmatureRepository().getArmatureApiService().getPdfFiles()
                    if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
                } catch (e: Exception) {
                    Log.w(tag, "Could not get PDF file info from server: ${e.message}")
                    emptyList()
                }
                
                var successCount = 0
                var skippedCount = 0
                for ((index, filename) in pdfFiles.withIndex()) {
                    try {
                        // Добавляем задержку между запросами для избежания Rate Limit (429)
                        if (index > 0) {
                            kotlinx.coroutines.delay(800) // 800ms между файлами
                            Log.d(tag, "Delay 800ms before next file to avoid rate limit")
                        }
                        
                        Log.d(tag, "=== PROCESSING PDF FILE: $filename ===")
                        
                        // Проверяем, нужно ли загружать файл (сравниваем size)
                        val localFile = File(context.filesDir, "data/$filename")
                        val serverFileInfo = serverPdfInfos.find { it.filename == filename }
                        
                        if (localFile.exists() && serverFileInfo != null) {
                            val localSize = localFile.length()
                            val serverSize = serverFileInfo.size
                            
                            // Сравниваем размер
                            if (localSize == serverSize) {
                                Log.d(tag, "File $filename unchanged (size match: $localSize bytes), skipping download")
                                skippedCount++
                                successCount++
                                continue
                            } else {
                                Log.d(tag, "File $filename changed: local=$localSize bytes, server=$serverSize bytes")
                            }
                        }
                        
                        val responseBody = getArmatureRepository().downloadPdfFile(filename)
                        if (responseBody != null) {
                            val file = File(context.filesDir, "data/$filename")
                            file.parentFile?.mkdirs()
                            
                            val inputStream: InputStream = responseBody.byteStream()
                            val outputStream = FileOutputStream(file)
                            
                            inputStream.use { input ->
                                outputStream.use { output ->
                                    input.copyTo(output)
                                }
                            }
                            
                            Log.d(tag, "Downloaded PDF file: $filename")
                            result.updatedFiles.add(filename)
                            successCount++
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Error downloading PDF file: $filename", e)
                    }
                }
                
                Log.d(tag, "=== PDF SYNC COMPLETED ===")
                Log.d(tag, "Total files: ${pdfFiles.size}")
                Log.d(tag, "Downloaded: ${successCount - skippedCount}")
                Log.d(tag, "Skipped (unchanged): $skippedCount")
                Log.d(tag, "Updated files: ${result.updatedFiles.filter { it.endsWith(".pdf") }}")
                
                if (successCount == 0) {
                    Log.e(tag, "ERROR: No PDF files were processed successfully!")
                    Log.e(tag, "This means either:")
                    Log.e(tag, "1. Server doesn't have the files")
                    Log.e(tag, "2. Download endpoints are not working")
                    Log.e(tag, "3. Network/connection issues")
                }
                
                successCount > 0
            } catch (e: com.example.vkbookandroid.repository.RateLimitException) {
                Log.w(tag, "Rate limit reached while syncing PDF files")
                result.rateLimitReached = true
                false
            } catch (e: Exception) {
                Log.e(tag, "=== PDF SYNC ERROR ===", e)
                Log.e(tag, "Exception type: ${e.javaClass.simpleName}")
                Log.e(tag, "Exception message: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Полная синхронизация всех данных
     */
    suspend fun syncAll(onProgress: suspend (Int, String) -> Unit = { _, _ -> }): SyncResult {
        return withContext(Dispatchers.IO) {
            Log.d(tag, "=== STARTING FULL SYNC ===")
            Log.d(tag, "Server URL: ${NetworkModule.getCurrentBaseUrl()}")
            
            val result = SyncResult()
            
            // Задержка перед началом синхронизации для стабилизации соединения
            kotlinx.coroutines.delay(500)
            Log.d(tag, "Initial delay 500ms before sync to stabilize connection")
            
            // Проверяем соединение
            Log.d(tag, "Checking server connection...")
            result.serverConnected = checkServerConnection()
            if (!result.serverConnected) {
                Log.w(tag, "Server not available, skipping sync")
                return@withContext result
            }
            Log.d(tag, "Server connection: OK")
            
            // Задержка перед синхронизацией данных для избежания Rate Limit
            kotlinx.coroutines.delay(500)
            Log.d(tag, "Delay 500ms before data sync to avoid rate limit")
            
            // Синхронизируем данные
            Log.d(tag, "Starting armature coords sync...")
            onProgress(33, "Загрузка JSON")
            result.armatureCoordsSynced = syncArmatureCoords(result)
            Log.d(tag, "Armature coords sync result: ${result.armatureCoordsSynced}")
            
            // Задержка между типами файлов для избежания Rate Limit
            kotlinx.coroutines.delay(1000)
            Log.d(tag, "Delay 1s before Excel sync to avoid rate limit")
            
            Log.d(tag, "Starting Excel files sync...")
            onProgress(66, "Загрузка Excel")
            result.excelFilesSynced = syncExcelFiles(result)
            Log.d(tag, "Excel files sync result: ${result.excelFilesSynced}")
            
            // Задержка между типами файлов для избежания Rate Limit
            kotlinx.coroutines.delay(1000)
            Log.d(tag, "Delay 1s before PDF sync to avoid rate limit")
            
            Log.d(tag, "Starting PDF files sync...")
            onProgress(100, "Загрузка PDF")
            result.pdfFilesSynced = syncPdfFiles(result)
            Log.d(tag, "PDF files sync result: ${result.pdfFilesSynced}")
            
            result.overallSuccess = result.armatureCoordsSynced || result.excelFilesSynced || result.pdfFilesSynced
            
            Log.d(tag, "=== FULL SYNC COMPLETED ===")
            Log.d(tag, "Overall success: ${result.overallSuccess}")
            Log.d(tag, "Updated files count: ${result.updatedFiles.size}")
            Log.d(tag, "Updated files: ${result.updatedFiles}")
            Log.d(tag, "Sync result: $result")
            
            result
        }
    }
    
    /**
     * Результат синхронизации
     */
    data class SyncResult(
        var serverConnected: Boolean = false,
        var armatureCoordsSynced: Boolean = false,
        var excelFilesSynced: Boolean = false,
        var pdfFilesSynced: Boolean = false,
        var overallSuccess: Boolean = false,
        var updatedFiles: MutableList<String> = mutableListOf(),
        var errorMessages: MutableList<String> = mutableListOf(),
        var rateLimitReached: Boolean = false
    ) {
        override fun toString(): String {
            return "SyncResult(server=$serverConnected, coords=$armatureCoordsSynced, excel=$excelFilesSynced, pdf=$pdfFilesSynced, success=$overallSuccess, files=${updatedFiles.size})"
        }
        
        fun getUpdateSummary(): String {
            val summary = mutableListOf<String>()
            if (armatureCoordsSynced) summary.add("Координаты арматур")
            if (excelFilesSynced) summary.add("Excel файлы (${updatedFiles.filter { it.endsWith(".xlsx") }.size})")
            if (pdfFilesSynced) summary.add("PDF схемы (${updatedFiles.filter { it.endsWith(".pdf") }.size})")
            return if (summary.isNotEmpty()) "Обновлено: ${summary.joinToString(", ")}" else "Нет обновлений"
        }
    }
}


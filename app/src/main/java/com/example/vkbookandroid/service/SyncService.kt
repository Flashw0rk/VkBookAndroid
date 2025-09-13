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
    
    private val armatureRepository = ArmatureRepository(context, NetworkModule.getArmatureApiService())
    private val fileHashManager = FileHashManager(context)
    private val tag = "SyncService"
    
    /**
     * Проверить доступность сервера
     */
    suspend fun checkServerConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val isHealthy = armatureRepository.checkServerHealth()
                Log.d(tag, "Server health check: $isHealthy")
                isHealthy
            } catch (e: Exception) {
                Log.e(tag, "Server connection failed", e)
                false
            }
        }
    }
    
    /**
     * Синхронизировать armature_coords.json с сервера
     */
    suspend fun syncArmatureCoords(result: SyncResult): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "Starting armature coords sync...")
                
                val serverData = armatureRepository.loadArmatureCoordsFromServer()
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
                Log.d(tag, "Starting Excel files sync...")
                
                val excelFiles = armatureRepository.getExcelFilesFromServer()
                Log.d(tag, "Found ${excelFiles.size} Excel files on server: $excelFiles")
                
                // Диагностика: проверяем, есть ли Armatures.xlsx в списке
                val hasArmatures = excelFiles.contains("Armatures.xlsx")
                Log.d(tag, "Armatures.xlsx found in server list: $hasArmatures")
                
                var successCount = 0
                for (filename in excelFiles) {
                    try {
                        Log.d(tag, "=== DOWNLOADING EXCEL FILE: $filename ===")
                        Log.d(tag, "Requesting file from server...")
                        
                        val responseBody = armatureRepository.downloadExcelFile(filename)
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
                
                Log.d(tag, "Excel sync completed: $successCount/${excelFiles.size} files")
                successCount > 0
            } catch (e: Exception) {
                Log.e(tag, "Error syncing Excel files", e)
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
                Log.d(tag, "Starting PDF files sync...")
                
                val pdfFiles = armatureRepository.getPdfFilesFromServer()
                Log.d(tag, "Found ${pdfFiles.size} PDF files on server: $pdfFiles")
                
                var successCount = 0
                for (filename in pdfFiles) {
                    try {
                        val responseBody = armatureRepository.downloadPdfFile(filename)
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
                
                Log.d(tag, "PDF sync completed: $successCount/${pdfFiles.size} files")
                successCount > 0
            } catch (e: Exception) {
                Log.e(tag, "Error syncing PDF files", e)
                false
            }
        }
    }
    
    /**
     * Полная синхронизация всех данных
     */
    suspend fun syncAll(): SyncResult {
        return withContext(Dispatchers.IO) {
            Log.d(tag, "Starting full sync...")
            
            val result = SyncResult()
            
            // Проверяем соединение
            result.serverConnected = checkServerConnection()
            if (!result.serverConnected) {
                Log.w(tag, "Server not available, skipping sync")
                return@withContext result
            }
            
            // Синхронизируем данные
            result.armatureCoordsSynced = syncArmatureCoords(result)
            result.excelFilesSynced = syncExcelFiles(result)
            result.pdfFilesSynced = syncPdfFiles(result)
            
            result.overallSuccess = result.armatureCoordsSynced || result.excelFilesSynced || result.pdfFilesSynced
            
            Log.d(tag, "Full sync completed: $result")
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
        var errorMessages: MutableList<String> = mutableListOf()
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


package com.example.vkbookandroid.service

import android.content.Context
import android.util.Log
import com.example.vkbookandroid.network.NetworkModule
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream

/**
 * Сервис для прямого доступа к файлам на сервере
 * Пробует различные пути и методы доступа к файлам
 */
class DirectFileAccessService(private val context: Context) {
    
    private val tag = "DirectFileAccessService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    /**
     * Попробовать различные варианты доступа к файлам
     */
    suspend fun tryDifferentFileAccess(): DirectAccessResult {
        return withContext(Dispatchers.IO) {
            val result = DirectAccessResult()
            val baseUrl = NetworkModule.getCurrentBaseUrl()
            
            Log.d(tag, "=== TESTING DIFFERENT FILE ACCESS METHODS ===")
            Log.d(tag, "Base URL: $baseUrl")
            
            // Список возможных путей к файлам
            val possiblePaths = listOf(
                // Прямые пути к updates
                "updates/",
                "opt/vkbook-server/updates/",
                "vkbook-server/updates/",
                "api/updates/",
                "files/updates/",
                
                // Статические файлы
                "static/",
                "static/files/",
                "static/updates/",
                
                // Файловые endpoints
                "files/",
                "download/",
                "assets/",
                
                // Прямой доступ к файлам
                "",
                "data/",
                "public/"
            )
            
            // Список файлов для тестирования
            val testFiles = listOf(
                "armature_coords.json",
                "Armatures.xlsx", 
                "Oborudovanie_BSCHU.xlsx",
                "test.pdf",
                "example.pdf"
            )
            
            for (path in possiblePaths) {
                Log.d(tag, "Testing path: $path")
                
                for (file in testFiles) {
                    val fullUrl = buildEncodedUrl(baseUrl, path, file)
                    
                    try {
                        val request = Request.Builder()
                            .url(fullUrl)
                            .get()
                            .build()
                        
                        val response = client.newCall(request).execute()
                        
                        Log.d(tag, "URL: $fullUrl")
                        Log.d(tag, "Response: ${response.code} ${response.message}")
                        
                        if (response.isSuccessful) {
                            val contentLength = response.body?.contentLength() ?: 0
                            Log.d(tag, "SUCCESS! File found: $file at path: $path (${contentLength} bytes)")
                            
                            result.workingPaths[file] = path
                            result.successfulUrls[file] = fullUrl
                            
                            // Пробуем скачать небольшую часть для проверки
                            if (contentLength > 0) {
                                result.fileSizes[file] = contentLength
                            }
                        }
                        
                        response.close()
                    } catch (e: Exception) {
                        Log.v(tag, "Failed: $fullUrl - ${e.message}")
                    }
                }
            }
            
            // Тестируем также directory listing endpoints
            testDirectoryListings(baseUrl, result)
            
            Log.d(tag, "=== TESTING COMPLETED ===")
            Log.d(tag, "Working paths found: ${result.workingPaths}")
            Log.d(tag, "Successful URLs: ${result.successfulUrls}")
            
            result
        }
    }
    
    /**
     * Тестировать endpoints для получения списков файлов
     */
    private suspend fun testDirectoryListings(baseUrl: String, result: DirectAccessResult) {
        val listingEndpoints = listOf(
            "api/updates/list",
            "api/updates/files",
            "api/files/list",
            "updates/list",
            "files/list",
            "list",
            "api/directory",
            "directory"
        )
        
        for (endpoint in listingEndpoints) {
            try {
                val fullUrl = "${baseUrl.trimEnd('/')}/$endpoint"
                val request = Request.Builder()
                    .url(fullUrl)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                
                Log.d(tag, "Directory listing URL: $fullUrl")
                Log.d(tag, "Response: ${response.code} ${response.message}")
                
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    Log.d(tag, "SUCCESS! Directory listing endpoint: $endpoint")
                    Log.d(tag, "Response body: $body")
                    
                    result.workingListingEndpoints[endpoint] = fullUrl
                    if (body != null) {
                        result.listingResponses[endpoint] = body
                    }
                }
                
                response.close()
            } catch (e: Exception) {
                Log.v(tag, "Directory listing failed: $endpoint - ${e.message}")
            }
        }
    }
    
    /**
     * Скачать файл по найденному рабочему пути
     */
    suspend fun downloadFileByWorkingPath(filename: String, workingPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = NetworkModule.getCurrentBaseUrl()
                val fullUrl = buildEncodedUrl(baseUrl, workingPath, filename)
                
                Log.d(tag, "Downloading file: $filename from: $fullUrl")
                
                val request = Request.Builder()
                    .url(fullUrl)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body
                    if (responseBody != null) {
                        // Определяем целевую папку
                        val targetDir = getTargetDirectory(filename)
                        if (!targetDir.exists()) {
                            targetDir.mkdirs()
                        }
                        
                        val targetFile = File(targetDir, filename)
                        
                        // Сохраняем файл
                        FileOutputStream(targetFile).use { outputStream ->
                            responseBody.byteStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        
                        Log.d(tag, "File downloaded successfully: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                        response.close()
                        return@withContext true
                    }
                }
                
                response.close()
                Log.w(tag, "Failed to download file: $filename (${response.code} ${response.message})")
                false
            } catch (e: Exception) {
                Log.e(tag, "Error downloading file: $filename", e)
                false
            }
        }
    }
    
    /**
     * Определить целевую папку для файла
     */
    private fun getTargetDirectory(filename: String): File {
        val dataDir = File(context.filesDir, "data")
        
        return when {
            filename.endsWith(".pdf", ignoreCase = true) -> dataDir
            filename.endsWith(".xlsx", ignoreCase = true) -> dataDir
            filename.endsWith(".json", ignoreCase = true) -> dataDir
            else -> dataDir
        }
    }
    
    /**
     * Построить корректно закодированный URL из базового URL, относительного пути и имени файла.
     * Использует addPathSegment, чтобы символ '+' был закодирован как %2B, пробел как %20 и т.д.
     */
    private fun buildEncodedUrl(baseUrl: String, relativePath: String, fileName: String?): String {
        val base = baseUrl.trimEnd('/').toHttpUrl()
        val builder = base.newBuilder()
        val trimmedPath = relativePath.trim('/').trim()
        if (trimmedPath.isNotEmpty()) {
            trimmedPath.split('/')
                .filter { it.isNotBlank() }
                .forEach { segment -> builder.addPathSegment(segment) }
        }
        if (!fileName.isNullOrBlank()) {
            builder.addPathSegment(fileName)
        }
        return builder.build().toString()
    }
}

/**
 * Результат тестирования прямого доступа к файлам
 */
data class DirectAccessResult(
    val workingPaths: MutableMap<String, String> = mutableMapOf(),
    val successfulUrls: MutableMap<String, String> = mutableMapOf(),
    val fileSizes: MutableMap<String, Long> = mutableMapOf(),
    val workingListingEndpoints: MutableMap<String, String> = mutableMapOf(),
    val listingResponses: MutableMap<String, String> = mutableMapOf()
) {
    fun hasWorkingPaths(): Boolean = workingPaths.isNotEmpty()
    fun hasListingEndpoints(): Boolean = workingListingEndpoints.isNotEmpty()
    
    fun getBestPathForFile(filename: String): String? = workingPaths[filename]
    fun getBestListingEndpoint(): String? = workingListingEndpoints.keys.firstOrNull()
}



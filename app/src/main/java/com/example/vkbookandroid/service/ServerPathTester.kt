package com.example.vkbookandroid.service

import android.content.Context
import android.util.Log
import com.example.vkbookandroid.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Тестировщик различных путей на сервере для поиска файлов
 */
class ServerPathTester(private val context: Context) {
    
    private val tag = "ServerPathTester"
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    /**
     * Протестировать все возможные пути к файлам updates
     */
    suspend fun testAllUpdatesPaths(): List<TestResult> {
        return withContext(Dispatchers.IO) {
            val baseUrl = NetworkModule.getCurrentBaseUrl().trimEnd('/')
            val results = mutableListOf<TestResult>()
            
            Log.d(tag, "=== TESTING ALL POSSIBLE UPDATES PATHS ===")
            Log.d(tag, "Base URL: $baseUrl")
            
            // Возможные пути к папке updates
            val updatesPaths = listOf(
                "updates",
                "opt/vkbook-server/updates", 
                "vkbook-server/updates",
                "api/updates",
                "api/files/updates",
                "files/updates",
                "static/updates",
                "public/updates",
                "data/updates",
                "assets/updates"
            )
            
            // Возможные методы доступа
            val accessMethods = listOf(
                "",  // Прямой доступ к файлам
                "list",  // Получение списка
                "files", // Список файлов
                "download", // Скачивание
                "get"    // Получение
            )
            
            // Тестируем каждую комбинацию
            for (updatesPath in updatesPaths) {
                for (method in accessMethods) {
                    val fullPath = if (method.isEmpty()) {
                        updatesPath
                    } else {
                        "$updatesPath/$method"
                    }
                    
                    val testUrl = "$baseUrl/$fullPath"
                    val result = testUrl(testUrl, fullPath)
                    results.add(result)
                    
                    if (result.isSuccessful) {
                        Log.d(tag, "✅ SUCCESS: $testUrl")
                        Log.d(tag, "   Response: ${result.responseCode} - ${result.contentType}")
                        Log.d(tag, "   Content length: ${result.contentLength}")
                        
                        if (result.responseBody.isNotEmpty()) {
                            Log.d(tag, "   Body preview: ${result.responseBody.take(200)}...")
                        }
                    }
                }
            }
            
            // Также тестируем прямой доступ к конкретным файлам
            testDirectFileAccess(baseUrl, results)
            
            Log.d(tag, "=== TESTING COMPLETED ===")
            Log.d(tag, "Total tests: ${results.size}")
            Log.d(tag, "Successful: ${results.count { it.isSuccessful }}")
            
            results.filter { it.isSuccessful }
        }
    }
    
    /**
     * Тестировать прямой доступ к файлам
     */
    private suspend fun testDirectFileAccess(baseUrl: String, results: MutableList<TestResult>) {
        val commonFilenames = listOf(
            "armature_coords.json",
            "Armatures.xlsx",
            "Oborudovanie_BSCHU.xlsx"
        )
        
        val directPaths = listOf(
            "updates",
            "opt/vkbook-server/updates",
            "static",
            "files",
            ""
        )
        
        for (path in directPaths) {
            for (filename in commonFilenames) {
                val fullPath = if (path.isEmpty()) {
                    filename
                } else {
                    "$path/$filename"
                }
                
                val testUrl = "$baseUrl/$fullPath"
                val result = testUrl(testUrl, fullPath, true)
                results.add(result)
                
                if (result.isSuccessful) {
                    Log.d(tag, "✅ DIRECT FILE ACCESS: $testUrl")
                    Log.d(tag, "   File: $filename, Size: ${result.contentLength} bytes")
                }
            }
        }
    }
    
    /**
     * Тестировать конкретный URL
     */
    private suspend fun testUrl(url: String, path: String, isFileAccess: Boolean = false): TestResult {
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "*/*")
                .build()
            
            val response = client.newCall(request).execute()
            
            val result = TestResult(
                url = url,
                path = path,
                isFileAccess = isFileAccess,
                responseCode = response.code,
                isSuccessful = response.isSuccessful,
                contentType = response.header("Content-Type") ?: "",
                contentLength = response.body?.contentLength() ?: 0,
                responseBody = if (response.isSuccessful && !isFileAccess) {
                    response.body?.string()?.take(500) ?: ""
                } else {
                    ""
                }
            )
            
            response.close()
            result
        } catch (e: Exception) {
            TestResult(
                url = url,
                path = path,
                isFileAccess = isFileAccess,
                responseCode = 0,
                isSuccessful = false,
                error = e.message
            )
        }
    }
}

/**
 * Результат тестирования URL
 */
data class TestResult(
    val url: String,
    val path: String,
    val isFileAccess: Boolean = false,
    val responseCode: Int = 0,
    val isSuccessful: Boolean = false,
    val contentType: String = "",
    val contentLength: Long = 0,
    val responseBody: String = "",
    val error: String? = null
) {
    fun isJsonResponse(): Boolean = contentType.contains("json", ignoreCase = true)
    fun isFileResponse(): Boolean = contentLength > 0 && (contentType.contains("application") || contentType.contains("octet-stream"))
}



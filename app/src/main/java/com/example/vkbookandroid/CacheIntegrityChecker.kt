package com.example.vkbookandroid

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.zip.GZIPInputStream
import java.io.FileInputStream
import kotlin.math.min

/**
 * Проверка целостности и полноты кэша Excel
 */
class CacheIntegrityChecker(private val context: android.content.Context) {
    
    private val gson = Gson()
    
    /**
     * Проверяет целостность и полноту кэша
     */
    fun validateCache(cacheDir: File): CacheStatus {
        // Проверка 1: Существование файлов
        val headersFile = File(cacheDir, "headers.json")
        val pagesDir = File(cacheDir, "pages")
        
        if (!headersFile.exists() || !pagesDir.exists()) {
            Log.w("CacheIntegrityChecker", "Cache files missing in ${cacheDir.name}")
            return CacheStatus.Missing
        }
        
        // Проверка 2: Валидность JSON файлов
        val pageFiles = pagesDir.listFiles { f ->
            (f.name.startsWith("page_") && f.name.endsWith(".json.gz")) ||
            (f.name.startsWith("page_") && f.name.endsWith(".json"))
        }?.sortedBy { it.name } ?: emptyList()
        
        if (pageFiles.isEmpty()) {
            Log.w("CacheIntegrityChecker", "No page files found in ${cacheDir.name}")
            return CacheStatus.Empty
        }
        
        var corruptedPages = 0
        var totalRows = 0
        var checkedPages = 0
        
        // Проверяем несколько случайных страниц (максимум 5 для скорости)
        val pagesToCheck = pageFiles.take(min(5, pageFiles.size))
        
        for (pageFile in pagesToCheck) {
            try {
                val rows = if (pageFile.name.endsWith(".gz")) {
                    readCompressedPage(pageFile)
                } else {
                    val json = pageFile.readText()
                    gson.fromJson(json, object : TypeToken<List<Map<String, String>>>() {}.type)
                }
                totalRows += rows.size
                checkedPages++
            } catch (e: Exception) {
                corruptedPages++
                Log.w("CacheIntegrityChecker", "Corrupted page: ${pageFile.name}", e)
            }
        }
        
        // Если >20% страниц повреждено → кэш невалиден
        if (corruptedPages > pagesToCheck.size * 0.2) {
            Log.e("CacheIntegrityChecker", "Cache corrupted: $corruptedPages/$pagesToCheck.size pages")
            return CacheStatus.Corrupted(corruptedPages, pagesToCheck.size)
        }
        
        // Подсчитываем общее количество строк во всех страницах
        var totalCachedRows = 0
        for (pageFile in pageFiles) {
            try {
                val rows = if (pageFile.name.endsWith(".gz")) {
                    readCompressedPage(pageFile)
                } else {
                    val json = pageFile.readText()
                    gson.fromJson(json, object : TypeToken<List<Map<String, String>>>() {}.type)
                }
                totalCachedRows += rows.size
            } catch (e: Exception) {
                // Пропускаем поврежденные страницы при подсчете
            }
        }
        
        return CacheStatus.Valid(totalCachedRows)
    }
    
    /**
     * Читает сжатую страницу из GZIP файла
     */
    private fun readCompressedPage(pageFile: File): List<Map<String, String>> {
        FileInputStream(pageFile).use { fis ->
            GZIPInputStream(fis).use { gzis ->
                val json = gzis.readBytes().toString(Charsets.UTF_8)
                return gson.fromJson(json, object : TypeToken<List<Map<String, String>>>() {}.type)
            }
        }
    }
    
    /**
     * Статус кэша
     */
    sealed class CacheStatus {
        object Missing : CacheStatus()
        object Empty : CacheStatus()
        data class Corrupted(val corruptedPages: Int, val totalPages: Int) : CacheStatus()
        data class Valid(val rowCount: Int) : CacheStatus()
    }
}


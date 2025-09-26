package com.example.vkbookandroid

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.example.pult.RowDataDynamic
import java.io.File

/**
 * Сессия поверх файлового кэша Excel (вариант 2).
 */
class CachedExcelPagingSession(private val datasetDir: File) : PagingSession {
    private val gson = Gson()
    private val cachedHeaders: List<String> by lazy {
        val headersFile = File(datasetDir, "headers.json")
        if (!headersFile.exists()) {
            throw IllegalStateException("Headers file does not exist: ${headersFile.absolutePath}")
        }
        val json = headersFile.readText()
        gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
    }

    private val widths: Map<String, Int> by lazy {
        val file = File(datasetDir, "widths.json")
        return@lazy try {
            if (!file.exists()) emptyMap() else gson.fromJson(file.readText(), object : TypeToken<Map<String, Int>>() {}.type)
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    override fun getHeaders(): List<String> = cachedHeaders

    override fun getColumnWidths(): Map<String, Int> = widths

    override fun readRange(startRow: Int, rowCount: Int): List<RowDataDynamic> {
        val pages = File(datasetDir, "pages")
        val result = ArrayList<RowDataDynamic>()
        // Страницы именованы page_00000.json, page_00001.json, ...
        val pageFiles = pages.listFiles { f -> f.name.startsWith("page_") && f.name.endsWith(".json") }?.sortedBy { it.name } ?: emptyList()
        if (pageFiles.isEmpty()) return emptyList()

        var skipped = 0
        for (pf in pageFiles) {
            val rows: List<Map<String, String>> = gson.fromJson(pf.readText(), object : TypeToken<List<Map<String, String>>>() {}.type)
            val remainingSkip = startRow - skipped
            if (remainingSkip >= rows.size) {
                skipped += rows.size
                continue
            }
            val startInPage = if (remainingSkip > 0) remainingSkip else 0
            var idx = startInPage
            while (idx < rows.size && result.size < rowCount) {
                result.add(RowDataDynamic(LinkedHashMap(rows[idx])))
                idx++
            }
            skipped += rows.size
            if (result.size >= rowCount) break
        }
        return result
    }

    override fun close() {
        // Нечего закрывать
    }
}



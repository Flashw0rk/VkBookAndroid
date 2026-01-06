package com.example.vkbookandroid

import android.content.Context
import java.io.InputStream

class ExcelRepository(
    private val context: Context,
    private val fileProvider: IFileProvider
) {
    private val cache by lazy { ExcelCacheManager(context) }

    private val PATH_BSCHU = "Oborudovanie_BSCHU.xlsx"
    private val SHEET_BSCHU = "Сигналы БЩУ"
    private val PATH_ARMATURES = "Armatures.xlsx"
    private val SHEET_ARMATURES = "Арматура"

    fun openBschu(): InputStream {
        return fileProvider.open(PATH_BSCHU)
    }

    fun openArmatures(): InputStream {
        return fileProvider.open(PATH_ARMATURES)
    }

    fun openPagingSessionBschu(): ExcelPagingSession {
        val input = fileProvider.open(PATH_BSCHU)
        return ExcelPagingSession.fromInputStream(input, SHEET_BSCHU)
    }

    fun openPagingSessionArmatures(): ExcelPagingSession {
        val input = fileProvider.open(PATH_ARMATURES)
        return ExcelPagingSession.fromInputStream(input, SHEET_ARMATURES)
    }

    // Cached sessions helpers
    fun openCachedSessionBschu(pageSize: Int): PagingSession? =
        cache.openCachedSessionOrNull(PATH_BSCHU, SHEET_BSCHU)

    fun openCachedSessionArmatures(pageSize: Int): PagingSession? =
        cache.openCachedSessionOrNull(PATH_ARMATURES, SHEET_ARMATURES)

    fun refreshCacheBschu(pageSize: Int, onUpdated: (() -> Unit)? = null) {
        cache.refreshCacheIfStale(
            relativePath = PATH_BSCHU,
            sheetName = SHEET_BSCHU,
            pageSize = pageSize,
            openInputStream = { fileProvider.open(PATH_BSCHU) },
            onUpdated = onUpdated
        )
    }

    fun refreshCacheArmatures(pageSize: Int, onUpdated: (() -> Unit)? = null) {
        cache.refreshCacheIfStale(
            relativePath = PATH_ARMATURES,
            sheetName = SHEET_ARMATURES,
            pageSize = pageSize,
            openInputStream = { fileProvider.open(PATH_ARMATURES) },
            onUpdated = onUpdated
        )
    }

    fun forceRefreshBschu(pageSize: Int, onUpdated: (() -> Unit)? = null) {
        cache.forceRebuild(
            relativePath = PATH_BSCHU,
            sheetName = SHEET_BSCHU,
            pageSize = pageSize,
            openInputStream = { fileProvider.open(PATH_BSCHU) },
            onUpdated = onUpdated
        )
    }

    fun forceRefreshArmatures(pageSize: Int, onUpdated: (() -> Unit)? = null) {
        cache.forceRebuild(
            relativePath = PATH_ARMATURES,
            sheetName = SHEET_ARMATURES,
            pageSize = pageSize,
            openInputStream = { fileProvider.open(PATH_ARMATURES) },
            onUpdated = onUpdated
        )
    }
    
    /**
     * ПРОФЕССИОНАЛЬНОЕ РЕШЕНИЕ: Поиск по всей таблице арматуры
     * Ищет по всем строкам Excel файла, а не только по загруженным в память
     */
    fun searchInAllArmatures(searchQuery: String, columnName: String? = null): List<org.example.pult.RowDataDynamic> {
        val input = fileProvider.open(PATH_ARMATURES)
        return try {
            val session = ExcelPagingSession.fromInputStream(input, SHEET_ARMATURES)
            val results = session.searchInAllData(searchQuery, columnName)
            session.close()
            results
        } catch (e: Exception) {
            android.util.Log.e("ExcelRepository", "Error searching in all armatures", e)
            emptyList()
        }
    }
    
    /**
     * Получает общее количество строк арматуры в Excel файле
     */
    fun getTotalArmaturesCount(): Int {
        val input = fileProvider.open(PATH_ARMATURES)
        return try {
            val session = ExcelPagingSession.fromInputStream(input, SHEET_ARMATURES)
            val count = session.getTotalDataRows()
            session.close()
            count
        } catch (e: Exception) {
            android.util.Log.e("ExcelRepository", "Error getting total armatures count", e)
            0
        }
    }
    
    /**
     * Поиск в диапазоне строк Excel (для дополнения неполного кэша)
     * @param searchQuery Поисковый запрос
     * @param columnName Имя колонки для поиска (null = все колонки)
     * @param startRow Начальная строка (0 = первая строка данных)
     * @param maxRows Максимальное количество строк для поиска
     */
    fun searchInExcelRange(
        searchQuery: String,
        columnName: String? = null,
        startRow: Int,
        maxRows: Int = 10000
    ): List<org.example.pult.RowDataDynamic> {
        val input = fileProvider.open(PATH_ARMATURES)
        return try {
            val session = ExcelPagingSession.fromInputStream(input, SHEET_ARMATURES)
            val results = session.searchInDataRange(searchQuery, columnName, startRow, maxRows)
            session.close()
            results
        } catch (e: Exception) {
            android.util.Log.e("ExcelRepository", "Error searching in Excel range", e)
            emptyList()
        }
    }
}




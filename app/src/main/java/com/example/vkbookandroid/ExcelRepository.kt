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
}




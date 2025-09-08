package com.example.vkbookandroid

import android.content.Context
import java.io.InputStream

class ExcelRepository(
    private val context: Context,
    private val remote: RemoteFileProvider?
) {
    private val cache by lazy { ExcelCacheManager(context) }

    private val PATH_BSCHU = "Databases/Oborudovanie_BSCHU.xlsx"
    private val SHEET_BSCHU = "Сигналы БЩУ"
    private val PATH_ARMATURES = "Databases/Armatures.xlsx"
    private val SHEET_ARMATURES = "Арматура"

    fun openBschu(): InputStream {
        return remote?.open(PATH_BSCHU, preferRemote = true) ?: context.assets.open(PATH_BSCHU)
    }

    fun openArmatures(): InputStream {
        return remote?.open(PATH_ARMATURES, preferRemote = true) ?: context.assets.open(PATH_ARMATURES)
    }

    fun openPagingSessionBschu(): ExcelPagingSession {
        val input = remote?.open(PATH_BSCHU, preferRemote = true) ?: context.assets.open(PATH_BSCHU)
        return ExcelPagingSession.fromInputStream(input, SHEET_BSCHU)
    }

    fun openPagingSessionArmatures(): ExcelPagingSession {
        val input = remote?.open(PATH_ARMATURES, preferRemote = true) ?: context.assets.open(PATH_ARMATURES)
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
            openInputStream = { remote?.open(PATH_BSCHU, preferRemote = true) ?: context.assets.open(PATH_BSCHU) },
            onUpdated = onUpdated
        )
    }

    fun refreshCacheArmatures(pageSize: Int, onUpdated: (() -> Unit)? = null) {
        cache.refreshCacheIfStale(
            relativePath = PATH_ARMATURES,
            sheetName = SHEET_ARMATURES,
            pageSize = pageSize,
            openInputStream = { remote?.open(PATH_ARMATURES, preferRemote = true) ?: context.assets.open(PATH_ARMATURES) },
            onUpdated = onUpdated
        )
    }

    fun forceRefreshBschu(pageSize: Int, onUpdated: (() -> Unit)? = null) {
        cache.forceRebuild(
            relativePath = PATH_BSCHU,
            sheetName = SHEET_BSCHU,
            pageSize = pageSize,
            openInputStream = { remote?.open(PATH_BSCHU, preferRemote = true) ?: context.assets.open(PATH_BSCHU) },
            onUpdated = onUpdated
        )
    }

    fun forceRefreshArmatures(pageSize: Int, onUpdated: (() -> Unit)? = null) {
        cache.forceRebuild(
            relativePath = PATH_ARMATURES,
            sheetName = SHEET_ARMATURES,
            pageSize = pageSize,
            openInputStream = { remote?.open(PATH_ARMATURES, preferRemote = true) ?: context.assets.open(PATH_ARMATURES) },
            onUpdated = onUpdated
        )
    }
}




package com.example.vkbookandroid.search

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Метрики производительности поиска
 * Отслеживает статистику использования кэша, индекса и общую производительность
 */
class SearchMetrics {
    
    // Счетчики операций
    private val cacheHits = AtomicInteger(0)
    private val cacheMisses = AtomicInteger(0)
    private val indexRebuilds = AtomicInteger(0)
    private val totalSearches = AtomicInteger(0)
    
    // Временные метрики
    private val totalSearchTime = AtomicLong(0)
    private val totalIndexBuildTime = AtomicLong(0)
    
    // Время последнего сброса статистики
    private val startTime = System.currentTimeMillis()
    
    /**
     * Регистрация попадания в кэш
     */
    fun logCacheHit() {
        cacheHits.incrementAndGet()
    }
    
    /**
     * Регистрация промаха кэша
     */
    fun logCacheMiss() {
        cacheMisses.incrementAndGet()
    }
    
    /**
     * Регистрация перестроения индекса
     */
    fun logIndexRebuild() {
        indexRebuilds.incrementAndGet()
    }
    
    /**
     * Регистрация выполненного поиска
     */
    fun logSearch(searchTimeMs: Long) {
        totalSearches.incrementAndGet()
        totalSearchTime.addAndGet(searchTimeMs)
    }
    
    /**
     * Регистрация времени построения индекса
     */
    fun logIndexBuild(buildTimeMs: Long) {
        totalIndexBuildTime.addAndGet(buildTimeMs)
    }
    
    /**
     * Получение процента попаданий в кэш
     */
    fun getCacheHitRate(): Float {
        val hits = cacheHits.get()
        val misses = cacheMisses.get()
        val total = hits + misses
        
        return if (total > 0) {
            (hits.toFloat() / total) * 100
        } else 0f
    }
    
    /**
     * Получение среднего времени поиска
     */
    fun getAverageSearchTime(): Float {
        val searches = totalSearches.get()
        val time = totalSearchTime.get()
        
        return if (searches > 0) {
            time.toFloat() / searches
        } else 0f
    }
    
    /**
     * Получение среднего времени построения индекса
     */
    fun getAverageIndexBuildTime(): Float {
        val rebuilds = indexRebuilds.get()
        val time = totalIndexBuildTime.get()
        
        return if (rebuilds > 0) {
            time.toFloat() / rebuilds
        } else 0f
    }
    
    /**
     * Получение времени работы в часах
     */
    fun getUptimeHours(): Float {
        val uptimeMs = System.currentTimeMillis() - startTime
        return uptimeMs.toFloat() / (1000 * 60 * 60) // Конвертируем в часы
    }
    
    /**
     * Получение детальной статистики
     */
    fun getDetailedStats(): DetailedStats {
        return DetailedStats(
            cacheHits = cacheHits.get(),
            cacheMisses = cacheMisses.get(),
            cacheHitRate = getCacheHitRate(),
            totalSearches = totalSearches.get(),
            averageSearchTime = getAverageSearchTime(),
            indexRebuilds = indexRebuilds.get(),
            averageIndexBuildTime = getAverageIndexBuildTime(),
            uptimeHours = getUptimeHours()
        )
    }
    
    /**
     * Получение краткой статистики в виде строки
     */
    fun getStats(): String {
        val hitRate = getCacheHitRate().toInt()
        val avgSearchTime = getAverageSearchTime().toInt()
        val searches = totalSearches.get()
        val rebuilds = indexRebuilds.get()
        
        return "Search: $searches queries, ${avgSearchTime}ms avg, cache: ${hitRate}%, rebuilds: $rebuilds"
    }
    
    /**
     * Сброс всех метрик
     */
    fun reset() {
        cacheHits.set(0)
        cacheMisses.set(0)
        indexRebuilds.set(0)
        totalSearches.set(0)
        totalSearchTime.set(0)
        totalIndexBuildTime.set(0)
    }
    
    /**
     * Детальная статистика
     */
    data class DetailedStats(
        val cacheHits: Int,
        val cacheMisses: Int,
        val cacheHitRate: Float,
        val totalSearches: Int,
        val averageSearchTime: Float,
        val indexRebuilds: Int,
        val averageIndexBuildTime: Float,
        val uptimeHours: Float
    ) {
        override fun toString(): String {
            return """
                |Search Metrics:
                |  Cache: $cacheHits hits, $cacheMisses misses (${cacheHitRate.toInt()}% hit rate)
                |  Searches: $totalSearches total, ${averageSearchTime.toInt()}ms average
                |  Index: $indexRebuilds rebuilds, ${averageIndexBuildTime.toInt()}ms average build time
                |  Uptime: ${uptimeHours.toInt()}h
            """.trimMargin()
        }
    }
    
    /**
     * Проверка эффективности поиска
     */
    fun isPerformanceGood(): Boolean {
        val hitRate = getCacheHitRate()
        val avgSearchTime = getAverageSearchTime()
        val searches = totalSearches.get()
        
        // Считаем производительность хорошей если:
        // - Процент попаданий в кэш > 60% (при достаточном количестве поисков)
        // - Среднее время поиска < 100мс
        return (searches < 10 || hitRate > 60f) && avgSearchTime < 100f
    }
    
    /**
     * Получение рекомендаций по оптимизации
     */
    fun getOptimizationSuggestions(): List<String> {
        val suggestions = mutableListOf<String>()
        
        val hitRate = getCacheHitRate()
        val avgSearchTime = getAverageSearchTime()
        val rebuilds = indexRebuilds.get()
        val searches = totalSearches.get()
        
        if (searches > 10 && hitRate < 40f) {
            suggestions.add("Низкий процент попаданий в кэш (${hitRate.toInt()}%). Рассмотрите увеличение времени жизни кэша.")
        }
        
        if (avgSearchTime > 200f) {
            suggestions.add("Медленный поиск (${avgSearchTime.toInt()}мс). Проверьте размер данных и эффективность индекса.")
        }
        
        if (rebuilds > searches / 2) {
            suggestions.add("Частые перестроения индекса ($rebuilds из $searches поисков). Данные часто изменяются.")
        }
        
        if (suggestions.isEmpty()) {
            suggestions.add("Производительность поиска в норме.")
        }
        
        return suggestions
    }
}


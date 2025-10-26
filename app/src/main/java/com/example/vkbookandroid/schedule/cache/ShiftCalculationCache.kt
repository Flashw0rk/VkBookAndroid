package com.example.vkbookandroid.schedule.cache

import android.util.LruCache
import android.util.Log

/**
 * Кэш для результатов расчета сдвигов месяцев
 * 
 * Пример использования:
 * val cache = ShiftCalculationCache()
 * val shift = cache.getShift(2025, 9) { year, month ->
 *     // Расчет сдвига если его нет в кэше
 *     calculateMonthShift(year, month)
 * }
 */
class ShiftCalculationCache {
    
    companion object {
        private const val TAG = "ShiftCalculationCache"
        private const val MAX_CACHE_SIZE = 100 // Максимум 100 уникальных вычислений
    }
    
    private val cache = object : LruCache<String, Int>(MAX_CACHE_SIZE) {
        override fun sizeOf(key: String, value: Int): Int = 1
    }
    
    /**
     * Получить сдвиг из кэша или вычислить его
     * 
     * @param year Год
     * @param monthIndex Индекс месяца (0-11)
     * @param calculator Функция для расчета сдвига если его нет в кэше
     * @return Сдвиг месяца
     */
    fun getShift(
        year: Int, 
        monthIndex: Int, 
        calculator: (Int, Int) -> Int
    ): Int {
        val key = createKey(year, monthIndex)
        
        synchronized(cache) {
            val cached = cache.get(key)
            if (cached != null) {
                hitCount++
                Log.d(TAG, "Cache HIT for $key")
                return cached
            }
            
            missCount++
            Log.d(TAG, "Cache MISS for $key - calculating...")
            val calculated = calculator(year, monthIndex)
            cache.put(key, calculated)
            return calculated
        }
    }
    
    /**
     * Простой get без calculator - вернет null если нет в кэше
     */
    operator fun get(key: String): Int? {
        synchronized(cache) {
            return cache.get(key)
        }
    }
    
    /**
     * Добавить сдвиг в кэш вручную
     */
    fun putShift(year: Int, monthIndex: Int, shift: Int) {
        val key = createKey(year, monthIndex)
        synchronized(cache) {
            cache.put(key, shift)
        }
    }
    
    /**
     * Проверить наличие сдвига в кэше
     */
    fun contains(year: Int, monthIndex: Int): Boolean {
        val key = createKey(year, monthIndex)
        synchronized(cache) {
            return cache.get(key) != null
        }
    }
    
    /**
     * Очистить кэш
     */
    fun clear() {
        synchronized(cache) {
            cache.evictAll()
            Log.d(TAG, "Cache cleared")
        }
    }
    
    /**
     * Получить статистику кэша
     */
    fun getStats(): CacheStats {
        synchronized(cache) {
            return CacheStats(
                size = cache.size(),
                maxSize = cache.maxSize(),
                hitRate = if (hitCount + missCount > 0) {
                    hitCount.toDouble() / (hitCount + missCount)
                } else 0.0
            )
        }
    }
    
    private var hitCount = 0
    private var missCount = 0
    
    private fun createKey(year: Int, monthIndex: Int): String {
        return "$year-$monthIndex"
    }
    
    data class CacheStats(
        val size: Int,
        val maxSize: Int,
        val hitRate: Double
    ) {
        override fun toString(): String {
            return "CacheStats(size=$size/$maxSize, hitRate=${String.format("%.1f", hitRate * 100)}%)"
        }
    }
}


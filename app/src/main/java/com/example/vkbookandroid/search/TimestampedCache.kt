package com.example.vkbookandroid.search

import android.util.LruCache
import android.util.Log

/**
 * Кэш с временными метками и версионированием данных
 * Автоматически инвалидирует устаревшие записи
 */
class TimestampedCache<K, V>(
    private val maxSize: Int = 100,
    private val maxAge: Long = 30_000L // 30 секунд по умолчанию
) {
    
    private val cache = object : LruCache<K, CacheEntry<V>>(maxSize) {
        override fun sizeOf(key: K, value: CacheEntry<V>): Int {
            return when (val data = value.value) {
                is List<*> -> data.size
                is Collection<*> -> data.size
                else -> 1
            }
        }
        
        override fun entryRemoved(
            evicted: Boolean,
            key: K,
            oldValue: CacheEntry<V>,
            newValue: CacheEntry<V>?
        ) {
            if (evicted) {
                Log.d("TimestampedCache", "Cache entry evicted: $key")
            }
        }
    }
    
    /**
     * Запись в кэше с метаданными
     */
    data class CacheEntry<V>(
        val value: V,
        val timestamp: Long,
        val dataVersion: Long
    )
    
    /**
     * Добавление элемента в кэш
     */
    fun put(key: K, value: V, dataVersion: Long) {
        val entry = CacheEntry(
            value = value,
            timestamp = System.currentTimeMillis(),
            dataVersion = dataVersion
        )
        cache.put(key, entry)
        Log.d("TimestampedCache", "Cached: $key (version: $dataVersion)")
    }
    
    /**
     * Получение элемента из кэша с проверкой актуальности
     */
    fun get(key: K, currentDataVersion: Long): V? {
        val entry = cache.get(key) ?: return null
        
        val now = System.currentTimeMillis()
        val age = now - entry.timestamp
        val isExpired = age > maxAge
        val isStale = entry.dataVersion != currentDataVersion
        
        return when {
            isExpired -> {
                Log.d("TimestampedCache", "Cache expired: $key (age: ${age}ms)")
                cache.remove(key)
                null
            }
            isStale -> {
                Log.d("TimestampedCache", "Cache stale: $key (version: ${entry.dataVersion} != $currentDataVersion)")
                cache.remove(key)
                null
            }
            else -> {
                Log.d("TimestampedCache", "Cache hit: $key")
                entry.value
            }
        }
    }
    
    /**
     * Проверка наличия актуального элемента в кэше
     */
    fun contains(key: K, currentDataVersion: Long): Boolean {
        return get(key, currentDataVersion) != null
    }
    
    /**
     * Инвалидация всего кэша
     */
    fun invalidateAll() {
        val size = cache.size()
        cache.evictAll()
        Log.d("TimestampedCache", "Cache invalidated: $size entries removed")
    }
    
    /**
     * Инвалидация записей с определенной версией данных
     */
    fun invalidateByDataVersion(dataVersion: Long) {
        val keysToRemove = mutableListOf<K>()
        
        cache.snapshot().forEach { (key, entry) ->
            if (entry.dataVersion == dataVersion) {
                keysToRemove.add(key)
            }
        }
        
        keysToRemove.forEach { cache.remove(it) }
        Log.d("TimestampedCache", "Invalidated ${keysToRemove.size} entries with version: $dataVersion")
    }
    
    /**
     * Инвалидация устаревших записей
     */
    fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val keysToRemove = mutableListOf<K>()
        
        cache.snapshot().forEach { (key, entry) ->
            val age = now - entry.timestamp
            if (age > maxAge) {
                keysToRemove.add(key)
            }
        }
        
        keysToRemove.forEach { cache.remove(it) }
        
        if (keysToRemove.isNotEmpty()) {
            Log.d("TimestampedCache", "Cleaned up ${keysToRemove.size} expired entries")
        }
    }
    
    /**
     * Получение статистики кэша
     */
    fun getStats(): CacheStats {
        val snapshot = cache.snapshot()
        val now = System.currentTimeMillis()
        
        var totalEntries = 0
        var expiredEntries = 0
        var avgAge = 0L
        
        snapshot.values.forEach { entry ->
            totalEntries++
            val age = now - entry.timestamp
            avgAge += age
            
            if (age > maxAge) {
                expiredEntries++
            }
        }
        
        avgAge = if (totalEntries > 0) avgAge / totalEntries else 0
        
        return CacheStats(
            totalEntries = totalEntries,
            expiredEntries = expiredEntries,
            averageAge = avgAge,
            maxSize = maxSize,
            maxAge = maxAge
        )
    }
    
    /**
     * Статистика кэша
     */
    data class CacheStats(
        val totalEntries: Int,
        val expiredEntries: Int,
        val averageAge: Long,
        val maxSize: Int,
        val maxAge: Long
    ) {
        fun getHitRate(): Float {
            return if (totalEntries > 0) {
                ((totalEntries - expiredEntries).toFloat() / totalEntries) * 100
            } else 0f
        }
        
        override fun toString(): String {
            return "Cache: $totalEntries/$maxSize entries, ${getHitRate().toInt()}% valid, avg age: ${averageAge}ms"
        }
    }
    
    /**
     * Получение размера кэша
     */
    fun size(): Int = cache.size()
    
    /**
     * Проверка пустоты кэша
     */
    fun isEmpty(): Boolean = cache.size() == 0
}


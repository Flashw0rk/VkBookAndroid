package com.example.vkbookandroid.utils

import android.util.LruCache

/**
 * Thread-safe обертка для LruCache
 * Обеспечивает безопасный доступ из нескольких потоков
 */
class ThreadSafeLruCache<K, V>(maxSize: Int) {
    
    private val cache = object : LruCache<K, V>(maxSize) {
        override fun sizeOf(key: K, value: V): Int {
            return when (value) {
                is String -> value.length / 2 // Примерная оценка для строк
                is List<*> -> value.size * 10 // Примерная оценка для списков
                else -> 1
            }
        }
    }
    
    /**
     * Thread-safe получение элемента из кэша
     */
    @Synchronized
    fun get(key: K): V? {
        return cache.get(key)
    }
    
    /**
     * Thread-safe добавление элемента в кэш
     */
    @Synchronized
    fun put(key: K, value: V): V? {
        return cache.put(key, value)
    }
    
    /**
     * Thread-safe очистка кэша
     */
    @Synchronized
    fun evictAll() {
        cache.evictAll()
    }
    
    /**
     * Thread-safe получение размера кэша
     */
    @Synchronized
    fun size(): Int {
        return cache.size()
    }
    
    /**
     * Thread-safe получение максимального размера кэша
     */
    fun maxSize(): Int {
        return cache.maxSize()
    }
    
    /**
     * Thread-safe получение количества попаданий в кэш
     */
    @Synchronized
    fun hitCount(): Long {
        return cache.hitCount().toLong()
    }
    
    /**
     * Thread-safe получение количества промахов кэша
     */
    @Synchronized
    fun missCount(): Long {
        return cache.missCount().toLong()
    }
    
    /**
     * Получение статистики кэша
     */
    @Synchronized
    fun getStats(): String {
        val hits = cache.hitCount()
        val misses = cache.missCount()
        val total = hits + misses
        val hitRate = if (total > 0) (hits * 100 / total) else 0
        
        return "Cache: ${cache.size()}/${cache.maxSize()}, hit rate: $hitRate%"
    }
    
    /**
     * Thread-safe проверка содержимого кэша
     */
    @Synchronized
    fun containsKey(key: K): Boolean {
        return cache.get(key) != null
    }
    
    /**
     * Thread-safe удаление элемента из кэша
     */
    @Synchronized
    fun remove(key: K): V? {
        return cache.remove(key)
    }
}

package com.example.vkbookandroid

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер для управления ленивой загрузкой и автоматическим обновлением данных
 */
class DataRefreshManager(private val context: Context) {
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val fileWatchers = ConcurrentHashMap<String, FileWatcher>()
    private val refreshCallbacks = ConcurrentHashMap<String, MutableList<() -> Unit>>()
    private var isPaused = false // Флаг паузы для экономии батареи
    
    companion object {
        private const val TAG = "DataRefreshManager"
        private const val CHECK_INTERVAL_MS = 5000L // Проверяем каждые 5 секунд
    }
    
    /**
     * Приостановить все watchers (когда приложение в фоне)
     */
    fun pauseAllWatchers() {
        if (isPaused) return
        isPaused = true
        Log.d(TAG, "All file watchers paused (app in background)")
    }
    
    /**
     * Возобновить все watchers (когда приложение на переднем плане)
     */
    fun resumeAllWatchers() {
        if (!isPaused) return
        isPaused = false
        Log.d(TAG, "All file watchers resumed (app in foreground)")
    }
    
    /**
     * Начать отслеживание файла для автоматического обновления
     */
    fun startWatching(filePath: String, onFileChanged: () -> Unit) {
        Log.d(TAG, "Starting to watch file: $filePath")
        
        // Добавляем callback
        refreshCallbacks.getOrPut(filePath) { mutableListOf() }.add(onFileChanged)
        
        // Создаем или обновляем watcher
        val watcher = fileWatchers.getOrPut(filePath) {
            FileWatcher(filePath, CHECK_INTERVAL_MS)
        }
        
        if (!watcher.isActive()) {
            watcher.start()
        }
    }
    
    /**
     * Остановить отслеживание файла
     */
    fun stopWatching(filePath: String) {
        Log.d(TAG, "Stopping watch for file: $filePath")
        fileWatchers[filePath]?.stop()
        fileWatchers.remove(filePath)
        refreshCallbacks.remove(filePath)
    }
    
    /**
     * Принудительно обновить данные для файла
     */
    fun forceRefresh(filePath: String) {
        Log.d(TAG, "Force refresh requested for file: $filePath")
        refreshCallbacks[filePath]?.forEach { callback ->
            try {
                callback()
            } catch (e: Exception) {
                Log.e(TAG, "Error in refresh callback for $filePath", e)
            }
        }
    }
    
    /**
     * Очистить все watchers
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up all watchers")
        fileWatchers.values.forEach { it.stop() }
        fileWatchers.clear()
        refreshCallbacks.clear()
        coroutineScope.cancel()
    }
    
    /**
     * Проверить, изменился ли файл с последней проверки
     */
    fun hasFileChanged(filePath: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false
        
        val watcher = fileWatchers[filePath] ?: return false
        return watcher.hasChanged()
    }
    
    /**
     * Получить время последнего изменения файла
     */
    fun getLastModified(filePath: String): Long {
        val file = File(filePath)
        return if (file.exists()) file.lastModified() else 0L
    }
    
    /**
     * Watcher для отслеживания изменений файла
     */
    private inner class FileWatcher(
        private val filePath: String,
        private val checkInterval: Long
    ) {
        private var lastModified: Long = 0L
        private var isRunning = false
        private var job: Job? = null
        
        fun start() {
            if (isRunning) return
            
            isRunning = true
            val file = File(filePath)
            lastModified = if (file.exists()) file.lastModified() else 0L
            
            job = coroutineScope.launch {
                while (isActive && isRunning) {
                    try {
                        checkForChanges()
                        delay(checkInterval)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in file watcher for $filePath", e)
                        delay(checkInterval)
                    }
                }
            }
            
            Log.d(TAG, "File watcher started for $filePath")
        }
        
        fun stop() {
            isRunning = false
            job?.cancel()
            job = null
            Log.d(TAG, "File watcher stopped for $filePath")
        }
        
        fun isActive(): Boolean {
            return isRunning
        }
        
        fun hasChanged(): Boolean {
            val file = File(filePath)
            if (!file.exists()) return false
            
            val currentModified = file.lastModified()
            return currentModified > lastModified
        }
        
        private suspend fun checkForChanges() {
            // ⚠️ ВАЖНО: Не проверяем файлы если приложение в фоне (экономия батареи)
            if (isPaused) {
                return
            }
            
            val file = File(filePath)
            if (!file.exists()) return
            
            val currentModified = file.lastModified()
            if (currentModified > lastModified) {
                Log.d(TAG, "File changed detected: $filePath (${currentModified - lastModified}ms ago)")
                lastModified = currentModified
                
                // Уведомляем все callback'и
                refreshCallbacks[filePath]?.forEach { callback ->
                    try {
                        callback()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in file change callback for $filePath", e)
                    }
                }
            }
        }
    }
}

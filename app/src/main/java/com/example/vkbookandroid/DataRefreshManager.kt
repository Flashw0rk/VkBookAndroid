package com.example.vkbookandroid

import android.content.Context
import android.os.FileObserver
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер для управления ленивой загрузкой и автоматическим обновлением данных
 * Использует FileObserver (event-based) для эффективного отслеживания изменений файлов
 */
class DataRefreshManager(private val context: Context) {
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val fileWatchers = ConcurrentHashMap<String, FileWatcher>()
    private val refreshCallbacks = ConcurrentHashMap<String, MutableList<() -> Unit>>()
    private var isPaused = false // Флаг паузы для экономии батареи
    
    companion object {
        private const val TAG = "DataRefreshManager"
        // УДАЛЕНО: CHECK_INTERVAL_MS - больше не нужен, используем FileObserver (event-based)
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
        
        // Создаем или обновляем watcher (используем FileObserver - event-based)
        val watcher = fileWatchers.getOrPut(filePath) {
            FileWatcher(filePath)
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
     * Использует FileObserver (event-based) вместо polling для экономии CPU и батареи
     */
    private inner class FileWatcher(
        private val filePath: String
    ) {
        private var lastModified: Long = 0L
        private var isRunning = false
        private var fileObserver: FileObserver? = null
        private val file = File(filePath)
        private val parentDir = file.parentFile
        
        fun start() {
            if (isRunning) return
            
            isRunning = true
            lastModified = if (file.exists()) file.lastModified() else 0L
            
            // Используем FileObserver для event-based отслеживания (0% CPU когда файл не меняется)
            if (parentDir != null && parentDir.exists()) {
                fileObserver = object : FileObserver(parentDir.absolutePath, FileObserver.MODIFY or FileObserver.CREATE) {
                    override fun onEvent(event: Int, path: String?) {
                        // Проверяем что событие относится к нашему файлу
                        if (isPaused) return // Не обрабатываем если приложение в фоне
                        
                        val changedFile = if (path != null) {
                            File(parentDir, path)
                        } else {
                            null
                        }
                        
                        // Проверяем что изменился именно наш файл
                        if (changedFile != null && changedFile.absolutePath == file.absolutePath) {
                            handleFileChange()
                        } else if (path == null && file.exists()) {
                            // Если path == null, это может быть событие для всего каталога
                            // Проверяем наш файл
                            handleFileChange()
                        }
                    }
                }
                
                fileObserver?.startWatching()
                Log.d(TAG, "FileObserver started for $filePath (event-based, 0% CPU when idle)")
            } else {
                // Fallback на polling если директория не существует (редкий случай)
                Log.w(TAG, "Parent directory not found for $filePath, using polling fallback")
                startPollingFallback()
            }
        }
        
        private fun handleFileChange() {
            if (isPaused) return
            
            val file = File(filePath)
            if (!file.exists()) return
            
            val currentModified = file.lastModified()
            if (currentModified > lastModified) {
                val timeDiff = currentModified - lastModified
                Log.d(TAG, "File changed detected via FileObserver: $filePath (${timeDiff}ms ago)")
                
                // Логирование для Battery Historian
                Log.d("Battery", "FileWatcher: file changed: $filePath")
                
                lastModified = currentModified
                
                // Уведомляем все callback'и в главном потоке
                coroutineScope.launch(Dispatchers.Main) {
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
        
        // Fallback на polling если FileObserver не может быть использован
        private fun startPollingFallback() {
            val fallbackJob = coroutineScope.launch {
                while (isActive && isRunning) {
                    try {
                        if (!isPaused) {
                            val file = File(filePath)
                            if (file.exists()) {
                                val currentModified = file.lastModified()
                                if (currentModified > lastModified) {
                                    Log.d(TAG, "File changed detected via polling fallback: $filePath")
                                    lastModified = currentModified
                                    
                                    // Уведомляем callback'и
                                    withContext(Dispatchers.Main) {
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
                        delay(10000) // 10 секунд для fallback (реже чем раньше)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in polling fallback for $filePath", e)
                        delay(10000)
                    }
                }
            }
        }
        
        fun stop() {
            isRunning = false
            fileObserver?.stopWatching()
            fileObserver = null
            Log.d(TAG, "FileObserver stopped for $filePath")
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
    }
}

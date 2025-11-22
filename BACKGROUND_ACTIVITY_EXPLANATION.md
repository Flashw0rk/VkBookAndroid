# 🔍 Анализ фоновой активности приложения VkBookAndroid

**Дата:** 21.11.2025  
**Вопрос:** Что работает в фоне, когда пользователь ничего не делает?

---

## 📋 Важно: Логи, которые вы показали

**Эти логи НЕ от вашего приложения!** Это системные процессы Android:
- `com.google.android.googlequicksearchbox` - Google Search
- `com.google.android.gms` - Google Play Services  
- `com.android.systemui` - Системный UI
- `com.android.phone` - Телефонная система
- `system_server` - Системный сервер Android

**В логах нет записей от `com.example.vkbookandroid`** - значит ваше приложение не активно или не логирует.

---

## 🔍 Что МОЖЕТ работать в фоне в вашем приложении

### 1. ⚠️ FileWatcher (DataRefreshManager) - РАБОТАЕТ ПОСТОЯННО

**Проблема:** Проверяет файлы каждые 5 секунд, даже когда приложение в фоне!

```kotlin
// DataRefreshManager.kt, строка 20
private const val CHECK_INTERVAL_MS = 5000L // Проверяем каждые 5 секунд

// Строки 114-124
job = coroutineScope.launch {
    while (isActive && isRunning) {
        try {
            checkForChanges()  // Проверка файла
            delay(checkInterval)  // Каждые 5 секунд!
        } catch (e: Exception) {
            Log.e(TAG, "Error in file watcher", e)
            delay(checkInterval)
        }
    }
}
```

**Что это делает:**
- Проверяет 4 файла каждые 5 секунд:
  - `Oborudovanie_BSCHU.xlsx`
  - `Armatures.xlsx`
  - Папка `data/` (PDF файлы)
  - `График проверок .xlsx`

**Проблема:** Это работает даже когда:
- Приложение в фоне
- Пользователь не использует приложение
- Файлы не меняются

**Влияние:**
- ⚠️ Расход батареи (постоянные проверки файлов)
- ⚠️ Нагрузка на CPU (каждые 5 секунд)
- ⚠️ Активность процесса (Android может не "заморозить" приложение)

---

### 2. ⚠️ WorkManager (если включена фоновая синхронизация)

**Работает только если включено в настройках:**
```kotlin
// MainActivity.kt, строки 218-220
if (AutoSyncSettings.isBackgroundSyncEnabled(this)) {
    schedulePeriodicBackgroundSync()  // Периодическая синхронизация
}
```

**Что делает:**
- Запускает синхронизацию по расписанию (например, каждые 6 часов)
- Работает через WorkManager (системный сервис Android)
- Требует сеть

**По умолчанию:** ОТКЛЮЧЕНО (не работает)

---

### 3. ✅ checkConnectionOnStartup() - Только при запуске

**Работает только один раз при запуске приложения:**
```kotlin
// MainActivity.kt, строка 211
checkConnectionOnStartup() // Только проверка соединения
```

**Не работает в фоне** - только при открытии приложения.

---

## 🐛 Проблема: FileWatcher работает постоянно

### Текущая реализация:

```kotlin
// DataRefreshManager.kt
private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

private inner class FileWatcher(...) {
    fun start() {
        job = coroutineScope.launch {
            while (isActive && isRunning) {
                checkForChanges()  // Проверка файла
                delay(5000)  // Каждые 5 секунд!
            }
        }
    }
}
```

**Проблемы:**
1. ❌ Работает даже когда приложение в фоне
2. ❌ Не останавливается при `onPause()` или `onStop()`
3. ❌ Расходует батарею постоянно
4. ❌ Не использует FileObserver (более эффективный механизм Android)

---

## ✅ Решение: Оптимизация FileWatcher

### Вариант 1: Остановка при уходе в фон

```kotlin
// MainActivity.kt

override fun onPause() {
    super.onPause()
    // Останавливаем FileWatcher при уходе в фон
    dataRefreshManager.pauseWatching()
}

override fun onResume() {
    super.onResume()
    // Возобновляем FileWatcher при возврате
    dataRefreshManager.resumeWatching()
}
```

```kotlin
// DataRefreshManager.kt

fun pauseWatching() {
    Log.d(TAG, "Pausing all file watchers")
    fileWatchers.values.forEach { it.pause() }
}

fun resumeWatching() {
    Log.d(TAG, "Resuming all file watchers")
    fileWatchers.values.forEach { it.resume() }
}

private inner class FileWatcher(...) {
    private var isPaused = false
    
    fun pause() {
        isPaused = true
        Log.d(TAG, "File watcher paused for $filePath")
    }
    
    fun resume() {
        isPaused = false
        Log.d(TAG, "File watcher resumed for $filePath")
    }
    
    private suspend fun checkForChanges() {
        if (isPaused) return  // Не проверяем если приостановлено
        
        val file = File(filePath)
        if (!file.exists()) return
        
        val currentModified = file.lastModified()
        if (currentModified > lastModified) {
            // ... обработка изменения
        }
    }
}
```

---

### Вариант 2: Использование Android FileObserver (рекомендуется)

**FileObserver** - нативный механизм Android, который:
- ✅ Работает только когда файл действительно меняется
- ✅ Не требует периодических проверок
- ✅ Экономит батарею
- ✅ Автоматически останавливается при уничтожении процесса

```kotlin
// DataRefreshManager.kt - улучшенная версия

import android.os.FileObserver
import java.io.File

class DataRefreshManager(private val context: Context) {
    
    private val fileObservers = ConcurrentHashMap<String, FileObserver>()
    private val refreshCallbacks = ConcurrentHashMap<String, MutableList<() -> Unit>>()
    
    fun startWatching(filePath: String, onFileChanged: () -> Unit) {
        Log.d(TAG, "Starting to watch file: $filePath")
        
        // Добавляем callback
        refreshCallbacks.getOrPut(filePath) { mutableListOf() }.add(onFileChanged)
        
        // Создаем FileObserver если его еще нет
        if (filePath !in fileObservers) {
            val file = File(filePath)
            val parentDir = if (file.isFile) file.parentFile else file
            
            val observer = object : FileObserver(
                parentDir?.absolutePath ?: filePath,
                FileObserver.MODIFY or FileObserver.CREATE or FileObserver.DELETE
            ) {
                override fun onEvent(event: Int, path: String?) {
                    if (event and FileObserver.MODIFY != 0 || 
                        event and FileObserver.CREATE != 0) {
                        
                        val changedFile = if (path != null) {
                            File(parentDir, path).absolutePath
                        } else {
                            filePath
                        }
                        
                        // Проверяем, что это именно наш файл
                        if (changedFile == filePath || 
                            (file.isDirectory && changedFile.startsWith(filePath))) {
                            
                            Log.d(TAG, "File changed detected via FileObserver: $changedFile")
                            
                            // Уведомляем все callback'и
                            refreshCallbacks[filePath]?.forEach { callback ->
                                try {
                                    callback()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error in file change callback", e)
                                }
                            }
                        }
                    }
                }
            }
            
            observer.startWatching()
            fileObservers[filePath] = observer
            Log.d(TAG, "FileObserver started for $filePath")
        }
    }
    
    fun stopWatching(filePath: String) {
        Log.d(TAG, "Stopping watch for file: $filePath")
        fileObservers[filePath]?.stopWatching()
        fileObservers.remove(filePath)
        refreshCallbacks.remove(filePath)
    }
    
    fun cleanup() {
        Log.d(TAG, "Cleaning up all file observers")
        fileObservers.values.forEach { it.stopWatching() }
        fileObservers.clear()
        refreshCallbacks.clear()
    }
}
```

**Преимущества FileObserver:**
- ✅ События только при реальных изменениях
- ✅ Нет периодических проверок
- ✅ Автоматически останавливается при уничтожении процесса
- ✅ Меньше нагрузка на батарею

---

### Вариант 3: Увеличение интервала проверки

Если оставляем текущую реализацию, можно увеличить интервал:

```kotlin
// DataRefreshManager.kt
private const val CHECK_INTERVAL_MS = 30_000L // 30 секунд вместо 5
// или
private const val CHECK_INTERVAL_MS = 60_000L // 1 минута
```

**Но это не решает проблему полностью** - проверки все равно будут.

---

## 🔍 Как проверить реальную активность вашего приложения

### 1. Фильтр логов по вашему приложению:

```bash
adb logcat | findstr "VkBookAndroid\|MainActivity\|SyncService\|DataRefreshManager"
```

Или в Android Studio Logcat:
- Фильтр: `package:mine` или `tag:MainActivity|SyncService|DataRefreshManager`

### 2. Проверка процессов:

```bash
adb shell ps | findstr "vkbookandroid"
```

### 3. Проверка использования батареи:

Настройки → Батарея → Использование батареи → VkBookAndroid

### 4. Проверка активных корутин:

Добавить логирование в DataRefreshManager:

```kotlin
private suspend fun checkForChanges() {
    Log.d(TAG, "FileWatcher checking: $filePath (${System.currentTimeMillis()})")
    // ... остальной код
}
```

---

## 📊 Текущее состояние фоновой активности

| Компонент | Работает в фоне? | Частота | Проблема? |
|-----------|------------------|---------|-----------|
| **FileWatcher** | ✅ ДА | Каждые 5 сек | ⚠️ **ДА** - расходует батарею |
| **WorkManager** | ⚠️ Только если включено | По расписанию | ✅ Нет (если отключено) |
| **checkConnectionOnStartup** | ❌ НЕТ | Только при запуске | ✅ Нет |
| **Аналитика** | ❌ НЕТ | Только при событиях | ✅ Нет |

---

## 🎯 Рекомендации

### Критично исправить:

1. **FileWatcher** - остановка при `onPause()` или переход на FileObserver
2. **Логирование** - добавить логи для отслеживания активности

### Опционально:

3. **Мониторинг батареи** - отслеживать влияние на батарею
4. **Настройки** - добавить опцию "Отключить автоматическое обновление"

---

## ✅ Быстрое исправление (минимальные изменения)

Добавить остановку FileWatcher при уходе в фон:

```kotlin
// MainActivity.kt

override fun onPause() {
    super.onPause()
    // Останавливаем FileWatcher для экономии батареи
    dataRefreshManager.pauseAllWatchers()
    Log.d("MainActivity", "FileWatcher paused (app in background)")
}

override fun onResume() {
    super.onResume()
    // Возобновляем FileWatcher
    dataRefreshManager.resumeAllWatchers()
    Log.d("MainActivity", "FileWatcher resumed (app in foreground)")
}
```

```kotlin
// DataRefreshManager.kt

private var isPaused = false

fun pauseAllWatchers() {
    isPaused = true
    Log.d(TAG, "All watchers paused")
}

fun resumeAllWatchers() {
    isPaused = false
    Log.d(TAG, "All watchers resumed")
}

private suspend fun checkForChanges() {
    if (isPaused) {
        // Не проверяем если приложение в фоне
        return
    }
    // ... остальной код
}
```

---

*Документ содержит анализ и решения для оптимизации фоновой активности.*



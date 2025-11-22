# 📱 Подробный анализ: Офлайн-режим, Интеграция с календарем, Журнал действий

**Дата:** 21.11.2025  
**Приложение:** VkBookAndroid v1.0

---

## 🔍 1. ОФЛАЙН-РЕЖИМ: Текущее состояние vs Рекомендации

### ✅ Что УЖЕ реализовано в вашем приложении:

#### 1.1. **Кэширование данных**
```kotlin
// NetworkModule.kt - строки 91-107
// Используем кэш даже при отсутствии сети (для GET запросов)
.addInterceptor { chain ->
    var request = chain.request()
    if (request.method == "GET") {
        request = request.newBuilder()
            .cacheControl(
                okhttp3.CacheControl.Builder()
                    .maxAge(5, TimeUnit.MINUTES)
                    .build()
            )
            .build()
    }
    chain.proceed(request)
}
```

**Что это дает:**
- ✅ HTTP-кэш на 10 MB (5 минут для GET-запросов)
- ✅ Приложение может читать закэшированные данные без интернета
- ✅ Файлы сохраняются в `filesDir/data/` для постоянного хранения

#### 1.2. **Локальное хранение файлов**
```kotlin
// FileProvider.kt - строки 19-40
// Приоритет filesDir над удаленными источниками
if (dataFile.exists() && dataFile.length() > 0) {
    return dataFile.inputStream()
}
```

**Что это дает:**
- ✅ Все синхронизированные файлы хранятся локально
- ✅ Приложение работает с локальными данными
- ✅ ExcelCacheManager кэширует разобранные Excel-файлы

#### 1.3. **Обработка отсутствия сети**
```kotlin
// SyncService.kt - строки 472-477
result.serverConnected = checkServerConnection()
if (!result.serverConnected) {
    Log.w(tag, "Server not available, skipping sync")
    onProgress(0, "Сервер недоступен")
    return@withContext result
}
```

**Что это дает:**
- ✅ Приложение не падает при отсутствии сети
- ✅ Показывает сообщение "Сервер недоступен"
- ✅ Продолжает работать с локальными данными

---

### ⚠️ Что ОТСУТСТВУЕТ (рекомендации для улучшения):

#### 1.4. **Индикатор офлайн/онлайн режима**
**Проблема:** Пользователь не знает, работает ли приложение офлайн или онлайн.

**Что добавить:**
```kotlin
// OfflineStatusManager.kt (новый класс)
class OfflineStatusManager(private val context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    fun observeNetworkState(callback: (Boolean) -> Unit) {
        // Использовать NetworkCallback для отслеживания изменений
    }
}
```

**UI элемент:**
- Индикатор в статус-баре (🟢 Онлайн / 🔴 Офлайн)
- Баннер при переходе в офлайн-режим
- Предупреждение при попытке синхронизации без сети

**Бизнес-ценность:**
- Пользователь понимает состояние приложения
- Меньше путаницы и жалоб
- Прозрачность работы системы

---

#### 1.5. **Очередь изменений для синхронизации**
**Проблема:** Если пользователь редактирует данные офлайн, изменения теряются или не синхронизируются.

**Что добавить:**
```kotlin
// OfflineChangeQueue.kt (новый класс)
class OfflineChangeQueue(private val context: Context) {
    private val changesFile = File(context.filesDir, "offline_changes.json")
    
    data class PendingChange(
        val id: String,
        val type: ChangeType, // CREATE, UPDATE, DELETE
        val entity: String, // "armature", "reminder", etc.
        val data: Map<String, Any>,
        val timestamp: Long
    )
    
    fun addChange(change: PendingChange) {
        val changes = loadChanges().toMutableList()
        changes.add(change)
        saveChanges(changes)
    }
    
    suspend fun syncPendingChanges(): SyncResult {
        val changes = loadChanges()
        if (changes.isEmpty()) return SyncResult(success = true)
        
        // Пытаемся синхронизировать каждое изменение
        val results = changes.map { change ->
            when (change.type) {
                ChangeType.CREATE -> createOnServer(change)
                ChangeType.UPDATE -> updateOnServer(change)
                ChangeType.DELETE -> deleteOnServer(change)
            }
        }
        
        // Удаляем успешно синхронизированные изменения
        val failed = results.filter { !it.success }
        saveChanges(failed.map { it.change })
        
        return SyncResult(
            success = failed.isEmpty(),
            syncedCount = results.count { it.success },
            failedCount = failed.size
        )
    }
}
```

**Где использовать:**
- При редактировании арматуры в редакторе
- При добавлении/изменении напоминаний
- При изменении настроек, которые нужно синхронизировать

**Бизнес-ценность:**
- Никакие изменения не теряются
- Пользователь может работать офлайн без ограничений
- Автоматическая синхронизация при восстановлении связи

---

#### 1.6. **Конфликт-резолюшн при расхождении данных**
**Проблема:** Если данные изменились и на сервере, и локально, нужно решить конфликт.

**Что добавить:**
```kotlin
// ConflictResolver.kt (новый класс)
class ConflictResolver {
    enum class ResolutionStrategy {
        SERVER_WINS,      // Всегда использовать серверную версию
        LOCAL_WINS,       // Всегда использовать локальную версию
        MERGE,            // Попытаться объединить изменения
        ASK_USER          // Спросить пользователя
    }
    
    suspend fun resolveConflict(
        localData: Map<String, Any>,
        serverData: Map<String, Any>,
        strategy: ResolutionStrategy
    ): Map<String, Any> {
        return when (strategy) {
            ResolutionStrategy.SERVER_WINS -> serverData
            ResolutionStrategy.LOCAL_WINS -> localData
            ResolutionStrategy.MERGE -> mergeData(localData, serverData)
            ResolutionStrategy.ASK_USER -> showConflictDialog(localData, serverData)
        }
    }
    
    private fun mergeData(local: Map<String, Any>, server: Map<String, Any>): Map<String, Any> {
        // Умное объединение: берем последние изменения по timestamp
        // или объединяем непересекающиеся поля
        return local + server.filter { it.key !in local }
    }
}
```

**UI для конфликтов:**
- Диалог с двумя версиями данных
- Возможность выбрать версию или объединить вручную
- Просмотр различий (diff view)

**Бизнес-ценность:**
- Предотвращение потери данных
- Гибкость в работе с конфликтами
- Прозрачность процесса синхронизации

---

#### 1.7. **Приоритизация синхронизации**
**Проблема:** При восстановлении связи все файлы синхронизируются сразу, что может быть медленно.

**Что добавить:**
```kotlin
// SyncPriorityManager.kt (новый класс)
class SyncPriorityManager {
    enum class Priority {
        CRITICAL,    // armature_coords.json, Armatures.xlsx
        HIGH,        // Oborudovanie_BSCHU.xlsx
        NORMAL,      // Остальные Excel файлы
        LOW          // PDF схемы
    }
    
    fun getSyncOrder(files: List<String>): List<String> {
        return files.sortedBy { file ->
            when {
                file.contains("armature_coords") -> Priority.CRITICAL.ordinal
                file.contains("Armatures.xlsx") -> Priority.CRITICAL.ordinal
                file.contains("Oborudovanie_BSCHU") -> Priority.HIGH.ordinal
                file.endsWith(".xlsx") -> Priority.NORMAL.ordinal
                file.endsWith(".pdf") -> Priority.LOW.ordinal
                else -> Priority.NORMAL.ordinal
            }
        }
    }
}
```

**Бизнес-ценность:**
- Критичные данные доступны быстрее
- Лучший UX при медленном интернете
- Эффективное использование трафика

---

#### 1.8. **Фоновая синхронизация при подключении к Wi-Fi**
**Проблема:** Синхронизация может расходовать мобильный трафик.

**Что добавить:**
```kotlin
// AutoSyncManager.kt (расширение существующего)
class AutoSyncManager(private val context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    fun shouldAutoSync(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        // Синхронизируем только на Wi-Fi или если пользователь явно разрешил
        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val allowMobile = AutoSyncSettings.isMobileSyncEnabled(context)
        
        return isWifi || allowMobile
    }
    
    fun scheduleSyncOnWifiConnect() {
        // Использовать BroadcastReceiver для отслеживания подключения к Wi-Fi
        // Автоматически запускать синхронизацию
    }
}
```

**Настройки:**
- "Синхронизировать только на Wi-Fi" (по умолчанию включено)
- "Разрешить синхронизацию на мобильных данных"
- "Автосинхронизация при подключении к Wi-Fi"

**Бизнес-ценность:**
- Экономия мобильного трафика
- Автоматическая синхронизация без участия пользователя
- Лучший UX

---

### 📊 Сравнительная таблица: Текущее vs Рекомендуемое

| Функция | Текущее состояние | Рекомендуемое улучшение |
|---------|-------------------|------------------------|
| **Чтение данных офлайн** | ✅ Работает (кэш + локальные файлы) | ✅ Оставить как есть |
| **Индикатор офлайн/онлайн** | ❌ Отсутствует | ⚠️ Добавить индикатор |
| **Очередь изменений** | ❌ Изменения теряются | ⚠️ Добавить очередь |
| **Конфликт-резолюшн** | ❌ Нет обработки конфликтов | ⚠️ Добавить разрешение конфликтов |
| **Приоритизация синхронизации** | ⚠️ Частично (в SyncService) | ⚠️ Улучшить приоритизацию |
| **Синхронизация на Wi-Fi** | ⚠️ Есть настройка, но нет авто-запуска | ⚠️ Добавить авто-запуск |

---

## 📅 2. ИНТЕГРАЦИЯ С КАЛЕНДАРЕМ: Подробное описание

### 🎯 Зачем это нужно:

1. **Синхронизация напоминаний** - напоминания из приложения появляются в системном календаре
2. **Единая точка доступа** - пользователь видит все события в одном месте
3. **Уведомления системы** - Android сам напоминает о событиях
4. **Интеграция с другими приложениями** - календарь синхронизируется с Google Calendar, Outlook и т.д.

---

### 📋 Что нужно реализовать:

#### 2.1. **Экспорт напоминаний в календарь**

```kotlin
// CalendarIntegrationManager.kt (новый класс)
class CalendarIntegrationManager(private val context: Context) {
    
    data class CalendarEvent(
        val title: String,
        val description: String,
        val startTime: Long,
        val endTime: Long,
        val recurrenceRule: String?, // RRULE для повторяющихся событий
        val reminderMinutes: List<Int> // За сколько минут напоминать
    )
    
    /**
     * Добавить событие в системный календарь
     */
    fun addEventToCalendar(event: CalendarEvent): Long? {
        val contentResolver = context.contentResolver
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, getDefaultCalendarId())
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DESCRIPTION, event.description)
            put(CalendarContract.Events.DTSTART, event.startTime)
            put(CalendarContract.Events.DTEND, event.endTime)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            event.recurrenceRule?.let {
                put(CalendarContract.Events.RRULE, it)
            }
        }
        
        val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val eventId = uri?.lastPathSegment?.toLongOrNull()
        
        // Добавляем напоминания
        event.reminderMinutes.forEach { minutes ->
            addReminder(eventId!!, minutes)
        }
        
        return eventId
    }
    
    /**
     * Преобразовать ReminderRule в CalendarEvent
     */
    fun convertReminderToEvent(rule: ReminderRule): CalendarEvent {
        val startTime = calculateNextOccurrence(rule)
        val endTime = startTime + (60 * 60 * 1000) // 1 час по умолчанию
        
        return CalendarEvent(
            title = "Проверка: ${rule.operationName}",
            description = "Правило: ${rule.description}",
            startTime = startTime,
            endTime = endTime,
            recurrenceRule = convertToRRule(rule),
            reminderMinutes = listOf(15, 60) // За 15 минут и за 1 час
        )
    }
    
    /**
     * Конвертировать ReminderRule в формат RRULE (iCalendar)
     */
    private fun convertToRRule(rule: ReminderRule): String {
        return when (rule.type) {
            ReminderRuleType.WEEKLY -> {
                val days = rule.weekDays.joinToString(",") { dayToRRuleDay(it) }
                "FREQ=WEEKLY;BYDAY=$days"
            }
            ReminderRuleType.MONTHLY -> {
                val dayOfMonth = rule.dayOfMonth
                "FREQ=MONTHLY;BYMONTHDAY=$dayOfMonth"
            }
            ReminderRuleType.QUARTERLY -> {
                "FREQ=MONTHLY;INTERVAL=3;BYMONTHDAY=${rule.dayOfMonth}"
            }
            ReminderRuleType.YEARLY -> {
                val month = rule.month
                val day = rule.dayOfMonth
                "FREQ=YEARLY;BYMONTH=$month;BYMONTHDAY=$day"
            }
            else -> null
        }
    }
}
```

**Разрешения в AndroidManifest.xml:**
```xml
<uses-permission android:name="android.permission.READ_CALENDAR" />
<uses-permission android:name="android.permission.WRITE_CALENDAR" />
```

**UI интеграция:**
- Кнопка "Добавить в календарь" в диалоге напоминания
- Настройка "Автоматически добавлять напоминания в календарь"
- Список синхронизированных событий с возможностью удаления

---

#### 2.2. **Импорт событий из календаря**

```kotlin
/**
 * Читать события из календаря за период
 */
fun readCalendarEvents(startTime: Long, endTime: Long): List<CalendarEvent> {
    val contentResolver = context.contentResolver
    val projection = arrayOf(
        CalendarContract.Events._ID,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DESCRIPTION,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.DTEND,
        CalendarContract.Events.RRULE
    )
    
    val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
    val selectionArgs = arrayOf(startTime.toString(), endTime.toString())
    
    val cursor = contentResolver.query(
        CalendarContract.Events.CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        "${CalendarContract.Events.DTSTART} ASC"
    )
    
    return cursor?.use {
        buildList {
            while (it.moveToNext()) {
                add(CalendarEvent(
                    id = it.getLong(0),
                    title = it.getString(1) ?: "",
                    description = it.getString(2) ?: "",
                    startTime = it.getLong(3),
                    endTime = it.getLong(4),
                    recurrenceRule = it.getString(5)
                ))
            }
        }
    } ?: emptyList()
}
```

**Использование:**
- Показывать события из календаря в графике проверок
- Синхронизировать с напоминаниями приложения
- Предлагать создать напоминание на основе события календаря

---

#### 2.3. **Синхронизация с Google Calendar**

Если пользователь использует Google Calendar, события автоматически синхронизируются через системный календарь Android. Дополнительная интеграция через Google Calendar API не требуется для базового функционала.

**Расширенная интеграция (опционально):**
- Прямая синхронизация через Google Calendar API
- Создание отдельного календаря "VkBook Проверки"
- Веб-интерфейс для просмотра событий

---

### 💡 Пример использования:

```kotlin
// В ChecksScheduleFragment.kt при сохранении напоминания
private fun saveReminderToCalendar(rule: ReminderRule) {
    val calendarManager = CalendarIntegrationManager(requireContext())
    val event = calendarManager.convertReminderToEvent(rule)
    val eventId = calendarManager.addEventToCalendar(event)
    
    if (eventId != null) {
        // Сохраняем ID события для последующего обновления/удаления
        rule.calendarEventId = eventId
        Toast.makeText(context, "Напоминание добавлено в календарь", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Ошибка добавления в календарь", Toast.LENGTH_SHORT).show()
    }
}
```

---

## 📝 3. ЖУРНАЛ ДЕЙСТВИЙ (AUDIT LOG): Подробное описание

### 🎯 Зачем это нужно:

1. **Безопасность** - отслеживание всех изменений данных
2. **Диагностика** - понимание что произошло при проблемах
3. **Соответствие требованиям** - некоторые организации требуют логирование действий
4. **Восстановление данных** - возможность откатить изменения
5. **Аналитика** - понимание как пользователи используют приложение

---

### 📋 Что нужно реализовать:

#### 3.1. **Базовая структура журнала**

```kotlin
// AuditLogManager.kt (новый класс)
class AuditLogManager(private val context: Context) {
    
    data class AuditLogEntry(
        val id: String = UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val userId: String? = null, // Если будет авторизация
        val action: Action,
        val entity: Entity,
        val details: Map<String, Any> = emptyMap(),
        val result: Result,
        val deviceInfo: DeviceInfo? = null
    )
    
    enum class Action {
        // Синхронизация
        SYNC_STARTED,
        SYNC_COMPLETED,
        SYNC_FAILED,
        FILE_DOWNLOADED,
        FILE_UPLOADED,
        
        // Данные
        DATA_CREATED,
        DATA_UPDATED,
        DATA_DELETED,
        DATA_VIEWED,
        
        // Настройки
        SETTINGS_CHANGED,
        THEME_CHANGED,
        SERVER_URL_CHANGED,
        
        // Безопасность
        ADMIN_MODE_ENABLED,
        ADMIN_MODE_DISABLED,
        PASSWORD_CHANGED,
        
        // Поиск
        SEARCH_PERFORMED,
        
        // Экспорт/Импорт
        DATA_EXPORTED,
        DATA_IMPORTED
    }
    
    enum class Entity {
        ARMATURE,
        REMINDER,
        EXCEL_FILE,
        PDF_FILE,
        SETTINGS,
        CALENDAR_EVENT
    }
    
    enum class Result {
        SUCCESS,
        FAILURE,
        PARTIAL
    }
    
    data class DeviceInfo(
        val model: String,
        val androidVersion: String,
        val appVersion: String
    )
}
```

---

#### 3.2. **Хранение логов**

```kotlin
class AuditLogManager(private val context: Context) {
    private val logFile = File(context.filesDir, "audit_log.jsonl") // JSON Lines формат
    private val maxLogSize = 10 * 1024 * 1024 // 10 MB
    private val maxEntries = 10000
    
    /**
     * Добавить запись в журнал
     */
    fun log(entry: AuditLogEntry) {
        try {
            // Ротация логов если файл слишком большой
            if (logFile.exists() && logFile.length() > maxLogSize) {
                rotateLogs()
            }
            
            val json = Gson().toJson(entry)
            logFile.appendText("$json\n")
            
            // Ограничиваем количество записей
            limitEntries()
            
        } catch (e: Exception) {
            Log.e("AuditLogManager", "Failed to write audit log", e)
        }
    }
    
    /**
     * Читать записи за период
     */
    fun readEntries(startTime: Long, endTime: Long): List<AuditLogEntry> {
        if (!logFile.exists()) return emptyList()
        
        return logFile.readLines()
            .mapNotNull { line ->
                try {
                    Gson().fromJson(line, AuditLogEntry::class.java)
                } catch (e: Exception) {
                    null
                }
            }
            .filter { it.timestamp in startTime..endTime }
            .sortedByDescending { it.timestamp }
    }
    
    /**
     * Фильтрация по действию или сущности
     */
    fun filterEntries(
        action: Action? = null,
        entity: Entity? = null,
        limit: Int = 100
    ): List<AuditLogEntry> {
        if (!logFile.exists()) return emptyList()
        
        return logFile.readLines()
            .mapNotNull { line ->
                try {
                    Gson().fromJson(line, AuditLogEntry::class.java)
                } catch (e: Exception) {
                    null
                }
            }
            .filter { entry ->
                (action == null || entry.action == action) &&
                (entity == null || entry.entity == entity)
            }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }
    
    /**
     * Ротация логов (архивирование старых)
     */
    private fun rotateLogs() {
        val archiveFile = File(context.filesDir, "audit_log_${System.currentTimeMillis()}.jsonl")
        logFile.copyTo(archiveFile, overwrite = true)
        logFile.delete()
    }
    
    /**
     * Ограничить количество записей
     */
    private fun limitEntries() {
        val lines = logFile.readLines()
        if (lines.size > maxEntries) {
            val keepLines = lines.takeLast(maxEntries)
            logFile.writeText(keepLines.joinToString("\n") + "\n")
        }
    }
}
```

---

#### 3.3. **Интеграция в существующий код**

```kotlin
// В SyncService.kt
suspend fun syncAll(onProgress: suspend (Int, String) -> Unit = { _, _ -> }): SyncResult {
    val auditLog = AuditLogManager(context)
    
    // Логируем начало синхронизации
    auditLog.log(AuditLogEntry(
        action = Action.SYNC_STARTED,
        entity = Entity.EXCEL_FILE,
        result = Result.SUCCESS,
        details = mapOf("server_url" to NetworkModule.getCurrentBaseUrl())
    ))
    
    return withContext(Dispatchers.IO) {
        try {
            // ... существующий код синхронизации ...
            
            // Логируем успешное завершение
            auditLog.log(AuditLogEntry(
                action = Action.SYNC_COMPLETED,
                entity = Entity.EXCEL_FILE,
                result = Result.SUCCESS,
                details = mapOf(
                    "files_synced" to result.updatedFiles.size,
                    "duration_ms" to (endTime - startTime)
                )
            ))
            
            result
        } catch (e: Exception) {
            // Логируем ошибку
            auditLog.log(AuditLogEntry(
                action = Action.SYNC_FAILED,
                entity = Entity.EXCEL_FILE,
                result = Result.FAILURE,
                details = mapOf("error" to e.message ?: "Unknown error")
            ))
            throw e
        }
    }
}
```

```kotlin
// В EditorFragment.kt при сохранении
private fun saveChanges() {
    val auditLog = AuditLogManager(requireContext())
    
    // ... код сохранения ...
    
    auditLog.log(AuditLogEntry(
        action = Action.DATA_UPDATED,
        entity = Entity.ARMATURE,
        result = Result.SUCCESS,
        details = mapOf(
            "file" to currentFileName,
            "markers_count" to markers.size,
            "changes_count" to changesCount
        )
    ))
}
```

```kotlin
// В MainActivity.kt при включении admin режима
private fun enableAdminMode() {
    val auditLog = AuditLogManager(this)
    
    // ... код включения ...
    
    auditLog.log(AuditLogEntry(
        action = Action.ADMIN_MODE_ENABLED,
        entity = Entity.SETTINGS,
        result = Result.SUCCESS,
        details = mapOf("timestamp" to System.currentTimeMillis())
    ))
}
```

---

#### 3.4. **UI для просмотра журнала**

```kotlin
// AuditLogActivity.kt (новая Activity)
class AuditLogActivity : AppCompatActivity() {
    private lateinit var auditLogManager: AuditLogManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AuditLogAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audit_log)
        
        auditLogManager = AuditLogManager(this)
        
        // Фильтры
        val filterAction = findViewById<Spinner>(R.id.filterAction)
        val filterEntity = findViewById<Spinner>(R.id.filterEntity)
        val dateRange = findViewById<DateRangePicker>(R.id.dateRange)
        
        // Кнопка экспорта
        findViewById<Button>(R.id.btnExport).setOnClickListener {
            exportLogs()
        }
        
        loadLogs()
    }
    
    private fun loadLogs() {
        val entries = auditLogManager.readEntries(
            startTime = dateRange.startTime,
            endTime = dateRange.endTime
        )
        
        adapter.submitList(entries)
    }
    
    private fun exportLogs() {
        val entries = auditLogManager.readEntries(0, Long.MAX_VALUE)
        val json = Gson().toJson(entries)
        
        // Сохранить в файл или отправить по email
        val file = File(getExternalFilesDir(null), "audit_log_export.json")
        file.writeText(json)
        
        // Поделиться файлом
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this@AuditLogActivity, "${packageName}.fileprovider", file))
        }
        startActivity(Intent.createChooser(intent, "Экспорт журнала"))
    }
}
```

**Меню в настройках:**
- "Журнал действий" → открывает AuditLogActivity
- Фильтры по действию, сущности, дате
- Поиск по журналу
- Экспорт в JSON/CSV

---

#### 3.5. **Приватность и безопасность**

```kotlin
/**
 * Очистка чувствительных данных из логов
 */
private fun sanitizeDetails(details: Map<String, Any>): Map<String, Any> {
    val sensitiveKeys = listOf("password", "api_key", "token", "secret")
    
    return details.mapValues { (key, value) ->
        if (sensitiveKeys.any { key.contains(it, ignoreCase = true) }) {
            "***REDACTED***"
        } else {
            value
        }
    }
}

/**
 * Удаление старых логов (GDPR compliance)
 */
fun deleteOldLogs(olderThanDays: Int) {
    val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
    val entries = readEntries(0, cutoffTime)
    
    // Удалить записи старше cutoffTime
    // ...
}
```

---

### 📊 Примеры записей в журнале:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": 1700568000000,
  "action": "SYNC_COMPLETED",
  "entity": "EXCEL_FILE",
  "details": {
    "server_url": "https://vkbookserver.onrender.com/",
    "files_synced": 5,
    "files_skipped": 2,
    "duration_ms": 3450
  },
  "result": "SUCCESS",
  "deviceInfo": {
    "model": "Samsung Galaxy S21",
    "androidVersion": "13",
    "appVersion": "1.0"
  }
}
```

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "timestamp": 1700568100000,
  "action": "DATA_UPDATED",
  "entity": "ARMATURE",
  "details": {
    "file": "armature_coords.json",
    "markers_added": 3,
    "markers_modified": 1,
    "markers_deleted": 0
  },
  "result": "SUCCESS"
}
```

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440002",
  "timestamp": 1700568200000,
  "action": "ADMIN_MODE_ENABLED",
  "entity": "SETTINGS",
  "details": {},
  "result": "SUCCESS"
}
```

---

## 🎯 Итоговые рекомендации по приоритизации:

### Высокий приоритет:
1. **Офлайн-режим:**
   - ✅ Индикатор офлайн/онлайн (1-2 дня)
   - ✅ Очередь изменений (3-5 дней)
   - ⚠️ Конфликт-резолюшн (5-7 дней)

### Средний приоритет:
2. **Журнал действий:**
   - ✅ Базовая структура и логирование (2-3 дня)
   - ✅ UI для просмотра (2-3 дня)
   - ⚠️ Экспорт и фильтрация (1-2 дня)

### Низкий приоритет:
3. **Интеграция с календарем:**
   - ✅ Экспорт напоминаний (2-3 дня)
   - ⚠️ Импорт событий (3-5 дней)
   - ⚠️ Расширенная интеграция (по необходимости)

---

*Документ создан на основе анализа кодовой базы и лучших практик разработки.*



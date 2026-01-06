package com.example.vkbookandroid

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.vkbookandroid.theme.AppTheme
import com.example.vkbookandroid.theme.strategies.ThemeStrategy
import com.example.vkbookandroid.theme.strategies.ThemeFactory
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class ChecksScheduleFragment : Fragment(), RefreshableFragment, com.example.vkbookandroid.theme.ThemeManager.ThemeAwareFragment {

    lateinit var recyclerHours: RecyclerView
    lateinit var recyclerCalendar: RecyclerView
    lateinit var recyclerTasks: RecyclerView
    lateinit var tvNow: TextView
    lateinit var btnEditMode: ToggleButton
    lateinit var btnPersonalMode: ToggleButton
    lateinit var btnToggleCalendar: TextView
    private val hoursAdapter by lazy { HoursAdapter() }
    private val monthAdapter by lazy { MonthAdapter() }
    private val tasksAdapter by lazy { TasksAdapter() }
    private var editMode: Boolean = false
    private var personalMode: Boolean = false // false = служебные, true = личные
    private var isDataLoadedFlag: Boolean = false
    private var isCalendarExpanded: Boolean = false
    private var lastNowHour = -1 // Для оптимизации обновления часов
    
    // Флаг для предотвращения множественных загрузок фона
    private var isLoadingBackground: Boolean = false
    
    // Текущая стратегия темы (изолированная)
    private var currentTheme: ThemeStrategy = ThemeFactory.createCurrentTheme()
    
    companion object {
        private const val PREFS_NAME = "ChecksSchedulePrefs"
        private const val KEY_PERSONAL_MODE = "personal_mode"
        private const val KEY_PERSONAL_TASKS = "personal_tasks"
    }
    private val tickHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            // Обновляем текущую дату/время и активность задач
            if (isAdded) {
                updateNow()
                
                // Оптимизация: обновляем только изменившиеся часы
                val nowH = LocalDateTime.now().hour
                if (nowH != lastNowHour) {
                    val oldPos = lastNowHour
                    lastNowHour = nowH
                    if (oldPos in 0..23) {
                        hoursAdapter.notifyItemChanged(oldPos)
                    }
                    hoursAdapter.notifyItemChanged(nowH)
                } else {
                    // Если час не изменился, просто обновляем текущий для рамки
                    hoursAdapter.notifyItemChanged(nowH)
                }
                
                // Обновляем активность задач с учетом выбранных дней/часов
                updateTasksActiveStatus()
            }
            tickHandler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_checks_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Инициализация UI элементов
        tvNow = view.findViewById(R.id.tvNow)
        recyclerHours = view.findViewById(R.id.recyclerHours)
        recyclerCalendar = view.findViewById(R.id.recyclerCalendar)
        recyclerTasks = view.findViewById(R.id.recyclerTasks)
        btnEditMode = view.findViewById(R.id.btnEditMode)
        btnPersonalMode = view.findViewById(R.id.btnPersonalMode)
        btnToggleCalendar = view.findViewById(R.id.btnToggleCalendar)
        
        // Загружаем сохраненное состояние режима
        loadPersonalModeState()
        
        // Загружаем и применяем тему
        AppTheme.loadTheme(requireContext())
        currentTheme = ThemeFactory.createCurrentTheme()
        
        // Инициализируем тему для адаптеров
        hoursAdapter.theme = currentTheme
        monthAdapter.theme = currentTheme
        tasksAdapter.theme = currentTheme
        
        applyThemeToView(view)
        
        // Настройка RecyclerView
        setupRecyclerViews()
        
        // КРИТИЧНО: Принудительно обновляем адаптеры после recreate()
        // Адаптеры создаются через lazy и могут сохранять старые данные
        hoursAdapter.notifyDataSetChanged()
        monthAdapter.notifyDataSetChanged()
        tasksAdapter.notifyDataSetChanged()
        
        // Настройка кнопки режима редактирования
        setupEditMode()
        
        // Настройка кнопки переключения личных/служебных задач
        setupPersonalMode()
        
        // Настройка кнопки разворачивания/сворачивания календаря
        setupCalendarToggle()
        
        view.isSoundEffectsEnabled = false
        // Данные будут загружены лениво при первом показе фрагмента (в onResume)
    }

    private fun setupRecyclerViews() {
        recyclerHours.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = hoursAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(24) // Кэшируем все 24 часа
        }
        
        // Устанавливаем коллбек для обновления задач при изменении выбранных часов
        hoursAdapter.onSelectionChanged = {
            updateTasksActiveStatus()
        }
        
        recyclerCalendar.apply {
            layoutManager = GridLayoutManager(requireContext(), 7)
            adapter = monthAdapter
            setHasFixedSize(false) // Размер меняется в зависимости от месяца
            setItemViewCacheSize(42) // Максимум 6 недель = 42 дня
        }
        monthAdapter.setContext(requireContext())
        
        // Устанавливаем коллбек для обновления задач при изменении выбранных дней
        monthAdapter.onSelectionChanged = {
            // Обновляем шкалу часов с учетом выбранных дат (для отображения меток дней)
            val selectedDates = monthAdapter.getSelectedDates()
            hoursAdapter.submit(buildHours(selectedDates), resetSelection = false)
            updateTasksActiveStatus()
        }
        
        recyclerTasks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = tasksAdapter
            setHasFixedSize(false) // Количество задач может меняться
        }
        tasksAdapter.setContext(requireContext())
        
        // Устанавливаем коллбек для сохранения личных задач
        tasksAdapter.onSaveRequested = { items ->
            if (personalMode) {
                savePersonalTasks(items)
            }
        }
    }
    
    private fun updateTasksActiveStatus() {
        val selectedCells = hoursAdapter.getSelectedCells()  // Получаем полные ячейки с dayOffset
        val selectedDates = monthAdapter.getSelectedDates()
        Log.d("ChecksSchedule", "updateTasksActiveStatus: cells=${selectedCells.map { "(${it.hour}h, day${it.dayOffset})" }}, dates=$selectedDates")
        tasksAdapter.updateActiveStatus(selectedCells, selectedDates)
    }

    private fun setupEditMode() {
        btnEditMode.setOnCheckedChangeListener { _, isChecked ->
            editMode = isChecked
            hoursAdapter.setEditMode(isChecked)
            monthAdapter.setEditMode(isChecked)
            tasksAdapter.setEditMode(isChecked)
            applyToggleStyle(btnEditMode, isChecked)
        }
        applyToggleStyle(btnEditMode, btnEditMode.isChecked)
    }

    private fun setupPersonalMode() {
        // Устанавливаем состояние кнопки из сохраненного значения
        btnPersonalMode.isChecked = personalMode
        applyToggleStyle(btnPersonalMode, personalMode)
        
        btnPersonalMode.setOnCheckedChangeListener { _, isChecked ->
            personalMode = isChecked
            savePersonalModeState()
            applyToggleStyle(btnPersonalMode, isChecked)
            // Перезагружаем данные в зависимости от режима
            isDataLoadedFlag = false
            loadChecksScheduleData()
        }
    }
    
    private fun loadPersonalModeState() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        personalMode = prefs.getBoolean(KEY_PERSONAL_MODE, false)
    }
    
    private fun savePersonalModeState() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PERSONAL_MODE, personalMode).apply()
    }

    private fun setupCalendarToggle() {
        btnToggleCalendar.setOnClickListener {
            isCalendarExpanded = !isCalendarExpanded
            updateCalendarView()
        }
        // Применяем стиль к кнопке
        applyCalendarToggleStyle(btnToggleCalendar)
    }

    private fun updateCalendarView(resetSelection: Boolean = false) {
        val ym = YearMonth.now()
        if (isCalendarExpanded) {
            // Развернутый календарь - показываем весь месяц
            monthAdapter.submit(buildMonth(ym, false), resetSelection)
            btnToggleCalendar.text = "▲"
            // Высота для полного месяца (максимум 6 недель)
            val heightPx = requireContext().dpToPx(210) // 6 недель по 35dp (увеличено на 25%)
            recyclerCalendar.layoutParams.height = heightPx
        } else {
            // Свернутый календарь - показываем только текущую неделю
            monthAdapter.submit(buildMonth(ym, true), resetSelection)
            btnToggleCalendar.text = "▼"
            // Высота для одной недели
            val heightPx = requireContext().dpToPx(55) // Уменьшено для экономии места
            recyclerCalendar.layoutParams.height = heightPx
        }
        recyclerCalendar.requestLayout()
    }

    /**
     * Гарантирует что данные загружены (аналогично ArmatureFragment)
     */
    fun ensureDataLoaded() {
        // ЗАЩИТА: Проверяем что view инициализирован
        if (view == null || !::recyclerHours.isInitialized) {
            android.util.Log.w("ChecksSchedule", "ensureDataLoaded() вызван но view не готов, откладываем загрузку")
            // Отложим загрузку до момента когда view будет готов
            view?.post {
                if (::recyclerHours.isInitialized && !isDataLoadedFlag) {
                    loadChecksScheduleData()
                }
            }
            return
        }
        
        if (!isDataLoadedFlag) {
            val mainActivity = activity as? MainActivity
            if (mainActivity != null && !mainActivity.isInitializationComplete()) {
                mainActivity.addInitializationListener {
                    loadChecksScheduleData()
                }
                return
            }
            loadChecksScheduleData()
        }
    }
    
    /**
     * Загружает данные графика проверок
     */
    private fun loadChecksScheduleData() {
        // Календарь - по умолчанию свернут (только текущая неделя), выделяем сегодняшний день
        updateCalendarView(resetSelection = true)
        // Получаем выбранные даты (сегодняшний день уже выделен в updateCalendarView)
        var selectedDates = monthAdapter.getSelectedDates()
        // КРИТИЧНО: Если даты не выбраны (не должно быть, но на всякий случай), используем сегодняшний день
        if (selectedDates.isEmpty()) {
            selectedDates = setOf(LocalDate.now())
        }
        // Часы 0..23 - при первой загрузке выделяем текущий интервал
        // КРИТИЧНО: Передаем selectedDates для правильного отображения меток дней (-1д/+1д → "Ср"/"Пт")
        hoursAdapter.submit(buildHours(selectedDates), resetSelection = true)
        scrollToCurrentHour()
        
        // Таблица задач: в зависимости от режима загружаем личные или служебные задачи
        val items = if (personalMode) {
            loadPersonalTasks()
        } else {
            loadChecksFromExcelOrCsv()
        }
        tasksAdapter.submit(items)
        
        updateNow()
        isDataLoadedFlag = true
        // Обновляем статусы задач с учетом выбранных элементов (текущий день + текущий интервал часов)
        updateTasksActiveStatus()
        // Запускаем таймер обновления времени
        startTicks()
    }
    
    /**
     * Загружает личные задачи пользователя
     */
    private fun loadPersonalTasks(): List<CheckItem> {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val tasksJson = prefs.getString(KEY_PERSONAL_TASKS, null)
        
        if (tasksJson.isNullOrBlank()) {
            // Если нет сохраненных задач, создаем 5 пустых строк
            return (0..4).map { 
                CheckItem(
                    operation = "",
                    time = "",
                    rule = "",
                    reminderRules = mutableListOf(),
                    isActive = false
                )
            }
        }
        
        // Парсим сохраненные задачи
        val loadedTasks = try {
            tasksJson.split("|||").mapIndexedNotNull { index, taskStr ->
                if (taskStr.isBlank()) return@mapIndexedNotNull null
                val parts = taskStr.split(";;")
                if (parts.size >= 3) {
                    val operation = parts[0]
                    val time = parts[1]
                    val rule = parts[2]
                    
                    // Загружаем правила напоминаний для этой задачи по индексу
                    val reminderRulesKey = "personal_reminder_$index"
                    val reminderRulesJson = prefs.getString(reminderRulesKey, null)
                    val reminderRules = if (reminderRulesJson.isNullOrBlank()) {
                        mutableListOf()
                    } else {
                        reminderRulesJson.split("|||")
                            .filter { it.isNotBlank() }
                            .mapNotNull { 
                                try {
                                    ReminderRule.deserialize(it)
                                } catch (e: Exception) {
                                    Log.e("ChecksSchedule", "Error deserializing personal reminder rule", e)
                                    null
                                }
                            }
                            .toMutableList()
                    }
                    
                    CheckItem(
                        operation = operation,
                        time = time,
                        rule = rule,
                        reminderRules = reminderRules,
                        isActive = false
                    )
                } else {
                    // Если парсинг не удался, возвращаем пустую строку
                    CheckItem(
                        operation = "",
                        time = "",
                        rule = "",
                        reminderRules = mutableListOf(),
                        isActive = false
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ChecksSchedule", "Error loading personal tasks", e)
            emptyList()
        }
        
        // Если загружено меньше 5 задач, добавляем пустые строки до 5
        val result = loadedTasks.toMutableList()
        while (result.size < 5) {
            result.add(CheckItem(
                operation = "",
                time = "",
                rule = "",
                reminderRules = mutableListOf(),
                isActive = false
            ))
        }
        
        return result
    }
    
    /**
     * Сохраняет личные задачи пользователя
     */
    private fun savePersonalTasks(items: List<CheckItem>) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Сохраняем задачи
        val tasksJson = items.joinToString("|||") { item ->
            "${item.operation};;${item.time};;${item.rule}"
        }
        editor.putString(KEY_PERSONAL_TASKS, tasksJson)
        
        // Сохраняем правила напоминаний для каждой задачи по индексу
        items.forEachIndexed { index, item ->
            val reminderRulesKey = "personal_reminder_$index"
            val reminderRulesJson = item.reminderRules.joinToString("|||") { it.serialize() }
            editor.putString(reminderRulesKey, reminderRulesJson)
        }
        
        editor.apply()
        Log.d("ChecksSchedule", "Сохранено ${items.size} личных задач")
    }
    
    /**
     * Загружает проверки из Excel (с сервера) или CSV (из ресурсов)
     */
    private fun loadChecksFromExcelOrCsv(): List<CheckItem> {
        val excelFile = File(requireContext().filesDir, "data/График проверок .xlsx")
        
        if (excelFile.exists()) {
            try {
                val parser = ChecksScheduleExcelParser()
                val excelItems = parser.parseExcelFile(excelFile.inputStream())
                
                return excelItems.map { item ->
                    CheckItem(
                        operation = item.operation,
                        time = "",
                        rule = "",
                        reminderRules = item.reminderRules.toMutableList(),
                        isActive = false
                    )
                }
            } catch (e: Exception) {
                Log.e("ChecksSchedule", "Error loading Excel file", e)
            }
        }
        
        // Если Excel нет или ошибка - загружаем из CSV
        return ScheduleCsvRepository(requireContext()).readAll()
    }

    /**
     * Применение темы к элементам интерфейса
     * Легковесный метод без создания лишних объектов
     */
    /**
     * Публичный метод для применения темы (вызывается из MainActivity)
     */
    override fun applyTheme() {
        // Создаём текущую тему (изолированную стратегию)
        currentTheme = ThemeFactory.createCurrentTheme()
        
        // Проверяем что фрагмент полностью инициализирован
        if (!isAdded || view == null) {
            Log.w("ChecksSchedule", "applyTheme() вызван но фрагмент не готов: isAdded=$isAdded, view=${view != null}")
            return
        }
        
        // Проверяем что все RecyclerView инициализированы
        if (!::recyclerHours.isInitialized || !::recyclerCalendar.isInitialized || !::recyclerTasks.isInitialized) {
            Log.w("ChecksSchedule", "applyTheme() вызван но RecyclerView не инициализированы")
            return
        }
        
        // Передаём тему адаптерам (после проверки инициализации)
        hoursAdapter.theme = currentTheme
        monthAdapter.theme = currentTheme
        tasksAdapter.theme = currentTheme
        
        Log.d("ChecksSchedule", "=== applyTheme() вызван ===")
        Log.d("ChecksSchedule", "isDataLoadedFlag=$isDataLoadedFlag")
        Log.d("ChecksSchedule", "hoursAdapter.itemCount=${hoursAdapter.itemCount}")
        Log.d("ChecksSchedule", "monthAdapter.itemCount=${monthAdapter.itemCount}")
        Log.d("ChecksSchedule", "tasksAdapter.itemCount=${tasksAdapter.itemCount}")
        
        view?.let { applyThemeToView(it) }
        
        // КРИТИЧНО: Если данные не загружены, загружаем их ПЕРЕД применением темы
        if (!isDataLoadedFlag) {
            Log.d("ChecksSchedule", "Данные не загружены, вызываем ensureDataLoaded()")
            ensureDataLoaded()
            // Принудительное обновление через 1.5 секунды для гарантии отображения
            view?.postDelayed({
                if (isAdded && isDataLoadedFlag) {
                    Log.d("ChecksSchedule", "⏰ Принудительное обновление через 1.5 сек (данные не были загружены)")
                    hoursAdapter.notifyDataSetChanged()
                    monthAdapter.notifyDataSetChanged()
                    tasksAdapter.notifyDataSetChanged()
                }
            }, 1500)
            return
        }
        
        // Проверяем что адаптеры содержат данные
        val hasHoursData = hoursAdapter.itemCount > 0
        val hasCalendarData = monthAdapter.itemCount > 0
        
        // Если адаптеры пустые (данные загружены но не отображены), используем полное обновление
        if (!hasHoursData || !hasCalendarData) {
            Log.d("ChecksSchedule", "Адаптеры пустые, выполняем полное обновление")
            hoursAdapter.notifyDataSetChanged()
            monthAdapter.notifyDataSetChanged()
            tasksAdapter.notifyDataSetChanged()
            
            // Дополнительное принудительное обновление через 1.5 секунды
            view?.postDelayed({
                if (isAdded) {
                    Log.d("ChecksSchedule", "⏰ Принудительное обновление через 1.5 сек (адаптеры были пустые)")
                    hoursAdapter.notifyDataSetChanged()
                    monthAdapter.notifyDataSetChanged()
                    tasksAdapter.notifyDataSetChanged()
                }
            }, 1500)
            return
        }
        
        Log.d("ChecksSchedule", "Данные есть, выполняем немедленное обновление")
        
        // ОПТИМИЗАЦИЯ: Убрали избыточные циклы по видимым элементам
        // notifyDataSetChanged() ниже и так обновит все элементы
        // Немедленное обновление для быстрого отклика
        hoursAdapter.notifyDataSetChanged()
        monthAdapter.notifyDataSetChanged()
        tasksAdapter.notifyDataSetChanged()
        
        // ГАРАНТИЯ: Принудительное полное обновление через 1.5 сек (для всех случаев)
        view?.postDelayed({
            if (isAdded) {
                Log.d("ChecksSchedule", "⏰ ПРИНУДИТЕЛЬНОЕ полное обновление через 1.5 сек")
                Log.d("ChecksSchedule", "Состояние: isAdded=$isAdded, isVisible=$isVisible, isResumed=$isResumed")
                hoursAdapter.notifyDataSetChanged()
                monthAdapter.notifyDataSetChanged()
                tasksAdapter.notifyDataSetChanged()
                Log.d("ChecksSchedule", "✅ Обновление выполнено!")
            } else {
                Log.w("ChecksSchedule", "⚠️ Обновление НЕ выполнено: фрагмент не добавлен")
            }
        }, 1500)
    }
    
    private fun applyThemeToView(view: View) {
        // Применяем цвет фона из стратегии темы
        view.setBackgroundColor(currentTheme.getBackgroundColor())
        tvNow.setTextColor(currentTheme.getTextPrimaryColor())
        
        // Асинхронно загружаем фоновое изображение (если есть)
        // ЗАЩИТА: предотвращаем множественные одновременные загрузки
        if (!isLoadingBackground) {
            isLoadingBackground = true
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bgDrawable = currentTheme.getBackgroundDrawable(requireContext())
                    if (bgDrawable != null && isAdded) {
                        withContext(Dispatchers.Main) {
                            if (isAdded && view.isAttachedToWindow) {
                                view.background = bgDrawable
                            }
                        }
                    }
                } finally {
                    isLoadingBackground = false
                }
            }
        } else {
            Log.d("ChecksSchedule", "Загрузка фона уже в процессе, пропускаем")
        }
        
        // Применяем стили к кнопкам (для всех тем, включая классическую)
        applyToggleStyle(btnEditMode, btnEditMode.isChecked)
        applyToggleStyle(btnPersonalMode, btnPersonalMode.isChecked)
        applyCalendarToggleStyle(btnToggleCalendar)
    }
    
    private fun applyToggleStyle(button: ToggleButton, isChecked: Boolean) {
        if (!AppTheme.shouldApplyTheme()) {
            // Классическая тема - восстанавливаем фиолетовый фон
            button.setBackgroundResource(R.drawable.bg_zoom_button)
            button.setTextColor(android.graphics.Color.WHITE)
            
            // График проверок: увеличиваем на 2dp (0.5мм)
            val px = button.context.resources.displayMetrics.density
            val paddingH = ((AppTheme.getButtonPaddingHorizontal() + 2) * px).toInt()
            val paddingV = ((AppTheme.getButtonPaddingVertical() + 2) * px).toInt()
            button.setPadding(paddingH, paddingV, paddingH, paddingV)
            button.minHeight = 0
            button.minWidth = 0
            return
        }
        
        // Сбрасываем Material tint который может перекрывать background
        try {
            button.backgroundTintList = null
        } catch (_: Throwable) {}
        
        // Используем gradient для Росатома и Брутальной темы
        val drawable = if (isChecked) {
            AppTheme.createGradientButtonDrawable() ?: AppTheme.createButtonDrawable(AppTheme.getPrimaryColor())
        } else {
            AppTheme.createButtonDrawable(AppTheme.getButtonColor())
        }
        
        drawable?.let { button.background = it }
        button.setTextColor(AppTheme.getButtonTextColor())
        
        // Применяем размеры и elevation (уменьшенные для экономии места)
        val baseTextSize = AppTheme.getButtonTextSize()
        // Для брутальной темы дополнительно уменьшаем шрифт на 2 единицы (было 1, теперь 2)
        val adjustedSize = if (AppTheme.getCurrentThemeId() == 4) { // THEME_MODERN_GRADIENT
            baseTextSize - 2f
        } else {
            baseTextSize
        }
        val finalSizeSp = (adjustedSize * 0.85f).coerceAtLeast(10f)
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, finalSizeSp)
        val px = button.context.resources.displayMetrics.density
        // График проверок: увеличиваем на 2dp (0.5мм)
        val paddingH = ((AppTheme.getButtonPaddingHorizontal() + 2) * px).toInt()
        val paddingV = ((AppTheme.getButtonPaddingVertical() + 2) * px).toInt()
        
        button.setPadding(paddingH, paddingV, paddingH, paddingV)
        button.minHeight = 0
        button.minWidth = 0
        button.elevation = (AppTheme.getButtonElevation() * 0.6f * px)
    }

    private fun applyButtonStyle(button: Button) {
        if (!AppTheme.shouldApplyTheme()) return
        
        // Используем gradient для некоторых тем
        val drawable = AppTheme.createGradientButtonDrawable() ?: AppTheme.createButtonDrawable()
        drawable?.let { button.background = it }
        
        // Полный профессиональный стиль
        AppTheme.applyButtonStyle(button)
    }

    private fun applyCalendarToggleStyle(textView: TextView) {
        if (!AppTheme.shouldApplyTheme()) {
            // Классическая тема - восстанавливаем фиолетовый фон
            textView.setBackgroundResource(R.drawable.bg_circle_button)
            textView.setTextColor(android.graphics.Color.WHITE)
            return
        }

        // Для других тем - просто меняем цвет фона
        textView.post {
            // Создаём круглый фон
            val circle = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(AppTheme.getAccentColor())
            }
            
            // Устанавливаем новый фон
            textView.background = circle
            
            // Устанавливаем белый текст
            textView.setTextColor(Color.WHITE)
        }
    }

    override fun refreshData() {
        isDataLoadedFlag = false
        loadChecksScheduleData()
    }

    override fun isDataLoaded(): Boolean = isDataLoadedFlag

    override fun onPause() {
        super.onPause()
        // Приостанавливаем таймер обновления времени
        stopTicks()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopTicks()
        
        // Очищаем коллбеки для предотвращения утечек памяти
        hoursAdapter.onSelectionChanged = null
        monthAdapter.onSelectionChanged = null
        tasksAdapter.onSaveRequested = null
        
        // Очищаем контексты в адаптерах
        monthAdapter.clearContext()
        tasksAdapter.clearContext()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // КРИТИЧНО: Останавливаем Handler для предотвращения утечки памяти
        tickHandler.removeCallbacksAndMessages(null)
    }

    override fun getWatchedFilePath(): String? {
        return try {
            // Отслеживаем Excel файл с графиком проверок (с пробелом в названии!)
            java.io.File(requireContext().filesDir, "data/График проверок .xlsx").absolutePath
        } catch (_: Throwable) { null }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Регистрируем фрагмент в ThemeManager
        com.example.vkbookandroid.theme.ThemeManager.registerFragment(this)
        
        // Загружаем данные только если фрагмент видим
        if (isVisible) ensureDataLoaded()
        if (isDataLoadedFlag) {
            startTicks()
        }
    }
    
    override fun isFragmentReady(): Boolean {
        return ::recyclerHours.isInitialized && ::recyclerCalendar.isInitialized && view != null
    }

    private fun startTicks() {
        tickHandler.removeCallbacksAndMessages(null)
        tickHandler.post(tickRunnable)
    }

    private fun stopTicks() {
        tickHandler.removeCallbacksAndMessages(null)
    }
}

// ===== Вспомогательные функции для работы с цветами =====
/**
 * Осветление цвета (для создания пастельных оттенков)
 * factor: 0.0 = исходный цвет, 1.0 = белый
 */
private fun lighten(color: Int, factor: Float): Int {
    val r = android.graphics.Color.red(color)
    val g = android.graphics.Color.green(color)
    val b = android.graphics.Color.blue(color)
    
    val newR = (r + (255 - r) * factor).toInt().coerceIn(0, 255)
    val newG = (g + (255 - g) * factor).toInt().coerceIn(0, 255)
    val newB = (b + (255 - b) * factor).toInt().coerceIn(0, 255)
    
    return android.graphics.Color.rgb(newR, newG, newB)
}

// ===== Шкала часов =====
private data class HourCell(
    val hour: Int, 
    val isNow: Boolean, 
    val dayLabel: String? = null,  // Метка дня для часов из соседних дней (например, "Ср" или "+1д")
    val dayOffset: Int = 0  // -1 = предыдущий день, 0 = текущий день, 1 = следующий день
)

private class HoursAdapter : RecyclerView.Adapter<HoursAdapter.VH>() {
    private val items = mutableListOf<HourCell>()
    private val selectedPositions = mutableSetOf<Int>()  // Теперь храним ПОЗИЦИИ, а не часы
    var onSelectionChanged: (() -> Unit)? = null
    lateinit var theme: ThemeStrategy // Стратегия темы (поздняя инициализация)
    
    fun submit(list: List<HourCell>, resetSelection: Boolean = false) { 
        items.clear()
        items.addAll(list)
        // Изначально выделяем все часы в интервале текущего часа ТОЛЬКО при первой загрузке
        if (resetSelection) {
            val nowH = LocalDateTime.now().hour
            val activeRange = getRangeForHour(nowH)
            selectedPositions.clear()
            
            // КРИТИЧНО: Ночная смена (20-07) охватывает ДВА дня!
            when {
                // 1. Если сейчас 00:00-07:59 (ночь), выделяем:
                //    - 20-23 ПРЕДЫДУЩЕГО дня (dayOffset=-1)
                //    - 00-07 ТЕКУЩЕГО дня (dayOffset=0)
                nowH in 0..7 -> {
                    items.forEachIndexed { index, cell ->
                        val inNightShift = (cell.hour in 20..23 && cell.dayOffset == -1) || 
                                         (cell.hour in 0..7 && cell.dayOffset == 0)
                        if (inNightShift) {
                            selectedPositions.add(index)
                        }
                    }
                }
                // 2. Если сейчас 08:00-19:59 (день), выделяем только текущую дневную смену
                nowH in 8..19 -> {
                    items.forEachIndexed { index, cell ->
                        if (cell.dayOffset == 0 && getRangeForHour(cell.hour) == activeRange) {
                            selectedPositions.add(index)
                        }
                    }
                }
                // 3. Если сейчас 20:00-23:59 (вечер), выделяем:
                //    - 20-23 ТЕКУЩЕГО дня (dayOffset=0)
                //    - 00-07 СЛЕДУЮЩЕГО дня (dayOffset=1)
                nowH in 20..23 -> {
                    items.forEachIndexed { index, cell ->
                        val inNightShift = (cell.hour in 20..23 && cell.dayOffset == 0) || 
                                         (cell.hour in 0..7 && cell.dayOffset == 1)
                        if (inNightShift) {
                            selectedPositions.add(index)
                        }
                    }
                }
            }
        }
        notifyDataSetChanged()
    }
    
    fun getSelectedCells(): List<HourCell> {
        return selectedPositions.map { items[it] }
    }
    
    fun getSelectedHours(): Set<Int> = selectedPositions.mapNotNull { 
        if (it < items.size) items[it].hour else null 
    }.toSet()
    
    fun setEditMode(enabled: Boolean) { /* Можно добавить логику при необходимости */ }
    override fun getItemCount() = items.size
    
    private fun getRangeForHour(h: Int): Int {
        return when {
            inRange(h, 8, 14) -> 1  // 08:00 - 13:59 (с 8 до 13)
            inRange(h, 14, 20) -> 2  // 14:00 - 19:59 (с 14 до 19)
            else -> 3 // 20:00 - 07:59 (с 20 до 7 утра)
        }
    }
    
    private fun inRange(h: Int, startIncl: Int, endExcl: Int): Boolean {
        return if (startIncl <= endExcl) h >= startIncl && h < endExcl else (h >= startIncl || h < endExcl)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context)
        val lp = RecyclerView.LayoutParams((parent.resources.displayMetrics.density*45).toInt(), RecyclerView.LayoutParams.MATCH_PARENT)
        tv.layoutParams = lp
        tv.textSize = 14f
        tv.gravity = android.view.Gravity.CENTER
        tv.setPadding(4,4,4,4)
        return VH(tv, { position -> selectedPositions.contains(position) })
    }
    
    override fun onBindViewHolder(holder: VH, position: Int) {
        val cell = items[position]
        val isSelected = selectedPositions.contains(position)
        holder.bind(cell, isSelected, theme)
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                // Переключение выделения по ПОЗИЦИИ
                if (selectedPositions.contains(pos)) {
                    selectedPositions.remove(pos)
                } else {
                    Log.d("ChecksSchedule", "Выбран час: ${items[pos].hour} (dayOffset=${items[pos].dayOffset})")
                    selectedPositions.add(pos)
                }
                Log.d("ChecksSchedule", "Выбранные ячейки: ${getSelectedCells().map { "(${it.hour}h, day${it.dayOffset})" }}")
                notifyItemChanged(pos, true)
                onSelectionChanged?.invoke()
            }
        }
    }
    class VH(private val tv: TextView, private val isSelected: (Int) -> Boolean) : RecyclerView.ViewHolder(tv) {
        fun bind(c: HourCell, selected: Boolean, theme: ThemeStrategy) {
            // Отображаем час с меткой дня для соседних дней
            tv.text = if (c.dayLabel != null) {
                String.format(Locale.getDefault(), "%02d\n(%s)", c.hour, c.dayLabel)
            } else {
                String.format(Locale.getDefault(), "%02d", c.hour)
            }
            
            val nowH = LocalDateTime.now().hour
            val activeRange = rangeOf(nowH)
            val currentRange = rangeOf(c.hour)
            
            // КРИТИЧНО: Ночная смена (20-07) охватывает ДВА дня!
            // Определяем, является ли ячейка активной (в текущей смене)
            val inActive = when {
                // Если сейчас 00:00-07:59 (ночь), активны:
                // - 20-23 предыдущего дня (dayOffset=-1)
                // - 00-07 текущего дня (dayOffset=0)
                nowH in 0..7 -> {
                    (c.hour in 20..23 && c.dayOffset == -1) || (c.hour in 0..7 && c.dayOffset == 0)
                }
                // Если сейчас 08:00-19:59 (день), активны часы текущей дневной смены с dayOffset=0
                nowH in 8..19 -> {
                    currentRange == activeRange && c.dayOffset == 0
                }
                // Если сейчас 20:00-23:59 (вечер), активны:
                // - 20-23 текущего дня (dayOffset=0)
                // - 00-07 следующего дня (dayOffset=1)
                nowH in 20..23 -> {
                    (c.hour in 20..23 && c.dayOffset == 0) || (c.hour in 0..7 && c.dayOffset == 1)
                }
                else -> false
            }
            
            // Получаем цвета из изолированной стратегии темы (вместо 86 строк if-else!)
            val style = theme.getHourCellColors(
                hour = c.hour,
                isSelected = selected,
                isActive = inActive,
                dayOffset = c.dayOffset
            )
            val bg = style.backgroundColor
            val fg = style.textColor
            tv.setTextColor(fg)

            val strokeColor = when {
                selected -> Color.BLACK
                c.isNow -> theme.getCurrentHourBorderColor()  // Используем цвет из темы (для брутальной - черный)
                else -> null
            }
            val density = tv.resources.displayMetrics.density
            val strokeWidthPx = if (strokeColor != null) {
                (density * 2.5f).toInt().coerceAtLeast(3)
            } else {
                0
            }
            // Используем cornerRadius из темы (для брутальной - 10dp, для других - из AppTheme или 0)
            val cornerRadiusPx = if (AppTheme.shouldApplyTheme()) {
                theme.getHourCellCornerRadius() * density
            } else {
                0f
            }
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerRadiusPx
                setColor(bg)
                if (strokeColor != null && strokeWidthPx > 0) {
                    setStroke(strokeWidthPx, strokeColor)
                }
            }
            tv.background = drawable
        }
        private fun inRange(h: Int, startIncl: Int, endExcl: Int): Boolean {
            return if (startIncl <= endExcl) h >= startIncl && h < endExcl else (h >= startIncl || h < endExcl)
        }
        private fun rangeOf(h: Int): Int {
            return when {
                inRange(h, 8, 14) -> 1   // 08:00 - 13:59 (с 8 до 13)
                inRange(h, 14, 20) -> 2  // 14:00 - 19:59 (с 14 до 19)
                else -> 3 // 20:00 - 07:59 (с 20 до 7 утра)
            }
        }
    }
    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // только перекраска при выборе
            val isSelected = selectedPositions.contains(position)
            holder.bind(items[position], isSelected, theme)
        }
    }
    
}

// ===== Календарная шкала месяца =====
private data class DayCell(val date: LocalDate?, val isToday: Boolean)
private data class DayOverride(val label: String?, val heightDp: Int?)

private class MonthAdapter : RecyclerView.Adapter<MonthAdapter.VH>() {
    private val items = mutableListOf<DayCell>()
    private val selected = mutableSetOf<LocalDate>()
    private var columnWidths = mutableMapOf<Int, Int>() // Индекс колонки (0-6) -> ширина в dp
    var onSelectionChanged: (() -> Unit)? = null
    lateinit var theme: ThemeStrategy // Стратегия темы (поздняя инициализация)
    
    fun submit(list: List<DayCell>, resetSelection: Boolean = false) { 
        items.clear()
        items.addAll(list)
        // Изначально выделяем сегодняшний день ТОЛЬКО при первой загрузке
        if (resetSelection) {
            val today = LocalDate.now()
            selected.clear()
            selected.add(today)
        }
        notifyDataSetChanged()
    }
    
    fun getSelectedDates(): Set<LocalDate> = selected.toSet()
    
    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context)
        val height = (parent.resources.displayMetrics.density*35).toInt() // Увеличено с 28 до 35 (на 25%)
        val defaultWidth = RecyclerView.LayoutParams.MATCH_PARENT
        tv.layoutParams = RecyclerView.LayoutParams(defaultWidth, height)
        tv.textSize = 13f // Увеличено с 11 до 13
        tv.gravity = android.view.Gravity.CENTER
        tv.setPadding(3,3,3,3) // Увеличено с 2 до 3
        return VH(tv, ::onDayClick, ::isSelected, { editMode }, { overrides[it] }, ::onEdit, { columnIndex -> columnWidths[columnIndex] }, ::onEditColumnWidth)
    }
    override fun onBindViewHolder(holder: VH, position: Int) {
        val columnIndex = position % 7
        holder.bind(items[position], columnIndex, theme)
    }
    private fun onDayClick(date: LocalDate) {
        if (!selected.add(date)) {
            Log.d("ChecksSchedule", "Снят выбор даты: $date")
            selected.remove(date)
        } else {
            Log.d("ChecksSchedule", "Выбрана дата: $date")
        }
        Log.d("ChecksSchedule", "Все выбранные даты: $selected")
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }
    private fun isSelected(date: LocalDate): Boolean = selected.contains(date)
    class VH(private val tv: TextView,
             private val onClick: (LocalDate) -> Unit,
             private val isSel: (LocalDate) -> Boolean,
             private val getEditMode: () -> Boolean,
             private val getOverride: (LocalDate) -> DayOverride?,
             private val onEditClick: (LocalDate, TextView) -> Unit,
             private val getColumnWidth: (Int) -> Int?,
             private val onEditColumnWidth: (Int) -> Unit) : RecyclerView.ViewHolder(tv) {
        override fun toString(): String = super.toString()
        fun bind(d: DayCell, columnIndex: Int, theme: ThemeStrategy) {
            if (d.date == null) {
                tv.text = ""
                tv.setBackgroundColor(0x00000000)
                // Применяем ширину колонки
                getColumnWidth(columnIndex)?.let { widthDp ->
                    val widthPx = (tv.resources.displayMetrics.density * widthDp).toInt()
                    val height = tv.layoutParams.height
                    tv.layoutParams = RecyclerView.LayoutParams(widthPx, height)
                } ?: run {
                    val height = tv.layoutParams.height
                    tv.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, height)
                }
                if (getEditMode()) {
                    tv.setOnClickListener { onEditColumnWidth(columnIndex) }
                    tv.setOnLongClickListener(null)
                } else {
                    tv.setOnClickListener(null)
                    tv.setOnLongClickListener(null)
                }
                return
            }
            val dow = d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru"))
            val custom = getOverride(d.date)
            val label = custom?.label ?: "${d.date.dayOfMonth}\n${dow}"
            tv.text = label
            // кастомная высота
            val height = custom?.heightDp?.let { (tv.resources.displayMetrics.density * it).toInt() } 
                ?: tv.layoutParams.height
            // Применяем ширину колонки или используем MATCH_PARENT
            val width = getColumnWidth(columnIndex)?.let { (tv.resources.displayMetrics.density * it).toInt() }
                ?: RecyclerView.LayoutParams.MATCH_PARENT
            tv.layoutParams = RecyclerView.LayoutParams(width, height)
            val selected = isSel(d.date)
            
            // Получаем цвета из изолированной стратегии темы
            val dayOfWeek = d.date.dayOfWeek.value // 1..7
            val dayStyle = theme.getCalendarDayColors(
                isToday = d.isToday,
                isSelected = selected,
                dayOfWeek = dayOfWeek
            )
            val bgColor = dayStyle.backgroundColor
            val textColor = dayStyle.textColor
            
            // Применяем цвета
            if (d.isToday) {
                // Для сегодняшней даты: получаем стиль рамки из темы
                val borderStyle = theme.getTodayBorderStyle()
                val density = tv.resources.displayMetrics.density
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = borderStyle.radiusDp * density
                    setColor(bgColor)
                    setStroke((borderStyle.widthDp * density).toInt(), borderStyle.color)
                }
                tv.background = drawable
                tv.setTextColor(textColor)
            } else {
                // Для обычных дат
                if (!AppTheme.shouldApplyTheme()) {
                    // Классическая тема - простые цвета без drawable
                    tv.setBackgroundColor(bgColor)
                    tv.setTextColor(textColor)
                } else {
                    // Другие темы - с drawable
                    val drawable = AppTheme.createCellDrawable(bgColor)
                    if (drawable != null) {
                        tv.background = drawable
                    } else {
                        tv.setBackgroundColor(bgColor)
                    }
                    tv.setTextColor(textColor)
                }
            }
            
            if (getEditMode()) {
                tv.setOnClickListener { onEditClick(d.date, tv) }
                tv.setOnLongClickListener {
                    onEditColumnWidth(columnIndex)
                    true
                }
            } else {
                tv.setOnClickListener {
                    onClick(d.date) // toggle выделения - если выделен, снимет, если нет - выделит
                }
                tv.setOnLongClickListener(null)
            }
        }
    }
    private var editMode: Boolean = false
    private val overrides = mutableMapOf<LocalDate, DayOverride>()
    fun setEditMode(enabled: Boolean) { 
        editMode = enabled
        notifyDataSetChanged()
    }
    private fun onEdit(date: LocalDate, tv: TextView) {
        val ctx = tv.context
        val container = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(24,8,24,0) }
        val inputLabel = android.widget.EditText(ctx).apply { hint = "Текст ячейки"; setText(overrides[date]?.label ?: "") }
        val inputHeight = android.widget.EditText(ctx).apply { hint = "Высота dp"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(overrides[date]?.heightDp?.toString() ?: "35") }
        container.addView(inputLabel); container.addView(inputHeight)
        AlertDialog.Builder(ctx)
            .setTitle("Редактировать день ${date}")
            .setView(container)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить") { _, _ ->
                val lbl = inputLabel.text.toString().ifBlank { null }
                val h = inputHeight.text.toString().toIntOrNull()
                overrides[date] = DayOverride(lbl, h)
                notifyDataSetChanged()
            }
            .show()
    }
    private var contextForDialog: android.content.Context? = null
    private fun onEditColumnWidth(columnIndex: Int) {
        val ctx = contextForDialog ?: return
        val container = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(24,8,24,0) }
        val inputWidth = android.widget.EditText(ctx).apply { 
            hint = "Ширина колонки (dp)"; 
            inputType = android.text.InputType.TYPE_CLASS_NUMBER; 
            setText(columnWidths[columnIndex]?.toString() ?: "") 
        }
        container.addView(inputWidth)
        AlertDialog.Builder(ctx)
            .setTitle("Изменить ширину колонки ${columnIndex + 1}")
            .setView(container)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить") { _, _ ->
                val width = inputWidth.text.toString().toIntOrNull()
                if (width != null && width > 0) {
                    columnWidths[columnIndex] = width
                } else {
                    columnWidths.remove(columnIndex)
                }
                notifyDataSetChanged()
            }
            .show()
    }
    fun setContext(ctx: android.content.Context) {
        contextForDialog = ctx
    }
    
    fun clearContext() {
        contextForDialog = null
    }
}

// ===== Helpers =====
private fun ChecksScheduleFragment.updateNow() {
    val now = LocalDateTime.now()
    val ym = YearMonth.from(now)
    tvNow.text = "${now.toLocalDate()}  ${String.format(Locale.getDefault(), "%02d:%02d", now.hour, now.minute)}"
}

private fun ChecksScheduleFragment.buildHours(selectedDates: Set<LocalDate> = emptySet()): List<HourCell> {
    val now = LocalDateTime.now()
    val nowH = now.hour
    val today = now.toLocalDate()
    
    // РАСШИРЕННАЯ 36-часовая шкала для ПОЛНОГО обзора смены:
    // [20-23 пред.день] + [00-23 тек.день] + [00-07 след.день]
    // Пример: если сейчас 05.12 00:00 (пятница), шкала покажет:
    // [20-23 четверг] + [00-23 пятница] + [00-07 суббота]
    // Ночная смена 20:00 чт → 07:00 пт будет видна целиком
    return buildList {
        // 1. Часы 20-23 ПРЕДЫДУЩЕГО дня (dayOffset = -1)
        val prevDayLabel = when (selectedDates.size) {
            1 -> {
                val selectedDate = selectedDates.first()
                val prevDay = selectedDate.minusDays(1)
                prevDay.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru", "RU"))
            }
            else -> "-1д"
        }
        for (h in 20..23) {
            add(HourCell(hour = h, isNow = false, dayLabel = prevDayLabel, dayOffset = -1))
        }
        
        // 2. Часы 00-23 ТЕКУЩЕГО дня (dayOffset = 0)
        for (h in 0..23) {
            add(HourCell(hour = h, isNow = (h == nowH), dayLabel = null, dayOffset = 0))
        }
        
        // 3. Часы 00-07 СЛЕДУЮЩЕГО дня (dayOffset = 1)
        val nextDayLabel = when (selectedDates.size) {
            1 -> {
                val selectedDate = selectedDates.first()
                val nextDay = selectedDate.plusDays(1)
                nextDay.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru", "RU"))
            }
            else -> "+1д"
        }
        for (h in 0..7) {
            add(HourCell(hour = h, isNow = false, dayLabel = nextDayLabel, dayOffset = 1))
        }
    }
}

// ===== Таблица задач (CSV) =====
private data class CheckItem(
    val operation: String, 
    val time: String, 
    val rule: String, 
    var reminderRules: MutableList<ReminderRule> = mutableListOf(),
    var isActive: Boolean = false
)

private class TasksAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<CheckItem>()
    private val originalOrder = mutableListOf<CheckItem>()
    private var editMode: Boolean = false
    private var contextForDialog: android.content.Context? = null
    private val columnWidths = mutableMapOf<Int, Int>()
    lateinit var theme: ThemeStrategy // Стратегия темы (поздняя инициализация)
    private val headers = listOf("Операция", "Повторения")
    var onSaveRequested: ((List<CheckItem>) -> Unit)? = null // Коллбек для сохранения личных задач
    
    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }
    
    fun submit(list: List<CheckItem>) { 
        items.clear()
        items.addAll(list)
        originalOrder.clear()
        // ВАЖНО: Сохраняем ГЛУБОКИЕ копии объектов, чтобы изменения isActive не влияли на originalOrder
        originalOrder.addAll(list.map { 
            CheckItem(
                operation = it.operation,
                time = it.time,
                rule = it.rule,
                reminderRules = it.reminderRules.toMutableList(),
                isActive = it.isActive
            )
        })
        
        // Загружаем сохраненные правила напоминаний ТОЛЬКО если их нет в элементе
        // И ТОЛЬКО для личных задач (если onSaveRequested != null)
        if (onSaveRequested != null) {
            contextForDialog?.let { loadReminderRules(it) }
        }
        
        // НЕ сохраняем правила из Excel в SharedPrefs для служебных задач
        // Служебные задачи всегда загружаются из Excel/CSV и не должны сохраняться
        
        notifyDataSetChanged()
    }
    
    fun updateActiveStatus(selectedCells: List<HourCell> = emptyList(), selectedDates: Set<LocalDate> = emptySet()) {
        Log.d("ChecksSchedule", "TasksAdapter.updateActiveStatus: cells=${selectedCells.map { "(${it.hour}h, day${it.dayOffset})" }}, dates=$selectedDates")
        
        // Проверяем только если пользователь что-то выбрал
        if (selectedCells.isEmpty() || selectedDates.isEmpty()) {
            Log.d("ChecksSchedule", "Снимаем активность со всех задач (пустой выбор)")
            // Снимаем активность со всех задач
            items.forEach { it.isActive = false }
            applyChanges()
            return
        }
        
        Log.d("ChecksSchedule", "Проверяем ${items.size} задач на активность")
        
        items.forEach { item ->
            // Если нет правил напоминаний - не выделяем
            if (item.reminderRules.isEmpty()) {
                item.isActive = false
                return@forEach
            }
            
            // ОПТИМИЗАЦИЯ: Используем any() вместо тройного вложенного цикла
            // Это позволяет выйти сразу при первом совпадении
            val isActive = item.reminderRules.any { rule ->
                selectedDates.any { date ->
                    selectedCells.any { cell ->
                        // Применяем dayOffset для правильного расчета даты
                        val actualDate = date.plusDays(cell.dayOffset.toLong())
                        val testDateTime = LocalDateTime.of(actualDate, java.time.LocalTime.of(cell.hour, 0))
                        rule.matches(testDateTime)
                    }
                }
            }
                
            item.isActive = isActive
            if (isActive) {
                Log.d("ChecksSchedule", "Задача АКТИВНА: ${item.operation.take(30)}")
            }
        }
        
        val activeCount = items.count { it.isActive }
        Log.d("ChecksSchedule", "Активных задач: $activeCount из ${items.size}")
        
        applyChanges()
    }
    
    private fun applyChanges() {
        val hasActiveItems = items.any { it.isActive }
        
        val newList = if (hasActiveItems) {
            // Активные задачи в начале, неактивные после
            val activeItems = items.filter { it.isActive }
            val inactiveItems = items.filter { !it.isActive }
            activeItems + inactiveItems
        } else {
            // Когда нет активных задач, показываем ВСЕ задачи из items
            items.toList()
        }
        
        items.clear()
        items.addAll(newList)
        
        // ИСПРАВЛЕНИЕ: Используем notifyDataSetChanged() вместо DiffUtil
        // Причина: payload обновления через DiffUtil не работают корректно 
        // при переиспользовании ViewHolder, что приводит к тому что 
        // updateActiveState обновляет неправильные view
        notifyDataSetChanged()
    }
    
    fun setEditMode(enabled: Boolean) { 
        editMode = enabled
        notifyDataSetChanged()
    }
    fun setContext(ctx: android.content.Context) {
        contextForDialog = ctx
        loadColumnWidths(ctx)
    }
    
    fun clearContext() {
        contextForDialog = null
    }
    
    private fun loadColumnWidths(ctx: android.content.Context) {
        val prefs = ctx.getSharedPreferences("ChecksSchedulePrefs", android.content.Context.MODE_PRIVATE)
        columnWidths[0] = prefs.getInt("column_width_0", ctx.dpToPx(200))
        columnWidths[1] = prefs.getInt("column_width_1", ctx.dpToPx(300))
    }
    
    private fun saveColumnWidths(ctx: android.content.Context) {
        val prefs = ctx.getSharedPreferences("ChecksSchedulePrefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("column_width_0", columnWidths[0] ?: ctx.dpToPx(200))
            putInt("column_width_1", columnWidths[1] ?: ctx.dpToPx(300))
            apply()
        }
    }
    
    override fun getItemViewType(position: Int): Int = if (position == 0) TYPE_HEADER else TYPE_ITEM
    override fun getItemCount() = items.size + 1 // +1 для заголовка
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_check_task, parent, false)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(v, ::onColumnResize)
        } else {
            ItemVH(v, { editMode }, ::onEditItem, ::onAddRecurrenceRule, ::onEditRecurrenceRule, ::onDeleteRecurrenceRule, ::onAddRow, ::onDeleteRow, { items }, ::onColumnResize)
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == TYPE_HEADER) {
            (holder as HeaderVH).bind(headers, columnWidths, holder.itemView.context, editMode, theme)
        } else {
            val item = items[position - 1] // -1 потому что 0 - заголовок
            (holder as ItemVH).bind(item, position - 1, headers, columnWidths, holder.itemView.context, theme)
        }
    }
    private fun onEditItem(position: Int, columnIndex: Int, currentValue: String) {
        val ctx = contextForDialog ?: return
        val item = items.getOrNull(position) ?: return
        
        // Только колонка "Операция" (0) редактируется текстом
        if (columnIndex == 0) {
        val container = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(24,8,24,0) }
        val input = android.widget.EditText(ctx).apply { 
                hint = "Название операции"; 
            setText(currentValue) 
        }
        container.addView(input)
        AlertDialog.Builder(ctx)
                .setTitle("Редактировать операцию")
            .setView(container)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить") { _, _ ->
                val newValue = input.text.toString()
                    val updatedItem = item.copy(operation = newValue)
                items[position] = updatedItem
                notifyItemChanged(position + 1) // +1 т.к. есть заголовок
            }
            .show()
        }
    }
    
    private fun onAddRecurrenceRule(position: Int) {
        val ctx = contextForDialog ?: return
        val item = items.getOrNull(position) ?: return
        
        UniversalReminderDialog(ctx, ReminderRule()) { newRule ->
            // Проверяем что хотя бы что-то настроено
            val isValid = newRule.selectedHours.isNotEmpty() && 
                         (newRule.selectedDaysOfWeek.isNotEmpty() || 
                          newRule.advancedType != "NONE")
            
            if (isValid) {
                item.reminderRules.add(newRule)
                saveReminderRules(ctx)
                notifyItemChanged(position + 1)
                
                // Логируем добавление напоминания
                val ruleType = when {
                    newRule.advancedType == "MONTHLY_BY_WEEKDAY" -> "monthly_by_weekday"
                    newRule.advancedType == "MONTHLY_BY_DATE" -> "monthly_by_date"
                    newRule.daysOfMonth.isNotEmpty() -> "specific_dates"
                    else -> "weekly"
                }
                com.example.vkbookandroid.analytics.AnalyticsManager.logReminderAdded(ruleType)
            }
        }.show()
    }
    
    private fun onEditRecurrenceRule(position: Int, ruleIndex: Int) {
        val ctx = contextForDialog ?: return
        val item = items.getOrNull(position) ?: return
        val rule = item.reminderRules.getOrNull(ruleIndex) ?: return
        
        UniversalReminderDialog(ctx, rule) { newRule ->
            val isValid = newRule.selectedHours.isNotEmpty() && 
                         (newRule.selectedDaysOfWeek.isNotEmpty() || 
                          newRule.advancedType != "NONE")
            
            if (isValid) {
                item.reminderRules[ruleIndex] = newRule
                saveReminderRules(ctx)
                notifyItemChanged(position + 1)
            }
        }.show()
    }
    
    private fun onDeleteRecurrenceRule(position: Int, ruleIndex: Int) {
        val ctx = contextForDialog ?: return
        val item = items.getOrNull(position) ?: return
        
        AlertDialog.Builder(ctx)
            .setTitle("Удалить правило?")
            .setMessage("Удалить правило: ${item.reminderRules.getOrNull(ruleIndex)?.toCompactString()}?")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ ->
                if (ruleIndex < item.reminderRules.size) {
                    item.reminderRules.removeAt(ruleIndex)
                    saveReminderRules(ctx)
                    notifyItemChanged(position + 1)
                }
            }
            .show()
    }
    
    private fun onAddRow(position: Int) {
        // Добавляем новую пустую строку после текущей позиции
        val newItem = CheckItem("", "", "", mutableListOf(), false)
        items.add(position + 1, newItem)
        notifyItemInserted(position + 2) // +2 т.к. есть заголовок
        contextForDialog?.let { saveItems(it) }
    }
    
    private fun onDeleteRow(position: Int) {
        val ctx = contextForDialog ?: return
        
        AlertDialog.Builder(ctx)
            .setTitle("Удалить строку?")
            .setMessage("Удалить пустую строку?")
            .setPositiveButton("Удалить") { _, _ ->
                if (position in items.indices) {
                    items.removeAt(position)
                    notifyItemRemoved(position + 1) // +1 т.к. есть заголовок
                    saveItems(ctx)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun saveItems(ctx: android.content.Context) {
        // ИСПРАВЛЕНИЕ: Сохраняем ТОЛЬКО личные задачи
        // Служебные задачи не должны сохраняться - они всегда загружаются из Excel/CSV
        if (onSaveRequested != null) {
            // Это личные задачи - сохраняем через коллбек
            onSaveRequested?.invoke(items)
        }
        // Служебные задачи НЕ сохраняем в SharedPreferences
    }
    
    private fun saveReminderRules(ctx: android.content.Context) {
        // ИСПРАВЛЕНИЕ: Сохраняем ТОЛЬКО личные задачи
        // Служебные задачи не должны сохраняться - они всегда загружаются из Excel/CSV
        if (onSaveRequested != null) {
            // Это личные задачи - сохраняем через коллбек
            onSaveRequested?.invoke(items)
        }
        // Служебные задачи НЕ сохраняем в SharedPreferences
    }
    
    private fun loadReminderRules(ctx: android.content.Context) {
        // ИСПРАВЛЕНИЕ: Загружаем правила ТОЛЬКО для личных задач
        // Служебные задачи всегда загружаются из Excel/CSV с их правилами
        if (onSaveRequested == null) {
            // Это служебные задачи - не загружаем из SharedPreferences
            return
        }
        
        val prefs = ctx.getSharedPreferences("ChecksSchedulePrefs", android.content.Context.MODE_PRIVATE)
        
        items.forEach { item ->
            // Если у элемента уже есть правила (из Excel), НЕ перезаписываем их
            if (item.reminderRules.isNotEmpty()) {
                return@forEach
            }
            
            val key = "reminder_list_${item.operation}"
            val serialized = prefs.getString(key, null)
            
            if (!serialized.isNullOrBlank()) {
                val rules = serialized.split("|||")
                    .filter { it.isNotBlank() }
                    .mapNotNull { 
                        try {
                            ReminderRule.deserialize(it)
                        } catch (e: Exception) {
                            Log.e("ChecksSchedule", "Error deserializing rule", e)
                            null
                        }
                    }
                    .filter { rule ->
                        val simpleRuleValid = rule.selectedHours.isNotEmpty() && rule.selectedDaysOfWeek.isNotEmpty()
                        val advancedRuleValid = rule.selectedHours.isNotEmpty() && 
                            (rule.advancedType == "MONTHLY_BY_DATE" && rule.daysOfMonth.isNotEmpty() ||
                             rule.advancedType == "MONTHLY_BY_WEEKDAY" && rule.weekOfMonth != null && rule.dayOfWeekInMonth != null ||
                             rule.advancedType == "WEEKLY" && rule.dayOfWeekInMonth != null)
                        
                        simpleRuleValid || advancedRuleValid
                    }
                    .toMutableList()
                
                item.reminderRules.clear()
                item.reminderRules.addAll(rules)
            }
        }
    }
    
    private fun onColumnResize(columnIndex: Int, newWidth: Int, action: Int) {
        columnWidths[columnIndex] = newWidth
        // Обновляем данные только когда пользователь отпустил палец
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            contextForDialog?.let { saveColumnWidths(it) }
            // Используем post для избежания ошибки "Cannot call this method while RecyclerView is computing a layout"
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                notifyDataSetChanged()
            }
        }
    }
    
    // ViewHolder для заголовка таблицы
    class HeaderVH(itemView: View, private val onColumnResize: (Int, Int, Int) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val container: LinearLayout = itemView.findViewById(R.id.linearLayoutTaskRowContent)
        private var initialX = 0f
        private var initialWidth = 0
        
        fun bind(headers: List<String>, columnWidths: Map<Int, Int>, context: Context, isResizingMode: Boolean, theme: ThemeStrategy) {
            container.removeAllViews()
            var maxCellHeight = 0
            val textViews = mutableListOf<TextView>()
            
            headers.forEachIndexed { i, headerName ->
                val colWidth = columnWidths[i] ?: context.dpToPx(120)
                val textView = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(colWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
                    text = headerName
                    setBackgroundResource(R.drawable.cell_border)
                    gravity = android.view.Gravity.CENTER
                    setPadding(context.dpToPx(4), context.dpToPx(4), context.dpToPx(4), context.dpToPx(4))
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    // КРИТИЧНО: Для классической темы - исходный цвет!
                    // Получаем цвет заголовка из стратегии темы
                    val headerTextColor = theme.getHeaderTextColor()
                    setTextColor(headerTextColor)
                    isSoundEffectsEnabled = false
                }
                
                // Измеряем ячейку - важно делать это ДО добавления в контейнер
                textView.measure(
                    View.MeasureSpec.makeMeasureSpec(colWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                maxCellHeight = maxOf(maxCellHeight, textView.measuredHeight)
                textViews.add(textView)
                container.addView(textView)
                
                // Ручка изменения размера (включая последнюю колонку)
                if (isResizingMode) {
                    val resizeHandle = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(context.dpToPx(16), LinearLayout.LayoutParams.MATCH_PARENT)
                        setBackgroundColor(0xFF2196F3.toInt())
                        alpha = 0.5f
                        isClickable = true
                        isFocusable = true
                        isFocusableInTouchMode = true
                        isSoundEffectsEnabled = false
                    }
                    
                    val lastTextView = textView
                    val lastIndex = i
                    resizeHandle.setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                initialX = event.rawX
                                initialWidth = (lastTextView.layoutParams as LinearLayout.LayoutParams).width
                                // КРИТИЧНО: Запрещаем родителям (HorizontalScrollView) перехватывать touch события
                                (v.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                (itemView.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                (itemView.parent?.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                (itemView.parent?.parent?.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val delta = (event.rawX - initialX).toInt()
                                val newWidth = (initialWidth + delta).coerceIn(context.dpToPx(50), context.dpToPx(400))
                                // КРИТИЧНО: Продолжаем блокировать прокрутку
                                (v.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                (itemView.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                (itemView.parent?.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                (itemView.parent?.parent?.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                (lastTextView.layoutParams as LinearLayout.LayoutParams).width = newWidth
                                lastTextView.requestLayout()
                                container.requestLayout()
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                // При отпускании сохраняем и обновляем весь список
                                val finalWidth = (lastTextView.layoutParams as LinearLayout.LayoutParams).width
                                onColumnResize(lastIndex, finalWidth, event.action)
                                initialX = 0f
                                initialWidth = 0
                                // Разрешаем прокрутку обратно
                                (v.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(false)
                                (itemView.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(false)
                                (itemView.parent?.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(false)
                                (itemView.parent?.parent?.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(false)
                                v.performClick()
                                true
                            }
                            else -> true
                        }
                    }
                    resizeHandle.measure(
                        View.MeasureSpec.makeMeasureSpec(context.dpToPx(16), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    maxCellHeight = maxOf(maxCellHeight, resizeHandle.measuredHeight)
                    container.addView(resizeHandle)
                } else if (!isResizingMode && i < headers.size - 1) {
                    // В обычном режиме показываем тонкие разделители между колонками
                    val separator = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(context.dpToPx(1), LinearLayout.LayoutParams.MATCH_PARENT)
                        setBackgroundColor(AppTheme.getBorderColor())
                    }
                    container.addView(separator)
                }
            }
            
            // КРИТИЧНО: Сначала устанавливаем высоту контейнера, затем высоту всех TextView
            val finalMaxCellHeight = maxOf(maxCellHeight, context.dpToPx(50))
            container.layoutParams.height = finalMaxCellHeight
            textViews.forEach { textView ->
                textView.layoutParams.height = finalMaxCellHeight
            }
            container.requestLayout()
        }
    }
    
    // ViewHolder для строки данных
    class ItemVH(itemView: View, 
                 private val getEditMode: () -> Boolean,
                 private val onEdit: (Int, Int, String) -> Unit,
                 private val onAddRule: (Int) -> Unit,
                 private val onEditRule: (Int, Int) -> Unit,
                 private val onDeleteRule: (Int, Int) -> Unit,
                 private val onAddRow: (Int) -> Unit,
                 private val onDeleteRow: (Int) -> Unit,
                 private val getItems: () -> List<CheckItem>,
                 private val onColumnResize: (Int, Int, Int) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val container: LinearLayout = itemView.findViewById(R.id.linearLayoutTaskRowContent)
        private var operationCell: LinearLayout? = null
        private var rulesCell: LinearLayout? = null
        
        fun bind(item: CheckItem, position: Int, headers: List<String>, columnWidths: Map<Int, Int>, context: Context, theme: ThemeStrategy) {
            container.removeAllViews()
            
            // КРИТИЧНО: Для классической темы используем ИСХОДНЫЕ цвета!
            // Получаем цвета строки задачи из изолированной стратегии темы
            val rowStyle = theme.getTaskRowColors(isActive = item.isActive)
            val rowBackgroundColor = rowStyle.backgroundColor
            
            // КОЛОНКА 0: Операция
            operationCell = createOperationCell(item, position, columnWidths, context, rowBackgroundColor, rowStyle.textColor)
            container.addView(operationCell)
            
            // Разделитель
            container.addView(createSeparator(context))
            
            // КОЛОНКА 1: Список правил повторения
            rulesCell = createRulesCell(item, position, columnWidths, context, rowBackgroundColor, rowStyle.textColor)
            container.addView(rulesCell)
            
            // КРИТИЧНО: Синхронизируем высоту всех клеток в строке
            container.post {
                var maxHeight = 0
                
                // Измеряем обе клетки
                operationCell?.let { opCell ->
                    opCell.measure(
                        View.MeasureSpec.makeMeasureSpec(opCell.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    maxHeight = maxOf(maxHeight, opCell.measuredHeight)
                }
                
                rulesCell?.let { rCell ->
                    rCell.measure(
                        View.MeasureSpec.makeMeasureSpec(rCell.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    maxHeight = maxOf(maxHeight, rCell.measuredHeight)
                }
                
                // Устанавливаем максимальную высоту для обеих клеток
                val finalHeight = maxOf(maxHeight, context.dpToPx(50))
                operationCell?.layoutParams?.height = finalHeight
                rulesCell?.layoutParams?.height = finalHeight
                
                operationCell?.requestLayout()
                rulesCell?.requestLayout()
            }
        }
        
        private fun createOperationCell(item: CheckItem, position: Int, columnWidths: Map<Int, Int>, context: Context, bgColor: Int, textColor: Int): LinearLayout {
            val colWidth = columnWidths[0] ?: context.dpToPx(200)
            val cellContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(colWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
                minimumHeight = context.dpToPx(50)
                
                val borderColor = if (AppTheme.isNuclearTheme()) Color.parseColor("#2DCBE0") else AppTheme.getBorderColor()
                val drawable = createCellDrawableCompat(context, bgColor, borderColor)
                if (drawable != null) {
                    // Увеличиваем толщину границы для лучшей видимости
                    (drawable as? android.graphics.drawable.GradientDrawable)?.setStroke(
                        context.dpToPx(2), // 2dp для всех границ
                        borderColor
                    )
                    background = drawable
                } else {
                    setBackgroundColor(bgColor)
                }
            }
            
            // Текст операции
            val textView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = item.operation
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                setPadding(context.dpToPx(8), context.dpToPx(8), context.dpToPx(8), context.dpToPx(8))
                textSize = 14f
                // Используем цвет текста из темы (переданный параметр)
                setTextColor(textColor)
                
                if (getEditMode()) {
                    setOnClickListener { onEdit(position, 0, item.operation) }
                }
                isSoundEffectsEnabled = false
            }
            cellContainer.addView(textView)
            
            // Кнопки управления строками (только в режиме редактирования)
            if (getEditMode()) {
                val isEmpty = item.operation.isBlank()
                val allItems = getItems()
                val lastFilledIndex = allItems.indexOfLast { it.operation.isNotBlank() }
                val isLastFilled = !isEmpty && position == lastFilledIndex
                
                // Если все строки пустые, показываем кнопку "Добавить" на последней строке
                val allEmpty = allItems.all { it.operation.isBlank() }
                val isLastRow = position == allItems.size - 1
                
                if (isEmpty) {
                    // Кнопка удаления пустой строки (кроме случая когда это единственная или последняя из всех пустых)
                    if (!(allEmpty && isLastRow)) {
                        cellContainer.addView(Button(context).apply {
                            text = "🗑 Удалить строку"
                            isAllCaps = false
                            textSize = 10f
                            setBackgroundColor(Color.parseColor("#D32F2F"))
                            setTextColor(Color.WHITE)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                context.dpToPx(32)
                            ).apply {
                                setMargins(context.dpToPx(4), context.dpToPx(4), context.dpToPx(4), context.dpToPx(4))
                            }
                            setOnClickListener { onDeleteRow(position) }
                        })
                    }
                }
                
                // Показываем кнопку "Добавить строку" если:
                // 1. Это последняя заполненная строка ИЛИ
                // 2. Все строки пустые и это последняя строка
                if (isLastFilled || (allEmpty && isLastRow)) {
                    cellContainer.addView(Button(context).apply {
                        text = "+ Добавить строку"
                        isAllCaps = false
                        textSize = 10f
                        setBackgroundColor(Color.parseColor("#4CAF50"))
                        setTextColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            context.dpToPx(32)
                        ).apply {
                            setMargins(context.dpToPx(4), context.dpToPx(4), context.dpToPx(4), context.dpToPx(4))
                        }
                        setOnClickListener { onAddRow(position) }
                    })
                }
            }
            
            return cellContainer
        }
        
        private fun createRulesCell(item: CheckItem, position: Int, columnWidths: Map<Int, Int>, context: Context, bgColor: Int, textColor: Int): LinearLayout {
            val colWidth = columnWidths[1] ?: context.dpToPx(300)
            val rulesContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(colWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
                setPadding(context.dpToPx(4), context.dpToPx(4), context.dpToPx(4), context.dpToPx(4))
                minimumHeight = context.dpToPx(50)
                
                val borderColor = if (AppTheme.isNuclearTheme()) Color.parseColor("#2DCBE0") else AppTheme.getBorderColor()
                val drawable = createCellDrawableCompat(context, bgColor, borderColor)
                if (drawable != null) {
                    // Увеличиваем толщину границы для лучшей видимости
                    (drawable as? android.graphics.drawable.GradientDrawable)?.setStroke(
                        context.dpToPx(2), // 2dp для всех границ
                        borderColor
                    )
                    setBackground(drawable)
                } else {
                    setBackgroundColor(bgColor)
                }
            }
            
            // Добавляем каждое правило
            val isEditMode = getEditMode()
            item.reminderRules.forEachIndexed { ruleIndex, rule ->
                rulesContainer.addView(createRuleRow(rule, position, ruleIndex, context, isEditMode, textColor))
            }
            
            // Кнопка [+ Добавить правило] - только в режиме редактирования
            if (isEditMode) {
            rulesContainer.addView(createAddButton(position, context))
            }
            
            return rulesContainer
        }
        
        private fun createRuleRow(rule: ReminderRule, position: Int, ruleIndex: Int, context: Context, isEditMode: Boolean, textColor: Int): LinearLayout {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, context.dpToPx(2), 0, context.dpToPx(2))
                }
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            
            // Используем цвет текста из темы (переданный параметр)
            val ruleTextColor = textColor

            val ruleText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                text = rule.toCompactString()
                textSize = 12f
                setTextColor(ruleTextColor)
                setPadding(context.dpToPx(4), context.dpToPx(4), context.dpToPx(4), context.dpToPx(4))
                maxLines = Int.MAX_VALUE // Показываем весь текст без обрезки
                setSingleLine(false) // Разрешаем перенос строк
                // Редактирование только в режиме редактирования
                if (isEditMode) {
                setOnClickListener { onEditRule(position, ruleIndex) }
                }
            }
            row.addView(ruleText)
            
            // Кнопка удаления [×] - только в режиме редактирования
            if (isEditMode) {
            val deleteButton = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    context.dpToPx(32),
                    context.dpToPx(32)
                )
                text = "×"
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.parseColor("#D32F2F"))
                setBackgroundColor(Color.argb(34, 211, 47, 47))
                setOnClickListener { onDeleteRule(position, ruleIndex) }
            }
            row.addView(deleteButton)
            }
            
            return row
        }
        
        private fun createAddButton(position: Int, context: Context): Button {
            return Button(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    context.dpToPx(36)
                ).apply {
                    setMargins(0, context.dpToPx(4), 0, 0)
                }
                text = "+ Добавить правило"
                textSize = 11f
                val primaryColor = when {
                    !AppTheme.shouldApplyTheme() -> Color.parseColor("#1976D2")
                    AppTheme.isNuclearTheme() -> Color.parseColor("#00C4FF")
                    else -> AppTheme.getPrimaryColor()
                }
                val backgroundColor = when {
                    !AppTheme.shouldApplyTheme() -> lighten(primaryColor, 0.9f)
                    AppTheme.isNuclearTheme() -> Color.parseColor("#023355")
                    else -> lighten(primaryColor, 0.9f)
                }
                val drawable = AppTheme.createButtonDrawable(backgroundColor)
                if (drawable != null) {
                    background = drawable
                } else {
                    setBackgroundColor(backgroundColor)
                }
                setTextColor(if (AppTheme.isNuclearTheme()) Color.parseColor("#8FDFFF") else primaryColor)
                setOnClickListener { onAddRule(position) }
            }
        }
        
        private fun createSeparator(context: Context): View {
            return View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    context.dpToPx(1),
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(AppTheme.getBorderColor())
            }
        }

        private fun createCellDrawableCompat(context: Context, backgroundColor: Int, borderColor: Int): android.graphics.drawable.GradientDrawable? {
            val themedDrawable = AppTheme.createCellDrawable(backgroundColor, borderColor)
            if (themedDrawable != null) return themedDrawable

            if (!AppTheme.shouldApplyTheme()) {
                return android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 0f
                    setColor(backgroundColor)
                    setStroke(context.dpToPx(1), borderColor)
                }
            }
            return null
        }
    }
}

// Утилитная функция для конвертации dp в px
private fun android.content.Context.dpToPx(dp: Int): Int {
    return (dp * resources.displayMetrics.density).toInt()
}


private class ScheduleCsvRepository(private val ctx: android.content.Context) {
    fun readAll(): List<CheckItem> {
        val stream = tryOpenFilesDir() ?: tryOpenRaw() ?: return emptyList()
        val out = mutableListOf<CheckItem>()
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).forEach { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEach
                val p = splitCsv(line)
                if (p.size >= 5) {
                    out.add(CheckItem(p[1].trim(), p[3].trim('"',' '), p[2].trim()))
                }
            }
        }
        return out
    }
    private fun tryOpenFilesDir(): java.io.InputStream? {
        return try {
            val f = java.io.File(ctx.filesDir, "data/schedule_operations.csv")
            if (f.exists()) f.inputStream() else null
        } catch (_: Throwable) { null }
    }
    private fun tryOpenRaw(): java.io.InputStream? {
        val id = ctx.resources.getIdentifier("schedule_operations", "raw", ctx.packageName)
        return if (id != 0) ctx.resources.openRawResource(id) else null
    }
    private fun splitCsv(line: String): List<String> {
        val res = mutableListOf<String>()
        val sb = StringBuilder()
        var q = false
        for (c in line) {
            when {
                c == '"' -> q = !q
                c == ',' && !q -> { res.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
        }
        res.add(sb.toString())
        return res
    }
}

private fun ChecksScheduleFragment.scrollToCurrentHour() {
    // ЗАЩИТА: Проверяем что view готов через try-catch
    try {
        val nowH = LocalDateTime.now().hour
        // 36-часовая шкала: [20-23 пред.день] + [00-23 тек.день] + [00-07 след.день]
        // Текущий час всегда находится в диапазоне [00-23 тек.день], который начинается с позиции 4
        val targetPosition = 4 + nowH  // 4 = смещение из-за 4 часов предыдущего дня (20-23)
        recyclerHours.post {
            try {
                (recyclerHours.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(targetPosition, (resources.displayMetrics.widthPixels/2.5).toInt())
            } catch (e: Exception) {
                android.util.Log.w("ChecksSchedule", "Не удалось прокрутить к текущему часу: ${e.message}")
            }
        }
    } catch (e: UninitializedPropertyAccessException) {
        android.util.Log.w("ChecksSchedule", "scrollToCurrentHour() вызван но recyclerHours не инициализирован")
    } catch (e: Exception) {
        android.util.Log.w("ChecksSchedule", "Ошибка в scrollToCurrentHour(): ${e.message}")
    }
}

private fun ChecksScheduleFragment.buildMonth(ym: YearMonth, onlyCurrentWeek: Boolean = false): List<DayCell> {
    val today = LocalDate.now()
    
    if (onlyCurrentWeek) {
        // Показываем только текущую неделю
        val cells = mutableListOf<DayCell>()
        val dayOfWeek = today.dayOfWeek.value // 1=пн ... 7=вс
        val startOfWeek = today.minusDays((dayOfWeek - 1).toLong()) // Понедельник текущей недели
        
        // Добавляем 7 дней начиная с понедельника текущей недели
        for (i in 0..6) {
            val date = startOfWeek.plusDays(i.toLong())
            cells.add(DayCell(date, date == today))
        }
        return cells
    } else {
        // Показываем весь месяц
    val first = ym.atDay(1)
    val days = ym.lengthOfMonth()
        val startShift = (first.dayOfWeek.value - 1) // 0-пн, 1-вт ... 6-вс (европейский стиль, воскресенье последний)
    val cells = mutableListOf<DayCell>()
    repeat(startShift) { cells.add(DayCell(null, false)) }
    for (d in 1..days) {
        val date = ym.atDay(d)
        cells.add(DayCell(date, date == today))
    }
    // добиваем до кратности 7
    while (cells.size % 7 != 0) cells.add(DayCell(null, false))
    return cells
    }
}

 



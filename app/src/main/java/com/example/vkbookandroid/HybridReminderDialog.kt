package com.example.vkbookandroid

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Гибридный диалог: Простой режим + Продвинутый режим
 */
class HybridReminderDialog(
    private val context: Context,
    private val currentRule: ReminderRule,
    private val onRuleSelected: (ReminderRule) -> Unit
) {
    
    private lateinit var dialog: Dialog
    private var isAdvancedMode = false
    
    // Простой режим
    private val selectedHours: MutableSet<Int> = currentRule.selectedHours.toMutableSet()
    private val selectedDays: MutableSet<DayOfWeek> = currentRule.selectedDaysOfWeek.toMutableSet()
    private val hourButtons = mutableMapOf<Int, ToggleButton>()
    private val dayButtons = mutableMapOf<DayOfWeek, ToggleButton>()
    
    // Продвинутый режим
    private var advancedType: AdvancedType = AdvancedType.WEEKLY // По умолчанию "Еженедельное"
    private var weekOfMonth: Int = 1 // 1-я, 2-я, 3-я, 4-я
    private var dayOfWeekInMonth: DayOfWeek = DayOfWeek.MONDAY
    private var daysOfMonth: MutableSet<Int> = mutableSetOf()
    private val dayOfMonthButtons = mutableMapOf<Int, ToggleButton>()
    
    private lateinit var mainContainer: LinearLayout
    private lateinit var simpleContainer: LinearLayout
    private lateinit var advancedContainer: LinearLayout
    private lateinit var resultPreview: TextView
    private lateinit var switchButton: Button
    
    fun show() {
        dialog = Dialog(context)
        dialog.setContentView(createDialogView())
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }
    
    private fun createDialogView(): ScrollView {
        val scrollView = ScrollView(context).apply {
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        }
        
        mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Заголовок
        mainContainer.addView(createTitle())
        
        // Кнопка переключения режимов
        switchButton = createSwitchButton()
        mainContainer.addView(switchButton)
        
        // Контейнер для простого режима
        simpleContainer = createSimpleMode()
        mainContainer.addView(simpleContainer)
        
        // Контейнер для продвинутого режима (скрыт по умолчанию)
        advancedContainer = createAdvancedMode()
        advancedContainer.visibility = View.GONE
        mainContainer.addView(advancedContainer)
        
        // Предпросмотр (ВАЖНО: создаем ДО вызова updateAdvancedDetailsSection)
        resultPreview = TextView(context).apply {
            text = buildPreviewText()
            setTextColor(Color.parseColor("#1976D2"))
            textSize = 12f
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            setBackgroundColor(Color.parseColor("#E3F2FD"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(16)
            }
        }
        mainContainer.addView(resultPreview)
        
        // Инициализируем детали продвинутого режима (после создания resultPreview)
        updateAdvancedDetailsSection()
        
        // Кнопки действий
        mainContainer.addView(createActionButtons())
        
        scrollView.addView(mainContainer)
        return scrollView
    }
    
    private fun createTitle(): TextView {
        return TextView(context).apply {
            text = "⚙️ Настройка напоминания"
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(12)
            }
        }
    }
    
    private fun createSwitchButton(): Button {
        return Button(context).apply {
            text = "⚙️ Продвинутые настройки"
            textSize = 12f
            isAllCaps = false // Отключаем автоматическое преобразование в верхний регистр
            setBackgroundColor(Color.parseColor("#78909C"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(40)
            ).apply {
                bottomMargin = dpToPx(12)
            }
            setOnClickListener {
                isAdvancedMode = !isAdvancedMode
                if (isAdvancedMode) {
                    simpleContainer.visibility = View.GONE
                    advancedContainer.visibility = View.VISIBLE
                    this.text = "◀ Простые настройки"
                    this.setBackgroundColor(Color.parseColor("#FF6F00")) // Оранжевый
                } else {
                    simpleContainer.visibility = View.VISIBLE
                    advancedContainer.visibility = View.GONE
                    this.text = "⚙️ Продвинутые настройки"
                    this.setBackgroundColor(Color.parseColor("#78909C"))
                }
                updatePreview()
            }
        }
    }
    
    // ===== ПРОСТОЙ РЕЖИМ =====
    
    private fun createSimpleMode(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        // Сетка часов (без быстрых кнопок)
        container.addView(createHoursGrid())
        
        // Разделитель
        container.addView(createDivider())
        
        // Кнопки дней (без быстрых кнопок)
        container.addView(createDaysButtons())
        
        return container
    }
    
    
    private fun createHoursGrid(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        container.addView(TextView(context).apply {
            text = "В КАКИЕ ЧАСЫ:"
            textSize = 12f
            setTextColor(Color.GRAY)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(8))
        })
        
        // 3 ряда по 8 часов
        for (row in 0..2) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 8f
            }
            
            for (col in 0..7) {
                val hour = row * 8 + col
                val button = createHourToggle(hour)
                hourButtons[hour] = button
                rowLayout.addView(button)
            }
            
            container.addView(rowLayout)
        }
        
        return container
    }
    
    private fun createHourToggle(hour: Int): ToggleButton {
        return ToggleButton(context).apply {
            text = String.format(Locale.getDefault(), "%02d", hour)
            textOn = String.format(Locale.getDefault(), "%02d", hour)
            textOff = String.format(Locale.getDefault(), "%02d", hour)
            textSize = 10f
            isChecked = selectedHours.contains(hour)
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(36),
                1f
            ).apply {
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedHours.add(hour)
                } else {
                    selectedHours.remove(hour)
                }
                updateButtonStyle(this, isChecked)
                updatePreview()
            }
            updateButtonStyle(this, isChecked)
        }
    }
    
    
    private fun createDaysButtons(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        container.addView(TextView(context).apply {
            text = "В КАКИЕ ДНИ:"
            textSize = 12f
            setTextColor(Color.GRAY)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(8))
        })
        
        val daysRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 7f
        }
        
        DayOfWeek.values().forEach { day ->
            val button = createDayToggle(day)
            dayButtons[day] = button
            daysRow.addView(button)
        }
        
        container.addView(daysRow)
        return container
    }
    
    private fun createDayToggle(day: DayOfWeek): ToggleButton {
        return ToggleButton(context).apply {
            text = day.toShortString()
            textOn = day.toShortString()
            textOff = day.toShortString()
            textSize = 11f
            isChecked = selectedDays.contains(day)
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(40),
                1f
            ).apply {
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedDays.add(day)
                } else {
                    selectedDays.remove(day)
                }
                updateButtonStyle(this, isChecked)
                updatePreview()
            }
            updateButtonStyle(this, isChecked)
        }
    }
    
    // ===== ПРОДВИНУТЫЙ РЕЖИМ =====
    
    private fun createAdvancedMode(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        container.addView(TextView(context).apply {
            text = "ПРОДВИНУТЫЕ НАСТРОЙКИ:"
            textSize = 11f
            setTextColor(Color.GRAY)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(8))
        })
        
        // Выбор типа
        val spinner = Spinner(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                listOf(
                    "🔄 Еженедельное (каждый ПН, ВТ...)",
                    "🗓 Ежемесячное (каждое 1-е, 15-е число...)",
                    "🗓 Ежемесячное (каждый 1-й ПН, 3-й ВТ...)"
                )
            )
            // advancedType.ordinal: NONE=0, WEEKLY=1, MONTHLY_BY_DATE=2, MONTHLY_BY_WEEKDAY=3
            // Но в Spinner индексы: WEEKLY=0, MONTHLY_BY_DATE=1, MONTHLY_BY_WEEKDAY=2
            val spinnerPosition = when (advancedType) {
                AdvancedType.WEEKLY -> 0
                AdvancedType.MONTHLY_BY_DATE -> 1
                AdvancedType.MONTHLY_BY_WEEKDAY -> 2
                else -> 0 // По умолчанию WEEKLY
            }
            setSelection(spinnerPosition)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    advancedType = when (position) {
                        0 -> AdvancedType.WEEKLY
                        1 -> AdvancedType.MONTHLY_BY_DATE
                        2 -> AdvancedType.MONTHLY_BY_WEEKDAY
                        else -> AdvancedType.WEEKLY
                    }
                    updateAdvancedDetailsSection()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        container.addView(spinner)
        
        return container
    }
    
    private fun updateAdvancedDetailsSection() {
        // Удаляем старые детали (НО НЕ заголовок и Spinner!)
        // child[0] = TextView "ПРОДВИНУТЫЕ НАСТРОЙКИ:"
        // child[1] = Spinner выбора типа
        // child[2+] = детали (их и удаляем)
        val childCount = advancedContainer.childCount
        if (childCount > 2) {
            advancedContainer.removeViews(2, childCount - 2)
        }
        
        when (advancedType) {
            AdvancedType.NONE -> {
                // Ничего
            }
            AdvancedType.WEEKLY -> {
                advancedContainer.addView(createWeeklySelector())
            }
            AdvancedType.MONTHLY_BY_DATE -> {
                advancedContainer.addView(createMonthlyByDateSelector())
            }
            AdvancedType.MONTHLY_BY_WEEKDAY -> {
                advancedContainer.addView(createMonthlyByWeekdaySelector())
            }
        }
        updatePreview()
    }
    
    // Еженедельное: каждый ПН, каждый ВТ...
    private fun createWeeklySelector(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12)
            }
        }
        
        container.addView(TextView(context).apply {
            text = "ВЫБЕРИ ДЕНЬ НЕДЕЛИ:"
            textSize = 11f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, dpToPx(6))
        })
        
        // Spinner дня недели
        val daySpinner = Spinner(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("каждый понедельник", "каждый вторник", "каждую среду", "каждый четверг", 
                       "каждую пятницу", "каждую субботу", "каждое воскресенье")
            )
            setSelection(dayOfWeekInMonth?.ordinal ?: 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    dayOfWeekInMonth = DayOfWeek.values()[position]
                    updatePreview()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        container.addView(daySpinner)
        
        // Часы для этого правила
        container.addView(createAdvancedHoursSelector())
        
        return container
    }
    
    // Ежемесячное по числам: каждое 1-е, 15-е число
    private fun createMonthlyByDateSelector(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12)
            }
        }
        
        container.addView(TextView(context).apply {
            text = "ВЫБЕРИ ЧИСЛА МЕСЯЦА:"
            textSize = 11f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, dpToPx(6))
        })
        
        // Сетка чисел 1-31
        for (row in 0..4) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 7f
            }
            
            for (col in 0..6) {
                val dayNum = row * 7 + col + 1
                if (dayNum <= 31) {
                    val button = createDayOfMonthToggle(dayNum)
                    dayOfMonthButtons[dayNum] = button
                    rowLayout.addView(button)
                } else {
                    rowLayout.addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f)
                    })
                }
            }
            container.addView(rowLayout)
        }
        
        // Часы для этого правила
        container.addView(createAdvancedHoursSelector())
        
        return container
    }
    
    // Ежемесячное по дням: каждый 1-й ПН, 3-й ВТ
    private fun createMonthlyByWeekdaySelector(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12)
            }
        }
        
        container.addView(TextView(context).apply {
            text = "ВЫБЕРИ:"
            textSize = 11f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, dpToPx(6))
        })
        
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        // Spinner недели
        val weekSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("1-я", "2-я", "3-я", "4-я")
            )
            setSelection((weekOfMonth - 1).coerceIn(0, 3))
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = dpToPx(8)
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    weekOfMonth = position + 1
                    updatePreview()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        row.addView(weekSpinner)
        
        // Spinner дня недели
        val daySpinner = Spinner(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                DayOfWeek.values().map { it.toFullString() }
            )
            setSelection((dayOfWeekInMonth ?: DayOfWeek.MONDAY).ordinal)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    dayOfWeekInMonth = DayOfWeek.values()[position]
                    updatePreview()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        row.addView(daySpinner)
        
        container.addView(row)
        
        // Часы для этого правила
        container.addView(createAdvancedHoursSelector())
        
        return container
    }
    
    private fun createDayOfMonthToggle(dayNum: Int): ToggleButton {
        return ToggleButton(context).apply {
            text = dayNum.toString()
            textOn = dayNum.toString()
            textOff = dayNum.toString()
            textSize = 10f
            isChecked = daysOfMonth.contains(dayNum)
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(36),
                1f
            ).apply {
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    daysOfMonth.add(dayNum)
                } else {
                    daysOfMonth.remove(dayNum)
                }
                updateButtonStyle(this, isChecked)
                updatePreview()
            }
            updateButtonStyle(this, isChecked)
        }
    }
    
    private fun createAdvancedHoursSelector(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12)
            }
        }
        
        container.addView(TextView(context).apply {
            text = "В КАКИЕ ЧАСЫ:"
            textSize = 11f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, dpToPx(6))
        })
        
        // Полная сетка часов (3 ряда × 8 кнопок)
        for (row in 0..2) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 8f
            }
            
            for (col in 0..7) {
                val hour = row * 8 + col
                // Используем те же кнопки что и в простом режиме
                val button = hourButtons[hour]
                if (button != null) {
                    // Кнопка уже создана в простом режиме - используем её
                    rowLayout.addView(createHourToggleForAdvanced(hour))
                } else {
                    // Создаем новую кнопку
                    val newButton = createHourToggle(hour)
                    hourButtons[hour] = newButton
                    rowLayout.addView(newButton)
                }
            }
            
            container.addView(rowLayout)
        }
        
        return container
    }
    
    private fun createHourToggleForAdvanced(hour: Int): ToggleButton {
        return ToggleButton(context).apply {
            text = String.format(Locale.getDefault(), "%02d", hour)
            textOn = String.format(Locale.getDefault(), "%02d", hour)
            textOff = String.format(Locale.getDefault(), "%02d", hour)
            textSize = 10f
            isChecked = selectedHours.contains(hour)
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(36),
                1f
            ).apply {
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedHours.add(hour)
                } else {
                    selectedHours.remove(hour)
                }
                updateButtonStyle(this, isChecked)
                updatePreview()
            }
            updateButtonStyle(this, isChecked)
        }
    }
    
    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    
    private fun createQuickButton(text: String, weight: Float, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            textSize = 10f
            setBackgroundColor(Color.parseColor("#78909C"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(36),
                weight
            ).apply {
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            setOnClickListener {
                onClick()
                updatePreview()
            }
        }
    }
    
    private fun createDivider(): View {
        return View(context).apply {
            setBackgroundColor(Color.LTGRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1)
            ).apply {
                topMargin = dpToPx(12)
                bottomMargin = dpToPx(12)
            }
        }
    }
    
    private fun createActionButtons(): LinearLayout {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(16)
            }
            weightSum = 2f
        }
        
        layout.addView(Button(context).apply {
            text = "Отмена"
            isAllCaps = false
            setBackgroundColor(Color.parseColor("#757575"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(48),
                1f
            ).apply {
                marginEnd = dpToPx(8)
            }
            setOnClickListener {
                dialog.dismiss()
            }
        })
        
        layout.addView(Button(context).apply {
            text = "Сохранить"
            isAllCaps = false
            setBackgroundColor(Color.parseColor("#1976D2"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(48),
                1f
            ).apply {
                marginStart = dpToPx(8)
            }
            setOnClickListener {
                val rule = buildReminderRule()
                onRuleSelected(rule)
                dialog.dismiss()
            }
        })
        
        return layout
    }
    
    private fun buildReminderRule(): ReminderRule {
        return if (isAdvancedMode) {
            when (advancedType) {
                AdvancedType.WEEKLY -> {
                    // Еженедельное: каждый понедельник и т.д.
                    ReminderRule(
                        selectedHours = selectedHours,
                        selectedDaysOfWeek = setOf(dayOfWeekInMonth ?: DayOfWeek.MONDAY),
                        advancedType = "WEEKLY",
                        weekOfMonth = null,
                        dayOfWeekInMonth = dayOfWeekInMonth,
                        daysOfMonth = emptySet()
                    )
                }
                AdvancedType.MONTHLY_BY_DATE -> {
                    // Ежемесячное по числам: каждое 1-е, 15-е число
                    ReminderRule(
                        selectedHours = selectedHours,
                        selectedDaysOfWeek = emptySet(),
                        advancedType = "MONTHLY_BY_DATE",
                        weekOfMonth = null,
                        dayOfWeekInMonth = null,
                        daysOfMonth = daysOfMonth
                    )
                }
                AdvancedType.MONTHLY_BY_WEEKDAY -> {
                    // Ежемесячное по дням: 1-й понедельник, 3-й вторник
                    ReminderRule(
                        selectedHours = selectedHours,
                        selectedDaysOfWeek = emptySet(),
                        advancedType = "MONTHLY_BY_WEEKDAY",
                        weekOfMonth = weekOfMonth,
                        dayOfWeekInMonth = dayOfWeekInMonth,
                        daysOfMonth = emptySet()
                    )
                }
                else -> {
                    ReminderRule(selectedHours, selectedDays)
                }
            }
        } else {
            // Простой режим
            ReminderRule(selectedHours, selectedDays)
        }
    }
    
    private fun updateButtonStyle(button: ToggleButton, isChecked: Boolean) {
        if (isChecked) {
            button.setBackgroundColor(Color.parseColor("#1976D2"))
            button.setTextColor(Color.WHITE)
        } else {
            button.setBackgroundColor(Color.parseColor("#E0E0E0"))
            button.setTextColor(Color.BLACK)
        }
    }
    
    private fun updatePreview() {
        resultPreview.text = buildPreviewText()
    }
    
    private fun buildPreviewText(): String {
        if (isAdvancedMode) {
            return when (advancedType) {
                AdvancedType.NONE -> "⚠️ Выберите тип повторения"
                
                AdvancedType.WEEKLY -> {
                    val day = dayOfWeekInMonth?.toFullString() ?: "понедельник"
                    val hours = if (selectedHours.isEmpty()) {
                        "⚠️ Выберите часы"
                    } else {
                        selectedHours.sorted().joinToString(", ") { String.format(Locale.getDefault(), "%02d:00", it) }
                    }
                    "✓ Каждый $day\nВ часы: $hours"
                }
                
                AdvancedType.MONTHLY_BY_DATE -> {
                    val dates = if (daysOfMonth.isEmpty()) {
                        "⚠️ Выберите числа"
                    } else {
                        daysOfMonth.sorted().joinToString(", ") { "$it число" }
                    }
                    val hours = if (selectedHours.isEmpty()) {
                        "⚠️ Выберите часы"
                    } else {
                        selectedHours.sorted().joinToString(", ") { String.format(Locale.getDefault(), "%02d:00", it) }
                    }
                    "✓ Каждый месяц: $dates\nВ часы: $hours"
                }
                
                AdvancedType.MONTHLY_BY_WEEKDAY -> {
                    val weekText = when (weekOfMonth) {
                        1 -> "Первый"
                        2 -> "Второй"
                        3 -> "Третий"
                        4 -> "Четвёртый"
                        else -> "$weekOfMonth-й"
                    }
                    val day = dayOfWeekInMonth?.toFullString() ?: "понедельник"
                    val hours = if (selectedHours.isEmpty()) {
                        "⚠️ Выберите часы"
                    } else {
                        selectedHours.sorted().joinToString(", ") { String.format(Locale.getDefault(), "%02d:00", it) }
                    }
                    "✓ $weekText $day месяца\nВ часы: $hours"
                }
            }
        } else {
            if (selectedHours.isEmpty() || selectedDays.isEmpty()) {
                return "⚠️ Выберите хотя бы 1 час и 1 день"
            }
            val rule = ReminderRule(selectedHours, selectedDays)
            return "✓ ${rule.toFullString()}"
        }
    }
    
    // Вспомогательные методы
    private fun updateAllHourButtons() {
        hourButtons.forEach { (hour, button) ->
            button.isChecked = selectedHours.contains(hour)
            updateButtonStyle(button, button.isChecked)
        }
    }
    
    private fun updateAllDayButtons() {
        dayButtons.forEach { (day, button) ->
            button.isChecked = selectedDays.contains(day)
            updateButtonStyle(button, button.isChecked)
        }
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

enum class AdvancedType {
    NONE,                  // Не выбрано
    WEEKLY,                // Еженедельное (каждый ПН, каждый ВТ...)
    MONTHLY_BY_DATE,       // Ежемесячное по числам (каждое 1-е, 15-е число)
    MONTHLY_BY_WEEKDAY     // Ежемесячное по дням (каждый 1-й ПН, 3-й ВТ)
}


package com.example.vkbookandroid

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.time.DayOfWeek
import java.util.EnumSet

/**
 * УНИВЕРСАЛЬНЫЙ диалог напоминаний - ВСЁ на одном экране!
 */
class UniversalReminderDialog(
    private val context: Context,
    private val currentRule: ReminderRule,
    private val onRuleSelected: (ReminderRule) -> Unit
) {
    
    private lateinit var dialog: Dialog
    
    // Выбранные значения
    private val selectedHours: MutableSet<Int> = currentRule.selectedHours.toMutableSet()
    private val selectedDaysOfWeek: MutableSet<DayOfWeek> = currentRule.selectedDaysOfWeek.toMutableSet()
    private val selectedDaysOfMonth: MutableSet<Int> = mutableSetOf()
    
    // Для "N-й день недели месяца"
    private var weekOfMonth: Int? = null  // 1-4 или null
    private var dayOfWeekInMonth: DayOfWeek? = null
    
    // Кнопки
    private val hourButtons = mutableMapOf<Int, ToggleButton>()
    private val dayOfWeekButtons = mutableMapOf<DayOfWeek, ToggleButton>()
    private val dayOfMonthButtons = mutableMapOf<Int, ToggleButton>()
    
    private lateinit var resultPreview: TextView
    private lateinit var weekSpinner: Spinner
    private lateinit var daySpinner: Spinner
    private lateinit var useWeekdayCheckBox: CheckBox
    private lateinit var allDayCheckBox: CheckBox
    private lateinit var daysOfWeekLabel: TextView
    private lateinit var daysOfMonthLabel: TextView
    private lateinit var saveButton: Button
    
    private var suppressHourListener = false
    private var suppressAllDayListener = false
    private var previousSelectedHours: Set<Int>? = null
    
    fun show() {
        dialog = Dialog(context)
        dialog.setContentView(createDialogView())
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        // Загружаем текущее правило
        loadCurrentRule()
        updateSectionsAvailability()
        updatePreview()
        
        dialog.show()
    }
    
    private fun loadCurrentRule() {
        // Если есть числа месяца
        if (currentRule.daysOfMonth.isNotEmpty()) {
            selectedDaysOfMonth.addAll(currentRule.daysOfMonth)
            updateDayOfMonthButtons()
        }
        
        // Если есть "N-й день недели"
        if (currentRule.weekOfMonth != null && currentRule.dayOfWeekInMonth != null) {
            weekOfMonth = currentRule.weekOfMonth
            dayOfWeekInMonth = currentRule.dayOfWeekInMonth
            useWeekdayCheckBox.isChecked = true
            weekSpinner.setSelection((currentRule.weekOfMonth ?: 1) - 1)
            daySpinner.setSelection(currentRule.dayOfWeekInMonth?.ordinal ?: 0)
        }
        
        refreshHourButtons()
        updateAllDayCheckbox()
    }
    
    private fun createDialogView(): ScrollView {
        val scrollView = ScrollView(context).apply {
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        }
        
        val mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Заголовок
        mainContainer.addView(TextView(context).apply {
            text = "Выбор времени и даты"
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(16)
            }
        })
        
        // 1. ЧАСЫ
        mainContainer.addView(createHoursSection())
        
        // Разделитель
        mainContainer.addView(createDivider())
        
        // 2. ДНИ НЕДЕЛИ
        mainContainer.addView(createDaysOfWeekSection())
        
        // Разделитель
        mainContainer.addView(createDivider())
        
        // 3. ЧИСЛА МЕСЯЦА (опционально)
        mainContainer.addView(createDaysOfMonthSection())
        
        // Разделитель
        mainContainer.addView(createDivider())
        
        // 4. N-Й ДЕНЬ НЕДЕЛИ МЕСЯЦА (опционально)
        mainContainer.addView(createWeekdayOfMonthSection())
        
        // Предпросмотр
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
        
        // Кнопки действий
        mainContainer.addView(createActionButtons())
        
        scrollView.addView(mainContainer)
        return scrollView
    }
    
    // ===== 1. ЧАСЫ =====
    
    private fun createHoursSection(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val titleView = TextView(context).apply {
            text = "В КАКИЕ ЧАСЫ:"
            textSize = 12f
            setTextColor(Color.GRAY)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        headerRow.addView(titleView)
        
        allDayCheckBox = CheckBox(context).apply {
            text = "Весь день"
            textSize = 12f
            setTextColor(Color.GRAY)
            setOnCheckedChangeListener { _, isChecked ->
                if (suppressAllDayListener) return@setOnCheckedChangeListener
                if (isChecked) {
                    previousSelectedHours = selectedHours.toSet()
                    selectedHours.clear()
                    selectedHours.addAll(0..23)
                } else {
                    val restore = previousSelectedHours
                    selectedHours.clear()
                    if (restore != null) {
                        selectedHours.addAll(restore)
                    }
                    previousSelectedHours = null
                }
                refreshHourButtons()
                updateSectionsAvailability()
                updatePreview()
            }
        }
        headerRow.addView(allDayCheckBox)
        
        container.addView(headerRow)
        
        container.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(4)
            )
        })
        
        // Сетка 24 часа (3 ряда × 8 кнопок)
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
            text = String.format("%02d", hour)
            textOn = String.format("%02d", hour)
            textOff = String.format("%02d", hour)
            textSize = 10f
            isAllCaps = false
            isChecked = selectedHours.contains(hour)
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(36),
                1f
            ).apply {
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            setOnCheckedChangeListener { _, isChecked ->
                if (suppressHourListener) return@setOnCheckedChangeListener
                if (isChecked) {
                    selectedHours.add(hour)
                } else {
                    selectedHours.remove(hour)
                }
                previousSelectedHours = selectedHours.toSet()
                updateButtonStyle(this, isChecked)
                updateSectionsAvailability()
                updatePreview()
                updateAllDayCheckbox()
            }
            updateButtonStyle(this, isChecked)
        }
    }
    
    // ===== 2. ДНИ НЕДЕЛИ =====
    
    private fun createDaysOfWeekSection(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        daysOfWeekLabel = TextView(context).apply {
            text = "В КАКИЕ ДНИ НЕДЕЛИ:"
            textSize = 12f
            setTextColor(Color.GRAY)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(8))
        }
        container.addView(daysOfWeekLabel)
        
        val daysRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 7f
        }
        
        DayOfWeek.values().forEach { day ->
            val button = createDayOfWeekToggle(day)
            dayOfWeekButtons[day] = button
            daysRow.addView(button)
        }
        
        container.addView(daysRow)
        return container
    }
    
    private fun createDayOfWeekToggle(day: DayOfWeek): ToggleButton {
        return ToggleButton(context).apply {
            text = day.toShortString()
            textOn = day.toShortString()
            textOff = day.toShortString()
            textSize = 11f
            isAllCaps = false
            isChecked = selectedDaysOfWeek.contains(day)
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(40),
                1f
            ).apply {
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedDaysOfWeek.add(day)
                } else {
                    selectedDaysOfWeek.remove(day)
                }
                updateButtonStyle(this, isChecked)
                updateSectionsAvailability()
                updatePreview()
            }
            updateButtonStyle(this, isChecked)
        }
    }
    
    // ===== 3. ЧИСЛА МЕСЯЦА =====
    
    private fun createDaysOfMonthSection(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        daysOfMonthLabel = TextView(context).apply {
            text = "ИЛИ КОНКРЕТНЫЕ ЧИСЛА МЕСЯЦА (опционально):"
            textSize = 12f
            setTextColor(Color.parseColor("#FF6F00")) // Оранжевый
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(8))
        }
        container.addView(daysOfMonthLabel)
        
        // Сетка чисел 1-31 (5 рядов × 7 кнопок)
        for (row in 0..4) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 7f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(36)
                )
            }
            
            for (col in 0..6) {
                val dayNum = row * 7 + col + 1
                if (dayNum <= 31) {
                    val button = createDayOfMonthToggle(dayNum)
                    dayOfMonthButtons[dayNum] = button
                    rowLayout.addView(button)
                } else {
                    // Пустая ячейка (для выравнивания)
                    rowLayout.addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            dpToPx(36),
                            1f
                        ).apply {
                            setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
                        }
                        visibility = View.INVISIBLE
                    })
                }
            }
            container.addView(rowLayout)
        }
        
        return container
    }
    
    private fun createDayOfMonthToggle(dayNum: Int): ToggleButton {
        return ToggleButton(context).apply {
            text = dayNum.toString()
            textOn = dayNum.toString()
            textOff = dayNum.toString()
            textSize = 10f
            isAllCaps = false
            isChecked = selectedDaysOfMonth.contains(dayNum)
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(36),
                1f
            ).apply {
                setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            }
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedDaysOfMonth.add(dayNum)
                } else {
                    selectedDaysOfMonth.remove(dayNum)
                }
                updateButtonStyle(this, isChecked)
                updateSectionsAvailability()
                updatePreview()
            }
            updateButtonStyle(this, isChecked)
        }
    }
    
    // ===== 4. N-Й ДЕНЬ НЕДЕЛИ МЕСЯЦА =====
    
    private fun createWeekdayOfMonthSection(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Чекбокс "Использовать N-й день недели"
        useWeekdayCheckBox = CheckBox(context).apply {
            text = "ИЛИ N-Й ДЕНЬ НЕДЕЛИ МЕСЯЦА (например, каждый 3-й вторник):"
            textSize = 12f
            setTextColor(Color.parseColor("#FF6F00"))
            isAllCaps = false
            setTypeface(null, android.graphics.Typeface.BOLD)
            isChecked = weekOfMonth != null && dayOfWeekInMonth != null
            setOnCheckedChangeListener { _, isChecked ->
                if (!isChecked) {
                    weekOfMonth = null
                    dayOfWeekInMonth = null
                }
                weekSpinner.isEnabled = isChecked
                daySpinner.isEnabled = isChecked
                updateSectionsAvailability()
                updatePreview()
            }
        }
        container.addView(useWeekdayCheckBox)
        
        // Выбор недели и дня
        val selectorsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
            }
        }
        
        // Spinner недели
        weekSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("1-я неделя", "2-я неделя", "3-я неделя", "4-я неделя")
            )
            setSelection((weekOfMonth ?: 1) - 1)
            isEnabled = useWeekdayCheckBox.isChecked
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
        selectorsRow.addView(weekSpinner)
        
        // Spinner дня недели
        daySpinner = Spinner(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                DayOfWeek.values().map { it.toFullString() }
            )
            setSelection(dayOfWeekInMonth?.ordinal ?: 0)
            isEnabled = useWeekdayCheckBox.isChecked
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
        selectorsRow.addView(daySpinner)
        
        container.addView(selectorsRow)
        return container
    }
    
    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    
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
        
        saveButton = Button(context).apply {
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
                if (!isRuleValid()) {
                    Toast.makeText(context, "Такое правило создать нельзя! Выберите хотя бы 1 час и настройте повторение", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val rule = buildReminderRule()
                onRuleSelected(rule)
                dialog.dismiss()
            }
        }
        layout.addView(saveButton)
        
        return layout
    }
    
    private fun buildReminderRule(): ReminderRule {
        // Определяем тип правила
        return when {
            // N-й день недели месяца
            useWeekdayCheckBox.isChecked && weekOfMonth != null && dayOfWeekInMonth != null -> {
                ReminderRule(
                    selectedHours = snapshotSelectedHours(),
                    selectedDaysOfWeek = emptySet(),
                    advancedType = "MONTHLY_BY_WEEKDAY",
                    weekOfMonth = weekOfMonth,
                    dayOfWeekInMonth = dayOfWeekInMonth,
                    daysOfMonth = emptySet()
                )
            }
            // Конкретные числа месяца
            selectedDaysOfMonth.isNotEmpty() -> {
                ReminderRule(
                    selectedHours = snapshotSelectedHours(),
                    selectedDaysOfWeek = emptySet(),
                    advancedType = "MONTHLY_BY_DATE",
                    weekOfMonth = null,
                    dayOfWeekInMonth = null,
                    daysOfMonth = snapshotSelectedDaysOfMonth()
                )
            }
            // Простое правило (дни недели)
            else -> {
                ReminderRule(
                    selectedHours = snapshotSelectedHours(),
                    selectedDaysOfWeek = snapshotSelectedDaysOfWeek(),
                    advancedType = "NONE",
                    weekOfMonth = null,
                    dayOfWeekInMonth = null,
                    daysOfMonth = emptySet()
                )
            }
        }
    }
    
    /**
     * Обновляет доступность секций при конфликте условий
     */
    private fun updateSectionsAvailability() {
        val hasDaysOfWeek = selectedDaysOfWeek.isNotEmpty()
        val hasDaysOfMonth = selectedDaysOfMonth.isNotEmpty()
        val hasWeekdayOfMonth = useWeekdayCheckBox.isChecked
        
        // Если выбраны дни недели - блокируем числа месяца и N-й день
        if (hasDaysOfWeek) {
            setDaysOfMonthEnabled(false)
            useWeekdayCheckBox.isEnabled = false
            useWeekdayCheckBox.alpha = 0.4f
            daysOfMonthLabel.alpha = 0.4f
        }
        // Если выбраны числа месяца - блокируем дни недели и N-й день
        else if (hasDaysOfMonth) {
            setDaysOfWeekEnabled(false)
            useWeekdayCheckBox.isEnabled = false
            useWeekdayCheckBox.alpha = 0.4f
            daysOfWeekLabel.alpha = 0.4f
        }
        // Если выбран N-й день недели - блокируем дни недели и числа месяца
        else if (hasWeekdayOfMonth) {
            setDaysOfWeekEnabled(false)
            setDaysOfMonthEnabled(false)
            daysOfWeekLabel.alpha = 0.4f
            daysOfMonthLabel.alpha = 0.4f
        }
        // Если ничего не выбрано - всё доступно
        else {
            setDaysOfWeekEnabled(true)
            setDaysOfMonthEnabled(true)
            useWeekdayCheckBox.isEnabled = true
            useWeekdayCheckBox.alpha = 1.0f
            daysOfWeekLabel.alpha = 1.0f
            daysOfMonthLabel.alpha = 1.0f
        }
        
        // Обновляем состояние кнопки "Сохранить"
        val isValid = isRuleValid()
        saveButton.isEnabled = isValid
        saveButton.alpha = if (isValid) 1.0f else 0.4f
    }
    
    /**
     * Проверяет валидность текущего правила
     */
    private fun isRuleValid(): Boolean {
        // Должен быть выбран хотя бы 1 час
        if (selectedHours.isEmpty()) return false
        
        // Должно быть настроено хотя бы одно повторение
        val hasRepeat = selectedDaysOfWeek.isNotEmpty() || 
                       selectedDaysOfMonth.isNotEmpty() || 
                       (weekOfMonth != null && dayOfWeekInMonth != null && useWeekdayCheckBox.isChecked)
        
        return hasRepeat
    }
    
    private fun setDaysOfWeekEnabled(enabled: Boolean) {
        dayOfWeekButtons.values.forEach { button ->
            button.isEnabled = enabled
            if (!enabled && button.isChecked) {
                button.isChecked = false
                selectedDaysOfWeek.remove(DayOfWeek.values()[button.text.toString().toDayOfWeekOrdinal()])
            }
            button.alpha = if (enabled) 1.0f else 0.4f
        }
    }
    
    private fun setDaysOfMonthEnabled(enabled: Boolean) {
        dayOfMonthButtons.values.forEach { button ->
            button.isEnabled = enabled
            if (!enabled && button.isChecked) {
                button.isChecked = false
                val dayNum = button.text.toString().toIntOrNull()
                if (dayNum != null) selectedDaysOfMonth.remove(dayNum)
            }
            button.alpha = if (enabled) 1.0f else 0.4f
        }
    }
    
    private fun String.toDayOfWeekOrdinal(): Int {
        return when (this) {
            "ПН" -> 0
            "ВТ" -> 1
            "СР" -> 2
            "ЧТ" -> 3
            "ПТ" -> 4
            "СБ" -> 5
            "ВС" -> 6
            else -> 0
        }
    }
    
    private fun updatePreview() {
        resultPreview.text = buildPreviewText()
    }
    
    private fun buildPreviewText(): String {
        if (selectedHours.isEmpty()) {
            return "⚠️ Выберите хотя бы 1 час"
        }
        
        val hoursText = selectedHours.sorted().joinToString(", ") { String.format("%02d:00", it) }
        
        val repeatText = when {
            weekOfMonth != null && dayOfWeekInMonth != null && useWeekdayCheckBox.isChecked -> {
                val weekText = when (weekOfMonth) {
                    1 -> "Первый"
                    2 -> "Второй"
                    3 -> "Третий"
                    4 -> "Четвёртый"
                    else -> "$weekOfMonth-й"
                }
                "$weekText ${dayOfWeekInMonth?.toFullString() ?: ""} месяца"
            }
            selectedDaysOfMonth.isNotEmpty() -> {
                "Каждый месяц: ${selectedDaysOfMonth.sorted().joinToString(", ")} число"
            }
            selectedDaysOfWeek.isNotEmpty() -> {
                val days = selectedDaysOfWeek.sortedBy { it.ordinal }.joinToString(", ") { it.toShortString() }
                "Дни недели: $days"
            }
            else -> {
                return "⚠️ Выберите дни недели, числа месяца или N-й день недели"
            }
        }
        
        return "✓ $repeatText\nВ часы: $hoursText"
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
    
    private fun updateDayOfMonthButtons() {
        dayOfMonthButtons.forEach { (day, button) ->
            button.isChecked = selectedDaysOfMonth.contains(day)
            updateButtonStyle(button, button.isChecked)
        }
    }

    private fun refreshHourButtons() {
        suppressHourListener = true
        hourButtons.forEach { (hour, button) ->
            val checked = selectedHours.contains(hour)
            button.isChecked = checked
            updateButtonStyle(button, checked)
        }
        suppressHourListener = false
        updateAllDayCheckbox()
    }

    private fun updateAllDayCheckbox() {
        if (!::allDayCheckBox.isInitialized) return
        val allSelected = selectedHours.size == 24
        if (allDayCheckBox.isChecked != allSelected) {
            suppressAllDayListener = true
            allDayCheckBox.isChecked = allSelected
            suppressAllDayListener = false
        }
    }

    private fun snapshotSelectedHours(): Set<Int> = selectedHours.toSet()

    private fun snapshotSelectedDaysOfWeek(): Set<DayOfWeek> =
        if (selectedDaysOfWeek.isEmpty()) {
            emptySet()
        } else {
            EnumSet.copyOf(selectedDaysOfWeek)
        }

    private fun snapshotSelectedDaysOfMonth(): Set<Int> = selectedDaysOfMonth.toSet()
    
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}


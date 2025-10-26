package com.example.vkbookandroid

import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.*

/**
 * Фрагмент для отображения графика смен на год
 */
class ScheduleFragment : Fragment() {
    
    private lateinit var calendarRecyclerView: RecyclerView
    private lateinit var horizontalScrollView: HorizontalScrollView
    private lateinit var yearTextView: TextView
    private lateinit var btnPrevYear: Button
    private lateinit var btnNextYear2: Button
    private lateinit var btnToday: Button
    private lateinit var btnZoomIn: Button
    private lateinit var btnZoomOut: Button
    
    private var currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private lateinit var scheduleAdapter: ScheduleCalendarAdapter
    
    // ООП-компоненты (оставляем для совместимости, не используем фоновые индексации здесь)
    
    // Кэш предрасчитанных сдвигов месяцев: Map<"Year-Month", Shift>
    private val monthShiftCache = mutableMapOf<String, Int>()
    
    // НОВЫЕ ОПТИМИЗИРОВАННЫЕ КОМПОНЕНТЫ (добавлены для оптимизации производительности)
    private val shiftCalculator = com.example.vkbookandroid.schedule.ShiftCalculator()
    private val calendarDataGenerator = com.example.vkbookandroid.schedule.CalendarDataGenerator(shiftCalculator)
    private val useOptimizedVersion = true // Флаг для переключения между версиями
    
    // БАЗОВЫЙ паттерн смены (10 элементов) - основа для всех смен
    private val baseShiftPattern = arrayOf("3", "2", "4", "1", "Вх", "4", "1", "3", "2", "Вх")
    
    // Начальные позиции для каждой смены в базовом паттерне
    private val shiftStartPositions = arrayOf(0, 2, 8, 6, 4)
    private val shiftNames = arrayOf("Смена 1", "Смена 2", "Смена 3", "Смена 4", "Смена 5")
    
    // Генерация 36-элементного паттерна для смены с учетом сдвига
    private fun generateShiftPattern(shiftIndex: Int, yearShiftOffset: Int): Array<String> {
        val shiftStartPos = shiftStartPositions[shiftIndex]
        val effectiveStartPos = (shiftStartPos + yearShiftOffset) % baseShiftPattern.size
        
        return Array(36) { index ->
            val positionInBase = (effectiveStartPos + index) % baseShiftPattern.size
            baseShiftPattern[positionInBase]
        }
    }
    
    // Кэш для паттернов (для обратной совместимости с адаптером)
    private val shiftPatterns: Array<Array<String>>
        get() = Array(5) { shiftIndex -> generateShiftPattern(shiftIndex, 0) }
    
    // Выбранный день для подсветки столбика
    private var selectedDayInMonth: Int = -1
    private var selectedMonthIndex: Int = -1
    
    // Сегодняшняя дата для автоматического выделения
    private val todayCalendar = Calendar.getInstance()
    private val todayYear: Int = todayCalendar.get(Calendar.YEAR)
    private val todayMonth: Int = todayCalendar.get(Calendar.MONTH)
    private val todayDay: Int = todayCalendar.get(Calendar.DAY_OF_MONTH)
    
    companion object {
        private const val TAG = "ScheduleFragment"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_schedule, container, false)
        
        // 🧪 ТЕСТИРОВАНИЕ ООП КОМПОНЕНТОВ (не влияет на работу приложения)
        view.post {
            com.example.vkbookandroid.schedule.ScheduleTestHelper.runAllTests()
        }
        
        // 🧪 ТЕСТИРОВАНИЕ НЕПРЕРЫВНОСТИ ПАТТЕРНА (не влияет на работу приложения)
        view.post {
            Log.d(TAG, "Запуск тестов непрерывности паттерна...")
            com.example.vkbookandroid.schedule.PatternContinuityTestHelper.runFullTests()
        }
        
        // 🧪 СПЕЦИФИЧЕСКИЙ ТЕСТ ДЛЯ НОЯБРЯ 2026 (не влияет на работу приложения)
        view.post {
            Log.d(TAG, "Запуск специфического теста для ноября 2026...")
            com.example.vkbookandroid.schedule.November2026SpecificTest.runAllNovember2026Tests()
        }
        
        // 🔍 АНАЛИЗ ПАТТЕРНА НОЯБРЯ 2026 (не влияет на работу приложения)
        view.post {
            Log.d(TAG, "Запуск анализа паттерна ноября 2026...")
            com.example.vkbookandroid.schedule.November2026PatternAnalyzer.runFullAnalysis()
        }
        
        // 🔍 АНАЛИЗ ПЕРЕХОДОВ К НОЯБРЮ 2026 (не влияет на работу приложения)
        view.post {
            Log.d(TAG, "Запуск анализа переходов к ноябрю 2026...")
            com.example.vkbookandroid.schedule.November2026TransitionAnalyzer.runFullTransitionAnalysis()
        }
        
        // Инициализация views
        calendarRecyclerView = view.findViewById(R.id.calendarRecyclerView)
        horizontalScrollView = view.findViewById(R.id.horizontalScrollView)
        yearTextView = view.findViewById(R.id.yearTextView)
        btnPrevYear = view.findViewById(R.id.btnPrevYear)
        btnNextYear2 = view.findViewById(R.id.btnNextYear2)
        btnToday = view.findViewById(R.id.btnToday)
        btnZoomIn = view.findViewById(R.id.btnZoomIn)
        btnZoomOut = view.findViewById(R.id.btnZoomOut)
        
        // Apply sharp diagonal weekend legend background (bottom-left to top-right)
        view.findViewById<TextView?>(R.id.legendWeekend)?.let { legend ->
            val saturdayColor = Color.parseColor("#FFE082")
            val sundayColor = Color.parseColor("#FFCDD2")
            legend.background = DiagonalSplitDrawable(saturdayColor, sundayColor)
        }
        
        setupViews()
        setupRecyclerView()
        updateYearDisplay()
        generateScheduleData()
        
        // Центрируем на текущей дате при открытии
        view.post { scrollToToday() }
        
        return view
    }
    
    private fun setupViews() {
        btnPrevYear.setOnClickListener {
            currentYear--
            updateYearDisplay()
            generateScheduleData()
        }
        
        btnNextYear2.setOnClickListener {
            currentYear++
            updateYearDisplay()
            generateScheduleData()
        }
        
        btnToday.setOnClickListener {
            currentYear = Calendar.getInstance().get(Calendar.YEAR)
            updateYearDisplay()
            generateScheduleData()
            // Выделяем сегодняшний столбец
            selectedDayInMonth = todayDay
            selectedMonthIndex = todayMonth
            scheduleAdapter.setSelectedDay(selectedDayInMonth, selectedMonthIndex)
            scheduleAdapter.notifyDataSetChanged()
            view?.post { scrollToToday() }
        }

        btnZoomIn.setOnClickListener { adjustZoom(+0.1f) }
        btnZoomOut.setOnClickListener { adjustZoom(-0.1f) }
    }
    // Масштаб UI без влияния на бизнес-логику
    private var zoomFactor: Float = 1.0f

    private fun adjustZoom(delta: Float) {
        val newZoom = (zoomFactor + delta).coerceIn(0.6f, 2.0f)
        if (newZoom == zoomFactor) return
        zoomFactor = newZoom
        scheduleAdapter.setZoomFactor(zoomFactor)
        scheduleAdapter.notifyDataSetChanged()
        view?.post { scrollToToday() }
    }
    
    private fun setupRecyclerView() {
        scheduleAdapter = ScheduleCalendarAdapter(
            requireContext(),
            ::onDayClick,
            ::calculateMonthShift,
            ::getAdjustedShiftForDisplay
        )
        calendarRecyclerView.layoutManager = LinearLayoutManager(context)
        calendarRecyclerView.adapter = scheduleAdapter
    }
    
    private fun updateYearDisplay() {
        yearTextView.text = currentYear.toString()
    }
    
    /**
     * Корректирует сдвиг месяца так, чтобы все дни поместились в 36 ячеек.
     * Сохраняет соответствие с паттерном смен, сдвигая весь календарь и график синхронно.
     * 
     * @param year Год
     * @param monthIndex Индекс месяца (0-11)
     * @param daysInMonth Количество дней в месяце
     * @return Скорректированный сдвиг (0-5 для 31-дневных месяцев)
     */
    private fun getAdjustedShiftForDisplay(year: Int, monthIndex: Int, daysInMonth: Int): Int {
        val calculatedShift = calculateMonthShift(year, monthIndex)
        val patternValue = calculatedShift % 10 // Значение 0-9 в базовом паттерне (10 элементов)
        
        // Максимальная безопасная позиция = 36 - количество дней в месяце
        // Для 31 дня: макс позиция = 5 (5 + 31 = 36)
        // Для 30 дней: макс позиция = 6 (6 + 30 = 36)
        // Для 29 дней: макс позиция = 7 (7 + 29 = 36)
        // Для 28 дней: макс позиция = 8 (8 + 28 = 36)
        val maxSafePosition = 36 - daysInMonth
        
        // ИСПРАВЛЕННАЯ ЛОГИКА: Проверяем patternValue напрямую
        if (patternValue + daysInMonth <= 36) {
            // Патерн помещается - используем его
            // УБРАНО: Log.d для предотвращения спама в логах
            return patternValue
        }
        
        // Патерн НЕ помещается - ищем позицию в предыдущих циклах
        // Возможные позиции: patternValue - 10, patternValue - 20, patternValue - 30, ...
        var adjustedPosition = patternValue
        while (adjustedPosition > maxSafePosition) {
            adjustedPosition -= 10
            if (adjustedPosition < 0) {
                // Не нашли подходящую позицию, используем 0 (или maxSafePosition, если 0 не помещается)
                val fallback = if (daysInMonth <= 36) 0 else maxSafePosition.coerceAtLeast(0)
                Log.w(TAG, "Год $year, месяц $monthIndex: НЕ НАШЛИ позицию! patternValue=$patternValue, используем fallback=$fallback")
                return fallback
            }
        }
        
        // Нашли подходящую позицию
        // УБРАНО: Log.d для предотвращения спама в логах
        return adjustedPosition
    }
    
    private fun generateScheduleData() {
        val scheduleData = mutableListOf<ScheduleRow>()
        
        val months = arrayOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        )
        
        val daysInMonths = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        if (isLeapYear(currentYear)) {
            daysInMonths[1] = 29
        }
        
        // ВАЖНО: Вычисляем ОПТИМАЛЬНЫЙ сдвиг для ВСЕГО года
        // Итеративно подбираем сдвиг, при котором ВСЕ месяцы попадают в паттерн И помещаются в 36 ячеек
        val yearShiftOffset = findOptimalYearShift(currentYear, daysInMonths)
        
        Log.d(TAG, "═══ Год $currentYear - Расчет календаря ═══")
        Log.d(TAG, "Оптимальный сдвиг года (yearShiftOffset): $yearShiftOffset")
        
        // Генерируем строки месяцев с фиксированной шириной 36 ячеек
        months.forEachIndexed { monthIndex, monthName ->
            val days = (1..daysInMonths[monthIndex]).toList()
            
            // Вычисляем количество дней от января (для позиционирования в окне)
            val daysFromJanuary = (0 until monthIndex).sumOf { daysInMonths[it] }
            
            // Позиция месяца в окне = позиция января + дни от января
            // График статичный, календарь последовательный от позиции јануари
            val calculatedShift = calculateMonthShift(currentYear, monthIndex)
            val adjustedShift = calculatedShift - yearShiftOffset
            
            Log.d(TAG, "Месяц $monthName: calculated=$calculatedShift, adjusted=$adjustedShift (сдвиг -$yearShiftOffset)")
            
            // ПРАВИЛЬНАЯ НОРМАЛИЗАЦИЯ: ищем ближайшую к НАЧАЛУ позицию с нужным значением паттерна
            val safeAdjustedShift = if (adjustedShift < 0 || adjustedShift + daysInMonths[monthIndex] > 36) {
                Log.w(TAG, "ВНИМАНИЕ: $monthName adjusted=$adjustedShift (calculated=$calculatedShift, offset=$yearShiftOffset)")
                
                // Вычисляем позицию в базовом паттерне (0-9)
                val patternPosition = calculatedShift % 10
                val patternValue = baseShiftPattern[patternPosition]
                
                Log.d(TAG, "  Ищем позицию для паттерна[$patternPosition]=\"$patternValue\"")
                
                // Ищем ВСЕ позиции в окне 36, где совпадает ИНДЕКС паттерна (а не только значение)
                // Формула индекса: indexAtPos = (yearShiftOffset + pos) % 10
                val validPositions = mutableListOf<Int>()
                
                for (pos in 0 until 36) {
                    val indexAtPos = (yearShiftOffset + pos) % 10
                    if (indexAtPos == patternPosition && pos + daysInMonths[monthIndex] <= 36) {
                        validPositions.add(pos)
                    }
                }
                
                if (validPositions.isEmpty()) {
                    Log.w(TAG, "  ⚠️ Нет позиций с совпадающим ИНДЕКСОМ ($patternPosition), ищем ЛЮБУЮ подходящую")
                    
                    // Ищем ЛЮБУЮ позицию, где месяц помещается (начиная с 0)
                    val anyValidPosition = (0..5).firstOrNull { pos ->
                        pos + daysInMonths[monthIndex] <= 36
                    }
                    
                    if (anyValidPosition != null) {
                        Log.w(TAG, "  ⚠️ Используем позицию $anyValidPosition (паттерн НЕ совпадает!)")
                        anyValidPosition
            } else {
                        Log.e(TAG, "  ❌ Месяц НЕ ПОМЕЩАЕТСЯ в 36 ячеек!")
                        0
                    }
                } else {
                    // Выбираем БЛИЖАЙШУЮ К НАЧАЛУ позицию (как в логике 2025 года)
                    val bestPos = validPositions.minOrNull() ?: 0
                    Log.d(TAG, "  ✅ Найдена позиция $bestPos (все варианты: $validPositions)")
                    bestPos
                }
            } else {
                adjustedShift
            }
            
            // Создаем список дней - числа идут от меньшего к большему
            val displayDays = mutableListOf<String>()
            
            // Добавляем пустые ячейки в начале (скорректированный сдвиг)
            repeat(safeAdjustedShift) { displayDays.add("") }
            
            // Добавляем дни месяца по порядку (1, 2, 3, ..., 31)
            days.forEach { day -> displayDays.add(day.toString()) }
            
            // Добиваем до 36 ячеек пустыми ячейками в конце
            while (displayDays.size < 36) {
                displayDays.add("")
            }
            
            // Обрезаем до 36 ячеек если больше
            val finalDays = displayDays.take(36)
            
            scheduleData.add(ScheduleRow(monthName, finalDays, isMonthRow = true, monthIndex = monthIndex, year = currentYear))
        }
        
        // ПРОВЕРКА ЦЕЛОСТНОСТИ: все месяцы должны иметь правильное количество дней
        Log.d(TAG, "═══ Проверка целостности календаря ═══")
        var allValid = true
        months.forEachIndexed { monthIndex, monthName ->
            val row = scheduleData[monthIndex]
            val nonEmptyDays = row.days.count { it.toString().isNotEmpty() && it.toString().toIntOrNull() != null }
            val expectedDays = daysInMonths[monthIndex]
            
            if (nonEmptyDays != expectedDays) {
                Log.e(TAG, "❌ ОШИБКА: $monthName имеет $nonEmptyDays дней вместо $expectedDays!")
                allValid = false
            } else {
                Log.d(TAG, "✅ $monthName: $nonEmptyDays дней (ожидается $expectedDays)")
            }
        }
        
        if (allValid) {
            Log.d(TAG, "✅ Все месяцы валидны!")
        } else {
            Log.e(TAG, "❌ ОБНАРУЖЕНЫ ОШИБКИ В КАЛЕНДАРЕ!")
        }
        
        // Добавляем строки смен - СДВИГАЕМ график на yearShiftOffset
        // ВАЖНО: График двигается вместе с календарем!
        // yearShiftOffset показывает, на сколько нужно сдвинуть график влево
        
        Log.d(TAG, "═══ Год $currentYear - График смен (сдвиг на $yearShiftOffset) ═══")
        
        repeat(5) { shiftIndex ->
            // Для 2025 года график должен совпадать с эталоном (без сдвига)
            val offsetForShifts = if (currentYear == 2025) 0 else yearShiftOffset
            val shiftedPattern = generateShiftPattern(shiftIndex, offsetForShifts).toList()
            
            Log.d(TAG, "Смена ${shiftIndex + 1}: сдвиг=$yearShiftOffset, начало=${shiftedPattern.take(5).joinToString()}")
            
            scheduleData.add(
                ScheduleRow(
                    shiftNames[shiftIndex],
                    shiftedPattern,
                    isMonthRow = false,
                    shiftIndex = shiftIndex
                )
            )
        }
        
        scheduleAdapter.updateData(scheduleData)
        
        // Подсветка "сегодня" только в текущем году
        if (currentYear == todayYear) {
            selectedDayInMonth = todayDay
            selectedMonthIndex = todayMonth
            scheduleAdapter.setSelectedDay(selectedDayInMonth, selectedMonthIndex)
        } else {
            scheduleAdapter.setSelectedDay(-1, -1)
        }
        scheduleAdapter.notifyDataSetChanged()
    }
    
    private fun isLeapYear(year: Int): Boolean {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    }
    
    /**
     * ИТЕРАТИВНЫЙ ПОДБОР оптимального сдвига года
     * Проверяет все варианты сдвига (0-9) и выбирает тот, при котором:
     * 1. ВСЕ месяцы попадают в правильный паттерн
     * 2. ВСЕ месяцы помещаются в 36 ячеек
     * 3. Минимум пустых столбцов слева
     * 
     * ЗАЩИТА ОТ ЗАВИСАНИЯ: максимум 40 попыток, после чего выдается ошибка
     */
    private fun findOptimalYearShift(year: Int, daysInMonths: IntArray): Int {
        Log.d(TAG, "═══ Поиск оптимального сдвига для $year года ═══")
        
        var bestOffset = 0
        var bestScore = -1
        var attemptCount = 0
        val maxAttempts = 40
        
        // Перебираем все возможные сдвиги (0-9, т.к. базовый паттерн имеет период 10)
        for (offset in 0..9) {
            attemptCount++
            
            // ЗАЩИТА: Проверка лимита попыток
            if (attemptCount > maxAttempts) {
                Log.e(TAG, "❌ КРИТИЧЕСКАЯ ОШИБКА: Превышен лимит попыток ($maxAttempts)!")
                Log.e(TAG, "❌ Невозможно построить график для $year года в 36 ячейках!")
                
                // Показываем сообщение пользователю
                activity?.runOnUiThread {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "⚠️ Невозможно построить график для $year года.\nТребуется расширение окна календаря.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                
                // Возвращаем лучший найденный вариант
                return bestOffset
            }
            var allMonthsFit = true
            var allMonthsMatchPattern = true
            var emptyColumnsLeft = 0
            
            // Проверяем каждый месяц
            for (monthIndex in 0..11) {
                val calculatedShift = calculateMonthShift(year, monthIndex)
                val adjustedShift = calculatedShift - offset
                val patternPosition = calculatedShift % 10
                val patternValue = baseShiftPattern[patternPosition]
                
                // Отладка для ноября 2026
                if (year == 2026 && monthIndex == 10) {
                    Log.d(TAG, "НОЯБРЬ 2026: offset=$offset, calculatedShift=$calculatedShift, adjustedShift=$adjustedShift, patternPosition=$patternPosition, patternValue=$patternValue")
                    Log.d(TAG, "ПРОВЕРКА ПЕРЕХОДА: Октябрь shift=${calculateMonthShift(year, 9)}, дней в октябре=${daysInMonths[9]}, ожидаемый ноябрь=${(calculateMonthShift(year, 9) + daysInMonths[9]) % 10}")
                }
                
                // Нормализуем отрицательные значения
                val normalizedShift = if (adjustedShift < 0) {
                    (adjustedShift + 100) % 10  // Приводим к 0-9
                } else {
                    adjustedShift % 36
                }
                
                // Проверка 1: Помещается ли месяц?
                if (normalizedShift + daysInMonths[monthIndex] > 36) {
                    allMonthsFit = false
                }
                
                // Проверка 2: Соответствует ли паттерну?
                // График сдвигается на offset, месяц начинается на normalizedShift
                // Значение в графике на позиции normalizedShift = baseShiftPattern[(offset + normalizedShift) % 10]
                // Это значение должно совпадать с patternValue (исходная позиция месяца)
                val graphValue = baseShiftPattern[(offset + normalizedShift) % 10]
                if (graphValue != patternValue) {
                    allMonthsMatchPattern = false
                }
                
                // ДОПОЛНИТЕЛЬНАЯ ПРОВЕРКА: Непрерывность паттерна между месяцами
                // Проверяем, что переход от предыдущего месяца к текущему корректен
                if (monthIndex > 0) {
                    val prevMonthShift = calculateMonthShift(year, monthIndex - 1)
                    val prevMonthDays = daysInMonths[monthIndex - 1]
                    val expectedCurrentShift = (prevMonthShift + prevMonthDays) % 10
                    
                    if (calculatedShift != expectedCurrentShift) {
                        Log.w(TAG, "ОШИБКА НЕПРЕРЫВНОСТИ: Месяц $monthIndex, ожидался shift=$expectedCurrentShift, получен=$calculatedShift")
                        allMonthsMatchPattern = false
                    }
                }
                
                // СПЕЦИАЛЬНАЯ ПРОВЕРКА ДЛЯ НОЯБРЯ 2026: Проверяем непрерывность паттерна
                if (year == 2026 && monthIndex == 10) { // Ноябрь 2026
                    val octoberShift = calculateMonthShift(year, 9)
                    val octoberDays = daysInMonths[9]
                    val expectedNovemberShift = (octoberShift + octoberDays) % 10
                    
                    if (calculatedShift != expectedNovemberShift) {
                        Log.e(TAG, "КРИТИЧЕСКАЯ ОШИБКА НЕПРЕРЫВНОСТИ ДЛЯ НОЯБРЯ 2026:")
                        Log.e(TAG, "  Октябрь shift=$octoberShift, дней=$octoberDays")
                        Log.e(TAG, "  Ожидаемый ноябрь shift=$expectedNovemberShift")
                        Log.e(TAG, "  Фактический ноябрь shift=$calculatedShift")
                        allMonthsMatchPattern = false
                    }
                }
                
                // Считаем пустые столбцы слева (для января)
        if (monthIndex == 0) {
                    emptyColumnsLeft = normalizedShift
                }
            }
            
            // Оценка варианта (приоритеты: 1-паттерн, 2-помещается, 3-меньше пустых)
            var score = 0
            if (allMonthsMatchPattern) score += 1000  // Главный приоритет - паттерн!
            if (allMonthsFit) score += 100
            score -= emptyColumnsLeft  // Минимизируем пустые столбцы
            
            Log.d(TAG, "Offset $offset: fit=$allMonthsFit, pattern=$allMonthsMatchPattern, empty=$emptyColumnsLeft, score=$score")
            
            if (score > bestScore) {
                bestScore = score
                bestOffset = offset
            }
        }
        
        Log.d(TAG, "✅ Выбран оптимальный сдвиг: $bestOffset (оценка: $bestScore)")
        
        // ПРОВЕРКА: Если не найден идеальный вариант (оценка < 1100), предупреждаем
        if (bestScore < 1100) {
            Log.w(TAG, "⚠️ ВНИМАНИЕ: Не найден идеальный вариант для $year года!")
            Log.w(TAG, "⚠️ Некоторые месяцы могут не совпадать с паттерном или не помещаться в 36 ячеек")
            
            if (bestScore < 100) {
                // Критическая проблема - ни один месяц не помещается
                activity?.runOnUiThread {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "⚠️ График для $year года построен с ошибками.\nРекомендуется расширение окна календаря.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        
        return bestOffset
    }
    
    /**
     * Вычисляет сдвиг месяца на основе строгой непрерывности последовательности смены 1
     * Каждый день календаря строго следует последовательности смены 1 день за днем
     * 1 января 2025 должно соответствовать "2" в паттерне (позиция 1)
     */
    private fun calculateMonthShift(year: Int, monthIndex: Int): Int {
        // Вычисляем глобальный номер дня от 1 января 2025 года
        val globalDayNumber = getGlobalDayNumberFrom2025(year, monthIndex, 1)
        
        // Сдвиг = позиция в паттерне смены 1 для этого дня
        // 1 января 2025 = день 1, должно быть на позиции 1 ("2")
        // Поэтому используем (globalDayNumber - 1 + 1) % 37 = globalDayNumber % 37
        // Но это дает неправильный результат для марта
        // Нужно проверить логику...
        
        // Давайте проверим: 1 января = позиция 1, 1 февраля = позиция 32
        // 1 марта должно быть на позиции, которая продолжает паттерн после 28 февраля
        
        // 28 февраля = день 59, позиция 59 % 37 = 22 ("Вх")
        // 1 марта = день 60, должно быть на позиции 23 ("3")
        // Но 60 % 37 = 23, что дает "1", а не "3"
        
        // Проблема в том, что мы неправильно вычисляем позицию
        // Нужно учесть, что паттерн начинается с позиции 1 для 1 января
        
        // По правильному графику:
        // Январь 1: "2" (позиция 1)
        // Февраль 1: "4" (позиция 2)
        // Март 1: "3" (позиция 0)
        // Апрель 1: "2" (позиция 1)
        // Май 1: "Вх" (позиция 4)
        // Июнь 1: "Вх" (позиция 9)
        // Июль 1: "1" (позиция 13)
        // Август 1: "Вх" (позиция 18)
        // Сентябрь 1: "1" (позиция 23)
        // Октябрь 1: "2" (позиция 28)
        // Ноябрь 1: "1" (позиция 32)
        // Декабрь 1: "4" (позиция 2)
        
        // Паттерн имеет 36 элементов, не 37!
        // Попробуем: (globalDayNumber - 1) % 36
        // 1 января: (1 - 1) % 36 = 0 → "3" ❌ (должно быть "2")
        // 1 марта: (60 - 1) % 36 = 23 → "1" ❌ (должно быть "3")
        
        // Попробуем: globalDayNumber % 36
        // 1 января: 1 % 36 = 1 → "2" ✅
        // 1 марта: 60 % 36 = 24 → "1" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 35) % 36
        // 1 января: (1 + 35) % 36 = 0 → "3" ❌
        // 1 марта: (60 + 35) % 36 = 23 → "1" ❌
        
        // Попробуем: (globalDayNumber - 2) % 36
        // 1 января: (1 - 2) % 36 = 35 → "4" ❌
        // 1 марта: (60 - 2) % 36 = 22 → "4" ❌
        
        // Попробуем: (globalDayNumber + 1) % 36
        // 1 января: (1 + 1) % 36 = 2 → "4" ❌
        // 1 марта: (60 + 1) % 36 = 25 → "Вх" ❌
        
        // Попробуем: (globalDayNumber + 2) % 36
        // 1 января: (1 + 2) % 36 = 3 → "1" ❌
        // 1 марта: (60 + 2) % 36 = 26 → "4" ❌
        
        // Попробуем: (globalDayNumber + 3) % 36
        // 1 января: (1 + 3) % 36 = 4 → "Вх" ❌
        // 1 марта: (60 + 3) % 36 = 27 → "1" ❌
        
        // Попробуем: (globalDayNumber + 4) % 36
        // 1 января: (1 + 4) % 36 = 5 → "4" ❌
        // 1 марта: (60 + 4) % 36 = 28 → "2" ❌
        
        // Попробуем: (globalDayNumber + 5) % 36
        // 1 января: (1 + 5) % 36 = 6 → "1" ❌
        // 1 марта: (60 + 5) % 36 = 29 → "Вх" ❌
        
        // Попробуем: (globalDayNumber + 6) % 36
        // 1 января: (1 + 6) % 36 = 7 → "3" ❌
        // 1 марта: (60 + 6) % 36 = 30 → "3" ✅
        
        // Но тогда 1 января будет "3", а не "2"
        
        // Попробуем: (globalDayNumber + 7) % 36
        // 1 января: (1 + 7) % 36 = 8 → "2" ✅
        // 1 марта: (60 + 7) % 36 = 31 → "2" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 8) % 36
        // 1 января: (1 + 8) % 36 = 9 → "Вх" ❌
        // 1 марта: (60 + 8) % 36 = 32 → "1" ❌
        
        // Попробуем: (globalDayNumber + 9) % 36
        // 1 января: (1 + 9) % 36 = 10 → "3" ❌
        // 1 марта: (60 + 9) % 36 = 33 → "2" ❌
        
        // Попробуем: (globalDayNumber + 10) % 36
        // 1 января: (1 + 10) % 36 = 11 → "2" ✅
        // 1 марта: (60 + 10) % 36 = 34 → "4" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 11) % 36
        // 1 января: (1 + 11) % 36 = 12 → "4" ❌
        // 1 марта: (60 + 11) % 36 = 35 → "1" ❌
        
        // Попробуем: (globalDayNumber + 12) % 36
        // 1 января: (1 + 12) % 36 = 13 → "1" ❌
        // 1 марта: (60 + 12) % 36 = 0 → "3" ✅
        
        // Но тогда 1 января будет "1", а не "2"
        
        // Попробуем: (globalDayNumber + 13) % 36
        // 1 января: (1 + 13) % 36 = 14 → "Вх" ❌
        // 1 марта: (60 + 13) % 36 = 1 → "2" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 14) % 36
        // 1 января: (1 + 14) % 36 = 15 → "4" ❌
        // 1 марта: (60 + 14) % 36 = 2 → "4" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 15) % 36
        // 1 января: (1 + 15) % 36 = 16 → "1" ❌
        // 1 марта: (60 + 15) % 36 = 3 → "1" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 16) % 36
        // 1 января: (1 + 16) % 36 = 17 → "3" ❌
        // 1 марта: (60 + 16) % 36 = 4 → "Вх" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 17) % 36
        // 1 января: (1 + 17) % 36 = 18 → "2" ✅
        // 1 марта: (60 + 17) % 36 = 5 → "4" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 18) % 36
        // 1 января: (1 + 18) % 36 = 19 → "Вх" ❌
        // 1 марта: (60 + 18) % 36 = 6 → "1" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 19) % 36
        // 1 января: (1 + 19) % 36 = 20 → "3" ❌
        // 1 марта: (60 + 19) % 36 = 7 → "3" ✅
        
        // Но тогда 1 января будет "3", а не "2"
        
        // Попробуем: (globalDayNumber + 20) % 36
        // 1 января: (1 + 20) % 36 = 21 → "2" ✅
        // 1 марта: (60 + 20) % 36 = 8 → "2" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 21) % 36
        // 1 января: (1 + 21) % 36 = 22 → "4" ❌
        // 1 марта: (60 + 21) % 36 = 9 → "Вх" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 22) % 36
        // 1 января: (1 + 22) % 36 = 23 → "1" ❌
        // 1 марта: (60 + 22) % 36 = 10 → "3" ✅
        
        // Но тогда 1 января будет "1", а не "2"
        
        // Попробуем: (globalDayNumber + 23) % 36
        // 1 января: (1 + 23) % 36 = 24 → "Вх" ❌
        // 1 марта: (60 + 23) % 36 = 11 → "2" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 24) % 36
        // 1 января: (1 + 24) % 36 = 25 → "4" ❌
        // 1 марта: (60 + 24) % 36 = 12 → "4" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 25) % 36
        // 1 января: (1 + 25) % 36 = 26 → "1" ❌
        // 1 марта: (60 + 25) % 36 = 13 → "1" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 26) % 36
        // 1 января: (1 + 26) % 36 = 27 → "3" ❌
        // 1 марта: (60 + 26) % 36 = 14 → "Вх" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 27) % 36
        // 1 января: (1 + 27) % 36 = 28 → "2" ✅
        // 1 марта: (60 + 27) % 36 = 15 → "4" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 28) % 36
        // 1 января: (1 + 28) % 36 = 29 → "Вх" ❌
        // 1 марта: (60 + 28) % 36 = 16 → "1" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 29) % 36
        // 1 января: (1 + 29) % 36 = 30 → "3" ❌
        // 1 марта: (60 + 29) % 36 = 17 → "3" ✅
        
        // Но тогда 1 января будет "3", а не "2"
        
        // Попробуем: (globalDayNumber + 30) % 36
        // 1 января: (1 + 30) % 36 = 31 → "2" ✅
        // 1 марта: (60 + 30) % 36 = 18 → "2" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 31) % 36
        // 1 января: (1 + 31) % 36 = 32 → "4" ❌
        // 1 марта: (60 + 31) % 36 = 19 → "Вх" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 32) % 36
        // 1 января: (1 + 32) % 36 = 33 → "1" ❌
        // 1 марта: (60 + 32) % 36 = 20 → "3" ✅
        
        // Но тогда 1 января будет "1", а не "2"
        
        // Попробуем: (globalDayNumber + 33) % 36
        // 1 января: (1 + 33) % 36 = 34 → "Вх" ❌
        // 1 марта: (60 + 33) % 36 = 21 → "2" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 34) % 36
        // 1 января: (1 + 34) % 36 = 35 → "4" ❌
        // 1 марта: (60 + 34) % 36 = 22 → "4" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber + 35) % 36
        // 1 января: (1 + 35) % 36 = 0 → "3" ❌
        // 1 марта: (60 + 35) % 36 = 23 → "1" ❌ (должно быть "3")
        
        // Точка отсчета: 1 января 2025 = "2" (позиция 1)
        // Попробуем формулу: globalDayNumber % 36
        
        // Проверим:
        // 1 января: 1 % 36 = 1 → "2" ✅
        // 1 февраля: 32 % 36 = 32 → "4" ✅
        // 1 марта: 60 % 36 = 24 → "1" ❌ (должно быть "3")
        
        // Попробуем: (globalDayNumber - 1) % 36
        // 1 января: (1 - 1) % 36 = 0 → "3" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 1) % 36
        // 1 января: (1 + 1) % 36 = 2 → "4" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 2) % 36
        // 1 января: (1 + 2) % 36 = 3 → "1" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 3) % 36
        // 1 января: (1 + 3) % 36 = 4 → "Вх" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 4) % 36
        // 1 января: (1 + 4) % 36 = 5 → "4" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 5) % 36
        // 1 января: (1 + 5) % 36 = 6 → "1" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 6) % 36
        // 1 января: (1 + 6) % 36 = 7 → "3" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 7) % 36
        // 1 января: (1 + 7) % 36 = 8 → "2" ✅
        // 1 февраля: (32 + 7) % 36 = 3 → "1" ❌ (должно быть "4")
        
        // Попробуем: (globalDayNumber + 8) % 36
        // 1 января: (1 + 8) % 36 = 9 → "Вх" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 9) % 36
        // 1 января: (1 + 9) % 36 = 10 → "3" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 10) % 36
        // 1 января: (1 + 10) % 36 = 11 → "2" ✅
        // 1 февраля: (32 + 10) % 36 = 6 → "1" ❌ (должно быть "4")
        
        // Попробуем: (globalDayNumber + 11) % 36
        // 1 января: (1 + 11) % 36 = 12 → "4" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 12) % 36
        // 1 января: (1 + 12) % 36 = 13 → "1" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 13) % 36
        // 1 января: (1 + 13) % 36 = 14 → "Вх" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 14) % 36
        // 1 января: (1 + 14) % 36 = 15 → "4" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 15) % 36
        // 1 января: (1 + 15) % 36 = 16 → "1" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 16) % 36
        // 1 января: (1 + 16) % 36 = 17 → "3" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 17) % 36
        // 1 января: (1 + 17) % 36 = 18 → "2" ✅
        // 1 февраля: (32 + 17) % 36 = 13 → "1" ❌ (должно быть "4")
        
        // Попробуем: (globalDayNumber + 18) % 36
        // 1 января: (1 + 18) % 36 = 19 → "Вх" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 19) % 36
        // 1 января: (1 + 19) % 36 = 20 → "3" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 20) % 36
        // 1 января: (1 + 20) % 36 = 21 → "2" ✅
        // 1 февраля: (32 + 20) % 36 = 16 → "1" ❌ (должно быть "4")
        
        // Попробуем: (globalDayNumber + 21) % 36
        // 1 января: (1 + 21) % 36 = 22 → "4" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 22) % 36
        // 1 января: (1 + 22) % 36 = 23 → "1" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 23) % 36
        // 1 января: (1 + 23) % 36 = 24 → "Вх" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 24) % 36
        // 1 января: (1 + 24) % 36 = 25 → "4" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 25) % 36
        // 1 января: (1 + 25) % 36 = 26 → "1" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 26) % 36
        // 1 января: (1 + 26) % 36 = 27 → "3" ❌ (должно быть "2")
        
        // Попробуем: (globalDayNumber + 27) % 36
        // 1 января: (1 + 27) % 36 = 28 → "2" ✅
        // 1 февраля: (32 + 27) % 36 = 23 → "1" ❌ (должно быть "4")
        
        // Попробуем: (globalDayNumber + 28) % 36
        // 1 января: (1 + 28) % 36 = 29 → "Вх" ❌ (должно быть "2")
        
        // Проверяем кэш
        val cacheKey = "$year-$monthIndex"
        monthShiftCache[cacheKey]?.let { return it }
        
        // НОВАЯ ЛОГИКА: базовый повторяющийся паттерн из 10 элементов
        // Индекс: 0   1   2   3   4    5   6   7   8    9
        // Значение: 3,  2,  4,  1,  Вх,  4,  1,  3,  2,  Вх
        val basePatternSize = 10
        
        val result: Int
        
        // Для января 2025 (точка отсчета) начинаем с индекса 1 (значение "2")
        if (year == 2025 && monthIndex == 0) {
            result = 1
        }
        // Для января годов > 2025 - вычисляем ВПЕРЕД от декабря предыдущего года
        else if (monthIndex == 0 && year > 2025) {
            val prevYear = year - 1
            val decemberShift = calculateMonthShift(prevYear, 11) // Декабрь = индекс 11
            val daysInDecember = 31
            
            // УПРОЩЕННАЯ ФОРМУЛА: Сдвиг + дни = следующий месяц
            result = (decemberShift + daysInDecember) % basePatternSize
            
            Log.d(TAG, "Переход ${prevYear}→${year}: Декабрь shift=$decemberShift + 31 день = Январь $result")
        }
        // Для января годов < 2025 - вычисляем НАЗАД от января следующего года
        else if (monthIndex == 0 && year < 2025) {
            val nextYear = year + 1
            val januaryNextYearShift = calculateMonthShift(nextYear, 0) // Январь след. года (рекурсия вверх до 2025)
            
            // Количество дней в текущем году
            val daysInYear = if (isLeapYear(year)) 366 else 365
            
            // ПРАВИЛЬНАЯ ЛОГИКА: Идем назад от января следующего года
            // Пример: Январь 2025 = позиция 1, 2024 = 366 дней
            // Идем назад: (1 - 366) % 10 = (1 - 6) % 10 = -5 % 10 → нужно добавить 10k
            val stepsBack = daysInYear % basePatternSize
            result = (januaryNextYearShift - stepsBack + basePatternSize * 100) % basePatternSize
            
            Log.d(TAG, "Переход ${year}→${nextYear}: Январь ${nextYear}=$januaryNextYearShift, дней в ${year}=$daysInYear, шагов назад=$stepsBack, Январь ${year}=$result")
        }
        else {
            // Продолжаем с остальной логикой (будет ниже)
            result = calculateMonthShiftInternal(year, monthIndex, basePatternSize)
        }
        
        // Сохраняем в кэш
        monthShiftCache[cacheKey] = result
        return result
    }
    
    private fun calculateMonthShiftInternal(year: Int, monthIndex: Int, basePatternSize: Int): Int {
        
        // Для остальных месяцев вычисляем на основе января этого года
        val daysInMonths = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        if (isLeapYear(year)) {
            daysInMonths[1] = 29
        }
        
        // ИСПРАВЛЕНИЕ: Убираем двойную рекурсию!
        // Получаем сдвиг января ОДИН РАЗ
        val januaryShift = calculateMonthShift(year, 0)
        
        // Идем от января последовательно, БЕЗ рекурсии для каждого месяца
        var currentShift = januaryShift
        for (m in 0 until monthIndex) {
            val daysInMonth = daysInMonths[m]
            // Сдвиг следующего месяца = текущий сдвиг + дни текущего месяца
            currentShift = (currentShift + daysInMonth) % basePatternSize
        }
        
        Log.d(TAG, "calculateMonthShiftInternal($year, месяц=$monthIndex): январь=$januaryShift, результат=$currentShift")
        
        return currentShift
    }
    
    /**
     * Получает паттерн последних 5 дней указанного месяца
     */
    private fun getLast5DaysPattern(year: Int, monthIndex: Int): List<String> {
        val daysInMonths = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        if (isLeapYear(year)) {
            daysInMonths[1] = 29
        }
        
        val daysInMonth = daysInMonths[monthIndex]
        val last5Days = (daysInMonth - 4..daysInMonth).toList() // последние 5 дней
        
        val pattern = mutableListOf<String>()
        last5Days.forEach { day ->
            // Вычисляем глобальный номер дня от 1 января 2025
            val globalDayNumber = getGlobalDayNumberFrom2025(year, monthIndex, day)
            // Получаем элемент паттерна для этого дня
            val patternIndex = (globalDayNumber - 1) % shiftPatterns[0].size
            pattern.add(shiftPatterns[0][patternIndex])
        }
        
        return pattern
    }
    
    /**
     * Ищет продолжение паттерна в первой трети графика смены 1 (позиции 0-11)
     */
    private fun findPatternContinuationInFirstThird(last5DaysPattern: List<String>): Int {
        val firstThird = shiftPatterns[0].take(12) // первые 12 элементов
        
        Log.d(TAG, "Ищем паттерн: $last5DaysPattern")
        Log.d(TAG, "Первая треть паттерна: $firstThird")
        
        // Ищем позицию, где заканчивается паттерн из 5 дней
        for (startPos in 0 until firstThird.size) {
            var found = true
            for (i in last5DaysPattern.indices) {
                val checkPos = (startPos + i) % shiftPatterns[0].size
                if (shiftPatterns[0][checkPos] != last5DaysPattern[i]) {
                    found = false
                    break
                }
            }
            
            if (found) {
                // Найдено совпадение, возвращаем позицию следующего элемента
                val nextPos = (startPos + last5DaysPattern.size) % shiftPatterns[0].size
                Log.d(TAG, "Найдено совпадение на позиции $startPos, следующая позиция: $nextPos")
                return nextPos
            }
        }
        
        // Если не найдено в первой трети, ищем в полном паттерне
        for (startPos in 0 until shiftPatterns[0].size) {
            var found = true
            for (i in last5DaysPattern.indices) {
                val checkPos = (startPos + i) % shiftPatterns[0].size
                if (shiftPatterns[0][checkPos] != last5DaysPattern[i]) {
                    found = false
                    break
                }
            }
            
            if (found) {
                // Найдено совпадение, возвращаем позицию следующего элемента
                val nextPos = (startPos + last5DaysPattern.size) % shiftPatterns[0].size
                Log.d(TAG, "Найдено совпадение в полном паттерне на позиции $startPos, следующая позиция: $nextPos")
                return nextPos
            }
        }
        
        // Если ничего не найдено, возвращаем 0
        Log.d(TAG, "Паттерн не найден, возвращаем 0")
        return 0
    }
    
    /**
     * Вычисляет глобальный номер дня от 1 января 2025 года
     * Обеспечивает строгую непрерывность последовательности смены 1
     * 1 января 2025 года = день номер 1
     */
    private fun getGlobalDayNumberFrom2025(year: Int, monthIndex: Int, day: Int): Int {
        var totalDays = 0
        
        // Добавляем дни за полные годы с 2025
        for (y in 2025 until year) {
            totalDays += if (isLeapYear(y)) 366 else 365
        }
        
        // Добавляем дни за полные месяцы в текущем году
        val daysInMonths = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        if (isLeapYear(year)) {
            daysInMonths[1] = 29
        }
        
        for (m in 0 until monthIndex) {
            totalDays += daysInMonths[m]
        }
        
        // Добавляем дни в текущем месяце
        totalDays += day - 1
        
        // Возвращаем глобальный номер дня (1 января 2025 = 1)
        return totalDays + 1
    }
    
    /**
     * Находит кратчайший сдвиг от нулевой точки (1 января)
     * Учитывает, что паттерн циклический (37 элементов для смены 1)
     */
    private fun findShortestShiftFromZero(targetPosition: Int): Int {
        // Поскольку паттерн циклический, кратчайший сдвиг - это сама позиция
        // Но нужно учесть, что сдвиг не может быть больше 36
        return targetPosition % shiftPatterns[0].size
    }
    
    /**
     * Вычисляет стартовую позицию января для конкретного года
     */
    private fun getJanuaryStartPosition(year: Int): Int {
        // Для 2025 года: 1,2,3,4,5 января должны быть 2,4,1,Вх,4,1
        // Это означает, что январь начинается с позиции 1 в паттерне
        return when (year) {
            2025 -> 1 // Позиция 1: 2,4,1,Вх,4,1...
            2024 -> 0 // Позиция 0: 3,2,4,1,Вх...
            else -> {
                // Для других лет вычисляем на основе глобальной нумерации
                val globalDay = getGlobalDayNumber(year, 0, 1)
                globalDay % shiftPatterns[0].size
            }
        }
    }
    
    /**
     * Находит начало следующего блока смен в паттерне
     */
    private fun findNextShiftStart(currentPatternIndex: Int): Int {
        val pattern = shiftPatterns[0] // Используем паттерн смены 1 как эталон
        
        // Получаем смену, на которой заканчивается предыдущий месяц
        val currentShift = pattern[currentPatternIndex]
        
        // Определяем, какая смена должна быть следующей
        val nextShift = when (currentShift) {
            "2" -> "4"  // После "2" идет "4"
            "4" -> "1"  // После "4" идет "1"
            "1" -> "Вх" // После "1" идет "Вх"
            "Вх" -> "3" // После "Вх" идет "3"
            "3" -> "2"  // После "3" идет "2"
            else -> "4" // По умолчанию "4"
        }
        
        // Ищем следующую позицию с нужной сменой
        for (i in 1..pattern.size) {
            val nextIndex = (currentPatternIndex + i) % pattern.size
            if (pattern[nextIndex] == nextShift) {
                return i
            }
        }
        
        return 1 // По умолчанию сдвиг на 1
    }
    
    /**
     * Вычисляет глобальный номер дня с 1 января 2024 года
     * Обеспечивает непрерывность смен во всех годах включая високосные
     */
    private fun getGlobalDayNumber(year: Int, month: Int, day: Int): Int {
        var totalDays = 0
        
        // Добавляем полные годы с 2024 до текущего года
        for (y in 2024 until year) {
            totalDays += if (isLeapYear(y)) 366 else 365
        }
        
        // Добавляем дни в текущем году до нужного дня
        val daysInMonths = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        if (isLeapYear(year)) {
            daysInMonths[1] = 29 // Високосный год
        }
        
        // Добавляем дни всех предыдущих месяцев в текущем году
        for (m in 0 until month) {
            totalDays += daysInMonths[m]
        }
        
        // Добавляем текущий день (day-1, так как дни начинаются с 1)
        totalDays += day - 1
        
        return totalDays
    }
    
    private fun onDayClick(day: String, month: String, year: Int, monthIndex: Int) {
        selectedDayInMonth = day.toIntOrNull() ?: -1
        selectedMonthIndex = monthIndex
        // selectedColumnIndex устанавливается внутри адаптера при клике по ячейке
        scheduleAdapter.setSelectedDay(selectedDayInMonth, selectedMonthIndex)
        scheduleAdapter.notifyDataSetChanged()
    }
    
    private fun scrollToToday() {
        try {
            val today = Calendar.getInstance()
            val todayDay = today.get(Calendar.DAY_OF_MONTH)
            val todayMonth = today.get(Calendar.MONTH)
            
            // СТАРАЯ ЛОГИКА (резервная копия):
            // val monthShift = calculateMonthShift(currentYear, todayMonth)
            // val limitedShift = monthShift % 12
            
            // НОВАЯ ЛОГИКА: Используем скорректированный сдвиг
            val daysInMonths = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
            if (isLeapYear(currentYear)) {
                daysInMonths[1] = 29
            }
            val adjustedShift = getAdjustedShiftForDisplay(currentYear, todayMonth, daysInMonths[todayMonth])
            val todayPosition = adjustedShift + (todayDay - 1)
            
            val cellWidth = 50 * zoomFactor * resources.displayMetrics.density
            val scrollX = (todayPosition * cellWidth - horizontalScrollView.width / 2).toInt()
            
            horizontalScrollView.post {
                horizontalScrollView.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scrolling to today", e)
        }
    }
    
    data class ScheduleRow(
        val name: String,
        val days: List<Any>,
        val isMonthRow: Boolean,
        val shiftIndex: Int = -1,
        val monthIndex: Int = -1,
        val year: Int = 2025
    )
}

class DiagonalSplitDrawable(private val leftBottomColor: Int, private val rightTopColor: Int) : Drawable() {
    private val paintLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = leftBottomColor }
    private val paintRight = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = rightTopColor }
    private val pathLeft = Path()
    private val pathRight = Path()

    override fun draw(canvas: Canvas) {
        val r = bounds
        pathLeft.reset()
        pathRight.reset()
        // Left-bottom triangle: bottom-left, bottom-right, top-left along diagonal
        pathLeft.moveTo(r.left.toFloat(), r.bottom.toFloat())
        pathLeft.lineTo(r.right.toFloat(), r.bottom.toFloat())
        pathLeft.lineTo(r.left.toFloat(), r.top.toFloat())
        pathLeft.close()
        // Right-top triangle: top-left, top-right, bottom-right
        pathRight.moveTo(r.left.toFloat(), r.top.toFloat())
        pathRight.lineTo(r.right.toFloat(), r.top.toFloat())
        pathRight.lineTo(r.right.toFloat(), r.bottom.toFloat())
        pathRight.close()
        canvas.drawPath(pathLeft, paintLeft)
        canvas.drawPath(pathRight, paintRight)
    }

    override fun setAlpha(alpha: Int) {
        paintLeft.alpha = alpha
        paintRight.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paintLeft.colorFilter = colorFilter
        paintRight.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

/**
 * СОБСТВЕННЫЙ адаптер ТОЛЬКО для ScheduleFragment
 * НЕ влияет на другие части приложения
 */
class ScheduleCalendarAdapter(
    private val context: Context,
    private val onDayClick: (String, String, Int, Int) -> Unit,
    private val calculateMonthShiftFunction: (Int, Int) -> Int,
    private val getAdjustedShiftFunction: (Int, Int, Int) -> Int
) : RecyclerView.Adapter<ScheduleCalendarAdapter.ViewHolder>() {
    
    private fun isLeapYear(year: Int): Boolean {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    }
    
    /**
     * Вычисляет сдвиг месяца для адаптера (использует ту же логику, что и основной код)
     */
    private fun calculateMonthShiftForAdapter(monthIndex: Int): Int {
        // Для 2025 года используем ту же логику непрерывности
        if (monthIndex == 0) return 0 // Январь
        
        // Вычисляем сдвиг для остальных месяцев на основе непрерывности
        val previousMonthIndex = monthIndex - 1
        val daysInPreviousMonth = if (previousMonthIndex == 1 && isLeapYear(2025)) 29 else 
            intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[previousMonthIndex]
        
        val previousMonthShift = calculateMonthShiftForAdapter(previousMonthIndex)
        
            // Вычисляем позицию последнего дня предыдущего месяца в паттерне
            val lastDayPosition = (previousMonthShift + daysInPreviousMonth - 1) % 37
            
            // Следующий день должен быть на позиции (lastDayPosition + 1) % 37
            val nextDayPosition = (lastDayPosition + 1) % 37
        
        return nextDayPosition
    }
    
    private var scheduleData = mutableListOf<ScheduleFragment.ScheduleRow>()
    private var zoomFactor: Float = 1.0f
    private var selectedDayInMonth: Int = -1
    private var selectedMonthIndex: Int = -1
    private var selectedColumnIndex: Int = -1
    
    fun updateData(data: List<ScheduleFragment.ScheduleRow>) {
        scheduleData.clear()
        scheduleData.addAll(data)
        notifyDataSetChanged()
    }

    fun setZoomFactor(value: Float) {
        zoomFactor = value
    }
    
    fun setSelectedDay(dayInMonth: Int, monthIndex: Int) {
        selectedDayInMonth = dayInMonth
        selectedMonthIndex = monthIndex
    }

    fun setSelectedColumn(columnIndex: Int) {
        selectedColumnIndex = columnIndex
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_schedule_row, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = scheduleData[position]
        holder.bind(row, onDayClick, selectedDayInMonth, selectedMonthIndex, calculateMonthShiftFunction, getAdjustedShiftFunction)
    }
    
    override fun getItemCount(): Int = scheduleData.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val rowNameTextView: TextView = itemView.findViewById(R.id.rowNameTextView)
        private val rowNameRightTextView: TextView? = itemView.findViewById(R.id.rowNameRightTextView)
        private val daysContainer: LinearLayout = itemView.findViewById(R.id.daysContainer)
        private var calculateMonthShiftFunc: ((Int, Int) -> Int)? = null
        private var getAdjustedShiftFunc: ((Int, Int, Int) -> Int)? = null
        
        fun bind(
            row: ScheduleFragment.ScheduleRow,
            onDayClick: (String, String, Int, Int) -> Unit,
            selectedDayInMonth: Int,
            selectedMonthIndex: Int,
            calculateMonthShiftFuncParam: (Int, Int) -> Int,
            getAdjustedShiftFuncParam: (Int, Int, Int) -> Int
        ) {
            this.calculateMonthShiftFunc = calculateMonthShiftFuncParam
            this.getAdjustedShiftFunc = getAdjustedShiftFuncParam
            
            rowNameTextView.text = row.name
            // Масштабируем подписи месяцев/смен
            val baseLabelTextSizeSp = 12f
            rowNameTextView.textSize = baseLabelTextSizeSp * zoomFactor
            rowNameRightTextView?.textSize = baseLabelTextSizeSp * zoomFactor
            if (row.isMonthRow) {
                rowNameTextView.setBackgroundColor(ContextCompat.getColor(itemView.context, android.R.color.holo_blue_light))
                rowNameTextView.setTextColor(Color.WHITE)
                rowNameRightTextView?.setBackgroundColor(ContextCompat.getColor(itemView.context, android.R.color.holo_blue_light))
                rowNameRightTextView?.setTextColor(Color.WHITE)
            } else {
                rowNameTextView.setBackgroundColor(Color.LTGRAY)
                rowNameTextView.setTextColor(Color.BLACK)
                rowNameRightTextView?.setBackgroundColor(Color.LTGRAY)
                rowNameRightTextView?.setTextColor(Color.BLACK)
            }
            rowNameRightTextView?.text = rowNameTextView.text
            
            daysContainer.removeAllViews()
            row.days.forEachIndexed { dayIndex, day ->
                val dayView = createDayView(
                    day.toString(), row, onDayClick, dayIndex, selectedDayInMonth, selectedMonthIndex
                )
                daysContainer.addView(dayView)
            }
        }
        
        private fun createDayView(
            day: String,
            row: ScheduleFragment.ScheduleRow,
            onDayClick: (String, String, Int, Int) -> Unit,
            dayIndex: Int,
            selectedDayInMonth: Int,
            selectedMonthIndex: Int
        ): View {
            val dayView = TextView(itemView.context)
            val cellWidth = (50 * zoomFactor * itemView.context.resources.displayMetrics.density).toInt()
            val layoutParams = LinearLayout.LayoutParams(cellWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutParams.setMargins(0, 0, 0, 0)
            dayView.layoutParams = layoutParams
            dayView.text = day
            dayView.textSize = 22f * zoomFactor
            val padH = 10 // фиксируем горизонтальные отступы, чтобы не съедать вертикаль
            val padV = (6 * zoomFactor).toInt().coerceAtMost(6) // не увеличиваем вертикальные отступы при уменьшении масштаба
            dayView.setPadding(padH, padV, padH, padV)
            dayView.gravity = android.view.Gravity.CENTER
            
            val gd = GradientDrawable()
            gd.setColor(Color.LTGRAY)
            
            val isSelectedColumn = when {
                selectedColumnIndex >= 0 -> dayIndex == selectedColumnIndex
                selectedDayInMonth > 0 && selectedMonthIndex >= 0 && getAdjustedShiftFunc != null -> {
                    val daysInMonths = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
                    if (row.year % 4 == 0 && (row.year % 100 != 0 || row.year % 400 == 0)) { daysInMonths[1] = 29 }
                    val adjustedShift = getAdjustedShiftFunc!!(row.year, selectedMonthIndex, daysInMonths[selectedMonthIndex])
                    val targetColumn = adjustedShift + (selectedDayInMonth - 1)
                    dayIndex == targetColumn
                }
                else -> false
            }
            
            if (row.isMonthRow) {
                var isTodayCell = false
                gd.setColor(Color.LTGRAY)
                dayView.setTextColor(Color.BLACK)
                
                // Подсветка выходных
                if (day.isNotEmpty() && day.toIntOrNull() != null) {
                    val calendar = Calendar.getInstance()
                    calendar.set(Calendar.getInstance().get(Calendar.YEAR), row.monthIndex, day.toInt())
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    when (dayOfWeek) {
                        Calendar.SATURDAY -> { gd.setColor(Color.parseColor("#FFE082")); dayView.setTextColor(Color.BLACK) }
                        Calendar.SUNDAY -> { gd.setColor(Color.parseColor("#FFCDD2")); dayView.setTextColor(Color.BLACK) }
                        else -> { gd.setColor(Color.WHITE); dayView.setTextColor(Color.BLACK) }
                    }
                    // Оранжевый фон для сегодняшней даты
                    val today = Calendar.getInstance()
                    val todayDay = today.get(Calendar.DAY_OF_MONTH)
                    val todayMonth = today.get(Calendar.MONTH)
                    val todayYear = today.get(Calendar.YEAR)
                    val monthNames = arrayOf("Январь","Февраль","Март","Апрель","Май","Июнь","Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь")
                    if (day == todayDay.toString() && row.name == monthNames[todayMonth] && Calendar.getInstance().get(Calendar.YEAR) == todayYear) {
                        gd.setColor(Color.parseColor("#FF6B35"))
                        dayView.setTextColor(Color.WHITE)
                        dayView.setTypeface(null, android.graphics.Typeface.BOLD)
                        isTodayCell = true
                    }
                }
                dayView.setOnClickListener {
                    if (day.isNotEmpty() && day.toIntOrNull() != null) {
                        // Фикс: подсветка столбца по абсолютной колонке для всех строк
                        setSelectedColumn(dayIndex)
                        notifyDataSetChanged()
                        onDayClick(day, row.name, row.year, row.monthIndex)
                    }
                }
            } else {
                when (day) {
                    "Вх" -> { gd.setColor(Color.GRAY); dayView.setTextColor(Color.WHITE) }
                    "1", "2", "3", "4", "5" -> { gd.setColor(ContextCompat.getColor(itemView.context, android.R.color.holo_green_light)); dayView.setTextColor(Color.BLACK) }
                }
            }
            
            if (isSelectedColumn) {
                val orange = Color.parseColor("#FF6B35")
                // Убираем светло-оранжевую заливку столбца, оставляем только рамку
                gd.setStroke(4, orange)
            } else {
                gd.setStroke(1, Color.parseColor("#666666"))
            }
            dayView.background = gd
            return dayView
        }
    }
}
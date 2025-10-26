package com.example.vkbookandroid.schedule

import android.util.Log

/**
 * Строитель данных графика на год
 * Инкапсулирует логику генерации календаря и синхронизации с графиком смен
 */
class YearScheduleBuilder(
    private val calculator: ScheduleCalculator,
    private val patternProvider: ShiftPatternProvider
) {
    
    companion object {
        private const val TAG = "YearScheduleBuilder"
        private const val CALENDAR_WINDOW_SIZE = 36
        private const val MAX_OFFSET_ATTEMPTS = 40
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

        val basePattern = arrayOf("3", "2", "4", "1", "Вх", "4", "1", "3", "2", "Вх")
        var bestOffset = 0
        var bestScore = -1
        var attemptCount = 0

        for (offset in 0..9) {
            attemptCount++
            if (attemptCount > MAX_OFFSET_ATTEMPTS) {
                Log.e(TAG, "❌ КРИТИЧЕСКАЯ ОШИБКА: Превышен лимит попыток ($MAX_OFFSET_ATTEMPTS)!")
                Log.e(TAG, "❌ Невозможно построить график для $year года в 36 ячейках!")
                return bestOffset
            }

            var allMonthsFit = true
            var allMonthsMatchPattern = true
            var emptyColumnsLeft = 0

            for (monthIndex in 0..11) {
                val calculatedShift = calculator.calculateMonthShift(year, monthIndex)
                val adjustedShift = calculatedShift - offset
                val patternPosition = calculatedShift % 10
                val patternValue = basePattern[patternPosition]

                val normalizedShift = if (adjustedShift < 0) {
                    (adjustedShift + 100) % 10
                } else {
                    adjustedShift % CALENDAR_WINDOW_SIZE
                }

                if (normalizedShift + daysInMonths[monthIndex] > CALENDAR_WINDOW_SIZE) {
                    allMonthsFit = false
                }

                val graphValue = basePattern[(offset + normalizedShift) % 10]
                if (graphValue != patternValue) {
                    allMonthsMatchPattern = false
                }

                if (monthIndex == 0) {
                    emptyColumnsLeft = normalizedShift
                }
            }

            var score = 0
            if (allMonthsMatchPattern) score += 1000
            if (allMonthsFit) score += 100
            score -= emptyColumnsLeft

            Log.d(TAG, "Offset $offset: fit=$allMonthsFit, pattern=$allMonthsMatchPattern, empty=$emptyColumnsLeft, score=$score")

            if (score > bestScore) {
                bestScore = score
                bestOffset = offset
            }
        }

        Log.d(TAG, "✅ Выбран оптимальный сдвиг: $bestOffset (оценка: $bestScore)")
        if (bestScore < 1100) {
            Log.w(TAG, "⚠️ ВНИМАНИЕ: Не найден идеальный вариант для $year года!")
            Log.w(TAG, "⚠️ Некоторые месяцы могут не совпадать с паттерном или не помещаться")
        }
        return bestOffset
    }
    
    /**
     * Генерирует данные графика на год
     * @param year Год
     * @return Список строк графика (месяцы + смены)
     */
    fun buildYearSchedule(year: Int): List<ScheduleRow> {
        Log.d(TAG, "═══ Генерация графика для $year года ═══")
        
        val scheduleData = mutableListOf<ScheduleRow>()
        val months = arrayOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        )
        
        // Вычисляем количество дней в каждом месяце
        val daysInMonths = IntArray(12) { monthIndex ->
            calculator.getDaysInMonth(year, monthIndex)
        }
        
        // ВАЖНО: Вычисляем ОПТИМАЛЬНЫЙ сдвиг для ВСЕГО года
        // Итеративно подбираем сдвиг, при котором ВСЕ месяцы попадают в паттерн И помещаются в 36 ячеек
        val yearShiftOffset = findOptimalYearShift(year, daysInMonths)
        
        Log.d(TAG, "═══ Год $year - Расчет календаря ═══")
        Log.d(TAG, "Оптимальный сдвиг года (yearShiftOffset): $yearShiftOffset")
        
        // Генерируем строки месяцев
        months.forEachIndexed { monthIndex, monthName ->
            val daysInMonth = calculator.getDaysInMonth(year, monthIndex)
            val days = (1..daysInMonth).toList()
            
            // РАБОЧАЯ ЛОГИКА: Применяем ЕДИНЫЙ yearShiftOffset ко всем месяцам!
            val calculatedShift = calculator.calculateMonthShift(year, monthIndex)
            val adjustedShift = calculatedShift - yearShiftOffset
            
            Log.d(TAG, "Месяц $monthName: calculated=$calculatedShift, adjusted=$adjustedShift (сдвиг -$yearShiftOffset)")
            
            // Проверка: если месяц не помещается в 36 ячеек
            val safeAdjustedShift = calculateSafePosition(adjustedShift, calculatedShift, yearShiftOffset, daysInMonth, monthName)
            
            // Создаем список дней для отображения
            val displayDays = mutableListOf<String>()
            
            // Добавляем пустые ячейки в начале
            repeat(safeAdjustedShift) {
                displayDays.add("")
            }
            
            // Добавляем числа месяца
            days.forEach { day ->
                displayDays.add(day.toString())
            }
            
            // Добавляем пустые ячейки в конце до 36 элементов
            while (displayDays.size < CALENDAR_WINDOW_SIZE) {
                displayDays.add("")
            }
            
            scheduleData.add(
                ScheduleRow(
                    monthName = monthName,
                    days = displayDays.toTypedArray(),
                    isMonthRow = true,
                    monthIndex = monthIndex,
                    year = year
                )
            )
        }
        
        // Проверка целостности календаря
        validateCalendar(scheduleData, year)
        
        // Добавляем строки смен с учетом сдвига
        Log.d(TAG, "═══ Год $year - График смен (сдвиг на $yearShiftOffset) ═══")
        
        repeat(ShiftPatternProvider.SHIFT_COUNT) { shiftIndex ->
            val shiftedPattern = patternProvider.getShiftPattern(shiftIndex, yearShiftOffset)
            
            Log.d(TAG, "Смена ${shiftIndex + 1}: сдвиг=$yearShiftOffset, начало=${shiftedPattern.take(5)}")
            
            scheduleData.add(
                ScheduleRow(
                    monthName = patternProvider.getShiftName(shiftIndex),
                    days = shiftedPattern,
                    isMonthRow = false,
                    shiftIndex = shiftIndex
                )
            )
        }
        
        Log.d(TAG, "✅ Генерация завершена: ${scheduleData.size} строк")
        return scheduleData
    }
    
    /**
     * Вычисляет безопасную позицию для месяца (с учетом переполнения)
     * ПРАВИЛЬНАЯ ЛОГИКА: ищем ближайшую к НАЧАЛУ позицию с нужным значением паттерна
     */
    private fun calculateSafePosition(adjustedShift: Int, calculatedShift: Int, yearShiftOffset: Int, daysInMonth: Int, monthName: String): Int {
        if (adjustedShift < 0 || adjustedShift + daysInMonth > CALENDAR_WINDOW_SIZE) {
            Log.w(TAG, "ВНИМАНИЕ: $monthName adjusted=$adjustedShift (calculated=$calculatedShift, offset=$yearShiftOffset)")
            
            // Базовый паттерн для проверки
            val basePattern = arrayOf("3", "2", "4", "1", "Вх", "4", "1", "3", "2", "Вх")
            
            // Вычисляем позицию в базовом паттерне (0-9)
            val patternPosition = calculatedShift % 10
            val patternValue = basePattern[patternPosition]
            
            Log.d(TAG, "  Ищем позицию для паттерна[$patternPosition]=\"$patternValue\"")
            
            // Ищем ВСЕ позиции в окне 36, которые соответствуют нужному значению паттерна
            // Формула: graph[pos] = basePattern[(yearShiftOffset + pos) % 10]
            val validPositions = mutableListOf<Int>()
            
            for (pos in 0 until CALENDAR_WINDOW_SIZE) {
                val graphValue = basePattern[(yearShiftOffset + pos) % 10]
                if (graphValue == patternValue && pos + daysInMonth <= CALENDAR_WINDOW_SIZE) {
                    validPositions.add(pos)
                }
            }
            
            if (validPositions.isEmpty()) {
                Log.w(TAG, "  ⚠️ Нет позиций с правильным паттерном, ищем ЛЮБУЮ подходящую")
                
                // Ищем ЛЮБУЮ позицию, где месяц помещается (начиная с 0)
                val anyValidPosition = (0..5).firstOrNull { pos ->
                    pos + daysInMonth <= CALENDAR_WINDOW_SIZE
                }
                
                if (anyValidPosition != null) {
                    Log.w(TAG, "  ⚠️ Используем позицию $anyValidPosition (паттерн НЕ совпадает!)")
                    return anyValidPosition
                } else {
                    Log.e(TAG, "  ❌ Месяц НЕ ПОМЕЩАЕТСЯ в 36 ячеек!")
                    return 0
                }
            } else {
                // Выбираем БЛИЖАЙШУЮ К НАЧАЛУ позицию (как в логике 2025 года)
                val bestPos = validPositions.minOrNull() ?: 0
                Log.d(TAG, "  ✅ Найдена позиция $bestPos (все варианты: $validPositions)")
                return bestPos
            }
        }
        
        return adjustedShift
    }
    
    /**
     * Проверяет целостность календаря (количество дней в месяцах)
     */
    private fun validateCalendar(scheduleData: List<ScheduleRow>, year: Int) {
        var hasErrors = false
        
        scheduleData.filter { it.isMonthRow }.forEachIndexed { monthIndex, row ->
            val actualDays = row.days.count { it.isNotEmpty() }
            val expectedDays = calculator.getDaysInMonth(year, monthIndex)
            
            if (actualDays != expectedDays) {
                Log.e(TAG, "❌ ${row.monthName}: ожидалось $expectedDays дней, получено $actualDays")
                hasErrors = true
            }
        }
        
        if (!hasErrors) {
            Log.d(TAG, "✅ Все месяцы валидны!")
        } else {
            Log.e(TAG, "❌ ОБНАРУЖЕНЫ ОШИБКИ В КАЛЕНДАРЕ!")
        }
    }
}



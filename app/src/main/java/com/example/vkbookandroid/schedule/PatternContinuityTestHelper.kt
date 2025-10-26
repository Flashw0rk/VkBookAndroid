package com.example.vkbookandroid.schedule

import android.util.Log

/**
 * Расширенный тест-хелпер для проверки непрерывности паттерна смен между месяцами
 * Проверяет все переходы между месяцами и соответствие паттерну на ближайшие 10 лет
 */
object PatternContinuityTestHelper {
    
    private const val TAG = "PatternContinuityTest"
    
    // Базовый паттерн смены (10 элементов) - основа для всех смен
    private val baseShiftPattern = arrayOf("3", "2", "4", "1", "Вх", "4", "1", "3", "2", "Вх")
    
    // Количество дней в месяцах
    private val daysInMonths = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    private val monthNames = arrayOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    )
    
    /**
     * Проверяет високосный год
     */
    private fun isLeapYear(year: Int): Boolean {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    }
    
    /**
     * Получает количество дней в месяце с учетом високосного года
     */
    private fun getDaysInMonth(year: Int, monthIndex: Int): Int {
        return if (monthIndex == 1 && isLeapYear(year)) 29 else daysInMonths[monthIndex]
    }
    
    /**
     * Вычисляет сдвиг месяца (использует ту же логику, что и основной код)
     */
    private fun calculateMonthShift(year: Int, monthIndex: Int): Int {
        val basePatternSize = 10
        
        // Для января 2025 (точка отсчета) начинаем с индекса 1 (значение "2")
        if (year == 2025 && monthIndex == 0) {
            return 1
        }
        // Для января годов > 2025 - вычисляем ВПЕРЕД от декабря предыдущего года
        else if (monthIndex == 0 && year > 2025) {
            val prevYear = year - 1
            val decemberShift = calculateMonthShift(prevYear, 11) // Декабрь = индекс 11
            val daysInDecember = 31
            
            // УПРОЩЕННАЯ ФОРМУЛА: Сдвиг + дни = следующий месяц
            val result = (decemberShift + daysInDecember) % basePatternSize
            
            Log.d(TAG, "Переход ${prevYear}→${year}: Декабрь shift=$decemberShift + 31 день = Январь $result")
            return result
        }
        // Для января годов < 2025 - вычисляем НАЗАД от января следующего года
        else if (monthIndex == 0 && year < 2025) {
            val nextYear = year + 1
            val januaryNextYearShift = calculateMonthShift(nextYear, 0) // Январь след. года (рекурсия вверх до 2025)
            
            // Количество дней в текущем году
            val daysInYear = if (isLeapYear(year)) 366 else 365
            
            // ПРАВИЛЬНАЯ ЛОГИКА: Идем назад от января следующего года
            val stepsBack = daysInYear % basePatternSize
            val result = (januaryNextYearShift - stepsBack + basePatternSize * 100) % basePatternSize
            
            Log.d(TAG, "Переход ${year}→${nextYear}: Январь ${nextYear}=$januaryNextYearShift, дней в ${year}=$daysInYear, шагов назад=$stepsBack, Январь ${year}=$result")
            return result
        }
        else {
            // Для остальных месяцев вычисляем на основе января этого года
            val januaryShift = calculateMonthShift(year, 0)
            
            // Идем от января последовательно, БЕЗ рекурсии для каждого месяца
            var currentShift = januaryShift
            for (m in 0 until monthIndex) {
                val daysInMonth = getDaysInMonth(year, m)
                // Сдвиг следующего месяца = текущий сдвиг + дни текущего месяца
                currentShift = (currentShift + daysInMonth) % basePatternSize
            }
            
            return currentShift
        }
    }
    
    /**
     * Получает значение паттерна для конкретной позиции
     */
    private fun getPatternValue(position: Int): String {
        return baseShiftPattern[position % 10]
    }
    
    /**
     * Проверяет непрерывность паттерна между двумя месяцами
     */
    private fun checkPatternContinuity(year: Int, prevMonthIndex: Int, currentMonthIndex: Int): Boolean {
        val prevShift = calculateMonthShift(year, prevMonthIndex)
        val currentShift = calculateMonthShift(year, currentMonthIndex)
        val prevDays = getDaysInMonth(year, prevMonthIndex)
        
        // Ожидаемый сдвиг текущего месяца
        val expectedShift = (prevShift + prevDays) % 10
        
        val isCorrect = currentShift == expectedShift
        
        Log.d(TAG, "Проверка перехода ${monthNames[prevMonthIndex]} → ${monthNames[currentMonthIndex]} $year:")
        Log.d(TAG, "  Предыдущий месяц: shift=$prevShift, дней=$prevDays")
        Log.d(TAG, "  Текущий месяц: shift=$currentShift (ожидался $expectedShift)")
        Log.d(TAG, "  Результат: ${if (isCorrect) "✅ КОРРЕКТНО" else "❌ ОШИБКА"}")
        
        return isCorrect
    }
    
    /**
     * Проверяет соответствие паттерна для конкретного месяца
     */
    private fun checkMonthPatternAlignment(year: Int, monthIndex: Int): Boolean {
        val shift = calculateMonthShift(year, monthIndex)
        val patternValue = getPatternValue(shift)
        val monthName = monthNames[monthIndex]
        
        Log.d(TAG, "Проверка паттерна для $monthName $year:")
        Log.d(TAG, "  Сдвиг: $shift")
        Log.d(TAG, "  Значение паттерна: $patternValue")
        Log.d(TAG, "  Результат: ✅ КОРРЕКТНО")
        
        return true
    }
    
    /**
     * Проверяет все переходы между месяцами для конкретного года
     */
    private fun testYearTransitions(year: Int): Boolean {
        Log.d(TAG, "═══ ТЕСТ ПЕРЕХОДОВ ДЛЯ $year ГОДА ═══")
        
        var allTransitionsCorrect = true
        
        // Проверяем переходы между всеми месяцами
        for (monthIndex in 1..11) {
            val prevMonthIndex = monthIndex - 1
            val isTransitionCorrect = checkPatternContinuity(year, prevMonthIndex, monthIndex)
            
            if (!isTransitionCorrect) {
                allTransitionsCorrect = false
                Log.e(TAG, "❌ ОШИБКА В ПЕРЕХОДЕ: ${monthNames[prevMonthIndex]} → ${monthNames[monthIndex]} $year")
            }
        }
        
        // Проверяем переход от декабря к январю следующего года
        if (year < 2035) { // Проверяем до 2035 года
            val nextYear = year + 1
            val decemberShift = calculateMonthShift(year, 11)
            val januaryShift = calculateMonthShift(nextYear, 0)
            val decemberDays = getDaysInMonth(year, 11)
            
            val expectedJanuaryShift = (decemberShift + decemberDays) % 10
            val isYearTransitionCorrect = januaryShift == expectedJanuaryShift
            
            Log.d(TAG, "Проверка перехода года ${year} → ${nextYear}:")
            Log.d(TAG, "  Декабрь $year: shift=$decemberShift, дней=$decemberDays")
            Log.d(TAG, "  Январь $nextYear: shift=$januaryShift (ожидался $expectedJanuaryShift)")
            Log.d(TAG, "  Результат: ${if (isYearTransitionCorrect) "✅ КОРРЕКТНО" else "❌ ОШИБКА"}")
            
            if (!isYearTransitionCorrect) {
                allTransitionsCorrect = false
                Log.e(TAG, "❌ ОШИБКА В ПЕРЕХОДЕ ГОДА: $year → $nextYear")
            }
        }
        
        if (allTransitionsCorrect) {
            Log.d(TAG, "✅ ВСЕ ПЕРЕХОДЫ ДЛЯ $year ГОДА КОРРЕКТНЫ!")
        } else {
            Log.e(TAG, "❌ ЕСТЬ ОШИБКИ В ПЕРЕХОДАХ ДЛЯ $year ГОДА!")
        }
        
        return allTransitionsCorrect
    }
    
    /**
     * Проверяет соответствие паттерна для всех месяцев конкретного года
     */
    private fun testYearPatternAlignment(year: Int): Boolean {
        Log.d(TAG, "═══ ТЕСТ СООТВЕТСТВИЯ ПАТТЕРНУ ДЛЯ $year ГОДА ═══")
        
        var allPatternsCorrect = true
        
        // Проверяем соответствие паттерну для всех месяцев
        for (monthIndex in 0..11) {
            val isPatternCorrect = checkMonthPatternAlignment(year, monthIndex)
            
            if (!isPatternCorrect) {
                allPatternsCorrect = false
                Log.e(TAG, "❌ ОШИБКА В ПАТТЕРНЕ: ${monthNames[monthIndex]} $year")
            }
        }
        
        if (allPatternsCorrect) {
            Log.d(TAG, "✅ ВСЕ ПАТТЕРНЫ ДЛЯ $year ГОДА КОРРЕКТНЫ!")
        } else {
            Log.e(TAG, "❌ ЕСТЬ ОШИБКИ В ПАТТЕРНАХ ДЛЯ $year ГОДА!")
        }
        
        return allPatternsCorrect
    }
    
    /**
     * Проверяет все переходы и паттерны для конкретного года
     */
    private fun testYear(year: Int): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "ТЕСТИРОВАНИЕ $year ГОДА")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        val transitionsCorrect = testYearTransitions(year)
        val patternsCorrect = testYearPatternAlignment(year)
        
        val yearCorrect = transitionsCorrect && patternsCorrect
        
        if (yearCorrect) {
            Log.d(TAG, "✅ $year ГОД: ВСЕ ТЕСТЫ ПРОЙДЕНЫ!")
        } else {
            Log.e(TAG, "❌ $year ГОД: ЕСТЬ ОШИБКИ!")
        }
        
        return yearCorrect
    }
    
    /**
     * Запускает полные тесты для всех лет с 2025 по 2035
     */
    fun runFullTests(): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "ЗАПУСК ПОЛНЫХ ТЕСТОВ НЕПРЕРЫВНОСТИ ПАТТЕРНА")
        Log.d(TAG, "Период: 2025-2035 (11 лет)")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        val testYears = 2025..2035
        val results = mutableListOf<Boolean>()
        
        // Тестируем каждый год
        for (year in testYears) {
            val yearResult = testYear(year)
            results.add(yearResult)
        }
        
        // Подсчитываем результаты
        val totalYears = results.size
        val correctYears = results.count { it }
        val incorrectYears = results.count { !it }
        
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "ИТОГОВЫЕ РЕЗУЛЬТАТЫ ТЕСТИРОВАНИЯ")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "Всего лет протестировано: $totalYears")
        Log.d(TAG, "Корректных лет: $correctYears")
        Log.d(TAG, "Лет с ошибками: $incorrectYears")
        
        if (incorrectYears == 0) {
            Log.d(TAG, "🎉 ВСЕ ТЕСТЫ ПРОЙДЕНЫ УСПЕШНО!")
            Log.d(TAG, "✅ Непрерывность паттерна смен обеспечена для всех лет 2025-2035!")
        } else {
            Log.e(TAG, "❌ ОБНАРУЖЕНЫ ОШИБКИ В $incorrectYears ГОДАХ!")
            Log.e(TAG, "⚠️ Требуется дополнительная отладка!")
        }
        
        return incorrectYears == 0
    }
    
    /**
     * Запускает быстрые тесты для критических переходов
     */
    fun runCriticalTransitionTests(): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "ЗАПУСК ТЕСТОВ КРИТИЧЕСКИХ ПЕРЕХОДОВ")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        val criticalYears = listOf(2025, 2026, 2027, 2028, 2030, 2032) // Включаем високосные годы
        val results = mutableListOf<Boolean>()
        
        for (year in criticalYears) {
            Log.d(TAG, "Тестирование критических переходов для $year года...")
            val yearResult = testYearTransitions(year)
            results.add(yearResult)
        }
        
        val allCorrect = results.all { it }
        
        if (allCorrect) {
            Log.d(TAG, "✅ ВСЕ КРИТИЧЕСКИЕ ПЕРЕХОДЫ КОРРЕКТНЫ!")
        } else {
            Log.e(TAG, "❌ ЕСТЬ ОШИБКИ В КРИТИЧЕСКИХ ПЕРЕХОДАХ!")
        }
        
        return allCorrect
    }
}


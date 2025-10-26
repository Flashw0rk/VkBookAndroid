package com.example.vkbookandroid.schedule

import android.util.Log

/**
 * Анализатор переходов между месяцами
 * Специально анализирует отличие перехода к ноябрю 2026 от других переходов
 */
object November2026TransitionAnalyzer {
    
    private const val TAG = "November2026Transition"
    
    // Базовый паттерн смены (10 элементов)
    private val baseShiftPattern = arrayOf("3", "2", "4", "1", "Вх", "4", "1", "3", "2", "Вх")
    
    /**
     * Проверяет високосный год
     */
    private fun isLeapYear(year: Int): Boolean {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    }
    
    /**
     * Вычисляет сдвиг месяца (использует ту же логику, что и основной код)
     */
    private fun calculateMonthShift(year: Int, monthIndex: Int): Int {
        val basePatternSize = 10
        
        if (year == 2025 && monthIndex == 0) {
            return 1
        }
        else if (monthIndex == 0 && year > 2025) {
            val prevYear = year - 1
            val decemberShift = calculateMonthShift(prevYear, 11)
            val daysInDecember = 31
            val result = (decemberShift + daysInDecember) % basePatternSize
            return result
        }
        else if (monthIndex == 0 && year < 2025) {
            val nextYear = year + 1
            val januaryNextYearShift = calculateMonthShift(nextYear, 0)
            val daysInYear = if (isLeapYear(year)) 366 else 365
            val stepsBack = daysInYear % basePatternSize
            val result = (januaryNextYearShift - stepsBack + basePatternSize * 100) % basePatternSize
            return result
        }
        else {
            val januaryShift = calculateMonthShift(year, 0)
            val daysInMonths = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
            if (isLeapYear(year)) {
                daysInMonths[1] = 29
            }
            
            var currentShift = januaryShift
            for (m in 0 until monthIndex) {
                val daysInMonth = daysInMonths[m]
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
     * Анализирует переход между двумя месяцами
     */
    private fun analyzeTransition(year: Int, prevMonthIndex: Int, currentMonthIndex: Int): Boolean {
        val monthNames = arrayOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        )
        
        val prevShift = calculateMonthShift(year, prevMonthIndex)
        val currentShift = calculateMonthShift(year, currentMonthIndex)
        val daysInMonths = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        if (isLeapYear(year)) {
            daysInMonths[1] = 29
        }
        val prevDays = daysInMonths[prevMonthIndex]
        
        // Ожидаемый сдвиг текущего месяца
        val expectedShift = (prevShift + prevDays) % 10
        
        val isCorrect = currentShift == expectedShift
        
        Log.d(TAG, "Переход ${monthNames[prevMonthIndex]} → ${monthNames[currentMonthIndex]} $year:")
        Log.d(TAG, "  Предыдущий месяц: shift=$prevShift, дней=$prevDays")
        Log.d(TAG, "  Текущий месяц: shift=$currentShift (ожидался $expectedShift)")
        Log.d(TAG, "  Результат: ${if (isCorrect) "✅ КОРРЕКТНО" else "❌ ОШИБКА"}")
        
        return isCorrect
    }
    
    /**
     * Анализирует все переходы в 2026 году
     */
    fun analyzeAllTransitions2026(): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "АНАЛИЗ ВСЕХ ПЕРЕХОДОВ В 2026 ГОДУ")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        val year = 2026
        var allCorrect = true
        
        // Анализируем все переходы между месяцами
        for (monthIndex in 1..11) {
            val prevMonthIndex = monthIndex - 1
            val isTransitionCorrect = analyzeTransition(year, prevMonthIndex, monthIndex)
            
            if (!isTransitionCorrect) {
                allCorrect = false
                val monthNames = arrayOf(
                    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
                )
                Log.e(TAG, "❌ ОШИБКА В ПЕРЕХОДЕ: ${monthNames[prevMonthIndex]} → ${monthNames[monthIndex]} $year")
            }
        }
        
        if (allCorrect) {
            Log.d(TAG, "✅ ВСЕ ПЕРЕХОДЫ В 2026 ГОДУ КОРРЕКТНЫ!")
        } else {
            Log.e(TAG, "❌ ЕСТЬ ОШИБКИ В ПЕРЕХОДАХ 2026 ГОДА!")
        }
        
        return allCorrect
    }
    
    /**
     * Специальный анализ перехода к ноябрю 2026
     */
    fun analyzeNovember2026Transition(): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "СПЕЦИАЛЬНЫЙ АНАЛИЗ ПЕРЕХОДА К НОЯБРЮ 2026")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        val year = 2026
        val octoberIndex = 9
        val novemberIndex = 10
        
        // Получаем сдвиги месяцев
        val octoberShift = calculateMonthShift(year, octoberIndex)
        val novemberShift = calculateMonthShift(year, novemberIndex)
        
        Log.d(TAG, "Октябрь 2026:")
        Log.d(TAG, "  Сдвиг: $octoberShift")
        Log.d(TAG, "  Начинается с: ${getPatternValue(octoberShift)}")
        
        Log.d(TAG, "Ноябрь 2026:")
        Log.d(TAG, "  Сдвиг: $novemberShift")
        Log.d(TAG, "  Начинается с: ${getPatternValue(novemberShift)}")
        
        // Проверяем переход
        val octoberDays = 31
        val expectedNovemberShift = (octoberShift + octoberDays) % 10
        
        Log.d(TAG, "Проверка перехода:")
        Log.d(TAG, "  Октябрь сдвиг: $octoberShift")
        Log.d(TAG, "  Октябрь дней: $octoberDays")
        Log.d(TAG, "  Ожидаемый сдвиг ноября: $expectedNovemberShift")
        Log.d(TAG, "  Фактический сдвиг ноября: $novemberShift")
        
        val transitionCorrect = novemberShift == expectedNovemberShift
        
        if (transitionCorrect) {
            Log.d(TAG, "✅ ПЕРЕХОД ОКТЯБРЬ → НОЯБРЬ КОРРЕКТЕН!")
        } else {
            Log.e(TAG, "❌ ОШИБКА В ПЕРЕХОДЕ ОКТЯБРЬ → НОЯБРЬ!")
            Log.e(TAG, "   Ожидался сдвиг: $expectedNovemberShift")
            Log.e(TAG, "   Получен сдвиг: $novemberShift")
        }
        
        return transitionCorrect
    }
    
    /**
     * Сравнивает переход к ноябрю с другими переходами в 2026 году
     */
    fun compareNovemberTransitionWithOthers(): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "СРАВНЕНИЕ ПЕРЕХОДА К НОЯБРЮ С ДРУГИМИ ПЕРЕХОДАМИ")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        val year = 2026
        val monthNames = arrayOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        )
        
        val daysInMonths = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        if (isLeapYear(year)) {
            daysInMonths[1] = 29
        }
        
        // Анализируем все переходы и сравниваем с переходом к ноябрю
        for (monthIndex in 1..11) {
            val prevMonthIndex = monthIndex - 1
            val prevShift = calculateMonthShift(year, prevMonthIndex)
            val currentShift = calculateMonthShift(year, monthIndex)
            val prevDays = daysInMonths[prevMonthIndex]
            val expectedShift = (prevShift + prevDays) % 10
            
            val isCorrect = currentShift == expectedShift
            
            Log.d(TAG, "Переход ${monthNames[prevMonthIndex]} → ${monthNames[monthIndex]}:")
            Log.d(TAG, "  Предыдущий сдвиг: $prevShift, дней: $prevDays")
            Log.d(TAG, "  Текущий сдвиг: $currentShift (ожидался $expectedShift)")
            Log.d(TAG, "  Результат: ${if (isCorrect) "✅ КОРРЕКТНО" else "❌ ОШИБКА"}")
            
            // Специальное внимание к переходу к ноябрю
            if (monthIndex == 10) { // Ноябрь
                Log.d(TAG, "  🔍 СПЕЦИАЛЬНЫЙ АНАЛИЗ ПЕРЕХОДА К НОЯБРЮ:")
                Log.d(TAG, "    Октябрь сдвиг: $prevShift")
                Log.d(TAG, "    Октябрь дней: $prevDays")
                Log.d(TAG, "    Ожидаемый ноябрь: $expectedShift")
                Log.d(TAG, "    Фактический ноябрь: $currentShift")
                Log.d(TAG, "    Разница: ${currentShift - expectedShift}")
                
                if (!isCorrect) {
                    Log.e(TAG, "    ❌ ОШИБКА: Ноябрь имеет неправильный сдвиг!")
                    Log.e(TAG, "    ❌ Это единственный переход с ошибкой в 2026 году!")
                }
            }
        }
        
        return true
    }
    
    /**
     * Анализирует паттерн последних дней октября и первых дней ноября
     */
    fun analyzePatternContinuity(): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "АНАЛИЗ НЕПРЕРЫВНОСТИ ПАТТЕРНА")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        val year = 2026
        val octoberIndex = 9
        val novemberIndex = 10
        
        val octoberShift = calculateMonthShift(year, octoberIndex)
        val novemberShift = calculateMonthShift(year, novemberIndex)
        
        // Последние 5 дней октября
        val octoberLastDaysStart = octoberShift + 26 // 27-31 октября (5 дней)
        val octoberLastDays = mutableListOf<String>()
        for (i in 0 until 5) {
            val position = (octoberLastDaysStart + i) % 10
            octoberLastDays.add(baseShiftPattern[position])
        }
        
        // Первые 5 дней ноября
        val novemberFirstDays = mutableListOf<String>()
        for (i in 0 until 5) {
            val position = (novemberShift + i) % 10
            novemberFirstDays.add(baseShiftPattern[position])
        }
        
        Log.d(TAG, "Последние 5 дней октября 2026:")
        Log.d(TAG, "  Позиция начала: $octoberLastDaysStart")
        Log.d(TAG, "  Последовательность: ${octoberLastDays.joinToString(", ")}")
        
        Log.d(TAG, "Первые 5 дней ноября 2026:")
        Log.d(TAG, "  Позиция начала: $novemberShift")
        Log.d(TAG, "  Последовательность: ${novemberFirstDays.joinToString(", ")}")
        
        // Проверяем непрерывность
        val lastDayOctober = octoberLastDays.last()
        val firstDayNovember = novemberFirstDays.first()
        
        Log.d(TAG, "Проверка непрерывности:")
        Log.d(TAG, "  Последний день октября: $lastDayOctober")
        Log.d(TAG, "  Первый день ноября: $firstDayNovember")
        
        // Проверяем, что переход корректен
        val expectedFirstDayNovember = getPatternValue((octoberLastDaysStart + 4 + 1) % 10)
        val continuityCorrect = firstDayNovember == expectedFirstDayNovember
        
        if (continuityCorrect) {
            Log.d(TAG, "✅ НЕПРЕРЫВНОСТЬ ПАТТЕРНА КОРРЕКТНА!")
        } else {
            Log.e(TAG, "❌ ОШИБКА В НЕПРЕРЫВНОСТИ ПАТТЕРНА!")
            Log.e(TAG, "   Ожидался первый день ноября: $expectedFirstDayNovember")
            Log.e(TAG, "   Получен первый день ноября: $firstDayNovember")
        }
        
        return continuityCorrect
    }
    
    /**
     * Запускает полный анализ переходов
     */
    fun runFullTransitionAnalysis(): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "ЗАПУСК ПОЛНОГО АНАЛИЗА ПЕРЕХОДОВ")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        val allTransitionsCorrect = analyzeAllTransitions2026()
        val novemberTransitionCorrect = analyzeNovember2026Transition()
        val comparisonCorrect = compareNovemberTransitionWithOthers()
        val continuityCorrect = analyzePatternContinuity()
        
        val allCorrect = allTransitionsCorrect && novemberTransitionCorrect && comparisonCorrect && continuityCorrect
        
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "ИТОГОВЫЕ РЕЗУЛЬТАТЫ АНАЛИЗА ПЕРЕХОДОВ")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "Все переходы в 2026 году: ${if (allTransitionsCorrect) "✅ КОРРЕКТНЫ" else "❌ ЕСТЬ ОШИБКИ"}")
        Log.d(TAG, "Переход к ноябрю 2026: ${if (novemberTransitionCorrect) "✅ КОРРЕКТЕН" else "❌ ОШИБКА"}")
        Log.d(TAG, "Сравнение с другими переходами: ${if (comparisonCorrect) "✅ ЗАВЕРШЕНО" else "❌ ОШИБКА"}")
        Log.d(TAG, "Непрерывность паттерна: ${if (continuityCorrect) "✅ КОРРЕКТНА" else "❌ ОШИБКА"}")
        
        if (allCorrect) {
            Log.d(TAG, "🎉 ВСЕ ПЕРЕХОДЫ КОРРЕКТНЫ!")
        } else {
            Log.e(TAG, "❌ ЕСТЬ ОШИБКИ В ПЕРЕХОДАХ!")
        }
        
        return allCorrect
    }
}

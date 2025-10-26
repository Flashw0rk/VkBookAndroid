package com.example.vkbookandroid.schedule

import android.util.Log

/**
 * Анализатор паттерна для ноября 2026 года
 * Определяет правильный паттерн путем прослеживания логики от 2025 года
 */
object November2026PatternAnalyzer {
    
    private const val TAG = "November2026Analyzer"
    
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
     * Получает последовательность паттерна для конкретного количества дней
     */
    private fun getPatternSequence(startPosition: Int, days: Int): List<String> {
        val sequence = mutableListOf<String>()
        for (i in 0 until days) {
            val position = (startPosition + i) % 10
            sequence.add(baseShiftPattern[position])
        }
        return sequence
    }
    
    /**
     * Анализирует весь путь от 2025 года до ноября 2026
     */
    fun analyzePathToNovember2026(): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "АНАЛИЗ ПУТИ К НОЯБРЮ 2026 ГОДА")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        // Анализируем 2025 год
        Log.d(TAG, "═══ АНАЛИЗ 2025 ГОДА ═══")
        analyzeYear(2025)
        
        // Анализируем 2026 год до ноября
        Log.d(TAG, "═══ АНАЛИЗ 2026 ГОДА (до ноября) ═══")
        analyzeYearToMonth(2026, 10) // До ноября (индекс 10)
        
        // Специальный анализ перехода от октября к ноябрю 2026
        Log.d(TAG, "═══ СПЕЦИАЛЬНЫЙ АНАЛИЗ ПЕРЕХОДА ОКТЯБРЬ → НОЯБРЬ 2026 ═══")
        analyzeOctoberToNovember2026Transition()
        
        return true
    }
    
    /**
     * Анализирует весь год
     */
    private fun analyzeYear(year: Int) {
        val monthNames = arrayOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        )
        
        for (monthIndex in 0..11) {
            val shift = calculateMonthShift(year, monthIndex)
            val patternValue = getPatternValue(shift)
            val first5Days = getPatternSequence(shift, 5)
            
            Log.d(TAG, "${monthNames[monthIndex]} $year:")
            Log.d(TAG, "  Сдвиг: $shift")
            Log.d(TAG, "  Начинается с: $patternValue")
            Log.d(TAG, "  Первые 5 дней: ${first5Days.joinToString(", ")}")
        }
    }
    
    /**
     * Анализирует год до определенного месяца
     */
    private fun analyzeYearToMonth(year: Int, maxMonthIndex: Int) {
        val monthNames = arrayOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        )
        
        for (monthIndex in 0..maxMonthIndex) {
            val shift = calculateMonthShift(year, monthIndex)
            val patternValue = getPatternValue(shift)
            val first5Days = getPatternSequence(shift, 5)
            
            Log.d(TAG, "${monthNames[monthIndex]} $year:")
            Log.d(TAG, "  Сдвиг: $shift")
            Log.d(TAG, "  Начинается с: $patternValue")
            Log.d(TAG, "  Первые 5 дней: ${first5Days.joinToString(", ")}")
        }
    }
    
    /**
     * Специальный анализ перехода от октября к ноябрю 2026
     */
    private fun analyzeOctoberToNovember2026Transition() {
        val year = 2026
        val octoberIndex = 9
        val novemberIndex = 10
        
        // Получаем сдвиги месяцев
        val octoberShift = calculateMonthShift(year, octoberIndex)
        val novemberShift = calculateMonthShift(year, novemberIndex)
        
        Log.d(TAG, "Октябрь 2026:")
        Log.d(TAG, "  Сдвиг: $octoberShift")
        Log.d(TAG, "  Начинается с: ${getPatternValue(octoberShift)}")
        Log.d(TAG, "  Первые 5 дней: ${getPatternSequence(octoberShift, 5).joinToString(", ")}")
        
        Log.d(TAG, "Ноябрь 2026:")
        Log.d(TAG, "  Сдвиг: $novemberShift")
        Log.d(TAG, "  Начинается с: ${getPatternValue(novemberShift)}")
        Log.d(TAG, "  Первые 5 дней: ${getPatternSequence(novemberShift, 5).joinToString(", ")}")
        
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
        
        // Анализируем последние дни октября и первые дни ноября
        Log.d(TAG, "Детальный анализ:")
        Log.d(TAG, "  Последние 5 дней октября: ${getPatternSequence(octoberShift + 26, 5).joinToString(", ")}")
        Log.d(TAG, "  Первые 5 дней ноября: ${getPatternSequence(novemberShift, 5).joinToString(", ")}")
        
        // Проверяем непрерывность
        val lastDayOctober = getPatternValue(octoberShift + 30) // 31 октября
        val firstDayNovember = getPatternValue(novemberShift) // 1 ноября
        
        Log.d(TAG, "Проверка непрерывности:")
        Log.d(TAG, "  Последний день октября (31): $lastDayOctober")
        Log.d(TAG, "  Первый день ноября (1): $firstDayNovember")
        
        // Проверяем, что переход корректен
        val expectedFirstDayNovember = getPatternValue((octoberShift + 31) % 10)
        val continuityCorrect = firstDayNovember == expectedFirstDayNovember
        
        if (continuityCorrect) {
            Log.d(TAG, "✅ НЕПРЕРЫВНОСТЬ ПАТТЕРНА КОРРЕКТНА!")
        } else {
            Log.e(TAG, "❌ ОШИБКА В НЕПРЕРЫВНОСТИ ПАТТЕРНА!")
            Log.e(TAG, "   Ожидался первый день ноября: $expectedFirstDayNovember")
            Log.e(TAG, "   Получен первый день ноября: $firstDayNovember")
        }
    }
    
    /**
     * Определяет правильный паттерн для ноября 2026
     */
    fun determineCorrectNovember2026Pattern(): List<String> {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "ОПРЕДЕЛЕНИЕ ПРАВИЛЬНОГО ПАТТЕРНА ДЛЯ НОЯБРЯ 2026")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        val year = 2026
        val novemberIndex = 10
        
        val novemberShift = calculateMonthShift(year, novemberIndex)
        val correctPattern = getPatternSequence(novemberShift, 5)
        
        Log.d(TAG, "Ноябрь 2026:")
        Log.d(TAG, "  Сдвиг: $novemberShift")
        Log.d(TAG, "  Правильный паттерн (первые 5 дней): ${correctPattern.joinToString(", ")}")
        
        return correctPattern
    }
    
    /**
     * Запускает полный анализ
     */
    fun runFullAnalysis(): List<String> {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "ЗАПУСК ПОЛНОГО АНАЛИЗА ПАТТЕРНА НОЯБРЯ 2026")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        analyzePathToNovember2026()
        val correctPattern = determineCorrectNovember2026Pattern()
        
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "ИТОГОВЫЙ РЕЗУЛЬТАТ")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "Правильный паттерн для ноября 2026 (первые 5 дней): ${correctPattern.joinToString(", ")}")
        
        return correctPattern
    }
}


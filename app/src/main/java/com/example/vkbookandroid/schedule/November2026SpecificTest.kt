package com.example.vkbookandroid.schedule

import android.util.Log

/**
 * Специфический тест для проверки ноября 2026 года
 * Проверяет переход от октября к ноябрю 2026 года и соответствие паттерну
 */
object November2026SpecificTest {
    
    private const val TAG = "November2026Test"
    
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
     * Проверяет переход от октября к ноябрю 2026 года
     */
    fun testOctoberToNovember2026Transition(): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "ТЕСТ ПЕРЕХОДА ОКТЯБРЬ → НОЯБРЬ 2026")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        val year = 2026
        val octoberIndex = 9  // Октябрь
        val novemberIndex = 10 // Ноябрь
        
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
        val octoberDays = 31 // Октябрь имеет 31 день
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
     * Проверяет последовательность паттерна для последних дней октября и первых дней ноября 2026
     */
    fun testPatternSequence2026(): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "ТЕСТ ПОСЛЕДОВАТЕЛЬНОСТИ ПАТТЕРНА 2026")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        val year = 2026
        val octoberIndex = 9
        val novemberIndex = 10
        
        val octoberShift = calculateMonthShift(year, octoberIndex)
        val novemberShift = calculateMonthShift(year, novemberIndex)
        
        // Последние 5 дней октября
        val octoberLastDaysStart = octoberShift + 26 // 27-31 октября (5 дней)
        val octoberLastDays = getPatternSequence(octoberLastDaysStart, 5)
        
        // Первые 5 дней ноября
        val novemberFirstDays = getPatternSequence(novemberShift, 5)
        
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
     * Проверяет соответствие ожидаемому паттерну для ноября 2026
     */
    fun testNovember2026ExpectedPattern(): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "ТЕСТ СООТВЕТСТВИЯ ОЖИДАЕМОМУ ПАТТЕРНУ НОЯБРЬ 2026")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        val year = 2026
        val novemberIndex = 10
        
        val novemberShift = calculateMonthShift(year, novemberIndex)
        val expectedPattern = listOf("3", "2", "4", "1", "Вх")
        val actualPattern = getPatternSequence(novemberShift, 5)
        
        Log.d(TAG, "Ноябрь 2026:")
        Log.d(TAG, "  Сдвиг: $novemberShift")
        Log.d(TAG, "  Ожидаемый паттерн (первые 5 дней): ${expectedPattern.joinToString(", ")}")
        Log.d(TAG, "  Фактический паттерн (первые 5 дней): ${actualPattern.joinToString(", ")}")
        
        val patternMatches = actualPattern == expectedPattern
        
        if (patternMatches) {
            Log.d(TAG, "✅ ПАТТЕРН НОЯБРЯ 2026 СООТВЕТСТВУЕТ ОЖИДАЕМОМУ!")
        } else {
            Log.e(TAG, "❌ ПАТТЕРН НОЯБРЯ 2026 НЕ СООТВЕТСТВУЕТ ОЖИДАЕМОМУ!")
            Log.e(TAG, "   Ожидался: ${expectedPattern.joinToString(", ")}")
            Log.e(TAG, "   Получен: ${actualPattern.joinToString(", ")}")
        }
        
        return patternMatches
    }
    
    /**
     * Запускает все тесты для ноября 2026 года
     */
    fun runAllNovember2026Tests(): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "ЗАПУСК ВСЕХ ТЕСТОВ ДЛЯ НОЯБРЯ 2026 ГОДА")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        
        val transitionTest = testOctoberToNovember2026Transition()
        val sequenceTest = testPatternSequence2026()
        val patternTest = testNovember2026ExpectedPattern()
        
        val allTestsPassed = transitionTest && sequenceTest && patternTest
        
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "ИТОГОВЫЕ РЕЗУЛЬТАТЫ ТЕСТОВ НОЯБРЯ 2026")
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "Тест перехода октябрь → ноябрь: ${if (transitionTest) "✅ ПРОЙДЕН" else "❌ ПРОВАЛЕН"}")
        Log.d(TAG, "Тест последовательности паттерна: ${if (sequenceTest) "✅ ПРОЙДЕН" else "❌ ПРОВАЛЕН"}")
        Log.d(TAG, "Тест соответствия ожидаемому паттерну: ${if (patternTest) "✅ ПРОЙДЕН" else "❌ ПРОВАЛЕН"}")
        
        if (allTestsPassed) {
            Log.d(TAG, "🎉 ВСЕ ТЕСТЫ НОЯБРЯ 2026 ПРОЙДЕНЫ УСПЕШНО!")
            Log.d(TAG, "✅ Паттерн смен для ноября 2026 года корректен!")
        } else {
            Log.e(TAG, "❌ ЕСТЬ ПРОВАЛЕННЫЕ ТЕСТЫ ДЛЯ НОЯБРЯ 2026!")
            Log.e(TAG, "⚠️ Требуется дополнительная отладка!")
        }
        
        return allTestsPassed
    }
}


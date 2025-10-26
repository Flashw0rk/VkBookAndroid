package com.example.vkbookandroid.schedule

import android.util.Log

/**
 * Вспомогательный класс для тестирования новых ООП компонентов
 * Проверяет, что логика работает так же, как в оригинальном ScheduleFragment
 */
object ScheduleTestHelper {
    
    private const val TAG = "ScheduleTestHelper"
    
    /**
     * Тестирует расчет сдвигов для всех месяцев 2025 года
     * Сравнивает с эталонными значениями из рабочего кода
     */
    fun testYear2025Shifts(): Boolean {
        Log.d(TAG, "═══ ТЕСТ: Год 2025 - Расчет сдвигов ═══")
        
        val calculator = ScheduleCalculator()
        
        // Эталонные значения из рабочего кода (базовый паттерн 0-9)
        val expected2025 = mapOf(
            0 to 1,   // Январь
            1 to 2,   // Февраль
            2 to 0,   // Март
            3 to 1,   // Апрель
            4 to 1,   // Май
            5 to 2,   // Июнь
            6 to 2,   // Июль
            7 to 3,   // Август
            8 to 3,   // Сентябрь
            9 to 4,   // Октябрь
            10 to 4,  // Ноябрь
            11 to 5   // Декабрь
        )
        
        val months = arrayOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        )
        
        var allCorrect = true
        
        expected2025.forEach { (monthIndex, expectedShift) ->
            val calculatedShift = calculator.calculateMonthShift(2025, monthIndex)
            val isCorrect = calculatedShift == expectedShift
            
            if (isCorrect) {
                Log.d(TAG, "✅ ${months[monthIndex]}: $calculatedShift (ожидалось $expectedShift)")
            } else {
                Log.e(TAG, "❌ ${months[monthIndex]}: $calculatedShift (ожидалось $expectedShift)")
                allCorrect = false
            }
        }
        
        if (allCorrect) {
            Log.d(TAG, "✅ ВСЕ ТЕСТЫ 2025 ГОДА ПРОЙДЕНЫ!")
        } else {
            Log.e(TAG, "❌ ЕСТЬ ОШИБКИ В РАСЧЕТАХ 2025 ГОДА!")
        }
        
        return allCorrect
    }
    
    /**
     * Тестирует расчет сдвигов для 2026 года
     */
    fun testYear2026Shifts(): Boolean {
        Log.d(TAG, "═══ ТЕСТ: Год 2026 - Расчет сдвигов ═══")
        
        val calculator = ScheduleCalculator()
        
        // Эталонные значения (должны продолжать паттерн от 2025)
        val expected2026 = mapOf(
            0 to 6,   // Январь (после 31 дня декабря 2025 с позиции 5)
            1 to 7,   // Февраль (6 + 31 = 37 % 10 = 7)
            2 to 5,   // Март (7 + 28 = 35 % 10 = 5)
            3 to 6,   // Апрель (5 + 31 = 36 % 10 = 6)
            4 to 6,   // Май (6 + 30 = 36 % 10 = 6)
            5 to 7,   // Июнь (6 + 31 = 37 % 10 = 7)
            6 to 7,   // Июль (7 + 30 = 37 % 10 = 7)
            7 to 8,   // Август (7 + 31 = 38 % 10 = 8)
            8 to 9,   // Сентябрь (8 + 31 = 39 % 10 = 9)
            9 to 9,   // Октябрь (9 + 30 = 39 % 10 = 9)
            10 to 0,  // Ноябрь (9 + 31 = 40 % 10 = 0)
            11 to 0   // Декабрь (0 + 30 = 30 % 10 = 0)
        )
        
        val months = arrayOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        )
        
        var allCorrect = true
        
        expected2026.forEach { (monthIndex, expectedShift) ->
            val calculatedShift = calculator.calculateMonthShift(2026, monthIndex)
            val isCorrect = calculatedShift == expectedShift
            
            if (isCorrect) {
                Log.d(TAG, "✅ ${months[monthIndex]}: $calculatedShift (ожидалось $expectedShift)")
            } else {
                Log.e(TAG, "❌ ${months[monthIndex]}: $calculatedShift (ожидалось $expectedShift)")
                allCorrect = false
            }
        }
        
        if (allCorrect) {
            Log.d(TAG, "✅ ВСЕ ТЕСТЫ 2026 ГОДА ПРОЙДЕНЫ!")
        } else {
            Log.e(TAG, "❌ ЕСТЬ ОШИБКИ В РАСЧЕТАХ 2026 ГОДА!")
        }
        
        return allCorrect
    }
    
    /**
     * Тестирует генерацию календаря для года
     */
    fun testYearScheduleGeneration(year: Int): Boolean {
        Log.d(TAG, "═══ ТЕСТ: Генерация календаря $year года ═══")
        
        val calculator = ScheduleCalculator()
        val patternProvider = ShiftPatternProvider()
        val builder = YearScheduleBuilder(calculator, patternProvider)
        
        val schedule = builder.buildYearSchedule(year)
        
        // Проверка 1: Должно быть 12 месяцев + 5 смен = 17 строк
        val expectedRows = 12 + ShiftPatternProvider.SHIFT_COUNT
        if (schedule.size != expectedRows) {
            Log.e(TAG, "❌ Неверное количество строк: ${schedule.size} (ожидалось $expectedRows)")
            return false
        }
        Log.d(TAG, "✅ Количество строк: ${schedule.size}")
        
        // Проверка 2: Первые 12 строк - месяцы
        val monthRows = schedule.filter { it.isMonthRow }
        if (monthRows.size != 12) {
            Log.e(TAG, "❌ Неверное количество месяцев: ${monthRows.size} (ожидалось 12)")
            return false
        }
        Log.d(TAG, "✅ Количество месяцев: ${monthRows.size}")
        
        // Проверка 3: Каждая строка имеет 36 элементов
        var allHave36 = true
        schedule.forEach { row ->
            if (row.days.size != 36) {
                Log.e(TAG, "❌ ${row.monthName}: ${row.days.size} элементов (ожидалось 36)")
                allHave36 = false
            }
        }
        if (allHave36) {
            Log.d(TAG, "✅ Все строки имеют 36 элементов")
        }
        
        // Проверка 4: Правильное количество дней в каждом месяце
        var allDaysCorrect = true
        monthRows.forEachIndexed { index, row ->
            val actualDays = row.days.count { it.isNotEmpty() }
            val expectedDays = calculator.getDaysInMonth(year, index)
            
            if (actualDays != expectedDays) {
                Log.e(TAG, "❌ ${row.monthName}: $actualDays дней (ожидалось $expectedDays)")
                allDaysCorrect = false
            }
        }
        if (allDaysCorrect) {
            Log.d(TAG, "✅ Все месяцы имеют правильное количество дней")
        }
        
        // Проверка 5: Последние 5 строк - смены
        val shiftRows = schedule.filter { !it.isMonthRow }
        if (shiftRows.size != 5) {
            Log.e(TAG, "❌ Неверное количество смен: ${shiftRows.size} (ожидалось 5)")
            return false
        }
        Log.d(TAG, "✅ Количество смен: ${shiftRows.size}")
        
        val allTestsPassed = allHave36 && allDaysCorrect
        
        if (allTestsPassed) {
            Log.d(TAG, "✅ ВСЕ ТЕСТЫ ГЕНЕРАЦИИ $year ГОДА ПРОЙДЕНЫ!")
        } else {
            Log.e(TAG, "❌ ЕСТЬ ОШИБКИ В ГЕНЕРАЦИИ $year ГОДА!")
        }
        
        return allTestsPassed
    }
    
    /**
     * Тестирует сдвиг графика смен
     */
    fun testShiftPatternOffset(): Boolean {
        Log.d(TAG, "═══ ТЕСТ: Сдвиг графика смен ═══")
        
        val patternProvider = ShiftPatternProvider()
        
        // Тест 1: Без сдвига
        val pattern0 = patternProvider.getShiftPattern(0, 0)
        if (pattern0[0] != "3" || pattern0[1] != "2") {
            Log.e(TAG, "❌ Паттерн без сдвига неверный: ${pattern0[0]}, ${pattern0[1]}")
            return false
        }
        Log.d(TAG, "✅ Паттерн без сдвига: ${pattern0.take(5).joinToString()}")
        
        // Тест 2: Со сдвигом на 6
        val pattern6 = patternProvider.getShiftPattern(0, 6)
        // После сдвига на 6 позиций вправо, начало должно быть другим
        if (pattern6[0] == pattern0[0]) {
            Log.e(TAG, "❌ Паттерн со сдвигом 6 не изменился")
            return false
        }
        Log.d(TAG, "✅ Паттерн со сдвигом 6: ${pattern6.take(5).joinToString()}")
        
        // Тест 3: Размер паттерна всегда 36
        if (pattern0.size != 36 || pattern6.size != 36) {
            Log.e(TAG, "❌ Размер паттерна неверный")
            return false
        }
        Log.d(TAG, "✅ Размер паттернов: 36")
        
        Log.d(TAG, "✅ ВСЕ ТЕСТЫ СДВИГА ГРАФИКА ПРОЙДЕНЫ!")
        return true
    }
    
    /**
     * Тестирует правильность расположения месяцев относительно графика смен
     * Проверяет, что каждый месяц начинается с правильной части паттерна
     */
    fun testMonthPatternAlignment(year: Int): Boolean {
        Log.d(TAG, "═══ ТЕСТ: Соответствие месяцев графику смен ($year год) ═══")
        
        val calculator = ScheduleCalculator()
        val patternProvider = ShiftPatternProvider()
        val builder = YearScheduleBuilder(calculator, patternProvider)
        
        val schedule = builder.buildYearSchedule(year)
        
        // Базовый паттерн для проверки
        val basePattern = arrayOf("3", "2", "4", "1", "Вх", "4", "1", "3", "2", "Вх")
        
        val monthRows = schedule.filter { it.isMonthRow }
        val shiftRows = schedule.filter { !it.isMonthRow }
        
        if (shiftRows.isEmpty()) {
            Log.e(TAG, "❌ Не найдены строки смен!")
            return false
        }
        
        // Берем первую смену для проверки
        val shift1Pattern = shiftRows[0].days
        
        var allCorrect = true
        val months = arrayOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        )
        
        monthRows.forEachIndexed { monthIndex, monthRow ->
            // Находим первый непустой элемент (число 1)
            val firstDayPosition = monthRow.days.indexOfFirst { it.isNotEmpty() }
            
            if (firstDayPosition == -1) {
                Log.e(TAG, "❌ ${monthRow.monthName}: не найдено начало месяца!")
                allCorrect = false
                return@forEachIndexed
            }
            
            // Получаем значение из графика смены 1 на этой позиции
            val shiftValue = shift1Pattern.getOrNull(firstDayPosition)
            
            // Вычисляем ожидаемое значение
            val calculatedShift = calculator.calculateMonthShift(year, monthIndex)
            val expectedValue = basePattern[calculatedShift]
            
            if (shiftValue == expectedValue) {
                Log.d(TAG, "✅ ${months[monthIndex]}: позиция $firstDayPosition → \"$shiftValue\" (ожидалось \"$expectedValue\")")
            } else {
                Log.e(TAG, "❌ ${months[monthIndex]}: позиция $firstDayPosition → \"$shiftValue\" (ожидалось \"$expectedValue\")")
                allCorrect = false
            }
        }
        
        if (allCorrect) {
            Log.d(TAG, "✅ ВСЕ МЕСЯЦЫ $year ГОДА ПРАВИЛЬНО СООТВЕТСТВУЮТ ГРАФИКУ!")
        } else {
            Log.e(TAG, "❌ ОБНАРУЖЕНЫ НЕСООТВЕТСТВИЯ В $year ГОДУ!")
        }
        
        return allCorrect
    }
    
    /**
     * Проверяет наличие пустых столбцов в календаре
     */
    fun testEmptyColumns(year: Int): Boolean {
        Log.d(TAG, "═══ ТЕСТ: Проверка пустых столбцов ($year год) ═══")
        
        val calculator = ScheduleCalculator()
        val patternProvider = ShiftPatternProvider()
        val builder = YearScheduleBuilder(calculator, patternProvider)
        
        val schedule = builder.buildYearSchedule(year)
        val monthRows = schedule.filter { it.isMonthRow }
        
        // Проверяем каждый столбец (0-35)
        val emptyColumns = mutableListOf<Int>()
        
        for (column in 0 until 36) {
            var hasAnyDay = false
            
            // Проверяем, есть ли хотя бы в одном месяце день в этом столбце
            for (monthRow in monthRows) {
                val cellValue = monthRow.days.getOrNull(column)
                if (!cellValue.isNullOrEmpty()) {
                    hasAnyDay = true
                    break
                }
            }
            
            if (!hasAnyDay) {
                emptyColumns.add(column)
            }
        }
        
        if (emptyColumns.isEmpty()) {
            Log.d(TAG, "✅ В календаре $year года НЕТ пустых столбцов!")
            return true
        } else {
            Log.w(TAG, "⚠️ В календаре $year года найдены ПУСТЫЕ столбцы: $emptyColumns")
            Log.w(TAG, "   Всего пустых: ${emptyColumns.size} из 36")
            
            // Проверяем, есть ли пустые столбцы СЛЕВА (в начале)
            val leftEmptyColumns = emptyColumns.takeWhile { it == emptyColumns.indexOf(it) }
            if (leftEmptyColumns.isNotEmpty()) {
                Log.w(TAG, "   🚨 ПУСТЫЕ СТОЛБЦЫ СЛЕВА: ${leftEmptyColumns.size} штук!")
            }
            
            return false
        }
    }
    
    /**
     * Запускает все тесты
     */
    fun runAllTests(): Boolean {
        Log.d(TAG, "╔═══════════════════════════════════════╗")
        Log.d(TAG, "║  ЗАПУСК ВСЕХ ТЕСТОВ ООП КОМПОНЕНТОВ  ║")
        Log.d(TAG, "╚═══════════════════════════════════════╝")
        
        val test1 = testYear2025Shifts()
        val test2 = testYear2026Shifts()
        val test3 = testYearScheduleGeneration(2025)
        val test4 = testYearScheduleGeneration(2026)
        val test5 = testShiftPatternOffset()
        val test6 = testMonthPatternAlignment(2025)
        val test7 = testMonthPatternAlignment(2026)
        val test8 = testEmptyColumns(2025)
        val test9 = testEmptyColumns(2026)
        
        // Расширенное тестирование 2026-2030
        Log.d(TAG, "")
        Log.d(TAG, "═══ РАСШИРЕННОЕ ТЕСТИРОВАНИЕ 2026-2030 ═══")
        val extendedTests = mutableListOf<Boolean>()
        for (year in 2026..2030) {
            val testGen = testYearScheduleGeneration(year)
            val testAlign = testMonthPatternAlignment(year)
            val testEmpty = testEmptyColumns(year)
            extendedTests.add(testGen && testAlign && testEmpty)
            
            if (testGen && testAlign && testEmpty) {
                Log.d(TAG, "✅ $year год: ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ")
            } else {
                Log.e(TAG, "❌ $year год: ЕСТЬ ПРОБЛЕМЫ (gen=$testGen, align=$testAlign, empty=$testEmpty)")
            }
        }
        
        val allPassed = test1 && test2 && test3 && test4 && test5 && test6 && test7 && test8 && test9 && extendedTests.all { it }
        
        Log.d(TAG, "")
        Log.d(TAG, "╔═══════════════════════════════════════╗")
        if (allPassed) {
            Log.d(TAG, "║  ✅ ВСЕ ТЕСТЫ УСПЕШНО ПРОЙДЕНЫ! ✅  ║")
        } else {
            Log.e(TAG, "║  ❌ НЕКОТОРЫЕ ТЕСТЫ НЕ ПРОЙДЕНЫ! ❌  ║")
        }
        Log.d(TAG, "╚═══════════════════════════════════════╝")
        
        return allPassed
    }
}



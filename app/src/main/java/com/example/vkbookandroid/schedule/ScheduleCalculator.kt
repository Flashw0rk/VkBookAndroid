package com.example.vkbookandroid.schedule

import android.util.Log

/**
 * Класс для расчета позиций месяцев в графике смен
 * Инкапсулирует логику вычисления сдвигов и кэширование
 */
class ScheduleCalculator {
    
    // Базовый паттерн смен (10 элементов)
    private val basePattern = arrayOf("3", "2", "4", "1", "Вх", "4", "1", "3", "2", "Вх")
    private val basePatternSize = basePattern.size
    
    // Кэш предрасчитанных сдвигов месяцев: Map<"Year-Month", Shift>
    private val monthShiftCache = mutableMapOf<String, Int>()
    
    companion object {
        private const val TAG = "ScheduleCalculator"
    }
    
    /**
     * Вычисляет сдвиг для месяца с кэшированием
     * @param year Год
     * @param monthIndex Индекс месяца (0-11)
     * @return Позиция начала месяца в базовом паттерне (0-9)
     */
    fun calculateMonthShift(year: Int, monthIndex: Int): Int {
        val cacheKey = "$year-$monthIndex"
        
        // Проверяем кэш
        monthShiftCache[cacheKey]?.let {
            return it
        }
        
        // Вычисляем и сохраняем в кэш
        val result = calculateMonthShiftInternal(year, monthIndex)
        monthShiftCache[cacheKey] = result
        
        Log.d(TAG, "Кэш: $cacheKey → $result")
        return result
    }
    
    /**
     * Внутренняя логика расчета сдвига месяца
     */
    private fun calculateMonthShiftInternal(year: Int, monthIndex: Int): Int {
        // Базовый случай: январь 2025 = позиция 1 ("2")
        if (year == 2025 && monthIndex == 0) {
            return 1
        }
        
        // Рекурсия для прошлых годов (расчет назад от следующего года)
        if (monthIndex == 0 && year < 2025) {
            val nextYear = year + 1
            val januaryNextYearShift = calculateMonthShift(nextYear, 0)
            val daysInYear = if (isLeapYear(year)) 366 else 365
            val stepsBack = daysInYear % basePatternSize
            return (januaryNextYearShift - stepsBack + basePatternSize * 100) % basePatternSize
        }
        
        // Рекурсия для будущих годов (расчет вперед от декабря предыдущего года)
        if (monthIndex == 0 && year > 2025) {
            val prevYear = year - 1
            val decemberShift = calculateMonthShift(prevYear, 11)
            val daysInDecember = 31
            return (decemberShift + daysInDecember) % basePatternSize
        }
        
        // Расчет для месяцев внутри года (последовательно от января)
        val januaryShift = calculateMonthShift(year, 0)
        var currentShift = januaryShift
        
        for (m in 0 until monthIndex) {
            val daysInMonth = getDaysInMonth(year, m)
            currentShift = (currentShift + daysInMonth) % basePatternSize
        }
        
        return currentShift
    }
    
    /**
     * Вычисляет глобальный номер дня от 1 января 2025
     * @param year Год
     * @param monthIndex Индекс месяца (0-11)
     * @param day День месяца (1-31)
     * @return Глобальный номер дня (1 = 1 января 2025)
     */
    fun getGlobalDayNumberFrom2025(year: Int, monthIndex: Int, day: Int): Int {
        val baseYear = 2025
        var totalDays = 0
        
        if (year >= baseYear) {
            // Считаем вперед
            for (y in baseYear until year) {
                totalDays += if (isLeapYear(y)) 366 else 365
            }
            
            // Добавляем дни текущего года до нужного месяца
            for (m in 0 until monthIndex) {
                totalDays += getDaysInMonth(year, m)
            }
            
            totalDays += day
        } else {
            // Считаем назад
            for (y in year until baseYear) {
                totalDays -= if (isLeapYear(y)) 366 else 365
            }
            
            // Добавляем дни текущего года до нужного месяца
            for (m in 0 until monthIndex) {
                totalDays += getDaysInMonth(year, m)
            }
            
            totalDays += day
        }
        
        return totalDays
    }
    
    /**
     * Проверяет, является ли год високосным
     */
    fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }
    
    /**
     * Возвращает количество дней в месяце
     * @param year Год
     * @param monthIndex Индекс месяца (0-11)
     */
    fun getDaysInMonth(year: Int, monthIndex: Int): Int {
        return when (monthIndex) {
            0 -> 31  // Январь
            1 -> if (isLeapYear(year)) 29 else 28  // Февраль
            2 -> 31  // Март
            3 -> 30  // Апрель
            4 -> 31  // Май
            5 -> 30  // Июнь
            6 -> 31  // Июль
            7 -> 31  // Август
            8 -> 30  // Сентябрь
            9 -> 31  // Октябрь
            10 -> 30  // Ноябрь
            11 -> 31  // Декабрь
            else -> throw IllegalArgumentException("Invalid month index: $monthIndex")
        }
    }
    
    /**
     * Очищает кэш (для освобождения памяти)
     */
    fun clearCache() {
        monthShiftCache.clear()
        Log.d(TAG, "Кэш очищен")
    }
    
    /**
     * Возвращает размер кэша
     */
    fun getCacheSize(): Int = monthShiftCache.size
}





package com.example.vkbookandroid.schedule

import android.util.Log
import com.example.vkbookandroid.schedule.cache.ShiftCalculationCache
import java.util.*

/**
 * Класс для расчета сдвигов месяцев в расписании смен
 * Изолирует бизнес-логику от UI
 */
class ShiftCalculator(
    private val cache: ShiftCalculationCache = ShiftCalculationCache()
) {
    
    companion object {
        private const val TAG = "ShiftCalculator"
    }
    
    /**
     * Вычислить сдвиг месяца для заданного года
     */
    fun calculateMonthShift(year: Int, monthIndex: Int): Int {
        return cache.getShift(year, monthIndex) { y, m ->
            calculateMonthShiftInternal(y, m)
        }
    }
    
    /**
     * Внутренний метод расчета без кэширования
     */
    private fun calculateMonthShiftInternal(year: Int, monthIndex: Int): Int {
        // Вычисляем глобальный номер дня от 1 января 2025 года
        val globalDayNumber = getGlobalDayNumberFrom2025(year, monthIndex, 1)
        
        // Используем тот же алгоритм что и в ScheduleFragment
        return globalDayNumber % 37
    }
    
    /**
     * Получить скорректированный сдвиг для отображения
     */
    fun getAdjustedShiftForDisplay(year: Int, monthIndex: Int, daysInMonth: Int): Int {
        val calculatedShift = calculateMonthShift(year, monthIndex)
        val patternValue = calculatedShift % ScheduleConstants.PATTERN_SIZE
        
        val maxSafePosition = ScheduleConstants.getMaxSafePosition(daysInMonth)
        
        // Проверяем patternValue напрямую
        if (patternValue + daysInMonth <= ScheduleConstants.CALENDAR_WIDTH) {
            return patternValue
        }
        
        // Патерн НЕ помещается - ищем позицию в предыдущих циклах
        var adjustedPosition = patternValue
        while (adjustedPosition > maxSafePosition) {
            adjustedPosition -= ScheduleConstants.PATTERN_SIZE
            if (adjustedPosition < 0) {
                val fallback = if (daysInMonth <= ScheduleConstants.CALENDAR_WIDTH) {
                    0
                } else {
                    maxSafePosition.coerceAtLeast(0)
                }
                Log.w(TAG, "Год $year, месяц $monthIndex: НЕ НАШЛИ позицию! patternValue=$patternValue, используем fallback=$fallback")
                return fallback
            }
        }
        
        return adjustedPosition
    }
    
    /**
     * Вычислить глобальный номер дня от 1 января 2025 года
     */
    private fun getGlobalDayNumberFrom2025(year: Int, monthIndex: Int, day: Int): Int {
        var totalDays = 0
        
        // Добавляем дни за полные годы
        for (y in ScheduleConstants.BASE_YEAR until year) {
            totalDays += if (ScheduleConstants.isLeapYear(y)) 366 else 365
        }
        
        // Добавляем дни за месяцы текущего года
        for (m in 0 until monthIndex) {
            totalDays += ScheduleConstants.getDaysInMonth(year, m)
        }
        
        // Добавляем дни текущего месяца
        totalDays += day - 1
        
        return totalDays
    }
    
    /**
     * Получить статистику кэша
     */
    fun getCacheStats(): ShiftCalculationCache.CacheStats {
        return cache.getStats()
    }
    
    /**
     * Очистить кэш
     */
    fun clearCache() {
        cache.clear()
    }
}


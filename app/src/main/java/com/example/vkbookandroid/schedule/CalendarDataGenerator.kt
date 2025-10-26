package com.example.vkbookandroid.schedule

import android.util.Log
import com.example.vkbookandroid.ScheduleFragment

/**
 * Класс для генерации данных календаря смен
 * Отвечает за создание данных для отображения в UI
 */
class CalendarDataGenerator(
    private val shiftCalculator: ShiftCalculator
) {
    
    companion object {
        private const val TAG = "CalendarDataGenerator"
    }
    
    /**
     * Сгенерировать данные календаря для года
     */
    fun generateYearData(year: Int): List<ScheduleFragment.ScheduleRow> {
        val scheduleData = mutableListOf<ScheduleFragment.ScheduleRow>()
        
        val months = ScheduleConstants.MONTH_NAMES
        val daysInMonths = Array(12) { ScheduleConstants.getDaysInMonth(year, it) }
        
        // Вычисляем оптимальный сдвиг для всего года
        val yearShiftOffset = findOptimalYearShift(year, daysInMonths)
        
        Log.d(TAG, "═══ Год $year - Расчет календаря ═══")
        Log.d(TAG, "Оптимальный сдвиг года (yearShiftOffset): $yearShiftOffset")
        
        // Генерируем строки месяцев
        months.forEachIndexed { monthIndex, monthName ->
            val days = (1..daysInMonths[monthIndex]).toList()
            
            val daysFromJanuary = (0 until monthIndex).sumOf { daysInMonths[it] }
            val calculatedShift = shiftCalculator.calculateMonthShift(year, monthIndex)
            val adjustedShift = calculatedShift - yearShiftOffset
            
            Log.d(TAG, "Месяц $monthName: calculated=$calculatedShift, adjusted=$adjustedShift (сдвиг -$yearShiftOffset)")
            
            // Получаем безопасный сдвиг
            val safeAdjustedShift = getSafeAdjustedShift(
                year, monthIndex, adjustedShift, 
                calculatedShift, yearShiftOffset, daysInMonths[monthIndex]
            )
            
            scheduleData.add(
                ScheduleFragment.ScheduleRow(
                    name = monthName,
                    days = days,
                    isMonthRow = true,
                    monthIndex = monthIndex,
                    year = year
                )
            )
        }
        
        return scheduleData
    }
    
    /**
     * Найти оптимальный сдвиг для года
     */
    private fun findOptimalYearShift(year: Int, daysInMonths: Array<Int>): Int {
        var bestOffset = 0
        var minEmptyColumns = Int.MAX_VALUE
        
        // Проверяем все возможные сдвиги от 0 до 9
        for (offset in 0 until ScheduleConstants.PATTERN_SIZE) {
            var emptyColumns = 0
            
            daysInMonths.forEachIndexed { monthIndex, daysInMonth ->
                val calculatedShift = shiftCalculator.calculateMonthShift(year, monthIndex)
                val adjustedShift = calculatedShift - offset
                val patternPosition = calculatedShift % ScheduleConstants.PATTERN_SIZE
                
                // Проверяем, помещается ли месяц в безопасную позицию
                val maxSafePosition = ScheduleConstants.getMaxSafePosition(daysInMonth)
                if (adjustedShift < 0 || adjustedShift + daysInMonth > ScheduleConstants.CALENDAR_WIDTH) {
                    emptyColumns++
                }
            }
            
            if (emptyColumns < minEmptyColumns) {
                minEmptyColumns = emptyColumns
                bestOffset = offset
            }
        }
        
        return bestOffset
    }
    
    /**
     * Получить безопасный сдвиг для месяца
     */
    private fun getSafeAdjustedShift(
        year: Int,
        monthIndex: Int,
        adjustedShift: Int,
        calculatedShift: Int,
        yearShiftOffset: Int,
        daysInMonth: Int
    ): Int {
        if (adjustedShift >= 0 && adjustedShift + daysInMonth <= ScheduleConstants.CALENDAR_WIDTH) {
            return adjustedShift
        }
        
        // Ищем подходящую позицию
        val patternPosition = calculatedShift % ScheduleConstants.PATTERN_SIZE
        val validPositions = mutableListOf<Int>()
        
        for (pos in 0 until ScheduleConstants.CALENDAR_WIDTH) {
            val indexAtPos = (yearShiftOffset + pos) % ScheduleConstants.PATTERN_SIZE
            if (indexAtPos == patternPosition && pos + daysInMonth <= ScheduleConstants.CALENDAR_WIDTH) {
                validPositions.add(pos)
            }
        }
        
        if (validPositions.isNotEmpty()) {
            return validPositions.minOrNull() ?: 0
        }
        
        // Fallback
        return ScheduleConstants.getMaxSafePosition(daysInMonth).coerceAtLeast(0)
    }
}


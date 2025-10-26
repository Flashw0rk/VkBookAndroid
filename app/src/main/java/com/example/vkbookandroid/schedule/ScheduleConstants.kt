package com.example.vkbookandroid.schedule

/**
 * Константы для модуля расписания смен
 * Централизует все магические числа из ScheduleFragment
 */
object ScheduleConstants {
    // Размеры паттернов
    const val PATTERN_SIZE = 10              // Базовый паттерн смен (10 элементов)
    const val CALENDAR_WIDTH = 36           // Ширина календаря в ячейках
    const val SHIFTS_COUNT = 5              // Количество смен
    
    // Базовый год для расчетов
    const val BASE_YEAR = 2025
    
    // Максимальные безопасные позиции для месяцев разной длины
    const val MAX_SAFE_POSITION_FOR_31_DAYS = CALENDAR_WIDTH - 31  // 5
    const val MAX_SAFE_POSITION_FOR_30_DAYS = CALENDAR_WIDTH - 30  // 6
    const val MAX_SAFE_POSITION_FOR_29_DAYS = CALENDAR_WIDTH - 29  // 7
    const val MAX_SAFE_POSITION_FOR_28_DAYS = CALENDAR_WIDTH - 28  // 8
    
    // Названия месяцев
    val MONTH_NAMES = arrayOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    )
    
    // Дни в месяцах (не високосный год)
    val DAYS_IN_MONTHS = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    
    // Названия смен
    val SHIFT_NAMES = arrayOf("Смена 1", "Смена 2", "Смена 3", "Смена 4", "Смена 5")
    
    /**
     * Получить количество дней в месяце с учетом високосного года
     */
    fun getDaysInMonth(year: Int, monthIndex: Int): Int {
        if (monthIndex == 1 && isLeapYear(year)) {
            return 29
        }
        return DAYS_IN_MONTHS[monthIndex]
    }
    
    /**
     * Проверка на високосный год
     */
    fun isLeapYear(year: Int): Boolean {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    }
    
    /**
     * Получить максимальную безопасную позицию для месяца
     */
    fun getMaxSafePosition(daysInMonth: Int): Int {
        return CALENDAR_WIDTH - daysInMonth
    }
}


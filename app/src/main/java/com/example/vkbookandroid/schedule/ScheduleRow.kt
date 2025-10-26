package com.example.vkbookandroid.schedule

/**
 * Данные одной строки графика (месяц или смена)
 */
data class ScheduleRow(
    val monthName: String,                  // Название месяца или смены
    val days: Array<String>,                 // Массив дней (36 элементов)
    val isMonthRow: Boolean = false,         // true = месяц, false = смена
    val monthIndex: Int = -1,                // Индекс месяца (0-11) для месячных строк
    val shiftIndex: Int = -1,                // Индекс смены (0-4) для строк смен
    val year: Int = 2025                     // Год (для месячных строк)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScheduleRow

        if (monthName != other.monthName) return false
        if (!days.contentEquals(other.days)) return false
        if (isMonthRow != other.isMonthRow) return false
        if (monthIndex != other.monthIndex) return false
        if (shiftIndex != other.shiftIndex) return false
        if (year != other.year) return false

        return true
    }

    override fun hashCode(): Int {
        var result = monthName.hashCode()
        result = 31 * result + days.contentHashCode()
        result = 31 * result + isMonthRow.hashCode()
        result = 31 * result + monthIndex
        result = 31 * result + shiftIndex
        result = 31 * result + year
        return result
    }
}





package com.example.vkbookandroid

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Упрощенная модель правила напоминания - просто набор часов и дней
 * + Продвинутые настройки для сложных правил
 */
data class ReminderRule(
    val selectedHours: Set<Int> = emptySet(),      // Конкретные часы: 0, 2, 4, 8, 12...
    val selectedDaysOfWeek: Set<DayOfWeek> = emptySet(), // Конкретные дни: ПН, ВТ, СР...
    // Продвинутые настройки
    val advancedType: String = "NONE",             // NONE, WEEK_DAY, DAYS_OF_MONTH
    val weekOfMonth: Int? = null,                  // 1-я, 2-я, 3-я, 4-я неделя
    val dayOfWeekInMonth: DayOfWeek? = null,       // День недели для N-й недели
    val daysOfMonth: Set<Int> = emptySet()         // Числа месяца: 1, 15, 31...
) {
    /**
     * Компактное текстовое представление для таблицы
     */
    fun toCompactString(): String {
        // Продвинутые настройки
        
        // Еженедельное: каждый понедельник
        if (advancedType == "WEEKLY" && dayOfWeekInMonth != null) {
            val dayText = dayOfWeekInMonth.toAccusativeWithPrefix()
            val hoursText = formatHoursReadable(selectedHours)
            return "$dayText$hoursText"
        }
        
        // Ежемесячное по числам
        if (advancedType == "MONTHLY_BY_DATE" && daysOfMonth.isNotEmpty()) {
            val datesText = if (daysOfMonth.size == 1) {
                "Каждое ${daysOfMonth.first()} число"
            } else {
                // Показываем ВСЕ числа месяца
                "Числа: ${daysOfMonth.sorted().joinToString(", ")}"
            }
            val hoursText = formatHoursReadable(selectedHours)
            return "$datesText$hoursText"
        }
        
        // Ежемесячное по дням недели
        if (advancedType == "MONTHLY_BY_WEEKDAY" && weekOfMonth != null && dayOfWeekInMonth != null) {
            val weekText = when (weekOfMonth) {
                1 -> "1-й"
                2 -> "2-й"
                3 -> "3-й"
                4 -> "4-й"
                else -> "${weekOfMonth}-й"
            }
            val dayText = dayOfWeekInMonth.toFullString()
            val hoursText = formatHoursReadable(selectedHours)
            return "Каждый $weekText $dayText$hoursText"
        }
        
        // Простой режим
        if (selectedHours.isEmpty() || selectedDaysOfWeek.isEmpty()) {
            return "Не настроено"
        }
        
        // Форматируем дни
        val daysText = when {
            selectedDaysOfWeek.size == 7 -> "Каждый день"
            selectedDaysOfWeek == setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) -> "По будням (ПН-ПТ)"
            selectedDaysOfWeek == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "По выходным (СБ-ВС)"
            selectedDaysOfWeek.size == 1 -> selectedDaysOfWeek.first().toAccusativeWithPrefix()
            selectedDaysOfWeek.size <= 3 -> {
                val days = selectedDaysOfWeek.sortedBy { it.value }.joinToString(", ") { it.toShortString() }
                "По: $days"
            }
            else -> "По ${selectedDaysOfWeek.size} дням"
        }
        
        val hoursText = formatHoursReadable(selectedHours)
        return "$daysText$hoursText"
    }
    
    /**
     * Полное текстовое описание
     */
    fun toFullString(): String {
        // Продвинутые настройки
        
        // Еженедельное
        if (advancedType == "WEEKLY" && dayOfWeekInMonth != null) {
            val hours = if (selectedHours.isEmpty()) "в любое время" else "в часы: ${selectedHours.sorted().joinToString(", ") { String.format(Locale.getDefault(), "%02d:00", it) }}"
            return "Каждый ${dayOfWeekInMonth.toFullString()}\n$hours"
        }
        
        // Ежемесячное по числам
        if (advancedType == "MONTHLY_BY_DATE" && daysOfMonth.isNotEmpty()) {
            val dates = daysOfMonth.sorted().joinToString(", ") { "$it число" }
            val hours = if (selectedHours.isEmpty()) "в любое время" else "в часы: ${selectedHours.sorted().joinToString(", ") { String.format(Locale.getDefault(), "%02d:00", it) }}"
            return "Каждый месяц: $dates\n$hours"
        }
        
        // Ежемесячное по дням недели
        if (advancedType == "MONTHLY_BY_WEEKDAY" && weekOfMonth != null && dayOfWeekInMonth != null) {
            val weekText = when (weekOfMonth) {
                1 -> "Первый"
                2 -> "Второй"
                3 -> "Третий"
                4 -> "Четвёртый"
                else -> "$weekOfMonth-й"
            }
            val hours = if (selectedHours.isEmpty()) "в любое время" else "в часы: ${selectedHours.sorted().joinToString(", ") { String.format(Locale.getDefault(), "%02d:00", it) }}"
            return "$weekText ${dayOfWeekInMonth.toFullString()} месяца\n$hours"
        }
        
        // Простой режим
        if (selectedHours.isEmpty() || selectedDaysOfWeek.isEmpty()) {
            return "Напоминание не настроено"
        }
        
        val hours = selectedHours.sorted().joinToString(", ") { String.format(Locale.getDefault(), "%02d:00", it) }
        val days = selectedDaysOfWeek.sortedBy { it.value }.joinToString(", ") { it.toFullString() }
        
        return "Напоминание в часы: $hours\nДни: $days"
    }
    
    /**
     * Проверяет, соответствует ли данное время правилу
     */
    fun matches(dateTime: LocalDateTime): Boolean {
        // Проверка продвинутых настроек
        
        // Еженедельное: каждый понедельник и т.д.
        if (advancedType == "WEEKLY" && dayOfWeekInMonth != null) {
            // Проверяем часы
            if (selectedHours.isNotEmpty() && !selectedHours.contains(dateTime.hour)) {
                return false
            }
            // Проверяем день недели
            return dateTime.dayOfWeek == dayOfWeekInMonth
        }
        
        // Ежемесячное по числам: каждое 1-е, 15-е число
        if (advancedType == "MONTHLY_BY_DATE" && daysOfMonth.isNotEmpty()) {
            // Проверяем часы
            if (selectedHours.isNotEmpty() && !selectedHours.contains(dateTime.hour)) {
                return false
            }
            // Проверяем число месяца
            return daysOfMonth.contains(dateTime.dayOfMonth)
        }
        
        // Ежемесячное по дням: 1-й понедельник и т.д.
        if (advancedType == "MONTHLY_BY_WEEKDAY" && weekOfMonth != null && dayOfWeekInMonth != null) {
            // Проверяем часы
            if (selectedHours.isNotEmpty() && !selectedHours.contains(dateTime.hour)) {
                return false
            }
            
            // Проверяем день недели
            if (dateTime.dayOfWeek != dayOfWeekInMonth) {
                return false
            }
            
            // ИСПРАВЛЕНИЕ: Правильно определяем N-й день недели в месяце
            // независимо от того, с какого дня недели начинается месяц
            val date = dateTime.toLocalDate()
            val firstDayOfMonth = date.withDayOfMonth(1)
            val yearMonth = java.time.YearMonth.from(date)
            val daysInMonth = yearMonth.lengthOfMonth()
            
            // Находим все вхождения нужного дня недели в месяце
            val occurrences = mutableListOf<java.time.LocalDate>()
            for (day in 1..daysInMonth) {
                val dayDate = firstDayOfMonth.withDayOfMonth(day)
                if (dayDate.dayOfWeek == dayOfWeekInMonth) {
                    occurrences.add(dayDate)
                }
            }
            
            // Проверяем что запрошенный номер недели существует в месяце
            if (weekOfMonth < 1 || weekOfMonth > occurrences.size) {
                return false
            }
            
            // N-й нужный день недели (индекс weekOfMonth - 1, т.к. список начинается с 0)
            val nthTargetDay = occurrences[weekOfMonth - 1]
            
            // Проверяем что текущая дата совпадает с N-м днем недели
            return date == nthTargetDay
        }
        
        // Простой режим
        val hourMatch = selectedHours.contains(dateTime.hour)
        val dayMatch = selectedDaysOfWeek.contains(dateTime.dayOfWeek)
        return hourMatch && dayMatch
    }
    
    /**
     * Сериализация для сохранения
     */
    fun serialize(): String {
        val hours = selectedHours.sorted().joinToString(",")
        val days = selectedDaysOfWeek.sortedBy { it.value }.joinToString(",") { it.value.toString() }
        val advType = advancedType
        val weekNum = weekOfMonth?.toString() ?: ""
        val dayInMonth = dayOfWeekInMonth?.value?.toString() ?: ""
        val monthDays = daysOfMonth.sorted().joinToString(",")
        return "hours=$hours|days=$days|advType=$advType|weekNum=$weekNum|dayInMonth=$dayInMonth|monthDays=$monthDays"
    }
    
    /**
     * Форматирует часы в читабельном виде
     */
    private fun formatHoursReadable(hours: Set<Int>): String {
        if (hours.isEmpty()) return ""
        if (hours.size == 24) return " (круглосуточно)"
        
        val sorted = hours.sorted()
        
        // Ищем последовательные диапазоны
        val ranges = mutableListOf<Pair<Int, Int>>()
        var start = sorted[0]
        var end = sorted[0]
        
        for (i in 1 until sorted.size) {
            if (sorted[i] == end + 1) {
                end = sorted[i]
            } else {
                ranges.add(start to end)
                start = sorted[i]
                end = sorted[i]
            }
        }
        ranges.add(start to end)
        
        // Форматируем результат
        val formatted = ranges.map { (s, e) ->
            if (e - s >= 2) {
                // Диапазон из 3+ часов подряд
                "с ${String.format(Locale.getDefault(), "%02d:00", s)} до ${String.format(Locale.getDefault(), "%02d:00", e)}"
            } else if (s == e) {
                // Один час
                String.format(Locale.getDefault(), "%02d:00", s)
            } else {
                // Два часа подряд
                "${String.format(Locale.getDefault(), "%02d:00", s)}, ${String.format(Locale.getDefault(), "%02d:00", e)}"
            }
        }
        
        return when {
            formatted.size == 1 && ranges[0].first == ranges[0].second -> " в ${formatted[0]}"
            formatted.size == 1 -> " ${formatted[0]}"
            formatted.size <= 3 -> " в ${formatted.joinToString(" и ")}"
            else -> " в ${formatted.joinToString(", ")}" // Показываем ВСЕ часы
        }
    }
    
    companion object {
        /**
         * Десериализация из строки
         */
        fun deserialize(serialized: String): ReminderRule {
            if (serialized.isBlank()) return ReminderRule()
            
            return try {
                val parts = serialized.split("|").associate { part ->
                    val (key, value) = part.split("=", limit = 2)
                    key to value
                }
                
                val hours = parts["hours"]?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.filter { it in 0..23 }
                    ?.toSet() ?: emptySet()
                
                val days = parts["days"]?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.mapNotNull { value -> DayOfWeek.values().find { it.value == value } }
                    ?.toSet() ?: emptySet()
                
                val advType = parts["advType"] ?: "NONE"
                val weekNum = parts["weekNum"]?.toIntOrNull()
                val dayInMonth = parts["dayInMonth"]?.toIntOrNull()
                    ?.let { value -> DayOfWeek.values().find { it.value == value } }
                val monthDays = parts["monthDays"]?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.filter { it in 1..31 }
                    ?.toSet() ?: emptySet()
                
                ReminderRule(hours, days, advType, weekNum, dayInMonth, monthDays)
            } catch (e: Exception) {
                android.util.Log.e("ReminderRule", "Error deserializing: $serialized", e)
                ReminderRule()
            }
        }
    }
}

/**
 * Расширения для DayOfWeek
 */
fun DayOfWeek.toShortString(): String = when (this) {
    DayOfWeek.MONDAY -> "ПН"
    DayOfWeek.TUESDAY -> "ВТ"
    DayOfWeek.WEDNESDAY -> "СР"
    DayOfWeek.THURSDAY -> "ЧТ"
    DayOfWeek.FRIDAY -> "ПТ"
    DayOfWeek.SATURDAY -> "СБ"
    DayOfWeek.SUNDAY -> "ВС"
}

fun DayOfWeek.toFullString(): String = when (this) {
    DayOfWeek.MONDAY -> "понедельник"
    DayOfWeek.TUESDAY -> "вторник"
    DayOfWeek.WEDNESDAY -> "среда"
    DayOfWeek.THURSDAY -> "четверг"
    DayOfWeek.FRIDAY -> "пятница"
    DayOfWeek.SATURDAY -> "суббота"
    DayOfWeek.SUNDAY -> "воскресенье"
}

/**
 * Винительный падеж с предлогом "Каждый/Каждую/Каждое"
 */
fun DayOfWeek.toAccusativeWithPrefix(): String = when (this) {
    DayOfWeek.MONDAY -> "Каждый понедельник"
    DayOfWeek.TUESDAY -> "Каждый вторник"
    DayOfWeek.WEDNESDAY -> "Каждую среду"
    DayOfWeek.THURSDAY -> "Каждый четверг"
    DayOfWeek.FRIDAY -> "Каждую пятницу"
    DayOfWeek.SATURDAY -> "Каждую субботу"
    DayOfWeek.SUNDAY -> "Каждое воскресенье"
}



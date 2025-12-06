package com.example.vkbookandroid

import android.util.Log
import java.io.InputStream
import java.time.DayOfWeek

/**
 * Парсер для Excel файла "График проверок .xlsx" (с пробелом!)
 * Читает столбцы A (операция), B (частота), C (время)
 * и создаёт ReminderRule автоматически
 */
class ChecksScheduleExcelParser {
    
    companion object {
        private const val TAG = "ChecksScheduleParser"
    }
    
    /**
     * Загружает данные из Excel файла
     */
    fun parseExcelFile(inputStream: InputStream): List<ChecksScheduleItem> {
        val items = mutableListOf<ChecksScheduleItem>()
        
        try {
            val workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            
            for (rowIndex in 1..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                
                val operation = getCellValue(row, 0).trim()
                if (operation.isEmpty()) continue
                
                val frequency = getCellValue(row, 1).trim()
                val timeStr = getCellValue(row, 2).trim()
                val actualFrequency = if (frequency.isEmpty()) "ЕЖЕДНЕВНО" else frequency
                
                val reminderRules = parseFrequencyAndTime(actualFrequency, timeStr)
                
                if (reminderRules.isNotEmpty()) {
                    items.add(ChecksScheduleItem(operation, reminderRules))
                }
            }
            
            workbook.close()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Excel file", e)
        }
        
        return items
    }
    
    /**
     * Парсит частоту и время, создаёт список ReminderRule
     * Может вернуть несколько правил для "1 и 3 понедельник"
     */
    private fun parseFrequencyAndTime(frequency: String, timeStr: String): List<ReminderRule> {
        val lower = frequency.lowercase()
        
        val hours = parseHours(timeStr)
        if (hours.isEmpty()) {
            return emptyList()
        }
        
        // ЕЖЕСМЕННО / ЕЖЕДНЕВНО = каждый день
        if (lower.contains("ежесменно") || 
            lower.contains("ежедневно") || 
            lower.contains("каждый день") ||
            lower == "ежедневно") {
            return listOf(ReminderRule(
                selectedHours = hours,
                selectedDaysOfWeek = DayOfWeek.values().toSet()
            ))
        }
        
        // N-й день недели (ПРОВЕРЯЕМ ПЕРВЫМ! "1 и 3 понедельник")
        val weekdayOfMonth = parseWeekdayOfMonth(lower)
        if (weekdayOfMonth != null) {
            val (weeks, dayOfWeek) = weekdayOfMonth
            
            // Создаём отдельное правило для КАЖДОЙ недели
            val rules = weeks.map { week ->
                ReminderRule(
                    selectedHours = hours,
                    selectedDaysOfWeek = emptySet(),
                    advancedType = "MONTHLY_BY_WEEKDAY",
                    weekOfMonth = week,
                    dayOfWeekInMonth = dayOfWeek,
                    daysOfMonth = emptySet()
                )
            }
            return rules
        }
        
        // Несколько дней недели (Понедельник и четверг)
        val multipleDays = parseMultipleDaysOfWeek(lower)
        if (multipleDays.isNotEmpty()) {
            return listOf(ReminderRule(
                selectedHours = hours,
                selectedDaysOfWeek = multipleDays
            ))
        }
        
        // Один день недели
        val singleDay = parseSingleDayOfWeek(lower)
        if (singleDay != null) {
            return listOf(ReminderRule(
                selectedHours = hours,
                selectedDaysOfWeek = setOf(singleDay)
            ))
        }
        
        // Числа месяца (1 и 16, 1,16)
        val daysOfMonth = parseDaysOfMonth(lower)
        if (daysOfMonth.isNotEmpty()) {
            return listOf(ReminderRule(
                selectedHours = hours,
                selectedDaysOfWeek = emptySet(),
                advancedType = "MONTHLY_BY_DATE",
                weekOfMonth = null,
                dayOfWeekInMonth = null,
                daysOfMonth = daysOfMonth
            ))
        }
        
        return emptyList()
    }
    
    /**
     * Парсит часы из строки времени
     * Поддерживает:
     * - Диапазоны: "08:00-14:00", "с 08:00 до 14:00"
     * - Несколько диапазонов: "с 00:00 до 02:00 и с 20:00 до 23:00"
     * - Списки: "02:00; 06:00; 10:00"
     */
    private fun parseHours(timeStr: String): Set<Int> {
        if (timeStr.isEmpty()) {
            return emptySet()
        }
        
        val hours = mutableSetOf<Int>()
        
        // ИСПРАВЛЕНИЕ: Сначала обрабатываем все диапазоны "с XX:XX до XX:XX"
        // Паттерн для "с XX:XX до XX:XX" (с учетом русского текста)
        val rangePatternRu = Regex("""с\s+(\d{1,2}):?(\d{2})\s+до\s+(\d{1,2}):?(\d{2})""", RegexOption.IGNORE_CASE)
        rangePatternRu.findAll(timeStr).forEach { match ->
            val startHour = match.groupValues[1].toIntOrNull()
            val endHour = match.groupValues[3].toIntOrNull()
            
            if (startHour != null && endHour != null && startHour in 0..23 && endHour in 0..23) {
                // "до 23:00" означает что час 23 НЕ включается (00:00-22:59)
                // Поэтому добавляем часы от startHour до endHour-1
                if (startHour <= endHour) {
                    // Обычный диапазон: "с 00:00 до 02:00" → 0, 1 (без 2)
                    (startHour until endHour).forEach { h -> hours.add(h) }
                } else {
                    // Переход через полночь: "с 22:00 до 02:00" → 22, 23, 0, 1
                    (startHour..23).forEach { h -> hours.add(h) }
                    (0 until endHour).forEach { h -> hours.add(h) }
                }
            }
        }
        
        // Если нашли диапазоны "с/до", возвращаем результат
        if (hours.isNotEmpty()) {
            return hours
        }
        
        // Формат 1: Диапазон "08:00-14:00" или "0800-1400" (без "с/до")
        val rangePattern = Regex("""(\d{1,2}):?(\d{2})\s*[-–]\s*(\d{1,2}):?(\d{2})""")
        rangePattern.findAll(timeStr).forEach { match ->
            val startHour = match.groupValues[1].toIntOrNull()
            val endHour = match.groupValues[3].toIntOrNull()
            
            if (startHour != null && endHour != null && startHour in 0..23 && endHour in 0..23) {
            if (startHour <= endHour) {
                    // Для дефисов "08:00-14:00" включаем оба конца
                (startHour..endHour).forEach { h -> hours.add(h) }
            } else {
                // Переход через полночь
                (startHour..23).forEach { h -> hours.add(h) }
                (0..endHour).forEach { h -> hours.add(h) }
            }
            }
        }
        
        // Если нашли диапазоны с дефисом, возвращаем результат
        if (hours.isNotEmpty()) {
            return hours
        }
        
        // Формат 2: Список "02:00; 06:00; 10:00" или "в 02:00, 06:00, 10:00"
        val listPattern = Regex("""(\d{1,2}):?(\d{2})""")
        listPattern.findAll(timeStr).forEach {
            val hour = it.groupValues[1].toIntOrNull()
            if (hour != null && hour in 0..23) {
                hours.add(hour)
            }
        }
        
        return hours
    }
    
    /**
     * Парсит один день недели
     */
    private fun parseSingleDayOfWeek(text: String): DayOfWeek? {
        return when {
            text.contains("понедельник") || text.contains("пн") -> DayOfWeek.MONDAY
            text.contains("вторник") || text.contains("вт") -> DayOfWeek.TUESDAY
            text.contains("сред") || text.contains("ср") -> DayOfWeek.WEDNESDAY
            text.contains("четверг") || text.contains("чт") -> DayOfWeek.THURSDAY
            text.contains("пятниц") || text.contains("пт") -> DayOfWeek.FRIDAY
            text.contains("суббот") || text.contains("сб") -> DayOfWeek.SATURDAY
            text.contains("воскресен") || text.contains("вс") -> DayOfWeek.SUNDAY
            else -> null
        }
    }
    
    /**
     * Парсит несколько дней недели (Понедельник и четверг)
     * НЕ срабатывает для "1 и 3 понедельник" (это parseWeekdayOfMonth)
     */
    private fun parseMultipleDaysOfWeek(text: String): Set<DayOfWeek> {
        // Проверяем что это НЕ "N-й день недели"
        // Если есть цифры перед днями недели - это parseWeekdayOfMonth
        val hasNumberBeforeDay = Regex("""\d+\s*(?:и\s*\d+)?\s*(?:понедельник|вторник|сред|четверг|пятниц|суббот|воскресен)""")
        if (hasNumberBeforeDay.find(text) != null) {
            return emptySet() // Это обработает parseWeekdayOfMonth
        }
        
        val days = mutableSetOf<DayOfWeek>()
        
        if (text.contains("понедельник") || text.contains("пн")) days.add(DayOfWeek.MONDAY)
        if (text.contains("вторник") || text.contains("вт")) days.add(DayOfWeek.TUESDAY)
        if (text.contains("сред") || text.contains("ср")) days.add(DayOfWeek.WEDNESDAY)
        if (text.contains("четверг") || text.contains("чт")) days.add(DayOfWeek.THURSDAY)
        if (text.contains("пятниц") || text.contains("пт")) days.add(DayOfWeek.FRIDAY)
        if (text.contains("суббот") || text.contains("сб")) days.add(DayOfWeek.SATURDAY)
        if (text.contains("воскресен") || text.contains("вс")) days.add(DayOfWeek.SUNDAY)
        
        // Если нашли 2+ дня и есть "и" - это несколько дней
        return if (days.size >= 2 && text.contains("и")) days else emptySet()
    }
    
    /**
     * Парсит числа месяца.
     * Поддерживает варианты:
     * - "1 и 16"
     * - "1,4" / "1, 4, 17" / "1;4"
     * - "1 числа" / "1-е число" / "1-го"
     * - просто "1"
     *
     * Внимание: разбор "1 и 3 понедельник" обрабатывается раньше в parseWeekdayOfMonth.
     */
    private fun parseDaysOfMonth(text: String): Set<Int> {
        val days = mutableSetOf<Int>()

        // Если внутри есть явные дни недели — это не "числа месяца"
        if (text.contains("понедельник") || text.contains("вторник") ||
            text.contains("среда") || text.contains("четверг") ||
            text.contains("пятниц") || text.contains("суббот") || text.contains("воскресен")) {
            return emptySet()
        }

        // Нормализуем разделители: "и" / "," / ";" → запятая
        val normalized = text
            .replace(Regex("\\s+и\\s+"), ",")
            .replace(';', ',')
            .replace(Regex("\\s+"), " ")
            .trim()

        // Извлекаем числа 1..31 с возможными суффиксами "-е"/"-го" и словами "число/числа"
        // Примеры: "1", "1 числа", "1-е", "1-го", "1-е число"
        val dayToken = Regex("""\b(\d{1,2})(?:-?е|-?го)?(?:\s*числа|\s*число)?\b""", RegexOption.IGNORE_CASE)
        dayToken.findAll(normalized).forEach { m ->
            val day = m.groupValues[1].toIntOrNull()
            if (day != null && day in 1..31) days.add(day)
        }

        // Если ничего не нашли выше, попробуем общий случай: списки через запятую "1,4,17"
        if (days.isEmpty() && normalized.contains(',')) {
            normalized.split(',')
                .map { it.trim() }
                .forEach { part ->
                    val d = part.toIntOrNull()
                    if (d != null && d in 1..31) days.add(d)
                }
        }

        // Пограничный случай: строка — одно число без суффиксов ("1")
        if (days.isEmpty()) {
            val single = text.trim().toIntOrNull()
            if (single != null && single in 1..31) days.add(single)
        }

        return days
    }
    
    /**
     * Парсит "N-й день недели месяца"
     * Возвращает пару: список недель и день недели
     */
    private fun parseWeekdayOfMonth(text: String): Pair<List<Int>, DayOfWeek>? {
        val day = parseSingleDayOfWeek(text) ?: return null
        val weeks = mutableListOf<Int>()
        
        // Ищем номера недель перед днем недели
        // "1 и 3 понедельник" → 1-я и 3-я неделя
        val pattern = Regex("""(\d+)\s*(?:и\s*(\d+))?\s*(?:понедельник|вторник|сред|четверг|пятниц|суббот|воскресен)""")
        pattern.find(text)?.let {
            val week1 = it.groupValues[1].toIntOrNull()
            val week2 = it.groupValues[2].toIntOrNull()
            
            if (week1 != null && week1 in 1..4) weeks.add(week1)
            if (week2 != null && week2 in 1..4) weeks.add(week2)
        }
        
        return if (weeks.isNotEmpty()) weeks to day else null
    }
    
    /**
     * Получает значение ячейки как строку
     */
    private fun getCellValue(row: org.apache.poi.ss.usermodel.Row, columnIndex: Int): String {
        val cell = row.getCell(columnIndex) ?: return ""
        
        return when (cell.cellType) {
            org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue
            org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                // Проверяем, является ли число датой
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    cell.localDateTimeCellValue.toString()
                } else {
                    cell.numericCellValue.toInt().toString()
                }
            }
            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue.toString()
            org.apache.poi.ss.usermodel.CellType.FORMULA -> {
                try {
                    cell.stringCellValue
                } catch (e: Exception) {
                    cell.numericCellValue.toString()
                }
            }
            else -> ""
        }
    }
}

/**
 * Элемент графика проверок из Excel
 */
data class ChecksScheduleItem(
    val operation: String,
    val reminderRules: List<ReminderRule> // Несколько правил для одной операции
)


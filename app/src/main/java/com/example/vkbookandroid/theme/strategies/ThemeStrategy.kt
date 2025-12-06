package com.example.vkbookandroid.theme.strategies

import android.content.Context
import android.graphics.drawable.Drawable

/**
 * Стратегия темы - интерфейс для изолированной реализации каждой темы
 * Каждая тема реализует этот интерфейс независимо от других
 */
interface ThemeStrategy {
    
    /**
     * Получить цвета для ячейки часа
     * @param hour номер часа (0-23)
     * @param isSelected выбрана ли ячейка пользователем
     * @param isActive находится ли час в текущей активной смене
     * @param dayOffset смещение дня (-1 = предыдущий, 0 = текущий, 1 = следующий)
     * @return Pair(background color, text color)
     */
    fun getHourCellColors(
        hour: Int,
        isSelected: Boolean,
        isActive: Boolean,
        dayOffset: Int
    ): HourCellStyle
    
    /**
     * Получить цвета для дня календаря
     * @param isToday является ли день сегодняшним
     * @param isSelected выбран ли день
     * @param dayOfWeek день недели (1-7, 1=понедельник)
     * @return пара (background color, text color)
     */
    fun getCalendarDayColors(
        isToday: Boolean,
        isSelected: Boolean,
        dayOfWeek: Int
    ): CalendarDayStyle
    
    /**
     * Получить стиль рамки для сегодняшней даты
     */
    fun getTodayBorderStyle(): BorderStyle
    
    /**
     * Получить радиус скругления углов для ячеек часов (в dp)
     */
    fun getHourCellCornerRadius(): Float
    
    /**
     * Получить цвет рамки для текущего часа (null = без рамки)
     */
    fun getCurrentHourBorderColor(): Int?
    
    /**
     * Получить цвета для строки задачи
     * @param isActive активна ли задача
     */
    fun getTaskRowColors(isActive: Boolean): TaskRowStyle
    
    /**
     * Получить стиль для toggle-кнопок
     * @param isChecked нажата ли кнопка
     */
    fun getToggleButtonStyle(isChecked: Boolean): ButtonStyle
    
    /**
     * Получить стиль для обычных кнопок
     */
    fun getButtonStyle(): ButtonStyle
    
    /**
     * Получить цвет фона главного экрана
     */
    fun getBackgroundColor(): Int
    
    /**
     * Получить drawable для фона (может быть null)
     */
    fun getBackgroundDrawable(context: Context): Drawable?
    
    /**
     * Получить цвет текста заголовков
     */
    fun getHeaderTextColor(): Int
    
    /**
     * Получить основной цвет текста
     */
    fun getTextPrimaryColor(): Int
    
    /**
     * Название темы
     */
    fun getThemeName(): String
}

/**
 * Стиль ячейки часа
 */
data class HourCellStyle(
    val backgroundColor: Int,
    val textColor: Int,
    val borderColor: Int? = null,
    val borderWidthDp: Float = 0f
)

/**
 * Стиль дня календаря
 */
data class CalendarDayStyle(
    val backgroundColor: Int,
    val textColor: Int
)

/**
 * Стиль рамки
 */
data class BorderStyle(
    val color: Int,
    val widthDp: Int,
    val radiusDp: Float
)

/**
 * Стиль строки задачи
 */
data class TaskRowStyle(
    val backgroundColor: Int,
    val textColor: Int
)

/**
 * Стиль кнопки
 */
data class ButtonStyle(
    val backgroundColor: Int,
    val textColor: Int,
    val cornerRadiusDp: Float,
    val drawableResId: Int? = null  // Для классической темы (bg_zoom_button)
)


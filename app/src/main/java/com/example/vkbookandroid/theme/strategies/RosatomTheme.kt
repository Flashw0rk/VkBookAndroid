package com.example.vkbookandroid.theme.strategies

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.core.content.ContextCompat
import com.example.vkbookandroid.R

/**
 * Корпоративная тема Росатома - ПОЛНОСТЬЮ ИЗОЛИРОВАННАЯ
 * НЕ зависит от других тем
 */
class RosatomTheme : ThemeStrategy {
    
    // Корпоративные цвета Росатома
    private val textDark = Color.parseColor("#003D5C")
    private val orangeAccent = Color.parseColor("#FF6B35")  // Фирменный оранжевый
    
    // Цвета для шкалы часов
    private val hour_morning_base = Color.parseColor("#B3E5FC")  // Утро 08-13
    private val hour_day_base = Color.parseColor("#81D4FA")      // День 14-19
    private val hour_night_base = Color.parseColor("#4DB6D5")    // Ночь 20-07 (темнее!)
    
    private val hour_morning_active = Color.parseColor("#4FC3F7")
    private val hour_day_active = Color.parseColor("#29B6F6")
    private val hour_night_active = Color.parseColor("#0398D4")  // Темнее для ночи
    
    private val hour_selected = orangeAccent
    
    // Цвета для календаря
    private val calendar_workday = Color.parseColor("#B3E5FC")
    private val calendar_saturday = Color.parseColor("#81D4FA")
    private val calendar_sunday = Color.parseColor("#4FC3F7")
    private val calendar_selected = Color.parseColor("#0091D5")
    
    override fun getHourCellColors(
        hour: Int,
        isSelected: Boolean,
        isActive: Boolean,
        dayOffset: Int
    ): HourCellStyle {
        // Определяем диапазон
        val range = when {
            hour in 8..13 -> 1   // Утро
            hour in 14..19 -> 2  // День  
            else -> 3            // Ночь (20-23 или 00-07)
        }
        
        val bgColor = when {
            isSelected -> hour_selected
            isActive -> when (range) {
                1 -> hour_morning_active
                2 -> hour_day_active
                else -> hour_night_active
            }
            else -> when (range) {
                1 -> hour_morning_base
                2 -> hour_day_base
                else -> hour_night_base
            }
        }
        
        val textColor = if (isSelected) Color.WHITE else textDark
        
        return HourCellStyle(
            backgroundColor = bgColor,
            textColor = textColor
        )
    }
    
    override fun getCalendarDayColors(
        isToday: Boolean,
        isSelected: Boolean,
        dayOfWeek: Int
    ): CalendarDayStyle {
        val (bg, text) = when {
            isToday && isSelected -> calendar_selected to Color.WHITE
            isToday -> {
                // Для сегодняшней даты: фон по дню недели, рамка будет отдельно
                val todayBg = when (dayOfWeek) {
                    6 -> calendar_saturday
                    7 -> calendar_sunday
                    else -> calendar_workday
                }
                todayBg to textDark
            }
            isSelected -> calendar_selected to Color.WHITE
            else -> {
                val dayBg = when (dayOfWeek) {
                    6 -> calendar_saturday
                    7 -> calendar_sunday
                    else -> calendar_workday
                }
                dayBg to textDark
            }
        }
        
        return CalendarDayStyle(bg, text)
    }
    
    override fun getTodayBorderStyle(): BorderStyle {
        return BorderStyle(
            color = orangeAccent,  // Оранжевая рамка
            widthDp = 2,
            radiusDp = 4f
        )
    }
    
    override fun getHourCellCornerRadius(): Float {
        return 4f  // Росатом - скругленные углы
    }
    
    override fun getCurrentHourBorderColor(): Int? {
        return null  // Росатом - без рамки для текущего часа
    }
    
    override fun getTaskRowColors(isActive: Boolean): TaskRowStyle {
        // Полностью изолированные цвета для Росатом
        val bg = if (isActive) {
            Color.parseColor("#FFEB3B")  // Желтый для активных задач
        } else {
            Color.parseColor("#E3F2FD")  // Светло-голубой для неактивных
        }
        
        return TaskRowStyle(
            backgroundColor = bg,
            textColor = textDark
        )
    }
    
    override fun getToggleButtonStyle(isChecked: Boolean): ButtonStyle {
        // Собственные цвета кнопок для Росатом
        val bgColor = if (isChecked) {
            Color.parseColor("#0091D5")  // Синий для нажатой
        } else {
            Color.parseColor("#4FC3F7")  // Голубой для ненажатой
        }
        
        return ButtonStyle(
            backgroundColor = bgColor,
            textColor = Color.WHITE,
            cornerRadiusDp = 12f
        )
    }
    
    override fun getButtonStyle(): ButtonStyle {
        return ButtonStyle(
            backgroundColor = Color.parseColor("#4FC3F7"),  // Голубой
            textColor = Color.WHITE,
            cornerRadiusDp = 12f
        )
    }
    
    override fun getBackgroundColor(): Int {
        return Color.parseColor("#E1F5FE")  // Очень светлый голубой фон
    }
    
    override fun getBackgroundDrawable(context: Context): Drawable? {
        // Росатом использует градиент и логотип
        return ContextCompat.getDrawable(context, R.drawable.bg_rosatom_photo)
    }
    
    override fun getHeaderTextColor(): Int {
        return textDark  // Темный текст для заголовков
    }
    
    override fun getTextPrimaryColor(): Int {
        return textDark
    }
    
    override fun getThemeName(): String = "Росатом"
}


package com.example.vkbookandroid.theme.strategies

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.vkbookandroid.R

/**
 * Неоновая (Nuclear) тема - ПОЛНОСТЬЮ ИЗОЛИРОВАННАЯ
 */
class NuclearTheme : ThemeStrategy {
    
    private val textBright = Color.parseColor("#E4F4FF")
    private val orangeAccent = Color.parseColor("#FF6B35")
    
    // Цвета для шкалы часов
    private val hour_morning_base = Color.parseColor("#1B4F82")
    private val hour_day_base = Color.parseColor("#256DB3")
    private val hour_night_base = Color.parseColor("#10355F")
    
    private val hour_morning_active = Color.parseColor("#2A68A9")
    private val hour_day_active = Color.parseColor("#3183D2")
    private val hour_night_active = Color.parseColor("#184472")
    
    // Цвета для календаря
    private val calendar_workday = Color.parseColor("#49C9D4")
    private val calendar_saturday = Color.parseColor("#34B2F2")
    private val calendar_sunday = Color.parseColor("#2C91E8")
    private val calendar_selected = Color.parseColor("#2FD2FF")
    
    private val textPrimary = Color.parseColor("#013349")
    private val textSaturday = Color.parseColor("#02243C")
    private val textSunday = Color.parseColor("#011B33")
    private val textSelected = Color.parseColor("#001E36")
    
    override fun getHourCellColors(
        hour: Int,
        isSelected: Boolean,
        isActive: Boolean,
        dayOffset: Int
    ): HourCellStyle {
        val range = when {
            hour in 8..13 -> 1
            hour in 14..19 -> 2
            else -> 3
        }
        
        val bgColor = when {
            isSelected -> orangeAccent
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
        
        val textColor = if (isSelected) Color.WHITE else textBright
        
        return HourCellStyle(bgColor, textColor)
    }
    
    override fun getCalendarDayColors(
        isToday: Boolean,
        isSelected: Boolean,
        dayOfWeek: Int
    ): CalendarDayStyle {
        val (bg, text) = when {
            isToday && isSelected -> calendar_selected to textSelected
            isToday -> {
                // Фон как у обычного дня
                val todayBg = when (dayOfWeek) {
                    6 -> calendar_saturday
                    7 -> calendar_sunday
                    else -> calendar_workday
                }
                val todayText = when (dayOfWeek) {
                    6 -> textSaturday
                    7 -> textSunday
                    else -> textPrimary
                }
                todayBg to todayText
            }
            isSelected -> calendar_selected to textSelected
            else -> {
                val dayBg = when (dayOfWeek) {
                    6 -> calendar_saturday
                    7 -> calendar_sunday
                    else -> calendar_workday
                }
                val dayText = when (dayOfWeek) {
                    6 -> textSaturday
                    7 -> textSunday
                    else -> textPrimary
                }
                dayBg to dayText
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
        return 4f  // Nuclear - скругленные углы
    }
    
    override fun getCurrentHourBorderColor(): Int? {
        return null  // Nuclear - без рамки для текущего часа
    }
    
    override fun getTaskRowColors(isActive: Boolean): TaskRowStyle {
        val inactive = Color.parseColor("#1D5EA9")
        val active = Color.parseColor("#0D2B51")
        
        return TaskRowStyle(
            backgroundColor = if (isActive) active else inactive,
            textColor = Color.parseColor("#E4F4FF")  // Светлый текст для Nuclear
        )
    }
    
    override fun getToggleButtonStyle(isChecked: Boolean): ButtonStyle {
        val bgColor = if (isChecked) {
            Color.parseColor("#00D8FF")  // Яркий циан для нажатой
        } else {
            Color.parseColor("#2DCBE0")  // Светлый циан для ненажатой
        }
        
        return ButtonStyle(
            backgroundColor = bgColor,
            textColor = Color.parseColor("#003144"),  // Темный текст
            cornerRadiusDp = 16f
        )
    }
    
    override fun getButtonStyle(): ButtonStyle {
        return ButtonStyle(
            backgroundColor = Color.parseColor("#2DCBE0"),
            textColor = Color.parseColor("#003144"),
            cornerRadiusDp = 16f
        )
    }
    
    override fun getBackgroundColor(): Int {
        return Color.parseColor("#001E2B")  // Темный фон для Nuclear
    }
    
    override fun getBackgroundDrawable(context: Context): Drawable? {
        return ContextCompat.getDrawable(context, R.drawable.bg_atom_3d_realistic)
    }
    
    override fun getHeaderTextColor(): Int {
        return Color.parseColor("#003144")
    }
    
    override fun getTextPrimaryColor(): Int {
        return textBright
    }
    
    override fun getThemeName(): String = "Nuclear"
}


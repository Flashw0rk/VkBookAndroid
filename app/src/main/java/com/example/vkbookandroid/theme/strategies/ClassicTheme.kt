package com.example.vkbookandroid.theme.strategies

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import com.example.vkbookandroid.R

/**
 * Классическая тема - оригинальные цвета приложения
 * ПОЛНОСТЬЮ ИЗОЛИРОВАНА от других тем
 */
class ClassicTheme : ThemeStrategy {
    
    // Функция осветления цвета (локальная копия)
    private fun lighten(color: Int, factor: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val nr = (r + (255 - r) * factor).toInt().coerceIn(0, 255)
        val ng = (g + (255 - g) * factor).toInt().coerceIn(0, 255)
        val nb = (b + (255 - b) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(nr, ng, nb)
    }
    
    override fun getHourCellColors(
        hour: Int,
        isSelected: Boolean,
        isActive: Boolean,
        dayOffset: Int
    ): HourCellStyle {
        val (bg, fg) = when {
            isSelected -> Color.parseColor("#64B5F6") to Color.parseColor("#0D47A1")
            isActive && hour in 8..13 -> Color.parseColor("#388E3C") to Color.WHITE
            isActive && hour in 14..19 -> lighten(Color.parseColor("#2196F3"), 0.3f) to Color.parseColor("#2196F3")
            isActive && (hour in 20..23 || hour in 0..7) -> lighten(Color.parseColor("#78909C"), 0.7f) to Color.parseColor("#78909C")
            !isActive && hour in 8..13 -> lighten(Color.parseColor("#388E3C"), 0.5f) to Color.parseColor("#2E7D32")
            !isActive && hour in 14..19 -> lighten(Color.parseColor("#2196F3"), 0.6f) to Color.parseColor("#1976D2")
            !isActive && (hour in 20..23 || hour in 0..7) -> lighten(Color.parseColor("#78909C"), 0.8f) to Color.parseColor("#546E7A")
            else -> Color.parseColor("#CCCCCC") to Color.parseColor("#212121")
        }
        
        return HourCellStyle(bg, fg)
    }
    
    override fun getCalendarDayColors(
        isToday: Boolean,
        isSelected: Boolean,
        dayOfWeek: Int
    ): CalendarDayStyle {
        val (bg, text) = when {
            isToday && isSelected -> Color.parseColor("#90CAF9") to Color.parseColor("#1976D2")
            isToday -> Color.parseColor("#FFFFFF") to Color.parseColor("#212121")
            isSelected -> Color.parseColor("#90CAF9") to Color.parseColor("#1976D2")
            else -> lighten(Color.parseColor("#FFFFFF"), 0.3f) to Color.parseColor("#212121")
        }
        
        return CalendarDayStyle(bg, text)
    }
    
    override fun getTodayBorderStyle(): BorderStyle {
        return BorderStyle(
            color = Color.BLACK,  // Черная рамка
            widthDp = 2,
            radiusDp = 4f
        )
    }
    
    override fun getHourCellCornerRadius(): Float {
        return 0f  // Классическая тема - без скругления
    }
    
    override fun getCurrentHourBorderColor(): Int? {
        return null  // Классическая тема - без рамки для текущего часа
    }
    
    override fun getTaskRowColors(isActive: Boolean): TaskRowStyle {
        val bg = if (isActive) {
            Color.parseColor("#FFEB3B")  // Желтый
        } else {
            Color.parseColor("#FFFFFF")  // Белый
        }
        
        return TaskRowStyle(bg, Color.parseColor("#212121"))
    }
    
    override fun getToggleButtonStyle(isChecked: Boolean): ButtonStyle {
        return ButtonStyle(
            backgroundColor = Color.parseColor("#673AB7"),  // Deep Purple
            textColor = Color.WHITE,
            cornerRadiusDp = 20f,
            drawableResId = R.drawable.bg_zoom_button
        )
    }
    
    override fun getButtonStyle(): ButtonStyle {
        return ButtonStyle(
            backgroundColor = Color.parseColor("#673AB7"),
            textColor = Color.WHITE,
            cornerRadiusDp = 20f,
            drawableResId = R.drawable.bg_zoom_button
        )
    }
    
    override fun getBackgroundColor(): Int {
        return Color.parseColor("#FAFAFA")  // Светло-серый
    }
    
    override fun getBackgroundDrawable(context: Context): Drawable? {
        return null  // Классическая тема без фонового изображения
    }
    
    override fun getHeaderTextColor(): Int {
        return Color.parseColor("#212121")  // Темно-серый
    }
    
    override fun getTextPrimaryColor(): Int {
        return Color.parseColor("#212121")
    }
    
    override fun getThemeName(): String = "Классическая"
}


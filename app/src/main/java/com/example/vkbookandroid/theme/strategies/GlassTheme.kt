package com.example.vkbookandroid.theme.strategies

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.vkbookandroid.R

/**
 * Стеклянная (Glass) тема - ПОЛНОСТЬЮ ИЗОЛИРОВАННАЯ
 */
class GlassTheme : ThemeStrategy {
    
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
        // Полностью изолированные цвета для стеклянной темы
        val textDefault = Color.parseColor("#212121")
        
        val selectedBg = Color.parseColor("#4DD0E1")  // Циан для выделенного
        val morningBg = Color.parseColor("#B2EBF2")   // Светлый циан для утра
        val dayBg = Color.parseColor("#80DEEA")       // Циан для дня
        val nightBg = Color.parseColor("#4DD0E1")     // Яркий циан для ночи
        
        val morningActive = Color.parseColor("#4DD0E1")
        val dayActive = Color.parseColor("#26C6DA")
        val nightActive = Color.parseColor("#00ACC1")
        
        val bgColor = when {
            isSelected -> selectedBg
            isActive && hour in 8..13 -> morningActive
            isActive && hour in 14..19 -> dayActive
            isActive -> nightActive
            hour in 8..13 -> morningBg
            hour in 14..19 -> dayBg
            else -> nightBg
        }
        
        // Для ночного промежутка (20-07) всегда черный текст, для остальных - по яркости
        val textColor = when {
            hour in 20..23 || hour in 0..7 -> textDefault  // Черный для 20-07
            ColorUtils.calculateLuminance(bgColor) < 0.5 -> Color.WHITE
            else -> textDefault
        }
        
        return HourCellStyle(bgColor, textColor)
    }
    
    override fun getCalendarDayColors(
        isToday: Boolean,
        isSelected: Boolean,
        dayOfWeek: Int
    ): CalendarDayStyle {
        // Полностью изолированные цвета календаря для Glass
        val cardColor = Color.parseColor("#E0F7FA")  // Очень светлый циан
        val textDefault = Color.parseColor("#212121")
        val selectedBg = Color.parseColor("#26C6DA")  // Циан для выделенного
        val normalBg = Color.parseColor("#F0F9FA")    // Почти белый с цианом
        
        fun textFor(bg: Int): Int {
            val luminance = ColorUtils.calculateLuminance(bg)
            return if (luminance < 0.45) Color.WHITE else textDefault
        }
        
        val (bg, text) = when {
            isToday && isSelected -> selectedBg to textFor(selectedBg)
            isToday -> normalBg to textFor(normalBg)
            isSelected -> selectedBg to textFor(selectedBg)
            else -> normalBg to textFor(normalBg)
        }
        
        return CalendarDayStyle(bg, text)
    }
    
    override fun getTodayBorderStyle(): BorderStyle {
        return BorderStyle(
            color = Color.BLACK,  // Черная рамка для стеклянной
            widthDp = 2,
            radiusDp = 4f
        )
    }
    
    override fun getHourCellCornerRadius(): Float {
        return 4f  // Glass - скругленные углы
    }
    
    override fun getCurrentHourBorderColor(): Int? {
        return Color.BLACK  // Черная рамка для текущего часа (как на других скинах)
    }
    
    override fun getTaskRowColors(isActive: Boolean): TaskRowStyle {
        val bg = if (isActive) {
            Color.parseColor("#80DEEA")  // Бирюзовый (как ячейки часов 14-19, dayBg) для активных
        } else {
            Color.parseColor("#E0F7FA")  // Светло-циановый для неактивных
        }
        
        return TaskRowStyle(bg, Color.parseColor("#212121"))
    }
    
    override fun getToggleButtonStyle(isChecked: Boolean): ButtonStyle {
        val bgColor = if (isChecked) {
            Color.parseColor("#00ACC1")  // Темный циан для нажатой
        } else {
            Color.parseColor("#80DEEA")  // Светлый циан для ненажатой
        }
        
        return ButtonStyle(
            backgroundColor = bgColor,
            textColor = Color.WHITE,
            cornerRadiusDp = 24f
        )
    }
    
    override fun getButtonStyle(): ButtonStyle {
        return ButtonStyle(
            backgroundColor = Color.parseColor("#80DEEA"),
            textColor = Color.WHITE,
            cornerRadiusDp = 24f
        )
    }
    
    override fun getBackgroundColor(): Int {
        return Color.parseColor("#F0F9FA")  // Очень светлый голубовато-белый
    }
    
    override fun getBackgroundDrawable(context: Context): Drawable? {
        return ContextCompat.getDrawable(context, R.drawable.bg_modern_glass)
    }
    
    override fun getHeaderTextColor(): Int {
        return Color.parseColor("#212121")  // Темный текст заголовков
    }
    
    override fun getTextPrimaryColor(): Int {
        return Color.parseColor("#212121")  // Темный основной текст
    }
    
    override fun getThemeName(): String = "Стеклянная"
}


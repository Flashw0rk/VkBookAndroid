package com.example.vkbookandroid.theme.strategies

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.vkbookandroid.R

/**
 * Эргономичная светлая тема - ПОЛНОСТЬЮ ИЗОЛИРОВАННАЯ
 */
class ErgonomicTheme : ThemeStrategy {
    
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
        // Полностью изолированные цвета для эргономичной темы
        val primary = Color.parseColor("#8BC34A")  // Зеленый
        val accent = Color.parseColor("#689F38")   // Темно-зеленый
        val textDefault = Color.parseColor("#212121")  // Темный текст
        
        val selectedBg = Color.parseColor("#558B2F")  // Темно-зеленый для выделенного
        val morningBg = Color.parseColor("#C5E1A5")   // Светло-зеленый для утра
        val dayBg = Color.parseColor("#AED581")       // Зеленый для дня
        val nightBg = Color.parseColor("#9CCC65")     // Средний зеленый для ночи
        
        val morningActive = Color.parseColor("#9CCC65")
        val dayActive = Color.parseColor("#7CB342")
        val nightActive = Color.parseColor("#689F38")
        
        val bgColor = when {
            isSelected -> selectedBg
            isActive && hour in 8..13 -> morningActive
            isActive && hour in 14..19 -> dayActive
            isActive -> nightActive
            hour in 8..13 -> morningBg
            hour in 14..19 -> dayBg
            else -> nightBg
        }
        
        val textColor = if (ColorUtils.calculateLuminance(bgColor) < 0.5) {
            Color.WHITE
        } else {
            textDefault
        }
        
        return HourCellStyle(bgColor, textColor)
    }
    
    override fun getCalendarDayColors(
        isToday: Boolean,
        isSelected: Boolean,
        dayOfWeek: Int
    ): CalendarDayStyle {
        // Полностью изолированные цвета календаря
        val cardColor = Color.parseColor("#F1F8E9")  // Очень светлый зеленый
        val textDefault = Color.parseColor("#212121")  // Темный текст
        val selectedBg = Color.parseColor("#7CB342")   // Зеленый для выделенного
        val normalBg = Color.parseColor("#F9FBE7")     // Белый с зеленоватым
        
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
            color = Color.parseColor("#689F38"),  // Зеленая рамка
            widthDp = 2,
            radiusDp = 4f
        )
    }
    
    override fun getHourCellCornerRadius(): Float {
        return 4f  // Ergonomic - скругленные углы
    }
    
    override fun getCurrentHourBorderColor(): Int? {
        return null  // Ergonomic - без рамки для текущего часа
    }
    
    override fun getTaskRowColors(isActive: Boolean): TaskRowStyle {
        val bg = if (isActive) {
            Color.parseColor("#AED581")  // Зеленый (как ячейки часов 14-19) для активных
        } else {
            Color.parseColor("#F1F8E9")  // Светло-зеленый для неактивных
        }
        
        return TaskRowStyle(bg, Color.parseColor("#212121"))
    }
    
    override fun getToggleButtonStyle(isChecked: Boolean): ButtonStyle {
        val bgColor = if (isChecked) {
            Color.parseColor("#689F38")  // Темно-зеленый для нажатой
        } else {
            Color.parseColor("#8BC34A")  // Зеленый для ненажатой
        }
        
        return ButtonStyle(
            backgroundColor = bgColor,
            textColor = Color.WHITE,
            cornerRadiusDp = 20f
        )
    }
    
    override fun getButtonStyle(): ButtonStyle {
        return ButtonStyle(
            backgroundColor = Color.parseColor("#8BC34A"),
            textColor = Color.WHITE,
            cornerRadiusDp = 20f
        )
    }
    
    override fun getBackgroundColor(): Int {
        return Color.parseColor("#F9FBE7")  // Очень светлый желто-зеленый фон
    }
    
    override fun getBackgroundDrawable(context: Context): Drawable? {
        return ContextCompat.getDrawable(context, R.drawable.bg_ergonomic_light)
    }
    
    override fun getHeaderTextColor(): Int {
        return Color.parseColor("#212121")  // Темный текст заголовков
    }
    
    override fun getTextPrimaryColor(): Int {
        return Color.parseColor("#212121")  // Темный основной текст
    }
    
    override fun getThemeName(): String = "Эргономичная"
}


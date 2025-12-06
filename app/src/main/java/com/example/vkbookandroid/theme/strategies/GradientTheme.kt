package com.example.vkbookandroid.theme.strategies

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.vkbookandroid.R

/**
 * Брутальная (Gradient) тема - ПОЛНОСТЬЮ ИЗОЛИРОВАННАЯ
 */
class GradientTheme : ThemeStrategy {
    
    /**
     * Снижает насыщенность цвета (делает менее ярким)
     */
    private fun desaturate(color: Int, desaturationFactor: Float = 0.5f): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        // Снижаем насыщенность (S компонент в HSL)
        hsl[1] = hsl[1] * (1f - desaturationFactor)
        return ColorUtils.HSLToColor(hsl)
    }
    
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
        // Брутальная тема с розовыми оттенками для смен (менее насыщенные)
        val textDefault = Color.parseColor("#FFFFFF")  // Белый текст
        
        // Розовые цвета для смен (снижена насыщенность на 40%):
        val nightDarkPink = desaturate(Color.parseColor("#C2185B"), 0.4f)      // Темно-розовый для ночи (20-07)
        val morningLightPink = desaturate(Color.parseColor("#F8BBD0"), 0.4f)  // Светло-розовый для утра (08-13)
        val eveningMidPink = desaturate(Color.parseColor("#E91E63"), 0.4f)     // Средне-розовый для вечера (14-19)
        
        val selectedBg = desaturate(Color.parseColor("#B71C1C"), 0.4f)   // Приглушенный темно-красный для выделенного
        
        // Определяем диапазон с учетом dayOffset для ночной смены
        val isNightShift = (hour in 20..23 && dayOffset == -1) || 
                          (hour in 0..7 && dayOffset == 0) ||
                          (hour in 20..23 && dayOffset == 0) ||
                          (hour in 0..7 && dayOffset == 1)
        
        val bgColor = when {
            isSelected -> selectedBg
            isActive && isNightShift -> nightDarkPink
            isActive && hour in 8..13 -> morningLightPink
            isActive && hour in 14..19 -> eveningMidPink
            isNightShift -> nightDarkPink
            hour in 8..13 -> morningLightPink
            hour in 14..19 -> eveningMidPink
            else -> nightDarkPink  // На всякий случай
        }
        
        val textColor = if (ColorUtils.calculateLuminance(bgColor) < 0.5) {
            Color.WHITE
        } else {
            Color.parseColor("#000000")  // Черный текст для светлых фонов
        }
        
        return HourCellStyle(bgColor, textColor)
    }
    
    override fun getCalendarDayColors(
        isToday: Boolean,
        isSelected: Boolean,
        dayOfWeek: Int
    ): CalendarDayStyle {
        // Брутальная тема - светло-розовые ячейки календаря (менее насыщенные)
        val lightPink = desaturate(Color.parseColor("#F8BBD0"), 0.4f)  // Светло-розовый для всех ячеек
        val selectedBg = desaturate(Color.parseColor("#B71C1C"), 0.4f)   // Приглушенный темно-красный для выделенного
        
        val (bg, text) = when {
            isToday && isSelected -> selectedBg to Color.WHITE
            isToday -> lightPink to Color.parseColor("#000000")  // Светло-розовый с черным текстом
            isSelected -> selectedBg to Color.WHITE
            else -> lightPink to Color.parseColor("#000000")  // Светло-розовый с черным текстом
        }
        
        return CalendarDayStyle(bg, text)
    }
    
    override fun getTodayBorderStyle(): BorderStyle {
        return BorderStyle(
            color = Color.BLACK,  // Черная рамка для сегодняшнего дня
            widthDp = 2,
            radiusDp = 4f
        )
    }
    
    override fun getHourCellCornerRadius(): Float {
        return 10f  // Квадратные с заметно скругленными углами (не круглые, но скругленные)
    }
    
    override fun getCurrentHourBorderColor(): Int? {
        return Color.BLACK  // Черная рамка для текущего часа
    }
    
    override fun getTaskRowColors(isActive: Boolean): TaskRowStyle {
        // Брутальная тема - белый фон для невыделенных, приглушенный среднерозовый для выделенных (менее насыщенные)
        val whiteBg = Color.parseColor("#FFFFFF")  // Белый фон для невыделенных задач
        val activePink = desaturate(Color.parseColor("#F48FB1"), 0.4f)  // Приглушенный среднерозовый для выделенных
        
        val bg = if (isActive) {
            activePink
        } else {
            whiteBg
        }
        
        // Темно-розовый текст для всех задач (менее насыщенный)
        val textColor = desaturate(Color.parseColor("#C2185B"), 0.4f)  // Темно-розовый текст
        
        return TaskRowStyle(bg, textColor)
    }
    
    override fun getToggleButtonStyle(isChecked: Boolean): ButtonStyle {
        val bgColor = if (isChecked) {
            desaturate(Color.parseColor("#D32F2F"), 0.4f)  // Темно-красный для нажатой (менее насыщенный)
        } else {
            desaturate(Color.parseColor("#FF4081"), 0.4f)  // Розовый для ненажатой (менее насыщенный)
        }
        
        return ButtonStyle(
            backgroundColor = bgColor,
            textColor = Color.WHITE,
            cornerRadiusDp = 28f
        )
    }
    
    override fun getButtonStyle(): ButtonStyle {
        return ButtonStyle(
            backgroundColor = desaturate(Color.parseColor("#FF4081"), 0.4f),  // Менее насыщенный розовый
            textColor = Color.WHITE,
            cornerRadiusDp = 28f
        )
    }
    
    override fun getBackgroundColor(): Int {
        return Color.parseColor("#303030")  // Очень темный серый фон
    }
    
    override fun getBackgroundDrawable(context: Context): Drawable? {
        return ContextCompat.getDrawable(context, R.drawable.bg_modern_gradient)
    }
    
    override fun getHeaderTextColor(): Int {
        return desaturate(Color.parseColor("#C2185B"), 0.4f)  // Темно-розовый текст заголовков (менее насыщенный)
    }
    
    override fun getTextPrimaryColor(): Int {
        return Color.parseColor("#FFFFFF")  // Белый основной текст
    }
    
    override fun getThemeName(): String = "Брутальная"
}


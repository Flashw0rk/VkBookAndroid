package com.example.vkbookandroid.theme.strategies

import android.graphics.Color
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Юнит-тесты для ClassicTheme - проверка изоляции и правильности цветов
 */
class ClassicThemeTest {
    
    private lateinit var theme: ClassicTheme
    
    @Before
    fun setup() {
        theme = ClassicTheme()
    }
    
    @Test
    fun `theme name is correct`() {
        assertEquals("Классическая", theme.getThemeName())
    }
    
    @Test
    fun `selected hour has correct colors`() {
        val style = theme.getHourCellColors(
            hour = 10,
            isSelected = true,
            isActive = false,
            dayOffset = 0
        )
        
        assertEquals(Color.parseColor("#64B5F6"), style.backgroundColor)
        assertEquals(Color.parseColor("#0D47A1"), style.textColor)
    }
    
    @Test
    fun `active morning hours have green color`() {
        val style = theme.getHourCellColors(
            hour = 10,
            isSelected = false,
            isActive = true,
            dayOffset = 0
        )
        
        assertEquals(Color.parseColor("#388E3C"), style.backgroundColor)
        assertEquals(Color.WHITE, style.textColor)
    }
    
    @Test
    fun `today border is black`() {
        val border = theme.getTodayBorderStyle()
        
        assertEquals(Color.BLACK, border.color)
        assertEquals(2, border.widthDp)
        assertEquals(4f, border.radiusDp)
    }
    
    @Test
    fun `active task row is yellow`() {
        val style = theme.getTaskRowColors(isActive = true)
        
        assertEquals(Color.parseColor("#FFEB3B"), style.backgroundColor)
        assertEquals(Color.parseColor("#212121"), style.textColor)
    }
    
    @Test
    fun `inactive task row is white`() {
        val style = theme.getTaskRowColors(isActive = false)
        
        assertEquals(Color.parseColor("#FFFFFF"), style.backgroundColor)
    }
    
    @Test
    fun `button has purple color`() {
        val style = theme.getButtonStyle()
        
        assertEquals(Color.parseColor("#673AB7"), style.backgroundColor)
        assertEquals(Color.WHITE, style.textColor)
        assertEquals(20f, style.cornerRadiusDp)
    }
    
    @Test
    fun `background color is light gray`() {
        val bgColor = theme.getBackgroundColor()
        
        assertEquals(Color.parseColor("#FAFAFA"), bgColor)
    }
    
    @Test
    fun `today calendar cell is white with black text`() {
        val style = theme.getCalendarDayColors(
            isToday = true,
            isSelected = false,
            dayOfWeek = 3
        )
        
        assertEquals(Color.parseColor("#FFFFFF"), style.backgroundColor)
        assertEquals(Color.parseColor("#212121"), style.textColor)
    }
}








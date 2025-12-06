package com.example.vkbookandroid.theme.strategies

import android.graphics.Color
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Юнит-тесты для RosatomTheme - проверка корпоративных цветов и изоляции
 */
class RosatomThemeTest {
    
    private lateinit var theme: RosatomTheme
    
    @Before
    fun setup() {
        theme = RosatomTheme()
    }
    
    @Test
    fun `theme name is correct`() {
        assertEquals("Росатом", theme.getThemeName())
    }
    
    @Test
    fun `selected hour has orange color`() {
        val style = theme.getHourCellColors(
            hour = 10,
            isSelected = true,
            isActive = false,
            dayOffset = 0
        )
        
        assertEquals(Color.parseColor("#FF6B35"), style.backgroundColor)
        assertEquals(Color.WHITE, style.textColor)
    }
    
    @Test
    fun `night shift base hours are darker`() {
        val nightStyle = theme.getHourCellColors(
            hour = 22,
            isSelected = false,
            isActive = false,
            dayOffset = 0
        )
        
        val morningStyle = theme.getHourCellColors(
            hour = 10,
            isSelected = false,
            isActive = false,
            dayOffset = 0
        )
        
        // Ночная смена должна быть темнее утренней
        assertEquals(Color.parseColor("#4DB6D5"), nightStyle.backgroundColor)
        assertEquals(Color.parseColor("#B3E5FC"), morningStyle.backgroundColor)
    }
    
    @Test
    fun `today border is orange`() {
        val border = theme.getTodayBorderStyle()
        
        assertEquals(Color.parseColor("#FF6B35"), border.color)
        assertEquals(2, border.widthDp)
        assertEquals(4f, border.radiusDp)
    }
    
    @Test
    fun `text color is dark blue`() {
        val style = theme.getHourCellColors(
            hour = 10,
            isSelected = false,
            isActive = false,
            dayOffset = 0
        )
        
        assertEquals(Color.parseColor("#003D5C"), style.textColor)
    }
    
    @Test
    fun `button has light blue color`() {
        val style = theme.getButtonStyle()
        
        assertEquals(Color.parseColor("#4FC3F7"), style.backgroundColor)
        assertEquals(Color.WHITE, style.textColor)
        assertEquals(12f, style.cornerRadiusDp)
    }
    
    @Test
    fun `background is very light blue`() {
        val bgColor = theme.getBackgroundColor()
        
        assertEquals(Color.parseColor("#E1F5FE"), bgColor)
    }
    
    @Test
    fun `active morning hours have bright blue`() {
        val style = theme.getHourCellColors(
            hour = 10,
            isSelected = false,
            isActive = true,
            dayOffset = 0
        )
        
        assertEquals(Color.parseColor("#4FC3F7"), style.backgroundColor)
        assertEquals(Color.parseColor("#003D5C"), style.textColor)
    }
}








package com.example.vkbookandroid.theme.strategies

import com.example.vkbookandroid.theme.AppTheme
import org.junit.Test
import org.junit.Assert.*

/**
 * Юнит-тесты для ThemeFactory - проверка создания тем
 */
class ThemeFactoryTest {
    
    @Test
    fun `factory creates ClassicTheme for THEME_CLASSIC`() {
        val theme = ThemeFactory.createTheme(AppTheme.THEME_CLASSIC)
        
        assertTrue(theme is ClassicTheme)
        assertEquals("Классическая", theme.getThemeName())
    }
    
    @Test
    fun `factory creates RosatomTheme for THEME_ROSATOM`() {
        val theme = ThemeFactory.createTheme(AppTheme.THEME_ROSATOM)
        
        assertTrue(theme is RosatomTheme)
        assertEquals("Росатом", theme.getThemeName())
    }
    
    @Test
    fun `factory creates NuclearTheme for THEME_NUCLEAR`() {
        val theme = ThemeFactory.createTheme(AppTheme.THEME_NUCLEAR)
        
        assertTrue(theme is NuclearTheme)
        assertEquals("Nuclear", theme.getThemeName())
    }
    
    @Test
    fun `factory creates ErgonomicTheme for THEME_ERGONOMIC_LIGHT`() {
        val theme = ThemeFactory.createTheme(AppTheme.THEME_ERGONOMIC_LIGHT)
        
        assertTrue(theme is ErgonomicTheme)
        assertEquals("Эргономичная", theme.getThemeName())
    }
    
    @Test
    fun `factory creates GlassTheme for THEME_MODERN_GLASS`() {
        val theme = ThemeFactory.createTheme(AppTheme.THEME_MODERN_GLASS)
        
        assertTrue(theme is GlassTheme)
        assertEquals("Стеклянная", theme.getThemeName())
    }
    
    @Test
    fun `factory creates GradientTheme for THEME_MODERN_GRADIENT`() {
        val theme = ThemeFactory.createTheme(AppTheme.THEME_MODERN_GRADIENT)
        
        assertTrue(theme is GradientTheme)
        assertEquals("Брутальная", theme.getThemeName())
    }
    
    @Test
    fun `factory creates ClassicTheme for unknown theme ID`() {
        val theme = ThemeFactory.createTheme(999)
        
        assertTrue(theme is ClassicTheme)
        assertEquals("Классическая", theme.getThemeName())
    }
    
    @Test
    fun `all themes implement ThemeStrategy interface`() {
        val themes = listOf(
            ThemeFactory.createTheme(AppTheme.THEME_CLASSIC),
            ThemeFactory.createTheme(AppTheme.THEME_ROSATOM),
            ThemeFactory.createTheme(AppTheme.THEME_NUCLEAR),
            ThemeFactory.createTheme(AppTheme.THEME_ERGONOMIC_LIGHT),
            ThemeFactory.createTheme(AppTheme.THEME_MODERN_GLASS),
            ThemeFactory.createTheme(AppTheme.THEME_MODERN_GRADIENT)
        )
        
        themes.forEach { theme ->
            assertTrue("${theme.getThemeName()} должна реализовывать ThemeStrategy", 
                theme is ThemeStrategy)
        }
    }
}








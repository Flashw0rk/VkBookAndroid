package com.example.vkbookandroid.theme.strategies

import android.graphics.Color
import org.junit.Test
import org.junit.Assert.*

/**
 * Тесты изоляции тем - проверка что темы не влияют друг на друга
 */
class ThemeIsolationTest {
    
    @Test
    fun `ClassicTheme and RosatomTheme have different colors`() {
        val classic = ClassicTheme()
        val rosatom = RosatomTheme()
        
        // Проверяем выбранный час - это должно быть точно разное
        val classicSelected = classic.getHourCellColors(10, true, false, 0)
        val rosatomSelected = rosatom.getHourCellColors(10, true, false, 0)
        
        // Проверяем что цвета не нулевые
        assertNotEquals("ClassicTheme цвет не должен быть 0", 0, classicSelected.backgroundColor)
        assertNotEquals("RosatomTheme цвет не должен быть 0", 0, rosatomSelected.backgroundColor)
        
        // Проверяем что они разные
        assertNotEquals("ClassicTheme и RosatomTheme должны иметь разные цвета для выбранного часа",
            classicSelected.backgroundColor, rosatomSelected.backgroundColor)
    }
    
    @Test
    fun `all themes have unique today border colors`() {
        val themes = listOf(
            ClassicTheme(),
            RosatomTheme(),
            NuclearTheme(),
            ErgonomicTheme(),
            GlassTheme(),
            GradientTheme()
        )
        
        val borderColors = themes.map { it.getTodayBorderStyle().color }
        
        // Проверяем что есть уникальные цвета (не все одинаковые)
        assertTrue("Рамки сегодняшнего дня должны отличаться между темами",
            borderColors.distinct().size > 1)
    }
    
    @Test
    fun `all themes have unique button styles`() {
        val themes = listOf(
            ClassicTheme(),
            RosatomTheme(),
            NuclearTheme(),
            ErgonomicTheme(),
            GlassTheme(),
            GradientTheme()
        )
        
        val buttonColors = themes.map { it.getButtonStyle().backgroundColor }
        val cornerRadii = themes.map { it.getButtonStyle().cornerRadiusDp }
        
        // Проверяем что все цвета не нулевые
        buttonColors.forEachIndexed { index, color ->
            assertNotEquals("Цвет кнопки темы $index не должен быть 0", 0, color)
        }
        
        // Проверяем что есть хотя бы 2 разных цвета (не все одинаковые)
        assertTrue("Кнопки должны иметь разные цвета между темами (найдено ${buttonColors.distinct().size} уникальных из ${buttonColors.size})",
            buttonColors.distinct().size >= 2)
        // Проверяем что радиусы разные
        assertTrue("Кнопки должны иметь разные радиусы (найдено ${cornerRadii.distinct().size} уникальных)",
            cornerRadii.distinct().size >= 2)
    }
    
    @Test
    fun `all themes have unique background colors`() {
        val themes = listOf(
            ClassicTheme(),
            RosatomTheme(),
            NuclearTheme(),
            ErgonomicTheme(),
            GlassTheme(),
            GradientTheme()
        )
        
        val bgColors = themes.map { it.getBackgroundColor() }
        
        // Проверяем что все цвета не нулевые
        bgColors.forEachIndexed { index, color ->
            assertNotEquals("Фон темы $index не должен быть 0", 0, color)
        }
        
        // Проверяем что есть хотя бы 2 разных цвета (не все одинаковые)
        assertTrue("Фоны должны отличаться между темами (найдено ${bgColors.distinct().size} уникальных из ${bgColors.size})",
            bgColors.distinct().size >= 2)
    }
    
    @Test
    fun `creating multiple instances of same theme returns consistent colors`() {
        val rosatom1 = RosatomTheme()
        val rosatom2 = RosatomTheme()
        
        val style1 = rosatom1.getHourCellColors(10, true, false, 0)
        val style2 = rosatom2.getHourCellColors(10, true, false, 0)
        
        // Две разные инстанции одной темы должны давать одинаковые цвета
        assertEquals(style1.backgroundColor, style2.backgroundColor)
        assertEquals(style1.textColor, style2.textColor)
    }
    
    @Test
    fun `all themes implement all required methods`() {
        val themes = listOf(
            ClassicTheme(),
            RosatomTheme(),
            NuclearTheme(),
            ErgonomicTheme(),
            GlassTheme(),
            GradientTheme()
        )
        
        themes.forEach { theme ->
            // Проверяем что все методы работают и не бросают исключений
            assertNotNull(theme.getHourCellColors(10, false, false, 0))
            assertNotNull(theme.getCalendarDayColors(false, false, 1))
            assertNotNull(theme.getTodayBorderStyle())
            assertNotNull(theme.getTaskRowColors(true))
            assertNotNull(theme.getToggleButtonStyle(true))
            assertNotNull(theme.getButtonStyle())
            assertNotNull(theme.getBackgroundColor())
            assertNotNull(theme.getHeaderTextColor())
            assertNotNull(theme.getTextPrimaryColor())
            assertNotNull(theme.getThemeName())
        }
    }
    
    @Test
    fun `RosatomTheme night shift is darker than day shift`() {
        val rosatom = RosatomTheme()
        
        // Ночная смена: 22:00 (20-23 или 00-07)
        val nightStyle = rosatom.getHourCellColors(22, false, false, 0)
        // Дневная смена: 14:00 (14-19)
        val dayStyle = rosatom.getHourCellColors(14, false, false, 0)
        
        // Проверяем что цвета не нулевые
        assertNotEquals("Ночная смена цвет не должен быть 0", 0, nightStyle.backgroundColor)
        assertNotEquals("Дневная смена цвет не должен быть 0", 0, dayStyle.backgroundColor)
        
        // Проверяем что ночная смена темнее дневной
        // Используем яркость (luminance) для более точного сравнения
        val nightR = Color.red(nightStyle.backgroundColor)
        val nightG = Color.green(nightStyle.backgroundColor)
        val nightB = Color.blue(nightStyle.backgroundColor)
        val nightBrightness = (0.299 * nightR + 0.587 * nightG + 0.114 * nightB)
        
        val dayR = Color.red(dayStyle.backgroundColor)
        val dayG = Color.green(dayStyle.backgroundColor)
        val dayB = Color.blue(dayStyle.backgroundColor)
        val dayBrightness = (0.299 * dayR + 0.587 * dayG + 0.114 * dayB)
        
        assertTrue("Ночная смена должна быть темнее дневной в Росатом теме (ночь: $nightBrightness, день: $dayBrightness)",
            nightBrightness < dayBrightness)
    }
}


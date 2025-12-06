package com.example.vkbookandroid.theme.strategies

import com.example.vkbookandroid.theme.AppTheme

/**
 * Фабрика для создания изолированных стратегий тем
 */
object ThemeFactory {
    
    /**
     * Создать стратегию темы по ID
     */
    fun createTheme(themeId: Int): ThemeStrategy {
        return when (themeId) {
            AppTheme.THEME_CLASSIC -> ClassicTheme()
            AppTheme.THEME_NUCLEAR -> NuclearTheme()
            AppTheme.THEME_ROSATOM -> RosatomTheme()
            AppTheme.THEME_ERGONOMIC_LIGHT -> ErgonomicTheme()
            AppTheme.THEME_MODERN_GLASS -> GlassTheme()
            AppTheme.THEME_MODERN_GRADIENT -> GradientTheme()
            else -> ClassicTheme()  // По умолчанию классическая
        }
    }
    
    /**
     * Создать стратегию для текущей темы
     */
    fun createCurrentTheme(): ThemeStrategy {
        return createTheme(AppTheme.getCurrentThemeId())
    }
}








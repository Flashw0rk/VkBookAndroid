package com.example.vkbookandroid.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.vkbookandroid.R
import com.example.vkbookandroid.theme.AppTheme
import com.example.vkbookandroid.theme.ThemeSelectionDialog

/**
 * Фрагмент настроек программы
 */
class AppSettingsFragment : Fragment() {
    
    private lateinit var btnSelectTheme: Button
    private lateinit var tvCurrentTheme: TextView
    private lateinit var btnTabSettings: Button
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.settings_tab_app, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Инициализация элементов
        btnSelectTheme = view.findViewById(R.id.btnSelectTheme)
        tvCurrentTheme = view.findViewById(R.id.tvCurrentTheme)
        btnTabSettings = view.findViewById(R.id.btnTabSettings)
        
        // Загружаем текущую тему
        AppTheme.loadTheme(requireContext())
        updateCurrentThemeText()
        
        // Обработчик выбора темы
        btnSelectTheme.setOnClickListener {
            showThemeSelector()
        }

        btnTabSettings.setOnClickListener {
            (activity as? SettingsTabsActivity)?.openTabSettings()
        }
    }
    
    private fun showThemeSelector() {
        ThemeSelectionDialog(requireContext()) { selectedThemeId ->
            // Сохраняем выбранную тему
            AppTheme.saveTheme(requireContext(), selectedThemeId)
            updateCurrentThemeText()
            
            // Уведомляем Activity о смене темы
            (activity as? SettingsTabsActivity)?.onThemeChanged()
        }.show()
    }
    
    private fun updateCurrentThemeText() {
        val currentThemeId = AppTheme.getCurrentThemeId()
        tvCurrentTheme.text = AppTheme.getThemeName(currentThemeId)
    }
    
    /**
     * Получить кнопку настройки вкладок для Activity
     */
    fun getTabSettingsButton(): Button = btnTabSettings
}


package com.example.vkbookandroid.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.vkbookandroid.R
import com.example.vkbookandroid.ServerSettingsActivity
import com.example.vkbookandroid.theme.AppTheme
import com.example.vkbookandroid.theme.ThemeHelper
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * Activity с вкладками настроек
 * Легковесная реализация без создания лишних объектов
 */
class SettingsTabsActivity : AppCompatActivity() {
    
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var tvTitle: TextView
    
    private var connectionFragment: ConnectionSettingsFragment? = null
    private var appFragment: AppSettingsFragment? = null
    private var themeChanged: Boolean = false
    
    // Счетчик тапов для Easter egg
    private var programTabTapCount: Int = 0
    private var lastTapTime: Long = 0
    private val TAP_TIMEOUT_MS = 2000L // 2 секунды между тапами
    private val REQUIRED_TAPS = 8

    private val fragmentLifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(
            fm: FragmentManager,
            f: Fragment,
            v: android.view.View,
            savedInstanceState: Bundle?
        ) {
            if (f is ConnectionSettingsFragment) {
                connectionFragment = f
                f.setupSecretPasswordTrigger(tvTitle)
            }
        }

        override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
            if (f is ConnectionSettingsFragment && connectionFragment === f) {
                connectionFragment = null
            }
            if (f is AppSettingsFragment && appFragment === f) {
                appFragment = null
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Загружаем тему
        AppTheme.loadTheme(this)
        applyTheme()
        
        setContentView(R.layout.activity_server_settings_new)
        
        // Инициализация элементов
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)
        btnSave = findViewById(R.id.btnSaveSettings)
        btnCancel = findViewById(R.id.btnCancel)
        tvTitle = findViewById(R.id.tvSettingsTitle)
        
        setupViewPager()
        setupButtons()
        applyThemeToUI()

        // Регистрируем обработчик кнопки "Назад" (новый API)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithResult(false)
            }
        })

        supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, true)
        connectionFragment?.setupSecretPasswordTrigger(tvTitle)
    }
    
    private fun setupViewPager() {
        // Создаем адаптер для ViewPager2
        val adapter = SettingsPagerAdapter(this)
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = 2
        
        // Связываем TabLayout с ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "⚙️ Программа"  // ПОМЕНЯЛИ МЕСТАМИ!
                1 -> "🌐 Подключение"
                else -> ""
            }
        }.attach()
        
        // Обработчик тапов на вкладку "Программа" для Easter egg
        setupProgramTabEasterEgg()
        
        // ПО УМОЛЧАНИЮ открываем вкладку "Программа" (теперь индекс 0)
        viewPager.setCurrentItem(0, false)
    }
    
    private fun setupButtons() {
        btnCancel.setOnClickListener {
            finishWithResult(false)
        }
        
        btnSave.setOnClickListener {
            // Сохраняем настройки подключения
            connectionFragment?.saveSettings()
            
            // КРИТИЧНО: Принудительно сохраняем тему и уведомляем MainActivity
            AppTheme.saveTheme(this, AppTheme.getCurrentThemeId())
            finishWithResult(true)
        }
    }

    fun openTabSettings() {
        connectionFragment?.openTabSettings()
            ?: android.widget.Toast.makeText(this, "Настройки вкладок недоступны", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Настройка Easter egg: 8 тапов по вкладке "Программа" для показа информации об авторе
     * 
     * ВАЖНО: Используем OnTabReselected для обработки повторных тапов на уже выбранную вкладку,
     * чтобы не конфликтовать с TabLayoutMediator и не блокировать стандартное переключение вкладок.
     */
    private fun setupProgramTabEasterEgg() {
        // Обрабатываем тапы через OnTabReselected - это срабатывает при тапе на уже выбранную вкладку
        // и НЕ конфликтует с TabLayoutMediator, который обрабатывает переключение через ViewPager2
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                // Сбрасываем счетчик при переключении на другую вкладку
                if (tab?.position != 0) {
                    programTabTapCount = 0
                }
            }
            
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                // Обрабатываем повторный тап на вкладку "Программа" (индекс 0)
                if (tab?.position == 0) {
                    val currentTime = System.currentTimeMillis()
                    
                    // Сбрасываем счетчик если прошло больше 2 секунд с последнего тапа
                    if (currentTime - lastTapTime > TAP_TIMEOUT_MS) {
                        programTabTapCount = 0
                    }
                    
                    lastTapTime = currentTime
                    programTabTapCount++
                    
                    // Если достигли 8 тапов, показываем диалог
                    if (programTabTapCount >= REQUIRED_TAPS) {
                        programTabTapCount = 0 // Сбрасываем счетчик
                        showAuthorInfoDialog()
                    }
                }
            }
        })
    }
    
    /**
     * Показать диалог с информацией об авторе
     */
    private fun showAuthorInfoDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("О разработчике")
            .setMessage("Идея и разработка: Плющик Станислав Викторович")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }
    
    /**
     * Применение темы к Activity
     */
    private fun applyTheme() {
        if (!AppTheme.shouldApplyTheme()) {
            // КЛАССИЧЕСКАЯ ТЕМА - исходные цвета!
            window.decorView.background = null
            window.decorView.setBackgroundColor(android.graphics.Color.parseColor("#FAFAFA"))
            window.statusBarColor = android.graphics.Color.parseColor("#1976D2")
            return
        }
        
        window.statusBarColor = AppTheme.getPrimaryColor()
        
        // Сначала применяем цвет фона (быстро)
        window.decorView.setBackgroundColor(AppTheme.getBackgroundColor())
        
        // Затем асинхронно загружаем фоновое изображение (если есть)
        lifecycleScope.launch(Dispatchers.IO) {
            val bgDrawable = AppTheme.getBackgroundDrawable(this@SettingsTabsActivity)
            if (bgDrawable != null) {
                withContext(Dispatchers.Main) {
                    window.decorView.background = bgDrawable
                }
            }
        }
    }
    
    /**
     * Применить тему ко всем UI элементам
     */
    private fun applyThemeToUI() {
        // Заголовок - применяем для всех тем
        tvTitle.setTextColor(AppTheme.getTextPrimaryColor())
        
        // Применяем размеры кнопок ДЛЯ ВСЕХ ТЕМ (включая классическую)
        val px = btnSave.context.resources.displayMetrics.density
        val paddingH = 8 * px.toInt()
        val paddingV = 16 * px.toInt()
        btnSave.setPadding(paddingH, paddingV, paddingH, paddingV)
        btnCancel.setPadding(paddingH, paddingV, paddingH, paddingV)
        btnSave.minHeight = 0
        btnSave.minWidth = 0
        btnCancel.minHeight = 0
        btnCancel.minWidth = 0
        
        // НЕ ПРИМЕНЯЕМ цвета и фоны если классическая
        if (!AppTheme.shouldApplyTheme()) return
        
        // Кнопки (ОВАЛЬНЫЕ с полным профессиональным стилем)
        // Используем gradient для Росатома и Брутальной темы
        val saveDrawable = AppTheme.createGradientButtonDrawable() 
            ?: AppTheme.createButtonDrawable(AppTheme.getPrimaryColor())
        saveDrawable?.let { btnSave.background = it }
        AppTheme.applyButtonStyle(btnSave)
        
        val cancelDrawable = AppTheme.createButtonDrawable(AppTheme.getTextSecondaryColor())
        cancelDrawable?.let { btnCancel.background = it }
        AppTheme.applyButtonStyle(btnCancel)
        
        // TabLayout
        tabLayout.setSelectedTabIndicatorColor(AppTheme.getPrimaryColor())
        tabLayout.setTabTextColors(AppTheme.getTextSecondaryColor(), AppTheme.getPrimaryColor())
    }
    
    /**
     * Вызывается при смене темы из AppSettingsFragment
     */
    fun onThemeChanged() {
        themeChanged = true
        AppTheme.loadTheme(this)
        applyTheme()
        applyThemeToUI()
        appFragment?.view?.let { ThemeHelper.applyThemeToFragment(appFragment!!, it) }
        connectionFragment?.view?.let { ThemeHelper.applyThemeToFragment(connectionFragment!!, it) }
        connectionFragment?.setupSecretPasswordTrigger(tvTitle)
        
        // Логируем смену темы
        val themeName = AppTheme.getThemeName(AppTheme.getCurrentThemeId())
        com.example.vkbookandroid.analytics.AnalyticsManager.logThemeChanged(themeName)
    }

    private fun finishWithResult(saved: Boolean) {
        val intent = Intent()
        if (themeChanged) {
            intent.putExtra("THEME_CHANGED", true)
        }
        if (saved || themeChanged) {
            setResult(RESULT_OK, intent)
        } else {
            setResult(RESULT_CANCELED)
        }
        super.finish()
    }

    override fun onDestroy() {
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentLifecycleCallbacks)
        super.onDestroy()
    }
    
    /**
     * Адаптер для ViewPager2 (внутренний класс для производительности)
     */
    private inner class SettingsPagerAdapter(activity: AppCompatActivity) : 
        FragmentStateAdapter(activity) {
        
        override fun getItemCount(): Int = 2
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> {
                    val fragment = AppSettingsFragment()
                    appFragment = fragment
                    fragment
                }
                1 -> {
                    val fragment = ConnectionSettingsFragment()
                    connectionFragment = fragment
                    fragment
                }
                else -> Fragment()
            }
        }
    }
}


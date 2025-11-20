package com.example.vkbookandroid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.example.pult.android.DataFragment
import android.os.StrictMode
import android.content.pm.ApplicationInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.vkbookandroid.service.SyncService
import com.example.vkbookandroid.network.NetworkModule
import com.example.vkbookandroid.utils.AutoSyncSettings
import com.example.vkbookandroid.security.AdminAccessManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var pagerAdapter: MainPagerAdapter
    private var sharedSearchQuery: String = ""
    private var isAdminMode = false
    
    // Синхронизация
    private lateinit var btnSync: Button
    private lateinit var btnSettings: android.widget.ImageButton
    private lateinit var tvSyncStatus: TextView
    private lateinit var syncService: SyncService
    private lateinit var dataRefreshManager: DataRefreshManager
    private var syncJob: Job? = null
    private var isWaitingServerWake = false
    private var syncButtonDefaultText: String = ""
    private val uiJob = SupervisorJob()
    private val uiScope = CoroutineScope(Dispatchers.Main + uiJob)
    private val navigationPrefs by lazy { getSharedPreferences(NAVIGATION_PREFS, MODE_PRIVATE) }
    
    // Состояние инициализации
    private var isInitializationComplete = false
    private val initializationListeners = mutableListOf<() -> Unit>()
    
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        android.util.Log.d("MainActivity", "=== settingsLauncher получил результат ===")
        android.util.Log.d("MainActivity", "resultCode=${result.resultCode}, RESULT_OK=$RESULT_OK")
        android.util.Log.d("MainActivity", "THEME_CHANGED=${result.data?.getBooleanExtra("THEME_CHANGED", false)}")
        
        val themeChanged = result.resultCode == RESULT_OK &&
            (result.data?.getBooleanExtra("THEME_CHANGED", false) == true)
        
        android.util.Log.d("MainActivity", "themeChanged=$themeChanged")

        // Обновляем настройки сервера после возврата из настроек
        loadServerSettings()
        // Применяем настройки вкладок и корректируем UI
        try { applyTabsVisibility() } catch (_: Throwable) {}

        if (themeChanged) {
            android.util.Log.d("MainActivity", "=== ТЕМА ИЗМЕНЕНА, обновляем ===")
            
            // Загружаем новую тему
            loadThemeConfiguration()
            
            // Применяем тему к MainActivity (внутри уже вызывается refreshAllFragmentsTheme)
            applyCurrentTheme()
            
            return@registerForActivityResult
        }
        
        // ПРОВЕРЯЕМ НАСТРОЙКИ АВТОСИНХРОНИЗАЦИИ
        if (AutoSyncSettings.isSyncOnSettingsChangeEnabled(this@MainActivity)) {
            // Автосинхронизация при изменении настроек ВКЛЮЧЕНА
            checkConnectionOnStartup()
            // Можно добавить автоматическую синхронизацию здесь, если нужно
        } else {
            // Только проверка соединения без синхронизации
            checkConnectionOnStartup()
        }
    }
    
    /**
     * Добавляет слушатель, который будет вызван после завершения инициализации
     */
    fun addInitializationListener(listener: () -> Unit) {
        if (isInitializationComplete) {
            listener()
        } else {
            initializationListeners.add(listener)
        }
    }
    
    /**
     * Проверяет, завершена ли инициализация
     */
    fun isInitializationComplete(): Boolean = isInitializationComplete
    
    /**
     * Уведомляет о завершении инициализации
     */
    private fun notifyInitializationComplete() {
        isInitializationComplete = true
        initializationListeners.forEach { it() }
        initializationListeners.clear()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Инициализируем HTTP кэш для NetworkModule
        com.example.vkbookandroid.network.NetworkModule.initCache(this)
        
        // Включаем StrictMode только в debug для раннего обнаружения медленных операций на UI-потоке
        // НО отключаем detectNetwork(), так как это может блокировать сетевые запросы
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    // .detectNetwork() // Отключаем, чтобы не блокировать сетевые запросы
                    .penaltyLog()
                    .build()
            )
        }
        
        // Загружаем тему перед созданием интерфейса
        loadThemeConfiguration()
        
        setContentView(R.layout.activity_main)
        
        // Применяем тему к окну
        if (com.example.vkbookandroid.theme.AppTheme.shouldApplyTheme()) {
            window.statusBarColor = com.example.vkbookandroid.theme.AppTheme.getPrimaryColor()
            
            // Асинхронная предзагрузка фона для темы Атом
            if (com.example.vkbookandroid.theme.AppTheme.isNuclearTheme()) {
                uiScope.launch(Dispatchers.IO) {
                    com.example.vkbookandroid.theme.AppTheme.getBackgroundDrawable(this@MainActivity)
                }
            }
        }
        // Глобально отключаем звуки кликов у корневого layout
        findViewById<android.view.View>(android.R.id.content)?.let { root ->
            root.isSoundEffectsEnabled = false
        }
        
        // Инициализация ViewPager2 и TabLayout
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        viewPager.isUserInputEnabled = false // Отключаем свайп между вкладками
        
        // Инициализация DataRefreshManager
        dataRefreshManager = DataRefreshManager(this)
        
        // Инициализация синхронизации
        btnSync = findViewById(R.id.btnSync)
        syncButtonDefaultText = btnSync.text.toString()
        btnSettings = findViewById(R.id.btnSettings)
        tvSyncStatus = findViewById(R.id.tvSyncStatus)
        
        // Загружаем настройки сервера ПЕРЕД созданием SyncService
        loadServerSettings()
        syncService = SyncService(this)
        
        setupSyncButton()
        setupSettingsButton()
        setupHashInfoButton()
        
        // Инициализируем ViewPager2 сразу для быстрого отображения UI
        initializeViewPager()
        
        // КРИТИЧНО: Настраиваем отслеживание файлов для автоматического обновления
        setupFileWatching()
        
        // ПРОВЕРЯЕМ НАСТРОЙКИ АВТОСИНХРОНИЗАЦИИ ПРИ ЗАПУСКЕ
        if (AutoSyncSettings.isSyncOnStartupEnabled(this)) {
            // Автосинхронизация при запуске ВКЛЮЧЕНА
            checkConnectionOnStartup()
            initializeAndCheckUpdates() // Старая функция с синхронизацией
        } else {
            // Автосинхронизация при запуске ОТКЛЮЧЕНА (по умолчанию)
            checkConnectionOnStartup() // Только проверка соединения
            initializeBasicFiles() // Только базовые файлы без синхронизации
        }
        
        // УБРАНО: удаление PDF при запуске

        // ПРОВЕРЯЕМ НАСТРОЙКИ ФОНОВОЙ СИНХРОНИЗАЦИИ
        if (AutoSyncSettings.isBackgroundSyncEnabled(this)) {
            // Фоновая синхронизация ВКЛЮЧЕНА - планируем WorkManager
            schedulePeriodicBackgroundSync()
        } else {
            // Фоновая синхронизация ОТКЛЮЧЕНА (по умолчанию)
            // Отменяем все запланированные задачи
            WorkManager.getInstance(this).cancelUniqueWork("vkbook_periodic_sync")
            Log.i("MainActivity", "Background sync is DISABLED - cancelled all scheduled work")
        }

        // Добавляем пункт "О приложении" в меню (через уже существующую иконку меню)

        // Инициализация аналитики (безопасно, работает без Firebase)
        com.example.vkbookandroid.analytics.AnalyticsManager.initialize(this)
        com.example.vkbookandroid.analytics.CrashlyticsManager.initialize()
        com.example.vkbookandroid.analytics.RemoteConfigManager.initialize(this)
    }
    
    override fun onResume() {
        super.onResume()
        
        android.util.Log.d("MainActivity", "=== onResume() вызван ===")
        
        val adminChanged = updateAdminModeState()
        
        // Перезагружаем тему (если изменилась в настройках)
        loadThemeConfiguration()
        android.util.Log.d("MainActivity", "Тема загружена: ${com.example.vkbookandroid.theme.AppTheme.getCurrentThemeId()}")
        
        // Применяем тему (внутри вызывается refreshAllFragmentsTheme)
        applyCurrentTheme()
        
        // ВАЖНО: Дополнительно обновляем текущую видимую вкладку через ThemeManager
        if (::pagerAdapter.isInitialized && ::viewPager.isInitialized) {
            window.decorView.postDelayed({
                val currentItem = viewPager.currentItem
                val tag = "f$currentItem"
                val fragment = supportFragmentManager.findFragmentByTag(tag)
                
                android.util.Log.d("MainActivity", "onResume: дополнительное обновление текущей вкладки #$currentItem")
                
                if (fragment != null) {
                    com.example.vkbookandroid.theme.ThemeManager.applyThemeToFragment(fragment, forceUpdate = true)
                }
            }, 100) // Небольшая задержка для гарантии готовности UI
        }
        
        // После ротации гарантируем, что видимая вкладка подгружена и отрисована
        // Проверяем, что ViewPager2 уже инициализирован
        if (::pagerAdapter.isInitialized) {
            if (adminChanged) {
                // ВАЖНО: Обновляем видимость вкладок при изменении режима администратора
                try {
                    applyTabsVisibility()
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error applying tabs visibility in onResume", e)
                }
            }
            
            ensureCurrentTabLoaded()
            // УЛУЧШЕНИЕ: Применяем поиск к текущей вкладке при возврате в приложение
            if (sharedSearchQuery.isNotEmpty()) {
                applySharedSearchToCurrentTab(viewPager.currentItem)
            }
        }
    }

    /**
     * Применить текущую тему ко всему интерфейсу
     */
    private fun applyCurrentTheme() {
        if (!com.example.vkbookandroid.theme.AppTheme.shouldApplyTheme()) {
            // КЛАССИЧЕСКАЯ ТЕМА - ВОССТАНАВЛИВАЕМ ИСХОДНЫЙ ВИД!
            window.decorView.background = null
            window.decorView.setBackgroundColor(android.graphics.Color.parseColor("#FAFAFA"))
            window.statusBarColor = android.graphics.Color.parseColor("#1976D2")
            
            // КРИТИЧНО: Восстанавливаем ИСХОДНУЮ кнопку (из XML - уже зеленая и овальная)!
            if (::btnSync.isInitialized) {
                // НЕ ТРОГАЕМ - кнопка уже правильная из activity_main.xml!
                // Там уже: backgroundTint="#4CAF50" и овальная форма
                // Просто сбрасываем если что-то изменилось
                btnSync.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50"))
                btnSync.setTextColor(android.graphics.Color.WHITE)
            }
            
            // Обновляем фрагменты (они сами восстановят исходный вид)
            refreshAllFragmentsTheme()
            return
        }
        
        // ДРУГИЕ ТЕМЫ - применяем стили
        // Сначала применяем цвет фона (быстро)
        window.decorView.setBackgroundColor(com.example.vkbookandroid.theme.AppTheme.getBackgroundColor())
        window.statusBarColor = com.example.vkbookandroid.theme.AppTheme.getPrimaryColor()
        
        // Затем асинхронно загружаем фоновое изображение (если есть)
        uiScope.launch(Dispatchers.IO) {
            val bgDrawable = com.example.vkbookandroid.theme.AppTheme.getBackgroundDrawable(this@MainActivity)
            if (bgDrawable != null) {
                withContext(Dispatchers.Main) {
                    window.decorView.background = bgDrawable
                }
            }
        }
        
        // Кнопки (ОВАЛЬНЫЕ с градиентом для некоторых тем)
        if (::btnSync.isInitialized) {
            // Сбрасываем backgroundTint который перекрывает background
            btnSync.backgroundTintList = null
            com.example.vkbookandroid.theme.AppTheme.applyButtonStyle(btnSync)
        }
        
        // Обновляем все загруженные фрагменты
        refreshAllFragmentsTheme()
    }
    
    /**
     * Обновить тему во всех загруженных фрагментах
     * ПРОФЕССИОНАЛЬНЫЙ ПОДХОД: Используем централизованный ThemeManager
     */
    private fun refreshAllFragmentsTheme() {
        android.util.Log.d("MainActivity", "=== refreshAllFragmentsTheme через ThemeManager ===")
        
        // Используем ThemeManager для централизованного управления темами
        com.example.vkbookandroid.theme.ThemeManager.applyThemeToAllFragments()
        
        android.util.Log.d("MainActivity", "Зарегистрировано фрагментов: ${com.example.vkbookandroid.theme.ThemeManager.getRegisteredFragmentsCount()}")
    }
    
    private fun ensureCurrentTabLoaded() {
        if (::pagerAdapter.isInitialized) {
            ensureTabLoaded(viewPager.currentItem)
        }
    }

    private fun ensureTabLoaded(position: Int) {
        if (!::pagerAdapter.isInitialized) return
        val adapter = pagerAdapter
        val tag = "f$position"
        val frag = supportFragmentManager.findFragmentByTag(tag)
        val globalPosition = adapter.getGlobalPositionAt(position)
        
        android.util.Log.d("MainActivity", "ensureTabLoaded(local=$position, global=$globalPosition): фрагмент=${frag?.javaClass?.simpleName}")
        
        when (frag) {
            is org.example.pult.android.DataFragment -> frag.ensureDataLoaded()
            is com.example.vkbookandroid.ArmatureFragment -> frag.ensureDataLoaded()
            is com.example.vkbookandroid.ChecksScheduleFragment -> frag.ensureDataLoaded()
            is com.example.vkbookandroid.SchemesFragment -> frag.ensureDataLoaded()
            is com.example.vkbookandroid.EditorFragment -> frag.ensureDataLoaded()
            is com.example.vkbookandroid.ScheduleFragment -> frag.ensureDataLoaded()
            else -> {
                android.util.Log.w("MainActivity", "Неизвестный тип фрагмента на позиции $position (global=$globalPosition)")
                when (globalPosition) {
                    0 -> (getFragmentByGlobalPosition(0) as? org.example.pult.android.DataFragment)?.ensureDataLoaded()
                    1 -> (getFragmentByGlobalPosition(1) as? com.example.vkbookandroid.ArmatureFragment)?.ensureDataLoaded()
                    2 -> (getFragmentByGlobalPosition(2) as? com.example.vkbookandroid.SchemesFragment)?.ensureDataLoaded()
                    3 -> (getFragmentByGlobalPosition(3) as? com.example.vkbookandroid.EditorFragment)?.ensureDataLoaded()
                    4 -> (getFragmentByGlobalPosition(4) as? com.example.vkbookandroid.ScheduleFragment)?.ensureDataLoaded()
                    5 -> (getFragmentByGlobalPosition(5) as? com.example.vkbookandroid.ChecksScheduleFragment)?.ensureDataLoaded()
                }
            }
        }
    }

    fun onFragmentSearchQueryChanged(query: String) {
        if (query == sharedSearchQuery) return
        sharedSearchQuery = query
        applySharedSearchToFragments()
    }

    private fun applySharedSearchToFragments() {
        if (!::pagerAdapter.isInitialized) return
        
        if (isAdminMode) {
            (getFragmentByGlobalPosition(0) as? org.example.pult.android.DataFragment)
                ?.setSearchQueryExternal(sharedSearchQuery)
        }
        (getFragmentByGlobalPosition(1) as? com.example.vkbookandroid.ArmatureFragment)
            ?.setSearchQueryExternal(sharedSearchQuery)
    }
    
    /**
     * Применяет общий поисковый запрос к конкретной вкладке
     */
    private fun applySharedSearchToCurrentTab(position: Int) {
        if (!::pagerAdapter.isInitialized) {
            Log.w("MainActivity", "PagerAdapter not initialized, cannot apply search")
            return
        }
        
        if (sharedSearchQuery.isBlank()) {
            Log.d("MainActivity", "Shared search query is blank, skipping")
            return
        }
        
        Log.d("MainActivity", "Applying shared search '$sharedSearchQuery' to tab $position")
        
        val adapter = viewPager.adapter as? MainPagerAdapter ?: return
        if (position >= adapter.getVisiblePositions().size) {
            Log.w("MainActivity", "applySharedSearchToCurrentTab: position $position out of bounds")
            return
        }
        val globalPosition = adapter.getGlobalPositionAt(position)
        when (globalPosition) {
            0 -> {
                if (!isAdminMode) {
                    Log.w("MainActivity", "Admin mode disabled, skipping DataFragment search apply")
                    return
                }
                Log.d("MainActivity", "=== SEARCHING IN TAB (global 0): DataFragment (БЩУ сигналы) ===")
                val fragment = getFragmentByGlobalPosition(0) as? org.example.pult.android.DataFragment
                if (fragment != null) {
                    Log.d("MainActivity", "Found DataFragment, applying search")
                    fragment.setSearchQueryExternal(sharedSearchQuery)
                } else {
                    Log.w("MainActivity", "DataFragment not found for global tab $globalPosition")
                }
            }
            1 -> {
                Log.d("MainActivity", "=== SEARCHING IN TAB (global 1): ArmatureFragment (Арматура) ===")
                val fragment = getFragmentByGlobalPosition(1) as? com.example.vkbookandroid.ArmatureFragment
                if (fragment != null) {
                    Log.d("MainActivity", "Found ArmatureFragment, applying search")
                    fragment.setSearchQueryExternal(sharedSearchQuery)
                } else {
                    Log.w("MainActivity", "ArmatureFragment not found for global tab $globalPosition")
                }
            }
            2 -> {
                // Вкладка "Схемы" - пока не поддерживает поиск
                Log.d("MainActivity", "Schemes tab doesn't support search yet")
            }
        }
    }
    
    /**
     * Настройка кнопки синхронизации
     */
    private fun setupSyncButton() {
        // Добавляем подсказку
        btnSync.setOnLongClickListener {
            Toast.makeText(this, "Обновление баз данных с сервера", Toast.LENGTH_SHORT).show()
            true
        }
        
        // РУЧНАЯ СИНХРОНИЗАЦИЯ - автообновления отключены
        // Добавляем диагностику по двойному тапу
        var lastTapTime = 0L
        btnSync.setOnClickListener {
            if (isWaitingServerWake) {
                cancelPendingSync()
                return@setOnClickListener
            }
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < 500) {
                diagnoseFiles()
                Toast.makeText(this, "Диагностика файлов выполнена. Смотрите логи.", Toast.LENGTH_LONG).show()
            } else {
                startSync()
            }
            lastTapTime = currentTime
        }
    }
    
    private fun setupSettingsButton() {
        btnSettings.setOnClickListener {
            val intent = android.content.Intent(this, com.example.vkbookandroid.settings.SettingsTabsActivity::class.java)
            settingsLauncher.launch(intent)
        }
    }
    
    private fun setupHashInfoButton() {
        // Добавляем длинное нажатие на кнопку синхронизации для просмотра хешей
        btnSync.setOnLongClickListener {
            val hashDialog = HashInfoDialog.newInstance()
            hashDialog.show(supportFragmentManager, "HashInfoDialog")
            true
        }

        // Убираем скрытую активацию режима разработчика
    }
    
    private fun loadServerSettings() {
        val currentUrl = readServerUrlFromPreferences()
        android.util.Log.d("MainActivity", "loadServerSettings: currentUrl=$currentUrl")
        NetworkModule.updateBaseUrl(currentUrl)
        updateAdminModeState()
        // После возврата из настроек применяем видимость вкладок
        try { applyTabsVisibility() } catch (_: Throwable) {}
    }
    
    private fun checkConnectionOnStartup() {
        // Проверяем соединение при запуске
        uiScope.launch {
            updateSyncStatus("Проверка соединения...")
            val isConnected = withContext(Dispatchers.IO) {
                syncService.checkServerConnection()
            }
            if (isConnected) {
                updateSyncStatus("Готов к обновлению")
                btnSync.isEnabled = true
            } else {
                updateSyncStatus("Сервер недоступен")
                btnSync.isEnabled = false
            }
        }
    }
    
    
    
    /**
     * Запуск РУЧНОЙ синхронизации (автообновления отключены)
     * Пользователь может обновить данные только нажав кнопку "Синхронизация"
     */
    private fun startSync() {
        if (syncJob?.isActive == true) return
        syncJob = uiScope.launch {
            try {
                val serverReady = waitForServerWakeup()
                if (!serverReady) {
                    updateSyncStatus("Сервер недоступен")
                    resetSyncButtonState()
                    return@launch
                }
                
                btnSync.isEnabled = false
                btnSync.text = syncButtonDefaultText
                updateSyncStatus("Ручное обновление...")
                
                val result = withContext(Dispatchers.IO) {
                    syncService.syncAll { percent, type ->
                        withContext(Dispatchers.Main) {
                            updateSyncStatus("[$percent%] $type")
                        }
                    }
                }
                
                when {
                    result.rateLimitReached -> {
                        updateSyncStatus("Лимит запросов достигнут")
                        Toast.makeText(this@MainActivity, "Достигнут лимит запросов к серверу", Toast.LENGTH_LONG).show()
                    }
                    result.overallSuccess -> {
                        updateSyncStatus("Обновление завершено")
                        val updateSummary = result.getUpdateSummary()
                        Toast.makeText(this@MainActivity, updateSummary, Toast.LENGTH_LONG).show()
                        
                        if (result.updatedFiles.isNotEmpty()) {
                            val filesList = result.updatedFiles.joinToString("\n• ", "• ")
                            showUpdateDetailsDialog(updateSummary, filesList)
                        }
                        
                        refreshFragmentsData()
                        diagnoseFilesAfterSync()
                        com.example.vkbookandroid.analytics.AnalyticsManager.logSyncCompleted(result.updatedFiles.size, true)
                    }
                    result.serverConnected -> {
                        updateSyncStatus("Ошибка обновления")
                        val errorMsg = if (result.errorMessages.isNotEmpty()) {
                            "Ошибки: ${result.errorMessages.joinToString(", ")}"
                        } else {
                            "Ошибка при обновлении данных"
                        }
                        Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        updateSyncStatus("Сервер недоступен")
                        Toast.makeText(this@MainActivity, "Сервер недоступен", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ce: CancellationException) {
                updateSyncStatus("Обновление отменено")
            } catch (e: Exception) {
                updateSyncStatus("Ошибка соединения")
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                if (!isWaitingServerWake) {
                    resetSyncButtonState()
                }
                syncJob = null
            }
        }
    }
    
    /**
     * Обновление статуса синхронизации
     */
    private fun updateSyncStatus(status: String) {
        tvSyncStatus.text = status
    }

    private fun resetSyncButtonState() {
        btnSync.isEnabled = true
        btnSync.text = syncButtonDefaultText
    }

    private fun cancelPendingSync() {
        syncJob?.cancel()
        exitServerWarmupState()
        resetSyncButtonState()
        updateSyncStatus("Обновление отменено")
    }

    private fun enterServerWarmupState() {
        if (isWaitingServerWake) return
        isWaitingServerWake = true
        btnSync.text = "Отменить обновление"
        btnSync.isEnabled = true
    }

    private fun exitServerWarmupState() {
        if (!isWaitingServerWake) return
        isWaitingServerWake = false
        btnSync.text = syncButtonDefaultText
    }

    private suspend fun waitForServerWakeup(): Boolean {
        updateSyncStatus("Проверяем соединение...")
        val readyImmediately = withContext(Dispatchers.IO) { syncService.checkServerConnection() }
        if (readyImmediately) {
            return true
        }
        enterServerWarmupState()
        return try {
            repeat(6) {
                if (!currentCoroutineContext().isActive) return false
                updateSyncStatus("Происходит включение сервера, базы данных скоро обновятся.")
                delay(5000)
                if (!currentCoroutineContext().isActive) return false
                val isReady = withContext(Dispatchers.IO) { syncService.checkServerConnection() }
                if (isReady) {
                    updateSyncStatus("Сервер активен, начинаем обновление…")
                    return true
                }
            }
            false
        } finally {
            exitServerWarmupState()
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_more -> {
                showMoreMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Диалог "О приложении" с версией и кнопкой копирования.
     */
    private fun openAboutScreen() {
        startActivity(android.content.Intent(this, AboutActivity::class.java))
    }

    /**
     * Короткое меню с действием "О приложении" и быстрым доступом к синку.
     * Без техдеталей для пользователя.
     */
    private fun showMoreMenu() {
        val items = arrayOf(
            "О приложении",
            "Проверить обновления"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(items) { d, which ->
                when (which) {
                    0 -> openAboutScreen()
                    1 -> startSync()
                }
                d.dismiss()
            }
            .show()
    }

    /**
     * Планирование периодической фоновой синхронизации.
     * Условия: требуется сеть (CONNECTED), интервал из настроек, уникальная задача.
     * 
     * Вызывается ТОЛЬКО если фоновая синхронизация включена в настройках
     */
    private fun schedulePeriodicBackgroundSync() {
        try {
            val intervalHours = AutoSyncSettings.getSyncIntervalHours(this)
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<com.example.vkbookandroid.service.SyncWorker>(
                intervalHours.toLong(), 
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "vkbook_periodic_sync",
                ExistingPeriodicWorkPolicy.REPLACE, // REPLACE чтобы обновить интервал
                workRequest
            )
            
            android.util.Log.i("MainActivity", "Background sync scheduled every $intervalHours hours")
            
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to schedule periodic sync: ${e.message}")
        }
    }
    
    /**
     * Инициализация только базовых файлов без синхронизации с сервером.
     * Создаёт необходимые файлы для работы приложения, но НЕ загружает данные с сервера.
     */
    private fun initializeBasicFiles() {
        uiScope.launch {
            try {
                updateSyncStatus("Инициализация файлов...")
                
                // Проверяем, нужно ли выполнить начальную установку
                val appInstallService = com.example.vkbookandroid.service.AppInstallationService(this@MainActivity)
                val needsInitialSetup = withContext(Dispatchers.IO) {
                    appInstallService.needsInitialSetup()
                }
                
                if (needsInitialSetup) {
                    updateSyncStatus("Первоначальная установка...")
                    
                    val setupComplete = withContext(Dispatchers.IO) {
                        appInstallService.performInitialSetup()
                    }
                    
                    if (setupComplete) {
                        updateSyncStatus("Установка завершена")
                    } else {
                        updateSyncStatus("Ошибка установки")
                        return@launch
                    }
                } else {
                    updateSyncStatus("Приложение готово")
                }
                
                // Создаем Excel файлы, если их нет (БЕЗ загрузки с сервера)
                val excelFilesMissing = withContext(Dispatchers.IO) {
                    val dataDir = filesDir.resolve("data")
                    val bschuFile = dataDir.resolve("Oborudovanie_BSCHU.xlsx")
                    val armaturesFile = dataDir.resolve("Armatures.xlsx")
                    !(bschuFile.exists() && armaturesFile.exists())
                }
                
                if (excelFilesMissing) {
                    updateSyncStatus("Создание базовых Excel файлов...")
                    val created = withContext(Dispatchers.IO) {
                        com.example.vkbookandroid.utils.ExcelFileCreator.createExcelFilesInDataDir(this@MainActivity)
                    }
                    if (created) {
                        updateSyncStatus("Excel файлы созданы")
                    } else {
                        updateSyncStatus("Ошибка создания Excel файлов")
                    }
                }
                
                // АВТОСИНХРОНИЗАЦИЯ ОТКЛЮЧЕНА
                // Пользователь может запустить синхронизацию вручную через кнопку "Синхронизация"
                updateSyncStatus("Готово (автообновления отключены)")
                
                Log.i("MainActivity", "Basic files initialized. Auto-sync is DISABLED - user can sync manually")
                
                // Уведомляем о завершении инициализации
                notifyInitializationComplete()
                
            } catch (e: Exception) {
                updateSyncStatus("Ошибка инициализации: ${e.message}")
                Log.e("MainActivity", "Error during basic files initialization", e)
            }
        }
    }
    
    
    /**
     * Инициализация файлов и проверка обновлений при запуске (ТОЛЬКО если включена автосинхронизация)
     */
    private fun initializeAndCheckUpdates() {
        uiScope.launch {
            try {
                updateSyncStatus("Проверка установки...")
                
                // Инициализируем базовые файлы
                val appInstallService = com.example.vkbookandroid.service.AppInstallationService(this@MainActivity)
                val needsInitialSetup = withContext(Dispatchers.IO) {
                    appInstallService.needsInitialSetup()
                }
                
                if (needsInitialSetup) {
                    updateSyncStatus("Первоначальная установка...")
                    
                    val setupComplete = withContext(Dispatchers.IO) {
                        appInstallService.performInitialSetup()
                    }
                    
                    if (setupComplete) {
                        updateSyncStatus("Установка завершена")
                    } else {
                        updateSyncStatus("Ошибка установки")
                        return@launch
                    }
                } else {
                    updateSyncStatus("Приложение готово")
                }
                
                // Создаем Excel файлы, если их нет
                val excelFilesMissing = withContext(Dispatchers.IO) {
                    val dataDir = filesDir.resolve("data")
                    val bschuFile = dataDir.resolve("Oborudovanie_BSCHU.xlsx")
                    val armaturesFile = dataDir.resolve("Armatures.xlsx")
                    !(bschuFile.exists() && armaturesFile.exists())
                }
                
                if (excelFilesMissing) {
                    updateSyncStatus("Создание Excel файлов...")
                    val created = withContext(Dispatchers.IO) {
                        com.example.vkbookandroid.utils.ExcelFileCreator.createExcelFilesInDataDir(this@MainActivity)
                    }
                    if (created) {
                        updateSyncStatus("Excel файлы созданы")
                    } else {
                        updateSyncStatus("Ошибка создания Excel файлов")
                    }
                }
                
                // АВТОСИНХРОНИЗАЦИЯ ВКЛЮЧЕНА - выполняем синхронизацию
                val isServerAvailable = withContext(Dispatchers.IO) {
                    syncService.checkServerConnection()
                }
                
                if (isServerAvailable) {
                    updateSyncStatus("Автосинхронизация...")
                    val result = withContext(Dispatchers.IO) {
                        syncService.syncAll()
                    }
                    
                    if (result.overallSuccess) {
                        updateSyncStatus("Автообновление завершено")
                        refreshFragmentsData()
                    } else {
                        updateSyncStatus("Ошибка автообновления")
                    }
                } else {
                    updateSyncStatus("Сервер недоступен")
                }
                
            } catch (e: Exception) {
                updateSyncStatus("Ошибка инициализации")
                Log.e("MainActivity", "Error during auto-initialization", e)
            }
        }
    }
    
    /**
     * Инициализация ViewPager2 после завершения setup
     */
    private fun initializeViewPager() {
        Log.d("MainActivity", "Initializing ViewPager2 after setup completion...")
        
        pagerAdapter = MainPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        // ViewPager2 не поддерживает 0, минимум = 1 (предзагружает 1 соседнюю вкладку)
        // Это компромисс между производительностью и плавностью переключения
        viewPager.offscreenPageLimit = 1

        // Минимальная высота вкладок для удобного тапа
        tabLayout.minimumHeight = (resources.displayMetrics.density * 56).toInt()
        // Корректный отступ с учётом системных панелей (статус-бар/вырезы)
        ViewCompat.setOnApplyWindowInsetsListener(tabLayout) { v, insets ->
            val systemBars = insets.getInsets(Type.systemBars())
            val extraTop = (v.resources.displayMetrics.density * 8).toInt()
            v.setPadding(v.paddingLeft, systemBars.top + extraTop, v.paddingRight, v.paddingBottom)
            insets
        }
        
        // Связывание TabLayout с ViewPager2
        val visible = loadTabsVisibility()
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            val adapter = (viewPager.adapter as? MainPagerAdapter)
            val globalPos = adapter?.getGlobalPositionAt(position) ?: position
            val label = when (globalPos) {
                0 -> "Сигналы БЩУ"
                1 -> "Арматура"
                2 -> "Схемы"
                3 -> "Редактор"
                4 -> "График"
                5 -> "График проверок"
                else -> ""
            }
            tab.text = label
        }.attach()
        adjustTabsLayoutSpacing()

        // Применяем видимость к TabLayout и ViewPager
        applyTabsVisibility()
        
        // Принудительно подгружаем АКТУАЛЬНУЮ вкладку после первой разметки (после ротации сохранится текущая)
        viewPager.postOnAnimation {
            ensureCurrentTabLoaded()
        }

        // Ленивая, но ускоренная подгрузка при переключении вкладок
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                Log.d("MainActivity", "Tab selected: $position, shared search query: '$sharedSearchQuery'")
                ensureTabLoaded(position)
                // УЛУЧШЕНИЕ: Применяем поиск к новой вкладке с задержкой
                // чтобы дать время загрузиться данным
                if (sharedSearchQuery.isNotEmpty()) {
                    uiScope.launch {
                        // Увеличиваем задержку для гарантированной загрузки данных
                        kotlinx.coroutines.delay(300)
                        Log.d("MainActivity", "Applying search '$sharedSearchQuery' to tab $position after delay")
                        applySharedSearchToCurrentTab(position)
                    }
                }
                (viewPager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val globalPosition = adapter.getGlobalPositionAt(position)
                    saveLastSelectedTab(globalPosition)
                    
                    // Логируем открытие вкладки
                    val tabName = when (globalPosition) {
                        0 -> "Сигналы БЩУ"
                        1 -> "Арматура"
                        2 -> "Схемы"
                        3 -> "Редактор"
                        4 -> "График"
                        5 -> "График проверок"
                        else -> "Неизвестная"
                    }
                    com.example.vkbookandroid.analytics.AnalyticsManager.logTabOpened(tabName)
                }
            }
        })
        
        Log.d("MainActivity", "ViewPager2 initialization completed")
    }

    private fun loadTabsVisibility(): MutableMap<Int, Boolean> {
        return try {
            val prefs = getSharedPreferences("server_settings", MODE_PRIVATE)
            val json = prefs.getString("tabs_visibility_json", null)
            
            // Если нет сохраненных настроек, возвращаем значения по умолчанию
            if (json == null) {
                return defaultTabsVisibility()
            }
            
            val gson = com.google.gson.Gson()
            if (json.trim().startsWith("[")) {
                // формат: список включённых индексов
                val listType = object : com.google.gson.reflect.TypeToken<List<Int>>() {}.type
                val list = gson.fromJson<List<Int>>(json, listType) ?: emptyList()
                val map = defaultTabsVisibility()
                (0..5).forEach { map[it] = list.contains(it) }
                map
            } else {
                // формат: карта индексов -> bool (строки или числа)
                return try {
                    val mapStrType = object : com.google.gson.reflect.TypeToken<Map<String, Boolean>>() {}.type
                    val m = gson.fromJson<Map<String, Boolean>>(json, mapStrType) ?: emptyMap()
                    val result = defaultTabsVisibility()
                    m.forEach { (key, value) ->
                        val intKey = key.toIntOrNull()
                        if (intKey != null && intKey in 0..5) {
                            result[intKey] = value ?: false
                        }
                    }
                    result
                } catch (_: Exception) {
                    val mapIntType = object : com.google.gson.reflect.TypeToken<Map<Int, Boolean>>() {}.type
                    val parsed = gson.fromJson<Map<Int, Boolean>>(json, mapIntType) ?: emptyMap()
                    val result = defaultTabsVisibility()
                    parsed.forEach { (key, value) ->
                        if (key in 0..5) {
                            result[key] = value ?: false
                        }
                    }
                    result
                }
            }
        } catch (e: Exception) { 
            // В случае ошибки возвращаем значения по умолчанию
            defaultTabsVisibility()
        }
    }

    private fun applyTabsVisibility() {
        val visible = loadTabsVisibility()
        if (!isAdminMode) {
            visible[0] = false
            visible[3] = false
        }
        if (!::tabLayout.isInitialized || !::viewPager.isInitialized) return
        val adapter = (viewPager.adapter as? MainPagerAdapter)
        val newPositions = (0..5).filter { visible[it] != false }
        adapter?.setVisiblePositions(newPositions)
        for (i in 0 until tabLayout.tabCount) {
            val tabView = tabLayout.getTabAt(i)?.view ?: continue
            val global = adapter?.getGlobalPositionAt(i) ?: i
            tabView.visibility = if (visible[global] != false) android.view.View.VISIBLE else android.view.View.GONE
        }
        if (newPositions.isEmpty()) {
            viewPager.visibility = android.view.View.GONE
            return
        }
        viewPager.visibility = android.view.View.VISIBLE
        
        // Проверяем текущую вкладку - если она стала невидимой (например, Редактор после отключения админ режима),
        // переключаемся на другую вкладку
        val currentLocalIndex = viewPager.currentItem
        val currentGlobalIndex = adapter?.getGlobalPositionAt(currentLocalIndex)
        val isCurrentTabVisible = currentGlobalIndex != null && newPositions.contains(currentGlobalIndex)
        
        // Если текущая вкладка невидима, определяем новую целевую вкладку
        // determineInitialGlobalTab() уже проверяет сохраненную вкладку и если она не видима, выбирает другую
        val targetGlobal = determineInitialGlobalTab(newPositions)
        
        // Переключаемся на целевую вкладку только если текущая невидима или целевая отличается
        if (!isCurrentTabVisible || currentGlobalIndex != targetGlobal) {
            setCurrentTabByGlobal(targetGlobal, smoothScroll = false)
            saveLastSelectedTab(targetGlobal)
        }
    }
    
    /**
     * Настройка отслеживания файлов для автоматического обновления данных
     */
    private fun setupFileWatching() {
        Log.d("MainActivity", "Setting up file watching for automatic data refresh")
        
        // Отслеживаем файлы для удаленного сервера
        val baseUrl = NetworkModule.getCurrentBaseUrl()
        Log.d("MainActivity", "Server URL: $baseUrl")
        
        // Настраиваем отслеживание для DataFragment (Oborudovanie_BSCHU.xlsx)
        val bschuFilePath = filesDir.resolve("data").resolve("Oborudovanie_BSCHU.xlsx").absolutePath
        dataRefreshManager.startWatching(bschuFilePath) {
            if (!isAdminMode) return@startWatching
            Log.d("MainActivity", "BSCHU file changed, refreshing DataFragment")
            uiScope.launch {
                try {
                    val dataFragment = getFragmentByGlobalPosition(0) as? org.example.pult.android.DataFragment
                    dataFragment?.let { (it as RefreshableFragment).refreshData() }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error refreshing DataFragment", e)
                }
            }
        }
        
        // Настраиваем отслеживание для ArmatureFragment (Armatures.xlsx)
        val armatureFilePath = filesDir.resolve("data").resolve("Armatures.xlsx").absolutePath
        dataRefreshManager.startWatching(armatureFilePath) {
            Log.d("MainActivity", "Armature file changed, refreshing ArmatureFragment")
            uiScope.launch {
                try {
                    val armatureFragment = getFragmentByGlobalPosition(1) as? ArmatureFragment
                    armatureFragment?.let { (it as RefreshableFragment).refreshData() }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error refreshing ArmatureFragment", e)
                }
            }
        }
        
        // Настраиваем отслеживание для SchemesFragment (папка data с PDF/TXT файлами)
        val dataDir = filesDir.resolve("data")
        if (dataDir.exists()) {
            dataRefreshManager.startWatching(dataDir.absolutePath) {
                Log.d("MainActivity", "Data directory changed, refreshing SchemesFragment")
                uiScope.launch {
                    try {
                        val schemesFragment = getFragmentByGlobalPosition(2) as? SchemesFragment
                        schemesFragment?.let { (it as RefreshableFragment).refreshData() }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error refreshing SchemesFragment", e)
                    }
                }
            }
        }
        
        // Настраиваем отслеживание для ChecksScheduleFragment (График проверок .xlsx - с пробелом!)
        val checksFilePath = filesDir.resolve("data").resolve("График проверок .xlsx").absolutePath
        dataRefreshManager.startWatching(checksFilePath) {
            Log.d("MainActivity", "График проверок.xlsx changed, refreshing ChecksScheduleFragment")
                uiScope.launch {
                    try {
                        val checksFragment = getFragmentByGlobalPosition(5) as? ChecksScheduleFragment
                    checksFragment?.let { (it as RefreshableFragment).refreshData() }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error refreshing ChecksScheduleFragment", e)
                }
            }
        }
        
        Log.d("MainActivity", "File watching setup completed")
    }
    
    /**
     * Получить фрагмент по позиции в ViewPager
     */
    fun refreshArmatureFragmentData() {
        uiScope.launch {
            try {
                val armatureFragment = getFragmentByGlobalPosition(1) as? ArmatureFragment
                armatureFragment?.let { 
                    (it as RefreshableFragment).refreshData()
                    Log.d("MainActivity", "ArmatureFragment refreshed from external call")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error refreshing ArmatureFragment from external call", e)
            }
        }
    }
    
    fun refreshDataFragmentData() {
        if (!isAdminMode) return
        uiScope.launch {
            try {
                val dataFragment = getFragmentByGlobalPosition(0) as? org.example.pult.android.DataFragment
                dataFragment?.let { 
                    (it as RefreshableFragment).refreshData()
                    Log.d("MainActivity", "DataFragment refreshed from external call")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error refreshing DataFragment from external call", e)
            }
        }
    }
    
    
    override fun onDestroy() {
        super.onDestroy()
        // Очищаем ресурсы DataRefreshManager
        dataRefreshManager.cleanup()
        uiJob.cancel()
    }
    
    /**
     * Обновление данных во фрагментах после синхронизации
     */
    private fun refreshFragmentsData() {
        if (!::pagerAdapter.isInitialized) return
        
        // НЕ очищаем кэш - файлы уже обновлены синхронизацией
        // Просто обновляем данные в фрагментах
        try {
            // Диагностика файлов перед обновлением
            diagnoseFiles()
            
            val dataFragment = (pagerAdapter as? MainPagerAdapter)?.getFragmentByGlobalPosition(0) as? org.example.pult.android.DataFragment
            val armatureFragment = (pagerAdapter as? MainPagerAdapter)?.getFragmentByGlobalPosition(1) as? com.example.vkbookandroid.ArmatureFragment
            val schemesFragment = (pagerAdapter as? MainPagerAdapter)?.getFragmentByGlobalPosition(2) as? com.example.vkbookandroid.SchemesFragment
            
            // Используем новый механизм обновления для фрагментов, поддерживающих RefreshableFragment
            dataFragment?.let { (it as RefreshableFragment).refreshData() }
            armatureFragment?.let { (it as RefreshableFragment).refreshData() }
            schemesFragment?.let { (it as RefreshableFragment).refreshData() }
            
            Log.d("MainActivity", "Refreshed data in all fragments using new refresh mechanism")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error refreshing fragments", e)
        }
    }
    
    private fun getFragmentByGlobalPosition(globalPosition: Int): androidx.fragment.app.Fragment? {
        if (!::pagerAdapter.isInitialized) return null
        val adapter = pagerAdapter
        // Сначала пробуем взять уже созданный экземпляр из адаптера
        adapter.getFragmentByGlobalPosition(globalPosition)?.let { return it }
        // Если фрагмент ещё не создан или был уничтожен, пытаемся найти его по тэгу ViewPager2
        val localIndex = adapter.getLocalIndex(globalPosition) ?: return null
        return try {
            supportFragmentManager.findFragmentByTag("f$localIndex")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error getting fragment by global position $globalPosition", e)
            null
        }
    }
    
    /**
     * Диагностика состояния файлов
     */
    private fun diagnoseFiles() {
        try {
            val dataDir = File(filesDir, "data")
            Log.d("MainActivity", "=== FILE DIAGNOSTICS ===")
            Log.d("MainActivity", "Data directory exists: ${dataDir.exists()}")
            Log.d("MainActivity", "Data directory path: ${dataDir.absolutePath}")
            
            if (dataDir.exists()) {
                val files = dataDir.listFiles()
                Log.d("MainActivity", "Files in data directory: ${files?.size ?: 0}")
                files?.forEach { file ->
                    Log.d("MainActivity", "  - ${file.name}: ${file.length()} bytes, modified: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))}")
                }
            }
            
            // Проверяем конкретно Armatures.xlsx
            val armaturesFile = File(dataDir, "Armatures.xlsx")
            Log.d("MainActivity", "Armatures.xlsx exists: ${armaturesFile.exists()}")
            if (armaturesFile.exists()) {
                Log.d("MainActivity", "Armatures.xlsx size: ${armaturesFile.length()} bytes")
            }
            
            // Проверяем через FileProvider
            val fileProvider = com.example.vkbookandroid.FileProvider(this)
            val hasArmatures = fileProvider.hasFilesDirFile("Armatures.xlsx")
            Log.d("MainActivity", "FileProvider reports Armatures.xlsx exists: $hasArmatures")
            
            Log.d("MainActivity", "=== END DIAGNOSTICS ===")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error during file diagnostics", e)
        }
    }
    
    
    /**
     * Диагностика файлов после синхронизации
     */
    private fun diagnoseFilesAfterSync() {
        try {
            val dataDir = File(filesDir, "data")
            Log.d("MainActivity", "=== POST-SYNC FILE DIAGNOSTICS ===")
            Log.d("MainActivity", "Data directory exists: ${dataDir.exists()}")
            
            if (dataDir.exists()) {
                val files = dataDir.listFiles()
                Log.d("MainActivity", "Files in data directory: ${files?.size ?: 0}")
                files?.forEach { file ->
                    val modified = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))
                    Log.d("MainActivity", "  - ${file.name}: ${file.length()} bytes, modified: $modified")
                }
                
                // Проверяем конкретные файлы
                val armaturesFile = File(dataDir, "Armatures.xlsx")
                val bschuFile = File(dataDir, "Oborudovanie_BSCHU.xlsx")
                val jsonFile = File(dataDir, "armature_coords.json")
                
                Log.d("MainActivity", "Armatures.xlsx: exists=${armaturesFile.exists()}, size=${armaturesFile.length()}")
                Log.d("MainActivity", "Oborudovanie_BSCHU.xlsx: exists=${bschuFile.exists()}, size=${bschuFile.length()}")
                Log.d("MainActivity", "armature_coords.json: exists=${jsonFile.exists()}, size=${jsonFile.length()}")
                
                // Проверяем содержимое JSON файла
                if (jsonFile.exists()) {
                    try {
                        val jsonContent = jsonFile.readText()
                        Log.d("MainActivity", "JSON content length: ${jsonContent.length}")
                        Log.d("MainActivity", "JSON content preview: ${jsonContent.take(200)}...")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error reading JSON file", e)
                    }
                }
            }
            
            Log.d("MainActivity", "=== END POST-SYNC DIAGNOSTICS ===")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error during post-sync file diagnostics", e)
        }
    }
    
    /**
     * Показать детальную информацию об обновлениях
     */
    private fun showUpdateDetailsDialog(summary: String, filesList: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Обновление завершено")
            .setMessage("$summary\n\nОбновленные файлы:\n$filesList")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setIcon(android.R.drawable.ic_dialog_info)
            .show()
    }

    private fun saveLastSelectedTab(globalPosition: Int) {
        navigationPrefs.edit().putInt(KEY_LAST_TAB, globalPosition).apply()
    }

    private fun loadLastSelectedTab(): Int =
        navigationPrefs.getInt(KEY_LAST_TAB, DEFAULT_TAB_GLOBAL)

    private fun updateAdminModeState(): Boolean {
        val newState = AdminAccessManager.isAdminModeEnabled(this)
        val changed = newState != isAdminMode
        isAdminMode = newState
        return changed
    }

    private fun determineInitialGlobalTab(visiblePositions: List<Int>): Int {
        if (visiblePositions.isEmpty()) return DEFAULT_TAB_GLOBAL
        val saved = loadLastSelectedTab()
        return when {
            visiblePositions.contains(saved) -> saved
            visiblePositions.contains(DEFAULT_TAB_GLOBAL) -> DEFAULT_TAB_GLOBAL
            else -> visiblePositions.first()
        }
    }

    private fun setCurrentTabByGlobal(globalPosition: Int, smoothScroll: Boolean) {
        if (!::viewPager.isInitialized) return
        val adapter = viewPager.adapter as? MainPagerAdapter ?: return
        val localIndex = adapter.getLocalIndex(globalPosition) ?: return
        if (viewPager.currentItem != localIndex) {
            viewPager.setCurrentItem(localIndex, smoothScroll)
        }
    }

    private fun defaultTabsVisibility(): MutableMap<Int, Boolean> {
        val defaultMap = mutableMapOf<Int, Boolean>()
        (0..5).forEach { defaultMap[it] = false }
        // По умолчанию включены: Арматура (1), Схемы (2), График (4)
        defaultMap[1] = true
        defaultMap[2] = true
        defaultMap[4] = true
        return defaultMap
    }

    private fun loadThemeConfiguration() {
        runBlocking {
            withContext(Dispatchers.IO) {
                com.example.vkbookandroid.theme.AppTheme.loadTheme(this@MainActivity)
            }
        }
    }

    private fun readServerUrlFromPreferences(): String {
        return runBlocking {
            withContext(Dispatchers.IO) {
                ServerSettingsActivity.getCurrentServerUrl(this@MainActivity)
            }
        }
    }

    companion object {
        private const val NAVIGATION_PREFS = "navigation_state"
        private const val KEY_LAST_TAB = "last_tab"
        private const val DEFAULT_TAB_GLOBAL = 1
    }

    private fun adjustTabsLayoutSpacing() {
        if (!::tabLayout.isInitialized) return
        val displayMetrics = resources.displayMetrics
        val deltaPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 3f, displayMetrics).toInt()
        val baseMinHeightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56f, displayMetrics).toInt()
        tabLayout.minimumHeight = (baseMinHeightPx - deltaPx).coerceAtLeast(0)
        tabLayout.setPadding(
            tabLayout.paddingLeft,
            (tabLayout.paddingTop - deltaPx).coerceAtLeast(0),
            tabLayout.paddingRight,
            tabLayout.paddingBottom
        )
        tabLayout.post {
            val strip = tabLayout.getChildAt(0) as? ViewGroup ?: return@post
            for (i in 0 until strip.childCount) {
                val tabView = strip.getChildAt(i)
                val newTop = (tabView.paddingTop - deltaPx).coerceAtLeast(0)
                tabView.setPadding(
                    tabView.paddingLeft,
                    newTop,
                    tabView.paddingRight,
                    tabView.paddingBottom
                )
            }
        }
    }
}

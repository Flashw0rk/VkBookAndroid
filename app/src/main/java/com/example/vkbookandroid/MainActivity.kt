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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.util.Log
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
    
    // Синхронизация
    private lateinit var btnSync: Button
    private lateinit var btnSettings: android.widget.ImageButton
    private lateinit var tvSyncStatus: TextView
    private lateinit var syncService: SyncService
    private lateinit var dataRefreshManager: DataRefreshManager
    private val uiJob = SupervisorJob()
    private val uiScope = CoroutineScope(Dispatchers.Main + uiJob)
    
    // Состояние инициализации
    private var isInitializationComplete = false
    private val initializationListeners = mutableListOf<() -> Unit>()
    
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // Обновляем настройки сервера после возврата из настроек
        loadServerSettings()
        // Применяем настройки вкладок и корректируем UI
        try { applyTabsVisibility() } catch (_: Throwable) {}
        
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
        setContentView(R.layout.activity_main)
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

    }
    
    override fun onResume() {
        super.onResume()
        // После ротации гарантируем, что видимая вкладка подгружена и отрисована
        // Проверяем, что ViewPager2 уже инициализирован
        if (::pagerAdapter.isInitialized) {
            ensureCurrentTabLoaded()
            // УЛУЧШЕНИЕ: Применяем поиск к текущей вкладке при возврате в приложение
            if (sharedSearchQuery.isNotEmpty()) {
                applySharedSearchToCurrentTab(viewPager.currentItem)
            }
        }
    }

    private fun ensureCurrentTabLoaded() {
        if (::pagerAdapter.isInitialized) {
            ensureTabLoaded(viewPager.currentItem)
        }
    }

    private fun ensureTabLoaded(position: Int) {
        if (!::pagerAdapter.isInitialized) return
        
        val tag = "f$position"
        val frag = supportFragmentManager.findFragmentByTag(tag)
        when (frag) {
            is org.example.pult.android.DataFragment -> frag.ensureDataLoaded()
            is com.example.vkbookandroid.ArmatureFragment -> frag.ensureDataLoaded()
            else -> {
                // Если Fragment ещё не найден по тегу, попробуем через адаптер как запасной вариант
                when (position) {
                    0 -> ((pagerAdapter as? MainPagerAdapter)?.getFragmentByGlobalPosition(0) as? org.example.pult.android.DataFragment)?.ensureDataLoaded()
                    1 -> ((pagerAdapter as? MainPagerAdapter)?.getFragmentByGlobalPosition(1) as? com.example.vkbookandroid.ArmatureFragment)?.ensureDataLoaded()
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
        
        // Пробуем найти уже созданные фрагменты по тегам ViewPager2
        (supportFragmentManager.findFragmentByTag("f0") as? org.example.pult.android.DataFragment)?.setSearchQueryExternal(sharedSearchQuery)
        (supportFragmentManager.findFragmentByTag("f1") as? com.example.vkbookandroid.ArmatureFragment)?.setSearchQueryExternal(sharedSearchQuery)
        // Фоллбек через адаптер
        ((pagerAdapter as? MainPagerAdapter)?.getFragmentByGlobalPosition(0) as? org.example.pult.android.DataFragment)?.setSearchQueryExternal(sharedSearchQuery)
        ((pagerAdapter as? MainPagerAdapter)?.getFragmentByGlobalPosition(1) as? com.example.vkbookandroid.ArmatureFragment)?.setSearchQueryExternal(sharedSearchQuery)
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
        
        when (position) {
            0 -> {
                // Вкладка "Сигналы БЩУ"
                Log.d("MainActivity", "=== SEARCHING IN TAB 0: DataFragment (БЩУ сигналы) ===")
                val fragment = supportFragmentManager.findFragmentByTag("f0") as? org.example.pult.android.DataFragment
                    ?: ((pagerAdapter as? MainPagerAdapter)?.getFragmentByGlobalPosition(0) as? org.example.pult.android.DataFragment)
                if (fragment != null) {
                    Log.d("MainActivity", "Found DataFragment, applying search")
                    fragment.setSearchQueryExternal(sharedSearchQuery)
                } else {
                    Log.w("MainActivity", "DataFragment not found for tab $position")
                }
            }
            1 -> {
                // Вкладка "Арматура"
                Log.d("MainActivity", "=== SEARCHING IN TAB 1: ArmatureFragment (Арматура) ===")
                val fragment = supportFragmentManager.findFragmentByTag("f1") as? com.example.vkbookandroid.ArmatureFragment
                    ?: ((pagerAdapter as? MainPagerAdapter)?.getFragmentByGlobalPosition(1) as? com.example.vkbookandroid.ArmatureFragment)
                if (fragment != null) {
                    Log.d("MainActivity", "Found ArmatureFragment, applying search")
                    fragment.setSearchQueryExternal(sharedSearchQuery)
                } else {
                    Log.w("MainActivity", "ArmatureFragment not found for tab $position")
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
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < 500) {
                // Двойной тап - запускаем диагностику
                diagnoseFiles()
                Toast.makeText(this, "Диагностика файлов выполнена. Смотрите логи.", Toast.LENGTH_LONG).show()
            } else {
                // Обычный тап - запускаем РУЧНУЮ синхронизацию (автообновления отключены)
                startSync()
            }
            lastTapTime = currentTime
        }
    }
    
    private fun setupSettingsButton() {
        btnSettings.setOnClickListener {
            val intent = android.content.Intent(this, ServerSettingsActivity::class.java)
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
        val currentUrl = ServerSettingsActivity.getCurrentServerUrl(this)
        android.util.Log.d("MainActivity", "loadServerSettings: currentUrl=$currentUrl")
        NetworkModule.updateBaseUrl(currentUrl)
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
        btnSync.isEnabled = false
        updateSyncStatus("Ручное обновление...")
        
        uiScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    // Полная РУЧНАЯ синхронизация данных с прогрессом
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
                        
                        // Показываем детальную информацию об обновленных файлах
                        if (result.updatedFiles.isNotEmpty()) {
                            val filesList = result.updatedFiles.joinToString("\n• ", "• ")
                            showUpdateDetailsDialog(updateSummary, filesList)
                        }
                        
                        // Обновляем данные во фрагментах
                        refreshFragmentsData()
                        
                        // Диагностика файлов после синхронизации
                        diagnoseFilesAfterSync()
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
            } catch (e: Exception) {
                updateSyncStatus("Ошибка соединения")
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnSync.isEnabled = true
            }
        }
    }
    
    /**
     * Обновление статуса синхронизации
     */
    private fun updateSyncStatus(status: String) {
        tvSyncStatus.text = status
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
                
                if (appInstallService.needsInitialSetup()) {
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
                val dataDir = filesDir.resolve("data")
                val bschuFile = dataDir.resolve("Oborudovanie_BSCHU.xlsx")
                val armaturesFile = dataDir.resolve("Armatures.xlsx")
                
                if (!bschuFile.exists() || !armaturesFile.exists()) {
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
                
                if (appInstallService.needsInitialSetup()) {
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
                val dataDir = filesDir.resolve("data")
                val bschuFile = dataDir.resolve("Oborudovanie_BSCHU.xlsx")
                val armaturesFile = dataDir.resolve("Armatures.xlsx")
                
                if (!bschuFile.exists() || !armaturesFile.exists()) {
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
        viewPager.offscreenPageLimit = 1 // держим только текущую и одну соседнюю страницу в памяти

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
                else -> ""
            }
            tab.text = label
        }.attach()

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
            }
        })
        
        Log.d("MainActivity", "ViewPager2 initialization completed")
    }

    private fun loadTabsVisibility(): Map<Int, Boolean> {
        return try {
            val prefs = getSharedPreferences("server_settings", MODE_PRIVATE)
            val json = prefs.getString("tabs_visibility_json", null)
            
            // Если нет сохраненных настроек, возвращаем значения по умолчанию
            if (json == null) {
                val defaultMap = mutableMapOf<Int, Boolean>()
                (0..4).forEach { defaultMap[it] = false }
                // По умолчанию включены: Арматура (1), Схемы (2), График (4)
                defaultMap[1] = true
                defaultMap[2] = true
                defaultMap[4] = true
                return defaultMap
            }
            
            val gson = com.google.gson.Gson()
            if (json.trim().startsWith("[")) {
                // формат: список включённых индексов
                val listType = object : com.google.gson.reflect.TypeToken<List<Int>>() {}.type
                val list = gson.fromJson<List<Int>>(json, listType) ?: emptyList()
                val map = mutableMapOf<Int, Boolean>()
                (0..4).forEach { map[it] = list.contains(it) }
                map
            } else {
                // формат: карта индексов -> bool (строки или числа)
                return try {
                    val mapStrType = object : com.google.gson.reflect.TypeToken<Map<String, Boolean>>() {}.type
                    val m = gson.fromJson<Map<String, Boolean>>(json, mapStrType) ?: emptyMap()
                    m.mapKeys { it.key.toIntOrNull() ?: -1 }.filterKeys { it in 0..4 }
                } catch (_: Exception) {
                    val mapIntType = object : com.google.gson.reflect.TypeToken<Map<Int, Boolean>>() {}.type
                    gson.fromJson<Map<Int, Boolean>>(json, mapIntType) ?: emptyMap()
                }
            }
        } catch (e: Exception) { 
            // В случае ошибки возвращаем значения по умолчанию
            val defaultMap = mutableMapOf<Int, Boolean>()
            (0..4).forEach { defaultMap[it] = false }
            defaultMap[1] = true  // Арматура
            defaultMap[2] = true  // Схемы
            defaultMap[4] = true  // График
            defaultMap
        }
    }

    private fun applyTabsVisibility() {
        val visible = loadTabsVisibility()
        if (!::tabLayout.isInitialized || !::viewPager.isInitialized) return
        // Обновляем список позиций для адаптера
        val newPositions = (0..4).filter { visible[it] != false }
        (viewPager.adapter as? MainPagerAdapter)?.setVisiblePositions(newPositions)
        // Перестраиваем TabLayout: скрываем вкладки без удаления (по глобальной позиции)
        val adapter = (viewPager.adapter as? MainPagerAdapter)
        for (i in 0 until tabLayout.tabCount) {
            val v = tabLayout.getTabAt(i)?.view ?: continue
            val global = adapter?.getGlobalPositionAt(i) ?: i
            v.visibility = if (visible[global] != false) android.view.View.VISIBLE else android.view.View.GONE
        }
        // Если все вкладки скрыты — показываем пустой фон
        if (newPositions.isEmpty()) {
            viewPager.visibility = android.view.View.GONE
        } else {
            viewPager.visibility = android.view.View.VISIBLE
            val currentGlobal = (viewPager.adapter as? MainPagerAdapter)?.getGlobalPositionAt(viewPager.currentItem) ?: 0
            if (visible[currentGlobal] == false) {
                viewPager.setCurrentItem(0, false)
            }
        }
    }
    
    /**
     * Настройка отслеживания файлов для автоматического обновления данных
     */
    private fun setupFileWatching() {
        Log.d("MainActivity", "Setting up file watching for automatic data refresh")
        
        // Отслеживаем файлы для удаленного сервера
        val baseUrl = ServerSettingsActivity.getCurrentServerUrl(this)
        Log.d("MainActivity", "Server URL: $baseUrl")
        
        // Настраиваем отслеживание для DataFragment (Oborudovanie_BSCHU.xlsx)
        val bschuFilePath = filesDir.resolve("data").resolve("Oborudovanie_BSCHU.xlsx").absolutePath
        dataRefreshManager.startWatching(bschuFilePath) {
            Log.d("MainActivity", "BSCHU file changed, refreshing DataFragment")
            uiScope.launch {
                try {
                    val dataFragment = getFragmentByPosition(0) as? org.example.pult.android.DataFragment
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
                    val armatureFragment = getFragmentByPosition(1) as? ArmatureFragment
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
                        val schemesFragment = getFragmentByPosition(2) as? SchemesFragment
                        schemesFragment?.let { (it as RefreshableFragment).refreshData() }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error refreshing SchemesFragment", e)
                    }
                }
            }
        }
        
        Log.d("MainActivity", "File watching setup completed")
    }
    
    /**
     * Получить фрагмент по позиции в ViewPager
     */
    private fun getFragmentByPosition(position: Int): androidx.fragment.app.Fragment? {
        return try {
            val fragmentManager = supportFragmentManager
            val fragmentTag = "f$position"
            fragmentManager.findFragmentByTag(fragmentTag)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error getting fragment by position $position", e)
            null
        }
    }
    
    fun refreshArmatureFragmentData() {
        uiScope.launch {
            try {
                val armatureFragment = getFragmentByPosition(1) as? ArmatureFragment
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
        uiScope.launch {
            try {
                val dataFragment = getFragmentByPosition(0) as? org.example.pult.android.DataFragment
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
}

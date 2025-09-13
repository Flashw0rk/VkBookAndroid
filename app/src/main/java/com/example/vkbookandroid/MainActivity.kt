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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {
    
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var pagerAdapter: MainPagerAdapter
    private var sharedSearchQuery: String = ""
    
    // Синхронизация
    private lateinit var btnSync: Button
    private lateinit var btnSettings: Button
    private lateinit var tvSyncStatus: TextView
    private lateinit var syncService: SyncService
    private lateinit var dataRefreshManager: DataRefreshManager
    private val uiJob = SupervisorJob()
    private val uiScope = CoroutineScope(Dispatchers.Main + uiJob)
    
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // Обновляем настройки сервера после возврата из настроек
        loadServerSettings()
        // Проверяем соединение с новыми настройками
        checkConnectionOnStartup()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Включаем StrictMode только в debug для раннего обнаружения медленных операций на UI-потоке
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
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
        syncService = SyncService(this)
        
        setupSyncButton()
        setupSettingsButton()
        setupHashInfoButton()
        loadServerSettings()
        checkConnectionOnStartup()
        
        // Инициализация файлов и проверка обновлений при запуске
        // ViewPager2 будет инициализирован после завершения setup
        initializeAndCheckUpdates()

    }
    
    override fun onResume() {
        super.onResume()
        // После ротации гарантируем, что видимая вкладка подгружена и отрисована
        // Проверяем, что ViewPager2 уже инициализирован
        if (::pagerAdapter.isInitialized) {
            ensureCurrentTabLoaded()
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
                    0 -> (pagerAdapter.getFragment(0) as? org.example.pult.android.DataFragment)?.ensureDataLoaded()
                    1 -> (pagerAdapter.getFragment(1) as? com.example.vkbookandroid.ArmatureFragment)?.ensureDataLoaded()
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
        (pagerAdapter.getFragment(0) as? org.example.pult.android.DataFragment)?.setSearchQueryExternal(sharedSearchQuery)
        (pagerAdapter.getFragment(1) as? com.example.vkbookandroid.ArmatureFragment)?.setSearchQueryExternal(sharedSearchQuery)
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
        
        // Добавляем диагностику по двойному тапу
        var lastTapTime = 0L
        btnSync.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < 500) {
                // Двойной тап - запускаем диагностику
                diagnoseFiles()
                Toast.makeText(this, "Диагностика файлов выполнена. Смотрите логи.", Toast.LENGTH_LONG).show()
            } else {
                // Обычный тап - запускаем синхронизацию
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
        
        btnSettings.setOnLongClickListener {
            Toast.makeText(this, "Настройки сервера", Toast.LENGTH_SHORT).show()
            true
        }
    }
    
    private fun setupHashInfoButton() {
        // Добавляем длинное нажатие на кнопку синхронизации для просмотра хешей
        btnSync.setOnLongClickListener {
            val hashDialog = HashInfoDialog.newInstance()
            hashDialog.show(supportFragmentManager, "HashInfoDialog")
            true
        }
    }
    
    private fun loadServerSettings() {
        val serverSettings = ServerSettingsActivity()
        val currentUrl = serverSettings.getCurrentServerUrl(this)
        NetworkModule.updateBaseUrl(currentUrl)
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
     * Запуск синхронизации
     */
    private fun startSync() {
        btnSync.isEnabled = false
        updateSyncStatus("Обновление...")
        
        uiScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    syncService.syncAll()
                }
                
                when {
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
    
    
    /**
     * Инициализация файлов и проверка обновлений при запуске
     */
    private fun initializeAndCheckUpdates() {
        uiScope.launch {
            try {
                updateSyncStatus("Проверка установки...")
                
                // Диагностика файлов
                Log.d("MainActivity", "Running file diagnostics...")
                
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
                
                // Создаем тестовые файлы, если их нет
                // Временно отключено для диагностики проблем со сборкой
                /*
                val dataDir = filesDir.resolve("data")
                val bschuFile = dataDir.resolve("Oborudovanie_BSCHU.xlsx")
                val armaturesFile = dataDir.resolve("Armatures.xlsx")
                
                if (!bschuFile.exists() || !armaturesFile.exists()) {
                    updateSyncStatus("Создание тестовых файлов...")
                    withContext(Dispatchers.IO) {
                        TestDataCreator.createTestExcelFiles(this@MainActivity)
                    }
                    updateSyncStatus("Тестовые файлы созданы")
                }
                */
                
                // Проверяем доступность сервера
                val isServerAvailable = withContext(Dispatchers.IO) {
                    syncService.checkServerConnection()
                }
                
                if (isServerAvailable) {
                    updateSyncStatus("Готов к обновлению")
                    // Можно добавить автоматическую синхронизацию здесь
                    // val result = withContext(Dispatchers.IO) { syncService.syncAll() }
                    // if (result.overallSuccess) { refreshFragmentsData() }
                } else {
                    updateSyncStatus("Сервер недоступен")
                }
                
                // Инициализируем ViewPager2 только после завершения setup
                initializeViewPager()
                
            } catch (e: Exception) {
                updateSyncStatus("Ошибка инициализации")
                Log.e("MainActivity", "Error during initialization", e)
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
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Сигналы БЩУ"
                1 -> "Арматура"
                2 -> "Схемы"
                else -> ""
            }
        }.attach()
        
        // Принудительно подгружаем АКТУАЛЬНУЮ вкладку после первой разметки (после ротации сохранится текущая)
        viewPager.postOnAnimation {
            ensureCurrentTabLoaded()
        }

        // Ленивая, но ускоренная подгрузка при переключении вкладок
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                ensureTabLoaded(position)
                // При переключении вкладки применим общий поисковый запрос к новой вкладке
                applySharedSearchToFragments()
            }
        })
        
        // Настройка отслеживания файлов для автоматического обновления
        setupFileWatching()
        
        Log.d("MainActivity", "ViewPager2 initialization completed")
    }
    
    /**
     * Настройка отслеживания файлов для автоматического обновления данных
     */
    private fun setupFileWatching() {
        Log.d("MainActivity", "Setting up file watching for automatic data refresh")
        
        // Отслеживаем файлы только в удаленном режиме
        val baseUrl = getString(R.string.remote_base_url)
        if (baseUrl.isEmpty()) {
            Log.d("MainActivity", "Local mode - file watching disabled")
            return
        }
        
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
            
            val dataFragment = pagerAdapter.getFragment(0) as? org.example.pult.android.DataFragment
            val armatureFragment = pagerAdapter.getFragment(1) as? com.example.vkbookandroid.ArmatureFragment
            val schemesFragment = pagerAdapter.getFragment(2) as? com.example.vkbookandroid.SchemesFragment
            
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

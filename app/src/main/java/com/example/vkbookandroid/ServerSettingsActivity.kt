package com.example.vkbookandroid

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.vkbookandroid.network.NetworkModule
import com.example.vkbookandroid.utils.AutoSyncSettings
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Activity для настройки параметров сервера
 */
class ServerSettingsActivity : AppCompatActivity() {
    
    private lateinit var radioGroup: RadioGroup
    private lateinit var radioInternet: RadioButton
    private lateinit var radioCustom: RadioButton
    private lateinit var editServerUrl: EditText
    private lateinit var btnDiagnose: Button
    private lateinit var btnTabSettings: Button
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var tvSettingsTitle: TextView
    
    // Элементы UI для автосинхронизации
    private lateinit var switchAutoSync: Switch
    private lateinit var layoutAutoSyncDetails: LinearLayout
    private lateinit var checkSyncOnStartup: CheckBox
    private lateinit var checkSyncOnSettings: CheckBox
    private lateinit var checkBackgroundSync: CheckBox
    private lateinit var layoutSyncInterval: LinearLayout
    private lateinit var spinnerSyncInterval: Spinner
    private lateinit var tvAutoSyncStatus: TextView
    
    private lateinit var sharedPrefs: SharedPreferences
    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    companion object {
        private const val PREFS_NAME = "server_settings"
        private const val KEY_SERVER_MODE = "server_mode"
        private const val KEY_CUSTOM_URL = "custom_url"
        private const val KEY_TABS_VISIBILITY = "tabs_visibility_json"
        private const val KEY_EDITOR_ACCESS = "editor_access_enabled"
        private const val ADMIN_PASSWORD = "Admin6459"
        
        const val MODE_INTERNET = "internet"
        const val MODE_CUSTOM = "custom"
        

        /**
         * Получить текущий URL сервера из настроек
         */
        fun getCurrentServerUrl(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val serverMode = prefs.getString(KEY_SERVER_MODE, MODE_INTERNET) ?: MODE_INTERNET
            val customUrl = prefs.getString(KEY_CUSTOM_URL, "") ?: ""
            
            val defaultUrl = "http://158.160.157.7/"
            
            val resolvedUrl = when (serverMode) {
                MODE_INTERNET -> "http://158.160.157.7/"
                MODE_CUSTOM -> if (customUrl.isNotBlank()) {
                    if (!customUrl.endsWith("/")) "$customUrl/" else customUrl
                } else defaultUrl
                else -> defaultUrl
            }
            
            android.util.Log.d("ServerSettingsActivity", "getCurrentServerUrl called. Mode: $serverMode, Custom URL: '$customUrl', Resolved URL: '$resolvedUrl'")
            return resolvedUrl
        }
    }
    // dev-меню удалено
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_settings)
        
        // Инициализация SharedPreferences
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Инициализация UI элементов
        initViews()
        
        // Загрузка сохраненных настроек
        loadSettings()
        
        // Настройка обработчиков событий
        setupEventHandlers()

        // dev-доступ удалён
    }

    // Удалено меню разработчика и связанная логика

    // Удалён запуск теста путей из меню разработчика
    
    private fun initViews() {
        radioGroup = findViewById(R.id.radioGroupServerMode)
        radioInternet = findViewById(R.id.radioInternet)
        radioCustom = findViewById(R.id.radioCustom)
        editServerUrl = findViewById(R.id.editServerUrl)
        btnDiagnose = findViewById(R.id.btnDiagnose)
        btnTabSettings = findViewById(R.id.btnTabSettings)
        btnSave = findViewById(R.id.btnSaveSettings)
        btnCancel = findViewById(R.id.btnCancel)
        tvSettingsTitle = findViewById(R.id.tvSettingsTitle)
        
        // Инициализация элементов автосинхронизации
        switchAutoSync = findViewById(R.id.switchAutoSync)
        layoutAutoSyncDetails = findViewById(R.id.layoutAutoSyncDetails)
        checkSyncOnStartup = findViewById(R.id.checkSyncOnStartup)
        checkSyncOnSettings = findViewById(R.id.checkSyncOnSettings)
        checkBackgroundSync = findViewById(R.id.checkBackgroundSync)
        layoutSyncInterval = findViewById(R.id.layoutSyncInterval)
        spinnerSyncInterval = findViewById(R.id.spinnerSyncInterval)
        tvAutoSyncStatus = findViewById(R.id.tvAutoSyncStatus)
        
        setupAutoSyncUI()
        
        // Настройка обработчика долгого нажатия на заголовок
        setupTitleLongPressHandler()
    }
    
    private fun loadSettings() {
        val serverMode = sharedPrefs.getString(KEY_SERVER_MODE, MODE_INTERNET) ?: MODE_INTERNET
        val customUrl = sharedPrefs.getString(KEY_CUSTOM_URL, "") ?: ""
        
        when (serverMode) {
            MODE_INTERNET -> {
                radioInternet.isChecked = true
                editServerUrl.isEnabled = false
                editServerUrl.setText("http://158.160.157.7/")
            }
            MODE_CUSTOM -> {
                radioCustom.isChecked = true
                editServerUrl.isEnabled = true
                editServerUrl.setText(customUrl)
            }
            else -> {
                // По умолчанию интернет-сервер
                radioInternet.isChecked = true
                editServerUrl.isEnabled = false
                editServerUrl.setText("http://158.160.157.7/")
            }
        }
    }
    
    /**
     * Настройка UI для автосинхронизации
     */
    private fun setupAutoSyncUI() {
        // Настройка спиннера интервалов
        val intervalAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            AutoSyncSettings.AVAILABLE_INTERVALS.map { "$it ч" }
        )
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSyncInterval.adapter = intervalAdapter
        
        // Обработчик мастер-переключателя
        switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            layoutAutoSyncDetails.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateAutoSyncStatus()
            
            if (!isChecked) {
                // Если автосинхронизация отключается, отключаем все подопции
                checkSyncOnStartup.isChecked = false
                checkSyncOnSettings.isChecked = false
                checkBackgroundSync.isChecked = false
                layoutSyncInterval.visibility = View.GONE
            }
        }
        
        // Обработчик фоновой синхронизации
        checkBackgroundSync.setOnCheckedChangeListener { _, isChecked ->
            layoutSyncInterval.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateAutoSyncStatus()
        }
        
        // Обработчики других чекбоксов
        checkSyncOnStartup.setOnCheckedChangeListener { _, _ -> updateAutoSyncStatus() }
        checkSyncOnSettings.setOnCheckedChangeListener { _, _ -> updateAutoSyncStatus() }
        
        loadAutoSyncSettings()
    }
    
    /**
     * Загрузка настроек автосинхронизации
     */
    private fun loadAutoSyncSettings() {
        switchAutoSync.isChecked = AutoSyncSettings.isAutoSyncEnabled(this)
        checkSyncOnStartup.isChecked = AutoSyncSettings.isSyncOnStartupEnabled(this)
        checkSyncOnSettings.isChecked = AutoSyncSettings.isSyncOnSettingsChangeEnabled(this)
        checkBackgroundSync.isChecked = AutoSyncSettings.isBackgroundSyncEnabled(this)
        
        // Установка интервала
        val currentInterval = AutoSyncSettings.getSyncIntervalHours(this)
        val intervalIndex = AutoSyncSettings.AVAILABLE_INTERVALS.indexOf(currentInterval)
        if (intervalIndex >= 0) {
            spinnerSyncInterval.setSelection(intervalIndex)
        }
        
        // Обновление видимости элементов
        layoutAutoSyncDetails.visibility = if (switchAutoSync.isChecked) View.VISIBLE else View.GONE
        layoutSyncInterval.visibility = if (checkBackgroundSync.isChecked) View.VISIBLE else View.GONE
        
        updateAutoSyncStatus()
    }
    
    /**
     * Сохранение настроек автосинхронизации
     */
    private fun saveAutoSyncSettings() {
        AutoSyncSettings.setAutoSyncEnabled(this, switchAutoSync.isChecked)
        AutoSyncSettings.setSyncOnStartupEnabled(this, checkSyncOnStartup.isChecked)
        AutoSyncSettings.setSyncOnSettingsChangeEnabled(this, checkSyncOnSettings.isChecked)
        AutoSyncSettings.setBackgroundSyncEnabled(this, checkBackgroundSync.isChecked)
        
        // Сохранение интервала
        val selectedIntervalIndex = spinnerSyncInterval.selectedItemPosition
        if (selectedIntervalIndex >= 0 && selectedIntervalIndex < AutoSyncSettings.AVAILABLE_INTERVALS.size) {
            val selectedInterval = AutoSyncSettings.AVAILABLE_INTERVALS[selectedIntervalIndex]
            AutoSyncSettings.setSyncIntervalHours(this, selectedInterval)
        }
    }
    
    /**
     * Обновление статуса автосинхронизации
     */
    private fun updateAutoSyncStatus() {
        val summary = AutoSyncSettings.getSettingsSummary(this)
        tvAutoSyncStatus.text = summary
    }
    
    private fun setupEventHandlers() {
        // Обработчик изменения режима сервера
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioInternet -> {
                    editServerUrl.isEnabled = false
                    editServerUrl.setText("http://158.160.157.7/")
                }
                R.id.radioCustom -> {
                    editServerUrl.isEnabled = true
                    if (editServerUrl.text.toString().isEmpty()) {
                        editServerUrl.setText("https://")
                    }
                }
            }
        }
        
        // Кнопка диагностики
        btnDiagnose.setOnClickListener {
            diagnoseNetwork()
        }
        // Кнопка настройки вкладок
        btnTabSettings.setOnClickListener {
            showTabSettingsDialog()
        }
        
        // Кнопка сохранения
        btnSave.setOnClickListener {
            saveSettings()
        }
        
        // Кнопка отмены
        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun setupTitleLongPressHandler() {
        var longPressStartTime = 0L
        var isLongPressing = false
        
        tvSettingsTitle.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    longPressStartTime = System.currentTimeMillis()
                    isLongPressing = true
                    
                    // Запускаем таймер на 5 секунд
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (isLongPressing && System.currentTimeMillis() - longPressStartTime >= 5000) {
                            // Если редактор уже разблокирован, отключаем его
                            if (hasEditorAccess()) {
                                toggleEditorAccess()
                            } else {
                                // Если заблокирован, показываем диалог пароля
                                showPasswordDialog()
                            }
                            isLongPressing = false
                        }
                    }, 5000)
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    isLongPressing = false
                }
            }
            false
        }
    }
    
    private fun showPasswordDialog() {
        val editText = EditText(this)
        editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        
        AlertDialog.Builder(this)
            .setTitle("Введите пароль")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val password = editText.text.toString()
                if (password == ADMIN_PASSWORD) {
                    sharedPrefs.edit().putBoolean(KEY_EDITOR_ACCESS, true).apply()
                    Toast.makeText(this, "Доступ к редактору разблокирован", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Неверный пароль", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun toggleEditorAccess() {
        // Отключаем доступ к редактору
        sharedPrefs.edit().putBoolean(KEY_EDITOR_ACCESS, false).apply()
        
        // Также отключаем Редактор из активных вкладок, если он включен
        val current = loadTabsVisibility()
        if (current[3] == true) {
            val json = sharedPrefs.getString(KEY_TABS_VISIBILITY, null)
            if (json != null && json.trim().startsWith("[")) {
                val gson = com.google.gson.Gson()
                val listType = object : com.google.gson.reflect.TypeToken<List<Int>>() {}.type
                val list = gson.fromJson<List<Int>>(json, listType) ?: emptyList()
                val filteredList = list.filter { it != 3 } // Убираем индекс 3 (Редактор)
                val newJson = gson.toJson(filteredList)
                sharedPrefs.edit().putString(KEY_TABS_VISIBILITY, newJson).apply()
            }
        }
        
        Toast.makeText(this, "Доступ к редактору заблокирован", Toast.LENGTH_SHORT).show()
    }
    
    private fun hasEditorAccess(): Boolean {
        return sharedPrefs.getBoolean(KEY_EDITOR_ACCESS, false)
    }
    
    private fun showTabSettingsDialog() {
        val tabs = mutableListOf<Pair<String, Int>>()
        tabs.add("Сигналы БЩУ" to 0)
        tabs.add("Арматура" to 1)
        tabs.add("Схемы" to 2)
        
        // Добавляем Редактор только если есть доступ
        if (hasEditorAccess()) {
            tabs.add("Редактор" to 3)
        }
        
        tabs.add("График" to 4)
        
        val current = loadTabsVisibility()
        val names = tabs.map { it.first }.toTypedArray()
        val checked = tabs.map { current[it.second] ?: true }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("Настройка вкладок")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Сохранить") { d, _ ->
                val enabled = mutableListOf<Int>()
                tabs.forEachIndexed { idx, pair -> if (checked[idx]) enabled.add(pair.second) }
                saveTabsVisibility(enabled)
                Toast.makeText(this, "Настройки вкладок сохранены", Toast.LENGTH_SHORT).show()
                d.dismiss()
            }
            .setNegativeButton("Отмена") { d, _ -> d.dismiss() }
            .show()
    }

    private fun saveTabsVisibility(enabledList: List<Int>) {
        try {
            val json = com.google.gson.Gson().toJson(enabledList)
            sharedPrefs.edit().putString(KEY_TABS_VISIBILITY, json).apply()
        } catch (_: Throwable) {}
    }

    private fun loadTabsVisibility(): Map<Int, Boolean> {
        return try {
            val json = sharedPrefs.getString(KEY_TABS_VISIBILITY, null)
            
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
            // Попытка 1: список включённых индексов
            if (json.trim().startsWith("[")) {
                val listType = object : com.google.gson.reflect.TypeToken<List<Int>>() {}.type
                val list = gson.fromJson<List<Int>>(json, listType) ?: emptyList()
                val map = mutableMapOf<Int, Boolean>()
                (0..4).forEach { map[it] = list.contains(it) }
                map
            } else {
                // Попытка 2: карта с ключами-строками или числами
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
    
    private fun saveSettings() {
        val serverMode = when {
            radioInternet.isChecked -> MODE_INTERNET
            radioCustom.isChecked -> MODE_CUSTOM
            else -> MODE_INTERNET
        }
        val customUrl = editServerUrl.text.toString().trim()
        
        // Валидация URL
        if (serverMode == MODE_CUSTOM) {
            if (customUrl.isEmpty()) {
                Toast.makeText(this, "Введите URL сервера", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (!isValidUrl(customUrl)) {
                Toast.makeText(this, "Некорректный URL сервера", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        // Сохранение настроек сервера
        sharedPrefs.edit()
            .putString(KEY_SERVER_MODE, serverMode)
            .putString(KEY_CUSTOM_URL, customUrl)
            .apply()
        
        // Сохранение настроек автосинхронизации
        saveAutoSyncSettings()
        
        // Обновление фоновой синхронизации согласно новым настройкам
        updateBackgroundSync()
        
        // Обновление NetworkModule
        updateNetworkModule()
        
        Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show()
        finish()
    }
    
    
    private fun updateNetworkModule() {
        val serverMode = sharedPrefs.getString(KEY_SERVER_MODE, MODE_INTERNET) ?: MODE_INTERNET
        val customUrl = sharedPrefs.getString(KEY_CUSTOM_URL, "") ?: ""
        
        val defaultUrl = "http://158.160.157.7/"
        
        val baseUrl = when (serverMode) {
            MODE_INTERNET -> "http://158.160.157.7/"
            MODE_CUSTOM -> if (customUrl.isNotBlank()) {
                if (!customUrl.endsWith("/")) "$customUrl/" else customUrl
            } else defaultUrl
            else -> defaultUrl
        }
        
        android.util.Log.d("ServerSettingsActivity", "updateNetworkModule: serverMode=$serverMode, baseUrl=$baseUrl")
        
        // Обновляем NetworkModule с новым URL
        NetworkModule.updateBaseUrl(baseUrl)
    }
    
    /**
     * Обновление фоновой синхронизации согласно настройкам
     */
    private fun updateBackgroundSync() {
        if (AutoSyncSettings.isBackgroundSyncEnabled(this)) {
            // Фоновая синхронизация включена - планируем WorkManager
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
                
                android.util.Log.i("ServerSettingsActivity", "Background sync scheduled every $intervalHours hours")
                
            } catch (e: Exception) {
                android.util.Log.w("ServerSettingsActivity", "Failed to schedule periodic sync: ${e.message}")
            }
        } else {
            // Фоновая синхронизация отключена - отменяем все задачи
            WorkManager.getInstance(this).cancelUniqueWork("vkbook_periodic_sync")
            android.util.Log.i("ServerSettingsActivity", "Background sync cancelled - disabled in settings")
        }
    }
    
    private fun isValidUrl(url: String): Boolean {
        return try {
            val parsedUrl = java.net.URL(url)
            val protocol = parsedUrl.protocol
            val host = parsedUrl.host
            
            // Проверяем протокол
            if (protocol !in listOf("http", "https")) {
                return false
            }
            
            // Проверяем hostname
            if (host.isNullOrEmpty()) {
                return false
            }
            
            // Проверяем на подозрительные символы
            if (host.contains("..") || host.contains("//")) {
                return false
            }
            
            // Проверяем длину URL
            if (url.length > 2048) {
                return false
            }
            
            // Проверяем на опасные схемы
            val dangerousSchemes = listOf("file://", "ftp://", "javascript:", "data:")
            if (dangerousSchemes.any { url.startsWith(it, ignoreCase = true) }) {
                return false
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Диагностика сетевого подключения
     */
    private fun diagnoseNetwork() {
        btnDiagnose.isEnabled = false
        btnDiagnose.text = "🔍 Проверяем..."
        
        val currentUrl = getCurrentServerUrl()
        
        executor.execute {
            val results = mutableListOf<String>()
            
            try {
                results.add("🔍 Диагностика сетевого подключения")
                results.add("URL: $currentUrl")
                results.add("")
                
                // 1. Проверка парсинга URL
                results.add("1️⃣ Парсинг URL...")
                val url = URL(currentUrl)
                val host = url.host
                val port = if (url.port != -1) url.port else url.defaultPort
                results.add("   ✅ Хост: $host")
                results.add("   ✅ Порт: $port")
                
                // 2. Проверка DNS резолвинга
                results.add("")
                results.add("2️⃣ DNS резолвинг...")
                try {
                    val address = InetAddress.getByName(host)
                    results.add("   ✅ IP адрес: ${address.hostAddress}")
                } catch (e: Exception) {
                    results.add("   ❌ DNS ошибка: ${e.message}")
                }
                
                // 3. Проверка подключения к порту
                results.add("")
                results.add("3️⃣ Проверка доступности порта...")
                try {
                    Socket().use { socket ->
                        socket.connect(java.net.InetSocketAddress(host, port), 10000)
                        results.add("   ✅ Порт $port доступен")
                    }
                } catch (e: Exception) {
                    results.add("   ❌ Порт $port недоступен")
                    results.add("   Причина: ${e.message}")
                }
                
                // 4. Проверка HTTP ответа через OkHttp (более надежно)
                results.add("")
                results.add("4️⃣ HTTP проверка...")
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    
                    val request = okhttp3.Request.Builder()
                        .url("${currentUrl}actuator/health")
                        .build()
                    
                    val response = client.newCall(request).execute()
                    results.add("   ✅ HTTP ответ: ${response.code}")
                    if (response.isSuccessful) {
                        results.add("   ✅ Сервер отвечает!")
                        val body = response.body?.string()
                        if (body?.contains("UP") == true) {
                            results.add("   ✅ Статус сервера: UP")
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    results.add("   ❌ HTTP ошибка: ${e.message}")
                    
                    // Пробуем основной URL
                    try {
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.connectTimeout = 10000
                        connection.readTimeout = 10000
                        connection.requestMethod = "GET"
                        val responseCode = connection.responseCode
                        results.add("   ℹ️ Основной URL ответ: $responseCode")
                        connection.disconnect()
                    } catch (e2: Exception) {
                        results.add("   ❌ Основной URL недоступен: ${e2.message}")
                    }
                }
                
                // 5. Информация о сети
                results.add("")
                results.add("5️⃣ Информация о сети:")
                try {
                    val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                    val wifiInfo = wifiManager.connectionInfo
                    val ssid = wifiInfo.ssid?.replace("\"", "") ?: "Неизвестно"
                    results.add("   📶 Wi-Fi сеть: $ssid")
                    results.add("   📡 IP устройства: ${getLocalIpAddress()}")
                } catch (e: Exception) {
                    results.add("   ❌ Ошибка получения сетевой информации: ${e.message}")
                }
                
                // 6. Анализ и рекомендации
                results.add("")
                results.add("6️⃣ Анализ настроек:")
                if (host == "158.160.157.7") {
                    results.add("   ✅ Используется правильный VkBook сервер")
                    results.add("   💡 Сервер развернут на Yandex Cloud")
                    results.add("   🌐 Доступен из любой сети")
                } else if (host.contains("192.168") || host.contains("10.0") || host.contains("172.")) {
                    results.add("   ⚠️ Используется локальный IP адрес")
                    results.add("   💡 Может не работать через мобильный интернет")
                    results.add("   🔧 Рекомендуется внешний IP или домен")
                } else {
                    results.add("   ✅ Используется внешний адрес: $host")
                    results.add("   💡 Убедитесь что сервер доступен из интернета")
                }
                
            } catch (e: Exception) {
                results.add("❌ Критическая ошибка диагностики:")
                results.add("   ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
            }
            
            mainHandler.post {
                btnDiagnose.isEnabled = true
                btnDiagnose.text = "🔍 Диагностика сети"
                showDiagnosticResults(results, currentUrl)
            }
        }
    }
    
    private fun getCurrentServerUrl(): String {
        val serverMode = when {
            radioInternet.isChecked -> MODE_INTERNET
            radioCustom.isChecked -> MODE_CUSTOM
            else -> MODE_INTERNET
        }
        val customUrl = editServerUrl.text.toString().trim()
        
        val defaultUrl = "http://158.160.157.7/"
        
        return when (serverMode) {
            MODE_INTERNET -> "http://158.160.157.7/"
            MODE_CUSTOM -> if (customUrl.isNotBlank()) {
                if (!customUrl.endsWith("/")) "$customUrl/" else customUrl
            } else defaultUrl
            else -> defaultUrl
        }
    }
    
    private fun getLocalIpAddress(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val dhcpInfo = wifiManager.dhcpInfo
            val ipAddress = dhcpInfo.ipAddress
            "${(ipAddress and 0xff)}.${(ipAddress shr 8 and 0xff)}.${(ipAddress shr 16 and 0xff)}.${(ipAddress shr 24 and 0xff)}"
        } catch (e: Exception) {
            "Неизвестно"
        }
    }
    
    private fun showDiagnosticResults(results: List<String>, url: String) {
        val message = "🔍 Диагностика сети\n\n" +
                "URL: $url\n\n" +
                results.joinToString("\n")
        
        AlertDialog.Builder(this)
            .setTitle("Результаты диагностики")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setNeutralButton("Копировать") { dialog, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Диагностика", message)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Результаты скопированы", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
    
}

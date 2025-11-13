package com.example.vkbookandroid.settings

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.vkbookandroid.R
import com.example.vkbookandroid.ServerSettingsActivity
import com.example.vkbookandroid.utils.AutoSyncSettings
import com.example.vkbookandroid.network.collectWifiDiagnostics
import java.security.MessageDigest

/**
 * Фрагмент настроек подключения к серверу
 * Переиспользует логику из ServerSettingsActivity
 */
class ConnectionSettingsFragment : Fragment() {
    
    private lateinit var radioGroup: RadioGroup
    private lateinit var radioInternet: RadioButton
    private lateinit var radioCustom: RadioButton
    private lateinit var editServerUrl: EditText
    private lateinit var switchAutoSync: Switch
    private lateinit var layoutAutoSyncDetails: LinearLayout
    private lateinit var checkSyncOnStartup: CheckBox
    private lateinit var checkSyncOnSettings: CheckBox
    private lateinit var checkBackgroundSync: CheckBox
    private lateinit var layoutSyncInterval: LinearLayout
    private lateinit var spinnerSyncInterval: Spinner
    private lateinit var tvAutoSyncStatus: TextView
    private lateinit var btnDiagnose: Button
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.settings_tab_connection, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Инициализация элементов
        radioGroup = view.findViewById(R.id.radioGroupServerMode)
        radioInternet = view.findViewById(R.id.radioInternet)
        radioCustom = view.findViewById(R.id.radioCustom)
        editServerUrl = view.findViewById(R.id.editServerUrl)
        switchAutoSync = view.findViewById(R.id.switchAutoSync)
        layoutAutoSyncDetails = view.findViewById(R.id.layoutAutoSyncDetails)
        checkSyncOnStartup = view.findViewById(R.id.checkSyncOnStartup)
        checkSyncOnSettings = view.findViewById(R.id.checkSyncOnSettings)
        checkBackgroundSync = view.findViewById(R.id.checkBackgroundSync)
        layoutSyncInterval = view.findViewById(R.id.layoutSyncInterval)
        spinnerSyncInterval = view.findViewById(R.id.spinnerSyncInterval)
        tvAutoSyncStatus = view.findViewById(R.id.tvAutoSyncStatus)
        btnDiagnose = view.findViewById(R.id.btnDiagnose)
        
        setupIntervalSpinner()
        loadSettings()
        setupListeners()
        applyAutoSyncVisibility()
    }
    
    private fun loadSettings() {
        val prefs = requireContext().getSharedPreferences("server_settings", android.content.Context.MODE_PRIVATE)
        
        // Загружаем режим сервера
        val mode = prefs.getString("server_mode", "internet")
        if (mode == "custom") {
            radioCustom.isChecked = true
            editServerUrl.isEnabled = true
        } else {
            radioInternet.isChecked = true
            editServerUrl.isEnabled = false
            editServerUrl.setText("http://158.160.157.7/")
        }
        
        // Загружаем URL
        val url = prefs.getString("custom_url", "")
        editServerUrl.setText(url)
        
        // Загружаем настройки автосинхронизации
        switchAutoSync.isChecked = AutoSyncSettings.isAutoSyncEnabled(requireContext())
        checkSyncOnStartup.isChecked = AutoSyncSettings.isSyncOnStartupEnabled(requireContext())
        checkSyncOnSettings.isChecked = AutoSyncSettings.isSyncOnSettingsChangeEnabled(requireContext())
        checkBackgroundSync.isChecked = AutoSyncSettings.isBackgroundSyncEnabled(requireContext())
        
        val currentInterval = AutoSyncSettings.getSyncIntervalHours(requireContext())
        val intervalIndex = AutoSyncSettings.AVAILABLE_INTERVALS.indexOf(currentInterval)
        if (intervalIndex >= 0) {
            spinnerSyncInterval.setSelection(intervalIndex)
        }
        
        // Обновляем статус
        updateAutoSyncStatus()
        applyAutoSyncVisibility()
    }
    
    private fun setupListeners() {
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioInternet -> {
                    editServerUrl.isEnabled = false
                    editServerUrl.setText("http://158.160.157.7/")
                }
                R.id.radioCustom -> {
                    editServerUrl.isEnabled = true
                    if (editServerUrl.text.isNullOrBlank()) {
                        editServerUrl.setText("https://")
                    }
                }
            }
        }
        
        // Переключатель автосинхронизации
        switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                checkSyncOnStartup.isChecked = false
                checkSyncOnSettings.isChecked = false
                checkBackgroundSync.isChecked = false
            }
            applyAutoSyncVisibility()
            updateAutoSyncStatus()
        }
        
        checkSyncOnStartup.setOnCheckedChangeListener { _, _ ->
            updateAutoSyncStatus()
        }
        checkSyncOnSettings.setOnCheckedChangeListener { _, _ ->
            updateAutoSyncStatus()
        }
        checkBackgroundSync.setOnCheckedChangeListener { _, isChecked ->
            applyAutoSyncVisibility()
            updateAutoSyncStatus()
            if (isChecked && spinnerSyncInterval.adapter != null) {
                updateAutoSyncStatus()
            }
        }
        
        spinnerSyncInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateAutoSyncStatus()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // nothing
            }
        }
        
        // Кнопка диагностики - выполняет диагностику сети
        btnDiagnose.setOnClickListener {
            diagnoseNetwork()
        }
    }
    
    /**
     * Сохранение настроек подключения
     */
    fun saveSettings() {
        val prefs = requireContext().getSharedPreferences("server_settings", android.content.Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Сохраняем режим
        val mode = if (radioCustom.isChecked) "custom" else "internet"
        editor.putString("server_mode", mode)
        
        // Сохраняем URL
        editor.putString("custom_url", editServerUrl.text.toString())
        
        editor.apply()
        
        // Сохраняем настройки автосинхронизации
        AutoSyncSettings.setAutoSyncEnabled(requireContext(), switchAutoSync.isChecked)
        AutoSyncSettings.setSyncOnStartupEnabled(requireContext(), checkSyncOnStartup.isChecked)
        AutoSyncSettings.setSyncOnSettingsChangeEnabled(requireContext(), checkSyncOnSettings.isChecked)
        AutoSyncSettings.setBackgroundSyncEnabled(requireContext(), checkBackgroundSync.isChecked)
        
        val selectedIndex = spinnerSyncInterval.selectedItemPosition
        if (selectedIndex in AutoSyncSettings.AVAILABLE_INTERVALS.indices) {
            val selectedHours = AutoSyncSettings.AVAILABLE_INTERVALS[selectedIndex]
            AutoSyncSettings.setSyncIntervalHours(requireContext(), selectedHours)
        }
    }
    
    private fun updateAutoSyncStatus() {
        tvAutoSyncStatus.text = AutoSyncSettings.getSettingsSummary(requireContext())
    }
    
    /**
     * Открыть настройки вкладок
     */
    fun openTabSettings() {
        showTabSettingsDialog()
    }
    
    private fun setupIntervalSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            AutoSyncSettings.AVAILABLE_INTERVALS.map { "$it ч" }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSyncInterval.adapter = adapter
    }

    private fun applyAutoSyncVisibility() {
        val autoSyncEnabled = switchAutoSync.isChecked
        layoutAutoSyncDetails.visibility = if (autoSyncEnabled) View.VISIBLE else View.GONE
        val showInterval = autoSyncEnabled && checkBackgroundSync.isChecked
        layoutSyncInterval.visibility = if (showInterval) View.VISIBLE else View.GONE
    }
    
    // ========================================
    // МЕХАНИКА СКРЫТОГО ДОСТУПА К РЕДАКТОРУ
    // ========================================
    
    companion object {
        private const val KEY_EDITOR_ACCESS = "editor_access_enabled"
        private const val ADMIN_PASSWORD_HASH = "7773b8d2211efb5d382d36f4ea8bc5dd12af0ab8e52ab96783c3b2be8002d786"
        private const val SALT = "VkBook2024"
        private const val KEY_TABS_VISIBILITY = "tabs_visibility_json"
        
        /**
         * Вычислить SHA-256 хеш строки
         */
        private fun calculateSHA256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray())
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
        
        /**
         * Проверить пароль администратора
         */
        private fun verifyPassword(inputPassword: String): Boolean {
            val normalized = inputPassword.trim()
            val hash = calculateSHA256(normalized + SALT)
            return hash == ADMIN_PASSWORD_HASH
        }
    }
    
    /**
     * Настройка обработчика нажатия на букву "Н" в слове "Настройки"
     */
    fun setupSecretPasswordTrigger(titleView: TextView) {
        var longPressStartTime = 0L
        var isLongPressing = false
        
        titleView.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Проверяем, нажали ли на букву "Н" в слове "Настройки"
                    if (isClickOnLetterN(view as TextView, event)) {
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
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    isLongPressing = false
                }
            }
            false
        }
    }
    
    /**
     * Проверяет, нажали ли на букву "Н" в слове "Настройки"
     */
    private fun isClickOnLetterN(textView: TextView, event: android.view.MotionEvent): Boolean {
        val text = textView.text.toString()
        
        // Ищем позицию буквы "Н" в тексте
        val letterNIndex = text.indexOf("Н")
        if (letterNIndex == -1) return false
        
        // Получаем layout текста
        val layout = textView.layout ?: return false
        
        // Получаем координаты буквы "Н"
        val line = layout.getLineForOffset(letterNIndex)
        val startX = layout.getPrimaryHorizontal(letterNIndex)
        val endX = layout.getPrimaryHorizontal(letterNIndex + 1)
        val startY = layout.getLineTop(line).toFloat()
        val endY = layout.getLineBottom(line).toFloat()
        
        // Проверяем, попадает ли точка нажатия в область буквы "Н"
        val clickX = event.x
        val clickY = event.y
        
        return clickX >= startX && clickX <= endX && clickY >= startY && clickY <= endY
    }
    
    private fun showPasswordDialog() {
        val editText = EditText(requireContext())
        editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        
        AlertDialog.Builder(requireContext())
            .setTitle("Введите пароль администратора")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val password = editText.text.toString().trim()
                if (verifyPassword(password)) {
                    val prefs = requireContext().getSharedPreferences("server_settings", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putBoolean(KEY_EDITOR_ACCESS, true).apply()
                    Toast.makeText(requireContext(), "✅ Доступ к редактору разблокирован! Теперь доступна вкладка 'Редактор' в настройках вкладок.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "❌ Неверный пароль", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun toggleEditorAccess() {
        val prefs = requireContext().getSharedPreferences("server_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_EDITOR_ACCESS, false).apply()
        Toast.makeText(requireContext(), "🔒 Доступ к редактору заблокирован", Toast.LENGTH_SHORT).show()
    }
    
    private fun hasEditorAccess(): Boolean {
        val prefs = requireContext().getSharedPreferences("server_settings", android.content.Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_EDITOR_ACCESS, false)
    }
    
    private fun showTabSettingsDialog() {
        val prefs = requireContext().getSharedPreferences("server_settings", android.content.Context.MODE_PRIVATE)
        
        val tabs = mutableListOf<Pair<String, Int>>()
        tabs.add("Сигналы БЩУ" to 0)
        tabs.add("Арматура" to 1)
        tabs.add("Схемы" to 2)
        
        // Добавляем Редактор ТОЛЬКО если есть доступ!
        if (hasEditorAccess()) {
            tabs.add("Редактор" to 3)
        }
        
        tabs.add("График" to 4)
        tabs.add("График проверок" to 5)
        
        val current = loadTabsVisibility(prefs)
        val names = tabs.map { it.first }.toTypedArray()
        val checked = tabs.map { current[it.second] ?: false }.toBooleanArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Настройка вкладок")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Сохранить") { d, _ ->
                val enabled = mutableListOf<Int>()
                tabs.forEachIndexed { idx, pair -> if (checked[idx]) enabled.add(pair.second) }
                saveTabsVisibility(prefs, enabled)
                Toast.makeText(requireContext(), "✅ Настройки вкладок сохранены. Перезапустите приложение.", Toast.LENGTH_LONG).show()
                d.dismiss()
            }
            .setNegativeButton("Отмена") { d, _ -> d.dismiss() }
            .show()
    }
    
    private fun saveTabsVisibility(prefs: SharedPreferences, enabledList: List<Int>) {
        try {
            val json = com.google.gson.Gson().toJson(enabledList)
            prefs.edit().putString(KEY_TABS_VISIBILITY, json).apply()
        } catch (_: Throwable) {}
    }
    
    private fun loadTabsVisibility(prefs: SharedPreferences): Map<Int, Boolean> {
        val defaults = defaultTabsVisibility().toMutableMap()
        return try {
            val json = prefs.getString(KEY_TABS_VISIBILITY, null) ?: return defaults
            val list = com.google.gson.Gson().fromJson(json, Array<Int>::class.java)?.toList()
            if (list != null) {
                (0..5).forEach { defaults[it] = list.contains(it) }
            }
            defaults
        } catch (_: Throwable) {
            defaults
        }
    }

    private fun defaultTabsVisibility(): Map<Int, Boolean> {
        val defaultMap = mutableMapOf<Int, Boolean>()
        (0..5).forEach { defaultMap[it] = false }
        defaultMap[1] = true
        defaultMap[2] = true
        defaultMap[4] = true
        return defaultMap
    }
    
    // ========================================
    // ДИАГНОСТИКА СЕТИ
    // ========================================
    
    private fun diagnoseNetwork() {
        btnDiagnose.isEnabled = false
        btnDiagnose.text = "🔍 Проверяем..."
        
        val currentUrl = getCurrentServerUrl()
        
        Thread {
            val results = mutableListOf<String>()
            
            try {
                results.add("🔍 Диагностика сетевого подключения")
                results.add("URL: $currentUrl")
                results.add("")
                
                // 1. Проверка парсинга URL
                results.add("1️⃣ Парсинг URL...")
                val url = java.net.URL(currentUrl)
                val host = url.host
                val port = if (url.port != -1) url.port else url.defaultPort
                results.add("   ✅ Хост: $host")
                results.add("   ✅ Порт: $port")
                
                // 2. Проверка DNS резолвинга
                results.add("")
                results.add("2️⃣ DNS резолвинг...")
                try {
                    val address = java.net.InetAddress.getByName(host)
                    results.add("   ✅ IP адрес: ${address.hostAddress}")
                } catch (e: Exception) {
                    results.add("   ❌ DNS ошибка: ${e.message}")
                }
                
                // 3. Проверка подключения к порту
                results.add("")
                results.add("3️⃣ Проверка доступности порта...")
                try {
                    java.net.Socket().use { socket ->
                        socket.connect(java.net.InetSocketAddress(host, port), 10000)
                        results.add("   ✅ Порт $port доступен")
                    }
                } catch (e: Exception) {
                    results.add("   ❌ Порт $port недоступен")
                    results.add("   Причина: ${e.message}")
                }
                
                // 4. Проверка HTTP ответа через actuator/health
                results.add("")
                results.add("4️⃣ HTTP проверка...")
                try {
                    val healthUrl = java.net.URL("${currentUrl}actuator/health")
                    val connection = healthUrl.openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("X-API-Key", com.example.vkbookandroid.BuildConfig.API_KEY)
                    
                    val responseCode = connection.responseCode
                    results.add("   ✅ HTTP ответ: $responseCode")
                    
                    if (responseCode == 200) {
                        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                        results.add("   ✅ Сервер отвечает!")
                        if (responseBody.contains("\"status\":\"UP\"")) {
                            results.add("   ✅ Статус сервера: UP")
                        }
                    } else {
                        results.add("   ⚠️ Код ответа: $responseCode")
                    }
                    connection.disconnect()
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
                val wifiDetails = requireContext().collectWifiDiagnostics()
                val ssid = wifiDetails.ssid ?: "Неизвестно"
                results.add("   📶 Wi-Fi сеть: $ssid")
                wifiDetails.ipAddress?.let { ip ->
                    results.add("   📡 IP устройства: $ip")
                }
                
                results.add("")
                results.add("✅ Диагностика завершена")
                
            } catch (e: Exception) {
                results.add("❌ Критическая ошибка: ${e.message}")
            }
            
            requireActivity().runOnUiThread {
                btnDiagnose.isEnabled = true
                btnDiagnose.text = "🔍 Диагностика сети"
                showDiagnosticResults(results, currentUrl)
            }
        }.start()
    }
    
    private fun getCurrentServerUrl(): String {
        val prefs = requireContext().getSharedPreferences("server_settings", android.content.Context.MODE_PRIVATE)
        val mode = prefs.getString("server_mode", "internet")
        val customUrl = prefs.getString("custom_url", "") ?: ""
        val defaultUrl = "http://158.160.157.7/"
        
        return when (mode) {
            "internet" -> "http://158.160.157.7/"
            "custom" -> if (customUrl.isNotBlank()) {
                if (!customUrl.endsWith("/")) "$customUrl/" else customUrl
            } else defaultUrl
            else -> defaultUrl
        }
    }
    
    private fun showDiagnosticResults(results: List<String>, url: String) {
        val message = results.joinToString("\n")
        
        AlertDialog.Builder(requireContext())
            .setTitle("Диагностика подключения")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Копировать") { _, _ ->
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Диагностика", message)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Результаты скопированы", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}


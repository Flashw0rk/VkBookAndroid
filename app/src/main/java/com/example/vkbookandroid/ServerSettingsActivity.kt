package com.example.vkbookandroid

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.vkbookandroid.network.NetworkModule
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors

/**
 * Activity для настройки параметров сервера
 */
class ServerSettingsActivity : AppCompatActivity() {
    
    private lateinit var radioGroup: RadioGroup
    private lateinit var radioInternet: RadioButton
    private lateinit var radioCustom: RadioButton
    private lateinit var editServerUrl: EditText
    private lateinit var btnDiagnose: Button
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    
    private lateinit var sharedPrefs: SharedPreferences
    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    companion object {
        private const val PREFS_NAME = "server_settings"
        private const val KEY_SERVER_MODE = "server_mode"
        private const val KEY_CUSTOM_URL = "custom_url"
        
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
    }
    
    private fun initViews() {
        radioGroup = findViewById(R.id.radioGroupServerMode)
        radioInternet = findViewById(R.id.radioInternet)
        radioCustom = findViewById(R.id.radioCustom)
        editServerUrl = findViewById(R.id.editServerUrl)
        btnDiagnose = findViewById(R.id.btnDiagnose)
        btnSave = findViewById(R.id.btnSaveSettings)
        btnCancel = findViewById(R.id.btnCancel)
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
        
        // Кнопка сохранения
        btnSave.setOnClickListener {
            saveSettings()
        }
        
        // Кнопка отмены
        btnCancel.setOnClickListener {
            finish()
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
        
        // Сохранение настроек
        sharedPrefs.edit()
            .putString(KEY_SERVER_MODE, serverMode)
            .putString(KEY_CUSTOM_URL, customUrl)
            .apply()
        
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

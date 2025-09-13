package com.example.vkbookandroid

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.vkbookandroid.network.NetworkModule

/**
 * Activity для настройки параметров сервера
 */
class ServerSettingsActivity : AppCompatActivity() {
    
    private lateinit var radioGroup: RadioGroup
    private lateinit var radioLocal: RadioButton
    private lateinit var radioCustom: RadioButton
    private lateinit var editServerUrl: EditText
    private lateinit var btnSave: Button
    private lateinit var btnTest: Button
    private lateinit var btnCancel: Button
    
    private lateinit var sharedPrefs: SharedPreferences
    
    companion object {
        private const val PREFS_NAME = "server_settings"
        private const val KEY_SERVER_MODE = "server_mode"
        private const val KEY_CUSTOM_URL = "custom_url"
        
        const val MODE_LOCAL = "local"
        const val MODE_CUSTOM = "custom"
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
        radioLocal = findViewById(R.id.radioLocal)
        radioCustom = findViewById(R.id.radioCustom)
        editServerUrl = findViewById(R.id.editServerUrl)
        btnSave = findViewById(R.id.btnSaveSettings)
        btnTest = findViewById(R.id.btnTestConnection)
        btnCancel = findViewById(R.id.btnCancel)
    }
    
    private fun loadSettings() {
        val serverMode = sharedPrefs.getString(KEY_SERVER_MODE, MODE_LOCAL) ?: MODE_LOCAL
        val customUrl = sharedPrefs.getString(KEY_CUSTOM_URL, "") ?: ""
        
        when (serverMode) {
            MODE_LOCAL -> {
                radioLocal.isChecked = true
                editServerUrl.isEnabled = false
                editServerUrl.setText("http://10.0.2.2:8082/")
            }
            MODE_CUSTOM -> {
                radioCustom.isChecked = true
                editServerUrl.isEnabled = true
                editServerUrl.setText(customUrl)
            }
        }
    }
    
    private fun setupEventHandlers() {
        // Обработчик изменения режима сервера
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioLocal -> {
                    editServerUrl.isEnabled = false
                    editServerUrl.setText("http://10.0.2.2:8082/")
                }
                R.id.radioCustom -> {
                    editServerUrl.isEnabled = true
                    if (editServerUrl.text.toString().isEmpty()) {
                        editServerUrl.setText("https://")
                    }
                }
            }
        }
        
        // Кнопка сохранения
        btnSave.setOnClickListener {
            saveSettings()
        }
        
        // Кнопка тестирования подключения
        btnTest.setOnClickListener {
            testConnection()
        }
        
        // Кнопка отмены
        btnCancel.setOnClickListener {
            finish()
        }
    }
    
    private fun saveSettings() {
        val serverMode = if (radioLocal.isChecked) MODE_LOCAL else MODE_CUSTOM
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
    
    private fun testConnection() {
        val serverMode = if (radioLocal.isChecked) MODE_LOCAL else MODE_CUSTOM
        val testUrl = if (serverMode == MODE_LOCAL) {
            "http://10.0.2.2:8082/"
        } else {
            editServerUrl.text.toString().trim()
        }
        
        if (serverMode == MODE_CUSTOM && testUrl.isEmpty()) {
            Toast.makeText(this, "Введите URL для тестирования", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (serverMode == MODE_CUSTOM && !isValidUrl(testUrl)) {
            Toast.makeText(this, "Некорректный URL", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Показываем индикатор загрузки
        btnTest.isEnabled = false
        btnTest.text = "Тестирование..."
        
        // Тестируем подключение в фоновом потоке
        Thread {
            try {
                val success = kotlinx.coroutines.runBlocking {
                    NetworkModule.testConnection(testUrl)
                }
                
                runOnUiThread {
                    btnTest.isEnabled = true
                    btnTest.text = "Тест подключения"
                    
                    if (success) {
                        Toast.makeText(this, "Подключение успешно!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Ошибка подключения", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnTest.isEnabled = true
                    btnTest.text = "Тест подключения"
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    private fun updateNetworkModule() {
        val serverMode = sharedPrefs.getString(KEY_SERVER_MODE, MODE_LOCAL) ?: MODE_LOCAL
        val customUrl = sharedPrefs.getString(KEY_CUSTOM_URL, "") ?: ""
        
        val baseUrl = when (serverMode) {
            MODE_LOCAL -> "http://10.0.2.2:8082/"
            MODE_CUSTOM -> customUrl
            else -> "http://10.0.2.2:8082/"
        }
        
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
     * Получить текущий URL сервера из настроек
     */
    fun getCurrentServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serverMode = prefs.getString(KEY_SERVER_MODE, MODE_LOCAL) ?: MODE_LOCAL
        val customUrl = prefs.getString(KEY_CUSTOM_URL, "") ?: ""
        
        return when (serverMode) {
            MODE_LOCAL -> "http://10.0.2.2:8082/"
            MODE_CUSTOM -> customUrl
            else -> "http://10.0.2.2:8082/"
        }
    }
}

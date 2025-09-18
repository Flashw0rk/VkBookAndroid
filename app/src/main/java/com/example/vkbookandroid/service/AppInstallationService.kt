package com.example.vkbookandroid.service

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Сервис для создания базовых файлов в filesDir при первом запуске
 * Заменяет необходимость в assets/ папке
 */
class AppInstallationService(private val context: Context) {
    
    private val tag = "AppInstallationService"
    private val prefs = context.getSharedPreferences("app_installation", Context.MODE_PRIVATE)
    private val INSTALLATION_COMPLETE_KEY = "installation_complete"
    
    /**
     * Проверить, нужно ли копировать файлы при первом запуске
     */
    fun needsInitialSetup(): Boolean {
        val isComplete = prefs.getBoolean(INSTALLATION_COMPLETE_KEY, false)
        Log.d(tag, "needsInitialSetup() called - installation complete: $isComplete")
        
        if (isComplete) {
            // Проверяем, действительно ли файлы существуют
            val dataDir = File(context.filesDir, "data")
            val jsonFile = File(dataDir, "armature_coords.json")
            val armaturesFile = File(dataDir, "Armatures.xlsx")
            val bschuFile = File(dataDir, "Oborudovanie_BSCHU.xlsx")
            
            val filesExist = dataDir.exists() && jsonFile.exists() && armaturesFile.exists() && bschuFile.exists()
            Log.d(tag, "Files exist check: dataDir=${dataDir.exists()}, json=${jsonFile.exists()}, armatures=${armaturesFile.exists()}, bschu=${bschuFile.exists()}")
            
            if (!filesExist) {
                Log.w(tag, "Installation marked complete but files missing, will recreate")
                return true
            }
            return false
        }
        return true
    }
    
    /**
     * Выполнить начальную настройку приложения
     */
    fun performInitialSetup(): Boolean {
        return try {
            Log.d(tag, "=== STARTING INITIAL SETUP ===")
            val startTime = System.currentTimeMillis()
            
            // Создаем единую папку data/ для всех файлов
            val dataDir = File(context.filesDir, "data")
            if (!dataDir.exists()) {
                dataDir.mkdirs()
                Log.d(tag, "Created data directory: ${dataDir.absolutePath}")
            } else {
                Log.d(tag, "Data directory already exists: ${dataDir.absolutePath}")
            }
            
            // Создаем минимальный набор базовых файлов для первого запуска
            Log.d(tag, "Creating base files (minimal)...")
            createBaseArmatureCoords()
            createBaseExcelFiles()
            // ВАЖНО: создаём только один PDF "Безымянный-1.pdf"
            createBasePdfScheme("Безымянный-1.pdf", "Базовая схема 1")
            
            // Проверяем, что файлы созданы
            val jsonFile = File(dataDir, "armature_coords.json")
            val armaturesFile = File(dataDir, "Armatures.xlsx")
            val bschuFile = File(dataDir, "Oborudovanie_BSCHU.xlsx")
            
            Log.d(tag, "Files created - JSON: ${jsonFile.exists()}, Armatures: ${armaturesFile.exists()}, BSCHU: ${bschuFile.exists()}")
            
            // Отмечаем установку как завершенную
            prefs.edit().putBoolean(INSTALLATION_COMPLETE_KEY, true).apply()
            
            val endTime = System.currentTimeMillis()
            Log.d(tag, "=== INITIAL SETUP COMPLETED SUCCESSFULLY in ${endTime - startTime}ms ===")
            true
        } catch (e: Exception) {
            Log.e(tag, "Error during initial setup", e)
            false
        }
    }
    
    /**
     * Создать базовые файлы для первого запуска
     */
    // Удалён общий метод createBaseFiles() — логика разнесена по вызовам выше
    
    /**
     * Создать базовый файл armature_coords.json
     */
    private fun createBaseArmatureCoords() {
        val jsonFile = File(context.filesDir, "data/armature_coords.json")
        val baseJson = """
        {
            "Безымянный-1.pdf": {
                "А-0": {
                    "page": 1,
                    "x": 390.0,
                    "y": 329.0,
                    "width": 16.0,
                    "height": 16.0,
                    "zoom": 1.0,
                    "label": "А-0",
                    "comment": "проверка 1",
                    "marker_type": "square:#FF0000:16"
                },
                "А-1/1": {
                    "page": 1,
                    "x": 484.0,
                    "y": 331.0,
                    "width": 16.0,
                    "height": 16.0,
                    "zoom": 1.0,
                    "label": "А-1/1",
                    "comment": "тут есть насос",
                    "marker_type": "square:#0000FF:16"
                },
                "А-1/2": {
                    "page": 1,
                    "x": 781.0,
                    "y": 332.0,
                    "width": 16.0,
                    "height": 16.0,
                    "zoom": 1.0,
                    "label": "А-1/2",
                    "comment": "угол здания",
                    "marker_type": "square:#FFFF4D:16"
                }
            },
            "Безымянный-2.pdf": {
                "А-1/3": {
                    "page": 1,
                    "x": 410.0,
                    "y": 248.0,
                    "width": 30.0,
                    "height": 30.0,
                    "zoom": 1.0,
                    "label": "А-1/3",
                    "comment": "центр здания",
                    "marker_type": "square:#008000:30"
                }
            }
        }
        """.trimIndent()

        jsonFile.writeText(baseJson)
        Log.d(tag, "Created base armature_coords.json with user's format (old format)")
    }
    
    /**
     * Создать базовые Excel файлы
     */
    private fun createBaseExcelFiles() {
        // Создаем базовый Excel файл Armatures.xlsx
        createBaseArmaturesExcel()
        
        // Создаем базовый Excel файл Oborudovanie_BSCHU.xlsx
        createBaseOborudovanieExcel()
    }
    
    /**
     * Создать базовый Excel файл Armatures.xlsx
     */
    private fun createBaseArmaturesExcel() {
        try {
            val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook()
            val sheet = workbook.createSheet("Арматура")
            
            // Создаем заголовки
            val headerRow = sheet.createRow(0)
            val headers = listOf("ID", "Обозначение", "Наименование", "PDF_Схема_и_ID_арматуры")
            
            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
            }
            
            // Создаем базовые данные
            val dataRow = sheet.createRow(1)
            dataRow.createCell(0).setCellValue("A001")
            dataRow.createCell(1).setCellValue("А1")
            dataRow.createCell(2).setCellValue("Базовая арматура")
            dataRow.createCell(3).setCellValue("Безымянный-1.pdf")
            
            // Сохраняем файл
            val file = File(context.filesDir, "data/Armatures.xlsx")
            file.outputStream().use { outputStream ->
                workbook.write(outputStream)
            }
            workbook.close()
            
            Log.d(tag, "Created base Armatures.xlsx")
        } catch (e: Exception) {
            Log.e(tag, "Error creating base Armatures.xlsx", e)
        }
    }
    
    /**
     * Создать базовый Excel файл Oborudovanie_BSCHU.xlsx
     */
    private fun createBaseOborudovanieExcel() {
        try {
            val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook()
            val sheet = workbook.createSheet("Сигналы БЩУ")
            
            // Создаем заголовки
            val headerRow = sheet.createRow(0)
            val headers = listOf("ID", "Сигнал", "Описание", "PDF_Схема_и_ID_арматуры")
            
            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
            }
            
            // Создаем базовые данные
            val dataRow = sheet.createRow(1)
            dataRow.createCell(0).setCellValue("S001")
            dataRow.createCell(1).setCellValue("СИГНАЛ1")
            dataRow.createCell(2).setCellValue("Базовый сигнал БЩУ")
            dataRow.createCell(3).setCellValue("Безымянный-1.pdf")
            
            // Сохраняем файл
            val file = File(context.filesDir, "data/Oborudovanie_BSCHU.xlsx")
            file.outputStream().use { outputStream ->
                workbook.write(outputStream)
            }
            workbook.close()
            
            Log.d(tag, "Created base Oborudovanie_BSCHU.xlsx")
        } catch (e: Exception) {
            Log.e(tag, "Error creating base Oborudovanie_BSCHU.xlsx", e)
        }
    }
    
    /**
     * Создать базовые PDF схемы
     */
    // УДАЛЕНО: генерация второго PDF при установке
    
    
    /**
     * Создать базовую PDF схему
     */
    private fun createBasePdfScheme(filename: String, title: String) {
        try {
            // Проверяем бизнес-правила имен
            if (!com.example.vkbookandroid.utils.FilePolicies.isPdfAllowed(filename)) {
                Log.w(tag, "Skipping disallowed PDF file: $filename")
                return
            }
            // Создаем минимальный PDF файл (заглушку)
            val pdfContent = createMinimalPdfContent(title)
            
            // Создаем только в filesDir/data/ (для приложения)
            val appPdfFile = File(context.filesDir, "data/$filename")
            appPdfFile.writeBytes(pdfContent)
            Log.d(tag, "Created PDF in app data: $filename")
            
        } catch (e: Exception) {
            Log.e(tag, "Error creating PDF scheme: $filename", e)
        }
    }
    
    /**
     * Создать минимальное содержимое PDF файла
     */
    private fun createMinimalPdfContent(title: String): ByteArray {
        // Создаем минимальный PDF файл с заголовком
        val pdfContent = """
            %PDF-1.4
            1 0 obj
            <<
            /Type /Catalog
            /Pages 2 0 R
            >>
            endobj
            
            2 0 obj
            <<
            /Type /Pages
            /Kids [3 0 R]
            /Count 1
            >>
            endobj
            
            3 0 obj
            <<
            /Type /Page
            /Parent 2 0 R
            /MediaBox [0 0 612 792]
            /Contents 4 0 R
            /Resources <<
            /Font <<
            /F1 5 0 R
            >>
            >>
            >>
            endobj
            
            4 0 obj
            <<
            /Length 44
            >>
            stream
            BT
            /F1 24 Tf
            100 700 Td
            ($title) Tj
            ET
            endstream
            endobj
            
            5 0 obj
            <<
            /Type /Font
            /Subtype /Type1
            /BaseFont /Helvetica
            >>
            endobj
            
            xref
            0 6
            0000000000 65535 f 
            0000000009 00000 n 
            0000000058 00000 n 
            0000000115 00000 n 
            0000000274 00000 n 
            0000000368 00000 n 
            trailer
            <<
            /Size 6
            /Root 1 0 R
            >>
            startxref
            465
            %%EOF
        """.trimIndent()
        
        return pdfContent.toByteArray(Charsets.UTF_8)
    }
    
    /**
     * Проверить, установлены ли файлы
     */
    fun areFilesInstalled(): Boolean {
        val dataDir = File(context.filesDir, "data")
        val jsonFile = File(context.filesDir, "data/armature_coords.json")
        
        val excelFiles = listOf("Armatures.xlsx", "Oborudovanie_BSCHU.xlsx")
        val allExcelExist = excelFiles.all { filename ->
            File(dataDir, filename).exists()
        }
        
        // Проверяем только один обязательный PDF
        val pdfFiles = listOf("Безымянный-1.pdf")
        val allPdfExist = pdfFiles.all { filename ->
            File(dataDir, filename).exists()
        }
        
        return allExcelExist && allPdfExist && jsonFile.exists()
    }
    
    /**
     * Сбросить статус установки (для тестирования)
     */
    fun resetInstallationStatus() {
        prefs.edit().remove(INSTALLATION_COMPLETE_KEY).apply()
        Log.d(tag, "Installation status reset")
    }
}

package com.example.vkbookandroid.service

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream

/**
 * Сервис для инициализации файлов при первом запуске
 * Копирует файлы из assets в filesDir для работы без интернета
 */
class FileInitializationService(private val context: Context) {
    
    private val tag = "FileInitializationService"
    
    /**
     * Инициализировать файлы при первом запуске
     */
    fun initializeFilesIfNeeded(): Boolean {
        return try {
            val dataDir = File(context.filesDir, "data")
            
            // Создаем папку
            dataDir.mkdirs()
            
            var initialized = false
            
            // Копируем Excel файлы
            val excelFiles = listOf("Armatures.xlsx", "Oborudovanie_BSCHU.xlsx")
            for (filename in excelFiles) {
                val targetFile = File(dataDir, filename)
                if (!targetFile.exists()) {
                    copyAssetToFile("Databases/$filename", targetFile)
                    initialized = true
                    Log.d(tag, "Initialized Excel file: $filename")
                }
            }
            
            // Копируем JSON файл
            val jsonFile = File(dataDir, "armature_coords.json")
            if (!jsonFile.exists()) {
                copyAssetToFile("armature_coords.json", jsonFile)
                initialized = true
                Log.d(tag, "Initialized JSON file: armature_coords.json")
            }
            
            // Копируем PDF файлы (если есть)
            try {
                val pdfFiles = context.assets.list("Schemes") ?: emptyArray()
                for (filename in pdfFiles) {
                    if (filename.endsWith(".pdf", ignoreCase = true)) {
                        val targetFile = File(dataDir, filename)
                        if (!targetFile.exists()) {
                            copyAssetToFile("Schemes/$filename", targetFile)
                            initialized = true
                            Log.d(tag, "Initialized PDF file: $filename")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "No Schemes folder in assets or error reading it", e)
            }
            
            if (initialized) {
                Log.d(tag, "Files initialized successfully")
            } else {
                Log.d(tag, "All files already exist")
            }
            
            true
        } catch (e: Exception) {
            Log.e(tag, "Error initializing files", e)
            false
        }
    }
    
    /**
     * Копировать файл из assets в filesDir
     */
    private fun copyAssetToFile(assetPath: String, targetFile: File) {
        try {
            val inputStream: InputStream = context.assets.open(assetPath)
            val outputStream = targetFile.outputStream()
            
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d(tag, "Copied $assetPath to ${targetFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(tag, "Error copying $assetPath", e)
            throw e
        }
    }
    
    /**
     * Проверить, инициализированы ли файлы
     */
    fun areFilesInitialized(): Boolean {
        val databasesDir = File(context.filesDir, "Databases")
        val jsonFile = File(context.filesDir, "armature_coords.json")
        
        val excelFiles = listOf("Armatures.xlsx", "Oborudovanie_BSCHU.xlsx")
        val allExcelExist = excelFiles.all { filename ->
            File(databasesDir, filename).exists()
        }
        
        return allExcelExist && jsonFile.exists()
    }
}

package com.example.vkbookandroid.utils

import android.content.Context
import android.util.Log
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Утилита для создания Excel файлов с базовой структурой
 */
object ExcelFileCreator {
    
    private const val TAG = "ExcelFileCreator"
    
    /**
     * Создать файл Oborudovanie_BSCHU.xlsx в assets директории
     */
    fun createBschuExcelFile(context: Context): Boolean {
        return try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Сигналы БЩУ")
            
            // Создаем заголовки
            val headerRow = sheet.createRow(0)
            val headers = arrayOf("Номер", "Наименование", "Тип", "Состояние", "Описание")
            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
                
                // Делаем заголовки жирными
                val cellStyle = workbook.createCellStyle()
                val font = workbook.createFont()
                font.bold = true
                cellStyle.setFont(font)
                cell.cellStyle = cellStyle
            }
            
            // Файл создается пустым, данные загружаются с сервера при синхронизации
            
            // Автоподбор ширины колонок
            headers.indices.forEach { sheet.autoSizeColumn(it) }
            
            // Сохраняем файл в assets
            val assetsDir = File(context.filesDir.parent, "app/src/main/assets")
            if (!assetsDir.exists()) {
                assetsDir.mkdirs()
            }
            
            val file = File(assetsDir, "Oborudovanie_BSCHU.xlsx")
            FileOutputStream(file).use { fileOut ->
                workbook.write(fileOut)
            }
            workbook.close()
            
            Log.i(TAG, "Successfully created Oborudovanie_BSCHU.xlsx")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to create Oborudovanie_BSCHU.xlsx", e)
            false
        }
    }
    
    /**
     * Создать файл Armatures.xlsx в assets директории
     */
    fun createArmatureExcelFile(context: Context): Boolean {
        return try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Арматура")
            
            // Создаем заголовки
            val headerRow = sheet.createRow(0)
            val headers = arrayOf("Арматура", "PDF_Схема_и_ID_арматуры", "Тип", "Состояние", "Описание")
            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
                
                // Делаем заголовки жирными
                val cellStyle = workbook.createCellStyle()
                val font = workbook.createFont()
                font.bold = true
                cellStyle.setFont(font)
                cell.cellStyle = cellStyle
            }
            
            // Файл создается пустым, данные загружаются с сервера при синхронизации
            
            // Автоподбор ширины колонок
            headers.indices.forEach { sheet.autoSizeColumn(it) }
            
            // Сохраняем файл в assets
            val assetsDir = File(context.filesDir.parent, "app/src/main/assets")
            if (!assetsDir.exists()) {
                assetsDir.mkdirs()
            }
            
            val file = File(assetsDir, "Armatures.xlsx")
            FileOutputStream(file).use { fileOut ->
                workbook.write(fileOut)
            }
            workbook.close()
            
            Log.i(TAG, "Successfully created Armatures.xlsx")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to create Armatures.xlsx", e)
            false
        }
    }
    
    /**
     * Создать оба файла
     */
    fun createBothExcelFiles(context: Context): Boolean {
        val bschuResult = createBschuExcelFile(context)
        val armatureResult = createArmatureExcelFile(context)
        return bschuResult && armatureResult
    }
    
    /**
     * Создать Excel файлы в директории filesDir/data/ для немедленного использования
     */
    fun createExcelFilesInDataDir(context: Context): Boolean {
        return try {
            val dataDir = File(context.filesDir, "data")
            if (!dataDir.exists()) {
                dataDir.mkdirs()
            }
            
            // Создаем BSCHU файл
            val bschuFile = File(dataDir, "Oborudovanie_BSCHU.xlsx")
            val bschuWorkbook = XSSFWorkbook()
            val bschuSheet = bschuWorkbook.createSheet("Сигналы БЩУ")
            
            val bschuHeaderRow = bschuSheet.createRow(0)
            val bschuHeaders = arrayOf("Номер", "Наименование", "Тип", "Состояние", "Описание")
            bschuHeaders.forEachIndexed { index, header ->
                bschuHeaderRow.createCell(index).setCellValue(header)
            }
            
            // Файл создается пустым, данные загружаются с сервера при синхронизации
            
            bschuHeaders.indices.forEach { bschuSheet.autoSizeColumn(it) }
            
            FileOutputStream(bschuFile).use { fileOut ->
                bschuWorkbook.write(fileOut)
            }
            bschuWorkbook.close()
            
            // Создаем Armatures файл
            val armatureFile = File(dataDir, "Armatures.xlsx")
            val armatureWorkbook = XSSFWorkbook()
            val armatureSheet = armatureWorkbook.createSheet("Арматура")
            
            val armatureHeaderRow = armatureSheet.createRow(0)
            val armatureHeaders = arrayOf("Арматура", "PDF_Схема_и_ID_арматуры", "Тип", "Состояние", "Описание")
            armatureHeaders.forEachIndexed { index, header ->
                armatureHeaderRow.createCell(index).setCellValue(header)
            }
            
            // Файл создается пустым, данные загружаются с сервера при синхронизации
            
            armatureHeaders.indices.forEach { armatureSheet.autoSizeColumn(it) }
            
            FileOutputStream(armatureFile).use { fileOut ->
                armatureWorkbook.write(fileOut)
            }
            armatureWorkbook.close()
            
            Log.i(TAG, "Successfully created Excel files in data directory")
            Log.i(TAG, "BSCHU file size: ${bschuFile.length()} bytes")
            Log.i(TAG, "Armature file size: ${armatureFile.length()} bytes")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Excel files in data directory", e)
            false
        }
    }
}



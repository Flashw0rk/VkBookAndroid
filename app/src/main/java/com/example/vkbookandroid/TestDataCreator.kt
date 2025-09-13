package com.example.vkbookandroid

import android.content.Context
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileOutputStream
import java.io.IOException

/**
 * Утилита для создания тестовых Excel файлов
 */
object TestDataCreator {
    
    fun createTestExcelFiles(context: Context) {
        try {
            createBschuFile(context)
            createArmaturesFile(context)
            android.util.Log.d("TestDataCreator", "Test Excel files created successfully")
        } catch (e: Exception) {
            android.util.Log.e("TestDataCreator", "Error creating test files", e)
        }
    }
    
    private fun createBschuFile(context: Context) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Сигналы БЩУ")
        
        // Создаем заголовки
        val headerRow = sheet.createRow(0)
        val headers = listOf("Сигнал", "Описание", "Тип", "Значение", "Единица измерения")
        
        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
        }
        
        // Создаем тестовые данные
        val testData = listOf(
            listOf("SIGNAL_001", "Температура котла", "Аналоговый", "75.5", "°C"),
            listOf("SIGNAL_002", "Давление пара", "Аналоговый", "15.2", "МПа"),
            listOf("SIGNAL_003", "Клапан подачи топлива", "Дискретный", "ВКЛ", ""),
            listOf("SIGNAL_004", "Аварийная сигнализация", "Дискретный", "ВЫКЛ", ""),
            listOf("SIGNAL_005", "Расход воды", "Аналоговый", "120.8", "м³/ч"),
            listOf("SIGNAL_006", "Уровень воды в баке", "Аналоговый", "85.2", "%"),
            listOf("SIGNAL_007", "Вентилятор принудительной тяги", "Дискретный", "ВКЛ", ""),
            listOf("SIGNAL_008", "Насос циркуляционный", "Дискретный", "ВКЛ", ""),
            listOf("SIGNAL_009", "Температура дымовых газов", "Аналоговый", "180.5", "°C"),
            listOf("SIGNAL_010", "Содержание кислорода", "Аналоговый", "3.2", "%")
        )
        
        testData.forEachIndexed { rowIndex, rowData ->
            val row = sheet.createRow(rowIndex + 1)
            rowData.forEachIndexed { cellIndex, cellValue ->
                val cell = row.createCell(cellIndex)
                cell.setCellValue(cellValue)
            }
        }
        
        // Автоподбор ширины колонок
        headers.forEachIndexed { index, _ ->
            sheet.autoSizeColumn(index)
        }
        
        // Сохраняем файл
        val file = context.filesDir.resolve("data").apply { mkdirs() }.resolve("Oborudovanie_BSCHU.xlsx")
        FileOutputStream(file).use { outputStream ->
            workbook.write(outputStream)
        }
        workbook.close()
        
        android.util.Log.d("TestDataCreator", "Created BSCHU file: ${file.absolutePath}")
    }
    
    private fun createArmaturesFile(context: Context) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Арматура")
        
        // Создаем заголовки
        val headerRow = sheet.createRow(0)
        val headers = listOf("ID", "Название", "Тип", "Состояние", "Позиция X", "Позиция Y")
        
        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
        }
        
        // Создаем тестовые данные
        val testData = listOf(
            listOf("ARM_001", "Клапан регулирующий", "Регулирующий", "Открыт", "100", "150"),
            listOf("ARM_002", "Клапан предохранительный", "Предохранительный", "Закрыт", "200", "250"),
            listOf("ARM_003", "Задвижка", "Задвижка", "Открыта", "300", "350"),
            listOf("ARM_004", "Кран шаровый", "Шаровый", "Закрыт", "400", "450"),
            listOf("ARM_005", "Обратный клапан", "Обратный", "Открыт", "500", "550"),
            listOf("ARM_006", "Редукционный клапан", "Редукционный", "Открыт", "600", "650"),
            listOf("ARM_007", "Клапан отсечной", "Отсечной", "Закрыт", "700", "750"),
            listOf("ARM_008", "Вентиль", "Вентиль", "Открыт", "800", "850")
        )
        
        testData.forEachIndexed { rowIndex, rowData ->
            val row = sheet.createRow(rowIndex + 1)
            rowData.forEachIndexed { cellIndex, cellValue ->
                val cell = row.createCell(cellIndex)
                cell.setCellValue(cellValue)
            }
        }
        
        // Автоподбор ширины колонок
        headers.forEachIndexed { index, _ ->
            sheet.autoSizeColumn(index)
        }
        
        // Сохраняем файл
        val file = context.filesDir.resolve("data").apply { mkdirs() }.resolve("Armatures.xlsx")
        FileOutputStream(file).use { outputStream ->
            workbook.write(outputStream)
        }
        workbook.close()
        
        android.util.Log.d("TestDataCreator", "Created Armatures file: ${file.absolutePath}")
    }
}




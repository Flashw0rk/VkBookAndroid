package com.example.vkbookandroid.editor

import com.example.vkbookandroid.EditorMarkerOverlayView
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ArmaturesExcelWriter : IArmaturesExcelWriter {
    override fun write(
        file: File,
        pdfName: String?,
        items: List<EditorMarkerOverlayView.EditorMarkerItem>,
        deletedIds: Set<String>
    ) {
        val headers = arrayOf("Арматура", "PDF_Схема_и_ID_арматуры", "Тип", "Состояние", "Описание")

        val workbook = if (file.exists()) {
            runCatching { FileInputStream(file).use { XSSFWorkbook(it) } }.getOrElse { XSSFWorkbook() }
        } else XSSFWorkbook()

        val sheet: XSSFSheet = workbook.getSheet("Арматура") ?: workbook.createSheet("Арматура")

        // Сохраняем ширину колонок из оригинала
        val originalColumnWidths = mutableMapOf<Int, Int>()
        for (i in 0 until headers.size) {
            originalColumnWidths[i] = sheet.getColumnWidth(i)
        }

        // Ensure header row exists with expected headers
        val headerRow: Row = sheet.getRow(0) ?: sheet.createRow(0)
        headers.forEachIndexed { index, header ->
            val cell = headerRow.getCell(index) ?: headerRow.createCell(index)
            if (cell.stringCellValue.isNullOrBlank()) cell.setCellValue(header) else cell.setCellValue(header)
        }

        // Build indices: by link (col 1) and by armature (col 0), включая нормализованный ключ
        val existingIndexByLink = mutableMapOf<String, Int>()
        val existingIndexByArm = mutableMapOf<String, Int>()
        val existingIndexByArmNormalized = mutableMapOf<String, Int>()
        val formatter = DataFormatter(java.util.Locale.getDefault())
        val lastRow = sheet.lastRowNum
        for (r in 1..lastRow) {
            val row = sheet.getRow(r) ?: continue
            val linkVal = formatter.formatCellValue(row.getCell(1)).trim()
            if (linkVal.isNotEmpty()) existingIndexByLink[linkVal] = r
            val armVal = formatter.formatCellValue(row.getCell(0)).trim()
            if (armVal.isNotEmpty()) {
                existingIndexByArm[armVal] = r
                existingIndexByArmNormalized[normalizeArmatureId(armVal)] = r
            }
        }

        // Remove deleted: только очищаем колонку со ссылкой, строку не удаляем
        if (deletedIds.isNotEmpty()) {
            val keysToDelete = if (pdfName.isNullOrBlank()) deletedIds else deletedIds.map { "$pdfName#$it" }.toSet()
            keysToDelete.forEach { k ->
                val idx = existingIndexByLink[k]
                if (idx != null) {
                    val row = sheet.getRow(idx)
                    row?.let {
                        (it.getCell(1) ?: it.createCell(1)).setCellValue("")
                    }
                }
            }
        }

        // Сохраняем стили из заголовков для новых ячеек
        val headerCellStyles = headers.indices.map { idx ->
            headerRow.getCell(idx)?.cellStyle
        }

        // Upsert rows for items
        items.forEach { m ->
            val armatureName = m.label ?: m.id
            val key = (pdfName?.takeIf { it.isNotBlank() }?.let { "$it#${m.id}" }) ?: m.id
            val targetRowIndex = existingIndexByArm[armatureName]
                ?: existingIndexByArmNormalized[normalizeArmatureId(armatureName)]
                ?: existingIndexByLink[key]
                ?: (sheet.lastRowNum + 1).let { idx ->
                    idx
                }
            val row = sheet.getRow(targetRowIndex) ?: sheet.createRow(targetRowIndex)
            
            // Обновляем ячейки с сохранением стилей
            val cell0 = row.getCell(0) ?: row.createCell(0).apply {
                headerCellStyles[0]?.let { cellStyle = workbook.createCellStyle().apply { cloneStyleFrom(it) } }
            }
            cell0.setCellValue(armatureName)
            
            val cell1 = row.getCell(1) ?: row.createCell(1).apply {
                headerCellStyles[1]?.let { cellStyle = workbook.createCellStyle().apply { cloneStyleFrom(it) } }
            }
            cell1.setCellValue(key)
            
            // Тип/Состояние: не перезаписываем существующие
            if (row.getCell(2) == null) {
                row.createCell(2).apply {
                    headerCellStyles[2]?.let { cellStyle = workbook.createCellStyle().apply { cloneStyleFrom(it) } }
                    setCellValue("")
                }
            }
            if (row.getCell(3) == null) {
                row.createCell(3).apply {
                    headerCellStyles[3]?.let { cellStyle = workbook.createCellStyle().apply { cloneStyleFrom(it) } }
                    setCellValue("")
                }
            }
            // Описание: не перезаписываем существующие, НЕ записываем комментарии (они хранятся только в JSON)
            if (row.getCell(4) == null) {
                row.createCell(4).apply {
                    headerCellStyles[4]?.let { cellStyle = workbook.createCellStyle().apply { cloneStyleFrom(it) } }
                    setCellValue("")
                }
            }
        }

        // Восстанавливаем ширину колонок из оригинала
        originalColumnWidths.forEach { (colIndex, width) ->
            if (width > 0) {
                sheet.setColumnWidth(colIndex, width)
            }
        }
        
        // Do not call autoSizeColumn (requires AWT)
        FileOutputStream(file).use { out -> workbook.write(out) }
        workbook.close()
    }

    private fun normalizeArmatureId(input: String): String {
        var s = input.trim()
        s = s.replace("\u2010", "-")
            .replace("\u2011", "-")
            .replace("\u2012", "-")
            .replace("\u2013", "-")
            .replace("\u2014", "-")
            .replace("\u2212", "-")
        s = s.replace(Regex("\\n|\\r"), " ")
        s = s.replace(Regex("\\s*-\\s*"), "-")
        s = s.replace(Regex("\\s+"), " ")
        s = s.lowercase(java.util.Locale.getDefault())
        s = s
            .replace('a', 'а')
            .replace('e', 'е')
            .replace('o', 'о')
            .replace('p', 'р')
            .replace('c', 'с')
            .replace('x', 'х')
            .replace('k', 'к')
            .replace('m', 'м')
            .replace('t', 'т')
            .replace('y', 'у')
            .replace('h', 'н')
            .replace('b', 'в')
        return s
    }
}



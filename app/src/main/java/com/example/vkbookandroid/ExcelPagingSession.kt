package com.example.vkbookandroid

import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.example.pult.RowDataDynamic
import java.io.InputStream
import java.io.IOException

class ExcelPagingSession private constructor(
    private val workbook: XSSFWorkbook,
    private val sheet: XSSFSheet,
    private val headers: List<String>,
    private val mergedRegions: List<CellRangeAddress>
) : PagingSession {

    companion object {
        fun fromInputStream(input: InputStream, sheetName: String): ExcelPagingSession {
            // 🔧 ИСПРАВЛЕНИЕ: НЕ закрываем InputStream преждевременно!
            // input.use { } автоматически закрывает поток, что вызывает NotOfficeXmlFileException
            val wb = XSSFWorkbook(input)
            val sheet = wb.getSheet(sheetName) ?: throw IOException("Лист с именем '" + sheetName + "' не найден.")
            val headers = ArrayList<String>()
            val headerRow = sheet.getRow(0)
            if (headerRow != null) {
                for (cell in headerRow) {
                    headers.add(getCellValueAsString(cell, wb))
                }
            }
            val merged = sheet.mergedRegions as List<CellRangeAddress>
            return ExcelPagingSession(wb, sheet, headers, merged)
        }

        fun getCellValueAsString(cell: org.apache.poi.ss.usermodel.Cell?, workbook: org.apache.poi.ss.usermodel.Workbook): String {
            if (cell == null) return ""
            val formatter = org.apache.poi.ss.usermodel.DataFormatter()
            val evaluator = workbook.creationHelper.createFormulaEvaluator()
            return formatter.formatCellValue(cell, evaluator)
        }
    }

    override fun getHeaders(): List<String> = headers.toList()

    override fun getColumnWidths(): Map<String, Int> {
        val widths = LinkedHashMap<String, Int>()
        val headerRow = sheet.getRow(0)
        if (headerRow != null) {
            for (cell in headerRow) {
                val columnName = Companion.getCellValueAsString(cell, workbook)
                val colIndex = cell.columnIndex
                val excelWidth = sheet.getColumnWidth(colIndex)
                val pixelWidth = (excelWidth * 40.0 / 256).toInt()
                widths[columnName] = kotlin.math.max(200, pixelWidth)
            }
        }
        return widths
    }

    override fun readRange(startRow: Int, rowCount: Int): List<RowDataDynamic> {
        val data = ArrayList<RowDataDynamic>()
        var taken = 0
        val firstDataRowIndex = 1 + startRow
        var rowIndex = firstDataRowIndex
        while (taken < rowCount) {
            val row = sheet.getRow(rowIndex) ?: break
            val rowMap = LinkedHashMap<String, String>()
            for (i in headers.indices) {
                val cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                val value = getMergedCellValue(cell)
                rowMap[headers[i]] = value
            }
            data.add(RowDataDynamic(rowMap))
            taken++
            rowIndex++
        }
        return data
    }

    private fun getMergedCellValue(cell: org.apache.poi.ss.usermodel.Cell?): String {
        if (cell == null) return ""
        for (mergedRegion in mergedRegions) {
            if (mergedRegion.isInRange(cell.rowIndex, cell.columnIndex)) {
                val firstRow = sheet.getRow(mergedRegion.firstRow)
                val firstCell = firstRow.getCell(mergedRegion.firstColumn)
                return Companion.getCellValueAsString(firstCell, workbook)
            }
        }
        return Companion.getCellValueAsString(cell, workbook)
    }

    /**
     * ПРОФЕССИОНАЛЬНОЕ РЕШЕНИЕ: Поиск по всей таблице Excel
     * Ищет по всем строкам, а не только по загруженным в память
     */
    fun searchInAllData(searchQuery: String, columnName: String? = null): List<RowDataDynamic> {
        val normalizedQuery = searchQuery.trim().lowercase()
        if (normalizedQuery.isEmpty()) return emptyList()
        
        val results = mutableListOf<RowDataDynamic>()
        val targetColumnIndex = if (columnName != null) {
            headers.indexOfFirst { it.equals(columnName, ignoreCase = true) }
        } else -1
        
        val firstDataRowIndex = 1 // Пропускаем заголовок
        var rowIndex = firstDataRowIndex
        var processedRows = 0
        val maxSearchRows = 10000 // Защита от бесконечного поиска
        
        while (processedRows < maxSearchRows) {
            val row = sheet.getRow(rowIndex) ?: break
            
            val rowMap = LinkedHashMap<String, String>()
            var hasMatch = false
            
            for (i in headers.indices) {
                val cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                val value = getMergedCellValue(cell)
                rowMap[headers[i]] = value
                
                // Проверяем совпадение
                if (!hasMatch && value.isNotEmpty()) {
                    val cellValue = value.trim().lowercase()
                    val shouldCheckThisCell = targetColumnIndex == -1 || i == targetColumnIndex
                    
                    if (shouldCheckThisCell && cellValue.contains(normalizedQuery)) {
                        hasMatch = true
                    }
                }
            }
            
            if (hasMatch) {
                results.add(RowDataDynamic(rowMap))
            }
            
            rowIndex++
            processedRows++
        }
        
        return results
    }
    
    /**
     * Получает общее количество строк данных в таблице
     */
    fun getTotalDataRows(): Int {
        var count = 0
        var rowIndex = 1 // Пропускаем заголовок
        
        while (true) {
            val row = sheet.getRow(rowIndex)
            if (row == null) break
            
            // Проверяем, что строка не пустая
            var hasData = false
            for (i in headers.indices) {
                val cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                val value = getMergedCellValue(cell)
                if (value.isNotEmpty()) {
                    hasData = true
                    break
                }
            }
            
            if (hasData) {
                count++
            } else {
                // Если встретили пустую строку, останавливаемся
                break
            }
            
            rowIndex++
        }
        
        return count
    }

    override fun close() {
        try { workbook.close() } catch (_: Throwable) {}
    }
}




package com.example.vkbookandroid

import android.content.Context
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.example.pult.ExcelDataManager
import org.example.pult.RowDataDynamic
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.LinkedHashMap
import kotlin.collections.List
import kotlin.collections.Map
import android.util.Log

/**
 * Android-специфичная реализация сервиса для чтения данных из Excel файлов.
 * Работает с InputStream, полученным из AssetManager или ContentResolver.
 */
class AndroidExcelService(private val context: Context) : ExcelDataManager.ExcelDataService {

    private val DATE_FORMAT = SimpleDateFormat("dd.MM.yyyy")

    override fun readHeaders(inputStream: InputStream, sheetName: String): List<String> {
        return inputStream.use {
            val headers = java.util.ArrayList<String>()
            val workbook = XSSFWorkbook(it)
            val sheet = workbook.getSheet(sheetName)
            if (sheet == null) {
                throw IOException("Лист с именем '" + sheetName + "' не найден.")
            }
            val headerRow = sheet.getRow(0)
            if (headerRow != null) {
                for (cell in headerRow) {
                    headers.add(getCellValueAsString(cell, workbook))
                }
            }
            headers.toList()
        }
    }

    override fun getColumnWidths(inputStream: InputStream, sheetName: String): Map<String, Int> {
        return inputStream.use {
            val widths = java.util.LinkedHashMap<String, Int>()
            val workbook = XSSFWorkbook(it)
            val sheet = workbook.getSheet(sheetName)
            Log.d("AndroidExcelService", "Sheet '" + sheetName + "': " + if (sheet == null) "not found" else "found")
            if (sheet == null) {
                throw IOException("Лист с именем '" + sheetName + "' не найден.")
            }           
            val headerRow = sheet.getRow(0)
            Log.d("AndroidExcelService", "Header row: " + if (headerRow == null) "not found" else "found, number of cells: ${headerRow.physicalNumberOfCells}")
            if (headerRow != null) {
                for (cell in headerRow) {
                    val columnName = getCellValueAsString(cell, workbook)
                    val colIndex = cell.columnIndex
                    val excelWidth = sheet.getColumnWidth(colIndex)
                    val pixelWidth = (excelWidth * 40.0 / 256).toInt() // Увеличил множитель до 40.0
                    Log.d("AndroidExcelService", "Column '" + columnName + "' (index " + colIndex + "): raw excelWidth=" + excelWidth + ", calculated pixelWidth (before Math.max)=" + pixelWidth);
                    widths.put(columnName, Math.max(200, pixelWidth)) // Увеличил минимальную ширину до 200
                }
            }
            Log.d("AndroidExcelService", "Final widths map size: ${widths.size}")
            widths.toMap()
        }
    }

    override fun readAllRows(inputStream: InputStream, sheetName: String): List<RowDataDynamic> {
        return inputStream.use {
            val data = java.util.ArrayList<RowDataDynamic>()
            val workbook = XSSFWorkbook(it)
            val sheet = workbook.getSheet(sheetName)
            if (sheet == null) {
                throw IOException("Лист с именем '" + sheetName + "' не найден.")
            }
            val headers = java.util.ArrayList<String>()
            val headerRow = sheet.getRow(0)
            if (headerRow != null) {
                for (cell in headerRow) {
                    headers.add(getCellValueAsString(cell, workbook))
                }
            }
            Log.d("AndroidExcelService", "readAllRows: Headers size: ${headers.size}")
            
            val rowIterator = sheet.iterator()
            if (rowIterator.hasNext()) {
                rowIterator.next() // Пропускаем строку заголовков
            }

            val mergedRegions = sheet.mergedRegions as List<CellRangeAddress> // ВОССТАНОВЛЕНО
            Log.d("AndroidExcelService", "readAllRows: Merged regions size: ${mergedRegions.size}")
            var rowCount = 0
            while (rowIterator.hasNext()) {
                val currentRow = rowIterator.next()
                rowCount++
                Log.d("AndroidExcelService", "readAllRows: Processing row: ${currentRow.rowNum}, current data size: ${data.size}")
                val rowMap = java.util.LinkedHashMap<String, String>()
                for (i in 0 until headers.size) {
                    val cell = currentRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                    val cellValue = getMergedCellValue(cell, mergedRegions, workbook)
                    rowMap.put(headers[i], cellValue)
                }
                Log.d("AndroidExcelService", "readAllRows: Row ${currentRow.rowNum} processed, rowMap size: ${rowMap.size}")
                data.add(RowDataDynamic(rowMap))
            }
            Log.d("AndroidExcelService", "readAllRows: Final data size: ${data.size}")
            data.toList()
        }
    }

    // Чтение диапазона строк (постранично): startRow — номер данных (0 = первая строка после заголовка), rowCount — количество
    fun readRowsRange(inputStream: InputStream, sheetName: String, startRow: Int, rowCount: Int): List<RowDataDynamic> {
        return inputStream.use {
            val data = java.util.ArrayList<RowDataDynamic>()
            val workbook = XSSFWorkbook(it)
            val sheet = workbook.getSheet(sheetName) ?: throw IOException("Лист с именем '" + sheetName + "' не найден.")

            val headers = java.util.ArrayList<String>()
            val headerRow = sheet.getRow(0)
            if (headerRow != null) {
                for (cell in headerRow) {
                    headers.add(getCellValueAsString(cell, workbook))
                }
            }

            val mergedRegions = sheet.mergedRegions as List<CellRangeAddress>
            // Пропускаем заголовок и стартуем со второй строки в файле (index=1)
            var skipped = 0
            var taken = 0
            val rowIterator = sheet.iterator()
            if (rowIterator.hasNext()) rowIterator.next() // skip header
            while (rowIterator.hasNext() && taken < rowCount) {
                val currentRow = rowIterator.next()
                if (skipped < startRow) { skipped++; continue }
                val rowMap = java.util.LinkedHashMap<String, String>()
                for (i in 0 until headers.size) {
                    val cell = currentRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                    val cellValue = getMergedCellValue(cell, mergedRegions, workbook)
                    rowMap[headers[i]] = cellValue
                }
                data.add(RowDataDynamic(rowMap))
                taken++
            }
            data
        }
    }

    /**
     * Вспомогательная функция для получения строкового значения ячейки.
     * Корректно обрабатывает различные типы ячеек (строки, числа, булевы, формулы).
     */
    private fun getCellValueAsString(cell: Cell?, workbook: Workbook): String {
        if (cell == null) {
            return ""
        }
        val formatter = DataFormatter()
        val evaluator = workbook.creationHelper.createFormulaEvaluator()
        return formatter.formatCellValue(cell, evaluator)
    }

    /**
     * Вспомогательная функция для получения значения объединенной ячейки.
     * Возвращает значение верхней левой ячейки объединенного диапазона.
     */
    private fun getMergedCellValue(cell: Cell?, mergedRegions: List<CellRangeAddress>, workbook: Workbook): String {
        if (cell == null) {
            return ""
        }
        for (mergedRegion in mergedRegions) {
            if (mergedRegion.isInRange(cell.rowIndex, cell.columnIndex)) {
                val firstRow = cell.sheet.getRow(mergedRegion.firstRow)
                val firstCell = firstRow.getCell(mergedRegion.firstColumn)
                return getCellValueAsString(firstCell, workbook)
            }
        }
        return getCellValueAsString(cell, workbook)
    }
}

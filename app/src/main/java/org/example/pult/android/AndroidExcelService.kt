package org.example.pult.android

import org.example.pult.ExcelDataManager
import org.example.pult.RowDataDynamic
import org.example.pult.util.ExcelService
import java.io.IOException
import java.io.InputStream
import java.util.stream.Collectors

/**
 * Реализация интерфейса ExcelDataService для Android.
 * Этот класс является "адаптером" между Android-средой и чистым ядром.
 * Он использует ExcelService из модуля :core, чтобы выполнять основную логику.
 */
class AndroidExcelDataService : ExcelDataManager.ExcelDataService {

    // Теперь мы можем вызвать методы readHeaders, readAllRows и getColumnWidths,
    // передавая им InputStream и sheetName, как того требует интерфейс.

    private val excelService = ExcelService() // Создаём экземпляр один раз

    override fun readHeaders(inputStream: InputStream, sheetName: String): List<String> {
        return try {
            excelService.readHeaders(inputStream, sheetName)
        } catch (e: IOException) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun readAllRows(inputStream: InputStream, sheetName: String): List<RowDataDynamic> {
        return try {
            excelService.readAllRows(inputStream, sheetName)
        } catch (e: IOException) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun getColumnWidths(inputStream: InputStream, sheetName: String): Map<String, Int> {
        return try {
            excelService.getColumnWidths(inputStream, sheetName)
        } catch (e: IOException) {
            e.printStackTrace()
            emptyMap()
        }
    }
}
package com.example.vkbookandroid

import android.content.Context
import org.example.pult.ExcelDataManager
import org.example.pult.RowDataDynamic
import java.io.InputStream
import android.util.Log

class AppExcelDataManager(context: Context) {

    private val androidExcelService = AndroidExcelService(context)
    private val excelDataManager = ExcelDataManager(androidExcelService)

    fun getTableHeaders(inputStream: InputStream, sheetName: String): List<String> {
        val javaHeaders = excelDataManager.getTableHeaders(inputStream, sheetName)
        return javaHeaders.map { it }
    }

    fun loadTableData(inputStream: InputStream, sheetName: String): List<RowDataDynamic> {
        val javaData = excelDataManager.loadTableData(inputStream, sheetName)
        return javaData.map { it }
    }

    fun loadTableDataRange(inputStream: InputStream, sheetName: String, startRow: Int, rowCount: Int): List<RowDataDynamic> {
        // Используем AndroidExcelService напрямую, т.к. Core не знает про постраничность
        return androidExcelService.readRowsRange(inputStream, sheetName, startRow, rowCount)
    }

    fun getColumnWidths(inputStream: InputStream, sheetName: String): Map<String, Int> {
        val javaWidths = androidExcelService.getColumnWidths(inputStream, sheetName)
        Log.d("AppExcelDataManager", "Raw widths from AndroidExcelService: $javaWidths")
        val kotlinWidths = mutableMapOf<String, Int>()
        for (entry in javaWidths.entries) {
            kotlinWidths[entry.key] = entry.value
        }
        return kotlinWidths
    }

    fun filterData(data: List<RowDataDynamic>, searchText: String): List<RowDataDynamic> {
        val predicate = excelDataManager.createPredicate(searchText)
        return data.filter { rowData -> predicate.test(rowData) }
    }
}

package com.example.vkbookandroid

import org.example.pult.RowDataDynamic

interface PagingSession {
    fun getHeaders(): List<String>
    fun getColumnWidths(): Map<String, Int>
    fun readRange(startRow: Int, rowCount: Int): List<RowDataDynamic>
    fun close()
}




package com.example.vkbookandroid.search

import org.example.pult.RowDataDynamic

/**
 * Результат поиска с оценкой совпадения
 */
data class SearchResult(
    val data: RowDataDynamic,
    val matchScore: Int,
    val matchedColumn: String? = null,
    val matchedValue: String? = null
) : Comparable<SearchResult> {
    
    override fun compareTo(other: SearchResult): Int {
        // Сортируем по убыванию оценки (высокие оценки первыми)
        return other.matchScore.compareTo(this.matchScore)
    }
}











package com.example.vkbookandroid.search

import androidx.recyclerview.widget.DiffUtil
import org.example.pult.RowDataDynamic

/**
 * DiffUtil.Callback для оптимизации обновлений результатов поиска
 * Сравнивает старые и новые результаты поиска для минимизации перерисовки
 */
class SearchDiffCallback(
    private val oldList: List<SearchResult>,
    private val newList: List<SearchResult>
) : DiffUtil.Callback() {
    
    override fun getOldListSize(): Int = oldList.size
    
    override fun getNewListSize(): Int = newList.size
    
    /**
     * Проверяет, являются ли элементы одним и тем же объектом
     * Используется для определения, нужно ли анимировать перемещение
     */
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]
        
        // Сравниваем по хэш-коду данных строки
        return oldItem.data.hashCode() == newItem.data.hashCode()
    }
    
    /**
     * Проверяет, одинаково ли содержимое элементов
     * Вызывается только если areItemsTheSame вернул true
     */
    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]
        
        // Сравниваем все поля SearchResult
        return oldItem.matchScore == newItem.matchScore &&
                oldItem.matchedColumn == newItem.matchedColumn &&
                oldItem.matchedValue == newItem.matchedValue &&
                areRowDataEqual(oldItem.data, newItem.data)
    }
    
    /**
     * Возвращает изменения в содержимом для частичного обновления
     * Вызывается когда areItemsTheSame = true, но areContentsTheSame = false
     */
    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]
        
        val changes = mutableSetOf<String>()
        
        // Проверяем что изменилось
        if (oldItem.matchScore != newItem.matchScore) {
            changes.add(PAYLOAD_MATCH_SCORE)
        }
        
        if (oldItem.matchedColumn != newItem.matchedColumn) {
            changes.add(PAYLOAD_MATCHED_COLUMN)
        }
        
        if (oldItem.matchedValue != newItem.matchedValue) {
            changes.add(PAYLOAD_MATCHED_VALUE)
        }
        
        if (!areRowDataEqual(oldItem.data, newItem.data)) {
            changes.add(PAYLOAD_ROW_DATA)
        }
        
        return if (changes.isNotEmpty()) changes else null
    }
    
    /**
     * Сравнение данных строк
     */
    private fun areRowDataEqual(oldData: RowDataDynamic, newData: RowDataDynamic): Boolean {
        val oldProperties = oldData.getAllProperties()
        val newProperties = newData.getAllProperties()
        
        if (oldProperties.size != newProperties.size) {
            return false
        }
        
        return oldProperties.indices.all { i ->
            oldProperties[i] == newProperties[i]
        }
    }
    
    companion object {
        // Константы для частичных обновлений
        const val PAYLOAD_MATCH_SCORE = "match_score"
        const val PAYLOAD_MATCHED_COLUMN = "matched_column"
        const val PAYLOAD_MATCHED_VALUE = "matched_value"
        const val PAYLOAD_ROW_DATA = "row_data"
    }
}

/**
 * DiffUtil.Callback для обычных данных (не результатов поиска)
 */
class DataDiffCallback(
    private val oldList: List<RowDataDynamic>,
    private val newList: List<RowDataDynamic>
) : DiffUtil.Callback() {
    
    override fun getOldListSize(): Int = oldList.size
    
    override fun getNewListSize(): Int = newList.size
    
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]
        
        return oldItem.hashCode() == newItem.hashCode()
    }
    
    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldList[oldItemPosition]
        val newItem = newList[newItemPosition]
        
        val oldProperties = oldItem.getAllProperties()
        val newProperties = newItem.getAllProperties()
        
        if (oldProperties.size != newProperties.size) {
            return false
        }
        
        return oldProperties.indices.all { i ->
            oldProperties[i] == newProperties[i]
        }
    }
}

/**
 * Утилитный класс для создания DiffUtil.DiffResult
 */
object DiffUtilHelper {
    
    /**
     * Создание DiffResult для результатов поиска
     */
    fun calculateSearchDiff(
        oldResults: List<SearchResult>,
        newResults: List<SearchResult>
    ): DiffUtil.DiffResult {
        val callback = SearchDiffCallback(oldResults, newResults)
        return DiffUtil.calculateDiff(callback)
    }
    
    /**
     * Создание DiffResult для обычных данных
     */
    fun calculateDataDiff(
        oldData: List<RowDataDynamic>,
        newData: List<RowDataDynamic>
    ): DiffUtil.DiffResult {
        val callback = DataDiffCallback(oldData, newData)
        return DiffUtil.calculateDiff(callback)
    }
    
    /**
     * Асинхронное вычисление DiffResult для больших наборов данных
     */
    suspend fun calculateSearchDiffAsync(
        oldResults: List<SearchResult>,
        newResults: List<SearchResult>
    ): DiffUtil.DiffResult {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            calculateSearchDiff(oldResults, newResults)
        }
    }
    
    /**
     * Асинхронное вычисление DiffResult для обычных данных
     */
    suspend fun calculateDataDiffAsync(
        oldData: List<RowDataDynamic>,
        newData: List<RowDataDynamic>
    ): DiffUtil.DiffResult {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            calculateDataDiff(oldData, newData)
        }
    }
}



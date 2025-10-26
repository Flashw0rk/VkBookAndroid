package com.example.vkbookandroid.search

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import kotlinx.coroutines.*
import org.example.pult.RowDataDynamic
import com.example.vkbookandroid.utils.SearchNormalizer

/**
 * Централизованный менеджер поиска с версионированием данных,
 * кэшированием и автоматической инвалидацией
 */
class SearchManager(private val context: Context) {
    
    // LiveData для UI
    private val _searchResults = MutableLiveData<SearchResults>()
    val searchResults: LiveData<SearchResults> = _searchResults
    
    private val _isSearching = MutableLiveData<Boolean>(false)
    val isSearching: LiveData<Boolean> = _isSearching
    
    private val _searchHistory = MutableLiveData<List<String>>(emptyList())
    val searchHistory: LiveData<List<String>> = _searchHistory
    
    // Компонент поиска: единый персистентный индекс (mmap), без FTS5 и без .so
    private val persistentEngine = PersistentSearchEngine(context)
    private val searchCache = TimestampedCache<String, List<SearchResult>>(
        maxSize = 50,
        maxAge = 30_000L // 30 секунд
    )
    private val searchMetrics = SearchMetrics()
    // Готовность индекса
    private val _isIndexReady = MutableLiveData<Boolean>(false)
    val isIndexReady: LiveData<Boolean> = _isIndexReady
    
    // Версионирование данных
    private var dataVersion: Long = 0
    private var lastIndexVersion: Long = -1
    
    // Scope для корутин
    private val searchScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    data class SearchResults(
        val results: List<SearchResult>,
        val totalCount: Int,
        val searchTime: Long,
        val fromCache: Boolean = false,
        val requestId: Int = -1,
        val normalizedQuery: String = ""
    )
    
    /**
     * Основной метод поиска с версионированием и кэшированием
     */
    suspend fun performSearch(
        query: String,
        data: List<RowDataDynamic>,
        headers: List<String>,
        selectedColumn: String? = null,
        forceRefresh: Boolean = false,
        requestId: Int = 0
    ) {
        if (query.isBlank()) {
            val normalizedQuery = SearchNormalizer.normalizeSearchQuery(query)
            _searchResults.value = SearchResults(emptyList(), 0, 0, fromCache = false, requestId = requestId, normalizedQuery = normalizedQuery)
            return
        }
        
        val normalizedQuery = SearchNormalizer.normalizeSearchQuery(query)
        Log.d("SearchManager", "=== PERFORMING SEARCH ===")
        Log.d("SearchManager", "Original query: '$query'")
        Log.d("SearchManager", "Normalized query: '$normalizedQuery'")
        Log.d("SearchManager", "Data rows: ${data.size}")
        Log.d("SearchManager", "Headers: ${headers.size}")
        
        // Обновляем версию данных (хэш), используем как ключ валидности индекса/кэша
        val currentDataVersion = calculateDataVersion(data)
        val dataChanged = currentDataVersion != dataVersion
        
        if (dataChanged) {
            Log.d("SearchManager", "Data version changed: $dataVersion -> $currentDataVersion")
            dataVersion = currentDataVersion
            invalidateCache()
        }
        
        // Проверяем кэш (только если данные не изменились и не принудительное обновление)
        if (!forceRefresh && !dataChanged) {
            val cachedResult = searchCache.get(normalizedQuery, currentDataVersion)
            if (cachedResult != null) {
                Log.d("SearchManager", "Cache hit for query: '$normalizedQuery'")
                searchMetrics.logCacheHit()
                _searchResults.value = SearchResults(
                    results = cachedResult,
                    totalCount = cachedResult.size,
                    searchTime = 0,
                    fromCache = true,
                    requestId = requestId,
                    normalizedQuery = normalizedQuery
                )
                addToSearchHistory(normalizedQuery)
                return
            }
        }
        
        searchMetrics.logCacheMiss()
        _isSearching.value = true
        val startTime = System.currentTimeMillis()
        
        try {
            // Пересобираем индекс если данные изменились, индекс не готов, либо принудительно
            if (lastIndexVersion != currentDataVersion || forceRefresh || !persistentEngine.isIndexReady()) {
                Log.d("SearchManager", "Rebuilding persistent search index...")
                // КРИТИЧНО: Ждем завершения индексации перед поиском
                withContext(Dispatchers.IO) {
                    persistentEngine.buildIndex(data, headers, currentDataVersion)
                }
                lastIndexVersion = currentDataVersion
                searchMetrics.logIndexRebuild()
                Log.d("SearchManager", "Index rebuild completed")
                _isIndexReady.postValue(true)
            }

            // ПРОВЕРЯЕМ что индекс готов после пересборки
            if (!persistentEngine.isIndexReady()) {
                Log.e("SearchManager", "Persistent search index is not ready after rebuild! Cannot perform search.")
                val stats = persistentEngine.getIndexStats()
                Log.e("SearchManager", "Index stats: $stats")
                _searchResults.value = SearchResults(
                    results = emptyList(),
                    totalCount = 0,
                    searchTime = System.currentTimeMillis() - startTime,
                    fromCache = false,
                    requestId = requestId,
                    normalizedQuery = normalizedQuery
                )
                return
            }

            Log.d("SearchManager", "Searching in persistent index...")
            val matchingIndices = withContext(Dispatchers.IO) {
                val indices = LinkedHashSet<Int>()
                // Базовый поиск по нормализованной строке
                indices.addAll(persistentEngine.search(normalizedQuery))
                // ДОБАВЛЕНО: Поиск по строгим вариантам (учёт а0 ↔ а-0 и др.)
                try {
                    val variants = com.example.vkbookandroid.utils.SearchNormalizer.createSearchVariants(normalizedQuery)
                    for (variant in variants) {
                        if (variant.isNotBlank() && variant != normalizedQuery) {
                            indices.addAll(persistentEngine.search(variant))
                            if (indices.size > 5000) break
                        }
                    }
                } catch (_: Throwable) { }
                indices.toList()
            }
            Log.d("SearchManager", "Search completed: found ${matchingIndices.size} matching indices")
            Log.d("SearchManager", "Matching indices: $matchingIndices")
            
            val results = matchingIndices.mapNotNull { index ->
                data.getOrNull(index)?.let { rowData ->
                    val score = calculateMatchScore(rowData, normalizedQuery, headers, selectedColumn)
                    if (score == 0) null else {
                        val matchedColumn = findMatchedColumn(rowData, normalizedQuery, headers, selectedColumn)
                        val matchedValue = getMatchedValue(rowData, normalizedQuery, headers, selectedColumn)
                        SearchResult(
                            data = rowData,
                            matchScore = score,
                            matchedColumn = matchedColumn,
                            matchedValue = matchedValue
                        )
                    }
                }
            }.sortedByDescending { it.matchScore }
            
            Log.d("SearchManager", "Final results: ${results.size} items")
            
            val searchTime = System.currentTimeMillis() - startTime
            
            // Логируем метрики поиска
            searchMetrics.logSearch(searchTime)
            
            // Кэшируем результат
            searchCache.put(normalizedQuery, results, currentDataVersion)
            
            // Обновляем результаты
            _searchResults.value = SearchResults(
                results = results,
                totalCount = results.size,
                searchTime = searchTime,
                fromCache = false,
                requestId = requestId,
                normalizedQuery = normalizedQuery
            )
            
            addToSearchHistory(normalizedQuery)
            
            Log.d("SearchManager", "Search completed: ${results.size} results in ${searchTime}ms")
            Log.d("SearchManager", searchMetrics.getStats())
            
        } catch (e: Exception) {
            Log.e("SearchManager", "Search error", e)
            _searchResults.value = SearchResults(emptyList(), 0, 0, fromCache = false, requestId = requestId, normalizedQuery = SearchNormalizer.normalizeSearchQuery(query))
        } finally {
            _isSearching.value = false
        }
    }
    
    /**
     * Принудительное обновление поиска при изменении данных
     */
    suspend fun refreshSearch(
        currentQuery: String,
        data: List<RowDataDynamic>,
        headers: List<String>,
        selectedColumn: String? = null
    ) {
        if (currentQuery.isNotEmpty()) {
            performSearch(currentQuery, data, headers, selectedColumn, forceRefresh = true)
        }
    }
    
    /**
     * Уведомление об изменении данных извне
     */
    fun notifyDataChanged() {
        Log.d("SearchManager", "Data change notification received")
        invalidateCache()
        lastIndexVersion = -1 // Помечаем индекс как устаревший
        persistentEngine.invalidateIndex()
        _isIndexReady.postValue(false)
    }
    
    /**
     * Вычисление версии данных на основе содержимого
     */
    private fun calculateDataVersion(data: List<RowDataDynamic>): Long {
        return data.foldIndexed(0L) { index, acc, row ->
            // Используем более надежное хэширование с учетом позиции
            val rowHash = row.getAllProperties().joinToString("").hashCode()
            acc xor ((rowHash.toLong() shl (index % 32)) or (rowHash.toLong() ushr (32 - (index % 32))))
        }
    }
    
    /**
     * Вычисление оценки совпадения для сортировки результатов
     */
    private fun calculateMatchScore(
        rowData: RowDataDynamic,
        query: String,
        headers: List<String>,
        selectedColumn: String?
    ): Int {
        val properties = rowData.getAllProperties()
        var maxScore = 0
        
        // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Если выбрана колонка - ищем ТОЛЬКО в ней
        if (selectedColumn != null) {
            val colIdx = headers.indexOfFirst { it.equals(selectedColumn, ignoreCase = true) }
            if (colIdx >= 0) {
                val value = properties.getOrNull(colIdx)
                if (!value.isNullOrBlank()) {
                    val score = SearchNormalizer.getMatchScore(value, query)
                    if (score > 0) maxScore = score + 200
                }
            }
            return maxScore
        }

        // Если колонка не выбрана - ищем во всех
        headers.forEachIndexed { index, header ->
            val value = properties.getOrNull(index)
            if (!value.isNullOrBlank()) {
                val baseScore = SearchNormalizer.getMatchScore(value, query)
                val armatureBonus = if (header.equals("Арматура", ignoreCase = true)) 100 else 0
                val totalScore = baseScore + armatureBonus
                if (totalScore > 0) maxScore = maxOf(maxScore, totalScore)
            }
        }

        return maxScore
    }
    
    /**
     * Поиск колонки, в которой найдено совпадение
     */
    private fun findMatchedColumn(
        rowData: RowDataDynamic,
        query: String,
        headers: List<String>,
        selectedColumn: String? = null
    ): String? {
        val properties = rowData.getAllProperties()
        
        if (selectedColumn != null) {
            val colIdx = headers.indexOfFirst { it.equals(selectedColumn, ignoreCase = true) }
            if (colIdx >= 0) {
                val value = properties.getOrNull(colIdx)
                if (!value.isNullOrBlank() && SearchNormalizer.matchesSearchVariants(value, query)) {
                    return selectedColumn
                }
            }
            return null
        }
        
        headers.forEachIndexed { index, header ->
            val value = properties.getOrNull(index)
            if (!value.isNullOrBlank() && SearchNormalizer.matchesSearchVariants(value, query)) {
                return header
            }
        }
        
        return null
    }
    
    /**
     * Получение значения, в котором найдено совпадение
     */
    private fun getMatchedValue(
        rowData: RowDataDynamic,
        query: String,
        headers: List<String>,
        selectedColumn: String? = null
    ): String? {
        val properties = rowData.getAllProperties()
        
        if (selectedColumn != null) {
            val colIdx = headers.indexOfFirst { it.equals(selectedColumn, ignoreCase = true) }
            if (colIdx >= 0) {
                val value = properties.getOrNull(colIdx)
                if (!value.isNullOrBlank() && SearchNormalizer.matchesSearchVariants(value, query)) {
                    return value
                }
            }
            return null
        }
        
        headers.forEachIndexed { index, _ ->
            val value = properties.getOrNull(index)
            if (!value.isNullOrBlank() && SearchNormalizer.matchesSearchVariants(value, query)) {
                return value
            }
        }
        
        return null
    }
    
    /**
     * Добавление запроса в историю поиска
     */
    private fun addToSearchHistory(query: String) {
        val currentHistory = _searchHistory.value?.toMutableList() ?: mutableListOf()
        currentHistory.remove(query) // Убираем если уже есть
        currentHistory.add(0, query) // Добавляем в начало
        
        // Ограничиваем размер истории
        if (currentHistory.size > 10) {
            currentHistory.removeAt(currentHistory.size - 1)
        }
        
        _searchHistory.value = currentHistory
    }
    
    /**
     * Инвалидация всего кэша
     */
    private fun invalidateCache() {
        searchCache.invalidateAll()
        Log.d("SearchManager", "Search cache invalidated")
    }
    
    /**
     * Очистка истории поиска
     */
    fun clearHistory() {
        _searchHistory.value = emptyList()
    }
    
    /**
     * Получение статистики поиска
     */
    fun getSearchStats(): String {
        return searchMetrics.getStats()
    }
    
    /**
     * Очистка кэша поиска
     */
    fun clearCache() {
        Log.d("SearchManager", "Clearing search cache")
        searchCache.invalidateAll()
        persistentEngine.clearCache()
        // Помечаем индекс как устаревший, чтобы гарантировать пересборку при следующем поиске
        lastIndexVersion = -1
        _isIndexReady.postValue(false)
    }
    
    /**
     * Очистка ресурсов
     */
    fun cleanup() {
        searchScope.cancel()
        invalidateCache()
        persistentEngine.invalidateIndex()
        _isIndexReady.postValue(false)
    }

    /**
     * Предпрогрев индекса без эмита результатов в UI.
     * Строит индекс для всего переданного набора данных и помечает индекс готовым.
     */
    suspend fun prewarmIndex(
        data: List<RowDataDynamic>,
        headers: List<String>
    ) {
        try {
            val currentDataVersion = calculateDataVersion(data)
            val ready = persistentEngine.isIndexReady()
            if (lastIndexVersion != currentDataVersion || !ready) {
                Log.d("SearchManager", "Prewarming persistent search index...")
                withContext(Dispatchers.IO) {
                    persistentEngine.buildIndex(data, headers, currentDataVersion)
                }
                lastIndexVersion = currentDataVersion
                searchMetrics.logIndexRebuild()
                Log.d("SearchManager", "Prewarm completed")
                _isIndexReady.postValue(true)
            } else {
                Log.d("SearchManager", "Prewarm skipped: index up to date")
                _isIndexReady.postValue(true)
            }
        } catch (e: Exception) {
            Log.e("SearchManager", "Prewarm error", e)
            _isIndexReady.postValue(false)
        }
    }
}

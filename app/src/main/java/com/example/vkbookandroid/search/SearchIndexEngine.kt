package com.example.vkbookandroid.search

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.pult.RowDataDynamic
import java.util.concurrent.atomic.AtomicBoolean
import com.example.vkbookandroid.utils.SearchNormalizer

/**
 * Индексированный поисковик с проверкой целостности и автоматической инвалидацией
 */
class SearchIndexEngine {
    
    // Инвертированный индекс: слово -> множество индексов строк
    private val wordIndex = mutableMapOf<String, MutableSet<Int>>()
    private val isIndexBuilt = AtomicBoolean(false)
    private var indexedDataHash: Long = 0
    private val buildMutex = java.util.concurrent.locks.ReentrantLock()
    
    /**
     * Построение поискового индекса
     */
    suspend fun buildIndex(data: List<RowDataDynamic>, headers: List<String>) {
        withContext(Dispatchers.Default) {
            // СЕРИАЛИЗУЕМ сборку индекса: вместо tryLock ждём завершения текущей сборки
            buildMutex.lock()
            try {
            val startTime = System.currentTimeMillis()
            
            // Вычисляем хэш данных
            val currentDataHash = calculateDataHash(data)
            
            // Пропускаем если данные не изменились
            if (isIndexBuilt.get() && indexedDataHash == currentDataHash) {
                Log.d("SearchIndexEngine", "Index is up to date, skipping rebuild")
                return@withContext
            }
            
            Log.d("SearchIndexEngine", "Building search index for ${data.size} records...")
            
            // Очищаем старый индекс
            wordIndex.clear()
            
            // Строим новый индекс с оптимизацией для больших данных
            var totalWordsIndexed = 0
            var processedRows = 0
            val progressInterval = data.size / 10 // Показываем прогресс каждые 10%
            
            data.forEachIndexed { rowIndex, row ->
                val properties = row.getAllProperties()
                headers.forEachIndexed { columnIndex, header ->
                    val value = properties.getOrNull(columnIndex)
                    if (!value.isNullOrBlank()) {
                        val wordsBefore = wordIndex.size
                        indexWords(value, rowIndex)
                        val wordsAfter = wordIndex.size
                        val newWords = wordsAfter - wordsBefore
                        totalWordsIndexed += newWords
                        
                        // ДИАГНОСТИКА: Логируем индексацию для первых нескольких строк
                        if (rowIndex < 5) {
                            Log.d("SearchIndexEngine", "Row $rowIndex, Column '$header': '$value' -> indexed $newWords new words")
                        }
                    }
                }
                
                processedRows++
                // ОПТИМИЗАЦИЯ: Показываем прогресс для больших баз данных
                if (progressInterval > 0 && processedRows % progressInterval == 0) {
                    val progress = (processedRows * 100) / data.size
                    Log.d("SearchIndexEngine", "Indexing progress: $progress% ($processedRows/${data.size} rows, ${wordIndex.size} unique words)")
                }
            }
            
            // Обновляем состояние
            indexedDataHash = currentDataHash
            isIndexBuilt.set(true)
            
            val buildTime = System.currentTimeMillis() - startTime
            Log.d("SearchIndexEngine", "Index built in ${buildTime}ms. Total words indexed: $totalWordsIndexed, unique words: ${wordIndex.size}")
            
            // ДИАГНОСТИКА: Показываем примеры проиндексированных слов
            val sampleWords = wordIndex.keys.take(20)
            Log.d("SearchIndexEngine", "Sample indexed words: $sampleWords")
            } finally {
                buildMutex.unlock()
            }
        }
    }
    
    /**
     * УМНЫЙ ПОИСК по индексу с поддержкой множественных вариантов запроса
     */
    suspend fun search(query: String): Set<Int> {
        if (!isIndexBuilt.get()) {
            Log.w("SearchIndexEngine", "Index not built, returning empty results")
            return emptySet()
        }
        
        return withContext(Dispatchers.Default) {
            val searchStartTime = System.currentTimeMillis()
            val normalizedQuery = query.lowercase().trim()
            
            Log.d("SearchIndexEngine", "=== SMART SEARCH ENABLED ===")
            Log.d("SearchIndexEngine", "Searching for: '$normalizedQuery' in index with ${wordIndex.size} words")
            
            // ДИАГНОСТИКА: Показываем примеры проиндексированных слов
            val sampleWords = wordIndex.keys.take(20)
            Log.d("SearchIndexEngine", "Sample indexed words: $sampleWords")
            
            // ДИАГНОСТИКА: Показываем все слова содержащие "с" или "с-"
            val wordsWithC = wordIndex.keys.filter { it.contains("с") || it.contains("с-") }
            Log.d("SearchIndexEngine", "Words containing 'с' or 'с-': $wordsWithC")
            
            // ДОПОЛНИТЕЛЬНАЯ ДИАГНОСТИКА: Показываем все слова, начинающиеся с "с"
            val wordsStartingWithC = wordIndex.keys.filter { it.startsWith("с") }
            Log.d("SearchIndexEngine", "Words starting with 'с': $wordsStartingWithC")
            
            // ДОПОЛНИТЕЛЬНАЯ ДИАГНОСТИКА: Показываем все слова, содержащие дефис
            val wordsWithDash = wordIndex.keys.filter { it.contains("-") }
            Log.d("SearchIndexEngine", "Words containing dash: ${wordsWithDash.take(10)}... (${wordsWithDash.size} total)")
            
            // НОВАЯ ДИАГНОСТИКА: Показываем все слова содержащие "а" или "А"
            val wordsWithA = wordIndex.keys.filter { it.contains("а") || it.contains("А") }
            Log.d("SearchIndexEngine", "Words containing 'а' or 'А': $wordsWithA")
            
            // НОВАЯ ДИАГНОСТИКА: Показываем все слова, начинающиеся с "а"
            val wordsStartingWithA = wordIndex.keys.filter { it.startsWith("а") }
            Log.d("SearchIndexEngine", "Words starting with 'а': $wordsStartingWithA")
            
            // УМНЫЙ ПОИСК: Создаем варианты поискового запроса
            val searchVariants = com.example.vkbookandroid.utils.SearchNormalizer.createSearchVariants(query)
            Log.d("SearchIndexEngine", "Search variants: $searchVariants")
            
            val allResults = mutableSetOf<Int>()
            var totalCheckedWords = 0
            
            // Ищем по каждому варианту с приоритизацией
            searchVariants.forEachIndexed { variantIndex, variant ->
                val variantLower = variant.lowercase()
                Log.d("SearchIndexEngine", "Searching variant ${variantIndex + 1}/${searchVariants.size}: '$variant'")
                
                // 1. Точные совпадения (высший приоритет)
                val exactMatches = wordIndex[variantLower]
                if (exactMatches != null && exactMatches.isNotEmpty()) {
                    allResults.addAll(exactMatches)
                    Log.d("SearchIndexEngine", "EXACT MATCH for variant '$variant': ${exactMatches.size} results")
                    return@forEachIndexed // Переходим к следующему варианту
                }
                
                // 2. Поиск по префиксу (высокий приоритет)
                val prefixMatches = mutableSetOf<Int>()
                var checkedWords = 0
                for ((indexedWord, indices) in wordIndex) {
                    checkedWords++
                    if (indexedWord.startsWith(variantLower)) {
                        prefixMatches.addAll(indices)
                        Log.d("SearchIndexEngine", "PREFIX MATCH: '$indexedWord' starts with '$variant'")
                    }
                    
                    // Ограничиваем поиск для производительности
                    if (checkedWords > 5000 && prefixMatches.isNotEmpty()) break
                }
                
                // 2.1. ДОПОЛНИТЕЛЬНО: Поиск по префиксу с учетом дефисов
                // Например, если ищем "с", то найдем "с-20", "с20", "с 20"
                if (prefixMatches.isEmpty() && variantLower.length >= 1) {
                    checkedWords = 0
                    for ((indexedWord, indices) in wordIndex) {
                        checkedWords++
                        // Ищем слова, которые начинаются с буквы + дефис/пробел/ничего + цифры
                        if (indexedWord.matches(Regex("^${Regex.escape(variantLower)}[-\\s]?\\d*$"))) {
                            prefixMatches.addAll(indices)
                            Log.d("SearchIndexEngine", "PREFIX MATCH (with dash): '$indexedWord' matches pattern for '$variant'")
                        }
                        
                        // Ограничиваем поиск для производительности
                        if (checkedWords > 5000 && prefixMatches.isNotEmpty()) break
                    }
                }
                
                // 2.2. ДОПОЛНИТЕЛЬНО: Специальная обработка для "А-0" типа паттернов
                if (prefixMatches.isEmpty() && variantLower.matches(Regex("^[а-яё]\\d$"))) {
                    val letter = variantLower[0].toString()
                    val digit = variantLower[1].toString()
                    
                    Log.d("SearchIndexEngine", "=== LETTER-DIGIT SEARCH ===")
                    Log.d("SearchIndexEngine", "Variant: '$variant', letter: '$letter', digit: '$digit'")
                    
                    checkedWords = 0
                    var foundMatches = 0
                    val matchingWords = mutableListOf<String>()
                    
                    for ((indexedWord, indices) in wordIndex) {
                        checkedWords++
                        // Ищем слова, которые содержат эту букву и цифру в различных комбинациях
                        if (indexedWord.matches(Regex("^$letter[-\\s]?$digit$")) || 
                            indexedWord.matches(Regex("^$letter$digit$")) ||
                            indexedWord.matches(Regex("^$letter $digit$"))) {
                            prefixMatches.addAll(indices)
                            foundMatches++
                            matchingWords.add(indexedWord)
                            Log.d("SearchIndexEngine", "LETTER-DIGIT MATCH: '$indexedWord' matches pattern for '$variant' -> ${indices.size} rows")
                        }
                        
                        // Ограничиваем поиск для производительности
                        if (checkedWords > 5000 && prefixMatches.isNotEmpty()) break
                    }
                    
                    Log.d("SearchIndexEngine", "LETTER-DIGIT search completed:")
                    Log.d("SearchIndexEngine", "  - Checked $checkedWords words")
                    Log.d("SearchIndexEngine", "  - Found $foundMatches matching words")
                    Log.d("SearchIndexEngine", "  - Matching words: $matchingWords")
                    Log.d("SearchIndexEngine", "  - Total rows found: ${prefixMatches.size}")
                }
                
                // 2.2.1. ДОПОЛНИТЕЛЬНАЯ ОБРАБОТКА: Специальный поиск для "А-0" с дефисом
                if (prefixMatches.isEmpty() && variantLower.matches(Regex("^[а-яё]-\\d$"))) {
                    val letter = variantLower[0].toString()
                    val digit = variantLower[2].toString()
                    
                    Log.d("SearchIndexEngine", "=== LETTER-DASH-DIGIT SEARCH ===")
                    Log.d("SearchIndexEngine", "Variant: '$variant', letter: '$letter', digit: '$digit'")
                    
                    checkedWords = 0
                    var foundMatches = 0
                    val matchingWords = mutableListOf<String>()
                    
                    for ((indexedWord, indices) in wordIndex) {
                        checkedWords++
                        // Ищем слова, которые содержат эту букву, дефис и цифру
                        if (indexedWord == "$letter-$digit" || 
                            indexedWord == "$letter$digit" ||
                            indexedWord == "$letter $digit") {
                            prefixMatches.addAll(indices)
                            foundMatches++
                            matchingWords.add(indexedWord)
                            Log.d("SearchIndexEngine", "LETTER-DASH-DIGIT MATCH: '$indexedWord' matches pattern for '$variant' -> ${indices.size} rows")
                        }
                        
                        // Ограничиваем поиск для производительности
                        if (checkedWords > 5000 && prefixMatches.isNotEmpty()) break
                    }
                    
                    Log.d("SearchIndexEngine", "LETTER-DASH-DIGIT search completed:")
                    Log.d("SearchIndexEngine", "  - Checked $checkedWords words")
                    Log.d("SearchIndexEngine", "  - Found $foundMatches matching words")
                    Log.d("SearchIndexEngine", "  - Matching words: $matchingWords")
                    Log.d("SearchIndexEngine", "  - Total rows found: ${prefixMatches.size}")
                }
                
                // 2.2. КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Специальная обработка для поиска "с-"
                // Если ищем "с-", то ищем все слова, которые начинаются с "с" и содержат дефис
                if (prefixMatches.isEmpty() && variantLower.endsWith("-")) {
                    val letter = variantLower.dropLast(1) // Убираем дефис
                    Log.d("SearchIndexEngine", "=== LETTER-DASH SEARCH ===")
                    Log.d("SearchIndexEngine", "Variant: '$variant', letter: '$letter', length: ${letter.length}")
                    Log.d("SearchIndexEngine", "Letter matches pattern [а-яёa-z]: ${letter.matches(Regex("[а-яёa-z]"))}")
                    
                    if (letter.length == 1 && letter.matches(Regex("[а-яёa-z]"))) {
                        checkedWords = 0
                        var foundMatches = 0
                        val matchingWords = mutableListOf<String>()
                        
                        for ((indexedWord, indices) in wordIndex) {
                            checkedWords++
                            // Ищем слова, которые начинаются с этой буквы и содержат дефис
                            if (indexedWord.startsWith(letter) && indexedWord.contains("-")) {
                                prefixMatches.addAll(indices)
                                foundMatches++
                                matchingWords.add(indexedWord)
                                Log.d("SearchIndexEngine", "LETTER-DASH MATCH: '$indexedWord' starts with '$letter' and contains dash -> ${indices.size} rows")
                            }
                            
                            // Ограничиваем поиск для производительности
                            if (checkedWords > 5000 && prefixMatches.isNotEmpty()) break
                        }
                        
                        Log.d("SearchIndexEngine", "LETTER-DASH search completed:")
                        Log.d("SearchIndexEngine", "  - Checked $checkedWords words")
                        Log.d("SearchIndexEngine", "  - Found $foundMatches matching words")
                        Log.d("SearchIndexEngine", "  - Matching words: $matchingWords")
                        Log.d("SearchIndexEngine", "  - Total rows found: ${prefixMatches.size}")
                    } else {
                        Log.d("SearchIndexEngine", "LETTER-DASH pattern not applicable: letter='$letter', length=${letter.length}")
                    }
                }
                
                if (prefixMatches.isNotEmpty()) {
                    allResults.addAll(prefixMatches)
                    Log.d("SearchIndexEngine", "PREFIX MATCHES for variant '$variant': ${prefixMatches.size} results")
                    return@forEachIndexed // Переходим к следующему варианту
                }
                
                // 3. Поиск по содержимому (низкий приоритет, только для коротких запросов)
                if (variant.length <= 3) {
                    val containsMatches = mutableSetOf<Int>()
                    checkedWords = 0
                    for ((indexedWord, indices) in wordIndex) {
                        checkedWords++
                        // Ужесточаем: не допускаем перескока по буквам (например, 'с' не должно находить 'т-114')
                        if (indexedWord.contains(variantLower)) {
                            containsMatches.addAll(indices)
                            Log.d("SearchIndexEngine", "CONTAINS MATCH: '$indexedWord' contains '$variant'")
                        }
                        
                        // Ограничиваем поиск для производительности
                        if (checkedWords > 10000 && containsMatches.isNotEmpty()) break
                    }
                    
                    if (containsMatches.isNotEmpty()) {
                        allResults.addAll(containsMatches)
                        Log.d("SearchIndexEngine", "CONTAINS MATCHES for variant '$variant': ${containsMatches.size} results")
                    }
                }
                
                totalCheckedWords += checkedWords
            }
            
            val searchTime = System.currentTimeMillis() - searchStartTime
            Log.d("SearchIndexEngine", "SMART SEARCH COMPLETED: ${allResults.size} total results in ${searchTime}ms (checked $totalCheckedWords words across ${searchVariants.size} variants)")
            
            return@withContext allResults
        }
    }
    
    /**
     * Извлечение поисковых слов из запроса
     */
    private fun extractSearchWords(query: String): List<String> {
        val words = mutableListOf<String>()
        
        // 1. Разбиваем по разделителям, включая дефис
        val splitWords = query.lowercase()
            .split(Regex("[\\s,.;!?()\\[\\]{}\"'\\-]+"))
            .filter { it.isNotEmpty() }
        
        words.addAll(splitWords)
        
        // 2. ИСПРАВЛЕНИЕ: Добавляем целые фразы типа "с-20" как единое слово
        val wholeWords = query.lowercase()
            .split(Regex("[\\s,.;!?()\\[\\]{}\"']+"))
            .filter { it.isNotEmpty() }
        
        words.addAll(wholeWords)
        
        // 3. ИСПРАВЛЕНИЕ: Разрешаем все непустые слова (включая однобуквенные)
        return words.filter { it.isNotEmpty() }.distinct()
    }
    
    /**
     * Поиск совпадений для одного слова
     */
    private fun findWordMatches(searchWord: String): Set<Int> {
        val results = mutableSetOf<Int>()
        
        // ИСПРАВЛЕНИЕ: Сначала ищем точные совпадения (высший приоритет)
        wordIndex[searchWord]?.let { 
            results.addAll(it)
            Log.d("SearchIndexEngine", "Found exact matches for '$searchWord': ${it.size}")
        }
        
        // Если точных совпадений достаточно (> 0), возвращаем только их для более точного поиска
        if (results.isNotEmpty() && searchWord.length > 1) {
            Log.d("SearchIndexEngine", "Using exact matches only for '$searchWord': ${results.size} results")
            return results
        }
        
        // Для коротких слов или если нет точных совпадений, ищем частичные
        wordIndex.entries.forEach { (indexedWord, indices) ->
            if (indexedWord != searchWord) {
                when {
                    // ИСПРАВЛЕНИЕ: Более строгие условия для частичных совпадений
                    // Слово начинается с поискового термина (только если термин достаточно длинный)
                    searchWord.length >= 2 && indexedWord.startsWith(searchWord) -> {
                        results.addAll(indices)
                        Log.d("SearchIndexEngine", "Found prefix match: '$indexedWord' starts with '$searchWord'")
                    }
                    // Слово содержит поисковый термин как отдельное слово (для коротких терминов)
                    searchWord.length == 1 && indexedWord.contains(searchWord) -> {
                        results.addAll(indices)
                        Log.d("SearchIndexEngine", "Found containment match: '$indexedWord' contains '$searchWord'")
                    }
                }
            }
        }
        
        Log.d("SearchIndexEngine", "Total matches for '$searchWord': ${results.size}")
        return results
    }
    
    /**
     * Оптимизированная индексация слов из текста для больших баз данных
     */
    private fun indexWords(text: String, rowIndex: Int) {
        // ОПТИМИЗАЦИЯ: Предварительная проверка для пустых или очень длинных строк
        if (text.isBlank() || text.length > 1000) {
            return // Пропускаем очень длинные строки для производительности
        }
        
        // Канонизируем и нормализуем значение перед индексацией, чтобы учесть похожие символы/дефисы
        val lowercaseText = SearchNormalizer.normalizeCellValue(text).lowercase()
        
        // ИСПРАВЛЕНИЕ: Правильная индексация для арматуры типа "с-20"
        val allWords = mutableSetOf<String>()
        
        // 1. Индексируем целое слово как есть (например "с-20")
        val wholeWord = lowercaseText.trim()
        if (wholeWord.isNotEmpty() && wholeWord.length <= 50) {
            allWords.add(wholeWord)
        }
        
        // 2. Разбиваем по разделителям БЕЗ дефиса (чтобы "с-20" осталось целым)
        val wordsWithoutDash = lowercaseText
            .split(Regex("[\\s,.;!?()\\[\\]{}\"'_/\\\\]+"))
            .filter { word -> word.isNotEmpty() && word.length <= 50 }
        allWords.addAll(wordsWithoutDash)
        
        // 3. Разбиваем С дефисом для поиска частей "с", "20"
        val wordsWithDash = lowercaseText
            .split(Regex("[\\s,.;!?()\\[\\]{}\"'_/\\\\-]+"))
            .filter { word -> word.isNotEmpty() && word.length <= 50 }
        allWords.addAll(wordsWithDash)
        
        // 4. ДОПОЛНИТЕЛЬНО: Индексируем варианты с разными разделителями
        // Например, для "с-20" также индексируем "с20", "с 20"
        val dashVariants = lowercaseText
            .replace("-", "")
            .replace(Regex("[\\s,.;!?()\\[\\]{}\"'_/\\\\]+"), " ")
            .split(" ")
            .filter { word -> word.isNotEmpty() && word.length <= 50 }
        allWords.addAll(dashVariants)
        
        // 5. КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Индексируем отдельные буквы для поиска по префиксу
        // Например, для "А-0" индексируем "а", "а-", "а-0"
        val letterPattern = Regex("^([а-яёa-z])([-\\s]?)(.*)$", RegexOption.IGNORE_CASE)
        val letterMatch = letterPattern.find(lowercaseText)
        
        if (letterMatch != null) {
            val letter = letterMatch.groupValues[1].lowercase()
            val separator = letterMatch.groupValues[2]
            val rest = letterMatch.groupValues[3]
            
            // Индексируем отдельную букву
            allWords.add(letter)
            
            // Индексируем букву с разделителем (если есть)
            if (separator.isNotEmpty()) {
                allWords.add("$letter$separator")
            }
            
            // Индексируем букву с разделителем и остатком (если есть)
            if (rest.isNotEmpty()) {
                allWords.add("$letter$separator$rest")
            }
            
            // ДИАГНОСТИКА: Логируем индексацию букв
            Log.d("SearchIndexEngine", "Indexed letter variants for '$text': letter='$letter', separator='$separator', rest='$rest'")
        }
        
        // 5.1. ДОПОЛНИТЕЛЬНОЕ ИСПРАВЛЕНИЕ: Специальная обработка для "А-0" типа паттернов
        val letterDigitPattern = Regex("^([а-яё])\\s*-\\s*(\\d)$", RegexOption.IGNORE_CASE)
        val letterDigitMatch = letterDigitPattern.find(lowercaseText)
        
        if (letterDigitMatch != null) {
            val letter = letterDigitMatch.groupValues[1].lowercase()
            val digit = letterDigitMatch.groupValues[2]
            
            // Индексируем все возможные варианты для "А-0"
            allWords.add(letter)           // "а"
            allWords.add("$letter-")       // "а-"
            allWords.add("$letter-$digit") // "а-0"
            allWords.add("$letter$digit")  // "а0"
            allWords.add("$letter $digit") // "а 0"
            
            // ДИАГНОСТИКА: Логируем индексацию для "А-0"
            Log.d("SearchIndexEngine", "=== INDEXING 'А-0' TYPE PATTERN ===")
            Log.d("SearchIndexEngine", "Original text: '$text'")
            Log.d("SearchIndexEngine", "Lowercase text: '$lowercaseText'")
            Log.d("SearchIndexEngine", "Extracted letter: '$letter', digit: '$digit'")
            Log.d("SearchIndexEngine", "Added variants: '$letter', '$letter-', '$letter-$digit', '$letter$digit', '$letter $digit'")
        }
        
        // 6. Индексируем все собранные слова
        allWords.forEach { word ->
            wordIndex.getOrPut(word) { mutableSetOf() }.add(rowIndex)
            // ДИАГНОСТИКА: Логируем индексацию для отладки
            if (word.contains("с") || word.contains("а") || word.contains("а-") || word.contains("А")) {
                Log.d("SearchIndexEngine", "Indexed word '$word' for row $rowIndex from text '$text'")
            }
        }
        
        // ДОПОЛНИТЕЛЬНАЯ ДИАГНОСТИКА: Логируем все слова для "А-0"
        if (text.contains("А-0") || text.contains("а-0")) {
            Log.d("SearchIndexEngine", "=== INDEXING 'А-0' ===")
            Log.d("SearchIndexEngine", "Original text: '$text'")
            Log.d("SearchIndexEngine", "Lowercase text: '$lowercaseText'")
            Log.d("SearchIndexEngine", "All words to index: $allWords")
        }
        
        // 7. ДОПОЛНИТЕЛЬНО: Индексируем очищенный текст если он короткий
        val cleanText = lowercaseText
            .replace(Regex("[^а-яёa-z0-9\\s\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        if (cleanText.isNotEmpty() && cleanText.length <= 100 && !cleanText.contains(" ")) {
            wordIndex.getOrPut(cleanText) { mutableSetOf() }.add(rowIndex)
            if (cleanText.contains("с") || cleanText.contains("а") || cleanText.contains("а-")) {
                Log.d("SearchIndexEngine", "Indexed clean text '$cleanText' for row $rowIndex from text '$text'")
            }
        }
    }
    
    /**
     * Вычисление хэша данных для проверки изменений
     */
    private fun calculateDataHash(data: List<RowDataDynamic>): Long {
        return data.foldIndexed(0L) { index, acc, row ->
            // Используем более надежное хэширование с учетом позиции
            val rowHash = row.getAllProperties().joinToString("").hashCode()
            acc xor ((rowHash.toLong() shl (index % 32)) or (rowHash.toLong() ushr (32 - (index % 32))))
        }
    }
    
    /**
     * Проверка актуальности индекса
     */
    fun validateIndex(data: List<RowDataDynamic>): Boolean {
        if (!isIndexBuilt.get()) return false
        
        val currentDataHash = calculateDataHash(data)
        val isValid = indexedDataHash == currentDataHash
        
        if (!isValid) {
            Log.w("SearchIndexEngine", "Index is invalid (data changed), needs rebuild")
        }
        
        return isValid
    }
    
    /**
     * Проверка готовности индекса
     */
    fun isIndexReady(): Boolean {
        val ready = isIndexBuilt.get() && wordIndex.isNotEmpty()
        Log.d("SearchIndexEngine", "Index ready: $ready (built: ${isIndexBuilt.get()}, entries: ${wordIndex.size})")
        return ready
    }
    
    /**
     * Очистка кэша индекса
     */
    fun clearCache() {
        wordIndex.clear()
        isIndexBuilt.set(false)
        indexedDataHash = 0
        Log.d("SearchIndexEngine", "Search index cache cleared")
    }
    
    /**
     * Инвалидация индекса
     */
    fun invalidateIndex() {
        wordIndex.clear()
        isIndexBuilt.set(false)
        indexedDataHash = 0
        Log.d("SearchIndexEngine", "Search index invalidated")
    }
    
    /**
     * Получение статистики индекса
     */
    fun getIndexStats(): String {
        return if (isIndexBuilt.get()) {
            "Index: ${wordIndex.size} words, hash: $indexedDataHash"
        } else {
            "Index: not built"
        }
    }
    
    /**
     * Проверка готовности индекса
     */
    fun isReady(): Boolean = isIndexBuilt.get()
}

package com.example.vkbookandroid.utils

import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap

/**
 * Утилитный класс для нормализации поисковых запросов
 * Обрабатывает лишние пробелы, запятые, точки и другие символы
 * Оптимизирован с thread-safe кэшированием для улучшения производительности
 */
object SearchNormalizer {
    // ВКЛЮЧЕН СТРОГИЙ РЕЖИМ: минимальный набор вариантов, быстрый и предсказуемый
    private const val STRICT_MODE: Boolean = true
    
    // Thread-safe кэши для оптимизации производительности
    private val normalizeCache = ThreadSafeLruCache<String, String>(200)
    private val variantsCache = ThreadSafeLruCache<String, List<String>>(100)
    private val cellValueCache = ThreadSafeLruCache<String, String>(300)
    
    // Константы для валидации входных данных
    private const val MAX_QUERY_LENGTH = 1000
    private const val MAX_CELL_VALUE_LENGTH = 2000

    // Карты соответствий визуально похожих символов (латиница↔кириллица)
    // Приводим всё к единым формам, чтобы "С-20" == "C-20", "А-0" == "A-0"
    private val latinToCyrillicMap: Map<Char, Char> = mapOf(
        // Заглавные
        'A' to 'А', 'B' to 'В', 'C' to 'С', 'E' to 'Е', 'H' to 'Н', 'K' to 'К',
        'M' to 'М', 'O' to 'О', 'P' to 'Р', 'T' to 'Т', 'X' to 'Х', 'Y' to 'У',
        // Строчные
        'a' to 'а', 'c' to 'с', 'e' to 'е', 'o' to 'о', 'p' to 'р', 'x' to 'х', 'y' to 'у'
    )
    private val cyrillicToLatinMap: Map<Char, Char> = mapOf(
        // Заглавные
        'А' to 'A', 'В' to 'B', 'С' to 'C', 'Е' to 'E', 'Н' to 'H', 'К' to 'K',
        'М' to 'M', 'О' to 'O', 'Р' to 'P', 'Т' to 'T', 'Х' to 'X', 'У' to 'Y',
        // Строчные
        'а' to 'a', 'с' to 'c', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'х' to 'x', 'у' to 'y'
    )
    // Вариации дефисов, которые приводим к обычному '-'
    private val dashChars = charArrayOf('\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2212')

    // Преобразование визуально похожих символов и унификация дефисов/пробелов
    private fun canonicalizeConfusables(input: String): String {
        if (input.isEmpty()) return input
        val builder = StringBuilder(input.length)
        for (ch in input) {
            when {
                // Разные варианты дефисов → '-'
                dashChars.contains(ch) -> builder.append('-')
                ch == '\u00A0' -> builder.append(' ') // неразрывный пробел → обычный пробел
                ch == 'ё' -> builder.append('е')
                ch == 'Ё' -> builder.append('Е')
                // Латиница в кириллицу (для единого канона)
                latinToCyrillicMap.containsKey(ch) -> builder.append(latinToCyrillicMap[ch])
                else -> builder.append(ch)
            }
        }
        // Схлопываем множественные пробелы
        return builder.toString().replace(Regex("\\s+"), " ").trim()
    }

    // Генерируем дополнительные варианты с заменой алфавита (латиница/кириллица)
    private fun addConfusableAlphabetVariants(variants: Collection<String>): List<String> {
        val result = LinkedHashSet<String>(variants.size * 3)
        for (v in variants) {
            result.add(v)
            // Вариант, где похожие кириллические буквы заменены на латинские
            val toLatin = buildString(v.length) {
                v.forEach { ch -> append(cyrillicToLatinMap[ch] ?: ch) }
            }
            result.add(toLatin)
            // Вариант, где похожие латинские буквы заменены на кириллические
            val toCyr = buildString(v.length) {
                v.forEach { ch -> append(latinToCyrillicMap[ch] ?: ch) }
            }
            result.add(toCyr)
        }
        return result.toList()
    }
    
    /**
     * Нормализует поисковый запрос для лучшего поиска
     * Убирает лишние пробелы, запятые, точки и приводит к единому формату
     * Использует thread-safe кэширование для повышения производительности
     * Валидирует входные данные для предотвращения проблем с производительностью
     */
    fun normalizeSearchQuery(query: String): String {
        if (query.isBlank()) return ""
        
        // ИСПРАВЛЕНИЕ: Валидация входных данных
        val validatedQuery = validateInput(query, MAX_QUERY_LENGTH, "Search query")
        val actualQuery = validatedQuery ?: query
        
        // Проверяем кэш (используем оригинальный query как ключ)
        normalizeCache.get(query)?.let { return it }
        
        // Вычисляем если не в кэше (используем валидированный query)
        // Канонизируем похожие символы, дефисы и пробелы до нормализации
        val canonical = canonicalizeConfusables(actualQuery)
        val normalized = canonical.trim()
            // Игнорируем все знаки пунктуации, КРОМЕ '-' и '/' (они значимы для арматур)
            .replace(Regex("[\\s\\p{Punct}&&[^-/]]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        // Кэшируем результат
        normalizeCache.put(query, normalized)
        return normalized
    }
    
    /**
     * УМНЫЙ ПОИСК: Создает множественные варианты поискового запроса для лучшего поиска
     * Включает нормализацию, варианты с дефисами, без пробелов и другие оптимизации
     */
    fun createSearchVariants(query: String): List<String> {
        val normalized = normalizeSearchQuery(query)
        if (normalized.isBlank()) return emptyList()
        
        // Проверяем кэш вариантов
        variantsCache.get(query)?.let { return it }
        
        val variants = if (STRICT_MODE) {
            createStrictVariants(normalized)
        } else {
            var v = buildSearchVariants(normalized)
            v = addConfusableAlphabetVariants(v)
            v
        }
        
        variantsCache.put(query, variants)
        return variants
    }

    /**
     * Строгие варианты поиска: быстрые и предсказуемые
     * - канонизация уже выполнена в normalizeSearchQuery/normalizeCellValue
     * - игнорируем пробелы/запятые/точки; дефис считаем значимым
     * - поддерживаем паттерны: "с-", "с-20", "а-0"
     */
    private fun createStrictVariants(normalized: String): List<String> {
        val base = mutableListOf<String>()
        base.add(normalized)
        if (normalized.lowercase() != normalized) base.add(normalized.lowercase())
        
        // Вариант без пробелов/знаков препинания (дефис оставляем)
        val noSpacesPunct = normalized
            .replace(Regex("[\\s\\p{Punct}&&[^-/]]+"), "")
            .trim()
        if (noSpacesPunct.isNotEmpty() && noSpacesPunct != normalized) base.add(noSpacesPunct)
        if (noSpacesPunct.isNotEmpty() && noSpacesPunct.lowercase() != noSpacesPunct) base.add(noSpacesPunct.lowercase())

        // Буква-цифры / буква-дефис-цифры: с20/с-20, а0/а-0, с-
        // (уже в каноне: кириллица и дефисы унифицированы)
        val letterNumber = Regex("^([а-яё])[- ]?(\\d+)$", RegexOption.IGNORE_CASE)
        val letterDashOnly = Regex("^([а-яё])-$", RegexOption.IGNORE_CASE)
        val letterDashDigit = Regex("^([а-яё])-(\\d+)$", RegexOption.IGNORE_CASE)

        when {
            letterDashDigit.matches(normalized) -> {
                val m = letterDashDigit.find(normalized)!!
                val letter = m.groupValues[1].lowercase()
                val num = m.groupValues[2]
                base.add("$letter-$num")
                base.add("$letter$num")
                base.add("$letter $num")
            }
            letterNumber.matches(normalized) -> {
                val m = letterNumber.find(normalized)!!
                val letter = m.groupValues[1].lowercase()
                val num = m.groupValues[2]
                base.add("$letter-$num")
                base.add("$letter$num")
                base.add("$letter $num")
            }
            letterDashOnly.matches(normalized) -> {
                val m = letterDashOnly.find(normalized)!!
                val letter = m.groupValues[1].lowercase()
                base.add("$letter-")
                base.add(letter)
            }
        }

        val result = base.distinct().filter { it.isNotBlank() }
        android.util.Log.d("SearchNormalizer", "STRICT SEARCH: ${result.size} variants for '$normalized': $result")
        return result
    }
    
    /**
     * Внутренний метод для построения вариантов поиска
     */
    private fun buildSearchVariants(normalized: String): List<String> {
        val variants = mutableListOf<String>()
        
        android.util.Log.d("SearchNormalizer", "=== BUILDING SEARCH VARIANTS ===")
        android.util.Log.d("SearchNormalizer", "Input: '$normalized'")
        android.util.Log.d("SearchNormalizer", "Length: ${normalized.length}")
        android.util.Log.d("SearchNormalizer", "Contains dash: ${normalized.contains("-")}")
        android.util.Log.d("SearchNormalizer", "Ends with dash: ${normalized.endsWith("-")}")
        
        // 1. Нормализованный запрос
        variants.add(normalized)
        
        // 2. Запрос без пробелов (для поиска слитных слов)
        val withoutSpaces = normalized.replace(" ", "")
        if (withoutSpaces != normalized && withoutSpaces.isNotEmpty()) {
            variants.add(withoutSpaces)
        }
        
        // 3. Запрос в нижнем регистре
        val lowercase = normalized.lowercase()
        if (lowercase != normalized) {
            variants.add(lowercase)
        }
        if (withoutSpaces != normalized && withoutSpaces.isNotEmpty()) {
            variants.add(withoutSpaces.lowercase())
        }
        
        // 4. Убираем все знаки препинания для поиска
        val withoutPunctuation = normalized.replace(Regex("[^а-яёa-z0-9\\s-]", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (withoutPunctuation != normalized && withoutPunctuation.isNotEmpty()) {
            variants.add(withoutPunctuation)
            val punctuationLowercase = withoutPunctuation.lowercase()
            if (punctuationLowercase != withoutPunctuation) {
                variants.add(punctuationLowercase)
            }
        }
        
        // 5. УЛУЧШЕННАЯ обработка для поиска арматуры типа "с-20", "с20", "с 20", "А-0"
        val armaturePattern = Regex("([а-яё])\\s*-?\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val armatureMatch = armaturePattern.find(normalized)
        
        if (armatureMatch != null) {
            val letter = armatureMatch.groupValues[1].lowercase()
            val number = armatureMatch.groupValues[2]
            
            // Создаем все возможные варианты для арматуры
            val armatureVariants = listOf(
                "$letter-$number",      // с-20, а-0
                "$letter$number",       // с20, а0
                "$letter $number",      // с 20, а 0
                "$letter-$number".uppercase(),  // С-20, А-0
                "$letter$number".uppercase(),   // С20, А0
                "$letter $number".uppercase()   // С 20, А 0
            )
            
            variants.addAll(armatureVariants)
            android.util.Log.d("SearchNormalizer", "Added armature variants for '$normalized': $armatureVariants")
        }
        
        // 5.1. ДОПОЛНИТЕЛЬНАЯ обработка для поиска "А-0" (буква-цифра)
        val letterDigitPattern = Regex("([а-яё])\\s*-\\s*(\\d)", RegexOption.IGNORE_CASE)
        val letterDigitMatch = letterDigitPattern.find(normalized)
        
        if (letterDigitMatch != null) {
            val letter = letterDigitMatch.groupValues[1].lowercase()
            val digit = letterDigitMatch.groupValues[2]
            
            android.util.Log.d("SearchNormalizer", "=== LETTER-DIGIT PATTERN MATCH ===")
            android.util.Log.d("SearchNormalizer", "Input: '$normalized'")
            android.util.Log.d("SearchNormalizer", "Extracted letter: '$letter', digit: '$digit'")
            
            // Создаем варианты для поиска арматуры типа "А-0"
            val letterDigitVariants = listOf(
                "$letter-$digit",       // а-0
                "$letter$digit",        // а0
                "$letter $digit",       // а 0
                "$letter-$digit".uppercase(),  // А-0
                "$letter$digit".uppercase(),   // А0
                "$letter $digit".uppercase()   // А 0
            )
            
            variants.addAll(letterDigitVariants)
            android.util.Log.d("SearchNormalizer", "Added letter-digit variants for '$normalized': $letterDigitVariants")
        } else {
            android.util.Log.d("SearchNormalizer", "Letter-digit pattern did not match for: '$normalized'")
        }
        
        // 6. ДОПОЛНИТЕЛЬНАЯ обработка для поиска только буквы с дефисом (например "с-")
        val letterDashPattern = Regex("([а-яё])\\s*-\\s*$", RegexOption.IGNORE_CASE)
        val letterDashMatch = letterDashPattern.find(normalized)
        
        if (letterDashMatch != null) {
            val letter = letterDashMatch.groupValues[1].lowercase()
            
            // Создаем варианты для поиска арматуры, начинающейся с этой буквы
            val letterDashVariants = listOf(
                "$letter-",             // с-
                "$letter",              // с
                "$letter-".uppercase(), // С-
                "$letter".uppercase()   // С
            )
            
            variants.addAll(letterDashVariants)
            android.util.Log.d("SearchNormalizer", "Added letter-dash variants for '$normalized': $letterDashVariants")
        }
        
        // 6.1. КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Обработка для поиска "с-" (без пробелов)
        val letterDashNoSpacesPattern = Regex("^([а-яё])\\s*-\\s*$", RegexOption.IGNORE_CASE)
        val letterDashNoSpacesMatch = letterDashNoSpacesPattern.find(normalized)
        
        if (letterDashNoSpacesMatch != null) {
            val letter = letterDashNoSpacesMatch.groupValues[1].lowercase()
            
            android.util.Log.d("SearchNormalizer", "=== LETTER-DASH-NO-SPACES PATTERN MATCH ===")
            android.util.Log.d("SearchNormalizer", "Input: '$normalized'")
            android.util.Log.d("SearchNormalizer", "Extracted letter: '$letter'")
            
            // Создаем варианты для поиска арматуры, начинающейся с этой буквы
            val letterDashNoSpacesVariants = listOf(
                "$letter-",             // с-
                "$letter",              // с
                "$letter-".uppercase(), // С-
                "$letter".uppercase()   // С
            )
            
            variants.addAll(letterDashNoSpacesVariants)
            android.util.Log.d("SearchNormalizer", "Added letter-dash-no-spaces variants for '$normalized': $letterDashNoSpacesVariants")
        } else {
            android.util.Log.d("SearchNormalizer", "Letter-dash-no-spaces pattern did not match for: '$normalized'")
        }
        
        // 7. ДОПОЛНИТЕЛЬНАЯ обработка для поиска только буквы (например "с")
        val singleLetterPattern = Regex("^([а-яё])\\s*$", RegexOption.IGNORE_CASE)
        val singleLetterMatch = singleLetterPattern.find(normalized)
        
        if (singleLetterMatch != null) {
            val letter = singleLetterMatch.groupValues[1].lowercase()
            
            // Создаем варианты для поиска арматуры, начинающейся с этой буквы
            val singleLetterVariants = listOf(
                "$letter",              // с
                "$letter-",             // с-
                "$letter".uppercase(),  // С
                "$letter-".uppercase()  // С-
            )
            
            variants.addAll(singleLetterVariants)
            android.util.Log.d("SearchNormalizer", "Added single letter variants for '$normalized': $singleLetterVariants")
        }
        
        // 8. Дополнительные варианты для общих случаев с дефисами
        if (normalized.contains("-") || normalized.contains(" ")) {
            val withDash = normalized.replace(" ", "-")
            val withoutDash = normalized.replace("-", " ").replace(Regex("\\s+"), " ")
            val withoutDashAndSpace = normalized.replace(Regex("[-\\s]+"), "")
            
            if (withDash != normalized && withDash.isNotEmpty()) variants.add(withDash)
            if (withoutDash != normalized && withoutDash.isNotEmpty()) variants.add(withoutDash)
            if (withoutDashAndSpace != normalized && withoutDashAndSpace.isNotEmpty()) variants.add(withoutDashAndSpace)
        }
        
        val finalVariants = variants.distinct().filter { it.isNotBlank() }
        android.util.Log.d("SearchNormalizer", "=== FINAL SEARCH VARIANTS ===")
        android.util.Log.d("SearchNormalizer", "Input: '$normalized'")
        android.util.Log.d("SearchNormalizer", "Total variants generated: ${variants.size}")
        android.util.Log.d("SearchNormalizer", "Unique variants: ${finalVariants.size}")
        android.util.Log.d("SearchNormalizer", "Final variants: $finalVariants")
        return finalVariants
    }
    
    /**
     * Нормализует значение ячейки для сравнения с поисковым запросом
     * Использует thread-safe кэширование для повышения производительности
     * Валидирует входные данные для предотвращения проблем с производительностью
     */
    fun normalizeCellValue(value: String?): String {
        if (value.isNullOrBlank()) return ""
        
        // ИСПРАВЛЕНИЕ: Валидация входных данных
        val validatedValue = validateInput(value, MAX_CELL_VALUE_LENGTH, "Cell value")
        val actualValue = validatedValue ?: value
        
        // Проверяем кэш (используем оригинальное value как ключ)
        cellValueCache.get(value)?.let { return it }
        
        // Вычисляем нормализованное значение (используем валидированное значение)
        // Канонизируем похожие символы, дефисы и пробелы до нормализации
        val canonical = canonicalizeConfusables(actualValue)
        val normalized = canonical.trim()
            // Игнорируем все знаки пунктуации, КРОМЕ '-' и '/'
            .replace(Regex("[\\s\\p{Punct}&&[^-/]]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        // Кэшируем результат
        cellValueCache.put(value, normalized)
        return normalized
    }
    
    /**
     * Проверяет, содержит ли нормализованное значение ячейки любой из вариантов поискового запроса
     * ПРИОРИТЕТ: сначала точное совпадение, потом частичное
     */
    fun matchesSearchVariants(cellValue: String?, searchQuery: String): Boolean {
        if (cellValue.isNullOrBlank() || searchQuery.isBlank()) return false
        
        val normalizedCellValue = normalizeCellValue(cellValue).lowercase()
        val searchVariants = createSearchVariants(searchQuery).map { it.lowercase() }
        
        // ИСПРАВЛЕНИЕ: Приоритет точному совпадению и префиксу; для contains — строгие границы
        return searchVariants.any { variant ->
            when {
                // 1. Точное совпадение (высший приоритет)
                normalizedCellValue == variant -> true
                // 2. Начинается с поискового запроса (высокий приоритет)
                normalizedCellValue.startsWith(variant) -> true
                // 3. Содержит поисковый запрос (низкий приоритет, но с проверкой границ слова)
                else -> containsBounded(normalizedCellValue, variant)
            }
        }
    }
    
    /**
     * Получает оценку совпадения для сортировки результатов
     * Чем выше оценка, тем выше приоритет в результатах
     */
    fun getMatchScore(cellValue: String?, searchQuery: String): Int {
        if (cellValue.isNullOrBlank() || searchQuery.isBlank()) return 0
        
        val normalizedCellValue = normalizeCellValue(cellValue).lowercase()
        val searchVariants = createSearchVariants(searchQuery).map { it.lowercase() }
        
        var maxScore = 0
        
        searchVariants.forEach { variant ->
            when {
                // 1. Точное совпадение - высший приоритет (1000+ очков)
                normalizedCellValue == variant -> maxScore = maxOf(maxScore, 1000)
                
                // 2. Начинается с поискового запроса - высокий приоритет (500+ очков)
                normalizedCellValue.startsWith(variant) -> {
                    // Бонус за длину совпадения
                    val bonus = variant.length * 10
                    maxScore = maxOf(maxScore, 500 + bonus)
                }
                
                // 3. Содержит поисковый запрос - низкий приоритет (100+ очков)
                containsBounded(normalizedCellValue, variant) -> {
                    // Бонус за длину совпадения
                    val bonus = variant.length * 5
                    maxScore = maxOf(maxScore, 100 + bonus)
                }
            }
        }
        
        return maxScore
    }

    /**
     * Строгое вхождение с проверкой границ: совпадение считается валидным,
     * если по обе стороны от подстроки стоят НЕ букво-цифровые символы
     * (или подстрока на границе строки). Также игнорируем слишком короткие
     * подстроки (например, одиночные символы), чтобы избежать ложных срабатываний.
     */
    private fun containsBounded(haystack: String, needle: String): Boolean {
        if (needle.length < 2) return false
        var start = 0
        while (start <= haystack.length - needle.length) {
            val idx = haystack.indexOf(needle, start)
            if (idx < 0) break
            val end = idx + needle.length
            val prevOk = idx == 0 || !haystack[idx - 1].isLetterOrDigit()
            val nextOk = end == haystack.length || !haystack[end].isLetterOrDigit()
            if (prevOk && nextOk) return true
            start = idx + 1
        }
        return false
    }
    
    /**
     * Проверяет, начинается ли нормализованное значение ячейки с любого из вариантов поискового запроса
     */
    fun startsWithSearchVariants(cellValue: String?, searchQuery: String): Boolean {
        if (cellValue.isNullOrBlank() || searchQuery.isBlank()) return false
        
        val normalizedCellValue = normalizeCellValue(cellValue).lowercase()
        val searchVariants = createSearchVariants(searchQuery).map { it.lowercase() }
        
        return searchVariants.any { variant ->
            normalizedCellValue.startsWith(variant)
        }
    }
    
    /**
     * ИСПРАВЛЕНИЕ: Валидация входных данных
     * Проверяет безопасность и разумность входных параметров
     */
    private fun validateInput(input: String, maxLength: Int, inputType: String): String? {
        return when {
            input.isBlank() -> ""
            input.length > maxLength -> {
                android.util.Log.w("SearchNormalizer", "$inputType too long (${input.length} chars), max allowed: $maxLength")
                input.take(maxLength)
            }
            // Проверка на потенциально вредоносные паттерны
            input.contains('\u0000') -> {
                android.util.Log.w("SearchNormalizer", "$inputType contains null bytes, sanitizing")
                input.replace('\u0000', ' ')
            }
            else -> null // Валидация пройдена, используем оригинальную строку
        }
    }
    
    /**
     * ИСПРАВЛЕНИЕ: Thread-safe очистка всех кэшей
     */
    fun clearCaches() {
        normalizeCache.evictAll()
        variantsCache.evictAll()
        cellValueCache.evictAll()
        android.util.Log.d("SearchNormalizer", "All caches cleared")
    }
    
    /**
     * ИСПРАВЛЕНИЕ: Улучшенная статистика кэшей с thread-safe доступом
     */
    fun getCacheStats(): String {
        val normalizeStats = normalizeCache.getStats()
        val variantsStats = variantsCache.getStats()
        val cellValueStats = cellValueCache.getStats()
        
        return "SearchNormalizer caches:\n" +
                "  - Normalize: $normalizeStats\n" +
                "  - Variants: $variantsStats\n" +
                "  - CellValue: $cellValueStats"
    }
    
    
    /**
     * НОВОЕ: Детальная диагностика производительности
     */
    fun getPerformanceStats(): String {
        val totalHits = normalizeCache.hitCount() + variantsCache.hitCount() + cellValueCache.hitCount()
        val totalMisses = normalizeCache.missCount() + variantsCache.missCount() + cellValueCache.missCount()
        val totalRequests = totalHits + totalMisses
        val overallHitRate = if (totalRequests > 0) (totalHits * 100 / totalRequests) else 0
        
        return "SearchNormalizer performance:\n" +
                "  - Total requests: $totalRequests\n" +
                "  - Overall hit rate: $overallHitRate%\n" +
                "  - Memory usage: ~${estimateMemoryUsage()}KB"
    }
    
    /**
     * НОВОЕ: Оценка потребления памяти кэшами
     */
    private fun estimateMemoryUsage(): Int {
        val normalizeMemory = normalizeCache.size() * 50 // ~50 байт на запись
        val variantsMemory = variantsCache.size() * 150 // ~150 байт на список вариантов  
        val cellValueMemory = cellValueCache.size() * 80 // ~80 байт на значение ячейки
        
        return (normalizeMemory + variantsMemory + cellValueMemory) / 1024 // В килобайтах
    }
    
    /**
     * НОВОЕ: Проверка здоровья кэшей
     */
    fun validateCacheHealth(): Boolean {
        return try {
            // Проверяем что кэши отвечают
            normalizeCache.size() >= 0 &&
            variantsCache.size() >= 0 &&
            cellValueCache.size() >= 0
        } catch (e: Exception) {
            android.util.Log.e("SearchNormalizer", "Cache health check failed", e)
            false
        }
    }
}

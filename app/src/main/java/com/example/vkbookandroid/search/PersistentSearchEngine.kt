package com.example.vkbookandroid.search

import android.content.Context
import android.util.Log
import com.example.vkbookandroid.BuildConfig
import org.example.pult.RowDataDynamic
import com.example.vkbookandroid.utils.SearchNormalizer
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Персистентный поисковый движок на чистом Kotlin.
 * Хранит инвертированный индекс (token -> список индексов строк) на диске и использует mmap для быстрого доступа.
 *
 * Формат индекса:
 * - dict.txt: строки вида "token offset count" (offset и count — в элементах int32 в postings.bin)
 * - postings.bin: последовательность int32, отсортированные индексы строк по токенам
 * - meta.version: строка с числовой версией данных (long), для быстрой проверки устаревания
 */
class PersistentSearchEngine(private val context: Context) {

    private data class DictEntry(
        val token: String,
        val offsetInts: Int,
        val count: Int
    )

    private val indexDir: File by lazy { File(context.filesDir, "search_index").apply { mkdirs() } }
    private val dictFile: File by lazy { File(indexDir, "dict.txt") }
    private val postingsFile: File by lazy { File(indexDir, "postings.bin") }
    private val metaFile: File by lazy { File(indexDir, "meta.version") }

    @Volatile private var dict: List<DictEntry> = emptyList()
    @Volatile private var mmap: MappedByteBuffer? = null
    private val ready = AtomicBoolean(false)
    private val buildLock = Any() // Синхронизация для buildIndex

    fun isIndexReady(): Boolean = ready.get()

    fun getIndexStats(): String {
        val d = dict
        val sizeInts = postingsFile.takeIf { it.exists() }?.length()?.div(4) ?: 0L
        return "dict=${d.size}, postingsInts=$sizeInts, ready=${ready.get()}"
    }

    fun invalidateIndex() {
        try {
            ready.set(false)
            dict = emptyList()
            mmap = null
            dictFile.delete()
            postingsFile.delete()
            metaFile.delete()
        } catch (e: Exception) {
            Log.w("PersistentSearchEngine", "Failed to invalidate index", e)
        }
    }

    fun clearCache() {
        // Нет отдельного кэша, всё на диске
    }

    /**
     * Полная перестройка индекса.
     */
    fun buildIndex(data: List<RowDataDynamic>, headers: List<String>, dataVersion: Long) {
        // Синхронизация для предотвращения одновременных вызовов
        synchronized(buildLock) {
            buildIndexInternal(data, headers, dataVersion)
        }
    }
    
    private fun buildIndexInternal(data: List<RowDataDynamic>, headers: List<String>, dataVersion: Long) {
        // 1) Строим postings в памяти (token -> MutableSet<Int>)
        // ОПТИМИЗАЦИЯ: Увеличили начальную емкость для уменьшения rehashing
        val map = LinkedHashMap<String, MutableSet<Int>>(32_768)
        val headersLower = headers.map { it.lowercase() }
        
        // ОПТИМИЗАЦИЯ: Предварительно определяем PDF-колонки
        val pdfColumnIndices = headersLower.mapIndexedNotNull { index, header ->
            if (header.contains("pdf")) index else null
        }.toSet()
        
        // ОПТИМИЗАЦИЯ: Используем более эффективный цикл
        val tokenSplitRegex = Regex("\\s+")
        val numericRegex = Regex("\\d{2,6}") // индексируем числовые фрагменты длиной 2..6
        for (i in data.indices) {
            val row = data[i]
            val props = row.getAllProperties()
            
            // Нормализуем и токенизируем: сохраняем '-' и '/'; разбиваем по пробелам
            for (colIndex in props.indices) {
                // Пропускаем PDF-колонки
                if (colIndex in pdfColumnIndices) continue
                
                val cell = props[colIndex]
                val norm = SearchNormalizer.normalizeCellValue(cell)
                if (norm.isEmpty()) continue
                
                // ОПТИМИЗАЦИЯ: Разбиваем и добавляем токены в одном проходе
                norm.split(tokenSplitRegex).forEach { rawToken ->
                    val token = rawToken.lowercase()
                    if (token.isEmpty()) return@forEach
                    
                    // Базовый токен (с сохранением '-' и '/')
                    map.getOrPut(token) { LinkedHashSet() }.add(i)
                    
                    // ДОПОЛНЕНИЕ: Индексируем числовые подстроки для быстрых запросов вроде "95"
                    // Это позволяет находить "м-95" по запросу "95" без дорогого contains-скана
                    if (token.any { it.isDigit() }) {
                        var added = 0
                        numericRegex.findAll(token).forEach { m ->
                            val num = m.value
                            if (num.isNotEmpty()) {
                                map.getOrPut(num) { LinkedHashSet() }.add(i)
                                added++
                                if (added >= 3) return@forEach // ограничим количество индексаций на токен
                            }
                        }
                    }
                }
            }
        }

        // 2) Сортируем токены лексикографически для префиксного поиска
        val tokens = map.keys.toMutableList()
        tokens.sort()

        // 3) Пишем postings.bin и dict.txt атомарно
        val tmpPost = File(indexDir, "postings.tmp")
        val tmpDict = File(indexDir, "dict.tmp")
        RandomAccessFile(tmpPost, "rw").use { raf ->
            val ch = raf.channel
            val buf = ByteBuffer.allocateDirect(4 * 4096).order(ByteOrder.LITTLE_ENDIAN)
            var offsetInts = 0
            tmpDict.bufferedWriter(Charsets.UTF_8).use { dw ->
                for (t in tokens) {
                    val indices = map[t]!!.toMutableList().also { it.sort() }
                    // Записываем в postings: просто int32 индексы
                    buf.clear()
                    var written = 0
                    for (idx in indices) {
                        if (buf.remaining() < 4) {
                            buf.flip(); ch.write(buf); buf.clear()
                        }
                        buf.putInt(idx)
                        written++
                    }
                    if (buf.position() > 0) { buf.flip(); ch.write(buf); buf.clear() }
                    // Запись строки словаря (токены уже в нижнем регистре)
                    dw.write("$t $offsetInts $written\n")
                    offsetInts += written
                }
            }
        }

        // 4) Пишем meta.version
        File(indexDir, "meta.tmp").writer(Charsets.UTF_8).use { it.write(dataVersion.toString()) }

        // 5) Атомарный своп (удаляем существующие файлы перед переименованием)
        ready.set(false) // Помечаем индекс как неготовый перед заменой файлов
        
        // Удаляем существующие файлы, если они есть
        if (postingsFile.exists() && !postingsFile.delete()) {
            Log.w("PersistentSearchEngine", "Failed to delete existing postings file, will try to overwrite")
        }
        if (dictFile.exists() && !dictFile.delete()) {
            Log.w("PersistentSearchEngine", "Failed to delete existing dict file, will try to overwrite")
        }
        if (metaFile.exists() && !metaFile.delete()) {
            Log.w("PersistentSearchEngine", "Failed to delete existing meta file, will try to overwrite")
        }
        
        // Переименовываем временные файлы
        if (!tmpPost.renameTo(postingsFile)) {
            val error = "Failed to commit postings: tmpPost.exists=${tmpPost.exists()}, postingsFile.exists=${postingsFile.exists()}, canWrite=${postingsFile.parentFile?.canWrite()}"
            Log.e("PersistentSearchEngine", error)
            throw IllegalStateException(error)
        }
        if (!tmpDict.renameTo(dictFile)) {
            val error = "Failed to commit dict: tmpDict.exists=${tmpDict.exists()}, dictFile.exists=${dictFile.exists()}, canWrite=${dictFile.parentFile?.canWrite()}"
            Log.e("PersistentSearchEngine", error)
            throw IllegalStateException(error)
        }
        val metaTmp = File(indexDir, "meta.tmp")
        if (!metaTmp.renameTo(metaFile)) {
            val error = "Failed to commit meta: metaTmp.exists=${metaTmp.exists()}, metaFile.exists=${metaFile.exists()}, canWrite=${metaFile.parentFile?.canWrite()}"
            Log.e("PersistentSearchEngine", error)
            throw IllegalStateException(error)
        }

        // 6) Подготавливаем mmap и загружаем словарь в память
        loadIndexIfPresent()
        Log.d("PersistentSearchEngine", "Index built: tokens=${dict.size}, postings=${postingsFile.length()/4}")
    }

    /**
     * Инициализация/перезагрузка mmap и словаря, если файлы присутствуют.
     */
    fun loadIndexIfPresent() {
        try {
            if (!dictFile.exists() || !postingsFile.exists()) {
                ready.set(false)
                dict = emptyList()
                mmap = null
                return
            }
            // Загружаем словарь (в память только токен+смещения)
            val entries = ArrayList<DictEntry>(16_384)
            dictFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split(' ')
                    if (parts.size >= 3) {
                        val token = parts[0]
                        val off = parts[1].toIntOrNull() ?: return@forEach
                        val cnt = parts[2].toIntOrNull() ?: return@forEach
                        entries.add(DictEntry(token, off, cnt))
                    }
                }
            }
            dict = entries

            // Открываем mmap
            val raf = RandomAccessFile(postingsFile, "r")
            val ch = raf.channel
            mmap = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size()).order(ByteOrder.LITTLE_ENDIAN) as MappedByteBuffer
            ready.set(true)
        } catch (e: Exception) {
            Log.e("PersistentSearchEngine", "Failed to load index", e)
            ready.set(false)
            dict = emptyList()
            mmap = null
        }
    }

    /**
     * Возвращает индексы строк: exact → prefix → contains (по токенам, ограниченно).
     */
    fun search(normalizedQuery: String): List<Int> {
        if (!ready.get()) return emptyList()
        if (normalizedQuery.isBlank()) return emptyList()

        val ordered = LinkedHashSet<Int>(256)
        val m = mmap ?: return emptyList()
        val d = dict

        // EXTRACT TOKENS из запроса (обычно один токен, но поддержим несколько)
        val tokens = normalizedQuery.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        if (BuildConfig.DEBUG) {
            Log.d("PersistentSearchEngine", "Search tokens=$tokens, index=${getIndexStats()}")
        }

        // Жесткие требования по символам: если в последнем токене есть '/' или '-',
        // то для CONTAINS будем учитывать только словарные токены, содержащие эти же символы.
        val lastToken = tokens.last()
        val requireSlash = lastToken.contains('/')
        val requireDash = lastToken.contains('-')

        // 1) EXACT: для каждого токена берём ровно совпадающий
        for (q in tokens) {
            val idx = lowerBound(d, q)
            if (idx >= 0 && idx < d.size && d[idx].token == q) {
                readPostingsInto(m, d[idx], ordered)
            }
        }

        // 2) PREFIX: все токены, начинающиеся с последнего токена запроса
        val last = tokens.lastOrNull()
        if (last != null) {
            var i = lowerBound(d, last)
            while (i in d.indices) {
                val de = d[i]
                if (!de.token.startsWith(last)) break
                readPostingsInto(m, de, ordered)
                i++
            }
        }

        // 3) CONTAINS (ограниченно): токены, содержащие подстроку, с проверкой границ в скоринге
        if (last != null) {
            // Чтобы не обходить весь словарь на коротких запросах, ограничим длиной ≥3
            if (last.length >= 3 && ordered.size < 100) {
                var scanned = 0
                for (de in d) {
                    scanned++
                    // Дополнительные ограничения: сохраняем семантику специальных символов
                    if (requireSlash && !de.token.contains('/')) continue
                    if (requireDash && !de.token.contains('-')) continue
                    if (de.token.contains(last) && !de.token.startsWith(last) && de.token != last) {
                        readPostingsInto(m, de, ordered)
                        // Ранний выход для снижения нагрузки
                        if (ordered.size > 1000) break
                    }
                    // Жёсткий лимит сканирования словаря в contains-фазе
                    if (scanned > 5000 && ordered.isNotEmpty()) break
                }
            }
        }

        return ordered.toList()
    }

    private fun readPostingsInto(m: MappedByteBuffer, de: DictEntry, out: MutableSet<Int>) {
        try {
            val posBytes = de.offsetInts * 4
            val limitBytes = posBytes + de.count * 4
            val dup = m.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            dup.position(posBytes)
            dup.limit(limitBytes)
            val buf = dup.slice().order(ByteOrder.LITTLE_ENDIAN)
            for (k in 0 until de.count) {
                out.add(buf.int)
            }
        } catch (e: Exception) {
            Log.w("PersistentSearchEngine", "readPostings failed for ${de.token}", e)
        }
    }

    private fun lowerBound(d: List<DictEntry>, key: String): Int {
        var l = 0
        var r = d.size
        while (l < r) {
            val mid = (l + r) ushr 1
            val cmp = d[mid].token.compareTo(key)
            if (cmp < 0) l = mid + 1 else r = mid
        }
        return l
    }
}



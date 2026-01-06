package com.example.vkbookandroid

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.example.pult.RowDataDynamic
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Кэш постраничных данных Excel на диске (вариант 2).
 * - Формат: headers.json, widths.json, pages/page_XXXXX.json
 * - Инвалидация: по размеру/mtime файла-источника (если удалённый), для assets считаем стабильным
 * - Фоновая пересборка: безопасная, через временную папку + атомарную замену
 */
class ExcelCacheManager(private val context: Context) {

    data class CacheManifest(
        val sourcePath: String,
        val sourceSize: Long,
        val sourceLastModified: Long,
        val sourceHash: String?,
        val schemaVersion: Int,
        val pageSize: Int
    )

    private val gson by lazy { Gson() }

    // Версия структуры кэша. Меняем при изменении формата.
    private val schemaVersion: Int = 2
    
    // Мьютекс для синхронизации операций с кэшем
    private val cacheLock = ReentrantLock()

    private fun rootDir(): File = File(context.filesDir, "excel_cache").apply { mkdirs() }

    fun datasetDir(relativePath: String, sheetName: String): File {
        val dir = File(rootDir(), (relativePath + "::" + sheetName).replace('/', '_'))
        if (!dir.exists()) {
            val created = dir.mkdirs()
            Log.d("ExcelCacheManager", "Created dataset directory: ${dir.absolutePath}, success: $created")
        }
        return dir
    }

    private fun manifestFile(dir: File): File = File(dir, "manifest.json")
    private fun headersFile(dir: File): File = File(dir, "headers.json")
    private fun widthsFile(dir: File): File = File(dir, "widths.json")
    private fun pagesDir(dir: File): File {
        val pagesDir = File(dir, "pages")
        if (!pagesDir.exists()) {
            val created = pagesDir.mkdirs()
            Log.d("ExcelCacheManager", "Created pages directory: ${pagesDir.absolutePath}, success: $created")
        }
        return pagesDir
    }

    fun hasCache(relativePath: String, sheetName: String): Boolean {
        val dir = datasetDir(relativePath, sheetName)
        val hasHeaders = headersFile(dir).exists()
        val hasWidths = widthsFile(dir).exists()
        val hasPages = File(dir, "pages").listFiles { f ->
            f.name.startsWith("page_") && f.name.endsWith(".json")
        }?.isNotEmpty() == true
        return hasHeaders && hasWidths && hasPages
    }

    fun openCachedSessionOrNull(relativePath: String, sheetName: String): CachedExcelPagingSession? {
        return cacheLock.withLock {
            try {
                val dir = datasetDir(relativePath, sheetName)
                val hasHeaders = headersFile(dir).exists()
                val hasWidths = widthsFile(dir).exists()
                val hasPages = File(dir, "pages").listFiles { f ->
                    f.name.startsWith("page_") && f.name.endsWith(".json")
                }?.isNotEmpty() == true
                if (!(hasHeaders && hasWidths && hasPages)) return@withLock null
                CachedExcelPagingSession(dir)
            } catch (e: Exception) {
                Log.w("ExcelCacheManager", "Failed to open cached session for $relativePath::$sheetName", e)
                null
            }
        }
    }

    /**
     * Осмотр локального файла удалённого источника для сравнения подписи.
     */
    private fun tryGetRemoteCachedFile(relativePath: String): File? {
        return try {
            File(File(context.cacheDir, "remote_cache"), relativePath.replace('/', '_')).takeIf { it.exists() }
        } catch (_: Throwable) { null }
    }

    private fun readManifest(dir: File): CacheManifest? {
        return try {
            val json = manifestFile(dir).takeIf { it.exists() }?.readText() ?: return null
            gson.fromJson(json, CacheManifest::class.java)
        } catch (e: Exception) {
            Log.w("ExcelCacheManager", "Failed to read manifest in ${dir.name}", e)
            null
        }
    }

    private fun writeManifest(dir: File, m: CacheManifest) {
        manifestFile(dir).writeText(gson.toJson(m))
    }

    /**
     * Быстрый показ старого кэша, тихая пересборка при изменении.
     * Если кэша нет — собираем сразу (в фоновом потоке), колбэк вызовется по готовности.
     */
    fun refreshCacheIfStale(
        relativePath: String,
        sheetName: String,
        pageSize: Int,
        openInputStream: () -> InputStream,
        onUpdated: (() -> Unit)? = null
    ) {
        val dir = datasetDir(relativePath, sheetName)
        val current = readManifest(dir)
        val hasHeaders = headersFile(dir).exists()
        val hasWidths = widthsFile(dir).exists()
        val hasPages = File(dir, "pages").listFiles { f ->
            f.name.startsWith("page_") && f.name.endsWith(".json")
        }?.isNotEmpty() == true
        val needsBuild = current == null ||
                current.schemaVersion != schemaVersion ||
                current.pageSize != pageSize ||
                !hasHeaders || !hasWidths || !hasPages

        val isBuilding = AtomicBoolean(false)
        if (needsBuild && isBuilding.compareAndSet(false, true)) {
            buildCacheAsync(relativePath, sheetName, pageSize, openInputStream, onUpdated)
            return
        }

        // Проверим контент-хеш (работает и для assets). Считаем в отдельном потоке.
        Thread {
            try {
                val newHash = computeSha256(openInputStream())
                val changedByHash = (current?.sourceHash ?: "") != (newHash ?: "")
                var changedByRemoteStamp = false
                tryGetRemoteCachedFile(relativePath)?.let { remoteFile ->
                    changedByRemoteStamp = !(current != null && current.sourceSize == remoteFile.length() && current.sourceLastModified == remoteFile.lastModified())
                }
                if ((changedByHash || changedByRemoteStamp) && isBuilding.compareAndSet(false, true)) {
                    buildCache(relativePath, sheetName, pageSize, openInputStream)
                    onUpdated?.invoke()
                }
            } catch (e: Exception) {
                Log.w("ExcelCacheManager", "Hash check failed for $relativePath", e)
            }
        }.start()
    }

    private fun buildCacheAsync(
        relativePath: String,
        sheetName: String,
        pageSize: Int,
        openInputStream: () -> InputStream,
        onUpdated: (() -> Unit)?
    ) {
        // Поручим запуск корутины стороне вызывающего кода (фрагмент/репозиторий) — здесь только синхронная сборка
        Thread {
            cacheLock.withLock {
                try {
                    buildCache(relativePath, sheetName, pageSize, openInputStream)
                    onUpdated?.invoke()
                } catch (e: Exception) {
                    Log.e("ExcelCacheManager", "Cache build failed for $relativePath", e)
                }
            }
        }.start()
    }

    /**
     * Сборка кэша: постраничная запись JSON страниц, заголовков и ширин колонок.
     */
    fun buildCache(
        relativePath: String,
        sheetName: String,
        pageSize: Int,
        openInputStream: () -> InputStream
    ) {
        cacheLock.withLock {
        val dir = datasetDir(relativePath, sheetName)
        val tmpDir = File(dir.parentFile, dir.name + "_tmp").apply {
            if (exists()) {
                Log.d("ExcelCacheManager", "Removing existing tmp directory: ${absolutePath}")
                val deleted = deleteRecursively()
                Log.d("ExcelCacheManager", "Existing tmp directory deleted: $deleted")
            }
            
            // Убеждаемся, что родительская директория существует
            parentFile?.let { parent ->
                if (!parent.exists()) {
                    val parentCreated = parent.mkdirs()
                    Log.d("ExcelCacheManager", "Created parent directory: ${parent.absolutePath}, success: $parentCreated")
                }
            }
            
            val created = mkdirs()
            Log.d("ExcelCacheManager", "Created tmp directory: ${absolutePath}, success: $created")
            
            if (!created && !exists()) {
                Log.e("ExcelCacheManager", "❌ Failed to create tmp directory!")
                throw IllegalStateException("Failed to create tmp directory: $absolutePath")
            }
        }
        val pages = pagesDir(tmpDir)

        // Читаем Excel и пишем страницы
        var sourceSize = -1L
        var sourceMtime = -1L
        try {
            openInputStream().use { input ->
                val wb = XSSFWorkbook(input)
                
                // Диагностика: проверяем доступные листы
                Log.d("ExcelCacheManager", "=== EXCEL FILE DIAGNOSTICS ===")
                Log.d("ExcelCacheManager", "Looking for sheet: '$sheetName'")
                Log.d("ExcelCacheManager", "Available sheets:")
                for (i in 0 until wb.numberOfSheets) {
                    val sheet = wb.getSheetAt(i)
                    Log.d("ExcelCacheManager", "  Sheet $i: '${sheet.sheetName}' (rows: ${sheet.lastRowNum + 1})")
                }
                
                val sheet = wb.getSheet(sheetName)
                if (sheet == null) {
                    Log.e("ExcelCacheManager", "Sheet '$sheetName' not found! Available sheets: ${(0 until wb.numberOfSheets).map { wb.getSheetAt(it).sheetName }}")
                    throw IllegalArgumentException("Sheet '$sheetName' not found in Excel file")
                }
                
                Log.d("ExcelCacheManager", "Found sheet '$sheetName' with ${sheet.lastRowNum + 1} rows")
                Log.d("ExcelCacheManager", "Physical number of rows: ${sheet.physicalNumberOfRows}")
                Log.d("ExcelCacheManager", "First row num: ${sheet.firstRowNum}")
                Log.d("ExcelCacheManager", "Last row num: ${sheet.lastRowNum}")
                
                val headerRow = sheet.getRow(0)
                    ?: throw IllegalArgumentException("Header row is null in sheet '$sheetName'")
                val headers = headerRow.map { ExcelPagingSession.Companion.getCellValueAsString(it, wb) }
                
                // Убеждаемся, что директория существует перед записью файлов
                if (!tmpDir.exists()) {
                    Log.e("ExcelCacheManager", "Tmp directory does not exist: ${tmpDir.absolutePath}")
                    throw IllegalStateException("Tmp directory does not exist: ${tmpDir.absolutePath}")
                }
                
                headersFile(tmpDir).writeText(gson.toJson(headers))

                val widths = LinkedHashMap<String, Int>()
                for (cell in headerRow) {
                    val colIndex = cell.columnIndex
                    val columnName = ExcelPagingSession.Companion.getCellValueAsString(cell, wb)
                    val excelWidth = sheet.getColumnWidth(colIndex)
                    val pixelWidth = (excelWidth * 40.0 / 256).toInt()
                    widths[columnName] = maxOf(200, pixelWidth)
                }
                widthsFile(tmpDir).writeText(gson.toJson(widths))

                // Пишем страницы
                val evaluator = wb.creationHelper.createFormulaEvaluator()
                val formatter = org.apache.poi.ss.usermodel.DataFormatter()
                val totalRows = sheet.lastRowNum
                Log.d("ExcelCacheManager", "Processing data rows from 1 to $totalRows (total: ${totalRows - 1 + 1} data rows)")
                var start = 1
                var pageIndex = 0
                while (start <= totalRows) {
                    val rows = ArrayList<Map<String, String>>()
                    var taken = 0
                    while (taken < pageSize && start + taken <= totalRows) {
                        val rowIndex = start + taken
                        val row = sheet.getRow(rowIndex)
                        if (row != null) {
                            val rowMap = LinkedHashMap<String, String>()
                            for (i in headers.indices) {
                                val cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                                val value = formatter.formatCellValue(cell, evaluator)
                                rowMap[headers[i]] = value
                            }
                            rows.add(rowMap)
                            Log.d("ExcelCacheManager", "Processed row $rowIndex: $rowMap")
                        } else {
                            Log.w("ExcelCacheManager", "Row $rowIndex is null, skipping")
                        }
                        taken++
                    }
                    val pageFile = File(pages, String.format(Locale.getDefault(), "page_%05d.json", pageIndex))
                    
                    // Убеждаемся, что директория pages существует
                    if (!pages.exists()) {
                        Log.e("ExcelCacheManager", "Pages directory does not exist: ${pages.absolutePath}")
                        throw IllegalStateException("Pages directory does not exist: ${pages.absolutePath}")
                    }
                    
                    pageFile.writeText(gson.toJson(rows))
                    pageIndex++
                    start += taken
                }

                wb.close()
            }
        } catch (e: Exception) {
            // При ошибке — очистка tmp
            tmpDir.deleteRecursively()
            throw e
        }

        // Попробуем прочитать подпись удалённого файла (если есть)
        tryGetRemoteCachedFile(relativePath)?.let { rf ->
            sourceSize = rf.length()
            sourceMtime = rf.lastModified()
        }

        // Посчитаем хеш содержимого (работает и для assets)
        val sourceHash = try {
            computeSha256(openInputStream())
        } catch (_: Throwable) { null }

        // Запишем манифест
        val manifest = CacheManifest(
            sourcePath = relativePath,
            sourceSize = sourceSize,
            sourceLastModified = sourceMtime,
            sourceHash = sourceHash,
            schemaVersion = schemaVersion,
            pageSize = pageSize
        )
        writeManifest(tmpDir, manifest)

        // Атомарная замена
        try {
            if (dir.exists()) {
                Log.d("ExcelCacheManager", "Removing existing cache directory: ${dir.absolutePath}")
                val deleted = dir.deleteRecursively()
                Log.d("ExcelCacheManager", "Cache directory deleted: $deleted")
            }
            
            Log.d("ExcelCacheManager", "Renaming tmp directory from: ${tmpDir.absolutePath}")
            Log.d("ExcelCacheManager", "Renaming tmp directory to: ${dir.absolutePath}")
            val renamed = tmpDir.renameTo(dir)
            Log.d("ExcelCacheManager", "Tmp directory renamed successfully: $renamed")
            
            if (!renamed) {
                Log.e("ExcelCacheManager", "❌ Failed to rename tmp directory!")
                Log.e("ExcelCacheManager", "Tmp dir exists: ${tmpDir.exists()}")
                Log.e("ExcelCacheManager", "Target dir exists: ${dir.exists()}")
                
                // Fallback: копируем файлы вручную
                Log.d("ExcelCacheManager", "Attempting manual copy as fallback...")
                if (!dir.exists()) dir.mkdirs()
                tmpDir.copyRecursively(dir, overwrite = true)
                tmpDir.deleteRecursively()
                Log.d("ExcelCacheManager", "✅ Manual copy completed")
            }
        } catch (e: Exception) {
            Log.e("ExcelCacheManager", "💥 Exception during atomic replacement", e)
            // Убеждаемся, что tmp директория удалена
            if (tmpDir.exists()) {
                Log.d("ExcelCacheManager", "Cleaning up tmp directory after error")
                tmpDir.deleteRecursively()
            }
            throw e
        }
        }
    }

    /** Принудительная пересборка кэша (используется кнопкой "Обновить данные"). */
    fun forceRebuild(
        relativePath: String,
        sheetName: String,
        pageSize: Int,
        openInputStream: () -> InputStream,
        onUpdated: (() -> Unit)?
    ) {
        Thread {
            try {
                buildCache(relativePath, sheetName, pageSize, openInputStream)
                onUpdated?.invoke()
            } catch (e: Exception) {
                Log.e("ExcelCacheManager", "Force rebuild failed for $relativePath", e)
            }
        }.start()
    }

    private fun computeSha256(input: InputStream): String? {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            input.use { ins ->
                // ОПТИМИЗАЦИЯ: Используем буфер 64KB для эффективного чтения больших файлов
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            val digest = md.digest()
            val sb = StringBuilder(digest.size * 2)
            for (b in digest) sb.append(String.format("%02x", b))
            sb.toString()
        } catch (e: Exception) {
            Log.w("ExcelCacheManager", "SHA-256 compute failed", e)
            null
        }
    }

    // Имя листа задаётся извне
}



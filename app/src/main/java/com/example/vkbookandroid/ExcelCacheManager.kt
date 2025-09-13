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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

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

    private fun rootDir(): File = File(context.filesDir, "excel_cache").apply { mkdirs() }

    fun datasetDir(relativePath: String, sheetName: String): File =
        File(rootDir(), (relativePath + "::" + sheetName).replace('/', '_')).apply { mkdirs() }

    private fun manifestFile(dir: File): File = File(dir, "manifest.json")
    private fun headersFile(dir: File): File = File(dir, "headers.json")
    private fun widthsFile(dir: File): File = File(dir, "widths.json")
    private fun pagesDir(dir: File): File = File(dir, "pages").apply { mkdirs() }

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
        return try {
            val dir = datasetDir(relativePath, sheetName)
            val hasHeaders = headersFile(dir).exists()
            val hasWidths = widthsFile(dir).exists()
            val hasPages = File(dir, "pages").listFiles { f ->
                f.name.startsWith("page_") && f.name.endsWith(".json")
            }?.isNotEmpty() == true
            if (!(hasHeaders && hasWidths && hasPages)) return null
            CachedExcelPagingSession(dir)
        } catch (e: Exception) {
            Log.w("ExcelCacheManager", "Failed to open cached session for $relativePath::$sheetName", e)
            null
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
            try {
                buildCache(relativePath, sheetName, pageSize, openInputStream)
                onUpdated?.invoke()
            } catch (e: Exception) {
                Log.e("ExcelCacheManager", "Cache build failed for $relativePath", e)
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
        val dir = datasetDir(relativePath, sheetName)
        val tmpDir = File(dir.parentFile, dir.name + "_tmp").apply {
            if (exists()) deleteRecursively()
            mkdirs()
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
                
                val headers = mutableListOf<String>()
                val headerRow = sheet.getRow(0)
                if (headerRow == null) {
                    Log.e("ExcelCacheManager", "Header row (row 0) is null in sheet '$sheetName'")
                    throw IllegalArgumentException("Header row is null in sheet '$sheetName'")
                }
                if (headerRow != null) {
                    for (cell in headerRow) headers.add(ExcelPagingSession.Companion.getCellValueAsString(cell, wb))
                }
                headersFile(tmpDir).writeText(gson.toJson(headers))

                val widths = LinkedHashMap<String, Int>()
                if (headerRow != null) {
                    for (cell in headerRow) {
                        val colIndex = cell.columnIndex
                        val columnName = ExcelPagingSession.Companion.getCellValueAsString(cell, wb)
                        val excelWidth = sheet.getColumnWidth(colIndex)
                        val pixelWidth = (excelWidth * 40.0 / 256).toInt()
                        widths[columnName] = maxOf(200, pixelWidth)
                    }
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
                    val pageFile = File(pages, String.format("page_%05d.json", pageIndex))
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
        if (dir.exists()) dir.deleteRecursively()
        tmpDir.renameTo(dir)
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



package com.example.vkbookandroid

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Простой провайдер удалённых файлов с кэшем на диске.
 * При неудаче скачивания возвращает поток из assets.
 */
class RemoteFileProvider(
    private val context: Context,
    private val baseUrl: String
) : IFileProvider {
    
    private val fileHashManager = FileHashManager(context)
    private val cacheDir: File by lazy {
        File(context.filesDir, "remote_cache").apply { mkdirs() }
    }

    override fun open(relativePath: String): InputStream {
        // Безопасная нормализация пути
        val normalized = sanitizePath(relativePath.trimStart('/'))
        
        if (normalized.isEmpty()) {
            throw SecurityException("Invalid file path: $relativePath")
        }

        return try {
            // 1. Сначала проверяем filesDir/data/ (приоритет после синхронизации)
            val dataFile = getDataFile(normalized)
            Log.d("RemoteFileProvider", "Requested file: '$normalized'")
            Log.d("RemoteFileProvider", "Data file exists: ${dataFile.exists()}")
            // Убрали логирование полных путей для безопасности
            
            if (dataFile.exists() && dataFile.length() > 0) {
                Log.d("RemoteFileProvider", "Using data file: $normalized")
                return dataFile.inputStream()
            }
            
            // 2. Затем проверяем remote_cache
            val cached = File(cacheDir, normalized.replace('/', '_'))
            
            // Проверяем, что файл находится в разрешенной директории
            if (!cached.canonicalPath.startsWith(cacheDir.canonicalPath)) {
                throw SecurityException("Path traversal attempt detected: $relativePath")
            }
            
            // Проверяем, есть ли уже загруженный файл в кэше
            if (cached.exists() && cached.length() > 0) {
                // Проверяем размер файла
                if (!FileSizeValidator.validateFileSize(cached.name, cached.length())) {
                    Log.w("RemoteFileProvider", "Cached file too large, removing: ${cached.name}")
                    cached.delete()
                } else {
                    // Проверяем целостность файла по хешу
                    if (fileHashManager.verifyFileIntegrity(cached)) {
                        Log.d("RemoteFileProvider", "Using verified cached file: ${cached.name} (${cached.length()} bytes)")
                        return cached.inputStream()
                    } else {
                        Log.w("RemoteFileProvider", "Cached file integrity check failed, removing: ${cached.name}")
                        cached.delete()
                    }
                }
            }
            
            // 3. Проверяем общий размер кэша
            FileSizeValidator.cleanupCacheIfNeeded(cacheDir)
            
            // 4. В последнюю очередь пробуем assets
            Log.d("RemoteFileProvider", "File not found in data/ or cache, falling back to assets: $normalized")
            try {
                context.assets.open(normalized)
            } catch (assetException: Exception) {
                Log.e("RemoteFileProvider", "Asset not found: $normalized, creating empty placeholder")
                createEmptyPlaceholderFile(normalized)
            }
        } catch (e: Exception) {
            Log.w("RemoteFileProvider", "Fallback to assets for $relativePath", e)
            try {
                context.assets.open(normalized)
            } catch (assetException: Exception) {
                Log.e("RemoteFileProvider", "Asset not found in fallback: $normalized, creating empty placeholder")
                createEmptyPlaceholderFile(normalized)
            }
        }
    }
    
    /**
     * Получить путь к файлу в filesDir/data/ (аналогично FileProvider)
     */
    private fun getDataFile(relativePath: String): File {
        return when {
            relativePath.startsWith("Databases/") -> {
                // Excel файлы в filesDir/data/
                val filename = relativePath.substringAfter("Databases/")
                File(context.filesDir, "data/$filename")
            }
            relativePath.startsWith("Schemes/") -> {
                // PDF файлы в filesDir/data/
                val filename = relativePath.substringAfter("Schemes/")
                File(context.filesDir, "data/$filename")
            }
            relativePath == "armature_coords.json" -> {
                // JSON файл в filesDir/data/
                File(context.filesDir, "data/armature_coords.json")
            }
            else -> {
                // Общий случай - ищем в data/
                File(context.filesDir, "data/$relativePath")
            }
        }
    }
    
    /**
     * Создать безопасный пустой поток для отсутствующих файлов,
     * чтобы не подменять формат (например, .xlsx) некорректным содержимым.
     * Вместо этого UI/верхний уровень должен обработать отсутствие данных.
     */
    private fun createEmptyPlaceholderFile(normalized: String): InputStream {
        Log.w("RemoteFileProvider", "Returning empty placeholder for missing file: $normalized")
        return "".byteInputStream()
    }

    /**
     * Безопасная санитизация пути к файлу
     */
    private fun sanitizePath(path: String): String {
        // Удаляем опасные последовательности и нормализуем разделители директорий
        val normalizedSeparators = path
            .replace("..", "") // Удаляем path traversal
            .replace("//", "/") // Удаляем двойные слеши
            .replace("\\", "/") // Заменяем обратные слеши

        // Разрешаем распространённые символы в именах файлов: пробел, плюс, запятая, круглые/квадратные скобки
        // и стандартные разделители/знаки. Юникодные буквы/цифры (в т.ч. кириллица) уже разрешены через isLetterOrDigit
        val allowedPunctuation = setOf('/', '-', '_', '.', ' ', '+', ',', '(', ')', '[', ']')

        return normalizedSeparators
            .filter { ch -> ch.isLetterOrDigit() || ch in allowedPunctuation }
            .trim()
    }

    private fun downloadToFile(urlString: String, target: File) {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
        }
        conn.inputStream.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        conn.disconnect()
    }
}

private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"






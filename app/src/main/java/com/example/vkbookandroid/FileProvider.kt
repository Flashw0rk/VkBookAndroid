package com.example.vkbookandroid

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream

/**
 * Провайдер файлов с приоритетом filesDir над assets
 * Решает проблему потери кэша и отсутствия интернета
 */
class FileProvider(private val context: Context) : IFileProvider {
    
    private val tag = "FileProvider"
    
    /**
     * Открыть файл из filesDir/data/ (единая папка для всех данных)
     */
    override fun open(relativePath: String): InputStream {
        val normalized = relativePath.trimStart('/')
        
        // Ищем в filesDir/data/
        val dataFile = getDataFile(normalized)
        Log.d(tag, "Requested file: '$normalized'")
        Log.d(tag, "Data file exists: ${dataFile.exists()}")
        // Убрали логирование полных путей и размеров для безопасности
        
        if (dataFile.exists() && dataFile.length() > 0) {
            Log.d(tag, "Using data file: $normalized")
            return dataFile.inputStream()
        }
        
        // Если файла нет, выбрасываем исключение
        Log.e(tag, "File not found in data/: $normalized")
        throw java.io.FileNotFoundException("File not found in data/: $normalized")
    }
    
    /**
     * Проверить, есть ли файл в filesDir
     */
    fun hasFilesDirFile(relativePath: String): Boolean {
        val normalized = relativePath.trimStart('/')
        val file = getDataFile(normalized)
        return file.exists() && file.length() > 0
    }
    
    /**
     * Получить информацию о файле
     */
    fun getFileInfo(relativePath: String): FileInfo? {
        val normalized = relativePath.trimStart('/')
        
        // Проверяем filesDir
        val filesDirFile = getDataFile(normalized)
        if (filesDirFile.exists()) {
            return FileInfo(
                path = filesDirFile.absolutePath,
                size = filesDirFile.length(),
                lastModified = filesDirFile.lastModified(),
                source = "filesDir"
            )
        }
        
        // Проверяем assets
        return try {
            val assetInfo = context.assets.openFd(normalized)
            FileInfo(
                path = "assets/$normalized",
                size = assetInfo.length,
                lastModified = 0, // assets не имеют даты модификации
                source = "assets"
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Получить путь к файлу в filesDir
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
     * Информация о файле
     */
    data class FileInfo(
        val path: String,
        val size: Long,
        val lastModified: Long,
        val source: String // "filesDir" или "assets"
    )
}

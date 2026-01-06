package com.example.vkbookandroid

import android.util.Log
import java.io.File
import java.io.InputStream

/**
 * Валидатор для проверки размера файлов
 */
object FileSizeValidator {
    
    // Максимальные размеры файлов (в байтах)
    private const val MAX_EXCEL_FILE_SIZE = 50 * 1024 * 1024 // 50 MB
    private const val MAX_PDF_FILE_SIZE = 100 * 1024 * 1024 // 100 MB
    private const val MAX_JSON_FILE_SIZE = 10 * 1024 * 1024 // 10 MB
    private const val MAX_TOTAL_CACHE_SIZE = 80 * 1024 * 1024 // 80 MB (ограничение размера кэша Excel)
    
    /**
     * Проверяет размер файла перед загрузкой
     */
    fun validateFileSize(fileName: String, fileSize: Long): Boolean {
        val maxSize = when (getFileExtension(fileName).lowercase()) {
            "xlsx", "xls" -> MAX_EXCEL_FILE_SIZE
            "pdf" -> MAX_PDF_FILE_SIZE
            "json" -> MAX_JSON_FILE_SIZE
            else -> MAX_EXCEL_FILE_SIZE // По умолчанию
        }
        
        if (fileSize > maxSize) {
            Log.w("FileSizeValidator", "File $fileName is too large: ${fileSize} bytes (max: $maxSize)")
            return false
        }
        
        return true
    }
    
    /**
     * Проверяет размер потока данных
     */
    fun validateStreamSize(inputStream: InputStream, fileName: String): Boolean {
        return try {
            // Читаем первые несколько байт для проверки заголовка
            val buffer = ByteArray(1024)
            val bytesRead = inputStream.read(buffer)
            
            if (bytesRead > 0) {
                // Проверяем заголовки файлов
                when (getFileExtension(fileName).lowercase()) {
                    "xlsx" -> validateExcelHeader(buffer)
                    "pdf" -> validatePdfHeader(buffer)
                    "json" -> validateJsonHeader(buffer)
                }
            }
            
            true
        } catch (e: Exception) {
            Log.e("FileSizeValidator", "Error validating stream for $fileName", e)
            false
        }
    }
    
    /**
     * Проверяет общий размер кэша
     */
    fun validateCacheSize(cacheDir: File): Boolean {
        val totalSize = calculateDirectorySize(cacheDir)
        
        if (totalSize > MAX_TOTAL_CACHE_SIZE) {
            Log.w("FileSizeValidator", "Cache size exceeded: $totalSize bytes (max: $MAX_TOTAL_CACHE_SIZE)")
            return false
        }
        
        return true
    }
    
    /**
     * Очищает кэш если он превышает лимит
     */
    fun cleanupCacheIfNeeded(cacheDir: File) {
        if (!validateCacheSize(cacheDir)) {
            Log.i("FileSizeValidator", "Cleaning up cache directory")
            deleteOldestFiles(cacheDir, MAX_TOTAL_CACHE_SIZE / 2L) // Удаляем половину файлов
        }
    }
    
    private fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "")
    }
    
    private fun validateExcelHeader(buffer: ByteArray): Boolean {
        // Excel файлы начинаются с PK (ZIP signature)
        return buffer[0] == 0x50.toByte() && buffer[1] == 0x4B.toByte()
    }
    
    private fun validatePdfHeader(buffer: ByteArray): Boolean {
        // PDF файлы начинаются с %PDF
        val pdfSignature = "%PDF"
        return buffer.take(4).map { it.toInt().toChar() }.joinToString("") == pdfSignature
    }
    
    private fun validateJsonHeader(buffer: ByteArray): Boolean {
        // JSON файлы начинаются с { или [
        val firstChar = buffer[0].toInt().toChar()
        return firstChar == '{' || firstChar == '['
    }
    
    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        if (directory.exists() && directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    calculateDirectorySize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }
    
    private fun deleteOldestFiles(directory: File, targetSize: Long) {
        if (!directory.exists() || !directory.isDirectory) return
        
        val files = directory.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var currentSize = files.sumOf { it.length() }
        
        for (file in files) {
            if (currentSize <= targetSize) break
            
            try {
                currentSize -= file.length()
                file.delete()
                Log.d("FileSizeValidator", "Deleted old file: ${file.name}")
            } catch (e: Exception) {
                Log.e("FileSizeValidator", "Error deleting file: ${file.name}", e)
            }
        }
    }
}

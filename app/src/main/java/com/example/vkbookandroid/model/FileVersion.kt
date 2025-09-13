package com.example.vkbookandroid.model

import java.util.Date

/**
 * Информация о версии файла
 */
data class FileVersion(
    val filename: String,
    val version: String,
    val lastModified: Date,
    val size: Long,
    val checksum: String? = null
)

/**
 * Информация о версии данных приложения
 */
data class AppDataVersion(
    val excelFiles: List<FileVersion>,
    val pdfFiles: List<FileVersion>,
    val jsonFiles: List<FileVersion>,
    val lastSync: Date,
    val isCriticalUpdate: Boolean = false
)


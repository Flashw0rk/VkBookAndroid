package com.example.vkbookandroid.model

/**
 * Корневая модель для armature_coords.json
 * Содержит маркеры арматур, сгруппированные по PDF файлам
 */
data class ArmatureCoordsData(
    val points: Map<String, Map<String, ArmatureMarker>>
)

/**
 * Информация о файле armature_coords.json
 */
data class ArmatureFileInfo(
    val fileExists: Boolean,
    val totalMarkers: Int,
    val pdfFiles: List<String>
)

/**
 * Ответ API для загрузки файлов
 */
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null
)

/**
 * Информация о загруженном файле
 */
data class FileInfoDto(
    val filename: String,
    val size: Long,
    val uploadTime: String
)





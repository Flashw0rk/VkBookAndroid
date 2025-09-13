package com.example.vkbookandroid.model

import com.google.gson.annotations.SerializedName

/**
 * Модель маркера арматуры для работы с серверным API
 * Совместима с форматом данных ПК-приложения
 */
data class ArmatureMarker(
    val page: Int,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val zoom: Double,
    val label: String,
    val comment: String? = null,
    @SerializedName("marker_type")
    val markerType: String? = null,
    val pdf: String = "" // PDF файл, к которому относится маркер
) {
    // Дополнительные поля для совместимости
    val id: String get() = label
    val size: Double get() = width // Для совместимости со старым кодом
    val color: String get() = extractColorFromMarkerType()
    
    /**
     * Извлечь цвет из marker_type (например, "square:#FF0000:16" -> "#FF0000")
     */
    private fun extractColorFromMarkerType(): String {
        return try {
            markerType?.substringAfter('#')?.substringBefore(':')?.let { "#$it" } ?: "#FF0000"
        } catch (e: Exception) {
            "#FF0000"
        }
    }
    
    /**
     * Получить цвет как Int для Android
     */
    fun getColorInt(): Int {
        return try {
            android.graphics.Color.parseColor(color)
        } catch (e: Exception) {
            android.graphics.Color.RED
        }
    }
    
    /**
     * Получить размер маркера в пикселях для отображения
     */
    fun getSizePx(scale: Float): Float {
        return (size * scale).toFloat()
    }
    
    /**
     * Получить ширину и высоту маркера (для совместимости со старым форматом)
     */
    fun getWidthCompat(): Double = width
    fun getHeightCompat(): Double = height
    
    /**
     * Получить текст для отображения (ID или комментарий)
     */
    fun getDisplayText(): String {
        return comment?.takeIf { it.isNotBlank() } ?: id
    }
}







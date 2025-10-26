package com.example.vkbookandroid.editor

import retrofit2.Response
import java.io.File

data class UploadItemResult(
    val fileType: String,
    val success: Boolean,
    val httpCode: Int?,
    val message: String?,
    val attempts: Int,
    val durationMs: Long
)

data class UploadReport(
    val overallStatus: String,
    val json: UploadItemResult?,
    val excel: UploadItemResult?
) {
    fun toSummary(): String {
        val parts = mutableListOf<String>()
        json?.let { parts.add("JSON: " + if (it.success) "OK" else "ошибка" + (it.httpCode?.let { c -> " $c" } ?: "")) }
        excel?.let { parts.add("Excel: " + if (it.success) "OK" else "ошибка" + (it.httpCode?.let { c -> " $c" } ?: "")) }
        return parts.joinToString(", ")
    }
}

interface IEditorUploadService {
    suspend fun uploadJson(file: File): Response<String>?
    suspend fun uploadExcel(file: File): Response<String>?
    suspend fun uploadAll(json: File?, excel: File?, parallel: Boolean = true): UploadReport
}


package com.example.vkbookandroid.editor

import com.example.vkbookandroid.network.NetworkModule
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import java.io.File
import kotlin.math.pow

class EditorUploadService(
    private val maxAttempts: Int = 3,
    private val baseBackoffMs: Long = 500L
) : IEditorUploadService {

    override suspend fun uploadJson(file: File): Response<String>? = runCatching {
        val part = MultipartBody.Part.createFormData(
            name = "file",
            filename = file.name,
            body = file.asRequestBody("application/json".toMediaType())
        )
        // Отправляем в обновлённый универсальный endpoint updates/upload
        val resp = NetworkModule.getArmatureApiService().uploadUpdatesFile(
            file = part,
            filename = file.name,
            adminLogin = EditorUploadState.adminLogin,
            adminPassword = EditorUploadState.adminPassword
        )
        if (resp.isSuccessful) {
            retrofit2.Response.success("OK")
        } else {
            val errBody = resp.errorBody() ?: okhttp3.ResponseBody.create(null, "")
            retrofit2.Response.error(resp.code(), errBody)
        }
    }.getOrNull()

    override suspend fun uploadExcel(file: File): Response<String>? = runCatching {
        val part = MultipartBody.Part.createFormData(
            name = "file",
            filename = file.name,
            body = file.asRequestBody("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".toMediaType())
        )
        val resp = NetworkModule.getArmatureApiService().uploadUpdatesFile(
            file = part,
            filename = file.name,
            adminLogin = EditorUploadState.adminLogin,
            adminPassword = EditorUploadState.adminPassword
        )
        if (resp.isSuccessful) {
            retrofit2.Response.success("OK")
        } else {
            val errBody = resp.errorBody() ?: okhttp3.ResponseBody.create(null, "")
            retrofit2.Response.error(resp.code(), errBody)
        }
    }.getOrNull()

    override suspend fun uploadAll(json: File?, excel: File?, parallel: Boolean): UploadReport {
        suspend fun <T> retryingUpload(
            type: String,
            block: suspend () -> Response<T>?
        ): UploadItemResult? {
            if (type == "JSON" && json == null) return null
            if (type == "Excel" && excel == null) return null
            var attempt = 0
            val start = System.currentTimeMillis()
            var lastCode: Int? = null
            var lastMsg: String? = null
            while (attempt < maxAttempts) {
                attempt++
                val resp = try { block() } catch (e: Exception) { lastMsg = e.message; null }
                if (resp == null) {
                    // сеть упала — ретрай
                } else if (resp.isSuccessful) {
                    val duration = System.currentTimeMillis() - start
                    return UploadItemResult(type, true, resp.code(), null, attempt, duration)
                } else {
                    lastCode = resp.code()
                    lastMsg = resp.message()
                    // 4xx — без ретраев, 5xx — ретраим
                    if (resp.code() in 400..499) break
                }
                if (attempt < maxAttempts) {
                    val backoff = (baseBackoffMs * 2.0.pow((attempt - 1).toDouble())).toLong()
                    delay(backoff + (0..150).random())
                }
            }
            val duration = System.currentTimeMillis() - start
            return UploadItemResult(type, false, lastCode, lastMsg, attempt, duration)
        }

        val jsonTask: suspend () -> UploadItemResult? = { retryingUpload("JSON") { json?.let { uploadJson(it) } } }
        val excelTask: suspend () -> UploadItemResult? = { retryingUpload("Excel") { excel?.let { uploadExcel(it) } } }

        val (jsonRes, excelRes) = if (parallel) {
            coroutineScope {
                val a = async { jsonTask() }
                val b = async { excelTask() }
                val results = awaitAll(a, b)
                Pair(results[0], results[1])
            }
        } else {
            Pair(jsonTask(), excelTask())
        }

        val overall = when {
            (jsonRes?.success != false) && (excelRes?.success != false) -> "success"
            (jsonRes?.success == true) || (excelRes?.success == true) -> "partial"
            else -> "failure"
        }
        return UploadReport(overall, jsonRes, excelRes)
    }
}

object EditorUploadState {
    var adminLogin: String? = null
    var adminPassword: String? = null
}




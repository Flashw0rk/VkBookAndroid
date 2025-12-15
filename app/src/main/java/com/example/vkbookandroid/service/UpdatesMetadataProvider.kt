package com.example.vkbookandroid.service

import android.util.Log
import com.example.vkbookandroid.FileHashManager
import com.example.vkbookandroid.network.NetworkModule
import com.example.vkbookandroid.network.model.UpdateFileMetadata
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.abs
import javax.net.ssl.SSLException

object MetadataConstants {
    const val UPDATES_PREFIX = "vkbook-server/updates/"
}

object UpdatesMetadataProvider {
    private var cachedMetadata: List<UpdateFileMetadata>? = null

    suspend fun ensureMetadata(forceRefresh: Boolean = false): List<UpdateFileMetadata> {
        if (!forceRefresh) {
            cachedMetadata?.let { return it }
        }
        val fresh = fetchMetadata().also {
            Log.d("UpdatesMetadataProvider", "Fetched ${it.size} entries from /api/updates/check")
        }
        cachedMetadata = fresh
        return fresh
    }

    private suspend fun fetchMetadata(): List<UpdateFileMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val response = try {
                NetworkModule.getArmatureApiService().checkUpdates()
            } catch (e: Exception) {
                // Точечный обход TLS‑ошибок на эмуляторах: повторяем через insecure‑клиент, если разрешено
                if (e is SSLException && com.example.vkbookandroid.BuildConfig.ALLOW_INSECURE_TLS_FOR_UPDATES) {
                    NetworkModule.getArmatureApiServiceInsecureForUpdates().checkUpdates()
                } else {
                    throw e
                }
            }
            if (!response.isSuccessful) {
                Log.e("UpdatesMetadataProvider", "checkUpdates failed: HTTP ${response.code()}")
                return@runCatching emptyList()
            }
            val body = response.body()?.string().orEmpty()
            if (body.isBlank()) {
                Log.w("UpdatesMetadataProvider", "checkUpdates returned empty body")
                emptyList()
            } else {
                parsePayload(body)
            }
        }.getOrElse {
            Log.e("UpdatesMetadataProvider", "Exception while fetching metadata", it)
            emptyList()
        }
    }

    private fun parsePayload(json: String): List<UpdateFileMetadata> {
        return runCatching {
            val element = NetworkModule.gson.fromJson(json, JsonElement::class.java)
            when {
                element == null -> emptyList()
                element.isJsonArray -> element.asJsonArray.mapNotNull { parseElement(it) }
                element.isJsonObject -> parseObject(element.asJsonObject)
                else -> emptyList()
            }
        }.getOrElse {
            Log.e("UpdatesMetadataProvider", "Failed to parse metadata payload", it)
            emptyList()
        }
    }

    private fun parseObject(obj: JsonObject): List<UpdateFileMetadata> {
        val arrayKeys = listOf("data", "files", "items", "result", "updates")
        arrayKeys.forEach { key ->
            if (obj.has(key) && obj.get(key).isJsonArray) {
                return obj.getAsJsonArray(key).mapNotNull { parseElement(it) }
            }
        }
        if (obj.has("file") && obj.get("file").isJsonObject) {
            return listOfNotNull(parseElement(obj.getAsJsonObject("file")))
        }
        return listOfNotNull(parseElement(obj))
    }

    private fun parseElement(element: JsonElement?): UpdateFileMetadata? {
        if (element == null || !element.isJsonObject) return null
        val obj = element.asJsonObject
        val filename = when {
            obj.has("filename") -> obj.get("filename").asString
            obj.has("name") -> obj.get("name").asString
            else -> ""
        }
        if (filename.isBlank()) return null
        return UpdateFileMetadata(
            filename = filename,
            size = obj.get("size")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong,
            lastModified = obj.get("lastModified")?.asString,
            hash = obj.get("hash")?.asString,
            version = obj.get("version")?.asString,
            hasUpdates = obj.get("hasUpdates")?.asBoolean,
            etag = obj.get("etag")?.asString,
            contentType = obj.get("contentType")?.asString
        )
    }
}

object UpdateFileDiffer {
    fun shouldDownloadFile(
        filename: String,
        metadata: UpdateFileMetadata?,
        targetFile: File,
        hashManager: FileHashManager
    ): Boolean {
        // Если файла нет локально - всегда скачиваем
        if (!targetFile.exists()) {
            Log.d("UpdateFileDiffer", "File $filename does not exist locally, will download")
            return true
        }
        
        if (metadata == null) {
            // Нет метаданных — пытаемся опереться на локальный хэш
            val localHash = hashManager.calculateFileHash(targetFile)
            val savedHash = hashManager.getSavedFileHash(filename)
            val normalizedLocal = normalizeHash(localHash ?: "")
            val normalizedSaved = normalizeHash(savedHash ?: "")
            if (normalizedLocal.isNotBlank() && normalizedLocal.equals(normalizedSaved, ignoreCase = true)) {
                Log.d("UpdateFileDiffer", "No metadata, but local hash matches saved hash for $filename, skip download")
                return false
            }
            return true
        }

        // Если файл существует и сервер говорит что обновлений нет - проверяем хэш
        if (metadata.hasUpdates == false) {
            metadata.hash?.takeIf { it.isNotBlank() }?.let { serverHash ->
                val normalizedServerHash = normalizeHash(serverHash)
                val localHash = hashManager.calculateFileHash(targetFile)
                val normalizedLocalHash = normalizeHash(localHash ?: "")
                if (normalizedLocalHash.isNotBlank() && normalizedLocalHash.equals(normalizedServerHash, ignoreCase = true)) {
                    Log.d("UpdateFileDiffer", "File $filename exists and hash matches, no updates needed")
            return false
                }
            }
            // Если хэш не совпадает или отсутствует, но hasUpdates=false - всё равно проверяем дальше
            Log.d("UpdateFileDiffer", "Metadata says no updates for $filename, but hash check needed")
        }

        metadata.hash?.takeIf { it.isNotBlank() }?.let { serverHash ->
            val normalizedServerHash = normalizeHash(serverHash)
            val localHash = hashManager.calculateFileHash(targetFile)
            val normalizedLocalHash = normalizeHash(localHash ?: "")
            if (normalizedLocalHash.isNotBlank() && normalizedLocalHash.equals(normalizedServerHash, ignoreCase = true)) {
                Log.d("UpdateFileDiffer", "Hash match for $filename, skipping download")
                return false
            }
        }

        if (metadata.hash.isNullOrBlank() && metadata.size != null) {
            if (targetFile.length() == metadata.size) {
                val serverTs = parseTimestamp(metadata.lastModified)
                if (serverTs != null) {
                    val diff = abs(targetFile.lastModified() - serverTs)
                    if (diff <= 2_000L) {
                        Log.d("UpdateFileDiffer", "Size & timestamp match for $filename, skipping download")
                        return false
                    }
                }
            }
        }

        return true
    }

    private fun parseTimestamp(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(value).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }
    
    /**
     * Нормализует хэш, убирая префикс "sha256:" если он есть
     */
    private fun normalizeHash(hash: String): String {
        return hash.trim().removePrefix("sha256:").trim()
    }
}


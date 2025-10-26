package com.example.vkbookandroid.repository

import android.content.Context
import android.util.Log
import com.example.vkbookandroid.model.ArmatureCoordsData
import com.example.vkbookandroid.model.ArmatureMarker
import com.example.vkbookandroid.model.FileInfo
import com.example.vkbookandroid.network.ArmatureApiService
import com.example.vkbookandroid.network.NetworkModule
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Locale

/**
 * Репозиторий для работы с данными арматур
 * Поддерживает загрузку как из assets, так и с сервера
 */
/**
 * Репозиторий доступа к данным арматуры.
 * Источники:
 *  - filesDir/data (локально сохранённые файлы)
 *  - assets (резерв)
 *  - серверные API (через ArmatureApiService) с фоллбэками
 */
class ArmatureRepository(
    private val context: Context,
    private val apiService: ArmatureApiService? = null
) {
    
    private val gson = Gson()
    
    /**
     * Проверка на rate limit (429) и выброс исключения
     */
    private fun checkRateLimit(responseCode: Int?) {
        if (responseCode == 429) {
            Log.w("ArmatureRepository", "Server rate limit (429) - не делаем дополнительные запросы")
            throw RateLimitException()
        }
    }
    
    /**
     * Загрузка маркеров из filesDir (основной источник после синхронизации).
     * Поддерживает два формата JSON: старый (Map<String, Map<String, ArmatureCoords>>) и новый (points).
     */
    suspend fun loadMarkersFromFilesDir(): Map<String, Map<String, org.example.pult.model.ArmatureCoords>> {
        return withContext(Dispatchers.IO) {
            try {
                val file = java.io.File(context.filesDir, "data/armature_coords.json")
                if (!file.exists()) {
                    Log.w("ArmatureRepository", "armature_coords.json not found in filesDir")
                    return@withContext emptyMap()
                }
                
                val bytes = file.readBytes()
                // Пытаемся сначала UTF-8, при признаках мандража кодировки пробуем CP1251
                var jsonString = bytes.toString(Charsets.UTF_8)
                val looksLikeMojibake = jsonString.contains('Р') && (jsonString.contains('Ð') || jsonString.contains("Р "))
                if (looksLikeMojibake) {
                    runCatching {
                        val cp1251 = java.nio.charset.Charset.forName("windows-1251")
                        val alt = String(bytes, cp1251)
                        // Если альтернативная декодировка содержит кириллицу и меньше «Р/Ð», используем её
                        val altHasCyr = alt.any { ch -> ch in '\u0400'..'\u04FF' }
                        val utfHasCyr = jsonString.any { ch -> ch in '\u0400'..'\u04FF' }
                        val altBadMarks = alt.count { it == 'Р' || it == 'Ð' }
                        val utfBadMarks = jsonString.count { it == 'Р' || it == 'Ð' }
                        if (altHasCyr && (!utfHasCyr || altBadMarks < utfBadMarks)) {
                            jsonString = alt
                            Log.w("ArmatureRepository", "Detected mojibake; applied CP1251 decode fallback")
                        }
                    }.onFailure { /* ignore */ }
                }
                Log.d("ArmatureRepository", "Loaded JSON from filesDir (head): ${jsonString.take(512)}${if (jsonString.length > 512) "..." else ""}")
                
                // Сначала пытаемся загрузить как старый формат (ваш формат)
                Log.d("ArmatureRepository", "Attempting to parse as old format (user's format)...")
                val type = com.google.gson.reflect.TypeToken.getParameterized(
                    Map::class.java,
                    String::class.java,
                    com.google.gson.reflect.TypeToken.getParameterized(
                        Map::class.java,
                        String::class.java,
                        org.example.pult.model.ArmatureCoords::class.java
                    ).type
                ).type
                
                try {
                    val oldFormatData: Map<String, Map<String, org.example.pult.model.ArmatureCoords>> = gson.fromJson(jsonString, type) ?: emptyMap()
                    if (oldFormatData.isNotEmpty()) {
                        Log.d("ArmatureRepository", "Old format (user's format) parsed successfully, markers count: ${oldFormatData.size}")
                        // Детальное логирование всех маркеров
                        oldFormatData.forEach { (pdfName, markers) ->
                            Log.d("ArmatureRepository", "  PDF: $pdfName, markers: ${markers.size}")
                            markers.forEach { (markerId, coords) ->
                                Log.d("ArmatureRepository", "    Marker: $markerId at (${coords.x}, ${coords.y}) with label: '${coords.label}'")
                            }
                        }
                        return@withContext oldFormatData
                    } else {
                        Log.w("ArmatureRepository", "Old format parsed but data is empty")
                    }
                } catch (e: Exception) {
                    Log.e("ArmatureRepository", "Error parsing old format", e)
                }
                
                // Если не получилось, пытаемся загрузить как новый формат (с "points")
                try {
                    val newFormatData = gson.fromJson(jsonString, ArmatureCoordsData::class.java)
                    if (newFormatData != null && newFormatData.points.isNotEmpty()) {
                        Log.d("ArmatureRepository", "Detected new format with points, markers count: ${newFormatData.points.size}")
                        return@withContext convertNewFormatToOld(newFormatData.points)
                    } else {
                        Log.d("ArmatureRepository", "New format detected but points is empty")
                    }
                } catch (e: Exception) {
                    Log.d("ArmatureRepository", "Not new format either", e)
                }
                
                Log.w("ArmatureRepository", "Could not parse JSON in any known format")
                emptyMap()
            } catch (e: Exception) {
                Log.e("ArmatureRepository", "Error loading markers from filesDir", e)
                emptyMap()
            }
        }
    }
    
    /**
     * Загрузка маркеров из assets как резервный вариант (старый формат данных).
     */
    suspend fun loadMarkersFromAssets(): Map<String, Map<String, org.example.pult.model.ArmatureCoords>> {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream = context.assets.open("armature_coords.json")
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                
                val type = com.google.gson.reflect.TypeToken.getParameterized(
                    Map::class.java,
                    String::class.java,
                    com.google.gson.reflect.TypeToken.getParameterized(
                        Map::class.java,
                        String::class.java,
                        org.example.pult.model.ArmatureCoords::class.java
                    ).type
                ).type
                
                gson.fromJson(jsonString, type) ?: emptyMap()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyMap()
            }
        }
    }
    
    /**
     * Проверить доступность сервера
     */
    suspend fun checkServerHealth(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("ArmatureRepository", "Checking server health...")
                val response = apiService?.getHealth()
                val responseCode = response?.code() ?: 0
                
                // Если получили 429, бросаем исключение для правильной обработки rate limit
                if (responseCode == 429) {
                    android.util.Log.w("ArmatureRepository", "Server rate limit (429) - сервер доступен, но ограничивает запросы. Подождите несколько секунд.")
                    throw RateLimitException()
                }
                
                val isSuccessful = response?.isSuccessful == true
                android.util.Log.d("ArmatureRepository", "Server health check result: $isSuccessful, response code: $responseCode")
                
                if (!isSuccessful) {
                    android.util.Log.e("ArmatureRepository", "Server health check failed: ${response?.errorBody()?.string()}")
                }
                isSuccessful
            } catch (e: com.example.vkbookandroid.repository.RateLimitException) {
                // Перебрасываем RateLimitException для обработки в SyncService
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ArmatureRepository", "Server health check exception", e)
                false
            }
        }
    }
    
    /**
     * Загрузить armature_coords.json с сервера
     * Возвращает данные в формате Map<String, Map<String, ArmatureMarker>>
     * который соответствует пользовательскому эталонному формату
     */
    suspend fun loadArmatureCoordsFromServer(): Map<String, Map<String, ArmatureMarker>>? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("ArmatureRepository", "Requesting armature coords from server...")
                val response = apiService?.getArmatureCoords()
                Log.d("ArmatureRepository", "Armature coords response: ${response?.code()}, success: ${response?.isSuccessful}")
                
                if (response?.isSuccessful == true) {
                    val body = response.body()
                    Log.d("ArmatureRepository", "Response body received: $body")
                    
                    if (body != null) {
                        Log.d("ArmatureRepository", "Body markers count: ${body.size}")
                        Log.d("ArmatureRepository", "Body markers: $body")
                        
                        if (body.isNotEmpty()) {
                            Log.d("ArmatureRepository", "Successfully loaded ${body.size} PDF files with markers from server")
                            return@withContext body
                        } else {
                            Log.w("ArmatureRepository", "Server returned empty markers map")
                        }
                    } else {
                        Log.w("ArmatureRepository", "Response body is null")
                    }
                } else {
                    Log.w("ArmatureRepository", "Failed to get armature coords: ${response?.code()}")
                    try { Log.w("ArmatureRepository", "Error body: ${response?.errorBody()?.string()}") } catch (_: Exception) {}
                    
                    // Если получили 429, бросаем исключение
                    checkRateLimit(response?.code())
                }

                // ФОЛБЭК 1: универсальная загрузка по имени файла (включая path и абсолютный URL)
                runCatching {
                    Log.d("ArmatureRepository", "Trying fallback: downloadFileByName(armature_coords.json)")
                    // Попытка через downloadUrl из list
                    val list = apiService?.getAllFiles()
                    if (list?.isSuccessful == true) {
                        val data = list.body()?.get("data") as? List<*>
                        val match = data?.firstOrNull {
                            val name = when (it) {
                                is com.example.vkbookandroid.model.FileInfo -> it.filename
                                is Map<*, *> -> it["filename"] as? String
                                else -> null
                            }
                            name?.equals("armature_coords.json", ignoreCase = true) == true
                        }
                        val url = when (match) {
                            is com.example.vkbookandroid.model.FileInfo -> match.path // в модели есть path
                            is Map<*, *> -> (match["downloadUrl"] as? String) ?: (match["path"] as? String)
                            else -> null
                        } ?: "/api/files/download/" + java.net.URLEncoder.encode("armature_coords.json", Charsets.UTF_8.name()).replace("+", "%20")
                        if (!url.isNullOrBlank()) {
                            val abs = if (url.startsWith("http")) url else com.example.vkbookandroid.network.NetworkModule.getCurrentBaseUrl().trimEnd('/') + "/" + url.trimStart('/')
                            val byUrl = com.example.vkbookandroid.network.NetworkModule.getArmatureApiService().downloadByUrl(abs)
                            if (byUrl.isSuccessful) {
                                val json = byUrl.body()?.string()
                                if (!json.isNullOrBlank()) {
                                    val type = com.google.gson.reflect.TypeToken.getParameterized(
                                        Map::class.java,
                                        String::class.java,
                                        com.google.gson.reflect.TypeToken.getParameterized(
                                            Map::class.java,
                                            String::class.java,
                                            ArmatureMarker::class.java
                                        ).type
                                    ).type
                                    val parsed: Map<String, Map<String, ArmatureMarker>>? = gson.fromJson(json, type)
                                    if (parsed != null && parsed.isNotEmpty()) {
                                        Log.d("ArmatureRepository", "Fallback by downloadUrl succeeded")
                                        return@withContext parsed
                                    }
                                }
                            }
                        }
                    }

                    // path вариант
                    val encodedPath = java.net.URLEncoder.encode("armature_coords.json", Charsets.UTF_8.name()).replace("+", "%20")
                    val dl = apiService?.downloadFileByPath(encodedPath)
                    if (dl?.isSuccessful == true) {
                        val json = dl.body()?.string()
                        if (!json.isNullOrBlank()) {
                            val type = com.google.gson.reflect.TypeToken.getParameterized(
                                Map::class.java,
                                String::class.java,
                                com.google.gson.reflect.TypeToken.getParameterized(
                                    Map::class.java,
                                    String::class.java,
                                    ArmatureMarker::class.java
                                ).type
                            ).type
                            val parsed: Map<String, Map<String, ArmatureMarker>>? = gson.fromJson(json, type)
                            if (parsed != null && parsed.isNotEmpty()) {
                                Log.d("ArmatureRepository", "Fallback byName succeeded: ${parsed.size} PDFs")
                                return@withContext parsed
                            }
                        }
                    } else {
                        Log.w("ArmatureRepository", "downloadFileByName fallback failed: code=${dl?.code()}")
                        // query: raw и encoded
                        val dl2 = apiService?.downloadFileByName("armature_coords.json")
                        if (dl2?.isSuccessful == true) {
                            val json = dl2.body()?.string()
                            if (!json.isNullOrBlank()) {
                                val type = com.google.gson.reflect.TypeToken.getParameterized(
                                    Map::class.java,
                                    String::class.java,
                                    com.google.gson.reflect.TypeToken.getParameterized(
                                        Map::class.java,
                                        String::class.java,
                                        ArmatureMarker::class.java
                                    ).type
                                ).type
                                val parsed: Map<String, Map<String, ArmatureMarker>>? = gson.fromJson(json, type)
                                if (parsed != null && parsed.isNotEmpty()) {
                                    Log.d("ArmatureRepository", "Fallback byName raw succeeded")
                                    return@withContext parsed
                                }
                            }
                        }
                        val enc = java.net.URLEncoder.encode("armature_coords.json", Charsets.UTF_8.name())
                        val dl3 = apiService?.downloadFileByName(enc)
                        if (dl3?.isSuccessful == true) {
                            val json = dl3.body()?.string()
                            if (!json.isNullOrBlank()) {
                                val type = com.google.gson.reflect.TypeToken.getParameterized(
                                    Map::class.java,
                                    String::class.java,
                                    com.google.gson.reflect.TypeToken.getParameterized(
                                        Map::class.java,
                                        String::class.java,
                                        ArmatureMarker::class.java
                                    ).type
                                ).type
                                val parsed: Map<String, Map<String, ArmatureMarker>>? = gson.fromJson(json, type)
                                if (parsed != null && parsed.isNotEmpty()) {
                                    Log.d("ArmatureRepository", "Fallback byName encoded succeeded")
                                    return@withContext parsed
                                }
                            }
                        }
                    }
                }

                // ФОЛБЭК 2: список JSON файлов через /api/files/json
                runCatching {
                    Log.d("ArmatureRepository", "Trying fallback: getJsonFiles()")
                    val listResp = apiService?.getJsonFiles()
                    if (listResp?.isSuccessful == true) {
                        val files = listResp.body().orEmpty()
                        val hasCoords = files.any { it.filename.equals("armature_coords.json", ignoreCase = true) }
                        Log.d("ArmatureRepository", "getJsonFiles returned ${files.size} items, hasCoords=$hasCoords")
                        if (hasCoords) {
                            val dl = apiService.downloadFileByName(java.net.URLEncoder.encode("armature_coords.json", Charsets.UTF_8.name()))
                            if (dl.isSuccessful) {
                                val json = dl.body()?.string()
                                if (!json.isNullOrBlank()) {
                                    val type = com.google.gson.reflect.TypeToken.getParameterized(
                                        Map::class.java,
                                        String::class.java,
                                        com.google.gson.reflect.TypeToken.getParameterized(
                                            Map::class.java,
                                            String::class.java,
                                            ArmatureMarker::class.java
                                        ).type
                                    ).type
                                    val parsed: Map<String, Map<String, ArmatureMarker>>? = gson.fromJson(json, type)
                                    if (parsed != null && parsed.isNotEmpty()) {
                                        Log.d("ArmatureRepository", "Fallback json list + byName succeeded")
                                        return@withContext parsed
                                    }
                                }
                            } else {
                                val dl2 = apiService.downloadFileByName("armature_coords.json")
                                if (dl2.isSuccessful) {
                                    val json = dl2.body()?.string()
                                    if (!json.isNullOrBlank()) {
                                        val type = com.google.gson.reflect.TypeToken.getParameterized(
                                            Map::class.java,
                                            String::class.java,
                                            com.google.gson.reflect.TypeToken.getParameterized(
                                                Map::class.java,
                                                String::class.java,
                                                ArmatureMarker::class.java
                                            ).type
                                        ).type
                                        val parsed: Map<String, Map<String, ArmatureMarker>>? = gson.fromJson(json, type)
                                        if (parsed != null && parsed.isNotEmpty()) {
                                            Log.d("ArmatureRepository", "Fallback json list + byName raw succeeded")
                                            return@withContext parsed
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ФОЛБЭК 3: updates API
                runCatching {
                    Log.d("ArmatureRepository", "Trying fallback: getUpdatesFilesByType(json)")
                    val upd = apiService?.getUpdatesFilesByType("json")
                    if (upd?.isSuccessful == true) {
                        val names = upd.body().orEmpty()
                        val has = names.any { it.equals("armature_coords.json", ignoreCase = true) }
                        if (has) {
                            val dl = apiService.downloadUpdatesFile("armature_coords.json")
                            if (dl.isSuccessful) {
                                val json = dl.body()?.string()
                                if (!json.isNullOrBlank()) {
                                    val type = com.google.gson.reflect.TypeToken.getParameterized(
                                        Map::class.java,
                                        String::class.java,
                                        com.google.gson.reflect.TypeToken.getParameterized(
                                            Map::class.java,
                                            String::class.java,
                                            ArmatureMarker::class.java
                                        ).type
                                    ).type
                                    val parsed: Map<String, Map<String, ArmatureMarker>>? = gson.fromJson(json, type)
                                    if (parsed != null && parsed.isNotEmpty()) {
                                        Log.d("ArmatureRepository", "Fallback updates download succeeded")
                                        return@withContext parsed
                                    }
                                }
                            }
                        }
                    }
                }

                Log.w("ArmatureRepository", "All fallbacks for armature_coords failed")
                return@withContext null
            } catch (e: Exception) {
                Log.e("ArmatureRepository", "Error loading armature coords from server", e)
                return@withContext null
            }
        }
    }
    
    /**
     * Получить список PDF файлов с сервера
     */
    suspend fun getPdfFilesFromServer(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("ArmatureRepository", "=== REQUESTING PDF FILES LIST ===")
                Log.d("ArmatureRepository", "API service available: ${apiService != null}")
                
                val response = apiService?.getPdfFiles()
                Log.d("ArmatureRepository", "PDF files response received")
                Log.d("ArmatureRepository", "Response code: ${response?.code()}")
                Log.d("ArmatureRepository", "Response message: ${response?.message()}")
                Log.d("ArmatureRepository", "Response successful: ${response?.isSuccessful}")
                
                if (response?.isSuccessful == true) {
                    val fileInfos = response.body() ?: emptyList()
                    Log.d("ArmatureRepository", "=== PDF FILES LIST RECEIVED ===")
                    Log.d("ArmatureRepository", "Files count: ${fileInfos.size}")
                    Log.d("ArmatureRepository", "Files list: $fileInfos")
                    
                    if (fileInfos.isEmpty()) {
                        Log.w("ArmatureRepository", "WARNING: Server returned empty PDF files list!")
                    }
                    
                    // Извлекаем только имена файлов
                    val filenames = fileInfos.map { it.filename }
                    Log.d("ArmatureRepository", "Extracted filenames: $filenames")
                    
                    if (filenames.isNotEmpty()) return@withContext filenames else Log.w("ArmatureRepository", "Empty PDF list from primary endpoint")
                } else {
                    Log.e("ArmatureRepository", "=== PDF FILES LIST REQUEST FAILED ===")
                    Log.e("ArmatureRepository", "Response code: ${response?.code()}")
                    Log.e("ArmatureRepository", "Response message: ${response?.message()}")
                    
                    // Пытаемся прочитать тело ошибки
                    try {
                        val errorBody = response?.errorBody()?.string()
                        Log.e("ArmatureRepository", "Error body: $errorBody")
                    } catch (e: Exception) {
                        Log.e("ArmatureRepository", "Could not read error body: ${e.message}")
                    }
                    
                    // Если получили 429, бросаем исключение
                    checkRateLimit(response?.code())
                }

                // ФОЛБЭК A: общий список файлов /api/files/list
                runCatching {
                    Log.d("ArmatureRepository", "Trying fallback: getAllFiles() for PDFs")
                    val all = apiService?.getAllFiles()
                    if (all?.isSuccessful == true) {
                        val body = all.body().orEmpty()
                        val dataAny = body["data"]
                        if (dataAny is List<*>) {
                            val names = dataAny.mapNotNull { item ->
                                when (item) {
                                    is com.example.vkbookandroid.model.FileInfo -> item.filename
                                    is Map<*, *> -> item["filename"] as? String
                                    else -> null
                                }
                            }.filter { it.endsWith(".pdf", ignoreCase = true) }
                            if (names.isNotEmpty()) {
                                Log.d("ArmatureRepository", "Fallback allFiles PDFs: ${names.size} items")
                                return@withContext names
                            }
                        }
                    }
                }

                // ФОЛБЭК B: updates по типу
                runCatching {
                    Log.d("ArmatureRepository", "Trying fallback: getUpdatesFilesByType(pdf)")
                    val upd = apiService?.getUpdatesFilesByType("pdf")
                    if (upd?.isSuccessful == true) {
                        val names = upd.body().orEmpty().filter { it.endsWith(".pdf", ignoreCase = true) }
                        if (names.isNotEmpty()) {
                            Log.d("ArmatureRepository", "Fallback updates PDFs: ${names.size} items")
                            return@withContext names
                        }
                    }
                }

                emptyList()
            } catch (e: Exception) {
                Log.e("ArmatureRepository", "=== PDF FILES LIST EXCEPTION ===", e)
                Log.e("ArmatureRepository", "Exception type: ${e.javaClass.simpleName}")
                Log.e("ArmatureRepository", "Exception message: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Получить список Excel файлов с сервера
     */
    suspend fun getExcelFilesFromServer(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("ArmatureRepository", "=== REQUESTING EXCEL FILES LIST ===")
                Log.d("ArmatureRepository", "API service available: ${apiService != null}")
                
                val response = apiService?.getExcelFiles()
                Log.d("ArmatureRepository", "Excel files response received")
                Log.d("ArmatureRepository", "Response code: ${response?.code()}")
                Log.d("ArmatureRepository", "Response message: ${response?.message()}")
                Log.d("ArmatureRepository", "Response successful: ${response?.isSuccessful}")
                Log.d("ArmatureRepository", "Response headers: ${response?.headers()}")
                
                if (response?.isSuccessful == true) {
                    val fileInfos = response.body() ?: emptyList()
                    Log.d("ArmatureRepository", "=== EXCEL FILES LIST RECEIVED ===")
                    Log.d("ArmatureRepository", "Files count: ${fileInfos.size}")
                    Log.d("ArmatureRepository", "Files list: $fileInfos")
                    
                    if (fileInfos.isEmpty()) {
                        Log.w("ArmatureRepository", "WARNING: Server returned empty Excel files list!")
                    }
                    
                    // Извлекаем только имена файлов
                    val filenames = fileInfos.map { it.filename }
                    Log.d("ArmatureRepository", "Extracted filenames: $filenames")
                    if (filenames.isNotEmpty()) return@withContext filenames else Log.w("ArmatureRepository", "Empty Excel list from primary endpoint")
                } else {
                    Log.e("ArmatureRepository", "=== EXCEL FILES LIST REQUEST FAILED ===")
                    Log.e("ArmatureRepository", "Response code: ${response?.code()}")
                    Log.e("ArmatureRepository", "Response message: ${response?.message()}")
                    
                    // Пытаемся прочитать тело ошибки
                    try {
                        val errorBody = response?.errorBody()?.string()
                        Log.e("ArmatureRepository", "Error body: $errorBody")
                    } catch (e: Exception) {
                        Log.e("ArmatureRepository", "Could not read error body: ${e.message}")
                    }
                    
                    // Если получили 429, бросаем исключение
                    checkRateLimit(response?.code())
                }

                // ФОЛБЭК A: общий список файлов /api/files/list
                runCatching {
                    Log.d("ArmatureRepository", "Trying fallback: getAllFiles() for Excel")
                    val all = apiService?.getAllFiles()
                    if (all?.isSuccessful == true) {
                        val body = all.body().orEmpty()
                        val dataAny = body["data"]
                        if (dataAny is List<*>) {
                            val names = dataAny.mapNotNull { item ->
                                when (item) {
                                    is com.example.vkbookandroid.model.FileInfo -> item.filename
                                    is Map<*, *> -> item["filename"] as? String
                                    else -> null
                                }
                            }.filter { it.endsWith(".xlsx", ignoreCase = true) }
                            if (names.isNotEmpty()) {
                                Log.d("ArmatureRepository", "Fallback allFiles Excel: ${names.size} items")
                                return@withContext names
                            }
                        }
                    }
                }

                // ФОЛБЭК B: updates по типу
                runCatching {
                    Log.d("ArmatureRepository", "Trying fallback: getUpdatesFilesByType(excel)")
                    val upd = apiService?.getUpdatesFilesByType("excel")
                    if (upd?.isSuccessful == true) {
                        val names = upd.body().orEmpty().filter { it.endsWith(".xlsx", ignoreCase = true) }
                        if (names.isNotEmpty()) {
                            Log.d("ArmatureRepository", "Fallback updates Excel: ${names.size} items")
                            return@withContext names
                        }
                    }
                }

                emptyList()
            } catch (e: Exception) {
                Log.e("ArmatureRepository", "=== EXCEL FILES LIST EXCEPTION ===", e)
                Log.e("ArmatureRepository", "Exception type: ${e.javaClass.simpleName}")
                Log.e("ArmatureRepository", "Exception message: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Скачать Excel файл с сервера
     */
    suspend fun downloadExcelFile(filename: String): okhttp3.ResponseBody? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("ArmatureRepository", "=== DOWNLOADING EXCEL FILE: $filename ===")
                Log.d("ArmatureRepository", "Making API request to server...")
                
                val response = apiService?.downloadExcel(filename)
                Log.d("ArmatureRepository", "Raw response received")
                Log.d("ArmatureRepository", "Response code: ${response?.code()}")
                Log.d("ArmatureRepository", "Response message: ${response?.message()}")
                Log.d("ArmatureRepository", "Response successful: ${response?.isSuccessful}")
                Log.d("ArmatureRepository", "Response headers: ${response?.headers()}")
                
                if (response?.isSuccessful == true) {
                    val body = response.body()
                    if (body != null) {
                        Log.d("ArmatureRepository", "Response body received successfully")
                        Log.d("ArmatureRepository", "Body content length: ${body.contentLength()} bytes")
                        Log.d("ArmatureRepository", "Body content type: ${body.contentType()}")
                        Log.d("ArmatureRepository", "Body source available: ${body.source().buffer().size}")
                        
                        // Проверяем, что файл не пустой
                        if (body.contentLength() == 0L) {
                            Log.e("ArmatureRepository", "ERROR: Server returned empty file for $filename")
                        } else if (body.contentLength() < 1000L) {
                            Log.w("ArmatureRepository", "WARNING: Server returned very small file for $filename (${body.contentLength()} bytes)")
                        }
                        
                        return@withContext body
                    } else {
                        Log.e("ArmatureRepository", "ERROR: Response body is null for $filename")
                    }
                }

                Log.w("ArmatureRepository", "Primary Excel download failed for $filename, trying fallbacks")
                try { Log.w("ArmatureRepository", "Response code: ${response?.code()} message=${response?.message()}") } catch (_: Exception) {}
                try { Log.w("ArmatureRepository", "Error body: ${response?.errorBody()?.string()}") } catch (_: Exception) {}

                // ФОЛБЭК 1: универсальная загрузка по имени
                runCatching {
                    val byName = apiService?.downloadFileByName(filename)
                    if (byName?.isSuccessful == true) {
                        val body = byName.body()
                        if (body != null && body.contentLength() > 0) {
                            Log.d("ArmatureRepository", "Fallback download by name succeeded for $filename")
                            return@withContext body
                        }
                    }
                }

                // ФОЛБЭК 2: загрузка из updates
                runCatching {
                    val upd = apiService?.downloadUpdatesFile(filename)
                    if (upd?.isSuccessful == true) {
                        val body = upd.body()
                        if (body != null && body.contentLength() > 0) {
                            Log.d("ArmatureRepository", "Fallback download from updates succeeded for $filename")
                            return@withContext body
                        }
                    }
                }

                return@withContext null
            } catch (e: Exception) {
                Log.e("ArmatureRepository", "Exception while downloading Excel file: $filename", e)
                Log.e("ArmatureRepository", "Exception type: ${e.javaClass.simpleName}")
                Log.e("ArmatureRepository", "Exception message: ${e.message}")
                e.printStackTrace()
                return@withContext null
            }
        }
    }
    
    /**
     * Скачать PDF файл с сервера
     */
    suspend fun downloadPdfFile(filename: String): okhttp3.ResponseBody? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("ArmatureRepository", "=== DOWNLOADING PDF FILE: $filename ===")
                Log.d("ArmatureRepository", "Making API request to server...")
                
                val response = apiService?.downloadPdf(filename)
                Log.d("ArmatureRepository", "PDF response received")
                Log.d("ArmatureRepository", "Response code: ${response?.code()}")
                Log.d("ArmatureRepository", "Response message: ${response?.message()}")
                Log.d("ArmatureRepository", "Response successful: ${response?.isSuccessful}")
                Log.d("ArmatureRepository", "Response headers: ${response?.headers()}")
                
                if (response?.isSuccessful == true) {
                    val body = response.body()
                    if (body != null) {
                        Log.d("ArmatureRepository", "PDF response body received successfully")
                        Log.d("ArmatureRepository", "Body content length: ${body.contentLength()} bytes")
                        Log.d("ArmatureRepository", "Body content type: ${body.contentType()}")
                        
                        // Проверяем, что файл не пустой
                        if (body.contentLength() == 0L) {
                            Log.e("ArmatureRepository", "ERROR: Server returned empty PDF file for $filename")
                        } else if (body.contentLength() < 1000L) {
                            Log.w("ArmatureRepository", "WARNING: Server returned very small PDF file for $filename (${body.contentLength()} bytes)")
                        }
                        
                        return@withContext body
                    } else {
                        Log.e("ArmatureRepository", "ERROR: PDF response body is null for $filename")
                    }
                }

                Log.w("ArmatureRepository", "Primary PDF download failed for $filename, trying fallbacks")
                try { Log.w("ArmatureRepository", "Response code: ${response?.code()} message=${response?.message()}") } catch (_: Exception) {}
                try { Log.w("ArmatureRepository", "Error body: ${response?.errorBody()?.string()}") } catch (_: Exception) {}

                // ФОЛБЭК 1: универсальная загрузка по имени
                runCatching {
                    val byName = apiService?.downloadFileByName(filename)
                    if (byName?.isSuccessful == true) {
                        val body = byName.body()
                        if (body != null && body.contentLength() > 0) {
                            Log.d("ArmatureRepository", "Fallback download by name succeeded for $filename")
                            return@withContext body
                        }
                    }
                }

                // ФОЛБЭК 2: загрузка из updates
                runCatching {
                    val upd = apiService?.downloadUpdatesFile(filename)
                    if (upd?.isSuccessful == true) {
                        val body = upd.body()
                        if (body != null && body.contentLength() > 0) {
                            Log.d("ArmatureRepository", "Fallback download from updates succeeded for $filename")
                            return@withContext body
                        }
                    }
                }

                return@withContext null
            } catch (e: Exception) {
                Log.e("ArmatureRepository", "Exception while downloading PDF file: $filename", e)
                Log.e("ArmatureRepository", "Exception type: ${e.javaClass.simpleName}")
                Log.e("ArmatureRepository", "Exception message: ${e.message}")
                e.printStackTrace()
                return@withContext null
            }
        }
    }
    
    /**
     * Загрузить PDF файл на сервер
     */
    suspend fun uploadPdfFile(file: okhttp3.MultipartBody.Part): String? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService?.uploadPdf(file)
                if (response?.isSuccessful == true) {
                    response.body()
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Загрузить Excel файл на сервер
     */
    suspend fun uploadExcelFile(file: okhttp3.MultipartBody.Part): com.example.vkbookandroid.model.ApiResponse<com.example.vkbookandroid.model.FileInfoDto>? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService?.uploadExcel(file)
                if (response?.isSuccessful == true) {
                    response.body()
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Загрузить armature_coords.json на сервер
     */
    suspend fun uploadArmatureCoords(file: okhttp3.MultipartBody.Part): String? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService?.uploadArmatureCoords(file)
                if (response?.isSuccessful == true) {
                    response.body()
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Конвертировать новый формат в старый для обратной совместимости
     */
    fun convertNewFormatToOld(newMarkers: List<ArmatureMarker>): Map<String, Map<String, org.example.pult.model.ArmatureCoords>> {
        val result = mutableMapOf<String, MutableMap<String, org.example.pult.model.ArmatureCoords>>()
        
        newMarkers.forEach { marker ->
            val pdfMarkers = result.getOrPut(marker.pdf) { mutableMapOf() }
            
            val oldCoords = org.example.pult.model.ArmatureCoords(
                marker.page,
                marker.x,
                marker.y,
                marker.width,
                marker.height,
                marker.zoom,
                marker.label,
                marker.comment,
                marker.markerType
            )
            
            pdfMarkers[marker.id] = oldCoords
        }
        
        return result
    }
    
    /**
     * Конвертировать новый формат (Map) в старый для обратной совместимости
     */
    fun convertNewFormatToOld(newMarkersMap: Map<String, Map<String, ArmatureMarker>>): Map<String, Map<String, org.example.pult.model.ArmatureCoords>> {
        val result = mutableMapOf<String, MutableMap<String, org.example.pult.model.ArmatureCoords>>()
        
        newMarkersMap.forEach { (pdfName, markers) ->
            val pdfMarkers = result.getOrPut(pdfName) { mutableMapOf() }
            
            markers.forEach { (markerId, marker) ->
                val oldCoords = org.example.pult.model.ArmatureCoords(
                    marker.page,
                    marker.x,
                    marker.y,
                    marker.width,
                    marker.height,
                    marker.zoom,
                    marker.label,
                    marker.comment,
                    marker.markerType
                )
                
                pdfMarkers[markerId] = oldCoords
            }
        }
        
        return result
    }
    
    /**
     * Конвертировать старый формат в новый
     */
    fun convertOldFormatToNew(oldMarkers: Map<String, Map<String, org.example.pult.model.ArmatureCoords>>): List<ArmatureMarker> {
        val result = mutableListOf<ArmatureMarker>()
        
        oldMarkers.forEach { (pdfName, markers) ->
            markers.forEach { (id, coords) ->
                val newMarker = ArmatureMarker(
                    page = coords.page,
                    x = coords.x,
                    y = coords.y,
                    width = coords.width,
                    height = coords.height,
                    zoom = coords.zoom,
                    label = coords.label ?: id,
                    comment = coords.comment,
                    markerType = coords.marker_type,
                    pdf = pdfName
                )
                result.add(newMarker)
            }
        }
        
        return result
    }
    
    /**
     * Конвертировать формат сервера (Map<String, Map<String, ArmatureMarker>>) в список маркеров
     */
    fun convertServerFormatToList(serverPoints: Map<String, Map<String, ArmatureMarker>>): List<ArmatureMarker> {
        val result = mutableListOf<ArmatureMarker>()
        
        serverPoints.forEach { (pdfName, markers) ->
            markers.forEach { (id, marker) ->
                // Создаем новый маркер с правильным pdf
                val markerWithPdf = ArmatureMarker(
                    page = marker.page,
                    x = marker.x,
                    y = marker.y,
                    width = marker.width,
                    height = marker.height,
                    zoom = marker.zoom,
                    label = marker.label,
                    comment = marker.comment,
                    markerType = marker.markerType,
                    pdf = pdfName
                )
                result.add(markerWithPdf)
            }
        }
        
        return result
    }
    
    /**
     * Получить API сервис для прямого доступа к endpoints
     */
    fun getArmatureApiService(): ArmatureApiService {
        return apiService ?: NetworkModule.getArmatureApiService()
    }
}

package com.example.vkbookandroid.repository

import android.content.Context
import android.util.Log
import com.example.vkbookandroid.model.ArmatureCoordsData
import com.example.vkbookandroid.model.ArmatureMarker
import com.example.vkbookandroid.network.ArmatureApiService
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Locale

/**
 * Репозиторий для работы с данными арматур
 * Поддерживает загрузку как из assets, так и с сервера
 */
class ArmatureRepository(
    private val context: Context,
    private val apiService: ArmatureApiService? = null
) {
    
    private val gson = Gson()
    
    /**
     * Загрузить маркеры из filesDir (основной источник)
     * Поддерживает как новый формат (с "points"), так и старый формат
     */
    suspend fun loadMarkersFromFilesDir(): Map<String, Map<String, org.example.pult.model.ArmatureCoords>> {
        return withContext(Dispatchers.IO) {
            try {
                val file = java.io.File(context.filesDir, "data/armature_coords.json")
                if (!file.exists()) {
                    Log.w("ArmatureRepository", "armature_coords.json not found in filesDir")
                    return@withContext emptyMap()
                }
                
                val jsonString = file.readText()
                Log.d("ArmatureRepository", "Loaded JSON from filesDir: $jsonString")
                
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
     * Загрузить маркеры из assets (старый формат)
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
                val isSuccessful = response?.isSuccessful == true
                android.util.Log.d("ArmatureRepository", "Server health check result: $isSuccessful, response code: ${response?.code()}")
                if (!isSuccessful) {
                    android.util.Log.e("ArmatureRepository", "Server health check failed: ${response?.errorBody()?.string()}")
                }
                isSuccessful
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
                        
                        // Проверяем, есть ли данные
                        if (body.isNotEmpty()) {
                            Log.d("ArmatureRepository", "Successfully loaded ${body.size} PDF files with markers from server")
                            return@withContext body
                        } else {
                            Log.w("ArmatureRepository", "Server returned empty markers map")
                            return@withContext null
                        }
                    } else {
                        Log.w("ArmatureRepository", "Response body is null")
                        return@withContext null
                    }
                } else {
                    Log.w("ArmatureRepository", "Failed to get armature coords: ${response?.code()}")
                    Log.w("ArmatureRepository", "Error body: ${response?.errorBody()?.string()}")
                    return@withContext null
                }
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
                val response = apiService?.getPdfFiles()
                if (response?.isSuccessful == true) {
                    response.body() ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
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
                Log.d("ArmatureRepository", "Requesting Excel files list from server...")
                val response = apiService?.getExcelFiles()
                Log.d("ArmatureRepository", "Excel files response: ${response?.code()}, success: ${response?.isSuccessful}")
                if (response?.isSuccessful == true) {
                    val files = response.body() ?: emptyList()
                    Log.d("ArmatureRepository", "Received Excel files: $files")
                    files
                } else {
                    Log.w("ArmatureRepository", "Failed to get Excel files: ${response?.code()}")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("ArmatureRepository", "Error getting Excel files from server", e)
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
                        return@withContext null
                    }
                } else {
                    Log.w("ArmatureRepository", "Failed to download $filename")
                    Log.w("ArmatureRepository", "Response code: ${response?.code()}")
                    Log.w("ArmatureRepository", "Response message: ${response?.message()}")
                    
                    // Пытаемся прочитать тело ошибки
                    try {
                        val errorBody = response?.errorBody()?.string()
                        Log.w("ArmatureRepository", "Error body: $errorBody")
                    } catch (e: Exception) {
                        Log.w("ArmatureRepository", "Could not read error body: ${e.message}")
                    }
                    
                    return@withContext null
                }
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
                val response = apiService?.downloadPdf(filename)
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
}

package com.example.vkbookandroid.network

import com.example.vkbookandroid.model.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * API интерфейс для работы с VkBookServer
 * Соответствует реальным endpoints сервера
 */
interface ArmatureApiService {
    
    // ========== HEALTH & SYSTEM ENDPOINTS ==========
    
    /**
     * Проверка состояния сервера
     */
    @GET("actuator/health")
    suspend fun getHealth(): Response<Map<String, Any>>
    
    /**
     * Информация о приложении
     */
    @GET("actuator/info")
    suspend fun getInfo(): Response<Map<String, Any>>
    
    /**
     * Метрики сервера
     */
    @GET("actuator/metrics")
    suspend fun getMetrics(): Response<Map<String, Any>>
    
    /**
     * Корневой endpoint
     */
    @GET("/")
    suspend fun getRoot(): Response<String>
    
    // ========== FILE MANAGEMENT ENDPOINTS ==========
    
    /**
     * Получить список всех файлов (РАБОЧИЙ ENDPOINT)
     * Возвращает: {"data": [{"filename": "file.xlsx", "downloadUrl": "/api/files/download/file.xlsx"}]}
     */
    @GET("api/files/list")
    suspend fun getAllFiles(): Response<Map<String, Any>>
    
    /**
     * УНИВЕРСАЛЬНОЕ СКАЧИВАНИЕ ФАЙЛОВ (РАБОЧИЙ ENDPOINT)
     * Скачать любой файл по имени через рабочий API
     */
    @GET("api/files/download")
    suspend fun downloadFileByName(@Query("filename") filename: String): Response<ResponseBody>
    
    /**
     * Получить список PDF файлов
     */
    @GET("api/files/pdf")
    suspend fun getPdfFiles(): Response<List<FileInfo>>
    
    /**
     * Получить список JSON файлов
     */
    @GET("api/files/json")
    suspend fun getJsonFiles(): Response<List<FileInfo>>
    
    /**
     * Получить список Excel файлов
     */
    @GET("api/files/excel")
    suspend fun getExcelFiles(): Response<List<FileInfo>>
    
    // ========== UPDATES DIRECTORY ENDPOINTS ==========
    
    /**
     * Получить список всех файлов из папки /opt/vkbook-server/updates
     */
    @GET("api/updates/files")
    suspend fun getUpdatesFiles(): Response<Map<String, Any>>
    
    /**
     * Скачать файл из папки updates
     */
    @GET("api/updates/download/{filename}")
    suspend fun downloadUpdatesFile(@Path(value = "filename", encoded = true) filename: String): Response<ResponseBody>

    /**
     * Скачать файл по пути: path-параметр вместо query. Удобно для имен с '+' и пробелами
     */
    @GET("api/files/download/{filename}")
    suspend fun downloadFileByPath(@Path(value = "filename", encoded = true) filename: String): Response<ResponseBody>

    /**
     * Скачать по абсолютному URL (ретрофит сам не изменяет URL)
     */
    @GET
    suspend fun downloadByUrl(@Url url: String): Response<ResponseBody>
    
    /**
     * Получить список файлов по типу из папки updates
     */
    @GET("api/updates/files/{type}")
    suspend fun getUpdatesFilesByType(@Path("type") type: String): Response<List<String>>
    
    // ========== ARMATURE COORDS ENDPOINTS ==========
    
    /**
     * Получить содержимое armature_coords.json
     * Возвращает прямой формат Map<String, Map<String, ArmatureMarker>>
     * который соответствует пользовательскому эталонному формату
     */
    @GET("api/files/armature-coords")
    suspend fun getArmatureCoords(): Response<Map<String, Map<String, ArmatureMarker>>>
    
    /**
     * Скачать armature_coords.json файл
     */
    @GET("api/files/armature-coords")
    suspend fun downloadArmatureCoords(): Response<ResponseBody>
    
    /**
     * Загрузить armature_coords.json файл
     */
    @Multipart
    @POST("api/files/armature-coords/upload")
    suspend fun uploadArmatureCoords(@Part file: MultipartBody.Part): Response<String>
    
    // ========== PDF FILE ENDPOINTS ==========
    
    /**
     * Скачать PDF файл
     */
    @GET("api/files/pdf/{filename}")
    suspend fun downloadPdf(@Path("filename") filename: String): Response<ResponseBody>
    
    /**
     * Загрузить PDF файл
     */
    @Multipart
    @POST("api/files/pdf/upload")
    suspend fun uploadPdf(@Part file: MultipartBody.Part): Response<String>
    
    // ========== EXCEL FILE ENDPOINTS ==========
    
    /**
     * Скачать Excel файл
     */
    @GET("api/files/excel/{filename}")
    suspend fun downloadExcel(@Path("filename") filename: String): Response<ResponseBody>
    
    /**
     * Загрузить Excel файл
     */
    @Multipart
    @POST("api/files/excel/upload")
    suspend fun uploadExcel(@Part file: MultipartBody.Part): Response<ApiResponse<FileInfoDto>>
}









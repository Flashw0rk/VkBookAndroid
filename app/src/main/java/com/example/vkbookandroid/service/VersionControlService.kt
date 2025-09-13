package com.example.vkbookandroid.service

import android.content.Context
import android.util.Log
import com.example.vkbookandroid.model.AppDataVersion
import com.example.vkbookandroid.model.FileVersion
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Date

/**
 * Сервис для контроля версий файлов и предотвращения отката к старым данным
 */
class VersionControlService(private val context: Context) {
    
    private val tag = "VersionControlService"
    private val gson = Gson()
    private val versionFile = File(context.filesDir, "app_data_version.json")
    
    /**
     * Сохранить информацию о версии данных
     */
    fun saveDataVersion(version: AppDataVersion) {
        try {
            val json = gson.toJson(version)
            versionFile.writeText(json)
            Log.d(tag, "Saved data version: ${version.lastSync}")
        } catch (e: Exception) {
            Log.e(tag, "Error saving data version", e)
        }
    }
    
    /**
     * Загрузить информацию о версии данных
     */
    fun loadDataVersion(): AppDataVersion? {
        return try {
            if (versionFile.exists()) {
                val json = versionFile.readText()
                gson.fromJson(json, AppDataVersion::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Error loading data version", e)
            null
        }
    }
    
    /**
     * Проверить, есть ли синхронизированные данные
     */
    fun hasSyncedData(): Boolean {
        val version = loadDataVersion()
        return version != null && version.lastSync.time > 0
    }
    
    /**
     * Проверить, является ли обновление критическим
     */
    fun isCriticalUpdate(serverVersion: AppDataVersion): Boolean {
        val localVersion = loadDataVersion()
        if (localVersion == null) {
            // Первая синхронизация - не критическая
            return false
        }
        
        // Проверяем, есть ли изменения в критических файлах
        val criticalFiles = listOf("Armatures.xlsx", "Oborudovanie_BSCHU.xlsx", "armature_coords.json")
        
        for (criticalFile in criticalFiles) {
            val localFile = localVersion.excelFiles.find { it.filename == criticalFile } 
                ?: localVersion.jsonFiles.find { it.filename == criticalFile }
            val serverFile = serverVersion.excelFiles.find { it.filename == criticalFile }
                ?: serverVersion.jsonFiles.find { it.filename == criticalFile }
            
            if (localFile != null && serverFile != null) {
                if (localFile.lastModified != serverFile.lastModified || 
                    localFile.size != serverFile.size) {
                    Log.d(tag, "Critical update detected for $criticalFile")
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * Получить информацию о локальных файлах
     */
    fun getLocalFilesInfo(): AppDataVersion? {
        try {
            val remoteCacheDir = File(context.filesDir, "remote_cache")
            val schemesDir = File(context.filesDir, "Schemes")
            
            val excelFiles = mutableListOf<FileVersion>()
            val pdfFiles = mutableListOf<FileVersion>()
            val jsonFiles = mutableListOf<FileVersion>()
            
            // Excel файлы
            if (remoteCacheDir.exists()) {
                remoteCacheDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".xlsx", ignoreCase = true)) {
                        excelFiles.add(FileVersion(
                            filename = file.name,
                            version = file.lastModified().toString(),
                            lastModified = Date(file.lastModified()),
                            size = file.length()
                        ))
                    }
                }
            }
            
            // PDF файлы
            if (schemesDir.exists()) {
                schemesDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".pdf", ignoreCase = true)) {
                        pdfFiles.add(FileVersion(
                            filename = file.name,
                            version = file.lastModified().toString(),
                            lastModified = Date(file.lastModified()),
                            size = file.length()
                        ))
                    }
                }
            }
            
            // JSON файлы
            val jsonFile = File(context.filesDir, "armature_coords_sync.json")
            if (jsonFile.exists()) {
                jsonFiles.add(FileVersion(
                    filename = "armature_coords.json",
                    version = jsonFile.lastModified().toString(),
                    lastModified = Date(jsonFile.lastModified()),
                    size = jsonFile.length()
                ))
            }
            
            return AppDataVersion(
                excelFiles = excelFiles,
                pdfFiles = pdfFiles,
                jsonFiles = jsonFiles,
                lastSync = Date()
            )
        } catch (e: Exception) {
            Log.e(tag, "Error getting local files info", e)
            return null
        }
    }
    
    /**
     * Очистить информацию о версии (при полной пересинхронизации)
     */
    fun clearVersionInfo() {
        try {
            if (versionFile.exists()) {
                versionFile.delete()
                Log.d(tag, "Cleared version info")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error clearing version info", e)
        }
    }
}


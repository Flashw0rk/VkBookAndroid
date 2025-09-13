package com.example.vkbookandroid.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Сервис принудительной синхронизации для предотвращения отката к старым данным
 */
class ForcedSyncService(private val context: Context) {
    
    private val tag = "ForcedSyncService"
    private val syncService = SyncService(context)
    private val versionControl = VersionControlService(context)
    
    /**
     * Принудительная синхронизация при первом запуске
     */
    suspend fun forceInitialSync(): SyncResult {
        return withContext(Dispatchers.IO) {
            Log.d(tag, "Starting forced initial sync...")
            
            val result = SyncResult()
            
            // Проверяем соединение с сервером
            result.serverConnected = syncService.checkServerConnection()
            if (!result.serverConnected) {
                Log.w(tag, "Server not available for forced sync")
                result.errorMessage = "Сервер недоступен. Приложение не может работать без актуальных данных."
                return@withContext result
            }
            
            // Выполняем полную синхронизацию
            val syncResult = syncService.syncAll()
            result.armatureCoordsSynced = syncResult.armatureCoordsSynced
            result.excelFilesSynced = syncResult.excelFilesSynced
            result.pdfFilesSynced = syncResult.pdfFilesSynced
            result.overallSuccess = syncResult.overallSuccess
            
            if (result.overallSuccess) {
                // Сохраняем информацию о версии
                val localVersion = versionControl.getLocalFilesInfo()
                if (localVersion != null) {
                    versionControl.saveDataVersion(localVersion)
                    Log.d(tag, "Saved version info after forced sync")
                }
            } else {
                result.errorMessage = "Ошибка синхронизации. Приложение не может работать без актуальных данных."
            }
            
            Log.d(tag, "Forced sync completed: $result")
            result
        }
    }
    
    /**
     * Проверить, нужна ли принудительная синхронизация
     */
    fun needsForcedSync(): Boolean {
        return !versionControl.hasSyncedData()
    }
    
    /**
     * Проверить, есть ли критические обновления
     */
    suspend fun hasCriticalUpdates(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!syncService.checkServerConnection()) {
                    return@withContext false
                }
                
                // Здесь можно добавить проверку версий на сервере
                // Пока возвращаем false, так как сервер не предоставляет информацию о версиях
                false
            } catch (e: Exception) {
                Log.e(tag, "Error checking critical updates", e)
                false
            }
        }
    }
    
    /**
     * Результат принудительной синхронизации
     */
    data class SyncResult(
        var serverConnected: Boolean = false,
        var armatureCoordsSynced: Boolean = false,
        var excelFilesSynced: Boolean = false,
        var pdfFilesSynced: Boolean = false,
        var overallSuccess: Boolean = false,
        var errorMessage: String? = null
    ) {
        override fun toString(): String {
            return "SyncResult(server=$serverConnected, coords=$armatureCoordsSynced, excel=$excelFilesSynced, pdf=$pdfFilesSynced, success=$overallSuccess, error=$errorMessage)"
        }
    }
}



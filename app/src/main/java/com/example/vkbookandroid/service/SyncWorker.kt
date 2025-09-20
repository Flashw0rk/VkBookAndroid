package com.example.vkbookandroid.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Периодический воркер для фоновой синхронизации данных.
 * Запускается WorkManager'ом по расписанию и вызывает SyncService.syncAll().
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            android.util.Log.d("SyncWorker", "Starting background sync...")
            val service = SyncService(applicationContext)
            val result = service.syncAll()
            android.util.Log.d("SyncWorker", "Background sync finished: success=${result.overallSuccess}")
            if (result.overallSuccess) Result.success() else Result.retry()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Background sync error: ${e.message}", e)
            Result.retry()
        }
    }
}





package com.example.vkbookandroid.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.vkbookandroid.R
import com.example.vkbookandroid.service.ForcedSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Диалог принудительной синхронизации
 */
class ForcedSyncDialog : DialogFragment() {
    
    private var onSyncComplete: ((ForcedSyncService.SyncResult) -> Unit)? = null
    private var onSyncFailed: (() -> Unit)? = null
    
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var retryButton: Button
    private lateinit var cancelButton: Button
    
    companion object {
        fun newInstance(
            onSyncComplete: (ForcedSyncService.SyncResult) -> Unit,
            onSyncFailed: () -> Unit
        ): ForcedSyncDialog {
            return ForcedSyncDialog().apply {
                this.onSyncComplete = onSyncComplete
                this.onSyncFailed = onSyncFailed
            }
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_forced_sync, null)
        
        progressBar = view.findViewById(R.id.progressBar)
        statusText = view.findViewById(R.id.statusText)
        retryButton = view.findViewById(R.id.retryButton)
        cancelButton = view.findViewById(R.id.cancelButton)
        
        statusText.text = "Требуется синхронизация с сервером для получения актуальных данных..."
        
        retryButton.setOnClickListener {
            startSync()
        }
        
        cancelButton.setOnClickListener {
            onSyncFailed?.invoke()
            dismiss()
        }
        
        // Автоматически запускаем синхронизацию
        startSync()
        
        return AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Критическое обновление")
            .setView(view)
            .setCancelable(false)
            .create()
    }
    
    private fun startSync() {
        retryButton.isEnabled = false
        cancelButton.isEnabled = false
        progressBar.visibility = android.view.View.VISIBLE
        statusText.text = "Синхронизация с сервером..."
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val forcedSyncService = ForcedSyncService(requireContext())
                val result = withContext(Dispatchers.IO) {
                    forcedSyncService.forceInitialSync()
                }
                
                if (result.overallSuccess) {
                    statusText.text = "✅ Синхронизация завершена успешно"
                    progressBar.visibility = android.view.View.GONE
                    onSyncComplete?.invoke(result)
                    
                    // Закрываем диалог через 2 секунды
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        dismiss()
                    }, 2000)
                } else {
                    statusText.text = "❌ Ошибка синхронизации: ${result.errorMessage ?: "Неизвестная ошибка"}"
                    progressBar.visibility = android.view.View.GONE
                    retryButton.isEnabled = true
                    cancelButton.isEnabled = true
                }
            } catch (e: Exception) {
                statusText.text = "❌ Ошибка: ${e.message}"
                progressBar.visibility = android.view.View.GONE
                retryButton.isEnabled = true
                cancelButton.isEnabled = true
            }
        }
    }
}



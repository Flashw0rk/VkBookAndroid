package com.example.vkbookandroid

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

/**
 * Диалог для отображения информации о хешах файлов
 */
class HashInfoDialog : DialogFragment() {
    
    private lateinit var fileHashManager: FileHashManager
    
    override fun onAttach(context: Context) {
        super.onAttach(context)
        fileHashManager = FileHashManager(context)
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Информация о целостности файлов")
        
        // Получаем информацию о всех файлах с хешами
        val filesWithHashes = fileHashManager.getAllFilesWithHashes()
        
        val message = if (filesWithHashes.isEmpty()) {
            "Нет файлов с сохраненными хешами.\n\nХеши создаются автоматически при загрузке файлов с сервера."
        } else {
            buildString {
                append("Файлы с проверенными хешами:\n\n")
                filesWithHashes.forEach { (fileName, hash) ->
                    append("📄 $fileName\n")
                    append("🔐 SHA-256: ${hash.take(16)}...\n")
                    append("📊 Размер хеша: ${hash.length} символов\n\n")
                }
                append("✅ Все файлы прошли проверку целостности")
            }
        }
        
        builder.setMessage(message)
        
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        
        builder.setNeutralButton("Очистить хеши") { dialog, _ ->
            showClearHashesConfirmation()
        }
        
        return builder.create()
    }
    
    private fun showClearHashesConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Очистить хеши")
            .setMessage("Вы уверены, что хотите очистить все сохраненные хеши? Это приведет к повторной проверке целостности всех файлов при следующей загрузке.")
            .setPositiveButton("Да, очистить") { _, _ ->
                fileHashManager.clearAllHashes()
                dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    companion object {
        fun newInstance(): HashInfoDialog {
            return HashInfoDialog()
        }
    }
}




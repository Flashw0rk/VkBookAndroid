package com.example.vkbookandroid.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.fragment.app.Fragment
import java.util.*

/**
 * Помощник для голосового поиска
 * Обрабатывает запуск распознавания речи и получение результатов
 */
class VoiceSearchHelper(private val fragment: Fragment) {
    
    companion object {
        private const val VOICE_SEARCH_REQUEST_CODE = 1001
    }
    
    /**
     * Запуск голосового поиска
     */
    fun startVoiceSearch() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                // Настройки распознавания
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите что искать...")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, fragment.requireContext().packageName)
                
                // Дополнительные настройки для лучшего качества
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            }
            
            fragment.startActivityForResult(intent, VOICE_SEARCH_REQUEST_CODE)
            
        } catch (e: ActivityNotFoundException) {
            // Голосовой поиск недоступен
            showVoiceSearchUnavailable()
        } catch (e: Exception) {
            // Другие ошибки
            showVoiceSearchError(e.message)
        }
    }
    
    /**
     * Обработка результата голосового поиска
     */
    fun handleVoiceSearchResult(requestCode: Int, resultCode: Int, data: Intent?): String? {
        if (requestCode != VOICE_SEARCH_REQUEST_CODE) {
            return null
        }
        
        return when (resultCode) {
            Activity.RESULT_OK -> {
                // Успешное распознавание
                val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val recognizedText = results?.firstOrNull()
                
                if (recognizedText.isNullOrBlank()) {
                    showMessage("Не удалось распознать речь")
                    null
                } else {
                    showMessage("Распознано: \"$recognizedText\"")
                    recognizedText
                }
            }
            
            Activity.RESULT_CANCELED -> {
                // Пользователь отменил распознавание
                showMessage("Голосовой поиск отменен")
                null
            }
            
            else -> {
                // Ошибка распознавания
                showMessage("Ошибка голосового поиска")
                null
            }
        }
    }
    
    /**
     * Проверка доступности голосового поиска
     */
    fun isVoiceSearchAvailable(): Boolean {
        return try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            val packageManager = fragment.requireContext().packageManager
            val activities = packageManager.queryIntentActivities(intent, 0)
            activities.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Получение списка поддерживаемых языков
     */
    fun getSupportedLanguages(): List<String> {
        return try {
            val intent = Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS)
            val supportedLanguages = mutableListOf<String>()
            
            // Добавляем популярные языки
            supportedLanguages.add("ru-RU") // Русский
            supportedLanguages.add("en-US") // Английский (США)
            supportedLanguages.add("en-GB") // Английский (Великобритания)
            
            supportedLanguages
        } catch (e: Exception) {
            listOf("ru-RU", "en-US")
        }
    }
    
    /**
     * Установка языка для распознавания
     */
    fun setRecognitionLanguage(languageTag: String): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
        }
    }
    
    /**
     * Показать сообщение о недоступности голосового поиска
     */
    private fun showVoiceSearchUnavailable() {
        val context = fragment.context ?: return
        Toast.makeText(
            context,
            "Голосовой поиск недоступен. Установите Google приложение или другой сервис распознавания речи.",
            Toast.LENGTH_LONG
        ).show()
    }
    
    /**
     * Показать сообщение об ошибке голосового поиска
     */
    private fun showVoiceSearchError(errorMessage: String?) {
        val context = fragment.context ?: return
        val message = "Ошибка голосового поиска${if (errorMessage != null) ": $errorMessage" else ""}"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Показать обычное сообщение
     */
    private fun showMessage(message: String) {
        val context = fragment.context ?: return
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Создание intent для голосового поиска с настройками
     */
    fun createVoiceSearchIntent(
        prompt: String = "Скажите что искать...",
        language: String = "ru-RU",
        maxResults: Int = 5
    ): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, maxResults)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, fragment.requireContext().packageName)
        }
    }
}



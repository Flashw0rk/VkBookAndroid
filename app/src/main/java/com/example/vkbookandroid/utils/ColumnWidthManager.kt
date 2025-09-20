package com.example.vkbookandroid.utils

import android.content.Context
import com.google.gson.Gson
import android.util.Log

/**
 * Утилитный класс для управления шириной колонок в таблицах
 */
object ColumnWidthManager {
    private const val PREFS_BSCHU = "ColumnWidths"
    private const val PREFS_ARMATURE = "ColumnWidthsArmature"
    private const val KEY_BSCHU = "Oborudovanie_BSCHU.xlsx"
    private const val KEY_ARMATURE = "Armatures.xlsx"
    
    /**
     * Сохранить ширины колонок для БЩУ
     */
    fun saveBschuColumnWidths(context: Context, widths: Map<String, Int>, tag: String = "ColumnWidthManager") {
        try {
            val sharedPrefs = context.getSharedPreferences(PREFS_BSCHU, Context.MODE_PRIVATE)
            sharedPrefs.edit().putString(KEY_BSCHU, Gson().toJson(widths)).apply()
            Log.d(tag, "БЩУ колонки сохранены: $widths")
        } catch (e: Exception) {
            Log.e(tag, "Ошибка сохранения БЩУ колонок", e)
        }
    }
    
    /**
     * Сохранить ширины колонок для арматуры
     */
    fun saveArmatureColumnWidths(context: Context, widths: Map<String, Int>, tag: String = "ColumnWidthManager") {
        try {
            val sharedPrefs = context.getSharedPreferences(PREFS_ARMATURE, Context.MODE_PRIVATE)
            sharedPrefs.edit().putString(KEY_ARMATURE, Gson().toJson(widths)).apply()
            Log.d(tag, "Арматура колонки сохранены: $widths")
        } catch (e: Exception) {
            Log.e(tag, "Ошибка сохранения колонок арматуры", e)
        }
    }
    
    /**
     * Загрузить ширины колонок для БЩУ
     */
    fun loadBschuColumnWidths(context: Context): Map<String, Int> {
        return try {
            val sharedPrefs = context.getSharedPreferences(PREFS_BSCHU, Context.MODE_PRIVATE)
            val json = sharedPrefs.getString(KEY_BSCHU, null)
            if (json != null) {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, Int>>() {}.type
                Gson().fromJson(json, type) ?: emptyMap()
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            Log.e("ColumnWidthManager", "Ошибка загрузки БЩУ колонок", e)
            emptyMap()
        }
    }
    
    /**
     * Загрузить ширины колонок для арматуры
     */
    fun loadArmatureColumnWidths(context: Context): Map<String, Int> {
        return try {
            val sharedPrefs = context.getSharedPreferences(PREFS_ARMATURE, Context.MODE_PRIVATE)
            val json = sharedPrefs.getString(KEY_ARMATURE, null)
            if (json != null) {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, Int>>() {}.type
                Gson().fromJson(json, type) ?: emptyMap()
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            Log.e("ColumnWidthManager", "Ошибка загрузки колонок арматуры", e)
            emptyMap()
        }
    }
}








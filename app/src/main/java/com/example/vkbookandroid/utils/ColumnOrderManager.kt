package com.example.vkbookandroid.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson

object ColumnOrderManager {
    private const val PREFS_BSCHU = "ColumnOrderBschu"
    private const val PREFS_ARMATURE = "ColumnOrderArmature"
    private const val KEY_BSCHU = "Oborudovanie_BSCHU.xlsx_order"
    private const val KEY_ARMATURE = "Armatures.xlsx_order"

    fun saveBschuColumnOrder(context: Context, order: List<String>, tag: String = "ColumnOrderManager") {
        try {
            val sp = context.getSharedPreferences(PREFS_BSCHU, Context.MODE_PRIVATE)
            sp.edit().putString(KEY_BSCHU, Gson().toJson(order)).apply()
            Log.d(tag, "Сохранён порядок колонок БЩУ: $order")
        } catch (e: Exception) {
            Log.e(tag, "Ошибка сохранения порядка колонок БЩУ", e)
        }
    }

    fun loadBschuColumnOrder(context: Context): List<String> {
        return try {
            val sp = context.getSharedPreferences(PREFS_BSCHU, Context.MODE_PRIVATE)
            val json = sp.getString(KEY_BSCHU, null) ?: return emptyList()
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            Gson().fromJson<List<String>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("ColumnOrderManager", "Ошибка загрузки порядка колонок БЩУ", e)
            emptyList()
        }
    }

    fun saveArmatureColumnOrder(context: Context, order: List<String>, tag: String = "ColumnOrderManager") {
        try {
            val sp = context.getSharedPreferences(PREFS_ARMATURE, Context.MODE_PRIVATE)
            sp.edit().putString(KEY_ARMATURE, Gson().toJson(order)).apply()
            Log.d(tag, "Сохранён порядок колонок Арматура: $order")
        } catch (e: Exception) {
            Log.e(tag, "Ошибка сохранения порядка колонок Арматура", e)
        }
    }

    fun loadArmatureColumnOrder(context: Context): List<String> {
        return try {
            val sp = context.getSharedPreferences(PREFS_ARMATURE, Context.MODE_PRIVATE)
            val json = sp.getString(KEY_ARMATURE, null) ?: return emptyList()
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            Gson().fromJson<List<String>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("ColumnOrderManager", "Ошибка загрузки порядка колонок Арматура", e)
            emptyList()
        }
    }
}




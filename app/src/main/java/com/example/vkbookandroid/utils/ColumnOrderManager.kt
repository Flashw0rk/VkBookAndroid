package com.example.vkbookandroid.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson

/**
 * Сервис для сохранения и восстановления порядка колонок в таблицах.
 *
 * Хранит два набора порядков:
 * - `PREFS_BSCHU` — порядок колонок для вкладки "Сигналы БЩУ" (файл `Oborudovanie_BSCHU.xlsx`);
 * - `PREFS_ARMATURE` — порядок колонок для вкладки "Арматура" (файл `Armatures.xlsx`).
 *
 * Вместо индексов колонок сохраняются **имена** (заголовки) колонок. Это позволяет
 * свободно менять их количество и порядок в Excel, не ломая сохранённые настройки.
 * Для арматуры дополнительно фильтруется служебная колонка `PDF_Схема_и_ID_арматуры`,
 * чтобы она не попадала в сохранённый порядок и не смещала данные при повторном открытии.
 */
object ColumnOrderManager {
    private const val PREFS_BSCHU = "ColumnOrderBschu"
    private const val PREFS_ARMATURE = "ColumnOrderArmature"
    private const val KEY_BSCHU = "Oborudovanie_BSCHU.xlsx_order"
    private const val KEY_ARMATURE = "Armatures.xlsx_order"
    private const val ARMATURE_PDF_HEADER = "PDF_Схема_и_ID_арматуры"

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
            // Не сохраняем скрытую PDF‑колонку в пользовательский порядок
            val filteredOrder = order.filterNot {
                it.equals(ARMATURE_PDF_HEADER, ignoreCase = true) || it.equals("PDF_Схема", ignoreCase = true)
            }
            if (filteredOrder.isEmpty()) {
                Log.d(tag, "Порядок колонок Арматура пустой после фильтрации — пропускаем сохранение")
                return
            }
            val sp = context.getSharedPreferences(PREFS_ARMATURE, Context.MODE_PRIVATE)
            sp.edit().putString(KEY_ARMATURE, Gson().toJson(filteredOrder)).apply()
            Log.d(tag, "Сохранён порядок колонок Арматура (без PDF‑колонки): $filteredOrder")
        } catch (e: Exception) {
            Log.e(tag, "Ошибка сохранения порядка колонок Арматура", e)
        }
    }

    fun loadArmatureColumnOrder(context: Context): List<String> {
        return try {
            val sp = context.getSharedPreferences(PREFS_ARMATURE, Context.MODE_PRIVATE)
            val json = sp.getString(KEY_ARMATURE, null) ?: return emptyList()
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            val loaded = Gson().fromJson<List<String>>(json, type) ?: emptyList()

            // Если в ранее сохранённом порядке присутствует скрытая PDF‑колонка,
            // считаем такой порядок устаревшим и игнорируем его (используем дефолтный)
            val hasPdfColumn = loaded.any {
                it.equals(ARMATURE_PDF_HEADER, ignoreCase = true) || it.equals("PDF_Схема", ignoreCase = true)
            }
            if (hasPdfColumn) {
                Log.w("ColumnOrderManager", "Обнаружен устаревший порядок колонок Арматура с PDF‑колонкой — игнорируем и используем дефолтный")
                emptyList()
            } else {
                loaded
            }
        } catch (e: Exception) {
            Log.e("ColumnOrderManager", "Ошибка загрузки порядка колонок Арматура", e)
            emptyList()
        }
    }
}




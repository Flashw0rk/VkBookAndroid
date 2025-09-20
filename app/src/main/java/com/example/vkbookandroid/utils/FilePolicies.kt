package com.example.vkbookandroid.utils

/**
 * Политики имен файлов: здесь описываем, какие PDF разрешено хранить/скачивать.
 */
object FilePolicies {
    /**
     * Разрешены ли PDF-файлы с указанным именем.
     * Бизнес-правило: исключить все файлы, содержащие фразу "План на отметке" (без учета регистра).
     */
    fun isPdfAllowed(filename: String): Boolean {
        val name = filename.lowercase()
        // Простая фильтрация по фразе. При необходимости можно расширить список.
        if (name.contains("план на отметке")) return false
        return true
    }
}





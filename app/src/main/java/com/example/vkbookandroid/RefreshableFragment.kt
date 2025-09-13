package com.example.vkbookandroid

/**
 * Интерфейс для фрагментов, которые поддерживают обновление данных
 */
interface RefreshableFragment {
    /**
     * Обновить данные в фрагменте
     */
    fun refreshData()
    
    /**
     * Проверить, загружены ли данные
     */
    fun isDataLoaded(): Boolean
    
    /**
     * Получить путь к отслеживаемому файлу
     */
    fun getWatchedFilePath(): String?
}





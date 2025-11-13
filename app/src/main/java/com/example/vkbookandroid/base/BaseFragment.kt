package com.example.vkbookandroid.base

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment

/**
 * Базовый класс для всех фрагментов приложения.
 * Реализует ленивую загрузку данных - данные загружаются только когда фрагмент становится видимым.
 * 
 * Принципы ООП:
 * - Инкапсуляция: скрывает детали реализации ленивой загрузки
 * - Наследование: предоставляет базовую функциональность для всех фрагментов
 * - Полиморфизм: дочерние классы переопределяют loadData()
 */
abstract class BaseFragment : Fragment() {

    // Флаг: были ли данные уже загружены
    private var isDataLoaded = false
    
    // Флаг: был ли вызван onViewCreated
    private var isViewCreated = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isViewCreated = true
        // Если фрагмент уже видим, загружаем данные сразу
        if (isResumed) {
            loadDataIfNeeded()
        }
    }

    override fun onResume() {
        super.onResume()
        // Загружаем данные когда фрагмент становится видимым
        loadDataIfNeeded()
    }

    override fun onPause() {
        super.onPause()
        // Можно освобождать ресурсы здесь при необходимости
        onFragmentPaused()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isViewCreated = false
        isDataLoaded = false
        // Освобождаем ресурсы при уничтожении view
        releaseResources()
    }

    /**
     * Загружает данные только если:
     * 1. View создан
     * 2. Данные еще не загружены
     * 3. Фрагмент видим пользователю
     */
    private fun loadDataIfNeeded() {
        if (isViewCreated && !isDataLoaded && isResumed) {
            isDataLoaded = true
            loadData()
        }
    }

    /**
     * Принудительная перезагрузка данных.
     * Сбрасывает флаг isDataLoaded и загружает данные заново.
     */
    fun reloadData() {
        isDataLoaded = false
        loadDataIfNeeded()
    }

    /**
     * Переопределите этот метод для загрузки данных фрагмента.
     * Вызывается только один раз при первом отображении фрагмента.
     */
    protected abstract fun loadData()

    /**
     * Переопределите этот метод для освобождения ресурсов.
     * Вызывается при уничтожении view или паузе фрагмента.
     */
    protected open fun releaseResources() {
        // Дочерние классы могут переопределить для освобождения ресурсов
    }

    /**
     * Вызывается когда фрагмент уходит с экрана.
     * Можно использовать для приостановки анимаций, таймеров и т.д.
     */
    protected open fun onFragmentPaused() {
        // Дочерние классы могут переопределить
    }

    /**
     * Проверяет, загружены ли данные
     */
    fun isDataLoadedFlag(): Boolean = isDataLoaded
}





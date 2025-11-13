package com.example.vkbookandroid.theme

import android.app.Activity
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.lang.ref.WeakReference

/**
 * Централизованный менеджер тем
 * Отслеживает все фрагменты и гарантирует применение темы
 */
object ThemeManager {
    private const val TAG = "ThemeManager"
    
    // Слабые ссылки на фрагменты для автоматической очистки
    private val registeredFragments = mutableSetOf<WeakReference<ThemeAwareFragment>>()
    
    // Интерфейс для фрагментов, поддерживающих темы
    interface ThemeAwareFragment {
        fun applyTheme()
        fun isFragmentReady(): Boolean = true
    }
    
    /**
     * Регистрирует фрагмент для автоматического обновления темы
     */
    fun registerFragment(fragment: Fragment) {
        if (fragment !is ThemeAwareFragment) {
            Log.w(TAG, "Фрагмент ${fragment.javaClass.simpleName} не реализует ThemeAwareFragment")
            return
        }
        
        // Удаляем мертвые ссылки
        cleanupDeadReferences()
        
        // Добавляем новую ссылку
        registeredFragments.add(WeakReference(fragment))
        Log.d(TAG, "Зарегистрирован фрагмент: ${fragment.javaClass.simpleName}, всего: ${registeredFragments.size}")
        
        // Добавляем lifecycle observer для автоматической отмены регистрации
        fragment.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                unregisterFragment(fragment)
                fragment.lifecycle.removeObserver(this)
            }
        })
    }
    
    /**
     * Отменяет регистрацию фрагмента
     */
    fun unregisterFragment(fragment: Fragment) {
        registeredFragments.removeAll { it.get() == fragment || it.get() == null }
        Log.d(TAG, "Отменена регистрация фрагмента: ${fragment.javaClass.simpleName}, осталось: ${registeredFragments.size}")
    }
    
    /**
     * Применяет тему ко всем зарегистрированным фрагментам
     */
    fun applyThemeToAllFragments() {
        cleanupDeadReferences()
        
        Log.d(TAG, "=== Применяем тему ко всем фрагментам (${registeredFragments.size}) ===")
        
        var successCount = 0
        var skipCount = 0
        var errorCount = 0
        
        registeredFragments.forEach { weakRef ->
            val themeAware = weakRef.get()
            if (themeAware == null) {
                Log.w(TAG, "Фрагмент уничтожен (weak reference)")
                return@forEach
            }

            val fragment = themeAware as? Fragment
            if (fragment == null) {
                Log.w(TAG, "ThemeAwareFragment ${themeAware.javaClass.simpleName} не является Fragment")
                return@forEach
            }

            val fragmentName = fragment.javaClass.simpleName

            try {
                // Проверяем готовность фрагмента
                if (!themeAware.isFragmentReady()) {
                    Log.d(TAG, "Фрагмент $fragmentName не готов, пропускаем")
                    skipCount++
                    return@forEach
                }
                
                // Проверяем что фрагмент добавлен и view готов
                if (!fragment.isAdded) {
                    Log.d(TAG, "Фрагмент $fragmentName не добавлен, пропускаем")
                    skipCount++
                    return@forEach
                }
                
                val view = fragment.view
                if (view == null) {
                    Log.d(TAG, "Фрагмент $fragmentName view == null, пропускаем")
                    skipCount++
                    return@forEach
                }
                
                // Применяем тему через post для гарантии готовности UI
                view.post {
                    try {
                        if (fragment.isAdded && view.isAttachedToWindow) {
                            Log.d(TAG, "Применяем тему к $fragmentName")
                            themeAware.applyTheme()
                            successCount++
                        } else {
                            Log.w(TAG, "Фрагмент $fragmentName отсоединен от окна")
                            skipCount++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка применения темы к $fragmentName", e)
                        errorCount++
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка обработки фрагмента $fragmentName", e)
                errorCount++
            }
        }
        
        Log.d(TAG, "=== Применение темы завершено: успешно=$successCount, пропущено=$skipCount, ошибок=$errorCount ===")
    }
    
    /**
     * Применяет тему к конкретному фрагменту
     */
    fun applyThemeToFragment(fragment: Fragment, forceUpdate: Boolean = false) {
        if (fragment !is ThemeAwareFragment) {
            Log.w(TAG, "Фрагмент ${fragment.javaClass.simpleName} не реализует ThemeAwareFragment")
            return
        }
        
        if (!fragment.isFragmentReady() && !forceUpdate) {
            Log.d(TAG, "Фрагмент ${fragment.javaClass.simpleName} не готов")
            return
        }
        
        val view = fragment.view
        if (view == null) {
            Log.w(TAG, "Фрагмент ${fragment.javaClass.simpleName} view == null")
            return
        }
        
        view.post {
            try {
                if (fragment.isAdded && view.isAttachedToWindow) {
                    Log.d(TAG, "Применяем тему к ${fragment.javaClass.simpleName}")
                    fragment.applyTheme()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка применения темы к ${fragment.javaClass.simpleName}", e)
            }
        }
    }
    
    /**
     * Очищает мертвые ссылки
     */
    private fun cleanupDeadReferences() {
        val sizeBefore = registeredFragments.size
        registeredFragments.removeAll { it.get() == null }
        val sizeAfter = registeredFragments.size
        
        if (sizeBefore != sizeAfter) {
            Log.d(TAG, "Очищено мертвых ссылок: ${sizeBefore - sizeAfter}")
        }
    }
    
    /**
     * Получает количество зарегистрированных фрагментов
     */
    fun getRegisteredFragmentsCount(): Int {
        cleanupDeadReferences()
        return registeredFragments.size
    }
}






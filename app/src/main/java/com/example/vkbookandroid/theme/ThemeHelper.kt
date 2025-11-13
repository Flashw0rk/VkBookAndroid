package com.example.vkbookandroid.theme

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment

/**
 * Универсальный помощник для применения тем
 * Использует рекурсивный обход View для автоматического применения тем
 */
object ThemeHelper {
    
    /**
     * Применить тему к Activity
     */
    fun applyThemeToActivity(activity: Activity) {
        // Фон окна
        activity.window.decorView.setBackgroundColor(AppTheme.getBackgroundColor())
        activity.window.statusBarColor = AppTheme.getPrimaryColor()
        
        // Применяем к корневой View
        val rootView = activity.window.decorView.findViewById<View>(android.R.id.content)
        applyThemeToViewRecursive(rootView)
    }
    
    /**
     * Применить тему к Fragment
     */
    fun applyThemeToFragment(fragment: Fragment, rootView: View) {
        applyThemeToViewRecursive(rootView)
    }
    
    /**
     * Рекурсивно применить тему к View и всем дочерним View
     */
    private fun applyThemeToViewRecursive(view: View?) {
        if (view == null) return
        
        when (view) {
            is Button -> applyThemeToButton(view)
            is CardView -> applyThemeToCardView(view)
            is TextView -> applyThemeToTextView(view)
            is ViewGroup -> {
                // Применяем к контейнеру
                applyThemeToViewGroup(view)
                // Рекурсивно к дочерним
                for (i in 0 until view.childCount) {
                    applyThemeToViewRecursive(view.getChildAt(i))
                }
            }
        }
    }
    
    /**
     * Применить тему к кнопке
     */
    private fun applyThemeToButton(button: Button) {
        button.background = AppTheme.createButtonDrawable()
        button.setTextColor(AppTheme.getButtonTextColor())
    }
    
    /**
     * Применить тему к CardView
     */
    private fun applyThemeToCardView(cardView: CardView) {
        cardView.setCardBackgroundColor(AppTheme.getCardBackgroundColor())
        cardView.radius = AppTheme.getCardCornerRadius()
    }
    
    /**
     * Применить тему к TextView
     */
    private fun applyThemeToTextView(textView: TextView) {
        // Только если не кнопка
        if (textView !is Button) {
            textView.setTextColor(AppTheme.getTextPrimaryColor())
        }
    }
    
    /**
     * Применить тему к ViewGroup
     */
    private fun applyThemeToViewGroup(viewGroup: ViewGroup) {
        // Можно добавить стилизацию контейнеров при необходимости
    }
}


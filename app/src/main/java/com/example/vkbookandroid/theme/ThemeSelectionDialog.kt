package com.example.vkbookandroid.theme

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/**
 * Диалог выбора темы приложения
 * Легковесный, без создания лишних объектов
 */
class ThemeSelectionDialog(
    private val context: Context,
    private val onThemeSelected: (Int) -> Unit
) {
    private var dialog: Dialog? = null

    fun show() {
        val dlg = Dialog(context)
        dialog = dlg
        dlg.setContentView(createView())
        dlg.setOnDismissListener { dialog = null }
        dlg.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dlg.show()
    }
    
    private fun createView(): ScrollView {
        val scrollView = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }
        
        // Заголовок
        container.addView(TextView(context).apply {
            text = "🎨 Выбор оформления"
            textSize = 18f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(16))
        })
        
        // Текущая тема
        val currentThemeId = AppTheme.getCurrentThemeId()
        container.addView(TextView(context).apply {
            text = "Текущая: ${AppTheme.getThemeName(currentThemeId)}"
            textSize = 12f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(16))
        })
        
        // Создаем карточку для каждой темы
        AppTheme.getAllThemes().forEach { themeId ->
            container.addView(createThemeCard(themeId, themeId == currentThemeId))
        }
        
        scrollView.addView(container)
        return scrollView
    }
    
    private fun createThemeCard(themeId: Int, isSelected: Boolean): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(12)
            }
            
            // Фон карточки с границей
            val drawable = GradientDrawable()
            drawable.setColor(Color.WHITE)
            drawable.setStroke(
                dpToPx(if (isSelected) 3 else 1),
                if (isSelected) Color.parseColor("#4CAF50") else Color.parseColor("#E0E0E0")
            )
            drawable.cornerRadius = dpToPx(8).toFloat()
            background = drawable
            
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onThemeSelected(themeId)
                dialog?.dismiss()
            }
        }
        
        // Заголовок темы
        card.addView(TextView(context).apply {
            text = AppTheme.getThemeName(themeId)
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            if (isSelected) {
                text = "$text ✓"
                setTextColor(Color.parseColor("#4CAF50"))
            }
        })
        
        // Описание темы
        card.addView(TextView(context).apply {
            text = AppTheme.getThemeDescription(themeId)
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dpToPx(4), 0, dpToPx(8))
        })
        
        // Превью цветов темы
        card.addView(createColorPreview(themeId))
        
        return card
    }
    
    private fun createColorPreview(themeId: Int): LinearLayout {
        val preview = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(40)
            )
        }
        
        // Временно переключаемся на нужную тему для получения цветов
        val savedTheme = AppTheme.getCurrentThemeId()
        AppTheme.saveTheme(context, themeId)
        
        // Показываем 5 основных цветов темы
        val colors = listOf(
            AppTheme.getPrimaryColor(),
            AppTheme.getAccentColor(),
            AppTheme.getActiveColor(),
            AppTheme.getSelectedColor(),
            AppTheme.getButtonColor()
        )
        
        // Возвращаем сохраненную тему
        AppTheme.saveTheme(context, savedTheme)
        
        colors.forEach { color ->
            preview.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                ).apply {
                    setMargins(dpToPx(2), 0, dpToPx(2), 0)
                }
                setBackgroundColor(color)
            })
        }
        
        return preview
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}


package com.example.vkbookandroid

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SchemesFragment : Fragment(), RefreshableFragment, com.example.vkbookandroid.theme.ThemeManager.ThemeAwareFragment {

    private lateinit var buttonPickPdf: Button
    private lateinit var textSchemeTitle: TextView
    private lateinit var textSelectedPath: TextView
    private lateinit var pdfContainer: FrameLayout
    
    // Флаг для предотвращения множественных одновременных загрузок фона
    private var isLoadingBackground: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_schemes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonPickPdf = view.findViewById(R.id.button_pick_pdf)
        textSchemeTitle = view.findViewById(R.id.text_scheme_title)
        textSelectedPath = view.findViewById(R.id.text_selected_path)
        pdfContainer = view.findViewById(R.id.pdf_container)

        buttonPickPdf.setOnClickListener { openAssetSchemePicker() }
        // Отключаем звуки кликов у всего дерева фрагмента
        view.isSoundEffectsEnabled = false
        
        // Применяем тему к кнопке
        applyThemeToButtons()
    }
    
    override fun onResume() {
        super.onResume()
        
        // Регистрируем фрагмент в ThemeManager
        com.example.vkbookandroid.theme.ThemeManager.registerFragment(this)
    }
    
    override fun isFragmentReady(): Boolean {
        return view != null && isAdded
    }
    
    /**
     * Публичный метод для применения темы (вызывается из MainActivity при смене скина)
     */
    override fun applyTheme() {
        if (!isAdded || view == null) {
            android.util.Log.w("SchemesFragment", "applyTheme() вызван но фрагмент не готов")
            return
        }
        
        android.util.Log.d("SchemesFragment", "Применяем тему к схемам")
        
        // Применяем фон
        view?.let { v ->
            if (!com.example.vkbookandroid.theme.AppTheme.shouldApplyTheme()) {
                v.background = null
                v.setBackgroundColor(android.graphics.Color.parseColor("#FAFAFA"))
            } else {
                val bgColor = com.example.vkbookandroid.theme.AppTheme.getBackgroundColor()
                v.setBackgroundColor(bgColor)
                
                // Затем асинхронно загружаем фоновое изображение
                if (!isLoadingBackground) {
                    isLoadingBackground = true
                    Log.d("SchemesFragment", "Начинаем загрузку фонового изображения...")
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val bgDrawable = com.example.vkbookandroid.theme.AppTheme.getBackgroundDrawable(requireContext())
                            Log.d("SchemesFragment", "Фоновое изображение получено: ${bgDrawable != null}")
                            if (bgDrawable != null && isAdded) {
                                withContext(Dispatchers.Main) {
                                    val currentView = view
                                    if (isAdded && currentView != null && currentView.isAttachedToWindow) {
                                        Log.d("SchemesFragment", "Применяем фоновое изображение к view")
                                        currentView.background = bgDrawable
                                    } else {
                                        Log.w("SchemesFragment", "View не готов: isAdded=$isAdded, isAttached=${currentView?.isAttachedToWindow}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SchemesFragment", "Ошибка загрузки фонового изображения", e)
                        } finally {
                            isLoadingBackground = false
                        }
                    }
                } else {
                    Log.d("SchemesFragment", "Загрузка фонового изображения уже выполняется, пропускаем")
                }
            }
        }
        
        // Применяем тему к кнопке
        applyThemeToButtons()
    }
    
    private fun applyThemeToButtons() {
        if (!com.example.vkbookandroid.theme.AppTheme.shouldApplyTheme()) {
            // Схемы: увеличиваем на 2dp (0.5мм)
            val px = buttonPickPdf.context.resources.displayMetrics.density
            val paddingH = ((com.example.vkbookandroid.theme.AppTheme.getButtonPaddingHorizontal() + 2) * px).toInt()
            val paddingV = ((com.example.vkbookandroid.theme.AppTheme.getButtonPaddingVertical() + 2) * px).toInt()
            buttonPickPdf.setPadding(paddingH, paddingV, paddingH, paddingV)
            buttonPickPdf.minHeight = 0
            buttonPickPdf.minWidth = 0
            return
        }
        
        // ИСПРАВЛЕНИЕ: Делаем кнопку темнее ФОНОВОГО цвета на 30% для лучшей видимости
        buttonPickPdf.backgroundTintList = null
        
        val bgColor = com.example.vkbookandroid.theme.AppTheme.getBackgroundColor()
        val darkerColor = darkenColor(bgColor, 0.3f) // Затемняем на 30%
        
        val drawable = android.graphics.drawable.GradientDrawable()
        drawable.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        drawable.cornerRadius = com.example.vkbookandroid.theme.AppTheme.getButtonCornerRadius()
        drawable.setColor(darkerColor)
        
        buttonPickPdf.background = drawable
        buttonPickPdf.setTextColor(com.example.vkbookandroid.theme.AppTheme.getTextPrimaryColor())
        
        // Схемы: увеличиваем на 2dp (0.5мм)
        val px = buttonPickPdf.context.resources.displayMetrics.density
        val paddingH = ((com.example.vkbookandroid.theme.AppTheme.getButtonPaddingHorizontal() + 2) * px).toInt()
        val paddingV = ((com.example.vkbookandroid.theme.AppTheme.getButtonPaddingVertical() + 2) * px).toInt()
        buttonPickPdf.setPadding(paddingH, paddingV, paddingH, paddingV)
        buttonPickPdf.minHeight = 0
        buttonPickPdf.minWidth = 0
    }
    
    private fun darkenColor(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] *= (1f - factor) // Уменьшаем яркость
        return android.graphics.Color.HSVToColor(hsv)
    }

    private fun openAssetSchemePicker() {
        // Ищем файлы в filesDir/data/
        val dataDir = File(requireContext().filesDir, "data")
        val files = if (dataDir.exists()) {
            dataDir.listFiles()?.filter { 
                it.isFile && it.canRead() && 
                (it.name.endsWith(".pdf", ignoreCase = true) || it.name.endsWith(".txt", ignoreCase = true)) &&
                it.length() > 0 // Проверяем что файл не пустой
            }?.map { it.name }?.sorted() ?: emptyList()
        } else {
            emptyList()
        }

        if (files.isEmpty()) {
            textSelectedPath.text = "В папке filesDir/data нет файлов"
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Выберите схему из filesDir/data")
            .setItems(files.toTypedArray()) { _, which ->
                val fileName = files[which]
                val filePath = "filesDir/data/$fileName"
                textSelectedPath.text = filePath
                textSchemeTitle.text = fileName
                openFileScheme(fileName)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openFileScheme(fileName: String) {
        val pdfPath = "Schemes/$fileName"
        android.util.Log.d("SchemesFragment", "=== OPENING SCHEME FROM SCHEMES TAB ===")
        android.util.Log.d("SchemesFragment", "Selected file: '$fileName'")
        android.util.Log.d("SchemesFragment", "Final PDF path: '$pdfPath'")
        
        val intent = Intent(requireContext(), PdfViewerActivity::class.java).apply {
            putExtra("pdf_path", pdfPath)
        }
        startActivity(intent)
    }
    
    private fun openAssetPath(assetPath: String) {
        val intent = Intent(requireContext(), PdfViewerActivity::class.java).apply {
            putExtra("pdf_path", assetPath)
        }
        startActivity(intent)
    }
    
    // Реализация интерфейса RefreshableFragment
    override fun refreshData() {
        // Для SchemesFragment просто обновляем список файлов
        // В будущем можно добавить загрузку PDF с сервера
        android.util.Log.d("SchemesFragment", "Refreshing schemes data after sync...")
    }
    
    override fun isDataLoaded(): Boolean {
        // Для SchemesFragment данные всегда "загружены", так как они читаются по требованию
        return true
    }
    
    override fun getWatchedFilePath(): String? {
        return try {
            val dataDir = requireContext().filesDir.resolve("data")
            // Отслеживаем всю папку data для PDF и TXT файлов
            if (dataDir.exists()) {
                dataDir.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("SchemesFragment", "Error getting watched file path", e)
            null
        }
    }
    
    /**
     * Обеспечить загрузку данных (совместимость с MainActivity)
     */
    fun ensureDataLoaded() {
        // Для SchemesFragment данные загружаются по требованию при выборе файла
        android.util.Log.d("SchemesFragment", "Ensuring schemes data is loaded...")
    }
}
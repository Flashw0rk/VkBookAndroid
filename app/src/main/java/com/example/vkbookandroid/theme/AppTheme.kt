package com.example.vkbookandroid.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import kotlin.math.max

/**
 * Легковесная система тем приложения
 * Без создания лишних объектов, работает через примитивы Int
 */
object AppTheme {
    
    private const val PREFS_NAME = "AppThemePrefs"
    private const val KEY_CURRENT_THEME = "current_theme"
    
    // ID тем (Темная эргономичная УДАЛЕНА по просьбе!)
    const val THEME_CLASSIC = 0           // Классическая (текущая, по умолчанию)
    const val THEME_NUCLEAR = 1           // Атомная промышленность
    const val THEME_ERGONOMIC_LIGHT = 2   // Эргономичная светлая
    const val THEME_MODERN_GLASS = 3      // Современная Glass (было 4)
    const val THEME_MODERN_GRADIENT = 4   // Современная Брутальная (бывшая Gradient)
    const val THEME_ROSATOM = 5           // Корпоративный стиль Росатома
    
    private var currentThemeId = THEME_ROSATOM // По умолчанию тема Росатом
    @Volatile
    private var cachedNuclearBitmap: Bitmap? = null
    @Volatile
    private var cachedNuclearBitmapResId: Int? = null
    
    @Volatile
    private var cachedRosatomBitmap: Bitmap? = null
    @Volatile
    private var cachedRosatomBitmapResId: Int? = null
    
    /**
     * Загружает сохраненную тему
     */
    fun loadTheme(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentThemeId = prefs.getInt(KEY_CURRENT_THEME, THEME_ROSATOM)
    }
    
    /**
     * Сохраняет выбранную тему
     */
    fun saveTheme(context: Context, themeId: Int) {
        currentThemeId = themeId
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CURRENT_THEME, themeId).apply()
        
        // Очищаем кэши картинок для других тем
        if (themeId != THEME_NUCLEAR) {
            clearNuclearBitmapCache()
        }
        if (themeId != THEME_ROSATOM) {
            clearRosatomBitmapCache()
        }
    }
    
    /**
     * Получить текущую тему
     */
    fun getCurrentThemeId(): Int = currentThemeId

    fun isNuclearTheme(): Boolean = currentThemeId == THEME_NUCLEAR
    
    fun isRosatomTheme(): Boolean = currentThemeId == THEME_ROSATOM
    
    /**
     * Получить название темы
     */
    fun getThemeName(themeId: Int): String = when (themeId) {
        THEME_CLASSIC -> "📘 Классическая"
        THEME_NUCLEAR -> "⚛️ Атом"
        THEME_ERGONOMIC_LIGHT -> "🌿 Эргономичная"
        THEME_MODERN_GLASS -> "💎 Стеклянная"
        THEME_MODERN_GRADIENT -> "🧱 Брутальная"
        THEME_ROSATOM -> "🔷 Росатом"
        else -> "Неизвестная"
    }
    
    // ========================================
    // ЦВЕТОВЫЕ СХЕМЫ (используем Int для производительности)
    // ========================================
    
    // Основной цвет приложения
    fun getPrimaryColor(): Int = when (currentThemeId) {
        THEME_CLASSIC -> Color.parseColor("#1976D2") // Синий (текущий)
        THEME_NUCLEAR -> Color.parseColor("#0091D5") // Голубой Росатома
        THEME_ERGONOMIC_LIGHT -> Color.parseColor("#689F38") // Зеленый (мягкий, не кислотный!)
        THEME_MODERN_GLASS -> Color.parseColor("#00BCD4") // Циан
        THEME_MODERN_GRADIENT -> Color.parseColor("#E91E63") // Розовый
        THEME_ROSATOM -> Color.parseColor("#0091D5") // Корпоративный голубой Росатома
        else -> Color.parseColor("#1976D2")
    }
    
    // Акцентный цвет
    fun getAccentColor(): Int = when (currentThemeId) {
        THEME_CLASSIC -> Color.parseColor("#2196F3") // Светло-синий (текущий)
        THEME_NUCLEAR -> Color.parseColor("#00D8FF") // Более яркий подсвет (повыше контраст)
        THEME_ERGONOMIC_LIGHT -> Color.parseColor("#8BC34A") // Светло-зеленый (мягкий)
        THEME_MODERN_GLASS -> Color.parseColor("#80DEEA") // Светлый циан
        THEME_MODERN_GRADIENT -> Color.parseColor("#FF4081") // Ярко-розовый
        THEME_ROSATOM -> Color.parseColor("#FF6B35") // Фирменный оранжевый Росатома
        else -> Color.parseColor("#2196F3")
    }
    
    // Фон приложения
    fun getBackgroundColor(): Int = when (currentThemeId) {
        THEME_CLASSIC -> Color.parseColor("#FAFAFA") // Светлый (текущий)
        THEME_NUCLEAR -> Color.parseColor("#0D47A1") // Темно-синий (как на картинке атома!)
        THEME_ERGONOMIC_LIGHT -> Color.parseColor("#F1F8E9") // Очень светло-зеленый (НЕ кислотный!)
        THEME_MODERN_GLASS -> Color.parseColor("#E0F7FA") // Светло-циановый
        THEME_MODERN_GRADIENT -> Color.parseColor("#FCE4EC") // Светло-розовый
        THEME_ROSATOM -> Color.parseColor("#F0F8FF") // Очень светлый голубой (корпоративный)
        else -> Color.parseColor("#FAFAFA")
    }
    
    // Фон карточек/элементов
    fun getCardBackgroundColor(): Int = when (currentThemeId) {
        THEME_CLASSIC -> Color.parseColor("#FFFFFF") // Белый (текущий)
        THEME_NUCLEAR -> Color.parseColor("#FFFFFF") // Белый (Росатом)
        THEME_ERGONOMIC_LIGHT -> Color.parseColor("#FFFFFF") // Белый
        THEME_MODERN_GLASS -> Color.parseColor("#FFFFFF") // Белый (прозрачный)
        THEME_MODERN_GRADIENT -> Color.parseColor("#FFFFFF") // Белый
        THEME_ROSATOM -> Color.parseColor("#FFFFFF") // Белый (корпоративный)
        else -> Color.parseColor("#FFFFFF")
    }
    
    // Цвет текста основной (РАЗНЫЙ для каждой темы!)
    fun getTextPrimaryColor(): Int = when (currentThemeId) {
        THEME_CLASSIC -> Color.parseColor("#212121") // Черный (текущий)
        THEME_NUCLEAR -> Color.parseColor("#FFFFFF") // Максимально яркий текст на темном фоне
        THEME_ERGONOMIC_LIGHT -> Color.parseColor("#33691E") // Темно-зеленый (хорошая читаемость!)
        THEME_MODERN_GLASS -> Color.parseColor("#006064") // Темный циан
        THEME_MODERN_GRADIENT -> Color.parseColor("#880E4F") // Темно-розовый
        THEME_ROSATOM -> Color.parseColor("#003D5C") // Темно-синий корпоративный
        else -> Color.parseColor("#212121")
    }
    
    // Цвет текста вторичный (РАЗНЫЙ для каждой темы!)
    fun getTextSecondaryColor(): Int = when (currentThemeId) {
        THEME_CLASSIC -> Color.parseColor("#757575") // Серый (текущий)
        THEME_NUCLEAR -> Color.parseColor("#CFE8FF") // Светлый голубой, читается на темном фоне
        THEME_ERGONOMIC_LIGHT -> Color.parseColor("#558B2F") // Средне-зеленый
        THEME_MODERN_GLASS -> Color.parseColor("#00838F") // Средний циан
        THEME_MODERN_GRADIENT -> Color.parseColor("#AD1457") // Средне-розовый
        THEME_ROSATOM -> Color.parseColor("#0091D5") // Корпоративный голубой
        else -> Color.parseColor("#757575")
    }
    
    // Цвет активного элемента
    fun getActiveColor(): Int = when (currentThemeId) {
        THEME_CLASSIC -> Color.parseColor("#FFEB3B") // Желтый (текущий)
        THEME_NUCLEAR -> Color.parseColor("#FFC107") // ЯРКО-ЖЕЛТЫЙ (Атом) - было блеклое!
        THEME_ERGONOMIC_LIGHT -> Color.parseColor("#DCEDC8") // Светло-зеленый (пастельный)
        THEME_MODERN_GLASS -> Color.parseColor("#B2EBF2") // Светлый циан
        THEME_MODERN_GRADIENT -> Color.parseColor("#F8BBD0") // Светло-розовый
        THEME_ROSATOM -> Color.parseColor("#FFE0B2") // Светло-оранжевый (пастельный)
        else -> Color.parseColor("#FFEB3B")
    }
    
    // Цвет выбранного элемента
    fun getSelectedColor(): Int = when (currentThemeId) {
        THEME_CLASSIC -> Color.parseColor("#90CAF9") // Голубой (текущий)
        THEME_NUCLEAR -> Color.parseColor("#00E5FF") // Очень яркий голубой для лучшего выделения
        THEME_ERGONOMIC_LIGHT -> Color.parseColor("#AED581") // Светло-зеленый
        THEME_MODERN_GLASS -> Color.parseColor("#80DEEA") // Циан
        THEME_MODERN_GRADIENT -> Color.parseColor("#F06292") // Розовый
        THEME_ROSATOM -> Color.parseColor("#B3E5FC") // Светло-голубой корпоративный
        else -> Color.parseColor("#90CAF9")
    }
    
    // Цвет границ/разделителей
    fun getBorderColor(): Int = when (currentThemeId) {
        THEME_CLASSIC -> Color.parseColor("#CCCCCC") // Серый (текущий)
        THEME_NUCLEAR -> Color.parseColor("#0091D5") // Голубой Росатома
        THEME_ERGONOMIC_LIGHT -> Color.parseColor("#C5E1A5") // Светло-зеленый
        THEME_MODERN_GLASS -> Color.parseColor("#E0E0E0") // Светло-серый
        THEME_MODERN_GRADIENT -> Color.parseColor("#F5F5F5") // Почти белый
        THEME_ROSATOM -> Color.parseColor("#0091D5") // Корпоративный голубой
        else -> Color.parseColor("#CCCCCC")
    }

    fun getBorderColorStrong(): Int = when (currentThemeId) {
        THEME_CLASSIC -> Color.parseColor("#CCCCCC")
        THEME_NUCLEAR -> Color.parseColor("#1665C1")
        THEME_ERGONOMIC_LIGHT -> Color.parseColor("#8BC34A")
        THEME_MODERN_GLASS -> Color.parseColor("#0097A7")
        THEME_MODERN_GRADIENT -> Color.parseColor("#AD1457")
        THEME_ROSATOM -> Color.parseColor("#0277BD") // Темно-голубой для выделения
        else -> Color.parseColor("#CCCCCC")
    }
    
    // Цвет кнопок
    fun getButtonColor(): Int = when (currentThemeId) {
        THEME_CLASSIC -> Color.parseColor("#78909C") // Серо-синий (текущий)
        THEME_NUCLEAR -> Color.parseColor("#0091D5") // Голубой Росатома
        THEME_ERGONOMIC_LIGHT -> Color.parseColor("#689F38") // Зеленый (мягкий)
        THEME_MODERN_GLASS -> Color.parseColor("#00BCD4") // Циан
        THEME_MODERN_GRADIENT -> Color.parseColor("#E91E63") // Розовый
        THEME_ROSATOM -> Color.parseColor("#0091D5") // Корпоративный голубой
        else -> Color.parseColor("#78909C")
    }
    
    // Цвет текста на кнопках
    fun getButtonTextColor(): Int = when (currentThemeId) {
        THEME_CLASSIC -> Color.parseColor("#FFFFFF") // Белый (текущий)
        THEME_NUCLEAR -> Color.parseColor("#FFFFFF") // Белый
        THEME_ERGONOMIC_LIGHT -> Color.parseColor("#FFFFFF") // Белый
        THEME_MODERN_GLASS -> Color.parseColor("#FFFFFF") // Белый
        THEME_MODERN_GRADIENT -> Color.parseColor("#FFFFFF") // Белый
        THEME_ROSATOM -> Color.parseColor("#FFFFFF") // Белый
        else -> Color.parseColor("#FFFFFF")
    }
    
    // ========================================
    // ФОРМЫ И СТИЛИ (радиусы, обводки)
    // ========================================
    
    // Радиус скругления кнопок (dp)
    // Используем ОЧЕНЬ БОЛЬШОЙ радиус для овальных/pill-shaped кнопок
    fun getButtonCornerRadius(): Float = when (currentThemeId) {
        THEME_CLASSIC -> 0f // КЛАССИЧЕСКАЯ - БЕЗ ИЗМЕНЕНИЙ (как в исходном приложении)
        THEME_NUCLEAR -> 100f // Росатом - ОВАЛЬНЫЕ кнопки!
        THEME_ERGONOMIC_LIGHT -> 100f // Pill-shaped (овальные)
        THEME_MODERN_GLASS -> 100f // Pill-shaped (овальные)
        THEME_MODERN_GRADIENT -> 100f // Pill-shaped (овальные) с градиентом
        THEME_ROSATOM -> 100f // Корпоративный - овальные кнопки
        else -> 0f
    }
    
    // Радиус скругления карточек/ячеек (dp)
    fun getCardCornerRadius(): Float = when (currentThemeId) {
        THEME_CLASSIC -> 0f // КЛАССИЧЕСКАЯ - БЕЗ ИЗМЕНЕНИЙ
        THEME_NUCLEAR -> 8f // Росатом - слегка скругленные
        THEME_ERGONOMIC_LIGHT -> 12f
        THEME_MODERN_GLASS -> 16f
        THEME_MODERN_GRADIENT -> 20f
        THEME_ROSATOM -> 4f // Корпоративный - минимальное скругление
        else -> 0f
    }
    
    // Толщина обводки (px)
    fun getBorderWidth(): Float = when (currentThemeId) {
        THEME_CLASSIC -> 1f // КЛАССИЧЕСКАЯ - КАК БЫЛО
        THEME_NUCLEAR -> 3f // Росатом - ЖИРНЫЕ границы для профессионализма
        THEME_ERGONOMIC_LIGHT -> 1f
        THEME_MODERN_GLASS -> 1f
        THEME_MODERN_GRADIENT -> 0f // Без обводки, чистый градиент
        THEME_ROSATOM -> 2f // Корпоративный - средняя толщина
        else -> 1f
    }
    
    // Специальные цвета для атомной темы (Росатом)
    fun getRosatomOrangeColor(): Int = Color.parseColor("#FF9800") // Оранжевый Росатома
    fun getRosatomYellowColor(): Int = Color.parseColor("#FFC107") // Желтый акцент
    
    // Прозрачность для стеклянной темы
    fun getGlassAlpha(): Float = if (currentThemeId == THEME_MODERN_GLASS) 0.85f else 1.0f
    
    // ========================================
    // ELEVATION И ВИЗУАЛЬНАЯ ГЛУБИНА (лучшие практики Material Design 3)
    // ========================================
    
    // Elevation для кнопок (dp) - создает тени и глубину
    fun getButtonElevation(): Float = when (currentThemeId) {
        THEME_CLASSIC -> 0f // БЕЗ ИЗМЕНЕНИЙ
        THEME_NUCLEAR -> 4f // Средняя тень - профессионально
        THEME_ERGONOMIC_LIGHT -> 2f // Легкая тень
        THEME_MODERN_GLASS -> 8f // Сильная тень - "парящие" кнопки
        THEME_MODERN_GRADIENT -> 12f // Максимальная тень - эффект глубины
        THEME_ROSATOM -> 3f // Корпоративный - умеренная тень
        else -> 0f
    }
    
    // Elevation для карточек (dp)
    fun getCardElevation(): Float = when (currentThemeId) {
        THEME_CLASSIC -> 0f
        THEME_NUCLEAR -> 3f
        THEME_ERGONOMIC_LIGHT -> 2f
        THEME_MODERN_GLASS -> 6f
        THEME_MODERN_GRADIENT -> 8f
        THEME_ROSATOM -> 2f
        else -> 0f
    }
    
    // Padding для кнопок (dp) - ОДИНАКОВЫЕ ДЛЯ ВСЕХ ТЕМ (как в классической)
    fun getButtonPaddingHorizontal(): Int = 4
    
    fun getButtonPaddingVertical(): Int = 2
    
    // Размер текста кнопок (sp) - УМЕНЬШЕНЫ
    fun getButtonTextSize(): Float = when (currentThemeId) {
        THEME_CLASSIC -> 14f
        THEME_NUCLEAR -> 14f // Росатом - нормальный размер
        THEME_ERGONOMIC_LIGHT -> 14f
        THEME_MODERN_GLASS -> 14f
        THEME_MODERN_GRADIENT -> 15f
        THEME_ROSATOM -> 14f // Корпоративный - стандартный размер
        else -> 14f
    }
    
    /**
     * Проверка темной темы
     */
    fun isDarkTheme(): Boolean = when (currentThemeId) {
        THEME_CLASSIC -> false
        THEME_NUCLEAR -> false // Росатом - светлая!
        THEME_ERGONOMIC_LIGHT -> false
        THEME_MODERN_GLASS -> false
        THEME_MODERN_GRADIENT -> false
        THEME_ROSATOM -> false // Корпоративная - светлая
        else -> false
    }
    
    /**
     * Описание темы
     */
    fun getThemeDescription(themeId: Int): String = when (themeId) {
        THEME_CLASSIC -> "Стандартное оформление. Проверенный временем дизайн, удобный для работы."
        THEME_NUCLEAR -> "Голубые и белые тона с оранжевыми акцентами."
        THEME_ERGONOMIC_LIGHT -> "Светлая эргономичная тема с природными мягкими зелеными тонами. Снижает усталость глаз при длительной работы."
        THEME_MODERN_GLASS -> "Современный стеклянный дизайн с легкой прозрачностью. Воздушный и элегантный."
        THEME_MODERN_GRADIENT -> "Контрастное брутальное оформление с насыщенными акцентами и строгой геометрией."
        THEME_ROSATOM -> "Голубые и оранжевые тона с чистым светлым фоном."
        else -> ""
    }
    
    /**
     * Получить все доступные темы
     */
    fun getAllThemes(): List<Int> = listOf(
        THEME_CLASSIC,
        THEME_ERGONOMIC_LIGHT,
        THEME_MODERN_GRADIENT,
        THEME_NUCLEAR,
        THEME_MODERN_GLASS,
        THEME_ROSATOM
    )
    
    // ========================================
    // СОЗДАНИЕ DRAWABLE (формы кнопок, ячеек)
    // ========================================
    
    /**
     * Проверка применения темы (классическая тема НЕ применяется программно)
     */
    fun shouldApplyTheme(): Boolean = currentThemeId != THEME_CLASSIC
    
    /**
     * Создать drawable для кнопки с текущей темой
     * ВАЖНО: Классическая тема возвращает null (не меняет исходное оформление)
     */
    fun createButtonDrawable(): GradientDrawable? {
        if (currentThemeId == THEME_CLASSIC) return null // НЕ ТРОГАЕМ классическую тему!
        
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(getButtonColor())
            
            // Для овальных кнопок (pill-shaped) используем очень большой радиус
            // Он автоматически сделает кнопку овальной
            val radius = getButtonCornerRadius()
            if (radius > 50f) {
                // Pill-shaped - высота кнопки определяет радиус
                cornerRadius = 1000f // Очень большой радиус = полностью овальная
            } else {
                cornerRadius = radius
            }
            
            if (getBorderWidth() > 0) {
                setStroke(getBorderWidth().toInt(), getBorderColor())
            }
        }
    }
    
    /**
     * Создать drawable для кнопки с произвольным цветом
     */
    fun createButtonDrawable(backgroundColor: Int): GradientDrawable? {
        if (currentThemeId == THEME_CLASSIC) return null // НЕ ТРОГАЕМ!
        
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(backgroundColor)
            
            val radius = getButtonCornerRadius()
            cornerRadius = if (radius > 50f) 1000f else radius
            
            if (getBorderWidth() > 0) {
                setStroke(getBorderWidth().toInt(), getBorderColor())
            }
        }
    }
    
    /**
     * Создать drawable для карточки/ячейки
     */
    fun createCardDrawable(backgroundColor: Int = getCardBackgroundColor()): GradientDrawable? {
        if (currentThemeId == THEME_CLASSIC) return null // НЕ ТРОГАЕМ!
        
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(backgroundColor)
            cornerRadius = getCardCornerRadius()
            if (getBorderWidth() > 0) {
                setStroke(getBorderWidth().toInt(), getBorderColor())
            }
        }
    }
    
    /**
     * Создать drawable с обводкой для ячеек
     */
    fun createCellDrawable(backgroundColor: Int, borderColor: Int = getBorderColor()): GradientDrawable? {
        if (currentThemeId == THEME_CLASSIC) return null // НЕ ТРОГАЕМ!
        
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = getCardCornerRadius()
            setColor(backgroundColor)
            setStroke(getBorderWidth().toInt(), borderColor)
        }
    }
    
    /**
     * Осветлить цвет (для пастельных оттенков)
     */
    fun lighten(color: Int, factor: Float = 0.4f): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val nr = (r + (255 - r) * factor).toInt().coerceIn(0, 255)
        val ng = (g + (255 - g) * factor).toInt().coerceIn(0, 255)
        val nb = (b + (255 - b) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(nr, ng, nb)
    }
    
    /**
     * Затемнить цвет
     */
    fun darken(color: Int, factor: Float = 0.2f): Int {
        val r = (Color.red(color) * (1 - factor)).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * (1 - factor)).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * (1 - factor)).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
    
    /**
     * Получить ID drawable для фона (с символикой)
     * Каждая тема имеет уникальный фон!
     */
    fun getBackgroundDrawable(context: Context): Drawable? {
        android.util.Log.d("AppTheme", "getBackgroundDrawable() вызван для темы: $currentThemeId (${getThemeName(currentThemeId)})")
        
        val drawable = when (currentThemeId) {
            THEME_CLASSIC -> null
            THEME_NUCLEAR -> createNuclearBackgroundDrawable(context)
            THEME_ERGONOMIC_LIGHT -> ContextCompat.getDrawable(context, com.example.vkbookandroid.R.drawable.bg_ergonomic_light)
            THEME_MODERN_GLASS -> ContextCompat.getDrawable(context, com.example.vkbookandroid.R.drawable.bg_modern_glass)
            THEME_MODERN_GRADIENT -> ContextCompat.getDrawable(context, com.example.vkbookandroid.R.drawable.bg_modern_gradient)
            THEME_ROSATOM -> createRosatomBackgroundDrawable(context)
            else -> null
        }
        
        android.util.Log.d("AppTheme", "getBackgroundDrawable() вернул: ${if (drawable != null) "drawable" else "NULL"}")
        return drawable
    }

    private fun createNuclearBackgroundDrawable(context: Context): Drawable? {
        android.util.Log.d("AppTheme", "createNuclearBackgroundDrawable() начало")
        
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#07152D"), Color.parseColor("#0B1F46"), Color.parseColor("#04102A"))
        ).apply { shape = GradientDrawable.RECTANGLE }

        val bitmap = getNuclearBitmap(context)
        android.util.Log.d("AppTheme", "getNuclearBitmap() вернул: ${if (bitmap != null) "bitmap ${bitmap.width}x${bitmap.height}" else "NULL"}")
        
        return if (bitmap != null) {
            android.util.Log.d("AppTheme", "Создаем LayerDrawable с градиентом и картинкой")
            LayerDrawable(arrayOf(gradient, CenterCropBitmapDrawable(bitmap)))
        } else {
            // Фоллбек: используем статичную картинку если не удалось декодировать
            android.util.Log.w("AppTheme", "Bitmap не загрузился, используем fallback drawable")
            ContextCompat.getDrawable(context, com.example.vkbookandroid.R.drawable.bg_atom_3d_realistic) ?: gradient
        }
    }

    private fun getNuclearBitmap(context: Context): Bitmap? {
        android.util.Log.d("AppTheme", "getNuclearBitmap() начало")
        
        val targetRes = com.example.vkbookandroid.R.drawable.bg_atom_photo_full
        val fallbackRes = com.example.vkbookandroid.R.drawable.bg_atom_photo_image

        val existing = cachedNuclearBitmap
        if (existing != null && !existing.isRecycled) {
            val resId = cachedNuclearBitmapResId
            if (resId == targetRes || resId == fallbackRes) {
                android.util.Log.d("AppTheme", "Используем кэшированный bitmap")
                return existing
            }
        }

        android.util.Log.d("AppTheme", "Декодируем bitmap из ресурсов...")
        var resUsed: Int? = null
        var decoded: Bitmap? = decodeBitmapResource(context, targetRes)
        if (decoded == null) {
            android.util.Log.w("AppTheme", "Не удалось загрузить bg_atom_photo_full, пробуем fallback")
            decoded = decodeBitmapResource(context, fallbackRes)
            resUsed = if (decoded != null) fallbackRes else null
        } else {
            resUsed = targetRes
        }

        if (decoded != null) {
            cachedNuclearBitmap = decoded
            cachedNuclearBitmapResId = resUsed
            android.util.Log.d("AppTheme", "Bitmap успешно декодирован и закэширован: ${decoded.width}x${decoded.height}")
        } else {
            android.util.Log.e("AppTheme", "НЕ УДАЛОСЬ загрузить ни один bitmap для темы Атом!")
        }

        return decoded
    }

    private fun decodeBitmapResource(context: Context, resId: Int): Bitmap? {
        return try {
            android.util.Log.d("AppTheme", "decodeBitmapResource() для resId=$resId")
            
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.resources, resId)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            } else {
                BitmapFactory.decodeResource(context.resources, resId)
            }
            
            android.util.Log.d("AppTheme", "decodeBitmapResource() успешно: ${bitmap?.width}x${bitmap?.height}")
            bitmap
        } catch (e: Throwable) {
            android.util.Log.e("AppTheme", "decodeBitmapResource() ОШИБКА для resId=$resId", e)
            null
        }
    }

    private fun clearNuclearBitmapCache() {
        cachedNuclearBitmap = null
        cachedNuclearBitmapResId = null
    }
    
    private fun clearRosatomBitmapCache() {
        cachedRosatomBitmap = null
        cachedRosatomBitmapResId = null
    }
    
    private fun createRosatomBackgroundDrawable(context: Context): Drawable {
        android.util.Log.d("AppTheme", "createRosatomBackgroundDrawable() начало")
        
        // Корпоративный градиент Росатома: от светло-голубого к белому
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#E1F5FE"), // Очень светлый голубой (верх)
                Color.parseColor("#F0F8FF"), // Почти белый (середина)
                Color.parseColor("#FFFFFF")  // Белый (низ)
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
        }
        
        // Пытаемся загрузить картинку Росатома
        val bitmap = getRosatomBitmap(context)
        android.util.Log.d("AppTheme", "getRosatomBitmap() вернул: ${if (bitmap != null) "bitmap ${bitmap.width}x${bitmap.height}" else "NULL"}")
        
        return if (bitmap != null) {
            // Используем специальный drawable с настройками:
            // - сдвиг: 5 мм правее (было 1 см влево), 1 см вниз
            // - масштаб уменьшен на 10% (0.9)
            val density = context.resources.displayMetrics.density
            val offsetXDp = -18.9f // 1 см влево - 5 мм = -37.8 + 18.9 = -18.9dp (5мм ≈ 18.9dp)
            val offsetYDp = 37.8f  // 1 см вниз (37.8dp ≈ 1cm)
            val scaleMultiplier = 0.9f // Уменьшение на 10%
            
            android.util.Log.d("AppTheme", "Создаем LayerDrawable для Росатом с картинкой")
            LayerDrawable(arrayOf(
                gradient, 
                AdjustableCropBitmapDrawable(bitmap, offsetXDp, offsetYDp, scaleMultiplier)
            ))
        } else {
            android.util.Log.w("AppTheme", "Bitmap Росатом не загрузился, используем только градиент")
            gradient
        }
    }
    
    
    private fun getRosatomBitmap(context: Context): Bitmap? {
        try {
            android.util.Log.d("AppTheme", "getRosatomBitmap() начало")
            
            // Загружаем картинку Росатома (bg_rosatom_photo.jpg)
            val targetRes = com.example.vkbookandroid.R.drawable.bg_rosatom_photo
            
            // Проверяем кэш
            val existing = cachedRosatomBitmap
            if (existing != null && !existing.isRecycled) {
                val resId = cachedRosatomBitmapResId
                if (resId == targetRes) {
                    android.util.Log.d("AppTheme", "Используем кэшированный bitmap Росатом")
                    return existing
                }
            }
            
            android.util.Log.d("AppTheme", "Декодируем bitmap Росатом из ресурсов...")
            
            // Декодируем картинку
            val decoded = decodeBitmapResource(context, targetRes)
            if (decoded != null) {
                cachedRosatomBitmap = decoded
                cachedRosatomBitmapResId = targetRes
                android.util.Log.d("AppTheme", "Картинка Росатома загружена успешно: ${decoded.width}x${decoded.height}")
                return decoded
            }
            
            android.util.Log.w("AppTheme", "Не удалось загрузить картинку Росатома, используем градиент")
            return null
        } catch (e: Exception) {
            android.util.Log.e("AppTheme", "Ошибка загрузки картинки Росатома", e)
            return null
        }
    }
    
    // ========================================
    // ПРИМЕНЕНИЕ СТИЛЕЙ К VIEW (опыт профессионалов)
    // ========================================
    
    /**
     * Применить полный стиль к кнопке (цвет, форма, размер, тень)
     * Следует лучшим практикам Material Design 3
     */
    fun applyButtonStyle(button: Button) {
        if (currentThemeId == THEME_CLASSIC) return // НЕ ТРОГАЕМ!
        
        // Форма и цвет
        button.background = createButtonDrawable()
        button.setTextColor(getButtonTextColor())
        
        // Размер текста
        button.textSize = getButtonTextSize()
        
        // НЕ ПЕРЕОПРЕДЕЛЯЕМ PADDING - он устанавливается в вызывающем коде
        
        // Elevation (тень) - эффект глубины
        val px = button.context.resources.displayMetrics.density
        button.elevation = getButtonElevation() * px
        button.translationZ = getButtonElevation() * px / 2
    }
    
    /**
     * Применить стиль к карточке
     */
    fun applyCardStyle(card: CardView) {
        if (currentThemeId == THEME_CLASSIC) return
        
        card.setCardBackgroundColor(getCardBackgroundColor())
        card.radius = getCardCornerRadius()
        card.cardElevation = getCardElevation() * card.context.resources.displayMetrics.density
    }
    
    /**
     * Создать gradient background для кнопки (современные темы)
     */
    fun createGradientButtonDrawable(): GradientDrawable? {
        if (currentThemeId == THEME_CLASSIC) return null
        
        return when (currentThemeId) {
            THEME_MODERN_GRADIENT -> {
                // Розовый градиент
                GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(
                        Color.parseColor("#E91E63"),
                        Color.parseColor("#F06292"),
                        Color.parseColor("#F48FB1")
                    )
                ).apply {
                    cornerRadius = 1000f // Овальная
                    shape = GradientDrawable.RECTANGLE
                }
            }
            THEME_NUCLEAR -> {
                // Голубой градиент Росатома
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.parseColor("#0091D5"),
                        Color.parseColor("#00C4FF")
                    )
                ).apply {
                    cornerRadius = 1000f // Овальная
                    shape = GradientDrawable.RECTANGLE
                    setStroke(getBorderWidth().toInt(), Color.parseColor("#00C4FF"))
                }
            }
            THEME_ROSATOM -> {
                // Корпоративный градиент: голубой с оранжевым акцентом
                GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(
                        Color.parseColor("#0091D5"), // Корпоративный голубой
                        Color.parseColor("#03A9F4"), // Светло-голубой
                        Color.parseColor("#FF6B35")  // Оранжевый акцент
                    )
                ).apply {
                    cornerRadius = 1000f // Овальные кнопки
                    shape = GradientDrawable.RECTANGLE
                    setStroke(2, Color.parseColor("#0091D5"))
                }
            }
            else -> createButtonDrawable() // Обычная
        }
    }
}


package com.example.vkbookandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextPaint
import android.text.Layout
import android.util.AttributeSet
import android.view.View

class MarkerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.RED
        strokeWidth = 4f
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = paint.color
        textSize = 14f * resources.displayMetrics.scaledDensity
    }

    private val rectBmp = RectF()
    private var imageMatrixCopy: Matrix = Matrix()
    private var pdfToBitmapScale: Float = 1f
    private var hasMarker: Boolean = false

    // Доп. данные для комментария
    private var labelText: String? = null
    private var commentText: String? = null
    private var commentColor: Int = Color.RED
    private var labelLayoutCache: StaticLayout? = null
    private var commentLayoutCache: StaticLayout? = null
    private var maxCommentWidthPx: Int = mmToPx(40f) // ограничение ширины ~40 мм

    // Параметры «умного» позиционирования
    private val edgeProximityPx: Float = mmToPx(15f).toFloat() // ~1.5 см
    private val gapPx: Float = mmToPx(2f).toFloat()            // ~2 мм

    // Параметры для pinned-режима (тайл видимой области)
    private var useViewportMapping: Boolean = false
    private var vpLeftPage: Float = 0f
    private var vpTopPage: Float = 0f
    private var vpScaleX: Float = 1f
    private var vpScaleY: Float = 1f

    fun setMarkerInPdfUnits(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        pdfToBmpScale: Float,
        currentImageMatrix: Matrix,
        label: String? = null,
        color: Int? = null,
        comment: String? = null
    ) {
        // Пересчитываем в координаты битмапа
        pdfToBitmapScale = pdfToBmpScale
        // Интерпретируем x,y как центр маркера
        val cx = x * pdfToBitmapScale
        val cy = y * pdfToBitmapScale
        val halfW = (width * pdfToBitmapScale) * 0.5f
        val halfH = (height * pdfToBitmapScale) * 0.5f
        rectBmp.set(
            cx - halfW,
            cy - halfH,
            cx + halfW,
            cy + halfH
        )
        imageMatrixCopy = Matrix(currentImageMatrix)
        hasMarker = true
        useViewportMapping = false
        labelText = label
        commentText = comment
        color?.let { applyColor(it) }
        labelLayoutCache = null
        commentLayoutCache = null
        visibility = VISIBLE
        invalidate()
    }

    // Отрисовка для pinned-режима: координаты в единицах страницы с учётом обрезанного тайла
    fun setMarkerInPageUnitsWithViewport(
        pageX: Float,
        pageY: Float,
        pageW: Float,
        pageH: Float,
        leftPage: Float,
        topPage: Float,
        scaleX: Float,
        scaleY: Float,
        currentImageMatrix: Matrix,
        label: String? = null,
        color: Int? = null,
        comment: String? = null
    ) {
        // Преобразуем координаты из страниц в пиксели текущего тайла (битмапа)
        // Интерпретируем pageX,pageY как центр маркера
        val halfWp = pageW * 0.5f
        val halfHp = pageH * 0.5f
        val leftBmp = ((pageX - halfWp) - leftPage) * scaleX
        val topBmp = ((pageY - halfHp) - topPage) * scaleY
        val rightBmp = ((pageX + halfWp) - leftPage) * scaleX
        val bottomBmp = ((pageY + halfHp) - topPage) * scaleY
        rectBmp.set(leftBmp, topBmp, rightBmp, bottomBmp)
        imageMatrixCopy = Matrix(currentImageMatrix)
        hasMarker = true
        useViewportMapping = true
        vpLeftPage = leftPage
        vpTopPage = topPage
        vpScaleX = scaleX
        vpScaleY = scaleY
        labelText = label
        commentText = comment
        color?.let { applyColor(it) }
        labelLayoutCache = null
        commentLayoutCache = null
        visibility = VISIBLE
        invalidate()
    }

    fun clearMarker() {
        hasMarker = false
        visibility = GONE
        invalidate()
    }

    fun onImageMatrixChanged(currentImageMatrix: Matrix) {
        imageMatrixCopy = Matrix(currentImageMatrix)
        if (hasMarker) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasMarker) return
        // Преобразуем координаты прямоугольника из пространства битмапа в пространство вью (экрана)
        val pts = floatArrayOf(
            rectBmp.left, rectBmp.top,
            rectBmp.right, rectBmp.bottom
        )
        imageMatrixCopy.mapPoints(pts)
        val left = pts[0]
        val top = pts[1]
        val right = pts[2]
        val bottom = pts[3]
        canvas.drawRect(left, top, right, bottom, paint)

        // Рисуем название арматуры над маркером
        if (!labelText.isNullOrBlank()) {
            textPaint.color = commentColor
            textPaint.textSize = 16f * resources.displayMetrics.scaledDensity // Больший размер для названия
            
            val availableWidth = maxCommentWidthPx.coerceAtMost(width)
            val labelLayout = labelLayoutCache ?: buildStaticLayout(labelText!!, availableWidth).also { labelLayoutCache = it }
            
            // Позиционируем название над маркером
            val centerX = (left + right) * 0.5f
            val labelX = centerX - labelLayout.width / 2f
            val labelY = top - labelLayout.height - gapPx
            
            canvas.save()
            canvas.translate(labelX, labelY)
            labelLayout.draw(canvas)
            canvas.restore()
        }
        
        // Рисуем комментарий под маркером
        if (!commentText.isNullOrBlank()) {
            textPaint.color = commentColor
            textPaint.textSize = 14f * resources.displayMetrics.scaledDensity // Обычный размер для комментария
            
            val availableWidth = maxCommentWidthPx.coerceAtMost(width)
            val commentLayout = commentLayoutCache ?: buildStaticLayout(commentText!!, availableWidth).also { commentLayoutCache = it }
            
            // Позиционируем комментарий под маркером
            val centerX = (left + right) * 0.5f
            val commentX = centerX - commentLayout.width / 2f
            val commentY = bottom + gapPx
            
            canvas.save()
            canvas.translate(commentX, commentY)
            commentLayout.draw(canvas)
            canvas.restore()
        }
    }

    private enum class Side { TOP, BOTTOM, LEFT, RIGHT }
    
    /**
     * Смарт-позиционирование комментария с учетом близости к краям
     * Порог близости: 1.5 см, зазор: 2 мм
     */
    private fun calculateSmartPosition(
        centerX: Float, centerY: Float, 
        layoutWidth: Int, layoutHeight: Int,
        viewWidth: Float, viewHeight: Float
    ): Pair<Float, Float> {
        
        // Базовое позиционирование снизу по центру
        var tx = centerX - layoutWidth / 2f
        var ty = centerY + gapPx
        
        // Проверяем близость к краям
        val isNearLeft = centerX < edgeProximityPx
        val isNearRight = centerX > viewWidth - edgeProximityPx
        val isNearTop = centerY < edgeProximityPx
        val isNearBottom = centerY > viewHeight - edgeProximityPx
        
        // Смарт-позиционирование
        when {
            isNearLeft && isNearTop -> {
                // Левый верхний угол - размещаем справа снизу
                tx = centerX + gapPx
                ty = centerY + gapPx
            }
            isNearRight && isNearTop -> {
                // Правый верхний угол - размещаем слева снизу
                tx = centerX - layoutWidth - gapPx
                ty = centerY + gapPx
            }
            isNearLeft && isNearBottom -> {
                // Левый нижний угол - размещаем справа сверху
                tx = centerX + gapPx
                ty = centerY - layoutHeight - gapPx
            }
            isNearRight && isNearBottom -> {
                // Правый нижний угол - размещаем слева сверху
                tx = centerX - layoutWidth - gapPx
                ty = centerY - layoutHeight - gapPx
            }
            isNearLeft -> {
                // Близко к левому краю - размещаем справа
                tx = centerX + gapPx
                ty = centerY - layoutHeight / 2f
            }
            isNearRight -> {
                // Близко к правому краю - размещаем слева
                tx = centerX - layoutWidth - gapPx
                ty = centerY - layoutHeight / 2f
            }
            isNearTop -> {
                // Близко к верхнему краю - размещаем снизу
                tx = centerX - layoutWidth / 2f
                ty = centerY + gapPx
            }
            isNearBottom -> {
                // Близко к нижнему краю - размещаем сверху
                tx = centerX - layoutWidth / 2f
                ty = centerY - layoutHeight - gapPx
            }
        }
        
        // Ограничиваем в пределах view
        tx = tx.coerceIn(0f, viewWidth - layoutWidth)
        ty = ty.coerceIn(0f, viewHeight - layoutHeight)
        
        return Pair(tx, ty)
    }

    private fun buildStaticLayout(text: String, maxWidthPx: Int): StaticLayout {
        @Suppress("DEPRECATION")
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxWidthPx)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()
        } else {
            StaticLayout(text, textPaint, maxWidthPx, Layout.Alignment.ALIGN_CENTER, 1.0f, 0f, false)
        }
    }

    private fun mmToPx(mm: Float): Int {
        val dpi = resources.displayMetrics.xdpi.takeIf { it > 0f } ?: resources.displayMetrics.densityDpi.toFloat()
        return kotlin.math.max(1, (mm * dpi / 25.4f).toInt())
    }

    private fun applyColor(color: Int) {
        paint.color = color
        textPaint.color = color
        commentColor = color
    }
}



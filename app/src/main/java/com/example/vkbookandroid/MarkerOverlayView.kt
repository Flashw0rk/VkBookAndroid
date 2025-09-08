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
    private var commentColor: Int = Color.RED
    private var layoutCache: StaticLayout? = null
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
        color: Int? = null
    ) {
        // Пересчитываем в координаты битмапа
        pdfToBitmapScale = pdfToBmpScale
        rectBmp.set(
            x * pdfToBitmapScale,
            y * pdfToBitmapScale,
            (x + width) * pdfToBitmapScale,
            (y + height) * pdfToBitmapScale
        )
        imageMatrixCopy = Matrix(currentImageMatrix)
        hasMarker = true
        useViewportMapping = false
        labelText = label
        color?.let { applyColor(it) }
        layoutCache = null
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
        color: Int? = null
    ) {
        // Преобразуем координаты из страниц в пиксели текущего тайла (битмапа)
        val leftBmp = (pageX - leftPage) * scaleX
        val topBmp = (pageY - topPage) * scaleY
        val rightBmp = (pageX + pageW - leftPage) * scaleX
        val bottomBmp = (pageY + pageH - topPage) * scaleY
        rectBmp.set(leftBmp, topBmp, rightBmp, bottomBmp)
        imageMatrixCopy = Matrix(currentImageMatrix)
        hasMarker = true
        useViewportMapping = true
        vpLeftPage = leftPage
        vpTopPage = topPage
        vpScaleX = scaleX
        vpScaleY = scaleY
        labelText = label
        color?.let { applyColor(it) }
        layoutCache = null
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

        // Рисуем комментарий/лейбл, если задан
        val text = labelText ?: return
        textPaint.color = commentColor

        // Готовим StaticLayout с ограничением по ширине
        val availableWidth = maxCommentWidthPx.coerceAtMost(width)
        val layout = layoutCache ?: buildStaticLayout(text, availableWidth).also { layoutCache = it }

        // Фиксированное размещение: строго НАД областью, по центру
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = (left + right) * 0.5f
        var tx = centerX - layout.width / 2f
        var ty = top - gapPx - layout.height

        // Без клампа: подпись остаётся жёстко привязанной к области

        canvas.save()
        canvas.translate(tx, ty)
        layout.draw(canvas)
        canvas.restore()
    }

    private enum class Side { TOP, BOTTOM, LEFT, RIGHT }

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



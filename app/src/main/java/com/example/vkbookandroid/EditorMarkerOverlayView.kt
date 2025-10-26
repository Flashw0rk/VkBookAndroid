package com.example.vkbookandroid

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.withSave

/**
 * Оверлей редактора: поддержка нескольких меток, перетаскивания, долгого тапа и hit-test.
 * Хранит координаты меток в PDF-единицах; отображает с учётом pdfToBitmapScale и imageMatrix.
 */
class EditorMarkerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class EditorMarkerItem(
        val id: String,
        var page: Int,
        var xPdf: Float,
        var yPdf: Float,
        var wPdf: Float,
        var hPdf: Float,
        var color: Int,
        var label: String? = null,
        var comment: String? = null,
        var markerType: String? = null
    )

    interface Listener {
        fun onAddRequested(xPdf: Float, yPdf: Float, page: Int)
        fun onEditRequested(marker: EditorMarkerItem)
        fun onMoveFinished(marker: EditorMarkerItem, newXPdf: Float, newYPdf: Float)
    }

    private val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.RED
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        textSize = 14f * resources.displayMetrics.scaledDensity
    }

    private val workRectBmp = RectF()
    private val tmpPts = FloatArray(4)
    private val matrixViewToBmpInv = Matrix()
    private var imageMatrixCopy: Matrix = Matrix()
    private var pdfToBmpScale: Float = 1f
    private var currentPage: Int = 0
    private var editMode: Boolean = false
    private var listener: Listener? = null

    // Целевая вью для панорамирования (например, ZoomableImageView)
    private var panTargetView: View? = null
    fun setPanTargetView(view: View?) { panTargetView = view }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!editMode) {
            val target = panTargetView
            return if (target != null) target.dispatchTouchEvent(event) else false
        }
        return super.dispatchTouchEvent(event)
    }

    private val markers: MutableList<EditorMarkerItem> = mutableListOf()

    fun setListener(l: Listener?) { listener = l }

    fun setEditMode(enabled: Boolean) {
        editMode = enabled
    }

    fun setPdfMapping(pdfToBitmapScale: Float, imageMatrix: Matrix) {
        this.pdfToBmpScale = pdfToBitmapScale
        this.imageMatrixCopy = Matrix(imageMatrix)
        invalidate()
    }

    fun setCurrentPage(page: Int) {
        currentPage = page
        invalidate()
    }

    fun setMarkers(list: List<EditorMarkerItem>) {
        markers.clear()
        markers.addAll(list)
        invalidate()
    }

    fun getMarkers(): List<EditorMarkerItem> = markers.map { it.copy() }

    fun clearMarkers() {
        markers.clear()
        invalidate()
    }

    fun addMarker(item: EditorMarkerItem) {
        markers.add(item)
        invalidate()
    }

    fun editMarker(existing: EditorMarkerItem, updated: EditorMarkerItem) {
        val idx = markers.indexOfFirst { it.id == existing.id }
        if (idx >= 0) {
            markers[idx] = updated
            invalidate()
        }
    }

    private fun markerToBmpRect(m: EditorMarkerItem, out: RectF) {
        // Интерпретируем xPdf,yPdf как центр маркера
        val cx = m.xPdf * pdfToBmpScale
        val cy = m.yPdf * pdfToBmpScale
        val halfW = (m.wPdf * pdfToBmpScale) * 0.5f
        val halfH = (m.hPdf * pdfToBmpScale) * 0.5f
        out.set(
            cx - halfW,
            cy - halfH,
            cx + halfW,
            cy + halfH
        )
    }

    private fun hitTestMarker(xView: Float, yView: Float): EditorMarkerItem? {
        // Преобразуем координаты касания из вью в координаты битмапа
        if (!imageMatrixCopy.invert(matrixViewToBmpInv)) return null
        val pts = floatArrayOf(xView, yView)
        matrixViewToBmpInv.mapPoints(pts)
        val bx = pts[0]
        val by = pts[1]
        // Проверяем по всем маркерам на текущей странице
        for (i in markers.indices.reversed()) {
            val m = markers[i]
            if (m.page != currentPage) continue
            markerToBmpRect(m, workRectBmp)
            if (workRectBmp.contains(bx, by)) return m
        }
        return null
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onLongPress(e: MotionEvent) {
            if (!editMode) return
            val hit = hitTestMarker(e.x, e.y)
            if (hit != null) {
                listener?.onEditRequested(hit)
            } else {
                // Добавление новой метки
                // Конвертируем в PDF-координаты через обратное преобразование
                if (!imageMatrixCopy.invert(matrixViewToBmpInv)) return
                val pts = floatArrayOf(e.x, e.y)
                matrixViewToBmpInv.mapPoints(pts)
                val xPdf = pts[0] / pdfToBmpScale
                val yPdf = pts[1] / pdfToBmpScale
                listener?.onAddRequested(xPdf, yPdf, currentPage)
            }
        }
    })

    private var draggingMarker: EditorMarkerItem? = null
    private var lastTouchBmpX = 0f
    private var lastTouchBmpY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Если режим редактирования выключен — не перехватываем жесты, чтобы панорамирование ZoomableImageView работало напрямую
        if (!editMode) return false
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = hitTestMarker(event.x, event.y)
                if (hit != null) {
                    draggingMarker = hit
                    // Запоминаем стартовую позицию в bmp-координатах для корректного смещения
                    if (imageMatrixCopy.invert(matrixViewToBmpInv)) {
                        val pts = floatArrayOf(event.x, event.y)
                        matrixViewToBmpInv.mapPoints(pts)
                        lastTouchBmpX = pts[0]
                        lastTouchBmpY = pts[1]
                    }
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dragging = draggingMarker ?: return true
                if (!imageMatrixCopy.invert(matrixViewToBmpInv)) return true
                val pts = floatArrayOf(event.x, event.y)
                matrixViewToBmpInv.mapPoints(pts)
                val curBmpX = pts[0]
                val curBmpY = pts[1]
                val dxBmp = curBmpX - lastTouchBmpX
                val dyBmp = curBmpY - lastTouchBmpY
                if (dxBmp != 0f || dyBmp != 0f) {
                    // Смещаем маркер в PDF-координатах
                    dragging.xPdf += dxBmp / pdfToBmpScale
                    dragging.yPdf += dyBmp / pdfToBmpScale
                    lastTouchBmpX = curBmpX
                    lastTouchBmpY = curBmpY
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dragging = draggingMarker
                if (dragging != null) {
                    draggingMarker = null
                    listener?.onMoveFinished(dragging, dragging.xPdf, dragging.yPdf)
                }
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Рисуем только маркеры текущей страницы
        for (m in markers) {
            if (m.page != currentPage) continue
            rectPaint.color = m.color
            textPaint.color = m.color
            markerToBmpRect(m, workRectBmp)
            // Переводим прямоугольник в координаты вью
            tmpPts[0] = workRectBmp.left
            tmpPts[1] = workRectBmp.top
            tmpPts[2] = workRectBmp.right
            tmpPts[3] = workRectBmp.bottom
            imageMatrixCopy.mapPoints(tmpPts)
            val l = tmpPts[0]
            val t = tmpPts[1]
            val r = tmpPts[2]
            val b = tmpPts[3]
            canvas.drawRect(l, t, r, b, rectPaint)
            val label = m.label ?: m.id
            if (!label.isNullOrBlank()) {
                canvas.withSave {
                    val tx = (l + r) * 0.5f - textPaint.measureText(label) / 2f
                    val ty = t - 6f
                    canvas.drawText(label, tx, ty, textPaint)
                }
            }
            if (!m.comment.isNullOrBlank()) {
                canvas.withSave {
                    val comment = m.comment!!
                    val tx = (l + r) * 0.5f - textPaint.measureText(comment) / 2f
                    val ty = b + textPaint.textSize + 6f
                    canvas.drawText(comment, tx, ty, textPaint)
                }
            }
        }
    }
}



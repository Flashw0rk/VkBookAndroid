package com.example.vkbookandroid

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrixValues = FloatArray(9)
    private val matrix = Matrix()
    private var minScale = 0.2f
    private var maxScale = 20f
    private var currentScale = 1f
    var onScaleChanged: ((Float) -> Unit)? = null
    var onMatrixChanged: ((Matrix) -> Unit)? = null
    fun getCurrentScale(): Float = currentScale
    fun getImageMatrixCopy(): Matrix = Matrix(matrix)
    fun resetMatrix() {
        matrix.reset()
        imageMatrix = matrix
        currentScale = 1f
        onScaleChanged?.invoke(currentScale)
        onMatrixChanged?.invoke(Matrix(matrix))
    }
    fun applyScale(factor: Float) {
        val target = (currentScale * factor).coerceIn(minScale, maxScale)
        val delta = target / currentScale
        // Масштаб вокруг центра виджета
        val px = width / 2f
        val py = height / 2f
        matrix.postScale(delta, delta, px, py)
        imageMatrix = matrix
        currentScale = target
        onScaleChanged?.invoke(currentScale)
        onMatrixChanged?.invoke(Matrix(matrix))
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            // ИСПРАВЛЕНИЕ: Сохраняем состояние панорамирования при начале масштабирования
            isPanning = false
            return true
        }
        
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val targetScale = (currentScale * scaleFactor).coerceIn(minScale, maxScale)
            val delta = targetScale / currentScale
            
            // ИСПРАВЛЕНИЕ: Плавное масштабирование с ограничением
            if (kotlin.math.abs(delta - 1.0f) > 0.01f) { // Игнорируем микро-изменения
                matrix.postScale(delta, delta, detector.focusX, detector.focusY)
                imageMatrix = matrix
                currentScale = targetScale
                onScaleChanged?.invoke(currentScale)
                onMatrixChanged?.invoke(Matrix(matrix))
            }
            return true
        }
        
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            // ИСПРАВЛЕНИЕ: Плавное завершение масштабирования
            // Не сбрасываем панорамирование здесь, это делается в handlePan
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val target = if (currentScale > minScale) minScale else 2f
            val delta = target / currentScale
            matrix.postScale(delta, delta, e.x, e.y)
            imageMatrix = matrix
            currentScale = target
            onScaleChanged?.invoke(currentScale)
            onMatrixChanged?.invoke(Matrix(matrix))
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
        imageMatrix = matrix
    }

    fun setScaleAndCenter(scale: Float, centerBitmapX: Float, centerBitmapY: Float) {
        val clamped = scale.coerceIn(minScale, maxScale)
        matrix.reset()
        matrix.postScale(clamped, clamped)
        val dx = width / 2f - centerBitmapX * clamped
        val dy = height / 2f - centerBitmapY * clamped
        matrix.postTranslate(dx, dy)
        imageMatrix = matrix
        currentScale = clamped
        onScaleChanged?.invoke(currentScale)
        onMatrixChanged?.invoke(Matrix(matrix))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled
        handlePan(event)
        return true
    }

    private var lastX = 0f
    private var lastY = 0f
    private var isPanning = false

    private fun handlePan(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                isPanning = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPanning && !scaleDetector.isInProgress && event.pointerCount == 1) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    matrix.postTranslate(dx, dy)
                    imageMatrix = matrix
                    lastX = event.x
                    lastY = event.y
                    onMatrixChanged?.invoke(Matrix(matrix))
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // ИСПРАВЛЕНИЕ: Когда убираем один палец, не сбрасываем панорамирование
                // Это предотвращает "дергание" схемы
                if (event.pointerCount == 2) {
                    // Обновляем координаты для оставшегося пальца
                    val remainingPointerIndex = if (event.actionIndex == 0) 1 else 0
                    lastX = event.getX(remainingPointerIndex)
                    lastY = event.getY(remainingPointerIndex)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // ИСПРАВЛЕНИЕ: Сбрасываем панорамирование только при полном поднятии всех пальцев
                if (event.pointerCount <= 1) {
                    isPanning = false
                }
            }
        }
    }
}



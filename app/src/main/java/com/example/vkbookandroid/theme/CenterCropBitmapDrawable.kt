package com.example.vkbookandroid.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.BitmapShader

/**
 * Drawable, который отрисовывает bitmap по принципу centerCrop без искажений.
 * Используется для больших фоновых картинок, чтобы избежать растяжения.
 */
class CenterCropBitmapDrawable(bitmap: Bitmap) : Drawable() {

    private val bitmap: Bitmap = bitmap
    private val shader: BitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = this@CenterCropBitmapDrawable.shader
    }
    private val matrix: Matrix = Matrix()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        updateShaderMatrix(bounds)
    }

    private fun updateShaderMatrix(bounds: Rect) {
        val viewWidth = bounds.width().toFloat()
        val viewHeight = bounds.height().toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return

        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()

        matrix.reset()

        val scale = maxOf(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
        val scaledWidth = bitmapWidth * scale
        val scaledHeight = bitmapHeight * scale
        val dx = bounds.left + (viewWidth - scaledWidth) * 0.5f
        val dy = bounds.top + (viewHeight - scaledHeight) * 0.5f

        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)

        shader.setLocalMatrix(matrix)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, paint)
    }

    override fun setAlpha(alpha: Int) {
        if (paint.alpha != alpha) {
            paint.alpha = alpha
            invalidateSelf()
        }
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        if (paint.colorFilter != colorFilter) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Drawable с настраиваемым смещением и масштабом для точного позиционирования картинки
 */
class AdjustableCropBitmapDrawable(
    bitmap: Bitmap,
    private val offsetXDp: Float = 0f,  // Смещение по X в dp (отрицательное = влево)
    private val offsetYDp: Float = 0f,  // Смещение по Y в dp (отрицательное = вверх)
    private val scaleMultiplier: Float = 1.0f  // Множитель масштаба (0.9 = уменьшение на 10%)
) : Drawable() {

    private val bitmap: Bitmap = bitmap
    private val shader: BitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = this@AdjustableCropBitmapDrawable.shader
    }
    private val matrix: Matrix = Matrix()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        updateShaderMatrix(bounds)
    }

    private fun updateShaderMatrix(bounds: Rect) {
        val viewWidth = bounds.width().toFloat()
        val viewHeight = bounds.height().toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return

        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()

        matrix.reset()

        // Базовый масштаб для заполнения экрана
        val baseScale = maxOf(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
        
        // Применяем множитель масштаба
        val scale = baseScale * scaleMultiplier
        
        val scaledWidth = bitmapWidth * scale
        val scaledHeight = bitmapHeight * scale
        
        // Базовое центрирование
        val baseDx = bounds.left + (viewWidth - scaledWidth) * 0.5f
        val baseDy = bounds.top + (viewHeight - scaledHeight) * 0.5f
        
        // Применяем смещение (конвертируем dp в пиксели)
        val density = bounds.width() / 360f // Примерная плотность (для 360dp ширины)
        val offsetXPx = offsetXDp * density
        val offsetYPx = offsetYDp * density
        
        val dx = baseDx + offsetXPx
        val dy = baseDy + offsetYPx

        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)

        shader.setLocalMatrix(matrix)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, paint)
    }

    override fun setAlpha(alpha: Int) {
        if (paint.alpha != alpha) {
            paint.alpha = alpha
            invalidateSelf()
        }
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        if (paint.colorFilter != colorFilter) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}



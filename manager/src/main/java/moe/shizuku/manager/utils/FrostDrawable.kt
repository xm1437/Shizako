package moe.shizuku.manager.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * Frosted-glass background for the app bar.
 *
 * Draws a pre-blurred copy of the window wallpaper scaled to the full window
 * size (so the top part lines up with the sharp window background behind it),
 * then a strong translucent scrim on top. The scrim is intentionally much
 * stronger than the window background's mask (~94% vs ~70%) so the bar is
 * clearly distinguishable from the content area while keeping text and
 * buttons readable. Works on all API levels, no runtime blur needed.
 */
class FrostDrawable(
    private val bitmap: Bitmap,
    private val scrimColor: Int
) : Drawable() {

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val scrimPaint = Paint().apply { color = scrimColor }
    private val matrix = Matrix()

    /** Size of the window the wallpaper is stretched to; set from the activity root view. */
    var windowWidth: Int = 0
        set(value) {
            field = value
            invalidateSelf()
        }
    var windowHeight: Int = 0
        set(value) {
            field = value
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty || windowWidth <= 0 || windowHeight <= 0 || bitmap.isRecycled) return

        matrix.reset()
        matrix.setScale(windowWidth / bitmap.width.toFloat(), windowHeight / bitmap.height.toFloat())
        matrix.postTranslate(-b.left.toFloat(), -b.top.toFloat())
        canvas.drawBitmap(bitmap, matrix, bitmapPaint)

        canvas.drawRect(b, scrimPaint)
    }

    override fun setAlpha(alpha: Int) {
        bitmapPaint.alpha = alpha
        scrimPaint.alpha = (scrimColor ushr 24) * alpha / 0xFF
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bitmapPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

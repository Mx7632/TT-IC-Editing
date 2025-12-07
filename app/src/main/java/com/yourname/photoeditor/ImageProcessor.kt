package com.yourname.photoeditor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ImageProcessor {

    /**
     * Adjust brightness and contrast of a bitmap.
     *
     * @param bitmap Source bitmap
     * @param brightness Brightness value, range [-100, 100] (user input)
     * @param contrast Contrast value, range [-50, 150] (user input)
     * @return New bitmap with effects applied
     */
    fun applyBrightnessContrast(bitmap: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val newBitmap = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(newBitmap)
        val paint = Paint()

        // Construct ColorMatrix
        val cm = ColorMatrix()

        // 1. Calculate Contrast Scale
        // User input range: -50 to 150.
        // We map 0 to 1.0 (no change).
        // Formula: scale = 1 + (contrast / 100f).
        // If contrast = 100 -> scale = 2.0.
        // If contrast = -50 -> scale = 0.5.
        val scale = 1f + (contrast / 100f)

        // 2. Calculate Translation for Contrast (to keep 128 gray fixed)
        // Formula: translate = 128 * (1 - scale)
        val translate = 128f * (1f - scale)

        // 3. Set the matrix
        // We combine Contrast (Scaling) and Brightness (Translation).
        // The diagonal elements handle scaling (Contrast).
        // The last column handles translation (Contrast offset + Brightness).
        //
        // Principle:
        // R' = R * scale + (translate + brightness)
        // G' = G * scale + (translate + brightness)
        // B' = B * scale + (translate + brightness)
        // A' = A
        
        val array = floatArrayOf(
            scale, 0f, 0f, 0f, translate + brightness,
            0f, scale, 0f, 0f, translate + brightness,
            0f, 0f, scale, 0f, translate + brightness,
            0f, 0f, 0f, 1f, 0f
        )
        cm.set(array)

        // Note on user's request about "setScale" and "postTranslate":
        // Standard ColorMatrix setScale() sets the diagonal (Contrast).
        // There isn't a direct "postTranslate" method on ColorMatrix like in geometric Matrix.
        // Instead, we manually set the translation in the 5th column of the matrix array.
        // In this implementation:
        // - Contrast is implemented via scaling (diagonal elements).
        // - Brightness is implemented via translation (5th column elements).

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return newBitmap
    }
}

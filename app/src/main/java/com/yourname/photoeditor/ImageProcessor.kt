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

    // --- Filters ---

    fun applyFilter(bitmap: Bitmap, filterType: FilterType): Bitmap {
        if (filterType == FilterType.Original) return bitmap
        
        val width = bitmap.width
        val height = bitmap.height
        val newBitmap = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(newBitmap)
        val paint = Paint()
        
        val cm = ColorMatrix()
        
        when (filterType) {
            FilterType.Grayscale -> {
                cm.setSaturation(0f)
            }
            FilterType.Sepia -> {
                // Vintage/Sepia
                // R' = R*0.393 + G*0.769 + B*0.189
                // G' = R*0.349 + G*0.686 + B*0.168
                // B' = R*0.272 + G*0.534 + B*0.131
                val sepiaMatrix = floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.set(sepiaMatrix)
            }
            FilterType.Warm -> {
                // Warm: Increase Red slightly, decrease Blue slightly
                val warmMatrix = floatArrayOf(
                    1.1f, 0f, 0f, 0f, 0f,
                    0f, 1.0f, 0f, 0f, 0f,
                    0f, 0f, 0.9f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.set(warmMatrix)
            }
            FilterType.Cool -> {
                // Cool: Increase Blue, decrease Red
                val coolMatrix = floatArrayOf(
                    0.9f, 0f, 0f, 0f, 0f,
                    0f, 1.0f, 0f, 0f, 0f,
                    0f, 0f, 1.1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.set(coolMatrix)
            }
            FilterType.Invert -> {
                // Invert colors
                val invertMatrix = floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.set(invertMatrix)
            }
            FilterType.Polaroid -> {
                // Polaroid: Low saturation + slight yellow/orange tint + high contrast
                val contrast = 1.2f
                val translate = 128f * (1f - contrast)
                
                val polaroidMatrix = floatArrayOf(
                    contrast * 1.0f, 0f, 0f, 0f, translate + 10f, // Red boost
                    0f, contrast * 0.95f, 0f, 0f, translate + 10f, // Green boost
                    0f, 0f, contrast * 0.8f, 0f, translate - 20f, // Blue cut (yellow tint)
                    0f, 0f, 0f, 1f, 0f
                )
                cm.set(polaroidMatrix)
            }
            else -> {}
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return newBitmap
    }

    enum class FilterType(val displayName: String) {
        Original("原图"),
        Grayscale("黑白"),
        Sepia("复古"),
        Warm("暖色"),
        Cool("冷色"),
        Invert("反色"),
        Polaroid("胶片")
    }
}


package com.yourname.photoeditor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ImageProcessor {

    /**
     * 调整位图的亮度和对比度。
     *
     * @param bitmap 源位图
     * @param brightness 亮度值，范围 [-100, 100]（用户输入）
     * @param contrast 对比度值，范围 [-50, 150]（用户输入）
     * @return 应用效果后的新位图
     */
    fun applyBrightnessContrast(bitmap: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val newBitmap = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(newBitmap)
        val paint = Paint()

        // 构建 ColorMatrix
        val cm = ColorMatrix()

        // 1. 计算对比度缩放比例
        // 用户输入范围：-50 到 150。
        // 我们将 0 映射为 1.0（无变化）。
        // 公式：scale = 1 + (contrast / 100f)。
        // 如果 contrast = 100 -> scale = 2.0。
        // 如果 contrast = -50 -> scale = 0.5。
        val scale = 1f + (contrast / 100f)

        // 2. 计算对比度平移量（保持 128 灰度固定）
        // 公式：translate = 128 * (1 - scale)
        val translate = 128f * (1f - scale)

        // 3. 设置矩阵
        // 我们结合对比度（缩放）和亮度（平移）。
        // 对角线元素处理缩放（对比度）。
        // 最后一列处理平移（对比度偏移 + 亮度）。
        //
        // 原理：
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

        // 关于用户关于 "setScale" 和 "postTranslate" 的说明：
        // 标准 ColorMatrix setScale() 设置对角线（对比度）。
        // ColorMatrix 没有像几何 Matrix 那样的直接 "postTranslate" 方法。
        // 相反，我们要手动设置矩阵数组第 5 列的平移量。
        // 在此实现中：
        // - 对比度通过缩放（对角线元素）实现。
        // - 亮度通过平移（第 5 列元素）实现。

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return newBitmap
    }

    // --- 滤镜 ---

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
                // 复古/Sepia
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
                // 暖色: 稍微增加红色，减少蓝色
                val warmMatrix = floatArrayOf(
                    1.1f, 0f, 0f, 0f, 0f,
                    0f, 1.0f, 0f, 0f, 0f,
                    0f, 0f, 0.9f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.set(warmMatrix)
            }
            FilterType.Cool -> {
                // 冷色: 增加蓝色，减少红色
                val coolMatrix = floatArrayOf(
                    0.9f, 0f, 0f, 0f, 0f,
                    0f, 1.0f, 0f, 0f, 0f,
                    0f, 0f, 1.1f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.set(coolMatrix)
            }
            FilterType.Invert -> {
                // 反转颜色
                val invertMatrix = floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.set(invertMatrix)
            }
            FilterType.Polaroid -> {
                // 胶片: 低饱和度 + 轻微黄色/橙色色调 + 高对比度
                val contrast = 1.2f
                val translate = 128f * (1f - contrast)
                
                val polaroidMatrix = floatArrayOf(
                    contrast * 1.0f, 0f, 0f, 0f, translate + 10f, // 增强红色
                    0f, contrast * 0.95f, 0f, 0f, translate + 10f, // 增强绿色
                    0f, 0f, contrast * 0.8f, 0f, translate - 20f, // 减少蓝色 (黄色色调)
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


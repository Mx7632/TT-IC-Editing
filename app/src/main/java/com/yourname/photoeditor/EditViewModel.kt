package com.yourname.photoeditor

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.roundToInt

class EditViewModel(application: Application) : AndroidViewModel(application) {

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap.asStateFlow()

    // Load bitmap from Uri with sampling to prevent OOM
    fun loadImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                
                // 1. First decode with inJustDecodeBounds=true to check dimensions
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                }

                // 2. Calculate inSampleSize
                // Target dimensions: e.g., screen width/height. 
                // For simplicity, let's limit to 1920x1080 approx or simply scale down if too large.
                // A safe max dimension is often 2048 or something reasonable for mobile editing.
                options.inSampleSize = calculateInSampleSize(options, 2048, 2048)
                options.inJustDecodeBounds = false
                
                // 3. Decode actual bitmap
                val resultBitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                }

                _bitmap.value = resultBitmap
            } catch (e: Exception) {
                Log.e("EditViewModel", "Error loading image: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    fun updateBitmap(newBitmap: Bitmap) {
        _bitmap.value = newBitmap
    }
    
    // Crop the current bitmap based on the provided rect (in pixel coordinates of the current bitmap)
    fun cropBitmap(x: Int, y: Int, width: Int, height: Int) {
        val currentBitmap = _bitmap.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure coordinates are within bounds
                val safeX = x.coerceIn(0, currentBitmap.width)
                val safeY = y.coerceIn(0, currentBitmap.height)
                val safeWidth = width.coerceAtMost(currentBitmap.width - safeX)
                val safeHeight = height.coerceAtMost(currentBitmap.height - safeY)
                
                if (safeWidth <= 0 || safeHeight <= 0) return@launch

                val croppedBitmap = Bitmap.createBitmap(currentBitmap, safeX, safeY, safeWidth, safeHeight)
                _bitmap.value = croppedBitmap
            } catch (e: Exception) {
                Log.e("EditViewModel", "Error cropping image: ${e.message}")
            }
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}

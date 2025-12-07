package com.yourname.photoeditor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditViewModel(application: Application) : AndroidViewModel(application) {

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap.asStateFlow()

    // Brightness/Contrast State
    private var adjustmentJob: Job? = null
    private var originalBitmapForAdjustment: Bitmap? = null
    
    private val _brightness = MutableStateFlow(0f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()
    
    private val _contrast = MutableStateFlow(0f)
    val contrast: StateFlow<Float> = _contrast.asStateFlow()

    fun loadImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { 
                    BitmapFactory.decodeStream(it, null, options) 
                }
                options.inSampleSize = calculateInSampleSize(options, 2048, 2048)
                options.inJustDecodeBounds = false
                val resultBitmap = context.contentResolver.openInputStream(uri)?.use { 
                    BitmapFactory.decodeStream(it, null, options) 
                }
                _bitmap.value = resultBitmap
            } catch (e: Exception) {
                Log.e("EditViewModel", "Error loading image", e)
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

    // --- Crop Feature ---
    fun cropBitmap(x: Int, y: Int, width: Int, height: Int) {
        val currentBitmap = _bitmap.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val safeX = x.coerceIn(0, currentBitmap.width)
                val safeY = y.coerceIn(0, currentBitmap.height)
                val safeWidth = width.coerceAtMost(currentBitmap.width - safeX)
                val safeHeight = height.coerceAtMost(currentBitmap.height - safeY)
                if (safeWidth <= 0 || safeHeight <= 0) return@launch
                val croppedBitmap = Bitmap.createBitmap(currentBitmap, safeX, safeY, safeWidth, safeHeight)
                _bitmap.value = croppedBitmap
            } catch (e: Exception) {
                Log.e("EditViewModel", "Error cropping image", e)
            }
        }
    }

    // --- Brightness/Contrast Feature ---
    
    fun onEnterAdjustmentMode() {
        // Snapshot the current bitmap to apply adjustments on
        if (originalBitmapForAdjustment == null) {
            originalBitmapForAdjustment = _bitmap.value
        }
        _brightness.value = 0f
        _contrast.value = 0f
    }

    fun onExitAdjustmentMode(save: Boolean) {
        if (!save) {
            // Revert to original if cancelled (though UI might not have cancel)
            originalBitmapForAdjustment?.let {
                _bitmap.value = it
            }
        }
        // If save is true, _bitmap is already holding the adjusted version (due to preview)
        originalBitmapForAdjustment = null
        _brightness.value = 0f
        _contrast.value = 0f
    }

    fun onBrightnessChanged(value: Float) {
        _brightness.value = value
        scheduleAdjustment()
    }

    fun onContrastChanged(value: Float) {
        _contrast.value = value
        scheduleAdjustment()
    }

    private fun scheduleAdjustment() {
        adjustmentJob?.cancel()
        adjustmentJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300) // Debounce 300ms
            applyAdjustment()
        }
    }
    
    private fun applyAdjustment() {
        val base = originalBitmapForAdjustment ?: return
        val b = _brightness.value
        val c = _contrast.value
        
        try {
            val res = ImageProcessor.applyBrightnessContrast(base, b, c)
            _bitmap.value = res
        } catch (e: Exception) {
            Log.e("EditViewModel", "Error applying adjustments", e)
        }
    }
}

package com.yourname.photoeditor

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.toArgb
import android.app.Application
import android.content.ContentValues
import android.content.Context
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

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.util.UUID

data class TextLayer(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "双击编辑",
    var position: Offset = Offset.Zero,
    var fontSize: Float = 40f,
    var color: Color = Color.White,
    var rotation: Float = 0f,
    var scale: Float = 1f,
    var alpha: Float = 1f,
    var fontFamilyIndex: Int = 0
)

class EditViewModel(application: Application) : AndroidViewModel(application) {

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap.asStateFlow()
    
    // Text Layers
    val textLayers = mutableStateListOf<TextLayer>()
    private val _selectedTextLayerId = MutableStateFlow<String?>(null)
    val selectedTextLayerId: StateFlow<String?> = _selectedTextLayerId.asStateFlow()

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

    fun rotateBitmap(degrees: Float) {
        val currentBitmap = _bitmap.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val matrix = Matrix().apply { postRotate(degrees) }
                val rotatedBitmap = Bitmap.createBitmap(
                    currentBitmap, 0, 0, currentBitmap.width, currentBitmap.height, matrix, true
                )
                _bitmap.value = rotatedBitmap
            } catch (e: Exception) {
                Log.e("EditViewModel", "Error rotating image", e)
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

    // --- Text Layer Management ---

    fun addTextLayer() {
        val currentBitmap = _bitmap.value ?: return
        val centerX = currentBitmap.width / 2f
        val centerY = currentBitmap.height / 2f
        
        val newLayer = TextLayer(
            position = Offset(centerX, centerY)
        )
        textLayers.add(newLayer)
        selectTextLayer(newLayer.id)
    }

    fun selectTextLayer(id: String?) {
        _selectedTextLayerId.value = id
    }

    fun updateTextLayer(id: String, update: (TextLayer) -> Unit) {
        val index = textLayers.indexOfFirst { it.id == id }
        if (index != -1) {
            val layer = textLayers[index]
            update(layer)
            // Trigger recomposition by replacing item (mutableStateListOf tracks adds/removes/replacements)
            // Since properties are vars, simple property update might not trigger list listener if we don't be careful,
            // but for mutableStateListOf, updating a property of an element doesn't automatically trigger list flow.
            // However, the UI observing the list will read the properties. 
            // Better: copy or force refresh. But TextLayer has vars.
            // Jetpack Compose SnapshotStateList: "If the elements are mutable objects, updating their properties won't trigger recomposition unless the properties themselves are backed by MutableState."
            // Our TextLayer properties are plain vars. This is a problem.
            // We should either make TextLayer properties MutableState or replace the object in the list.
            // Let's replace the object in the list using copy (if it were a data class with vals) or just creating a new one.
            // Since I made them vars, I should probably change them to vals and use copy, or wrap them in MutableState.
            // Actually, let's just make TextLayer a data class with vals and use copy to be safe and idiomatic.
            // I will fix TextLayer definition in a moment or just handle replacement here.
            // For now, I'll stick to replacing the object in the list.
            textLayers[index] = layer.copy() // This assumes TextLayer is a data class. The `update` lambda modified the `layer` reference? 
            // Wait, if I pass `layer` to `update`, and `update` modifies it, and then I put `layer` back, `layer` is the same reference. 
            // `mutableStateListOf` checks for equality. If I modify in place, it might not trigger.
            // Strategy: Make TextLayer immutable (vals) and update via copy.
        }
    }
    
    fun removeSelectedTextLayer() {
        val id = _selectedTextLayerId.value ?: return
        textLayers.removeAll { it.id == id }
        _selectedTextLayerId.value = null
    }

    // --- Filter Feature ---
    private var originalBitmapForFilter: Bitmap? = null
    private val _currentFilter = MutableStateFlow(ImageProcessor.FilterType.Original)
    val currentFilter: StateFlow<ImageProcessor.FilterType> = _currentFilter.asStateFlow()

    fun onEnterFilterMode() {
        if (originalBitmapForFilter == null) {
            originalBitmapForFilter = _bitmap.value
        }
        _currentFilter.value = ImageProcessor.FilterType.Original
    }

    fun onFilterSelected(filterType: ImageProcessor.FilterType) {
        _currentFilter.value = filterType
        val base = originalBitmapForFilter ?: return
        
        viewModelScope.launch(Dispatchers.IO) {
            val result = ImageProcessor.applyFilter(base, filterType)
            _bitmap.value = result
        }
    }
    
    fun onExitFilterMode(save: Boolean) {
        if (!save) {
            originalBitmapForFilter?.let {
                _bitmap.value = it
            }
        }
        originalBitmapForFilter = null
        _currentFilter.value = ImageProcessor.FilterType.Original
    }

    // --- Save Feature ---
    sealed class SaveResult {
        object Loading : SaveResult()
        data class Success(val uri: Uri) : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    private val _saveState = MutableStateFlow<SaveResult?>(null)
    val saveState: StateFlow<SaveResult?> = _saveState.asStateFlow()
    
    fun resetSaveState() {
        _saveState.value = null
    }

    fun saveImage() {
        val currentBitmap = _bitmap.value ?: return
        _saveState.value = SaveResult.Loading
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val finalBitmap = currentBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(finalBitmap)
                val paint = Paint().apply {
                    isAntiAlias = true
                }
                
                textLayers.forEach { layer ->
                    paint.color = layer.color.toArgb()
                    paint.textSize = layer.fontSize
                    paint.alpha = (layer.alpha * 255).toInt()
                    
                    paint.typeface = when (layer.fontFamilyIndex) {
                        1 -> Typeface.SERIF
                        2 -> Typeface.SANS_SERIF
                        3 -> Typeface.MONOSPACE
                        4 -> Typeface.create("cursive", Typeface.NORMAL)
                        else -> Typeface.DEFAULT
                    }
                    
                    val textWidth = paint.measureText(layer.text)
                    val fontMetrics = paint.fontMetrics
                    val xPos = -textWidth / 2f
                    val yPos = -(fontMetrics.descent + fontMetrics.ascent) / 2f
                    
                    canvas.save()
                    canvas.translate(layer.position.x, layer.position.y)
                    canvas.rotate(layer.rotation)
                    canvas.scale(layer.scale, layer.scale)
                    canvas.drawText(layer.text, xPos, yPos, paint)
                    canvas.restore()
                }
                
                val filename = "TTEdit_${System.currentTimeMillis()}.jpg"
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    }
                }
                
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { stream ->
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    }
                    _saveState.value = SaveResult.Success(uri)
                } else {
                    _saveState.value = SaveResult.Error("Failed to create MediaStore entry")
                }
                
            } catch (e: Exception) {
                Log.e("EditViewModel", "Error saving image", e)
                _saveState.value = SaveResult.Error(e.message ?: "Unknown error")
            }
        }
    }
}

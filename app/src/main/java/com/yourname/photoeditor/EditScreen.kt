package com.yourname.photoeditor

import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun EditScreen(
    imageUri: String?,
    viewModel: EditViewModel = viewModel()
) {
    val bitmapState by viewModel.bitmap.collectAsState()
    
    // States for modes
    var isCropMode by remember { mutableStateOf(false) }
    var isBrightnessMode by remember { mutableStateOf(false) }
    var isFilterMode by remember { mutableStateOf(false) }
    
    // Text states
    val textLayers = viewModel.textLayers
    val selectedTextLayerId by viewModel.selectedTextLayerId.collectAsState()
    var showTextInputDialog by remember { mutableStateOf(false) }
    var editingTextLayerId by remember { mutableStateOf<String?>(null) }
    
    // States for Brightness/Contrast
    val brightness by viewModel.brightness.collectAsState()
    val contrast by viewModel.contrast.collectAsState()

    // States for Filter & Save
    val currentFilter by viewModel.currentFilter.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Load image when Uri changes or screen enters
    LaunchedEffect(imageUri) {
        imageUri?.let {
            viewModel.loadImage(Uri.parse(it))
        }
    }

    LaunchedEffect(saveState) {
        when (val state = saveState) {
            is EditViewModel.SaveResult.Success -> {
                scope.launch {
                    snackbarHostState.showSnackbar("保存成功")
                }
                viewModel.resetSaveState()
            }
            is EditViewModel.SaveResult.Error -> {
                scope.launch {
                    snackbarHostState.showSnackbar("保存失败: ${state.message}")
                }
                viewModel.resetSaveState()
            }
            else -> {}
        }
    }

    if (showTextInputDialog && editingTextLayerId != null) {
        val layer = textLayers.find { it.id == editingTextLayerId }
        if (layer != null) {
            TextInputDialog(
                initialText = layer.text,
                onDismiss = { showTextInputDialog = false },
                onConfirm = { newText ->
                    viewModel.updateTextLayer(layer.id) { it.text = newText }
                    showTextInputDialog = false
                    editingTextLayerId = null
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isCropMode) {
                CropToolbar(
                    onConfirm = {}, // Handled in container
                    onCancel = { isCropMode = false },
                    onRatioSelected = {}
                )
            } else if (isBrightnessMode) {
                BrightnessContrastPanel(
                    brightness = brightness,
                    contrast = contrast,
                    onBrightnessChange = { viewModel.onBrightnessChanged(it) },
                    onContrastChange = { viewModel.onContrastChanged(it) },
                    onClose = {
                        viewModel.onExitAdjustmentMode(save = true)
                        isBrightnessMode = false
                    }
                )
            } else if (isFilterMode) {
                FilterPanel(
                    currentFilter = currentFilter,
                    onFilterSelect = { viewModel.onFilterSelected(it) },
                    onClose = {
                        viewModel.onExitFilterMode(save = true)
                        isFilterMode = false
                    }
                )
            } else if (selectedTextLayerId != null) {
                // Text Style Panel
                val selectedLayer = textLayers.find { it.id == selectedTextLayerId }
                if (selectedLayer != null) {
                    TextStylePanel(
                        layer = selectedLayer,
                        onUpdate = { update -> viewModel.updateTextLayer(selectedLayer.id, update) },
                        onDelete = { viewModel.removeSelectedTextLayer() },
                        onClose = { viewModel.selectTextLayer(null) }
                    )
                }
            } else {
                EditToolbar(
                    onCropClick = { isCropMode = true },
                    onRotateClick = { viewModel.rotateBitmap(90f) },
                    onBrightnessClick = { 
                        viewModel.onEnterAdjustmentMode()
                        isBrightnessMode = true 
                    },
                    onFilterClick = {
                        viewModel.onEnterFilterMode()
                        isFilterMode = true
                    },
                    onTextClick = { 
                        viewModel.addTextLayer()
                        val newId = viewModel.selectedTextLayerId.value
                        if (newId != null) {
                            editingTextLayerId = newId
                            showTextInputDialog = true
                        }
                    },
                    onSaveClick = { viewModel.saveImage() }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            if (bitmapState != null) {
                if (isCropMode) {
                    CropContainer(
                        bitmap = bitmapState!!,
                        onCropConfirm = { x, y, w, h ->
                            viewModel.cropBitmap(x, y, w, h)
                            isCropMode = false
                        },
                        onCropCancel = { isCropMode = false }
                    )
                } else {
                    ZoomableEditor(
                        bitmap = bitmapState!!,
                        textLayers = textLayers,
                        selectedLayerId = selectedTextLayerId,
                        onLayerClick = { id -> viewModel.selectTextLayer(id) },
                        onLayerUpdate = { id, update -> viewModel.updateTextLayer(id, update) },
                        onLayerEdit = { id ->
                            editingTextLayerId = id
                            showTextInputDialog = true
                        },
                        onBackgroundClick = { viewModel.selectTextLayer(null) }
                    )
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            if (saveState is EditViewModel.SaveResult.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun ZoomableEditor(
    bitmap: Bitmap,
    textLayers: List<TextLayer>,
    selectedLayerId: String?,
    onLayerClick: (String) -> Unit,
    onLayerUpdate: (String, (TextLayer) -> Unit) -> Unit,
    onLayerEdit: (String) -> Unit,
    onBackgroundClick: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // Calculate displayed image rect to map text coordinates
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var imageRect by remember { mutableStateOf(Rect.Zero) }
    
    LaunchedEffect(containerSize, bitmap) {
        if (containerSize.width > 0 && containerSize.height > 0) {
            val (fitScale, fitOffset) = calculateFitScaleAndOffset(containerSize, bitmap.width, bitmap.height)
            imageRect = Rect(fitOffset, Size(bitmap.width * fitScale, bitmap.height * fitScale))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerSize = it.size }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onBackgroundClick() })
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                    offset += pan
                }
            }
    ) {
        // Container for Image + Text that transforms together
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            // 1. The Image
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Editing Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            
            // 2. The Text Layers
            // They need to be positioned relative to the imageRect.
            // Since `Image` with ContentScale.Fit is centered (default), 
            // `imageRect` tells us where it is relative to the Box(fillMaxSize).
            // BUT, this inner Box is already fillMaxSize.
            // The `Image` composable centers the content.
            // So if we just put TextLayers in a Box that matches `imageRect` exactly?
            // No, because `imageRect` is calculated based on `containerSize`.
            // Inside this transformed Box, the coordinate system is 1:1 with container (before transform).
            // So yes, we can use `imageRect`.
            
            if (imageRect != Rect.Zero) {
                Box(
                    modifier = Modifier
                        .width(with(LocalDensity.current) { imageRect.width.toDp() })
                        .height(with(LocalDensity.current) { imageRect.height.toDp() })
                        .offset(
                            with(LocalDensity.current) { imageRect.left.toDp() },
                            with(LocalDensity.current) { imageRect.top.toDp() }
                        )
                ) {
                    // Now we are in a box that exactly matches the displayed image bounds.
                    // TextLayer positions (bitmap pixel coords) need to be scaled to this box.
                    val fitScale = imageRect.width / bitmap.width
                    
                    textLayers.forEach { layer ->
                        key(layer.id) {
                            TextLayerItem(
                                layer = layer,
                                isSelected = layer.id == selectedLayerId,
                                fitScale = fitScale,
                                onSelect = { onLayerClick(layer.id) },
                                onUpdate = { update -> onLayerUpdate(layer.id, update) },
                                onEdit = { onLayerEdit(layer.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextLayerItem(
    layer: TextLayer,
    isSelected: Boolean,
    fitScale: Float,
    onSelect: () -> Unit,
    onUpdate: ((TextLayer) -> Unit) -> Unit,
    onEdit: () -> Unit
) {
    val density = LocalDensity.current
    var textSize by remember { mutableStateOf(IntSize.Zero) }
    
    // Convert bitmap coords to screen coords (relative to image box)
    val x = layer.position.x * fitScale
    val y = layer.position.y * fitScale
    
    val fontSizeSp = with(density) { (layer.fontSize * fitScale).toSp() }
    
    Box(
        modifier = Modifier
            .offset(
                x = with(density) { x.toDp() },
                y = with(density) { y.toDp() }
            )
            // Center the text based on actual measured size
            .offset(
                x = with(density) { -(textSize.width / 2).toDp() }, 
                y = with(density) { -(textSize.height / 2).toDp() }
            )
            .graphicsLayer(
                rotationZ = layer.rotation,
                scaleX = layer.scale,
                scaleY = layer.scale,
                alpha = layer.alpha
            )
            .pointerInput(isSelected) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    if (!isSelected) onSelect()
                    onUpdate {
                        // Update fontSize instead of scale for better UX consistency
                        it.fontSize *= zoom
                        it.fontSize = it.fontSize.coerceIn(10f, 500f)
                        
                        // Normalize rotation to 0-360
                        it.rotation += rotation
                        it.rotation = (it.rotation % 360 + 360) % 360
                        
                        // Apply pan (drag) during transform
                        val dx = pan.x / fitScale
                        val dy = pan.y / fitScale
                        it.position += Offset(dx, dy)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSelect() },
                    onDoubleTap = { onEdit() }
                )
            }
    ) {
        BasicText(
            text = layer.text,
            style = TextStyle(
                color = layer.color,
                fontSize = fontSizeSp,
                fontWeight = FontWeight.Normal,
                fontFamily = getFontFamily(layer.fontFamilyIndex)
            ),
            onTextLayout = { textSize = it.size },
            modifier = Modifier
                .then(
                    if (isSelected) Modifier.border(1.dp, Color.White, RoundedCornerShape(4.dp)).padding(4.dp)
                    else Modifier.padding(4.dp)
                )
        )
    }
}

fun getFontFamily(index: Int): FontFamily {
    return when (index) {
        1 -> FontFamily.Serif
        2 -> FontFamily.SansSerif
        3 -> FontFamily.Monospace
        4 -> FontFamily.Cursive
        else -> FontFamily.Default
    }
}

@Composable
fun TextInputDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑文字") },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it }
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) {
                Text("确定")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun TextStylePanel(
    layer: TextLayer,
    onUpdate: ((TextLayer) -> Unit) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.9f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("样式编辑", color = Color.White, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Font Size
        Text("大小: ${layer.fontSize.toInt()}", color = Color.White)
        Slider(
            value = layer.fontSize,
            onValueChange = { val v = it; onUpdate { it.fontSize = v } },
            valueRange = 10f..200f
        )
        
        // Alpha
        Text("透明度: ${(layer.alpha * 100).toInt()}%", color = Color.White)
        Slider(
            value = layer.alpha,
            onValueChange = { val v = it; onUpdate { it.alpha = v } },
            valueRange = 0f..1f
        )
        
        // Colors
        Text("颜色", color = Color.White)
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            val colors = listOf(Color.White, Color.Black, Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan, Color.Magenta)
            colors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (layer.color == color) 2.dp else 0.dp,
                            color = if (layer.color == color) Color.Gray else Color.Transparent,
                            shape = CircleShape
                        )
                        .pointerInput(Unit) {
                            detectTapGestures { onUpdate { it.color = color } }
                        }
                )
            }
        }
        
        // Fonts
        Text("字体", color = Color.White)
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            val fonts = listOf("默认", "衬线", "无衬线", "等宽", "手写")
            fonts.forEachIndexed { index, name ->
                FilterChip(
                    selected = layer.fontFamilyIndex == index,
                    onClick = { onUpdate { it.fontFamilyIndex = index } },
                    label = { Text(name) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

// -------------------- UTILS & HELPERS --------------------

fun calculateFitScaleAndOffset(containerSize: IntSize, bitmapWidth: Int, bitmapHeight: Int): Pair<Float, Offset> {
    if (containerSize.width == 0 || containerSize.height == 0) return 1f to Offset.Zero
    val wRatio = containerSize.width.toFloat() / bitmapWidth
    val hRatio = containerSize.height.toFloat() / bitmapHeight
    val scale = minOf(wRatio, hRatio)
    val dx = (containerSize.width - bitmapWidth * scale) / 2f
    val dy = (containerSize.height - bitmapHeight * scale) / 2f
    return scale to Offset(dx, dy)
}

// -------------------- EXISTING BRIGHTNESS/CROP COMPONENTS --------------------

@Composable
fun BrightnessContrastPanel(
    brightness: Float,
    contrast: Float,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(16.dp)
    ) {
        Text("亮度: ${brightness.toInt()}", color = Color.White)
        Slider(
            value = brightness,
            onValueChange = onBrightnessChange,
            valueRange = -100f..100f
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("对比度: ${contrast.toInt()}", color = Color.White)
        Slider(
            value = contrast,
            onValueChange = onContrastChange,
            valueRange = -50f..150f
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onClose,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("完成")
        }
    }
}

@Composable
fun EditToolbar(
    onCropClick: () -> Unit,
    onRotateClick: () -> Unit,
    onBrightnessClick: () -> Unit,
    onFilterClick: () -> Unit,
    onTextClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TextButton(onClick = onCropClick) { Text("裁剪") }
        TextButton(onClick = onRotateClick) { Text("旋转") }
        TextButton(onClick = onBrightnessClick) { Text("亮度") }
        TextButton(onClick = onFilterClick) { Text("滤镜") }
        TextButton(onClick = onTextClick) { Text("文字") }
        Button(onClick = onSaveClick) { Text("保存") }
    }
}

@Composable
fun CropToolbar(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onRatioSelected: (Float?) -> Unit
) {
    // Placeholder, real controls are in CropContainer
}

@Composable
fun CropContainer(
    bitmap: android.graphics.Bitmap,
    onCropConfirm: (Int, Int, Int, Int) -> Unit,
    onCropCancel: () -> Unit
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var cropRect by remember { mutableStateOf(Rect.Zero) }
    var imageRect by remember { mutableStateOf(Rect.Zero) }
    var currentAspectRatio by remember { mutableStateOf<Float?>(null) } // Null = Free

    LaunchedEffect(containerSize, bitmap) {
        if (containerSize.width > 0 && containerSize.height > 0) {
            val (scale, offset) = calculateFitScaleAndOffset(containerSize, bitmap.width, bitmap.height)
            val displayedWidth = bitmap.width * scale
            val displayedHeight = bitmap.height * scale
            
            imageRect = Rect(offset, Size(displayedWidth, displayedHeight))
            cropRect = imageRect
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerSize = coordinates.size
            }
    ) {
        if (imageRect != Rect.Zero) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(
                        with(LocalDensity.current) { imageRect.width.toDp() },
                        with(LocalDensity.current) { imageRect.height.toDp() }
                    )
                    .offset(
                        with(LocalDensity.current) { imageRect.left.toDp() },
                        with(LocalDensity.current) { imageRect.top.toDp() }
                    ),
                contentScale = ContentScale.FillBounds
            )
        }

        if (cropRect != Rect.Zero) {
            CropOverlay(
                rect = cropRect,
                imageBounds = imageRect,
                aspectRatio = currentAspectRatio,
                onRectChange = { newRect -> cropRect = newRect }
            )
        }
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.8f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = { 
                    currentAspectRatio = null 
                }) { Text("Free", color = Color.White) }
                TextButton(onClick = { 
                    currentAspectRatio = 1f 
                    cropRect = fitAspectRatio(cropRect, imageRect, 1f)
                }) { Text("1:1", color = Color.White) }
                TextButton(onClick = { 
                    currentAspectRatio = 4f/3f 
                    cropRect = fitAspectRatio(cropRect, imageRect, 4f/3f)
                }) { Text("4:3", color = Color.White) }
                TextButton(onClick = { 
                    currentAspectRatio = 16f/9f 
                    cropRect = fitAspectRatio(cropRect, imageRect, 16f/9f)
                }) { Text("16:9", color = Color.White) }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = onCropCancel) { Text("取消") }
                Button(onClick = {
                    val scale = bitmap.width.toFloat() / imageRect.width
                    val x = ((cropRect.left - imageRect.left) * scale).roundToInt()
                    val y = ((cropRect.top - imageRect.top) * scale).roundToInt()
                    val w = (cropRect.width * scale).roundToInt()
                    val h = (cropRect.height * scale).roundToInt()
                    onCropConfirm(x, y, w, h)
                }) { Text("确认") }
            }
        }
    }
}

private enum class CropHandle {
    TopLeft, TopRight, BottomLeft, BottomRight, Center, None
}

@Composable
fun CropOverlay(
    rect: Rect,
    imageBounds: Rect,
    aspectRatio: Float?,
    onRectChange: (Rect) -> Unit
) {
    var activeHandle by remember { mutableStateOf(CropHandle.None) }
    val currentRect by rememberUpdatedState(rect)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imageBounds, aspectRatio) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val r = currentRect
                        val left = r.left
                        val top = r.top
                        val right = r.right
                        val bottom = r.bottom
                        val threshold = 100f

                        val distTL = (offset - Offset(left, top)).getDistance()
                        val distTR = (offset - Offset(right, top)).getDistance()
                        val distBL = (offset - Offset(left, bottom)).getDistance()
                        val distBR = (offset - Offset(right, bottom)).getDistance()

                        activeHandle = when {
                            distTL < threshold -> CropHandle.TopLeft
                            distTR < threshold -> CropHandle.TopRight
                            distBL < threshold -> CropHandle.BottomLeft
                            distBR < threshold -> CropHandle.BottomRight
                            r.contains(offset) -> CropHandle.Center
                            else -> CropHandle.None
                        }
                    },
                    onDragEnd = { activeHandle = CropHandle.None },
                    onDragCancel = { activeHandle = CropHandle.None },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (activeHandle == CropHandle.None) return@detectDragGestures

                        val r = currentRect
                        var newLeft = r.left
                        var newTop = r.top
                        var newRight = r.right
                        var newBottom = r.bottom

                        when (activeHandle) {
                            CropHandle.TopLeft -> {
                                newLeft += dragAmount.x
                                newTop += dragAmount.y
                            }
                            CropHandle.TopRight -> {
                                newRight += dragAmount.x
                                newTop += dragAmount.y
                            }
                            CropHandle.BottomLeft -> {
                                newLeft += dragAmount.x
                                newBottom += dragAmount.y
                            }
                            CropHandle.BottomRight -> {
                                newRight += dragAmount.x
                                newBottom += dragAmount.y
                            }
                            CropHandle.Center -> {
                                newLeft += dragAmount.x
                                newRight += dragAmount.x
                                newTop += dragAmount.y
                                newBottom += dragAmount.y
                            }
                            else -> {}
                        }

                        if (activeHandle == CropHandle.Center) {
                            val width = r.width
                            val height = r.height
                            newLeft = newLeft.coerceIn(imageBounds.left, imageBounds.right - width)
                            newTop = newTop.coerceIn(imageBounds.top, imageBounds.bottom - height)
                            newRight = newLeft + width
                            newBottom = newTop + height
                        } else {
                            newLeft = newLeft.coerceIn(imageBounds.left, newRight - 50f)
                            newTop = newTop.coerceIn(imageBounds.top, newBottom - 50f)
                            newRight = newRight.coerceIn(newLeft + 50f, imageBounds.right)
                            newBottom = newBottom.coerceIn(newTop + 50f, imageBounds.bottom)
                        }

                        var newRect = Rect(newLeft, newTop, newRight, newBottom)
                        if (aspectRatio != null && activeHandle != CropHandle.Center) {
                            newRect = fitAspectRatio(newRect, imageBounds, aspectRatio)
                        }
                        onRectChange(newRect)
                    }
                )
            }
    ) {
        // Draw dimmed background outside crop rect
        val path = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            addRect(rect)
            fillType = PathFillType.EvenOdd
        }
        drawPath(path, Color.Black.copy(alpha = 0.5f))

        // Draw crop rect border
        drawRect(
            color = Color.White,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw corners
        val cornerSize = 20.dp.toPx()
        val cornerStroke = 4.dp.toPx()
        
        // Top Left
        drawLine(Color.White, rect.topLeft, rect.topLeft + Offset(cornerSize, 0f), cornerStroke)
        drawLine(Color.White, rect.topLeft, rect.topLeft + Offset(0f, cornerSize), cornerStroke)
        
        // Top Right
        drawLine(Color.White, rect.topRight, rect.topRight - Offset(cornerSize, 0f), cornerStroke)
        drawLine(Color.White, rect.topRight, rect.topRight + Offset(0f, cornerSize), cornerStroke)
        
        // Bottom Left
        drawLine(Color.White, rect.bottomLeft, rect.bottomLeft + Offset(cornerSize, 0f), cornerStroke)
        drawLine(Color.White, rect.bottomLeft, rect.bottomLeft - Offset(0f, cornerSize), cornerStroke)
        
        // Bottom Right
        drawLine(Color.White, rect.bottomRight, rect.bottomRight - Offset(cornerSize, 0f), cornerStroke)
        drawLine(Color.White, rect.bottomRight, rect.bottomRight - Offset(0f, cornerSize), cornerStroke)
    }
}

fun fitAspectRatio(currentRect: Rect, bounds: Rect, ratio: Float): Rect {
    var w = currentRect.width
    var h = w / ratio
    
    // If height exceeds bounds, scale down
    if (h > bounds.height) {
        h = bounds.height
        w = h * ratio
    }
    // If width exceeds bounds (after adjustment), scale down again
    if (w > bounds.width) {
        w = bounds.width
        h = w / ratio
    }
    
    val center = currentRect.center
    val halfW = w / 2
    val halfH = h / 2
    
    var newLeft = center.x - halfW
    var newTop = center.y - halfH
    var newRight = center.x + halfW
    var newBottom = center.y + halfH
    
    // Shift if out of bounds
    if (newLeft < bounds.left) {
        val diff = bounds.left - newLeft
        newLeft += diff
        newRight += diff
    }
    if (newTop < bounds.top) {
        val diff = bounds.top - newTop
        newTop += diff
        newBottom += diff
    }
    if (newRight > bounds.right) {
        val diff = newRight - bounds.right
        newLeft += diff
        newRight += diff
    }
    if (newBottom > bounds.bottom) {
        val diff = newBottom - bounds.bottom
        newTop += diff
        newBottom += diff
    }
    
    return Rect(newLeft, newTop, newRight, newBottom)
}

@Composable
fun FilterPanel(
    currentFilter: ImageProcessor.FilterType,
    onFilterSelect: (ImageProcessor.FilterType) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.9f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("滤镜选择", color = Color.White, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White)
            }
        }
        
        LazyRow(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(ImageProcessor.FilterType.values()) { filter ->
                FilterItem(
                    filter = filter,
                    isSelected = filter == currentFilter,
                    onClick = { onFilterSelect(filter) }
                )
            }
        }
    }
}

@Composable
fun FilterItem(
    filter: ImageProcessor.FilterType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray)
                .border(2.dp, if (isSelected) Color.White else Color.Transparent, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = filter.displayName.take(1),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = filter.displayName,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

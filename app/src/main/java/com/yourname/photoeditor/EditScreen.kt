package com.yourname.photoeditor

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Region
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.abs
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
    
    // States for Brightness/Contrast
    val brightness by viewModel.brightness.collectAsState()
    val contrast by viewModel.contrast.collectAsState()

    // Load image when Uri changes or screen enters
    LaunchedEffect(imageUri) {
        imageUri?.let {
            viewModel.loadImage(Uri.parse(it))
        }
    }

    Scaffold(
        bottomBar = {
            if (isCropMode) {
                CropToolbar(
                    onConfirm = { 
                        // Actual confirm is triggered from CropContainer
                    },
                    onCancel = { isCropMode = false },
                    onRatioSelected = { /* Handled in CropContainer */ }
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
            } else {
                EditToolbar(
                    onCropClick = { isCropMode = true },
                    onRotateClick = { Log.d("EditScreen", "Rotate Clicked") },
                    onBrightnessClick = { 
                        viewModel.onEnterAdjustmentMode()
                        isBrightnessMode = true 
                    },
                    onTextClick = { Log.d("EditScreen", "Text Clicked") },
                    onSaveClick = { Log.d("EditScreen", "Save Clicked") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black) // Dark background for editing
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
                    ZoomableImage(bitmap = bitmapState!!)
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

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
            .background(Color.Black.copy(alpha = 0.8f)) // Match dark theme
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
fun ZoomableImage(bitmap: android.graphics.Bitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 2f)
                    val newOffset = offset + pan
                    offset = newOffset
                }
            }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Editing Image",
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun EditToolbar(
    onCropClick: () -> Unit,
    onRotateClick: () -> Unit,
    onBrightnessClick: () -> Unit,
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

// -------------------- CROP IMPLEMENTATION --------------------

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
            val dstWidth = containerSize.width.toFloat()
            val dstHeight = containerSize.height.toFloat()
            val srcWidth = bitmap.width.toFloat()
            val srcHeight = bitmap.height.toFloat()

            val scale = minOf(dstWidth / srcWidth, dstHeight / srcHeight)
            val displayedWidth = srcWidth * scale
            val displayedHeight = srcHeight * scale
            
            val offsetX = (dstWidth - displayedWidth) / 2
            val offsetY = (dstHeight - displayedHeight) / 2
            
            imageRect = Rect(offsetX, offsetY, offsetX + displayedWidth, offsetY + displayedHeight)
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

@Composable
fun CropOverlay(
    rect: Rect,
    imageBounds: Rect,
    aspectRatio: Float?,
    onRectChange: (Rect) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imageBounds, aspectRatio) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val left = rect.left
                        val top = rect.top
                        val right = rect.right
                        val bottom = rect.bottom
                        val distTL = (change.position - Offset(left, top)).getDistance()
                        val distTR = (change.position - Offset(right, top)).getDistance()
                        val distBL = (change.position - Offset(left, bottom)).getDistance()
                        val distBR = (change.position - Offset(right, bottom)).getDistance()
                        val threshold = 100f
                        var newLeft = left
                        var newTop = top
                        var newRight = right
                        var newBottom = bottom

                        if (distTL < threshold) {
                            newLeft += dragAmount.x
                            newTop += dragAmount.y
                        } else if (distTR < threshold) {
                            newRight += dragAmount.x
                            newTop += dragAmount.y
                        } else if (distBL < threshold) {
                            newLeft += dragAmount.x
                            newBottom += dragAmount.y
                        } else if (distBR < threshold) {
                            newRight += dragAmount.x
                            newBottom += dragAmount.y
                        } else {
                            newLeft += dragAmount.x
                            newRight += dragAmount.x
                            newTop += dragAmount.y
                            newBottom += dragAmount.y
                        }

                        // Constraints
                        newLeft = newLeft.coerceIn(imageBounds.left, newRight - 50f)
                        newTop = newTop.coerceIn(imageBounds.top, newBottom - 50f)
                        newRight = newRight.coerceIn(newLeft + 50f, imageBounds.right)
                        newBottom = newBottom.coerceIn(newTop + 50f, imageBounds.bottom)

                        var newRect = Rect(newLeft, newTop, newRight, newBottom)
                        if (aspectRatio != null) {
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
            fillType = Path.FillType.EvenOdd
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

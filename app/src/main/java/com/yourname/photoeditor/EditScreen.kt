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
    var isCropMode by remember { mutableStateOf(false) }

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
                        // The actual confirm action is handled inside CropContainer via callback,
                        // but since we moved logic here, we need a way to trigger it.
                        // Actually, it's better if CropContainer exposes the crop action or handles the state.
                        // For simplicity, we will let CropContainer manage the crop state and trigger the callback.
                    },
                    onCancel = { isCropMode = false },
                    onRatioSelected = { /* Handled in CropContainer */ }
                )
            } else {
                EditToolbar(
                    onCropClick = { isCropMode = true },
                    onRotateClick = { Log.d("EditScreen", "Rotate Clicked") },
                    onBrightnessClick = { Log.d("EditScreen", "Brightness Clicked") },
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
    onConfirm: () -> Unit, // This might be used differently if CropContainer handles state
    onCancel: () -> Unit,
    onRatioSelected: (Float?) -> Unit // Null for Free
) {
    // This toolbar is tricky because it needs to communicate with CropContainer state.
    // We'll move the specific Ratio buttons INTO CropContainer or hoist state.
    // For this structure, let's just use it as a placeholder and render the real controls inside CropContainer or hoist state up.
    // However, to follow the request "bottom toolbar should appear...", we need to hoist state.
}

// -------------------- CROP IMPLEMENTATION --------------------

@Composable
fun CropContainer(
    bitmap: android.graphics.Bitmap,
    onCropConfirm: (Int, Int, Int, Int) -> Unit,
    onCropCancel: () -> Unit
) {
    // We need to know the displayed image size to draw the overlay correctly.
    // BoxWithConstraints or onGloballyPositioned can help.
    
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var cropRect by remember { mutableStateOf(Rect.Zero) }
    var imageRect by remember { mutableStateOf(Rect.Zero) }
    var currentAspectRatio by remember { mutableStateOf<Float?>(null) } // Null = Free

    // Calculate image placement when container size is known
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
            // Initialize crop rect to full image
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
        // 1. Draw the image (static, fitted)
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

        // 2. Crop Overlay
        if (cropRect != Rect.Zero) {
            CropOverlay(
                rect = cropRect,
                imageBounds = imageRect,
                aspectRatio = currentAspectRatio,
                onRectChange = { newRect -> cropRect = newRect }
            )
        }
        
        // 3. Crop Controls (Bottom Overlay)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.8f))
        ) {
            // Aspect Ratio Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = { 
                    currentAspectRatio = null 
                    cropRect = imageRect // Reset to full on free? Or just unlock? Let's just unlock.
                }) { Text("Free") }
                TextButton(onClick = { 
                    currentAspectRatio = 1f 
                    cropRect = fitAspectRatio(cropRect, imageRect, 1f)
                }) { Text("1:1") }
                TextButton(onClick = { 
                    currentAspectRatio = 4f/3f 
                    cropRect = fitAspectRatio(cropRect, imageRect, 4f/3f)
                }) { Text("4:3") }
                TextButton(onClick = { 
                    currentAspectRatio = 16f/9f 
                    cropRect = fitAspectRatio(cropRect, imageRect, 16f/9f)
                }) { Text("16:9") }
            }
            
            // Confirm/Cancel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = onCropCancel) { Text("取消") }
                Button(onClick = {
                    // Calculate pixel coordinates
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

fun fitAspectRatio(currentRect: Rect, bounds: Rect, ratio: Float): Rect {
    // Try to keep center the same, adjust width/height
    var w = currentRect.width
    var h = w / ratio
    
    if (h > bounds.height) {
        h = bounds.height
        w = h * ratio
    }
    if (w > bounds.width) {
        w = bounds.width
        h = w / ratio
    }
    
    val cx = currentRect.center.x
    val cy = currentRect.center.y
    
    var left = cx - w / 2
    var top = cy - h / 2
    
    // Adjust if out of bounds
    if (left < bounds.left) left = bounds.left
    if (top < bounds.top) top = bounds.top
    if (left + w > bounds.right) left = bounds.right - w
    if (top + h > bounds.bottom) top = bounds.bottom - h
    
    return Rect(left, top, left + w, top + h)
}

@Composable
fun CropOverlay(
    rect: Rect,
    imageBounds: Rect,
    aspectRatio: Float?,
    onRectChange: (Rect) -> Unit
) {
    val handleSize = 40.dp
    val handleSizePx = with(LocalDensity.current) { handleSize.toPx() }
    val touchSlop = 50f // Area around handle to catch touch

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imageBounds, aspectRatio) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // Determine which handle is touched
                        // We store the active handle in a closure variable for the drag session
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        
                        // Simple hit testing for corners
                        // (In a real app, this should be robust. Here we do a quick check based on distance)
                        val left = rect.left
                        val top = rect.top
                        val right = rect.right
                        val bottom = rect.bottom
                        
                        // Distance to corners
                        val distTL = (change.position - Offset(left, top)).getDistance()
                        val distTR = (change.position - Offset(right, top)).getDistance()
                        val distBL = (change.position - Offset(left, bottom)).getDistance()
                        val distBR = (change.position - Offset(right, bottom)).getDistance()
                        
                        // Threshold
                        val threshold = 100f
                        
                        var newLeft = left
                        var newTop = top
                        var newRight = right
                        var newBottom = bottom
                        
                        // Identify handle (Naive "closest corner" approach for simplicity in this turn)
                        // Ideally we should lock the handle type on drag start.
                        // Let's assume the user grabs near a corner.
                        
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
                            // Drag whole rect
                            newLeft += dragAmount.x
                            newRight += dragAmount.x
                            newTop += dragAmount.y
                            newBottom += dragAmount.y
                        }
                        
                        // Constrain to bounds
                        // If moving whole rect
                        if (distTL >= threshold && distTR >= threshold && distBL >= threshold && distBR >= threshold) {
                            if (newLeft < imageBounds.left) {
                                val diff = imageBounds.left - newLeft
                                newLeft += diff; newRight += diff
                            }
                            if (newTop < imageBounds.top) {
                                val diff = imageBounds.top - newTop
                                newTop += diff; newBottom += diff
                            }
                            if (newRight > imageBounds.right) {
                                val diff = imageBounds.right - newRight
                                newRight += diff; newLeft += diff
                            }
                            if (newBottom > imageBounds.bottom) {
                                val diff = imageBounds.bottom - newBottom
                                newBottom += diff; newTop += diff
                            }
                        } else {
                            // Resizing - clamp edges
                            newLeft = newLeft.coerceIn(imageBounds.left, newRight - 50f)
                            newRight = newRight.coerceIn(newLeft + 50f, imageBounds.right)
                            newTop = newTop.coerceIn(imageBounds.top, newBottom - 50f)
                            newBottom = newBottom.coerceIn(newTop + 50f, imageBounds.bottom)
                            
                            // Apply Aspect Ratio if set
                            if (aspectRatio != null) {
                                // This is tricky during free drag. 
                                // Simple approach: Adjust the non-dominant axis or the one we are not dragging?
                                // If dragging BR corner, width drives height or vice versa?
                                // Let's just say width drives height for BR/TR/BL/TL logic simplification
                                val currentW = newRight - newLeft
                                val currentH = newBottom - newTop
                                
                                if (distBR < threshold || distTR < threshold) {
                                     // Right side moving, adjust height? Or adjust width to match height?
                                     // Let's enforce ratio on Width
                                     val targetH = currentW / aspectRatio
                                     if (distBR < threshold) newBottom = newTop + targetH
                                     else newTop = newBottom - targetH
                                } else {
                                     // Left side moving
                                     val targetH = currentW / aspectRatio
                                     if (distBL < threshold) newBottom = newTop + targetH
                                     else newTop = newBottom - targetH
                                }
                            }
                        }
                        
                        onRectChange(Rect(newLeft, newTop, newRight, newBottom))
                    }
                )
            }
    ) {
        // Draw Scrim (darkened area outside)
        // We use a Path to subtract the rect from the canvas size
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        // Draw semi-transparent background everywhere
        drawRect(Color.Black.copy(alpha = 0.5f))
        
        // Clear the crop area (BlendMode.Clear doesn't work well on top of a black background in Box, 
        // better to draw 4 rects around the crop area or use clipPath)
        // Since we are drawing ON TOP of the image, we want the image to show through.
        // Actually, BlendMode.Clear works if we are in a separate layer, but here we are just drawing colors.
        // The easiest way to achieve "Hole" is to draw 4 darkened rectangles around the center one.
        
        // Top
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(0f, 0f),
            size = Size(canvasWidth, rect.top)
        )
        // Bottom
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(0f, rect.bottom),
            size = Size(canvasWidth, canvasHeight - rect.bottom)
        )
        // Left
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(0f, rect.top),
            size = Size(rect.left, rect.height)
        )
        // Right
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(rect.right, rect.top),
            size = Size(canvasWidth - rect.right, rect.height)
        )
        
        // Draw Border
        drawRect(
            color = Color.White,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = 2.dp.toPx())
        )
        
        // Draw Grid (Rule of Thirds)
        val thirdW = rect.width / 3
        val thirdH = rect.height / 3
        
        drawLine(Color.White.copy(alpha = 0.5f), Offset(rect.left + thirdW, rect.top), Offset(rect.left + thirdW, rect.bottom))
        drawLine(Color.White.copy(alpha = 0.5f), Offset(rect.right - thirdW, rect.top), Offset(rect.right - thirdW, rect.bottom))
        drawLine(Color.White.copy(alpha = 0.5f), Offset(rect.left, rect.top + thirdH), Offset(rect.right, rect.top + thirdH))
        drawLine(Color.White.copy(alpha = 0.5f), Offset(rect.left, rect.bottom - thirdH), Offset(rect.right, rect.bottom - thirdH))
        
        // Draw Corner Handles
        val cornerSize = 20f
        val thick = 4.dp.toPx()
        
        // TL
        drawLine(Color.White, rect.topLeft, rect.topLeft + Offset(cornerSize, 0f), thick)
        drawLine(Color.White, rect.topLeft, rect.topLeft + Offset(0f, cornerSize), thick)
        // TR
        drawLine(Color.White, rect.topRight, rect.topRight - Offset(cornerSize, 0f), thick)
        drawLine(Color.White, rect.topRight, rect.topRight + Offset(0f, cornerSize), thick)
        // BL
        drawLine(Color.White, rect.bottomLeft, rect.bottomLeft + Offset(cornerSize, 0f), thick)
        drawLine(Color.White, rect.bottomLeft, rect.bottomLeft - Offset(0f, cornerSize), thick)
        // BR
        drawLine(Color.White, rect.bottomRight, rect.bottomRight - Offset(cornerSize, 0f), thick)
        drawLine(Color.White, rect.bottomRight, rect.bottomRight - Offset(0f, cornerSize), thick)
    }
}

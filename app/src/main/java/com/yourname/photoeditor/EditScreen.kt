package com.yourname.photoeditor

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun EditScreen(
    imageUri: String?,
    viewModel: EditViewModel = viewModel()
) {
    val bitmapState by viewModel.bitmap.collectAsState()
    
    // Load image when Uri changes or screen enters
    LaunchedEffect(imageUri) {
        imageUri?.let {
            viewModel.loadImage(Uri.parse(it))
        }
    }

    Scaffold(
        bottomBar = {
            EditToolbar(
                onCropClick = { Log.d("EditScreen", "Crop Clicked") },
                onRotateClick = { Log.d("EditScreen", "Rotate Clicked") },
                onBrightnessClick = { Log.d("EditScreen", "Brightness Clicked") },
                onTextClick = { Log.d("EditScreen", "Text Clicked") },
                onSaveClick = { Log.d("EditScreen", "Save Clicked") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black) // Dark background for editing
        ) {
            if (bitmapState != null) {
                ZoomableImage(bitmap = bitmapState!!)
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun ZoomableImage(bitmap: android.graphics.Bitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ClipToBounds can be useful if we don't want the image to overlap UI elements when zoomed in
            // .clipToBounds() 
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 2f)
                    
                    // Apply rotation logic here if needed, but currently just pan & zoom
                    // Scale pan by current scale to make panning feel natural at different zoom levels? 
                    // Standard implementation usually just adds pan.
                    // However, when zoomed in, we need to consider boundaries. 
                    // For this basic implementation, we allow free panning.
                    
                    val newOffset = offset + pan
                    // Optional: Add logic to restrict panning within bounds based on scale
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
            // 'Fit' ensures the initial state shows the whole image within the screen
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

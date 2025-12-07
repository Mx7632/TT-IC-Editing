package com.yourname.photoeditor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onNavigateToEdit: (Uri) -> Unit) {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Launcher for Gallery (using StartActivityForResult to use ACTION_PICK as requested)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                imageUri = uri
            }
        }
    }

    // Launcher for Camera
    // Using StartActivityForResult to strictly follow "start Intent(MediaStore.ACTION_IMAGE_CAPTURE)"
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // The image is saved to tempCameraUri
            tempCameraUri?.let { uri ->
                imageUri = uri
            }
        }
    }
    
    // Permission Launchers
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
             val uri = createTempPictureUri(context)
             tempCameraUri = uri
             val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
             intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
             // Grant write permission to the camera app
             intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
             cameraLauncher.launch(intent)
        }
    }
    
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryLauncher.launch(intent)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Photo Editor") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Placeholder / Image
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Selected Image",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("Placeholder Area", style = MaterialTheme.typography.headlineSmall)
                }
            }

            if (imageUri != null) {
                Button(onClick = { onNavigateToEdit(imageUri!!) }) {
                    Text("进入编辑")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    if (PermissionUtils.hasStoragePermission(context)) {
                        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        galleryLauncher.launch(intent)
                    } else {
                        storagePermissionLauncher.launch(PermissionUtils.READ_STORAGE)
                    }
                }) {
                    Text("从相册选择")
                }

                Button(onClick = {
                    if (PermissionUtils.hasCameraPermission(context)) {
                        val uri = createTempPictureUri(context)
                        tempCameraUri = uri
                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
                        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        cameraLauncher.launch(intent)
                    } else {
                        cameraPermissionLauncher.launch(PermissionUtils.CAMERA)
                    }
                }) {
                    Text("拍照")
                }
            }
        }
    }
}

fun createTempPictureUri(context: Context): Uri {
    val tempFile = File.createTempFile(
        "picture_${System.currentTimeMillis()}", 
        ".jpg", 
        context.cacheDir // Using cacheDir as defined in file_paths.xml
    )
    return FileProvider.getUriForFile(
        context, 
        "${context.packageName}.fileprovider", 
        tempFile
    )
}

package com.arsniper.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.arsniper.data.PhotoRepository

private const val TAG = "CaptureScreen"

@Composable
fun CaptureScreen(
    onPhotoCaptured: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        CameraContent(onPhotoCaptured = onPhotoCaptured, modifier = modifier)
    } else {
        PermissionContent(
            onRequest = { launcher.launch(Manifest.permission.CAMERA) },
            modifier = modifier
        )
    }
}

@Composable
private fun PermissionContent(
    onRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Camera permission is required",
                color = Color.White,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequest) {
                Text("Grant permission")
            }
        }
    }
}

@Composable
private fun CameraContent(
    onPhotoCaptured: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = context as ComponentActivity

    val previewView = remember { PreviewView(context) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1920, 1080),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
            )
            .build()
    }

    var isCapturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        providerFuture.addListener({
            try {
                val cameraProvider = providerFuture.get()
                provider = cameraProvider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
                cameraError = true
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose { provider?.unbindAll() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        if (cameraError) {
            Text(
                text = "Could not open the front camera",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Button(
            onClick = {
                if (!isCapturing) {
                    isCapturing = true
                    takePhoto(
                        context = context,
                        imageCapture = imageCapture,
                        onSaved = onPhotoCaptured,
                        onFailure = { isCapturing = false }
                    )
                }
            },
            enabled = !isCapturing && !cameraError,
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(32.dp)
                .size(96.dp)
        ) {
            Text("SHOOT", fontSize = 14.sp)
        }
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onSaved: () -> Unit,
    onFailure: () -> Unit
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val rotation = image.imageInfo.rotationDegrees
                    val raw = image.toBitmap()

                    // סיבוב לפי הכיוון + היפוך אופקי, כדי להתאים למה שנראה בתצוגה המקדימה
                    val matrix = Matrix().apply {
                        postRotate(rotation.toFloat())
                        postScale(-1f, 1f)
                    }
                    val bitmap = Bitmap.createBitmap(
                        raw, 0, 0, raw.width, raw.height, matrix, true
                    )

                    PhotoRepository.save(context, bitmap)
                    onSaved()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process photo", e)
                    onFailure()
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture failed", exception)
                onFailure()
            }
        }
    )
}

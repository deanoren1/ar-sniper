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
import com.arsniper.ui.components.rememberGalleryPicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import com.arsniper.data.PhotoImport
import com.arsniper.pano.HeadingTracker
import com.arsniper.pano.PanoFrame
import com.arsniper.pano.PanoramaStitcher
import com.arsniper.pano.PanoramaStore
import com.arsniper.pano.horizontalFov
import com.arsniper.pano.targetAngles
import com.arsniper.pano.wrapAngle
import com.arsniper.ui.components.OutlinedText
import kotlinx.coroutines.delay
import androidx.compose.runtime.DisposableEffect
import com.arsniper.ui.components.GameIcon
import com.arsniper.ui.components.IconGlyph
import com.arsniper.ui.theme.GameColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.runtime.withFrameNanos
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

/** הטיה (מעלות) שמעליה מתריעים, והטיה שמעליה לא מצלמים פריים */
private const val PANO_WARN_TILT_DEG = 10f
private const val PANO_MAX_TILT_DEG = 20f

@Composable
fun CaptureScreen(
    onPhotoCaptured: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onGalleryPicked: () -> Unit = {},
    onPanorama: () -> Unit = {}
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
        CameraContent(onPhotoCaptured = onPhotoCaptured, onBack = onBack, onGalleryPicked = onGalleryPicked, onPanorama = onPanorama, modifier = modifier)
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
    onBack: () -> Unit,
    onGalleryPicked: () -> Unit,
    onPanorama: () -> Unit,
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

    // תמונות לפנורמה: מנתח התמונה מוסר פריים רק כשמבקשים (wantFrame)
    val wantFrame = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val frameSink = remember { arrayOfNulls<(Bitmap) -> Unit>(1) }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
    }
    var panoAvailable by remember { mutableStateOf(true) }

    var isCapturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val executor = ContextCompat.getMainExecutor(context)

        providerFuture.addListener({
            try {
                val cameraProvider = providerFuture.get()
                provider = cameraProvider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                cameraProvider.unbindAll()
                try {
                    analysis.setAnalyzer(java.util.concurrent.Executors.newSingleThreadExecutor()) { proxy ->
                        try {
                            if (wantFrame.getAndSet(false)) {
                                val raw = proxy.toBitmap()
                                val matrix = Matrix().apply { postRotate(proxy.imageInfo.rotationDegrees.toFloat()) }
                                val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                                val targetW = 960
                                val scaled = if (rotated.width > targetW)
                                    Bitmap.createScaledBitmap(rotated, targetW, rotated.height * targetW / rotated.width, true)
                                else rotated
                                executor.execute { frameSink[0]?.invoke(scaled) }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Frame grab failed", e)
                        } finally {
                            proxy.close()
                        }
                    }
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Bind with analysis failed, panorama disabled", e)
                    panoAvailable = false
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
                cameraError = true
            }
        }, executor)

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
                text = "Could not open the rear camera",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        CameraFrame()

        val scope = rememberCoroutineScope()
        var importing by remember { mutableStateOf(false) }
        var flashOn by remember { mutableStateOf(false) }
        // 0 = תמונה, 1 = פנורמה 180, 2 = פנורמה 360
        var mode by remember { mutableStateOf(0) }
        val tracker = remember { HeadingTracker(context) }
        val fov = remember { horizontalFov(context) }
        var panoRunning by remember { mutableStateOf(false) }
        var panoStitching by remember { mutableStateOf(false) }
        var panoAngle by remember { mutableStateOf(0f) }
        var panoTotal by remember { mutableStateOf(1f) }
        var panoMessage by remember { mutableStateOf<String?>(null) }
        // הטיית המצלמה למעלה/למטה (מעלות) בזמן סריקה: אם גדולה, מבקשים ליישר
        var panoPitchDeg by remember { mutableStateOf(0f) }
        val picker = rememberGalleryPicker { uri ->
            run {
                importing = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { PhotoImport.importFromUri(context, uri) }
                    importing = false
                    if (ok) onGalleryPicked()
                }
            }
        }

        DisposableEffect(Unit) { onDispose { tracker.stop(); wantFrame.set(false); frameSink[0] = null } }

        fun startPanorama() {
            if (!tracker.available) { panoMessage = "No rotation sensor on this phone"; return }
            val is360 = mode == 2
            val targets = targetAngles(fov, is360)
            val frames = ArrayList<PanoFrame>()
            panoTotal = if (is360) (2 * Math.PI).toFloat() else Math.PI.toFloat()
            panoAngle = 0f
            panoMessage = null
            panoRunning = true
            tracker.start()
            scope.launch {
                // ממתינים לקריאה ראשונה מהחיישן
                var waited = 0
                while (!tracker.ready && waited < 100) { delay(20); waited++ }
                var lastYaw = tracker.yaw
                var unwrapped = 0f
                var index = 0
                var pending = false
                var pendingPhi = 0f
                var pendingPitch = 0f
                frameSink[0] = { bmp ->
                    val px = IntArray(bmp.width * bmp.height)
                    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                    frames.add(PanoFrame(px, bmp.width, bmp.height, pendingPhi, pendingPitch))
                    index++
                    pending = false
                }
                while (panoRunning && index < targets.size) {
                    withFrameNanos { }
                    // הצילום האחרון יכול להשלים בזמן ההמתנה לפריים, ואז אין עוד יעד
                    if (index >= targets.size) break
                    val y = tracker.yaw
                    unwrapped += wrapAngle(y - lastYaw)
                    lastYaw = y
                    panoAngle = unwrapped.coerceIn(0f, panoTotal)
                    panoPitchDeg = Math.toDegrees(tracker.pitch.toDouble()).toFloat()
                    // לא מצלמים כשהטלפון מוטה חזק: מחכים שיישרו, כדי שהתפרים יהיו נקיים
                    if (!pending && unwrapped >= targets[index] && kotlin.math.abs(panoPitchDeg) < PANO_MAX_TILT_DEG) {
                        pending = true
                        pendingPhi = unwrapped
                        pendingPitch = tracker.pitch
                        wantFrame.set(true)
                    }
                }
                frameSink[0] = null
                wantFrame.set(false)
                tracker.stop()
                if (!panoRunning || frames.isEmpty()) {
                    panoRunning = false
                    return@launch
                }
                panoRunning = false
                panoStitching = true
                val result = withContext(Dispatchers.Default) {
                    try { PanoramaStitcher.stitch(frames, fov, is360) } catch (e: Throwable) { Log.e(TAG, "stitch failed", e); null }
                }
                if (result != null) {
                    withContext(Dispatchers.IO) { PanoramaStore.save(context, result, is360) }
                    panoStitching = false
                    onPanorama()
                } else {
                    panoStitching = false
                    panoMessage = "Could not build the panorama"
                }
            }
        }

        // חזרה (שמאל למעלה) ופלאש (ימין למעלה)
        RoundCamButton(
            onClick = { if (panoRunning) panoRunning = false else onBack() }, blue = true,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 18.dp)
        ) { IconGlyph(if (panoRunning) GameIcon.Close else GameIcon.Back, size = 26.dp, tint = Color.White) }
        if (mode == 0) {
            RoundCamButton(
                onClick = { flashOn = !flashOn; imageCapture.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF },
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 22.dp, top = 18.dp)
            ) { BoltIcon(on = flashOn) }
        }

        // בורר מצב: PHOTO / 180 / 360
        if (!panoRunning && !panoStitching && panoAvailable) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp)
            ) {
                ModePill("PHOTO", mode == 0) { mode = 0 }
                ModePill("180\u00B0", mode == 1) { mode = 1 }
                ModePill("360\u00B0", mode == 2) { mode = 2 }
                if (mode != 0) BetaTag()
            }
        }

        // הנחיה והתקדמות בזמן סריקת פנורמה
        if (panoRunning || panoStitching || panoMessage != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp)
            ) {
                val text = when {
                    panoStitching -> "Building panorama..."
                    panoRunning && kotlin.math.abs(panoPitchDeg) >= PANO_WARN_TILT_DEG ->
                        if (panoPitchDeg > 0f) "Tilt down: hold the phone level (${panoPitchDeg.toInt()}\u00B0 up)" else "Tilt up: hold the phone level (${(-panoPitchDeg).toInt()}\u00B0 down)"
                    panoRunning -> "Turn slowly to the right  ${Math.toDegrees(panoAngle.toDouble()).toInt()}\u00B0 / ${Math.toDegrees(panoTotal.toDouble()).toInt()}\u00B0"
                    else -> panoMessage ?: ""
                }
                OutlinedText(text, fontSize = 16.sp, color = if (panoRunning && kotlin.math.abs(panoPitchDeg) >= PANO_WARN_TILT_DEG) Color(0xFFFFB74D) else Color.White)
                if (panoRunning) {
                    Spacer(Modifier.size(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.4f)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF050B1C))
                            .border(2.dp, Color(0xFF050B1C), CircleShape)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((panoAngle / panoTotal).coerceIn(0.02f, 1f))
                                .height(10.dp)
                                .background(Color(0xFFFFC21F))
                        )
                    }
                }
            }
        }

        // שורת תחתונה: גלריה, צילום, (מקום ריק לאיזון)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
        ) {
            // הייבוא מהגלריה נמצא במסך PLAY, לא כאן
            Spacer(Modifier.size(52.dp))
            ShutterButton(enabled = !isCapturing && !cameraError && !importing && !panoStitching) {
                if (mode == 0) {
                    if (!isCapturing) {
                        isCapturing = true
                        takePhoto(
                            context = context,
                            imageCapture = imageCapture,
                            onSaved = onPhotoCaptured,
                            onFailure = { isCapturing = false }
                        )
                    }
                } else if (panoRunning) {
                    panoRunning = false
                } else {
                    startPanorama()
                }
            }
            Spacer(Modifier.size(52.dp))
        }
    }
}

@Composable
private fun ModePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Brush.verticalGradient(listOf(Color(0xFFFFE04A), Color(0xFFFFA800))) else Brush.verticalGradient(listOf(Color(0xFF262F4A), Color(0xFF111729))))
            .border(2.dp, Color(0xFF050B1C), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (selected) Color(0xFF2B1A00) else Color.White, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
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

                    // סיבוב לפי הכיוון (במצלמה האחורית אין צורך בהיפוך אופקי)
                    val matrix = Matrix().apply {
                        postRotate(rotation.toFloat())
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


/** מסגרת עתידנית: מסגרת כחולה כהה, פינות כתומות וסוגריים של מיקוד באמצע */
@Composable
internal fun CameraFrame(brackets: Boolean = true) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = 12.dp.toPx()
        val navy = Color(0xFF14295E)
        val edge = Color(0xFF050B1C)
        val cyan = Color(0xFF39B6FF)
        val orange = Color(0xFFFF9A1F)
        // מסגרת
        drawRect(navy, Offset.Zero, androidx.compose.ui.geometry.Size(size.width, t))
        drawRect(navy, Offset(0f, size.height - t), androidx.compose.ui.geometry.Size(size.width, t))
        drawRect(navy, Offset.Zero, androidx.compose.ui.geometry.Size(t, size.height))
        drawRect(navy, Offset(size.width - t, 0f), androidx.compose.ui.geometry.Size(t, size.height))
        drawRect(cyan, Offset(t, t), androidx.compose.ui.geometry.Size(size.width - 2 * t, size.height - 2 * t), style = Stroke(2.dp.toPx()))
        drawRect(edge, Offset.Zero, size, style = Stroke(3.dp.toPx()))
        // פינות כתומות חתוכות
        val cw = 86.dp.toPx()
        val ch = 26.dp.toPx()
        val cut = 14.dp.toPx()
        fun corner(left: Boolean, top: Boolean) {
            val x0 = if (left) 0f else size.width
            val y0 = if (top) 0f else size.height
            val dx = if (left) 1f else -1f
            val dy = if (top) 1f else -1f
            val path = Path().apply {
                moveTo(x0, y0)
                lineTo(x0 + dx * cw, y0)
                lineTo(x0 + dx * (cw - cut), y0 + dy * ch)
                lineTo(x0 + dx * ch, y0 + dy * ch)
                lineTo(x0 + dx * ch, y0 + dy * cw)
                lineTo(x0, y0 + dy * cw)
                close()
            }
            drawPath(path, orange)
            drawPath(path, edge, style = Stroke(2.5.dp.toPx()))
        }
        corner(true, true); corner(false, true); corner(true, false); corner(false, false)
        if (!brackets) return@Canvas
        // סוגריים באמצע
        val bw = size.width * 0.28f
        val bh = size.height * 0.5f
        val left = (size.width - bw) / 2f
        val top = (size.height - bh) / 2f - size.height * 0.04f
        val len = 26.dp.toPx()
        val st = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
        val c = Color(0xCCFFFFFF)
        fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
            val p = Path().apply { moveTo(x, y + dy * len); lineTo(x, y); lineTo(x + dx * len, y) }
            drawPath(p, c, style = st)
        }
        bracket(left, top, 1f, 1f); bracket(left + bw, top, -1f, 1f)
        bracket(left, top + bh, 1f, -1f); bracket(left + bw, top + bh, -1f, -1f)
    }
}

@Composable
private fun RoundCamButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    blue: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (blue) Brush.verticalGradient(listOf(Color(0xFF3D8BFF), Color(0xFF1451C8))) else Brush.verticalGradient(listOf(Color(0xFF262F4A), Color(0xFF111729))))
            .border(3.dp, Color(0xFF050B1C), CircleShape)
            .clickable(onClick = onClick),
        content = content
    )
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(80.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .background(Color(0xFF0E1D45))
            .border(5.dp, Color(0xFF2E7BFF), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFFFE04A), Color(0xFFFFA800))))
                .border(2.dp, Color(0xFF050B1C), CircleShape)
        )
    }
}

@Composable
private fun BoltIcon(on: Boolean) {
    Canvas(Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.58f, 0f); lineTo(w * 0.18f, h * 0.56f); lineTo(w * 0.46f, h * 0.56f)
            lineTo(w * 0.36f, h); lineTo(w * 0.84f, h * 0.38f); lineTo(w * 0.54f, h * 0.38f); close()
        }
        drawPath(p, if (on) Color(0xFFFFD84A) else Color.White)
    }
}


@Composable
private fun BetaTag() {
    val shape = RoundedCornerShape(6.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(Color(0xFFFF8A1F))
            .border(2.dp, Color(0xFF050B1C), shape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text("BETA", color = Color.White, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
    }
}

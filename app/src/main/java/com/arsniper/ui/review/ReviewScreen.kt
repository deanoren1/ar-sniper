package com.arsniper.ui.review

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PhotoQuality
import com.arsniper.data.PhotoRepository
import com.arsniper.ml.ModelStore
import com.arsniper.ml.PersonMask
import com.arsniper.ml.PersonRefiner
import com.arsniper.ml.PersonRemover
import com.arsniper.ml.PersonSegmenter
import com.arsniper.ml.Sam2Segmenter
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class BrushMode { Add, Erase }

/** רדיוסי המברשת ב-dp: קטן (לאדם זעיר), בינוני, גדול */
private val BRUSH_RADII_DP = floatArrayOf(6f, 14f, 28f)
private val BRUSH_LABELS = arrayOf("S", "M", "L")

private val OVERLAY_RED = AndroidColor.argb(150, 255, 0, 0)

private class ReviewResult(
    val bitmap: Bitmap,
    val photo: ImageBitmap,
    val mask: PersonMask,
    val overlay: Bitmap,
    val seconds: Float,
    val quality: PhotoQuality.Report,
    /** הערה על דיוק המסכה (ריקה אם לא דויקה) */
    val refineNote: String
)

/**
 * מסך בדיקה ועריכה אחרי הצילום: מציג את התמונה, מסמן באדום אנשים שזוהו,
 * מאפשר להוסיף סימון (ADD) או למחוק סימון שגוי (ERASE),
 * ומסיר את האנשים המסומנים (REMOVE).
 * בודק גם את איכות התמונה (טשטוש, חשכה, כיוון) ומזהיר. ROTATE מסובב תמונה שנשמרה על הצד.
 */
@Composable
fun ReviewScreen(
    onContinue: () -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()

    var result by remember { mutableStateOf<ReviewResult?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var overlayVersion by remember { mutableStateOf(0) }
    var mode by remember { mutableStateOf(BrushMode.Add) }
    var brushIndex by remember { mutableStateOf(1) }
    var boxWidth by remember { mutableStateOf(0) }
    var boxHeight by remember { mutableStateOf(0) }

    // מצב ההסרה
    var cleaned by remember { mutableStateOf<Bitmap?>(null) }
    var removing by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var removeInfo by remember { mutableStateOf<String?>(null) }

    // עולה בכל סיבוב של התמונה, כדי שהניתוח ירוץ מחדש על התמונה המסובבת
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        val outcome = withContext(Dispatchers.Default) {
            runCatching { analyze(context) }
        }
        outcome
            .onSuccess { result = it }
            .onFailure { errorText = it.message ?: it.toString() }
    }

    // סיבוב התמונה ב-90 מעלות עם כיוון השעון (לתמונה שנשמרה על הצד), ואז ניתוח מחדש
    fun rotatePhoto() {
        if (removing || cleaned != null || result == null) return
        result = null
        errorText = null
        scope.launch {
            withContext(Dispatchers.IO) {
                val bitmap = PhotoRepository.load(context)
                if (bitmap != null) {
                    val matrix = Matrix().apply { postRotate(90f) }
                    val rotated = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )
                    PhotoRepository.save(context, rotated)
                }
            }
            reloadKey += 1
        }
    }

    // ציור במיקום מגע: ממיר מקואורדינטות מסך לקואורדינטות תמונה (ContentScale.Crop)
    fun paintAt(position: Offset) {
        val current = result ?: return
        if (removing || cleaned != null) return
        if (boxWidth == 0 || boxHeight == 0) return

        val imageWidth = current.mask.width
        val imageHeight = current.mask.height
        val scale = maxOf(
            boxWidth.toFloat() / imageWidth,
            boxHeight.toFloat() / imageHeight
        )
        val offsetX = (boxWidth - imageWidth * scale) / 2f
        val offsetY = (boxHeight - imageHeight * scale) / 2f

        val centerX = ((position.x - offsetX) / scale).roundToInt()
        val centerY = ((position.y - offsetY) / scale).roundToInt()
        val radius = (BRUSH_RADII_DP[brushIndex] * density / scale)
            .roundToInt()
            .coerceAtLeast(1)

        val changed = current.mask.paintCircle(
            centerX, centerY, radius, mode == BrushMode.Add
        )
        if (!changed.isEmpty()) {
            refreshOverlay(current.overlay, current.mask, changed)
            overlayVersion += 1
        }
    }

    // הסרת האנשים המסומנים, ברקע
    fun startRemoval() {
        val current = result ?: return
        if (removing) return
        removing = true
        removeInfo = null
        progressText = "Loading model..."

        scope.launch {
            val startNanos = System.nanoTime()
            val outcome = withContext(Dispatchers.Default) {
                runCatching {
                    val remover = PersonRemover(context)
                    try {
                        val bitmap = remover.remove(current.bitmap, current.mask) { done, total ->
                            progressText = "Removing people $done/$total"
                        }
                        Pair(bitmap, remover.usesGpu)
                    } finally {
                        remover.close()
                    }
                }
            }
            removing = false
            outcome
                .onSuccess { (bitmap, gpu) ->
                    val seconds = (System.nanoTime() - startNanos) / 1_000_000_000f
                    cleaned = bitmap
                    removeInfo = "People removed in ${formatNumber(seconds)}s " +
                        "(${if (gpu) "GPU" else "CPU"})"
                }
                .onFailure {
                    removeInfo = "Removal failed: ${it.message ?: it}"
                }
        }
    }

    val current = result
    val cleanedBitmap = cleaned
    // עטיפה חדשה בכל שינוי, כדי ש-Compose יצייר מחדש את התמונה שהשתנתה
    val overlayImage = remember(current, overlayVersion) {
        current?.overlay?.asImageBitmap()
    }
    val cleanedImage = remember(cleanedBitmap) { cleanedBitmap?.asImageBitmap() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14))
            .onSizeChanged { size ->
                boxWidth = size.width
                boxHeight = size.height
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { paintAt(it) })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { paintAt(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        paintAt(change.position)
                    }
                )
            }
    ) {
        if (current != null && overlayImage != null) {
            Image(
                bitmap = cleanedImage ?: current.photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (cleanedImage == null) {
                Image(
                    bitmap = overlayImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        val statusText = when {
            errorText != null -> "Analysis failed: $errorText"
            current == null -> "Analyzing photo..."
            removing -> progressText
            cleanedBitmap != null ->
                "${removeInfo ?: "People removed"}\n" +
                    "UNDO: restore the original · CONTINUE: play with this photo"
            else -> {
                val percent = current.mask.coverage() * 100f
                val marked = if (percent == 0f) {
                    "No people marked"
                } else {
                    "People marked: ${formatNumber(percent)}% of the photo"
                }
                val extra = removeInfo?.let { "\n$it" } ?: ""
                val warnings = current.quality.warnings().joinToString("") { "\n$it" }
                "$marked (${formatNumber(current.seconds)}s${current.refineNote})\n" +
                    "ADD: paint missed people · ERASE: remove wrong marks · REMOVE: erase people" +
                    extra +
                    warnings
            }
        }

        Text(
            text = statusText,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        if ((current == null && errorText == null) || removing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // סיבוב תמונה: בצד, כדי שלא יתפוס מקום בשורת הכפתורים
        if (current != null && !removing && cleanedBitmap == null) {
            Button(
                onClick = { rotatePhoto() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF616161)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
            ) { Text("ROTATE") }
        }

        val editingEnabled = current != null && !removing && cleanedBitmap == null
        val removeEnabled = current != null && !removing &&
            (cleanedBitmap != null || current.mask.coverage() > 0f)

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Button(onClick = onRetake, enabled = !removing) { Text("RETAKE") }

            Button(
                onClick = { mode = BrushMode.Add },
                enabled = editingEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (mode == BrushMode.Add) {
                        Color(0xFFC62828)
                    } else {
                        Color(0xFF616161)
                    }
                )
            ) { Text("ADD") }

            Button(
                onClick = { mode = BrushMode.Erase },
                enabled = editingEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (mode == BrushMode.Erase) {
                        Color(0xFF1565C0)
                    } else {
                        Color(0xFF616161)
                    }
                )
            ) { Text("ERASE") }

            Button(
                onClick = { brushIndex = (brushIndex + 1) % BRUSH_RADII_DP.size },
                enabled = editingEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF616161))
            ) { Text("BRUSH ${BRUSH_LABELS[brushIndex]}") }

            Button(
                onClick = {
                    if (cleanedBitmap != null) {
                        cleaned = null
                        removeInfo = null
                    } else {
                        startRemoval()
                    }
                },
                enabled = removeEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) { Text(if (cleanedBitmap != null) "UNDO" else "REMOVE") }

            Button(
                onClick = {
                    if (cleanedBitmap != null) {
                        PhotoRepository.save(context, cleanedBitmap)
                    }
                    onContinue()
                },
                enabled = !removing
            ) { Text("CONTINUE") }
        }
    }
}

private fun formatNumber(value: Float): String = String.format(Locale.US, "%.1f", value)

private fun analyze(context: Context): ReviewResult {
    val startNanos = System.nanoTime()
    val bitmap = PhotoRepository.load(context) ?: error("No photo found")
    val segmenter = PersonSegmenter(context)
    try {
        val quality = PhotoQuality.assess(bitmap)
        val rough = segmenter.detectPeople(bitmap)
        var mask = rough
        var refineNote = ""
        if (rough.coverage() > 0f && ModelStore.samReady(context)) {
            // מדייקים את הקצוות בעזרת SAM; כל כשל משאיר את המסכה כמו שזוהתה
            val files = ModelStore.SamFiles(
                File(context.filesDir, ModelStore.SAM_ENCODER_NAME),
                File(context.filesDir, ModelStore.SAM_DECODER_NAME),
                File(context.filesDir, ModelStore.SAM_PROMPT_NAME)
            )
            var sam: Sam2Segmenter? = null
            try {
                sam = Sam2Segmenter(files)
                sam.encode(bitmap)
                val active = sam
                val refined = PersonRefiner.refine(rough) { x, y -> active.segment(x, y) }
                if (refined.refined > 0) {
                    mask = refined.mask
                    refineNote = " · refined ${refined.refined}/${refined.people}"
                }
            } catch (e: Throwable) {
                refineNote = ""
            } finally {
                try { sam?.close() } catch (e: Throwable) { }
            }
        }
        val overlay = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
        refreshOverlay(overlay, mask, Rect(0, 0, mask.width, mask.height))
        val seconds = (System.nanoTime() - startNanos) / 1_000_000_000f
        return ReviewResult(
            bitmap = bitmap,
            photo = bitmap.asImageBitmap(),
            mask = mask,
            overlay = overlay,
            seconds = seconds,
            quality = quality,
            refineNote = refineNote
        )
    } finally {
        segmenter.close()
    }
}

/** מעדכן את תמונת הסימון (אדום חצי-שקוף) רק באזור שהשתנה */
private fun refreshOverlay(overlay: Bitmap, mask: PersonMask, rect: Rect) {
    val rectWidth = rect.width()
    val rectHeight = rect.height()
    if (rectWidth <= 0 || rectHeight <= 0) return

    val pixels = IntArray(rectWidth * rectHeight)
    for (y in 0 until rectHeight) {
        val maskRow = (rect.top + y) * mask.width + rect.left
        val pixelRow = y * rectWidth
        for (x in 0 until rectWidth) {
            if (mask.data[maskRow + x]) pixels[pixelRow + x] = OVERLAY_RED
        }
    }
    overlay.setPixels(pixels, 0, rectWidth, rect.left, rect.top, rectWidth, rectHeight)
}
package com.arsniper.ui.review

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.DisposableEffect
import kotlin.math.cos
import kotlin.math.sin
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
    /** הערה על דיוק המסכה (ריקה אם לא דויקה) */
    val refineNote: String
)

/**
 * מסך בדיקה ועריכה אחרי הצילום: מציג את התמונה, מסמן באדום אנשים שזוהו,
 * מאפשר להוסיף סימון (ADD) או למחוק סימון שגוי (ERASE),
 * ומסיר את האנשים המסומנים (REMOVE).
 * ROTATE מסובב תמונה שנשמרה על הצד.
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

    // בחירה בנגיעה (SAM), ביטול, תפריט ותיקון עדין
    val samHolder = remember { arrayOfNulls<Sam2Segmenter>(1) }
    val undoStack = remember { mutableListOf<BooleanArray>() }
    var samBusy by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var fineTune by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            try { samHolder[0]?.close() } catch (e: Throwable) { }
            samHolder[0] = null
        }
    }

    LaunchedEffect(reloadKey) {
        val outcome = withContext(Dispatchers.Default) {
            runCatching { analyze(context, samHolder) }
        }
        outcome
            .onSuccess {
                undoStack.clear()
                result = it
            }
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

    fun pushUndo() {
        val current = result ?: return
        undoStack.add(current.mask.data.copyOf())
        if (undoStack.size > 12) undoStack.removeAt(0)
    }

    fun fullRefresh(current: ReviewResult) {
        refreshOverlay(current.overlay, current.mask, Rect(0, 0, current.mask.width, current.mask.height))
        overlayVersion += 1
    }

    // נגיעה: אם הנקודה כבר אדומה מורידים את העצם מהסימון, אחרת מוסיפים אותו (SAM)
    fun samTap(position: Offset) {
        val current = result ?: return
        if (removing || cleaned != null || samBusy) return
        if (boxWidth == 0 || boxHeight == 0) return
        val imageWidth = current.mask.width
        val imageHeight = current.mask.height
        val scale = maxOf(boxWidth.toFloat() / imageWidth, boxHeight.toFloat() / imageHeight)
        val offsetX = (boxWidth - imageWidth * scale) / 2f
        val offsetY = (boxHeight - imageHeight * scale) / 2f
        val px = (position.x - offsetX) / scale
        val py = (position.y - offsetY) / scale
        if (px < 0f || py < 0f || px >= imageWidth || py >= imageHeight) return
        val wasMarked = current.mask.data[py.toInt() * imageWidth + px.toInt()]
        samBusy = true
        hint = "Selecting..."
        scope.launch {
            val outcome = withContext(Dispatchers.Default) {
                runCatching {
                    var sam = samHolder[0]
                    if (sam == null) {
                        sam = openSam(context, current.bitmap, samHolder)
                    }
                    if (sam == null) {
                        null
                    } else {
                        val masks = sam.segment(px / imageWidth, py / imageHeight)
                        logitsToRegion(masks[0], imageWidth, imageHeight)
                    }
                }
            }
            samBusy = false
            val region = outcome.getOrNull()
            when {
                outcome.isFailure -> hint = "Selection failed. Use the gear menu: Fine brush"
                region == null -> hint = "Object picker is not available. Use the gear menu: Fine brush"
                region.count { it } > region.size * MAX_TAP_FRACTION ->
                    hint = "That is too big. Tap closer to the object"
                else -> {
                    pushUndo()
                    current.mask.applyRegion(region, !wasMarked)
                    fullRefresh(current)
                    hint = null
                }
            }
        }
    }

    fun undoLast() {
        val current = result ?: return
        if (removing) return
        if (cleaned != null) {
            cleaned = null
            removeInfo = null
            return
        }
        val last = undoStack.removeLastOrNull() ?: return
        current.mask.restore(last)
        fullRefresh(current)
    }

    // הסרת האנשים המסומנים, ברקע. autoContinue: אחרי ההסרה שומרים וממשיכים ישר
    fun startRemoval(autoContinue: Boolean = false) {
        val current = result ?: return
        if (removing) return
        removing = true
        removeInfo = null
        hint = null
        progressText = "Loading model..."
        try { samHolder[0]?.close() } catch (e: Throwable) { }
        samHolder[0] = null

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
                    if (autoContinue) {
                        PhotoRepository.save(context, bitmap)
                        onContinue()
                    }
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
            .pointerInput(fineTune) {
                if (fineTune) {
                    detectTapGestures(onTap = {
                        pushUndo()
                        paintAt(it)
                    })
                } else {
                    detectTapGestures(onTap = { samTap(it) })
                }
            }
            .pointerInput(fineTune) {
                if (fineTune) {
                    detectDragGestures(
                        onDragStart = {
                            pushUndo()
                            paintAt(it)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            paintAt(change.position)
                        }
                    )
                }
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
            current == null -> "Scanning photo..."
            removing -> progressText
            cleanedBitmap != null ->
                "${removeInfo ?: "People removed"}\nOK: continue with this photo \u00B7 Menu > Undo: restore"
            hint != null -> hint!!
            fineTune -> "Fine brush: paint to add, erase to remove marks"
            current.mask.coverage() == 0f -> "No people found. Tap anything to remove it"
            else -> "Tap anything else to remove it too \u00B7 tap a red area to keep it"
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

        if ((current == null && errorText == null) || removing || samBusy) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        val ready = current != null && !removing
        val editing = ready && cleanedBitmap == null

        // תפריט (גלגל שיניים) בפינה
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC263238))
                    .clickable(enabled = ready) { menuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(28.dp)) {
                    val c = center
                    val r = size.minDimension / 2f
                    for (i in 0 until 8) {
                        val ang = i * (Math.PI.toFloat() / 4f)
                        drawLine(
                            Color.White,
                            Offset(c.x + cos(ang) * r * 0.7f, c.y + sin(ang) * r * 0.7f),
                            Offset(c.x + cos(ang) * r, c.y + sin(ang) * r),
                            strokeWidth = r * 0.35f,
                            cap = StrokeCap.Butt
                        )
                    }
                    drawCircle(Color.White, r * 0.72f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.3f))
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Undo") },
                    enabled = ready && (cleanedBitmap != null || undoStack.isNotEmpty()),
                    onClick = { menuOpen = false; undoLast() }
                )
                DropdownMenuItem(
                    text = { Text("Remove now (preview)") },
                    enabled = editing && current!!.mask.coverage() > 0f,
                    onClick = { menuOpen = false; startRemoval() }
                )
                DropdownMenuItem(
                    text = { Text(if (fineTune) "Fine brush: off" else "Fine brush") },
                    enabled = editing,
                    onClick = { menuOpen = false; fineTune = !fineTune }
                )
                DropdownMenuItem(
                    text = { Text("Rotate") },
                    enabled = editing,
                    onClick = { menuOpen = false; rotatePhoto() }
                )
                DropdownMenuItem(
                    text = { Text("Retake photo") },
                    enabled = !removing,
                    onClick = { menuOpen = false; onRetake() }
                )
            }
        }

        // מברשת עדינה: רק כשהופעלה מהתפריט
        if (fineTune && editing) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Button(
                    onClick = { mode = BrushMode.Add },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mode == BrushMode.Add) Color(0xFFC62828) else Color(0xFF616161)
                    )
                ) { Text("ADD") }
                Button(
                    onClick = { mode = BrushMode.Erase },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mode == BrushMode.Erase) Color(0xFF1565C0) else Color(0xFF616161)
                    )
                ) { Text("ERASE") }
                Button(
                    onClick = { brushIndex = (brushIndex + 1) % BRUSH_RADII_DP.size },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF616161))
                ) { Text("BRUSH ${BRUSH_LABELS[brushIndex]}") }
                Button(onClick = { fineTune = false }) { Text("DONE") }
            }
        }

        // הכפתור היחיד שהשחקן צריך: אישור (מסיר את המסומן ממשיך)
        Button(
            onClick = {
                when {
                    cleanedBitmap != null -> {
                        PhotoRepository.save(context, cleanedBitmap)
                        onContinue()
                    }
                    current != null && current.mask.coverage() > 0f -> startRemoval(autoContinue = true)
                    else -> onContinue()
                }
            },
            enabled = ready && !samBusy,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp)
        ) { Text("OK", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    }
}

private fun formatNumber(value: Float): String = String.format(Locale.US, "%.1f", value)

/** נגיעה שמסמנת יותר מזה מהתמונה נראית כטעות (למשל קיר או רצפה) */
private const val MAX_TAP_FRACTION = 0.5f

/** פותח את SAM ומקודד את התמונה (כמה שניות). null אם המודל לא הורד או נכשל */
private fun openSam(context: Context, bitmap: Bitmap, holder: Array<Sam2Segmenter?>): Sam2Segmenter? {
    if (!ModelStore.samReady(context)) return null
    try { holder[0]?.close() } catch (e: Throwable) { }
    holder[0] = null
    val files = ModelStore.SamFiles(
        File(context.filesDir, ModelStore.SAM_ENCODER_NAME),
        File(context.filesDir, ModelStore.SAM_DECODER_NAME),
        File(context.filesDir, ModelStore.SAM_PROMPT_NAME)
    )
    var sam: Sam2Segmenter? = null
    return try {
        sam = Sam2Segmenter(files)
        sam.encode(bitmap)
        holder[0] = sam
        sam
    } catch (e: Throwable) {
        try { sam?.close() } catch (ignored: Throwable) { }
        null
    }
}

/** ממיר מסכת SAM (256x256 לוגיטים על כל התמונה) למסכה בגודל מסכת האנשים, עם החלקה */
private fun logitsToRegion(logits: FloatArray, width: Int, height: Int): BooleanArray {
    val grid = 256
    val out = BooleanArray(width * height)
    val x0 = IntArray(width)
    val x1 = IntArray(width)
    val fx = FloatArray(width)
    for (x in 0 until width) {
        val g = ((x + 0.5f) / width * grid - 0.5f).coerceIn(0f, grid - 1f)
        x0[x] = g.toInt()
        x1[x] = minOf(grid - 1, x0[x] + 1)
        fx[x] = g - x0[x]
    }
    for (y in 0 until height) {
        val g = ((y + 0.5f) / height * grid - 0.5f).coerceIn(0f, grid - 1f)
        val y0 = g.toInt()
        val y1 = minOf(grid - 1, y0 + 1)
        val fy = g - y0
        val row = y * width
        for (x in 0 until width) {
            val top = logits[y0 * grid + x0[x]] * (1f - fx[x]) + logits[y0 * grid + x1[x]] * fx[x]
            val bottom = logits[y1 * grid + x0[x]] * (1f - fx[x]) + logits[y1 * grid + x1[x]] * fx[x]
            out[row + x] = top * (1f - fy) + bottom * fy > 0f
        }
    }
    return out
}

private fun analyze(context: Context, samHolder: Array<Sam2Segmenter?>): ReviewResult {
    val startNanos = System.nanoTime()
    val bitmap = PhotoRepository.load(context) ?: error("No photo found")
    val segmenter = PersonSegmenter(context)
    try {
        val rough = segmenter.detectPeople(bitmap)
        var mask = rough
        var refineNote = ""
        // SAM נשאר פתוח אחרי הניתוח, כדי שנגיעות יהיו מהירות
        val sam = openSam(context, bitmap, samHolder)
        if (sam != null && rough.coverage() > 0f) {
            try {
                val refined = PersonRefiner.refine(rough) { x, y -> sam.segment(x, y) }
                if (refined.refined > 0) {
                    mask = refined.mask
                    refineNote = " \u00B7 refined ${refined.refined}/${refined.people}"
                }
            } catch (e: Throwable) {
                refineNote = ""
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

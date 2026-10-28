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
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.res.imageResource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.arsniper.ui.components.OutlinedText
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
    // תמונה רחבה (פנורמה): איזה חלק ממנה רואים. -1 = קצה שמאל, 1 = קצה ימין
    var panBias by remember { mutableStateOf(0f) }
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
        // הניתוח של הסצנה הקודמת נשאר בזיכרון אחרי משחק: משחררים אותו לפני הזיהוי (אחרת OOM אחרי כמה תמונות)
        com.arsniper.ml.SceneSession.clear()
        var outcome = withContext(Dispatchers.Default) {
            runCatching { analyze(context, samHolder) }
        }
        if (outcome.isFailure) {
            // ניסיון שני אחרי שחרור זיכרון
            try { samHolder[0]?.close() } catch (e: Throwable) { }
            samHolder[0] = null
            kotlinx.coroutines.delay(400)
            outcome = withContext(Dispatchers.Default) {
                System.gc()
                runCatching { analyze(context, samHolder) }
            }
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
        val offsetX = (boxWidth - imageWidth * scale) * (panBias + 1f) / 2f
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
        val offsetX = (boxWidth - imageWidth * scale) * (panBias + 1f) / 2f
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
                outcome.isFailure -> { fineTune = true; hint = "Paint over what you want to remove" }
                region == null -> { fineTune = true; hint = "Paint over what you want to remove" }
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
                    System.gc()
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
                if (!fineTune) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val r = result
                        if (r != null && boxWidth > 0 && boxHeight > 0) {
                            val scaledW = r.mask.width * maxOf(boxWidth.toFloat() / r.mask.width, boxHeight.toFloat() / r.mask.height)
                            val travel = (scaledW - boxWidth) / 2f
                            if (travel > 1f) panBias = (panBias - drag.x / travel).coerceIn(-1f, 1f)
                        }
                    }
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
                alignment = androidx.compose.ui.BiasAlignment(panBias, 0f),
                modifier = Modifier.fillMaxSize()
            )
            if (cleanedImage == null) {
                Image(
                    bitmap = overlayImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = androidx.compose.ui.BiasAlignment(panBias, 0f),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // תמונה רחבה: חיצים להזזה ימינה ושמאלה (עובדים גם במצב צביעה, שבו גרירה צובעת)
        val wideImage = current != null && boxHeight > 0 &&
            current.mask.width.toFloat() / current.mask.height > boxWidth.toFloat() / boxHeight * 1.05f
        if (wideImage) {
            if (panBias > -0.99f) {
                OutlinedText(
                    "\u25C0", fontSize = 34.sp,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp)
                        .clickable { panBias = (panBias - 0.4f).coerceIn(-1f, 1f) }.padding(14.dp)
                )
            }
            if (panBias < 0.99f) {
                OutlinedText(
                    "\u25B6", fontSize = 34.sp,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)
                        .clickable { panBias = (panBias + 0.4f).coerceIn(-1f, 1f) }.padding(14.dp)
                )
            }
        }

        val statusText = when {
            errorText != null -> "Analysis failed: $errorText\nOK: continue without removing people"
            current == null -> "Scanning photo..."
            removing -> progressText
            cleanedBitmap != null ->
                "${removeInfo ?: "People removed"}\nOK: continue \u00B7 undo arrow: restore"
            hint != null -> hint!!
            fineTune -> if (mode == BrushMode.Add) "Paint over what you want to remove" else "Paint to un-mark"
            current.mask.coverage() == 0f -> "Remove people or objects from your arena\nTap anything to remove it \u00B7 OK to continue"
            else -> "Red areas will be removed from your arena\nTap anything else to remove it too \u00B7 tap red to keep it \u00B7 OK to continue"
        }

        if ((current == null && errorText == null) || removing || samBusy) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        val ready = current != null && !removing
        val editing = ready && cleanedBitmap == null

        val frameImg = androidx.compose.ui.graphics.ImageBitmap.imageResource(com.arsniper.R.drawable.review_frame)
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val sc = constraints.maxHeight / 941f
            fun u(px: Float) = with(density) { (px * sc).toDp() }
            // המסגרת מצוירת לפי הגובה; רק הפס האמצעי נמתח לרוחב, כך שהכפתורים והפינות לא מתעוותים
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val h = size.height.toInt()
                val w = size.width.toInt()
                val ls = (660 * sc).toInt()
                val rs = (662 * sc).toInt()
                drawImage(frameImg, androidx.compose.ui.unit.IntOffset(0, 0), androidx.compose.ui.unit.IntSize(660, 941),
                    androidx.compose.ui.unit.IntOffset(0, 0), androidx.compose.ui.unit.IntSize(ls, h))
                drawImage(frameImg, androidx.compose.ui.unit.IntOffset(660, 0), androidx.compose.ui.unit.IntSize(350, 941),
                    androidx.compose.ui.unit.IntOffset(ls, 0), androidx.compose.ui.unit.IntSize(w - ls - rs, h))
                drawImage(frameImg, androidx.compose.ui.unit.IntOffset(1010, 0), androidx.compose.ui.unit.IntSize(662, 941),
                    androidx.compose.ui.unit.IntOffset(w - rs, 0), androidx.compose.ui.unit.IntSize(rs, h))
            }
            // לוח ההוראות העליון: מכסה את הכיתוב הצבוע במסגרת ומציג את הטקסט הנוכחי
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = u(300f), end = u(300f), top = u(33f))
                    .fillMaxWidth()
                    .height(u(138f))
                    .clip(RoundedCornerShape(u(38f)))
                    .background(Color(0xFF0A1535))
                    .border(u(4f), Color(0xFF2FB4FF), RoundedCornerShape(u(38f)))
            ) {
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            // אזורי מגע שקופים מעל הכפתורים שבמסגרת
            @Composable
            fun Hit(cx: Float, cy: Float, r: Float, enabled: Boolean, onClick: () -> Unit) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = u(cx - r), top = u(cy - r))
                        .size(u(2 * r))
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable(enabled = enabled, onClick = onClick)
                )
            }
            Hit(151f, 762f, 75f, !removing, onRetake)
            Hit(330f, 762f, 75f, editing) { fineTune = !fineTune }
            Hit(508f, 762f, 75f, ready && (cleanedBitmap != null || undoStack.isNotEmpty())) { undoLast() }
            if (fineTune && editing) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(start = u(620f), top = u(690f))
                ) {
                    ReviewRoundButton(
                        enabled = true,
                        active = mode == BrushMode.Erase,
                        activeColor = Color(0xFF1565C0),
                        onClick = { mode = if (mode == BrushMode.Add) BrushMode.Erase else BrushMode.Add }
                    ) { EraserIcon() }
                    ReviewRoundButton(
                        enabled = true,
                        onClick = { brushIndex = (brushIndex + 1) % BRUSH_RADII_DP.size }
                    ) { SizeDotIcon(brushIndex) }
                }
            }
            // אישור
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = u(47f), top = u(720f))
                    .size(width = u(360f), height = u(150f))
                    .clip(RoundedCornerShape(u(70f)))
                    .clickable(enabled = (ready && !samBusy) || errorText != null) {
                        when {
                            errorText != null -> onContinue()
                            cleanedBitmap != null -> {
                                PhotoRepository.save(context, cleanedBitmap)
                                onContinue()
                            }
                            current != null && current.mask.coverage() > 0f -> startRemoval(autoContinue = true)
                            else -> onContinue()
                        }
                    }
            )
        }
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
    // מפנים זיכרון משלבים קודמים לפני מודל הזיהוי (OOM אחרי כמה תמונות ברצף)
    System.gc()
    com.arsniper.data.Breadcrumb.mark(context, "review: analysis start")
    val startNanos = System.nanoTime()
    val bitmap = PhotoRepository.load(context) ?: error("No photo found")
    com.arsniper.data.Breadcrumb.mark(context, "review: photo loaded ${bitmap.width}x${bitmap.height}")
    val segmenter = PersonSegmenter(context)
    com.arsniper.data.Breadcrumb.mark(context, "review: segmenter created")
    try {
        val rough = segmenter.detectPeople(bitmap)
        var mask = rough
        var refineNote = ""
        com.arsniper.data.Breadcrumb.mark(context, "review: people detected, opening SAM")
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
        com.arsniper.data.Breadcrumb.mark(context, "review: analysis done")
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


@Composable
private fun ReviewRoundButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color = Color(0xFFFFB300),
    content: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(52.dp)
            .androidx_alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(if (active) activeColor else Color(0xFF14295E))
            .androidx_border()
            .clickable(enabled = enabled, onClick = onClick)
    ) { content() }
}

private fun Modifier.androidx_alpha(a: Float): Modifier = this.then(Modifier.alpha(a))
private fun Modifier.androidx_border(): Modifier =
    this.then(Modifier.border(3.dp, Color(0xFF050B1C), CircleShape))

private val ICON = Color.White

@Composable
private fun BrushIcon() {
    Canvas(Modifier.size(28.dp)) {
        val w = size.width
        drawLine(ICON, Offset(w * 0.85f, w * 0.15f), Offset(w * 0.42f, w * 0.58f), strokeWidth = w * 0.2f, cap = StrokeCap.Round)
        drawCircle(ICON, w * 0.18f, Offset(w * 0.28f, w * 0.72f))
    }
}

@Composable
private fun EraserIcon() {
    Canvas(Modifier.size(28.dp)) {
        val w = size.width
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.15f, w * 0.65f)
            lineTo(w * 0.55f, w * 0.2f)
            lineTo(w * 0.88f, w * 0.5f)
            lineTo(w * 0.5f, w * 0.88f)
            lineTo(w * 0.3f, w * 0.88f)
            close()
        }
        drawPath(path, ICON)
        drawLine(Color(0xFF14295E), Offset(w * 0.34f, w * 0.43f), Offset(w * 0.68f, w * 0.7f), strokeWidth = w * 0.06f)
    }
}

@Composable
private fun SizeDotIcon(index: Int) {
    Canvas(Modifier.size(28.dp)) {
        val r = size.width * (0.14f + 0.12f * index)
        drawCircle(ICON, r, center)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.refreshArrow(c: Offset, r: Float, sw: Float) {
    drawArc(
        ICON, 40f, 280f, false,
        topLeft = Offset(c.x - r, c.y - r),
        size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = StrokeCap.Round)
    )
    val a = Math.toRadians(320.0)
    val tip = Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
    val head = androidx.compose.ui.graphics.Path().apply {
        moveTo(tip.x + sw * 1.8f, tip.y - sw * 0.2f)
        lineTo(tip.x - sw * 0.2f, tip.y - sw * 1.8f)
        lineTo(tip.x - sw * 1.2f, tip.y + sw * 1.6f)
        close()
    }
    drawPath(head, ICON)
}

@Composable
private fun RotateIcon() {
    Canvas(Modifier.size(28.dp)) { refreshArrow(center, size.width * 0.34f, size.width * 0.11f) }
}

@Composable
private fun UndoIcon() {
    Canvas(Modifier.size(28.dp)) {
        val w = size.width
        drawArc(
            ICON, 200f, 220f, false,
            topLeft = Offset(w * 0.2f, w * 0.3f),
            size = androidx.compose.ui.geometry.Size(w * 0.62f, w * 0.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.11f, cap = StrokeCap.Round)
        )
        val head = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.1f, w * 0.2f)
            lineTo(w * 0.1f, w * 0.52f)
            lineTo(w * 0.4f, w * 0.38f)
            close()
        }
        drawPath(head, ICON)
    }
}

@Composable
private fun RetakeIcon() {
    Canvas(Modifier.size(30.dp)) {
        val w = size.width
        drawRoundRect(ICON, Offset(w * 0.05f, w * 0.3f), androidx.compose.ui.geometry.Size(w * 0.9f, w * 0.6f), androidx.compose.ui.geometry.CornerRadius(w * 0.1f))
        drawRoundRect(ICON, Offset(w * 0.3f, w * 0.15f), androidx.compose.ui.geometry.Size(w * 0.3f, w * 0.2f), androidx.compose.ui.geometry.CornerRadius(w * 0.05f))
        drawCircle(Color(0xFF14295E), w * 0.22f, Offset(w * 0.5f, w * 0.6f))
        refreshArrow(Offset(w * 0.5f, w * 0.6f), w * 0.13f, w * 0.05f)
    }
}

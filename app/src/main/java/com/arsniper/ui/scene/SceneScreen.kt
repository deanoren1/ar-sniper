package com.arsniper.ui.scene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PhotoRepository
import com.arsniper.game.spots.GuideImage
import com.arsniper.game.spots.HidingSpot
import com.arsniper.game.spots.SpotKind
import com.arsniper.ml.AnalysisCache
import com.arsniper.ml.Ade20kClasses
import com.arsniper.ml.ModelStore
import com.arsniper.ml.Sam2Segmenter
import com.arsniper.ml.SamAuto
import com.arsniper.ml.SceneAnalysis
import com.arsniper.ml.SceneSegmenter
import com.arsniper.ml.SceneSession
import com.arsniper.ml.UserObjects
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ViewMode { Segmentation, Depth, Spots, Photo, Objects }

/** כמה פעמים גבוהה מרשת הניתוח הרזולוציה של ליטוש הקצוות */
private const val REFINE_SCALE = 3

/** צבע קבוע לכל קטגוריה (חצי-שקוף) */
private val CLASS_COLORS = IntArray(150) { index ->
    AndroidColor.HSVToColor(150, floatArrayOf((index * 47 % 360).toFloat(), 0.75f, 0.95f))
}

/** עצמים שמעניינים אותנו להסתתרות: מציגים אותם במקרא גם אם הם תופסים אחוז זעיר */
private val HIDING_CLASSES = listOf(
    "windowpane", "door", "ashcan", "barrel", "box", "pole", "column",
    "fence", "car", "truck", "van", "bus", "streetlight", "bench"
)

private val PREVIEW_COLOR = AndroidColor.argb(150, 255, 0, 200)

private class SceneView(
    val photo: ImageBitmap,
    val bitmap: Bitmap,
    val analysis: SceneAnalysis,
    val cacheKey: String?,
    val objects: List<FloatArray>,
    val samMasks: List<FloatArray>,
    val segmentation: ImageBitmap,
    val depth: ImageBitmap,
    val legend: List<String>,
    val spotsOverlay: ImageBitmap,
    val spotsLegend: List<String>,
    val seconds: Float,
    val tileCount: Int,
    val cached: Boolean
)

/** תוצאת עדכון רשימת העצמים שסומנו ידנית */
private class SpotsUpdate(val overlay: ImageBitmap, val legend: List<String>)

/**
 * מסך בדיקת היתכנות: מוריד (פעם אחת) את מודל הסצנה, מריץ אותו על התמונה,
 * ומציג מה המערכת "רואה": סוגי עצמים ועומק.
 * תמונה שכבר נותחה נטענת מהניתוח השמור (בלי להריץ את המודל מחדש).
 * במצב OBJ אפשר לגעת בעצם שהמערכת פספסה (SAM) ולהוסיף אותו כמחבוא.
 */
@Composable
fun SceneScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    auto: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var statusText by remember { mutableStateOf("Preparing...") }
    var busy by remember { mutableStateOf(true) }
    var sceneView by remember { mutableStateOf<SceneView?>(null) }
    // התקדמות הסריקה לשחקן (0..1): ניתוח 0-0.6, הכנת מודל העצמים עד 0.7, זיהוי עצמים עד 1
    var scanProgress by remember { mutableStateOf(0f) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var viewMode by remember { mutableStateOf(ViewMode.Segmentation) }

    // מצב סימון עצמים (SAM)
    val samHolder = remember { arrayOfNulls<Sam2Segmenter>(1) }
    var samState by remember { mutableStateOf(0) } // 0 לא נטען, 1 טוען, 2 מוכן
    var samStatus by remember { mutableStateOf("") }
    var samBusy by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(IntSize(1, 1)) }
    var masks by remember { mutableStateOf<Array<FloatArray>?>(null) }
    var maskIndex by remember { mutableStateOf(0) }
    var previewImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var userObjects by remember { mutableStateOf<List<FloatArray>>(emptyList()) }
    var autoMasks by remember { mutableStateOf<List<FloatArray>>(emptyList()) }
    var spotsImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var debugImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var debugLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var showDebug by remember { mutableStateOf(false) }
    var spotsLines by remember { mutableStateOf<List<String>>(emptyList()) }

    DisposableEffect(Unit) {
        onDispose {
            samHolder[0]?.close()
            samHolder[0] = null
        }
    }

    LaunchedEffect(Unit) {
        SceneSession.clear()
        try {
            // תמונה שכבר נותחה: טוענים את הניתוח השמור (שניות במקום חצי דקה), בלי לגעת במודל
            val savedAnalysis = withContext(Dispatchers.IO) { loadSavedAnalysis(context) }
            val modelFile: File? = if (savedAnalysis != null) {
                null
            } else {
                statusText = "Checking scene model..."
                ModelStore.ensureTipsModel(context) { downloaded, total ->
                    val megabytes = downloaded / 1_000_000L
                    statusText = if (total > 0L) {
                        "Downloading scene model (one time, ~318 MB): " +
                            "$megabytes / ${total / 1_000_000L} MB (${downloaded * 100L / total}%)"
                    } else {
                        "Downloading scene model (one time): $megabytes MB"
                    }
                }
            }

            statusText = if (savedAnalysis != null) {
                "Loading saved scene analysis..."
            } else {
                "Preparing scene analysis (first run takes longer)..."
            }
            val outcome = withContext(Dispatchers.Default) {
                runCatching {
                    analyzeScene(context, modelFile, savedAnalysis) { done, total ->
                        statusText = "Analyzing scene: part $done of $total"
                        scanProgress = 0.6f * done / maxOf(total, 1)
                    }
                }
            }
            outcome
                .onSuccess {
                    scanProgress = 0.6f
                    sceneView = it
                    userObjects = it.objects
                    autoMasks = it.samMasks
                    spotsImage = it.spotsOverlay
                    spotsLines = it.spotsLegend
                }
                .onFailure { errorText = it.message ?: it.toString() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorText = e.message ?: e.toString()
        } finally {
            busy = false
        }
    }

    val current = sceneView

    /** מוריד את SAM (פעם אחת), טוען ומקודד את התמונה */
    fun startSam(view: SceneView) {
        if (samState != 0) return
        samState = 1
        scanProgress = maxOf(scanProgress, 0.62f)
        scope.launch {
            try {
                samStatus = "Preparing object model..."
                val files = ModelStore.ensureSamModels(context) { name, downloaded, total ->
                    val mb = downloaded / 1_000_000L
                    samStatus = if (total > 0L) {
                        "Downloading object model ($name, one time): $mb / ${total / 1_000_000L} MB"
                    } else {
                        "Downloading object model ($name): $mb MB"
                    }
                }
                samStatus = "Reading the photo (a few seconds)..."
                val ms = withContext(Dispatchers.Default) {
                    val sam = Sam2Segmenter(files)
                    samHolder[0] = sam
                    sam.encode(view.bitmap)
                }
                val acc = samHolder[0]?.encoderAccelerator ?: "?"
                samStatus = "Tap an object (photo read in ${ms} ms, $acc)"
                scanProgress = maxOf(scanProgress, 0.7f)
                samState = 2
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                samStatus = "Object model failed: ${e.message ?: e}"
                samState = 0
            }
        }
    }

    fun showMask(view: SceneView, all: Array<FloatArray>, index: Int) {
        val gw = view.analysis.width
        val gh = view.analysis.height
        scope.launch {
            val bmp = withContext(Dispatchers.Default) {
                val px = UserObjects.previewPixels(all[index], gw, gh, PREVIEW_COLOR)
                Bitmap.createBitmap(px, gw, gh, Bitmap.Config.ARGB_8888)
            }
            previewImage = bmp.asImageBitmap()
        }
    }

    fun onTap(view: SceneView, x: Float, y: Float) {
        val sam = samHolder[0] ?: return
        if (samState != 2 || samBusy) return
        val pw = view.bitmap.width.toFloat()
        val ph = view.bitmap.height.toFloat()
        val cw = boxSize.width.toFloat()
        val ch = boxSize.height.toFloat()
        val scale = max(cw / pw, ch / ph)
        val offX = (cw - pw * scale) / 2f
        val offY = (ch - ph * scale) / 2f
        val nx = ((x - offX) / scale) / pw
        val ny = ((y - offY) / scale) / ph
        if (nx < 0f || nx > 1f || ny < 0f || ny > 1f) return
        samBusy = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.Default) { sam.segment(nx, ny) }
                masks = result
                maskIndex = 0
                showMask(view, result, 0)
                samStatus = "Mask 1/3. ADD to keep it, or MASK to try another"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                samStatus = "Segmentation failed: ${e.message ?: e}"
            } finally {
                samBusy = false
            }
        }
    }

    fun applyObjects(view: SceneView, list: List<FloatArray>, message: String) {
        scope.launch {
            samBusy = true
            val update = withContext(Dispatchers.Default) {
                UserObjects.save(context.filesDir, view.cacheKey ?: "none", list)
                SceneSession.setUserObjects(list)
                SpotsUpdate(
                    buildSpotsOverlay(view.analysis, SceneSession.spots).asImageBitmap(),
                    buildSpotsLegend(SceneSession.spots)
                )
            }
            userObjects = list
            spotsImage = update.overlay
            spotsLines = update.legend
            masks = null
            previewImage = null
            samStatus = message
            samBusy = false
        }
    }

    /** שומר את העצמים ש-SAM מצא לבד (ריק = חוזרים לזיהוי לפי סוגים) ומעדכן את התצוגה */
    fun applyAuto(view: SceneView, list: List<FloatArray>, message: String) {
        scope.launch {
            samBusy = true
            val update = withContext(Dispatchers.Default) {
                UserObjects.save(context.filesDir, view.cacheKey ?: "none", list, "sam", SamAuto.MAX_MASKS)
                SceneSession.setSamMasks(list)
                SpotsUpdate(
                    buildSpotsOverlay(view.analysis, SceneSession.spots).asImageBitmap(),
                    buildSpotsLegend(SceneSession.spots)
                )
            }
            autoMasks = list
            spotsImage = update.overlay
            spotsLines = update.legend
            masks = null
            previewImage = null
            samStatus = message
            samBusy = false
        }
    }

    /** זיהוי אוטומטי: SAM מנסה נקודות בכל התמונה ובמרכז כל מחבוא, ושומר את העצמים שמצא */
    fun runAuto(view: SceneView) {
        val sam = samHolder[0] ?: return
        if (samState != 2 || samBusy) return
        samBusy = true
        scope.launch {
            try {
                val startMs = System.currentTimeMillis()
                val found = withContext(Dispatchers.Default) {
                    SamAuto.run(
                        view.analysis,
                        SceneSession.finderSpots,
                        { x, y -> sam.segment(x, y) },
                        { done, total ->
                            val seconds = (System.currentTimeMillis() - startMs) / 1000
                            samStatus = "Finding objects: $done / $total (${seconds}s)"
                            scanProgress = 0.7f + 0.3f * done / maxOf(total, 1)
                        }
                    )
                }
                val seconds = (System.currentTimeMillis() - startMs) / 1000
                val overlay = SamAuto.debugOverlay
                if (overlay != null) {
                    val dw = SamAuto.debugWidth
                    val dh = SamAuto.debugHeight
                    debugImage = Bitmap.createBitmap(overlay, dw, dh, Bitmap.Config.ARGB_8888).asImageBitmap()
                    val c = SamAuto.debugCounts
                    debugLines = listOf(
                        "kept (orange): ${c["kept"] ?: 0}",
                        "RED = wall: ${c[SamAuto.R_WALL] ?: 0}",
                        "YELLOW = size: ${c[SamAuto.R_AREA] ?: 0}",
                        "GREEN = floor: ${c[SamAuto.R_FLOOR] ?: 0}",
                        "CYAN = unstable: ${c[SamAuto.R_UNSTABLE] ?: 0}",
                        "WHITE = duplicate: ${c[SamAuto.R_DUP] ?: 0}",
                        "skipped points: ${c[SamAuto.R_SKIP] ?: 0}"
                    )
                    showDebug = false
                }
                samBusy = false
                applyAuto(view, found, "Auto: found ${found.size} objects in ${seconds}s. DBG shows rejected")
            } catch (e: CancellationException) {
                samBusy = false
                throw e
            } catch (e: Throwable) {
                samBusy = false
                samStatus = "Auto failed: ${e.message ?: e}"
            }
        }
    }

    fun addCurrent(view: SceneView) {
        val all = masks ?: return
        val logits = all[maskIndex]
        val test = UserObjects.toSpot(view.analysis, logits, 0)
        if (test == null) {
            samStatus = "That mask is too small or too big. Try MASK or tap elsewhere"
            return
        }
        val list = (userObjects + listOf(logits)).takeLast(UserObjects.MAX_OBJECTS)
        applyObjects(view, list, "Added. Objects marked: ${list.size}")
    }

    // מצב שחקן (auto): ניתוח, זיהוי עצמים אוטומטי, וממשיכים למשחק בלי לגעת בכלום
    var autoStage by remember { mutableStateOf(0) }
    if (auto) {
        LaunchedEffect(current, samState, samBusy, samStatus) {
            val view = current ?: return@LaunchedEffect
            when {
                autoStage == 0 -> {
                    if (view.samMasks.isNotEmpty()) {
                        autoStage = 9
                        onContinue()
                    } else {
                        autoStage = 1
                        startSam(view)
                    }
                }
                autoStage == 1 && samState == 2 && !samBusy -> {
                    autoStage = 2
                    runAuto(view)
                }
                // מודל העצמים נכשל: ממשיכים עם הזיהוי הרגיל לפי סוגים
                autoStage == 1 && samState == 0 && samStatus.startsWith("Object model failed") -> {
                    autoStage = 9
                    onContinue()
                }
                autoStage == 2 && !samBusy && samStatus.startsWith("Auto") -> {
                    autoStage = 9
                    onContinue()
                }
            }
        }
        AutoScanOverlay(
            progress = scanProgress,
            status = when {
                errorText != null -> "Scan failed: $errorText\nPress back and try another photo."
                current == null -> statusText
                else -> samStatus
            },
            failed = errorText != null
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14))
            .onSizeChanged { boxSize = it }
            .pointerInput(viewMode, samState, current) {
                detectTapGestures { offset ->
                    val view = current
                    if (view != null && viewMode == ViewMode.Objects) {
                        onTap(view, offset.x, offset.y)
                    }
                }
            }
    ) {
        if (current != null) {
            Image(
                bitmap = current.photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            when (viewMode) {
                ViewMode.Segmentation -> Image(
                    bitmap = current.segmentation,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                ViewMode.Depth -> Image(
                    bitmap = current.depth,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                ViewMode.Spots, ViewMode.Objects -> {
                    Image(
                        bitmap = spotsImage ?: current.spotsOverlay,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    val dbg = debugImage
                    if (viewMode == ViewMode.Objects && showDebug && dbg != null) {
                        Image(
                            bitmap = dbg,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    val preview = previewImage
                    if (viewMode == ViewMode.Objects && preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                ViewMode.Photo -> Unit
            }

            val legendLines = when (viewMode) {
                ViewMode.Segmentation -> current.legend
                ViewMode.Spots, ViewMode.Objects ->
                    if (viewMode == ViewMode.Objects && showDebug && debugLines.isNotEmpty()) debugLines
                    else if (spotsLines.isNotEmpty()) spotsLines else current.spotsLegend
                else -> emptyList()
            }
            if (legendLines.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(8.dp)
                ) {
                    for (line in legendLines) {
                        Text(text = line, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        val statusLine = when {
            errorText != null -> "Scene analysis failed: $errorText"
            current != null && viewMode == ViewMode.Objects && samStatus.isNotEmpty() -> samStatus
            current != null && current.cached ->
                "Scene loaded from saved analysis in ${String.format(Locale.US, "%.1f", current.seconds)}s"
            current != null ->
                "Scene analyzed in ${String.format(Locale.US, "%.1f", current.seconds)}s " +
                    "(${current.tileCount} parts, GPU)"
            else -> statusText
        }

        Text(
            text = statusLine,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        if (busy || samState == 1 || samBusy) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            if (current != null && viewMode == ViewMode.Objects && samState == 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton("ADD", masks != null && !samBusy) { addCurrent(current) }
                    ActionButton("MASK ${maskIndex + 1}/3", masks != null && !samBusy) {
                        val all = masks
                        if (all != null) {
                            maskIndex = (maskIndex + 1) % 3
                            showMask(current, all, maskIndex)
                            samStatus = "Mask ${maskIndex + 1}/3. ADD to keep it"
                        }
                    }
                    ActionButton("UNDO", userObjects.isNotEmpty() && !samBusy) {
                        applyObjects(current, userObjects.dropLast(1), "Removed last. Objects marked: ${userObjects.size - 1}")
                    }
                    ActionButton("CLEAR", userObjects.isNotEmpty() && !samBusy) {
                        applyObjects(current, emptyList(), "All marked objects cleared")
                    }
                    ActionButton(if (autoMasks.isEmpty()) "AUTO" else "AUTO OFF", !samBusy) {
                        if (autoMasks.isEmpty()) runAuto(current)
                        else applyAuto(current, emptyList(), "Auto off: back to the standard detection")
                    }
                    ActionButton(if (showDebug) "DBG OFF" else "DBG", debugImage != null && !samBusy) {
                        showDebug = !showDebug
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeButton("SEG", ViewMode.Segmentation, viewMode, current != null) { viewMode = it }
                ModeButton("DEPTH", ViewMode.Depth, viewMode, current != null) { viewMode = it }
                ModeButton("SPOTS", ViewMode.Spots, viewMode, current != null) { viewMode = it }
                ModeButton("PHOTO", ViewMode.Photo, viewMode, current != null) { viewMode = it }
                ModeButton("OBJ", ViewMode.Objects, viewMode, current != null) {
                    viewMode = it
                    if (current != null) startSam(current)
                }
                Button(onClick = onContinue) { Text("CONTINUE") }
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
    ) { Text(label) }
}

@Composable
private fun ModeButton(
    label: String,
    mode: ViewMode,
    selected: ViewMode,
    enabled: Boolean,
    onSelect: (ViewMode) -> Unit
) {
    Button(
        onClick = { onSelect(mode) },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (mode == selected) Color(0xFF1565C0) else Color(0xFF616161)
        )
    ) { Text(label) }
}

/** מחפש ניתוח שמור לתמונה הנוכחית (לפי תוכן הקובץ), או null אם אין */
private fun loadSavedAnalysis(context: Context): SceneAnalysis? {
    val file = PhotoRepository.currentFile(context) ?: return null
    return AnalysisCache.load(context.filesDir, AnalysisCache.keyFor(file))
}

private fun analyzeScene(
    context: Context,
    modelFile: File?,
    saved: SceneAnalysis?,
    onProgress: (done: Int, total: Int) -> Unit
): SceneView {
    val startNanos = System.nanoTime()
    val photo = PhotoRepository.load(context) ?: error("No photo found")
    val photoFile = PhotoRepository.currentFile(context)
    val cacheKey = photoFile?.let { AnalysisCache.keyFor(it) }

    val analysis: SceneAnalysis
    if (saved != null) {
        analysis = saved
    } else {
        val segmenter = SceneSegmenter(modelFile ?: error("Scene model missing"))
        analysis = try {
            segmenter.analyze(photo, onProgress)
        } finally {
            segmenter.close()
        }
        // שומרים לפעם הבאה (אותה תמונה = ניתוח מיידי)
        if (cacheKey != null) AnalysisCache.save(context.filesDir, cacheKey, analysis)
    }

    // שומרים את הניתוח (ומוצאים מחבואים ומלטשים את הקצוות שלהם) בשביל מסך המשחק
    SceneSession.set(analysis, buildGuide(photo, analysis.width, analysis.height))

    // עצמים שסומנו ידנית בעבר על אותה תמונה
    val objects = if (cacheKey != null) UserObjects.load(context.filesDir, cacheKey) else emptyList()
    if (objects.isNotEmpty()) SceneSession.setUserObjects(objects)

    // עצמים ש-SAM מצא לבד באותה תמונה (אם הופעל זיהוי אוטומטי בעבר)
    val samMasks = if (cacheKey != null) UserObjects.load(context.filesDir, cacheKey, "sam") else emptyList()
    if (samMasks.isNotEmpty()) SceneSession.setSamMasks(samMasks)

    val spots = SceneSession.spots
    val seconds = (System.nanoTime() - startNanos) / 1_000_000_000f

    return SceneView(
        photo = photo.asImageBitmap(),
        bitmap = photo,
        analysis = analysis,
        cacheKey = cacheKey,
        objects = objects,
        samMasks = samMasks,
        segmentation = buildSegmentationOverlay(analysis).asImageBitmap(),
        depth = buildDepthImage(analysis).asImageBitmap(),
        legend = buildLegend(analysis),
        spotsOverlay = buildSpotsOverlay(analysis, spots).asImageBitmap(),
        spotsLegend = buildSpotsLegend(spots),
        seconds = seconds,
        tileCount = analysis.tileCount,
        cached = saved != null
    )
}

/** מקרא: הקטגוריות הנפוצות, ומתחתן עצמים שמעניינים אותנו להסתתרות (גם אם זעירים) */
private fun buildLegend(analysis: SceneAnalysis): List<String> {
    val lines = ArrayList<String>()
    for ((index, fraction) in analysis.topClasses(6)) {
        lines.add("${Ade20kClasses.name(index)}  ${(fraction * 100).roundToInt()}%")
    }
    lines.add("-- hiding candidates --")
    var foundAny = false
    for (name in HIDING_CLASSES) {
        val index = Ade20kClasses.NAMES.indexOf(name)
        if (index < 0) continue
        val fraction = analysis.fraction(index)
        if (fraction > 0f) {
            foundAny = true
            lines.add("$name  ${String.format(Locale.US, "%.1f", fraction * 100f)}%")
        }
    }
    if (!foundAny) lines.add("(none found)")
    return lines
}

/** תמונה שקופה בפרופורציות התמונה, עם צבע לכל סוג עצם */
private fun buildSegmentationOverlay(analysis: SceneAnalysis): Bitmap {
    val pixels = IntArray(analysis.width * analysis.height)
    for (i in pixels.indices) {
        val label = analysis.labelIndex(i)
        pixels[i] = CLASS_COLORS[label.coerceIn(0, CLASS_COLORS.size - 1)]
    }
    return Bitmap.createBitmap(pixels, analysis.width, analysis.height, Bitmap.Config.ARGB_8888)
}

/** מפת עומק באפור: בהיר = קרוב, כהה = רחוק */
private fun buildDepthImage(analysis: SceneAnalysis): Bitmap {
    var minDepth = Float.MAX_VALUE
    var maxDepth = -Float.MAX_VALUE
    for (value in analysis.depth) {
        if (value.isNaN() || value.isInfinite()) continue
        if (value < minDepth) minDepth = value
        if (value > maxDepth) maxDepth = value
    }
    val range = (maxDepth - minDepth).coerceAtLeast(1e-6f)

    val pixels = IntArray(analysis.width * analysis.height)
    for (i in pixels.indices) {
        val t = ((analysis.depth[i] - minDepth) / range).coerceIn(0f, 1f)
        val gray = ((1f - t) * 255f).roundToInt()
        pixels[i] = AndroidColor.argb(255, gray, gray, gray)
    }
    return Bitmap.createBitmap(pixels, analysis.width, analysis.height, Bitmap.Config.ARGB_8888)
}

private val COVER_COLOR = AndroidColor.argb(170, 255, 140, 0)
private val WINDOW_COLOR = AndroidColor.argb(170, 0, 200, 255)

/** תמונת אפור ברזולוציה גבוהה (פי REFINE_SCALE מהרשת), שעוזרת להצמיד קצוות לתמונה */
private fun buildGuide(photo: Bitmap, gridWidth: Int, gridHeight: Int): GuideImage {
    val width = gridWidth * REFINE_SCALE
    val height = gridHeight * REFINE_SCALE
    val scaled = Bitmap.createScaledBitmap(photo, width, height, true)
    val pixels = IntArray(width * height)
    scaled.getPixels(pixels, 0, width, 0, 0, width, height)
    if (scaled !== photo) scaled.recycle()

    val gray = ByteArray(width * height)
    for (i in pixels.indices) {
        val color = pixels[i]
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        gray[i] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
    }
    return GuideImage(width, height, gray, REFINE_SCALE)
}

/**
 * מסכת המחבואים שזוהו, ברזולוציה של הליטוש (כך רואים את הקצוות המלוטשים):
 * כתום = מאחורי עצם, תכלת = חלון. שקיפות הקצה משקפת את האלפא הרכה.
 */
private fun buildSpotsOverlay(analysis: SceneAnalysis, spots: List<HidingSpot>): Bitmap {
    val width = analysis.width * REFINE_SCALE
    val height = analysis.height * REFINE_SCALE
    val pixels = IntArray(width * height)
    for (spot in spots) {
        val base = if (spot.kind == SpotKind.WINDOW) WINDOW_COLOR else COVER_COLOR
        val maxAlpha = AndroidColor.alpha(base)
        val red = AndroidColor.red(base)
        val green = AndroidColor.green(base)
        val blue = AndroidColor.blue(base)
        val alpha = spot.drawAlpha()
        // כמה פיקסלים של העל-מסכה מכסה כל פיקסל ציור
        val ratio = REFINE_SCALE / spot.drawScale
        for (ly in 0 until spot.drawHeight) {
            for (lx in 0 until spot.drawWidth) {
                val value = alpha[ly * spot.drawWidth + lx].toInt() and 0xFF
                if (value == 0) continue
                val color = AndroidColor.argb(maxAlpha * value / 255, red, green, blue)
                val x0 = (spot.drawX + lx) * ratio
                val y0 = (spot.drawY + ly) * ratio
                for (dy in 0 until ratio) {
                    for (dx in 0 until ratio) {
                        val x = x0 + dx
                        val y = y0 + dy
                        if (x < width && y < height) pixels[y * width + x] = color
                    }
                }
            }
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun buildSpotsLegend(spots: List<HidingSpot>): List<String> {
    val covers = spots.count { it.kind == SpotKind.COVER }
    val windows = spots.count { it.kind == SpotKind.WINDOW }
    val lines = ArrayList<String>()
    lines.add("hiding spots: $covers cover (orange)")
    lines.add("windows: $windows (blue)")
    if (SceneSession.samMasks.isNotEmpty()) lines.add("auto objects (SAM): ${SceneSession.samMasks.size}")
    if (SceneSession.userObjects.isNotEmpty()) lines.add("marked by you: ${SceneSession.userObjects.size}")
    for (spot in spots.take(8)) {
        val depthText = if (spot.depth.isNaN()) "?" else String.format(Locale.US, "%.0f m", spot.depth)
        lines.add("${Ade20kClasses.name(spot.classIndex)}  $depthText")
    }
    return lines
}

/** מסך הסריקה לשחקן: תמונת טעינה, פס התקדמות וסטטוס. אין כפתורים: הסריקה ממשיכה למשחק לבד */
@Composable
private fun AutoScanOverlay(progress: Float, status: String, failed: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.arsniper.ui.theme.GameColors.Navy900),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = androidx.compose.ui.res.painterResource(com.arsniper.R.drawable.loading_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .fillMaxWidth(0.42f)
        ) {
            if (!failed) {
                com.arsniper.ui.components.LoadingBar(progress = progress)
                Box(Modifier.size(8.dp))
            }
            com.arsniper.ui.components.OutlinedText(
                if (failed) "SCAN FAILED" else "LOADING...",
                fontSize = 22.sp,
                outlineWidth = 8f
            )
            com.arsniper.ui.components.OutlinedText(
                status,
                fontSize = 12.sp,
                color = Color.White,
                outlineWidth = 5f,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

package com.arsniper.ui.scene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PhotoRepository
import com.arsniper.game.spots.GuideImage
import com.arsniper.game.spots.HidingSpot
import com.arsniper.game.spots.SpotKind
import com.arsniper.ml.Ade20kClasses
import com.arsniper.ml.ModelStore
import com.arsniper.ml.SceneAnalysis
import com.arsniper.ml.SceneSegmenter
import com.arsniper.ml.SceneSession
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class ViewMode { Segmentation, Depth, Spots, Photo }

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

private class SceneView(
    val photo: ImageBitmap,
    val segmentation: ImageBitmap,
    val depth: ImageBitmap,
    val legend: List<String>,
    val spotsOverlay: ImageBitmap,
    val spotsLegend: List<String>,
    val seconds: Float,
    val tileCount: Int
)

/**
 * מסך בדיקת היתכנות: מוריד (פעם אחת) את מודל הסצנה, מריץ אותו על התמונה,
 * ומציג מה המערכת "רואה": סוגי עצמים ועומק.
 */
@Composable
fun SceneScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var statusText by remember { mutableStateOf("Preparing...") }
    var busy by remember { mutableStateOf(true) }
    var sceneView by remember { mutableStateOf<SceneView?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var viewMode by remember { mutableStateOf(ViewMode.Segmentation) }

    LaunchedEffect(Unit) {
        SceneSession.clear()
        try {
            statusText = "Checking scene model..."
            val modelFile = ModelStore.ensureTipsModel(context) { downloaded, total ->
                val megabytes = downloaded / 1_000_000L
                statusText = if (total > 0L) {
                    "Downloading scene model (one time, ~318 MB): " +
                        "$megabytes / ${total / 1_000_000L} MB (${downloaded * 100L / total}%)"
                } else {
                    "Downloading scene model (one time): $megabytes MB"
                }
            }

            statusText = "Preparing scene analysis (first run takes longer)..."
            val outcome = withContext(Dispatchers.Default) {
                runCatching {
                    analyzeScene(context, modelFile) { done, total ->
                        statusText = "Analyzing scene: part $done of $total"
                    }
                }
            }
            outcome
                .onSuccess { sceneView = it }
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14))
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
                ViewMode.Spots -> Image(
                    bitmap = current.spotsOverlay,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                ViewMode.Photo -> Unit
            }

            val legendLines = when (viewMode) {
                ViewMode.Segmentation -> current.legend
                ViewMode.Spots -> current.spotsLegend
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

        if (busy) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            ModeButton("SEG", ViewMode.Segmentation, viewMode, current != null) { viewMode = it }
            ModeButton("DEPTH", ViewMode.Depth, viewMode, current != null) { viewMode = it }
            ModeButton("SPOTS", ViewMode.Spots, viewMode, current != null) { viewMode = it }
            ModeButton("PHOTO", ViewMode.Photo, viewMode, current != null) { viewMode = it }
            Button(onClick = onContinue) { Text("CONTINUE") }
        }
    }
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

private fun analyzeScene(
    context: Context,
    modelFile: File,
    onProgress: (done: Int, total: Int) -> Unit
): SceneView {
    val startNanos = System.nanoTime()
    val photo = PhotoRepository.load(context) ?: error("No photo found")

    val segmenter = SceneSegmenter(modelFile)
    val analysis = try {
        segmenter.analyze(photo, onProgress)
    } finally {
        segmenter.close()
    }

    // שומרים את הניתוח (ומוצאים מחבואים ומלטשים את הקצוות שלהם) בשביל מסך המשחק
    SceneSession.set(analysis, buildGuide(photo, analysis.width, analysis.height))
    val spots = SceneSession.spots
    val seconds = (System.nanoTime() - startNanos) / 1_000_000_000f

    return SceneView(
        photo = photo.asImageBitmap(),
        segmentation = buildSegmentationOverlay(analysis).asImageBitmap(),
        depth = buildDepthImage(analysis).asImageBitmap(),
        legend = buildLegend(analysis),
        spotsOverlay = buildSpotsOverlay(analysis, spots).asImageBitmap(),
        spotsLegend = buildSpotsLegend(spots),
        seconds = seconds,
        tileCount = analysis.tileCount
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
 * כתום = מאחורי עצם, תכלת = חלון או דלת. שקיפות הקצה משקפת את האלפא הרכה.
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
    lines.add("windows/doors: $windows (blue)")
    for (spot in spots.take(8)) {
        val depthText = if (spot.depth.isNaN()) "?" else String.format(Locale.US, "%.0f m", spot.depth)
        lines.add("${Ade20kClasses.name(spot.classIndex)}  $depthText")
    }
    return lines
}
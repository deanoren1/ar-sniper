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
import com.arsniper.ml.Ade20kClasses
import com.arsniper.ml.ModelStore
import com.arsniper.ml.SceneAnalysis
import com.arsniper.ml.SceneSegmenter
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class ViewMode { Segmentation, Depth, Photo }

private const val OVERLAY_WIDTH = 640

/** צבע קבוע לכל קטגוריה (חצי-שקוף) */
private val CLASS_COLORS = IntArray(150) { index ->
    AndroidColor.HSVToColor(150, floatArrayOf((index * 47 % 360).toFloat(), 0.75f, 0.95f))
}

private class SceneView(
    val photo: ImageBitmap,
    val segmentation: ImageBitmap,
    val depth: ImageBitmap,
    val legend: List<String>,
    val seconds: Float
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

            statusText = "Analyzing scene (first run takes longer)..."
            val outcome = withContext(Dispatchers.Default) {
                runCatching { analyzeScene(context, modelFile) }
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
                ViewMode.Photo -> Unit
            }

            if (viewMode == ViewMode.Segmentation) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(8.dp)
                ) {
                    for (line in current.legend) {
                        Text(text = line, color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }

        val statusLine = when {
            errorText != null -> "Scene analysis failed: $errorText"
            current != null ->
                "Scene analyzed in ${String.format(Locale.US, "%.1f", current.seconds)}s (GPU)"
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

private fun analyzeScene(context: Context, modelFile: File): SceneView {
    val startNanos = System.nanoTime()
    val photo = PhotoRepository.load(context) ?: error("No photo found")

    val segmenter = SceneSegmenter(modelFile)
    val analysis: SceneAnalysis
    try {
        analysis = segmenter.analyze(photo)
    } finally {
        segmenter.close()
    }
    val seconds = (System.nanoTime() - startNanos) / 1_000_000_000f

    val outWidth = OVERLAY_WIDTH
    val outHeight = (OVERLAY_WIDTH.toFloat() * photo.height / photo.width)
        .roundToInt()
        .coerceAtLeast(1)

    val legend = analysis.topClasses(8).map { (index, fraction) ->
        "${Ade20kClasses.name(index)}  ${(fraction * 100).roundToInt()}%"
    }

    return SceneView(
        photo = photo.asImageBitmap(),
        segmentation = buildSegmentationOverlay(analysis, outWidth, outHeight).asImageBitmap(),
        depth = buildDepthImage(analysis, outWidth, outHeight).asImageBitmap(),
        legend = legend,
        seconds = seconds
    )
}

/** תמונה שקופה בגודל ובפרופורציות התמונה, עם צבע לכל סוג עצם */
private fun buildSegmentationOverlay(analysis: SceneAnalysis, width: Int, height: Int): Bitmap {
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val ly = y * analysis.labelHeight / height
        for (x in 0 until width) {
            val lx = x * analysis.labelWidth / width
            val label = analysis.labelIndex(ly * analysis.labelWidth + lx)
            pixels[y * width + x] = CLASS_COLORS[label.coerceIn(0, CLASS_COLORS.size - 1)]
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

/** מפת עומק באפור: בהיר = קרוב, כהה = רחוק */
private fun buildDepthImage(analysis: SceneAnalysis, width: Int, height: Int): Bitmap {
    var minDepth = Float.MAX_VALUE
    var maxDepth = -Float.MAX_VALUE
    for (value in analysis.depth) {
        if (value.isNaN() || value.isInfinite()) continue
        if (value < minDepth) minDepth = value
        if (value > maxDepth) maxDepth = value
    }
    val range = (maxDepth - minDepth).coerceAtLeast(1e-6f)

    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val dy = y * analysis.depthHeight / height
        for (x in 0 until width) {
            val dx = x * analysis.depthWidth / width
            val value = analysis.depth[dy * analysis.depthWidth + dx]
            val t = ((value - minDepth) / range).coerceIn(0f, 1f)
            val gray = ((1f - t) * 255f).roundToInt()
            pixels[y * width + x] = AndroidColor.argb(255, gray, gray, gray)
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

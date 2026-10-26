package com.arsniper.ui.menu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PhotoRepository
import com.arsniper.ml.Ade20kClasses
import com.arsniper.ml.GeminiScan
import com.arsniper.ml.SceneSession
import com.arsniper.ui.components.GameButton
import com.arsniper.ui.components.NavyPanel
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.ScreenHeader
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.ResponsiveBox
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Marked(val box: GeminiScan.Box, val isNew: Boolean)

/**
 * מעבדת סריקה (SCAN LAB): משווה בין המחבואים שהמערכת שלנו מצאה (ירוק) לבין מה ש-Gemini מצא (סגול).
 * תיבה של Gemini שאין לידה מחבוא שלנו מסומנת בצהוב (NEW): אלה התוספות האפשריות.
 * המחבואים שלנו קיימים רק אחרי שעברו במסך הסצנה (PLAY, תמונה, המשך) באותה הפעלה של האפליקציה.
 */
@Composable
fun ScanLabScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("artlab", Context.MODE_PRIVATE) }
    val key = remember { prefs.getString("key", "") ?: "" }
    val photo = remember { PhotoRepository.load(context) }
    val ours = remember { SceneSession.spots }
    var marked by remember { mutableStateOf<List<Marked>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var raw by remember { mutableStateOf("") }

    fun scan() {
        val source = photo
        when {
            source == null -> message = "No photo found. Pick a photo first."
            key.isBlank() -> message = "No API key. Enter it in ART LAB first."
            else -> {
                busy = true
                message = null
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) { GeminiScan.scan(key.trim(), GeminiScan.DEFAULT_MODEL, source) }
                    busy = false
                    when (outcome) {
                        is GeminiScan.Result.Boxes -> {
                            raw = outcome.raw
                            marked = outcome.boxes.map { Marked(it, isNew(it, ours)) }
                            if (outcome.boxes.isEmpty()) message = "Gemini returned no boxes."
                        }
                        is GeminiScan.Result.Error -> message = outcome.message
                    }
                }
            }
        }
    }

    ResponsiveBox(modifier = modifier.background(GameColors.Navy900)) { ui ->
        Column(modifier = Modifier.fillMaxSize().padding(ui.dp(12f))) {
            ScreenHeader(title = "SCAN LAB (TEST)", onBack = onBack)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    GameButton(
                        text = if (busy) "SCANNING..." else "SCAN WITH GEMINI",
                        onClick = { scan() },
                        enabled = !busy,
                        height = 48.dp,
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedText("GREEN: our detection (${ours.size})", fontSize = 13.sp, color = Color(0xFF69F0AE), outline = null)
                    OutlinedText("PURPLE: Gemini, matches ours", fontSize = 13.sp, color = Color(0xFFD08CFF), outline = null)
                    OutlinedText("YELLOW: Gemini only (NEW)", fontSize = 13.sp, color = GameColors.Yellow, outline = null)
                    if (marked.isNotEmpty()) {
                        val newCount = marked.count { it.isNew }
                        OutlinedText("Gemini: ${marked.size} objects, $newCount new", fontSize = 14.sp, outline = null)
                    }
                    if (ours.isEmpty()) {
                        OutlinedText(
                            "Our detection is empty: run PLAY > photo > continue once, then come back here (same app session).",
                            fontSize = 12.sp, color = GameColors.Ice, outline = null
                        )
                    }
                    message?.let { OutlinedText(it, fontSize = 12.sp, color = Color(0xFFFF8A80), outline = null) }
                }
                NavyPanel(modifier = Modifier.weight(2.2f).fillMaxSize()) {
                    val bmp = photo
                    if (bmp != null) {
                        val image = remember(bmp) { bmp.asImageBitmap() }
                        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                            val scale = min(size.width / bmp.width, size.height / bmp.height)
                            val w = bmp.width * scale
                            val h = bmp.height * scale
                            val ox = (size.width - w) / 2f
                            val oy = (size.height - h) / 2f
                            drawImage(
                                image,
                                dstOffset = IntOffset(ox.toInt(), oy.toInt()),
                                dstSize = IntSize(w.toInt(), h.toInt())
                            )
                            val paint = Paint().apply { textSize = 28f; isFakeBoldText = true }
                            fun frame(x0: Float, y0: Float, x1: Float, y1: Float, color: Color, label: String) {
                                drawRect(color, Offset(ox + x0 * w, oy + y0 * h), Size((x1 - x0) * w, (y1 - y0) * h), style = Stroke(4f))
                                paint.color = color.toArgb()
                                drawContext.canvas.nativeCanvas.drawText(label, ox + x0 * w + 6f, oy + y0 * h + 28f, paint)
                            }
                            for (spot in ours) {
                                val name = Ade20kClasses.NAMES.getOrNull(spot.classIndex) ?: "?"
                                frame(spot.minX, spot.minY, spot.maxX, spot.maxY, Color(0xFF69F0AE), name)
                            }
                            for (m in marked) {
                                frame(
                                    m.box.minX, m.box.minY, m.box.maxX, m.box.maxY,
                                    if (m.isNew) GameColors.Yellow else Color(0xFFD08CFF), m.box.label
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)

/** תיבה חדשה = אין מחבוא שלנו שחופף לה מספיק */
private fun isNew(box: GeminiScan.Box, ours: List<com.arsniper.game.spots.HidingSpot>): Boolean {
    for (spot in ours) {
        val ix = max(0f, min(box.maxX, spot.maxX) - max(box.minX, spot.minX))
        val iy = max(0f, min(box.maxY, spot.maxY) - max(box.minY, spot.minY))
        val inter = ix * iy
        val union = (box.maxX - box.minX) * (box.maxY - box.minY) +
            (spot.maxX - spot.minX) * (spot.maxY - spot.minY) - inter
        if (union > 0f && inter / union > 0.25f) return false
    }
    return true
}

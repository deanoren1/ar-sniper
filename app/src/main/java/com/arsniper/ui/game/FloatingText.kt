package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** כמה זמן ניקוד צף נראה, בשניות */
const val FLOATER_SECONDS = 1.0f

/** ניקוד צף: מספר שעולה ונעלם מנקודת ההצלחה. x ו-y בקואורדינטות עולם (0..1) */
class Floater(
    val x: Float,
    val y: Float,
    val text: String,
    val bornNanos: Long,
    /** true = הצלחה גדולה (גדול וצהוב), false = קטנה (לבן) */
    val big: Boolean
)

/** שכבת הניקוד הצף, בתוך שכבת העולם (עוברת זום ותזוזה יחד עם התמונה) */
@Composable
fun FloatingTextLayer(floaters: List<Floater>, nowNanos: Long, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        for (f in floaters) {
            val age = (nowNanos - f.bornNanos) / 1e9f
            if (age < 0f || age > FLOATER_SECONDS) continue
            val p = age / FLOATER_SECONDS
            val cx = f.x * size.width
            val cy = (f.y - 0.14f * p) * size.height
            val textSize = size.height * (if (f.big) 0.075f else 0.05f)
            val alpha = (255 * (1f - p * p)).toInt().coerceIn(0, 255)
            drawIntoCanvas { canvas ->
                val fill = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    this.textSize = textSize
                    color = if (f.big) android.graphics.Color.rgb(255, 235, 59) else android.graphics.Color.WHITE
                    this.alpha = alpha
                }
                val outline = android.graphics.Paint(fill).apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = textSize * 0.16f
                    color = android.graphics.Color.BLACK
                    this.alpha = alpha
                }
                canvas.nativeCanvas.drawText(f.text, cx, cy, outline)
                canvas.nativeCanvas.drawText(f.text, cx, cy, fill)
            }
        }
    }
}

/** תג הקומבו: מופיע מרצף של 2 ומעלה, וצבעו מתחמם ככל שהמכפיל גדל */
@Composable
fun ComboBadge(count: Int, multiplier: Int, modifier: Modifier = Modifier) {
    if (count < 2) return
    val color = when (multiplier) {
        1 -> Color(0xFFFFFFFF)
        2 -> Color(0xFFFFEB3B)
        3 -> Color(0xFFFFA726)
        4 -> Color(0xFFFF7043)
        else -> Color(0xFFFF1744)
    }
    Text(
        text = "COMBO $count  x$multiplier",
        color = color,
        fontSize = (16 + multiplier * 2).sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

/** כמה זמן "פוף" ההעברה נראה, בשניות */
const val POOF_SECONDS = 0.6f

/** אפקט העברת מקום: טבעת וניצוצות שמתרחבים ונעלמים. x ו-y בקואורדינטות עולם */
class Poof(val x: Float, val y: Float, val bornNanos: Long)

@Composable
fun PoofLayer(poofs: List<Poof>, nowNanos: Long, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        for (poof in poofs) {
            val age = (nowNanos - poof.bornNanos) / 1e9f
            if (age < 0f || age > POOF_SECONDS) continue
            val p = age / POOF_SECONDS
            val cx = poof.x * size.width
            val cy = poof.y * size.height
            val radius = size.height * (0.03f + 0.11f * p)
            val alpha = (1f - p) * 0.9f
            val color = Color(0xFF80DEEA)
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.height * 0.012f)
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.5f),
                radius = radius * 0.6f,
                center = androidx.compose.ui.geometry.Offset(cx, cy)
            )
            for (i in 0 until 8) {
                val a = (2.0 * Math.PI * i / 8.0).toFloat()
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = size.height * 0.008f,
                    center = androidx.compose.ui.geometry.Offset(
                        cx + kotlin.math.cos(a) * radius * 1.3f,
                        cy + kotlin.math.sin(a) * radius * 1.3f
                    )
                )
            }
        }
    }
}

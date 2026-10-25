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

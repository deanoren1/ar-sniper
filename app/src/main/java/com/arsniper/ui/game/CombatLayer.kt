package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.arsniper.game.engine.Shot
import com.arsniper.game.engine.ShotKind
import com.arsniper.game.engine.TelegraphView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * שכבת הקרב (בתוך שכבת העולם, ולכן עוברת זום ותזוזה יחד עם התמונה):
 * הכנה לירייה (הבהוב אדום סביב המפלצת ו-"!"), וקליעים שגדלים בדרך אל השחקן עם טבעת אזהרה שמתכווצת.
 * time = שעון הלחימה בשניות (משתנה בכל פריים ומפעיל את האנימציה).
 * shotRadius מחשב את הרדיוס הנוכחי של קליע (ביחידות גובה מסך), כדי שהציור יתאים בדיוק לאזור הלחיצה.
 */
@Composable
fun CombatLayer(
    shots: List<Shot>,
    telegraphs: List<TelegraphView>,
    time: Float,
    shotRadius: (Shot) -> Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        for (t in telegraphs) drawTelegraph(t, time)
        for (s in shots) drawShot(s, time, shotRadius(s))
    }
}

private fun DrawScope.drawTelegraph(t: TelegraphView, time: Float) {
    val cx = t.x * size.width
    val cy = t.y * size.height
    val r = t.radius * size.height
    val pulse = 0.5f + 0.5f * sin(time * 18f)
    val red = Color(0xFFFF1744)
    // הילה אדומה שמתחזקת, וטבעת שמתכווצת אל המפלצת
    drawCircle(red.copy(alpha = 0.12f + 0.25f * t.progress * pulse), r * 1.25f, Offset(cx, cy))
    drawCircle(
        color = red.copy(alpha = 0.85f),
        radius = r * (1.9f - 0.8f * t.progress),
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.08f + 3f)
    )
    // סימן קריאה מעל הראש
    val textSize = r * 1.1f
    drawIntoCanvas { canvas ->
        val fill = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            this.textSize = textSize
            color = android.graphics.Color.WHITE
        }
        val stroke = android.graphics.Paint(fill).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = textSize * 0.2f
            color = android.graphics.Color.rgb(213, 0, 0)
        }
        val y = cy - r * 1.35f
        canvas.nativeCanvas.drawText("!", cx, y, stroke)
        canvas.nativeCanvas.drawText("!", cx, y, fill)
    }
}

private fun DrawScope.drawShot(s: Shot, time: Float, radiusUnits: Float) {
    val cx = s.x * size.width
    val cy = s.y * size.height
    val r = radiusUnits * size.height
    val full = s.baseRadius * size.height
    val p = ((time - s.born) / s.flight).coerceIn(0f, 1f)
    val center = Offset(cx, cy)

    when (s.kind) {
        ShotKind.Orb -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color(0xFF40C4FF), Color(0xFF7C4DFF), Color(0x007C4DFF)),
                    center = center,
                    radius = r * 1.3f
                ),
                radius = r * 1.3f,
                center = center
            )
        }

        ShotKind.Bomb -> {
            drawCircle(Color(0x55FF5722), r * 1.3f, center)
            drawCircle(Color(0xFF263238), r, center)
            drawCircle(Color(0xFF546E7A), r * 0.35f, Offset(cx - r * 0.3f, cy - r * 0.3f))
            // פתיל עם ניצוץ
            drawCircle(Color(0xFFFFC107), r * 0.2f * (0.8f + 0.4f * sin(time * 25f)), Offset(cx + r * 0.55f, cy - r * 0.95f))
        }

        ShotKind.Gas -> {
            val green = Color(0xFF76FF03)
            for (i in 0 until 3) {
                val a = (2.0 * PI * i / 3.0).toFloat() + time * 2f
                val o = Offset(cx + cos(a) * r * 0.35f, cy + sin(a) * r * 0.35f)
                drawCircle(green.copy(alpha = 0.45f), r * 0.75f, o)
            }
            drawCircle(Color(0xFFCCFF90).copy(alpha = 0.6f), r * 0.45f, center)
        }

        ShotKind.Rock -> {
            drawCircle(Color(0xFF5D4037), r, center)
            drawCircle(Color(0xFF8D6E63), r * 0.55f, Offset(cx - r * 0.25f, cy - r * 0.25f))
            drawCircle(Color(0xFF3E2723), r * 0.25f, Offset(cx + r * 0.3f, cy + r * 0.25f))
            // סדק אחרי פגיעה ראשונה
            if (s.hp < s.maxHp) {
                drawLine(Color.White, Offset(cx - r * 0.5f, cy - r * 0.6f), Offset(cx + r * 0.2f, cy + r * 0.7f), strokeWidth = r * 0.08f + 2f)
            }
        }

        ShotKind.Spike -> {
            val spikes = 8
            for (i in 0 until spikes) {
                val a = (2.0 * PI * i / spikes).toFloat() + time * 3f
                drawLine(
                    color = Color(0xFFFF5252),
                    start = center,
                    end = Offset(cx + cos(a) * r * 1.3f, cy + sin(a) * r * 1.3f),
                    strokeWidth = r * 0.22f + 2f
                )
            }
            drawCircle(Color(0xFFFFEBEE), r * 0.55f, center)
            drawCircle(Color(0xFFD50000), r * 0.3f, center)
        }
    }

    // ירייה מטעה (המפלצת מבולבלת): מוחשכת באפור, וגם לא פוגעת כשמגיעה
    if (s.harmless) {
        drawCircle(Color(0xCC7F8A93), r * 1.45f, center)
    }

    // טבעת אזהרה: מתחילה גדולה ומתכווצת עד שהיא פוגשת את הקליע, ברגע הפגיעה. בסוף מהבהבת באדום
    val ringRadius = full * (1f + 1.4f * (1f - p))
    val urgent = p > 0.7f && (time * 10f).toInt() % 2 == 0
    drawCircle(
        color = if (urgent) Color(0xFFFF1744) else Color.White.copy(alpha = 0.9f),
        radius = ringRadius,
        center = center,
        style = Stroke(width = full * 0.07f + 3f)
    )
}

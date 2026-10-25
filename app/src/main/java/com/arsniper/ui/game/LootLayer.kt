package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.arsniper.game.engine.Loot
import com.arsniper.game.engine.LootDrop
import com.arsniper.game.engine.LootKind
import kotlin.math.PI
import kotlin.math.sin

/**
 * הפרסים על הקרקע, בתוך שכבת העולם: לב אדום או תא אנרגיה כחול, מרחפים ונעים למעלה ולמטה בתוך הילה.
 * טבעת סביבם מתכווצת עם הזמן, ובשתי השניות האחרונות הם מהבהבים לפני שנעלמים.
 */
@Composable
fun LootLayer(loot: Loot, nowNanos: Long, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE") val tick = nowNanos
        for (drop in loot.drops) drawDrop(drop, loot.ageOf(drop), loot.time)
    }
}

private fun DrawScope.drawDrop(drop: LootDrop, age: Float, time: Float) {
    val left = Loot.LIFETIME_SECONDS - age
    // מהבהב בסוף
    if (left < 2f && ((left * 6f).toInt() % 2 == 0)) return
    val r = size.height * 0.04f
    val bob = sin(time * 4f + drop.id) * r * 0.25f
    val cx = drop.x * size.width
    val cy = (drop.y - Loot.RISE) * size.height + bob
    val center = Offset(cx, cy)
    val glow = if (drop.kind == LootKind.Heart) Color(0xFFFF1744) else Color(0xFF40C4FF)

    // הילה
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glow.copy(alpha = 0.55f), glow.copy(alpha = 0f)),
            center = center,
            radius = r * 2.4f
        ),
        radius = r * 2.4f,
        center = center
    )
    // טבעת זמן שנשארה
    drawArc(
        color = Color.White.copy(alpha = 0.8f),
        startAngle = -90f,
        sweepAngle = 360f * (left / Loot.LIFETIME_SECONDS).coerceIn(0f, 1f),
        useCenter = false,
        topLeft = Offset(cx - r * 1.5f, cy - r * 1.5f),
        size = Size(r * 3f, r * 3f),
        style = Stroke(width = r * 0.14f + 1.5f)
    )

    if (drop.kind == LootKind.Heart) {
        val w = r * 2f
        val h = r * 1.8f
        val path = Path().apply {
            moveTo(cx, cy + 0.5f * h)
            cubicTo(cx - 0.6f * w, cy + 0.05f * h, cx - 0.5f * w, cy - 0.55f * h, cx, cy - 0.2f * h)
            cubicTo(cx + 0.5f * w, cy - 0.55f * h, cx + 0.6f * w, cy + 0.05f * h, cx, cy + 0.5f * h)
            close()
        }
        drawPath(path, Color(0xFFFF1744))
        drawPath(path, Color.White.copy(alpha = 0.9f), style = Stroke(width = r * 0.12f + 1f))
    } else {
        // תא אנרגיה: קפסולה כחולה זוהרת עם פס בהיר
        val w = r * 0.95f
        val h = r * 1.7f
        drawRoundRect(Color(0xFF0D47A1), Offset(cx - w / 2f, cy - h / 2f), Size(w, h), CornerRadius(w / 2f))
        drawRoundRect(Color(0xFF40C4FF), Offset(cx - w * 0.34f, cy - h * 0.42f), Size(w * 0.68f, h * 0.84f), CornerRadius(w * 0.34f))
        drawRoundRect(Color.White.copy(alpha = 0.8f), Offset(cx - w * 0.12f, cy - h * 0.34f), Size(w * 0.16f, h * 0.5f), CornerRadius(w * 0.08f))
        drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(cx - w / 2f, cy - h / 2f), Size(w, h), CornerRadius(w / 2f), style = Stroke(width = r * 0.1f + 1f))
    }
}

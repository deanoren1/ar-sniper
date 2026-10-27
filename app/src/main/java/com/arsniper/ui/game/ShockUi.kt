package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.imageResource
import com.arsniper.ui.components.OutlinedText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * כפתור ההלם: טבעת שמתמלאת לפי המד, וכשהיא מלאה הכפתור זוהר ופועם. לחיצה רק כשמלא.
 * nowNanos מגרה ציור מחדש (פעימה).
 */
@Composable
fun ShockButton(
    fraction: Float,
    ready: Boolean,
    nowNanos: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cores = listOf(
        androidx.compose.ui.graphics.ImageBitmap.imageResource(com.arsniper.R.drawable.hud_core_0),
        androidx.compose.ui.graphics.ImageBitmap.imageResource(com.arsniper.R.drawable.hud_core_1),
        androidx.compose.ui.graphics.ImageBitmap.imageResource(com.arsniper.R.drawable.hud_core_2),
        androidx.compose.ui.graphics.ImageBitmap.imageResource(com.arsniper.R.drawable.hud_core_3),
        androidx.compose.ui.graphics.ImageBitmap.imageResource(com.arsniper.R.drawable.hud_core_4)
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 88.dp, height = 88.dp)
            .clickable(enabled = ready, onClick = onClick)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pulse = if (ready) 0.5f + 0.5f * sin(nowNanos / 1e9f * 7f) else 0f
            val center = Offset(size.width / 2f, size.height / 2f)
            // מילוי הליבה בחמישה שלבים, עם מעבר חלק ביניהם. מלא = השלב האחרון
            val f = if (ready) 1f else fraction.coerceIn(0f, 1f)
            val pos = f * 4f
            val i = pos.toInt().coerceIn(0, 4)
            val t = pos - i
            val scale = if (ready) 1f + 0.05f * pulse else 1f
            val side = (size.width * 0.96f * scale).toInt()
            val dst = androidx.compose.ui.unit.IntSize(side, side)
            val off = androidx.compose.ui.unit.IntOffset((center.x - side / 2f).toInt(), (center.y - side / 2f).toInt())
            if (ready) {
                // זוהר פועם כשהליבה מלאה ומוכנה ללחיצה
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color(0xFF40C4FF).copy(alpha = 0.55f + 0.3f * pulse), Color(0x0040C4FF)),
                        center = center,
                        radius = size.minDimension * (0.72f + 0.1f * pulse)
                    ),
                    radius = size.minDimension * (0.72f + 0.1f * pulse),
                    center = center
                )
            }
            drawImage(cores[i], dstOffset = off, dstSize = dst)
            if (i < 4 && t > 0.01f) {
                drawImage(cores[i + 1], dstOffset = off, dstSize = dst, alpha = t)
            }
        }
    }
}

private const val SHOCK_SECONDS = 0.8f

/** האפקט של ההלם: הבזק לבן שדועך וטבעת שמתפשטת מהמרכז עד פינות המסך. startNanos = 0 אין אפקט */
@Composable
fun ShockwaveEffect(startNanos: Long, nowNanos: Long, modifier: Modifier = Modifier) {
    if (startNanos == 0L) return
    val t = (nowNanos - startNanos) / 1e9f / SHOCK_SECONDS
    if (t < 0f || t > 1f) return
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(Color.White.copy(alpha = (0.85f * (1f - t * 2.2f)).coerceIn(0f, 0.85f)))
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        val maxR = hypot(size.width, size.height) * 0.6f
        val r = maxR * t
        drawCircle(
            Color(0xFFFFF59D).copy(alpha = (1f - t) * 0.9f), radius = r, center = center,
            style = Stroke(width = 46f * (1f - t) + 6f)
        )
        drawCircle(
            Color(0xFF40C4FF).copy(alpha = (1f - t) * 0.6f), radius = r * 0.82f, center = center,
            style = Stroke(width = 24f * (1f - t) + 3f)
        )
    }
}

package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
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
    val empty = androidx.compose.ui.graphics.ImageBitmap.imageResource(com.arsniper.R.drawable.hud_ring_empty)
    val full = androidx.compose.ui.graphics.ImageBitmap.imageResource(com.arsniper.R.drawable.hud_ring_full)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 84.dp, height = 77.dp)
            .clickable(enabled = ready, onClick = onClick)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pulse = if (ready) 0.5f + 0.5f * sin(nowNanos / 1e9f * 9f) else 0f
            val dst = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
            val center = Offset(size.width * (114f / 230f), size.height * (112f / 210f))
            if (ready) {
                drawCircle(Color(0xFFFFEA00).copy(alpha = 0.18f + 0.3f * pulse), radius = size.minDimension * 0.62f, center = center)
            }
            drawImage(empty, dstSize = dst)
            val f = if (ready) 1f else fraction.coerceIn(0f, 1f)
            if (f > 0.001f) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    if (f >= 0.999f) {
                        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                    } else {
                        moveTo(center.x, center.y)
                        arcTo(
                            androidx.compose.ui.geometry.Rect(center.x - size.width, center.y - size.width, center.x + size.width, center.y + size.width),
                            -90f, -360f * f, false
                        )
                        close()
                    }
                }
                androidx.compose.ui.graphics.drawscope.clipPath(path) {
                    drawImage(full, dstSize = dst)
                }
            }
        }
        if (!ready) {
            OutlinedText("${(fraction * 100).toInt()}%", fontSize = 11.sp, outline = null, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
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

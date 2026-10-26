package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.arsniper.game.progress.ArenaStyle
import com.arsniper.game.progress.ParticleKind
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * שכבת הסגנון של הארנה: גוון צבע, חשכה בקצוות וחלקיקים נעים מעל התמונה.
 * nowNanos משנה את הציור בכל פריים. particleScale מקטין את מספר החלקיקים בתצוגות מקדימות.
 */
@Composable
fun ArenaStyleLayer(
    style: ArenaStyle,
    nowNanos: Long,
    modifier: Modifier = Modifier,
    particleScale: Float = 1f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = nowNanos / 1e9f
        drawRect(Color(style.tint.toInt()).copy(alpha = style.tintAlpha))
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = style.vignette)),
                center = center,
                radius = max(size.width, size.height) * 0.75f
            )
        )
        drawParticles(style, t, particleScale)
    }
}

private fun hash(i: Int, salt: Int): Float {
    val v = sin((i * 12.9898f) + salt * 78.233f) * 43758.547f
    return v - floor(v)
}

private fun frac(v: Float): Float = v - floor(v)

private fun DrawScope.drawParticles(style: ArenaStyle, t: Float, scale: Float) {
    val color = Color(style.particleColor.toInt())
    val count = (style.particleCount * scale).toInt().coerceAtLeast(6)
    val unit = size.minDimension * 0.004f
    for (i in 0 until count) {
        val r1 = hash(i, 1)
        val r2 = hash(i, 2)
        val r3 = hash(i, 3)
        when (style.particle) {
            ParticleKind.Snow -> {
                val y = frac(r2 + t * (0.05f + 0.09f * r3))
                val x = frac(r1 + sin(t * 0.6f + i) * 0.015f)
                drawCircle(color.copy(alpha = 0.85f), unit * (1f + 2.2f * r3), Offset(x * size.width, y * size.height))
            }
            ParticleKind.Rain -> {
                val speed = 0.55f + 0.4f * r3
                val y = frac(r2 + t * speed)
                val x = frac(r1 + y * 0.05f)
                val start = Offset(x * size.width, y * size.height)
                val end = Offset(start.x - size.height * 0.012f, start.y + size.height * 0.05f)
                drawLine(color.copy(alpha = 0.6f), start, end, strokeWidth = unit * (0.8f + r3), cap = StrokeCap.Round)
            }
            ParticleKind.Rise -> {
                val y = 1f - frac(r2 + t * (0.03f + 0.06f * r3))
                val x = frac(r1 + sin(t * 1.1f + i * 3f) * 0.02f)
                val twinkle = 0.35f + 0.65f * abs(sin(t * 2.4f + i))
                drawCircle(color.copy(alpha = twinkle), unit * (0.9f + 1.8f * r3), Offset(x * size.width, y * size.height))
            }
            ParticleKind.Drift -> {
                val x = frac(r1 + t * (0.02f + 0.05f * r3))
                val y = r2 + sin(t * 0.7f + i) * 0.02f
                drawCircle(color.copy(alpha = 0.35f + 0.3f * r3), unit * (0.8f + 1.6f * r3), Offset(x * size.width, y * size.height))
            }
        }
    }
}

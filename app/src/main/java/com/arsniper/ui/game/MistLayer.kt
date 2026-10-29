package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.floor
import kotlin.math.sin

/**
 * ערפל שצף מעל המפלצות ומסתיר אותן חלקית (אווירה, וגם קצת סיכון): כתמי ערפל רכים שנעים לאט לרוחב המסך,
 * בעיקר בחצי התחתון. מצויר מעל שכבת המפלצות ומתחת למסגרת.
 */
@Composable
fun MistLayer(nowNanos: Long, color: Color = Color(0xFFDCEBFF), strength: Float = 1f, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = nowNanos / 1e9f
        val blobs = 7
        for (i in 0 until blobs) {
            val seed = i * 17.31f
            val speed = 0.012f + 0.010f * frac(sin(seed) * 43758.55f)
            val dir = if (i % 2 == 0) 1f else -1f
            val x = frac(frac(sin(seed + 1f) * 9731.7f) + dir * t * speed)
            val y = 0.50f + 0.34f * frac(sin(seed + 2f) * 5821.3f) + 0.03f * sin(t * 0.25f + seed)
            val radius = size.width * (0.16f + 0.10f * frac(sin(seed + 3f) * 1234.5f))
            val alpha = (0.20f + 0.10f * sin(t * 0.3f + seed)) * strength
            val center = Offset(x * (size.width + radius * 2f) - radius, y * size.height)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
    }
}

private fun frac(v: Float): Float = v - floor(v)

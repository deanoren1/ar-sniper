package com.arsniper.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arsniper.ui.theme.GameColors

/**
 * פס טעינה: מסגרת מתכת, מסילה כהה, ומילוי כתום עם פסים אלכסוניים שנעים. progress בין 0 ל-1.
 */
@Composable
fun LoadingBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 34.dp) {
    val transition = rememberInfiniteTransition(label = "stripes")
    val shift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "shift"
    )
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val radius = size.height / 2.6f
        // מסגרת מתכת
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFB9C2D0), Color(0xFF6C7686), Color(0xFF3C4452))),
            cornerRadius = CornerRadius(radius)
        )
        drawRoundRect(GameColors.Outline, cornerRadius = CornerRadius(radius), style = Stroke(3.dp.toPx()))
        val inset = size.height * 0.16f
        val innerLeft = inset
        val innerTop = inset
        val innerWidth = size.width - inset * 2f
        val innerHeight = size.height - inset * 2f
        val innerRadius = CornerRadius(innerHeight / 2.5f)
        // מסילה
        drawRoundRect(Color(0xFF1A1F2B), Offset(innerLeft, innerTop), Size(innerWidth, innerHeight), innerRadius)
        // מילוי
        val fillWidth = innerWidth * progress.coerceIn(0.02f, 1f)
        val fillPath = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(innerLeft, innerTop, innerLeft + fillWidth, innerTop + innerHeight, innerRadius))
        }
        clipPath(fillPath) {
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFFFFD84A), Color(0xFFFF8A1F)), startY = innerTop, endY = innerTop + innerHeight),
                Offset(innerLeft, innerTop), Size(fillWidth, innerHeight)
            )
            // פסים אלכסוניים שנעים
            val stripe = innerHeight * 1.1f
            var x = innerLeft - stripe * 2f + shift * stripe * 2f
            while (x < innerLeft + fillWidth + stripe) {
                val band = Path().apply {
                    moveTo(x, innerTop + innerHeight)
                    lineTo(x + stripe * 0.5f, innerTop + innerHeight)
                    lineTo(x + stripe * 0.5f + innerHeight * 0.6f, innerTop)
                    lineTo(x + innerHeight * 0.6f, innerTop)
                    close()
                }
                drawPath(band, Color(0x33FFFFFF))
                x += stripe
            }
            // ברק עליון
            drawRect(Color(0x33FFFFFF), Offset(innerLeft, innerTop), Size(fillWidth, innerHeight * 0.35f))
        }
    }
}

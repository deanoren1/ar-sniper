package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * ציור הכוונת. המיקום מנורמל (0..1) ביחס למסך:
 * (0.5, 0.5) = מרכז המסך.
 */
@Composable
fun CrosshairOverlay(
    modifier: Modifier = Modifier,
    position: Offset = Offset(0.5f, 0.5f)
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width * position.x, size.height * position.y)
        val radius = 36.dp.toPx()
        val gap = 10.dp.toPx()
        val tick = 18.dp.toPx()
        val stroke = 2.dp.toPx()
        val color = Color.Red

        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = stroke)
        )

        // שמאל
        drawLine(
            color = color,
            start = Offset(center.x - radius - tick, center.y),
            end = Offset(center.x - gap, center.y),
            strokeWidth = stroke
        )
        // ימין
        drawLine(
            color = color,
            start = Offset(center.x + gap, center.y),
            end = Offset(center.x + radius + tick, center.y),
            strokeWidth = stroke
        )
        // למעלה
        drawLine(
            color = color,
            start = Offset(center.x, center.y - radius - tick),
            end = Offset(center.x, center.y - gap),
            strokeWidth = stroke
        )
        // למטה
        drawLine(
            color = color,
            start = Offset(center.x, center.y + gap),
            end = Offset(center.x, center.y + radius + tick),
            strokeWidth = stroke
        )

        // נקודה במרכז
        drawCircle(
            color = color,
            radius = 2.dp.toPx(),
            center = center
        )
    }
}

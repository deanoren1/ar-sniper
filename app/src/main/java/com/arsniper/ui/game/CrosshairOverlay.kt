package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * ציור הכוונת (תמונה מהגיליון החדש). המיקום מנורמל (0..1) ביחס למסך:
 * (0.5, 0.5) = מרכז המסך. מרכז התמונה הוא נקודת הפגיעה.
 */
@Composable
fun CrosshairOverlay(
    modifier: Modifier = Modifier,
    position: Offset = Offset(0.5f, 0.5f)
) {
    val image = ImageBitmap.imageResource(com.arsniper.R.drawable.hud_crosshair)
    Canvas(modifier = modifier.fillMaxSize()) {
        val d = 96.dp.toPx().toInt()
        val cx = (size.width * position.x).toInt()
        val cy = (size.height * position.y).toInt()
        drawImage(image, dstOffset = IntOffset(cx - d / 2, cy - d / 2), dstSize = IntSize(d, d))
    }
}

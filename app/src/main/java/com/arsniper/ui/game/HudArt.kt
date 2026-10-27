package com.arsniper.ui.game

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/** תמונת HUD מהגיליון החדש; גודל נקבע מבחוץ */
@Composable
fun HudImage(res: Int, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier
    )
}

/**
 * תא HUD מצויר בקוד (וקטורי, חד בכל גודל): רקע כחול כהה עם מעבר, וקו תכלת זוהר עם הילה.
 */
@Composable
fun GlowPill(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val corner = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f)
        val d = density
        val inset = 2.5f * d
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        val box = androidx.compose.ui.geometry.Size(w - 2 * inset, h - 2 * inset)
        val cr = androidx.compose.ui.geometry.CornerRadius(box.height / 2f, box.height / 2f)
        // הילה חיצונית
        for ((sw, a) in listOf(9f to 0.10f, 6f to 0.16f, 3.5f to 0.30f)) {
            drawRoundRect(
                color = androidx.compose.ui.graphics.Color(0xFF2F8BFF).copy(alpha = a),
                topLeft = topLeft, size = box, cornerRadius = cr,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw * d)
            )
        }
        // מילוי
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(
                    androidx.compose.ui.graphics.Color(0xFF0E2358),
                    androidx.compose.ui.graphics.Color(0xFF071336),
                    androidx.compose.ui.graphics.Color(0xFF040C26)
                )
            ),
            topLeft = topLeft, size = box, cornerRadius = cr
        )
        // ברק עליון עדין
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f), androidx.compose.ui.graphics.Color.Transparent),
                startY = inset, endY = inset + box.height * 0.5f
            ),
            topLeft = androidx.compose.ui.geometry.Offset(inset + 3f * d, inset + 1.5f * d),
            size = androidx.compose.ui.geometry.Size(box.width - 6f * d, box.height * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(box.height / 4f, box.height / 4f)
        )
        // קו ראשי וקו פנימי
        drawRoundRect(
            color = androidx.compose.ui.graphics.Color(0xFF6FD3FF),
            topLeft = topLeft, size = box, cornerRadius = cr,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6f * d)
        )
        drawRoundRect(
            color = androidx.compose.ui.graphics.Color(0xFF2A5FD0).copy(alpha = 0.7f),
            topLeft = androidx.compose.ui.geometry.Offset(inset + 3f * d, inset + 3f * d),
            size = androidx.compose.ui.geometry.Size(box.width - 6f * d, box.height - 6f * d),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius((box.height - 6f * d) / 2f, (box.height - 6f * d) / 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f * d)
        )
    }
}

/** לב מצויר בקוד: אדום מבריק עם מתאר כהה וברק, או אפור כשהחיים אבדו */
@Composable
fun GlowHeart(filled: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0.5f * w, 0.93f * h)
            cubicTo(-0.08f * w, 0.52f * h, 0.06f * w, 0.0f * h, 0.5f * w, 0.28f * h)
            cubicTo(0.94f * w, 0.0f * h, 1.08f * w, 0.52f * h, 0.5f * w, 0.93f * h)
            close()
        }
        val top = if (filled) androidx.compose.ui.graphics.Color(0xFFFF6B7D) else androidx.compose.ui.graphics.Color(0xFF9AA0A8)
        val mid = if (filled) androidx.compose.ui.graphics.Color(0xFFE8153A) else androidx.compose.ui.graphics.Color(0xFF6B7078)
        val bot = if (filled) androidx.compose.ui.graphics.Color(0xFFA80C28) else androidx.compose.ui.graphics.Color(0xFF454A52)
        drawPath(path, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(top, mid, bot)))
        drawPath(
            path,
            if (filled) androidx.compose.ui.graphics.Color(0xFF5A0616) else androidx.compose.ui.graphics.Color(0xFF23272D),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.07f * h)
        )
        // ברק
        drawOval(
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = if (filled) 0.55f else 0.3f),
            topLeft = androidx.compose.ui.geometry.Offset(0.17f * w, 0.17f * h),
            size = androidx.compose.ui.geometry.Size(0.2f * w, 0.14f * h)
        )
    }
}

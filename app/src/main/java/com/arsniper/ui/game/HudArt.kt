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
fun GlowPill(modifier: Modifier = Modifier, corner: Float = 0.5f, flat: Boolean = false) {
    val ratio = corner
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val d = density
        val inset = 2.5f * d
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        val box = androidx.compose.ui.geometry.Size(w - 2 * inset, h - 2 * inset)
        val cr = androidx.compose.ui.geometry.CornerRadius(box.height * ratio, box.height * ratio)
        if (flat) {
            // תא שטוח בסגנון מסכי התפריט: מילוי שקוף למחצה וקו דק, בלי הילה
            drawRoundRect(
                color = androidx.compose.ui.graphics.Color(0xFF0A1E5E).copy(alpha = 0.45f),
                topLeft = topLeft, size = box, cornerRadius = cr
            )
            drawRoundRect(
                color = androidx.compose.ui.graphics.Color(0xFF3F8EF0).copy(alpha = 0.9f),
                topLeft = topLeft, size = box, cornerRadius = cr,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.3f * d)
            )
            return@Canvas
        }
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
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(box.height * ratio / 2f, box.height * ratio / 2f)
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
            cornerRadius = androidx.compose.ui.geometry.CornerRadius((box.height - 6f * d) * ratio, (box.height - 6f * d) * ratio),
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

/** כפתור עגול מצויר בקוד (לזום): כדור כחול כהה עם טבעת תכלת, וסימן + או - לבן */
@Composable
fun GlowRoundButton(plus: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val c = center
        val r = size.minDimension / 2f
        val d = density
        drawCircle(androidx.compose.ui.graphics.Color(0xFF2F8BFF).copy(alpha = 0.25f), r, c)
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(androidx.compose.ui.graphics.Color(0xFF1E4CB8), androidx.compose.ui.graphics.Color(0xFF0A1E5E)),
                startY = c.y - r, endY = c.y + r
            ),
            radius = r * 0.86f, center = c
        )
        drawCircle(
            androidx.compose.ui.graphics.Color(0xFF6FD3FF), r * 0.86f, c,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6f * d)
        )
        drawCircle(
            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f), r * 0.6f,
            androidx.compose.ui.geometry.Offset(c.x, c.y - r * 0.18f)
        )
        val len = r * 0.42f
        val sw = r * 0.2f
        drawLine(androidx.compose.ui.graphics.Color.White, androidx.compose.ui.geometry.Offset(c.x - len, c.y), androidx.compose.ui.geometry.Offset(c.x + len, c.y), strokeWidth = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        if (plus) {
            drawLine(androidx.compose.ui.graphics.Color.White, androidx.compose.ui.geometry.Offset(c.x, c.y - len), androidx.compose.ui.geometry.Offset(c.x, c.y + len), strokeWidth = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** תאי מד הכדורים: slots תאים, הראשונים lit מוארים בתכלת, השאר כהים */
@Composable
fun AmmoSegments(lit: Int, slots: Int, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val gap = size.width * 0.018f
        val w = (size.width - gap * (slots - 1)) / slots
        for (i in 0 until slots) {
            val x = i * (w + gap)
            val on = i < lit
            val cr = androidx.compose.ui.geometry.CornerRadius(w / 2f, w / 2f)
            if (on) {
                drawRoundRect(
                    androidx.compose.ui.graphics.Color(0xFF29D3FF).copy(alpha = 0.25f),
                    topLeft = androidx.compose.ui.geometry.Offset(x - gap * 0.4f, -gap * 0.3f),
                    size = androidx.compose.ui.geometry.Size(w + gap * 0.8f, size.height + gap * 0.6f),
                    cornerRadius = cr
                )
                drawRoundRect(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(androidx.compose.ui.graphics.Color(0xFF8FF0FF), androidx.compose.ui.graphics.Color(0xFF18B8F0), androidx.compose.ui.graphics.Color(0xFF0B86D0))
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(w, size.height),
                    cornerRadius = cr
                )
                drawRoundRect(
                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.45f),
                    topLeft = androidx.compose.ui.geometry.Offset(x + w * 0.2f, size.height * 0.06f),
                    size = androidx.compose.ui.geometry.Size(w * 0.28f, size.height * 0.55f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f, w * 0.14f)
                )
            } else {
                drawRoundRect(
                    androidx.compose.ui.graphics.Color(0xFF0C1C4A),
                    topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(w, size.height),
                    cornerRadius = cr
                )
                drawRoundRect(
                    androidx.compose.ui.graphics.Color(0xFF2A4C9C).copy(alpha = 0.8f),
                    topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(w, size.height),
                    cornerRadius = cr,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f * density)
                )
            }
        }
    }
}

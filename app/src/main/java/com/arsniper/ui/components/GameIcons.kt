package com.arsniper.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** האייקונים של הממשק. כולם מצוירים בקוד (בלי קבצים), בצבע אחד שנבחר בהצגה */
enum class GameIcon { Play, Pause, Gear, Stats, Cart, Trophy, Lock, Back, Plus, Star, Home, Photo, Gallery, Close, Check, Heart, Restart }

@Composable
fun IconGlyph(icon: GameIcon, modifier: Modifier = Modifier, size: Dp = 28.dp, tint: Color = Color.White) {
    Canvas(modifier = modifier.size(size)) {
        drawGlyph(icon, tint)
    }
}

private fun DrawScope.drawGlyph(icon: GameIcon, tint: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (icon) {
        GameIcon.Play -> {
            val path = Path().apply {
                moveTo(w * 0.25f, h * 0.12f); lineTo(w * 0.88f, h * 0.5f); lineTo(w * 0.25f, h * 0.88f); close()
            }
            drawPath(path, tint)
        }
        GameIcon.Pause -> {
            drawRoundRect(tint, Offset(w * 0.2f, h * 0.15f), Size(w * 0.22f, h * 0.7f), CornerRadius(w * 0.05f))
            drawRoundRect(tint, Offset(w * 0.58f, h * 0.15f), Size(w * 0.22f, h * 0.7f), CornerRadius(w * 0.05f))
        }
        GameIcon.Gear -> {
            val c = center
            val r = w / 2f
            for (i in 0 until 8) {
                val a = i * (Math.PI.toFloat() / 4f)
                drawLine(
                    tint,
                    Offset(c.x + cos(a) * r * 0.62f, c.y + sin(a) * r * 0.62f),
                    Offset(c.x + cos(a) * r * 0.97f, c.y + sin(a) * r * 0.97f),
                    strokeWidth = r * 0.36f, cap = StrokeCap.Butt
                )
            }
            drawCircle(tint, r * 0.72f, c)
            drawCircle(Color(0xFF14295E), r * 0.3f, c)
        }
        GameIcon.Stats -> {
            drawRoundRect(tint, Offset(w * 0.12f, h * 0.55f), Size(w * 0.2f, h * 0.35f), CornerRadius(w * 0.04f))
            drawRoundRect(tint, Offset(w * 0.4f, h * 0.15f), Size(w * 0.2f, h * 0.75f), CornerRadius(w * 0.04f))
            drawRoundRect(tint, Offset(w * 0.68f, h * 0.4f), Size(w * 0.2f, h * 0.5f), CornerRadius(w * 0.04f))
        }
        GameIcon.Cart -> {
            val path = Path().apply {
                moveTo(w * 0.05f, h * 0.15f); lineTo(w * 0.22f, h * 0.15f); lineTo(w * 0.34f, h * 0.62f)
                lineTo(w * 0.8f, h * 0.62f); lineTo(w * 0.92f, h * 0.28f); lineTo(w * 0.27f, h * 0.28f)
            }
            drawPath(path, tint, style = stroke)
            drawCircle(tint, w * 0.09f, Offset(w * 0.4f, h * 0.82f))
            drawCircle(tint, w * 0.09f, Offset(w * 0.74f, h * 0.82f))
        }
        GameIcon.Trophy -> {
            val cup = Path().apply {
                moveTo(w * 0.25f, h * 0.1f); lineTo(w * 0.75f, h * 0.1f); lineTo(w * 0.7f, h * 0.5f)
                quadraticTo(w * 0.5f, h * 0.7f, w * 0.3f, h * 0.5f); close()
            }
            drawPath(cup, tint)
            drawArc(tint, 90f, 180f, false, Offset(w * 0.02f, h * 0.15f), Size(w * 0.3f, h * 0.28f), style = stroke)
            drawArc(tint, -90f, 180f, false, Offset(w * 0.68f, h * 0.15f), Size(w * 0.3f, h * 0.28f), style = stroke)
            drawRect(tint, Offset(w * 0.45f, h * 0.6f), Size(w * 0.1f, h * 0.2f))
            drawRoundRect(tint, Offset(w * 0.28f, h * 0.8f), Size(w * 0.44f, h * 0.12f), CornerRadius(w * 0.03f))
        }
        GameIcon.Lock -> {
            drawArc(tint, 180f, 180f, false, Offset(w * 0.28f, h * 0.08f), Size(w * 0.44f, h * 0.5f), style = stroke)
            drawRoundRect(tint, Offset(w * 0.18f, h * 0.42f), Size(w * 0.64f, h * 0.48f), CornerRadius(w * 0.08f))
            drawCircle(Color(0xFF14295E), w * 0.07f, Offset(w * 0.5f, h * 0.63f))
        }
        GameIcon.Back -> {
            val path = Path().apply {
                moveTo(w * 0.62f, h * 0.15f); lineTo(w * 0.28f, h * 0.5f); lineTo(w * 0.62f, h * 0.85f)
            }
            drawPath(path, tint, style = Stroke(width = w * 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        GameIcon.Plus -> {
            drawLine(tint, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.82f), w * 0.2f, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), w * 0.2f, StrokeCap.Round)
        }
        GameIcon.Star -> drawPath(starPath(w, h), tint)
        GameIcon.Home -> {
            val roof = Path().apply {
                moveTo(w * 0.5f, h * 0.08f); lineTo(w * 0.95f, h * 0.5f); lineTo(w * 0.05f, h * 0.5f); close()
            }
            drawPath(roof, tint)
            drawRect(tint, Offset(w * 0.2f, h * 0.48f), Size(w * 0.6f, h * 0.42f))
            drawRect(Color(0xFF14295E), Offset(w * 0.43f, h * 0.62f), Size(w * 0.14f, h * 0.28f))
        }
        GameIcon.Photo -> {
            drawRoundRect(tint, Offset(w * 0.08f, h * 0.18f), Size(w * 0.84f, h * 0.64f), CornerRadius(w * 0.1f), style = stroke)
            drawCircle(tint, w * 0.09f, Offset(w * 0.33f, h * 0.42f))
            val hill = Path().apply {
                moveTo(w * 0.16f, h * 0.75f); lineTo(w * 0.42f, h * 0.52f); lineTo(w * 0.6f, h * 0.68f)
                lineTo(w * 0.72f, h * 0.58f); lineTo(w * 0.86f, h * 0.75f); close()
            }
            drawPath(hill, tint)
        }
        GameIcon.Gallery -> {
            // שני מסגרות תמונה זו על זו: האחורית מסומנת בקו דק, הקדמית עם שמש והרים
            val thin = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val back = Path().apply {
                moveTo(w * 0.26f, h * 0.24f); lineTo(w * 0.26f, h * 0.14f)
                quadraticBezierTo(w * 0.26f, h * 0.08f, w * 0.32f, h * 0.08f)
                lineTo(w * 0.90f, h * 0.08f)
                quadraticBezierTo(w * 0.96f, h * 0.08f, w * 0.96f, h * 0.14f)
                lineTo(w * 0.96f, h * 0.58f)
                quadraticBezierTo(w * 0.96f, h * 0.64f, w * 0.90f, h * 0.64f)
                lineTo(w * 0.84f, h * 0.64f)
            }
            drawPath(back, tint.copy(alpha = 0.75f), style = thin)
            drawRoundRect(tint, Offset(w * 0.04f, h * 0.30f), Size(w * 0.74f, h * 0.60f), CornerRadius(w * 0.1f), style = stroke)
            drawCircle(tint, w * 0.07f, Offset(w * 0.24f, h * 0.50f))
            val hills = Path().apply {
                moveTo(w * 0.11f, h * 0.82f); lineTo(w * 0.31f, h * 0.62f); lineTo(w * 0.45f, h * 0.76f)
                lineTo(w * 0.56f, h * 0.66f); lineTo(w * 0.72f, h * 0.82f); close()
            }
            drawPath(hills, tint)
        }
        GameIcon.Close -> {
            drawLine(tint, Offset(w * 0.22f, h * 0.22f), Offset(w * 0.78f, h * 0.78f), w * 0.18f, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.22f, h * 0.78f), w * 0.18f, StrokeCap.Round)
        }
        GameIcon.Check -> {
            val path = Path().apply {
                moveTo(w * 0.15f, h * 0.55f); lineTo(w * 0.4f, h * 0.78f); lineTo(w * 0.86f, h * 0.24f)
            }
            drawPath(path, tint, style = Stroke(width = w * 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        GameIcon.Heart -> {
            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.9f)
                cubicTo(w * -0.1f, h * 0.45f, w * 0.15f, h * 0.0f, w * 0.5f, h * 0.3f)
                cubicTo(w * 0.85f, h * 0.0f, w * 1.1f, h * 0.45f, w * 0.5f, h * 0.9f)
                close()
            }
            drawPath(path, tint)
        }
        GameIcon.Restart -> {
            drawArc(tint, -60f, 290f, false, Offset(w * 0.16f, h * 0.16f), Size(w * 0.68f, h * 0.68f), style = Stroke(w * 0.14f, cap = StrokeCap.Round))
            val head = Path().apply {
                moveTo(w * 0.62f, h * 0.02f); lineTo(w * 0.62f, h * 0.34f); lineTo(w * 0.94f, h * 0.2f); close()
            }
            drawPath(head, tint)
        }
    }
}

/** מצולע כוכב בעל חמש קודקודים בתוך הריבוע w x h */
fun starPath(w: Float, h: Float): Path {
    val path = Path()
    val cx = w / 2f
    val cy = h * 0.54f
    val outer = minOf(w, h) * 0.5f
    val inner = outer * 0.45f
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = -Math.PI / 2 + i * Math.PI / 5
        val x = cx + (cos(a) * r).toFloat()
        val y = cy + (sin(a) * r).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

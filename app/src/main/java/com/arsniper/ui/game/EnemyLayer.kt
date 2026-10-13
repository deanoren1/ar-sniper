package com.arsniper.ui.game

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.arsniper.game.engine.SceneContext
import com.arsniper.game.engine.WINDOW_HIDE_OFFSET
import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import com.arsniper.game.spots.HidingSpot
import kotlin.math.roundToInt

/**
 * ציור האויבים: דמות אדם פשוטה.
 * מציץ: החלק שנמצא "בתוך" העצם נחתך, כך שנראה שהוא מאחורי העצם.
 * חלון: הדמות נחתכת באדן החלון.
 */
@Composable
fun EnemyLayer(
    enemies: List<Enemy>,
    scene: SceneContext?,
    modifier: Modifier = Modifier
) {
    val maskCache = remember(scene) { HashMap<Int, ImageBitmap>() }

    Canvas(modifier = modifier.fillMaxSize()) {
        for (enemy in enemies) {
            if (!enemy.isAlive) continue
            val isPeeker = enemy.kind == EnemyKind.Peek || enemy.kind == EnemyKind.Window
            if (isPeeker && enemy.exposure < 0.02f) continue

            val center = Offset(size.width * enemy.position.x, size.height * enemy.position.y)
            val radius = size.height * enemy.radius
            val spot = enemy.spot

            // התאמה לתאורה בתמונה: באזור כהה האויב כהה יותר, באזור בהיר בהיר יותר
            val shade = if (scene != null) {
                (0.55f + 0.6f * scene.lumaAt(enemy.position.x, enemy.position.y)).coerceIn(0.55f, 1.1f)
            } else {
                1f
            }
            // צל על הקרקע מתחת לאויב (לא לאויב שיוצא מחלון)
            val shadow = enemy.kind != EnemyKind.Window

            if (enemy.kind == EnemyKind.Window) {
                // יוצא מחלון: הדמות נחתכת באדן החלון, כך שנראה שהיא עומדת מאחוריו
                val sillY = (enemy.hidePos.y - WINDOW_HIDE_OFFSET * enemy.radius) * size.height
                clipRect(0f, 0f, size.width, sillY) {
                    drawPerson(center, radius, shade, false)
                }
            } else if (isPeeker && spot != null && scene != null) {
                val mask = maskCache.getOrPut(spot.id) { buildMask(spot) }
                drawPersonBehind(center, radius, spot, mask, scene, shade, shadow)
            } else {
                drawPerson(center, radius, shade, shadow)
            }
        }
    }
}

/** מסכת הציור של המחבוא: לבן עם אלפא רכה (אחרי ליטוש הקצוות) */
private fun buildMask(spot: HidingSpot): ImageBitmap {
    val alpha = spot.drawAlpha()
    val pixels = IntArray(alpha.size)
    for (i in pixels.indices) {
        pixels[i] = ((alpha[i].toInt() and 0xFF) shl 24) or 0x00FFFFFF
    }
    return Bitmap.createBitmap(pixels, spot.drawWidth, spot.drawHeight, Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

/** מצייר אדם מאחורי עצם: הדמות נמחקת בכל מקום שבו העצם (המסכה המלוטשת) נמצא */
private fun DrawScope.drawPersonBehind(
    center: Offset,
    radius: Float,
    spot: HidingSpot,
    mask: ImageBitmap,
    scene: SceneContext,
    shade: Float,
    shadow: Boolean
) {
    val mapper = scene.mapper
    val left = mapper.worldX(spot.drawMinX) * size.width
    val top = mapper.worldY(spot.drawMinY) * size.height
    val right = mapper.worldX(spot.drawMaxX) * size.width
    val bottom = mapper.worldY(spot.drawMaxY) * size.height

    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
        drawPerson(center, radius, shade, shadow)
        drawImage(
            image = mask,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(mask.width, mask.height),
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(
                (right - left).roundToInt().coerceAtLeast(1),
                (bottom - top).roundToInt().coerceAtLeast(1)
            ),
            blendMode = BlendMode.DstOut
        )
        canvas.restore()
    }
}

/** צובע בגוון כהה/בהיר יותר, לפי התאורה בתמונה */
private fun Color.shaded(factor: Float): Color = Color(
    red = (red * factor).coerceIn(0f, 1f),
    green = (green * factor).coerceIn(0f, 1f),
    blue = (blue * factor).coerceIn(0f, 1f),
    alpha = alpha
)

/**
 * דמות אדם פשוטה. center = מרכז ההיט; הרגליים ב-center.y + r, והראש ב-center.y - 1.2r.
 * הצבעים מוכהים או מובהרים לפי התאורה בתמונה (shade), ויש צל מתחת לרגליים.
 */
private fun DrawScope.drawPerson(center: Offset, r: Float, shade: Float, shadow: Boolean) {
    val cx = center.x
    val cy = center.y
    val feetY = cy + r

    // צל על הקרקע, בדיוק מתחת לרגליים
    if (shadow) {
        drawOval(
            color = Color.Black.copy(alpha = 0.4f),
            topLeft = Offset(cx - r * 0.8f, feetY - r * 0.14f),
            size = Size(r * 1.6f, r * 0.3f)
        )
    }

    val cloth = Color(0xFF4A5D68).shaded(shade)
    val clothDark = Color(0xFF263238).shaded(shade)
    val skin = Color(0xFFD2A67F).shaded(shade)
    val line = Color(0xFF10181C)
    val stroke = Stroke(width = r * 0.05f)

    // רגליים
    for (side in listOf(-1f, 1f)) {
        val topLeft = Offset(if (side < 0) cx - r * 0.36f else cx + r * 0.06f, cy + r * 0.15f)
        val legSize = Size(r * 0.3f, r * 0.85f)
        drawRoundRect(clothDark, topLeft, legSize, CornerRadius(r * 0.08f))
        drawRoundRect(line, topLeft, legSize, CornerRadius(r * 0.08f), stroke)
    }

    // ידיים
    for (side in listOf(-1f, 1f)) {
        val topLeft = Offset(if (side < 0) cx - r * 0.6f else cx + r * 0.42f, cy - r * 0.5f)
        val armSize = Size(r * 0.18f, r * 0.68f)
        drawRoundRect(cloth, topLeft, armSize, CornerRadius(r * 0.09f))
        drawRoundRect(line, topLeft, armSize, CornerRadius(r * 0.09f), stroke)
        drawCircle(skin, r * 0.1f, Offset(topLeft.x + r * 0.09f, cy + r * 0.22f))
    }

    // גוף
    val torsoTopLeft = Offset(cx - r * 0.44f, cy - r * 0.55f)
    val torsoSize = Size(r * 0.88f, r * 0.8f)
    drawRoundRect(cloth, torsoTopLeft, torsoSize, CornerRadius(r * 0.22f))
    drawRoundRect(line, torsoTopLeft, torsoSize, CornerRadius(r * 0.22f), stroke)

    // ראש וכובע
    val headCenter = Offset(cx, cy - r * 0.9f)
    drawCircle(skin, r * 0.3f, headCenter)
    drawCircle(line, r * 0.3f, headCenter, style = stroke)
    val capTopLeft = Offset(cx - r * 0.33f, cy - r * 1.22f)
    val capSize = Size(r * 0.66f, r * 0.24f)
    drawRoundRect(clothDark, capTopLeft, capSize, CornerRadius(r * 0.12f))
    drawRoundRect(line, capTopLeft, capSize, CornerRadius(r * 0.12f), stroke)
}

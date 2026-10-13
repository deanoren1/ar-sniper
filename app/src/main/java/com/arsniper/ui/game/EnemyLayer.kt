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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.arsniper.game.engine.SceneContext
import com.arsniper.game.engine.WINDOW_HIDE_OFFSET
import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import com.arsniper.game.model.EnemyState
import com.arsniper.game.spots.HidingSpot
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * ציור האויבים: דמות אדם פשוטה.
 * מציץ: החלק שנמצא "בתוך" העצם נחתך, כך שנראה שהוא מאחורי העצם.
 * חלון: הדמות נחתכת באדן החלון.
 * דמות שיוצאת מאחורי עצם: נחתכת על ידי העצם עד שהיא כולה בחוץ.
 */
@Composable
fun EnemyLayer(
    enemies: List<Enemy>,
    scene: SceneContext?,
    modifier: Modifier = Modifier
) {
    val maskCache = remember(scene) { HashMap<Int, ImageBitmap>() }

    Canvas(modifier = modifier.fillMaxSize()) {
        // מהרחוק לקרוב: הדמות שרגליה נמוכות יותר במסך (קרובה יותר) מצוירת אחרונה, מעל האחרות
        val ordered = enemies.sortedBy { depthKey(it) }
        for (enemy in ordered) {
            if (!enemy.isAlive) continue
            val isPeeker = enemy.kind == EnemyKind.Peek || enemy.kind == EnemyKind.Window
            if (isPeeker && enemy.exposure < 0.02f) continue
            // דמות הולכת שמחכה מוסתרת מאחורי עצם: לא מציירים כלל
            val emerging = enemy.kind == EnemyKind.Open && enemy.spot != null
            if (emerging && enemy.state == EnemyState.Idle) continue

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
            val gait = Gait.of(enemy)

            if (enemy.kind == EnemyKind.Window) {
                // יוצא מחלון: הדמות נחתכת באדן החלון, כך שנראה שהיא עומדת מאחוריו
                val sillY = (enemy.hidePos.y - WINDOW_HIDE_OFFSET * enemy.radius) * size.height
                clipRect(0f, 0f, size.width, sillY) {
                    drawPerson(center, radius, shade, false, gait)
                }
            } else if ((isPeeker || emerging) && spot != null && scene != null) {
                val mask = maskCache.getOrPut(spot.id) { buildMask(spot) }
                // אדם שעולה מאחורי עצם נמוך (תנועה אנכית): נחתך בקצה התחתון של העצם
                val rising = isPeeker && enemy.hidePos.y != enemy.peekPos.y
                val clipBottom = if (rising) {
                    spot.columnBottom(scene.mapper.photoX(enemy.position.x))
                        ?.let { scene.mapper.worldY(it) * size.height }
                } else {
                    null
                }
                drawPersonBehind(center, radius, spot, mask, scene, shade, shadow && !rising, clipBottom, gait)
            } else {
                drawPerson(center, radius, shade, shadow, gait)
            }
        }
    }
}

/**
 * מפתח עומק לסדר הציור: גובה הרגליים במסך. מציץ/חלון: לפי מקום ההסתתרות (לא לפי מצב ההצצה הנוכחי),
 * כדי שהסדר לא יקפוץ תוך כדי עלייה.
 */
private fun depthKey(enemy: Enemy): Float =
    if (enemy.kind == EnemyKind.Peek || enemy.kind == EnemyKind.Window) {
        enemy.hidePos.y + enemy.radius
    } else {
        enemy.position.y + enemy.radius
    }

/** תנועת הליכה: phase = שלב המחזור, swing = כמה חזק מנופפים (0 = עומד, 1 = הליכה, 1.6 = ריצה) */
private class Gait(val phase: Float, val swing: Float) {
    companion object {
        val STILL = Gait(0f, 0f)

        fun of(enemy: Enemy): Gait {
            if (enemy.kind == EnemyKind.Open) {
                if (enemy.state == EnemyState.Walking) return Gait(enemy.phase, 1f)
                if (enemy.state == EnemyState.Running) return Gait(enemy.phase, 1.6f)
            }
            return STILL
        }
    }
}

/** כמה פיקסלים (ברזולוציית הציור) מצמצמים את מסכת העצם: ערך גבוה = הדמות קרובה יותר לעצם */
private const val MASK_SHRINK_PX = 2

/** שחיקה (min) של מסכה, אופקית ואז אנכית. מחוץ למסכה נחשב שקוף. */
private fun erode(alpha: ByteArray, width: Int, height: Int, radius: Int): ByteArray {
    if (radius <= 0) return alpha
    val temp = ByteArray(alpha.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var m = 255
            for (d in -radius..radius) {
                val xx = x + d
                val v = if (xx in 0 until width) alpha[y * width + xx].toInt() and 0xFF else 0
                if (v < m) m = v
            }
            temp[y * width + x] = m.toByte()
        }
    }
    val out = ByteArray(alpha.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var m = 255
            for (d in -radius..radius) {
                val yy = y + d
                val v = if (yy in 0 until height) temp[yy * width + x].toInt() and 0xFF else 0
                if (v < m) m = v
            }
            out[y * width + x] = m.toByte()
        }
    }
    return out
}

/** מסכת הציור של המחבוא: לבן עם אלפא רכה (אחרי ליטוש הקצוות) */
private fun buildMask(spot: HidingSpot): ImageBitmap {
    // מצמצמים מעט את המסכה, כדי שלא יישאר פס רקע בין העצם לדמות
    val alpha = erode(spot.drawAlpha(), spot.drawWidth, spot.drawHeight, MASK_SHRINK_PX)
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
    shadow: Boolean,
    clipBottom: Float?,
    gait: Gait
) {
    val mapper = scene.mapper
    val left = mapper.worldX(spot.drawMinX) * size.width
    val top = mapper.worldY(spot.drawMinY) * size.height
    val right = mapper.worldX(spot.drawMaxX) * size.width
    val bottom = mapper.worldY(spot.drawMaxY) * size.height

    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
        if (clipBottom != null) {
            clipRect(0f, 0f, size.width, clipBottom) {
                drawPerson(center, radius, shade, shadow, gait)
            }
        } else {
            drawPerson(center, radius, shade, shadow, gait)
        }
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
 * הליכה: הרגליים והידיים מתנדנדות בניגוד זו לזו, והגוף עולה ויורד קלות עם כל צעד.
 */
private fun DrawScope.drawPerson(center: Offset, r: Float, shade: Float, shadow: Boolean, gait: Gait) {
    val cx = center.x
    val feetY = center.y + r

    // צל על הקרקע, בדיוק מתחת לרגליים (נשאר על הקרקע גם כשהגוף עולה)
    if (shadow) {
        drawOval(
            color = Color.Black.copy(alpha = 0.4f),
            topLeft = Offset(cx - r * 0.8f, feetY - r * 0.14f),
            size = Size(r * 1.6f, r * 0.3f)
        )
    }

    val swingSin = sin(gait.phase)
    // קפיצה קלה בכל צעד (פעמיים במחזור), רק בזמן הליכה
    val bob = abs(swingSin) * 0.05f * r * gait.swing
    val cy = center.y - bob
    val legAngle = 26f * gait.swing * swingSin
    val armAngle = 22f * gait.swing * swingSin

    val cloth = Color(0xFF4A5D68).shaded(shade)
    val clothDark = Color(0xFF263238).shaded(shade)
    val skin = Color(0xFFD2A67F).shaded(shade)
    val line = Color(0xFF10181C)
    val stroke = Stroke(width = r * 0.05f)

    // רגליים: מסתובבות סביב הירך, אחת קדימה ואחת אחורה
    for (side in listOf(-1f, 1f)) {
        val topLeft = Offset(if (side < 0) cx - r * 0.36f else cx + r * 0.06f, cy + r * 0.15f)
        val legSize = Size(r * 0.3f, r * 0.85f)
        val hip = Offset(topLeft.x + legSize.width / 2f, topLeft.y)
        rotate(degrees = legAngle * side, pivot = hip) {
            drawRoundRect(clothDark, topLeft, legSize, CornerRadius(r * 0.08f))
            drawRoundRect(line, topLeft, legSize, CornerRadius(r * 0.08f), stroke)
        }
    }

    // ידיים: מתנדנדות בניגוד לרגל שבאותו צד, סביב הכתף
    for (side in listOf(-1f, 1f)) {
        val topLeft = Offset(if (side < 0) cx - r * 0.6f else cx + r * 0.42f, cy - r * 0.5f)
        val armSize = Size(r * 0.18f, r * 0.68f)
        val shoulder = Offset(topLeft.x + armSize.width / 2f, topLeft.y)
        rotate(degrees = -armAngle * side, pivot = shoulder) {
            drawRoundRect(cloth, topLeft, armSize, CornerRadius(r * 0.09f))
            drawRoundRect(line, topLeft, armSize, CornerRadius(r * 0.09f), stroke)
            drawCircle(skin, r * 0.1f, Offset(topLeft.x + r * 0.09f, cy + r * 0.22f))
        }
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

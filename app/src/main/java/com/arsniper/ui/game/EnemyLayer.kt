package com.arsniper.ui.game

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import com.arsniper.game.spots.HidingSpot
import kotlin.math.roundToInt

/**
 * ציור האויבים. כרגע דמות פשוטה (מפלצת ירוקה).
 * מציץ: החלק שנמצא "בתוך" העצם נחתך, כך שנראה שהוא מאחורי העצם.
 * חלון: האויב נחתך לגבולות החלון.
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

            if (isPeeker && spot != null && scene != null) {
                val mask = maskCache.getOrPut(spot.id) { buildMask(spot) }
                drawMonsterWithMask(center, radius, spot, mask, scene, enemy.kind == EnemyKind.Window)
            } else {
                drawMonster(center, radius)
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

private fun DrawScope.drawMonsterWithMask(
    center: Offset,
    radius: Float,
    spot: HidingSpot,
    mask: ImageBitmap,
    scene: SceneContext,
    insideOnly: Boolean
) {
    val mapper = scene.mapper
    val left = mapper.worldX(spot.drawMinX) * size.width
    val top = mapper.worldY(spot.drawMinY) * size.height
    val right = mapper.worldX(spot.drawMaxX) * size.width
    val bottom = mapper.worldY(spot.drawMaxY) * size.height

    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
        if (insideOnly) {
            clipRect(left, top, right, bottom) {
                drawMonster(center, radius)
            }
        } else {
            drawMonster(center, radius)
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
            blendMode = if (insideOnly) BlendMode.DstIn else BlendMode.DstOut
        )
        canvas.restore()
    }
}

private fun DrawScope.drawMonster(center: Offset, r: Float) {
    // גוף
    drawCircle(
        color = Color(0xFF2E7D32),
        radius = r,
        center = center
    )
    drawCircle(
        color = Color(0xFF1B5E20),
        radius = r,
        center = center,
        style = Stroke(width = r * 0.08f)
    )

    // עיניים
    val eyeY = center.y - r * 0.2f
    val eyeRadius = r * 0.22f
    listOf(-1f, 1f).forEach { side ->
        val eyeCenter = Offset(center.x + side * r * 0.35f, eyeY)
        drawCircle(
            color = Color.White,
            radius = eyeRadius,
            center = eyeCenter
        )
        drawCircle(
            color = Color.Black,
            radius = eyeRadius * 0.5f,
            center = eyeCenter
        )
    }

    // פה
    drawLine(
        color = Color(0xFF1B5E20),
        start = Offset(center.x - r * 0.35f, center.y + r * 0.4f),
        end = Offset(center.x + r * 0.35f, center.y + r * 0.4f),
        strokeWidth = r * 0.1f
    )
}
package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.arsniper.game.model.Enemy

/**
 * ציור האויבים. כרגע דמות פשוטה (מפלצת ירוקה).
 * בהמשך אפשר להחליף את הציור בדמויות אחרות בלי לגעת במנוע המשחק.
 */
@Composable
fun EnemyLayer(
    enemies: List<Enemy>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        enemies.filter { it.isAlive }.forEach { enemy ->
            val center = Offset(
                size.width * enemy.position.x,
                size.height * enemy.position.y
            )
            val r = size.height * enemy.radius

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
    }
}

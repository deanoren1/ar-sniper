package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.ui.res.imageResource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arsniper.game.model.Vec2

/**
 * Joystick וירטואלי. onMove מקבל כיוון בין -1 ל-1 בכל ציר,
 * ו-(0,0) כשמרימים את האצבע.
 * הבסיס כמעט שקוף (טבעת דקה), כדי שלא יסתיר מפלצות שנמצאות מאחוריו.
 */
@Composable
fun Joystick(
    onMove: (Vec2) -> Unit,
    modifier: Modifier = Modifier,
    baseSize: Dp = 130.dp
) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    val baseImg = androidx.compose.ui.graphics.ImageBitmap.imageResource(com.arsniper.R.drawable.hud_joy_base)
    val knobImg = androidx.compose.ui.graphics.ImageBitmap.imageResource(com.arsniper.R.drawable.hud_joy_knob)
    val currentOnMove by rememberUpdatedState(onMove)

    Canvas(
        modifier = modifier
            .size(baseSize)
            .pointerInput(Unit) {
                val knobRadius = 26.dp.toPx()
                val travel = size.width / 2f - knobRadius
                val center = Offset(size.width / 2f, size.height / 2f)

                fun update(position: Offset) {
                    var delta = position - center
                    val distance = delta.getDistance()
                    if (distance > travel) {
                        delta = delta * (travel / distance)
                    }
                    knob = delta
                    currentOnMove(Vec2(delta.x / travel, delta.y / travel))
                }

                fun release() {
                    knob = Offset.Zero
                    currentOnMove(Vec2.ZERO)
                }

                detectDragGestures(
                    onDragStart = { update(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        update(change.position)
                    },
                    onDragEnd = { release() },
                    onDragCancel = { release() }
                )
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val w = size.width.toInt()
        drawImage(baseImg, dstOffset = androidx.compose.ui.unit.IntOffset(0, 0), dstSize = androidx.compose.ui.unit.IntSize(w, w))
        val k = size.width * 0.425f
        val kd = k.toInt()
        val kc = center + knob
        drawImage(
            knobImg,
            dstOffset = androidx.compose.ui.unit.IntOffset((kc.x - kd / 2f).toInt(), (kc.y - kd / 2f).toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(kd, kd)
        )
    }
}

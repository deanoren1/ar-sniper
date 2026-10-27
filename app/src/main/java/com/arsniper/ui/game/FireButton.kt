package com.arsniper.ui.game

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * כפתור הירי (עיצוב ה-HUD החדש). onPress נקרא ברגע המגע (תגובה מיידית), onRelease כשמרימים את האצבע.
 * כך נשק עם ירי רצוף יכול לירות כל עוד הכפתור לחוץ.
 */
@Composable
fun FireButton(
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val currentPress by rememberUpdatedState(onPress)
    val currentRelease by rememberUpdatedState(onRelease)
    Box(
        modifier = modifier
            .size(width = 112.dp, height = 109.dp)
            .graphicsLayer {
                val s = if (pressed) 0.93f else 1f
                scaleX = s
                scaleY = s
                alpha = if (pressed) 1f else 0.92f
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        currentPress()
                        tryAwaitRelease()
                        pressed = false
                        currentRelease()
                    }
                )
            }
    ) {
        HudImage(com.arsniper.R.drawable.hud_fire, Modifier.fillMaxSize())
    }
}

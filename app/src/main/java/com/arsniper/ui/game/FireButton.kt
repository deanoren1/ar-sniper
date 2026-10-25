package com.arsniper.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * כפתור הירי. onPress נקרא ברגע המגע (תגובה מיידית), onRelease כשמרימים את האצבע.
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
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(96.dp)
            .background(
                if (pressed) Color(0xFFE53935).copy(alpha = 0.85f) else Color(0xFFC62828).copy(alpha = 0.6f),
                CircleShape
            )
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
        Text(text = "FIRE", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

package com.arsniper.ui.capture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.pano.HeadingTracker
import com.arsniper.pano.PanoramaStore
import com.arsniper.pano.wrapAngle
import com.arsniper.ui.components.GameButton
import com.arsniper.ui.components.GameIcon
import com.arsniper.ui.components.GameIconButton
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.theme.GameButtonStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * תצוגת בדיקה של הפנורמה: גוררים עם האצבע, או מפעילים GYRO ומסובבים את הטלפון.
 * עדיין לא מחובר למשחק: נועד לבדוק שהחיבור יצא טוב.
 */
@Composable
fun PanoViewScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pano by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null) {
        value = withContext(Dispatchers.IO) { PanoramaStore.load(context)?.asImageBitmap() }
    }
    val is360 = remember { PanoramaStore.is360(context) }
    val radius = remember { PanoramaStore.radius(context) }
    var offset by remember { mutableStateOf(0f) }
    var gyro by remember { mutableStateOf(false) }
    val tracker = remember { HeadingTracker(context) }
    var viewSize by remember { mutableStateOf(IntSize(1, 1)) }

    DisposableEffect(Unit) { onDispose { tracker.stop() } }

    LaunchedEffect(gyro) {
        if (!gyro) { tracker.stop(); return@LaunchedEffect }
        tracker.start()
        var last = Float.NaN
        while (true) {
            withFrameNanos { }
            if (!tracker.ready) continue
            if (last.isNaN()) last = tracker.yaw
            val bmp = pano ?: continue
            val scale = viewSize.height.toFloat() / bmp.height
            offset += wrapAngle(tracker.yaw - last) * radius * scale
            last = tracker.yaw
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val bmp = pano
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { _, drag -> offset -= drag.x }
                }
        ) {
            viewSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
            if (bmp != null) {
                val scale = size.height / bmp.height
                val scaledW = bmp.width * scale
                var o = offset
                if (is360) {
                    o = ((o % scaledW) + scaledW) % scaledW
                } else {
                    o = o.coerceIn(0f, (scaledW - size.width).coerceAtLeast(0f))
                }
                var x = -o
                val src = IntSize(bmp.width, bmp.height)
                while (x < size.width) {
                    drawImage(
                        bmp,
                        srcOffset = IntOffset.Zero,
                        srcSize = src,
                        dstOffset = IntOffset(x.roundToInt(), 0),
                        dstSize = IntSize(scaledW.roundToInt(), size.height.roundToInt())
                    )
                    if (!is360) break
                    x += scaledW
                }
            }
        }
        GameIconButton(
            GameIcon.Back, onClick = onBack, style = GameButtonStyle.Primary, size = 48.dp,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp)
        ) {
            OutlinedText(if (is360) "360° PANORAMA (TEST)" else "180° PANORAMA (TEST)", fontSize = 16.sp)
            OutlinedText("Drag, or turn GYRO on and rotate the phone", fontSize = 12.sp)
        }
        GameButton(
            text = if (gyro) "GYRO: ON" else "GYRO: OFF",
            onClick = { gyro = !gyro },
            style = if (gyro) GameButtonStyle.Success else GameButtonStyle.Secondary,
            height = 40.dp,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        )
        if (bmp == null) {
            OutlinedText("Loading...", modifier = Modifier.align(Alignment.Center))
        }
    }
}

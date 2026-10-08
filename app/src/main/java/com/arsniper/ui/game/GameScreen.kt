package com.arsniper.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arsniper.data.PhotoRepository
import com.arsniper.game.engine.FireResult
import com.arsniper.game.engine.GameEngine
import com.arsniper.game.model.Enemy
import com.arsniper.game.model.Vec2
import kotlinx.coroutines.delay

@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val photo = remember { PhotoRepository.load(context)?.asImageBitmap() }
    val engine = remember { GameEngine() }

    // מיקום הכוונת (מנורמל, 0..1) וכיוון ה-joystick הנוכחי
    var crosshair by remember { mutableStateOf(Vec2(0.5f, 0.5f)) }
    var joystick by remember { mutableStateOf(Vec2.ZERO) }
    var aspect by remember { mutableStateOf(2f) }

    // אויב אחד בשלב הזה
    var enemies by remember {
        mutableStateOf(listOf(Enemy(id = 1, position = Vec2(0.65f, 0.45f))))
    }
    var showHit by remember { mutableStateOf(false) }

    // לולאת משחק: מעדכנת את הכוונת בכל פריים
    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastFrameNanos != 0L) {
                    val dt = (now - lastFrameNanos) / 1_000_000_000f
                    crosshair = engine.moveCrosshair(crosshair, joystick, dt, aspect)
                }
                lastFrameNanos = now
            }
        }
    }

    // הודעת HIT נעלמת אחרי כשנייה
    LaunchedEffect(showHit) {
        if (showHit) {
            delay(1000)
            showHit = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (size.height > 0) {
                    aspect = size.width.toFloat() / size.height.toFloat()
                }
            }
    ) {
        BackgroundLayer(photo = photo)

        EnemyLayer(enemies = enemies)

        CrosshairOverlay(position = Offset(crosshair.x, crosshair.y))

        HitIndicator(
            visible = showHit,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        )

        Joystick(
            onMove = { joystick = it },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 64.dp, bottom = 32.dp)
        )

        FireButton(
            onClick = {
                when (val result = engine.fire(crosshair, enemies, aspect)) {
                    is FireResult.Hit -> {
                        enemies = enemies.map {
                            if (it.id == result.enemy.id) it.copy(isAlive = false) else it
                        }
                        showHit = true
                    }
                    is FireResult.Miss -> {
                        // פספוס: לא קורה כלום
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 64.dp, bottom = 32.dp)
        )
    }
}

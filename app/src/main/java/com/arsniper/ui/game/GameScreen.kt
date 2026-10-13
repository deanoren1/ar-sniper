package com.arsniper.ui.game

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arsniper.audio.SoundPlayer
import com.arsniper.data.PhotoRepository
import com.arsniper.game.engine.FireResult
import com.arsniper.game.engine.GameEngine
import com.arsniper.game.engine.SceneContext
import com.arsniper.game.engine.ScoreManager
import com.arsniper.game.model.Enemy
import com.arsniper.game.model.GameCamera
import com.arsniper.game.model.Vec2
import com.arsniper.game.model.WorldMapper
import com.arsniper.ml.SceneSession
import kotlinx.coroutines.delay

/** מספר האויבים שפעילים במקביל */
private const val TARGET_ENEMIES = 4
private const val ZOOM_STEP = 0.5f

/** זום התחלתי: מאפשר להזיז את המצלמה ימינה ושמאלה, כדי לגלות מטרות שהכפתורים מסתירים */
private const val START_ZOOM = 1.3f

@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val photoBitmap = remember { PhotoRepository.load(context) }
    val photo = remember { photoBitmap?.asImageBitmap() }
    val engine = remember { GameEngine() }
    val scoreManager = remember { ScoreManager() }
    val soundPlayer = remember { SoundPlayer(context) }

    DisposableEffect(Unit) {
        onDispose { soundPlayer.release() }
    }

    // הכוונת נשמרת בקואורדינטות עולם (0..1 ביחס לתמונה)
    var crosshair by remember { mutableStateOf(Vec2(0.5f, 0.5f)) }
    var joystick by remember { mutableStateOf(Vec2.ZERO) }
    var aspect by remember { mutableStateOf(2f) }
    var sizeKnown by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(START_ZOOM) }

    // פילטר סגנון קריקטורי על התמונה (מחושב פעם אחת, כשמדליקים בפעם הראשונה)
    var styleOn by remember { mutableStateOf(false) }
    var styleBusy by remember { mutableStateOf(false) }
    var styledPhoto by remember { mutableStateOf<ImageBitmap?>(null) }

    // אויבים, וניקוד
    var enemies by remember { mutableStateOf(emptyList<Enemy>()) }
    var scene by remember { mutableStateOf<SceneContext?>(null) }
    var score by remember { mutableStateOf(0) }
    var hitCount by remember { mutableStateOf(0) }
    var showHit by remember { mutableStateOf(false) }

    // אחרי שגודל המסך ידוע: בונים את הסצנה (מחבואים לפי ניתוח התמונה) ויוצרים אויבים
    LaunchedEffect(sizeKnown) {
        if (sizeKnown) {
            val analysis = SceneSession.analysis
            val sceneContext = if (analysis != null) {
                SceneContext(
                    analysis = analysis,
                    spots = SceneSession.spots,
                    mapper = WorldMapper(analysis.width.toFloat() / analysis.height, aspect),
                    luma = SceneSession.luma
                )
            } else {
                null
            }
            scene = sceneContext
            engine.configure(sceneContext)
            enemies = engine.replenish(emptyList(), TARGET_ENEMIES, aspect)
        }
    }

    // מחשבים את התמונה הקריקטורית ברקע בפעם הראשונה שמדליקים את הסגנון
    LaunchedEffect(styleOn) {
        if (styleOn && styledPhoto == null && photoBitmap != null) {
            styleBusy = true
            val result = withContext(Dispatchers.Default) {
                runCatching { StyleFilter.cartoon(photoBitmap).asImageBitmap() }.getOrNull()
            }
            styledPhoto = result
            if (result == null) styleOn = false
            styleBusy = false
        }
    }

    // משלימים אויבים מדי פעם, למקרה שלא נמצא מקום מתאים בניסיון הקודם
    LaunchedEffect(sizeKnown) {
        while (true) {
            delay(1500)
            if (sizeKnown) {
                enemies = engine.replenish(enemies, TARGET_ENEMIES, aspect)
            }
        }
    }

    // לולאת משחק: מעדכנת את הכוונת בכל פריים
    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastFrameNanos != 0L) {
                    val dt = (now - lastFrameNanos) / 1_000_000_000f
                    crosshair = engine.moveCrosshair(crosshair, joystick, dt, aspect, zoom)
                    enemies = engine.update(enemies, dt)
                }
                lastFrameNanos = now
            }
        }
    }

    // אחרי פגיעה: HIT נשאר שנייה, ואז מושלמים אויבים חדשים
    LaunchedEffect(hitCount) {
        if (hitCount > 0) {
            showHit = true
            delay(1000)
            showHit = false
            enemies = engine.replenish(enemies, TARGET_ENEMIES, aspect)
        }
    }

    // המצלמה עוקבת אחרי הכוונת, ובלי לצאת מגבולות התמונה
    val camera = GameCamera.following(crosshair, zoom)
    val crosshairOnScreen = camera.worldToScreen(crosshair)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (size.height > 0) {
                    aspect = size.width.toFloat() / size.height.toFloat()
                    sizeKnown = true
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoomChange, _ ->
                    zoom = (zoom * zoomChange)
                        .coerceIn(GameCamera.MIN_ZOOM, GameCamera.MAX_ZOOM)
                }
            }
    ) {
        // העולם: רקע ואויבים, מוגדלים ומוזזים יחד לפי המצלמה
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = camera.zoom
                    scaleY = camera.zoom
                    translationX = (0.5f - camera.center.x * camera.zoom) * size.width
                    translationY = (0.5f - camera.center.y * camera.zoom) * size.height
                }
        ) {
            BackgroundLayer(photo = if (styleOn) (styledPhoto ?: photo) else photo)
            EnemyLayer(enemies = enemies, scene = scene)
        }

        // הכוונת מצוירת במיקום המסך שלה
        CrosshairOverlay(position = Offset(crosshairOnScreen.x, crosshairOnScreen.y))

        ScoreDisplay(
            score = score,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )

        // מתג סגנון קריקטורי
        Button(
            onClick = { if (!styleBusy) styleOn = !styleOn },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (styleOn) Color(0xFF6A1B9A) else Color(0xFF424242)
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text(if (styleBusy) "..." else if (styleOn) "STYLE ON" else "STYLE")
        }

        HitIndicator(
            visible = showHit,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        )

        // זום: שורה אופקית מעל ה-joystick, ממורכזת אליו (joystick: מרכז ב-134dp משמאל)
        ZoomControls(
            zoom = zoom,
            onZoomIn = {
                zoom = (zoom + ZOOM_STEP).coerceAtMost(GameCamera.MAX_ZOOM)
            },
            onZoomOut = {
                zoom = (zoom - ZOOM_STEP).coerceAtLeast(GameCamera.MIN_ZOOM)
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 56.dp, bottom = 184.dp)
        )

        Joystick(
            onMove = { joystick = it },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 64.dp, bottom = 32.dp)
        )

        FireButton(
            onClick = {
                soundPlayer.playShot()
                val result = engine.fire(crosshair, enemies, aspect)
                score = scoreManager.apply(score, result)
                when (result) {
                    is FireResult.Hit -> {
                        enemies = enemies.map {
                            if (it.id == result.enemy.id) it.copy(isAlive = false) else it
                        }
                        hitCount += 1
                    }
                    is FireResult.Miss -> {
                        // פספוס: רק צליל, בלי שינוי נוסף
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 64.dp, bottom = 32.dp)
        )
    }
}

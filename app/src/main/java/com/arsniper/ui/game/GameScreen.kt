package com.arsniper.ui.game

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
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
import com.arsniper.game.engine.Accessories
import com.arsniper.game.engine.FireResult
import com.arsniper.game.engine.GameEngine
import com.arsniper.game.engine.SceneContext
import com.arsniper.game.engine.ScoreManager
import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import com.arsniper.game.model.EnemyState
import com.arsniper.game.model.GameCamera
import com.arsniper.game.model.Vec2
import com.arsniper.game.model.WorldMapper
import com.arsniper.ml.SceneSession
import kotlinx.coroutines.delay

/** מספר האויבים שפעילים במקביל */
private const val TARGET_ENEMIES = 4
private const val ZOOM_STEP = 0.5f

/**
 * זום הבסיס הפנימי: כבר בו המצלמה מוגדלת מעט, כדי שאפשר יהיה להזיז אותה ימינה ושמאלה
 * ולגלות מטרות שהכפתורים מסתירים. לשחקן הוא מוצג כ-x1.0 והוא גם הרמה הנמוכה ביותר.
 */
private const val BASE_ZOOM = 1.2f

/** חיים לכל מפלצת, לפי סדר השורות בגיליון המפלצות: ורוד, סגול, ירוק, כתום, כחול */
private val MONSTER_HP = intArrayOf(2, 3, 1, 2, 3)

/** ניקוד על פגיעה שלא הורגת (הריגה נותנת את הניקוד המלא) */
private const val HURT_POINTS = 25

/** כמה זמן מפלצת שנפגעה ולא מתה רצה (מתעצבנת), בשניות */
private const val ENRAGE_SECONDS = 2f

private fun hpFor(id: Int): Int = MONSTER_HP[((id % MONSTER_HP.size) + MONSTER_HP.size) % MONSTER_HP.size]

/** קובע חיים למפלצות חדשות. מחזיר את אותה רשימה אם לכולן כבר יש חיים. */
private fun withHp(list: List<Enemy>): List<Enemy> =
    if (list.none { it.maxHp == 0 }) {
        list
    } else {
        list.map {
            if (it.maxHp == 0) {
                val max = hpFor(it.id)
                it.copy(maxHp = max, hp = max)
            } else {
                it
            }
        }
    }

private fun nowSeconds(): Double = System.nanoTime() / 1e9

/** מורידה חיים (תמיד נשאר לפחות 1: ההריגה נעשית בנפרד), מבהבהת, ורצה אם הולכת בחוץ */
private fun hurt(enemy: Enemy, damage: Int, now: Double): Enemy {
    val max = if (enemy.maxHp > 0) enemy.maxHp else hpFor(enemy.id)
    val current = if (enemy.maxHp > 0) enemy.hp else max
    val enraged = enemy.kind == EnemyKind.Open && enemy.spot == null
    return enemy.copy(
        maxHp = max,
        hp = (current - damage).coerceAtLeast(1),
        hurtAt = now,
        state = if (enraged) EnemyState.Running else enemy.state,
        timer = if (enraged) ENRAGE_SECONDS else enemy.timer
    )
}

@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val photo = remember { PhotoRepository.load(context)?.asImageBitmap() }
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
    var zoom by remember { mutableStateOf(BASE_ZOOM) }

    // אויבים, וניקוד
    var enemies by remember { mutableStateOf(emptyList<Enemy>()) }
    var scene by remember { mutableStateOf<SceneContext?>(null) }
    var score by remember { mutableStateOf(0) }
    var hitCount by remember { mutableStateOf(0) }
    var showHit by remember { mutableStateOf(false) }

    // פיצוצים של נקודות תורפה, וזמן הפריים הנוכחי (מפעיל את האנימציה שלהם)
    var blasts by remember { mutableStateOf(emptyList<Blast>()) }
    var frameNanos by remember { mutableStateOf(0L) }

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
            enemies = withHp(engine.replenish(emptyList(), TARGET_ENEMIES, aspect))
        }
    }

    // משלימים אויבים מדי פעם, למקרה שלא נמצא מקום מתאים בניסיון הקודם
    LaunchedEffect(sizeKnown) {
        while (true) {
            delay(1500)
            if (sizeKnown) {
                enemies = withHp(engine.replenish(enemies, TARGET_ENEMIES, aspect))
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
                    crosshair = engine.moveCrosshair(crosshair, joystick, dt, aspect, zoom / BASE_ZOOM)
                    enemies = withHp(engine.update(enemies, dt))
                }
                lastFrameNanos = now
                frameNanos = now
                if (blasts.isNotEmpty()) {
                    val alive = blasts.filter { (now - it.bornNanos) / 1e9f <= BLAST_SECONDS }
                    if (alive.size != blasts.size) blasts = alive
                }
            }
        }
    }

    // אחרי פגיעה: HIT נשאר שנייה, ואז מושלמים אויבים חדשים
    LaunchedEffect(hitCount) {
        if (hitCount > 0) {
            showHit = true
            delay(1000)
            showHit = false
            enemies = withHp(engine.replenish(enemies, TARGET_ENEMIES, aspect))
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
                        .coerceIn(BASE_ZOOM, GameCamera.MAX_ZOOM)
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
            BackgroundLayer(photo = photo)
            EnemyLayer(enemies = enemies, scene = scene)
            EffectsLayer(blasts = blasts, nowNanos = frameNanos)
        }

        // הכוונת מצוירת במיקום המסך שלה
        CrosshairOverlay(position = Offset(crosshairOnScreen.x, crosshairOnScreen.y))

        ScoreDisplay(
            score = score,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )

        HitIndicator(
            visible = showHit,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        )

        // זום: בפינה הימנית העליונה, מעל אזור ההליכה של המפלצות, כדי שלא יסתיר אותן
        ZoomControls(
            // לשחקן מוצג הזום ביחס לבסיס: הרמה ההתחלתית היא x1.0
            zoom = zoom / BASE_ZOOM,
            onZoomIn = {
                zoom = (zoom + ZOOM_STEP * BASE_ZOOM).coerceAtMost(GameCamera.MAX_ZOOM)
            },
            onZoomOut = {
                zoom = (zoom - ZOOM_STEP * BASE_ZOOM).coerceAtLeast(BASE_ZOOM)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
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
                val accessoryHit = Accessories.hit(crosshair, AccessoryRegistry.snapshot(), enemies, aspect)
                if (accessoryHit != null) {
                    // פגיעה בנקודת תורפה: פיצוץ, ניקוד גדול, ואולי עוד מפלצות נופלות
                    val outcome = Accessories.explode(accessoryHit, enemies, aspect)
                    score += outcome.points
                    val now = nowSeconds()
                    enemies = enemies.map {
                        if (outcome.killedIds.contains(it.id)) {
                            it.copy(isAlive = false)
                        } else if (outcome.damagedIds.contains(it.id)) {
                            hurt(it, Accessories.BOMB_SPLASH_DAMAGE, now)
                        } else {
                            it
                        }
                    }
                    blasts = blasts + Blast(
                        x = outcome.anchor.x,
                        y = outcome.anchor.y,
                        kind = outcome.kind,
                        label = outcome.label,
                        radius = outcome.blastRadius,
                        bornNanos = frameNanos
                    )
                    hitCount += 1
                } else {
                    val result = engine.fire(crosshair, enemies, aspect)
                    when (result) {
                        is FireResult.Hit -> {
                            val target = enemies.firstOrNull { it.id == result.enemy.id }
                            val remaining = if (target == null) {
                                1
                            } else if (target.maxHp > 0) {
                                target.hp
                            } else {
                                hpFor(target.id)
                            }
                            if (remaining > 1) {
                                // נפגעה ולא מתה: מאבדת חיים, מבהבהת ורצה
                                score += HURT_POINTS
                                val now = nowSeconds()
                                enemies = enemies.map {
                                    if (it.id == result.enemy.id) hurt(it, 1, now) else it
                                }
                            } else {
                                score = scoreManager.apply(score, result)
                                enemies = enemies.map {
                                    if (it.id == result.enemy.id) it.copy(isAlive = false) else it
                                }
                                hitCount += 1
                            }
                        }
                        is FireResult.Miss -> {
                            // פספוס: רק צליל, בלי שינוי נוסף
                        }
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 64.dp, bottom = 32.dp)
        )
    }
}
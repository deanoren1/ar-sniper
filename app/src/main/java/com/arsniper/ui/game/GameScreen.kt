package com.arsniper.ui.game

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.arsniper.audio.SoundPlayer
import com.arsniper.data.PhotoRepository
import com.arsniper.game.engine.Accessories
import com.arsniper.game.engine.AccessoryKind
import com.arsniper.game.engine.ArenaDirector
import com.arsniper.game.engine.ArenaPhase
import com.arsniper.game.engine.ComboTracker
import com.arsniper.game.engine.EnemyFire
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

/** ניקוד על עצירת קליע בלחיצה / בירי, ועל קטיעת הכנה לירייה (הריגת המפלצת באמצע ההכנה) */
private const val BLOCK_POINTS = 50
private const val CRACK_POINTS = 10
private const val STOP_POINTS = 100

/** מרווח נוסף סביב האצבע בלחיצה על קליע (ביחידות גובה מסך), ובירי עם הכוונת */
private const val TAP_SLACK = 0.06f
private const val FIRE_SLACK = 0.02f

/** כמה זמן נמשך הבהוב האדום כשהשחקן נפגע */
private const val FLASH_START = 0.55f
private const val FLASH_DECAY_PER_SECOND = 1.4f
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
    val director = remember { ArenaDirector() }
    val fire = remember { EnemyFire() }
    val combo = remember { ComboTracker() }
    val haptic = LocalHapticFeedback.current

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
    var started by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(0f) }

    // פיצוצים של נקודות תורפה, וזמן הפריים הנוכחי (מפעיל את האנימציה שלהם)
    var blasts by remember { mutableStateOf(emptyList<Blast>()) }
    var frameNanos by remember { mutableStateOf(0L) }
    var floaters by remember { mutableStateOf(emptyList<Floater>()) }

    // ניקוד עם מכפיל הקומבו, וניקוד צף במקום ההצלחה. register=false: לא מאריך את הרצף
    fun award(points: Int, x: Float, y: Float, register: Boolean = true) {
        if (register) combo.register()
        val gained = points * combo.multiplier
        score += gained
        val text = if (combo.multiplier > 1) "+$gained  x${combo.multiplier}" else "+$gained"
        floaters = floaters + Floater(x, y - 0.04f, text, frameNanos, big = points >= BLOCK_POINTS)
    }

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
            // המפלצות נוצרות על ידי מנהל הארנה, גל אחרי גל
            enemies = emptyList()
            fire.reset()
            director.restart()
            started = true
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
                    if (started && director.phase != ArenaPhase.Won && director.phase != ArenaPhase.Lost) {
                        enemies = withHp(engine.update(enemies, dt))
                        enemies = director.step(dt, enemies, engine, aspect)
                        score += director.takeBonus()

                        val out = fire.update(dt, enemies, director)
                        AttackPose.ids = fire.telegraphViews.map { it.enemyId }.toSet()
                        for (spot in out.stopped) {
                            award(STOP_POINTS, spot.x, spot.y)
                            blasts = blasts + Blast(
                                x = spot.x,
                                y = spot.y,
                                kind = AccessoryKind.Bomb,
                                label = "STOPPED!",
                                radius = 0.12f,
                                bornNanos = now
                            )
                        }
                        for (i in 0 until out.playerHits) {
                            if (director.hitPlayer()) {
                                combo.reset()
                                flash = FLASH_START
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    }
                    combo.update(dt)
                    if (floaters.isNotEmpty()) {
                        val live = floaters.filter { (now - it.bornNanos) / 1e9f <= FLOATER_SECONDS }
                        if (live.size != floaters.size) floaters = live
                    }
                    if (flash > 0f) flash = (flash - FLASH_DECAY_PER_SECOND * dt).coerceAtLeast(0f)
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
        }
    }

    // המצלמה עוקבת אחרי הכוונת, ובלי לצאת מגבולות התמונה
    val camera = GameCamera.following(crosshair, zoom)
    val crosshairOnScreen = camera.worldToScreen(crosshair)
    val latestCamera by rememberUpdatedState(camera)
    val latestAspect by rememberUpdatedState(aspect)

    // ניקוד ואפקט על קליע שנפגע (בלחיצה או בירי)
    fun onIntercept(result: com.arsniper.game.engine.Intercept) {
        val shot = result.shot
        if (result.destroyed) {
            award(BLOCK_POINTS, shot.x, shot.y)
            blasts = blasts + Blast(
                x = shot.x,
                y = shot.y,
                kind = AccessoryKind.Gem,
                label = "BLOCK!",
                radius = fire.currentRadius(shot) * 1.5f,
                bornNanos = frameNanos
            )
        } else {
            award(CRACK_POINTS, shot.x, shot.y)
            blasts = blasts + Blast(
                x = shot.x,
                y = shot.y,
                kind = AccessoryKind.Bomb,
                label = "CRACK!",
                radius = fire.currentRadius(shot),
                bornNanos = frameNanos
            )
        }
    }

    fun retry() {
        enemies = emptyList()
        blasts = emptyList()
        score = 0
        flash = 0f
        floaters = emptyList()
        combo.reset()
        fire.reset()
        AttackPose.ids = emptySet()
        director.restart()
    }

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
            .pointerInput(Unit) {
                // לחיצה ישירה על מה שהמפלצת יורה: עוצרת את הקליע
                detectTapGestures { offset ->
                    if (director.phase == ArenaPhase.Fighting && size.width > 0 && size.height > 0) {
                        val cam = latestCamera
                        val world = Vec2(
                            (offset.x / size.width - 0.5f) / cam.zoom + cam.center.x,
                            (offset.y / size.height - 0.5f) / cam.zoom + cam.center.y
                        )
                        val result = fire.intercept(world, latestAspect, TAP_SLACK)
                        if (result != null) onIntercept(result)
                    }
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
            CombatLayer(
                shots = fire.shots.toList(),
                telegraphs = fire.telegraphViews.toList(),
                time = fire.time,
                shotRadius = { fire.currentRadius(it) }
            )
            EffectsLayer(blasts = blasts, nowNanos = frameNanos)
            FloatingTextLayer(floaters = floaters, nowNanos = frameNanos)
        }

        // הכוונת מצוירת במיקום המסך שלה
        CrosshairOverlay(position = Offset(crosshairOnScreen.x, crosshairOnScreen.y))

        ScoreDisplay(
            score = score,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )

        ComboBadge(
            count = combo.count,
            multiplier = combo.multiplier,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 84.dp)
        )

        HitIndicator(
            visible = showHit,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 110.dp)
        )

        HeartsRow(
            hearts = director.hearts,
            max = ArenaDirector.MAX_HEARTS,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        )

        WaveStatus(
            wave = director.waveIndex + 1,
            waves = director.waveCount,
            left = director.leftInWave,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
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
                if (director.phase != ArenaPhase.Fighting) return@FireButton
                val blocked = fire.intercept(crosshair, aspect, FIRE_SLACK)
                if (blocked != null) {
                    onIntercept(blocked)
                    return@FireButton
                }
                val accessoryHit = Accessories.hit(crosshair, AccessoryRegistry.snapshot(), enemies, aspect)
                if (accessoryHit != null) {
                    // פגיעה בנקודת תורפה: פיצוץ, ניקוד גדול, ואולי עוד מפלצות נופלות
                    val outcome = Accessories.explode(accessoryHit, enemies, aspect)
                    award(outcome.points, outcome.anchor.x, outcome.anchor.y)
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
                                award(HURT_POINTS, target?.position?.x ?: result.enemy.position.x, target?.position?.y ?: result.enemy.position.y, register = false)
                                val now = nowSeconds()
                                enemies = enemies.map {
                                    if (it.id == result.enemy.id) hurt(it, 1, now) else it
                                }
                            } else {
                                val base = scoreManager.apply(score, result) - score
                                award(base, result.enemy.position.x, result.enemy.position.y)
                                enemies = enemies.map {
                                    if (it.id == result.enemy.id) it.copy(isAlive = false) else it
                                }
                                hitCount += 1
                            }
                        }
                        is FireResult.Miss -> {
                            // פספוס: שובר את הקומבו
                            combo.reset()
                        }
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 64.dp, bottom = 32.dp)
        )

        // כרזות בין הגלים
        if (director.phase == ArenaPhase.Intro) {
            ArenaBanner(
                title = "WAVE ${director.waveIndex + 1}",
                subtitle = if (director.waveIndex == director.waveCount - 1) "FINAL WAVE" else null,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (director.phase == ArenaPhase.Clear) {
            ArenaBanner(
                title = "WAVE CLEAR!",
                subtitle = if (director.healedOnClear) "+1 \u2665" else null,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // הבהוב אדום כשנפגעים
        DamageFlash(alpha = flash)

        if (director.phase == ArenaPhase.Won || director.phase == ArenaPhase.Lost) {
            EndOverlay(
                won = director.phase == ArenaPhase.Won,
                stars = director.stars,
                score = score,
                wave = director.waveIndex + 1,
                onRetry = { retry() }
            )
        }
    }
}
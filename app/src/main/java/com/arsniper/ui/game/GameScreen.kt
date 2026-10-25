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
import com.arsniper.audio.Sfx
import com.arsniper.data.PlayerProfile
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
import com.arsniper.game.engine.Loot
import com.arsniper.game.engine.LootKind
import com.arsniper.game.engine.Hover
import com.arsniper.game.engine.ShotResult
import com.arsniper.game.progress.Catalog
import com.arsniper.game.engine.Shockwave
import com.arsniper.game.engine.WeakZones
import com.arsniper.game.engine.Weapon
import com.arsniper.game.engine.SceneContext
import com.arsniper.game.engine.ScoreManager
import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import com.arsniper.game.model.EnemyState
import com.arsniper.game.model.GameCamera
import com.arsniper.game.model.Vec2
import com.arsniper.game.model.WorldMapper
import com.arsniper.ml.SceneSession
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.delay

/** ניקוד על עצירת קליע בלחיצה / בירי, ועל קטיעת הכנה לירייה (הריגת המפלצת באמצע ההכנה) */
private const val BLOCK_POINTS = 50
private const val CRACK_POINTS = 10
private const val STOP_POINTS = 100

/** מרווח נוסף סביב האצבע בלחיצה על קליע (ביחידות גובה מסך), ובירי עם הכוונת */
private const val TAP_SLACK = 0.06f

/** מחיר החייאה ביהלומים */
private const val REVIVE_COST = 3

/** מרחק הכדורים הצדדיים בנשק הפיזור (בקואורדינטות עולם) */
private const val SPREAD_OFFSET = 0.05f

/** ניקוד על כל מפלצת שההלם מחסל (חצי מהריגה רגילה, בלי קומבו) */
private const val SHOCK_KILL_POINTS = 100
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
private val MONSTER_HP = intArrayOf(2, 3, 1, 3, 3)

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
    // זיכרון קטן לזיהוי אירועי צליל בלולאת המשחק (לא מצב שמצויר, ולכן לא גורם לציור מחדש)
    val sfxMemo = remember { SfxMemo() }
    // הרובה: מחסנית וטעינה. ammoShown/reloadingShown מעדכנים את מד הכדורים רק כשהם משתנים
    val equippedId = remember { PlayerProfile.equipped }
    val stats = remember { PlayerProfile.equippedStats() }
    val weapon = remember { Weapon(stats.magazine, stats.reloadSeconds) }
    // הפניה לפונקציית הירי, כדי שלולאת המשחק תוכל לירות ברצף כשמחזיקים את הכפתור (Auto Gun)
    val fireRef = remember { arrayOfNulls<() -> Unit>(1) }
    val loot = remember { Loot() }
    val shock = remember { Shockwave() }
    var shockStart by remember { mutableStateOf(0L) }
    val weaponArt = rememberWeaponArt(equippedId)
    var ammoShown by remember { mutableStateOf(weapon.ammo) }
    var reloadingShown by remember { mutableStateOf(false) }
    var tracerTarget by remember { mutableStateOf<Offset?>(null) }
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
    // יהלומים שנאספו בריצה הזאת (לתצוגה בסוף)
    var diamondsGained by remember { mutableStateOf(0) }
    // מספר ההחייאות בריצה (מותר אחת)
    var revives by remember { mutableStateOf(0) }
    var started by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(0f) }
    // רעידת מסך (0..1): פגיעה בשחקן, שאגת בוס, מוות הבוס. דועכת מהר
    var shake by remember { mutableStateOf(0f) }

    // פיצוצים של נקודות תורפה, וזמן הפריים הנוכחי (מפעיל את האנימציה שלהם)
    var blasts by remember { mutableStateOf(emptyList<Blast>()) }
    var frameNanos by remember { mutableStateOf(0L) }
    var floaters by remember { mutableStateOf(emptyList<Floater>()) }
    var poofs by remember { mutableStateOf(emptyList<Poof>()) }

    // ניקוד עם מכפיל הקומבו, וניקוד צף במקום ההצלחה. register=false: לא מאריך את הרצף
    fun award(points: Int, x: Float, y: Float, register: Boolean = true) {
        val multiplierBefore = combo.multiplier
        if (register) combo.register()
        if (combo.multiplier > multiplierBefore) soundPlayer.play(Sfx.Combo, 0.9f + 0.1f * combo.multiplier)
        val gained = points * combo.multiplier
        score += gained
        val text = if (combo.multiplier > 1) "+$gained  x${combo.multiplier}" else "+$gained"
        floaters = floaters + Floater(x, y - 0.04f, text, frameNanos, big = points >= BLOCK_POINTS)
    }

    // יהלומים: נשמרים בפרופיל מיד, ומוצגים בסוף הריצה
    fun gainDiamonds(n: Int, label: String) {
        if (n <= 0) return
        PlayerProfile.addDiamonds(n)
        diamondsGained += n
        soundPlayer.play(Sfx.Gem)
        floaters = floaters + Floater(0.5f, 0.42f, label, frameNanos, big = true)
    }

    // מעביר את הניקוד שנצבר (מטבעות) לפרופיל השמור. בטוח לקריאה חוזרת: מעביר רק את ההפרש
    fun bankRun() {
        val delta = score - sfxMemo.banked
        if (delta > 0) {
            PlayerProfile.addCoins(delta)
            sfxMemo.banked = score
        }
    }
    DisposableEffect(Unit) {
        onDispose { bankRun() }
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
                        // מפלצת שמתה (חוץ מהבוס ומהמפלצות שמתפוצצות איתו): אולי נופל פרס
                        for (e in enemies) {
                            if (!e.isAlive && e.id != ArenaDirector.BOSS_ID && !director.bossDying &&
                                director.foeOf(e.id) != null && sfxMemo.lootSeen.add(e.id)
                            ) {
                                loot.onKill(e.position.x, e.position.y, director.hearts, ArenaDirector.MAX_HEARTS, weapon.ammo, weapon.magSize)
                            }
                        }
                        loot.update(dt)
                        enemies = director.step(dt, enemies, engine, aspect)
                        score += director.takeBonus()
                        for (jump in director.takeTeleports()) {
                            poofs = poofs + Poof(jump.from.x, jump.from.y, now) + Poof(jump.to.x, jump.to.y, now)
                        }

                        val out = fire.update(dt, Hover.lifted(enemies, nowSeconds()), director)
                        val telegraphing = fire.telegraphViews.map { it.enemyId }.toSet()
                        val newlyTelegraphing = telegraphing - AttackPose.ids
                        if (newlyTelegraphing.isNotEmpty()) {
                            soundPlayer.play(if (ArenaDirector.BOSS_ID in newlyTelegraphing) Sfx.BossRoar else Sfx.Telegraph)
                            if (ArenaDirector.BOSS_ID in newlyTelegraphing) shake = max(shake, 0.3f)
                        }
                        if (fire.launchCount != sfxMemo.launches) {
                            if (fire.launchCount > sfxMemo.launches) soundPlayer.play(Sfx.Launch)
                            sfxMemo.launches = fire.launchCount
                        }
                        AttackPose.ids = telegraphing
                        AttackPose.progress = fire.telegraphViews.associate { it.enemyId to it.progress }
                        AttackPose.bossSinceFire = fire.secondsSinceLaunch(ArenaDirector.BOSS_ID)
                        AttackPose.bossStunned = fire.isDisabled(ArenaDirector.BOSS_ID)
                        AttackPose.bossEnraged = director.bossEnraged
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
                                soundPlayer.play(Sfx.PlayerHit)
                                sfxMemo.damagedThisWave = true
                                shake = max(shake, 0.7f)
                                combo.reset()
                                flash = FLASH_START
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                        // מעברי שלב: גל חדש, גל נגמר, ניצחון, הפסד
                        if (director.phase != sfxMemo.phase) {
                            when (director.phase) {
                                ArenaPhase.Intro -> {
                                    sfxMemo.damagedThisWave = false
                                    weapon.refill()
                                    soundPlayer.play(Sfx.WaveStart)
                                }
                                ArenaPhase.Clear -> {
                                    soundPlayer.play(Sfx.WaveClear)
                                    if (!sfxMemo.damagedThisWave) gainDiamonds(1, "NO DAMAGE  +1 DIAMOND")
                                }
                                ArenaPhase.Won -> {
                                    soundPlayer.play(Sfx.Win)
                                    val bonus = (if (director.isBossWave) 5 else 0) + (if (director.stars == 3) 2 else 0)
                                    gainDiamonds(bonus, "VICTORY  +$bonus DIAMONDS")
                                    bankRun()
                                }
                                ArenaPhase.Lost -> {
                                    soundPlayer.play(Sfx.Lose)
                                    bankRun()
                                }
                                else -> Unit
                            }
                            sfxMemo.phase = director.phase
                        }
                    }
                    combo.update(dt)
                    weapon.update(dt)
                    // ירי רצוף בהחזקת הכפתור (נשק עם autoRate)
                    if (sfxMemo.holding && stats.autoRate > 0f && director.phase == ArenaPhase.Fighting) {
                        sfxMemo.autoTimer += dt
                        val interval = 1f / stats.autoRate
                        var guard = 0
                        while (sfxMemo.autoTimer >= interval && guard++ < 3) {
                            sfxMemo.autoTimer -= interval
                            fireRef[0]?.invoke()
                        }
                    }
                    if (director.phase == ArenaPhase.Fighting && !director.bossDying) shock.update(dt)
                    if (weapon.ammo != ammoShown) ammoShown = weapon.ammo
                    if (weapon.reloading != reloadingShown) {
                        if (weapon.reloading) soundPlayer.play(Sfx.Reload)
                        reloadingShown = weapon.reloading
                    }
                    if (poofs.isNotEmpty()) {
                        val live = poofs.filter { (now - it.bornNanos) / 1e9f <= POOF_SECONDS }
                        if (live.size != poofs.size) poofs = live
                    }
                    if (floaters.isNotEmpty()) {
                        val live = floaters.filter { (now - it.bornNanos) / 1e9f <= FLOATER_SECONDS }
                        if (live.size != floaters.size) floaters = live
                    }
                    if (flash > 0f) flash = (flash - FLASH_DECAY_PER_SECOND * dt).coerceAtLeast(0f)
                    if (director.bossDying) shake = max(shake, 0.55f)
                }
                if (director.phase == ArenaPhase.Won || director.phase == ArenaPhase.Lost) {
                    shake = 0f
                } else if (shake > 0f) {
                    shake = (shake - 2.0f * dt).coerceAtLeast(0f)
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

    // הבוס מת: שאגה אחרונה ופיצוץ
    LaunchedEffect(director.bossDying) {
        if (director.bossDying) {
            soundPlayer.play(Sfx.BossRoar)
            soundPlayer.play(Sfx.EnemyDie, 0.7f)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // הבוס נכנס לשליש האחרון: כרזה קצרה ושאגה
    var rageBanner by remember { mutableStateOf(false) }
    LaunchedEffect(director.bossEnraged) {
        if (director.bossEnraged) {
            soundPlayer.play(Sfx.BossRoar)
            shake = max(shake, 0.9f)
            rageBanner = true
            delay(1700)
            rageBanner = false
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

    // הלם: מחסל את כל המפלצות הרגילות וכל הקליעים. הבוס לא מת: מאבד 15% מהחיים ומסוחרר
    fun triggerShock() {
        if (director.phase != ArenaPhase.Fighting || director.bossDying) return
        if (!shock.trigger()) return
        soundPlayer.play(Sfx.Boom)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        shake = max(shake, 1.3f)
        shockStart = frameNanos
        fire.clearAll()
        val now = nowSeconds()
        var kills = 0
        enemies = enemies.map { e ->
            if (!e.isAlive) {
                e
            } else if (e.id == ArenaDirector.BOSS_ID) {
                val cut = kotlin.math.ceil(e.maxHp * Shockwave.BOSS_DAMAGE_FRACTION).toInt()
                e.copy(hp = (e.hp - cut).coerceAtLeast(1), hurtAt = now)
            } else {
                kills++
                sfxMemo.lootSeen.add(e.id)
                e.copy(isAlive = false)
            }
        }
        fire.stun(ArenaDirector.BOSS_ID, Shockwave.BOSS_STUN_SECONDS)
        if (kills > 0) {
            score += kills * SHOCK_KILL_POINTS
            floaters = floaters + Floater(0.5f, 0.35f, "SHOCKWAVE  +${kills * SHOCK_KILL_POINTS}", frameNanos, big = true)
        }
    }

    // איסוף פרס: לב מחזיר חיים (אם כבר מלא: נקודות), כדורים ממלאים את המחסנית מיד
    fun collectLoot(drop: com.arsniper.game.engine.LootDrop) {
        soundPlayer.play(Sfx.Pickup)
        val label: String
        when (drop.kind) {
            LootKind.Heart -> {
                if (director.heal()) {
                    label = "+1 HEART"
                    flash = 0f
                } else {
                    score += 150
                    label = "+150"
                }
            }
            LootKind.Ammo -> {
                weapon.topUp()
                label = "AMMO FULL"
            }
            LootKind.Diamond -> {
                gainDiamonds(1, "+1 DIAMOND")
                return
            }
        }
        floaters = floaters + Floater(drop.x, drop.y - 0.1f, label, frameNanos, big = true)
    }

    // ניקוד ואפקט על קליע שנפגע (בלחיצה או בירי)
    fun onIntercept(result: com.arsniper.game.engine.Intercept) {
        val shot = result.shot
        soundPlayer.play(if (result.destroyed) Sfx.Deflect else Sfx.Crack)
        if (result.destroyed) shock.reward()
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

    // ירייה אחת בנקודה p (קואורדינטות עולם) עם נזק dmg: קליע של מפלצת, נקודת תורפה, או מפלצת
    fun shootAt(p: Vec2, dmg: Int) {
        val blocked = fire.intercept(p, aspect, FIRE_SLACK)
        if (blocked != null) {
            onIntercept(blocked)
            return
        }
        val zoneHit = WeakZones.hit(p, ZoneRegistry.snapshot(), enemies, aspect)
        if (zoneHit != null) {
            // פגיעה בנקודת תורפה: ההשפעה של הנקודה (השבתה, בלבול, נזק מוגבר...) וניקוד
            val zone = zoneHit.zone
            val target = enemies.firstOrNull { it.id == zoneHit.enemy.id }
            if (target != null) {
                val now = nowSeconds()
                val remaining = if (target.maxHp > 0) target.hp else hpFor(target.id)
                val zoneDamage = if (zone.damage > 0) zone.damage + (dmg - 1) else 0
                val killed = zoneDamage > 0 && zoneDamage >= remaining
                enemies = enemies.map {
                    if (it.id != target.id) {
                        it
                    } else if (killed) {
                        it.copy(isAlive = false)
                    } else if (zoneDamage > 0) {
                        hurt(it, zoneDamage, now)
                    } else {
                        it
                    }
                }
                fire.applyZone(target.id, zone)
                val balloons = zone.id == WeakZones.BALLOONS.id
                soundPlayer.play(
                    if (balloons) Sfx.BalloonPop else if (killed) Sfx.EnemyDie else Sfx.Zone,
                    if (balloons) 0.9f + 0.1f * (remaining % 3) else 1f
                )
                if (!killed && !balloons && zone.damage > 0) soundPlayer.play(Sfx.EnemyHurt)
                award(if (balloons && killed) 300 else zone.points, zoneHit.anchor.x, zoneHit.anchor.y)
                blasts = blasts + Blast(
                    x = zoneHit.anchor.x,
                    y = zoneHit.anchor.y,
                    kind = if (balloons) AccessoryKind.Balloon else if (zone.damage >= 2) AccessoryKind.Bomb else AccessoryKind.Gem,
                    label = zone.label,
                    radius = zoneHit.hitRadius * 1.6f,
                    bornNanos = frameNanos
                )
                if (killed) {
                    hitCount += 1
                    shock.reward()
                }
            }
        } else {
            val result = engine.fire(p, Hover.lifted(enemies, nowSeconds()), aspect)
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
                    if (remaining > dmg) {
                        // נפגעה ולא מתה: מאבדת חיים, מבהבהת ורצה
                        soundPlayer.play(Sfx.EnemyHurt)
                        award(HURT_POINTS, target?.position?.x ?: result.enemy.position.x, target?.position?.y ?: result.enemy.position.y, register = false)
                        val now = nowSeconds()
                        enemies = enemies.map {
                            if (it.id == result.enemy.id) hurt(it, dmg, now) else it
                        }
                    } else {
                        soundPlayer.play(Sfx.EnemyDie)
                        val base = scoreManager.apply(score, result) - score
                        award(base, result.enemy.position.x, result.enemy.position.y)
                        enemies = enemies.map {
                            if (it.id == result.enemy.id) it.copy(isAlive = false) else it
                        }
                        hitCount += 1
                        shock.reward()
                    }
                }
                is FireResult.Miss -> {
                    // פספוס: שובר את הקומבו
                    combo.reset()
                }
            }
        }
    }

    // לחיצה על ירי: פרס בכוונת, מחסנית/טעינה, ואז כל הכדורים של הנשק (פיזור = כמה כדורים)
    fun fireNow() {
        if (director.phase != ArenaPhase.Fighting) {
            soundPlayer.playShot()
            return
        }
        // כוונת על פרס: אוספים אותו (בלי לצרוך כדור)
        val picked = loot.collect(crosshair, aspect, FIRE_SLACK + 0.03f)
        if (picked != null) {
            collectLoot(picked)
            return
        }
        when (weapon.tryFire()) {
            ShotResult.Fired -> {
                soundPlayer.playShot()
                tracerTarget = Offset(crosshairOnScreen.x, crosshairOnScreen.y)
            }
            else -> {
                // מחסנית ריקה או בטעינה: רק נקישה
                soundPlayer.play(Sfx.Empty)
                return
            }
        }
        val offsets = if (stats.pellets > 1) floatArrayOf(-SPREAD_OFFSET, 0f, SPREAD_OFFSET) else floatArrayOf(0f)
        for (offset in offsets) {
            val dmg = Catalog.rollDamage(stats.damage, kotlin.random.Random.nextFloat())
            shootAt(Vec2(crosshair.x + offset, crosshair.y), dmg)
        }
    }
    fireRef[0] = { fireNow() }

    // החייאה ביהלומים: חוזרים לאותו גל עם 2 לבבות, מחסנית והלם מלאים
    fun reviveRun() {
        if (director.phase != ArenaPhase.Lost || revives >= 1) return
        if (!PlayerProfile.spendDiamonds(REVIVE_COST)) return
        revives += 1
        enemies = emptyList()
        blasts = emptyList()
        poofs = emptyList()
        loot.reset()
        fire.reset()
        weapon.refill()
        shock.chargeNow()
        AttackPose.ids = emptySet()
        AttackPose.progress = emptyMap()
        AttackPose.bossSinceFire = 999f
        AttackPose.bossStunned = false
        AttackPose.bossEnraged = false
        flash = 0f
        sfxMemo.phase = null
        sfxMemo.damagedThisWave = true
        director.revive()
    }

    fun retry() {
        bankRun()
        revives = 0
        enemies = emptyList()
        blasts = emptyList()
        score = 0
        sfxMemo.banked = 0
        diamondsGained = 0
        flash = 0f
        floaters = emptyList()
        poofs = emptyList()
        combo.reset()
        fire.reset()
        weapon.reset()
        shock.reset()
        shockStart = 0L
        loot.reset()
        sfxMemo.lootSeen.clear()
        AttackPose.ids = emptySet()
        AttackPose.progress = emptyMap()
        AttackPose.bossSinceFire = 999f
        AttackPose.bossStunned = false
        AttackPose.bossEnraged = false
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
                        val picked = loot.collect(Vec2(world.x, world.y), latestAspect, TAP_SLACK)
                        if (picked != null) {
                            collectLoot(picked)
                        } else {
                            val result = fire.intercept(world, latestAspect, TAP_SLACK)
                            if (result != null) onIntercept(result)
                        }
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
                    val t = frameNanos / 1e9f
                    val shakeX = sin(t * 83f) * shake * size.width * 0.012f
                    val shakeY = cos(t * 97f) * shake * size.height * 0.016f
                    translationX = (0.5f - camera.center.x * camera.zoom) * size.width + shakeX
                    translationY = (0.5f - camera.center.y * camera.zoom) * size.height + shakeY
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
            LootLayer(loot = loot, nowNanos = frameNanos)
            EffectsLayer(blasts = blasts, nowNanos = frameNanos)
            PoofLayer(poofs = poofs, nowNanos = frameNanos)
            FloatingTextLayer(floaters = floaters, nowNanos = frameNanos)
        }

        // הכוונת מצוירת במיקום המסך שלה
        CrosshairOverlay(position = Offset(crosshairOnScreen.x, crosshairOnScreen.y))

        CurrencyBar(
            coins = score,
            diamonds = PlayerProfile.diamonds,
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

        // הרובה בתחתית המסך (בלי מגע), ומד הכדורים מעל כפתור הירי
        if (weaponArt != null) {
            WeaponLayer(art = weaponArt, weapon = weapon, nowNanos = frameNanos, tracerTarget = tracerTarget)
        }
        ShockButton(
            fraction = shock.fraction,
            ready = shock.ready && director.phase == ArenaPhase.Fighting,
            nowNanos = frameNanos,
            onClick = { triggerShock() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 28.dp, bottom = 196.dp)
        )

        AmmoBar(
            ammo = ammoShown,
            magSize = weapon.magSize,
            reloading = reloadingShown,
            onReload = { weapon.reload() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 136.dp)
        )

        Joystick(
            onMove = { joystick = it },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 64.dp, bottom = 32.dp)
        )

        FireButton(
            onPress = {
                sfxMemo.holding = true
                sfxMemo.autoTimer = 0f
                fireNow()
            },
            onRelease = { sfxMemo.holding = false },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 64.dp, bottom = 32.dp)
        )

        // קו החיים של הבוס, מתחת לסטטוס הגל
        if (director.isBossWave && director.bossMax > 0 &&
            (director.phase == ArenaPhase.Fighting || director.phase == ArenaPhase.Won)
        ) {
            BossBar(
                fraction = director.bossFraction,
                enraged = director.bossEnraged,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 86.dp)
            )
        }
        if (rageBanner) {
            ArenaBanner(
                title = "ENRAGED!",
                subtitle = "THE DRAGON FIRES FASTER",
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // כרזות בין הגלים
        if (director.phase == ArenaPhase.Intro) {
            ArenaBanner(
                title = if (director.isBossWave) "BOSS!" else "WAVE ${director.waveIndex + 1}",
                subtitle = if (director.isBossWave) "SHOOT THE MOUTH WHEN IT GLOWS" else null,
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

        ShockwaveEffect(startNanos = shockStart, nowNanos = frameNanos)

        // הבהוב אדום כשנפגעים
        DamageFlash(alpha = flash)

        if (director.phase == ArenaPhase.Won || director.phase == ArenaPhase.Lost) {
            EndOverlay(
                won = director.phase == ArenaPhase.Won,
                stars = director.stars,
                score = score,
                wave = director.waveIndex + 1,
                diamondsGained = diamondsGained,
                totalCoins = PlayerProfile.coins,
                reviveCost = if (revives < 1) REVIVE_COST else 0,
                diamonds = PlayerProfile.diamonds,
                onRevive = { reviveRun() },
                onRetry = { retry() }
            )
        }
    }
}
/** מה כבר ננגן ממנו צליל: כמה קליעים יצאו, ובאיזה שלב היה הארנה בפריים הקודם */
private class SfxMemo {
    /** כמה מהניקוד כבר נכנס למטבעות השמורים */
    var banked: Int = 0

    /** האם נפגעתי בגל הנוכחי (לבונוס "גל בלי נזק") */
    var damagedThisWave: Boolean = false
    val lootSeen = HashSet<Int>()
    var launches: Int = 0

    /** כפתור הירי לחוץ, וכמה זמן עבר מהירייה הרצופה האחרונה */
    var holding: Boolean = false
    var autoTimer: Float = 0f
    var phase: ArenaPhase? = null
}

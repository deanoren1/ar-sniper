package com.arsniper.ui.game

import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.asAndroidBitmap
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.platform.LocalDensity
import com.arsniper.pano.HeadingTracker
import com.arsniper.pano.PanoramaStore
import com.arsniper.pano.wrapAngle
import com.arsniper.game.engine.Shot
import com.arsniper.game.engine.ShotKind
import com.arsniper.game.engine.TelegraphView
import com.arsniper.coop.CoopClient
import com.arsniper.coop.CoopStatus
import com.arsniper.coop.CoopSync
import com.arsniper.ui.components.OutlinedText
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
import com.arsniper.game.engine.LootDrop
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
import com.arsniper.game.progress.ArenaStyle
import androidx.activity.compose.BackHandler
import com.arsniper.ui.components.GameIcon
import com.arsniper.ui.components.GameIconButton
import com.arsniper.ml.SceneSession
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.delay

/** ניקוד על עצירת קליע בלחיצה / בירי, ועל קטיעת הכנה לירייה (הריגת המפלצת באמצע ההכנה) */
/** הניקוד הפנימי מחולק בעשר: כך מטבעות הם מספרים קטנים ונוחים */
private const val COINS_DIVISOR = 35
/** מסגרת הקישוטים מעל ארנה מעוצבת (טונדרה): כבויה בזמן הבדיקה; להחזרה: true */
private const val SHOW_FRAME_ON_THEMED = true

private const val NEW_ARENA_COINS = 60
private const val NEW_ARENA_MILESTONE_GEMS = 3
private val NEW_ARENA_MILESTONES = setOf(5, 10, 25, 50)
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

/** צלילים שהמארח שולח גם לשותף (פגיעות והשמדה). צלילי הירייה והגלים של האורח נוצרים אצלו */
private val COOP_SHARED_SFX = setOf(
    Sfx.Telegraph, Sfx.Launch, Sfx.BossRoar, Sfx.Deflect, Sfx.Crack,
    Sfx.Zone, Sfx.EnemyHurt, Sfx.EnemyDie, Sfx.BalloonPop, Sfx.Boom
)

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

private fun hpFor(id: Int): Int = MONSTER_HP[((id % MONSTER_HP.size) + MONSTER_HP.size) % MONSTER_HP.size] * ArenaDirector.HP_SCALE

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
fun GameScreen(
    style: ArenaStyle = ArenaStyle.Classic,
    onExit: () -> Unit = {},
    onNextArena: (() -> Unit)? = null,
    /** משחק משותף: null = רגיל, "host" = מארח (מריץ הכל), "guest" = אורח (מציג ויורה) */
    coop: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isGuest = coop == "guest"
    val isHost = coop == "host"
    val photo = remember { PhotoRepository.load(context)?.asImageBitmap() }
    val frameVariant = 0 // מסגרת טונדרה מעוצבת: הפוטוריאליסטית (arena_frame_tundra_v0)
    var themed by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
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
    // משחק משותף (מארח): ירייה והקשה של השותף מופעלות דרך אותן פונקציות כמו של השחקן
    val coopShootRef = remember { arrayOfNulls<(Vec2, Int) -> Unit>(1) }
    // (נקודה, מרווח רחב: האורח לחץ ירי כשהכוונת על פרס)
    val coopTapRef = remember { arrayOfNulls<(Vec2, Boolean) -> Unit>(1) }
    // פקודות האורח: shock / revive / retry
    val coopCmdRef = remember { arrayOfNulls<(String) -> Unit>(1) }
    // משחק משותף: הכוונת של השותף (אצל האורח: של המארח, מתוך תמונת המצב)
    var hostCross by remember { mutableStateOf<Vec2?>(null) }
    // משחק משותף: ניקוד לכל שחקן (0 = מארח, 1 = אורח), ומי יורה כרגע (לשיוך הניקוד)
    val scoreBy = remember { intArrayOf(0, 0) }
    val shooter = remember { intArrayOf(0) }
    // משחק משותף (מארח): אפקטים שכבר נשלחו לשותף
    val seenFx = remember { HashSet<Any>() }
    val loot = remember { Loot() }
    val shock = remember { Shockwave() }
    var shockStart by remember { mutableStateOf(0L) }
    val weaponArt = rememberWeaponArt(equippedId)
    var ammoShown by remember { mutableStateOf(weapon.ammo) }
    var reloadingShown by remember { mutableStateOf(false) }
    var tracerTarget by remember { mutableStateOf<Offset?>(null) }
    val difficulty = remember { PlayerProfile.difficulty }
    val director = remember { ArenaDirector(difficulty.apply(ArenaDirector.DEFAULT_WAVES)) }
    // התגמול בהתאם לקושי; כל מקום שמצטבר בו ניקוד עובר דרך זה, כדי שהמספרים יהיו עקביים
    fun scaled(points: Int): Int = (points * difficulty.rewardScale).roundToInt()
    val context0 = LocalContext.current
    var photoBonus by remember { mutableStateOf<String?>(null) }

    // בונוס "זירה חדשה": פעם אחת לכל תמונה, בסיום הקרב הראשון עליה
    fun photoBonusOnEnd() {
        if (photoBonus != null) return
        val file = PhotoRepository.currentFile(context0) ?: return
        val key = try { com.arsniper.ml.AnalysisCache.keyFor(file) } catch (_: Exception) { return }
        if (!PlayerProfile.registerPhoto(key)) return
        PlayerProfile.addCoins(NEW_ARENA_COINS)
        val count = PlayerProfile.photoCount
        if (count in NEW_ARENA_MILESTONES) {
            PlayerProfile.addDiamonds(NEW_ARENA_MILESTONE_GEMS)
            photoBonus = "NEW ARENA  +$NEW_ARENA_COINS COINS  +$NEW_ARENA_MILESTONE_GEMS GEMS ($count arenas)"
        } else {
            photoBonus = "NEW ARENA  +$NEW_ARENA_COINS COINS"
        }
    }
    val fire = remember { EnemyFire() }
    val combo = remember { ComboTracker() }
    val haptic = LocalHapticFeedback.current

    DisposableEffect(Unit) {
        onDispose { soundPlayer.release() }
    }
    // משחק משותף (מארח): צלילי פגיעה והשמדה נשלחים גם לשותף
    DisposableEffect(isHost) {
        if (isHost) soundPlayer.onPlay = { s, r -> if (s in COOP_SHARED_SFX) CoopSync.queueSound(s.ordinal, r) }
        onDispose { soundPlayer.onPlay = null }
    }

    // הכוונת נשמרת בקואורדינטות עולם (0..1 ביחס לתמונה)
    var crosshair by remember { mutableStateOf(Vec2(0.5f, 0.5f)) }
    var joystick by remember { mutableStateOf(Vec2.ZERO) }
    // פנורמה: העולם רחב מהמסך. aspect = יחס העולם (כל התמונה), viewAspect = יחס המסך
    val photoRatio = photo?.let { it.width.toFloat() / it.height } ?: 0f
    val panoMode = photoRatio >= 2.6f
    // פנורמה 360: ring = כמה מהעולם הוא המעגל המלא, ext = ההרחבה בכל קצה. הכוונת עוטפת בין הקצוות
    val panoInfo = remember { if (panoMode) com.arsniper.data.PhotoLibrary.panoInfo(context) else null }
    val ring = panoInfo?.ring ?: 1f
    val ext = panoInfo?.ext ?: 0f
    val wraps = panoMode && ring < 0.999f && ext > 0f
    var aspect by remember { mutableStateOf(2f) }
    var viewAspect by remember { mutableStateOf(2f) }
    var screenHeightPx by remember { mutableStateOf(1) }
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
    var paused by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(0f) }
    // שניות שנותרו לאפקט הפגיעה של כדור האנרגיה במסך (0 = אין)
    var orbImpact by remember { mutableStateOf(0f) }
    var lostAge by remember { mutableStateOf(0f) } // זמן מאז ההפסד: מעכב את מסך הסיום עד שאפקט הפגיעה נגמר
    // נקודות תורפה בלי נזק שכבר נוצלו (מפלצת:נקודה), לנעילת ניצול חוזר
    val usedFreeZones = remember { HashSet<String>() }
    // רעידת מסך (0..1): פגיעה בשחקן, שאגת בוס, מוות הבוס. דועכת מהר
    var shake by remember { mutableStateOf(0f) }

    // פיצוצים של נקודות תורפה, וזמן הפריים הנוכחי (מפעיל את האנימציה שלהם)
    var blasts by remember { mutableStateOf(emptyList<Blast>()) }
    var frameNanos by remember { mutableStateOf(0L) }
    var floaters by remember { mutableStateOf(emptyList<Floater>()) }
    // הודעת בונוס אחת במקום קבוע בראש המסך (החדשה מחליפה את הקודמת)
    var toast by remember { mutableStateOf<BonusToastData?>(null) }
    fun showToast(text: String) {
        if (text.isNotEmpty()) {
            toast = BonusToastData(text, frameNanos)
            if (isHost) CoopSync.queueToast(text)
        }
    }
    var poofs by remember { mutableStateOf(emptyList<Poof>()) }

    // ניקוד עם מכפיל הקומבו, וניקוד צף במקום ההצלחה. register=false: לא מאריך את הרצף
    fun award(points: Int, x: Float, y: Float, register: Boolean = true, showText: Boolean = true) {
        val multiplierBefore = combo.multiplier
        if (register) combo.register()
        if (combo.multiplier > multiplierBefore) soundPlayer.play(Sfx.Combo, 0.9f + 0.1f * combo.multiplier)
        val gained = scaled(points * combo.multiplier)
        score += gained
        scoreBy[shooter[0]] += gained
        val shown = maxOf(1, gained / COINS_DIVISOR)
        val text = if (combo.multiplier > 1) "+$shown  x${combo.multiplier}" else "+$shown"
        if (showText) floaters = floaters + Floater(x, y - 0.04f, text, frameNanos, big = points >= BLOCK_POINTS)
    }

    // יהלומים: נשמרים בפרופיל מיד, ומוצגים בסוף הריצה
    fun gainDiamonds(n: Int, label: String) {
        if (n <= 0) return
        PlayerProfile.addDiamonds(n)
        diamondsGained += n
        soundPlayer.play(Sfx.Gem)
        showToast(label)
    }

    // מעביר את הניקוד שנצבר (מטבעות) לפרופיל השמור. בטוח לקריאה חוזרת: מעביר רק את ההפרש
    fun bankRun() {
        val total = score / COINS_DIVISOR
        val delta = total - sfxMemo.banked
        if (delta > 0) {
            PlayerProfile.addCoins(delta)
            sfxMemo.banked = total
        }
    }
    DisposableEffect(Unit) {
        onDispose { bankRun() }
    }

    var noRoom by remember { mutableStateOf(false) }
    if (noRoom) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { androidx.compose.material3.Text("No room for monsters") },
            text = {
                androidx.compose.material3.Text(
                    "This photo has no floor or hiding places the monsters can use.\n" +
                        "Take a new photo with a visible floor or ground and a few objects (a table, a door, plants)."
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(onClick = onExit) {
                    androidx.compose.material3.Text("RETAKE")
                }
            }
        )
    }

    // אחרי שגודל המסך ידוע: בונים את הסצנה (מחבואים לפי ניתוח התמונה) ויוצרים אויבים
    LaunchedEffect(sizeKnown) {
        if (sizeKnown) {
            val analysis = SceneSession.analysis
            val sceneContext = if (analysis != null) {
                SceneContext(
                    analysis = analysis,
                    // 360: ההרחבות בקצוות הן רק המשך ציור. מפלצות ומחבואים נשארים באמצע (המעגל האמיתי)
                    spots = if (wraps) SceneSession.spots.filter { (it.minX + it.maxX) / 2f in (ext + 0.01f)..(1f - ext - 0.01f) } else SceneSession.spots,
                    mapper = WorldMapper(analysis.width.toFloat() / analysis.height, aspect),
                    luma = SceneSession.luma,
                    walkMinX = if (wraps) ext + 0.02f else 0.04f,
                    walkMaxX = if (wraps) 1f - ext - 0.02f else 0.96f
                )
            } else {
                null
            }
            scene = sceneContext
            engine.configure(sceneContext)
            // עיצוב הארנה לפי גיאומטריית התמונה (טונדרה: שלג, כפור, קרחונים); מטמון בדיסק
            if (analysis != null && photo != null && style == ArenaStyle.Tundra) {
                val file = PhotoRepository.currentFile(context)
                if (file != null) {
                    themed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        try {
                            val key = com.arsniper.ml.AnalysisCache.keyFor(file)
                            com.arsniper.ml.ThemeBake.bake(context, style, photo.asAndroidBitmap(), analysis, key)?.asImageBitmap()
                        } catch (_: Throwable) {
                            null
                        }
                    }
                }
            }
            if (isGuest) {
                // האורח לא יוצר מפלצות: הוא מציג את מה שהמארח שולח
                CoopSync.sendGuestReady()
                return@LaunchedEffect
            }
            // תמונה בלי רצפה או מחבואים (למשל אמבטיה מלאה במשטחים): אין איפה לשים מפלצות. לא מתחילים משחק ריק.
            if (sceneContext != null && engine.replenish(emptyList(), 4, aspect).size < 2) {
                noRoom = true
                return@LaunchedEffect
            }
            // המפלצות נוצרות על ידי מנהל הארנה, גל אחרי גל
            enemies = emptyList()
            fire.reset()
            if (isHost) {
                // מחכים (עד 10 שניות) שהמסך של האורח יהיה מוכן
                var waited = 0
                while (!CoopSync.guestReady && waited < 100) {
                    delay(100)
                    waited += 1
                }
            }
            director.restart()
            started = true
        }
    }

    // משחק משותף (אורח): מחבואים לפי מזהה, כדי לחבר את המפלצות שהגיעו מהמארח לאותם מחבואים
    val spotMap by rememberUpdatedState(remember(scene) { scene?.spots?.associateBy { it.id } ?: emptyMap() })

    // לולאת משחק: מעדכנת את הכוונת בכל פריים
    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        var lastAimNanos = 0L
        var lastSnapNanos = 0L
        var snapSeq = 0
        while (true) {
            withFrameNanos { now ->
                if (lastFrameNanos != 0L) {
                    val dt = if (paused) 0f else (now - lastFrameNanos) / 1_000_000_000f
                    crosshair = engine.moveCrosshair(crosshair, joystick, dt, aspect, zoom / BASE_ZOOM, viewAspect)
                    if (wraps) {
                        var wx = crosshair.x
                        if (wx > 1f - ext) wx -= ring else if (wx < ext) wx += ring
                        if (wx != crosshair.x) crosshair = Vec2(wx, crosshair.y)
                    }
                    if (!isGuest && started && director.phase != ArenaPhase.Won && director.phase != ArenaPhase.Lost) {
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
                        score += scaled(director.takeBonus())
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
                        AttackPose.sinceLaunch = enemies.mapNotNull { e ->
                            val t = fire.secondsSinceLaunch(e.id)
                            if (t < AttackPose.RELEASE_SECONDS) e.id to t else null
                        }.toMap()
                        AttackPose.bossStunned = fire.isDisabled(ArenaDirector.BOSS_ID)
                        AttackPose.bossEnraged = director.bossEnraged
                        for (spot in out.stopped) {
                            award(STOP_POINTS, spot.x, spot.y)
                            showToast("STOPPED!")
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
                                val orbHit = out.hitKinds.getOrNull(i) == com.arsniper.game.engine.ShotKind.Orb
                                // כדור אנרגיה: אפקט סגול במסך, וההבהוב האדום חלש יותר כדי שלא ייווצר ערבוב עכור
                                flash = if (orbHit) FLASH_START * 0.4f else FLASH_START
                                if (orbHit) orbImpact = OrbFx.IMPACT_SECONDS
                                if (PlayerProfile.vibrationOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                    PlayerProfile.recordStars(style, director.stars)
                                    PlayerProfile.recordRun(true, director.waveIndex + 1, score / COINS_DIVISOR)
                                    bankRun()
                                    photoBonusOnEnd()
                                }
                                ArenaPhase.Lost -> {
                                    soundPlayer.play(Sfx.Lose)
                                    PlayerProfile.recordRun(false, director.waveIndex + 1, score / COINS_DIVISOR)
                                    bankRun()
                                    photoBonusOnEnd()
                                }
                                else -> Unit
                            }
                            sfxMemo.phase = director.phase
                        }
                    }
                    if (isGuest) {
                        // אורח: מציג את תמונת המצב של המארח, ושולח לו את הכוונת
                        val view = CoopSync.view(System.nanoTime())
                        if (view != null) {
                            val snap = view.latest
                            val nowSec = nowSeconds()
                            val kinds = EnemyKind.values()
                            val states = EnemyState.values()
                            enemies = view.enemies.map { se ->
                                Enemy(
                                    id = se.id,
                                    position = Vec2(se.x, se.y),
                                    radius = se.r,
                                    isAlive = se.alive,
                                    kind = kinds[se.kind.coerceIn(0, kinds.size - 1)],
                                    state = states[se.state.coerceIn(0, states.size - 1)],
                                    spot = if (se.spotId >= 0) spotMap[se.spotId] else null,
                                    hidePos = Vec2(se.hx, se.hy),
                                    peekPos = Vec2(se.px, se.py),
                                    exposure = se.exposure,
                                    timer = se.timer,
                                    direction = se.dir,
                                    phase = se.phase,
                                    dirY = se.dirY,
                                    maxHp = se.maxHp,
                                    hp = se.hp,
                                    hurtAt = nowSec - se.hurtAge
                                )
                            }
                            val shotKinds = ShotKind.values()
                            fire.applyRemote(
                                view.fireTime,
                                snap.shots.map {
                                    Shot(it.id, it.x, it.y, shotKinds[it.kind.coerceIn(0, shotKinds.size - 1)], it.born, it.flight, it.baseRadius, it.maxHp, it.hp, it.harmless)
                                },
                                snap.views.map { TelegraphView(it.x, it.y, it.radius, it.progress, it.enemyId) }
                            )
                            val phases = ArenaPhase.values()
                            director.applyRemote(
                                phases[snap.phase.coerceIn(0, phases.size - 1)], snap.wave, snap.hearts, snap.left,
                                snap.bossHp, snap.bossMax, snap.bossAlive, snap.bossDying, snap.healed
                            )
                            AttackPose.ids = snap.apIds
                            AttackPose.progress = snap.apProgress
                            AttackPose.bossSinceFire = snap.bossSince
                            AttackPose.bossStunned = snap.bossStunned
                            AttackPose.bossEnraged = snap.bossEnraged
                            // הניקוד ירד = המארח התחיל מחדש: שומרים לפרופיל את מה שנצבר לפני האיפוס
                            if (snap.score < score) {
                                bankRun()
                                sfxMemo.banked = 0
                            }
                            score = snap.score
                            // המד והפרסים הם של המארח; מעדכנים רק כשהגיעה תמונת מצב חדשה
                            if (snap.seq != sfxMemo.guestSeq) {
                                sfxMemo.guestSeq = snap.seq
                                shock.applyRemote(snap.shock)
                                loot.applyRemote(
                                    snap.loot.map {
                                        LootDrop(it.id, it.x, it.y, LootKind.values()[it.kind.coerceIn(0, LootKind.values().size - 1)], it.born)
                                    },
                                    snap.lootTime
                                )
                            }
                            scoreBy[0] = snap.scoreHost
                            scoreBy[1] = snap.scoreGuest
                            hostCross = Vec2(snap.hostX, snap.hostY)
                            // אירועים: איבדנו לב, או שהשלב התחלף
                            if (snap.hearts < sfxMemo.guestHearts) {
                                soundPlayer.play(Sfx.PlayerHit)
                                shake = max(shake, 0.7f)
                                flash = FLASH_START
                                if (PlayerProfile.vibrationOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            sfxMemo.guestHearts = snap.hearts
                            if (director.phase != sfxMemo.phase) {
                                when (director.phase) {
                                    ArenaPhase.Intro -> {
                                        weapon.refill()
                                        soundPlayer.play(Sfx.WaveStart)
                                    }
                                    ArenaPhase.Clear -> soundPlayer.play(Sfx.WaveClear)
                                    ArenaPhase.Won -> {
                                        soundPlayer.play(Sfx.Win)
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
                        loot.advanceClock(dt)
                        // אפקטים שהמארח שלח: צלילים, פיצוצים, ניקוד צף
                        while (true) {
                            val ev = CoopSync.events.poll() ?: break
                            when (ev.type) {
                                's' -> {
                                    val all = Sfx.values()
                                    if (ev.i in all.indices) soundPlayer.play(all[ev.i], ev.f)
                                }
                                'b' -> {
                                    val kinds = AccessoryKind.values()
                                    blasts = blasts + Blast(ev.x, ev.y, kinds[ev.i.coerceIn(0, kinds.size - 1)], ev.text, ev.f, now)
                                }
                                'f' -> floaters = floaters + Floater(ev.x, ev.y, ev.text, now, ev.big)
                                't' -> showToast(ev.text)
                                'k' -> {
                                    shockStart = now
                                    shake = max(shake, 1.3f)
                                }
                                'p' -> poofs = poofs + Poof(ev.x, ev.y, now)
                                // פרס שהאורח אסף: כדורים למחסנית שלו, או יהלום לפרופיל שלו
                                'g' -> if (ev.i == 1) {
                                    soundPlayer.play(Sfx.Pickup)
                                    gainDiamonds(1, "+1 DIAMOND")
                                } else {
                                    soundPlayer.play(Sfx.Pickup)
                                    weapon.topUp()
                                    showToast("AMMO FULL")
                                }
                                // המארח החיה: ההחייאה נוצלה, ומנקים את האפקטים
                                'v' -> {
                                    revives = 1
                                    blasts = emptyList()
        usedFreeZones.clear()
                                    poofs = emptyList()
                                    floaters = emptyList()
                                    flash = 0f
                                    orbImpact = 0f
                                    weapon.refill()
                                }
                                // המארח התחיל מחדש
                                'x' -> {
                                    revives = 0
                                    blasts = emptyList()
        usedFreeZones.clear()
                                    poofs = emptyList()
                                    floaters = emptyList()
                                    flash = 0f
                                    orbImpact = 0f
                                    diamondsGained = 0
                                    sfxMemo.banked = 0
                                    sfxMemo.guestHearts = ArenaDirector.MAX_HEARTS
                                    combo.reset()
                                    weapon.reset()
                                }
                                // ההחייאה נדחתה אצל המארח: מחזירים את היהלומים
                                'n' -> {
                                    PlayerProfile.addDiamonds(REVIVE_COST)
                                    revives = 0
                                }
                            }
                        }
                        if (now - lastAimNanos > 100_000_000L) {
                            lastAimNanos = now
                            CoopSync.sendAim(crosshair.x, crosshair.y)
                        }
                    }
                    if (isHost) {
                        // מארח: הירי וההקשות של השותף, ותמונת מצב אליו
                        while (true) {
                            val input = CoopSync.inbox.poll() ?: break
                            if (input.cmd.isNotEmpty()) {
                                coopCmdRef[0]?.invoke(input.cmd)
                                continue
                            }
                            if (director.phase != ArenaPhase.Fighting) continue
                            if (input.tap) {
                                coopTapRef[0]?.invoke(Vec2(input.x, input.y), input.wide)
                            } else {
                                coopShootRef[0]?.invoke(Vec2(input.x, input.y), input.damage.coerceIn(1, 20))
                            }
                        }
                        // אפקטים חדשים (פיצוצים, ניקוד צף, פוף) נשלחים לשותף פעם אחת
                        for (b in blasts) if (seenFx.add(b)) CoopSync.queueBlast(b.x, b.y, b.kind.ordinal, b.label, b.radius)
                        for (f in floaters) if (seenFx.add(f)) CoopSync.queueFloater(f.x, f.y, f.text, f.big)
                        for (p in poofs) if (seenFx.add(p)) CoopSync.queuePoof(p.x, p.y)
                        if (seenFx.size > 300) {
                            seenFx.clear()
                            seenFx.addAll(blasts)
                            seenFx.addAll(floaters)
                            seenFx.addAll(poofs)
                        }
                        if (now - lastSnapNanos >= 80_000_000L) {
                            lastSnapNanos = now
                            CoopSync.sendSnapshot(
                                snapSeq++, enemies, fire, director, crosshair, score, nowSeconds(),
                                AttackPose.ids, AttackPose.progress, AttackPose.bossSinceFire,
                                AttackPose.bossStunned, AttackPose.bossEnraged, scoreBy[0], scoreBy[1],
                                shock.fraction, loot
                            )
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
                    // אורח: מד ההלם מגיע מהמארח
                    if (!isGuest && director.phase == ArenaPhase.Fighting && !director.bossDying) shock.update(dt)
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
                    if (orbImpact > 0f) orbImpact = (orbImpact - dt).coerceAtLeast(0f)
                    if (director.phase == ArenaPhase.Lost) lostAge += dt else lostAge = 0f
                    if (director.bossDying) shake = max(shake, 0.55f)
                    if (director.phase == ArenaPhase.Won || director.phase == ArenaPhase.Lost) {
                        shake = 0f
                    } else if (shake > 0f) {
                        shake = (shake - 2.0f * dt).coerceAtLeast(0f)
                    }
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
            if (PlayerProfile.vibrationOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
    val viewFraction = if (panoMode) (viewAspect / aspect).coerceAtMost(1f) else 1f
    val camera = GameCamera.following(crosshair, zoom, viewFraction)
    val crosshairOnScreen = camera.worldToScreen(crosshair)
    // הקליעים טסים אל אמצע תחתית מה שרואים, ומפלצות יורות רק קרוב לתצוגה
    if (panoMode) {
        val halfView = 0.5f * viewFraction / camera.zoom
        Shot.targetX = camera.center.x
        Shot.targetY = camera.center.y + 0.5f / camera.zoom
        Shot.attackMinX = camera.center.x - halfView * 1.5f
        Shot.attackMaxX = camera.center.x + halfView * 1.5f
    } else {
        Shot.targetX = 0.5f
        Shot.targetY = 1f
        Shot.attackMinX = 0f
        Shot.attackMaxX = 1f
    }

    // ג'ירו: סיבוב הטלפון מזיז את הכוונת (והמצלמה אחריה) לאורך הפנורמה
    val context2 = LocalContext.current
    val tracker = remember { HeadingTracker(context2) }
    val spanH = panoInfo?.spanH ?: PanoramaStore.spanH(context2)
    val spanV = panoInfo?.spanV ?: PanoramaStore.spanV(context2)
    DisposableEffect(panoMode) {
        if (panoMode) tracker.start()
        onDispose { tracker.stop() }
    }
    LaunchedEffect(panoMode) {
        if (!panoMode) return@LaunchedEffect
        var lastYaw = Float.NaN
        var lastPitch = 0f
        while (true) {
            withFrameNanos { }
            if (!tracker.ready || paused) {
                lastYaw = Float.NaN
                continue
            }
            if (lastYaw.isNaN()) {
                lastYaw = tracker.yaw
                lastPitch = tracker.pitch
            }
            // כשהטלפון מכוון כמעט למעלה/למטה הכיוון האופקי לא יציב (קפיצות): מתעלמים
            if (kotlin.math.abs(tracker.pitch) > 1.1f) {
                lastYaw = Float.NaN
                continue
            }
            var dYaw = wrapAngle(tracker.yaw - lastYaw)
            var dPitch = tracker.pitch - lastPitch
            lastYaw = tracker.yaw
            lastPitch = tracker.pitch
            // רעידות קטנות לא מזיזות, וקפיצה פתאומית גדולה נחתכת
            if (kotlin.math.abs(dYaw) < 0.0012f) dYaw = 0f
            if (kotlin.math.abs(dPitch) < 0.0012f) dPitch = 0f
            dYaw = dYaw.coerceIn(-0.12f, 0.12f)
            dPitch = dPitch.coerceIn(-0.12f, 0.12f)
            crosshair = Vec2(
                (crosshair.x + dYaw / spanH).coerceIn(0f, 1f),
                (crosshair.y - dPitch / spanV).coerceIn(0f, 1f)
            )
        }
    }
    val worldModifier = if (panoMode && screenHeightPx > 1) {
        with(LocalDensity.current) {
            Modifier.requiredSize((screenHeightPx * aspect).toDp(), screenHeightPx.toDp())
        }
    } else {
        Modifier.fillMaxSize()
    }
    val latestCamera by rememberUpdatedState(camera)
    val latestAspect by rememberUpdatedState(aspect)

    // הלם: מחסל את כל המפלצות הרגילות וכל הקליעים. הבוס לא מת: מאבד 15% מהחיים ומסוחרר
    fun triggerShock() {
        if (director.phase != ArenaPhase.Fighting || director.bossDying) return
        if (isGuest) {
            // אורח: המארח מפעיל את ההלם (המד משותף)
            if (shock.ready) CoopSync.sendCommand("shock")
            return
        }
        if (!shock.trigger()) return
        soundPlayer.play(Sfx.Boom)
        if (PlayerProfile.vibrationOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        shake = max(shake, 1.3f)
        shockStart = frameNanos
        if (isHost) CoopSync.queueShock()
        fire.clearAll()
        val now = nowSeconds()
        var kills = 0
        enemies = enemies.map { e ->
            if (!e.isAlive) {
                e
            } else if (e.id == ArenaDirector.BOSS_ID) {
                val cut = kotlin.math.ceil(e.maxHp * Shockwave.BOSS_DAMAGE_FRACTION).toInt()
                e.copy(hp = (e.hp - cut).coerceAtLeast(1), hurtAt = now)
            } else if (Hover.isBalloonMonster(e)) {
                // מפלצת הבלונים: בלון שלם הוא שליש מהחיים, ופגיעת חיים בודדת לא משנה את מספר הבלונים שרואים.
                // לכן ההלם מפוצץ בלון שלם (הבלון האחרון הורג אותה)
                val max = if (e.maxHp > 0) e.maxHp else hpFor(e.id)
                val hp = if (e.maxHp > 0) e.hp else max
                val pop = max(1, kotlin.math.ceil(max / 3f).toInt())
                if (hp > pop) {
                    e.copy(maxHp = max, hp = hp - pop, hurtAt = now)
                } else {
                    kills++
                    sfxMemo.lootSeen.add(e.id)
                    e.copy(isAlive = false)
                }
            } else if (e.hp > 1) {
                e.copy(hp = e.hp - 1, hurtAt = now)
            } else {
                kills++
                sfxMemo.lootSeen.add(e.id)
                e.copy(isAlive = false)
            }
        }
        fire.stun(ArenaDirector.BOSS_ID, Shockwave.BOSS_STUN_SECONDS)
        if (kills > 0) {
            score += scaled(kills * SHOCK_KILL_POINTS)
            scoreBy[shooter[0]] += scaled(kills * SHOCK_KILL_POINTS)
            showToast("SHOCKWAVE  +${kills * SHOCK_KILL_POINTS / COINS_DIVISOR}")
        }
    }

    // איסוף פרס: לב מחזיר חיים (אם כבר מלא: נקודות), כדורים ממלאים את המחסנית מיד
    // byGuest (מארח): האורח אסף. הלב מרפא את הצוות; כדורים ויהלום נשלחים לאורח, לנשק ולפרופיל שלו
    fun collectLoot(drop: com.arsniper.game.engine.LootDrop, byGuest: Boolean = false) {
        soundPlayer.play(Sfx.Pickup)
        val label: String
        when (drop.kind) {
            LootKind.Heart -> {
                if (director.heal()) {
                    label = "+1 HEART"
                    flash = 0f
                    orbImpact = 0f
                } else {
                    score += scaled(150)
                    scoreBy[shooter[0]] += scaled(150)
                    label = "+15"
                }
            }
            LootKind.Ammo -> {
                if (byGuest) {
                    CoopSync.queueGive(0)
                    return
                }
                weapon.topUp()
                label = "AMMO FULL"
            }
            LootKind.Diamond -> {
                if (byGuest) {
                    CoopSync.queueGive(1)
                    return
                }
                gainDiamonds(1, "+1 DIAMOND")
                return
            }
        }
        showToast(label)
    }

    // ניקוד ואפקט על קליע שנפגע (בלחיצה או בירי)
    fun onIntercept(result: com.arsniper.game.engine.Intercept) {
        val shot = result.shot
        val shotPos = shot.posAt(fire.time)
        soundPlayer.play(if (result.destroyed) Sfx.Deflect else Sfx.Crack)
        if (result.destroyed) shock.reward()
        if (result.destroyed) {
            award(BLOCK_POINTS, shotPos.x, shotPos.y, register = false, showText = false)
            blasts = blasts + Blast(
                x = shotPos.x,
                y = shotPos.y,
                kind = AccessoryKind.Gem,
                label = "",
                radius = fire.currentRadius(shot) * 1.5f,
                bornNanos = frameNanos,
                orb = shot.kind == com.arsniper.game.engine.ShotKind.Orb
            )
        } else {
            award(CRACK_POINTS, shotPos.x, shotPos.y, register = false, showText = false)
            blasts = blasts + Blast(
                x = shotPos.x,
                y = shotPos.y,
                kind = AccessoryKind.Bomb,
                label = "",
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
        // נקודת תורפה בלי נזק (למשל מיכל הגז של התמנון) נותנת ניקוד והשפעה רק פעם אחת לכל מפלצת:
        // אחרת אפשר היה לירות עליה בלי סוף ולצבור ניקוד ומטבעות (המפלצת לא מתה ואין הגבלת כדורים)
        val zoneHit = WeakZones.hit(
            p,
            ZoneRegistry.snapshot().filter { it.zone.damage > 0 || !usedFreeZones.contains("${it.enemy.id}:${it.zone.id}") },
            enemies,
            aspect
        )
        if (zoneHit != null) {
            // פגיעה בנקודת תורפה: ההשפעה של הנקודה (השבתה, בלבול, נזק מוגבר...) וניקוד
            val zone = zoneHit.zone
            if (zone.damage <= 0) usedFreeZones.add("${zoneHit.enemy.id}:${zone.id}")
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
                showToast(zone.label)
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
                is FireResult.Hit -> if (Hover.isBalloonMonster(result.enemy) && Hover.lift(result.enemy, nowSeconds()) > 0f) {
                    // מפלצת בלונים: הגוף חסין, רק פיצוץ בלונים פוגע בה (אזור הבלונים מטופל למעלה)
                    combo.reset()
                } else {
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
            if (isGuest) {
                // אורח: המארח מאשר את האיסוף (הפרס יוסר גם אצלו בתמונת המצב הבאה)
                soundPlayer.play(Sfx.Pickup)
                CoopSync.sendTap(crosshair.x, crosshair.y, wide = true)
            } else {
                collectLoot(picked)
            }
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
            if (isGuest) {
                CoopSync.sendFire(crosshair.x + offset, crosshair.y, dmg)
            } else {
                shootAt(Vec2(crosshair.x + offset, crosshair.y), dmg)
            }
        }
    }
    fireRef[0] = { fireNow() }
    coopShootRef[0] = { p, d ->
        shooter[0] = 1
        try { shootAt(p, d) } finally { shooter[0] = 0 }
    }
    coopTapRef[0] = { w, wide ->
        shooter[0] = 1
        try {
            val pickedLoot = loot.collect(w, latestAspect, if (wide) FIRE_SLACK + 0.03f else TAP_SLACK)
            if (pickedLoot != null) {
                collectLoot(pickedLoot, byGuest = true)
            } else {
                val hit = fire.intercept(w, latestAspect, TAP_SLACK)
                if (hit != null) onIntercept(hit)
            }
        } finally {
            shooter[0] = 0
        }
    }

    // החייאה ביהלומים: חוזרים לאותו גל עם 2 לבבות, מחסנית והלם מלאים
    // byGuest (מארח): האורח כבר שילם מהיהלומים שלו. אם ההחייאה לא אפשרית, הוא מקבל החזר
    fun reviveRun(byGuest: Boolean = false) {
        if (director.phase != ArenaPhase.Lost || revives >= 1) {
            if (byGuest) CoopSync.queueRefund()
            return
        }
        if (!byGuest && !PlayerProfile.spendDiamonds(REVIVE_COST)) return
        revives += 1
        if (isHost) CoopSync.queueRevived()
        enemies = emptyList()
        blasts = emptyList()
        usedFreeZones.clear()
        poofs = emptyList()
        loot.reset()
        fire.reset()
        weapon.refill()
        shock.chargeNow()
        AttackPose.ids = emptySet()
        AttackPose.progress = emptyMap()
        AttackPose.bossSinceFire = 999f
        AttackPose.sinceLaunch = emptyMap()
        AttackPose.bossStunned = false
        AttackPose.bossEnraged = false
        flash = 0f
        orbImpact = 0f
        sfxMemo.phase = null
        sfxMemo.damagedThisWave = true
        director.revive()
    }

    fun retry() {
        bankRun()
        if (isHost) CoopSync.queueRestart()
        revives = 0
        enemies = emptyList()
        blasts = emptyList()
        usedFreeZones.clear()
        score = 0
        scoreBy[0] = 0
        scoreBy[1] = 0
        sfxMemo.banked = 0
        diamondsGained = 0
        flash = 0f
        orbImpact = 0f
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
        AttackPose.sinceLaunch = emptyMap()
        AttackPose.bossStunned = false
        AttackPose.bossEnraged = false
        director.restart()
    }

    // משחק משותף (מארח): פקודות האורח. הלם נזקף לאורח, החייאה שולמה כבר על ידו
    coopCmdRef[0] = { cmd ->
        when (cmd) {
            "shock" -> {
                shooter[0] = 1
                try { triggerShock() } finally { shooter[0] = 0 }
            }
            "revive" -> reviveRun(byGuest = true)
            "retry" -> retry()
            else -> Unit
        }
    }

    // אורח: החייאה ביהלומים שלו, ובקשת ניסיון חוזר מהמארח
    fun guestRevive() {
        if (director.phase != ArenaPhase.Lost || revives >= 1) return
        if (!PlayerProfile.spendDiamonds(REVIVE_COST)) return
        revives = 1
        CoopSync.sendCommand("revive")
    }

    // משחק משותף: אם המארח יצא או שהחיבור נפל, חוזרים ללובי
    if (coop != null) {
        LaunchedEffect(CoopSync.hostEnded, CoopClient.status) {
            if ((isGuest && CoopSync.hostEnded) || CoopClient.status != CoopStatus.Connected) onExit()
        }
    }

    // כפתור "אחורה": פותח את תפריט ההשהיה, ובתפריט פתוח חוזר למשחק
    BackHandler(enabled = true) {
        if (director.phase == ArenaPhase.Won || director.phase == ArenaPhase.Lost) {
            onExit()
        } else {
            sfxMemo.holding = false
            paused = !paused
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (size.height > 0) {
                    viewAspect = size.width.toFloat() / size.height.toFloat()
                    aspect = if (panoMode) photoRatio else viewAspect
                    screenHeightPx = size.height
                    sizeKnown = true
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    zoom = (zoom * zoomChange)
                        .coerceIn(BASE_ZOOM, GameCamera.MAX_ZOOM)
                    // פנורמה: גרירה מזיזה את הכוונת (והמצלמה אחריה)
                    if (panoMode && size.width > 0 && size.height > 0) {
                        val vf = (viewAspect / aspect).coerceAtMost(1f)
                        crosshair = Vec2(
                            (crosshair.x - pan.x / size.width * vf / zoom).coerceIn(0f, 1f),
                            (crosshair.y - pan.y / size.height / zoom).coerceIn(0f, 1f)
                        )
                    }
                }
            }
            .pointerInput(Unit) {
                // לחיצה ישירה על מה שהמפלצת יורה: עוצרת את הקליע
                detectTapGestures { offset ->
                    if (director.phase == ArenaPhase.Fighting && size.width > 0 && size.height > 0) {
                        val cam = latestCamera
                        val world = Vec2(
                            (offset.x / size.width - 0.5f) / cam.zoom * cam.viewFraction + cam.center.x,
                            (offset.y / size.height - 0.5f) / cam.zoom + cam.center.y
                        )
                        if (isGuest) {
                            CoopSync.sendTap(world.x, world.y)
                        } else {
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
            }
    ) {
        // העולם: רקע ואויבים, מוגדלים ומוזזים יחד לפי המצלמה
        Box(
            modifier = worldModifier
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = camera.zoom
                    scaleY = camera.zoom
                    val t = frameNanos / 1e9f
                    val shakeX = sin(t * 83f) * shake * size.width * 0.012f
                    val shakeY = cos(t * 97f) * shake * size.height * 0.016f
                    translationX = (0.5f - camera.center.x * camera.zoom) * size.width + shakeX
                    translationY = (0.5f - camera.center.y * camera.zoom) * size.height + shakeY
                    // פגיעת כדור אנרגיה: העולם מטשטש לרגע ומתחדד בחזרה (הממשק נשאר חד כדי שאפשר להמשיך לשחק).
                    // ב-Android 12 ומעלה בלבד; בישנים יותר אין טשטוש
                    if (orbImpact > 0f) {
                        val blur = OrbFx.blur(OrbFx.IMPACT_SECONDS - orbImpact) * 16f * density
                        if (blur > 0.5f) renderEffect = androidx.compose.ui.graphics.BlurEffect(blur, blur, androidx.compose.ui.graphics.TileMode.Clamp)
                    }
                }
        ) {
            BackgroundLayer(photo = photo, colorFilter = remember(style) { arenaColorFilter(style) })
            // התמונה המעוצבת נכנסת בהדרגה מעל המקורית (בלי קפיצה כשהחישוב מסתיים)
            themed?.let { t ->
                var fadeIn by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { fadeIn = true }
                val themedAlpha by androidx.compose.animation.core.animateFloatAsState(if (fadeIn) 1f else 0f, androidx.compose.animation.core.tween(900), label = "themedFade")
                androidx.compose.foundation.layout.Box(Modifier.graphicsLayer { alpha = themedAlpha }) {
                    BackgroundLayer(photo = t, colorFilter = null)
                }
            }
            ArenaStyleLayer(style = style, nowNanos = frameNanos, props = true, drawFrame = false, frameVariant = if (themed != null) frameVariant else -1, worldViewAspect = if (panoMode) viewAspect else 0f)
            // ערפל הטונדרה, המעבר האחורי: מאחורי המפלצות
            if (style == ArenaStyle.Tundra) TundraSnow(nowNanos = frameNanos)
            if (style == ArenaStyle.Tundra) TundraFog(nowNanos = frameNanos, front = false)
            EnemyLayer(enemies = enemies, scene = scene, monsterSet = style.name.lowercase())
            // ערפל צף מעל המפלצות: בטונדרה שני סוגי ערפל שמתחלפים (המעבר הקדמי), בשאר הארנות המעוצבות הכתמים הישנים
            if (style == ArenaStyle.Tundra) TundraFog(nowNanos = frameNanos, front = true)
            else if (themed != null) MistLayer(nowNanos = frameNanos)
            // המסגרת הקדמית (קישוטי הקצוות של הארנה) מעל המפלצות: הן עומדות מאחוריה ולא עליה
            if (!(themed != null && SHOW_FRAME_ON_THEMED.not())) ArenaStyleLayer(style = style, nowNanos = frameNanos, props = true, frameOnly = true, frameVariant = if (themed != null) frameVariant else -1, worldViewAspect = if (panoMode) viewAspect else 0f)
            CombatLayer(
                shots = fire.shots.toList(),
                telegraphs = fire.telegraphViews.toList(),
                time = fire.time,
                shotRadius = { fire.currentRadius(it) },
                altShots = style == ArenaStyle.Tundra || style == ArenaStyle.Cyber || style == ArenaStyle.Industrial || style == ArenaStyle.Ruins
            )
            LootLayer(loot = loot, nowNanos = frameNanos)
            EffectsLayer(blasts = blasts, nowNanos = frameNanos)
            PoofLayer(poofs = poofs, nowNanos = frameNanos)
            FloatingTextLayer(floaters = floaters, nowNanos = frameNanos)
        }

        // פנורמה: חלקיקים, ערפל וחשכת קצוות צמודים למסך. המסגרת וכתמי הרצפה בעולם עצמו
        if (panoMode) ArenaStyleLayer(style = style, nowNanos = frameNanos, drawFrame = false, screenOnly = true)

        // הכוונת מצוירת במיקום המסך שלה
        CrosshairOverlay(position = Offset(crosshairOnScreen.x, crosshairOnScreen.y))

        // משחק משותף: הכוונת השקופה של השותף, וחץ כשהוא מכוון אל מחוץ למסך
        if (coop != null) {
            val partner: Vec2? = if (isHost) {
                if (System.nanoTime() - CoopSync.partnerAimAt < 2_000_000_000L) CoopSync.partnerAim else null
            } else {
                hostCross
            }
            if (partner != null) {
                val ps = camera.worldToScreen(partner)
                if (ps.x in 0f..1f && ps.y in 0f..1f) {
                    CrosshairOverlay(position = Offset(ps.x, ps.y), alpha = 0.45f)
                } else {
                    OutlinedText(
                        if (ps.x < 0f) "◀ P2" else if (ps.x > 1f) "P2 ▶" else "P2",
                        fontSize = 20.sp,
                        modifier = Modifier
                            .align(if (ps.x < 0f) Alignment.CenterStart else if (ps.x > 1f) Alignment.CenterEnd else Alignment.TopCenter)
                            .padding(horizontal = 12.dp, vertical = 120.dp)
                    )
                }
            }
        }

        // פנורמה: חיצים שמראים כמה מפלצות יש מחוץ למסך, מכל צד
        if (panoMode) {
            var leftCount = 0
            var rightCount = 0
            for (e in enemies) {
                if (!e.isAlive) continue
                var ex = e.position.x
                if (wraps) {
                    // בעטיפה: מודדים את הדרך הקצרה ביותר סביב המעגל
                    var d = ex - crosshair.x
                    if (d > ring / 2f) d -= ring else if (d < -ring / 2f) d += ring
                    ex = crosshair.x + d
                }
                val sx = camera.worldToScreen(Vec2(ex, e.position.y)).x
                if (sx < 0f) leftCount++ else if (sx > 1f) rightCount++
            }
            if (leftCount > 0) {
                OutlinedText("◀ $leftCount", fontSize = 24.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
            }
            if (rightCount > 0) {
                OutlinedText("$rightCount ▶", fontSize = 24.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
            }
        }

        val tundraHud = style == ArenaStyle.Tundra && themed != null
        if (tundraHud) {
            TundraHudFrame(
                currency = { CurrencyBar(coins = score / COINS_DIVISOR, diamonds = PlayerProfile.diamonds) },
                hearts = { HeartsRow(hearts = director.hearts, max = ArenaDirector.MAX_HEARTS) },
                zoom = {
                    ZoomControls(
                        zoom = zoom / BASE_ZOOM,
                        onZoomIn = { zoom = (zoom + ZOOM_STEP * BASE_ZOOM).coerceAtMost(GameCamera.MAX_ZOOM) },
                        onZoomOut = { zoom = (zoom - ZOOM_STEP * BASE_ZOOM).coerceAtLeast(BASE_ZOOM) }
                    )
                },
                joystick = { Joystick(onMove = { joystick = it }) },
                fire = {
                    FireButton(
                        onPress = {
                            sfxMemo.holding = true
                            sfxMemo.autoTimer = 0f
                            fireNow()
                        },
                        onRelease = { sfxMemo.holding = false }
                    )
                },
                ammo = {
                    AmmoBar(
                        ammo = ammoShown,
                        magSize = weapon.magSize,
                        reloading = reloadingShown,
                        onReload = { weapon.reload() }
                    )
                }
            )
        } else CurrencyBar(
            coins = score / COINS_DIVISOR,
            diamonds = PlayerProfile.diamonds,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )

        BonusToast(
            data = toast,
            nowNanos = frameNanos,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 128.dp)
        )

        ComboBadge(
            count = combo.count,
            multiplier = combo.multiplier,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 84.dp)
        )

        val coopLine = if (coop != null) {
            val mine = if (isHost) scoreBy[0] else scoreBy[1]
            val other = if (isHost) scoreBy[1] else scoreBy[0]
            "YOU ${mine / COINS_DIVISOR}   PARTNER ${other / COINS_DIVISOR}"
        } else {
            null
        }
        if (coopLine != null) {
            OutlinedText(
                coopLine,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 138.dp)
            )
        }

        if (!tundraHud) HeartsRow(
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
                .padding(top = if (tundraHud) 74.dp else 56.dp)
        )

        // זום: בפינה הימנית העליונה, מעל אזור ההליכה של המפלצות, כדי שלא יסתיר אותן
        if (!tundraHud) ZoomControls(
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
            WeaponLayer(art = weaponArt, weapon = weapon, nowNanos = frameNanos, tracerTarget = tracerTarget, aimX = crosshair.x)
        }
        if (coop != null) {
            com.arsniper.ui.components.MicToggle(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 124.dp, end = 16.dp)
            )
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

        if (!tundraHud) AmmoBar(
            ammo = ammoShown,
            magSize = weapon.magSize,
            reloading = reloadingShown,
            onReload = { weapon.reload() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 34.dp)
        )

        if (!tundraHud) Joystick(
            onMove = { joystick = it },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 64.dp, bottom = 32.dp)
        )

        if (!tundraHud) FireButton(
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
                    .padding(top = 96.dp)
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

        // כפתור השהיה (רק בזמן קרב)
        if (director.phase != ArenaPhase.Won && director.phase != ArenaPhase.Lost && !paused) {
            HudImage(
                com.arsniper.R.drawable.hud_pause,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 68.dp, end = 16.dp)
                    .size(width = 52.dp, height = 46.dp)
                    .clickable {
                        sfxMemo.holding = false
                        paused = true
                    }
            )
        }

        // הבהוב אדום כשנפגעים
        DamageFlash(alpha = flash)
        if (orbImpact > 0f) OrbImpactOverlay(age = OrbFx.IMPACT_SECONDS - orbImpact)

        if (paused) {
            PauseMenu(
                onResume = { paused = false },
                onRestart = {
                    paused = false
                    if (isGuest) CoopSync.sendCommand("retry") else retry()
                },
                onMainMenu = {
                    paused = false
                    onExit()
                }
            )
        }

        if (director.phase == ArenaPhase.Won || (director.phase == ArenaPhase.Lost && lostAge >= 1.0f)) {
            EndOverlay(
                won = director.phase == ArenaPhase.Won,
                stars = director.stars,
                score = score / COINS_DIVISOR,
                wave = director.waveIndex + 1,
                diamondsGained = diamondsGained,
                totalCoins = PlayerProfile.coins,
                reviveCost = if (revives < 1) REVIVE_COST else 0,
                diamonds = PlayerProfile.diamonds,
                onRevive = { if (isGuest) guestRevive() else reviveRun() },
                onRetry = { if (isGuest) CoopSync.sendCommand("retry") else retry() },
                onMenu = { onExit() },
                onNext = onNextArena,
                bonusText = listOfNotNull(coopLine?.let { "$it   TEAM ${score / COINS_DIVISOR}" }, photoBonus)
                    .joinToString("\n").ifEmpty { null },
                difficultyTitle = difficulty.title
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

    /** אורח במשחק משותף: כמה לבבות היו בתמונת המצב הקודמת (לזיהוי פגיעה בשחקן) */
    var guestHearts: Int = ArenaDirector.MAX_HEARTS

    /** אורח: מספר תמונת המצב האחרונה שעובדה (מד הלם ופרסים) */
    var guestSeq: Int = -1
}

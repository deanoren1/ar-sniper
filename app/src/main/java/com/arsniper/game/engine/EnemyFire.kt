package com.arsniper.game.engine

import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import com.arsniper.game.model.EnemyState
import com.arsniper.game.model.Vec2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * קליע שיורה אויב על השחקן. הוא נשאר במקום (x, y בקואורדינטות עולם) וגדל בדרך אל המצלמה:
 * ברגע שהזמן נגמר הוא פוגע. אפשר להשמיד אותו בלחיצה עליו או בירי עליו עם הכוונת.
 * baseRadius = הרדיוס ברגע הפגיעה, ביחידות גובה מסך.
 */
class Shot(
    val id: Int,
    val x: Float,
    val y: Float,
    val kind: ShotKind,
    val born: Float,
    val flight: Float,
    val baseRadius: Float,
    val maxHp: Int,
    var hp: Int,
    /** ירייה "מטעה": המפלצת הייתה מבולבלת, והקליע לא פוגע בשחקן כשהוא מגיע */
    val harmless: Boolean = false,
    /** הסטת היעד לרוחב (חלק מרוחב המסך): שלושת הכדורים של הדרקון הנחת עפים במקביל לשלושה כיוונים */
    val dx: Float = 0f,
    /** כדור נוסף של אותה ירייה (לא נספר בתקרת הקליעים של הגל) */
    val extra: Boolean = false,
    /** כמה לב הקליע לוקח כשהוא פוגע (כדור נותב של רובה = רבע) */
    val damage: Float = 1f,
    /** איך הקליע טס (ShotPattern) */
    val pattern: ShotPattern = ShotPattern.Single,
    /** מאיזה גודל (שבר מהרדיוס הסופי) הקליע מתחיל: חצאי ענן הגז ממשיכים מהגודל של הענן שהתפצל */
    val growFrom: Float = 0.35f,
    /** עד איזה שלב בדרך (0..1) אי אפשר לפגוע בקליע: הסלע של הבוס בזמן שהוא עולה בקשת */
    val armorUntil: Float = 0f
) {
    /** כמה מהדרך עבר (0..1) */
    fun progressAt(time: Float): Float = ((time - born) / flight).coerceIn(0f, 1f)

    /** האם הקליע כבר יצא (כדורי צרור יוצאים בהפרש קצר זה מזה) */
    fun launched(time: Float): Boolean = time >= born

    /**
     * מיקום הירייה בזמן t (קואורדינטות עולם): יוצאת מהמפלצת וטסה אל השחקן (אמצע התחתית), מאיצה בדרך.
     * הציור ואזור ההקשה משתמשים באותה פונקציה, כדי שיתאימו תמיד.
     */
    fun posAt(time: Float): Vec2 {
        val p = ((time - born) / flight).coerceIn(0f, 1f)
        if (kind == ShotKind.Burst) {
            // כדור נותב: קו ישר ומהיר, בלי האצה
            val e = p * TRAVEL
            return Vec2(x + (targetX + dx - x) * e, y + (targetY - y) * e)
        }
        when (pattern) {
            ShotPattern.Lob -> {
                // קשת: עולה גבוה מעל המפלצת ונוחתת על המסך. בלי האצה בהתחלה (נראה כמו זריקה)
                val e = p * TRAVEL
                val lift = sin(p * 3.1416f) * LOB_HEIGHT
                return Vec2(x + (targetX + dx - x) * e, y + (targetY - y) * e - lift)
            }
            ShotPattern.Fuse -> {
                // הפצצה נופלת מהבלון, ורק אחר כך טסה אל השחקן
                if (p < FUSE_DROP) {
                    val k = p / FUSE_DROP
                    return Vec2(x, y + FUSE_FALL * k * k)
                }
                val q = (p - FUSE_DROP) / (1f - FUSE_DROP)
                val e = Math.pow(q.toDouble(), 1.4).toFloat() * TRAVEL
                val y0 = y + FUSE_FALL
                return Vec2(x + (targetX + dx - x) * e, y0 + (targetY - y0) * e)
            }
            ShotPattern.Wobble -> {
                val e = Math.pow(p.toDouble(), 1.4).toFloat() * TRAVEL
                val sway = sin(p * 15.7f + id) * WOBBLE * (1f - 0.4f * p)
                return Vec2(x + (targetX + dx - x) * e + sway, y + (targetY - y) * e)
            }
            else -> Unit
        }
        val e = Math.pow(p.toDouble(), 1.4).toFloat() * TRAVEL
        if (kind == ShotKind.Rocket) {
            // טיל: מסתחרר בספירלה שהולכת ונפתחת ואז מתהדקת לקראת הפגיעה
            val swirl = sin(p * 3.1416f) * 0.09f
            val ang = p * 11f + id
            return Vec2(
                x + (targetX + dx - x) * e + cos(ang) * swirl,
                y + (targetY - y) * e + sin(ang) * swirl * 0.55f
            )
        }
        return Vec2(x + (targetX + dx - x) * e, y + (targetY - y) * e)
    }

    companion object {
        /** היעד של הקליעים: אמצע תחתית מה שרואים על המסך (בפנורמה זז עם המצלמה) */
        @Volatile var targetX = 0.5f
        @Volatile var targetY = 1.0f
        /** טווח x בעולם שבו מפלצות מותר להן לירות (בפנורמה: רק מה שליד התצוגה) */
        @Volatile var attackMinX = 0f
        @Volatile var attackMaxX = 1f
        /** כמה מהדרך אל היעד הירייה עוברת עד שהיא מגיעה אל השחקן */
        const val TRAVEL = 0.5f

        /** גובה הקשת של סלע נזרק (חלק מגובה המסך) */
        const val LOB_HEIGHT = 0.3f

        /** פצצת פתיל: איזה חלק מהזמן היא נופלת מהבלון, וכמה נמוך */
        const val FUSE_DROP = 0.22f
        const val FUSE_FALL = 0.1f

        /** עד איזה שלב בדרך פיצוץ של פצצת פתיל פוגע במפלצות שלידה (BACKFIRE) */
        const val BACKFIRE_UNTIL = 0.5f

        /** תנודה לצדדים של כדור מתנודד */
        const val WOBBLE = 0.055f
    }
}

/** הכנה לירייה: המפלצת מהבהבת באדום זמן קצר לפני שהיא יורה. אפשר להרוג אותה בזמן הזה */
class TelegraphView(val x: Float, val y: Float, val radius: Float, val progress: Float, val enemyId: Int, val kind: ShotKind? = null)

/** תוצאת פגיעה בקליע: הקליע, והאם הושמד (או רק נסדק) */
class Intercept(
    val shot: Shot,
    val destroyed: Boolean,
    /** כמה מהדרך הקליע עבר כשנעצר (0..1): קרוב ל-1 = ברגע האחרון */
    val progress: Float = 0f,
    /** פצצת פתיל שפוצצה מוקדם: מתפוצצת כאן ופוגעת במפלצות שלידה */
    val backfire: Vec2? = null
)

/** מה קרה בפריים: כמה קליעים פגעו בשחקן, ואיפה נקטעה הכנה כי המפלצת נהרגה (לבונוס) */
class FireOutcome(
    val playerHits: Int,
    val stopped: List<Vec2>,
    val hitKinds: List<ShotKind> = emptyList(),
    /** לכל פגיעה: איפה הקליע נגמר (קואורדינטות עולם), ומאיפה יצא. לכיוון ולמיקום של אפקטי פגיעה על המסך */
    val hitSpots: List<Vec2> = emptyList(),
    val hitFrom: List<Vec2> = emptyList(),
    /** לכל פגיעה: דפוס הקליע (סלע בקשת סודק את הזכוכית, פצצה חורכת) */
    val hitPatterns: List<ShotPattern> = emptyList()
)

/**
 * ירי האויבים על השחקן. Kotlin טהור.
 * כל מפלצת גלויה שולחת מדי פעם הכנה (הבהוב אדום), ואחריה קליע. הזמנים נקבעים לפי הגל.
 */
class EnemyFire(private val random: Random = Random.Default) {

    /** שעון פנימי בשניות: מתקדם רק כשהארנה רצה */
    var time: Float = 0f
        private set

    val shots = ArrayList<Shot>()
    val telegraphViews = ArrayList<TelegraphView>()

    private class Telegraph(val enemyId: Int, val start: Float, val duration: Float)

    private val telegraphs = ArrayList<Telegraph>()
    private val lostSince = HashMap<Int, Float>()
    private val nextAttack = HashMap<Int, Float>()
    private var nextShotId = 1

    /** השפעות נקודות תורפה לכל מפלצת: עד מתי היא לא תוקפת, עד מתי הירייה שלה מטעה, וכמה היא מוחלשת */
    private val disabledUntil = HashMap<Int, Float>()
    private val confusedUntil = HashMap<Int, Float>()
    private val weakened = HashMap<Int, Int>()
    private val groupHits = HashMap<Int, HashSet<String>>()
    private val lastLaunchAt = HashMap<Int, Float>()

    /** נזק חלקי שנצבר מכדורי נותב (כל 1.0 = לב) */
    private var chip = 0f

    /** כמה כדורי נותב פגעו בלי להוריד לב (להבזק קטן על המסך) */
    var chipHits: Int = 0
        private set

    /** כמה עננים התפצלו (לצליל) */
    var splitCount: Int = 0
        private set

    /** כמה קליעים יצאו עד עכשיו (לצליל ירייה של מפלצת) */
    var launchCount: Int = 0
        private set

    fun reset() {
        shots.clear()
        telegraphs.clear()
        telegraphViews.clear()
        nextAttack.clear()
        disabledUntil.clear()
        confusedUntil.clear()
        weakened.clear()
        groupHits.clear()
        lastLaunchAt.clear()
        chip = 0f
        chipHits = 0
        launchCount = 0
        time = 0f
    }

    /** משחק משותף, אצל האורח: מציג את הקליעים וההכנות שהמארח שלח, בשעון של המארח */
    fun applyRemote(newTime: Float, remoteShots: List<Shot>, views: List<TelegraphView>) {
        time = newTime
        shots.clear()
        shots.addAll(remoteShots)
        telegraphViews.clear()
        telegraphViews.addAll(views)
    }

    /** האם האויב נראה עכשיו וכך יכול לירות (מציץ שחשוף לגמרי, או דמות שהולכת בחוץ) */
    private fun canAttack(e: Enemy): Boolean {
        if (!e.isAlive) return false
        // בוס שעוד יוצא מהפורטל לא יורה
        if (e.id == ArenaDirector.BOSS_ID && BossEntrance.entering) return false
        // מפלצת בלונים בלי בלונים: מוממת על הקרקע ולא יורה
        if (Hover.isGrounded(e)) return false
        if (e.position.x < Shot.attackMinX || e.position.x > Shot.attackMaxX) return false
        return when (e.kind) {
            EnemyKind.Peek, EnemyKind.Window -> e.state == EnemyState.Exposed && e.exposure >= 0.95f
            // דמות שהולכת הרחק מהשחקן (גבה אליו) לא מתחילה הכנה לירי; היא תירה כשתפנה לצד או אליו
            EnemyKind.Open -> e.spot == null && e.exposure >= 0.95f && e.dirY >= -0.5f
            EnemyKind.Static -> true
        }
    }

    fun update(dt: Float, enemies: List<Enemy>, director: ArenaDirector): FireOutcome {
        if (director.phase != ArenaPhase.Fighting) {
            // בין גלים ובסוף: אין קליעים באוויר
            shots.clear()
            telegraphs.clear()
            telegraphViews.clear()
            return FireOutcome(0, emptyList())
        }
        if (director.bossDying) {
            shots.clear()
            telegraphs.clear()
            telegraphViews.clear()
            return FireOutcome(0, emptyList())
        }
        time += dt
        val wave = director.currentWave
        val stopped = ArrayList<Vec2>()

        // הכנות: ממשיכות עד שהזמן נגמר, ואז יוצא קליע. אם המפלצת מתה בזמן הזה: נקטעה
        val tIterator = telegraphs.iterator()
        while (tIterator.hasNext()) {
            val t = tIterator.next()
            val enemy = enemies.firstOrNull { it.id == t.enemyId }
            if (enemy != null && canAttack(enemy)) lostSince.remove(enemy.id)
            if (enemy == null) {
                tIterator.remove()
            } else if (!enemy.isAlive) {
                stopped.add(enemy.position)
                tIterator.remove()
            } else if (time < (disabledUntil[enemy.id] ?: 0f)) {
                // הושבתה באמצע ההכנה (פגיעה בנקודת תורפה)
                tIterator.remove()
            } else if (!canAttack(enemy)) {
                // התחבאה באמצע ההכנה: לא יורה. הפסקה קצרה (למשל דמות שמתהפכת בין כיוונים כשהיא תקועה) לא מבטלת
                val since = lostSince.getOrPut(enemy.id) { time }
                if (time - since >= LOST_GRACE) {
                    lostSince.remove(enemy.id)
                    tIterator.remove()
                    nextAttack[enemy.id] = time + cooldownFor(enemy.id, wave, director)
                }
            } else if (time - t.start >= t.duration) {
                lostSince.remove(enemy.id)
                launch(enemy, director, wave)
                tIterator.remove()
                nextAttack[enemy.id] = time + cooldownFor(enemy.id, wave, director)
            }
        }

        // הכנות חדשות
        for (e in enemies) {
            if (director.foeOf(e.id) == null) continue
            if (!canAttack(e)) continue
            if (time < (disabledUntil[e.id] ?: 0f)) continue
            val due = nextAttack.getOrPut(e.id) { time + randomBetween(1.2f, 2.4f) }
            if (time < due) continue
            if (telegraphs.any { it.enemyId == e.id }) continue
            val capacity = wave.maxAttacks + if (director.bossEnraged) 1 else 0
            if (telegraphs.size + shots.count { !it.extra } >= capacity) continue
            val foe = director.foeOf(e.id)
            val kind = foe?.let { ShotStyle.kindOf(it) }
            // דפוס כבד (מניפה, קשת, פתיל): הבהוב קצת ארוך יותר, כדי שיהיה זמן לקרוא אותו ולעצור את היורה
            val heavy = foe != null && ShotStyle.patternOf(foe, director.shotTier).let { it == ShotPattern.Fan || it == ShotPattern.Lob || it == ShotPattern.Fuse }
            telegraphs.add(
                Telegraph(
                    e.id, time,
                    if (kind == ShotKind.Burst) BURST_AIM_SECONDS else if (heavy) TELEGRAPH_SECONDS * 1.2f else TELEGRAPH_SECONDS
                )
            )
        }

        // ענן גז מתפצל: באמצע הדרך הופך לשני חצאים שממשיכים לשני צדדים, כל אחד חצי לב
        var i = 0
        while (i < shots.size) {
            val s = shots[i]
            if (s.pattern == ShotPattern.Split && s.launched(time) && s.progressAt(time) >= SPLIT_AT) {
                val pos = s.posAt(time)
                val grown = currentRadius(s) / (s.baseRadius * 0.75f)
                shots.removeAt(i)
                for ((k, side) in floatArrayOf(-SPLIT_SPREAD, SPLIT_SPREAD).withIndex()) shots.add(
                    Shot(
                        id = nextShotId++,
                        x = pos.x,
                        y = pos.y,
                        kind = s.kind,
                        born = time,
                        flight = s.flight * (1f - SPLIT_AT),
                        baseRadius = s.baseRadius * 0.75f,
                        maxHp = 1, hp = 1,
                        harmless = s.harmless,
                        dx = s.dx + side,
                        extra = k > 0 || s.extra,
                        damage = s.damage * 0.5f,
                        growFrom = grown.coerceIn(0.35f, 0.95f)
                    )
                )
                splitCount++
                continue
            }
            i++
        }

        // קליעים שהגיעו אל השחקן
        var hits = 0
        val hitKinds = ArrayList<ShotKind>()
        val hitSpots = ArrayList<Vec2>()
        val hitFrom = ArrayList<Vec2>()
        val hitPatterns = ArrayList<ShotPattern>()
        val sIterator = shots.iterator()
        while (sIterator.hasNext()) {
            val s = sIterator.next()
            // ירייה מבולבלת מתפוגגת באמצע הדרך (ראו drawShot), ולא מגיעה לשחקן
            if (time - s.born >= s.flight || (s.harmless && time - s.born >= s.flight * 0.62f)) {
                if (!s.harmless) {
                    chip += s.damage
                    if (chip < 0.999f) {
                        // כדור נותב בודד: עוד לא לב שלם (רק הבזק פגיעה)
                        chipHits++
                        sIterator.remove()
                        continue
                    }
                    chip -= 1f
                    hits++
                    hitKinds.add(s.kind)
                    hitSpots.add(s.posAt(s.born + s.flight))
                    hitFrom.add(Vec2(s.x, s.y))
                    hitPatterns.add(s.pattern)
                }
                sIterator.remove()
            }
        }

        // לתצוגה: מיקום וגודל ההכנות לפי המפלצת (שיכולה לזוז)
        telegraphViews.clear()
        for (t in telegraphs) {
            val enemy = enemies.firstOrNull { it.id == t.enemyId } ?: continue
            telegraphViews.add(
                TelegraphView(
                    x = enemy.position.x,
                    y = enemy.position.y,
                    radius = enemy.radius,
                    progress = ((time - t.start) / t.duration).coerceIn(0f, 1f),
                    enemyId = enemy.id,
                    kind = director.foeOf(enemy.id)?.let { ShotStyle.kindOf(it) }
                )
            )
        }
        return FireOutcome(hits, stopped, hitKinds, hitSpots, hitFrom, hitPatterns)
    }

    /** כמה שניות עברו מאז שהמפלצת ירתה לאחרונה (לאנימציית הירייה), או ערך גדול אם לא ירתה */
    fun secondsSinceLaunch(enemyId: Int): Float = lastLaunchAt[enemyId]?.let { time - it } ?: 999f

    /** האם המפלצת מושבתת עכשיו (פגיעה בנקודת תורפה): מסוחררת ולא תוקפת */
    fun isDisabled(enemyId: Int): Boolean = time < (disabledUntil[enemyId] ?: 0f)

    /** זמן עד ההתקפה הבאה. בוס כועס (שליש חיים אחרון) תוקף בקצב כפול */
    private fun cooldownFor(enemyId: Int, wave: WaveSpec, director: ArenaDirector): Float {
        val base = randomBetween(wave.cooldownMin, wave.cooldownMax)
        return if (enemyId == ArenaDirector.BOSS_ID && director.bossEnraged) base * 0.5f else base
    }

    private fun launch(enemy: Enemy, director: ArenaDirector, wave: WaveSpec) {
        val foe = director.foeOf(enemy.id) ?: return
        lastLaunchAt[enemy.id] = time
        launchCount++
        // הדרקון אחרי הנחיתה (השלב הכועס): שלושה כדורים במקביל, כל אחד קטן יותר וסופג פגיעה אחת
        val kind = ShotStyle.kindOf(foe)
        val harmless = time < (confusedUntil[enemy.id] ?: 0f)
        if (kind == ShotKind.Burst) {
            // צרור: 4 כדורי נותב בהפרש קצר, כל אחד רבע לב. מפלצת מוחלשת יורה פחות כדורים
            val count = (BURST_COUNT - (weakened[enemy.id] ?: 0)).coerceAtLeast(2)
            for (i in 0 until count) shots.add(
                Shot(
                    id = nextShotId++,
                    x = enemy.position.x,
                    y = enemy.position.y - enemy.radius * 0.1f,
                    kind = kind,
                    born = time + i * BURST_GAP,
                    flight = BURST_FLIGHT * wave.flightScale,
                    baseRadius = baseRadiusOf(kind),
                    maxHp = 99, hp = 99,
                    harmless = harmless,
                    dx = (random.nextFloat() - 0.5f) * 0.12f,
                    extra = i > 0,
                    damage = 1f / BURST_COUNT
                )
            )
            return
        }
        val pattern = ShotStyle.patternOf(foe, director.shotTier)
        if (pattern == ShotPattern.Fan) {
            // מניפה: שלושה קוצים, האמצעי ישר והצדדיים מעט מהירים יותר. כל אחד שליש לב
            for ((index, spread) in floatArrayOf(0f, -FAN_SPREAD, FAN_SPREAD).withIndex()) shots.add(
                Shot(
                    id = nextShotId++,
                    x = enemy.position.x,
                    y = enemy.position.y,
                    kind = kind,
                    born = time + index * 0.05f,
                    flight = foe.flightSeconds * wave.flightScale * (if (index == 0) 1f else 0.9f) * (1f + 0.2f * (weakened[enemy.id] ?: 0)),
                    baseRadius = baseRadiusOf(kind) * 0.8f,
                    maxHp = 1, hp = 1,
                    harmless = harmless,
                    dx = spread,
                    extra = index > 0,
                    damage = 1f / 3f,
                    pattern = pattern
                )
            )
            return
        }
        if (kind == ShotKind.Boulder) {
            // הסלע של הקולוסוס: יוצא מהיד המורמת (פעם ימין פעם שמאל), עולה בקשת בלי שאפשר לפגוע בו, ורק אז נעצר בירי.
            // כועס: שני סלעים, אחד מכל יד
            val sides = if (director.bossEnraged) listOf(-1f, 1f) else listOf(if (launchCount % 2 == 0) -1f else 1f)
            for ((index, side) in sides.withIndex()) shots.add(
                Shot(
                    id = nextShotId++,
                    x = (enemy.position.x + side * enemy.radius * BOULDER_HAND_X).coerceIn(0.08f, 0.92f),
                    y = enemy.position.y - enemy.radius * BOULDER_HAND_Y,
                    kind = kind,
                    born = time + index * 0.35f,
                    flight = foe.flightSeconds * 1.25f * wave.flightScale,
                    baseRadius = baseRadiusOf(kind),
                    maxHp = BOULDER_HP, hp = BOULDER_HP,
                    harmless = harmless,
                    dx = -side * 0.08f,
                    extra = index > 0,
                    pattern = ShotPattern.Lob,
                    armorUntil = BOULDER_ARMOR
                )
            )
            return
        }
        val triple = enemy.id == ArenaDirector.BOSS_ID && director.bossEnraged
        val offsets = if (triple) floatArrayOf(-0.17f, 0f, 0.17f) else floatArrayOf(0f)
        // זריקה בקשת איטית יותר (רואים אותה עולה), פצצת פתיל מבלה זמן בנפילה
        val patternTime = when (pattern) {
            ShotPattern.Lob -> 1.25f
            ShotPattern.Fuse -> 1.2f
            ShotPattern.Split -> 1.1f
            else -> 1f
        }
        for ((index, spread) in offsets.withIndex()) shots.add(
            Shot(
                id = nextShotId++,
                x = enemy.position.x,
                // כדור האנרגיה יוצא מהבטן ולא ממרכז הגוף (כמו בהכנה שבציור)
                // סליים החילזון יוצא מהפה: מעט מעל מרכז הגוף
                y = enemy.position.y + when (kind) {
                    ShotKind.Orb -> enemy.radius * 0.45f
                    ShotKind.Gas -> -enemy.radius * 0.06f
                    else -> 0f
                },
                kind = kind,
                born = time,
                flight = (if (kind == ShotKind.Rocket) ROCKET_FLIGHT else foe.flightSeconds) * patternTime * wave.flightScale * (1f + 0.2f * (weakened[enemy.id] ?: 0)) *
                    (if (enemy.id == ArenaDirector.BOSS_ID && director.bossEnraged) 0.85f else 1f),
                baseRadius = baseRadiusOf(kind) * (if (triple) 0.75f else 1f),
                maxHp = if (triple) 1 else if (kind == ShotKind.Rocket) 2 else if (kind == ShotKind.Boulder) 3 else foe.shotHp,
                hp = if (triple) 1 else if (kind == ShotKind.Rocket) 2 else if (kind == ShotKind.Boulder) 3 else foe.shotHp,
                harmless = harmless,
                dx = spread,
                extra = index > 0,
                pattern = if (triple) ShotPattern.Single else pattern
            )
        )
    }

    /** מסחרר מפלצת (הלם): לא תוקפת כמה שניות, וקוטע את ההכנה שלה */
    fun stun(enemyId: Int, seconds: Float) {
        disabledUntil[enemyId] = max(disabledUntil[enemyId] ?: 0f, time + seconds)
        cancelTelegraph(enemyId)
    }

    /** מנקה את כל הקליעים וההכנות באוויר (הלם) */
    fun clearAll() {
        shots.clear()
        telegraphs.clear()
        telegraphViews.clear()
    }

    /** פגיעה בנקודת תורפה: מפעילה את ההשפעה שלה על המפלצת */
    fun applyZone(enemyId: Int, zone: Zone) {
        if (zone.cancel) cancelTelegraph(enemyId)
        if (zone.disable > 0f) {
            disabledUntil[enemyId] = max(disabledUntil[enemyId] ?: 0f, time + zone.disable)
            cancelTelegraph(enemyId)
        }
        if (zone.confuse > 0f) confusedUntil[enemyId] = time + zone.confuse
        if (zone.slow > 0) weakened[enemyId] = ((weakened[enemyId] ?: 0) + zone.slow).coerceAtMost(3)
        val group = zone.group
        if (group != null) {
            val hit = groupHits.getOrPut(enemyId) { HashSet() }
            hit.add(zone.id)
            // שתי העיניים: המפלצת עיוורת לגמרי ולא תוקפת
            if (hit.size >= 2) {
                disabledUntil[enemyId] = max(disabledUntil[enemyId] ?: 0f, time + 6f)
                cancelTelegraph(enemyId)
                hit.clear()
            }
        }
    }

    /** קוטע הכנה לירייה של מפלצת (בלי למות) */
    private fun cancelTelegraph(enemyId: Int) {
        telegraphs.removeAll { it.enemyId == enemyId }
        nextAttack[enemyId] = time + 1.5f
    }

    /** הרדיוס הנוכחי של קליע (ביחידות גובה מסך): גדל מ-35% ל-100% מהרדיוס הסופי */
    fun currentRadius(shot: Shot): Float {
        val p = ((time - shot.born) / shot.flight).coerceIn(0f, 1f)
        if (shot.pattern == ShotPattern.Fuse && p < Shot.FUSE_DROP) return shot.baseRadius * shot.growFrom
        val q = if (shot.pattern == ShotPattern.Fuse) (p - Shot.FUSE_DROP) / (1f - Shot.FUSE_DROP) else p
        return shot.baseRadius * (shot.growFrom + (1f - shot.growFrom) * q)
    }

    /**
     * פגיעה בקליע: נקודה (לחיצה או כוונת) בקואורדינטות עולם. slack = מרווח נוסף ליד האצבע.
     * הקליע הקרוב ביותר שבטווח נפגע; קליע עם כמה חיים רק נסדק. null אם אין קליע בנקודה.
     */
    fun intercept(point: Vec2, aspect: Float, slack: Float): Intercept? {
        var best: Shot? = null
        var bestDistance = Float.MAX_VALUE
        for (s in shots) {
            // הסלע של הבוס בזמן שהוא עולה: עוד אי אפשר לעצור אותו (אחרת ירי רצוף על הבוס פוצץ אותו מיד ביציאה)
            if (s.armorUntil > 0f && s.progressAt(time) < s.armorUntil) continue
            // כדורי נותב מהירים מדי ליירוט; עוצרים את היורה
            if (s.kind == ShotKind.Burst || !s.launched(time)) continue
            val pos = s.posAt(time)
            val dx = (point.x - pos.x) * aspect
            val dy = point.y - pos.y
            val distance = sqrt(dx * dx + dy * dy)
            val reach = max(currentRadius(s), MIN_REACH) + slack
            if (distance <= reach && distance < bestDistance) {
                best = s
                bestDistance = distance
            }
        }
        val shot = best ?: return null
        shot.hp -= 1
        val destroyed = shot.hp <= 0
        if (destroyed) shots.remove(shot)
        val progress = shot.progressAt(time)
        val backfire = if (destroyed && shot.pattern == ShotPattern.Fuse && progress < Shot.BACKFIRE_UNTIL) shot.posAt(time) else null
        return Intercept(shot, destroyed, progress, backfire)
    }

    /** הרדיוס הסופי של קליע לפי סוגו, ביחידות גובה מסך (גדול מספיק ללחיצה עם אצבע) */
    private fun baseRadiusOf(kind: ShotKind): Float = when (kind) {
        ShotKind.Orb -> 0.075f
        ShotKind.Bomb -> 0.065f
        ShotKind.Gas -> 0.10f
        ShotKind.Rock -> 0.10f
        ShotKind.Spike -> 0.07f
        ShotKind.Fire -> 0.13f
        ShotKind.Burst -> 0.03f
        ShotKind.Rocket -> 0.085f
        ShotKind.Boulder -> 0.14f
    }

    private fun randomBetween(min: Float, max: Float): Float = min + random.nextFloat() * (max - min)

    companion object {
        /** כמה זמן המפלצת מהבהבת לפני שהיא יורה */
        const val TELEGRAPH_SECONDS = 0.8f

        /** רובה: קו לייזר ארוך יותר לפני הצרור (זה הזמן לעצור את היורה) */
        const val BURST_AIM_SECONDS = 1.1f
        const val BURST_COUNT = 4
        const val BURST_GAP = 0.12f
        const val BURST_FLIGHT = 0.55f

        /** טיל: איטי, כדי שיהיה זמן לפגוע בו פעמיים */
        const val ROCKET_FLIGHT = 2.6f

        /** הסלע של הבוס: מאיזה גובה ומרחק מהמרכז הוא יוצא (ברדיוסי בוס), כמה פגיעות, ועד איזה שלב הוא חסין */
        const val BOULDER_HAND_X = 0.9f
        const val BOULDER_HAND_Y = 1.7f
        const val BOULDER_HP = 4
        const val BOULDER_ARMOR = 0.4f

        /** מניפת קוצים: הסטה לרוחב של הקוצים הצדדיים */
        const val FAN_SPREAD = 0.2f

        /** ענן גז מתפצל: באיזה שלב, וכמה החצאים מתרחקים */
        const val SPLIT_AT = 0.45f
        const val SPLIT_SPREAD = 0.13f
        private const val LOST_GRACE = 0.4f

        /** גודל מינימלי לפגיעה בקליע (גם כשהוא עוד קטן), ביחידות גובה מסך */
        private const val MIN_REACH = 0.05f
    }
}

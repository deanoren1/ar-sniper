package com.arsniper.game.engine

import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import com.arsniper.game.model.EnemyState
import com.arsniper.game.model.Vec2
import kotlin.math.max
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
    val harmless: Boolean = false
)

/** הכנה לירייה: המפלצת מהבהבת באדום זמן קצר לפני שהיא יורה. אפשר להרוג אותה בזמן הזה */
class TelegraphView(val x: Float, val y: Float, val radius: Float, val progress: Float, val enemyId: Int)

/** תוצאת פגיעה בקליע: הקליע, והאם הושמד (או רק נסדק) */
class Intercept(val shot: Shot, val destroyed: Boolean)

/** מה קרה בפריים: כמה קליעים פגעו בשחקן, ואיפה נקטעה הכנה כי המפלצת נהרגה (לבונוס) */
class FireOutcome(val playerHits: Int, val stopped: List<Vec2>)

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
    private val nextAttack = HashMap<Int, Float>()
    private var nextShotId = 1

    /** השפעות נקודות תורפה לכל מפלצת: עד מתי היא לא תוקפת, עד מתי הירייה שלה מטעה, וכמה היא מוחלשת */
    private val disabledUntil = HashMap<Int, Float>()
    private val confusedUntil = HashMap<Int, Float>()
    private val weakened = HashMap<Int, Int>()
    private val groupHits = HashMap<Int, HashSet<String>>()
    private val lastLaunchAt = HashMap<Int, Float>()

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
        launchCount = 0
        time = 0f
    }

    /** האם האויב נראה עכשיו וכך יכול לירות (מציץ שחשוף לגמרי, או דמות שהולכת בחוץ) */
    private fun canAttack(e: Enemy): Boolean {
        if (!e.isAlive) return false
        return when (e.kind) {
            EnemyKind.Peek, EnemyKind.Window -> e.state == EnemyState.Exposed && e.exposure >= 0.95f
            EnemyKind.Open -> e.spot == null && e.exposure >= 0.95f
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
            if (enemy == null) {
                tIterator.remove()
            } else if (!enemy.isAlive) {
                stopped.add(enemy.position)
                tIterator.remove()
            } else if (time < (disabledUntil[enemy.id] ?: 0f)) {
                // הושבתה באמצע ההכנה (פגיעה בנקודת תורפה)
                tIterator.remove()
            } else if (!canAttack(enemy)) {
                // התחבאה באמצע ההכנה: לא יורה
                tIterator.remove()
                nextAttack[enemy.id] = time + cooldownFor(enemy.id, wave, director)
            } else if (time - t.start >= t.duration) {
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
            if (telegraphs.size + shots.size >= capacity) continue
            telegraphs.add(Telegraph(e.id, time, TELEGRAPH_SECONDS))
        }

        // קליעים שהגיעו אל השחקן
        var hits = 0
        val sIterator = shots.iterator()
        while (sIterator.hasNext()) {
            val s = sIterator.next()
            if (time - s.born >= s.flight) {
                if (!s.harmless) hits++
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
                    enemyId = enemy.id
                )
            )
        }
        return FireOutcome(hits, stopped)
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
        shots.add(
            Shot(
                id = nextShotId++,
                x = enemy.position.x,
                y = enemy.position.y,
                kind = foe.shot,
                born = time,
                flight = foe.flightSeconds * wave.flightScale * (1f + 0.2f * (weakened[enemy.id] ?: 0)) *
                    (if (enemy.id == ArenaDirector.BOSS_ID && director.bossEnraged) 0.85f else 1f),
                baseRadius = baseRadiusOf(foe.shot),
                maxHp = foe.shotHp,
                hp = foe.shotHp,
                harmless = time < (confusedUntil[enemy.id] ?: 0f)
            )
        )
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
        return shot.baseRadius * (0.35f + 0.65f * p)
    }

    /**
     * פגיעה בקליע: נקודה (לחיצה או כוונת) בקואורדינטות עולם. slack = מרווח נוסף ליד האצבע.
     * הקליע הקרוב ביותר שבטווח נפגע; קליע עם כמה חיים רק נסדק. null אם אין קליע בנקודה.
     */
    fun intercept(point: Vec2, aspect: Float, slack: Float): Intercept? {
        var best: Shot? = null
        var bestDistance = Float.MAX_VALUE
        for (s in shots) {
            val dx = (point.x - s.x) * aspect
            val dy = point.y - s.y
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
        return Intercept(shot, destroyed)
    }

    /** הרדיוס הסופי של קליע לפי סוגו, ביחידות גובה מסך (גדול מספיק ללחיצה עם אצבע) */
    private fun baseRadiusOf(kind: ShotKind): Float = when (kind) {
        ShotKind.Orb -> 0.075f
        ShotKind.Bomb -> 0.065f
        ShotKind.Gas -> 0.10f
        ShotKind.Rock -> 0.10f
        ShotKind.Spike -> 0.07f
        ShotKind.Fire -> 0.13f
    }

    private fun randomBetween(min: Float, max: Float): Float = min + random.nextFloat() * (max - min)

    companion object {
        /** כמה זמן המפלצת מהבהבת לפני שהיא יורה */
        const val TELEGRAPH_SECONDS = 0.8f

        /** גודל מינימלי לפגיעה בקליע (גם כשהוא עוד קטן), ביחידות גובה מסך */
        private const val MIN_REACH = 0.05f
    }
}

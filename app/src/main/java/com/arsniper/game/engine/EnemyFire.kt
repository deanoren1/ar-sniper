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
    var hp: Int
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

    fun reset() {
        shots.clear()
        telegraphs.clear()
        telegraphViews.clear()
        nextAttack.clear()
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
            } else if (!canAttack(enemy)) {
                // התחבאה באמצע ההכנה: לא יורה
                tIterator.remove()
                nextAttack[enemy.id] = time + randomBetween(wave.cooldownMin, wave.cooldownMax)
            } else if (time - t.start >= t.duration) {
                launch(enemy, director, wave)
                tIterator.remove()
                nextAttack[enemy.id] = time + randomBetween(wave.cooldownMin, wave.cooldownMax)
            }
        }

        // הכנות חדשות
        for (e in enemies) {
            if (director.foeOf(e.id) == null) continue
            if (!canAttack(e)) continue
            val due = nextAttack.getOrPut(e.id) { time + randomBetween(1.2f, 2.4f) }
            if (time < due) continue
            if (telegraphs.any { it.enemyId == e.id }) continue
            if (telegraphs.size + shots.size >= wave.maxAttacks) continue
            telegraphs.add(Telegraph(e.id, time, TELEGRAPH_SECONDS))
        }

        // קליעים שהגיעו אל השחקן
        var hits = 0
        val sIterator = shots.iterator()
        while (sIterator.hasNext()) {
            val s = sIterator.next()
            if (time - s.born >= s.flight) {
                hits++
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

    private fun launch(enemy: Enemy, director: ArenaDirector, wave: WaveSpec) {
        val foe = director.foeOf(enemy.id) ?: return
        shots.add(
            Shot(
                id = nextShotId++,
                x = enemy.position.x,
                y = enemy.position.y,
                kind = foe.shot,
                born = time,
                flight = foe.flightSeconds * wave.flightScale,
                baseRadius = baseRadiusOf(foe.shot),
                maxHp = foe.shotHp,
                hp = foe.shotHp
            )
        )
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
    }

    private fun randomBetween(min: Float, max: Float): Float = min + random.nextFloat() * (max - min)

    companion object {
        /** כמה זמן המפלצת מהבהבת לפני שהיא יורה */
        const val TELEGRAPH_SECONDS = 0.8f

        /** גודל מינימלי לפגיעה בקליע (גם כשהוא עוד קטן), ביחידות גובה מסך */
        private const val MIN_REACH = 0.05f
    }
}

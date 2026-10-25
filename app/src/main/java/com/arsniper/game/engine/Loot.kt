package com.arsniper.game.engine

import com.arsniper.game.model.Vec2
import kotlin.math.sqrt
import kotlin.random.Random

/** מה נופל ממפלצת: לב (מחזיר חיים) או כדורים (מחסנית מלאה מיד) */
enum class LootKind { Heart, Ammo, Diamond }

/** פרס אחד שנופל על הקרקע: x,y בקואורדינטות עולם (0..1), born = שעון הלוט כשנוצר */
class LootDrop(val id: Int, val x: Float, val y: Float, val kind: LootKind, val born: Float)

/**
 * פרסים שנופלים ממפלצות שמתות. Kotlin טהור.
 * לא כל מפלצת מפילה: סיכוי קבוע, ואחרי כמה הריגות בלי פרס, הבאה בטוח מפילה (כדי שלא יהיו רצפים ארוכים בלי כלום).
 * לב נופל רק כשחסר לשחקן לב. כדורים נופלים תמיד כשאין לב לתת, וביתר סיכוי כשהמחסנית כמעט ריקה.
 */
class Loot(private val random: Random = Random.Default) {

    val drops = ArrayList<LootDrop>()

    /** שעון הלוט בשניות, מתקדם רק כשהארנה רצה */
    var time: Float = 0f
        private set

    private var nextId = 1
    private var killsSinceDrop = 0

    fun reset() {
        drops.clear()
        time = 0f
        killsSinceDrop = 0
    }

    fun update(dt: Float) {
        time += dt
        drops.removeAll { time - it.born > LIFETIME_SECONDS }
    }

    /** כמה זמן הפרס כבר על הקרקע (שניות) */
    fun ageOf(drop: LootDrop): Float = time - drop.born

    /** מפלצת מתה במקום (x,y): אולי נופל פרס */
    fun onKill(x: Float, y: Float, hearts: Int, maxHearts: Int, ammo: Int, magSize: Int) {
        killsSinceDrop++
        // יהלום: נדיר, בנפרד מהפרס הרגיל
        if (random.nextFloat() < DIAMOND_CHANCE) {
            drops.add(LootDrop(nextId++, (x + 0.04f).coerceAtMost(0.98f), y, LootKind.Diamond, time))
        }
        val lowAmmo = ammo <= magSize / 3
        val chance = if (lowAmmo || hearts < maxHearts) DROP_CHANCE + 0.1f else DROP_CHANCE
        if (killsSinceDrop < PITY_KILLS && random.nextFloat() > chance) return
        killsSinceDrop = 0
        val wantHeart = hearts < maxHearts && (hearts <= 1 || random.nextFloat() < 0.55f)
        drops.add(LootDrop(nextId++, x, y, if (wantHeart) LootKind.Heart else LootKind.Ammo, time))
    }

    /**
     * אוסף את הפרס הקרוב ביותר שנמצא בטווח מהנקודה (לחיצה או כוונת). slack = מרווח נוסף ליד האצבע.
     * מחזיר אותו (והוא מוסר), או null.
     */
    fun collect(point: Vec2, aspect: Float, slack: Float): LootDrop? {
        var best: LootDrop? = null
        var bestDistance = Float.MAX_VALUE
        for (d in drops) {
            val dx = (point.x - d.x) * aspect
            val dy = point.y - (d.y - RISE)
            val distance = sqrt(dx * dx + dy * dy)
            if (distance <= RADIUS + slack && distance < bestDistance) {
                best = d
                bestDistance = distance
            }
        }
        val drop = best ?: return null
        drops.remove(drop)
        return drop
    }

    companion object {
        const val LIFETIME_SECONDS = 8f

        /** גובה הציור מעל נקודת הנפילה (יחידות גובה מסך): הפרס "מרחף" מעל הקרקע */
        const val RISE = 0.05f

        /** רדיוס האיסוף (יחידות גובה מסך) */
        const val RADIUS = 0.06f

        private const val DROP_CHANCE = 0.22f
        private const val DIAMOND_CHANCE = 0.04f
        private const val PITY_KILLS = 6
    }
}

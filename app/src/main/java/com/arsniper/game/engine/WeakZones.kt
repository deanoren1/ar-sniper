package com.arsniper.game.engine

import com.arsniper.game.model.Enemy
import com.arsniper.game.model.Vec2
import kotlin.math.sqrt

/**
 * נקודת תורפה על גוף מפלצת. המיקום יחסי לציור העמידה (מבט קדמי): x = היסט מהמרכז ביחידות של חצי רוחב
 * (שלילי = שמאל), y = מהראש (0) עד הרגליים (1), radius ביחידות של גובה הציור.
 * ההשפעה: damage = כמה חיים יורדים, cancel = קוטע התקפה שמתכוננת, disable = לא יכולה לתקוף כמה שניות,
 * confuse = הירייה הבאה שלה "מטעה" ולא פוגעת בשחקן, slow = הקליעים שלה איטיים יותר (נערם עד 3).
 * onlyTelegraph = נחשפת ופגיעה בה עובדת רק בזמן שהמפלצת מתכוננת לירות.
 * group = קבוצה שצריך לפגוע בכל חבריה (למשל שתי העיניים של החייזר) כדי להפעיל השפעה גדולה.
 */
class Zone(
    val id: String,
    val label: String,
    val x: Float,
    val y: Float,
    val radius: Float,
    val damage: Int,
    val points: Int,
    val cancel: Boolean = false,
    val disable: Float = 0f,
    val confuse: Float = 0f,
    val slow: Int = 0,
    val onlyTelegraph: Boolean = false,
    val group: String? = null
)

/** נקודת תורפה שצוירה עכשיו על המסך: המיקום (קואורדינטות עולם) ורדיוס הפגיעה (יחידות גובה מסך) */
class ZoneTarget(val enemy: Enemy, val zone: Zone, val anchor: Vec2, val hitRadius: Float)

/**
 * נקודות התורפה של כל מפלצת, לפי "חידת הירי" שלה: התמנון (העין), החייזר (מכלי הגז), הענק והקיפוד (הבטן
 * בזמן ההכנה). מפלצת הבלונים מטופלת בנפרד (הבלונים), וה-Zone שלה כאן משמש רק לשם ולנקודות.
 */
object WeakZones {

    /** הבלונים של מפלצת הבלונים: כל פגיעה מפוצצת בלון אחד */
    val BALLOONS = Zone("balloons", "POP!", 0f, 0.2f, 0.2f, damage = 1, points = 100)

    private val OCTOPUS = listOf(
        Zone("eye", "EYE HIT!", 0f, 0.33f, 0.14f, damage = 1, points = 200, confuse = 4f),
        Zone("tankL", "TANK OFF!", -0.88f, 0.45f, 0.12f, damage = 0, points = 150, disable = 5f),
        Zone("tankR", "TANK OFF!", 0.88f, 0.45f, 0.12f, damage = 0, points = 150, disable = 5f),
        Zone("dome", "CRACK!", 0f, 0.2f, 0.22f, damage = 1, points = 50)
    )

    private val ALIEN = listOf(
        Zone("eyeL", "BLINDED!", -0.48f, 0.1f, 0.10f, damage = 0, points = 120, confuse = 3f, group = "eyes"),
        Zone("eyeR", "BLINDED!", 0.48f, 0.1f, 0.10f, damage = 0, points = 120, confuse = 3f, group = "eyes"),
        Zone("gasL", "GAS OFF!", -0.62f, 0.52f, 0.14f, damage = 0, points = 200, disable = 5f),
        Zone("gasR", "GAS OFF!", 0.62f, 0.52f, 0.14f, damage = 0, points = 200, disable = 5f),
        Zone("pipeL", "LEAK!", -0.9f, 0.72f, 0.09f, damage = 0, points = 150, cancel = true, disable = 3f),
        Zone("pipeR", "LEAK!", 0.9f, 0.72f, 0.09f, damage = 0, points = 150, cancel = true, disable = 3f)
    )

    private val GIANT = listOf(
        Zone("face", "STUNNED!", 0f, 0.42f, 0.15f, damage = 1, points = 200, cancel = true, disable = 2.5f),
        Zone("tankL", "WEAKENED!", -0.78f, 0.3f, 0.12f, damage = 0, points = 150, slow = 1),
        Zone("tankR", "WEAKENED!", 0.78f, 0.3f, 0.12f, damage = 0, points = 150, slow = 1),
        Zone("belly", "BELLY!", 0f, 0.74f, 0.14f, damage = 2, points = 300, onlyTelegraph = true)
    )

    private val HEDGEHOG = listOf(
        Zone("spikes", "SPIKES!", 0f, 0.08f, 0.14f, damage = 0, points = 100, slow = 1),
        Zone("head", "DIZZY!", 0f, 0.4f, 0.15f, damage = 1, points = 200, cancel = true, disable = 2f, onlyTelegraph = true),
        Zone("belly", "BELLY!", 0f, 0.74f, 0.14f, damage = 2, points = 300, onlyTelegraph = true)
    )

    private val DRAGON = listOf(
        Zone("wingL", "WING!", -0.9f, 0.4f, 0.14f, damage = 0, points = 150, slow = 1),
        Zone("wingR", "WING!", 0.9f, 0.4f, 0.14f, damage = 0, points = 150, slow = 1),
        Zone("head", "HEAD!", 0f, 0.27f, 0.13f, damage = 1, points = 250, confuse = 3f),
        Zone("belly", "BELLY!", 0f, 0.72f, 0.14f, damage = 1, points = 200),
        Zone("mouth", "FIRE OUT!", 0f, 0.4f, 0.13f, damage = 3, points = 600, cancel = true, disable = 3f, onlyTelegraph = true)
    )

    /** סוג המפלצת לפי המספר שלה (id % 5 = שורת הספרייט), או null */
    fun foeByRow(enemyId: Int): Foe? {
        if (enemyId == ArenaDirector.BOSS_ID) return Foe.Dragon
        val row = ((enemyId % 5) + 5) % 5
        return Foe.values().firstOrNull { it.row == row }
    }

    fun zonesOf(foe: Foe?): List<Zone> = when (foe) {
        Foe.Octopus -> OCTOPUS
        Foe.Alien -> ALIEN
        Foe.Giant -> GIANT
        Foe.Hedgehog -> HEDGEHOG
        Foe.Dragon -> DRAGON
        else -> emptyList()
    }

    /**
     * נקודת התורפה שהכוונת עליה (הקרובה ביותר יחסית לגודלה), או null.
     * targets = מה שצויר בפריים האחרון. aspect = רוחב המסך חלקי גובהו.
     */
    fun hit(crosshair: Vec2, targets: List<ZoneTarget>, enemies: List<Enemy>, aspect: Float): ZoneTarget? {
        var best: ZoneTarget? = null
        var bestScore = Float.MAX_VALUE
        for (target in targets) {
            if (enemies.none { it.id == target.enemy.id && it.isAlive }) continue
            val dx = (crosshair.x - target.anchor.x) * aspect
            val dy = crosshair.y - target.anchor.y
            val distance = sqrt(dx * dx + dy * dy)
            if (distance > target.hitRadius) continue
            val score = distance / target.hitRadius
            if (score < bestScore) {
                best = target
                bestScore = score
            }
        }
        return best
    }
}

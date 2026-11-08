package com.arsniper.game.engine

import kotlin.math.hypot
import kotlin.random.Random

/**
 * עולם הקרח: שלב הבוס של ארנת הקרח. בוס ענק (ICE BEHEMOTH) שתופס כמעט את כל המסך, בלי מפלצות אחרות,
 * והכול אנימציה בלי קשר לתמונה האמיתית. פוגעים ביהלומים (גבישי הקרח שעל הבוס) כדי לשבור אותו.
 * Kotlin טהור: הציור נמצא ב-IceRealmLayer, וההתחברות למשחק ב-GameScreen.
 *
 * מהלך: פורטל נפתח בארנה, נמשכים פנימה, עולם הקרח, צללית של הבוס, חשיפה עם שאגה, ואז הקרב.
 * בקרב כל היהלומים תמיד גלויים (פגיעה = נזק 1). מדי כמה שניות הבוס מתחיל מתקפה ואחד היהלומים נדלק:
 * פגיעה ביהלום הדלוק שוברת בו 2 ומבטלת את המתקפה; אם לא פוגעים בו בזמן, השחקן מאבד לב.
 */
class IceRealm {

    enum class Stage { Off, Portal, Pull, Shift, Silhouette, Reveal, Fight, Dying, Done }

    /** יהלום: מיקום כשבר מתיבת הבוס (0..1), וחיים */
    class Diamond(val x: Float, val y: Float, var hp: Int) {
        val alive: Boolean get() = hp > 0
    }

    var stage: Stage = Stage.Off
        private set

    /** שניות מתחילת העולם (כולל המעבר) */
    var time: Float = 0f
        private set

    /** שניות בתוך השלב הנוכחי */
    var stageTime: Float = 0f
        private set

    val diamonds: List<Diamond> = listOf(
        Diamond(0.585f, 0.12f, DIAMOND_HP), // ראש
        Diamond(0.20f, 0.29f, DIAMOND_HP),  // כתף שמאל
        Diamond(0.84f, 0.29f, DIAMOND_HP),  // כתף ימין
        Diamond(0.09f, 0.62f, DIAMOND_HP),  // אגרוף שמאל
        Diamond(0.91f, 0.62f, DIAMOND_HP)   // אגרוף ימין
    )

    /** היהלום שנדלק במתקפה (-1 = אין מתקפה), וכמה זמן עבר מתחילת המתקפה */
    var exposed: Int = -1
        private set
    var windup: Float = 0f
        private set

    /** הבהוב נזק אחרי פגיעה ביהלום, והבהוב כשפגיעה לא עשתה כלום */
    var hitFlash: Float = 0f
        private set
    var deflectFlash: Float = 0f
        private set

    /** זמן שהבוס "מבוהל" אחרי שהמתקפה בוטלה */
    var stagger: Float = 0f
        private set

    private var attackIn: Float = FIRST_ATTACK_SECONDS

    /** האם הבוס כבר נוצח במהלך הריצה (כדי לא להתחיל שוב) */
    var finished: Boolean = false
        private set

    val active: Boolean get() = stage != Stage.Off && stage != Stage.Done

    val fighting: Boolean get() = stage == Stage.Fight

    val totalHp: Int get() = diamonds.sumOf { it.hp }
    val fraction: Float get() = totalHp.toFloat() / (diamonds.size * DIAMOND_HP)

    /** נשארו לפחות 2 יהלומים? (מי שנשאר אחד כועס: מתקפות מהירות יותר) */
    private val enraged: Boolean get() = diamonds.count { it.alive } <= 2

    fun begin() {
        if (stage != Stage.Off) return
        reset()
        stage = Stage.Portal
    }

    /** חוזר להתחלה (החייאה, משחק חוזר, או ארנה חדשה) */
    fun reset() {
        stage = Stage.Off
        time = 0f
        stageTime = 0f
        for (d in diamonds) d.hp = DIAMOND_HP
        exposed = -1
        windup = 0f
        hitFlash = 0f
        deflectFlash = 0f
        stagger = 0f
        attackIn = FIRST_ATTACK_SECONDS
        finished = false
    }

    private fun go(next: Stage) {
        stage = next
        stageTime = 0f
    }

    /**
     * מקדם פריים. onPlayerHit נקרא כשמתקפה הגיעה לשחקן, onWin כשהבוס נפל.
     */
    fun update(dt: Float, onPlayerHit: () -> Unit, onWin: () -> Unit) {
        if (!active) return
        time += dt
        stageTime += dt
        hitFlash = (hitFlash - dt).coerceAtLeast(0f)
        deflectFlash = (deflectFlash - dt).coerceAtLeast(0f)
        stagger = (stagger - dt).coerceAtLeast(0f)
        when (stage) {
            Stage.Portal -> if (stageTime >= PORTAL_SECONDS) go(Stage.Pull)
            Stage.Pull -> if (stageTime >= PULL_SECONDS) go(Stage.Shift)
            Stage.Shift -> if (stageTime >= SHIFT_SECONDS) go(Stage.Silhouette)
            Stage.Silhouette -> if (stageTime >= SILHOUETTE_SECONDS) go(Stage.Reveal)
            Stage.Reveal -> if (stageTime >= REVEAL_SECONDS) go(Stage.Fight)
            Stage.Fight -> fight(dt, onPlayerHit)
            Stage.Dying -> if (stageTime >= DYING_SECONDS) {
                go(Stage.Done)
                finished = true
                onWin()
            }
            else -> Unit
        }
    }

    private fun fight(dt: Float, onPlayerHit: () -> Unit) {
        if (stagger > 0f) return
        if (exposed >= 0) {
            windup += dt
            if (windup >= windupSeconds()) {
                exposed = -1
                windup = 0f
                attackIn = nextCooldown()
                onPlayerHit()
            }
        } else {
            attackIn -= dt
            if (attackIn <= 0f) {
                val live = diamonds.indices.filter { diamonds[it].alive }
                if (live.isNotEmpty()) {
                    exposed = live[Random.nextInt(live.size)]
                    windup = 0f
                }
            }
        }
    }

    private fun windupSeconds() = if (enraged) WINDUP_ENRAGED_SECONDS else WINDUP_SECONDS
    private fun nextCooldown() = (if (enraged) 2.0f else 3.0f) + Random.nextFloat() * 1.0f

    /** התקדמות המתקפה 0..1 (להצגה), או 0 אם אין */
    val windupFraction: Float get() = if (exposed >= 0) (windup / windupSeconds()).coerceIn(0f, 1f) else 0f

    /**
     * ירי על נקודה על המסך (px, py בשברי מסך). מחזיר מה קרה.
     * aspect = רוחב/גובה של המסך. היהלומים עגולים על המסך (לא אליפסה), ולכן המרחק נמדד עם התאמת יחס.
     */
    fun shoot(px: Float, py: Float, aspect: Float, bossBob: Float = 0f): Hit {
        if (stage != Stage.Fight) return Hit.None
        val rect = bossRect(aspect, bossBob)
        var best = -1
        var bestD = Float.MAX_VALUE
        for ((i, d) in diamonds.withIndex()) {
            if (!d.alive) continue
            val cx = rect[0] + d.x * rect[2]
            val cy = rect[1] + d.y * rect[3]
            val dist = hypot((px - cx) * aspect, py - cy)
            if (dist < HIT_RADIUS && dist < bestD) { best = i; bestD = dist }
        }
        if (best < 0) {
            val inBody = px >= rect[0] && px <= rect[0] + rect[2] && py >= rect[1] && py <= rect[1] + rect[3]
            if (inBody) deflectFlash = 0.18f
            return if (inBody) Hit.Deflect else Hit.None
        }
        val d = diamonds[best]
        val cancel = best == exposed
        d.hp = (d.hp - if (cancel) EXPOSED_DAMAGE else 1).coerceAtLeast(0)
        hitFlash = 0.22f
        if (cancel) {
            exposed = -1
            windup = 0f
            attackIn = nextCooldown()
            stagger = STAGGER_SECONDS
        }
        if (diamonds.none { it.alive }) {
            exposed = -1
            go(Stage.Dying)
            return Hit.Killed
        }
        return if (d.hp == 0) Hit.Broke else if (cancel) Hit.Cancel else Hit.Hit
    }

    enum class Hit { None, Deflect, Hit, Cancel, Broke, Killed }

    companion object {
        const val DIAMOND_HP = 3
        const val EXPOSED_DAMAGE = 2
        const val FIRST_ATTACK_SECONDS = 2.5f
        const val WINDUP_SECONDS = 1.7f
        const val WINDUP_ENRAGED_SECONDS = 1.25f
        const val STAGGER_SECONDS = 1.1f
        const val HIT_RADIUS = 0.075f   // בגובה מסך

        const val PORTAL_SECONDS = 1.3f
        const val PULL_SECONDS = 1.8f
        const val SHIFT_SECONDS = 0.9f
        const val SILHOUETTE_SECONDS = 0.8f
        const val REVEAL_SECONDS = 1.2f
        const val DYING_SECONDS = 2.4f

        /** תמונת הבוס: יחס רוחב/גובה, וגובה הבוס כשבר מגובה המסך */
        const val BOSS_IMAGE_ASPECT = 454f / 354f
        const val BOSS_HEIGHT = 0.90f
        const val BOSS_BOTTOM = 1.05f

        /** תיבת הבוס בשברי מסך: [שמאל, למעלה, רוחב, גובה]. bob = תזוזה אנכית (שבר מגובה המסך) */
        fun bossRect(aspect: Float, bob: Float = 0f): FloatArray {
            val h = BOSS_HEIGHT
            val w = h * BOSS_IMAGE_ASPECT / aspect
            return floatArrayOf(0.5f - w / 2f, BOSS_BOTTOM - h + bob, w, h)
        }
    }
}

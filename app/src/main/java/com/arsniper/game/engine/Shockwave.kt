package com.arsniper.game.engine

/**
 * מד ההלם: מתמלא עם הזמן (בזמן לחימה), וכל הריגה או הדיפת קליע מקצרות את הזמן.
 * כשהוא מלא אפשר להפעיל הלם שמחסל את כל המפלצות הרגילות וקליעיהן. Kotlin טהור.
 */
class Shockwave(var chargeSeconds: Float = DEFAULT_CHARGE_SECONDS) {

    private var elapsed: Float = 0f

    /** כמה מהמד מלא (0..1) */
    val fraction: Float get() = (elapsed / chargeSeconds).coerceIn(0f, 1f)

    val ready: Boolean get() = elapsed >= chargeSeconds

    fun update(dt: Float) {
        if (!ready) elapsed = (elapsed + dt).coerceAtMost(chargeSeconds)
    }

    /** הריגה או הדיפה: מקדמת את הטעינה */
    fun reward(seconds: Float = KILL_REWARD_SECONDS) {
        elapsed = (elapsed + seconds).coerceAtMost(chargeSeconds)
    }

    /** מפעיל: אם המד מלא מאפס אותו ומחזיר true */
    fun trigger(): Boolean {
        if (!ready) return false
        elapsed = 0f
        return true
    }

    /** טעינה מיידית (יהלום) */
    fun chargeNow() {
        elapsed = chargeSeconds
    }

    fun reset() {
        elapsed = 0f
    }

    companion object {
        const val DEFAULT_CHARGE_SECONDS = 25f
        const val KILL_REWARD_SECONDS = 0f

        /** כמה מחיי הבוס ההלם מוריד */
        const val BOSS_DAMAGE_FRACTION = 0.15f

        /** כמה זמן הבוס מסוחרר מההלם */
        const val BOSS_STUN_SECONDS = 3f
    }
}

package com.arsniper.game.engine

import kotlin.math.max

/** תוצאת לחיצה על ירייה */
enum class ShotResult {
    /** יצאה ירייה (נצרכה כדור) */
    Fired,

    /** המחסנית ריקה: מתחילה טעינה (ולא יוצאת ירייה) */
    Empty,

    /** באמצע טעינה: אי אפשר לירות */
    Reloading
}

/**
 * הרובה של השחקן: מחסנית עם כדורים וטעינה. Kotlin טהור.
 * הגנה בלחיצה על קליע (אצבע) לא צורכת כדורים. רק כפתור FIRE.
 * כשהמחסנית מתרוקנת הטעינה מתחילה אוטומטית. אפשר גם לטעון ידנית לפני שהיא ריקה.
 */
class Weapon(
    val magSize: Int = 12,
    val reloadSeconds: Float = 1.1f
) {
    var ammo: Int = magSize
        private set

    var reloading: Boolean = false
        private set

    /** כמה זמן מהטעינה עבר (שניות) */
    private var reloadElapsed: Float = 0f

    /** כמה זמן עבר מהירייה האחרונה (לתנוחת הירי וההדף) */
    var sinceShot: Float = 999f
        private set

    /** התקדמות הטעינה 0..1 (0 כשלא טוענת) */
    val reloadProgress: Float
        get() = if (reloading) (reloadElapsed / reloadSeconds).coerceIn(0f, 1f) else 0f

    fun update(dt: Float) {
        sinceShot += dt
        if (reloading) {
            reloadElapsed += dt
            if (reloadElapsed >= reloadSeconds) {
                reloading = false
                reloadElapsed = 0f
                ammo = magSize
            }
        }
    }

    fun tryFire(): ShotResult {
        if (reloading) return ShotResult.Reloading
        if (ammo <= 0) {
            startReload()
            return ShotResult.Empty
        }
        ammo -= 1
        sinceShot = 0f
        if (ammo == 0) startReload()
        return ShotResult.Fired
    }

    /** טעינה ידנית: רק אם יש מקום במחסנית ולא טוענת כבר. מחזיר true אם התחילה טעינה */
    fun reload(): Boolean {
        if (reloading || ammo >= magSize) return false
        startReload()
        return true
    }

    private fun startReload() {
        reloading = true
        reloadElapsed = 0f
    }

    /** מחסנית מלאה מיד (בתחילת גל) */
    fun refill() {
        ammo = magSize
        reloading = false
        reloadElapsed = 0f
    }

    fun reset() {
        refill()
        sinceShot = 999f
    }

    /** כמה פגיעות נשארו עד סוף הטעינה (לתצוגה) */
    val secondsToReady: Float get() = if (reloading) max(0f, reloadSeconds - reloadElapsed) else 0f
}

package com.arsniper.game.engine

/**
 * קומבו: כמה הצלחות ברצף (הריגה, עצירת קליע, פגיעה בנקודת תורפה) בלי החטאה ובלי לספוג פגיעה.
 * המכפיל גדל ככל שהרצף ארוך יותר ומכפיל את הניקוד. הרצף נשבר בפספוס, בפגיעה בשחקן, או אחרי זמן בלי הצלחה.
 * Kotlin טהור.
 */
class ComboTracker {

    var count: Int = 0
        private set

    private var idle: Float = 0f

    /** מכפיל הניקוד הנוכחי: x1 עד x3 */
    val multiplier: Int get() = (1 + count / STEP).coerceAtMost(MAX_MULTIPLIER)

    /** הצלחה: מאריכה את הרצף ומאפסת את שעון חוסר הפעילות */
    fun register() {
        count += 1
        idle = 0f
    }

    /** פספוס או פגיעה בשחקן: הרצף נשבר */
    fun reset() {
        count = 0
        idle = 0f
    }

    /** מקדם את השעון: רצף שלא התחדש זמן רב נשבר */
    fun update(dt: Float) {
        if (count == 0) return
        idle += dt
        if (idle >= TIMEOUT_SECONDS) reset()
    }

    companion object {
        /** כל כמה הצלחות המכפיל עולה */
        const val STEP = 6
        const val MAX_MULTIPLIER = 3
        const val TIMEOUT_SECONDS = 7f
    }
}

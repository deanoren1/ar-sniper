package com.arsniper.game.engine

/**
 * כניסת בוס דרך פורטל, בתוך הארנה עצמה (בלי סרטונים ובלי מעבר לעולם אחר):
 * הגאוגרפיה של העולם נשארת, הכוונת והנשק נשארים במקומם, והבוס נחשף מתוך ערפל ומערבולת.
 *
 * ציר הזמן (שניות מרגע הופעת הבוס, לפי זמן המשחק כך שהשהיה עוצרת גם אותו):
 *  0 → OPEN          מערבולת נפתחת במקום של הבוס, ערפל נשאב פנימה
 *  OPEN → REVEAL     צללית כהה עם עיניים זוהרות מתקרבת מתוך הפורטל, ברקים על השפה
 *  REVEAL → BURST    הבוס פורץ החוצה: הבזק, אבנים עפות, גל הלם על הרצפה, רעידה ושאגה
 *  BURST → CLOSE     הפורטל מתכווץ ונעלם מאחוריו, הערפל מתפזר
 * עד BURST הבוס לא פגיע ולא יורה.
 *
 * Kotlin טהור: הציור ב-BossPortal.kt, ההפעלה ב-GameScreen, הזמן מתקדם ב-ArenaDirector.
 */
object BossEntrance {
    /** העולמות שהבוס שלהם נכנס דרך פורטל */
    private val PORTAL_WORLDS = setOf("jungle")

    fun portalWorld(key: String): Boolean = key in PORTAL_WORLDS

    /** הבוס של הארנה הנוכחית נכנס דרך פורטל */
    @Volatile
    var portal: Boolean = false

    /** שניות מאז שהבוס הופיע (-1 = אין בוס עדיין) */
    @Volatile
    var age: Float = -1f

    const val OPEN = 0.9f
    const val REVEAL = 1.9f
    const val BURST = 2.3f
    const val CLOSE = 3.3f

    /** הבוס עוד בתוך הפורטל: לא פגיע, לא יורה */
    val entering: Boolean get() = portal && age in 0f..BURST

    /** יש מה לצייר (פורטל, ערפל, אבנים) */
    val drawing: Boolean get() = portal && age >= 0f && age < CLOSE + 0.6f

    fun reset() {
        age = -1f
    }
}

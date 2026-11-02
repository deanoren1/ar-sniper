package com.arsniper.game.engine

import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import kotlin.math.sin

/**
 * ריחוף של מפלצת הבלונים: היא עפה באוויר כמו בלון הליום ונעה מעט למעלה ולמטה.
 * ההרמה היא רק בציור ובפגיעות (לא במנוע): EnemyLayer מצייר מורם, ולוגיקת הפגיעה והירי מקבלת
 * את הרשימה דרך lifted, כך שמה שרואים הוא מה שפוגעים בו. Kotlin טהור.
 */
object Hover {

    /**
     * כמה גבוה היא מרחפת מעבר לגובה שכבר מצויר בגיליון, ברדיוסים של המפלצת, לפי מספר הבלונים שנשארו (0..3), ועוצמת התנודה.
     * שלושה בלונים: גבוה. בלון אחד: הרגליים כמעט נוגעות בקרקע. בלי בלונים: על הקרקע
     */
    private val HEIGHT_RADII = floatArrayOf(0f, 0f, 0.45f, 0.9f)
    private const val BOB_RADII = 0.14f

    /** "חיים" של מפלצת הבלונים: ארבעה. שלושה בלונים, שניים, אחד, ובלי בלונים (נפלה, מוממת וחשופה) */
    const val LIVES = 4

    /** מפלצת הבלונים היא שורה 3 בגיליון: מספר האויב מודולו 5 */
    fun isBalloonMonster(enemy: Enemy): Boolean = ((enemy.id % 5) + 5) % 5 == 3

    /** כמה בלונים נשארו (3..0). בלי חיים רשומים: שלושה */
    fun balloons(enemy: Enemy): Int {
        if (!isBalloonMonster(enemy) || enemy.maxHp <= 0) return 3
        val lives = kotlin.math.ceil(enemy.hp * LIVES.toFloat() / enemy.maxHp).toInt().coerceIn(1, LIVES)
        return lives - 1
    }

    /** בלי בלונים: מוממת על הקרקע. לא זזה, לא יורה, והגוף שלה חשוף לפגיעה */
    fun isGrounded(enemy: Enemy): Boolean = isBalloonMonster(enemy) && enemy.maxHp > 0 && balloons(enemy) == 0

    /** גובה ההרמה, ביחידות גובה מסך. מציץ שמאחורי עצם לא מרחף */
    fun lift(enemy: Enemy, nowSeconds: Double): Float {
        if (!isBalloonMonster(enemy)) return 0f
        val free = (enemy.kind == EnemyKind.Open && enemy.spot == null) || enemy.kind == EnemyKind.Static
        if (!free) return 0f
        val count = balloons(enemy)
        if (count <= 0) return 0f
        val bob = 0.5f + 0.5f * sin(nowSeconds * 2.2 + enemy.id).toFloat()
        return enemy.radius * (HEIGHT_RADII[count] + BOB_RADII * bob * count / 3f)
    }

    /** הרשימה עם המיקום המורם של מפלצות מרחפות (אותה רשימה אם אין כאלה) */
    fun lifted(enemies: List<Enemy>, nowSeconds: Double): List<Enemy> {
        if (enemies.none { lift(it, nowSeconds) > 0f }) return enemies
        return enemies.map {
            val lift = lift(it, nowSeconds)
            if (lift > 0f) it.copy(position = it.position.copy(y = it.position.y - lift)) else it
        }
    }
}

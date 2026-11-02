package com.arsniper.game.engine

import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import kotlin.math.PI
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

    // --- דחיפה הצידה: כשבלון מתפוצץ, הפיצוץ מעיף את המפלצת הצידה ואז היא חוזרת ---
    private class Pop(var last: Int, var at: Double, var dir: Float)

    private val pops = HashMap<Int, Pop>()
    /** יחס מסך משוער להמרת גובה לרוחב בפגיעות (ההיסט קטן, הסטייה זניחה) */
    private const val ASPECT_GUESS = 2.1f
    private const val SHOVE_SECONDS = 0.6f
    private const val SHOVE_RADII = 1.1f

    /** היסט אופקי (ביחידות גובה מסך) של מפלצת בלונים שבלון עתה התפוצץ לידה. 0 כשאין דחיפה */
    fun shove(enemy: Enemy, nowSeconds: Double): Float {
        if (!isBalloonMonster(enemy) || enemy.maxHp <= 0) return 0f
        val free = (enemy.kind == EnemyKind.Open && enemy.spot == null) || enemy.kind == EnemyKind.Static
        if (!free) return 0f
        val count = balloons(enemy)
        val pop = pops.getOrPut(enemy.id) { Pop(count, -100.0, 1f) }
        if (count < pop.last) {
            pop.at = nowSeconds
            // לא דוחפים אל מחוץ למסך
            pop.dir = when {
                enemy.position.x < 0.22f -> 1f
                enemy.position.x > 0.78f -> -1f
                else -> if (Math.random() < 0.5) -1f else 1f
            }
        }
        pop.last = count
        val e = ((nowSeconds - pop.at) / SHOVE_SECONDS).toFloat()
        if (e < 0f || e >= 1f) return 0f
        return pop.dir * enemy.radius * SHOVE_RADII * sin(e * PI.toFloat()) * (1f - e)
    }

    /** הרשימה עם המיקום המורם של מפלצות מרחפות (אותה רשימה אם אין כאלה) */
    fun lifted(enemies: List<Enemy>, nowSeconds: Double): List<Enemy> {
        if (enemies.none { lift(it, nowSeconds) > 0f || shove(it, nowSeconds) != 0f }) return enemies
        return enemies.map {
            val lift = lift(it, nowSeconds)
            val dx = shove(it, nowSeconds) / ASPECT_GUESS
            if (lift > 0f || dx != 0f) it.copy(position = it.position.copy(x = it.position.x + dx, y = it.position.y - lift)) else it
        }
    }
}

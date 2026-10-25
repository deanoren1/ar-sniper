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

    /** כמה גבוה היא מרחפת, ברדיוסים של המפלצת, ועוצמת התנודה */
    private const val HEIGHT_RADII = 1.1f
    private const val BOB_RADII = 0.15f

    /** מפלצת הבלונים היא שורה 3 בגיליון: מספר האויב מודולו 5 */
    fun isBalloonMonster(enemy: Enemy): Boolean = ((enemy.id % 5) + 5) % 5 == 3

    /** גובה ההרמה, ביחידות גובה מסך. מציץ שמאחורי עצם לא מרחף */
    fun lift(enemy: Enemy, nowSeconds: Double): Float {
        if (!isBalloonMonster(enemy)) return 0f
        val free = (enemy.kind == EnemyKind.Open && enemy.spot == null) || enemy.kind == EnemyKind.Static
        if (!free) return 0f
        val bob = sin(nowSeconds * 2.2 + enemy.id).toFloat()
        return enemy.radius * (HEIGHT_RADII + BOB_RADII * bob)
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

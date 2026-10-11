package com.arsniper.game.engine

import com.arsniper.game.model.Enemy
import com.arsniper.game.model.Vec2
import kotlin.math.sqrt
import kotlin.random.Random

/** תוצאת ירייה */
sealed interface FireResult {
    data class Hit(val enemy: Enemy) : FireResult
    data object Miss : FireResult
}

/**
 * מנוע המשחק: Kotlin טהור, בלי תלות ב-Android.
 * הכוונת והאויבים בקואורדינטות עולם מנורמלות (0..1) ביחס לתמונה.
 */
class GameEngine(
    /** מהירות מלאה: חלק מרוחב העולם בשנייה (ב-zoom=1) */
    private val crosshairSpeed: Float = 0.4f,
    private val random: Random = Random.Default
) {

    private var nextEnemyId: Int = 1

    /**
     * input: כיוון ה-joystick (-1..1 בכל ציר)
     * aspect: רוחב המסך חלקי גובהו, כדי שהמהירות תהיה זהה פיזית בשני הצירים
     * zoom: ככל שמתקרבים, הכוונת איטית יותר (כיוון מדויק)
     */
    fun moveCrosshair(
        current: Vec2,
        input: Vec2,
        dtSeconds: Float,
        aspect: Float,
        zoom: Float = 1f
    ): Vec2 {
        val dt = dtSeconds.coerceAtMost(0.05f)
        val speed = crosshairSpeed / zoom.coerceAtLeast(1f)
        val dx = input.x * speed * dt
        val dy = input.y * speed * dt * aspect
        return Vec2(
            (current.x + dx).coerceIn(0f, 1f),
            (current.y + dy).coerceIn(0f, 1f)
        )
    }

    /** ירייה: מחזירה את האויב שנפגע, או Miss אם פספסנו */
    fun fire(
        crosshair: Vec2,
        enemies: List<Enemy>,
        aspect: Float
    ): FireResult {
        val target = enemies.firstOrNull {
            it.isAlive && HitDetector.isHit(crosshair, it, aspect)
        }
        return if (target != null) FireResult.Hit(target) else FireResult.Miss
    }

    /**
     * משלים את רשימת האויבים עד targetCount אויבים חיים.
     * אויבים מתים מוסרים מהרשימה, וחדשים נוצרים במקומות אקראיים.
     */
    fun replenish(
        current: List<Enemy>,
        targetCount: Int,
        aspect: Float
    ): List<Enemy> {
        val result = current.filter { it.isAlive }.toMutableList()
        while (result.size < targetCount) {
            result.add(spawnEnemy(result, aspect))
        }
        return result
    }

    /** יוצר אויב במקום אקראי, הרחק מהקצוות, מאזור הכפתורים ומאויבים אחרים */
    private fun spawnEnemy(existing: List<Enemy>, aspect: Float): Enemy {
        val minDistance = 0.25f // ביחידות של גובה המסך
        var candidate = randomEnemy()
        repeat(15) {
            val tooClose = existing.any { other ->
                val dx = (candidate.position.x - other.position.x) * aspect
                val dy = candidate.position.y - other.position.y
                sqrt(dx * dx + dy * dy) < minDistance
            }
            if (!tooClose) return candidate
            candidate = randomEnemy()
        }
        return candidate
    }

    private fun randomEnemy(): Enemy {
        val x = random.nextFloat() * 0.6f + 0.2f   // 0.2 .. 0.8
        val y = random.nextFloat() * 0.45f + 0.2f  // 0.2 .. 0.65
        return Enemy(id = nextEnemyId++, position = Vec2(x, y))
    }
}

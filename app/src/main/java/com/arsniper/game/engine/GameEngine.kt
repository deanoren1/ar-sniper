package com.arsniper.game.engine

import com.arsniper.game.model.Enemy
import com.arsniper.game.model.Vec2

/** תוצאת ירייה */
sealed interface FireResult {
    data class Hit(val enemy: Enemy) : FireResult
    data object Miss : FireResult
}

/**
 * מנוע המשחק: Kotlin טהור, בלי תלות ב-Android.
 * הכוונת נעה בקואורדינטות מנורמלות (0..1) ביחס למסך.
 */
class GameEngine(
    /** מהירות מלאה: חלק מרוחב המסך בשנייה */
    private val crosshairSpeed: Float = 0.4f
) {

    /**
     * input: כיוון ה-joystick (-1..1 בכל ציר)
     * aspect: רוחב המסך חלקי גובהו, כדי שהמהירות תהיה זהה פיזית בשני הצירים
     */
    fun moveCrosshair(
        current: Vec2,
        input: Vec2,
        dtSeconds: Float,
        aspect: Float
    ): Vec2 {
        val dt = dtSeconds.coerceAtMost(0.05f)
        val dx = input.x * crosshairSpeed * dt
        val dy = input.y * crosshairSpeed * dt * aspect
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
}

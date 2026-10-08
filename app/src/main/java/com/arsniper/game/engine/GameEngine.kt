package com.arsniper.game.engine

import com.arsniper.game.model.Vec2

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
}

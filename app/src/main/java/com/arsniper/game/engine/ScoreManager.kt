package com.arsniper.game.engine

/** חישוב ניקוד. טהור: מקבל ניקוד נוכחי ותוצאת ירייה ומחזיר ניקוד חדש. */
class ScoreManager(
    private val pointsPerHit: Int = 100
) {
    fun apply(currentScore: Int, result: FireResult): Int =
        when (result) {
            is FireResult.Hit -> currentScore + pointsPerHit
            is FireResult.Miss -> currentScore
        }
}

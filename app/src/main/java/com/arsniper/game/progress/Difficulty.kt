package com.arsniper.game.progress

import com.arsniper.game.engine.Foe
import com.arsniper.game.engine.WaveSpec
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * רמת קושי: משפיעה על מספר המפלצות בכל גל, על קצב ההתקפות ומהירות הקליעים, ועל התגמול.
 * Kotlin טהור. לא משנה את גל הבוס (הדרקון) עצמו, רק את מי שמלווה אותו ואת קצב ההתקפות.
 */
enum class Difficulty(
    val title: String,
    val foeScale: Float,
    val cooldownScale: Float,
    val flightScale: Float,
    val rewardScale: Float
) {
    EASY("EASY", 0.7f, 1.25f, 1.15f, 0.75f),
    NORMAL("NORMAL", 1f, 1f, 1f, 1f),
    HARD("HARD", 1.4f, 0.8f, 0.9f, 1.5f);

    /** מחיל את הקושי על רשימת הגלים. NORMAL מחזיר את הרשימה כמו שהיא */
    fun apply(waves: List<WaveSpec>): List<WaveSpec> {
        if (this == NORMAL) return waves
        return waves.map { w ->
            val boss = w.foes.contains(Foe.Dragon)
            WaveSpec(
                foes = if (boss) w.foes else scaleFoes(w.foes),
                maxThreat = w.maxThreat,
                maxAttacks = w.maxAttacks,
                cooldownMin = w.cooldownMin * cooldownScale,
                cooldownMax = w.cooldownMax * cooldownScale,
                flightScale = w.flightScale * flightScale
            )
        }
    }

    /** הסוג החדש של הגל (האחרון ברשימה) נשאר תמיד, ושאר המפלצות מוסרות או מתווספות */
    private fun scaleFoes(foes: List<Foe>): List<Foe> {
        if (foes.size < 2) return foes
        val target = max(2, (foes.size * foeScale).roundToInt())
        val last = foes.last()
        val rest = foes.dropLast(1)
        return if (target <= foes.size) {
            rest.take(target - 1) + last
        } else {
            val extra = (0 until target - foes.size).map { rest[it % rest.size] }
            rest + extra + last
        }
    }

    companion object {
        fun fromName(name: String?): Difficulty = values().firstOrNull { it.name == name } ?: NORMAL
    }
}

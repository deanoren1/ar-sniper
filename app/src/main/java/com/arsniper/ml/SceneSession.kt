package com.arsniper.ml

import com.arsniper.game.spots.HidingSpot
import com.arsniper.game.spots.HidingSpotFinder

/**
 * תוצאת ניתוח הסצנה של הזירה הנוכחית, שעוברת ממסך הסצנה למסך המשחק (בזיכרון בלבד).
 */
object SceneSession {

    var analysis: SceneAnalysis? = null
        private set

    var spots: List<HidingSpot> = emptyList()
        private set

    fun set(analysis: SceneAnalysis) {
        this.analysis = analysis
        this.spots = HidingSpotFinder.find(analysis)
    }

    fun clear() {
        analysis = null
        spots = emptyList()
    }
}

package com.arsniper.ml

import com.arsniper.game.spots.GuideImage
import com.arsniper.game.spots.HidingSpot
import com.arsniper.game.spots.HidingSpotFinder
import com.arsniper.game.spots.SpotRefiner

/**
 * תוצאת ניתוח הסצנה של הזירה הנוכחית, שעוברת ממסך הסצנה למסך המשחק (בזיכרון בלבד).
 */
object SceneSession {

    var analysis: SceneAnalysis? = null
        private set

    var spots: List<HidingSpot> = emptyList()
        private set

    /**
     * שומר את הניתוח, מוצא מחבואים, ואם יש תמונת עזר (guide) מלטש את הקצוות שלהם.
     */
    fun set(analysis: SceneAnalysis, guide: GuideImage? = null) {
        this.analysis = analysis
        val found = HidingSpotFinder.find(analysis)
        this.spots = if (guide != null) {
            found.map { SpotRefiner.refine(it, guide) }
        } else {
            found
        }
    }

    fun clear() {
        analysis = null
        spots = emptyList()
    }
}
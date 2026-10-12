package com.arsniper.game.engine

import com.arsniper.game.model.WorldMapper
import com.arsniper.game.spots.HidingSpot
import com.arsniper.ml.Ade20kClasses
import com.arsniper.ml.SceneAnalysis

/**
 * מה שהמנוע יודע על הסצנה: ניתוח התמונה, מקומות ההסתתרות והמרת קואורדינטות.
 * כל הפונקציות כאן מקבלות קואורדינטות עולם (0..1 ביחס למסך).
 */
class SceneContext(
    val analysis: SceneAnalysis,
    val spots: List<HidingSpot>,
    val mapper: WorldMapper
) {

    private val walkable = BooleanArray(256).also { table ->
        for (name in WALKABLE_NAMES) {
            val index = Ade20kClasses.NAMES.indexOf(name)
            if (index >= 0) table[index] = true
        }
    }

    /** האם אפשר לעמוד על הנקודה הזו (כביש, מדרכה, רצפה, אדמה...) */
    fun isWalkable(worldX: Float, worldY: Float): Boolean {
        if (worldX < 0.04f || worldX > 0.96f || worldY < 0.04f || worldY > 0.98f) return false
        val label = analysis.labelAt(mapper.photoX(worldX), mapper.photoY(worldY))
        return walkable[label and 0xFF]
    }

    /** עומק במטרים בנקודה */
    fun depthAt(worldX: Float, worldY: Float): Float =
        analysis.depthAt(mapper.photoX(worldX), mapper.photoY(worldY))

    /** האם הנקודה נמצאת בתוך העצם של מקום ההסתתרות */
    fun spotContains(spot: HidingSpot, worldX: Float, worldY: Float): Boolean =
        spot.contains(mapper.photoX(worldX), mapper.photoY(worldY))

    companion object {
        private val WALKABLE_NAMES = listOf(
            "road", "sidewalk", "floor", "earth", "path", "grass", "field",
            "sand", "dirt track", "land", "runway", "rug"
        )
    }
}

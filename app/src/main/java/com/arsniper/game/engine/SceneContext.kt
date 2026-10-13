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
    val mapper: WorldMapper,
    /** בהירות התמונה (0..255) ברזולוציית רשת הניתוח, להתאמת הצבע של האויבים לתאורה (אופציונלי) */
    private val luma: ByteArray? = null
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

    /** בהירות התמונה סביב נקודה (0..1): ממוצע על חלון קטן, או 0.7 אם אין נתונים */
    fun lumaAt(worldX: Float, worldY: Float): Float {
        val data = luma ?: return 0.7f
        val cx = (mapper.photoX(worldX) * analysis.width).toInt()
        val cy = (mapper.photoY(worldY) * analysis.height).toInt()
        var sum = 0
        var count = 0
        for (dy in -4..4 step 2) {
            for (dx in -4..4 step 2) {
                val x = (cx + dx).coerceIn(0, analysis.width - 1)
                val y = (cy + dy).coerceIn(0, analysis.height - 1)
                sum += data[y * analysis.width + x].toInt() and 0xFF
                count++
            }
        }
        return sum / (count * 255f)
    }

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

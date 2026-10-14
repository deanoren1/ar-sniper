package com.arsniper.game.engine

import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import com.arsniper.game.spots.HidingSpot
import com.arsniper.game.spots.SpotKind

/**
 * הסתרה לפי עומק: דמות שהולכת על הקרקע נמצאת "מאחורי" עצם אם העצם נוגע ברצפה נמוך יותר במסך
 * מהרגליים שלה (כלומר קרוב יותר למצלמה) והם חופפים. אז העצם מכסה אותה, גם אם הוא לא מקום הסתתרות שלה.
 * אותה הלוגיקה משמשת לציור (מה מכוסה) ולפגיעה (אי אפשר לפגוע במה שמכוסה).
 */
object Occlusion {

    /** חצי רוחב דמות ביחידות רדיוס (הרוחב המקסימלי של מפלצת הוא 3 רדיוסים) */
    private const val HALF_WIDTH_RADII = 1.5f

    /** גובה הדמות ביחידות רדיוס (קצת יותר מגובה מפלצת רגילה) */
    private const val HEIGHT_RADII = 2.5f

    /** כמה (ביחידות גובה מסך) תחתית העצם צריכה להיות מתחת לרגליים כדי שייחשב קרוב יותר */
    private const val MARGIN = 0.01f

    /** נקודות דגימה לרוחב הדמות (כחלק מחצי הרוחב) */
    private val SAMPLES = floatArrayOf(-0.6f, -0.3f, 0f, 0.3f, 0.6f)

    /** העצמים שמכסים את הדמות (קרובים אליה יותר וחופפים לה). ריק לדמויות שמציצות או יוצאות מחלון. */
    fun nearerSpots(scene: SceneContext, enemy: Enemy): List<HidingSpot> {
        if (enemy.kind == EnemyKind.Peek || enemy.kind == EnemyKind.Window) return emptyList()
        val mapper = scene.mapper
        val aspect = mapper.screenAspect
        val x = enemy.position.x
        val feetY = enemy.position.y + enemy.radius
        val halfWidth = HALF_WIDTH_RADII * enemy.radius / aspect
        val headY = feetY - HEIGHT_RADII * enemy.radius

        val result = ArrayList<HidingSpot>()
        for (spot in scene.spots) {
            if (spot === enemy.spot || spot.kind == SpotKind.WINDOW) continue

            // חפיפה אופקית ואנכית עם הדמות
            if (mapper.worldX(spot.maxX) < x - halfWidth || mapper.worldX(spot.minX) > x + halfWidth) continue
            if (mapper.worldY(spot.maxY) < headY || mapper.worldY(spot.minY) > feetY) continue

            // העצם נוגע ברצפה מתחת לרגליים, באחת מהעמודות שמתחת לדמות
            var nearer = false
            for (f in SAMPLES) {
                val photoX = mapper.photoX(x + f * halfWidth)
                if (photoX < spot.minX || photoX > spot.maxX) continue
                val bottom = spot.columnBottom(photoX) ?: continue
                if (mapper.worldY(bottom) > feetY + MARGIN) {
                    nearer = true
                    break
                }
            }
            if (nearer) result.add(spot)
        }
        return result
    }

    /** האם הנקודה (קואורדינטות עולם) נמצאת בתוך אחד מהעצמים */
    fun covered(scene: SceneContext, spots: List<HidingSpot>, worldX: Float, worldY: Float): Boolean {
        for (spot in spots) {
            if (scene.spotContains(spot, worldX, worldY)) return true
        }
        return false
    }
}
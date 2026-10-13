package com.arsniper.game.engine

import com.arsniper.game.spots.HidingSpot
import java.util.WeakHashMap

/**
 * מלבן נקי בתוך חלון או דלת: המלבן הגדול ביותר שכולו בתוך המסכה של המקום.
 * מסכת חלון אמיתית מקוטעת (מחיצות, חורים, מדבקות), ולכן משתמשים במלבן נקי במקומה:
 * הדמות נחתכת לפיו, ופגיעה נספרת רק בתוכו. הקואורדינטות מנורמלות לתמונה (0..1).
 */
object WindowRects {

    class Rect(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
        fun contains(nx: Float, ny: Float): Boolean =
            nx >= minX && nx <= maxX && ny >= minY && ny <= maxY
    }

    private val cache = WeakHashMap<HidingSpot, Rect?>()

    /** המלבן הנקי של המקום (מחושב פעם אחת ונשמר), או null אם אין */
    fun of(spot: HidingSpot): Rect? {
        if (cache.containsKey(spot)) return cache[spot]
        val rect = compute(spot)
        cache[spot] = rect
        return rect
    }

    /** המלבן הגדול ביותר שכולו "דולק" במסכה: אלגוריתם היסטוגרמה קלאסי, זמן לינארי בגודל המסכה */
    private fun compute(spot: HidingSpot): Rect? {
        val w = spot.maskWidth
        val h = spot.maskHeight
        val heights = IntArray(w)
        val stack = IntArray(w + 1)
        var bestArea = 0
        var bx0 = 0
        var bx1 = 0
        var by0 = 0
        var by1 = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                heights[x] = if (spot.mask[y * w + x]) heights[x] + 1 else 0
            }
            var sp = 0
            for (x in 0..w) {
                val current = if (x == w) 0 else heights[x]
                while (sp > 0 && heights[stack[sp - 1]] >= current) {
                    val height = heights[stack[sp - 1]]
                    sp--
                    val left = if (sp == 0) 0 else stack[sp - 1] + 1
                    val area = height * (x - left)
                    if (area > bestArea) {
                        bestArea = area
                        bx0 = left
                        bx1 = x
                        by1 = y + 1
                        by0 = y + 1 - height
                    }
                }
                stack[sp++] = x
            }
        }
        if (bestArea == 0) return null
        return Rect(
            (spot.cellX + bx0).toFloat() / spot.gridWidth,
            (spot.cellY + by0).toFloat() / spot.gridHeight,
            (spot.cellX + bx1).toFloat() / spot.gridWidth,
            (spot.cellY + by1).toFloat() / spot.gridHeight
        )
    }
}

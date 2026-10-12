package com.arsniper.game.spots

/** סוג מקום הסתתרות */
enum class SpotKind {
    /** עצם שמאחוריו מסתתרים ומציצים מהצד (פח, ארגז, רכב, עמוד...) */
    COVER,

    /** פתח שבו האויב מופיע בתוכו (חלון, דלת) */
    WINDOW
}

/**
 * מקום הסתתרות שזוהה אוטומטית בתמונה: רכיב רציף של עצם מסוג מסוים.
 * המסכה נשמרת ברשת הניתוח (גריד), רק בתוך המלבן התוחם, וכל הקואורדינטות החיצוניות
 * מנורמלות לתמונה (0..1).
 */
data class HidingSpot(
    val id: Int,
    val kind: SpotKind,
    val classIndex: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val cellX: Int,
    val cellY: Int,
    val maskWidth: Int,
    val maskHeight: Int,
    val mask: BooleanArray,
    val cellCount: Int,
    /** עומק חציוני במטרים */
    val depth: Float
) {
    val minX: Float get() = cellX.toFloat() / gridWidth
    val maxX: Float get() = (cellX + maskWidth).toFloat() / gridWidth
    val minY: Float get() = cellY.toFloat() / gridHeight
    val maxY: Float get() = (cellY + maskHeight).toFloat() / gridHeight

    /** האם נקודה בתמונה (מנורמלת) נמצאת בתוך העצם */
    fun contains(nx: Float, ny: Float): Boolean {
        val localX = (nx * gridWidth).toInt() - cellX
        val localY = (ny * gridHeight).toInt() - cellY
        if (localX < 0 || localX >= maskWidth || localY < 0 || localY >= maskHeight) return false
        return mask[localY * maskWidth + localX]
    }

    /** הקצה העליון של העצם בעמודה מסוימת (מנורמל), או null אם אין שם עצם */
    fun columnTop(nx: Float): Float? {
        val localX = ((nx * gridWidth).toInt() - cellX).coerceIn(0, maskWidth - 1)
        for (localY in 0 until maskHeight) {
            if (mask[localY * maskWidth + localX]) {
                return (cellY + localY).toFloat() / gridHeight
            }
        }
        return null
    }

    /** הקצה התחתון של העצם בעמודה מסוימת (מנורמל), או null אם אין שם עצם */
    fun columnBottom(nx: Float): Float? {
        val localX = ((nx * gridWidth).toInt() - cellX).coerceIn(0, maskWidth - 1)
        for (localY in maskHeight - 1 downTo 0) {
            if (mask[localY * maskWidth + localX]) {
                return (cellY + localY + 1).toFloat() / gridHeight
            }
        }
        return null
    }
}

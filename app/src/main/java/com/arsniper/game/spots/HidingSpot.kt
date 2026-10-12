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
 * המסכה הגסה נשמרת ברשת הניתוח (גריד), רק בתוך המלבן התוחם, וכל הקואורדינטות החיצוניות
 * מנורמלות לתמונה (0..1).
 * אחרי ליטוש, יש גם alpha: מסכה רכה (0..255) ברזולוציה גבוהה פי alphaScale מהרשת,
 * שמתחילה בנקודה (alphaX, alphaY) ברזולוציה הגבוהה. היא משמשת לציור ולבדיקת פגיעה.
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
    val depth: Float,
    val alpha: ByteArray? = null,
    val alphaX: Int = 0,
    val alphaY: Int = 0,
    val alphaWidth: Int = 0,
    val alphaHeight: Int = 0,
    val alphaScale: Int = 1
) {
    val minX: Float get() = cellX.toFloat() / gridWidth
    val maxX: Float get() = (cellX + maskWidth).toFloat() / gridWidth
    val minY: Float get() = cellY.toFloat() / gridHeight
    val maxY: Float get() = (cellY + maskHeight).toFloat() / gridHeight

    // --- נתוני הציור: המסכה המלוטשת אם יש, אחרת המסכה הגסה ---

    /** גודל היחידה של מסכת הציור ביחס לתא רשת (1 = תא רשת) */
    val drawScale: Int get() = if (alpha != null) alphaScale else 1
    val drawX: Int get() = if (alpha != null) alphaX else cellX
    val drawY: Int get() = if (alpha != null) alphaY else cellY
    val drawWidth: Int get() = if (alpha != null) alphaWidth else maskWidth
    val drawHeight: Int get() = if (alpha != null) alphaHeight else maskHeight

    /** גבולות מסכת הציור, מנורמלים לתמונה */
    val drawMinX: Float get() = drawX.toFloat() / (gridWidth * drawScale)
    val drawMaxX: Float get() = (drawX + drawWidth).toFloat() / (gridWidth * drawScale)
    val drawMinY: Float get() = drawY.toFloat() / (gridHeight * drawScale)
    val drawMaxY: Float get() = (drawY + drawHeight).toFloat() / (gridHeight * drawScale)

    /** מסכת הציור: ערך אלפא 0..255 לכל פיקסל, בגודל drawWidth x drawHeight */
    fun drawAlpha(): ByteArray =
        alpha ?: ByteArray(mask.size) { if (mask[it]) -1 else 0 }

    /** האם נקודה בתמונה (מנורמלת) נמצאת בתוך העצם */
    fun contains(nx: Float, ny: Float): Boolean {
        val a = alpha
        if (a != null) {
            val localX = (nx * gridWidth * alphaScale).toInt() - alphaX
            val localY = (ny * gridHeight * alphaScale).toInt() - alphaY
            if (localX < 0 || localX >= alphaWidth || localY < 0 || localY >= alphaHeight) return false
            return (a[localY * alphaWidth + localX].toInt() and 0xFF) >= 128
        }
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
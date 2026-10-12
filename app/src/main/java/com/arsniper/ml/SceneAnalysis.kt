package com.arsniper.ml

/**
 * תוצאת ניתוח סצנה: לכל פיקסל (ברשת הקטנה) איזה סוג עצם הוא, ומפת עומק במטרים.
 * הקואורדינטות נבדקות באופן מנורמל (0..1) כדי שלא יהיו תלויות בגודל התמונה.
 */
class SceneAnalysis(
    val labelWidth: Int,
    val labelHeight: Int,
    private val labels: ByteArray,
    val depthWidth: Int,
    val depthHeight: Int,
    val depth: FloatArray
) {

    /** סוג העצם (0..149) בנקודה מנורמלת */
    fun labelAt(nx: Float, ny: Float): Int {
        val x = (nx * labelWidth).toInt().coerceIn(0, labelWidth - 1)
        val y = (ny * labelHeight).toInt().coerceIn(0, labelHeight - 1)
        return labelIndex(y * labelWidth + x)
    }

    fun labelIndex(position: Int): Int = labels[position].toInt() and 0xFF

    /** הקטגוריות הנפוצות ביותר: זוגות של (מספר קטגוריה, חלק מהתמונה 0..1) */
    fun topClasses(limit: Int): List<Pair<Int, Float>> {
        val counts = IntArray(Ade20kClasses.NAMES.size)
        for (i in labels.indices) {
            val index = labelIndex(i)
            if (index < counts.size) counts[index] += 1
        }
        return counts.indices
            .filter { counts[it] > 0 }
            .sortedByDescending { counts[it] }
            .take(limit)
            .map { Pair(it, counts[it].toFloat() / labels.size) }
    }
}

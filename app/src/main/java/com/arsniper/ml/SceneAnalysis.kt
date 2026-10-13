package com.arsniper.ml

/**
 * תוצאת ניתוח סצנה על רשת בפרופורציות התמונה: לכל תא סוג עצם (0..149) ועומק במטרים.
 * הקואורדינטות נבדקות באופן מנורמל (0..1) כדי שלא יהיו תלויות בגודל התמונה.
 */
class SceneAnalysis(
    val width: Int,
    val height: Int,
    private val labels: ByteArray,
    val depth: FloatArray,
    val tileCount: Int
) {

    /** סוג העצם (0..149) בנקודה מנורמלת */
    fun labelAt(nx: Float, ny: Float): Int {
        val x = (nx * width).toInt().coerceIn(0, width - 1)
        val y = (ny * height).toInt().coerceIn(0, height - 1)
        return labelIndex(y * width + x)
    }

    /** עומק במטרים בנקודה מנורמלת */
    fun depthAt(nx: Float, ny: Float): Float {
        val x = (nx * width).toInt().coerceIn(0, width - 1)
        val y = (ny * height).toInt().coerceIn(0, height - 1)
        return depth[y * width + x]
    }

    fun labelIndex(position: Int): Int = labels[position].toInt() and 0xFF

    /** הסיווג הגולמי של כל התאים (לשמירה בקובץ) */
    fun labelBytes(): ByteArray = labels

    /** חלק התמונה (0..1) שמסווג כקטגוריה מסוימת */
    fun fraction(classIndex: Int): Float {
        var count = 0
        for (i in labels.indices) {
            if (labelIndex(i) == classIndex) count++
        }
        return count.toFloat() / labels.size
    }

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
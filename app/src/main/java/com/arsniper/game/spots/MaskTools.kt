package com.arsniper.game.spots

import kotlin.math.max
import kotlin.math.min

/** תוצאת ניקוי מסכה: המסכה החדשה, וההיסט שלה ביחס למסכה המקורית */
class CleanedMask(
    val mask: BooleanArray,
    val width: Int,
    val height: Int,
    /** ההיסט של הפינה השמאלית-עליונה ביחס לפינה של המסכה המקורית (יכול להיות שלילי) */
    val offsetX: Int,
    val offsetY: Int,
    val count: Int
)

/**
 * כלי עיבוד מסכות. Kotlin טהור, בלי תלות ב-Android.
 * מסכות בינאריות (BooleanArray) ומסכות רכות (FloatArray), שורה אחר שורה.
 */
object MaskTools {

    /**
     * מנקה מסכה גסה: סוגר חורים קטנים, ממלא חורים סגורים קטנים,
     * ובעצמים לא דקים גם מסיר בליטות ואיים דקים (עד 2 תאים).
     * מחזיר null אם לא נשאר כלום.
     */
    fun clean(mask: BooleanArray, width: Int, height: Int, thin: Boolean): CleanedMask? {
        val pad = 3
        val pw = width + 2 * pad
        val ph = height + 2 * pad
        val padded = BooleanArray(pw * ph)
        var originalCount = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y * width + x]) {
                    padded[(y + pad) * pw + (x + pad)] = true
                    originalCount++
                }
            }
        }

        var result = close(padded, pw, ph, 1)
        result = fillHoles(result, pw, ph, max(12, originalCount / 10))
        if (!thin) result = open(result, pw, ph, 1)

        var minX = pw
        var maxX = -1
        var minY = ph
        var maxY = -1
        var count = 0
        for (y in 0 until ph) {
            for (x in 0 until pw) {
                if (result[y * pw + x]) {
                    count++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (count == 0) return null

        val newWidth = maxX - minX + 1
        val newHeight = maxY - minY + 1
        val cropped = BooleanArray(newWidth * newHeight)
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                cropped[y * newWidth + x] = result[(y + minY) * pw + (x + minX)]
            }
        }
        return CleanedMask(cropped, newWidth, newHeight, minX - pad, minY - pad, count)
    }

    /** ממלא אזורי רקע סגורים (שלא נוגעים בשוליים) ששטחם עד maxHoleArea */
    fun fillHoles(mask: BooleanArray, width: Int, height: Int, maxHoleArea: Int): BooleanArray {
        val result = mask.copyOf()
        val visited = BooleanArray(width * height)
        val queue = IntArray(width * height)
        for (start in 0 until width * height) {
            if (visited[start] || mask[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var touchesBorder = false
            while (head < tail) {
                val position = queue[head++]
                val x = position % width
                val y = position / width
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) touchesBorder = true
                if (x > 0) tail = push(position - 1, mask, visited, queue, tail)
                if (x < width - 1) tail = push(position + 1, mask, visited, queue, tail)
                if (y > 0) tail = push(position - width, mask, visited, queue, tail)
                if (y < height - 1) tail = push(position + width, mask, visited, queue, tail)
            }
            if (!touchesBorder && tail <= maxHoleArea) {
                for (i in 0 until tail) result[queue[i]] = true
            }
        }
        return result
    }

    private fun push(
        position: Int,
        mask: BooleanArray,
        visited: BooleanArray,
        queue: IntArray,
        tail: Int
    ): Int {
        if (visited[position] || mask[position]) return tail
        visited[position] = true
        queue[tail] = position
        return tail + 1
    }

    fun close(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray =
        erode(dilate(mask, width, height, radius), width, height, radius)

    fun open(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray =
        dilate(erode(mask, width, height, radius), width, height, radius)

    /** הרחבה: תא נדלק אם יש תא דלוק בריבוע ברדיוס radius סביבו */
    fun dilate(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray =
        windowPass(mask, width, height, radius, requireAll = false)

    /** כיווץ: תא נשאר דלוק רק אם כל הריבוע ברדיוס radius סביבו דלוק (מחוץ לתמונה נחשב דלוק) */
    fun erode(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray =
        windowPass(mask, width, height, radius, requireAll = true)

    private fun windowPass(
        mask: BooleanArray,
        width: Int,
        height: Int,
        radius: Int,
        requireAll: Boolean
    ): BooleanArray {
        val temp = BooleanArray(width * height)
        val out = BooleanArray(width * height)
        val prefix = IntArray(max(width, height) + 1)

        for (y in 0 until height) {
            val base = y * width
            for (x in 0 until width) prefix[x + 1] = prefix[x] + if (mask[base + x]) 1 else 0
            for (x in 0 until width) {
                val lo = max(0, x - radius)
                val hi = min(width - 1, x + radius)
                val sum = prefix[hi + 1] - prefix[lo]
                temp[base + x] = if (requireAll) sum == hi - lo + 1 else sum > 0
            }
        }
        for (x in 0 until width) {
            for (y in 0 until height) prefix[y + 1] = prefix[y] + if (temp[y * width + x]) 1 else 0
            for (y in 0 until height) {
                val lo = max(0, y - radius)
                val hi = min(height - 1, y + radius)
                val sum = prefix[hi + 1] - prefix[lo]
                out[y * width + x] = if (requireAll) sum == hi - lo + 1 else sum > 0
            }
        }
        return out
    }

    /** ממוצע בריבוע ברדיוס radius (בשוליים: ממוצע על החלק שבתוך התמונה) */
    fun boxFilter(source: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        val temp = FloatArray(width * height)
        val out = FloatArray(width * height)
        val prefix = DoubleArray(max(width, height) + 1)

        for (y in 0 until height) {
            val base = y * width
            for (x in 0 until width) prefix[x + 1] = prefix[x] + source[base + x]
            for (x in 0 until width) {
                val lo = max(0, x - radius)
                val hi = min(width - 1, x + radius)
                temp[base + x] = ((prefix[hi + 1] - prefix[lo]) / (hi - lo + 1)).toFloat()
            }
        }
        for (x in 0 until width) {
            for (y in 0 until height) prefix[y + 1] = prefix[y] + temp[y * width + x]
            for (y in 0 until height) {
                val lo = max(0, y - radius)
                val hi = min(height - 1, y + radius)
                out[y * width + x] = ((prefix[hi + 1] - prefix[lo]) / (hi - lo + 1)).toFloat()
            }
        }
        return out
    }

    /**
     * Guided filter (He, Sun, Tang): מחליק את input כך שהקצוות שלו נצמדים לקצוות של guide.
     * guide ו-input בטווח 0..1. radius בפיקסלים, eps = חוזק ההחלקה (קטן = נצמד יותר לקצוות).
     */
    fun guidedFilter(
        guide: FloatArray,
        input: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
        eps: Float
    ): FloatArray {
        val n = width * height
        val meanGuide = boxFilter(guide, width, height, radius)
        val meanInput = boxFilter(input, width, height, radius)

        val guideSquared = FloatArray(n) { guide[it] * guide[it] }
        val guideTimesInput = FloatArray(n) { guide[it] * input[it] }
        val corrGuide = boxFilter(guideSquared, width, height, radius)
        val corrGuideInput = boxFilter(guideTimesInput, width, height, radius)

        val a = FloatArray(n)
        val b = FloatArray(n)
        for (i in 0 until n) {
            val variance = corrGuide[i] - meanGuide[i] * meanGuide[i]
            val covariance = corrGuideInput[i] - meanGuide[i] * meanInput[i]
            a[i] = covariance / (variance + eps)
            b[i] = meanInput[i] - a[i] * meanGuide[i]
        }
        val meanA = boxFilter(a, width, height, radius)
        val meanB = boxFilter(b, width, height, radius)
        return FloatArray(n) { meanA[it] * guide[it] + meanB[it] }
    }
}
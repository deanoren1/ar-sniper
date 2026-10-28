package com.arsniper.ml

import android.graphics.Bitmap
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * מריץ את מודל TIPSv2 (LiteRT, GPU): מסווג כל נקודה בתמונה ל-150 קטגוריות (ADE20K)
 * ומעריך עומק. כדי לקבל פרטים קטנים, התמונה נחתכת לחלקים ריבועיים חופפים,
 * כל חלק מנותח בנפרד, והתוצאות מאוחדות על רשת אחת.
 *
 * קלט למודל: [1, 3, 448, 448] RGB בטווח 0..1.
 * פלטים לפי הסדר: עומק [448x448] במטרים, נורמלים [3x448x448], סיווג [150x256x256].
 */
class SceneSegmenter(modelFile: File) {

    private val model: CompiledModel = CompiledModel.create(
        modelFile.absolutePath,
        CompiledModel.Options(Accelerator.GPU)
    )
    private val inputBuffers = model.createInputBuffers()
    private val outputBuffers = model.createOutputBuffers()

    private class TileResult(val labels: ByteArray, val depth: FloatArray)

    fun analyze(photo: Bitmap, onProgress: (done: Int, total: Int) -> Unit): SceneAnalysis {
        val width = photo.width
        val height = photo.height
        // תמונה רחבה (פנורמה): רשת רחבה יותר, כדי שהפרטים לא יתפרסו על כל כך מעט תאים
        val photoAspect = width.toFloat() / height
        val gridWidth = if (photoAspect > 2.2f) (GRID_WIDTH * photoAspect / 1.8f).roundToInt().coerceIn(GRID_WIDTH, 1600) else GRID_WIDTH
        val gridHeight = (gridWidth.toFloat() * height / width).roundToInt().coerceAtLeast(1)

        val labels = ByteArray(gridWidth * gridHeight)
        val depth = FloatArray(gridWidth * gridHeight)
        val bestWeight = FloatArray(gridWidth * gridHeight)

        val shortSide = min(width, height)
        val side = (shortSide * TILE_FRACTION).roundToInt().coerceIn(64, shortSide)
        val stride = (side * STRIDE_FRACTION).roundToInt().coerceAtLeast(1)

        val lefts = tileStarts(width, side, stride)
        val tops = tileStarts(height, side, stride)
        val total = lefts.size * tops.size

        var done = 0
        onProgress(0, total)
        for (top in tops) {
            for (left in lefts) {
                val crop = Bitmap.createBitmap(photo, left, top, side, side)
                val tile = runTile(crop)
                if (crop !== photo) crop.recycle()

                mergeTile(
                    tile, left, top, side, width, height,
                    gridWidth, gridHeight, labels, depth, bestWeight
                )
                done += 1
                onProgress(done, total)
            }
        }
        return SceneAnalysis(gridWidth, gridHeight, labels, depth, total)
    }

    fun close() {
        try {
            model.close()
        } catch (e: Throwable) {
            // אין מה לעשות בכשל סגירה
        }
    }

    /** נקודות התחלה של חלקים לאורך ציר אחד, כך שהחלק האחרון נצמד לקצה */
    private fun tileStarts(length: Int, side: Int, stride: Int): List<Int> {
        val starts = ArrayList<Int>()
        var start = 0
        while (true) {
            starts.add(min(start, length - side))
            if (start + side >= length) break
            start += stride
        }
        return starts.distinct()
    }

    /** מריץ את המודל על חלק ריבועי אחד */
    private fun runTile(tileBitmap: Bitmap): TileResult {
        val scaled = Bitmap.createScaledBitmap(tileBitmap, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_PLANE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (scaled !== tileBitmap) scaled.recycle()

        val input = FloatArray(3 * INPUT_PLANE)
        for (i in 0 until INPUT_PLANE) {
            val color = pixels[i]
            input[i] = ((color shr 16) and 0xFF) / 255f
            input[INPUT_PLANE + i] = ((color shr 8) and 0xFF) / 255f
            input[2 * INPUT_PLANE + i] = (color and 0xFF) / 255f
        }

        inputBuffers[0].writeFloat(input)
        model.run(inputBuffers, outputBuffers)

        val depth = outputBuffers[0].readFloat()
        val segmentation = outputBuffers[2].readFloat()

        // לכל פיקסל: הקטגוריה עם הערך הגבוה ביותר
        val plane = SEG_SIZE * SEG_SIZE
        val labels = ByteArray(plane)
        for (p in 0 until plane) {
            var best = 0
            var bestValue = segmentation[p]
            for (c in 1 until CLASS_COUNT) {
                val value = segmentation[c * plane + p]
                if (value > bestValue) {
                    bestValue = value
                    best = c
                }
            }
            labels[p] = best.toByte()
        }
        return TileResult(labels, depth)
    }

    /**
     * מוסיף תוצאה של חלק לרשת הכללית. כשכמה חלקים חופפים, כל תא מקבל את התוצאה
     * מהחלק שבו הוא הכי קרוב למרכז (רחוק מהקצוות, שבהם המודל פחות אמין).
     */
    private fun mergeTile(
        tile: TileResult,
        left: Int,
        top: Int,
        side: Int,
        photoWidth: Int,
        photoHeight: Int,
        gridWidth: Int,
        gridHeight: Int,
        labels: ByteArray,
        depth: FloatArray,
        bestWeight: FloatArray
    ) {
        val gxStart = (left * gridWidth / photoWidth).coerceIn(0, gridWidth - 1)
        val gxEnd = (((left + side) * gridWidth + photoWidth - 1) / photoWidth).coerceIn(1, gridWidth)
        val gyStart = (top * gridHeight / photoHeight).coerceIn(0, gridHeight - 1)
        val gyEnd = (((top + side) * gridHeight + photoHeight - 1) / photoHeight).coerceIn(1, gridHeight)

        for (gy in gyStart until gyEnd) {
            val v = ((gy + 0.5f) * photoHeight / gridHeight - top) / side
            if (v < 0f || v >= 1f) continue
            for (gx in gxStart until gxEnd) {
                val u = ((gx + 0.5f) * photoWidth / gridWidth - left) / side
                if (u < 0f || u >= 1f) continue

                val weight = min(min(u, 1f - u), min(v, 1f - v)) + 0.001f
                val index = gy * gridWidth + gx
                if (weight > bestWeight[index]) {
                    bestWeight[index] = weight
                    labels[index] = tile.labels[(v * SEG_SIZE).toInt() * SEG_SIZE + (u * SEG_SIZE).toInt()]
                    depth[index] = tile.depth[(v * INPUT_SIZE).toInt() * INPUT_SIZE + (u * INPUT_SIZE).toInt()]
                }
            }
        }
    }

    companion object {
        private const val INPUT_SIZE = 448
        private const val INPUT_PLANE = INPUT_SIZE * INPUT_SIZE
        private const val SEG_SIZE = 256
        private const val CLASS_COUNT = 150

        /** רוחב הרשת המאוחדת (הגובה לפי פרופורציות התמונה) */
        private const val GRID_WIDTH = 640

        /** גודל כל חלק, כחלק מהצלע הקצרה של התמונה */
        private const val TILE_FRACTION = 0.75f

        /** המרחק בין חלקים סמוכים, כחלק מגודל החלק (נמוך = יותר חפיפה) */
        private const val STRIDE_FRACTION = 0.8f
    }
}

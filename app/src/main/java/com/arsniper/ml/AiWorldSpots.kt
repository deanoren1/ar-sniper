package com.arsniper.ml

import android.content.Context
import android.graphics.Bitmap
import com.arsniper.game.spots.GuideImage
import com.arsniper.game.spots.HidingSpot
import com.arsniper.game.spots.HidingSpotFinder
import com.arsniper.game.spots.SpotKind
import com.arsniper.game.spots.SpotRefiner

/**
 * מחבואי צמחים מתמונת ה-AI: הרצפה, העומק והמחבואים הרגילים נשארים מהתמונה המקורית (מדויקים),
 * ומתמונת ה-AI לוקחים רק אזורי צמחים (ג'ונגל) כמחבואים נוספים. תמונת ה-AI באותה פריסה, אז הקואורדינטות תואמות.
 */
object AiWorldSpots {

    private const val REFINE_SCALE = 3
    private val PLANT_NAMES = listOf("plant", "flower", "palm", "tree")

    /** על אילו אזורים במקור מותר "לצייר" צמח: רק רקע, לא עצם קיים (שולחן עם אזוב נשאר שולחן) */
    private val BACKGROUND_NAMES = listOf("wall", "floor", "ceiling", "building", "sky", "earth", "road", "sidewalk", "grass", "rug", "windowpane")

    private fun indices(names: List<String>): Set<Int> =
        names.map { Ade20kClasses.NAMES.indexOf(it) }.filter { it >= 0 }.toSet()

    /**
     * מוסיף ל-SceneSession מחבואי צמחים מתמונת ה-AI. חוסם (כמה שניות): להריץ מחוץ לתהליך הראשי.
     * מחזיר כמה מחבואים נוספו (0 = אין שינוי).
     */
    fun addPlantSpots(context: Context, aiWorld: Bitmap): Int {
        val original = SceneSession.analysis ?: return 0
        val model = ModelStore.tipsModelFile(context)
        if (!model.exists()) return 0
        val ai = try {
            // CPU ובעדיפות נמוכה: ניתוח ב-GPU במקביל לציור המשחק גרם לתקיעה של כמה שניות בתחילת הארנה
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            val segmenter = SceneSegmenter(model, useGpu = false)
            try {
                segmenter.analyze(aiWorld) { _, _ -> }
            } finally {
                segmenter.close()
            }
        } catch (_: Throwable) {
            return 0
        }
        val merged = merge(original, ai) ?: return 0
        val guide = buildGuide(aiWorld, merged.width, merged.height)
        return SceneSession.addExtraSpots(merged, indices(PLANT_NAMES), guide)
    }

    /** ניתוח מקורי + צמחים מניתוח ה-AI (רק מעל אזורי רקע). עומק הצמח לא רחוק יותר מהעומק במקור באותה נקודה */
    private fun merge(original: SceneAnalysis, ai: SceneAnalysis): SceneAnalysis? {
        val plants = indices(PLANT_NAMES)
        val background = indices(BACKGROUND_NAMES)
        val w = original.width
        val h = original.height
        val labels = original.labelBytes().copyOf()
        val depth = original.depth.copyOf()
        var painted = 0
        for (y in 0 until h) {
            val ay = (y * ai.height / h).coerceIn(0, ai.height - 1)
            for (x in 0 until w) {
                val ax = (x * ai.width / w).coerceIn(0, ai.width - 1)
                val aiLabel = ai.labelIndex(ay * ai.width + ax)
                if (aiLabel !in plants) continue
                val i = y * w + x
                if (original.labelIndex(i) !in background) continue
                labels[i] = aiLabel.toByte()
                val aiDepth = ai.depth[ay * ai.width + ax]
                if (aiDepth.isFinite() && aiDepth > 0f) depth[i] = minOf(depth[i], aiDepth)
                painted++
            }
        }
        if (painted < w * h / 400) return null
        return SceneAnalysis(w, h, labels, depth, original.tileCount)
    }

    private fun buildGuide(photo: Bitmap, gridWidth: Int, gridHeight: Int): GuideImage {
        val width = gridWidth * REFINE_SCALE
        val height = gridHeight * REFINE_SCALE
        val scaled = Bitmap.createScaledBitmap(photo, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== photo) scaled.recycle()
        val gray = ByteArray(width * height)
        for (i in pixels.indices) {
            val c = pixels[i]
            gray[i] = ((((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000).toByte()
        }
        return GuideImage(width, height, gray, REFINE_SCALE)
    }
}

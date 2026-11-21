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

    /** מה ה-AI מוסיף כמחבוא בארנות שאינן ג'ונגל/יער: ארגזים, חביות, סלים, שקים, עמודים קטנים (ועציצים) */
    val PROP_NAMES = listOf("box", "barrel", "ashcan", "basket", "bag", "column", "plant")

    /** על אילו אזורים במקור מותר "לצייר" צמח: רק רקע, לא עצם קיים (שולחן עם אזוב נשאר שולחן) */
    private val BACKGROUND_NAMES = listOf("wall", "floor", "ceiling", "building", "sky", "earth", "road", "sidewalk", "grass", "rug", "windowpane")

    private fun indices(names: List<String>): Set<Int> =
        names.map { Ade20kClasses.NAMES.indexOf(it) }.filter { it >= 0 }.toSet()

    /**
     * מוסיף ל-SceneSession מחבואי צמחים מתמונת ה-AI. חוסם (כמה שניות): להריץ מחוץ לתהליך הראשי.
     * מחזיר כמה מחבואים נוספו (0 = אין שינוי).
     */
    /** מידע אבחון על הריצה האחרונה (לאיתור למה אין מחסים בעולם AI) */
    @Volatile var lastDebug: String = ""

    /** האם ניתוח עולם ה-AI כבר שמור בדיסק (אז הוספת המחבואים מהירה ואפשר לחכות לה לפני הגל הראשון) */
    fun isCached(context: Context, cacheKey: String): Boolean = AnalysisCache.fileFor(context.filesDir, cacheKey).exists()

    fun addPlantSpots(context: Context, aiWorld: Bitmap, hideNames: List<String> = PLANT_NAMES, cacheKey: String? = null): Int {
        val original = SceneSession.analysis ?: return 0
        val cachedAi = cacheKey?.let { AnalysisCache.load(context.filesDir, it) }
        val model = ModelStore.tipsModelFile(context)
        if (cachedAi == null && !model.exists()) return 0
        val ai = cachedAi ?: try {
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
        if (cachedAi == null && cacheKey != null) AnalysisCache.save(context.filesDir, cacheKey, ai)
        // מחבואים מהמקור שעולם ה-AI מחק: דמות מאחוריהם הייתה נראית חתוכה באוויר
        val pruned = SceneSession.pruneMissing(ai, indices(BACKGROUND_NAMES - "windowpane"))
        val merged = merge(original, ai, hideNames) ?: return pruned
        val guide = buildGuide(aiWorld, merged.width, merged.height)
        return pruned + SceneSession.addExtraSpots(merged, indices(hideNames), guide)
    }

    /** ניתוח מקורי + צמחים מניתוח ה-AI (רק מעל אזורי רקע). עומק הצמח לא רחוק יותר מהעומק במקור באותה נקודה */
    private fun merge(original: SceneAnalysis, ai: SceneAnalysis, hideNames: List<String>): SceneAnalysis? {
        val plants = indices(hideNames)
        val background = indices(BACKGROUND_NAMES)
        val w = original.width
        val h = original.height
        val labels = original.labelBytes().copyOf()
        val depth = original.depth.copyOf()
        var painted = 0
        val counts = IntArray(150)
        for (y in 0 until h) {
            val ay = (y * ai.height / h).coerceIn(0, ai.height - 1)
            for (x in 0 until w) {
                val ax = (x * ai.width / w).coerceIn(0, ai.width - 1)
                val aiLabel = ai.labelIndex(ay * ai.width + ax)
                val i = y * w + x
                if (aiLabel in 0..149 && original.labelIndex(i) in background) counts[aiLabel]++
                if (aiLabel !in plants) continue
                if (original.labelIndex(i) !in background) continue
                labels[i] = aiLabel.toByte()
                val aiDepth = ai.depth[ay * ai.width + ax]
                if (aiDepth.isFinite() && aiDepth > 0f) depth[i] = minOf(depth[i], aiDepth)
                painted++
            }
        }
        val top = counts.withIndex().filter { it.index !in background }.sortedByDescending { it.value }.take(5)
            .joinToString(" ") { Ade20kClasses.name(it.index) + ":" + it.value * 1000 / (w * h) }
        lastDebug = "painted=${painted * 1000 / (w * h)}/1000 need=${1000 / 400 + 0}/1000 top(per mille over floor/wall): $top"
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

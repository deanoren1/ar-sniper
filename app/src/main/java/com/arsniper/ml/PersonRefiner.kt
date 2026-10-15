package com.arsniper.ml

import com.arsniper.game.spots.MaskTools
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * מדייק את מסכת האנשים של DeepLab בעזרת SAM.
 * DeepLab גס (257x257 לחלק) ולכן הקצוות מרובעים והמסכה גולשת לרקע ולרהיטים. כאן, לכל אדם שנמצא,
 * SAM מציע שלוש מסכות מנקודה שבתוכו, ובוחרים את הקטנה ביותר שמכסה את רוב ליבת האדם ולא חורגת ממנו.
 * אם אין מסכה מתאימה, האדם נשאר כמו שזוהה. את התוצאה מחליקים לרזולוציית התמונה המלאה.
 * Kotlin טהור: את SAM מקבלים כפונקציה, כך אפשר לבדוק בלי Android.
 */
object PersonRefiner {

    /** רזולוציית העבודה: הצלע הארוכה של הרשת בערך כך */
    private const val WORK_SIDE = 700f

    /** כמה אנשים לכל היותר מדייקים (הגדולים ביותר) */
    private const val MAX_PEOPLE = 6

    /** כתם קטן מזה (בתאי רשת) לא מדייקים */
    private const val MIN_COMPONENT_CELLS = 40

    /** מסכת SAM צריכה לכסות לפחות כל כך מהליבה של האדם */
    private const val MIN_CORE_COVERAGE = 0.6f

    /** הליבה: תאים שעומקם לפחות כך מהעומק המקסימלי של הכתם */
    private const val CORE_DEPTH = 0.6f

    /** ולא יותר מזה משטחה יכול להיות מחוץ לאדם (המורחב) */
    private const val MAX_OUTSIDE = 0.4f

    /** ולא גדולה מפי כך משטח הכתם (אחרת זה חדר או רהיט, לא האדם) */
    private const val MAX_AREA_RATIO = 2.5f

    /** גבול הערכים בציון (לוגיט): כדי שרמת הביטחון של SAM לא תשפיע על ההחלקה */
    private const val SCORE_CLAMP = 4f

    /** תוצאת הדיוק: המסכה, וכמה אנשים דוייקו (לדיווח) */
    class Result(val mask: PersonMask, val refined: Int, val people: Int)

    /**
     * segment(x, y) = שלוש המסכות של SAM (256x256, לוגיטים) לנקודה בקואורדינטות 0..1 של התמונה.
     * מחזיר מסכה חדשה (המקורית לא משתנה).
     */
    fun refine(
        mask: PersonMask,
        segment: (Float, Float) -> Array<FloatArray>
    ): Result {
        val width = mask.width
        val height = mask.height
        if (mask.coverage() == 0f) return Result(mask, 0, 0)

        val s = max(1, (max(width, height) / WORK_SIDE).roundToInt())
        val gw = (width + s - 1) / s
        val gh = (height + s - 1) / s
        val grid = downsample(mask.data, width, height, s, gw, gh)

        val components = findComponents(grid, gw, gh)
            .filter { it.cells.size >= MIN_COMPONENT_CELLS }
            .sortedByDescending { it.cells.size }
            .take(MAX_PEOPLE)
        if (components.isEmpty()) return Result(mask, 0, 0)

        // ציון לכל תא: חיובי = אדם. מתחילים מהמסכה הגסה (+1 / -1) ומחליפים באדם שדוייק
        val score = FloatArray(gw * gh) { if (grid[it]) 1f else -1f }
        val positive = BooleanArray(gw * gh)
        var refinedCount = 0

        for (component in components) {
            val comp = BooleanArray(gw * gh)
            for (c in component.cells) comp[c] = true

            val bigSide = max(component.maxX - component.minX + 1, component.maxY - component.minY + 1)
            val field = distanceField(comp, gw, gh, component)
            // ליבה = החלק העבה ביותר של הכתם (רחוק מהקצה). כך גלישה דקה לרהיטים סמוכים לא נחשבת
            val coreMin = max(1, Math.ceil(CORE_DEPTH * field.max.toDouble()).toInt())
            val core = BooleanArray(gw * gh)
            var coreCount = 0
            for (cell in component.cells) {
                if (field.at(cell % gw, cell / gw) >= coreMin) {
                    core[cell] = true
                    coreCount++
                }
            }
            // אזור מותר: הכתם מורחב קצת (SAM מדויק יותר מ-DeepLab, אבל לא נותנים לו לברוח)
            val allowedRadius = (bigSide / 6).coerceIn(3, 12)
            val allowed = MaskTools.dilate(comp, gw, gh, allowedRadius)

            val seed = field.deepest(component.cells)
            val sx = seed % gw
            val sy = seed / gw
            val masks = try {
                segment((sx + 0.5f) / gw, (sy + 0.5f) / gh)
            } catch (e: Exception) {
                continue
            }

            var bestIndex = -1
            var bestArea = Int.MAX_VALUE
            val grids = ArrayList<FloatArray>()
            for ((i, logits) in masks.withIndex()) {
                val g = UserObjects.gridLogits(logits, gw, gh)
                grids.add(g)
                var area = 0
                var insideCore = 0
                var outside = 0
                for (k in g.indices) {
                    if (g[k] > 0f) {
                        area++
                        if (core[k]) insideCore++
                        if (!allowed[k]) outside++
                    }
                }
                if (area == 0) continue
                val coverage = insideCore.toFloat() / coreCount
                val outsideFraction = outside.toFloat() / area
                if (coverage < MIN_CORE_COVERAGE) continue
                if (outsideFraction > MAX_OUTSIDE) continue
                if (area > MAX_AREA_RATIO * component.cells.size) continue
                if (area < bestArea) {
                    bestArea = area
                    bestIndex = i
                }
            }
            if (bestIndex < 0) continue

            // בתוך האזור המותר הציון הגס מוחלף בציון של SAM. תא שהוא חלק מאדם אחר לא נוגעים בו,
            // ותא שאדם אחר כבר סימן כחיובי נשאר חיובי
            val g = grids[bestIndex]
            for (k in g.indices) {
                if (!allowed[k]) continue
                if (grid[k] && !comp[k]) continue
                val value = g[k].coerceIn(-SCORE_CLAMP, SCORE_CLAMP)
                if (value > 0f) {
                    score[k] = if (positive[k]) max(score[k], value) else value
                    positive[k] = true
                } else if (!positive[k]) {
                    score[k] = value
                }
            }
            refinedCount++
        }

        if (refinedCount == 0) return Result(mask, 0, components.size)

        val out = upsample(score, gw, gh, s, width, height)
        val refined = PersonMask(width, height, out)
        refined.precise = true
        return Result(refined, refinedCount, components.size)
    }

    // --- עזרים ---

    private class Component(
        val cells: IntArray,
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int
    )

    /** מקטין את מסכת הפיקסלים לרשת עבודה: תא נדלק אם לפחות חצי מהפיקסלים בו דלוקים */
    private fun downsample(data: BooleanArray, width: Int, height: Int, s: Int, gw: Int, gh: Int): BooleanArray {
        val out = BooleanArray(gw * gh)
        for (gy in 0 until gh) {
            val y0 = gy * s
            val y1 = min(height, y0 + s)
            for (gx in 0 until gw) {
                val x0 = gx * s
                val x1 = min(width, x0 + s)
                var on = 0
                var total = 0
                for (y in y0 until y1) {
                    val row = y * width
                    for (x in x0 until x1) {
                        total++
                        if (data[row + x]) on++
                    }
                }
                out[gy * gw + gx] = total > 0 && on * 2 >= total
            }
        }
        return out
    }

    /** כתמים רציפים; שברים קרובים (עד 2 תאים) של אותו אדם מתאחדים */
    private fun findComponents(grid: BooleanArray, gw: Int, gh: Int): List<Component> {
        val merged = MaskTools.dilate(grid, gw, gh, 2)
        val labels = IntArray(gw * gh)
        val queue = IntArray(gw * gh)
        var nextLabel = 0
        val result = ArrayList<Component>()
        for (start in grid.indices) {
            if (!merged[start] || labels[start] != 0) continue
            nextLabel++
            var head = 0
            var tail = 0
            queue[tail++] = start
            labels[start] = nextLabel
            val members = ArrayList<Int>()
            var minX = gw
            var minY = gh
            var maxX = -1
            var maxY = -1
            while (head < tail) {
                val p = queue[head++]
                val x = p % gw
                val y = p / gw
                if (grid[p]) {
                    members.add(p)
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                if (x > 0 && merged[p - 1] && labels[p - 1] == 0) { labels[p - 1] = nextLabel; queue[tail++] = p - 1 }
                if (x < gw - 1 && merged[p + 1] && labels[p + 1] == 0) { labels[p + 1] = nextLabel; queue[tail++] = p + 1 }
                if (y > 0 && merged[p - gw] && labels[p - gw] == 0) { labels[p - gw] = nextLabel; queue[tail++] = p - gw }
                if (y < gh - 1 && merged[p + gw] && labels[p + gw] == 0) { labels[p + gw] = nextLabel; queue[tail++] = p + gw }
            }
            if (members.isNotEmpty()) {
                result.add(Component(members.toIntArray(), minX, minY, maxX, maxY))
            }
        }
        return result
    }

    /** מרחק (בבלוקים) של כל תא בכתם מקצה הכתם */
    private class Field(val x0: Int, val y0: Int, val lw: Int, val d: IntArray, val max: Int, val gw: Int) {
        fun at(x: Int, y: Int): Int = d[(y - y0) * lw + (x - x0)]

        /** תא עמוק ביותר, ומבין העמוקים ביותר זה הקרוב למרכז הכובד (נקודת הנחיה יציבה ל-SAM) */
        fun deepest(cells: IntArray): Int {
            var sumX = 0L
            var sumY = 0L
            for (c in cells) { sumX += c % gw; sumY += c / gw }
            val cx = sumX.toDouble() / cells.size
            val cy = sumY.toDouble() / cells.size
            val threshold = max(1, (max * 0.9f).toInt())
            var best = cells[0]
            var bestDistance = Double.MAX_VALUE
            for (c in cells) {
                val x = c % gw
                val y = c / gw
                if (at(x, y) < threshold) continue
                val dist = (x - cx) * (x - cx) + (y - cy) * (y - cy)
                if (dist < bestDistance) { bestDistance = dist; best = c }
            }
            return best
        }
    }

    private fun distanceField(comp: BooleanArray, gw: Int, gh: Int, c: Component): Field {
        val x0 = max(0, c.minX - 1)
        val y0 = max(0, c.minY - 1)
        val x1 = min(gw - 1, c.maxX + 1)
        val y1 = min(gh - 1, c.maxY + 1)
        val lw = x1 - x0 + 1
        val lh = y1 - y0 + 1
        val d = IntArray(lw * lh) { 1_000_000 }
        for (y in 0 until lh) for (x in 0 until lw) {
            if (!comp[(y0 + y) * gw + (x0 + x)]) d[y * lw + x] = 0
        }
        for (y in 0 until lh) for (x in 0 until lw) {
            val i = y * lw + x
            if (d[i] == 0) continue
            var v = d[i]
            v = min(v, (if (x > 0) d[i - 1] else 0) + 1)
            v = min(v, (if (y > 0) d[i - lw] else 0) + 1)
            d[i] = v
        }
        var maxD = 0
        for (y in lh - 1 downTo 0) for (x in lw - 1 downTo 0) {
            val i = y * lw + x
            if (d[i] == 0) continue
            var v = d[i]
            v = min(v, (if (x < lw - 1) d[i + 1] else 0) + 1)
            v = min(v, (if (y < lh - 1) d[i + lw] else 0) + 1)
            d[i] = v
            if (v > maxD) maxD = v
        }
        return Field(x0, y0, lw, d, maxD, gw)
    }

    /** מגדיל את הציון לרזולוציית התמונה באינטרפולציה לינארית; חיובי = אדם */
    private fun upsample(score: FloatArray, gw: Int, gh: Int, s: Int, width: Int, height: Int): BooleanArray {
        val out = BooleanArray(width * height)
        val lastX = gw - 1
        val lastY = gh - 1
        for (y in 0 until height) {
            val fy = ((y + 0.5f) / s - 0.5f).coerceIn(0f, lastY.toFloat())
            val y0 = fy.toInt()
            val y1 = min(lastY, y0 + 1)
            val wy = fy - y0
            val row0 = y0 * gw
            val row1 = y1 * gw
            val outRow = y * width
            for (x in 0 until width) {
                val fx = ((x + 0.5f) / s - 0.5f).coerceIn(0f, lastX.toFloat())
                val x0 = fx.toInt()
                val x1 = min(lastX, x0 + 1)
                val wx = fx - x0
                val top = score[row0 + x0] * (1f - wx) + score[row0 + x1] * wx
                val bottom = score[row1 + x0] * (1f - wx) + score[row1 + x1] * wx
                out[outRow + x] = top * (1f - wy) + bottom * wy > 0f
            }
        }
        return out
    }
}
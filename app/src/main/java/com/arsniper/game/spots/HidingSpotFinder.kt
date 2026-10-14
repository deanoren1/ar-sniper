package com.arsniper.game.spots

import com.arsniper.ml.Ade20kClasses
import com.arsniper.ml.SceneAnalysis
import kotlin.math.max

/**
 * מוצא מקומות הסתתרות באופן אוטומטי מתוך ניתוח הסצנה:
 * רכיבים רציפים של עצמים שאפשר להסתתר מאחוריהם (פחים, ארגזים, רכבים, דלתות...)
 * או לצאת מתוכם (חלונות). Kotlin טהור.
 */
object HidingSpotFinder {

    private val COVER_NAMES = listOf(
        "ashcan", "barrel", "box", "column", "pole", "fence", "car", "truck", "van", "bus",
        "bench", "streetlight", "cabinet", "wardrobe", "bookcase", "plant", "tree", "palm",
        "boat", "minibike", "bicycle", "table", "desk",
        // דלת: עצם שעומד על הרצפה. דמות מציצה מהקצה שלה (דלת סגורה היא לא חלון שאפשר לצאת ממנו)
        "door",
        // רהיטים וחפצים בבית: ספה, כורסה, כיסא, כריות, פוף, סל, תיק, שרפרף, שידה...
        "sofa", "armchair", "chair", "swivel chair", "seat", "cushion", "pillow", "ottoman",
        "bed", "basket", "bag", "stool", "chest of drawers", "counter", "kitchen island",
        "refrigerator", "coffee table", "buffet", "case", "plaything", "ball", "blanket",
        "shelf", "crt screen", "washer", "oven", "bar", "cradle", "tank", "bookcase"
    )

    /**
     * משפחות של עצמים שהמודל נוטה לפצל לכמה קטגוריות באותו עצם (כרית שחציה "כרית" וחציה "פוף").
     * תאים סמוכים מאותה משפחה מתאחדים לעצם אחד.
     */
    private val FAMILIES = listOf(
        listOf("sofa", "armchair", "cushion", "pillow", "ottoman", "seat", "bed", "blanket", "bag", "basket"),
        listOf("chair", "swivel chair", "stool"),
        listOf("cabinet", "wardrobe", "bookcase", "chest of drawers", "buffet", "shelf", "case"),
        listOf("table", "desk", "coffee table", "counter", "kitchen island", "bar"),
        listOf("plaything", "ball", "box")
    )
    private val WINDOW_NAMES = listOf("windowpane")

    /** עצמים דקים: לא מנקים בהם בליטות, כדי שלא יימחקו */
    private val THIN_NAMES = listOf("pole", "streetlight", "fence", "column")

    /** רכיב קטן מזה (כחלק משטח התמונה) מתעלמים ממנו */
    private const val MIN_AREA_FRACTION = 0.0006f

    /** רכיב גדול מזה (ברוחב או בגובה התמונה) הוא לא מחבוא אלא רקע */
    private const val MAX_SIZE_FRACTION = 0.65f

    private const val MAX_SPOTS = 24

    fun find(analysis: SceneAnalysis): List<HidingSpot> {
        val width = analysis.width
        val height = analysis.height
        val total = width * height

        val kindByLabel = arrayOfNulls<SpotKind>(256)
        for (name in COVER_NAMES) {
            val index = Ade20kClasses.NAMES.indexOf(name)
            if (index >= 0) kindByLabel[index] = SpotKind.COVER
        }
        for (name in WINDOW_NAMES) {
            val index = Ade20kClasses.NAMES.indexOf(name)
            if (index >= 0) kindByLabel[index] = SpotKind.WINDOW
        }

        // קבוצה לכל קטגוריה: קטגוריות באותה משפחה מקבלות אותה קבוצה, כדי שיתאחדו
        val groupByLabel = IntArray(256) { it }
        for (family in FAMILIES) {
            val indices = family.map { Ade20kClasses.NAMES.indexOf(it) }.filter { it >= 0 }
            if (indices.isEmpty()) continue
            for (index in indices) groupByLabel[index] = indices[0]
        }

        val thinByLabel = BooleanArray(256)
        for (name in THIN_NAMES) {
            val index = Ade20kClasses.NAMES.indexOf(name)
            if (index >= 0) thinByLabel[index] = true
        }

        val minCells = max(20, (total * MIN_AREA_FRACTION).toInt())
        val visited = BooleanArray(total)
        val queue = IntArray(total)
        val found = ArrayList<HidingSpot>()

        for (start in 0 until total) {
            if (visited[start]) continue
            val label = analysis.labelIndex(start)
            val kind = kindByLabel[label]
            val group = groupByLabel[label]
            if (kind == null) {
                visited[start] = true
                continue
            }

            // חיפוש רוחב על תאים סמוכים עם אותה קטגוריה
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            val labelCounts = IntArray(256)
            var minX = width
            var maxX = -1
            var minY = height
            var maxY = -1

            while (head < tail) {
                val position = queue[head++]
                val x = position % width
                val y = position / width
                labelCounts[analysis.labelIndex(position)] += 1
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                if (x > 0) {
                    val n = position - 1
                    if (!visited[n] && sameGroup(analysis.labelIndex(n), group, kindByLabel, kind, groupByLabel)) {
                        visited[n] = true
                        queue[tail++] = n
                    }
                }
                if (x < width - 1) {
                    val n = position + 1
                    if (!visited[n] && sameGroup(analysis.labelIndex(n), group, kindByLabel, kind, groupByLabel)) {
                        visited[n] = true
                        queue[tail++] = n
                    }
                }
                if (y > 0) {
                    val n = position - width
                    if (!visited[n] && sameGroup(analysis.labelIndex(n), group, kindByLabel, kind, groupByLabel)) {
                        visited[n] = true
                        queue[tail++] = n
                    }
                }
                if (y < height - 1) {
                    val n = position + width
                    if (!visited[n] && sameGroup(analysis.labelIndex(n), group, kindByLabel, kind, groupByLabel)) {
                        visited[n] = true
                        queue[tail++] = n
                    }
                }
            }

            val count = tail
            if (count < minCells) continue

            // הקטגוריה הנפוצה ביותר בעצם: משמשת לשם ולבחירת ניקוי הקצוות
            var dominant = label
            for (i in 0 until 256) if (labelCounts[i] > labelCounts[dominant]) dominant = i

            val maskWidth = maxX - minX + 1
            val maskHeight = maxY - minY + 1
            if (maskWidth.toFloat() / width > MAX_SIZE_FRACTION) continue
            if (maskHeight.toFloat() / height > MAX_SIZE_FRACTION) continue

            val rawMask = BooleanArray(maskWidth * maskHeight)
            for (i in 0 until count) {
                val position = queue[i]
                rawMask[(position / width - minY) * maskWidth + (position % width - minX)] = true
            }

            // ניקוי: סוגר חורים, ממלא חורים קטנים, מסיר בליטות דקות
            val cleaned = MaskTools.clean(rawMask, maskWidth, maskHeight, thinByLabel[dominant])
            val cellX = if (cleaned != null) minX + cleaned.offsetX else minX
            val cellY = if (cleaned != null) minY + cleaned.offsetY else minY
            val useCleaned = cleaned != null &&
                cellX >= 0 && cellY >= 0 &&
                cellX + cleaned.width <= width && cellY + cleaned.height <= height &&
                cleaned.count >= minCells
            if (cleaned == null || cleaned.count < minCells) continue

            val finalMask = if (useCleaned) cleaned.mask else rawMask
            val finalWidth = if (useCleaned) cleaned.width else maskWidth
            val finalHeight = if (useCleaned) cleaned.height else maskHeight
            val finalX = if (useCleaned) cellX else minX
            val finalY = if (useCleaned) cellY else minY

            // עומק חציוני לפי התאים בפועל
            var finalCount = 0
            for (i in finalMask.indices) if (finalMask[i]) finalCount++
            val cells = IntArray(finalCount)
            var written = 0
            for (ly in 0 until finalHeight) {
                for (lx in 0 until finalWidth) {
                    if (finalMask[ly * finalWidth + lx]) {
                        cells[written++] = (finalY + ly) * width + (finalX + lx)
                    }
                }
            }

            found.add(
                HidingSpot(
                    id = -1,
                    kind = kind,
                    classIndex = dominant,
                    gridWidth = width,
                    gridHeight = height,
                    cellX = finalX,
                    cellY = finalY,
                    maskWidth = finalWidth,
                    maskHeight = finalHeight,
                    mask = finalMask,
                    cellCount = finalCount,
                    depth = medianDepth(analysis, cells, finalCount)
                )
            )
        }

        return found
            .sortedByDescending { it.cellCount }
            .take(MAX_SPOTS)
            .mapIndexed { index, spot -> spot.copy(id = index) }
    }

    /** האם תא עם קטגוריה מסוימת שייך לאותו עצם: אותו סוג, ואותה קבוצה (משפחה) */
    private fun sameGroup(
        otherLabel: Int,
        group: Int,
        kindByLabel: Array<SpotKind?>,
        kind: SpotKind,
        groupByLabel: IntArray
    ): Boolean = kindByLabel[otherLabel] == kind && groupByLabel[otherLabel] == group

    /** עומק חציוני של הרכיב, לפי מדגם של עד כ-200 תאים */
    private fun medianDepth(analysis: SceneAnalysis, cells: IntArray, count: Int): Float {
        val step = max(1, count / 200)
        val samples = ArrayList<Float>()
        var i = 0
        while (i < count) {
            val value = analysis.depth[cells[i]]
            if (!value.isNaN() && !value.isInfinite()) samples.add(value)
            i += step
        }
        if (samples.isEmpty()) return Float.NaN
        samples.sort()
        return samples[samples.size / 2]
    }
}
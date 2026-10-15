package com.arsniper.ml

import com.arsniper.game.spots.HidingSpot
import com.arsniper.game.spots.MaskTools
import com.arsniper.game.spots.SpotKind
import java.io.File
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * עצמים שהשחקן סימן בנגיעה (המודל SAM מצייר את המסכה): מהמסכה של SAM ל-HidingSpot, ושמירה בקובץ.
 * מסכה = 256x256 ערכי logit (מעל 0 = העצם), שמכסה את כל התמונה (כמו שהמודל רואה אותה).
 * Kotlin טהור, בלי תלות ב-Android.
 */
object UserObjects {

    const val MASK_SIZE = 256

    /** כמה עצמים אפשר לשמור לתמונה */
    const val MAX_OBJECTS = 12

    /** כמות הקידוד: logit * QUANT + 128 נכנס לבית אחד */
    private const val QUANT = 6f

    /** מסכה קטנה מזה (מחלק משטח הרשת) היא כנראה רעש */
    private const val MIN_AREA_FRACTION = 0.0002f

    /** מסכה גדולה מזה היא כנראה קיר או רצפה, לא עצם להסתתר מאחוריו */
    private const val MAX_AREA_FRACTION = 0.45f

    /** דוגם את מסכת ה-logit (256x256) לרשת הניתוח, באינטרפולציה, בתא אחד לכל תא רשת */
    fun gridLogits(logits: FloatArray, gridWidth: Int, gridHeight: Int): FloatArray {
        val out = FloatArray(gridWidth * gridHeight)
        val last = MASK_SIZE - 1
        for (y in 0 until gridHeight) {
            val sy = ((y + 0.5f) / gridHeight * MASK_SIZE - 0.5f).coerceIn(0f, last.toFloat())
            val y0 = floor(sy).toInt()
            val y1 = min(last, y0 + 1)
            val fy = sy - y0
            for (x in 0 until gridWidth) {
                val sx = ((x + 0.5f) / gridWidth * MASK_SIZE - 0.5f).coerceIn(0f, last.toFloat())
                val x0 = floor(sx).toInt()
                val x1 = min(last, x0 + 1)
                val fx = sx - x0
                val top = logits[y0 * MASK_SIZE + x0] * (1f - fx) + logits[y0 * MASK_SIZE + x1] * fx
                val bottom = logits[y1 * MASK_SIZE + x0] * (1f - fx) + logits[y1 * MASK_SIZE + x1] * fx
                out[y * gridWidth + x] = top * (1f - fy) + bottom * fy
            }
        }
        return out
    }

    /** תמונת תצוגה מקדימה ברזולוציית הרשת: צבע בכל תא שבתוך העצם, שקוף בכל השאר */
    fun previewPixels(logits: FloatArray, gridWidth: Int, gridHeight: Int, argb: Int): IntArray {
        val grid = gridLogits(logits, gridWidth, gridHeight)
        return IntArray(grid.size) { if (grid[it] > 0f) argb else 0 }
    }

    /**
     * "חצאית" מתחת לעצם: כמה תאים מאריכים כל עמודה של המסכה כלפי מטה. מסכת SAM נגמרת לפעמים
     * כמה פיקסלים לפני הבסיס האמיתי של העצם (דלת, ארון), ואז כף הרגל של דמות שמסתתרת מאחוריו
     * מציצה מתחתיו.
     */
    private const val SKIRT_CELLS = 5

    /** מאריך כל עמודה שיש בה תא דלוק ב-extra תאים מתחת לתא הדלוק האחרון שלה */
    private fun growDown(mask: BooleanArray, width: Int, height: Int, extra: Int): Pair<BooleanArray, Int> {
        if (extra <= 0) return Pair(mask, height)
        val newHeight = height + extra
        val out = BooleanArray(width * newHeight)
        System.arraycopy(mask, 0, out, 0, mask.size)
        for (x in 0 until width) {
            var last = -1
            for (y in height - 1 downTo 0) {
                if (mask[y * width + x]) {
                    last = y
                    break
                }
            }
            if (last < 0) continue
            for (k in 1..extra) out[(last + k) * width + x] = true
        }
        return Pair(out, newHeight)
    }

    /** מוסיף "חצאית" גם למחבוא שנמצא לפי סוג העצם (דלת, ארון...), לפני הליטוש. למחבוא חלון לא נוגעים */
    fun addSkirt(spot: HidingSpot): HidingSpot {
        if (spot.kind != SpotKind.COVER || spot.alpha != null) return spot
        val extra = minOf(SKIRT_CELLS, spot.gridHeight - (spot.cellY + spot.maskHeight))
        if (extra <= 0) return spot
        val grown = growDown(spot.mask, spot.maskWidth, spot.maskHeight, extra)
        var count = 0
        for (v in grown.first) if (v) count++
        return spot.copy(maskHeight = grown.second, mask = grown.first, cellCount = count)
    }

    /**
     * ממיר מסכת SAM למקום הסתתרות. מחזיר null אם המסכה קטנה מדי או גדולה מדי.
     * העומק הוא החציון של תאי העצם, והסוג הוא הסוג הנפוץ ביותר בתוכו (בשביל השם).
     */
    fun toSpot(analysis: SceneAnalysis, logits: FloatArray, id: Int): HidingSpot? {
        val width = analysis.width
        val height = analysis.height
        val grid = gridLogits(logits, width, height)

        var minX = width
        var maxX = -1
        var minY = height
        var maxY = -1
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (grid[y * width + x] > 0f) {
                    count++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        val total = width * height
        if (count < max(20, (total * MIN_AREA_FRACTION).toInt())) return null
        if (count > total * MAX_AREA_FRACTION) return null

        val rawWidth = maxX - minX + 1
        val rawHeight = maxY - minY + 1
        val raw = BooleanArray(rawWidth * rawHeight)
        for (y in 0 until rawHeight) {
            for (x in 0 until rawWidth) {
                raw[y * rawWidth + x] = grid[(minY + y) * width + (minX + x)] > 0f
            }
        }

        // ניקוי עדין (עצמים דקים כמו קורקינט לא נחתכים)
        val cleaned = MaskTools.clean(raw, rawWidth, rawHeight, true)
        val cellX = if (cleaned != null) minX + cleaned.offsetX else minX
        val cellY = if (cleaned != null) minY + cleaned.offsetY else minY
        val useCleaned = cleaned != null &&
            cellX >= 0 && cellY >= 0 &&
            cellX + cleaned.width <= width && cellY + cleaned.height <= height
        val mask = if (useCleaned) cleaned!!.mask else raw
        val maskWidth = if (useCleaned) cleaned!!.width else rawWidth
        val maskHeight = if (useCleaned) cleaned!!.height else rawHeight
        val x0 = if (useCleaned) cellX else minX
        val y0 = if (useCleaned) cellY else minY

        // סוג נפוץ ועומק חציוני לפי התאים בפועל
        val labelCounts = IntArray(Ade20kClasses.NAMES.size)
        val depths = ArrayList<Float>()
        var finalCount = 0
        for (ly in 0 until maskHeight) {
            for (lx in 0 until maskWidth) {
                if (!mask[ly * maskWidth + lx]) continue
                finalCount++
                val position = (y0 + ly) * width + (x0 + lx)
                val label = analysis.labelIndex(position)
                if (label < labelCounts.size) labelCounts[label]++
                if (finalCount % 5 == 0) {
                    val d = analysis.depth[position]
                    if (!d.isNaN() && !d.isInfinite()) depths.add(d)
                }
            }
        }
        if (finalCount == 0) return null
        var label = 0
        for (i in labelCounts.indices) if (labelCounts[i] > labelCounts[label]) label = i
        depths.sort()
        val depth = if (depths.isEmpty()) Float.NaN else depths[depths.size / 2]

        val extra = minOf(SKIRT_CELLS, height - (y0 + maskHeight))
        val grown = growDown(mask, maskWidth, maskHeight, extra)
        var grownCount = 0
        for (v in grown.first) if (v) grownCount++

        return HidingSpot(
            id = id,
            kind = SpotKind.COVER,
            classIndex = label,
            gridWidth = width,
            gridHeight = height,
            cellX = x0,
            cellY = y0,
            maskWidth = maskWidth,
            maskHeight = grown.second,
            mask = grown.first,
            cellCount = grownCount,
            depth = depth
        )
    }

    // --- שמירה בקובץ (לפי תוכן התמונה) ---

    fun file(dir: File, key: String, prefix: String = "objects"): File = File(dir, "${prefix}_$key.bin")

    fun pack(logits: FloatArray): ByteArray = ByteArray(logits.size) {
        (logits[it] * QUANT + 128f).roundToInt().coerceIn(0, 255).toByte()
    }

    fun unpack(bytes: ByteArray, offset: Int): FloatArray = FloatArray(MASK_SIZE * MASK_SIZE) {
        ((bytes[offset + it].toInt() and 0xFF) - 128) / QUANT
    }

    fun save(dir: File, key: String, objects: List<FloatArray>, prefix: String = "objects", max: Int = MAX_OBJECTS) {
        val file = file(dir, key, prefix)
        if (objects.isEmpty()) {
            file.delete()
            return
        }
        val list = objects.takeLast(max)
        val data = ByteArray(1 + list.size * MASK_SIZE * MASK_SIZE)
        data[0] = list.size.toByte()
        for ((index, logits) in list.withIndex()) {
            System.arraycopy(pack(logits), 0, data, 1 + index * MASK_SIZE * MASK_SIZE, MASK_SIZE * MASK_SIZE)
        }
        file.writeBytes(data)
    }

    fun load(dir: File, key: String, prefix: String = "objects"): List<FloatArray> {
        val file = file(dir, key, prefix)
        if (!file.exists()) return emptyList()
        val data = try {
            file.readBytes()
        } catch (e: java.io.IOException) {
            return emptyList()
        }
        if (data.isEmpty()) return emptyList()
        val count = data[0].toInt() and 0xFF
        val size = MASK_SIZE * MASK_SIZE
        if (count <= 0 || data.size < 1 + count * size) return emptyList()
        return List(count) { unpack(data, 1 + it * size) }
    }
}
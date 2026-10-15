package com.arsniper.ml

import com.arsniper.game.spots.HidingSpot
import kotlin.math.abs

/**
 * זיהוי עצמים אוטומטי עם SAM: במקום שהשחקן יסמן עצמים ידנית, המודל מנסה נקודות בכל התמונה
 * (וגם במרכז של כל מחבוא שהמערכת מצאה), והמסכות הטובות נשמרות.
 * Kotlin טהור: את הרצת המודל מקבלים כפונקציה, כך אפשר לבדוק בלי Android.
 */
object SamAuto {

    /** כמה נקודות ברשת הניסוי: עמודות ושורות */
    const val GRID_COLS = 12
    const val GRID_ROWS = 7

    /** כמה מסכות לכל היותר שומרים לתמונה */
    const val MAX_MASKS = 40

    /** מסכה קטנה או גדולה מזה (חלק משטח התמונה) לא נחשבת עצם להסתתר מאחוריו */
    private const val MIN_AREA = 0.0004f
    private const val MAX_AREA = 0.35f

    /**
     * יציבות: איזה חלק משטח המסכה (logit מעל 0) הוא "בטוח" (מעל 30% מהערך הגבוה האופייני של המסכה).
     * מסכה לא יציבה (לא בטוחה, נקודות נקודות) היא בדרך כלל ניחוש. המדידה יחסית לעוצמה של המסכה עצמה,
     * כי בבדיקה על מכשיר סף קבוע דחה גם קרש חד וברור (עוצמת ה-logit בפועל נמוכה מהצפוי).
     */
    private const val MIN_STABILITY = 0.6f
    private const val CONFIDENT_FRACTION = 0.3f

    /** שתי מסכות עם חפיפה (IoU) כזו נחשבות אותו עצם */
    const val SAME_OBJECT_IOU = 0.6f

    /** מסכה שרובה קיר, תקרה או שמיים היא רקע, לא עצם */
    private const val BACKGROUND_FRACTION = 0.7f

    /**
     * ...אבל רק אם היא גדולה. מסכה קטנה שהמודל קרא לה "קיר" (קרש שנשען על הקיר, דלת לבנה)
     * היא עצם אמיתי. חלק משטח התמונה
     */
    private const val BACKGROUND_MIN_AREA = 0.04f

    /** מסכה גדולה שרובה רצפה היא הרצפה עצמה (מסכה קטנה על "רצפה" יכולה להיות קורקינט שהמודל לא מכיר) */
    private const val FLOOR_FRACTION = 0.6f
    private const val FLOOR_MAX_AREA = 0.12f

    private val WALLISH = listOf("wall", "ceiling", "sky", "building", "house")
    private val FLOORISH = listOf("floor", "rug", "road", "sidewalk", "grass", "earth", "path", "field", "sand")

    private class Point(val x: Float, val y: Float, val spot: HidingSpot?)

    /** סיבות דחייה (לדיבוג) והצבע שלהן בתצוגה: ARGB */
    const val R_WALL = "wall"
    const val R_AREA = "area"
    const val R_FLOOR = "floor"
    const val R_UNSTABLE = "unstable"
    const val R_DUP = "dup"
    const val R_SKIP = "skip"
    val DEBUG_COLORS = mapOf(
        R_WALL to 0x99FF2020.toInt(),
        R_AREA to 0x99FFFF00.toInt(),
        R_FLOOR to 0x9900FF00.toInt(),
        R_UNSTABLE to 0x9900FFFF.toInt(),
        R_DUP to 0x99FFFFFF.toInt()
    )

    /** תמונת דיבוג של הריצה האחרונה: כל המסכות שנדחו, צבועות לפי סיבת הדחייה (ברזולוציית רשת הניתוח) */
    var debugOverlay: IntArray? = null
        private set
    var debugWidth = 0
        private set
    var debugHeight = 0
        private set

    /** כמה מסכות נדחו מכל סיבה בריצה האחרונה (וגם kept = כמה נשמרו, skip = נקודות שכבר מכוסות) */
    var debugCounts: Map<String, Int> = emptyMap()
        private set

    /**
     * מריץ את הזיהוי ומחזיר מסכות logit (256x256) של עצמים. segment(x, y) = שלוש המסכות של SAM לנקודה
     * בקואורדינטות 0..1. onProgress(נעשה, סה"כ). cancelled() מאפשר לעצור באמצע.
     */
    fun run(
        analysis: SceneAnalysis,
        autoSpots: List<HidingSpot>,
        segment: (Float, Float) -> Array<FloatArray>,
        onProgress: (Int, Int) -> Unit,
        cancelled: () -> Boolean = { false }
    ): List<FloatArray> {
        val gw = analysis.width
        val gh = analysis.height
        val wallLabels = labelSet(WALLISH)
        val floorLabels = labelSet(FLOORISH)

        val points = ArrayList<Point>()
        // קודם מרכזי המחבואים שכבר נמצאו: המסכה הטובה ביותר שלהם מחליפה אותם
        for (spot in autoSpots) {
            val p = innerPoint(spot) ?: continue
            points.add(Point(p.first, p.second, spot))
        }
        // אחר כך רשת נקודות בכל התמונה, לעצמים שהמערכת פספסה
        for (r in 0 until GRID_ROWS) {
            for (c in 0 until GRID_COLS) {
                val x = 0.04f + 0.92f * (c + 0.5f) / GRID_COLS
                val y = 0.08f + 0.88f * (r + 0.5f) / GRID_ROWS
                points.add(Point(x, y, null))
            }
        }

        val kept = ArrayList<FloatArray>()
        val keptGrid = ArrayList<BooleanArray>()
        val covered = BooleanArray(gw * gh)

        val overlay = IntArray(gw * gh)
        val counts = HashMap<String, Int>()
        fun count(reason: String) { counts[reason] = (counts[reason] ?: 0) + 1 }
        fun paint(grid: BooleanArray, reason: String) {
            val color = DEBUG_COLORS[reason] ?: return
            for (i in grid.indices) if (grid[i]) overlay[i] = color
        }

        for ((index, point) in points.withIndex()) {
            if (cancelled()) break
            onProgress(index, points.size)
            if (kept.size >= MAX_MASKS) break
            // נקודה שכבר בתוך עצם שנמצא לא צריכה ניסיון נוסף
            if (point.spot == null) {
                val cx = (point.x * gw).toInt().coerceIn(0, gw - 1)
                val cy = (point.y * gh).toInt().coerceIn(0, gh - 1)
                if (covered[cy * gw + cx]) {
                    count(R_SKIP)
                    continue
                }
            }

            val masks = segment(point.x, point.y)
            val candidates = if (point.spot != null) masks.toList() else listOf(masks[0])

            // למחבוא קיים: המסכה שהכי דומה לו. לנקודת רשת: המסכה הראשונה
            var best: FloatArray? = null
            var bestGrid: BooleanArray? = null
            var bestScore = -1f
            for (logits in candidates) {
                val grid = toGrid(logits, gw, gh)
                val score = if (point.spot != null) iouWithSpot(grid, gw, gh, point.spot) else 1f
                if (score > bestScore) {
                    bestScore = score
                    best = logits
                    bestGrid = grid
                }
            }
            val logits = best ?: continue
            val grid = bestGrid ?: continue
            val reason = rejectReason(logits, grid, analysis, wallLabels, floorLabels)
            if (reason != null) {
                count(reason)
                paint(grid, reason)
                continue
            }
            if (keptGrid.any { iou(it, grid) > SAME_OBJECT_IOU }) {
                count(R_DUP)
                paint(grid, R_DUP)
                continue
            }

            count("kept")
            kept.add(logits)
            keptGrid.add(grid)
            for (i in grid.indices) if (grid[i]) covered[i] = true
        }
        onProgress(points.size, points.size)
        debugOverlay = overlay
        debugWidth = gw
        debugHeight = gh
        debugCounts = counts
        return kept
    }

    /** חפיפה (IoU) בין שני עצמים ברשת הניתוח: חיתוך חלקי איחוד */
    fun iou(a: BooleanArray, b: BooleanArray): Float {
        var inter = 0
        var union = 0
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            if (x || y) union++
            if (x && y) inter++
        }
        return if (union == 0) 0f else inter.toFloat() / union
    }

    /** חפיפה בין מסכה (ברשת) למחבוא */
    fun iouWithSpot(grid: BooleanArray, gw: Int, gh: Int, spot: HidingSpot): Float {
        var inter = 0
        var spotCells = 0
        var gridCells = 0
        for (v in grid) if (v) gridCells++
        for (ly in 0 until spot.maskHeight) {
            for (lx in 0 until spot.maskWidth) {
                if (!spot.mask[ly * spot.maskWidth + lx]) continue
                spotCells++
                val x = spot.cellX + lx
                val y = spot.cellY + ly
                if (x in 0 until gw && y in 0 until gh && grid[y * gw + x]) inter++
            }
        }
        val union = gridCells + spotCells - inter
        return if (union <= 0) 0f else inter.toFloat() / union
    }

    fun toGrid(logits: FloatArray, gw: Int, gh: Int): BooleanArray {
        val values = UserObjects.gridLogits(logits, gw, gh)
        return BooleanArray(values.size) { values[it] > 0f }
    }

    private fun labelSet(names: List<String>): BooleanArray {
        val set = BooleanArray(256)
        for (name in names) {
            val index = Ade20kClasses.NAMES.indexOf(name)
            if (index in 0..255) set[index] = true
        }
        return set
    }

    /** נקודה בתוך המחבוא, קרובה למרכז שלו (תא של המסכה שהכי קרוב לחציון) */
    private fun innerPoint(spot: HidingSpot): Pair<Float, Float>? {
        val xs = ArrayList<Int>()
        val ys = ArrayList<Int>()
        for (ly in 0 until spot.maskHeight) {
            for (lx in 0 until spot.maskWidth) {
                if (spot.mask[ly * spot.maskWidth + lx]) {
                    xs.add(lx)
                    ys.add(ly)
                }
            }
        }
        if (xs.isEmpty()) return null
        val sx = xs.sorted()
        val sy = ys.sorted()
        val mx = sx[sx.size / 2]
        val my = sy[sy.size / 2]
        var bestIndex = 0
        var bestDist = Int.MAX_VALUE
        for (i in xs.indices) {
            val d = abs(xs[i] - mx) + abs(ys[i] - my)
            if (d < bestDist) {
                bestDist = d
                bestIndex = i
            }
        }
        val nx = (spot.cellX + xs[bestIndex] + 0.5f) / spot.gridWidth
        val ny = (spot.cellY + ys[bestIndex] + 0.5f) / spot.gridHeight
        return Pair(nx, ny)
    }

    /** null = המסכה מתקבלת. אחרת הסיבה לדחייה */
    private fun rejectReason(
        logits: FloatArray,
        grid: BooleanArray,
        analysis: SceneAnalysis,
        wallLabels: BooleanArray,
        floorLabels: BooleanArray
    ): String? {
        var cells = 0
        var wall = 0
        var floor = 0
        for (i in grid.indices) {
            if (!grid[i]) continue
            cells++
            val label = analysis.labelIndex(i) and 0xFF
            if (wallLabels[label]) wall++
            if (floorLabels[label]) floor++
        }
        val total = grid.size
        val fraction = cells.toFloat() / total
        if (fraction < MIN_AREA || fraction > MAX_AREA) return R_AREA
        if (fraction >= BACKGROUND_MIN_AREA && wall >= BACKGROUND_FRACTION * cells) return R_WALL
        if (fraction > FLOOR_MAX_AREA && floor >= FLOOR_FRACTION * cells) return R_FLOOR

        // יציבות: כמה משטח המסכה הוא מעל חלק מהערך הגבוה האופייני שלה (אחוזון 90)
        var positive = 0
        for (v in logits) if (v > 0f) positive++
        if (positive == 0) return R_UNSTABLE
        val values = FloatArray(positive)
        var written = 0
        for (v in logits) if (v > 0f) values[written++] = v
        values.sort()
        val typical = values[(positive * 0.9f).toInt().coerceAtMost(positive - 1)]
        val threshold = (typical * CONFIDENT_FRACTION).coerceAtLeast(0.2f)
        var strict = 0
        for (v in values) if (v > threshold) strict++
        if (strict.toFloat() / positive < MIN_STABILITY) return R_UNSTABLE
        return null
    }
}
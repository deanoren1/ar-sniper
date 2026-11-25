package com.arsniper.ml

import com.arsniper.game.spots.GuideImage
import com.arsniper.game.spots.HidingSpot
import com.arsniper.game.spots.HidingSpotFinder
import com.arsniper.game.spots.SpotRefiner

/**
 * תוצאת ניתוח הסצנה של הזירה הנוכחית, שעוברת ממסך הסצנה למסך המשחק (בזיכרון בלבד).
 * המחבואים = מה שנמצא אוטומטית לפי סוגי העצמים + עצמים שהשחקן סימן בנגיעה (SAM).
 */
object SceneSession {

    var analysis: SceneAnalysis? = null
        private set

    /** כל המחבואים: האוטומטיים ואחריהם אלה שהשחקן סימן */
    var spots: List<HidingSpot> = emptyList()
        private set

    /** בהירות התמונה (0..255) ברזולוציית רשת הניתוח, להתאמת צבע האויבים לתאורה */
    var luma: ByteArray? = null
        private set

    /** העצמים שהשחקן סימן: מסכות logit של SAM (256x256) */
    var userObjects: List<FloatArray> = emptyList()
        private set

    /** העצמים ש-SAM מצא לבד (זיהוי אוטומטי). אם יש: מחליפים את המחבואים האוטומטיים (חוץ מחלונות) */
    var samMasks: List<FloatArray> = emptyList()
        private set

    /** עצם שסומן ידנית מחליף מחבוא רק אם שטחו לא יותר מפי כך משטח המחבוא */
    private const val REPLACE_MAX_RATIO = 2.5f

    private var autoSpots: List<HidingSpot> = emptyList()

    /** מחבואי צמחים שנוספו מתמונת ה-AI (נשמרים גם אחרי בנייה מחדש של הרשימה) */
    private var extraSpots: List<HidingSpot> = emptyList()
    private var guide: GuideImage? = null

    /** מחבואים מהתמונה המקורית שעולם ה-AI מחק (במקומם רצפה/קיר/שמיים): לא מסתתרים מאחוריהם */
    private var prunedIds: Set<Int> = emptySet()

    /** המחבואים שנמצאו לפי סוגי העצמים (לפני כל שינוי): הבסיס לזיהוי האוטומטי של SAM */
    val finderSpots: List<HidingSpot> get() = autoSpots

    /**
     * שומר את הניתוח, מוצא מחבואים, ואם יש תמונת עזר (guide) מלטש את הקצוות שלהם
     * ושומר גם מפת בהירות. עצמים ש-SAM מצא וששחקן סימן מוסיפים בנפרד.
     */
    fun set(analysis: SceneAnalysis, guide: GuideImage? = null) {
        this.analysis = analysis
        this.guide = guide
        // חצאית מתחת לכל מחבוא (גם דלת מהזיהוי הרגיל), כדי שכף רגל של דמות לא תציץ מתחתיו
        val found = HidingSpotFinder.find(analysis).map { UserObjects.addSkirt(it) }
        this.autoSpots = if (guide != null) {
            found.map { SpotRefiner.refine(it, guide) }
        } else {
            found
        }
        this.userObjects = emptyList()
        this.samMasks = emptyList()
        this.extraSpots = emptyList()
        this.prunedIds = emptySet()
        this.spots = autoSpots
        this.luma = if (guide != null) buildLuma(analysis, guide) else null
    }

    /** קובע את העצמים שהשחקן סימן (מחליף את הקודמים) ובונה מחדש את רשימת המחבואים */
    fun setUserObjects(objects: List<FloatArray>) {
        if (analysis == null) return
        userObjects = objects
        rebuild()
    }

    /** קובע את העצמים ש-SAM מצא אוטומטית (רשימה ריקה = חוזרים לזיהוי לפי סוגים) */
    fun setSamMasks(masks: List<FloatArray>) {
        if (analysis == null) return
        samMasks = masks
        rebuild()
    }

    /**
     * בונה את המחבואים: בסיס = המחבואים לפי סוגים, או (אם SAM רץ) החלונות + עצמי SAM.
     * מעל זה עצמים שהשחקן סימן: הם מחליפים מחבוא בסיס שהוא בעצם אותו עצם.
     */
    private fun rebuild() {
        val current = analysis ?: return
        var nextId = (autoSpots.maxOfOrNull { it.id } ?: -1) + 1
        val g = guide

        val base: List<HidingSpot> = if (samMasks.isEmpty()) {
            autoSpots
        } else {
            val fromSam = ArrayList<HidingSpot>()
            for (logits in samMasks) {
                val spot = UserObjects.toSpot(current, logits, nextId) ?: continue
                // SAM מסמן גם כתמי צל ומשטחים על הרצפה (כביש, מדרכה). מפלצת שיצאה "מאחוריהם" נראתה חתוכה באוויר
                if (groundShare(current, spot) >= SAM_GROUND_SHARE) continue
                fromSam.add(if (g != null) SpotRefiner.refine(spot, g) else spot)
                nextId++
            }
            // דלתות: SAM לא תמיד מצליח עליהן (דלת לבנה על קיר בהיר), אז דלת שהמערכת מצאה לפי סוג
            // נשארת, אלא אם SAM כבר מצא אותה
            val doorIndex = Ade20kClasses.NAMES.indexOf("door")
            val doors = autoSpots.filter { a ->
                a.kind == com.arsniper.game.spots.SpotKind.COVER &&
                    a.classIndex == doorIndex &&
                    fromSam.none { s -> mostlyInside(a, s) }
            }
            autoSpots.filter { it.kind == com.arsniper.game.spots.SpotKind.WINDOW } + doors + fromSam
        }

        val raw = ArrayList<HidingSpot>()
        val extra = ArrayList<HidingSpot>()
        for (logits in userObjects) {
            val spot = UserObjects.toSpot(current, logits, nextId) ?: continue
            raw.add(spot)
            extra.add(if (g != null) SpotRefiner.refine(spot, g) else spot)
            nextId++
        }
        val kept = base.filter { b -> raw.none { user -> mostlyInside(b, user) } }
        spots = (kept + extra + extraSpots).filter { it.id !in prunedIds }
    }

    /**
     * האם המחבוא הוא בעצם אותו עצם שהשחקן סימן: יותר ממחצית תאי המסכה שלו בתוך העצם שסומן,
     * והעצם שסומן לא גדול ממנו בהרבה (אחרת זו מסכה רחבה, כמו מדרגות, שמכילה עצמים קטנים אחרים).
     */
    private fun mostlyInside(inner: HidingSpot, outer: HidingSpot): Boolean {
        if (outer.cellCount > REPLACE_MAX_RATIO * inner.cellCount) return false
        var total = 0
        var inside = 0
        for (ly in 0 until inner.maskHeight) {
            for (lx in 0 until inner.maskWidth) {
                if (!inner.mask[ly * inner.maskWidth + lx]) continue
                total++
                val nx = (inner.cellX + lx + 0.5f) / inner.gridWidth
                val ny = (inner.cellY + ly + 0.5f) / inner.gridHeight
                if (outer.contains(nx, ny)) inside++
            }
        }
        return total > 0 && inside * 2 > total
    }

    /**
     * מוסיף מחבואים (צמחים) מניתוח ממוזג של תמונת ה-AI, בלי לגעת במחבואים הקיימים (כדי שדמויות שכבר במשחק לא יושפעו).
     * מחבוא שרוב שטחו כבר מכוסה במחבוא קיים לא נוסף. מחזיר כמה נוספו.
     */
    /**
     * עולם ה-AI לפעמים מוחק עצם שהיה בתמונה המקורית (משטח, ארגז). דמות שמסתתרת מאחורי מחבוא כזה נראית חתוכה באוויר.
     * מסירים מחבוא (לא חלון) שברוב שטחו עולם ה-AI מראה רקע בלבד. מחזיר כמה הוסרו
     */
    fun pruneMissing(ai: SceneAnalysis, background: Set<Int>): Int {
        val removed = HashSet<Int>()
        for (spot in spots) {
            if (spot.kind != com.arsniper.game.spots.SpotKind.COVER) continue
            if (extraSpots.any { it.id == spot.id }) continue
            var total = 0
            var bg = 0
            for (ly in 0 until spot.maskHeight - spot.skirtCells.coerceAtMost(spot.maskHeight - 1)) {
                for (lx in 0 until spot.maskWidth) {
                    if (!spot.mask[ly * spot.maskWidth + lx]) continue
                    total++
                    val nx = (spot.cellX + lx + 0.5f) / spot.gridWidth
                    val ny = (spot.cellY + ly + 0.5f) / spot.gridHeight
                    val ax = (nx * ai.width).toInt().coerceIn(0, ai.width - 1)
                    val ay = (ny * ai.height).toInt().coerceIn(0, ai.height - 1)
                    if (ai.labelIndex(ay * ai.width + ax) in background) bg++
                }
            }
            if (total > 0 && bg >= total * PRUNE_BACKGROUND_SHARE) removed.add(spot.id)
        }
        if (removed.isEmpty()) return 0
        prunedIds = prunedIds + removed
        spots = spots.filter { it.id !in prunedIds }
        return removed.size
    }

    private const val PRUNE_BACKGROUND_SHARE = 0.7f

    /** עצם SAM שחלק כזה ממנו (לפי הסגמנטציה) הוא רצפה, כביש או מדרכה: לא מחבוא (צל, כתם, משטח) */
    private const val SAM_GROUND_SHARE = 0.5f

    private val GROUND_NAMES = listOf(
        "floor", "earth", "road", "sidewalk", "path", "rug", "grass", "sand", "dirt track", "land",
        "runway", "field", "water", "ground"
    )

    private val groundLabels: BooleanArray by lazy {
        val b = BooleanArray(256)
        for (name in GROUND_NAMES) {
            val i = com.arsniper.ml.Ade20kClasses.NAMES.indexOf(name)
            if (i in 0..255) b[i] = true
        }
        b
    }

    /** איזה חלק מתאי המחבוא מסווג כקרקע בסגמנטציה */
    private fun groundShare(a: SceneAnalysis, spot: HidingSpot): Float {
        var total = 0
        var ground = 0
        for (ly in 0 until spot.maskHeight) {
            for (lx in 0 until spot.maskWidth) {
                if (!spot.mask[ly * spot.maskWidth + lx]) continue
                total++
                val nx = (spot.cellX + lx + 0.5f) / spot.gridWidth
                val ny = (spot.cellY + ly + 0.5f) / spot.gridHeight
                val ax = (nx * a.width).toInt().coerceIn(0, a.width - 1)
                val ay = (ny * a.height).toInt().coerceIn(0, a.height - 1)
                if (groundLabels[a.labelIndex(ay * a.width + ax).coerceIn(0, 255)]) ground++
            }
        }
        return if (total == 0) 0f else ground.toFloat() / total
    }

    /** מלטש מחדש את קצוות המחבואים (לא חלונות, לא אלה שנוספו מה-AI) לפי תמונת עולם ה-AI. מחזיר כמה לוטשו */
    fun refineEdges(aiGuide: GuideImage?): Int {
        if (aiGuide == null) return 0
        var n = 0
        spots = spots.map { spot ->
            if (spot.kind != com.arsniper.game.spots.SpotKind.COVER || extraSpots.any { it.id == spot.id }) spot
            else { n++; SpotRefiner.refine(spot, aiGuide) }
        }
        return n
    }

    fun addExtraSpots(merged: SceneAnalysis, classes: Set<Int>, aiGuide: GuideImage?): Int {
        val current = analysis ?: return 0
        if (merged.width != current.width || merged.height != current.height) return 0
        var nextId = (spots.maxOfOrNull { it.id } ?: -1) + 1
        val added = ArrayList<HidingSpot>()
        for (found in HidingSpotFinder.find(merged)) {
            if (found.kind != com.arsniper.game.spots.SpotKind.COVER || found.classIndex !in classes) continue
            if (spots.any { existing -> overlap(found, existing) > 0.5f }) continue
            var spot = UserObjects.addSkirt(found.copy(id = nextId))
            if (aiGuide != null) spot = SpotRefiner.refine(spot, aiGuide)
            added.add(spot)
            nextId++
        }
        if (added.isEmpty()) return 0
        extraSpots = extraSpots + added
        spots = spots + added
        return added.size
    }

    /** איזה חלק משטח inner נמצא בתוך outer (0..1) */
    private fun overlap(inner: HidingSpot, outer: HidingSpot): Float {
        var total = 0
        var inside = 0
        for (ly in 0 until inner.maskHeight) {
            for (lx in 0 until inner.maskWidth) {
                if (!inner.mask[ly * inner.maskWidth + lx]) continue
                total++
                val nx = (inner.cellX + lx + 0.5f) / inner.gridWidth
                val ny = (inner.cellY + ly + 0.5f) / inner.gridHeight
                if (outer.contains(nx, ny)) inside++
            }
        }
        return if (total == 0) 1f else inside.toFloat() / total
    }

    fun clear() {
        extraSpots = emptyList()
        analysis = null
        spots = emptyList()
        autoSpots = emptyList()
        userObjects = emptyList()
        samMasks = emptyList()
        guide = null
        luma = null
    }

    /** דוגם את תמונת העזר לרזולוציית הרשת */
    private fun buildLuma(analysis: SceneAnalysis, guide: GuideImage): ByteArray {
        val s = guide.scale
        val out = ByteArray(analysis.width * analysis.height)
        for (y in 0 until analysis.height) {
            for (x in 0 until analysis.width) {
                val fx = (x * s + s / 2).coerceAtMost(guide.width - 1)
                val fy = (y * s + s / 2).coerceAtMost(guide.height - 1)
                out[y * analysis.width + x] = guide.gray[fy * guide.width + fx]
            }
        }
        return out
    }
}
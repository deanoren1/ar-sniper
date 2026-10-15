package com.arsniper.ml

import com.arsniper.game.spots.GuideImage
import com.arsniper.game.spots.HidingSpot
import com.arsniper.game.spots.HidingSpotFinder
import com.arsniper.game.spots.SpotRefiner

/**
 * תוצאת ניתוח הסצנה של הזירה הנוכחית, שעוברת ממסך הסצנה למסך המשחק (בזיכרון בלבד).
 * המחבואים = מה שנמצא אוטומטית לפי סוגי העצמים (או לפי SAM) + עצמים שהשחקן סימן בנגיעה.
 */
object SceneSession {

    var analysis: SceneAnalysis? = null
        private set

    /** כל המחבואים: הבסיס (לפי סוגים או לפי SAM) ואחריו אלה שהשחקן סימן */
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
    private var guide: GuideImage? = null

    /** המחבואים שנמצאו לפי סוגי העצמים (לפני כל שינוי): הבסיס לזיהוי האוטומטי של SAM */
    val finderSpots: List<HidingSpot> get() = autoSpots

    /**
     * שומר את הניתוח, מוצא מחבואים, ואם יש תמונת עזר (guide) מלטש את הקצוות שלהם
     * ושומר גם מפת בהירות. עצמים ש-SAM מצא וששחקן סימן מוסיפים בנפרד.
     */
    fun set(analysis: SceneAnalysis, guide: GuideImage? = null) {
        this.analysis = analysis
        this.guide = guide
        val found = HidingSpotFinder.find(analysis)
        this.autoSpots = if (guide != null) {
            found.map { SpotRefiner.refine(it, guide) }
        } else {
            found
        }
        this.userObjects = emptyList()
        this.samMasks = emptyList()
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
                fromSam.add(if (g != null) SpotRefiner.refine(spot, g) else spot)
                nextId++
            }
            autoSpots.filter { it.kind == com.arsniper.game.spots.SpotKind.WINDOW } + fromSam
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
        spots = kept + extra
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

    fun clear() {
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
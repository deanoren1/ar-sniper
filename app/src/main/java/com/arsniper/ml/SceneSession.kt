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

    /** כל המחבואים: האוטומטיים (בלי אלה שהוחלפו) ואחריהם אלה שהשחקן סימן */
    var spots: List<HidingSpot> = emptyList()
        private set

    /** בהירות התמונה (0..255) ברזולוציית רשת הניתוח, להתאמת צבע האויבים לתאורה */
    var luma: ByteArray? = null
        private set

    /** העצמים שהשחקן סימן: מסכות logit של SAM (256x256) */
    var userObjects: List<FloatArray> = emptyList()
        private set

    private var autoSpots: List<HidingSpot> = emptyList()
    private var guide: GuideImage? = null

    /**
     * שומר את הניתוח, מוצא מחבואים, ואם יש תמונת עזר (guide) מלטש את הקצוות שלהם
     * ושומר גם מפת בהירות. עצמים שהשחקן סימן מוסיפים בנפרד עם setUserObjects.
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
        this.spots = autoSpots
        this.luma = if (guide != null) buildLuma(analysis, guide) else null
    }

    /**
     * קובע את רשימת העצמים שהשחקן סימן (מחליף את הקודמת) ובונה מהם מחבואים.
     * מסכה שקטנה או גדולה מדי נזרקת. מחבוא אוטומטי שרוב השטח שלו נמצא בתוך עצם שהשחקן סימן
     * מוחלף בו (המסכה של SAM מדויקת יותר, למשל פח עם המכסה שלו), כדי שלא יישארו שני מחבואים על אותו עצם.
     */
    fun setUserObjects(objects: List<FloatArray>) {
        val current = analysis ?: return
        userObjects = objects
        var nextId = (autoSpots.maxOfOrNull { it.id } ?: -1) + 1
        val raw = ArrayList<HidingSpot>()
        val extra = ArrayList<HidingSpot>()
        for (logits in objects) {
            val spot = UserObjects.toSpot(current, logits, nextId) ?: continue
            raw.add(spot)
            val g = guide
            extra.add(if (g != null) SpotRefiner.refine(spot, g) else spot)
            nextId++
        }
        val kept = autoSpots.filter { auto -> raw.none { user -> mostlyInside(auto, user) } }
        spots = kept + extra
    }

    /** האם יותר ממחצית תאי המסכה של המחבוא נמצאים בתוך עצם אחר */
    private fun mostlyInside(inner: HidingSpot, outer: HidingSpot): Boolean {
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
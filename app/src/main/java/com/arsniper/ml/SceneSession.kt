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
     * מסכה שקטנה או גדולה מדי נזרקת.
     */
    fun setUserObjects(objects: List<FloatArray>) {
        val current = analysis ?: return
        userObjects = objects
        var nextId = (autoSpots.maxOfOrNull { it.id } ?: -1) + 1
        val extra = ArrayList<HidingSpot>()
        for (logits in objects) {
            val spot = UserObjects.toSpot(current, logits, nextId) ?: continue
            val g = guide
            extra.add(if (g != null) SpotRefiner.refine(spot, g) else spot)
            nextId++
        }
        spots = autoSpots + extra
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
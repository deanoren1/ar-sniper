package com.arsniper.game.engine

import kotlin.math.max
import kotlin.math.min

/**
 * מודל הקרקע של הסצנה, לפי הפרספקטיבה: על רצפה ישרה, גובה אדם בתמונה גדל ישר עם המרחק של הרגליים
 * מקו האופק: גובה = (1.7 / גובה המצלמה) * (y_רגליים - y_אופק), כשהכל ביחידות של גובה התמונה.
 * את קו האופק מוצאים מהעומק של השורות הרחוקות של הרצפה, ואם אין מספיק רצפה: ברירת מחדל.
 * כך אדם רחוק (רגליים גבוהות במסך) קטן באמת, ואדם קרוב גדול, בלי להסתמך על עומק רועש בנקודה אחת.
 */
class GroundModel(
    /** קו האופק (y בתמונה, 0..1; יכול לצאת מחוץ לתמונה אם המצלמה מופנית למטה) */
    val horizon: Float,
    /** גובה המצלמה מעל הרצפה במטרים */
    val cameraHeight: Float,
    /** האם הערכים נמצאו מהתמונה (או ברירת מחדל) */
    val fitted: Boolean
) {

    /** גובה אדם עומד (חלק מגובה התמונה) שרגליו בגובה feetY בתמונה */
    fun personHeight(feetY: Float): Float =
        max(MIN_HEIGHT, PERSON_METERS / cameraHeight * (feetY - horizon))

    companion object {
        private const val PERSON_METERS = 1.7f
        private const val MIN_HEIGHT = 0.02f
        private const val DEFAULT_HORIZON = 0.48f
        private const val DEFAULT_CAMERA_HEIGHT = 1.5f

        /** אורך מוקד משוער של מצלמת טלפון ביחידות של גובה תמונה */
        private const val FOCAL = 1.1f

        private const val ROWS = 48
        private const val COLUMNS = 64

        fun default(): GroundModel = GroundModel(DEFAULT_HORIZON, DEFAULT_CAMERA_HEIGHT, false)

        fun fit(ctx: SceneContext): GroundModel {
            val mapper = ctx.mapper
            val ys = ArrayList<Float>()
            val inv = ArrayList<Float>()
            for (row in 0 until ROWS) {
                val py = (row + 0.5f) / ROWS
                val values = ArrayList<Float>()
                for (col in 0 until COLUMNS) {
                    val px = (col + 0.5f) / COLUMNS
                    if (!ctx.isWalkable(mapper.worldX(px), mapper.worldY(py))) continue
                    val d = ctx.analysis.depthAt(px, py)
                    if (d.isNaN() || d < 0.3f || d > 80f) continue
                    values.add(1f / d)
                }
                if (values.size < 8) continue
                values.sort()
                ys.add(py)
                inv.add(values[values.size / 2])
            }
            if (ys.size < 6) return default()

            // קו האופק לפי השורות הרחוקות ביותר של הרצפה: שם (y - אופק) קטן מאוד, ולכן הערכה
            // כמעט לא רגישה לשגיאות בקנה המידה של העומק. y - אופק = F * H / עומק.
            val farCount = max(3, ys.size / 3)
            val estimates = ArrayList<Float>()
            for (i in 0 until farCount) estimates.add(ys[i] - FOCAL * DEFAULT_CAMERA_HEIGHT * inv[i])
            estimates.sort()
            var horizon = estimates[estimates.size / 2]
            // הרצפה נמצאת מתחת לאופק, אף פעם לא מעליו
            horizon = min(horizon, ys[0] - 0.01f)
            horizon = max(horizon, -0.4f)
            return GroundModel(horizon, DEFAULT_CAMERA_HEIGHT, true)
        }
    }
}
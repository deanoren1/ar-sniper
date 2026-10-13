package com.arsniper.game.engine

import kotlin.math.abs
import kotlin.math.max

/**
 * מודל הקרקע של הסצנה, לפי הפרספקטיבה: על רצפה ישרה, גובה אדם בתמונה גדל ישר עם המרחק של הרגליים
 * מקו האופק: גובה = (1.7 / גובה המצלמה) * (y_רגליים - y_אופק), כשהכל ביחידות של גובה התמונה.
 * את קו האופק מוצאים מהעומק של פיקסלי הרצפה (1/עומק גדל לינארית עם y), ואם אין התאמה טובה: ברירת מחדל.
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
        private const val FOCAL = 1.0f

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

            // Theil-Sen: חציון השיפועים בין כל זוגות השורות
            val slopes = ArrayList<Float>()
            for (i in ys.indices) {
                for (j in i + 1 until ys.size) {
                    val dy = ys[j] - ys[i]
                    if (dy >= 0.05f) slopes.add((inv[j] - inv[i]) / dy)
                }
            }
            if (slopes.isEmpty()) return default()
            slopes.sort()
            val a = slopes[slopes.size / 2]
            if (a < 0.01f) return default()
            val offsets = ArrayList<Float>()
            for (i in ys.indices) offsets.add(inv[i] - a * ys[i])
            offsets.sort()
            val b = offsets[offsets.size / 2]
            val horizon = -b / a
            if (horizon < -0.4f || horizon > 0.75f) return default()

            // התאמה טובה: רוב השורות קרובות לקו
            val maxInv = inv.max()
            var good = 0
            for (i in ys.indices) {
                if (abs(inv[i] - (a * ys[i] + b)) <= 0.15f * maxInv) good++
            }
            if (good * 10 < ys.size * 6) return default()

            // גובה מצלמה: מהשיפוע (1/עומק = (y - אופק) / (F * H)), מעורבב עם ברירת המחדל כדי לרסן שגיאות
            val estimated = 1f / (a * FOCAL)
            val camera = ((estimated + DEFAULT_CAMERA_HEIGHT) / 2f).coerceIn(1.1f, 1.9f)
            return GroundModel(horizon, camera, true)
        }
    }
}

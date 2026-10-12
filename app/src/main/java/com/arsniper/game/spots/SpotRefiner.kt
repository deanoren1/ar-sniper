package com.arsniper.game.spots

import kotlin.math.max
import kotlin.math.min

/**
 * תמונת עזר לליטוש הקצוות: גווני אפור של התמונה ברזולוציה גבוהה מרשת הניתוח.
 * width/height = (רוחב/גובה הרשת) * scale.
 */
class GuideImage(
    val width: Int,
    val height: Int,
    val gray: ByteArray,
    val scale: Int
)

/**
 * מלטש את קצוות המסכה של מחבוא: לוקח את המסכה הגסה מהמודל ומצמיד את הקצוות שלה
 * לקצוות האמיתיים בתמונה, ומחזיר מסכה רכה (אלפא) ברזולוציה גבוהה, עם קצה מוחלק.
 * Kotlin טהור.
 */
object SpotRefiner {

    /** כמה תאי רשת מסביב לעצם נכללים בחישוב */
    private const val MARGIN_CELLS = 3

    /** מרחק (בתאי רשת) שבו אנחנו בטוחים בפנים/בחוץ: הליטוש לא משנה מעבר לזה */
    private const val TRUST_CELLS = 2

    /** רדיוס ה-guided filter בפיקסלים של התמונה הגבוהה (כ-1.5 תאי רשת ב-scale=3) */
    private const val GUIDE_RADIUS = 5

    /** קטן = הקצה נצמד חזק יותר לקצוות התמונה */
    private const val GUIDE_EPS = 0.001f

    /** חדות המעבר בקצה (גדול = חד יותר) */
    private const val SHARPEN = 5f

    fun refine(spot: HidingSpot, guide: GuideImage): HidingSpot {
        val s = guide.scale
        val gridWidth = spot.gridWidth
        val gridHeight = spot.gridHeight

        // חלון העבודה ברשת הגסה, עם שוליים
        val gx0 = max(0, spot.cellX - MARGIN_CELLS)
        val gy0 = max(0, spot.cellY - MARGIN_CELLS)
        val gx1 = min(gridWidth, spot.cellX + spot.maskWidth + MARGIN_CELLS)
        val gy1 = min(gridHeight, spot.cellY + spot.maskHeight + MARGIN_CELLS)
        val cw = gx1 - gx0
        val ch = gy1 - gy0

        val coarse = BooleanArray(cw * ch)
        for (ly in 0 until spot.maskHeight) {
            for (lx in 0 until spot.maskWidth) {
                if (spot.mask[ly * spot.maskWidth + lx]) {
                    coarse[(spot.cellY + ly - gy0) * cw + (spot.cellX + lx - gx0)] = true
                }
            }
        }
        val sureInside = MaskTools.erode(coarse, cw, ch, TRUST_CELLS)
        val maybeInside = MaskTools.dilate(coarse, cw, ch, TRUST_CELLS)

        // החלון ברזולוציה הגבוהה
        val fw = cw * s
        val fh = ch * s
        val fx0 = gx0 * s
        val fy0 = gy0 * s

        val prior = FloatArray(fw * fh)
        val guideValues = FloatArray(fw * fh)
        for (y in 0 until fh) {
            val coarseRow = (y / s) * cw
            val guideRow = (fy0 + y) * guide.width + fx0
            for (x in 0 until fw) {
                prior[y * fw + x] = if (coarse[coarseRow + x / s]) 1f else 0f
                guideValues[y * fw + x] = (guide.gray[guideRow + x].toInt() and 0xFF) / 255f
            }
        }

        val softPrior = MaskTools.boxFilter(prior, fw, fh, s)
        val filtered = MaskTools.guidedFilter(guideValues, softPrior, fw, fh, GUIDE_RADIUS, GUIDE_EPS)

        val alpha = ByteArray(fw * fh)
        for (y in 0 until fh) {
            val coarseRow = (y / s) * cw
            for (x in 0 until fw) {
                val cell = coarseRow + x / s
                val value = when {
                    sureInside[cell] -> 1f
                    !maybeInside[cell] -> 0f
                    else -> ((filtered[y * fw + x] - 0.5f) * SHARPEN + 0.5f).coerceIn(0f, 1f)
                }
                alpha[y * fw + x] = (value * 255f + 0.5f).toInt().toByte()
            }
        }

        return spot.copy(
            alpha = alpha,
            alphaX = fx0,
            alphaY = fy0,
            alphaWidth = fw,
            alphaHeight = fh,
            alphaScale = s
        )
    }
}
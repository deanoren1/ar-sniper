package com.arsniper.ui.game

import android.graphics.Bitmap
import com.arsniper.game.spots.MaskTools
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * פילטר סגנון "קריקטורה": מחליק משטחים תוך שמירה על קצוות (guided filter),
 * מצמצם את מספר הצבעים, מגביר רוויה ומוסיף קווי מתאר כהים.
 * כך התמונה והדמויות הקריקטוריות נראות כמו עולם אחד.
 */
object StyleFilter {

    /** רוחב העבודה: מספיק לטלפון, וקצר מספיק כדי שהחישוב לא יימשך יותר מדי */
    private const val WORK_WIDTH = 960

    private const val SMOOTH_RADIUS = 4
    private const val SMOOTH_EPS = 0.02f

    /** מספר רמות הצבע בכל ערוץ */
    private const val LEVELS = 7

    private const val SATURATION = 1.3f

    /** חוזק קווי המתאר (0..1) */
    private const val OUTLINE_STRENGTH = 0.7f

    fun cartoon(source: Bitmap): Bitmap {
        val width = min(WORK_WIDTH, source.width)
        val height = max(1, (width.toLong() * source.height / source.width).toInt())
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== source) scaled.recycle()

        val n = width * height
        val red = FloatArray(n)
        val green = FloatArray(n)
        val blue = FloatArray(n)
        val gray = FloatArray(n)
        for (i in 0 until n) {
            val c = pixels[i]
            red[i] = ((c shr 16) and 0xFF) / 255f
            green[i] = ((c shr 8) and 0xFF) / 255f
            blue[i] = (c and 0xFF) / 255f
            gray[i] = 0.299f * red[i] + 0.587f * green[i] + 0.114f * blue[i]
        }

        // החלקה שמכבדת קצוות: כל ערוץ מוחלק לפי הקצוות של תמונת האפור
        val smoothRed = MaskTools.guidedFilter(gray, red, width, height, SMOOTH_RADIUS, SMOOTH_EPS)
        val smoothGreen = MaskTools.guidedFilter(gray, green, width, height, SMOOTH_RADIUS, SMOOTH_EPS)
        val smoothBlue = MaskTools.guidedFilter(gray, blue, width, height, SMOOTH_RADIUS, SMOOTH_EPS)
        val smoothGray = MaskTools.guidedFilter(gray, gray, width, height, SMOOTH_RADIUS, SMOOTH_EPS)

        val out = IntArray(n)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x

                // קווי מתאר: Sobel על האפור המוחלק
                val edge = if (x > 0 && y > 0 && x < width - 1 && y < height - 1) {
                    val tl = smoothGray[i - width - 1]
                    val t = smoothGray[i - width]
                    val tr = smoothGray[i - width + 1]
                    val l = smoothGray[i - 1]
                    val r = smoothGray[i + 1]
                    val bl = smoothGray[i + width - 1]
                    val b = smoothGray[i + width]
                    val br = smoothGray[i + width + 1]
                    val gx = (tr + 2 * r + br) - (tl + 2 * l + bl)
                    val gy = (bl + 2 * b + br) - (tl + 2 * t + tr)
                    sqrt(gx * gx + gy * gy)
                } else {
                    0f
                }
                val outline = smoothStep(0.10f, 0.35f, edge) * OUTLINE_STRENGTH

                // רוויה, צמצום צבעים, ואז קווי מתאר
                val luma = 0.299f * smoothRed[i] + 0.587f * smoothGreen[i] + 0.114f * smoothBlue[i]
                var rr = posterize(luma + (smoothRed[i] - luma) * SATURATION)
                var gg = posterize(luma + (smoothGreen[i] - luma) * SATURATION)
                var bb = posterize(luma + (smoothBlue[i] - luma) * SATURATION)
                val keep = 1f - outline
                rr *= keep
                gg *= keep
                bb *= keep

                out[i] = (0xFF shl 24) or
                    (to255(rr) shl 16) or
                    (to255(gg) shl 8) or
                    to255(bb)
            }
        }
        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun posterize(value: Float): Float {
        val v = value.coerceIn(0f, 1f)
        return (v * (LEVELS - 1)).roundToInt() / (LEVELS - 1).toFloat()
    }

    private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun to255(value: Float): Int = (value.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
}

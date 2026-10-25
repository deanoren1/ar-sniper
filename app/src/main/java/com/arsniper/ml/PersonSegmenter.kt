package com.arsniper.ml

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import java.nio.ByteOrder
import kotlin.math.min

/**
 * מזהה אנשים בתמונה בעזרת MediaPipe (DeepLab-v3).
 * המודל עובד ברזולוציה נמוכה (257x257), ולכן מריצים אותו גם על כל התמונה
 * וגם על חלקים חופפים בשני גדלים, כדי לתפוס גם אנשים קטנים ורחוקים.
 *
 * במקום מסכה קטגורית (שמתנפחת לריבועים של 257 פיקסלים) משתמשים בהסתברות "אדם" של כל פיקסל,
 * מגדילים אותה באינטרפולציה חלקה, ולוקחים את המקסימום מכל המעברים. כך הקצוות רכים ומדויקים יותר.
 * אם המודל לא מחזיר הסתברויות, חוזרים למסכה הקטגורית כמו קודם.
 * בסוף נמחקים כתמים קטנים ורחוקים מהאנשים (זיהוי שגוי, כמו נעל); אפשר להוסיף אותם בחזרה עם ADD.
 */
class PersonSegmenter(context: Context) {

    private val segmenter: ImageSegmenter

    init {
        val options = ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(
                BaseOptions.builder().setModelAssetPath(MODEL_PATH).build()
            )
            .setRunningMode(RunningMode.IMAGE)
            .setOutputCategoryMask(true)
            .setOutputConfidenceMasks(true)
            .build()
        segmenter = ImageSegmenter.createFromOptions(context, options)
    }

    fun detectPeople(source: Bitmap): PersonMask {
        val width = source.width
        val height = source.height

        // רשת הסתברויות ברזולוציה מופחתת (עד כ-1024 בצלע הארוכה), כדי לא לבזבז זיכרון
        val k = ((maxOf(width, height) + WORK_SIDE - 1) / WORK_SIDE).coerceAtLeast(1)
        val ww = (width + k - 1) / k
        val wh = (height + k - 1) / k
        val probability = FloatArray(ww * wh)

        // מעבר על כל התמונה
        mergeTile(source, 0, 0, width, height, probability, ww, wh, k)

        // מעברים על חלקים ריבועיים חופפים, בכמה גדלים
        for (fraction in TILE_FRACTIONS) {
            val side = (minOf(width, height) * fraction).toInt().coerceAtLeast(64)
            val stride = (side * STRIDE_FRACTION).toInt().coerceAtLeast(1)

            var y = 0
            while (true) {
                val tileTop = minOf(y, height - side)
                var x = 0
                while (true) {
                    val tileLeft = minOf(x, width - side)
                    mergeTile(source, tileLeft, tileTop, side, side, probability, ww, wh, k)
                    if (x + side >= width) break
                    x += stride
                }
                if (y + side >= height) break
                y += stride
            }
        }

        // כתמים קטנים ורחוקים מהאנשים (למשל נעל על הרצפה) הם בדרך כלל זיהוי שגוי: מוחקים אותם
        return PersonRefiner.dropStray(PersonMask(width, height, threshold(probability, ww, wh, k, width, height)))
    }

    fun close() {
        segmenter.close()
    }

    /** מריץ זיהוי על חלק מהתמונה ומעדכן את ההסתברות הכללית (מקסימום) */
    private fun mergeTile(
        source: Bitmap,
        left: Int,
        top: Int,
        tileWidth: Int,
        tileHeight: Int,
        probability: FloatArray,
        ww: Int,
        wh: Int,
        k: Int
    ) {
        val tile = Bitmap.createBitmap(source, left, top, tileWidth, tileHeight)
        try {
            val result = segmenter.segment(BitmapImageBuilder(tile).build())

            var personMap: MPImage? = null
            var floats: java.nio.FloatBuffer? = null
            try {
                val list = result.confidenceMasks().orElse(null)
                if (list != null && list.size > PERSON_CLASS) {
                    val candidate = list[PERSON_CLASS]
                    floats = ByteBufferExtractor.extract(candidate).duplicate()
                        .order(ByteOrder.nativeOrder()).asFloatBuffer()
                    personMap = candidate
                }
            } catch (e: Throwable) {
                personMap = null
                floats = null
            }

            // בלי הסתברויות: חוזרים למסכה הקטגורית
            val category: MPImage? = if (personMap == null) result.categoryMask().get() else null
            val categoryBuffer = if (category != null) ByteBufferExtractor.extract(category) else null
            val mw = (personMap ?: category!!).width
            val mh = (personMap ?: category!!).height

            // תאי הרשת שנופלים בתוך החלק
            val wx0 = (left + k - 1) / k
            val wy0 = (top + k - 1) / k
            val wx1 = min(ww - 1, (left + tileWidth - 1) / k)
            val wy1 = min(wh - 1, (top + tileHeight - 1) / k)
            for (wy in wy0..wy1) {
                val v = ((wy * k + k / 2f - top) / tileHeight * mh - 0.5f).coerceIn(0f, mh - 1f)
                val v0 = v.toInt()
                val v1 = min(mh - 1, v0 + 1)
                val fv = v - v0
                for (wx in wx0..wx1) {
                    val u = ((wx * k + k / 2f - left) / tileWidth * mw - 0.5f).coerceIn(0f, mw - 1f)
                    val u0 = u.toInt()
                    val u1 = min(mw - 1, u0 + 1)
                    val fu = u - u0
                    val value: Float = if (floats != null) {
                        val top2 = floats.get(v0 * mw + u0) * (1f - fu) + floats.get(v0 * mw + u1) * fu
                        val bottom2 = floats.get(v1 * mw + u0) * (1f - fu) + floats.get(v1 * mw + u1) * fu
                        top2 * (1f - fv) + bottom2 * fv
                    } else {
                        val c = categoryBuffer!!.get(
                            (v + 0.5f).toInt().coerceAtMost(mh - 1) * mw + (u + 0.5f).toInt().coerceAtMost(mw - 1)
                        ).toInt() and 0xFF
                        if (c == PERSON_CLASS) 1f else 0f
                    }
                    val index = wy * ww + wx
                    if (value > probability[index]) probability[index] = value
                }
            }
        } finally {
            if (tile !== source) tile.recycle()
        }
    }

    /** מגדיל את ההסתברות לרזולוציית התמונה באינטרפולציה לינארית; אדם = מעל הסף */
    private fun threshold(
        probability: FloatArray,
        ww: Int,
        wh: Int,
        k: Int,
        width: Int,
        height: Int
    ): BooleanArray {
        val out = BooleanArray(width * height)
        for (y in 0 until height) {
            val fy = ((y + 0.5f) / k - 0.5f).coerceIn(0f, wh - 1f)
            val y0 = fy.toInt()
            val y1 = min(wh - 1, y0 + 1)
            val wy = fy - y0
            for (x in 0 until width) {
                val fx = ((x + 0.5f) / k - 0.5f).coerceIn(0f, ww - 1f)
                val x0 = fx.toInt()
                val x1 = min(ww - 1, x0 + 1)
                val wx = fx - x0
                val top = probability[y0 * ww + x0] * (1f - wx) + probability[y0 * ww + x1] * wx
                val bottom = probability[y1 * ww + x0] * (1f - wx) + probability[y1 * ww + x1] * wx
                out[y * width + x] = top * (1f - wy) + bottom * wy >= PERSON_THRESHOLD
            }
        }
        return out
    }

    companion object {
        private const val MODEL_PATH = "deeplab_v3.tflite"

        /** מספר הקטגוריה "אדם" במודל DeepLab-v3 (תיוג PASCAL VOC) */
        private const val PERSON_CLASS = 15

        /** גודל החלקים, כחלק מהצלע הקצרה של התמונה */
        private val TILE_FRACTIONS = floatArrayOf(0.6f, 0.3f)

        /** מרחק בין חלקים סמוכים, כחלק מגודל החלק (נמוך = יותר חפיפה) */
        private const val STRIDE_FRACTION = 0.7f

        /** הצלע הארוכה של רשת ההסתברויות */
        private const val WORK_SIDE = 1024

        /** מעל הסתברות זו הפיקסל נחשב אדם */
        private const val PERSON_THRESHOLD = 0.5f
    }
}

package com.arsniper.ml

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter

/**
 * מזהה אנשים בתמונה בעזרת MediaPipe (DeepLab-v3).
 * המודל עובד ברזולוציה נמוכה (257x257), ולכן מריצים אותו גם על כל התמונה
 * וגם על חלקים חופפים שלה, כדי לתפוס גם אנשים קטנים ורחוקים.
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
            .setOutputConfidenceMasks(false)
            .build()
        segmenter = ImageSegmenter.createFromOptions(context, options)
    }

    fun detectPeople(source: Bitmap): PersonMask {
        val width = source.width
        val height = source.height
        val data = BooleanArray(width * height)

        // מעבר על כל התמונה
        mergeTile(source, 0, 0, width, height, data)

        // מעבר על חלקים ריבועיים חופפים (חפיפה של חצי)
        val side = (minOf(width, height) * 0.6f).toInt().coerceAtLeast(64)
        val stride = (side / 2).coerceAtLeast(1)

        var y = 0
        while (true) {
            val tileTop = minOf(y, height - side)
            var x = 0
            while (true) {
                val tileLeft = minOf(x, width - side)
                mergeTile(source, tileLeft, tileTop, side, side, data)
                if (x + side >= width) break
                x += stride
            }
            if (y + side >= height) break
            y += stride
        }

        return PersonMask(width, height, data)
    }

    fun close() {
        segmenter.close()
    }

    /** מריץ זיהוי על חלק מהתמונה ומוסיף את האנשים שנמצאו למסכה הכללית */
    private fun mergeTile(
        source: Bitmap,
        left: Int,
        top: Int,
        tileWidth: Int,
        tileHeight: Int,
        out: BooleanArray
    ) {
        val tile = Bitmap.createBitmap(source, left, top, tileWidth, tileHeight)
        val result = segmenter.segment(BitmapImageBuilder(tile).build())
        val maskImage = result.categoryMask().get()
        val buffer = ByteBufferExtractor.extract(maskImage)
        val maskWidth = maskImage.width
        val maskHeight = maskImage.height

        for (ty in 0 until tileHeight) {
            val my = ty * maskHeight / tileHeight
            for (tx in 0 until tileWidth) {
                val mx = tx * maskWidth / tileWidth
                val category = buffer.get(my * maskWidth + mx).toInt() and 0xFF
                if (category == PERSON_CLASS) {
                    out[(top + ty) * source.width + (left + tx)] = true
                }
            }
        }

        if (tile !== source) tile.recycle()
    }

    companion object {
        private const val MODEL_PATH = "deeplab_v3.tflite"

        /** מספר הקטגוריה "אדם" במודל DeepLab-v3 (תיוג PASCAL VOC) */
        private const val PERSON_CLASS = 15
    }
}

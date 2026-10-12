package com.arsniper.ml

import android.graphics.Bitmap
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import java.io.File

/**
 * מריץ את מודל TIPSv2 (LiteRT, GPU): מקבל תמונה ומחזיר סיווג של כל פיקסל ל-150 קטגוריות
 * (ADE20K) יחד עם מפת עומק.
 *
 * קלט: [1, 3, 448, 448] RGB בטווח 0..1.
 * פלטים לפי הסדר: עומק [448x448] במטרים, נורמלים [3x448x448], סיווג [150x256x256].
 */
class SceneSegmenter(modelFile: File) {

    private val model: CompiledModel = CompiledModel.create(
        modelFile.absolutePath,
        CompiledModel.Options(Accelerator.GPU)
    )
    private val inputBuffers = model.createInputBuffers()
    private val outputBuffers = model.createOutputBuffers()

    fun analyze(photo: Bitmap): SceneAnalysis {
        val scaled = Bitmap.createScaledBitmap(photo, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_PLANE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (scaled !== photo) scaled.recycle()

        val input = FloatArray(3 * INPUT_PLANE)
        for (i in 0 until INPUT_PLANE) {
            val color = pixels[i]
            input[i] = ((color shr 16) and 0xFF) / 255f
            input[INPUT_PLANE + i] = ((color shr 8) and 0xFF) / 255f
            input[2 * INPUT_PLANE + i] = (color and 0xFF) / 255f
        }

        inputBuffers[0].writeFloat(input)
        model.run(inputBuffers, outputBuffers)

        val depth = outputBuffers[0].readFloat()
        val segmentation = outputBuffers[2].readFloat()

        // לכל פיקסל: הקטגוריה עם הערך הגבוה ביותר
        val segmentationPlane = SEG_SIZE * SEG_SIZE
        val labels = ByteArray(segmentationPlane)
        for (p in 0 until segmentationPlane) {
            var best = 0
            var bestValue = segmentation[p]
            for (c in 1 until CLASS_COUNT) {
                val value = segmentation[c * segmentationPlane + p]
                if (value > bestValue) {
                    bestValue = value
                    best = c
                }
            }
            labels[p] = best.toByte()
        }

        return SceneAnalysis(SEG_SIZE, SEG_SIZE, labels, INPUT_SIZE, INPUT_SIZE, depth)
    }

    fun close() {
        try {
            model.close()
        } catch (e: Throwable) {
            // אין מה לעשות בכשל סגירה
        }
    }

    companion object {
        private const val INPUT_SIZE = 448
        private const val INPUT_PLANE = INPUT_SIZE * INPUT_SIZE
        private const val SEG_SIZE = 256
        private const val CLASS_COUNT = 150
    }
}

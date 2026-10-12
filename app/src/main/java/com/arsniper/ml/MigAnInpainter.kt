package com.arsniper.ml

import android.content.Context
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel

/**
 * מעטפת למודל MI-GAN (LiteRT): מקבל קלט בגודל 512x512 (תמונה ומסכה)
 * ומחזיר תמונה שבה האזור המסומן מולא.
 *
 * קלט: [1, 4, 512, 512] = (mask - 0.5) ואחריו rgb * mask, כש-mask: 1 = לשמור, 0 = למחוק,
 *      ו-rgb בטווח [-1, 1].
 * פלט: [1, 3, 512, 512] בטווח [-1, 1].
 */
class MigAnInpainter(context: Context) {

    /** האם המודל רץ על ה-GPU (אחרת על ה-CPU) */
    var usesGpu: Boolean = false
        private set

    private val model: CompiledModel = createModel(context)
    private val inputBuffers = model.createInputBuffers()
    private val outputBuffers = model.createOutputBuffers()

    private fun createModel(context: Context): CompiledModel {
        return try {
            val gpuModel = CompiledModel.create(
                context.assets,
                MODEL_PATH,
                CompiledModel.Options(Accelerator.GPU),
                null
            )
            usesGpu = true
            gpuModel
        } catch (e: Throwable) {
            Log.w(TAG, "GPU unavailable, falling back to CPU", e)
            CompiledModel.create(
                context.assets,
                MODEL_PATH,
                CompiledModel.Options(Accelerator.CPU),
                null
            )
        }
    }

    fun run(input: FloatArray): FloatArray {
        inputBuffers[0].writeFloat(input)
        model.run(inputBuffers, outputBuffers)
        return outputBuffers[0].readFloat()
    }

    fun close() {
        try {
            model.close()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to close model", e)
        }
    }

    companion object {
        private const val TAG = "MigAnInpainter"
        private const val MODEL_PATH = "migan_fp16.tflite"
    }
}

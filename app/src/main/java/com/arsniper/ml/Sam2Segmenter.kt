package com.arsniper.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * SAM 2.1 Hiera-Tiny: סימון עצם בנגיעה, בלי לדעת מראש מה הוא (קורקינט, תיק, ארגז...).
 * המקודד (פעם אחת לתמונה) מנסה GPU ועובר ל-CPU אם התוצאה פגומה.
 * המפענח רץ על CPU: ב-GPU של Mali הוא מחשב תוצאה שגויה.
 */
class Sam2Segmenter(files: ModelStore.SamFiles) : AutoCloseable {

    companion object {
        private const val TAG = "SAM2"
        private const val SIZE = 1024
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
        private const val IE = 256 * 64 * 64
        private const val F0 = 32 * 256 * 256
        private const val F1 = 64 * 128 * 128
        private const val TWO_PI = (2.0 * Math.PI).toFloat()
        const val MASK = 256 * 256
    }

    private var encoder: CompiledModel
    private val decoder: CompiledModel
    private val encoderPath = files.encoder.absolutePath
    private val gaussian: FloatArray
    private val pointEmbed1: FloatArray
    private val notAPoint: FloatArray

    var encoderAccelerator = "GPU"; private set

    private var cachedIe = FloatArray(0)
    private var cachedF0 = FloatArray(0)
    private var cachedF1 = FloatArray(0)

    init {
        encoder = try {
            CompiledModel.create(encoderPath, CompiledModel.Options(Accelerator.GPU))
        } catch (e: Exception) {
            Log.w(TAG, "encoder GPU failed, using CPU: $e")
            encoderAccelerator = "CPU"
            CompiledModel.create(encoderPath, CompiledModel.Options(Accelerator.CPU))
        }
        decoder = CompiledModel.create(files.decoder.absolutePath, CompiledModel.Options(Accelerator.CPU))
        val bytes = files.prompt.readBytes()
        val fb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val all = FloatArray(768).also { fb.get(it) }
        gaussian = all.copyOfRange(0, 256)
        pointEmbed1 = all.copyOfRange(256, 512)
        notAPoint = all.copyOfRange(512, 768)
    }

    private fun valid(flat: FloatArray): Boolean {
        if (flat.size < IE + F0 + F1) return false
        var nonZero = 0
        var i = 0
        while (i < flat.size) {
            val v = flat[i]
            if (v.isNaN() || v.isInfinite()) return false
            if (v != 0f) nonZero++
            i += 97
        }
        return nonZero > 50
    }

    private fun runEncoder(input: FloatArray): FloatArray {
        val inBufs = encoder.createInputBuffers()
        val outBufs = encoder.createOutputBuffers()
        try {
            inBufs[0].writeFloat(input)
            encoder.run(inBufs, outBufs)
            return outBufs[0].readFloat()
        } finally {
            inBufs.forEach { it.close() }
            outBufs.forEach { it.close() }
        }
    }

    /** מקודד את התמונה (פעם אחת). מחזיר את משך הזמן במילישניות */
    fun encode(src: Bitmap): Long {
        val t = System.nanoTime()
        val canvasBmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        Canvas(canvasBmp).drawBitmap(src, null, RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(SIZE * SIZE)
        canvasBmp.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        canvasBmp.recycle()
        val plane = SIZE * SIZE
        val input = FloatArray(3 * plane)
        for (i in pixels.indices) {
            val p = pixels[i]
            input[i] = (((p shr 16) and 0xFF) / 255f - MEAN[0]) / STD[0]
            input[plane + i] = (((p shr 8) and 0xFF) / 255f - MEAN[1]) / STD[1]
            input[2 * plane + i] = ((p and 0xFF) / 255f - MEAN[2]) / STD[2]
        }

        var flat: FloatArray? = null
        try {
            flat = runEncoder(input)
            if (!valid(flat)) flat = null
        } catch (e: Exception) {
            Log.w(TAG, "encoder run failed on $encoderAccelerator: $e")
        }
        if (flat == null && encoderAccelerator == "GPU") {
            Log.w(TAG, "encoder GPU output invalid, switching to CPU")
            try { encoder.close() } catch (_: Exception) {}
            encoder = CompiledModel.create(encoderPath, CompiledModel.Options(Accelerator.CPU))
            encoderAccelerator = "CPU"
            flat = runEncoder(input)
            if (!valid(flat)) flat = null
        }
        if (flat == null) error("SAM encoder produced no valid output")

        cachedIe = flat.copyOfRange(0, IE)
        cachedF0 = flat.copyOfRange(IE, IE + F0)
        cachedF1 = flat.copyOfRange(IE + F0, IE + F0 + F1)
        return (System.nanoTime() - t) / 1_000_000
    }

    /**
     * נקודה חיובית בקואורדינטות 0..1 של התמונה.
     * מחזיר 3 מסכות (256x256 כל אחת, לוגיטים): 0 = כל העצם, 1-2 = חלקים/גרסאות אחרות.
     */
    fun segment(nx: Float, ny: Float): Array<FloatArray> {
        val modelX = nx.coerceIn(0f, 1f) * SIZE
        val modelY = ny.coerceIn(0f, 1f) * SIZE
        val sparse = FloatArray(512)
        val ccx = 2f * ((modelX + 0.5f) / SIZE) - 1f
        val ccy = 2f * ((modelY + 0.5f) / SIZE) - 1f
        for (k in 0 until 128) {
            val proj = TWO_PI * (ccx * gaussian[k] + ccy * gaussian[128 + k])
            sparse[k] = sin(proj) + pointEmbed1[k]
            sparse[128 + k] = cos(proj) + pointEmbed1[128 + k]
        }
        for (k in 0 until 256) sparse[256 + k] = notAPoint[k]

        val flatIn = FloatArray(IE + 512 + F0 + F1)
        System.arraycopy(cachedIe, 0, flatIn, 0, IE)
        System.arraycopy(sparse, 0, flatIn, IE, 512)
        System.arraycopy(cachedF0, 0, flatIn, IE + 512, F0)
        System.arraycopy(cachedF1, 0, flatIn, IE + 512 + F0, F1)

        val inBufs = decoder.createInputBuffers()
        val outBufs = decoder.createOutputBuffers()
        try {
            inBufs[0].writeFloat(flatIn)
            decoder.run(inBufs, outBufs)
            val masks = outBufs[0].readFloat()
            return Array(3) { m -> masks.copyOfRange(m * MASK, (m + 1) * MASK) }
        } finally {
            inBufs.forEach { it.close() }
            outBufs.forEach { it.close() }
        }
    }

    override fun close() {
        try { encoder.close() } catch (_: Exception) {}
        try { decoder.close() } catch (_: Exception) {}
    }
}
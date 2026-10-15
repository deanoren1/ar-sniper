package com.arsniper.ml

import android.content.Context
import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * מסיר אנשים מתמונה לפי מסכה: מרחיב את המסכה, מפצל לכתמים נפרדים,
 * וממלא כל כתם בעזרת MI-GAN על חלק ריבועי מהתמונה (עם הקשר מסביב).
 */
class PersonRemover(context: Context) {

    private val appContext: Context = context.applicationContext ?: context
    private val inpainter = MigAnInpainter(context)

    /** מזהה האנשים של המעבר השני (נטען רק אם צריך) */
    private var segmenter: PersonSegmenter? = null

    /** כמה להרחיב את המסכה (כחלק מגודל האדם): מסכה שדויקה ב-SAM צריכה פחות */
    private var growFraction = GROW_FRACTION

    /** האם ההסרה רצה על ה-GPU */
    val usesGpu: Boolean get() = inpainter.usesGpu

    fun remove(
        source: Bitmap,
        mask: PersonMask,
        onProgress: (done: Int, total: Int) -> Unit
    ): Bitmap {
        val width = source.width
        val height = source.height
        if (mask.coverage() == 0f) return source
        growFraction = if (mask.precise) PRECISE_GROW_FRACTION else GROW_FRACTION

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // מעבר ראשון: מסירים את מה שסומן
        onProgress(0, 0)
        var done = erase(pixels, mask.data, width, height, 0, onProgress)

        // מעברים נוספים: מחפשים שאריות של אנשים ליד המקום שבו עמדו, ומסירים אותן
        val near = dilate(mask.data, width, height, max(8, min(width, height) / NEAR_DIVISOR))
        for (round in 0 until MAX_REFINE_ROUNDS) {
            val residual = findResidual(pixels, near, width, height) ?: break
            done += erase(pixels, residual, width, height, done, onProgress)
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    fun close() {
        inpainter.close()
        segmenter?.close()
        segmenter = null
    }

    /**
     * מרחיב את המסכה, מפצל לכתמים וממלא כל כתם. מחזיר כמה כתמים טופלו.
     * done = כמה כתמים כבר טופלו במעברים קודמים (להצגת התקדמות).
     */
    private fun erase(
        pixels: IntArray,
        rawMask: BooleanArray,
        width: Int,
        height: Int,
        done: Int,
        onProgress: (done: Int, total: Int) -> Unit
    ): Int {
        val remaining = expandMask(rawMask, width, height)

        // כתמים נפרדים (כל אדם בנפרד), מהגדול לקטן
        val labels = IntArray(width * height)
        val components = findComponents(remaining, labels, width, height)
        val targets = components
            .filter { it.area >= MIN_AREA }
            .sortedByDescending { it.area }
            .take(MAX_COMPONENTS)

        // כתמים זעירים או מעבר למגבלה נשארים בתמונה כמו שהם
        val isTarget = BooleanArray(components.size + 1)
        for (component in targets) isTarget[component.label] = true
        for (i in remaining.indices) {
            if (remaining[i] && !isTarget[labels[i]]) remaining[i] = false
        }

        val total = done + targets.size
        onProgress(done, total)
        targets.forEachIndexed { index, component ->
            inpaintComponent(pixels, remaining, labels, width, height, component)
            onProgress(done + index + 1, total)
        }
        return targets.size
    }

    /**
     * הרחבת המסכה לפי גודל כל אדם: המודל מזהה ברזולוציה נמוכה ולכן הקצוות (ידיים, שיער, רגליים, צל)
     * נחתכים, והחיתוך גדול יותר באדם גדול. אדם גדול מורחב יותר, אדם קטן פחות.
     */
    private fun expandMask(raw: BooleanArray, width: Int, height: Int): BooleanArray {
        val minRadius = max(4, min(width, height) / 150)
        val maxRadius = max(minRadius, min(width, height) / MAX_GROW_DIVISOR)

        // קודם הרחבה קטנה, כדי לאחד שברים של אותו אדם לכתם אחד
        val coarse = dilate(raw, width, height, minRadius)
        val labels = IntArray(width * height)
        val components = findComponents(coarse, labels, width, height)

        val out = BooleanArray(width * height)
        for (component in components) {
            val bboxWidth = component.maxX - component.minX + 1
            val bboxHeight = component.maxY - component.minY + 1
            val radius = (growFraction * max(bboxWidth, bboxHeight)).roundToInt()
                .coerceIn(minRadius, maxRadius)

            val left = max(0, component.minX - radius)
            val top = max(0, component.minY - radius)
            val right = min(width - 1, component.maxX + radius)
            val bottom = min(height - 1, component.maxY + radius)
            val localWidth = right - left + 1
            val localHeight = bottom - top + 1

            val local = BooleanArray(localWidth * localHeight)
            for (y in component.minY..component.maxY) {
                for (x in component.minX..component.maxX) {
                    val index = y * width + x
                    if (raw[index] && labels[index] == component.label) {
                        local[(y - top) * localWidth + (x - left)] = true
                    }
                }
            }
            val grown = dilate(local, localWidth, localHeight, radius)
            for (y in 0 until localHeight) {
                for (x in 0 until localWidth) {
                    if (grown[y * localWidth + x]) out[(top + y) * width + (left + x)] = true
                }
            }
        }
        return out
    }

    /**
     * מחפש שאריות של אנשים בתמונה אחרי ההסרה: מריץ שוב את הזיהוי על האזור שבו עמדו האנשים,
     * ומחזיר רק מה שנמצא ליד המסכה המקורית (כך שדברים שהמשתמש מחק מהסימון, כמו כיסא, לא יוסרו).
     * מחזיר null אם לא נשאר כלום, או אם הזיהוי נכשל.
     */
    private fun findResidual(pixels: IntArray, near: BooleanArray, width: Int, height: Int): BooleanArray? {
        try {
            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (near[y * width + x]) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
            if (maxX < 0) return null

            // חלק מהתמונה שמכיל את האזור, לא קטן מדי בשביל הזיהוי
            val minSide = min(MIN_DETECT_SIDE, min(width, height))
            var cropWidth = max(maxX - minX + 1, minSide)
            var cropHeight = max(maxY - minY + 1, minSide)
            cropWidth = min(cropWidth, width)
            cropHeight = min(cropHeight, height)
            val left = ((minX + maxX) / 2 - cropWidth / 2).coerceIn(0, width - cropWidth)
            val top = ((minY + maxY) / 2 - cropHeight / 2).coerceIn(0, height - cropHeight)

            val crop = Bitmap.createBitmap(
                pixels, top * width + left, width, cropWidth, cropHeight, Bitmap.Config.ARGB_8888
            )
            val detector = segmenter ?: PersonSegmenter(appContext).also { segmenter = it }
            val found = detector.detectPeople(crop)
            crop.recycle()

            val residual = BooleanArray(width * height)
            var count = 0
            for (y in 0 until cropHeight) {
                for (x in 0 until cropWidth) {
                    val index = (top + y) * width + (left + x)
                    if (found.data[y * cropWidth + x] && near[index]) {
                        residual[index] = true
                        count++
                    }
                }
            }
            return if (count >= max(MIN_RESIDUAL, width * height / RESIDUAL_DIVISOR)) residual else null
        } catch (e: Exception) {
            return null
        }
    }

    private class Component(val label: Int) {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        var area = 0
    }

    /** מוצא כתמים מחוברים (4 שכנים) ומסמן כל פיקסל במספר הכתם שלו */
    private fun findComponents(
        mask: BooleanArray,
        labels: IntArray,
        width: Int,
        height: Int
    ): List<Component> {
        val components = ArrayList<Component>()
        val stack = IntArray(width * height)

        for (start in mask.indices) {
            if (!mask[start] || labels[start] != 0) continue

            val component = Component(components.size + 1)
            components.add(component)

            var stackSize = 0
            stack[stackSize++] = start
            labels[start] = component.label

            while (stackSize > 0) {
                val index = stack[--stackSize]
                val x = index % width
                val y = index / width

                component.area += 1
                if (x < component.minX) component.minX = x
                if (x > component.maxX) component.maxX = x
                if (y < component.minY) component.minY = y
                if (y > component.maxY) component.maxY = y

                if (x > 0) {
                    val n = index - 1
                    if (mask[n] && labels[n] == 0) {
                        labels[n] = component.label
                        stack[stackSize++] = n
                    }
                }
                if (x < width - 1) {
                    val n = index + 1
                    if (mask[n] && labels[n] == 0) {
                        labels[n] = component.label
                        stack[stackSize++] = n
                    }
                }
                if (y > 0) {
                    val n = index - width
                    if (mask[n] && labels[n] == 0) {
                        labels[n] = component.label
                        stack[stackSize++] = n
                    }
                }
                if (y < height - 1) {
                    val n = index + width
                    if (mask[n] && labels[n] == 0) {
                        labels[n] = component.label
                        stack[stackSize++] = n
                    }
                }
            }
        }
        return components
    }

    /** ממלא כתם אחד: חותך, מריץ את המודל, ומחזיר לתמונה רק את הפיקסלים של הכתם */
    private fun inpaintComponent(
        pixels: IntArray,
        remaining: BooleanArray,
        labels: IntArray,
        width: Int,
        height: Int,
        component: Component
    ) {
        val bboxWidth = component.maxX - component.minX + 1
        val bboxHeight = component.maxY - component.minY + 1
        val maxSide = min(width, height)
        val side = (max(bboxWidth, bboxHeight) * CONTEXT_FACTOR)
            .roundToInt()
            .coerceIn(min(MIN_CROP, maxSide), maxSide)

        val centerX = (component.minX + component.maxX) / 2
        val centerY = (component.minY + component.maxY) / 2
        val left = (centerX - side / 2).coerceIn(0, width - side)
        val top = (centerY - side / 2).coerceIn(0, height - side)

        // חיתוך והקטנה ל-512x512
        val crop = Bitmap.createBitmap(
            pixels, top * width + left, width, side, side, Bitmap.Config.ARGB_8888
        )
        val scaled = Bitmap.createScaledBitmap(crop, MODEL_SIZE, MODEL_SIZE, true)
        val modelPixels = IntArray(PLANE)
        scaled.getPixels(modelPixels, 0, MODEL_SIZE, 0, 0, MODEL_SIZE, MODEL_SIZE)
        if (scaled !== crop) scaled.recycle()
        crop.recycle()

        // מסכה ב-512x512: true = למחוק (כל האנשים שעוד לא טופלו, כדי שלא ישמשו כהקשר)
        val erase = BooleanArray(PLANE)
        for (my in 0 until MODEL_SIZE) {
            val sy = top + my * side / MODEL_SIZE
            for (mx in 0 until MODEL_SIZE) {
                val sx = left + mx * side / MODEL_SIZE
                erase[my * MODEL_SIZE + mx] = remaining[sy * width + sx]
            }
        }

        val output = inpainter.run(buildModelInput(modelPixels, erase))

        // החזרה לתמונה: רק פיקסלים של הכתם הזה
        val scale = MODEL_SIZE.toFloat() / side
        for (py in top until top + side) {
            val row = py * width
            val fy = (py - top + 0.5f) * scale - 0.5f
            for (px in left until left + side) {
                val index = row + px
                if (labels[index] != component.label || !remaining[index]) continue

                val fx = (px - left + 0.5f) * scale - 0.5f
                val red = toByte(sample(output, 0, fx, fy))
                val green = toByte(sample(output, 1, fx, fy))
                val blue = toByte(sample(output, 2, fx, fy))
                pixels[index] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
                remaining[index] = false
            }
        }
    }

    private fun buildModelInput(modelPixels: IntArray, erase: BooleanArray): FloatArray {
        val input = FloatArray(4 * PLANE)
        for (i in 0 until PLANE) {
            val keep = if (erase[i]) 0f else 1f
            val color = modelPixels[i]
            val red = ((color shr 16) and 0xFF) / 127.5f - 1f
            val green = ((color shr 8) and 0xFF) / 127.5f - 1f
            val blue = (color and 0xFF) / 127.5f - 1f
            input[i] = keep - 0.5f
            input[PLANE + i] = red * keep
            input[2 * PLANE + i] = green * keep
            input[3 * PLANE + i] = blue * keep
        }
        return input
    }

    /** דגימה ליניארית של ערוץ אחד מהפלט (512x512) */
    private fun sample(output: FloatArray, channel: Int, fx: Float, fy: Float): Float {
        val x = fx.coerceIn(0f, (MODEL_SIZE - 1).toFloat())
        val y = fy.coerceIn(0f, (MODEL_SIZE - 1).toFloat())
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val x1 = min(x0 + 1, MODEL_SIZE - 1)
        val y1 = min(y0 + 1, MODEL_SIZE - 1)
        val tx = x - x0
        val ty = y - y0
        val base = channel * PLANE

        val upper = output[base + y0 * MODEL_SIZE + x0] * (1f - tx) +
            output[base + y0 * MODEL_SIZE + x1] * tx
        val lower = output[base + y1 * MODEL_SIZE + x0] * (1f - tx) +
            output[base + y1 * MODEL_SIZE + x1] * tx
        return upper * (1f - ty) + lower * ty
    }

    private fun toByte(value: Float): Int =
        ((value + 1f) * 127.5f).roundToInt().coerceIn(0, 255)

    /** הרחבת מסכה ב-radius פיקסלים (ריבוע), בשני מעברים, בזמן ליניארי */
    private fun dilate(mask: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
        val horizontal = BooleanArray(width * height)
        val prefixRow = IntArray(width + 1)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                prefixRow[x + 1] = prefixRow[x] + if (mask[row + x]) 1 else 0
            }
            for (x in 0 until width) {
                val a = max(0, x - radius)
                val b = min(width - 1, x + radius)
                horizontal[row + x] = prefixRow[b + 1] - prefixRow[a] > 0
            }
        }

        val result = BooleanArray(width * height)
        val prefixColumn = IntArray(height + 1)
        for (x in 0 until width) {
            for (y in 0 until height) {
                prefixColumn[y + 1] = prefixColumn[y] + if (horizontal[y * width + x]) 1 else 0
            }
            for (y in 0 until height) {
                val a = max(0, y - radius)
                val b = min(height - 1, y + radius)
                result[y * width + x] = prefixColumn[b + 1] - prefixColumn[a] > 0
            }
        }
        return result
    }

    companion object {
        private const val MODEL_SIZE = 512
        private const val PLANE = MODEL_SIZE * MODEL_SIZE

        /** גודל החלק שחותכים סביב כל כתם, ביחס לגודל הכתם (יותר הקשר = מילוי טבעי יותר) */
        private const val CONTEXT_FACTOR = 2.5f

        /** גודל חלק מינימלי בפיקסלים (כשהכתם קטן מאוד) */
        private const val MIN_CROP = 192

        /** כתמים קטנים מזה נשארים בתמונה (רעש) */
        private const val MIN_AREA = 25

        /** מקסימום כתמים לטיפול בתמונה אחת, כדי להגביל את הזמן */
        private const val MAX_COMPONENTS = 25

        /** הרחבת המסכה סביב אדם, כחלק מהצלע הארוכה שלו (אדם גדול מורחב יותר) */
        private const val GROW_FRACTION = 0.035f

        /** הרחבה למסכה מדויקת (SAM): רק כדי לכסות את שולי הצל והשיער */
        private const val PRECISE_GROW_FRACTION = 0.015f

        /** גבול עליון להרחבה: הצלע הקצרה של התמונה חלקי המספר הזה */
        private const val MAX_GROW_DIVISOR = 30

        /** כמה מעברים נוספים לחיפוש שאריות אחרי ההסרה הראשונה */
        private const val MAX_REFINE_ROUNDS = 2

        /** שאריות נחפשות רק עד מרחק כזה מהמסכה המקורית (הצלע הקצרה חלקי המספר הזה) */
        private const val NEAR_DIVISOR = 30

        /** גודל מינימלי של החלק שמריצים עליו את הזיהוי בפיקסלים */
        private const val MIN_DETECT_SIDE = 256

        /** שאריות קטנות מזה (בפיקסלים) מתעלמים מהן */
        private const val MIN_RESIDUAL = 150
        private const val RESIDUAL_DIVISOR = 20000
    }
}
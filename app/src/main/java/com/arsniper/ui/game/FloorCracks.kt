package com.arsniper.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * סדקי קרח זוהרים שמתפשטים על הרצפה של הארנה, לפני שהפורטל נפתח.
 * הרצפה מגיעה מניתוח התמונה (אילו נקודות "אפשר ללכת עליהן"), כך שבכל ארנה הסדקים רצים על הרצפה שלה בלבד,
 * משטוחים לפי הפרספקטיבה (קטנים ודקים יותר ככל שהרצפה רחוקה). בלי ניתוח: החלק התחתון של התמונה.
 * הסדקים נבנים פעם אחת (בקואורדינטות של העולם/התמונה) ומצוירים כל פריים לפי הזמן.
 */
class FloorCracks private constructor(
    /** לכל קטע: x0, y0, x1, y1 (עולם 0..1), לידה0, לידה1 (0..1 לאורך ההתפשטות), עובי, פרספקטיבה */
    private val seg: FloatArray,
    private val count: Int,
    /** מרכז ההתפשטות (עולם) */
    val seedX: Float,
    val seedY: Float,
    /** פרספקטיבה במרכז (לגודל הזוהר על הרצפה) */
    private val seedPersp: Float,
    /** שכבת הכפור על הרצפה (כל העולם, שקוף מחוץ לרצפה): ברק קר, רעש של קרח ולוחות קרח עם תפרים בהירים */
    private val frost: ImageBitmap?
) {

    companion object {
        private const val STRIDE = 8

        /** כמה מתוך זמן הסדקים לוקחת ההתפשטות עצמה; השאר זוהר מלא לפני הפורטל */
        const val GROW_FRACTION = 0.8f

        /**
         * בונה את רשת הסדקים. floorAt(x, y) = האם נקודה בעולם (0..1) היא רצפה; worldAspect = רוחב/גובה העולם.
         */
        fun build(floorAt: ((Float, Float) -> Boolean)?, worldAspect: Float, seed: Int = 7): FloorCracks {
            val gx = 64
            val gy = 40
            val walk = BooleanArray(gx * gy)
            var n = 0
            for (j in 0 until gy) for (i in 0 until gx) {
                val x = (i + 0.5f) / gx
                val y = (j + 0.5f) / gy
                val w = floorAt?.let { f -> runCatching { f(x, y) }.getOrDefault(false) } ?: false
                walk[j * gx + i] = w
                if (w) n++
            }
            // בלי רצפה מזוהה (או מעט מדי): החלק התחתון של התמונה
            val useMask = n >= 30
            fun isFloor(x: Float, y: Float): Boolean {
                if (x < 0.01f || x > 0.99f || y < 0.02f || y > 0.995f) return false
                if (!useMask) return y > 0.62f
                val i = (x * gx).toInt().coerceIn(0, gx - 1)
                val j = (y * gy).toInt().coerceIn(0, gy - 1)
                return walk[j * gx + i]
            }
            // גובה הקצה הרחוק של הרצפה: השורה העליונה שיש בה לפחות כמה תאי רצפה
            var top = 0.62f
            if (useMask) {
                for (j in 0 until gy) {
                    var c = 0
                    for (i in 0 until gx) if (walk[j * gx + i]) c++
                    if (c >= 3) { top = (j + 0.5f) / gy; break }
                }
            }
            val span = max(0.1f, 1f - top)
            fun persp(y: Float) = ((y - top) / span).coerceIn(0.15f, 1f)

            // מרכז: נקודת הרצפה הקרובה ביותר לאמצע הרוחב, בערך באמצע עומק הרצפה
            val wantX = 0.5f
            val wantY = top + 0.55f * span
            var sx = wantX
            var sy = wantY
            var best = Float.MAX_VALUE
            for (j in 0 until gy) for (i in 0 until gx) {
                val x = (i + 0.5f) / gx
                val y = (j + 0.5f) / gy
                if (!isFloor(x, y)) continue
                val d = hypot((x - wantX) * worldAspect, y - wantY)
                if (d < best) { best = d; sx = x; sy = y }
            }

            val rnd = Random(seed)
            val out = ArrayList<Float>(4096)
            var maxDist = 0.001f

            fun grow(x0: Float, y0: Float, a0: Float, steps: Int, width: Float, dist0: Float, depth: Int) {
                var x = x0
                var y = y0
                var a = a0
                var dist = dist0
                for (s in 0 until steps) {
                    a += (rnd.nextFloat() - 0.5f) * 0.45f
                    val p = persp(y)
                    val step = 0.045f * (0.35f + 0.65f * p)
                    // משטוח: על רצפה תנועה "קדימה" נראית קצרה יותר בגובה
                    val nx = x + cos(a) * step / worldAspect * 1.6f
                    val ny = y + sin(a) * step * 0.5f
                    if (!isFloor(nx, ny)) break
                    val d1 = dist + step
                    out.add(x); out.add(y); out.add(nx); out.add(ny)
                    out.add(dist); out.add(d1); out.add(width * (1f - 0.5f * s / steps)); out.add(p)
                    maxDist = max(maxDist, d1)
                    if (depth < 2 && rnd.nextFloat() < 0.18f) {
                        val side = if (rnd.nextBoolean()) 1f else -1f
                        grow(nx, ny, a + side * (0.5f + rnd.nextFloat() * 0.7f), (steps * 0.55f).toInt().coerceAtLeast(3), width * 0.6f, d1, depth + 1)
                    }
                    x = nx
                    y = ny
                    dist = d1
                }
            }

            val mains = 6
            for (k in 0 until mains) {
                val a = (k + rnd.nextFloat() * 0.6f) / mains * 2f * PI.toFloat()
                grow(sx, sy, a, 10 + rnd.nextInt(8), 1f, 0f, 0)
            }
            val arr = FloatArray(out.size)
            for (i in out.indices) arr[i] = out[i]
            // לידה מנורמלת 0..1 לפי המרחק מהמרכז לאורך הסדק
            var i = 0
            while (i < arr.size) {
                arr[i + 4] /= maxDist
                arr[i + 5] /= maxDist
                i += STRIDE
            }
            val frostBmp = runCatching { buildFrost(::isFloor, ::persp, worldAspect, top, rnd) }.getOrNull()
            return FloorCracks(arr, arr.size / STRIDE, sx, sy, persp(sy), frostBmp)
        }

        /**
         * מצייר פעם אחת את שכבת הכפור: רק על הרצפה, לבן-תכלת שקוף עם רעש, ולוחות קרח (וורונוי משוטח לפי הפרספקטיבה)
         * שהתפרים ביניהם בהירים יותר. גודל קטן (המסך מגדיל אותה) כדי שהבנייה תהיה מהירה.
         */
        private fun buildFrost(
            isFloor: (Float, Float) -> Boolean, persp: (Float) -> Float, worldAspect: Float, top: Float, rnd: Random
        ): ImageBitmap {
            val bw = 320
            val bh = (bw / worldAspect).toInt().coerceIn(100, 320)
            // נקודות הלוחות: יותר צפופות ליד הקצה הרחוק של הרצפה (פרספקטיבה)
            val n = 70
            val px = FloatArray(n)
            val py = FloatArray(n)
            for (i in 0 until n) {
                px[i] = rnd.nextFloat()
                val u = rnd.nextFloat()
                py[i] = top + (1f - top) * u * u
            }
            // רעש ערכים פשוט (רשת 24×16, אינטרפולציה)
            val gw = 24
            val gh = 16
            val grid = FloatArray(gw * gh) { rnd.nextFloat() }
            fun noise(x: Float, y: Float): Float {
                val fx = x * (gw - 1)
                val fy = y * (gh - 1)
                val ix = fx.toInt().coerceIn(0, gw - 2)
                val iy = fy.toInt().coerceIn(0, gh - 2)
                val tx = fx - ix
                val ty = fy - iy
                val a = grid[iy * gw + ix] + (grid[iy * gw + ix + 1] - grid[iy * gw + ix]) * tx
                val b = grid[(iy + 1) * gw + ix] + (grid[(iy + 1) * gw + ix + 1] - grid[(iy + 1) * gw + ix]) * tx
                return a + (b - a) * ty
            }
            val pixels = IntArray(bw * bh)
            for (j in 0 until bh) {
                val y = (j + 0.5f) / bh
                val p = persp(y)
                for (i in 0 until bw) {
                    val x = (i + 0.5f) / bw
                    if (!isFloor(x, y)) continue
                    // שני הקרובים בוורונוי: התפר = כשהמרחקים כמעט שווים. משטוחים את y כדי שהלוחות ייראו שוכבים
                    var d1 = Float.MAX_VALUE
                    var d2 = Float.MAX_VALUE
                    for (k in 0 until n) {
                        val dx = (x - px[k]) * worldAspect
                        val dy = (y - py[k]) * 2.6f / (0.35f + 0.65f * p)
                        val d = dx * dx + dy * dy
                        if (d < d1) { d2 = d1; d1 = d } else if (d < d2) d2 = d
                    }
                    val seam = (1f - ((kotlin.math.sqrt(d2) - kotlin.math.sqrt(d1)) / (0.012f + 0.02f * p)).coerceIn(0f, 1f))
                    val nz = 0.55f * noise(x, y) + 0.45f * noise((x * 3.1f) % 1f, (y * 3.7f) % 1f)
                    val a = (0.32f + 0.3f * nz + 0.45f * seam * seam).coerceIn(0f, 1f) * (0.55f + 0.45f * p)
                    val r = (170 + 85 * seam).toInt().coerceIn(0, 255)
                    val g = (215 + 40 * seam).toInt().coerceIn(0, 255)
                    pixels[j * bw + i] = ((a * 255).toInt() shl 24) or (r shl 16) or (g shl 8) or 255
                }
            }
            val bmp = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, bw, 0, 0, bw, bh)
            return bmp.asImageBitmap()
        }
    }

    /**
     * מצייר בזמן k (0..1 מתוך זמן הסדקים). map = [ax, bx, ay, by]: מסך = a·עולם + b (בשברי מסך).
     * fade = שקיפות כוללת (לדעיכה מתחת לפורטל).
     */
    fun draw(scope: DrawScope, k: Float, map: FloatArray, fade: Float = 1f) = with(scope) {
        if (count == 0 || fade <= 0f) return@with
        val w = size.width
        val h = size.height
        val reveal = (k / GROW_FRACTION).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) }
        val pulse = 0.85f + 0.15f * sin(k * 18f)

        // זוהר כחול על הרצפה סביב המרכז, משוטח לפי הפרספקטיבה
        val cx = (map[0] * seedX + map[1]) * w
        val cy = (map[2] * seedY + map[3]) * h
        val gr = h * (0.1f + 0.75f * reveal) * (0.5f + 0.5f * seedPersp)
        val flat = 0.38f
        drawContext.canvas.save()
        drawContext.canvas.translate(cx, cy)
        drawContext.canvas.scale(1f, flat)
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFBFF0FF).copy(alpha = 0.75f * fade * pulse), Color(0xFF2E9BFF).copy(alpha = 0.4f * fade), Color.Transparent),
                center = Offset.Zero, radius = gr
            ),
            gr, Offset.Zero
        )
        drawContext.canvas.restore()

        // שכבת הכפור מתפשטת מהמרכז יחד עם הסדקים (עיגול רך ומשוטח שגדל), וזוהרת קלות
        val fz = frost
        if (fz != null) {
            val left = map[1] * w
            val topY = map[3] * h
            val ww = map[0] * w
            val hh = map[2] * h
            val fr = (0.08f + 1.25f * reveal) * w
            drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, w, h), androidx.compose.ui.graphics.Paint())
            drawImage(
                fz, IntOffset.Zero, IntSize(fz.width, fz.height),
                IntOffset(left.toInt(), topY.toInt()), IntSize(ww.toInt(), hh.toInt()),
                alpha = 0.85f * fade * (0.9f + 0.1f * pulse)
            )
            // מסכה: רק בתוך העיגול שגדל מהמרכז נשאר כפור
            drawContext.canvas.save()
            drawContext.canvas.translate(cx, cy)
            drawContext.canvas.scale(1f, 0.42f)
            drawRect(
                Brush.radialGradient(listOf(Color.White, Color.White, Color.Transparent), center = Offset.Zero, radius = fr),
                topLeft = Offset(-w * 2f, -h * 5f), size = androidx.compose.ui.geometry.Size(w * 4f, h * 10f),
                blendMode = BlendMode.DstIn
            )
            drawContext.canvas.restore()
            drawContext.canvas.restore()
        }

        val glow = Color(0xFF3FB8FF)
        val core = Color(0xFFE8FCFF)
        val base = h * 0.008f
        // שני מעברים: זוהר רחב, ואז קו לבן דק
        for (pass in 0 until 2) {
            var i = 0
            while (i < count * STRIDE) {
                val b0 = seg[i + 4]
                if (b0 < reveal) {
                    val b1 = seg[i + 5]
                    val part = if (b1 <= reveal) 1f else (reveal - b0) / max(1e-4f, b1 - b0)
                    val x0 = (map[0] * seg[i] + map[1]) * w
                    val y0 = (map[2] * seg[i + 1] + map[3]) * h
                    val x1w = seg[i] + (seg[i + 2] - seg[i]) * part
                    val y1w = seg[i + 1] + (seg[i + 3] - seg[i + 1]) * part
                    val x1 = (map[0] * x1w + map[1]) * w
                    val y1 = (map[2] * y1w + map[3]) * h
                    val p = seg[i + 7]
                    val wd = base * seg[i + 6] * (0.35f + 0.65f * p)
                    if (pass == 0) {
                        drawLine(glow.copy(alpha = 0.7f * fade * pulse), Offset(x0, y0), Offset(x1, y1), strokeWidth = wd * 6.5f, cap = StrokeCap.Round)
                    } else {
                        drawLine(core.copy(alpha = 0.95f * fade), Offset(x0, y0), Offset(x1, y1), strokeWidth = max(1.6f, wd * 1.4f), cap = StrokeCap.Round)
                        // ניצוץ בקצה שעוד מתקדם
                        if (part < 1f) drawCircle(Color.White.copy(alpha = 0.9f * fade), wd * 1.6f + 1.5f, Offset(x1, y1))
                    }
                }
                i += STRIDE
            }
        }
    }
}

/** מיפוי עולם→מסך מהמצלמה (אפיני): [ax, bx, ay, by] */
fun cameraMap(zoom: Float, viewFraction: Float, centerX: Float, centerY: Float): FloatArray =
    floatArrayOf(zoom / viewFraction, -centerX * zoom / viewFraction + 0.5f, zoom, -centerY * zoom + 0.5f)

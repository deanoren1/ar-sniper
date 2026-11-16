package com.arsniper.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    private val seedPersp: Float
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
            return FloorCracks(arr, arr.size / STRIDE, sx, sy, persp(sy))
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

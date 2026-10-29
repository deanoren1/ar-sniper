package com.arsniper.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.arsniper.game.progress.ArenaStyle
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * עיצוב הארנה לפי הגיאומטריה האמיתית של התמונה (בלי AI בענן): משתמש בעומק ובסיווג של ניתוח הסצנה.
 * שלג מצטבר רק על משטחים שפונים למעלה, כפור על משטחים אנכיים, וקרחונים תלויים מהתקרה.
 * התוצאה נשמרת במטמון לפי מפתח התמונה, כך שהחישוב קורה פעם אחת לכל תמונה. Kotlin בלי תלות במסך.
 *
 * איך מזהים "פונה למעלה": בהיטל פרספקטיבי, משטח אופקי מקיים 1/עומק ~ ליניארי בשורת התמונה, כלומר
 * השיפוע של 1/עומק לאורך Y קבוע ועובד כמדד לכיוון המשטח. מכיילים אותו מול הרצפה של אותה תמונה.
 */
object ThemeBake {

    private const val VERSION = 2
    private const val MAX_PIXELS = 2_000_000

    /** קטגוריות ADE20K של רצפה/קרקע */
    private val FLOOR = setOf(3, 6, 9, 11, 13, 28, 29, 46, 52, 91, 94)

    /** קטגוריות שלעולם לא נערם עליהן שלג: קירות, שמיים, תקרה, חלונות, דלתות, תמונות, מראות, שלטים */
    private val NO_SNOW = setOf(0, 1, 2, 5, 8, 14, 22, 27, 36, 43)
    private const val CEILING = 5

    /** מחזיר תמונה מעוצבת, או null אם אין עיצוב לארנה הזאת או שהחישוב נכשל */
    fun bake(context: Context, style: ArenaStyle, source: Bitmap, analysis: SceneAnalysis, key: String): Bitmap? {
        if (style != ArenaStyle.Tundra) return null
        val dir = File(context.cacheDir, "themes").apply { mkdirs() }
        val cached = File(dir, "${key}_${style.name}_v$VERSION.jpg")
        if (cached.exists()) {
            BitmapFactory.decodeFile(cached.absolutePath)?.let { return it }
        }
        val result = try {
            tundra(context, source, analysis)
        } catch (_: Throwable) {
            null
        } ?: return null
        try {
            cached.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        } catch (_: Exception) {
        }
        return result
    }

    // ------------------------------------------------------------------ טונדרה

    private fun tundra(context: Context, source: Bitmap, a: SceneAnalysis): Bitmap? {
        val snowTex = loadTex(context, "theme/snow.jpg")
        val frostTex = loadTex(context, "theme/frost.jpg")
        val scale = min(1f, sqrt(MAX_PIXELS.toFloat() / (source.width.toFloat() * source.height)))
        val w = max(8, (source.width * scale).roundToInt())
        val h = max(8, (source.height * scale).roundToInt())
        val resized = if (w == source.width && h == source.height) source else Bitmap.createScaledBitmap(source, w, h, true)
        val out = resized.copy(Bitmap.Config.ARGB_8888, true)
        if (resized !== source) resized.recycle()

        val gw = a.width
        val gh = a.height
        if (gw < 8 || gh < 8) return null

        // שיפוע ההיפוך של העומק לאורך Y (מוחלק), ואחריו כיול מול הרצפה
        val inv = FloatArray(gw * gh) { 1f / max(a.depth[it], 0.25f) }
        val smooth = boxBlur(inv, gw, gh, 3)
        val g = FloatArray(gw * gh)
        for (y in 1 until gh - 1) {
            for (x in 0 until gw) {
                g[y * gw + x] = (smooth[(y + 1) * gw + x] - smooth[(y - 1) * gw + x]) * 0.5f
            }
        }
        val c = calibrate(a, g, gw * gh)
        if (c <= 1e-6f) return null

        val up = FloatArray(gw * gh)
        for (i in 0 until gw * gh) {
            val label = a.labelIndex(i)
            var u = smoothstep(0.35f, 0.8f, g[i] / c)
            if (label in NO_SNOW) u = 0f else if (label in FLOOR) u = max(u, 0.9f)
            up[i] = u
        }
        val upSmooth = boxBlur(up, gw, gh, 1)

        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        val sx = (gw - 1).toFloat() / (w - 1)
        val sy = (gh - 1).toFloat() / (h - 1)
        for (y in 0 until h) {
            val gy = y * sy
            val y0 = gy.toInt().coerceAtMost(gh - 2)
            val fy = gy - y0
            for (x in 0 until w) {
                val gx = x * sx
                val x0 = gx.toInt().coerceAtMost(gw - 2)
                val fx = gx - x0
                val i00 = y0 * gw + x0
                val upv = lerp(lerp(upSmooth[i00], upSmooth[i00 + 1], fx), lerp(upSmooth[i00 + gw], upSmooth[i00 + gw + 1], fx), fy)
                val z = lerp(lerp(a.depth[i00], a.depth[i00 + 1], fx), lerp(a.depth[i00 + gw], a.depth[i00 + gw + 1], fx), fy)
                val label = a.labelIndex((y0 + if (fy > 0.5f) 1 else 0) * gw + x0 + if (fx > 0.5f) 1 else 0)
                val isFloor = label in FLOOR

                // רעש בקנה מידה שתלוי בעומק: הכתמים קטנים יותר ככל שהם רחוקים
                val f = (2.5f / z.coerceIn(1.5f, 8f)).coerceIn(0.5f, 1.8f)
                val n1 = noise(x * 0.55f * f, y * 0.9f * f)
                val n2 = noise(x * 1.7f * f + 50f, y * 2.8f * f + 17f)
                val n3 = noise(x * 1.3f + 99f, y * 1.3f)
                val thr = if (isFloor) 0.44f else 0.30f
                val cover = smoothstep(thr - 0.10f, thr + 0.10f, n1 * 0.75f + n2 * 0.25f)
                val snow = upv * cover

                val argb = px[y * w + x]
                var r = ((argb shr 16) and 0xFF) / 255f
                var gg = ((argb shr 8) and 0xFF) / 255f
                var b = (argb and 0xFF) / 255f
                val luma = 0.299f * r + 0.587f * gg + 0.114f * b

                // גוון קר כללי: פחות רוויה, יותר כחול
                r = luma + (r - luma) * 0.72f
                gg = luma + (gg - luma) * 0.72f
                b = luma + (b - luma) * 0.72f
                r *= 0.84f
                gg *= 0.92f
                b *= 1.06f

                // כפור על משטחים אנכיים וחפצים (לא שמיים/תקרה)
                if (label != 2 && label != CEILING) {
                    val frost = (1f - upv) * 0.20f * smoothstep(0.52f, 0.78f, n3)
                    if (frostTex != null) {
                        val fc = sample(frostTex, x * 0.8f, y * 0.8f)
                        val fk = (1f - upv) * 0.38f * smoothstep(0.40f, 0.70f, n3)
                        val shadeF = 0.7f + 0.5f * luma
                        r = lerp(r, ((fc shr 16) and 0xFF) / 255f * shadeF, fk)
                        gg = lerp(gg, ((fc shr 8) and 0xFF) / 255f * shadeF, fk)
                        b = lerp(b, (fc and 0xFF) / 255f * shadeF, fk)
                    } else {
                        r = lerp(r, 0.86f, frost)
                        gg = lerp(gg, 0.94f, frost)
                        b = lerp(b, 1.0f, frost)
                    }
                }

                // שלג: מואר לפי הבהירות המקורית, כחלחל בצל, עם נצנוץ
                if (snow > 0.01f) {
                    val t = (luma * 1.35f).coerceIn(0f, 1f)
                    val shade = 0.66f + 0.40f * t
                    var sr: Float
                    var sg: Float
                    var sb: Float
                    if (snowTex != null) {
                        val c = sample(snowTex, x * 0.9f * f, y * 0.9f * f)
                        sr = ((c shr 16) and 0xFF) / 255f * shade * 1.05f
                        sg = ((c shr 8) and 0xFF) / 255f * shade * 1.05f
                        sb = (c and 0xFF) / 255f * shade * 1.05f
                    } else {
                        sr = (0.82f + 0.18f * t) * shade
                        sg = (0.89f + 0.11f * t) * shade
                        sb = shade
                        if (n3 > 0.80f) {
                            val sparkle = (n3 - 0.80f) * 1.6f
                            sr += sparkle
                            sg += sparkle
                            sb += sparkle
                        }
                    }
                    val k = snow * 0.95f
                    r = lerp(r, sr, k)
                    gg = lerp(gg, sg, k)
                    b = lerp(b, sb, k)
                } else if (isFloor) {
                    // רצפה קפואה בלי שלג: ברק קר בבהירים
                    val sheen = 0.10f * smoothstep(0.55f, 1f, luma)
                    r += sheen * 0.8f
                    gg += sheen * 0.95f
                    b += sheen
                }
                px[y * w + x] = (0xFF shl 24) or
                    (clamp255(r) shl 16) or
                    (clamp255(gg) shl 8) or
                    clamp255(b)
            }
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        drawIcicles(context, out, a)
        return out
    }

    /** קרחונים שתלויים מקצה התקרה (או מראש התמונה אם אין תקרה בסצנה) */
    private fun drawIcicles(context: Context, bitmap: Bitmap, a: SceneAnalysis) {
        val sprites = (0 until 6).mapNotNull { i ->
            try { context.assets.open("theme/icicle$i.png").use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
        }
        val w = bitmap.width
        val h = bitmap.height
        val canvas = Canvas(bitmap)
        val rnd = java.util.Random(77)
        val ceilingFraction = a.fraction(CEILING)
        val step = max(5, w / 100)
        val body = Paint(Paint.ANTI_ALIAS_FLAG)
        val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB4FFFFFF.toInt(); style = Paint.Style.STROKE }
        var x = step / 2
        var column = 0
        while (x < w) {
            column += 1
            val nx = x.toFloat() / w
            var edgeY = -1f
            if (ceilingFraction > 0.02f) {
                var run = 0
                var ny = 0f
                while (ny < 0.9f) {
                    if (a.labelAt(nx, ny) == CEILING) {
                        run += 1
                    } else if (run >= 2) {
                        edgeY = ny
                        break
                    } else {
                        run = 0
                    }
                    ny += 1f / a.height
                }
            } else if (column % 3 == 0) {
                edgeY = 0f
            }
            if (edgeY >= 0f && rnd.nextFloat() < 0.75f) {
                val z = a.depthAt(nx, edgeY.coerceIn(0f, 0.99f))
                val depthFactor = (2.5f / z.coerceIn(1.5f, 10f)).coerceIn(0.4f, 1.4f)
                val length = h * (0.03f + 0.07f * rnd.nextFloat()) * depthFactor
                val half = max(1.5f, length * (0.09f + 0.05f * rnd.nextFloat()))
                val top = edgeY * h
                val sway = (rnd.nextFloat() - 0.5f) * half * 0.8f
                if (sprites.isNotEmpty()) {
                    val sp = sprites[rnd.nextInt(sprites.size)]
                    val sh = h * (0.07f + 0.10f * rnd.nextFloat()) * depthFactor
                    val sw = sh * sp.width / sp.height
                    val dst = android.graphics.RectF(x - sw / 2f, top - sh * 0.04f, x + sw / 2f, top + sh)
                    canvas.drawBitmap(sp, null, dst, spritePaint)
                    x += (sw * 0.55f).toInt()
                } else {
                    body.shader = LinearGradient(
                        x.toFloat(), top, x.toFloat(), top + length,
                        intArrayOf(0xF0EAF6FF.toInt(), 0xC8B8E0FF.toInt(), 0x8CA0D8FF.toInt()),
                        floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
                    )
                    val path = Path().apply {
                        moveTo(x - half, top)
                        lineTo(x + half, top)
                        lineTo(x + sway, top + length)
                        close()
                    }
                    canvas.drawPath(path, body)
                    shine.strokeWidth = max(1f, half * 0.35f)
                    canvas.drawLine(x - half * 0.45f, top + length * 0.05f, x + sway * 0.6f - half * 0.1f, top + length * 0.8f, shine)
                }
            }
            x += step
        }
    }

    // ------------------------------------------------------------------ עזרים

    private class Tex(val w: Int, val h: Int, val px: IntArray)

    private fun loadTex(context: Context, name: String): Tex? = try {
        context.assets.open(name).use { BitmapFactory.decodeStream(it) }?.let { bmp ->
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            Tex(bmp.width, bmp.height, px).also { bmp.recycle() }
        }
    } catch (_: Exception) {
        null
    }

    /** דגימה עם שיקוף בקצוות, כדי שהאריח לא ייראה תפר */
    private fun sample(t: Tex, u: Float, v: Float): Int {
        var x = (u.toInt() % (t.w * 2)).let { if (it < 0) it + t.w * 2 else it }
        var y = (v.toInt() % (t.h * 2)).let { if (it < 0) it + t.h * 2 else it }
        if (x >= t.w) x = t.w * 2 - 1 - x
        if (y >= t.h) y = t.h * 2 - 1 - y
        return t.px[y * t.w + x]
    }

    /** הערך האופייני של השיפוע על הרצפה (חציון); בלי רצפה בסצנה: אחוזון 70 של הערכים החיוביים */
    private fun calibrate(a: SceneAnalysis, g: FloatArray, size: Int): Float {
        val floor = ArrayList<Float>()
        val all = ArrayList<Float>()
        val stride = max(1, size / 20000)
        var i = 0
        while (i < size) {
            val value = g[i]
            if (value > 0f) {
                all.add(value)
                if (a.labelIndex(i) in FLOOR) floor.add(value)
            }
            i += stride
        }
        val pool = if (floor.size >= 150) floor else all
        if (pool.size < 30) return 0f
        pool.sort()
        return if (pool === floor) pool[pool.size / 2] else pool[(pool.size * 0.7f).toInt().coerceAtMost(pool.size - 1)]
    }

    private fun boxBlur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        if (radius <= 0) return src
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                var n = 0
                for (k in -radius..radius) {
                    val xx = x + k
                    if (xx in 0 until w) {
                        sum += src[y * w + xx]
                        n += 1
                    }
                }
                tmp[y * w + x] = sum / n
            }
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                var n = 0
                for (k in -radius..radius) {
                    val yy = y + k
                    if (yy in 0 until h) {
                        sum += tmp[yy * w + x]
                        n += 1
                    }
                }
                out[y * w + x] = sum / n
            }
        }
        return out
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun clamp255(v: Float): Int = (v * 255f + 0.5f).toInt().coerceIn(0, 255)

    // רעש חלק (fBm) מטבלה אחת שמוכנה מראש, עם חזרה כל 256 יחידות
    private const val TILE = 256
    private val tile: FloatArray by lazy { makeNoiseTile() }

    private fun makeNoiseTile(): FloatArray {
        val result = FloatArray(TILE * TILE)
        val rnd = java.util.Random(1234)
        var amplitude = 1f
        var total = 0f
        var cells = 4
        repeat(5) {
            val grid = FloatArray(cells * cells) { rnd.nextFloat() }
            for (y in 0 until TILE) {
                val gy = y.toFloat() * cells / TILE
                val y0 = gy.toInt() % cells
                val y1 = (y0 + 1) % cells
                val fy = gy - gy.toInt()
                val ty = fy * fy * (3f - 2f * fy)
                for (x in 0 until TILE) {
                    val gx = x.toFloat() * cells / TILE
                    val x0 = gx.toInt() % cells
                    val x1 = (x0 + 1) % cells
                    val fx = gx - gx.toInt()
                    val tx = fx * fx * (3f - 2f * fx)
                    val v = lerp(
                        lerp(grid[y0 * cells + x0], grid[y0 * cells + x1], tx),
                        lerp(grid[y1 * cells + x0], grid[y1 * cells + x1], tx),
                        ty
                    )
                    result[y * TILE + x] += v * amplitude
                }
            }
            total += amplitude
            amplitude *= 0.5f
            cells *= 2
        }
        for (i in result.indices) {
            result[i] = ((result[i] / total - 0.5f) * 1.7f + 0.5f).coerceIn(0f, 1f)
        }
        return result
    }

    private fun noise(x: Float, y: Float): Float {
        val xf = kotlin.math.floor(x)
        val yf = kotlin.math.floor(y)
        val fx = x - xf
        val fy = y - yf
        val x0 = xf.toInt() and (TILE - 1)
        val y0 = yf.toInt() and (TILE - 1)
        val x1 = (x0 + 1) and (TILE - 1)
        val y1 = (y0 + 1) and (TILE - 1)
        val t = tile
        return lerp(
            lerp(t[y0 * TILE + x0], t[y0 * TILE + x1], fx),
            lerp(t[y1 * TILE + x0], t[y1 * TILE + x1], fx),
            fy
        )
    }
}

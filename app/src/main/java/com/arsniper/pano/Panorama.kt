package com.arsniper.pano

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

/** כיוון המצלמה האחורית מחיישן הסיבוב: yaw (ימינה = עולה) ו-pitch (למעלה = חיובי), ברדיאנים */
class HeadingTracker(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    @Volatile var yaw = 0f
    @Volatile var pitch = 0f
    @Volatile var ready = false

    val available: Boolean get() = sensor != null

    fun start() {
        sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        manager.unregisterListener(this)
        ready = false
    }

    private val rotation = FloatArray(9)

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        // כיוון המצלמה האחורית במכשיר הוא (0,0,-1): בעולם זה (-R[2], -R[5], -R[8]) = (מזרח, צפון, מעלה)
        val east = -rotation[2]
        val north = -rotation[5]
        val up = -rotation[8]
        yaw = atan2(east, north)
        pitch = asin(up.coerceIn(-1f, 1f))
        ready = true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

fun wrapAngle(a: Float): Float {
    val twoPi = (2 * PI).toFloat()
    var r = a % twoPi
    if (r > PI) r -= twoPi
    if (r < -PI) r += twoPi
    return r
}

/** שדה ראייה אופקי של המצלמה האחורית (ברדיאנים); ברירת מחדל 65 מעלות אם אי אפשר לקרוא */
fun horizontalFov(context: Context): Float {
    try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (id in cm.cameraIdList) {
            val ch = cm.getCameraCharacteristics(id)
            if (ch.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) continue
            val size = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: continue
            val f = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: continue
            return (2.0 * kotlin.math.atan(size.width / (2.0 * f))).toFloat() * 0.97f
        }
    } catch (_: Exception) {
    }
    return (65.0 * PI / 180.0).toFloat()
}

class PanoFrame(val pixels: IntArray, val w: Int, val h: Int, val phi: Float, val pitch: Float)

/** זוויות היעד לצילום (יחסית לנקודת ההתחלה). חפיפה של כ-40% בין תמונות */
fun targetAngles(fov: Float, is360: Boolean): List<Float> {
    val step = fov * 0.6f
    val need = if (is360) (2 * PI).toFloat() - fov * 0.6f else PI.toFloat()
    val out = ArrayList<Float>()
    var t = 0f
    while (true) {
        out.add(t)
        if (t >= need) break
        t = if (is360) t + step else min(t + step, PI.toFloat())
    }
    return out
}

object PanoramaStitcher {

    class Result(val bitmap: Bitmap, val radius: Float)

    /** מחבר את התמונות על גליל לפי הזווית שנמדדה בחיישן, עם מיזוג רך בחפיפות */
    fun stitch(rawFrames: List<PanoFrame>, hFov: Float, is360: Boolean): Result? {
        if (rawFrames.isEmpty()) return null
        // תיקון קטן של הזוויות לפי התמונות עצמן (החיישן סוטה קצת ואין עין מושלמת)
        val frames = try { refine(rawFrames, hFov) } catch (e: Throwable) { rawFrames }
        val w = frames[0].w
        val h = frames[0].h
        val radius = (w / 2f) / tan(hFov / 2f)
        val half = hFov / 2f
        val span = frames.maxOf { it.phi }
        val panoW = if (is360) (2 * PI * radius).roundToInt() else ((span + hFov) * radius).roundToInt()
        val thetaMin = if (is360) 0f else -half
        val pad = min(h, (radius * tan(0.4f)).roundToInt())
        val hp = h + 2 * pad
        val out = IntArray(panoW * hp)
        val cover = IntArray(hp)

        val accR = FloatArray(hp)
        val accG = FloatArray(hp)
        val accB = FloatArray(hp)
        val accW = FloatArray(hp)

        for (x in 0 until panoW) {
            java.util.Arrays.fill(accR, 0f); java.util.Arrays.fill(accG, 0f)
            java.util.Arrays.fill(accB, 0f); java.util.Arrays.fill(accW, 0f)
            val theta = thetaMin + x / radius
            for (f in frames) {
                val d = wrapAngle(theta - f.phi)
                if (abs(d) >= half) continue
                val xs = w / 2f + radius * tan(d)
                if (xs < 0f || xs >= w - 1) continue
                val cosD = cos(d)
                // משקל חד: כמעט תמיד התמונה הקרובה ביותר לאמצע, כדי שלא יהיו רוחות רפאים בחפיפה
                val lin = 1f - abs(d) / half
                val weight = lin * lin * lin * lin * lin * lin * lin * lin + 1e-6f
                val cy = hp / 2f - radius * tan(f.pitch)
                val x0 = floor(xs).toInt()
                val fx = xs - x0
                for (y in 0 until hp) {
                    val ys = h / 2f + (y - cy) / cosD
                    if (ys < 0f || ys >= h - 1) continue
                    val y0 = floor(ys).toInt()
                    val fy = ys - y0
                    val p00 = f.pixels[y0 * w + x0]
                    val p10 = f.pixels[y0 * w + x0 + 1]
                    val p01 = f.pixels[(y0 + 1) * w + x0]
                    val p11 = f.pixels[(y0 + 1) * w + x0 + 1]
                    val a = (1 - fx) * (1 - fy)
                    val b = fx * (1 - fy)
                    val c = (1 - fx) * fy
                    val e = fx * fy
                    val r = ch(p00, 16) * a + ch(p10, 16) * b + ch(p01, 16) * c + ch(p11, 16) * e
                    val g = ch(p00, 8) * a + ch(p10, 8) * b + ch(p01, 8) * c + ch(p11, 8) * e
                    val bl = ch(p00, 0) * a + ch(p10, 0) * b + ch(p01, 0) * c + ch(p11, 0) * e
                    accR[y] += r * weight; accG[y] += g * weight; accB[y] += bl * weight; accW[y] += weight
                }
            }
            for (y in 0 until hp) {
                val wt = accW[y]
                if (wt > 0f) {
                    val r = (accR[y] / wt).toInt().coerceIn(0, 255)
                    val g = (accG[y] / wt).toInt().coerceIn(0, 255)
                    val b = (accB[y] / wt).toInt().coerceIn(0, 255)
                    out[y * panoW + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    cover[y]++
                } else {
                    out[y * panoW + x] = 0xFF000000.toInt()
                }
            }
        }

        // חותכים לשורות שכמעט כולן מכוסות
        val need = (panoW * 0.9f).toInt()
        var top = 0
        while (top < hp && cover[top] < need) top++
        var bottom = hp - 1
        while (bottom > top && cover[bottom] < need) bottom--
        if (bottom - top < h / 4) { top = 0; bottom = hp - 1 }
        val outH = bottom - top + 1
        val bmp = Bitmap.createBitmap(panoW, outH, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, top * panoW, panoW, 0, 0, panoW, outH)
        return Result(bmp, radius)
    }


    /**
     * מתקן את הזווית והגובה של כל תמונה לפי התמונה הקודמת: משווים את אזור החפיפה (בגודל מוקטן, בגווני אפור)
     * ובוחרים את ההזזה הקטנה שמתאימה הכי טוב. כך הסטיות הקטנות של החיישן לא יוצרות כפילויות.
     */
    private fun refine(frames: List<PanoFrame>, hFov: Float): List<PanoFrame> {
        if (frames.size < 2) return frames
        val w = frames[0].w
        val h = frames[0].h
        val gw = 200
        val sc = gw.toFloat() / w
        val gh = (h * sc).toInt().coerceAtLeast(8)
        val rs = (gw / 2f) / tan(hFov / 2f)
        val half = hFov / 2f
        // תמונות אפור מוקטנות (ממוצע בלוקים)
        val grays = frames.map { f ->
            val g = FloatArray(gw * gh)
            val bx = (w.toFloat() / gw)
            val by = (h.toFloat() / gh)
            for (gy in 0 until gh) for (gx in 0 until gw) {
                val x0 = (gx * bx).toInt(); val x1 = ((gx + 1) * bx).toInt().coerceAtLeast(x0 + 1).coerceAtMost(w)
                val y0 = (gy * by).toInt(); val y1 = ((gy + 1) * by).toInt().coerceAtLeast(y0 + 1).coerceAtMost(h)
                var sum = 0f; var n = 0
                var yy = y0
                while (yy < y1) {
                    var xx = x0
                    while (xx < x1) {
                        val p = f.pixels[yy * w + xx]
                        sum += 0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
                        n++; xx++
                    }
                    yy++
                }
                g[gy * gw + gx] = if (n > 0) sum / n else 0f
            }
            g
        }
        val phis = FloatArray(frames.size) { frames[it].phi }
        val pitches = FloatArray(frames.size) { frames[it].pitch }

        fun sample(g: FloatArray, theta: Float, phi: Float, cyRow: Float, y: Float): Float {
            val d = wrapAngle(theta - phi)
            if (abs(d) >= half * 0.98f) return Float.NaN
            val xs = gw / 2f + rs * tan(d)
            val ys = gh / 2f + (y - cyRow) / cos(d)
            if (xs < 0f || xs >= gw - 1 || ys < 0f || ys >= gh - 1) return Float.NaN
            val x0 = floor(xs).toInt(); val y0 = floor(ys).toInt()
            val fx = xs - x0; val fy = ys - y0
            val a = g[y0 * gw + x0]; val b = g[y0 * gw + x0 + 1]
            val c = g[(y0 + 1) * gw + x0]; val e = g[(y0 + 1) * gw + x0 + 1]
            return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + e * fx) * fy
        }

        for (i in 1 until frames.size) {
            val d0 = wrapAngle(phis[i] - phis[i - 1])
            if (abs(d0) >= half * 1.7f) continue
            val tStart = phis[i - 1] + (if (d0 > 0f) d0 - half * 0.85f else -half * 0.85f)
            val tEnd = phis[i - 1] + (if (d0 > 0f) half * 0.85f else d0 + half * 0.85f)
            if (tEnd - tStart < 0.1f) continue
            val steps = ((tEnd - tStart) * rs).toInt().coerceIn(10, 160)
            val rowsFrom = (gh * 0.25f).toInt()
            val rowsTo = (gh * 0.75f).toInt()
            val cyPrev = gh / 2f - rs * tan(pitches[i - 1])
            val cyCur = gh / 2f - rs * tan(pitches[i])
            var bestCost = Float.MAX_VALUE
            var bestDx = 0; var bestDy = 0
            var cost0 = Float.MAX_VALUE
            for (dy in -14..14) for (dx in -14..14) {
                var sum = 0f; var n = 0
                val phiTry = phis[i] + dx / rs
                val cyTry = cyCur + dy
                var si = 0
                while (si < steps) {
                    val theta = tStart + (tEnd - tStart) * si / steps
                    var row = rowsFrom
                    while (row < rowsTo) {
                        val y = row.toFloat()
                        val a = sample(grays[i - 1], theta, phis[i - 1], cyPrev, y)
                        val b = sample(grays[i], theta, phiTry, cyTry, y)
                        if (!a.isNaN() && !b.isNaN()) {
                            val diff = a - b
                            sum += diff * diff; n++
                        }
                        row += 2
                    }
                    si += 2
                }
                if (n < 150) continue
                val cost = sum / n
                if (dx == 0 && dy == 0) cost0 = cost
                if (cost < bestCost) { bestCost = cost; bestDx = dx; bestDy = dy }
            }
            if (cost0 < Float.MAX_VALUE && bestCost < cost0 * 0.92f && (bestDx != 0 || bestDy != 0)) {
                phis[i] += bestDx / rs
                pitches[i] = atan(tan(pitches[i]) - bestDy / rs)
            }
        }
        return frames.mapIndexed { i, f -> PanoFrame(f.pixels, f.w, f.h, phis[i], pitches[i]) }
    }

    private fun ch(p: Int, shift: Int): Float = ((p shr shift) and 0xFF).toFloat()
}

object PanoramaStore {
    private const val PREFS = "pano"

    fun file(context: Context) = File(context.filesDir, "panorama.jpg")

    fun save(context: Context, result: PanoramaStitcher.Result, is360: Boolean) {
        file(context).outputStream().use { result.bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("is360", is360).putFloat("radius", result.radius).apply()
    }

    fun load(context: Context): Bitmap? = file(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }

    fun is360(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("is360", true)

    fun radius(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat("radius", 700f)
}

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
    val need = if (is360) (2 * PI).toFloat() - fov else PI.toFloat()
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
    fun stitch(frames: List<PanoFrame>, hFov: Float, is360: Boolean): Result? {
        if (frames.isEmpty()) return null
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
                val weight = 1f - abs(d) / half
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

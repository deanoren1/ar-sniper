package com.arsniper.ui.game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * שלג הטונדרה: שלוש שכבות נפרדות (קרוב, בינוני, רחוק), לכל אחת תמונה, מהירות, כיוון וקצב נדנוד משלה,
 * ושכבה רביעית: משבי רוח שמדי פעם סוחפים ענן שלג מצד לצד.
 * העוצמה נמוכה-בינונית, השכבה הקרובה שקופה מאוד, וכל זה מצויר מתחת למפלצות ולכוונת.
 */
@Composable
fun TundraSnow(nowNanos: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val near = remember { loadSnow(context, "snow_near") }
    val mid = remember { loadSnow(context, "snow_mid") }
    val far = remember { loadSnow(context, "snow_far") }
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = nowNanos / 1e9f
        // רחוק: קטן, איטי, כמעט אנכי
        tile(far, t, scale = 0.85f, vx = 0.004f, vy = 0.030f, sway = 0.010f, swayRate = 0.35f, phase = 0.0f, alpha = 0.30f)
        // בינוני: מהירות בינונית, נוטה ימינה
        tile(mid, t, scale = 1.0f, vx = -0.012f, vy = 0.075f, sway = 0.020f, swayRate = 0.50f, phase = 1.7f, alpha = 0.20f)
        // קרוב: גדול ומהיר, נוטה שמאלה, שקוף מאוד כדי לא להסתיר
        tile(near, t, scale = 1.25f, vx = -0.030f, vy = 0.19f, sway = 0.035f, swayRate = 0.70f, phase = 3.1f, alpha = 0.10f)
        gust(mid, far, t)
    }
}

private fun DrawScope.tile(
    image: ImageBitmap?, t: Float, scale: Float, vx: Float, vy: Float,
    sway: Float, swayRate: Float, phase: Float, alpha: Float
) {
    image ?: return
    val tw = size.width * scale
    val th = tw * image.height / image.width
    val ox = ((vx * t + sway * sin(t * swayRate + phase)) * size.width).mod(tw)
    val oy = (vy * t * size.height).mod(th)
    var y = oy - th
    while (y < size.height) {
        var x = ox - tw
        while (x < size.width) {
            drawImage(
                image,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset(x.toInt(), y.toInt()),
                dstSize = IntSize(tw.toInt() + 1, th.toInt() + 1),
                alpha = alpha
            )
            x += tw
        }
        y += th
    }
}

/** משב רוח: כל ~17 שניות ענן שלג עובר מצד לצד (הכיוון מתחלף) בחלק העליון והאמצעי של המסך */
private fun DrawScope.gust(a: ImageBitmap?, b: ImageBitmap?, t: Float) {
    val period = 17f
    val duration = 4.5f
    val k = floor(t / period).toInt()
    val local = t - k * period - 4f
    if (local < 0f || local > duration) return
    val p = local / duration
    val env = sin(p * PI.toFloat())
    val leftToRight = k % 2 == 0
    val top = size.height * (0.05f + 0.30f * ((k * 37) % 10) / 10f)
    val band = size.height * 0.5f
    val x = (if (leftToRight) -0.5f + 2.0f * p else 1.5f - 2.0f * p) * size.width
    val img = (if (k % 3 == 0) a else b) ?: a ?: return
    val w = size.width * 1.3f
    val h = w * img.height / img.width * 2f
    // כמה שכבות עם שוליים רכים (נחתכות לרצועה)
    clipBand(top, band) {
        drawImage(
            img,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset((x - w / 2f).toInt(), top.toInt()),
            dstSize = IntSize(w.toInt(), h.toInt().coerceAtLeast(band.toInt())),
            alpha = 0.30f * env
        )
    }
}

private fun DrawScope.clipBand(top: Float, height: Float, block: DrawScope.() -> Unit) {
    clipRect(0f, top, size.width, top + height) { block() }
}

private fun loadSnow(context: android.content.Context, name: String): ImageBitmap? {
    val id = context.resources.getIdentifier(name, "drawable", context.packageName)
    if (id == 0) return null
    val o = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }
    val bmp = BitmapFactory.decodeResource(context.resources, id, o) ?: return null
    bmp.density = Bitmap.DENSITY_NONE
    return bmp.asImageBitmap()
}

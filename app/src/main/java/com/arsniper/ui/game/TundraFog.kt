package com.arsniper.ui.game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor
import kotlin.math.sin

/**
 * ערפל הטונדרה: שני סוגי ערפל (fog_tundra_a, fog_tundra_b) שמופיעים, נשארים ונעלמים.
 * כל 10 שניות מתחיל ערפל חדש מהסוג השני, ולפני שהקודם נעלם (3 שניות חפיפה) הוא כבר מתחיל להופיע.
 * מצויר בשני מעברים: מאחורי המפלצות (front = false), ומעליהן (front = true), כך שהן עומדות בתוך הערפל:
 * חלק מהן נראות לפניו וחלק מוסתרות על ידו. במעבר הקדמי משתמשים בסוג השני ובשיקוף, כדי שהמעברים לא יהיו זהים.
 */
@Composable
fun TundraFog(nowNanos: Long, front: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val images = remember { listOf(load(context, "fog_tundra_a"), load(context, "fog_tundra_b")) }
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = nowNanos / 1e9f
        val current = floor(t / STEP).toInt()
        for (k in (current - 1)..current) {
            if (k < 0) continue
            val local = t - k * STEP
            val env = envelope(local)
            if (env <= 0.01f) continue
            val index = if (front) (k + 1) % 2 else k % 2
            val image = images[index] ?: continue
            // המעבר הקדמי: שיקוף ותנועה בכיוון הפוך
            val flip = if (front) (k % 2 == 0) else ((k / 2) % 2 == 1)
            val dir = if (front) -1f else 1f
            val drift = dir * sin(local * 0.30f + k * 1.7f) * size.width * 0.025f
            val alpha = env * if (front) FRONT_ALPHA else BACK_ALPHA
            val center = Offset(size.width / 2f, size.height / 2f)
            withTransform({
                scale(if (flip) -1.06f else 1.06f, 1.06f, center)
                translate(drift, 0f)
            }) {
                drawImage(
                    image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    alpha = alpha.coerceIn(0f, 1f)
                )
            }
        }
    }
}

/** כניסה (3 שניות), החזקה (7), יציאה (3): סה"כ 13 שניות לכל ערפל */
private fun envelope(local: Float): Float = when {
    local < 0f -> 0f
    local < FADE -> local / FADE
    local < STEP -> 1f
    local < STEP + FADE -> 1f - (local - STEP) / FADE
    else -> 0f
}

private const val STEP = 10f
private const val FADE = 3f
private const val BACK_ALPHA = 0.9f
private const val FRONT_ALPHA = 0.55f

private fun load(context: android.content.Context, name: String): ImageBitmap? {
    val id = context.resources.getIdentifier(name, "drawable", context.packageName)
    if (id == 0) return null
    val options = BitmapFactory.Options().apply {
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = BitmapFactory.decodeResource(context.resources, id, options) ?: return null
    bitmap.density = Bitmap.DENSITY_NONE
    return bitmap.asImageBitmap()
}

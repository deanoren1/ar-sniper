package com.arsniper.ui.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** מריחה אחת של סליים על המסך. x, y = מרכז כחלק מהמסך, size = קוטר כחלק מגובה המסך, age = שניות מאז הפגיעה */
data class SlimeSplat(val variant: Int, val x: Float, val y: Float, val size: Float, val rotation: Float, val age: Float = 0f, val spikes: Boolean = false)

/**
 * ירייה של החילזון שפגעה בשחקן: מריחה חצי שקופה על המסך שמטפטפת למטה ונעלמת.
 * הגיליון shot_gas_splats: 6 מריחות (עיגול, אופקית, שני טפטופים, אלכסונית, כתמים, שלושה טפטופים).
 */
object SlimeFx {
    const val VARIANTS = 6
    const val LIFE_SECONDS = 3.2f
    private const val MAX_ALPHA = 0.55f

    /** הטיפטוף ניכר רק במריחות עם טפטופים */
    val DRIPPING = setOf(2, 5)

    private fun decode(context: Context, name: String): ImageBitmap? {
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

    @Composable
    fun art(): ImageBitmap? {
        val context = LocalContext.current
        return remember { decode(context, "shot_gas_splats") }
    }

    /** קוצי הקיפוד תקועים במסך: shot_spike_splats, 6 צבירים (3 קוצים בשלושה הראשונים, 5 בשאר) */
    @Composable
    fun spikeArt(): ImageBitmap? {
        val context = LocalContext.current
        return remember { decode(context, "shot_spike_splats") }
    }

    private const val SPIKE_ALPHA = 0.9f

    /** קוצים: נתקעים בבת אחת ונשארים חדים, ודועכים בחצי השני של החיים */
    fun spikeAlphaAt(age: Float): Float = SPIKE_ALPHA * ramp(age, 0f, 0.04f) * (1f - ramp(age, LIFE_SECONDS * 0.55f, LIFE_SECONDS))

    /** צביר קוצים במקום שבו הקליע נגמר. גדול: 5 קוצים (3-5), קטן: 3 קוצים (0-2) */
    fun newSpikeSplat(random: kotlin.random.Random, x: Float, y: Float, big: Boolean): SlimeSplat = SlimeSplat(
        variant = if (big) 3 + random.nextInt(3) else random.nextInt(3),
        x = x.coerceIn(0.14f, 0.86f),
        y = y.coerceIn(0.28f, 0.7f),
        size = if (big) 0.95f + random.nextFloat() * 0.2f else 0.65f + random.nextFloat() * 0.15f,
        rotation = (random.nextFloat() - 0.5f) * 24f,
        spikes = true
    )

    private fun ramp(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

    fun alphaAt(age: Float): Float = MAX_ALPHA * ramp(age, 0f, 0.06f) * (1f - ramp(age, LIFE_SECONDS * 0.45f, LIFE_SECONDS))

    /**
     * מריחה במקום שבו הקליע נגמר (x, y = חלק מהמסך). בלי סיבוב: המריחה נוחתת ישרה ואז נוזלת למטה
     * (ראו SlimeSplatOverlay). נבחרות בעיקר מריחות עם טפטופים ועגולות, כי הן נראות הכי טוב כשהן נוזלות.
     * הכוונת נשארת גלויה בגלל החור שנחתך סביבה.
     */
    @Suppress("UNUSED_PARAMETER")
    fun newSplat(random: kotlin.random.Random, x: Float, y: Float, lineDeg: Float, big: Boolean): SlimeSplat {
        val pool = intArrayOf(0, 2, 2, 4, 5, 5, 1, 3)
        return SlimeSplat(
            variant = pool[random.nextInt(pool.size)],
            x = x.coerceIn(0.14f, 0.86f),
            y = y.coerceIn(0.28f, 0.7f),
            size = if (big) 1.45f + random.nextFloat() * 0.35f else 0.9f + random.nextFloat() * 0.25f,
            rotation = (random.nextFloat() - 0.5f) * 8f
        )
    }
}

/**
 * מצייר את המריחות מתחת לכוונת: מסביב לכוונת נחתך חור רך, כדי שאפשר יהיה להמשיך לכוון.
 * crosshair = מיקום הכוונת במסך, כחלק מהמסך.
 */
@Composable
fun SlimeSplatOverlay(splats: List<SlimeSplat>, crosshairX: Float, crosshairY: Float, modifier: Modifier = Modifier) {
    val slimeImage = SlimeFx.art()
    val spikeImage = SlimeFx.spikeArt()
    if (splats.isEmpty()) return
    Canvas(modifier = modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        for (sp in splats) {
            val image = (if (sp.spikes) spikeImage else slimeImage) ?: continue
            val cell = image.width / SlimeFx.VARIANTS
            val a = if (sp.spikes) SlimeFx.spikeAlphaAt(sp.age) else SlimeFx.alphaAt(sp.age)
            if (a <= 0.01f) continue
            val life = (sp.age / SlimeFx.LIFE_SECONDS).coerceIn(0f, 1f)
            // נחיתה: קטן ומתרחב במהירות. אחר כך נוזלת: נמתחת למטה (העוגן בקצה העליון) ויורדת, בהתחלה לאט ואז מהר
            // קוצים: "נתקעים" בהגדלה קטנה שמתכווצת פנימה. סליים: קטן שמתרחב
            val pop = if (sp.spikes) 1.18f - 0.18f * (1f - (1f - (sp.age / 0.1f).coerceIn(0f, 1f)).let { it * it })
            else 0.7f + 0.3f * (1f - (1f - (sp.age / 0.12f).coerceIn(0f, 1f)).let { it * it })
            val dia = sp.size * size.height * pop
            val flow = if (sp.spikes) 0f else ((sp.age - 0.35f) / (SlimeFx.LIFE_SECONDS - 0.35f)).coerceIn(0f, 1f).let { it * it }
            val dripping = !sp.spikes && sp.variant in SlimeFx.DRIPPING
            val stretch = 1f + (if (dripping) 0.65f else 0.4f) * flow
            val slide = size.height * (if (dripping) 0.22f else 0.14f) * flow
            val cx = sp.x * size.width
            val cy = sp.y * size.height + slide
            withTransform({
                rotate(sp.rotation, Offset(cx, cy))
                scale(1f, stretch, Offset(cx, cy - dia / 2f))
            }) {
                drawImage(
                    image,
                    srcOffset = IntOffset(sp.variant * cell, 0),
                    srcSize = IntSize(cell, image.height),
                    dstOffset = IntOffset((cx - dia / 2f).toInt(), (cy - dia / 2f).toInt()),
                    dstSize = IntSize(dia.toInt().coerceAtLeast(1), dia.toInt().coerceAtLeast(1)),
                    alpha = a
                )
            }
        }
        // חור רך סביב הכוונת
        val c = Offset(crosshairX * size.width, crosshairY * size.height)
        val hole = size.height * 0.16f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Black, Color.Black.copy(alpha = 0.85f), Color.Transparent),
                center = c,
                radius = hole
            ),
            radius = hole,
            center = c,
            blendMode = BlendMode.DstOut
        )
    }
}

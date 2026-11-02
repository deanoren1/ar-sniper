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
data class SlimeSplat(val variant: Int, val x: Float, val y: Float, val size: Float, val rotation: Float, val age: Float = 0f, val spikes: Boolean = false, val boom: Boolean = false, val fire: Boolean = false)

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

    /** פיצוץ בלון על המסך: shot_boom_splats, 6 פיצוצים (0-2 קטנים, 3-5 גדולים) */
    @Composable
    fun boomArt(): ImageBitmap? {
        val context = LocalContext.current
        return remember { decode(context, "shot_boom_splats") }
    }

    /** פיצוץ: מופיע בבת אחת, נשאר רגע ודועך מהר (חיים של כשנייה וחצי) */
    fun boomAlphaAt(age: Float): Float = 0.95f * ramp(age, 0f, 0.03f) * (1f - ramp(age, 0.45f, 1.5f))

    fun newBoomSplat(random: kotlin.random.Random, x: Float, y: Float, big: Boolean): SlimeSplat = SlimeSplat(
        variant = if (big) 3 + random.nextInt(3) else random.nextInt(3),
        x = x.coerceIn(0.14f, 0.86f),
        y = y.coerceIn(0.28f, 0.7f),
        size = if (big) 1.15f + random.nextFloat() * 0.2f else 0.8f + random.nextFloat() * 0.15f,
        rotation = (random.nextFloat() - 0.5f) * 20f,
        boom = true
    )

    /** פיצוץ כדור אש של הדרקון: מצויר בקוד (להבות, ליבה לוהטת, גחלים וצל פיח), לא פיצוץ הבלון */
    fun fireAlphaAt(age: Float): Float = 0.95f * ramp(age, 0f, 0.03f) * (1f - ramp(age, 0.5f, 1.4f))

    fun newFireSplat(random: kotlin.random.Random, x: Float, y: Float, big: Boolean): SlimeSplat = SlimeSplat(
        variant = random.nextInt(1000),
        x = x.coerceIn(0.14f, 0.86f),
        y = y.coerceIn(0.28f, 0.7f),
        size = if (big) 1.2f + random.nextFloat() * 0.2f else 0.8f + random.nextFloat() * 0.15f,
        rotation = random.nextFloat() * 360f,
        fire = true
    )

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
    val boomImage = SlimeFx.boomArt()
    if (splats.isEmpty()) return
    Canvas(modifier = modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        for (sp in splats) {
            if (sp.fire) {
                drawFireBurst(sp)
                continue
            }
            val image = (if (sp.boom) boomImage else if (sp.spikes) spikeImage else slimeImage) ?: continue
            val cell = image.width / SlimeFx.VARIANTS
            val a = if (sp.boom) SlimeFx.boomAlphaAt(sp.age) else if (sp.spikes) SlimeFx.spikeAlphaAt(sp.age) else SlimeFx.alphaAt(sp.age)
            if (a <= 0.01f) continue
            val life = (sp.age / SlimeFx.LIFE_SECONDS).coerceIn(0f, 1f)
            // נחיתה: קטן ומתרחב במהירות. אחר כך נוזלת: נמתחת למטה (העוגן בקצה העליון) ויורדת, בהתחלה לאט ואז מהר
            // קוצים: "נתקעים" בהגדלה קטנה שמתכווצת פנימה. סליים: קטן שמתרחב
            val pop = if (sp.boom) 0.6f + 0.5f * (1f - (1f - (sp.age / 0.18f).coerceIn(0f, 1f)).let { it * it }) + 0.08f * (sp.age / 1.5f).coerceIn(0f, 1f)
            else if (sp.spikes) 1.18f - 0.18f * (1f - (1f - (sp.age / 0.1f).coerceIn(0f, 1f)).let { it * it })
            else 0.7f + 0.3f * (1f - (1f - (sp.age / 0.12f).coerceIn(0f, 1f)).let { it * it })
            val dia = sp.size * size.height * pop
            val flow = if (sp.spikes || sp.boom) 0f else ((sp.age - 0.35f) / (SlimeFx.LIFE_SECONDS - 0.35f)).coerceIn(0f, 1f).let { it * it }
            val dripping = !sp.spikes && !sp.boom && sp.variant in SlimeFx.DRIPPING
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


/** פיצוץ כדור אש על המסך: הילה כתומה, צל פיח, לשונות להבה, ליבה לוהטת וגחלים שעפות החוצה */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFireBurst(sp: SlimeSplat) {
    val age = sp.age
    val a = SlimeFx.fireAlphaAt(age)
    if (a <= 0.01f) return
    val t = (age / 0.16f).coerceIn(0f, 1f)
    val pop = 0.55f + 0.45f * (1f - (1f - t) * (1f - t)) + 0.1f * (age / 1.4f).coerceIn(0f, 1f)
    val dia = sp.size * size.height * pop
    val c = Offset(sp.x * size.width, sp.y * size.height)
    // צל פיח מתחת
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1B0B00).copy(alpha = 0.4f * a), Color.Transparent),
            center = c, radius = dia * 0.62f
        ),
        radius = dia * 0.62f, center = c
    )
    // הילה כתומה
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFF8A00).copy(alpha = 0.6f * a), Color(0xFFD50000).copy(alpha = 0.25f * a), Color.Transparent),
            center = c, radius = dia * 0.8f
        ),
        radius = dia * 0.8f, center = c
    )
    // לשונות להבה: משולשים מחודדים, שכבה אדומה-כתומה ומעליה צהובה קטנה יותר
    val n = 11
    val seed = sp.variant
    for (layer in 0..1) {
        for (i in 0 until n) {
            val jitter = (((i * 37 + seed * 11) % 5) / 4f)
            val ang = Math.toRadians((sp.rotation + 360.0 * i / n))
            val len = dia * (0.36f + 0.2f * jitter) * (if (layer == 0) 1f else 0.62f)
            val half = dia * (if (layer == 0) 0.075f else 0.045f)
            val dx = kotlin.math.cos(ang).toFloat()
            val dy = kotlin.math.sin(ang).toFloat()
            val px = -dy
            val py = dx
            val base = dia * 0.1f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(c.x + dx * base + px * half, c.y + dy * base + py * half)
                lineTo(c.x + dx * len, c.y + dy * len)
                lineTo(c.x + dx * base - px * half, c.y + dy * base - py * half)
                close()
            }
            drawPath(path, if (layer == 0) Color(0xFFFF4A00).copy(alpha = 0.92f * a) else Color(0xFFFFC107).copy(alpha = 0.95f * a))
        }
    }
    // ליבה לוהטת
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = a), Color(0xFFFFF176).copy(alpha = 0.95f * a), Color(0xFFFF9800).copy(alpha = 0.7f * a), Color.Transparent),
            center = c, radius = dia * 0.3f
        ),
        radius = dia * 0.3f, center = c
    )
    // גחלים שעפות החוצה ודועכות
    val spread = ((age) / 0.9f).coerceIn(0f, 1f)
    for (i in 0 until 9) {
        val jitter = (((i * 53 + seed * 7) % 7) / 6f)
        val ang = Math.toRadians((sp.rotation * 0.7 + 360.0 * i / 9 + jitter * 25.0))
        val dist = dia * (0.42f + 0.3f * jitter + 0.35f * spread)
        val r = dia * (0.025f + 0.02f * (1f - spread))
        drawCircle(
            Color(0xFFFFD54F).copy(alpha = a * (1f - spread * 0.6f)),
            r,
            Offset(c.x + kotlin.math.cos(ang).toFloat() * dist, c.y + kotlin.math.sin(ang).toFloat() * dist)
        )
    }
}

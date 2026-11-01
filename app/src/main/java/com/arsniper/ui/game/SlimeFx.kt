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
data class SlimeSplat(val variant: Int, val x: Float, val y: Float, val size: Float, val rotation: Float, val age: Float = 0f)

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

    private fun decode(context: Context): ImageBitmap? {
        val id = context.resources.getIdentifier("shot_gas_splats", "drawable", context.packageName)
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
        return remember { decode(context) }
    }

    private fun ramp(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

    fun alphaAt(age: Float): Float = MAX_ALPHA * ramp(age, 0f, 0.06f) * (1f - ramp(age, LIFE_SECONDS * 0.45f, LIFE_SECONDS))

    /** זווית (במעלות, מסך) שבה כל מריחה "מצביעה" בציור המקורי: האופקית עולה מעט, האלכסונית עולה בחדות */
    private fun nativeAngle(variant: Int): Float = when (variant) { 1 -> -6f; 3 -> -33f; else -> 0f }

    /**
     * מריחה במקום שבו הקליע נגמר (x, y = חלק מהמסך), וכיוונה לפי כיוון התעופה שלו: lineDeg = זווית הקו
     * מהמפלצת אל הנקודה, במעלות על המסך (פיקסלים). כשהמפלצת בצד נבחרות בעיקר המריחות הארוכות,
     * והן מסתובבות לאורך הקו. מריחות עם טפטופים נשארות כמעט ישרות, כדי שהטיפות ירדו למטה.
     * הכוונת נשארת גלויה בגלל החור שנחתך סביבה (SlimeSplatOverlay).
     */
    fun newSplat(random: kotlin.random.Random, x: Float, y: Float, lineDeg: Float, big: Boolean): SlimeSplat {
        var line = lineDeg
        while (line > 90f) line -= 180f
        while (line <= -90f) line += 180f
        val sideShot = kotlin.math.abs(line) > 15f
        val variant = if (sideShot && random.nextFloat() < 0.6f) (if (random.nextBoolean()) 1 else 3) else random.nextInt(VARIANTS)
        val rotation = when (variant) {
            1, 3 -> (line - nativeAngle(variant)).coerceIn(-70f, 70f)
            2, 5 -> (line * 0.2f).coerceIn(-12f, 12f)
            else -> line * 0.3f + (random.nextFloat() - 0.5f) * 20f
        }
        return SlimeSplat(
            variant = variant,
            x = x.coerceIn(0.14f, 0.86f),
            y = y.coerceIn(0.3f, 0.75f),
            size = if (big) 1.45f + random.nextFloat() * 0.35f else 0.9f + random.nextFloat() * 0.25f,
            rotation = rotation
        )
    }
}

/**
 * מצייר את המריחות מתחת לכוונת: מסביב לכוונת נחתך חור רך, כדי שאפשר יהיה להמשיך לכוון.
 * crosshair = מיקום הכוונת במסך, כחלק מהמסך.
 */
@Composable
fun SlimeSplatOverlay(splats: List<SlimeSplat>, crosshairX: Float, crosshairY: Float, modifier: Modifier = Modifier) {
    val image = SlimeFx.art() ?: return
    if (splats.isEmpty()) return
    Canvas(modifier = modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val cell = image.width / SlimeFx.VARIANTS
        for (sp in splats) {
            val a = SlimeFx.alphaAt(sp.age)
            if (a <= 0.01f) continue
            val life = (sp.age / SlimeFx.LIFE_SECONDS).coerceIn(0f, 1f)
            // נחיתה: קטן ומתרחב במהירות; מריחות עם טפטופים נמתחות ויורדות
            val pop = 0.7f + 0.3f * (1f - (1f - (sp.age / 0.12f).coerceIn(0f, 1f)).let { it * it })
            val dia = sp.size * size.height * pop
            val dripping = sp.variant in SlimeFx.DRIPPING
            val stretch = if (dripping) 1f + 0.16f * life else 1f
            val slide = size.height * (if (dripping) 0.07f else 0.025f) * life
            val cx = sp.x * size.width
            val cy = sp.y * size.height + slide
            withTransform({
                rotate(sp.rotation * (1f - 0.6f * life), Offset(cx, cy))
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

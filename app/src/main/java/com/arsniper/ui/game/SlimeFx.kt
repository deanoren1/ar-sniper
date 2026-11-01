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
    private const val MAX_ALPHA = 0.7f

    /** הטיפטוף ניכר רק במריחות עם טפטופים */
    private val DRIPPING = setOf(2, 5)

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

    fun newSplat(random: kotlin.random.Random, avoidX: Float, avoidY: Float): SlimeSplat {
        var x = 0.5f
        var y = 0.5f
        // מרחיק את המריחה מהכוונת בזמן הפגיעה (גם אחר כך הכוונת נשארת גלויה: ראו SlimeSplatOverlay)
        for (attempt in 0 until 6) {
            x = 0.14f + random.nextFloat() * 0.72f
            y = 0.22f + random.nextFloat() * 0.56f
            if (kotlin.math.hypot(x - avoidX, (y - avoidY) * 0.56f) > 0.2f) break
        }
        return SlimeSplat(
            variant = random.nextInt(VARIANTS),
            x = x,
            y = y,
            size = 0.55f + random.nextFloat() * 0.25f,
            rotation = (random.nextFloat() - 0.5f) * 50f
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

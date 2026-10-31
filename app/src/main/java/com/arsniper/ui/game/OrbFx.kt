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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * אפקטי כדור האנרגיה (על רקע שחור, מצויירים בערבוב תוספת: שחור = שקוף):
 * שורה של 9 פריימי פיצוץ קטן (הקליע הופל באוויר), ושלושה פריימי פגיעה במסך מלא (הקליע פגע בשחקן).
 */
object OrbFx {
    const val HIT_FRAMES = 9
    const val IMPACT_FRAMES = 3

    /** כמה זמן נמשך אפקט הפגיעה במסך */
    const val IMPACT_SECONDS = 0.8f

    /** כמה זמן נמשך הפיצוץ הקטן */
    const val HIT_SECONDS = 0.55f

    class Art(val hit: ImageBitmap?, val impact: ImageBitmap?)

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
    fun art(): Art {
        val context = LocalContext.current
        return remember { Art(decode(context, "shot_orb_hit"), decode(context, "shot_orb_impact")) }
    }

    /** כמה הטשטוש חזק (0..1) בזמן age: נכנס מהר, מחזיק, ומתחדד בחזרה */
    fun blur(age: Float): Float = ramp(age, 0f, 0.12f) * (1f - ramp(age, 0.40f, IMPACT_SECONDS))

    /** עלייה ליניארית מ-0 ל-1 בין from ל-to */
    private fun ramp(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

    /** פיצוץ קטן במרכז (cx, cy) בגובה height; age = שניות מאז ההתחלה */
    fun DrawScope.drawHit(art: Art, cx: Float, cy: Float, height: Float, age: Float) {
        val image = art.hit ?: return
        if (age < 0f || age >= HIT_SECONDS) return
        val cellW = image.width / HIT_FRAMES
        val frame = ((age / HIT_SECONDS) * HIT_FRAMES).toInt().coerceIn(0, HIT_FRAMES - 1)
        val w = height * cellW / image.height
        drawImage(
            image = image,
            srcOffset = IntOffset(frame * cellW, 0),
            srcSize = IntSize(cellW, image.height),
            dstOffset = IntOffset((cx - w / 2f).toInt(), (cy - height / 2f).toInt()),
            dstSize = IntSize(w.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1)),
            alpha = 1f - 0.5f * ramp(age, HIT_SECONDS * 0.7f, HIT_SECONDS),
            blendMode = BlendMode.Plus
        )
    }
}

/**
 * הכדור פגע בשחקן: הבזק סגול במסך מלא, אחריו שני פריימים של עשן וניצוצות שדועכים.
 * שקוף למחצה ולא ממלא את המסך לגמרי, כדי שאפשר יהיה להמשיך לכוון. age = שניות מאז הפגיעה.
 */
/** סדק זכוכית: נקודות ביחידות של גובה המסך, יחסית למרכז */
private class Crack(val pts: List<Offset>, val thin: Boolean)

private fun makeCracks(): List<Crack> {
    val rnd = kotlin.random.Random(11)
    val rays = 16
    val rayPts = ArrayList<List<Offset>>()
    val out = ArrayList<Crack>()
    for (i in 0 until rays) {
        var ang = (2.0 * Math.PI * i / rays).toFloat() + (rnd.nextFloat() - 0.5f) * 0.25f
        var p = Offset.Zero
        val pts = arrayListOf(p)
        val segs = 8 + rnd.nextInt(4)
        for (k in 0 until segs) {
            ang += (rnd.nextFloat() - 0.5f) * 0.5f
            p += Offset(kotlin.math.cos(ang), kotlin.math.sin(ang)) * (0.085f * (0.7f + rnd.nextFloat() * 0.6f))
            pts.add(p)
            if (k >= 2 && rnd.nextFloat() < 0.3f) {
                val branch = arrayListOf(p)
                var a2 = ang + (if (rnd.nextBoolean()) 0.9f else -0.9f)
                var q = p
                for (j in 0 until 3) {
                    a2 += (rnd.nextFloat() - 0.5f) * 0.6f
                    q += Offset(kotlin.math.cos(a2), kotlin.math.sin(a2)) * (0.05f * (0.7f + rnd.nextFloat() * 0.6f))
                    branch.add(q)
                }
                out.add(Crack(branch, true))
            }
        }
        rayPts.add(pts)
        out.add(Crack(pts, false))
    }
    // טבעות שבורות בין הקרניים
    for (ring in intArrayOf(2, 4, 6)) {
        val pts = ArrayList<Offset>()
        for (i in 0..rays) pts.add(rayPts[i % rays][ring.coerceAtMost(rayPts[i % rays].size - 1)])
        out.add(Crack(pts, true))
    }
    return out
}

/**
 * הכדור פגע בשחקן: הבזק סגול במסך מלא, סדקים כמו בזכוכית, ואחריהם שני פריימים של עשן וניצוצות שדועכים
 * (ובמקביל העולם מטשטש ומתחדד, ראו OrbFx.blur). שקוף למחצה כדי שאפשר יהיה להמשיך לכוון.
 * age = שניות מאז הפגיעה.
 */
@Composable
fun OrbImpactOverlay(age: Float, modifier: Modifier = Modifier) {
    val art = OrbFx.art()
    val cracks = remember { makeCracks() }
    val image = art.impact
    if (age < 0f || age >= OrbFx.IMPACT_SECONDS) return
    fun ramp(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    val alphas = floatArrayOf(
        0.8f * ramp(age, 0f, 0.03f) * (1f - ramp(age, 0.12f, 0.26f)),
        0.65f * ramp(age, 0.10f, 0.18f) * (1f - ramp(age, 0.32f, 0.5f)),
        0.55f * ramp(age, 0.28f, 0.38f) * (1f - ramp(age, 0.5f, OrbFx.IMPACT_SECONDS))
    )
    val grow = ramp(age, 0f, 0.10f)
    val crackAlpha = (1f - ramp(age, 0.40f, OrbFx.IMPACT_SECONDS)).coerceIn(0f, 1f)
    Canvas(modifier = modifier.fillMaxSize()) {
        if (image != null) {
            val cellW = image.width / OrbFx.IMPACT_FRAMES
            for (i in 0 until OrbFx.IMPACT_FRAMES) {
                if (alphas[i] <= 0.01f) continue
                drawImage(
                    image = image,
                    srcOffset = IntOffset(i * cellW, 0),
                    srcSize = IntSize(cellW, image.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    alpha = alphas[i],
                    blendMode = BlendMode.Plus
                )
            }
        }
        // סדקי זכוכית: גדלים מהמרכז בהתחלה, ואז דועכים
        if (crackAlpha > 0.01f) {
            val center = Offset(size.width / 2f, size.height / 2f)
            for (c in cracks) {
                val count = (c.pts.size * grow).toInt().coerceAtLeast(2).coerceAtMost(c.pts.size)
                val path = Path()
                for (k in 0 until count) {
                    val pt = Offset(center.x + c.pts[k].x * size.height, center.y + c.pts[k].y * size.height)
                    if (k == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                }
                drawPath(path, Color(0xFFB040FF).copy(alpha = 0.45f * crackAlpha), style = Stroke(width = size.height * (if (c.thin) 0.008f else 0.014f), cap = StrokeCap.Round), blendMode = BlendMode.Plus)
                drawPath(path, Color.White.copy(alpha = 0.9f * crackAlpha), style = Stroke(width = size.height * (if (c.thin) 0.0022f else 0.0035f), cap = StrokeCap.Round), blendMode = BlendMode.Plus)
            }
        }
    }
}

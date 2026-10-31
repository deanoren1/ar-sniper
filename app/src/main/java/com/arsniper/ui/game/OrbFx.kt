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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    const val IMPACT_SECONDS = 0.5f

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
@Composable
fun OrbImpactOverlay(age: Float, modifier: Modifier = Modifier) {
    val art = OrbFx.art()
    val image = art.impact ?: return
    if (age < 0f || age >= OrbFx.IMPACT_SECONDS) return
    fun ramp(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    val alphas = floatArrayOf(
        0.8f * ramp(age, 0f, 0.03f) * (1f - ramp(age, 0.10f, 0.20f)),
        0.65f * ramp(age, 0.08f, 0.14f) * (1f - ramp(age, 0.22f, 0.32f)),
        0.55f * ramp(age, 0.20f, 0.28f) * (1f - ramp(age, 0.32f, OrbFx.IMPACT_SECONDS))
    )
    Canvas(modifier = modifier.fillMaxSize()) {
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
}

package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.arsniper.ml.SceneSession
import java.util.Random
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.arsniper.game.progress.ArenaStyle
import com.arsniper.game.progress.ParticleKind
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * שכבת הסגנון של הארנה: גוון צבע, חשכה בקצוות וחלקיקים נעים מעל התמונה.
 * nowNanos משנה את הציור בכל פריים. particleScale מקטין את מספר החלקיקים בתצוגות מקדימות.
 */
@Composable
fun ArenaStyleLayer(
    style: ArenaStyle,
    nowNanos: Long,
    modifier: Modifier = Modifier,
    particleScale: Float = 1f,
    /** מסגרת קדמית וכתמי רצפה (רק בקרב עצמו, ורק לארנות שיש להן נכסים) */
    props: Boolean = false
) {
    val context = LocalContext.current
    val assets = remember(style, props) { if (props) ArenaAssets.load(context, style) else null }
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = nowNanos / 1e9f
        // כתמי רצפה: מתחת לגוון, כדי שיהיו חלק מהתמונה
        assets?.let { drawDecals(it) }
        drawRect(Color(style.tint.toInt()).copy(alpha = style.tintAlpha))
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = style.vignette)),
                center = center,
                radius = max(size.width, size.height) * 0.75f
            )
        )
        // ערפל לאורך האופק, נושם לאט
        if (style.fog > 0f) {
            val a = style.fog * (0.85f + 0.15f * sin(t * 0.4f))
            val fogColor = Color(style.particleColor.toInt()).copy(alpha = a)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, fogColor, Color.Transparent),
                    startY = size.height * 0.34f,
                    endY = size.height * 0.78f
                )
            )
        }
        drawParticles(style, t, particleScale)
        // המסגרת הקדמית מעל הכול בשכבה הזאת (המפלצות והמחבואים מצוירים מעליה)
        assets?.frame?.let { frame ->
            val w = size.width * 1.08f
            val h = size.height * 1.08f
            drawImage(
                image = frame,
                dstOffset = IntOffset((-size.width * 0.04f).toInt(), (-size.height * 0.04f).toInt()),
                dstSize = IntSize(w.toInt(), h.toInt()),
                alpha = 0.97f
            )
        }
    }
}

/** נכסי הארנה: מסגרת וכתמי רצפה, אם קיימים בקבצי המשחק (arena_frame_<style>, arena_decal_<style>_<n>) */
private class ArenaAssets(val frame: ImageBitmap?, val decals: List<ImageBitmap>, val placed: List<Placed>) {
    companion object {
        private val FLOOR_LABELS = setOf(3, 6, 9, 11, 13, 28, 29, 46, 52, 91, 94)

        fun load(context: Context, style: ArenaStyle): ArenaAssets? {
            val res = context.resources
            val pkg = context.packageName
            val key = style.name.lowercase()
            fun id(name: String) = res.getIdentifier(name, "drawable", pkg)
            val frameId = id("arena_frame_$key")
            val frame = if (frameId != 0) ImageBitmap.imageResource(res, frameId) else null
            val decals = ArrayList<ImageBitmap>()
            var n = 0
            while (true) {
                val d = id("arena_decal_${key}_$n")
                if (d == 0) break
                decals.add(ImageBitmap.imageResource(res, d))
                n++
            }
            if (frame == null && decals.isEmpty()) return null
            return ArenaAssets(frame, decals, if (decals.isEmpty()) emptyList() else placeDecals(decals))
        }

        /** בוחר מקומות לכתמים: רק על רצפה לפי ניתוח התמונה, גדולים יותר ככל שקרוב למצלמה */
        private fun placeDecals(decals: List<ImageBitmap>): List<Placed> {
            val available = decals.size
            val analysis = SceneSession.analysis
            val rnd = Random()
            val out = ArrayList<Placed>()
            var tries = 0
            while (out.size < 5 && tries < 80) {
                tries++
                val x = 0.1f + 0.8f * rnd.nextFloat()
                val y = 0.55f + 0.4f * rnd.nextFloat()
                if (analysis != null && analysis.labelAt(x, y) !in FLOOR_LABELS) continue
                if (out.any { abs(it.x - x) < 0.18f && abs(it.y - y) < 0.12f }) continue
                val index = rnd.nextInt(available)
                val base = 0.2f * decals[index].width / 384f
                out.add(Placed(x, y, base * (0.7f + 0.8f * (y - 0.55f)), index))
            }
            return out
        }
    }
}

private class Placed(val x: Float, val y: Float, val width: Float, val index: Int)

private fun DrawScope.drawDecals(assets: ArenaAssets) {
    for (p in assets.placed) {
        val img = assets.decals.getOrNull(p.index) ?: continue
        val w = size.width * p.width
        val h = w * img.height / img.width * 0.38f // משטחים את הכתם כדי שיראה כאילו הוא על הרצפה
        drawImage(
            image = img,
            dstOffset = IntOffset((p.x * size.width - w / 2f).toInt(), (p.y * size.height - h / 2f).toInt()),
            dstSize = IntSize(w.toInt(), h.toInt()),
            alpha = 0.8f
        )
    }
}

private fun hash(i: Int, salt: Int): Float {
    val v = sin((i * 12.9898f) + salt * 78.233f) * 43758.547f
    return v - floor(v)
}

private fun frac(v: Float): Float = v - floor(v)

private fun DrawScope.drawParticles(style: ArenaStyle, t: Float, scale: Float) {
    val color = Color(style.particleColor.toInt())
    if (style.particleCount <= 0) return
    val count = (style.particleCount * scale).toInt().coerceAtLeast(6)
    val unit = size.minDimension * 0.004f
    for (i in 0 until count) {
        val r1 = hash(i, 1)
        val r2 = hash(i, 2)
        val r3 = hash(i, 3)
        when (style.particle) {
            ParticleKind.Snow -> {
                val y = frac(r2 + t * (0.05f + 0.09f * r3))
                val x = frac(r1 + sin(t * 0.6f + i) * 0.015f)
                drawCircle(color.copy(alpha = 0.85f), unit * (1f + 2.2f * r3), Offset(x * size.width, y * size.height))
            }
            ParticleKind.Rain -> {
                val speed = 0.55f + 0.4f * r3
                val y = frac(r2 + t * speed)
                val x = frac(r1 + y * 0.05f)
                val start = Offset(x * size.width, y * size.height)
                val end = Offset(start.x - size.height * 0.012f, start.y + size.height * 0.05f)
                drawLine(color.copy(alpha = 0.6f), start, end, strokeWidth = unit * (0.8f + r3), cap = StrokeCap.Round)
            }
            ParticleKind.Rise -> {
                val y = 1f - frac(r2 + t * (0.03f + 0.06f * r3))
                val x = frac(r1 + sin(t * 1.1f + i * 3f) * 0.02f)
                val twinkle = 0.35f + 0.65f * abs(sin(t * 2.4f + i))
                drawCircle(color.copy(alpha = twinkle), unit * (0.9f + 1.8f * r3), Offset(x * size.width, y * size.height))
            }
            ParticleKind.Drift -> {
                val x = frac(r1 + t * (0.02f + 0.05f * r3))
                val y = r2 + sin(t * 0.7f + i) * 0.02f
                drawCircle(color.copy(alpha = 0.35f + 0.3f * r3), unit * (0.8f + 1.6f * r3), Offset(x * size.width, y * size.height))
            }
        }
    }
}

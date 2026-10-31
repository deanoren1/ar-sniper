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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
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
    props: Boolean = false,
    /** false: בלי המסגרת הקדמית (היא מצוירת בנפרד, מעל המפלצות) */
    drawFrame: Boolean = true,
    /** true: רק המסגרת הקדמית (שכבה שמונחת מעל המפלצות, כדי שהן יהיו מאחוריה) */
    frameOnly: Boolean = false,
    /** > 0: השכבה מצוירת בתוך עולם רחב (פנורמה). הערך הוא יחס המסך: כתמי הרצפה והמסגרת נשארים בגודל מסך אמיתי ומונחים לאורך כל העולם */
    worldViewAspect: Float = 0f,
    /** true: רק מה שצמוד למסך (חלקיקים, ערפל, חשכת קצוות), בלי כתמי רצפה ובלי מסגרת */
    screenOnly: Boolean = false,
    /** >= 0: ארנה מעוצבת (בלי כתמי רצפה ישנים) עם גרסת מסגרת לבדיקה; גרסה בלי קובץ = בלי מסגרת */
    frameVariant: Int = -1
) {
    val context = LocalContext.current
    val assets = remember(style, props, frameOnly, frameVariant) { if (props) ArenaAssets.load(context, style, frameOnly, frameVariant) else null }
    val fog = remember(style, props, frameOnly, screenOnly) {
        if (props && !frameOnly && !screenOnly && style.fog > 0f && style != ArenaStyle.Tundra) buildDepthFog(style) else null
    }
    if (frameOnly) {
        val baked = remember(assets, worldViewAspect) {
            val frame = assets?.frame
            val analysis = SceneSession.analysis
            if (worldViewAspect > 0f && frame != null && analysis != null) {
                bakeWorldFrame(frame, analysis.width.toFloat() / analysis.height, worldViewAspect, style.ordinal)
            } else {
                null
            }
        }
        Canvas(modifier = modifier.fillMaxSize()) {
            if (worldViewAspect > 0f) {
                baked?.let { drawImage(it, srcOffset = IntOffset.Zero, srcSize = IntSize(it.width, it.height), dstSize = IntSize(size.width.toInt(), size.height.toInt()), alpha = 0.97f) }
            } else {
                drawArenaFrame(assets)
            }
        }
        return
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = nowNanos / 1e9f
        // כתמי רצפה: מתחת לגוון, כדי שיהיו חלק מהתמונה
        val worldMode = worldViewAspect > 0f
        if (!screenOnly) {
            assets?.let { drawDecals(it, if (worldMode) size.height * worldViewAspect else size.width) }
            val grade = gradeOf(style)
            if (grade == null) {
                drawRect(Color(style.tint.toInt()).copy(alpha = style.tintAlpha))
            } else {
                // תיקון צבע: גוון קל, צללים וגבהים בצבעים שונים, ערפל לפי עומק וקרני אור
                drawRect(Color(style.tint.toInt()).copy(alpha = style.tintAlpha * 0.5f))
                drawRect(lerp(Color.White, Color(grade.shadow.toInt()), 0.5f), blendMode = BlendMode.Multiply)
                drawRect(Color(grade.highlight.toInt()).copy(alpha = 0.28f), blendMode = BlendMode.Overlay)
                fog?.let { f ->
                    drawImage(f, srcOffset = IntOffset.Zero, srcSize = IntSize(f.width, f.height), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                }
                if (grade.rays) drawLightRays(Color(grade.highlight.toInt()), t, if (worldMode) worldViewAspect else size.width / size.height)
            }
        }
        if (worldMode) return@Canvas
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = style.vignette)),
                center = center,
                radius = max(size.width, size.height) * 0.75f
            )
        )
        // ערפל לאורך האופק, נושם לאט (בטונדרה הערפל מגיע מ-TundraFog)
        if (style.fog > 0f && style != ArenaStyle.Tundra) {
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
        if (drawFrame && !screenOnly) drawArenaFrame(assets)
    }
}

/** המסגרת הקדמית של הארנה (קישוטי הקצוות), מעל התמונה והמפלצות */
private fun DrawScope.drawArenaFrame(assets: ArenaAssets?) {
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

/** תיקון צבע לארנה: צבע לצללים, צבע לגבהים, ניגודיות ורוויה. null = בלי (קלאסי) */
private class Grade(val shadow: Long, val highlight: Long, val contrast: Float, val saturation: Float, val rays: Boolean)

private fun gradeOf(style: ArenaStyle): Grade? = when (style) {
    ArenaStyle.Classic -> null
    ArenaStyle.Tundra -> Grade(0xFFA9C8FF, 0xFFE8F4FF, 1.08f, 0.9f, true)
    ArenaStyle.Jungle -> Grade(0xFF7FB38A, 0xFFFFF0A0, 1.10f, 1.2f, true)
    ArenaStyle.Military -> Grade(0xFF8FA070, 0xFFFFE9B0, 1.10f, 0.85f, true)
    ArenaStyle.Ruins -> Grade(0xFFB58A5A, 0xFFFFD27A, 1.10f, 1.05f, true)
    ArenaStyle.Market -> Grade(0xFFC48A6A, 0xFFFFC27A, 1.08f, 1.2f, true)
    ArenaStyle.Cyber -> Grade(0xFF6A3AD0, 0xFF5CF2FF, 1.18f, 1.25f, false)
    ArenaStyle.Forest -> Grade(0xFF4AA088, 0xFFC8FFE0, 1.10f, 1.15f, true)
    ArenaStyle.Industrial -> Grade(0xFF8A5A3A, 0xFFFF9A3C, 1.15f, 0.95f, true)
}

/** מסנן צבע (ניגודיות ורוויה) לתמונת הרקע של הארנה */
fun arenaColorFilter(style: ArenaStyle): ColorFilter? {
    val g = gradeOf(style) ?: return null
    val m = ColorMatrix().apply { setToSaturation(g.saturation) }
    val k = g.contrast
    val o = 128f * (1f - k)
    m *= ColorMatrix(floatArrayOf(
        k, 0f, 0f, 0f, o,
        0f, k, 0f, 0f, o,
        0f, 0f, k, 0f, o,
        0f, 0f, 0f, 1f, 0f
    ))
    return ColorFilter.colorMatrix(m)
}

/** ערפל לפי עומק: המרוחק מתעמעם בצבע הארנה, הקרוב נשאר חד. נבנה פעם אחת מניתוח העומק */
private fun buildDepthFog(style: ArenaStyle): ImageBitmap? {
    val analysis = SceneSession.analysis ?: return null
    val w = analysis.width
    val h = analysis.height
    if (w <= 0 || h <= 0) return null
    // החלקה חזקה: בלי תפרים אנכיים שנוצרים בגבולות האריחים של ניתוח העומק
    val depth = smoothDepth(analysis.depth, w, h, (w / 24).coerceAtLeast(3))
    val sorted = depth.copyOf().also { it.sort() }
    val near = sorted[(sorted.size * 0.05f).toInt()]
    val far = sorted[(sorted.size * 0.95f).toInt()]
    val range = (far - near).coerceAtLeast(0.01f)
    val base = Color(style.particleColor.toInt())
    val tint = Color(style.tint.toInt())
    val fogColor = lerp(base, tint, 0.5f)
    val strength = (style.fog * 2.4f).coerceAtMost(0.6f)
    val px = IntArray(w * h)
    for (i in px.indices) {
        val t = ((depth[i] - near) / range).coerceIn(0f, 1f)
        val a = (t * t * (3f - 2f * t)) * strength
        px[i] = Color(fogColor.red, fogColor.green, fogColor.blue, a).toArgb()
    }
    return android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** קרני אור אלכסוניות ורכות שנושמות לאט */
private fun DrawScope.drawLightRays(color: Color, t: Float, aspect: Float) {
    val n = (aspect * 1.4f).toInt().coerceAtLeast(3)
    for (i in 0 until n) {
        val x0 = size.width * (i + 0.3f + 0.4f * hash(i, 7)) / n
        val w = size.height * (0.18f + 0.22f * hash(i, 8))
        val a = 0.10f + 0.07f * sin(t * 0.35f + i * 1.7f)
        withTransform({ rotate(-18f, Offset(x0, 0f)) }) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, color.copy(alpha = a), Color.Transparent),
                    startX = x0 - w / 2f, endX = x0 + w / 2f
                ),
                topLeft = Offset(x0 - w / 2f, -size.height * 0.2f),
                size = androidx.compose.ui.geometry.Size(w, size.height * 1.6f),
                blendMode = BlendMode.Screen
            )
        }
    }
}

/**
 * מסגרת בעולם רחב, נאפית פעם אחת לתמונה אחת לכל האורך: הקצה השמאלי והימני של המסגרת בקצוות העולם,
 * ובאמצע פסים אקראיים (גודל, מקום וכיוון) מהפס המרכזי של המסגרת, עם חפיפה רכה בין פס לפס.
 * ככה אין חזרה סימטרית כמו מראה, והתפרים לא נראים. הקצוות חלקים כי הפס הראשון והאחרון נשענים על קצה המסגרת.
 */
private fun bakeWorldFrame(frameImage: ImageBitmap, worldAspect: Float, viewAspect: Float, seed: Int): ImageBitmap? {
    return try {
        val frame = frameImage.asAndroidBitmap()
        val outH = 540
        val outW = (outH * worldAspect).toInt().coerceAtLeast(outH)
        val out = android.graphics.Bitmap.createBitmap(outW, outH, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        val fw = outH * 1.08f * viewAspect
        val fh = outH * 1.08f
        val ox = -outH * 0.04f * viewAspect
        val oy = -outH * 0.04f
        val edge = 0.30f
        val edgePx = (frame.width * edge).toInt()
        val dstEdge = fw * edge
        val scale = fw / frame.width
        fun draw(srcL: Int, srcW: Int, dstL: Float, dstW: Float, flip: Boolean, target: android.graphics.Canvas = canvas, dx: Float = 0f) {
            val src = android.graphics.Rect(srcL, 0, srcL + srcW, frame.height)
            val dst = android.graphics.RectF(dstL - dx, oy, dstL - dx + dstW, oy + fh)
            if (flip) {
                target.save()
                target.scale(-1f, 1f, dst.centerX(), 0f)
                target.drawBitmap(frame, src, dst, paint)
                target.restore()
            } else {
                target.drawBitmap(frame, src, dst, paint)
            }
        }
        val start = ox + dstEdge
        val end = outW - ox - dstEdge
        val midSrcX = edgePx
        val midSrcW = frame.width - 2 * edgePx
        val rnd = java.util.Random(seed.toLong() * 7919L + 13L)
        val feather = fw * 0.05f
        // אמצע: פסים אקראיים
        var x = start
        var first = true
        while (x < end - 1f) {
            val remaining = end - x
            var sw = (midSrcW * (0.45f + 0.35f * rnd.nextFloat())).toInt().coerceAtLeast(8)
            var srcL = midSrcX + rnd.nextInt((midSrcW - sw).coerceAtLeast(1))
            var dstW = sw * scale
            var flip = !first && rnd.nextBoolean()
            val last = remaining <= dstW + feather
            if (first) srcL = midSrcX
            if (last) {
                // הפס האחרון נגמר בדיוק במקום שבו מתחיל הקצה הימני
                dstW = remaining + feather
                sw = (dstW / scale).toInt().coerceIn(8, midSrcW)
                dstW = sw * scale
                srcL = midSrcX + midSrcW - sw
                flip = false
            }
            val pieceW = dstW.toInt().coerceAtLeast(2)
            val piece = android.graphics.Bitmap.createBitmap(pieceW, outH, android.graphics.Bitmap.Config.ARGB_8888)
            val pc = android.graphics.Canvas(piece)
            draw(srcL, sw, 0f, pieceW.toFloat(), flip, pc, dx = 0f)
            if (!first) {
                val mask = android.graphics.Paint().apply {
                    shader = android.graphics.LinearGradient(0f, 0f, feather, 0f, 0x00000000, -0x1, android.graphics.Shader.TileMode.CLAMP)
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
                }
                pc.drawRect(0f, 0f, pieceW.toFloat(), outH.toFloat(), mask)
            }
            canvas.drawBitmap(piece, if (first) x else x - feather, 0f, paint)
            piece.recycle()
            x += pieceW - (if (first) 0f else feather)
            first = false
            if (last) break
        }
        // הקצוות
        draw(0, edgePx, ox, dstEdge, false)
        draw(frame.width - edgePx, edgePx, outW - ox - dstEdge, dstEdge, false)
        out.asImageBitmap()
    } catch (e: Throwable) {
        null
    }
}

/** נכסי הארנה: מסגרת וכתמי רצפה, אם קיימים בקבצי המשחק (arena_frame_<style>, arena_decal_<style>_<n>) */
private class ArenaAssets(val frame: ImageBitmap?, val decals: List<ImageBitmap>, val placed: List<Placed>) {
    companion object {
        private val FLOOR_LABELS = setOf(3, 6, 9, 11, 13, 28, 29, 46, 52, 91, 94)

        fun load(context: Context, style: ArenaStyle, frameOnly: Boolean = false, variant: Int = -1): ArenaAssets? {
            val res = context.resources
            val pkg = context.packageName
            val key = style.name.lowercase()
            fun id(name: String) = res.getIdentifier(name, "drawable", pkg)
            val frameId = if (variant >= 0) id("arena_frame_${key}_v$variant") else id("arena_frame_$key")
            val frame = if (frameId != 0) ImageBitmap.imageResource(res, frameId) else null
            val decals = ArrayList<ImageBitmap>()
            var n = 0
            while (!frameOnly && variant < 0) {
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
            // פנורמה: יותר כתמים ופיזור על כל האורך (המרחקים ביחס למסך רגיל)
            val photoAspect = if (analysis != null) analysis.width.toFloat() / analysis.height else 2.1f
            val wide = photoAspect >= 2.6f
            val widthScale = if (wide) photoAspect / 2.1f else 1f
            val wanted = if (wide) (5 * widthScale).toInt() else 5
            while (out.size < wanted && tries < 80 * widthScale.toInt().coerceAtLeast(1)) {
                tries++
                val x = if (wide) 0.03f + 0.94f * rnd.nextFloat() else 0.1f + 0.8f * rnd.nextFloat()
                val y = 0.55f + 0.4f * rnd.nextFloat()
                if (analysis != null && analysis.labelAt(x, y) !in FLOOR_LABELS) continue
                if (out.any { abs(it.x - x) < 0.18f / widthScale && abs(it.y - y) < 0.12f }) continue
                val index = rnd.nextInt(available)
                val base = 0.2f * decals[index].width / 384f
                out.add(Placed(x, y, base * (0.7f + 0.8f * (y - 0.55f)), index))
            }
            return out
        }
    }
}

private class Placed(val x: Float, val y: Float, val width: Float, val index: Int)

private fun DrawScope.drawDecals(assets: ArenaAssets, refWidth: Float) {
    // הכתמים ממוקמים לפי קואורדינטות התמונה (רצפה לפי הניתוח). התמונה מוצגת במצב Crop, ולכן ממירים למסך
    val analysis = SceneSession.analysis
    val mapper = if (analysis != null && analysis.height > 0 && size.height > 0f) {
        com.arsniper.game.model.WorldMapper(analysis.width.toFloat() / analysis.height, size.width / size.height)
    } else {
        null
    }
    for (p in assets.placed) {
        val img = assets.decals.getOrNull(p.index) ?: continue
        val w = refWidth * p.width
        val h = w * img.height / img.width * 0.38f // משטחים את הכתם כדי שיראה כאילו הוא על הרצפה
        drawImage(
            image = img,
            dstOffset = IntOffset(
                ((mapper?.worldX(p.x) ?: p.x) * size.width - w / 2f).toInt(),
                ((mapper?.worldY(p.y) ?: p.y) * size.height - h / 2f).toInt()
            ),
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

/** טשטוש מלבני מופרד (פעמיים) של מפת העומק */
private fun smoothDepth(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
    var cur = src
    repeat(2) {
        val tmp = FloatArray(cur.size)
        for (y in 0 until h) {
            var sum = 0f
            var lo = 0
            var hi = -1
            for (x in 0 until w) {
                val newHi = minOf(w - 1, x + radius)
                while (hi < newHi) { hi++; sum += cur[y * w + hi] }
                val newLo = maxOf(0, x - radius)
                while (lo < newLo) { sum -= cur[y * w + lo]; lo++ }
                tmp[y * w + x] = sum / (hi - lo + 1)
            }
        }
        val out = FloatArray(cur.size)
        for (x in 0 until w) {
            var sum = 0f
            var lo = 0
            var hi = -1
            for (y in 0 until h) {
                val newHi = minOf(h - 1, y + radius)
                while (hi < newHi) { hi++; sum += tmp[hi * w + x] }
                val newLo = maxOf(0, y - radius)
                while (lo < newLo) { sum -= tmp[lo * w + x]; lo++ }
                out[y * w + x] = sum / (hi - lo + 1)
            }
        }
        cur = out
    }
    return cur
}

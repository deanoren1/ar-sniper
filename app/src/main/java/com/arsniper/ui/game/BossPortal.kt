package com.arsniper.ui.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.arsniper.game.engine.BossEntrance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * כניסת הבוס דרך פורטל בתוך הארנה (ראו BossEntrance לציר הזמן).
 * Back = מה שמאחורי הבוס (המערבולת עצמה), Front = מה שמולו (ערפל, עיניים זוהרות, הבזק, גל הלם ואבנים).
 * הכול מצויר סביב התיבה של הבוס, כך שהפורטל נפתח בדיוק במקום שבו הוא יעמוד.
 * בלי הקצאות אקראיות בכל פריים: המיקומים נגזרים ממספר האבן/הענן.
 */

/**
 * הציורים של הפורטל (מ-ChatGPT): מערבולת וענן ערפל על רקע שחור (מצוירים במיזוג Screen, השחור נעלם),
 * ואבני מקדש שקופות. חסר קובץ: null והפורטל מצויר רק בצורות.
 */
class BossPortalArt(val vortex: ImageBitmap?, val fog: ImageBitmap?, val rocks: List<ImageBitmap>) {
    companion object {
        fun load(context: Context): BossPortalArt {
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            fun open(name: String): ImageBitmap? {
                val id = context.resources.getIdentifier(name, "drawable", context.packageName)
                if (id == 0) return null
                return runCatching { BitmapFactory.decodeResource(context.resources, id, options)?.asImageBitmap() }.getOrNull()
            }
            return BossPortalArt(
                open("fx_portal_vortex"),
                open("fx_portal_fog"),
                (0 until 8).mapNotNull { open("fx_portal_rock_$it") }
            )
        }
    }
}

private fun DrawScope.drawArt(
    image: ImageBitmap,
    center: Offset,
    width: Float,
    height: Float,
    degrees: Float,
    alpha: Float,
    blend: BlendMode = BlendMode.SrcOver
) {
    if (alpha <= 0.003f || width < 1f || height < 1f) return
    rotate(degrees, pivot = center) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset((center.x - width / 2f).toInt(), (center.y - height / 2f).toInt()),
            dstSize = IntSize(width.toInt(), height.toInt()),
            alpha = alpha.coerceIn(0f, 1f),
            blendMode = blend
        )
    }
}

private val VOID = Color(0xFF07031A)
private val DEEP = Color(0xFF2A0F5E)
private val VIOLET = Color(0xFFB45CFF)
private val JADE = Color(0xFF6BFF5A)
private val MIST = Color(0xFFC9C2E0)

/** מיקום העיניים של הקולוסוס בתוך התיבה שלו: מרכז הפנים (שבר מחצי הרוחב), המרחק בין העיניים, גובה (שבר מלמעלה) */
private const val EYE_CX = 0.11f
private const val EYE_DX = 0.12f
private const val EYE_Y = 0.35f

private fun smooth(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** 0..1..0: כמה הפורטל פתוח */
private fun openness(age: Float): Float = when {
    age < 0f -> 0f
    age < BossEntrance.OPEN -> smooth(age / BossEntrance.OPEN)
    age < BossEntrance.BURST -> 1f
    age < BossEntrance.CLOSE -> {
        val c = (age - BossEntrance.BURST) / (BossEntrance.CLOSE - BossEntrance.BURST)
        (1f - c) * (1f - c)
    }
    else -> 0f
}

private fun hash(i: Int, salt: Int): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}

private fun portalCenter(box: MonsterBox) = Offset(box.cx, box.headY + box.height * 0.52f)

private fun portalRadius(box: MonsterBox) = max(box.halfWidth * 1.02f, box.height * 0.6f)

internal fun DrawScope.drawBossPortalBack(box: MonsterBox, age: Float, anim: Float, art: BossPortalArt?) {
    val o = openness(age)
    if (o <= 0.001f) return
    val c = portalCenter(box)
    val r = portalRadius(box) * (0.15f + 0.85f * o)
    val rx = r * 0.9f
    val ry = r * 1.08f

    // הילה רחבה סביב הפורטל
    drawCircle(
        Brush.radialGradient(
            0f to VIOLET.copy(alpha = 0.42f * o),
            0.55f to JADE.copy(alpha = 0.16f * o),
            1f to Color.Transparent,
            center = c,
            radius = r * 1.75f
        ),
        radius = r * 1.75f,
        center = c
    )
    // חור כהה עמוק: רק הפנים, השפה באה מהמערבולת
    drawOval(
        Brush.radialGradient(
            0f to VOID.copy(alpha = 0.97f * o),
            0.55f to DEEP.copy(alpha = 0.9f * o),
            0.9f to DEEP.copy(alpha = 0.35f * o),
            1f to Color.Transparent,
            center = c,
            radius = ry
        ),
        topLeft = Offset(c.x - rx, c.y - ry),
        size = Size(rx * 2f, ry * 2f)
    )
    val vortex = art?.vortex
    if (vortex != null) {
        // המערבולת המצוירת: שתי שכבות שמסתובבות במהירויות שונות (עומק), במיזוג Screen כך שהשחור שלה נעלם
        scale(scaleX = 1f, scaleY = 1.12f, pivot = c) {
            drawArt(vortex, c, r * 2.3f, r * 2.3f, -anim * 95f, o, BlendMode.Screen)
            drawArt(vortex, c, r * 1.55f, r * 1.55f, anim * 140f + 60f, 0.55f * o, BlendMode.Screen)
        }
    } else {
        // בלי הציור: זרועות ספירלה מנקודות
        val spin = anim * 3.4f
        for (arm in 0 until 4) {
            for (j in 0 until 34) {
                val f = j / 33f
                val ang = arm * (PI.toFloat() / 2f) + f * 4.8f - spin
                val rad = r * (0.06f + 0.88f * f)
                val p = Offset(c.x + cos(ang) * rad * 0.9f, c.y + sin(ang) * rad * 1.08f)
                drawCircle((if (arm % 2 == 0) JADE else VIOLET).copy(alpha = (0.1f + 0.55f * f) * o), radius = r * (0.012f + 0.03f * f), center = p)
            }
        }
    }
    // טבעת אנרגיה דקה שמסתובבת על השפה
    for (i in 0 until 2) {
        val k = 0.9f + i * 0.07f
        rotate(degrees = anim * (if (i % 2 == 0) 160f else -120f) + i * 47f, pivot = c) {
            drawArc(
                color = (if (i % 2 == 0) JADE else VIOLET).copy(alpha = 0.7f * o),
                startAngle = i * 70f,
                sweepAngle = 150f + 40f * i,
                useCenter = false,
                topLeft = Offset(c.x - rx * k, c.y - ry * k),
                size = Size(rx * 2f * k, ry * 2f * k),
                style = Stroke(width = r * 0.018f, cap = StrokeCap.Round)
            )
        }
    }
    // אבנים ועלים שנשאבים פנימה בזמן שהמערבולת נפתחת
    val rocks = art?.rocks.orEmpty()
    if (rocks.isNotEmpty() && age < BossEntrance.REVEAL) {
        for (i in 0 until 10) {
            val life = 1.1f
            val start = hash(i, 11) * BossEntrance.REVEAL
            var u = (age - start) / life
            if (u < 0f || u > 1f) continue
            u = u * u
            val ang = hash(i, 12) * 2f * PI.toFloat() + u * 3.2f
            val dist = r * (2.2f - 2.1f * u)
            val p = Offset(c.x + cos(ang) * dist, c.y + sin(ang) * dist * 0.8f)
            val sz = r * (0.09f + 0.07f * hash(i, 13)) * (1f - 0.7f * u)
            val img = rocks[i % rocks.size]
            drawArt(img, p, sz * 2f, sz * 2f * img.height / img.width, u * 540f + i * 40f, (1f - u) * 1.4f * o)
        }
    }
    // ברקים מהשפה החוצה, בזמן שהצללית מתקרבת
    if (age >= BossEntrance.OPEN * 0.6f && age < BossEntrance.BURST) {
        val seed = (age * 12f).toInt()
        for (b in 0 until 4) {
            if (hash(seed, b + 40) < 0.35f) continue
            val a0 = hash(seed, b) * 2f * PI.toFloat()
            val path = Path()
            var px = c.x + cos(a0) * rx * 0.92f
            var py = c.y + sin(a0) * ry * 0.92f
            path.moveTo(px, py)
            for (step in 1..6) {
                val out = r * 0.07f
                val jitter = (hash(seed * 7 + b, step) - 0.5f) * r * 0.12f
                px += cos(a0) * out - sin(a0) * jitter
                py += sin(a0) * out + cos(a0) * jitter
                path.lineTo(px, py)
            }
            drawPath(path, VIOLET.copy(alpha = 0.5f), style = Stroke(width = r * 0.04f, cap = StrokeCap.Round))
            drawPath(path, Color.White.copy(alpha = 0.9f), style = Stroke(width = r * 0.012f, cap = StrokeCap.Round))
        }
    }
}

internal fun DrawScope.drawBossPortalFront(box: MonsterBox, age: Float, anim: Float, art: BossPortalArt?) {
    if (age < 0f) return
    val c = portalCenter(box)
    val r0 = portalRadius(box)

    // ערפל: עננים שמסתחררים סביב הפורטל ומסתירים את הצללית; בפריצה הם נהדפים החוצה ונמוגים
    val fogIn = smooth(age / BossEntrance.OPEN)
    val blown = ((age - BossEntrance.REVEAL) / (BossEntrance.CLOSE + 0.5f - BossEntrance.REVEAL)).coerceIn(0f, 1f)
    val fog = fogIn * (1f - smooth(blown))
    if (fog > 0.01f) {
        val fogArt = art?.fog
        for (i in 0 until 11) {
            val base = i / 11f * 2f * PI.toFloat()
            val ang = base + anim * (0.55f + 0.2f * hash(i, 1)) * (if (age < BossEntrance.REVEAL) 1f else 0.3f)
            val inward = if (age < BossEntrance.REVEAL) 1.25f - 0.3f * fogIn else 1f + 2.2f * blown
            val dist = r0 * inward * (0.75f + 0.35f * hash(i, 2))
            val p = Offset(c.x + cos(ang) * dist * 1.1f, c.y + sin(ang) * dist * 0.75f + r0 * 0.2f)
            val br = r0 * (0.38f + 0.22f * hash(i, 3)) * (1f + blown)
            if (fogArt != null) {
                drawArt(fogArt, p, br * 2.4f, br * 2.0f, anim * (20f + 15f * hash(i, 4)) + i * 33f, 0.6f * fog, BlendMode.Screen)
            } else {
                drawCircle(
                    Brush.radialGradient(
                        0f to MIST.copy(alpha = 0.5f * fog),
                        0.6f to MIST.copy(alpha = 0.22f * fog),
                        1f to Color.Transparent,
                        center = p,
                        radius = br
                    ),
                    radius = br,
                    center = p
                )
            }
        }
        // ערפל נמוך על הרצפה, לרגלי הבוס
        val gw = r0 * (1.8f + 1.2f * blown)
        val gh = r0 * 0.35f
        drawOval(
            Brush.radialGradient(
                0f to MIST.copy(alpha = 0.5f * fog),
                1f to Color.Transparent,
                center = Offset(box.cx, box.feetY),
                radius = gw
            ),
            topLeft = Offset(box.cx - gw, box.feetY - gh),
            size = Size(gw * 2f, gh * 2f)
        )
    }

    // עיניים זוהרות של הצללית
    if (age >= BossEntrance.OPEN && age < BossEntrance.BURST) {
        val s = ((age - BossEntrance.OPEN) / (BossEntrance.REVEAL - BossEntrance.OPEN)).coerceIn(0f, 1f)
        val fade = if (age < BossEntrance.REVEAL) s else 1f - (age - BossEntrance.REVEAL) / (BossEntrance.BURST - BossEntrance.REVEAL)
        val grow = 0.72f + 0.24f * smooth(s)
        val eyeY = box.feetY - (box.feetY - box.headY - box.height * EYE_Y) * grow
        val pulse = 0.85f + 0.15f * sin(anim * 14f)
        for (side in listOf(-1f, 1f)) {
            val p = Offset(box.cx + (EYE_CX + side * EYE_DX) * box.halfWidth * grow, eyeY)
            val er = box.halfWidth * 0.09f * pulse
            drawCircle(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = fade),
                    0.25f to JADE.copy(alpha = 0.95f * fade),
                    1f to Color.Transparent,
                    center = p,
                    radius = er
                ),
                radius = er,
                center = p
            )
        }
    }

    // הפריצה: הבזק, גל הלם על הרצפה ואבנים שעפות לכל הכיוונים (גם אל המצלמה)
    val t = age - BossEntrance.REVEAL
    if (t >= 0f) {
        val f = (t / 0.45f).coerceIn(0f, 1f)
        if (f < 1f) {
            val fr = r0 * (0.6f + 2.6f * f)
            drawCircle(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.95f * (1f - f)),
                    0.4f to JADE.copy(alpha = 0.6f * (1f - f)),
                    1f to Color.Transparent,
                    center = c,
                    radius = fr
                ),
                radius = fr,
                center = c
            )
            drawRect(Color.White.copy(alpha = 0.4f * (1f - f) * (1f - f) * (1f - f)))
        }
        val w = (t / 0.8f).coerceIn(0f, 1f)
        if (w < 1f) {
            val wr = r0 * (0.5f + 3f * w)
            drawOval(
                color = JADE.copy(alpha = 0.7f * (1f - w)),
                topLeft = Offset(box.cx - wr, box.feetY - wr * 0.22f),
                size = Size(wr * 2f, wr * 0.44f),
                style = Stroke(width = r0 * 0.05f * (1f - w) + 1f)
            )
        }
        val rocks = art?.rocks.orEmpty()
        if (t < 1.4f) {
            val gravity = r0 * 4.2f
            for (i in 0 until 18) {
                val a = -PI.toFloat() / 2f + (i / 17f - 0.5f) * 3.1f + (hash(i, 5) - 0.5f) * 0.3f
                val v = r0 * (2.0f + 1.6f * hash(i, 6))
                // חלק מהאבנים עפות אל המצלמה: גדלות בזמן התעופה
                val toCamera = 1f + (if (i % 4 == 0) 2.2f * t else 0f)
                val px = c.x + cos(a) * v * t
                val py = c.y + r0 * 0.25f + sin(a) * v * t + gravity * t * t
                val sz = r0 * (0.06f + 0.08f * hash(i, 7)) * toCamera
                val fade = if (t < 1.05f) 1f else 1f - (t - 1.05f) / 0.35f
                val spin = t * (300f + 400f * hash(i, 8)) * (if (i % 2 == 0) 1f else -1f)
                if (rocks.isNotEmpty()) {
                    val img = rocks[i % rocks.size]
                    drawArt(img, Offset(px, py), sz * 2f, sz * 2f * img.height / img.width, spin, fade)
                } else {
                    rotate(degrees = spin, pivot = Offset(px, py)) {
                        drawRoundRect(
                            color = Color(0xFF8C8578).copy(alpha = fade),
                            topLeft = Offset(px - sz, py - sz * 0.8f),
                            size = Size(sz * 2f, sz * 1.6f),
                            cornerRadius = CornerRadius(sz * 0.35f)
                        )
                    }
                }
            }
        }
    }
}

package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.arsniper.R
import com.arsniper.game.engine.IceRealm
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** תזוזה אנכית עדינה של הבוס (שבר מגובה המסך). משותפת לציור ולבדיקת הפגיעה */
fun realmBob(time: Float): Float = sin(time * 2.1f) * 0.006f

private fun ease(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

/**
 * ציור עולם הקרח מעל הארנה: פורטל שנפתח, משיכה פנימה, עולם הקרח, צללית וחשיפת הבוס, והקרב עם היהלומים.
 * שכבה קבועה למסך (לא זזה עם המצלמה). nowNanos רק כדי שהציור יתרענן בכל פריים.
 */
@Composable
fun IceRealmLayer(realm: IceRealm, nowNanos: Long, modifier: Modifier = Modifier) {
    val world = ImageBitmap.imageResource(R.drawable.realm_world)
    val portal = ImageBitmap.imageResource(R.drawable.realm_portal)
    val idle = ImageBitmap.imageResource(R.drawable.realm_yeti_idle)
    val roar = ImageBitmap.imageResource(R.drawable.realm_yeti_roar)
    if (!realm.active) return
    Canvas(modifier = modifier.fillMaxSize()) {
        if (nowNanos < 0L) return@Canvas
        val stage = realm.stage
        val p = when (stage) {
            IceRealm.Stage.Portal -> realm.stageTime / IceRealm.PORTAL_SECONDS
            IceRealm.Stage.Pull -> realm.stageTime / IceRealm.PULL_SECONDS
            IceRealm.Stage.Shift -> realm.stageTime / IceRealm.SHIFT_SECONDS
            IceRealm.Stage.Silhouette -> realm.stageTime / IceRealm.SILHOUETTE_SECONDS
            IceRealm.Stage.Reveal -> realm.stageTime / IceRealm.REVEAL_SECONDS
            IceRealm.Stage.Dying -> realm.stageTime / IceRealm.DYING_SECONDS
            else -> 0f
        }.coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        val aspect = w / h
        val cx = w * 0.5f
        val cy = h * 0.46f

        when (stage) {
            IceRealm.Stage.Portal -> {
                drawRect(Color(0xFF3FA0FF).copy(alpha = 0.30f * ease(p)))
                val r = h * 0.21f * ease(p)
                drawPortal(portal, world, cx, cy, r, 1.7f, 1f, realm.time)
            }
            IceRealm.Stage.Pull -> {
                drawRect(Color(0xFF3FA0FF).copy(alpha = 0.30f))
                val e = ease(p)
                val r = h * 0.21f + (h * 2.6f - h * 0.21f) * e * e
                drawPortal(portal, world, cx, cy, r, 1.7f - 0.7f * e, 1f - e * e, realm.time)
                // פסי מהירות מהמרכז
                val n = 28
                for (i in 0 until n) {
                    val a = i * 2f * PI.toFloat() / n + realm.time * 0.6f
                    val r0 = h * (0.25f + 0.2f * ((i * 37) % 10) / 10f) * (1f + 2f * e)
                    val r1 = r0 + h * (0.25f + 0.5f * e)
                    drawLine(
                        Color.White.copy(alpha = 0.55f * e),
                        Offset(cx + cos(a) * r0, cy + sin(a) * r0),
                        Offset(cx + cos(a) * r1, cy + sin(a) * r1),
                        strokeWidth = 2f + 3f * e
                    )
                }
                drawRect(Color.White.copy(alpha = 0.55f * ease((p - 0.7f) / 0.3f)))
            }
            IceRealm.Stage.Shift -> {
                drawWorld(world, 1f)
                drawRect(Color(0xFFCFEFFF).copy(alpha = 0.75f * (1f - ease(p))))
            }
            IceRealm.Stage.Silhouette -> {
                drawWorld(world, 1f)
                val s = 0.78f + 0.07f * ease(p)
                drawBoss(idle, aspect, 0f, s, 1f, silhouette = 0.9f)
            }
            IceRealm.Stage.Reveal -> {
                drawWorld(world, 1f)
                val shake = sin(realm.time * 70f) * 7f * (1f - p)
                translate(left = shake) {
                    drawRoar(roar, idle, 0.85f + 0.15f * ease(p), silhouette = 1f - ease(p * 2f))
                }
                drawRect(Color.White.copy(alpha = 0.25f * (1f - ease(p * 1.5f))))
            }
            IceRealm.Stage.Fight, IceRealm.Stage.Dying -> {
                drawWorld(world, 1f)
                val bob = realmBob(realm.time)
                val dying = stage == IceRealm.Stage.Dying
                val stagger = if (realm.stagger > 0f) sin(realm.time * 55f) * 9f * (realm.stagger / IceRealm.STAGGER_SECONDS) else 0f
                val dyingShake = if (dying) sin(realm.time * 60f) * 10f * (1f - p) else 0f
                translate(left = stagger + dyingShake) {
                    val alpha = if (dying) (1f - ease((p - 0.35f) / 0.65f)) else 1f
                    drawBoss(idle, aspect, bob, 1f, alpha, flash = realm.hitFlash / 0.22f, warn = realm.windupFraction, dying = dying)
                    if (!dying) drawDiamonds(realm, aspect, bob)
                }
                if (dying) {
                    val pulse = (sin(realm.stageTime * 18f) * 0.5f + 0.5f)
                    drawRect(Color.White.copy(alpha = (0.35f * pulse * (1f - p) + 0.9f * ease((p - 0.8f) / 0.2f)).coerceIn(0f, 1f)))
                } else if (realm.windupFraction > 0f) {
                    // התראה אדומה בקצוות המסך בזמן מתקפה
                    val a = 0.28f * realm.windupFraction
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.Transparent, Color(0xFFFF2A2A).copy(alpha = a)),
                            center = Offset(cx, h * 0.5f), radius = w * 0.62f
                        )
                    )
                }
                if (!dying) drawBossBar(realm)
            }
            else -> Unit
        }
    }
}

/** העולם ממלא את המסך (crop), בהגדלה zoom סביב המרכז */
private fun DrawScope.drawWorld(world: ImageBitmap, zoom: Float) {
    val w = size.width
    val h = size.height
    val imgAspect = world.width.toFloat() / world.height
    // ממלאים את המסך בלי עיוות
    val srcW: Float
    val srcH: Float
    if (imgAspect > w / h) { srcH = world.height / zoom; srcW = srcH * (w / h) } else { srcW = world.width / zoom; srcH = srcW * (h / w) }
    drawImage(
        world,
        srcOffset = IntOffset(((world.width - srcW) / 2f).toInt(), ((world.height - srcH) / 2f).toInt()),
        srcSize = IntSize(srcW.toInt(), srcH.toInt()),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(w.toInt(), h.toInt())
    )
}

/** פורטל: עולם הקרח נראה בתוך העיגול, והטבעת המסתובבת סביבו */
private fun DrawScope.drawPortal(portal: ImageBitmap, world: ImageBitmap, cx: Float, cy: Float, r: Float, worldZoom: Float, ringAlpha: Float, time: Float) {
    if (r < 2f) return
    val path = Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - r * 0.94f, cy - r * 1.04f, cx + r * 0.94f, cy + r * 1.04f)) }
    clipPath(path) {
        drawWorld(world, worldZoom)
        drawRect(Brush.radialGradient(listOf(Color(0xFFBFEFFF).copy(alpha = 0.55f), Color.Transparent), center = Offset(cx, cy), radius = r * 0.7f))
    }
    val d = r * 2.35f
    val ringH = d * portal.height / portal.width
    rotate(-time * 80f, Offset(cx, cy)) {
        drawImage(
            portal,
            dstOffset = IntOffset((cx - d / 2f).toInt(), (cy - ringH / 2f).toInt()),
            dstSize = IntSize(d.toInt(), ringH.toInt()),
            alpha = ringAlpha.coerceIn(0f, 1f)
        )
    }
}

/** הבוס בפרופורציות של תיבת הבוס. scale סביב מרכז התחתית. silhouette 0..1 = כמה כהה, warn = אדום במתקפה, flash = לבן בפגיעה */
private fun DrawScope.drawBoss(
    img: ImageBitmap, aspect: Float, bob: Float, scale: Float, alpha: Float = 1f,
    silhouette: Float = 0f, flash: Float = 0f, warn: Float = 0f, dying: Boolean = false
) {
    val a = alpha.coerceIn(0f, 1f)
    val rect = IceRealm.bossRect(aspect, bob)
    val bw = rect[2] * size.width * scale
    val bh = rect[3] * size.height * scale
    val left = size.width * 0.5f - bw / 2f
    val top = (rect[1] + rect[3]) * size.height - bh
    val dst = IntOffset(left.toInt(), top.toInt())
    val dsz = IntSize(bw.toInt(), bh.toInt())
    if (silhouette > 0f) {
        drawImage(img, dstOffset = dst, dstSize = dsz, alpha = a * silhouette, colorFilter = ColorFilter.tint(Color(0xFF040A1C), BlendMode.SrcIn))
        if (silhouette >= 1f) return
    }
    drawImage(img, dstOffset = dst, dstSize = dsz, alpha = a * (1f - silhouette))
    if (warn > 0f) {
        val pulse = 0.5f + 0.5f * sin(warn * 24f)
        drawImage(img, dstOffset = dst, dstSize = dsz, alpha = a * (0.18f + 0.22f * pulse) * warn.coerceAtLeast(0.3f), colorFilter = ColorFilter.tint(Color(0xFFFF3030), BlendMode.SrcAtop))
    }
    if (flash > 0f) {
        drawImage(img, dstOffset = dst, dstSize = dsz, alpha = a * flash.coerceIn(0f, 1f) * 0.7f, colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcAtop))
    }
}

/** שאגה: תמונת השאגה באותה סקאלה של תמונת העמידה (פיקסל מקור = אותו גודל על המסך) */
private fun DrawScope.drawRoar(roar: ImageBitmap, idle: ImageBitmap, scale: Float, silhouette: Float) {
    val aspect = size.width / size.height
    val rect = IceRealm.bossRect(aspect, 0f)
    val pxPerSrc = rect[3] * size.height * scale / idle.height
    val bw = roar.width * pxPerSrc
    val bh = roar.height * pxPerSrc
    val left = size.width * 0.5f - bw / 2f
    val top = (rect[1] + rect[3]) * size.height - bh
    val dst = IntOffset(left.toInt(), top.toInt())
    val dsz = IntSize(bw.toInt(), bh.toInt())
    drawImage(roar, dstOffset = dst, dstSize = dsz, alpha = 1f - silhouette)
    if (silhouette > 0f) drawImage(roar, dstOffset = dst, dstSize = dsz, alpha = silhouette, colorFilter = ColorFilter.tint(Color(0xFF040A1C), BlendMode.SrcIn))
}

/** סימוני היהלומים: טבעת זוהרת, ביהלום שנדלק במתקפה טבעת מתכווצת. שבורים: טבעת אפורה עם X */
private fun DrawScope.drawDiamonds(realm: IceRealm, aspect: Float, bob: Float) {
    val rect = IceRealm.bossRect(aspect, bob)
    val unit = size.height
    val t = realm.time
    for ((i, d) in realm.diamonds.withIndex()) {
        val x = (rect[0] + d.x * rect[2]) * size.width
        val y = (rect[1] + d.y * rect[3]) * size.height
        val r = IceRealm.HIT_RADIUS * unit
        if (!d.alive) {
            val c = Color(0xFF7A8AA8).copy(alpha = 0.55f)
            drawCircle(c, r * 0.55f, Offset(x, y), style = Stroke(width = 3f))
            drawLine(c, Offset(x - r * 0.3f, y - r * 0.3f), Offset(x + r * 0.3f, y + r * 0.3f), strokeWidth = 3f)
            drawLine(c, Offset(x + r * 0.3f, y - r * 0.3f), Offset(x - r * 0.3f, y + r * 0.3f), strokeWidth = 3f)
            continue
        }
        val exposed = i == realm.exposed
        val pulse = 0.5f + 0.5f * sin(t * 4f + i)
        val base = if (exposed) Color(0xFFFFD34D) else Color(0xFF7FE6FF)
        drawCircle(
            Brush.radialGradient(listOf(base.copy(alpha = if (exposed) 0.7f else 0.18f + 0.12f * pulse), Color.Transparent), center = Offset(x, y), radius = r * 1.5f),
            r * 1.5f, Offset(x, y)
        )
        drawCircle(base.copy(alpha = if (exposed) 0.95f else 0.45f + 0.2f * pulse), r * 0.75f, Offset(x, y), style = Stroke(width = if (exposed) 6f else 3f))
        if (exposed) {
            val shrink = 1f - realm.windupFraction
            drawCircle(Color(0xFFFF4A3A).copy(alpha = 0.9f), r * (0.75f + 0.9f * shrink), Offset(x, y), style = Stroke(width = 5f))
        }
        // חיים שנשארו ביהלום: נקודות קטנות מתחתיו
        for (k in 0 until d.hp) {
            drawCircle(Color.White.copy(alpha = 0.85f), 4f, Offset(x + (k - (d.hp - 1) / 2f) * 14f, y + r * 0.95f))
        }
    }
}

/** מד חיים של הבוס: שם וקו, לפי סך החיים שנשארו ביהלומים */
private fun DrawScope.drawBossBar(realm: IceRealm) {
    val w = size.width
    val h = size.height
    val bw = w * 0.34f
    val bh = h * 0.028f
    val x = (w - bw) / 2f
    val y = h * 0.135f
    drawRoundRect(Color(0xCC081028), Offset(x - 6f, y - 6f), Size(bw + 12f, bh + 12f), androidx.compose.ui.geometry.CornerRadius(bh))
    val f = realm.fraction
    val color = if (f > 0.5f) Color(0xFFFFC04A) else Color(0xFFFF5A3A)
    drawRoundRect(color, Offset(x, y), Size(bw * f, bh), androidx.compose.ui.geometry.CornerRadius(bh / 2f))
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = android.graphics.Color.WHITE
        textSize = h * 0.032f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
    }
    drawContext.canvas.nativeCanvas.drawText("ICE BEHEMOTH", w / 2f, y - h * 0.018f, paint)
}

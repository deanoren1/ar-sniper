package com.arsniper.ui.game

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaPlayer
import android.os.Build
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arsniper.R
import com.arsniper.data.PlayerProfile
import com.arsniper.game.engine.BossFrame
import com.arsniper.game.engine.DangerZone
import com.arsniper.game.engine.IceRealm
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import com.arsniper.game.engine.BossClip
import com.arsniper.game.engine.DAZZLE_X
import com.arsniper.game.engine.DAZZLE_Y
import kotlin.math.cos
import kotlin.math.sin

/**
 * עולם הקרח (בוס ארנת הקרח): פורטל (סרטון 0) מעל הארנה, ואז סרטון הכניסה, ואז הבוס כווידאו שממלא את המסך (בלי עיוות),
 * ומעליו סימוני היהלומים, מד החיים והבזקים. שכבה קבועה למסך, מעל הארנה ומתחת לכוונת, לנשק ולממשק.
 * paused = המשחק עצור (תפריט עצירה / הפסד): הווידאו נעצר איתו.
 * nowNanos רק כדי שהשכבה תתרענן בכל פריים (וכך גם הסנכרון עם הנגן).
 */
@Composable
fun IceRealmLayer(
    realm: IceRealm,
    nowNanos: Long,
    paused: Boolean,
    shake: Float = 0f,
    floorAt: ((Float, Float) -> Boolean)? = null,
    worldAspect: Float = 2f,
    camMap: FloatArray = cameraMap(1f, 1f, 0.5f, 0.5f),
    aimX: Float = 0.5f,
    aimY: Float = 0.5f,
    modifier: Modifier = Modifier
) {
    if (!realm.visible) return
    val context = LocalContext.current

    // נגן אחד לכל הקרב; משתחרר כשהשכבה יורדת (ארנה נסגרה, החייאה, משחק חוזר, יציאה)
    val controller = remember { BossVideoController(context) }
    DisposableEffect(controller) {
        // סרטון חדש מתחיל מיד כשהמצב מתחלף (למשל תגובת פגיעה ברגע הירייה), בלי לחכות לפריים הבא
        realm.onClip = { c, token, speed -> controller.show(c, token, speed) }
        onDispose {
            realm.onClip = null
            controller.release()
        }
    }

    // מחזור חיים: האפליקציה ברקע = הווידאו נעצר, בלי לנגן ברקע
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> controller.setLifecyclePaused(true)
                Lifecycle.Event.ON_RESUME -> controller.setLifecyclePaused(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.setLifecyclePaused(true)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // סדקי הקרח על רצפת הארנה (לפי ניתוח התמונה), נבנים פעם אחת כשהבוס מתחיל
    val cracks = remember(floorAt, worldAspect) { FloorCracks.build(floorAt, worldAspect, seed = (System.nanoTime() and 0xFFFF).toInt()) }

    // סרטון 0: הפורטל, עם שקיפות (הרקע הירוק הוסר מראש), מעל הארנה. מתנגן פעם אחת. AnimatedImageDrawable קיים מאנדרואיד 9
    val portal = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                (ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, R.raw.boss_portal)) as? AnimatedImageDrawable)
                    ?.apply { repeatCount = 0 }
            }.getOrNull()
        } else {
            null
        }
    }
    val inPortal = realm.stage == IceRealm.Stage.Portal
    LaunchedEffect(inPortal) {
        if (inPortal && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) portal?.start()
    }
    // הצליל של סרטון 0 (האנימציה עצמה בלי קול)
    val portalSound = remember { if (PlayerProfile.soundOn) runCatching { MediaPlayer.create(context, R.raw.boss_portal_sfx) }.getOrNull() else null }
    LaunchedEffect(inPortal) {
        if (inPortal) runCatching { portalSound?.start() }
    }
    DisposableEffect(portalSound) {
        onDispose { runCatching { portalSound?.release() } }
    }

    // סנכרון בכל פריים: מה שמכונת המצבים רוצה לנגן, ומיקום הנגן בחזרה למכונה
    SideEffect {
        controller.setMuted(!PlayerProfile.soundOn)
        controller.setGamePaused(paused)
        val clip = realm.clip
        if (inPortal) {
            controller.preload(BossClip.Intro)
        } else if (clip != null) {
            controller.show(clip, realm.clipToken, realm.clipSpeed)
        }
        if (nowNanos >= 0L) realm.syncVideo(controller.syncToken(), controller.positionMs(), controller.isEnded())
    }

    // הווידאו מוצג מהפריים הראשון של סרטון הכניסה; עד אז רואים את הארנה
    val videoShown = !inPortal && controller.frameShownToken != -1
    realm.covering = videoShown

    // כשהבוס פוגע: כל השכבה רועדת (כמו הארנה), ומוגדלת קצת בזמן הרעידה כדי שלא ייראו קצוות
    val t = nowNanos / 1_000_000_000f
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                if (shake > 0f) {
                    translationX = sin(t * 83f) * shake * size.width * 0.012f
                    translationY = cos(t * 97f) * shake * size.height * 0.016f
                    val sc = 1f + 0.04f * shake.coerceAtMost(1f)
                    scaleX = sc
                    scaleY = sc
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = true
                    alpha = 0f
                    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> controller.fit() }
                    controller.attach(this)
                }
            },
            update = { v ->
                controller.attach(v)
                v.alpha = if (videoShown) 1f else 0f
            },
            modifier = Modifier.fillMaxSize()
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (nowNanos < 0L) return@Canvas
            val aspect = size.width / size.height

            // סדקי קרח זוהרים שמתפשטים על הרצפה (2 שניות), ונשארים מתחת לפורטל עד שסרטון הכניסה מופיע
            if (!videoShown && (realm.stage == IceRealm.Stage.Cracks || inPortal || realm.stage == IceRealm.Stage.Intro)) {
                val k = if (realm.stage == IceRealm.Stage.Cracks) realm.stageTime / IceRealm.CRACKS_SECONDS else 1f
                cracks.draw(this, k, camMap)
            }

            // סרטון 0: הפורטל, עד שהפריים הראשון של הכניסה מוכן (הפריים האחרון שלו זהה לראשון של הכניסה)
            if (portal != null && !videoShown && (inPortal || realm.stage == IceRealm.Stage.Intro)) {
                val pr = BossFrame.rect(aspect)
                drawIntoCanvas { c ->
                    portal.setBounds(
                        (pr[0] * size.width).toInt(), (pr[1] * size.height).toInt(),
                        ((pr[0] + pr[2]) * size.width).toInt(), ((pr[1] + pr[3]) * size.height).toInt()
                    )
                    portal.draw(c.nativeCanvas)
                }
            }

            val vulnerable = realm.vulnerable
            if (vulnerable) {
                val zones = realm.zonesOnScreen(aspect)
                if (zones != null) drawDiamonds(realm, zones)
                if (realm.stage == IceRealm.Stage.PreparingAttack) {
                    // התראה אדומה בקצוות המסך בזמן ההכנה למתקפה
                    val a = 0.08f + 0.22f * realm.prepareFraction
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.Transparent, Color(0xFFFF2A2A).copy(alpha = a)),
                            center = Offset(size.width * 0.5f, size.height * 0.5f), radius = size.width * 0.62f
                        )
                    )
                }
            }

            // אזור הסכנה של המתקפה, רגע לפני הפגיעה: לצאת ממנו עם הכוונת
            realm.warnZone?.let { drawDanger(it, realm.warnFraction, realm.time, aimX, aimY, realm.attacksSeen <= 3) }
            if (realm.dazzle > 0f) drawDazzle(realm.dazzle / IceRealm.DAZZLE_SECONDS)
            if (realm.dodgeFlash > 0f) drawDodged(realm.dodgeFlash / IceRealm.DODGE_FLASH_SECONDS)

            // פגיעה ביהלום: הבזק קטן במקום הפגיעה
            if (realm.hitFlash > 0f) {
                val k = realm.hitFlash / IceRealm.HIT_FLASH_SECONDS
                val c = Offset(realm.hitX * size.width, realm.hitY * size.height)
                val rad = size.height * (0.05f + 0.06f * (1f - k))
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f * k), Color(0xFF7FE6FF).copy(alpha = 0.4f * k), Color.Transparent), center = c, radius = rad),
                    rad, c
                )
            }

            // הבזק לבן קצר שמסתיר קפיצה בין סוף ההכנה לתחילת מתקפה
            if (realm.cutFlash > 0f) {
                drawRect(Color.White.copy(alpha = 0.85f * (realm.cutFlash / IceRealm.CUT_FLASH_SECONDS)))
            }

            if (realm.fighting) drawBossBar(realm)
        }
    }
}

/**
 * אזור הסכנה, רגע לפני מכה: אם הכוונת בפנים הוא אדום ומהבהב, עם "MOVE!" וחץ גדול לכיוון היציאה הקרובה;
 * כשהכוונת יצאה הוא נהיה כחול-בהיר עם "SAFE". בשלוש המתקפות הראשונות מוצג גם הסבר קצר.
 */
private fun DrawScope.drawDanger(zone: DangerZone, k: Float, time: Float, aimX: Float, aimY: Float, tutorial: Boolean) {
    val r = BossFrame.rect(size.width / size.height)
    val vx = (aimX - r[0]) / r[2]
    val vy = (aimY - r[1]) / r[3]
    val inside = zone.contains(vx, vy)
    val pulse = 0.5f + 0.5f * sin(time * (10f + 20f * k))
    val fillC = if (inside) Color(0xFFFF2A2A).copy(alpha = 0.22f + 0.25f * k + 0.1f * pulse) else Color(0xFF9FDFFF).copy(alpha = 0.12f)
    val edge = if (inside) Color(0xFFFF6A4A).copy(alpha = 0.7f + 0.3f * pulse) else Color(0xFFBFEFFF).copy(alpha = 0.5f)
    val sw = 7f
    fun sx(fx: Float) = BossFrame.fracToScreenX(fx, r) * size.width
    fun sy(fy: Float) = BossFrame.fracToScreenY(fy, r) * size.height
    when (zone) {
        is DangerZone.Box -> {
            val tl = Offset(sx(zone.l), sy(zone.t))
            val sz = Size(sx(zone.r) - tl.x, sy(zone.b) - tl.y)
            drawRect(fillC, tl, sz)
            drawRect(edge, tl, sz, style = Stroke(width = sw))
        }
        is DangerZone.Circle -> {
            val c = Offset(sx(zone.cx), sy(zone.cy))
            val rad = zone.radius * r[3] * size.height
            drawCircle(fillC, rad, c)
            drawCircle(edge, rad, c, style = Stroke(width = sw))
        }
        is DangerZone.Band -> {
            val hpx = zone.half * r[3] * size.height
            val ax = sx(zone.x0); val ay = sy(zone.y0); val bx = sx(zone.x1); val by = sy(zone.y1)
            val len = kotlin.math.hypot(bx - ax, by - ay)
            val nx = -(by - ay) / len * hpx
            val ny = (bx - ax) / len * hpx
            val path = Path().apply {
                moveTo(ax + nx, ay + ny); lineTo(bx + nx, by + ny); lineTo(bx - nx, by - ny); lineTo(ax - nx, ay - ny); close()
            }
            drawPath(path, fillC)
            drawPath(path, edge, style = Stroke(width = sw))
        }
    }
    val cx = aimX * size.width
    val cy = aimY * size.height
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(10f, 0f, 3f, android.graphics.Color.BLACK)
    }
    if (inside) {
        // הכיוון הקצר ביותר החוצה (בודקים 16 כיוונים מהכוונת)
        var bestA = 0f
        var bestD = Float.MAX_VALUE
        for (i in 0 until 16) {
            val a = i / 16f * 2f * kotlin.math.PI.toFloat()
            var d = 0.02f
            while (d < 1.2f && zone.contains(vx + cos(a) * d / BossFrame.VIDEO_ASPECT, vy + sin(a) * d)) d += 0.02f
            if (d < bestD) { bestD = d; bestA = a }
        }
        val dirX = cos(bestA)
        val dirY = sin(bestA)
        val len = size.height * 0.16f
        val start = Offset(cx + dirX * size.height * 0.07f, cy + dirY * size.height * 0.07f)
        val end = Offset(start.x + dirX * len, start.y + dirY * len)
        val arrow = Color.White.copy(alpha = 0.75f + 0.25f * pulse)
        drawLine(arrow, start, end, strokeWidth = 12f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val back = 0.42f * len
        val wing = 0.6f
        val ex = end.x - dirX * back
        val ey = end.y - dirY * back
        drawLine(arrow, end, Offset(ex - dirY * back * wing, ey + dirX * back * wing), strokeWidth = 12f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(arrow, end, Offset(ex + dirY * back * wing, ey - dirX * back * wing), strokeWidth = 12f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        paint.color = android.graphics.Color.argb(255, 255, 90, 70)
        paint.textSize = size.height * 0.075f
        drawContext.canvas.nativeCanvas.drawText("MOVE!", cx, cy - size.height * 0.1f, paint)
    } else {
        paint.color = android.graphics.Color.argb(230, 170, 240, 255)
        paint.textSize = size.height * 0.06f
        drawContext.canvas.nativeCanvas.drawText("SAFE", cx, cy - size.height * 0.1f, paint)
    }
    if (tutorial) {
        paint.color = android.graphics.Color.WHITE
        paint.textSize = size.height * 0.042f
        drawContext.canvas.nativeCanvas.drawText("Move the crosshair out of the red zone with the joystick!", size.width / 2f, size.height * 0.33f, paint)
    }
}

/**
 * סינוור מכדור הקרח (סרטון 6): אור לבן-תכלת חזק בצד ימין, איפה שהכדור פוגע, ולא במרכז.
 * k = 1 בתחילת הסינוור ויורד ל-0. נדלק מהר, נשאר רגע, ודועך.
 */
private fun DrawScope.drawDazzle(k: Float) {
    val r = BossFrame.rect(size.width / size.height)
    val c = Offset(BossFrame.fracToScreenX(DAZZLE_X, r) * size.width, BossFrame.fracToScreenY(DAZZLE_Y, r) * size.height)
    val a = if (k > 0.85f) (1f - k) / 0.15f else (k / 0.85f)
    val a2 = a * a * (3f - 2f * a)
    val rad = size.width * (0.55f + 0.25f * (1f - k))
    drawRect(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.97f * a2), Color(0xFFCFF4FF).copy(alpha = 0.8f * a2), Color(0xFF7FD8FF).copy(alpha = 0.35f * a2), Color.Transparent),
            center = c, radius = rad
        )
    )
    drawRect(Color.White.copy(alpha = 0.22f * a2))
}

/** "DODGE!" באמצע המסך אחרי התחמקות */
private fun DrawScope.drawDodged(k: Float) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb((255 * k.coerceIn(0f, 1f)).toInt(), 0x9F, 0xF0, 0xFF)
        textSize = size.height * (0.09f + 0.02f * (1f - k))
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(10f, 0f, 3f, android.graphics.Color.BLACK)
    }
    drawContext.canvas.nativeCanvas.drawText("DODGE!", size.width / 2f, size.height * 0.42f, paint)
}

/** סימוני היהלומים במיקום שלהם על הבוס: טבעת זוהרת; הדלוק בהכנה בזהב עם טבעת אדומה שמתכווצת; שבור = X אפור */
private fun DrawScope.drawDiamonds(realm: IceRealm, z: FloatArray) {
    val t = realm.time
    for ((i, d) in realm.diamonds.withIndex()) {
        val idx = d.zone.index
        val x = z[idx * 3] * size.width
        val y = z[idx * 3 + 1] * size.height
        val r = z[idx * 3 + 2] * size.height * 0.55f
        if (!d.alive) {
            val c = Color(0xFF7A8AA8).copy(alpha = 0.55f)
            drawCircle(c, r * 0.45f, Offset(x, y), style = Stroke(width = 3f))
            drawLine(c, Offset(x - r * 0.25f, y - r * 0.25f), Offset(x + r * 0.25f, y + r * 0.25f), strokeWidth = 3f)
            drawLine(c, Offset(x + r * 0.25f, y - r * 0.25f), Offset(x - r * 0.25f, y + r * 0.25f), strokeWidth = 3f)
            continue
        }
        val exposed = i == realm.exposed
        val pulse = 0.5f + 0.5f * sin(t * 4f + i)
        val base = if (exposed) Color(0xFFFFD34D) else Color(0xFF7FE6FF)
        drawCircle(
            Brush.radialGradient(listOf(base.copy(alpha = if (exposed) 0.55f else 0.10f + 0.08f * pulse), Color.Transparent), center = Offset(x, y), radius = r * 1.4f),
            r * 1.4f, Offset(x, y)
        )
        drawCircle(base.copy(alpha = if (exposed) 0.95f else 0.35f + 0.2f * pulse), r * 0.8f, Offset(x, y), style = Stroke(width = if (exposed) 6f else 3f))
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
    drawRoundRect(Color(0xCC081028), Offset(x - 6f, y - 6f), Size(bw + 12f, bh + 12f), CornerRadius(bh))
    val f = realm.fraction
    val color = if (f > 0.5f) Color(0xFFFFC04A) else Color(0xFFFF5A3A)
    drawRoundRect(color, Offset(x, y), Size(bw * f, bh), CornerRadius(bh / 2f))
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

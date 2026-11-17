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
    bossZoom: Float = 1f,
    bossCenterX: Float = 0.5f,
    bossCenterY: Float = 0.5f,
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
    // זום על הבוס: כשהווידאו מכסה, הבוס הוא "העולם", והמצלמה (זום + מרכז שעוקב אחרי הכוונת) מגדילה אותו
    val bz = if (videoShown) bossZoom.coerceAtLeast(1f) else 1f
    val bcx = if (videoShown) bossCenterX else 0.5f
    val bcy = if (videoShown) bossCenterY else 0.5f
    Box(modifier = modifier.fillMaxSize()) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                val sc = bz * (1f + 0.04f * shake.coerceIn(0f, 1f))
                scaleX = sc
                scaleY = sc
                // מרכז ההגדלה בשולי הרעידה: מגדילים מעט כדי שלא ייראו קצוות
                val shakeX = if (shake > 0f) sin(t * 83f) * shake * size.width * 0.012f - 0.02f * shake.coerceIn(0f, 1f) * size.width * bz else 0f
                val shakeY = if (shake > 0f) cos(t * 97f) * shake * size.height * 0.016f - 0.02f * shake.coerceIn(0f, 1f) * size.height * bz else 0f
                translationX = (0.5f - bcx * bz) * size.width + shakeX
                translationY = (0.5f - bcy * bz) * size.height + shakeY
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

            // מטרות החסימה של המתקפה: לפגוע בהן לפני המכה
            if (realm.targetsActive) drawTargets(realm)
            if (realm.dazzle > 0f) drawDazzle(realm.dazzle / IceRealm.DAZZLE_SECONDS)

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

        }
    }
        // שכבת מסך (לא מוגדלת): מד חיים, "BLOCKED!", והסבר בהתחלה
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (nowNanos < 0L) return@Canvas
            if (realm.fighting) drawBossBar(realm)
            if (realm.dodgeFlash > 0f) drawBlocked(realm.dodgeFlash / IceRealm.DODGE_FLASH_SECONDS)
            if (realm.targetsActive && realm.attacksSeen <= 3) {
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                    color = android.graphics.Color.WHITE
                    textSize = size.height * 0.045f
                    setShadowLayer(10f, 0f, 3f, android.graphics.Color.BLACK)
                }
                drawContext.canvas.nativeCanvas.drawText("Shoot the target to block the attack!", size.width / 2f, size.height * 0.2f, paint)
            }
        }
    }
}

/**
 * מטרת החסימה: עיגול קרח זוהר עם מרכז, וטבעת אדומה שמתכווצת עד רגע המכה.
 * בזעם יש שתיים ברצף: הנוכחית בולטת, הבאה מוצגת חלשה עם מספר.
 */
private fun DrawScope.drawTargets(realm: IceRealm) {
    val h = size.height
    val left = realm.targetTimeLeft
    val pulse = 0.5f + 0.5f * sin(realm.time * (12f + 16f * (1f - left)))
    for (i in realm.targetIndex until realm.targetCount) {
        val c = Offset(realm.targets[i * 2] * size.width, realm.targets[i * 2 + 1] * h)
        val r = realm.targetRadius * h
        val current = i == realm.targetIndex
        val a = if (current) 1f else 0.35f
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFBFF4FF).copy(alpha = 0.55f * a), Color(0xFF2EA8FF).copy(alpha = 0.25f * a), Color.Transparent), center = c, radius = r * 2.2f),
            r * 2.2f, c
        )
        drawCircle(Color.White.copy(alpha = 0.95f * a), r, c, style = Stroke(width = 5f))
        drawCircle(Color(0xFF7FE6FF).copy(alpha = 0.9f * a), r * 0.55f, c, style = Stroke(width = 4f))
        drawCircle(Color.White.copy(alpha = a), r * 0.16f, c)
        if (current) {
            // הזמן שנשאר: טבעת אדומה שמתכווצת אל המטרה
            drawCircle(Color(0xFFFF4A3A).copy(alpha = 0.6f + 0.4f * pulse), r * (1.15f + 1.6f * left), c, style = Stroke(width = 6f))
        } else {
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
                color = android.graphics.Color.WHITE
                textSize = r * 1.1f
            }
            drawContext.canvas.nativeCanvas.drawText("${i + 1}", c.x, c.y + r * 0.38f, paint)
        }
    }
}

/** "BLOCKED!" באמצע המסך אחרי חסימה */
private fun DrawScope.drawBlocked(k: Float) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb((255 * k.coerceIn(0f, 1f)).toInt(), 0x6F, 0xF0, 0xFF)
        textSize = size.height * (0.1f + 0.02f * (1f - k))
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(14f, 0f, 4f, android.graphics.Color.BLACK)
    }
    drawContext.canvas.nativeCanvas.drawText("BLOCKED!", size.width / 2f, size.height * 0.42f, paint)
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


/**
 * נקודות התורפה (היהלומים) על הבוס: טבעת כפולה ברורה עם זוהר ויהלום קטן במרכז, וחיים שנשארו כנקודות.
 * הזהוב (בהכנה למתקפה) = נזק כפול. שבור = X אפור.
 */
private fun DrawScope.drawDiamonds(realm: IceRealm, z: FloatArray) {
    val t = realm.time
    for ((i, d) in realm.diamonds.withIndex()) {
        val idx = d.zone.index
        val x = z[idx * 3] * size.width
        val y = z[idx * 3 + 1] * size.height
        val r = z[idx * 3 + 2] * size.height * 0.55f
        val c = Offset(x, y)
        val sw = size.height * 0.008f
        if (!d.alive) {
            val g = Color(0xFF8A96B0).copy(alpha = 0.7f)
            drawCircle(g, r * 0.5f, c, style = Stroke(width = 4f))
            drawLine(g, Offset(x - r * 0.3f, y - r * 0.3f), Offset(x + r * 0.3f, y + r * 0.3f), strokeWidth = 4f)
            drawLine(g, Offset(x + r * 0.3f, y - r * 0.3f), Offset(x - r * 0.3f, y + r * 0.3f), strokeWidth = 4f)
            continue
        }
        val exposed = i == realm.exposed
        val pulse = 0.5f + 0.5f * sin(t * 5f + i)
        val base = if (exposed) Color(0xFFFFD34D) else Color(0xFF8FF0FF)
        drawCircle(
            Brush.radialGradient(listOf(base.copy(alpha = 0.5f + 0.25f * pulse), base.copy(alpha = 0.15f), Color.Transparent), center = c, radius = r * 1.7f),
            r * 1.7f, c
        )
        drawCircle(Color(0xFF06182E).copy(alpha = 0.75f), r * 0.86f, c, style = Stroke(width = sw * 2.2f))
        drawCircle(base, r * 0.86f, c, style = Stroke(width = if (exposed) sw * 1.6f else sw * 1.2f))
        drawCircle(Color.White.copy(alpha = 0.6f + 0.4f * pulse), r * 0.86f * (1.1f + 0.12f * pulse), c, style = Stroke(width = sw * 0.5f))
        // יהלום קטן במרכז
        val dr = r * 0.28f
        val gem = Path().apply { moveTo(x, y - dr); lineTo(x + dr * 0.75f, y); lineTo(x, y + dr); lineTo(x - dr * 0.75f, y); close() }
        drawPath(gem, Color.White.copy(alpha = 0.9f))
        drawPath(gem, base, style = Stroke(width = 3f))
        // חיים שנשארו ביהלום
        for (k in 0 until d.hp) {
            val p = Offset(x + (k - (d.hp - 1) / 2f) * 18f, y + r * 1.08f)
            drawCircle(Color(0xFF06182E), 7f, p)
            drawCircle(Color.White, 5f, p)
        }
    }
}

/** מד חיים של הבוס: קו דק בקצה העליון של המסך, מעל הלבבות, כדי שלא יסתיר את הבוס */
private fun DrawScope.drawBossBar(realm: IceRealm) {
    val w = size.width
    val h = size.height
    val bw = w * 0.42f
    val bh = h * 0.011f
    val x = (w - bw) / 2f
    val y = h * 0.006f
    drawRoundRect(Color(0x99081028), Offset(x - 3f, y - 3f), Size(bw + 6f, bh + 6f), CornerRadius(bh))
    val f = realm.fraction
    val color = when (realm.phase) { 1 -> Color(0xFF7FE6FF); 2 -> Color(0xFFFFC04A); else -> Color(0xFFFF5A3A) }
    drawRoundRect(color.copy(alpha = 0.9f), Offset(x, y), Size(bw * f, bh), CornerRadius(bh / 2f))
}

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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arsniper.R
import com.arsniper.data.PlayerProfile
import com.arsniper.game.engine.BossClip
import com.arsniper.game.engine.BossFrame
import com.arsniper.game.engine.IceRealm
import kotlin.math.sin

/**
 * עולם הקרח (בוס ארנת הקרח): פורטל מעל הארנה, ואז הבוס כווידאו שממלא את המסך (בלי עיוות),
 * ומעליו סימוני היהלומים, מד החיים והבזקים. שכבה קבועה למסך, מעל הארנה ומתחת לכוונת, לנשק ולממשק.
 * paused = המשחק עצור (תפריט עצירה / הפסד): הווידאו נעצר איתו.
 * nowNanos רק כדי שהשכבה תתרענן בכל פריים (וכך גם הסנכרון עם הנגן).
 */
@Composable
fun IceRealmLayer(realm: IceRealm, nowNanos: Long, paused: Boolean, modifier: Modifier = Modifier) {
    if (!realm.visible) return
    val context = LocalContext.current

    // נגן אחד לכל הקרב; משתחרר כשהשכבה יורדת (ארנה נסגרה, החייאה, משחק חוזר, יציאה)
    val controller = remember { BossVideoController(context) }
    DisposableEffect(controller) {
        onDispose { controller.release() }
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

    // הפורטל: אנימציה עם שקיפות (הרקע הירוק הוסר מראש), מתנגנת פעם אחת. AnimatedImageDrawable קיים מאנדרואיד 9
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
    // הצליל של הפורטל (הקובץ המקורי היה עם קול; האנימציה עצמה בלי)
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

    // הווידאו מוצג מהפריים הראשון של סרטון הכניסה; עד אז רואים את הארנה והפורטל
    val videoShown = !inPortal && controller.frameShownToken != -1

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = false
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

            // הפורטל: בזמן הפורטל, ועד שהפריים הראשון של הכניסה מוכן (הפריים האחרון שלו זהה לראשון של הכניסה)
            if (portal != null && !videoShown && (inPortal || realm.stage == IceRealm.Stage.Intro)) {
                val r = BossFrame.rect(aspect)
                drawIntoCanvas { c ->
                    portal.setBounds(
                        (r[0] * size.width).toInt(), (r[1] * size.height).toInt(),
                        ((r[0] + r[2]) * size.width).toInt(), ((r[1] + r[3]) * size.height).toInt()
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
        if (exposed) {
            val shrink = 1f - realm.prepareFraction
            drawCircle(Color(0xFFFF4A3A).copy(alpha = 0.9f), r * (0.8f + 0.9f * shrink), Offset(x, y), style = Stroke(width = 5f))
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

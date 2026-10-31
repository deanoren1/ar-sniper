package com.arsniper.ui.game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.R
import com.arsniper.game.engine.Weapon
import com.arsniper.game.progress.WeaponId
import kotlin.math.PI
import kotlin.math.sin

/**
 * ציורי הרובה בגוף ראשון. Sheet = Plasma Blaster עם גיליון אנימציה (6 עמודות על 3 שורות, כל תא מיושר):
 * שורה 0: נשימה (0-2) והדף עם הבזק (3-5). שורה 1: טעינה (6 פריימים). שורה 2: שליפה (עמודות 1-3).
 * Still = שאר הנשקים: ציור עמידה וציור ירי, והתנועות (הדף, טעינה) נעשות בקוד.
 * muzzleX/Y = מיקום הלוע בציור הירי (שבר מהרוחב והגובה), ממנו יוצא קו הפלזמה.
 */
sealed class WeaponArt {
    class Sheet(val sheet: ImageBitmap) : WeaponArt() {
        val cell: Int = sheet.width / 6
    }

    /** Plasma בגיליון זוויות (תאים ריבועיים עם הידיים): 7 עמודות (מקצה שמאל לקצה ימין) על 5 שורות: עמידה, 3 שלבי טעינה, הבזק ירי */
    class Aim(val sheet: ImageBitmap) : WeaponArt() {
        val cw: Int = sheet.width / 7
        val ch: Int = sheet.height / 5
    }

    class Still(val idle: ImageBitmap, val fire: ImageBitmap, val muzzleX: Float, val muzzleY: Float) : WeaponArt()
}

private fun decode(context: android.content.Context, id: Int): ImageBitmap? {
    val options = BitmapFactory.Options().apply {
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = BitmapFactory.decodeResource(context.resources, id, options) ?: return null
    bitmap.density = Bitmap.DENSITY_NONE
    return bitmap.asImageBitmap()
}

@Composable
fun rememberWeaponArt(weapon: WeaponId): WeaponArt? {
    val context = LocalContext.current
    return remember(weapon) {
        when (weapon) {
            WeaponId.Plasma -> decode(context, R.drawable.weapon_aim)?.let { WeaponArt.Aim(it) }
            WeaponId.Auto -> still(context, R.drawable.weapon_auto_idle, R.drawable.weapon_auto_fire, 0.21f, 0.31f)
            WeaponId.Spread -> still(context, R.drawable.weapon_spread_idle, R.drawable.weapon_spread_fire, 0.42f, 0.37f)
            WeaponId.Sniper -> still(context, R.drawable.weapon_sniper_idle, R.drawable.weapon_sniper_fire, 0.39f, 0.35f)
        }
    }
}

private fun still(context: android.content.Context, idle: Int, fire: Int, mx: Float, my: Float): WeaponArt? {
    val i = decode(context, idle) ?: return null
    val f = decode(context, fire) ?: return null
    return WeaponArt.Still(i, f, mx, my)
}

private const val TRACER_SECONDS = 0.13f

/** מיקום הלוע (שבר מרוחב התא) בכל זווית, בשורת ההבזק */
private val AIM_MUZZLE_X = floatArrayOf(0.40f, 0.36f, 0.34f, 0.50f, 0.64f, 0.68f, 0.70f)

/** מיקום הלוע בתא ההדף של Plasma (שבר מרוחב ומגובה התא), ממנו יוצא קו הפלזמה */
private const val MUZZLE_X = 0.33f
private const val MUZZLE_Y = 0.30f

/** התא (שורה, עמודה) לציור עכשיו בגיליון של Plasma, לפי מה שהרובה עושה */
private fun frameOf(weapon: Weapon, timeSeconds: Float): Pair<Int, Int> {
    if (weapon.reloading) {
        return 1 to (weapon.reloadProgress * 6f).toInt().coerceIn(0, 5)
    }
    val equip = weapon.sinceEquip
    if (equip < 0.36f) return 2 to (1 + (equip / 0.12f).toInt().coerceIn(0, 2))
    val since = weapon.sinceShot
    if (since < 0.05f) return 0 to 3
    if (since < 0.10f) return 0 to 4
    if (since < 0.17f) return 0 to 5
    // נשימה: 0,1,2,1 בלולאה
    val breath = intArrayOf(0, 1, 2, 1)
    return 0 to breath[((timeSeconds * 2.2f).toInt() % 4 + 4) % 4]
}

/**
 * מצייר את הרובה בתחתית המסך, מימין למרכז. לא מקבל מגע: הכול עובר דרכו למשחק.
 * nowNanos מגרה ציור מחדש בכל פריים. tracerTarget = היעד של הירייה האחרונה (שבר מהמסך).
 */
@Composable
fun WeaponLayer(
    art: WeaponArt,
    weapon: Weapon,
    nowNanos: Long,
    tracerTarget: Offset?,
    aimX: Float = 0.5f,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawWeapon(art, weapon, tracerTarget, nowNanos / 1e9f, aimX)
    }
}

private fun DrawScope.drawTracer(muzzle: Offset, target: Offset, since: Float) {
    if (since >= TRACER_SECONDS) return
    val a = 1f - since / TRACER_SECONDS
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFB3E5FC).copy(alpha = a), Color(0xFF29B6F6).copy(alpha = a * 0.6f)),
            start = muzzle,
            end = target
        ),
        start = muzzle,
        end = target,
        strokeWidth = 7f * density * a + 2f
    )
    drawCircle(Color(0xFFE1F5FE).copy(alpha = a * 0.8f), 26f * density * (1.4f - a * 0.4f), target)
    drawCircle(Color(0xFF40C4FF).copy(alpha = a * 0.5f), 44f * density * (1.5f - a * 0.5f), target)
}

private fun DrawScope.drawWeapon(art: WeaponArt, weapon: Weapon, tracerNormalized: Offset?, timeSeconds: Float, aimX: Float) {
    // היעד נשמר כשבר מהמסך (0..1), כמו מיקום הכוונת. ממירים לפיקסלים
    val tracerTarget = tracerNormalized?.let { Offset(it.x * size.width, it.y * size.height) }
    val since = weapon.sinceShot

    // הדף: דוחף אחורה (ימינה ולמטה) ומתאושש
    val kick = if (since < 0.2f) {
        val t = since / 0.2f
        (1f - t) * (1f - t)
    } else {
        0f
    }
    val dx = kick * size.height * 0.02f
    val dy = kick * size.height * 0.035f
    val bottom = size.height + size.height * 0.02f

    when (art) {
        is WeaponArt.Aim -> {
            // העמודה לפי מיקום הכוונת: הרובה מסתובב אליה, עם מעבר חלק בין שתי זוויות סמוכות
            val pos = 3f + ((aimX - 0.62f) / 0.45f).coerceIn(-1f, 1f) * 3f
            val col0 = pos.toInt().let { (if (pos - it > 0.62f) it + 1 else it) }.coerceIn(0, 6)
            val row = when {
                weapon.reloading -> 1 + (weapon.reloadProgress * 2f).toInt().coerceIn(0, 1)
                since < 0.15f -> 4
                else -> 0
            }
            val scale = size.height * 0.68f / art.ch
            val w = art.cw * scale
            val h = art.ch * scale
            val left = size.width * 0.62f - w / 2f
            val top = bottom - h
            if (tracerTarget != null) {
                drawTracer(
                    Offset(left + w * AIM_MUZZLE_X[col0] + dx, top + h * 0.16f + dy),
                    tracerTarget, since
                )
            }
            translate(dx, dy) {
                drawImage(
                    image = art.sheet,
                    srcOffset = IntOffset(col0 * art.cw, row * art.ch),
                    srcSize = IntSize(art.cw, art.ch),
                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize = IntSize(w.toInt(), h.toInt())
                )
            }
        }

        is WeaponArt.Sheet -> {
            val (row, column) = frameOf(weapon, timeSeconds)
            // גובה התא הוא 55% מגובה המסך. הרובה מיושר לתחתית, וקצת יוצא מהמסך
            val scale = size.height * 0.55f / art.cell
            val left = size.width * 0.62f - art.cell * scale / 2f
            val top = bottom - art.cell * scale
            if (tracerTarget != null) {
                drawTracer(Offset(left + art.cell * scale * MUZZLE_X + dx, top + art.cell * scale * MUZZLE_Y + dy), tracerTarget, since)
            }
            translate(dx, dy) {
                drawImage(
                    image = art.sheet,
                    srcOffset = IntOffset(column * art.cell, row * art.cell),
                    srcSize = IntSize(art.cell, art.cell),
                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize = IntSize((art.cell * scale).toInt(), (art.cell * scale).toInt())
                )
            }
        }

        is WeaponArt.Still -> {
            val reloading = weapon.reloading
            // טעינה: הרובה יורד, מוטה, וחוזר
            val dip = if (reloading) sin(PI.toFloat() * weapon.reloadProgress) else 0f
            val scale = size.height * 0.52f / art.idle.height
            val left = size.width * 0.62f - art.idle.width * scale / 2f
            val firing = !reloading && since < 0.09f
            val image = if (firing) art.fire else art.idle
            val top = bottom - image.height * scale
            val ox = dx + dip * size.width * 0.03f
            val oy = dy + dip * size.height * 0.42f
            if (tracerTarget != null) {
                val fireTop = bottom - art.fire.height * scale
                drawTracer(
                    Offset(left + art.fire.width * scale * art.muzzleX + ox, fireTop + art.fire.height * scale * art.muzzleY + oy),
                    tracerTarget, since
                )
            }
            val pivot = Offset(left + image.width * scale * 0.5f, bottom)
            rotate(dip * -14f, pivot) {
                translate(ox, oy) {
                    drawImage(
                        image = image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(image.width, image.height),
                        dstOffset = IntOffset(left.toInt(), top.toInt()),
                        dstSize = IntSize((image.width * scale).toInt(), (image.height * scale).toInt())
                    )
                }
            }
        }
    }
}

/** תצוגה מקדימה של רובה (עמידה) לחנות: מצייר את ציור העמידה כך שיתאים לתיבה */
@Composable
fun WeaponPreview(weapon: WeaponId, modifier: Modifier = Modifier) {
    val art = rememberWeaponArt(weapon) ?: return
    Canvas(modifier = modifier) {
        when (art) {
            is WeaponArt.Aim -> {
                val s = minOf(size.width / art.cw, size.height / art.ch)
                drawImage(
                    image = art.sheet,
                    srcOffset = IntOffset(3 * art.cw, 0),
                    srcSize = IntSize(art.cw, art.ch),
                    dstOffset = IntOffset(((size.width - art.cw * s) / 2f).toInt(), (size.height - art.ch * s).toInt()),
                    dstSize = IntSize((art.cw * s).toInt(), (art.ch * s).toInt())
                )
            }

            is WeaponArt.Sheet -> {
                val s = minOf(size.width, size.height) / art.cell
                drawImage(
                    image = art.sheet,
                    srcOffset = IntOffset(0, 0),
                    srcSize = IntSize(art.cell, art.cell),
                    dstOffset = IntOffset(((size.width - art.cell * s) / 2f).toInt(), (size.height - art.cell * s).toInt()),
                    dstSize = IntSize((art.cell * s).toInt(), (art.cell * s).toInt())
                )
            }

            is WeaponArt.Still -> {
                val s = minOf(size.width / art.idle.width, size.height / art.idle.height)
                drawImage(
                    image = art.idle,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(art.idle.width, art.idle.height),
                    dstOffset = IntOffset(((size.width - art.idle.width * s) / 2f).toInt(), (size.height - art.idle.height * s).toInt()),
                    dstSize = IntSize((art.idle.width * s).toInt(), (art.idle.height * s).toInt())
                )
            }
        }
    }
}

/** מד הכדורים בעיצוב ה-HUD החדש: 11 תאים; לחיצה עליו טוענת ידנית */
@Composable
fun AmmoBar(
    ammo: Int,
    magSize: Int,
    reloading: Boolean,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val k = 130f / 372f
    val slots = 11
    val lit = if (reloading || ammo <= 0 || magSize <= 0) 0
        else kotlin.math.ceil(slots * ammo.toFloat() / magSize).toInt().coerceIn(1, slots)
    // עמודה אנכית ושקופה למחצה (מתמלאת מלמטה); מצוירת כפס אופקי ומסובבת ב-90 מעלות
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = (90f * k).dp, height = 130.dp)
            .clickable(onClick = onReload)
    ) {
    Box(
        modifier = Modifier
            .requiredSize(width = 130.dp, height = (90f * k).dp)
            .graphicsLayer { rotationZ = -90f; alpha = 0.55f }
    ) {
        GlowPill(Modifier.fillMaxSize(), corner = 0.22f)
        AmmoSegments(
            lit = lit,
            slots = slots,
            modifier = Modifier
                .offset(x = (17f * k).dp, y = (12f * k).dp)
                .size(width = (338f * k).dp, height = (66f * k).dp)
        )
        if (reloading) {
            Text("RELOADING", color = Color(0xFFFFEB3B), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
        } else if (ammo == 0) {
            Text("EMPTY", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
        }
    }
    }
}

package com.arsniper.ui.game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.R
import com.arsniper.game.engine.Weapon

/**
 * הרובה בגוף ראשון: גיליון אחד של 6 עמודות על 3 שורות, כל תא באותו גודל ומיושר באותו מקום:
 * שורה 0: נשימה (0-2) והדף עם הבזק (3-5). שורה 1: טעינה (6 פריימים).
 * שורה 2: עמודה 0 ריק (לא בשימוש), עמודות 1-3 שליפה (עלייה מלמטה).
 */
class WeaponArt(val sheet: ImageBitmap) {
    val cell: Int = sheet.width / COLUMNS

    companion object {
        const val COLUMNS = 6
    }
}

@Composable
fun rememberWeaponArt(): WeaponArt? {
    val context = LocalContext.current
    return remember {
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.weapon_sheet, options)
        if (bitmap == null) {
            null
        } else {
            bitmap.density = Bitmap.DENSITY_NONE
            WeaponArt(bitmap.asImageBitmap())
        }
    }
}

private const val TRACER_SECONDS = 0.13f

/** מיקום הלוע בתוך תא ההדף (שבר מרוחב ומגובה התא), ממנו יוצא קו הפלזמה */
private const val MUZZLE_X = 0.33f
private const val MUZZLE_Y = 0.30f

/** התא (שורה, עמודה) לציור עכשיו, לפי מה שהרובה עושה */
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
 * nowNanos מגרה ציור מחדש בכל פריים. tracerTarget = היעד של הירייה האחרונה (במסך).
 */
@Composable
fun WeaponLayer(
    art: WeaponArt,
    weapon: Weapon,
    nowNanos: Long,
    tracerTarget: Offset?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawWeapon(art, weapon, tracerTarget, nowNanos / 1e9f)
    }
}

private fun DrawScope.drawWeapon(art: WeaponArt, weapon: Weapon, tracerTarget: Offset?, timeSeconds: Float) {
    val since = weapon.sinceShot
    val (row, column) = frameOf(weapon, timeSeconds)

    // גודל: גובה התא הוא 55% מגובה המסך. הרובה מיושר לתחתית, וקצת יוצא מהמסך
    val scale = size.height * 0.55f / art.cell
    val left = size.width * 0.62f - art.cell * scale / 2f
    val bottom = size.height + size.height * 0.02f
    val top = bottom - art.cell * scale

    // הדף: דוחף אחורה (ימינה ולמטה) ומתאושש
    val kick = if (since < 0.2f) {
        val t = since / 0.2f
        (1f - t) * (1f - t)
    } else {
        0f
    }
    val dx = kick * size.height * 0.02f
    val dy = kick * size.height * 0.035f

    // קו הפלזמה מהלוע עד היעד (נעלם מהר)
    if (tracerTarget != null && since < TRACER_SECONDS) {
        val a = 1f - since / TRACER_SECONDS
        val muzzle = Offset(left + art.cell * scale * MUZZLE_X + dx, top + art.cell * scale * MUZZLE_Y + dy)
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFB3E5FC).copy(alpha = a), Color(0xFF29B6F6).copy(alpha = a * 0.6f)),
                start = muzzle,
                end = tracerTarget
            ),
            start = muzzle,
            end = tracerTarget,
            strokeWidth = 7f * density * a + 2f
        )
        drawCircle(Color(0xFFE1F5FE).copy(alpha = a * 0.8f), 26f * density * (1.4f - a * 0.4f), tracerTarget)
        drawCircle(Color(0xFF40C4FF).copy(alpha = a * 0.5f), 44f * density * (1.5f - a * 0.5f), tracerTarget)
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

/** מד הכדורים: פסים קטנים (אחד לכל כדור), ובטעינה כתוב RELOADING. לחיצה עליו טוענת ידנית */
@Composable
fun AmmoBar(
    ammo: Int,
    magSize: Int,
    reloading: Boolean,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable(onClick = onReload)
            .padding(8.dp)
    ) {
        Row {
            for (i in 0 until magSize) {
                val lit = !reloading && i < ammo
                Box(
                    modifier = Modifier
                        .width(9.dp)
                        .height(22.dp)
                        .background(
                            if (lit) Color(0xFF40C4FF) else Color(0x55FFFFFF),
                            RoundedCornerShape(3.dp)
                        )
                )
                if (i < magSize - 1) Spacer(Modifier.width(3.dp))
            }
        }
        if (reloading) {
            Text(
                text = "RELOADING",
                color = Color(0xFFFFEB3B),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 26.dp)
            )
        } else if (ammo == 0) {
            Text("EMPTY", color = Color(0xFFFF5252), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 26.dp))
        }
    }
}

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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.R
import com.arsniper.game.engine.Weapon
import kotlin.math.PI
import kotlin.math.sin

/** הרובה בגוף ראשון: שני ציורים (עמידה, ירי) ותנועות בקוד (הדף, טעינה). שאר ההגדרות ב-GameScreen */
class WeaponArt(val idle: ImageBitmap, val fire: ImageBitmap)

@Composable
fun rememberWeaponArt(): WeaponArt? {
    val context = LocalContext.current
    return remember {
        fun load(id: Int): ImageBitmap? {
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeResource(context.resources, id, options) ?: return null
            bitmap.density = Bitmap.DENSITY_NONE
            return bitmap.asImageBitmap()
        }
        val idle = load(R.drawable.weapon_idle)
        val fire = load(R.drawable.weapon_fire)
        if (idle != null && fire != null) WeaponArt(idle, fire) else null
    }
}

/** כמה זמן (שניות) מוצגת תנוחת הירי, וכמה זמן נמשך הקו של הקליע */
private const val FIRE_POSE_SECONDS = 0.09f
private const val RECOIL_SECONDS = 0.22f
private const val TRACER_SECONDS = 0.13f

/**
 * מצייר את הרובה בתחתית המסך, מימין למרכז: עומד, מחזיר הדף בירייה (עם תנוחת הירי וקו פלזמה עד הכוונת),
 * ונשלף למטה ונטה בזמן טעינה. לא מקבל מגע: הכול עובר דרכו למשחק.
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
        @Suppress("UNUSED_EXPRESSION") nowNanos
        drawWeapon(art, weapon, tracerTarget)
    }
}

private fun DrawScope.drawWeapon(art: WeaponArt, weapon: Weapon, tracerTarget: Offset?) {
    val since = weapon.sinceShot
    val reload = weapon.reloadProgress
    val reloading = weapon.reloading

    // גודל: גובה ציור העמידה הוא 52% מגובה המסך. אותו קנה מידה לשני הציורים (הם מיושרים בתחתית)
    val scale = size.height * 0.52f / art.idle.height
    val baseLeft = size.width * 0.62f - art.idle.width * scale / 2f
    val bottom = size.height + size.height * 0.03f

    // הדף: דוחף אחורה (ימינה ולמטה) ומתאושש
    val kick = if (since < RECOIL_SECONDS) {
        val t = since / RECOIL_SECONDS
        (1f - t) * (1f - t)
    } else 0f
    // טעינה: יורד למטה, מוטה, וחוזר
    val dip = if (reloading) sin(PI.toFloat() * reload) else 0f
    val dx = kick * size.height * 0.025f + dip * size.width * 0.03f
    val dy = kick * size.height * 0.04f + dip * size.height * 0.42f
    val tilt = dip * -14f + kick * 1.5f

    val firing = !reloading && since < FIRE_POSE_SECONDS
    val image = if (firing) art.fire else art.idle
    val top = bottom - image.height * scale

    // קו הפלזמה מהלוע עד היעד (נעלם מהר)
    if (tracerTarget != null && since < TRACER_SECONDS) {
        val a = 1f - since / TRACER_SECONDS
        val muzzle = Offset(baseLeft + art.fire.width * 0.265f * scale + dx, bottom - art.fire.height * (1f - 0.28f) * scale + dy)
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

    val pivot = Offset(baseLeft + image.width * scale * 0.5f, bottom)
    rotate(tilt, pivot) {
        translate(dx, dy) {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset(baseLeft.toInt(), top.toInt()),
                dstSize = IntSize((image.width * scale).toInt(), (image.height * scale).toInt())
            )
        }
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

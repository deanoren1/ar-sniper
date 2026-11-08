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

    /** Blaster Mk1 ברזולוציה גבוהה: 5 זוויות עמידה בשורה אחת (תאים שווים) + תמונת הבזק נפרדת שמצוירת על הלוע */
    class Angles(
        val sheet: ImageBitmap,
        val flash: ImageBitmap,
        val muzzleX: FloatArray,
        val muzzleY: FloatArray,
        val dirs: FloatArray,
        val originX: FloatArray,
        val originY: FloatArray,
        val cols: Int = ANGLE_COUNT,
        val centerCol: Int = cols / 2
    ) : WeaponArt() {
        val maxResidual: Float = if (cols > 5) 24f else 14f
        val cw: Int = sheet.width / cols
        val ch: Int = sheet.height / ANGLE_ROWS
    }
}

/** נקודת המוצא של הרובה לרוחב המסך (0.5 = אמצע). ממנה נמשך הקו הבלתי נראה אל הכוונת */
private const val ANGLE_BASE_X = 0.5f

const val ANGLE_COUNT = 5   // עמודות: שמאל רחוק ... ימין רחוק
const val ANGLE_ROWS = 3    // שורות: מבט למעלה / ישר / למטה

/**
 * טבלאות הרובים, לפי תא (אינדקס = שורה*5 + עמודה), נמדדו מהתמונות:
 * MX/MY = קצה הקנה (הלוע), OX/OY = מרכז הגב של הרובה (מהן נמשך הקו אל הלוע), DIR = כיוון הקנה במעלות מהאנך (שלילי = שמאלה).
 * כל הערכים כשבר מרוחב ומגובה התא.
 */
private val MK1_MUZZLE_X = floatArrayOf(0.021f, 0.306f, 0.267f, 0.223f, 0.225f, 0.277f, 0.246f, 0.448f, 0.446f, 0.592f, 0.700f, 0.773f, 0.804f, 0.846f, 0.981f, 0.992f, 0.996f, 0.008f, 0.056f, 0.312f, 0.260f, 0.269f, 0.317f, 0.292f, 0.452f, 0.456f, 0.581f, 0.717f, 0.771f, 0.815f, 0.846f, 0.992f, 0.994f, 0.998f, 0.056f, 0.048f, 0.060f, 0.102f, 0.156f, 0.173f, 0.160f, 0.221f, 0.463f, 0.912f, 0.881f, 0.963f, 0.892f, 0.996f, 0.998f, 0.998f, 0.998f)
private val MK1_MUZZLE_Y = floatArrayOf(0.402f, 0.250f, 0.212f, 0.209f, 0.177f, 0.168f, 0.152f, 0.184f, 0.138f, 0.129f, 0.191f, 0.134f, 0.175f, 0.118f, 0.305f, 0.318f, 0.384f, 0.430f, 0.411f, 0.227f, 0.170f, 0.170f, 0.164f, 0.157f, 0.150f, 0.195f, 0.202f, 0.202f, 0.148f, 0.182f, 0.136f, 0.312f, 0.375f, 0.396f, 0.807f, 0.621f, 0.571f, 0.605f, 0.396f, 0.391f, 0.396f, 0.250f, 0.252f, 0.354f, 0.334f, 0.473f, 0.421f, 0.602f, 0.623f, 0.729f, 0.729f)
private val MK1_ORIGIN_X = floatArrayOf(0.458f, 0.458f, 0.681f, 0.752f, 0.738f, 0.424f, 0.709f, 0.577f, 0.554f, 0.502f, 0.369f, 0.385f, 0.315f, 0.319f, 0.176f, 0.103f, 0.103f, 0.816f, 0.816f, 0.672f, 0.784f, 0.763f, 0.753f, 0.702f, 0.575f, 0.519f, 0.487f, 0.346f, 0.379f, 0.262f, 0.235f, 0.097f, 0.097f, 0.097f, 0.767f, 0.438f, 0.808f, 0.760f, 0.644f, 0.413f, 0.731f, 0.446f, 0.514f, 0.340f, 0.374f, 0.313f, 0.285f, 0.146f, 0.088f, 0.088f, 0.088f)
private val MK1_ORIGIN_Y = floatArrayOf(0.672f, 0.672f, 0.680f, 0.702f, 0.658f, 0.540f, 0.684f, 0.671f, 0.679f, 0.662f, 0.611f, 0.608f, 0.629f, 0.624f, 0.675f, 0.591f, 0.591f, 0.715f, 0.715f, 0.724f, 0.787f, 0.734f, 0.695f, 0.728f, 0.682f, 0.680f, 0.688f, 0.687f, 0.684f, 0.680f, 0.680f, 0.690f, 0.690f, 0.690f, 0.951f, 0.819f, 0.795f, 0.893f, 0.771f, 0.764f, 0.801f, 0.625f, 0.679f, 0.681f, 0.775f, 0.768f, 0.819f, 0.837f, 0.781f, 0.781f, 0.781f)
private val MK1_DIR = floatArrayOf(-54.188f, -17.143f, -37.220f, -42.577f, -42.429f, -18.728f, -36.748f, -12.764f, -9.742f, 8.215f, 34.080f, 35.041f, 42.732f, 41.745f, 61.873f, 70.294f, 74.884f, -67.617f, -64.923f, -31.814f, -36.020f, -36.917f, -35.148f, -31.656f, -11.168f, -6.358f, 9.411f, 33.232f, 32.094f, 43.587f, 43.898f, 63.773f, 67.698f, 69.167f, -76.667f, -59.395f, -70.793f, -62.942f, -48.140f, -28.904f, -50.378f, -27.251f, -5.938f, 56.315f, 44.578f, 62.084f, 52.604f, 72.127f, 78.567f, 86.162f, 86.162f)

private val AUTO_MUZZLE_X = floatArrayOf(0.124f, 0.147f, 0.497f, 0.861f, 0.891f, 0.098f, 0.121f, 0.497f, 0.903f, 0.928f, 0.074f, 0.082f, 0.494f, 0.898f, 0.936f)
private val AUTO_MUZZLE_Y = floatArrayOf(0.200f, 0.202f, 0.132f, 0.203f, 0.199f, 0.262f, 0.245f, 0.135f, 0.258f, 0.273f, 0.383f, 0.394f, 0.297f, 0.394f, 0.399f)
private val AUTO_ORIGIN_X = floatArrayOf(0.745f, 0.708f, 0.516f, 0.352f, 0.288f, 0.726f, 0.723f, 0.515f, 0.340f, 0.285f, 0.691f, 0.694f, 0.510f, 0.340f, 0.331f)
private val AUTO_ORIGIN_Y = floatArrayOf(0.643f, 0.582f, 0.535f, 0.568f, 0.627f, 0.557f, 0.538f, 0.516f, 0.542f, 0.565f, 0.676f, 0.655f, 0.625f, 0.657f, 0.657f)
private val AUTO_DIR = floatArrayOf(-54.542f, -55.865f, -2.659f, 54.333f, 54.648f, -64.817f, -63.995f, -2.689f, 63.220f, 65.565f, -64.584f, -66.855f, -2.825f, 64.775f, 66.853f)

/** סיבוב (מעלות) סביב הציר, כך שקו הקנה היוצא מהלוע עובר דרך הכוונת. null אם אין פתרון (הכוונת מתחת ללוע) */
private fun aimResidual(
    mrx: Float, mry: Float, axis: Float, pivot: Offset, tx: Float, ty: Float, w: Float, drop0: Float, maxRes: Float
): Float? {
    fun f(r: Float): Float {
        val rr = Math.toRadians(r.toDouble())
        val cs = kotlin.math.cos(rr).toFloat(); val sn = kotlin.math.sin(rr).toFloat()
        val drop = drop0 + (w / 2f) * kotlin.math.sin(Math.toRadians(kotlin.math.abs(r).toDouble())).toFloat()
        val mx = pivot.x + mrx * cs - mry * sn
        val my = pivot.y + mrx * sn + mry * cs + drop
        if (my - ty < 4f) return Float.NaN
        return Math.toDegrees(kotlin.math.atan2((tx - mx).toDouble(), (my - ty).toDouble())).toFloat() - axis - r
    }
    var best: Float? = null
    var prevR = -maxRes
    var prevF = f(prevR)
    var r = -maxRes
    while (r < maxRes - 0.01f) {
        val nr = (r + 1f).coerceAtMost(maxRes)
        val nf = f(nr)
        if (!prevF.isNaN() && !nf.isNaN() && prevF * nf <= 0f) {
            var lo = prevR; var hi = nr; val flo = prevF
            repeat(24) {
                val mid = (lo + hi) / 2f
                if (flo * f(mid) <= 0f) hi = mid else lo = mid
            }
            val sol = (lo + hi) / 2f
            if (best == null || kotlin.math.abs(sol) < kotlin.math.abs(best!!)) best = sol
        }
        prevR = nr; prevF = nf; r = nr
    }
    return best
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
            WeaponId.Plasma -> {
                val sheet = decode(context, R.drawable.weapon_angles)
                val flash = decode(context, R.drawable.weapon_flash)
                if (sheet != null && flash != null) WeaponArt.Angles(sheet, flash, MK1_MUZZLE_X, MK1_MUZZLE_Y, MK1_DIR, MK1_ORIGIN_X, MK1_ORIGIN_Y, 17, 8)
                else decode(context, R.drawable.weapon_sheet)?.let { WeaponArt.Sheet(it) }
            }
            WeaponId.Auto -> {
                val sheet = decode(context, R.drawable.weapon_auto_angles)
                val flash = decode(context, R.drawable.weapon_flash_blue)
                if (sheet != null && flash != null) WeaponArt.Angles(sheet, flash, AUTO_MUZZLE_X, AUTO_MUZZLE_Y, AUTO_DIR, AUTO_ORIGIN_X, AUTO_ORIGIN_Y)
                else still(context, R.drawable.weapon_auto_idle, R.drawable.weapon_auto_fire, 0.21f, 0.31f)
            }
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
    modifier: Modifier = Modifier,
    aimY: Float = 0.5f
) {
    // הכוונה המוחלקת: הרובה "רודף" אחרי הכוונת בהאטה, כדי שהסיבוב יהיה זורם ולא קופץ
    val smooth = remember { floatArrayOf(Float.NaN, Float.NaN, 0f, (ANGLE_COUNT / 2).toFloat(), 1f, 7f, -10f, 7f, Float.NaN, 0f, 0f, 0f, 0f, 0f) }
    Canvas(modifier = modifier.fillMaxSize()) {
        val now = nowNanos / 1e9f
        val dt = if (smooth[2] == 0f) 0f else (now - smooth[2]).coerceIn(0f, 0.1f)
        val k = 1f - kotlin.math.exp(-dt * AIM_FOLLOW_SPEED)
        smooth[0] = if (smooth[0].isNaN()) aimX else smooth[0] + (aimX - smooth[0]) * k
        smooth[1] = if (smooth[1].isNaN()) aimY else smooth[1] + (aimY - smooth[1]) * k
        smooth[2] = now
        drawWeapon(art, weapon, tracerTarget, now, smooth[0], smooth[1], smooth)
    }
}

/** כמה מהר הרובה מגיע לכיוון הכוונת (גדול = מהיר). 14 = כ-70ms לרוב הדרך */
private const val AIM_FOLLOW_SPEED = 14f

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

private fun DrawScope.drawWeapon(art: WeaponArt, weapon: Weapon, tracerNormalized: Offset?, timeSeconds: Float, aimX: Float, aimY: Float, state: FloatArray) {
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
            val pos = (3f + ((aimX - 0.62f) / 0.45f).coerceIn(-1f, 1f) * 3f).coerceIn(0f, 6f)
            val col0 = pos.toInt().coerceIn(0, 5)
            val col1 = col0 + 1
            val blend = (pos - col0).coerceIn(0f, 1f)
            val row = when {
                weapon.reloading -> 1 + (weapon.reloadProgress * 2f).toInt().coerceIn(0, 1)
                since < 0.15f -> 4
                else -> 0
            }
            val scale = size.height * 0.68f / art.ch
            val w = art.cw * scale
            val h = art.ch * scale
            // גובה הכוונת מזיז את הרובה מעט למעלה/למטה, כאילו הלוע עוקב אחריה
            val aimDy = ((aimY - 0.5f).coerceIn(-0.5f, 0.5f)) * size.height * 0.10f
            val left = size.width * 0.62f - w / 2f
            val top = bottom - h + aimDy
            val muzzleX = AIM_MUZZLE_X[col0] + (AIM_MUZZLE_X[col1] - AIM_MUZZLE_X[col0]) * blend
            if (tracerTarget != null) {
                drawTracer(
                    Offset(left + w * muzzleX + dx, top + h * 0.16f + dy),
                    tracerTarget, since
                )
            }
            translate(dx, dy) {
                // זווית ראשונה בכל עוצמה, והסמוכה מעליה בשקיפות לפי הקרבה: מעבר רך במקום קפיצה
                drawImage(
                    image = art.sheet,
                    srcOffset = IntOffset(col0 * art.cw, row * art.ch),
                    srcSize = IntSize(art.cw, art.ch),
                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize = IntSize(w.toInt(), h.toInt())
                )
                if (blend > 0.02f) {
                    drawImage(
                        image = art.sheet,
                        srcOffset = IntOffset(col1 * art.cw, row * art.ch),
                        srcSize = IntSize(art.cw, art.ch),
                        dstOffset = IntOffset(left.toInt(), top.toInt()),
                        dstSize = IntSize(w.toInt(), h.toInt()),
                        alpha = blend
                    )
                }
            }
        }

        is WeaponArt.Angles -> {
            val scale = size.height * 0.74f / art.ch
            val w = art.cw * scale
            val h = art.ch * scale
            val tx = aimX * size.width
            val ty = aimY * size.height
            // "לייזר בלתי נראה": קו מנקודת המוצא הקבועה של הרובה (אמצע המסך למטה) אל הכוונת. הכיוון שלו קובע איך הרובה מוטה
            val baseX = size.width * ANGLE_BASE_X
            val left = baseX - w / 2f
            val top = bottom - h
            val pivot = Offset(baseX, bottom - h * 0.06f)
            // זווית הקו הבלתי נראה (0 = ישר למעלה, שלילי = שמאלה)
            val up = (pivot.y - ty).coerceAtLeast(size.height * 0.25f)
            val theta = (Math.toDegrees(kotlin.math.atan2(tx - pivot.x, up).toDouble()).toFloat()).coerceIn(-80f, 80f)
            // שורה לפי גובה הכוונת במסך (מבט למעלה / ישר / למטה), עם היסטרזיס
            var row = state[4].toInt().coerceIn(0, ANGLE_ROWS - 1)
            val ay = aimY
            if (row == 1 && ay < 0.36f) row = 0 else if (row == 1 && ay > 0.64f) row = 2
            else if (row == 0 && ay > 0.42f) row = 1 else if (row == 2 && ay < 0.58f) row = 1
            state[4] = row.toFloat()
            val rowDrop = if (row == 2) h * 0.06f else 0f
            val baseDrop = h * 0.08f
            // לכל עמודה: הסיבוב הקטן שבו קו הקנה (מהלוע של אותה תמונה) עובר בדיוק דרך הכוונת. בוחרים את הקטן ביותר, עם היסטרזיס
            fun resFor(c: Int): Float? {
                val k = row * art.cols + c
                return aimResidual(
                    left + w * art.muzzleX[k] - pivot.x, top + h * art.muzzleY[k] - pivot.y,
                    art.dirs[k], pivot, tx, ty, w, baseDrop + rowDrop, art.maxResidual
                )
            }
            var idx = state[3].toInt().coerceIn(0, art.cols - 1)
            var bestC = -1
            var bestR = 0f
            for (c in 0 until art.cols) {
                val r = resFor(c) ?: continue
                if (bestC < 0 || kotlin.math.abs(r) < kotlin.math.abs(bestR)) { bestC = c; bestR = r }
            }
            var residual = 0f
            if (bestC < 0) {
                // הכוונת נמוכה מהלוע: אי אפשר לכוון בדיוק, אז הרובה פונה לכיוון הכוונת (התמונה הקרובה ביותר לזווית מהציר + סיבוב)
                var nc = idx
                for (c in 0 until art.cols) {
                    if (kotlin.math.abs(theta - art.dirs[row * art.cols + c]) < kotlin.math.abs(theta - art.dirs[row * art.cols + nc])) nc = c
                }
                if (kotlin.math.abs(theta - art.dirs[row * art.cols + nc]) + 6f < kotlin.math.abs(theta - art.dirs[row * art.cols + idx])) idx = nc
                residual = (theta - art.dirs[row * art.cols + idx]).coerceIn(-art.maxResidual, art.maxResidual)
            } else {
                val cur = resFor(idx)
                if (cur != null && kotlin.math.abs(cur) <= kotlin.math.abs(bestR) + 10f) { residual = cur } else { idx = bestC; residual = bestR }
            }
            state[3] = idx.toFloat()
            val cell = row * art.cols + idx
            val mrx = left + w * art.muzzleX[cell] - pivot.x
            val mry = top + h * art.muzzleY[cell] - pivot.y
            val orx = left + w * art.originX[cell] - pivot.x
            val ory = top + h * art.originY[cell] - pivot.y
            val reloading = weapon.reloading
            val dip = if (reloading) sin(PI.toFloat() * weapon.reloadProgress) else 0f
            val equip = weapon.sinceEquip
            val rise = if (equip < 0.3f) (1f - equip / 0.3f) * (1f - equip / 0.3f) else 0f
            val rotDrop = (w / 2f) * kotlin.math.sin(Math.toRadians(kotlin.math.abs(residual).toDouble())).toFloat()
            val ox = dx + dip * size.width * 0.03f
            val oy = dy + baseDrop + rotDrop + rowDrop + dip * size.height * 0.42f + rise * size.height * 0.5f
            val totalRot = residual + dip * -14f
            val rad = Math.toRadians(totalRot.toDouble())
            val cosr = kotlin.math.cos(rad).toFloat(); val sinr = kotlin.math.sin(rad).toFloat()
            val muzzle = Offset(pivot.x + mrx * cosr - mry * sinr + ox, pivot.y + mrx * sinr + mry * cosr + oy)
            val origin = Offset(pivot.x + orx * cosr - ory * sinr + ox, pivot.y + orx * sinr + ory * cosr + oy)

            // החלפת תא: מעבר רך קצר מהתא הקודם, כדי שלא יהיו קפיצות
            if (cell != state[5].toInt()) {
                state[7] = state[5]; state[5] = cell.toFloat(); state[6] = timeSeconds
                if (!state[8].isNaN()) {
                    // רציפות: הלוע והזווית מתחילים במקום שבו היו לפני ההחלפה ומתכנסים בהדרגה
                    state[11] = (state[8] - muzzle.x).coerceIn(-220f, 220f)
                    state[12] = (state[9] - muzzle.y).coerceIn(-220f, 220f)
                    state[13] = (state[10] - totalRot).coerceIn(-25f, 25f)
                }
            }
            val ck = (1f - (timeSeconds - state[6]) / 0.14f).coerceIn(0f, 1f).let { it * it }
            val cex = state[11] * ck; val cey = state[12] * ck; val cer = state[13] * ck
            val crr = Math.toRadians(cer.toDouble()); val ccs = kotlin.math.cos(crr).toFloat(); val csn = kotlin.math.sin(crr).toFloat()
            val ovx = origin.x - muzzle.x; val ovy = origin.y - muzzle.y
            val originV = Offset(muzzle.x + ovx * ccs - ovy * csn + cex, muzzle.y + ovx * csn + ovy * ccs + cey)
            val muzzleV = Offset(muzzle.x + cex, muzzle.y + cey)
            state[8] = muzzleV.x; state[9] = muzzleV.y; state[10] = totalRot + cer
            val fade = ((timeSeconds - state[6]) / 0.09f).coerceIn(0f, 1f)
            val fromCell = state[7].toInt().coerceIn(0, art.cols * ANGLE_ROWS - 1)

            // הקו נמשך בישר מגב הרובה דרך הלוע אל הכוונת. מצויר מתחת לרובה, אז נראה יוצא מהקצה
            if (tracerTarget != null) drawTracer(originV, tracerTarget, since)
            translate(cex, cey) { rotate(cer, muzzle) { rotate(totalRot, pivot) {
                translate(ox, oy) {
                    if (fade < 1f) {
                        drawImage(
                            image = art.sheet,
                            srcOffset = IntOffset((fromCell % art.cols) * art.cw, (fromCell / art.cols) * art.ch),
                            srcSize = IntSize(art.cw, art.ch),
                            dstOffset = IntOffset(left.toInt(), top.toInt()),
                            dstSize = IntSize(w.toInt(), h.toInt())
                        )
                    }
                    drawImage(
                        image = art.sheet,
                        srcOffset = IntOffset(idx * art.cw, row * art.ch),
                        srcSize = IntSize(art.cw, art.ch),
                        dstOffset = IntOffset(left.toInt(), top.toInt()),
                        dstSize = IntSize(w.toInt(), h.toInt()),
                        alpha = fade
                    )
                }
            } } }
            // הבזק ירי מעל הלוע (חיבור אור), דועך תוך 90ms, מכוון אל הכוונת
            if (!reloading && since < 0.09f) {
                val a = (1f - since / 0.09f).coerceIn(0f, 1f)
                val fw = w * 0.9f
                val fh = fw * art.flash.height / art.flash.width
                val flashRot = Math.toDegrees(kotlin.math.atan2((tx - muzzleV.x).toDouble(), (muzzleV.y - ty).toDouble())).toFloat().coerceIn(-85f, 85f)
                rotate(flashRot, muzzleV) {
                    drawImage(
                        image = art.flash,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(art.flash.width, art.flash.height),
                        dstOffset = IntOffset((muzzleV.x - fw / 2f).toInt(), (muzzleV.y - fh * 0.80f).toInt()),
                        dstSize = IntSize(fw.toInt(), fh.toInt()),
                        alpha = a,
                        blendMode = androidx.compose.ui.graphics.BlendMode.Plus
                    )
                }
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

            is WeaponArt.Angles -> {
                val s = minOf(size.width / art.cw, size.height / art.ch)
                drawImage(
                    image = art.sheet,
                    srcOffset = IntOffset(art.centerCol * art.cw, art.ch),
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

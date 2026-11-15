package com.arsniper.ui.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.arsniper.game.engine.Weapon
import java.util.concurrent.Executors

/**
 * רובה מטריצת הספרייטים (sprites_v10 מ-Blender): 21 זוויות yaw (‎-50..+50, כל 5 מעלות) על 9 שורות pitch.
 * כל ספרייט הוא חיתוך קבוע מתוך פריים 1440x810 של מצלמת ה-viewmodel, ולכן מצויר תמיד באותו מקום על המסך.
 * עמודה 0 = yaw ‎-50 (הרובה מכוון ימינה), עמודה 20 = yaw ‎+50 (שמאלה). שורה 0 = הכי למטה, שורה 8 = הכי למעלה.
 * התמונות נטענות מ-assets לפי דרישה (ברקע) ונשמרות במטמון קטן, כי 189 תמונות לא נכנסות לזיכרון יחד.
 */
class WeaponMatrixArt(context: Context, val flash: ImageBitmap) : WeaponArt() {
    private val assets = context.applicationContext.assets
    private val cache = LruCache<Int, ImageBitmap>(CACHE_SIZE)
    private val pending = HashSet<Int>()
    private val loader = Executors.newSingleThreadExecutor()

    /** פריימי הטעינה (13), נטענים ברקע פעם אחת */
    val reload = arrayOfNulls<ImageBitmap>(RELOAD_FRAMES)

    /** התמונה האחרונה שצוירה: מצוירת שוב אם התא המבוקש עוד לא נטען */
    @Volatile
    var last: ImageBitmap? = null

    init {
        // התא הנייטרלי נטען מיד, כדי שתמיד יהיה מה לצייר
        decode(cellName(CENTER_COL, CENTER_ROW))?.let { cache.put(cellOf(CENTER_COL, CENTER_ROW), it); last = it }
        loader.execute {
            for (i in 0 until RELOAD_FRAMES) reload[i] = decode("reload_" + i.toString().padStart(2, '0') + ".webp")
        }
    }

    private fun cellName(col: Int, row: Int) = "c" + col.toString().padStart(2, '0') + "_r" + row + ".webp"

    private fun decode(name: String): ImageBitmap? = try {
        assets.open("$DIR/$name").use { stream ->
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
        }
    } catch (e: Exception) {
        null
    }

    /** התמונה של התא אם היא במטמון. אם לא, מבקש טעינה ברקע ומחזיר null */
    fun get(col: Int, row: Int): ImageBitmap? {
        val key = cellOf(col, row)
        cache.get(key)?.let { return it }
        synchronized(pending) { if (!pending.add(key)) return null }
        loader.execute {
            val bitmap = decode(cellName(col, row))
            if (bitmap != null) cache.put(key, bitmap)
            synchronized(pending) { pending.remove(key) }
        }
        return null
    }

    /** טוען מראש את השכנים של התא, כדי שתזוזת הכוונת לא תחכה לטעינה */
    fun warm(col: Int, row: Int) {
        for (dr in -1..1) for (dc in -1..1) {
            val c = col + dc
            val r = row + dr
            if (c in 0 until COLS && r in 0 until ROWS) get(c, r)
        }
    }

    companion object {
        const val DIR = "weapon_v10"
        const val COLS = 21
        const val ROWS = 9
        const val CENTER_COL = 10
        const val CENTER_ROW = 3
        const val RELOAD_FRAMES = 13
        const val CACHE_SIZE = 14
        fun cellOf(col: Int, row: Int) = row * COLS + col

        /** ערך ה-pitch (מעלות) של כל שורה. שלוש השורות התחתונות אינן מעלות אחידות: הן תלויות ב-yaw */
        val PITCHES = floatArrayOf(-8f, -5f, -2f, 0f, 4f, 8f, 12f, 16f, 20f)
    }
}

// החיתוך של הספרייטים ושל פריימי הטעינה מתוך הפריים המלא (שבר מ-1440x810)
private const val CROP_L = 0.15278f
private const val CROP_T = 0.19753f
private const val CROP_W = 0.66389f
private const val CROP_H = 0.80247f
private const val RELOAD_L = 0.26111f
private const val RELOAD_T = 0.08395f
private const val RELOAD_W = 0.67222f
private const val RELOAD_H = 0.91605f

/** מיקום קצה הקנה בכל תא (אינדקס = שורה*21 + עמודה), כשבר מהפריים המלא. חושב מהקרנת המצלמה של Blender */
private val MATRIX_MUZZLE_X = floatArrayOf(0.7568f, 0.7347f, 0.7106f, 0.6855f, 0.6599f, 0.6342f, 0.6083f, 0.5820f, 0.5556f, 0.5293f, 0.5033f, 0.4779f, 0.4526f, 0.4273f, 0.4020f, 0.3767f, 0.3514f, 0.3262f, 0.3002f, 0.2723f, 0.2435f, 0.7561f, 0.7340f, 0.7099f, 0.6845f, 0.6587f, 0.6327f, 0.6066f, 0.5802f, 0.5538f, 0.5276f, 0.5014f, 0.4757f, 0.4499f, 0.4241f, 0.3984f, 0.3726f, 0.3468f, 0.3210f, 0.2949f, 0.2678f, 0.2403f, 0.7553f, 0.7334f, 0.7092f, 0.6836f, 0.6576f, 0.6315f, 0.6053f, 0.5790f, 0.5528f, 0.5266f, 0.5004f, 0.4743f, 0.4482f, 0.4220f, 0.3959f, 0.3697f, 0.3435f, 0.3173f, 0.2910f, 0.2644f, 0.2378f, 0.7547f, 0.7330f, 0.7087f, 0.6831f, 0.6570f, 0.6308f, 0.6047f, 0.5786f, 0.5524f, 0.5263f, 0.5003f, 0.4740f, 0.4476f, 0.4213f, 0.3949f, 0.3685f, 0.3421f, 0.3157f, 0.2892f, 0.2628f, 0.2364f, 0.7481f, 0.7285f, 0.7057f, 0.6809f, 0.6552f, 0.6295f, 0.6038f, 0.5779f, 0.5521f, 0.5262f, 0.5003f, 0.4737f, 0.4472f, 0.4206f, 0.3940f, 0.3674f, 0.3408f, 0.3141f, 0.2874f, 0.2607f, 0.2340f, 0.7403f, 0.7231f, 0.7019f, 0.6779f, 0.6529f, 0.6277f, 0.6024f, 0.5770f, 0.5515f, 0.5259f, 0.5003f, 0.4736f, 0.4469f, 0.4202f, 0.3935f, 0.3668f, 0.3399f, 0.3131f, 0.2862f, 0.2594f, 0.2325f, 0.7313f, 0.7166f, 0.6971f, 0.6743f, 0.6499f, 0.6254f, 0.6007f, 0.5758f, 0.5507f, 0.5256f, 0.5003f, 0.4736f, 0.4469f, 0.4201f, 0.3933f, 0.3665f, 0.3397f, 0.3128f, 0.2858f, 0.2589f, 0.2320f, 0.7209f, 0.7090f, 0.6914f, 0.6698f, 0.6463f, 0.6225f, 0.5985f, 0.5742f, 0.5498f, 0.5251f, 0.5003f, 0.4736f, 0.4470f, 0.4203f, 0.3935f, 0.3668f, 0.3400f, 0.3131f, 0.2862f, 0.2594f, 0.2325f, 0.7093f, 0.7002f, 0.6847f, 0.6644f, 0.6419f, 0.6190f, 0.5958f, 0.5723f, 0.5486f, 0.5246f, 0.5003f, 0.4738f, 0.4472f, 0.4207f, 0.3941f, 0.3675f, 0.3409f, 0.3142f, 0.2875f, 0.2608f, 0.2341f)
private val MATRIX_MUZZLE_Y = floatArrayOf(0.4021f, 0.4239f, 0.4451f, 0.4653f, 0.4839f, 0.5004f, 0.5153f, 0.5211f, 0.5254f, 0.5315f, 0.5395f, 0.5401f, 0.5426f, 0.5470f, 0.5532f, 0.5614f, 0.5716f, 0.5839f, 0.5940f, 0.5951f, 0.5938f, 0.3970f, 0.4176f, 0.4345f, 0.4479f, 0.4591f, 0.4696f, 0.4796f, 0.4845f, 0.4891f, 0.4954f, 0.5034f, 0.5042f, 0.5067f, 0.5111f, 0.5173f, 0.5253f, 0.5353f, 0.5474f, 0.5588f, 0.5652f, 0.5709f, 0.3919f, 0.4112f, 0.4240f, 0.4306f, 0.4346f, 0.4391f, 0.4443f, 0.4483f, 0.4531f, 0.4596f, 0.4676f, 0.4684f, 0.4709f, 0.4752f, 0.4812f, 0.4890f, 0.4987f, 0.5104f, 0.5230f, 0.5348f, 0.5476f, 0.3885f, 0.4070f, 0.4170f, 0.4191f, 0.4183f, 0.4189f, 0.4210f, 0.4244f, 0.4294f, 0.4359f, 0.4439f, 0.4447f, 0.4472f, 0.4513f, 0.4571f, 0.4647f, 0.4742f, 0.4855f, 0.4988f, 0.5143f, 0.5320f, 0.3503f, 0.3697f, 0.3806f, 0.3836f, 0.3835f, 0.3846f, 0.3871f, 0.3909f, 0.3960f, 0.4026f, 0.4106f, 0.4113f, 0.4137f, 0.4176f, 0.4232f, 0.4305f, 0.4395f, 0.4503f, 0.4630f, 0.4778f, 0.4947f, 0.3128f, 0.3329f, 0.3446f, 0.3484f, 0.3489f, 0.3506f, 0.3535f, 0.3576f, 0.3629f, 0.3696f, 0.3776f, 0.3783f, 0.3805f, 0.3842f, 0.3895f, 0.3964f, 0.4050f, 0.4152f, 0.4273f, 0.4413f, 0.4574f, 0.2761f, 0.2967f, 0.3091f, 0.3136f, 0.3148f, 0.3170f, 0.3203f, 0.3247f, 0.3302f, 0.3369f, 0.3449f, 0.3456f, 0.3476f, 0.3512f, 0.3562f, 0.3627f, 0.3707f, 0.3804f, 0.3918f, 0.4050f, 0.4202f, 0.2402f, 0.2612f, 0.2742f, 0.2793f, 0.2812f, 0.2839f, 0.2875f, 0.2922f, 0.2979f, 0.3046f, 0.3125f, 0.3132f, 0.3151f, 0.3184f, 0.3231f, 0.3292f, 0.3367f, 0.3458f, 0.3565f, 0.3689f, 0.3830f, 0.2054f, 0.2264f, 0.2399f, 0.2456f, 0.2480f, 0.2512f, 0.2552f, 0.2601f, 0.2659f, 0.2728f, 0.2806f, 0.2812f, 0.2830f, 0.2861f, 0.2904f, 0.2961f, 0.3031f, 0.3115f, 0.3215f, 0.3330f, 0.3461f)

/** הגודל המבוקש של הרובה בתנוחה הישרה: שבר מגובה המסך ומרוחב המסך */
private const val TARGET_HEIGHT = 0.40f
private const val TARGET_WIDTH = 0.22f

/** הגודל של הרובה בתנוחה הישרה בתוך הפריים המקורי של Blender (נמדד מהספרייט): שבר מגובה ומרוחב הפריים */
private const val NEUTRAL_HEIGHT = 0.5741f
private const val NEUTRAL_WIDTH = 0.3639f

/** כמה זמן (שניות) נמשך המעבר הרך בין שני תאים */
private const val CELL_FADE = 0.07f

/**
 * מצייר את רובה המטריצה. state: [3]=עמודה נוכחית, [4]=שורה נוכחית, [5]=תא מצויר, [6]=זמן ההחלפה, [7]=התא הקודם.
 * הפריים המלא (16:9) מוקטן כך שהרובה תופס כ-40% מגובה המסך, צמוד לתחתית ובמרכז.
 */
internal fun DrawScope.drawWeaponMatrix(
    art: WeaponMatrixArt, weapon: Weapon, tracerTarget: Offset?, timeSeconds: Float,
    aimX: Float, aimY: Float, state: FloatArray
) {
    val cols = WeaponMatrixArt.COLS
    val rows = WeaponMatrixArt.ROWS
    val centerCell = WeaponMatrixArt.cellOf(WeaponMatrixArt.CENTER_COL, WeaponMatrixArt.CENTER_ROW)
    // גודל: בתנוחה הישרה הרובה תופס לכל היותר 40% מגובה המסך ו-22% מרוחבו (בלי עיוות: לוקחים את הקטן מהשניים).
    // הפריים כולו מוקטן סביב אמצע תחתית המסך, כך שהרובה נשאר צמוד לתחתית ובמרכז
    val scale = minOf(TARGET_HEIGHT / NEUTRAL_HEIGHT, TARGET_WIDTH * size.width / (NEUTRAL_WIDTH * size.height * 16f / 9f))
    val frameH = size.height * scale
    val frameW = frameH * 16f / 9f
    val frameL = (size.width - frameW) / 2f
    val frameT = size.height - frameH
    val since = weapon.sinceShot
    val kick = if (since < 0.2f) { val t = since / 0.2f; (1f - t) * (1f - t) } else 0f
    val equip = weapon.sinceEquip
    val rise = if (equip < 0.3f) (1f - equip / 0.3f) * (1f - equip / 0.3f) else 0f
    val ox = kick * frameH * 0.012f
    val oy = kick * frameH * 0.03f + rise * size.height * 0.5f

    // טעינה: 13 פריימים מ-Blender (הטיה, שליפת מחסנית, החזרה). מתחילה ונגמרת בתנוחה הנייטרלית
    if (weapon.reloading) {
        val frames = WeaponMatrixArt.RELOAD_FRAMES
        val pos = weapon.reloadProgress.coerceIn(0f, 1f) * (frames - 1)
        val i0 = pos.toInt().coerceIn(0, frames - 1)
        val i1 = (i0 + 1).coerceAtMost(frames - 1)
        val a = art.reload[i0]
        val b = art.reload[i1]
        if (a != null) {
            val dstOffset = IntOffset((frameL + frameW * RELOAD_L).toInt(), (frameT + frameH * RELOAD_T).toInt())
            val dstSize = IntSize((frameW * RELOAD_W).toInt(), (frameH * RELOAD_H).toInt())
            drawImage(a, IntOffset.Zero, IntSize(a.width, a.height), dstOffset, dstSize)
            val blend = pos - i0
            if (b != null && i1 != i0 && blend > 0.02f) {
                drawImage(b, IntOffset.Zero, IntSize(b.width, b.height), dstOffset, dstSize, alpha = blend)
            }
            // אחרי הטעינה חוזרים לתא הנייטרלי, ומשם הרובה עוקב שוב אחרי הכוונת
            state[3] = WeaponMatrixArt.CENTER_COL.toFloat()
            state[4] = WeaponMatrixArt.CENTER_ROW.toFloat()
            state[5] = centerCell.toFloat()
            state[7] = centerCell.toFloat()
            state[6] = timeSeconds - 1f
            art.warm(WeaponMatrixArt.CENTER_COL, WeaponMatrixArt.CENTER_ROW)
            return
        }
        // פריימי הטעינה עוד לא נטענו: ממשיכים לצייר את הרובה הרגיל
    }

    // עמודה לפי מיקום הכוונת לרוחב: ימין המסך = yaw שלילי = עמודה 0
    val colPos = ((1f - aimX) * (cols - 1)).coerceIn(0f, (cols - 1).toFloat())
    // שורה לפי גובה הכוונת: למעלה = pitch +20, סביב 55% מהגובה = ישר, למטה = הכי נמוך
    val pitch = if (aimY < 0.55f) ((0.55f - aimY) / 0.45f).coerceIn(0f, 1f) * 20f
        else -((aimY - 0.55f) / 0.35f).coerceIn(0f, 1f) * 8f
    val pitches = WeaponMatrixArt.PITCHES
    var rowPos = 0f
    for (r in 0 until pitches.size - 1) {
        if (pitch >= pitches[r]) rowPos = r + ((pitch - pitches[r]) / (pitches[r + 1] - pitches[r])).coerceIn(0f, 1f)
    }
    // היסטרזיס: מחליפים תא רק כשהכוונת עברה בבירור את אמצע הדרך לתא הבא, כדי שלא יהבהב על הגבול
    var col = state[3].toInt().coerceIn(0, cols - 1)
    var row = state[4].toInt().coerceIn(0, rows - 1)
    if (kotlin.math.abs(colPos - col) > 0.62f) col = Math.round(colPos).coerceIn(0, cols - 1)
    if (kotlin.math.abs(rowPos - row) > 0.62f) row = Math.round(rowPos).coerceIn(0, rows - 1)
    state[3] = col.toFloat()
    state[4] = row.toFloat()
    val cell = WeaponMatrixArt.cellOf(col, row)
    if (cell != state[5].toInt().coerceIn(0, cols * rows - 1)) {
        art.warm(col, row)
        // מחליפים רק כשהתמונה החדשה כבר בזיכרון. עד אז ממשיכים להציג את הקודמת
        if (art.get(col, row) != null) {
            state[7] = state[5]
            state[5] = cell.toFloat()
            state[6] = timeSeconds
        }
    }
    val shownCell = state[5].toInt().coerceIn(0, cols * rows - 1)
    val prevCell = state[7].toInt().coerceIn(0, cols * rows - 1)
    val shown = art.get(shownCell % cols, shownCell / cols) ?: art.last ?: return
    val fade = ((timeSeconds - state[6]) / CELL_FADE).coerceIn(0f, 1f)
    val prev = if (fade < 1f && prevCell != shownCell) art.get(prevCell % cols, prevCell / cols) else null
    art.last = shown

    // הלוע: בזמן מעבר בין תאים הוא נע בהדרגה מהמיקום הקודם לחדש
    val mx = MATRIX_MUZZLE_X[prevCell] + (MATRIX_MUZZLE_X[shownCell] - MATRIX_MUZZLE_X[prevCell]) * fade
    val my = MATRIX_MUZZLE_Y[prevCell] + (MATRIX_MUZZLE_Y[shownCell] - MATRIX_MUZZLE_Y[prevCell]) * fade
    val muzzle = Offset(frameL + frameW * mx + ox, frameT + frameH * my + oy)

    if (tracerTarget != null) drawTracer(muzzle, tracerTarget, since)
    val dstOffset = IntOffset((frameL + frameW * CROP_L).toInt(), (frameT + frameH * CROP_T).toInt())
    val dstSize = IntSize((frameW * CROP_W).toInt(), (frameH * CROP_H).toInt())
    translate(ox, oy) {
        if (prev != null) drawImage(prev, IntOffset.Zero, IntSize(prev.width, prev.height), dstOffset, dstSize)
        drawImage(shown, IntOffset.Zero, IntSize(shown.width, shown.height), dstOffset, dstSize, alpha = if (prev != null) fade else 1f)
    }
    // הבזק ירי על הלוע, מכוון אל הכוונת, דועך תוך 90ms
    if (since < 0.09f) {
        val a = (1f - since / 0.09f).coerceIn(0f, 1f)
        val fw = frameH * 0.30f
        val fh = fw * art.flash.height / art.flash.width
        val tx = aimX * size.width
        val ty = aimY * size.height
        val flashRot = Math.toDegrees(kotlin.math.atan2((tx - muzzle.x).toDouble(), (muzzle.y - ty).toDouble())).toFloat().coerceIn(-85f, 85f)
        rotate(flashRot, muzzle) {
            drawImage(
                image = art.flash, srcOffset = IntOffset.Zero, srcSize = IntSize(art.flash.width, art.flash.height),
                dstOffset = IntOffset((muzzle.x - fw / 2f).toInt(), (muzzle.y - fh * 0.80f).toInt()),
                dstSize = IntSize(fw.toInt(), fh.toInt()), alpha = a, blendMode = BlendMode.Plus
            )
        }
    }
}

/** תצוגה מקדימה (חנות): התא הנייטרלי, מותאם לתיבה ומיושר לתחתית */
internal fun DrawScope.drawWeaponMatrixPreview(art: WeaponMatrixArt) {
    val image = art.get(WeaponMatrixArt.CENTER_COL, WeaponMatrixArt.CENTER_ROW) ?: art.last ?: return
    val s = minOf(size.width / image.width, size.height / image.height)
    drawImage(
        image = image, srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(((size.width - image.width * s) / 2f).toInt(), (size.height - image.height * s).toInt()),
        dstSize = IntSize((image.width * s).toInt(), (image.height * s).toInt())
    )
}

package com.arsniper.ui.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * פריים אחד של מפלצת: התמונה כוללת מסגרת לבנה (סטיקר) סביב הדמות, ולכן גדולה מהדמות עצמה ב-pad פיקסלים
 * מכל צד. width ו-height הם הגודל של הדמות עצמה, בלי המסגרת.
 */
class MonsterSprite(
    val image: ImageBitmap,
    val width: Int,
    val height: Int,
    val pad: Int
)

/** כל הפריימים של מפלצת אחת */
class MonsterSheet(
    val idle: MonsterSprite,
    val walk: List<MonsterSprite>,
    val peek: MonsterSprite,
    val hit: MonsterSprite,
    /** גודל יחסי של המפלצת (גובה העמידה שלה ביחס לגודל "אדם" באותו מקום) */
    val sizeScale: Float
)

/**
 * טוען את ספרייטי המפלצות מגיליון אחד: הקובץ monsters_atlas.png בתיקייה res/drawable-nodpi (או res/drawable).
 * הגיליון בנוי משורה לכל מפלצת (ורוד, סגול, ירוק, כתום, כחול) ו-7 עמודות:
 * עמידה, 4 פריימי הליכה, הצצה, פגיעה. כל תא בגודל 280 על 190 והפריים בפינה השמאלית העליונה שלו.
 * אם הקובץ לא קיים, load מחזיר null והמשחק חוזר לדמות הפשוטה.
 */
class MonsterSprites private constructor(private val sheets: List<MonsterSheet>) {

    /** המפלצת של אויב מסוים: קבועה לפי מספר האויב */
    fun forEnemy(id: Int): MonsterSheet = sheets[((id % sheets.size) + sheets.size) % sheets.size]

    companion object {
        private const val ATLAS_NAME = "monsters_atlas"
        private const val CELL_WIDTH = 280
        private const val CELL_HEIGHT = 190

        /** גודל הגיליון המקורי בפיקסלים */
        private const val ATLAS_WIDTH = 1960
        private const val ATLAS_HEIGHT = 950

        /** גודל יחסי לכל מפלצת, לפי סדר השורות בגיליון */
        private val SIZE_SCALES = floatArrayOf(1.05f, 1.0f, 0.8f, 0.88f, 1.15f)

        /** רוחב וגובה של כל פריים בגיליון: שורה לכל מפלצת, עמודה לכל פריים */
        private val FRAME_WIDTHS = arrayOf(
            intArrayOf(275, 209, 223, 213, 192, 254, 268),
            intArrayOf(239, 175, 174, 168, 159, 242, 225),
            intArrayOf(174, 138, 139, 139, 129, 199, 188),
            intArrayOf(181, 181, 184, 167, 164, 225, 216),
            intArrayOf(248, 195, 213, 168, 192, 247, 240)
        )
        private val FRAME_HEIGHTS = arrayOf(
            intArrayOf(186, 182, 182, 181, 182, 124, 185),
            intArrayOf(171, 160, 160, 157, 157, 125, 173),
            intArrayOf(139, 138, 135, 137, 137, 125, 138),
            intArrayOf(136, 135, 136, 136, 133, 131, 137),
            intArrayOf(170, 168, 168, 167, 168, 142, 172)
        )

        private fun atlasId(context: Context): Int =
            context.resources.getIdentifier(ATLAS_NAME, "drawable", context.packageName)

        /** בדיקה זולה: האם גיליון המפלצות הועלה לפרויקט */
        fun isAvailable(context: Context): Boolean = atlasId(context) != 0

        fun load(context: Context): MonsterSprites? {
            val id = atlasId(context)
            if (id == 0) return null
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val atlas = BitmapFactory.decodeResource(context.resources, id, options) ?: return null
            val sheets = ArrayList<MonsterSheet>()
            for (row in SIZE_SCALES.indices) {
                val sheet = loadSheet(atlas, row) ?: continue
                sheets.add(sheet)
            }
            return if (sheets.isEmpty()) null else MonsterSprites(sheets)
        }

        /**
         * חותך פריים מהגיליון. הטבלאות מתארות גיליון בגודל המקורי (1960 על 950), ואם הקובץ שהועלה מוקטן
         * (למשל תמונה שנשמרה מתצוגה מקדימה), כל המידות מוקטנות באותו יחס.
         */
        private fun cut(atlas: Bitmap, row: Int, column: Int): Bitmap? {
            val scaleX = atlas.width / ATLAS_WIDTH.toFloat()
            val scaleY = atlas.height / ATLAS_HEIGHT.toFloat()
            val x = (column * CELL_WIDTH * scaleX).roundToInt()
            val y = (row * CELL_HEIGHT * scaleY).roundToInt()
            val w = min((FRAME_WIDTHS[row][column] * scaleX).roundToInt(), atlas.width - x)
            val h = min((FRAME_HEIGHTS[row][column] * scaleY).roundToInt(), atlas.height - y)
            if (x < 0 || y < 0 || w < 1 || h < 1) return null
            return Bitmap.createBitmap(atlas, x, y, w, h)
        }

        private fun loadSheet(atlas: Bitmap, row: Int): MonsterSheet? {
            val frames = ArrayList<Bitmap>()
            for (column in 0 until 7) {
                frames.add(cut(atlas, row, column) ?: return null)
            }
            // עובי המסגרת יחסי לגובה הדמות, כך שבכל הגדלים היא נראית אותו דבר
            val pad = max(3, frames[0].height / 36)
            fun sprite(column: Int): MonsterSprite {
                val src = frames[column]
                return MonsterSprite(outlined(src, pad).asImageBitmap(), src.width, src.height, pad)
            }
            return MonsterSheet(
                idle = sprite(0),
                walk = listOf(sprite(1), sprite(2), sprite(3), sprite(4)),
                peek = sprite(5),
                hit = sprite(6),
                sizeScale = SIZE_SCALES[row]
            )
        }

        /** מוסיף מסגרת לבנה עבה סביב הצורה (כמו מדבקה): הדמות משורטטת לבנה בכמה היסטים ואז הדמות עצמה מעליה */
        private fun outlined(src: Bitmap, pad: Int): Bitmap {
            val out = Bitmap.createBitmap(src.width + 2 * pad, src.height + 2 * pad, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val white = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            white.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            for (ring in 1..2) {
                val radius = pad * ring / 2f
                for (i in 0 until 16) {
                    val angle = (2.0 * PI * i / 16.0).toFloat()
                    canvas.drawBitmap(
                        src,
                        pad + cos(angle) * radius,
                        pad + sin(angle) * radius,
                        white
                    )
                }
            }
            canvas.drawBitmap(src, pad.toFloat(), pad.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
            return out
        }
    }
}
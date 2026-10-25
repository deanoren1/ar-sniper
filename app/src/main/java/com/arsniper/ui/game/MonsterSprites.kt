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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
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

/** אילו אויבים מתכוננים עכשיו לירות (GameScreen מעדכן בכל פריים): הם מציגים את תנוחת ההתקפה */
object AttackPose {
    @Volatile
    var ids: Set<Int> = emptySet()
}

/** כל הפריימים של מפלצת אחת */
class MonsterSheet(
    val idle: MonsterSprite,
    val walk: List<MonsterSprite>,
    val peek: MonsterSprite,
    val hit: MonsterSprite,
    /** גודל יחסי של המפלצת (גובה העמידה שלה ביחס לגודל "אדם" באותו מקום) */
    val sizeScale: Float,
    /** פריימי הליכה אמיתיים (אם הועלה גיליון הליכה למפלצת הזאת), אחרת null וההליכה מדומה */
    val moves: WalkSet? = null,
    /** מפלצת הבלונים עם פחות בלונים: המפתח הוא מספר הבלונים שנשארו (2 או 1). ריק אם אין ציורים */
    val fewer: Map<Int, WalkSet> = emptyMap()
)

/** לולאות הליכה: 6 פריימים בכל כיוון. צד = פונה ימינה (מתהפך לשמאל), קדימה = אל המצלמה, אחורה = מתרחקת */
class WalkSet(
    val side: List<MonsterSprite>,
    val front: List<MonsterSprite>,
    val back: List<MonsterSprite>,
    val run: List<MonsterSprite>
)

/**
 * טוען את ספרייטי המפלצות מגיליון אחד: הקובץ monsters_atlas.png בתיקייה res/drawable.
 * הגיליון בנוי משורה לכל מפלצת (ורוד, סגול, ירוק, כתום, כחול) ו-7 עמודות:
 * עמידה, 4 פריימי הליכה, הצצה, פגיעה.
 *
 * החיתוך לא תלוי בגודל הקובץ: הגיליון מחולק ל-7 על 5 תאים שווים, ובכל תא מחפשים את הפיקסלים
 * הלא-שקופים של הדמות. אם הקובץ נשמר עם רקע אחיד במקום שקיפות, הרקע מוסר אוטומטית.
 * אם הקובץ לא קיים, load מחזיר null והמשחק חוזר לדמות הפשוטה.
 */
class MonsterSprites private constructor(private val sheets: List<MonsterSheet>) {

    /** המפלצת של אויב מסוים: קבועה לפי מספר האויב */
    fun forEnemy(id: Int): MonsterSheet = sheets[((id % sheets.size) + sheets.size) % sheets.size]

    companion object {
        private const val ATLAS_NAME = "monsters_atlas"
        private const val COLUMNS = 7
        private const val ROWS = 5

        /** פיקסל נחשב חלק מהדמות רק אם השקיפות שלו מעל הסף הזה */
        private const val ALPHA_MIN = 24

        /** מרחק צבע מרקע אחיד שעדיין נחשב רקע */
        private const val BACKGROUND_TOLERANCE = 40

        /** גודל יחסי לכל מפלצת, לפי סדר השורות בגיליון */
        private val SIZE_SCALES = floatArrayOf(1.05f, 1.0f, 0.8f, 0.88f, 1.15f)

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
            val decoded = BitmapFactory.decodeResource(context.resources, id, options) ?: return null
            val atlas = withoutBackground(decoded)
            // בלי צפיפות: אחרת Canvas מגדיל את הפריימים לפי יחס הצפיפות כשמציירים אותם, ורק פינה שלהם נכנסת
            decoded.density = Bitmap.DENSITY_NONE
            atlas.density = Bitmap.DENSITY_NONE
            if (atlas.width < COLUMNS || atlas.height < ROWS) return null
            val sheets = ArrayList<MonsterSheet>()
            for (row in SIZE_SCALES.indices) {
                // מפלצת הבלונים (שורה 3): אם יש ציורים עם 2 או 1 בלונים (walk_3_2, walk_3_1), טוענים גם אותם
                val fewer = HashMap<Int, WalkSet>()
                for (balloons in 1..2) {
                    loadWalk(context, row, "walk_${row}_$balloons")?.let { fewer[balloons] = it }
                }
                val sheet = loadSheet(atlas, row, loadWalk(context, row, "walk_$row"), fewer) ?: continue
                sheets.add(sheet)
            }
            return if (sheets.isEmpty()) null else MonsterSprites(sheets)
        }

        /**
         * אם הפינה השמאלית העליונה אטומה, הגיליון נשמר בלי שקיפות (רקע אחיד).
         * במקרה כזה הופכים לשקוף כל פיקסל שדומה לצבע הפינה.
         */
        private fun withoutBackground(source: Bitmap): Bitmap {
            val corner = source.getPixel(0, 0)
            if (Color.alpha(corner) < 200) return source
            val copy = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
            val pixels = IntArray(copy.width * copy.height)
            copy.getPixels(pixels, 0, copy.width, 0, 0, copy.width, copy.height)
            val r = Color.red(corner)
            val g = Color.green(corner)
            val b = Color.blue(corner)
            for (i in pixels.indices) {
                val p = pixels[i]
                val distance = abs(Color.red(p) - r) + abs(Color.green(p) - g) + abs(Color.blue(p) - b)
                if (distance <= BACKGROUND_TOLERANCE) pixels[i] = 0
            }
            copy.setPixels(pixels, 0, copy.width, 0, 0, copy.width, copy.height)
            return copy
        }

        /**
         * חותך פריים: תא שווה בגיליון, ובתוכו המלבן הצר ביותר שמקיף את כל הפיקסלים הלא-שקופים.
         */
        private fun cut(atlas: Bitmap, row: Int, column: Int): Bitmap? {
            val cellWidth = atlas.width / COLUMNS
            val cellHeight = atlas.height / ROWS
            val x0 = column * cellWidth
            val y0 = row * cellHeight
            val pixels = IntArray(cellWidth * cellHeight)
            atlas.getPixels(pixels, 0, cellWidth, x0, y0, cellWidth, cellHeight)

            var minX = cellWidth
            var minY = cellHeight
            var maxX = -1
            var maxY = -1
            for (y in 0 until cellHeight) {
                val offset = y * cellWidth
                for (x in 0 until cellWidth) {
                    if (Color.alpha(pixels[offset + x]) > ALPHA_MIN) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
            if (maxX < 0 || maxY < 0) return null
            return Bitmap.createBitmap(atlas, x0 + minX, y0 + minY, maxX - minX + 1, maxY - minY + 1)
        }

        private const val WALK_COLUMNS = 6
        private const val WALK_ROWS = 4

        /**
         * גיליון הליכה של מפלצת: הקובץ walk_N.png (N = שורת המפלצת בגיליון הראשי), 6 עמודות על 4 שורות:
         * צד, קדימה, אחורה, ריצה. בכל שורה כל הפריימים נחתכים באותו מלבן, כדי שהתנועה בתוך הפריים תישמר.
         * אין קובץ: מחזיר null.
         */
        private fun loadWalk(context: Context, row: Int, name: String): WalkSet? {
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (id == 0) return null
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeResource(context.resources, id, options) ?: return null
            bitmap.density = Bitmap.DENSITY_NONE
            val cellWidth = bitmap.width / WALK_COLUMNS
            val cellHeight = bitmap.height / WALK_ROWS
            if (cellWidth < 2 || cellHeight < 2) return null
            val lines = ArrayList<List<MonsterSprite>>()
            for (line in 0 until WALK_ROWS) {
                var minX = cellWidth
                var minY = cellHeight
                var maxX = -1
                var maxY = -1
                val pixels = IntArray(cellWidth * cellHeight)
                for (column in 0 until WALK_COLUMNS) {
                    bitmap.getPixels(pixels, 0, cellWidth, column * cellWidth, line * cellHeight, cellWidth, cellHeight)
                    for (y in 0 until cellHeight) {
                        for (x in 0 until cellWidth) {
                            if (Color.alpha(pixels[y * cellWidth + x]) > ALPHA_MIN) {
                                if (x < minX) minX = x
                                if (x > maxX) maxX = x
                                if (y < minY) minY = y
                                if (y > maxY) maxY = y
                            }
                        }
                    }
                }
                if (maxX < 0) return null
                val frames = ArrayList<MonsterSprite>()
                for (column in 0 until WALK_COLUMNS) {
                    val frame = Bitmap.createBitmap(
                        bitmap,
                        column * cellWidth + minX,
                        line * cellHeight + minY,
                        maxX - minX + 1,
                        maxY - minY + 1
                    )
                    frame.density = Bitmap.DENSITY_NONE
                    frames.add(MonsterSprite(frame.asImageBitmap(), frame.width, frame.height, 0))
                }
                lines.add(frames)
            }
            return WalkSet(side = lines[0], front = lines[1], back = lines[2], run = lines[3])
        }

        private fun loadSheet(atlas: Bitmap, row: Int, moves: WalkSet?, fewer: Map<Int, WalkSet>): MonsterSheet? {
            val frames = ArrayList<Bitmap>()
            for (column in 0 until COLUMNS) {
                frames.add(cut(atlas, row, column) ?: return null)
            }
            // עובי המסגרת יחסי לגובה הדמות, כך שבכל הגדלים היא נראית אותו דבר
            // בלי מסגרת לבנה: הציורים החדשים כבר נראים טוב על הרקע (pad = 0)
            fun sprite(column: Int): MonsterSprite {
                val src = frames[column]
                return MonsterSprite(src.asImageBitmap(), src.width, src.height, 0)
            }
            return MonsterSheet(
                idle = sprite(0),
                walk = listOf(sprite(1), sprite(2), sprite(3), sprite(4)),
                peek = sprite(5),
                hit = sprite(6),
                sizeScale = SIZE_SCALES[row],
                moves = moves,
                fewer = fewer
            )
        }

        /** מוסיף מסגרת לבנה עבה סביב הצורה (כמו מדבקה): הדמות משורטטת לבנה בכמה היסטים ואז הדמות עצמה מעליה */
        private fun outlined(src: Bitmap, pad: Int): Bitmap {
            val out = Bitmap.createBitmap(src.width + 2 * pad, src.height + 2 * pad, Bitmap.Config.ARGB_8888)
            out.density = Bitmap.DENSITY_NONE
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
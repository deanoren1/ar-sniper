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
import com.arsniper.game.engine.ArenaDirector
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

    /** התקדמות ההכנה של כל מפלצת מתכוננת (0..1), לאנימציית הבוס */
    @Volatile
    var progress: Map<Int, Float> = emptyMap()

    /** הבוס: כמה שניות מאז הירייה האחרונה, והאם הוא מסוחרר */
    @Volatile
    var bossSinceFire: Float = 999f

    @Volatile
    var bossStunned: Boolean = false

    /** הבוס כועס (שליש חיים אחרון): אדום, נע מהר ורועד */
    @Volatile
    var bossEnraged: Boolean = false
}

/** אנימציות הבוס: נשימה/כנפיים בעמידה, הכנה (פה נפתח ונדלק), ירייה, ופגיעה (האחרון: מסוחרר עם כוכבים) */
class BossAnim(
    val idle: List<MonsterSprite>,
    val prep: List<MonsterSprite>,
    val fire: List<MonsterSprite>,
    val hit: List<MonsterSprite>,
    /** מוות: 6 פריימים (נופל, שוכב, מתפוצץ) */
    val death: List<MonsterSprite>
)

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
    val fewer: Map<Int, WalkSet> = emptyMap(),
    /** רק לבוס: אנימציות מלאות */
    val bossAnim: BossAnim? = null,
    /** מפלצת rig עם גיליון חזית: כשהיא מציצה מאחורי עצם מציירים אותה מלפנים ולא מהצד */
    val frontPeek: Boolean = false
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
class MonsterSprites private constructor(
    /** לכל סוג התנהגות (id % 5 = שורת Foe) מאגר של מראות אפשריים */
    private val pools: List<List<MonsterSheet>>,
    private val boss: MonsterSheet? = null
) {

    /** המפלצת של אויב מסוים: הסוג לפי id % 5 (כדי שהמראה יתאים להתנהגות), והמראה בתוך הסוג קבוע לפי המספר */
    fun forEnemy(id: Int): MonsterSheet {
        if (id == ArenaDirector.BOSS_ID && boss != null) return boss
        val pool = pools[((id % 5) + 5) % 5]
        return pool[(((id / 5) % pool.size) + pool.size) % pool.size]
    }

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

        private fun atlasId(context: Context, variant: String? = null): Int =
            context.resources.getIdentifier(
                if (variant == null) ATLAS_NAME else "${ATLAS_NAME}_$variant", "drawable", context.packageName
            )

        /** גיליון ארנה (monsters_atlas_<סגנון>): 7 עמודות, כל תא 320 על 256, מספר שורות חופשי */
        private const val VARIANT_CELL_HEIGHT = 256

        /** גודל יחסי לגיליון טונדרה: בלון, תמנון, חייזר, ענק, קיפוד, רוח, שלג-אדם, יטי */
        private val VARIANT_SCALES = mapOf(
            "tundra" to floatArrayOf(0.95f, 1.0f, 0.8f, 1.1f, 0.85f, 0.95f, 1.0f, 1.15f),
            "jungle" to floatArrayOf(0.95f, 1.0f, 0.8f, 1.1f, 0.85f, 0.9f, 1.1f, 1.05f),
            "military" to floatArrayOf(0.95f, 1.0f, 0.8f, 1.1f, 0.85f, 1.05f, 1.0f, 0.95f, 0.95f),
            "market" to floatArrayOf(0.95f, 1.0f, 0.8f, 1.1f, 0.85f, 1.0f, 0.95f, 0.95f),
            "ruins" to floatArrayOf(0.95f, 1.0f, 0.8f, 1.1f, 0.85f, 1.0f, 0.95f, 1.1f),
            "forest" to floatArrayOf(0.95f, 1.0f, 0.8f, 1.1f, 0.85f, 1.1f, 0.9f, 0.95f),
            "industrial" to floatArrayOf(0.95f, 1.0f, 0.8f, 1.1f, 0.85f, 0.95f, 1.0f, 1.0f),
            "cyber" to floatArrayOf(0.95f, 1.0f, 0.8f, 1.1f, 0.85f, 0.95f, 0.8f, 0.95f)
        )

        /** בדיקה זולה: האם גיליון המפלצות הועלה לפרויקט */
        fun isAvailable(context: Context): Boolean = atlasId(context) != 0

        fun load(context: Context, variant: String? = null): MonsterSprites? {
            val variantId = if (variant != null) atlasId(context, variant) else 0
            if (variantId != 0) loadVariant(context, variant!!, variantId)?.let { return it }
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
            if (sheets.isEmpty()) return null
            val pools = List(5) { mutableListOf(sheets[minOf(it, sheets.size - 1)]) }
            addRigs(context, "classic", pools)
            return MonsterSprites(pools, loadBoss(context))
        }

        /** גיליון מפלצות של ארנה: אין הליכה אמיתית (מדומה), הבוס נשאר הרגיל */
        private fun loadVariant(context: Context, variant: String, id: Int): MonsterSprites? {
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeResource(context.resources, id, options) ?: return null
            decoded.density = Bitmap.DENSITY_NONE
            val rows = decoded.height / VARIANT_CELL_HEIGHT
            if (rows < 1) return null
            val scales = VARIANT_SCALES[variant]
            val sheets = ArrayList<MonsterSheet>()
            for (row in 0 until rows) {
                val scale = scales?.getOrNull(row) ?: 1.0f
                loadSheet(decoded, row, null, emptyMap(), rows, scale)?.let { sheets.add(it) }
            }
            if (sheets.size < 5) return null
            // שורות Foe (ענק 0, תמנון 1, קיפוד 2, בלון 3, חייזר 4) מול סדר הגיליון (בלון, תמנון, חייזר, ענק, קיפוד)
            val themedIndexForRow = intArrayOf(3, 1, 4, 0, 2)
            val pools = List(5) { row -> mutableListOf(sheets[themedIndexForRow[row]]) }
            // מפלצות נוספות בגיליון (רוח, שלג-אדם...): מצטרפות לסוגי ההתנהגות בסבב
            val extraRows = intArrayOf(1, 0, 2, 4) // בלי שורה 3: שורה 3 מרחפת, ורק מפלצות בלונים אמיתיות מתאימות לה
            for (i in 5 until sheets.size) pools[extraRows[(i - 5) % extraRows.size]].add(sheets[i])
            // בכל ארנה מתערבבות גם המפלצות המקוריות, לא רק המחופשות
            load(context, null)?.let { original ->
                for (row in 0 until 5) pools[row].addAll(original.pools[row])
                // מפלצת הבלונים (שורה 3) רק מהציורים המקוריים: רק להם יש ציור של 2 ושל בלון אחד, והבלונים מתפוצצים בזה אחר זה.
                // בציור מעוצב (למשל טונדרה) הייתה תמיד עם שלושה בלונים גם אחרי פגיעות.
                pools[3].clear()
                pools[3].addAll(original.pools[3])
            }
            addRigs(context, variant, pools)
            return MonsterSprites(pools, loadBoss(context, variant) ?: loadBoss(context))
        }

        private const val RIG_COLUMNS = 6
        private const val RIG_ROWS = 4

        /**
         * מפלצות מונפשות על שלד (rig): קובץ rig_<ארנה>_<סוג>_<n> ב-res/drawable, למשל rig_tundra_1_0.
         * ארנה: classic, tundra, jungle... סוג = שורת ההתנהגות (0 ענק, 1 תמנון, 2 קיפוד, 4 חייזר; לא 3: מפלצת הבלונים מיוחדת).
         * n = 0, 1, 2... כמה שרוצים. המפלצת מצטרפת למאגר של הסוג ומתערבבת עם האחרות. המפרט המלא: docs/MONSTER_SPRITES.md
         */
        private fun addRigs(context: Context, arena: String, pools: List<MutableList<MonsterSheet>>) {
            for (foe in intArrayOf(0, 1, 2, 4)) {
                var n = 0
                while (true) {
                    val name = "rig_${arena}_${foe}_$n"
                    val id = context.resources.getIdentifier(name, "drawable", context.packageName)
                    if (id == 0) break
                    // זוויות נוספות (אופציונלי): rig_..._<n>_front ו-rig_..._<n>_back, לשורת ההליכה אל המצלמה ומהמצלמה
                    val front = context.resources.getIdentifier("${name}_front", "drawable", context.packageName).takeIf { it != 0 }
                    val back = context.resources.getIdentifier("${name}_back", "drawable", context.packageName).takeIf { it != 0 }
                    loadRig(context, id, front, back)?.let { pools[foe].add(it) }
                    n++
                }
            }
        }

        /**
         * גיליון rig: 6 עמודות על 4 שורות. שורה 0: עמידה. שורה 1: הליכה (פונה ימינה). שורה 2: הכנה לירי (3) וירייה (3).
         * שורה 3: פגיעה (2) ומוות (4). כל הפריימים נחתכים באותו מלבן, כך שהרגליים נשארות על אותו קו והגודל קבוע.
         */
        private fun loadRig(context: Context, id: Int, frontId: Int? = null, backId: Int? = null): MonsterSheet? {
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeResource(context.resources, id, options) ?: return null
            bitmap.density = Bitmap.DENSITY_NONE
            val cw = bitmap.width / RIG_COLUMNS
            val ch = bitmap.height / RIG_ROWS
            if (cw < 8 || ch < 8) return null
            var minX = cw
            var minY = ch
            var maxX = -1
            var maxY = -1
            val pixels = IntArray(cw * ch)
            for (row in 0 until RIG_ROWS) {
                for (col in 0 until RIG_COLUMNS) {
                    bitmap.getPixels(pixels, 0, cw, col * cw, row * ch, cw, ch)
                    for (y in 0 until ch) {
                        for (x in 0 until cw) {
                            if (Color.alpha(pixels[y * cw + x]) > ALPHA_MIN) {
                                if (x < minX) minX = x
                                if (x > maxX) maxX = x
                                if (y < minY) minY = y
                                if (y > maxY) maxY = y
                            }
                        }
                    }
                }
            }
            if (maxX < 0) return null
            fun frame(row: Int, col: Int): MonsterSprite {
                val f = Bitmap.createBitmap(bitmap, col * cw + minX, row * ch + minY, maxX - minX + 1, maxY - minY + 1)
                f.density = Bitmap.DENSITY_NONE
                return MonsterSprite(f.asImageBitmap(), f.width, f.height, 0)
            }
            val side = (0 until RIG_COLUMNS).map { frame(1, it) }
            // זוויות אל המצלמה ומהמצלמה: רק שורת ההליכה של הגיליון הנוסף, בגודל אחיד לפי הגיליון הראשי
            fun walkOf(extraId: Int?): List<MonsterSprite> {
                if (extraId == null) return side
                val extra = BitmapFactory.decodeResource(context.resources, extraId, options) ?: return side
                extra.density = Bitmap.DENSITY_NONE
                val ew = extra.width / RIG_COLUMNS
                val eh = extra.height / RIG_ROWS
                if (ew < 8 || eh < 8) return side
                var x0 = ew; var y0 = eh; var x1 = -1; var y1 = -1
                val px = IntArray(ew * eh)
                for (col in 0 until RIG_COLUMNS) {
                    extra.getPixels(px, 0, ew, col * ew, eh, ew, eh)
                    for (y in 0 until eh) for (x in 0 until ew) {
                        if (Color.alpha(px[y * ew + x]) > ALPHA_MIN) {
                            if (x < x0) x0 = x; if (x > x1) x1 = x
                            if (y < y0) y0 = y; if (y > y1) y1 = y
                        }
                    }
                }
                if (x1 < 0) return side
                return (0 until RIG_COLUMNS).map { col ->
                    val f = Bitmap.createBitmap(extra, col * ew + x0, eh + y0, x1 - x0 + 1, y1 - y0 + 1)
                    f.density = Bitmap.DENSITY_NONE
                    MonsterSprite(f.asImageBitmap(), f.width, f.height, 0)
                }
            }
            val moves = if (frontId != null || backId != null) WalkSet(side, walkOf(frontId), walkOf(backId), side) else null
            return MonsterSheet(
                idle = frame(0, 0),
                walk = side,
                peek = frame(2, 2),
                hit = frame(3, 0),
                sizeScale = 1.0f,
                moves = moves,
                frontPeek = frontId != null
            )
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
        private fun cut(atlas: Bitmap, row: Int, column: Int, rows: Int = ROWS, cols: Int = COLUMNS): Bitmap? {
            val cellWidth = atlas.width / cols
            val cellHeight = atlas.height / rows
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

        private const val BOSS_COLUMNS = 6
        private const val BOSS_ROWS = 5

        /**
         * גיליון הבוס (boss_anim.png): 6 עמודות על 5 שורות: עמידה (6 פריימים), הכנה (5), ירייה (2), פגיעה (4), מוות (6).
         * אין קובץ: null והבוס יצויר כמפלצת רגילה.
         */
        private fun loadBoss(context: Context, variant: String? = null): MonsterSheet? {
            val bossName = if (variant == null) "boss_anim" else "boss_anim_$variant"
            val id = context.resources.getIdentifier(bossName, "drawable", context.packageName)
            if (id == 0) return null
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeResource(context.resources, id, options) ?: return null
            bitmap.density = Bitmap.DENSITY_NONE
            if (bitmap.width < BOSS_COLUMNS) return null

            fun line(row: Int, count: Int): List<MonsterSprite> {
                val frames = ArrayList<MonsterSprite>()
                for (column in 0 until count) {
                    val src = cut(bitmap, row, column, BOSS_ROWS, BOSS_COLUMNS) ?: continue
                    src.density = Bitmap.DENSITY_NONE
                    frames.add(MonsterSprite(src.asImageBitmap(), src.width, src.height, 0))
                }
                return frames
            }

            val idle = line(0, 6)
            val prep = line(1, 5)
            val fire = line(2, 2)
            val hit = line(3, 4)
            val death = line(4, 6)
            if (idle.size < 6 || prep.size < 5 || fire.size < 2 || hit.size < 4 || death.size < 6) return null
            return MonsterSheet(
                idle = idle[3],
                walk = listOf(idle[1], idle[2], idle[4], idle[5]),
                peek = prep[3],
                hit = hit[1],
                sizeScale = 1.0f,
                bossAnim = BossAnim(idle, prep, fire, hit, death)
            )
        }

        private fun loadSheet(
            atlas: Bitmap,
            row: Int,
            moves: WalkSet?,
            fewer: Map<Int, WalkSet>,
            rows: Int = ROWS,
            sizeScale: Float = SIZE_SCALES[row]
        ): MonsterSheet? {
            val frames = ArrayList<Bitmap>()
            for (column in 0 until COLUMNS) {
                frames.add(cut(atlas, row, column, rows) ?: return null)
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
                sizeScale = sizeScale,
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
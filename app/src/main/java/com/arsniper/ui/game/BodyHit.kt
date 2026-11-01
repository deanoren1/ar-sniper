package com.arsniper.ui.game

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.arsniper.game.model.Vec2

/**
 * פגיעה לפי הגוף: בכל ציור נרשם הפריים שמצויר עכשיו של כל מפלצת (עם מיקומו וגודלו על המסך),
 * והקשה/ירייה נספרת רק אם הנקודה על הגוף של הציור או קרוב אליו (שוליים קטנים לאצבע).
 * נקודות תורפה ואביזרים לא מושפעים; הם נבדקים לפני הגוף ובנפרד.
 * הקואורדינטות מנורמלות (0..1) כמו העולם.
 */
object BodyRegistry {
    private class Entry(val image: ImageBitmap, val left: Float, val top: Float, val w: Float, val h: Float, val flip: Boolean)

    /** מסכת אלפא מוקטנת של פריים: תא דלוק = יש בו פיקסל גלוי */
    private class Mask(val grid: BooleanArray, val gw: Int, val gh: Int)

    private val entries = HashMap<Int, Entry>()
    private val masks = java.util.WeakHashMap<ImageBitmap, Mask>()

    /** שוליים סביב הגוף, ביחידות גובה המסך */
    const val MARGIN = 0.025f

    private const val GRID = 64
    private const val ALPHA_MIN = 40

    fun clear() = entries.clear()

    fun register(id: Int, image: ImageBitmap, left: Float, top: Float, w: Float, h: Float, flip: Boolean) {
        entries[id] = Entry(image, left, top, w, h, flip)
    }

    private fun maskOf(image: ImageBitmap): Mask {
        masks[image]?.let { return it }
        val bmp = image.asAndroidBitmap()
        val gw = GRID
        val gh = (GRID * bmp.height.toFloat() / bmp.width).toInt().coerceIn(8, GRID * 2)
        val grid = BooleanArray(gw * gh)
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        for (y in 0 until bmp.height) {
            val cy = (y * gh / bmp.height).coerceAtMost(gh - 1)
            for (x in 0 until bmp.width) {
                if ((px[y * bmp.width + x] ushr 24) > ALPHA_MIN) {
                    grid[cy * gw + (x * gw / bmp.width).coerceAtMost(gw - 1)] = true
                }
            }
        }
        return Mask(grid, gw, gh).also { masks[image] = it }
    }

    /**
     * האם הנקודה על הגוף של המפלצת (או בשוליים)? null = אין ציור רשום למפלצת הזאת (ימשיכו לפי העיגול).
     * aspect = רוחב המסך חלקי גובהו (השוליים זהים פיזית בשני הצירים).
     */
    fun contains(id: Int, p: Vec2, aspect: Float, margin: Float = MARGIN): Boolean? {
        val e = entries[id] ?: return null
        val mx = margin / aspect
        if (p.x < e.left - mx || p.x > e.left + e.w + mx || p.y < e.top - margin || p.y > e.top + e.h + margin) return false
        val m = maskOf(e.image)
        val cw = e.w / m.gw
        val ch = e.h / m.gh
        val rx = kotlin.math.ceil(mx / cw).toInt()
        val ry = kotlin.math.ceil(margin / ch).toInt()
        var u = ((p.x - e.left) / cw).toInt()
        if (e.flip) u = m.gw - 1 - u
        val v = ((p.y - e.top) / ch).toInt()
        for (dy in -ry..ry) {
            val y = v + dy
            if (y < 0 || y >= m.gh) continue
            for (dx in -rx..rx) {
                val x = u + dx
                if (x < 0 || x >= m.gw) continue
                if (m.grid[y * m.gw + x]) return true
            }
        }
        return false
    }
}

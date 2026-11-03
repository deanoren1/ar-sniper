package com.arsniper.ui.game

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.arsniper.game.engine.SceneContext

/**
 * צבע הסביבה סביב כל מפלצת (כדי שתיטה קצת לגוון החדר) ומקדם ברק לרצפה (להשתקפות מתחת לרגליים).
 * גריד קטן של הרקע המוצג (עולם AI אם יש, אחרת התמונה), נבנה פעם אחת.
 */
object SceneTint {
    private const val GW = 48
    private const val GH = 27

    @Volatile private var grid: IntArray? = null

    fun set(source: Bitmap?) {
        if (source == null) {
            grid = null
            return
        }
        try {
            val soft = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
            val small = Bitmap.createScaledBitmap(soft, GW, GH, true)
            val px = IntArray(GW * GH)
            small.getPixels(px, 0, GW, 0, 0, GW, GH)
            grid = px
        } catch (_: Throwable) {
            grid = null
        }
    }

    /** גוון הסביבה (מנורמל, ללא בהירות) מעורבב קלות עם לבן: לשימוש כמכפיל על הדמות */
    fun ambientAt(scene: SceneContext?, wx: Float, wy: Float): Color {
        val g = grid ?: return Color.White
        if (scene == null) return Color.White
        val cx = (scene.mapper.photoX(wx) * GW).toInt()
        val cy = (scene.mapper.photoY(wy) * GH).toInt()
        var r = 0f; var gg = 0f; var b = 0f; var n = 0
        for (dy in -2..2) for (dx in -2..2) {
            val x = (cx + dx).coerceIn(0, GW - 1)
            val y = (cy + dy).coerceIn(0, GH - 1)
            val p = g[y * GW + x]
            r += ((p shr 16) and 0xFF) / 255f
            gg += ((p shr 8) and 0xFF) / 255f
            b += (p and 0xFF) / 255f
            n++
        }
        r /= n; gg /= n; b /= n
        val m = maxOf(r, gg, b, 0.001f)
        val k = 0.3f
        return Color(1f + (r / m - 1f) * k, 1f + (gg / m - 1f) * k, 1f + (b / m - 1f) * k, 1f)
    }
}

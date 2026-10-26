package com.arsniper.ui.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.arsniper.game.engine.ShotKind

/** שתי גרסאות לאותו קליע: קטנה ופשוטה (מרחוק) ומפותחת (מקרוב) */
class ShotPair(val small: ImageBitmap?, val big: ImageBitmap?) {
    /** צבע הזוהר: ממוצע הפיקסלים הרוויים בציור עצמו, כך שהזוהר תמיד תואם את הגרסה (כחול, זהוב, ירוק...) */
    val glow: androidx.compose.ui.graphics.Color = computeGlow(big ?: small)

    private fun computeGlow(image: ImageBitmap?): androidx.compose.ui.graphics.Color {
        if (image == null) return androidx.compose.ui.graphics.Color.White
        val bmp = image.asAndroidBitmap()
        val step = maxOf(1, bmp.width / 24)
        var r = 0f; var g = 0f; var b = 0f; var n = 0
        val hsv = FloatArray(3)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val c = bmp.getPixel(x, y)
                if (android.graphics.Color.alpha(c) > 200) {
                    android.graphics.Color.colorToHSV(c, hsv)
                    if (hsv[1] > 0.5f && hsv[2] > 0.45f) {
                        r += android.graphics.Color.red(c); g += android.graphics.Color.green(c); b += android.graphics.Color.blue(c); n++
                    }
                }
                x += step
            }
            y += step
        }
        if (n == 0) return androidx.compose.ui.graphics.Color(0xFFFFE0A0)
        return androidx.compose.ui.graphics.Color(r / n / 255f, g / n / 255f, b / n / 255f)
    }
}

/**
 * ציורי הקליעים: קבצים shot_<סוג>_<a|b>_<small|big> בתיקיית drawable-nodpi.
 * a = צבעים רגילים, b = גרסה שנייה (קרה/עתידנית). חסר קובץ: מחזיר null והקליע מצויר בקוד.
 */
class ShotSprites private constructor(private val map: Map<ShotKind, ShotPair>) {
    fun get(kind: ShotKind): ShotPair? = map[kind]

    companion object {
        private val NAMES = mapOf(
            ShotKind.Bomb to "bomb", ShotKind.Orb to "orb", ShotKind.Gas to "gas",
            ShotKind.Rock to "rock", ShotKind.Spike to "spike", ShotKind.Fire to "fire"
        )

        private fun decode(context: Context, name: String): ImageBitmap? {
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (id == 0) return null
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeResource(context.resources, id, options) ?: return null
            bitmap.density = Bitmap.DENSITY_NONE
            return bitmap.asImageBitmap()
        }

        fun load(context: Context, alt: Boolean): ShotSprites {
            val variant = if (alt) "b" else "a"
            val map = HashMap<ShotKind, ShotPair>()
            for ((kind, name) in NAMES) {
                val small = decode(context, "shot_${name}_${variant}_small")
                val big = decode(context, "shot_${name}_${variant}_big")
                if (small != null || big != null) map[kind] = ShotPair(small ?: big, big ?: small)
            }
            return ShotSprites(map)
        }
    }
}

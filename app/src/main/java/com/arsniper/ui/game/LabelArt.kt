package com.arsniper.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource

/**
 * תוויות צפות מהעיצוב החדש: הטקסט שהקוד מציג (למשל "BLOCK!") מוחלף בתמונה.
 * טקסט שאין לו תמונה ממשיך להיות מצויר כטקסט רגיל.
 */
object LabelArt {
    private val RES = mapOf(
        "BLOCK!" to com.arsniper.R.drawable.lbl_block,
        "STOPPED!" to com.arsniper.R.drawable.lbl_stopped,
        "AMMO FULL" to com.arsniper.R.drawable.lbl_ammofull,
        "+1 HEART" to com.arsniper.R.drawable.lbl_heart,
        "+15" to com.arsniper.R.drawable.lbl_battery,
        "NO DAMAGE  +1 DIAMOND" to com.arsniper.R.drawable.lbl_nodamage
    )

    /** רוחב התמונה ביחידות גובה מסך */
    fun widthFor(text: String): Float = when (text) {
        "BLOCK!" -> 0.42f
        else -> 0.55f
    }

    @Composable
    fun load(): Map<String, ImageBitmap> {
        val out = HashMap<String, ImageBitmap>()
        for ((k, id) in RES) {
            out[k] = ImageBitmap.imageResource(id)
        }
        return out
    }
}

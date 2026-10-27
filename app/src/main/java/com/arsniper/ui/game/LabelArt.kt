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

    /** מזהה התמונה של הודעה, או null אם אין לה תמונה */
    fun resFor(text: String): Int? = RES[text]

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

    private val GOLD = intArrayOf(
        com.arsniper.R.drawable.dg_0, com.arsniper.R.drawable.dg_1, com.arsniper.R.drawable.dg_2,
        com.arsniper.R.drawable.dg_3, com.arsniper.R.drawable.dg_4, com.arsniper.R.drawable.dg_5,
        com.arsniper.R.drawable.dg_6, com.arsniper.R.drawable.dg_7, com.arsniper.R.drawable.dg_8,
        com.arsniper.R.drawable.dg_9
    )
    private val WHITE = intArrayOf(
        com.arsniper.R.drawable.dw_0, com.arsniper.R.drawable.dw_1, com.arsniper.R.drawable.dw_2,
        com.arsniper.R.drawable.dw_3, com.arsniper.R.drawable.dw_4, com.arsniper.R.drawable.dw_5,
        com.arsniper.R.drawable.dw_6, com.arsniper.R.drawable.dw_7, com.arsniper.R.drawable.dw_8,
        com.arsniper.R.drawable.dw_9
    )
    private val ORANGE = intArrayOf(
        com.arsniper.R.drawable.do_0, com.arsniper.R.drawable.do_1, com.arsniper.R.drawable.do_2,
        com.arsniper.R.drawable.do_3, com.arsniper.R.drawable.do_4, com.arsniper.R.drawable.do_5,
        com.arsniper.R.drawable.do_6, com.arsniper.R.drawable.do_7, com.arsniper.R.drawable.do_8,
        com.arsniper.R.drawable.do_9
    )

    /** ספרות זהב לבאנר הגל (0-9) */
    fun goldRes(d: Int): Int = GOLD[d.coerceIn(0, 9)]

    /** ספרות לבנות + "+", וספרות כתומות + "x", לניקוד הצף. מפתחות: "w0".."w9","w+","o0".."o9","ox" */
    @Composable
    fun loadDigits(): Map<String, ImageBitmap> {
        val out = HashMap<String, ImageBitmap>()
        for (i in 0..9) {
            out["w$i"] = ImageBitmap.imageResource(WHITE[i])
            out["o$i"] = ImageBitmap.imageResource(ORANGE[i])
        }
        out["w+"] = ImageBitmap.imageResource(com.arsniper.R.drawable.dw_plus)
        out["ox"] = ImageBitmap.imageResource(com.arsniper.R.drawable.do_x)
        return out
    }

    /** מפרק טקסט ניקוד ("+12" או "+12  x3") לרצף מפתחות, או null אם זה לא ניקוד */
    fun scoreKeys(text: String): List<String>? {
        if (!text.startsWith("+")) return null
        val parts = text.split("  x")
        if (parts.isEmpty() || parts.size > 2) return null
        val num = parts[0].substring(1)
        if (num.isEmpty() || !num.all { it.isDigit() }) return null
        val keys = ArrayList<String>()
        keys.add("w+")
        for (c in num) keys.add("w$c")
        if (parts.size == 2) {
            val m = parts[1]
            if (m.isEmpty() || !m.all { it.isDigit() }) return null
            keys.add("ox")
            for (c in m) keys.add("o$c")
        }
        return keys
    }
}

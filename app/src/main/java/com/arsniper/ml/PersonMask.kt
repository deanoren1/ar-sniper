package com.arsniper.ml

import android.graphics.Rect

/** מסכה של אנשים בתמונה: data[y * width + x] == true כשהפיקסל שייך לאדם. */
class PersonMask(
    val width: Int,
    val height: Int,
    val data: BooleanArray
) {
    private var count: Int = data.count { it }

    /** true כשהמסכה דויקה בעזרת SAM (קצוות מדויקים), ואז מרחיבים אותה פחות לפני ההסרה */
    var precise: Boolean = false

    /** חלק התמונה (0..1) שמסומן כאדם */
    fun coverage(): Float {
        if (data.isEmpty()) return 0f
        return count.toFloat() / data.size
    }

    /**
     * מצייר עיגול במסכה: value=true מוסיף סימון, value=false מוחק.
     * מחזיר את המלבן שהשתנה (ריק אם העיגול מחוץ לתמונה).
     */
    fun paintCircle(centerX: Int, centerY: Int, radius: Int, value: Boolean): Rect {
        val left = (centerX - radius).coerceAtLeast(0)
        val right = (centerX + radius).coerceAtMost(width - 1)
        val top = (centerY - radius).coerceAtLeast(0)
        val bottom = (centerY + radius).coerceAtMost(height - 1)
        if (left > right || top > bottom) return Rect(0, 0, 0, 0)

        val radiusSquared = radius.toLong() * radius
        for (y in top..bottom) {
            val dy = (y - centerY).toLong()
            for (x in left..right) {
                val dx = (x - centerX).toLong()
                if (dx * dx + dy * dy <= radiusSquared) {
                    val index = y * width + x
                    if (data[index] != value) {
                        data[index] = value
                        count += if (value) 1 else -1
                    }
                }
            }
        }
        return Rect(left, top, right + 1, bottom + 1)
    }

    /** מחזיר את המסכה למצב שמור (לביטול פעולה אחרונה) */
    fun restore(source: BooleanArray) {
        System.arraycopy(source, 0, data, 0, data.size)
        count = data.count { it }
    }

    /** מוסיף (value=true) או מוריד (value=false) את כל האזור שמסומן ב-region. region באותו גודל כמו המסכה */
    fun applyRegion(region: BooleanArray, value: Boolean) {
        for (i in data.indices) {
            if (region[i] && data[i] != value) {
                data[i] = value
                count += if (value) 1 else -1
            }
        }
    }
}

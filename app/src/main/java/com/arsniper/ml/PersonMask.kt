package com.arsniper.ml

/** מסכה של אנשים בתמונה: data[y * width + x] == true כשהפיקסל שייך לאדם. */
class PersonMask(
    val width: Int,
    val height: Int,
    val data: BooleanArray
) {
    /** חלק התמונה (0..1) שמסומן כאדם */
    fun coverage(): Float {
        if (data.isEmpty()) return 0f
        var count = 0
        for (value in data) {
            if (value) count++
        }
        return count.toFloat() / data.size
    }
}

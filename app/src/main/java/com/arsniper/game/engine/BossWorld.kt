package com.arsniper.game.engine

/**
 * איזה עולם מארח את שלב הבוס הגדול (הסרטונים, מיקומי נקודות התורפה, הכניסה).
 * tundra = בוס הקרח המקורי (סדקים, פורטל, סרטונים מלאים). עולם אחר = הסרטונים boss_<עולם>_<קטע> ב-res/raw,
 * עם קובץ מעקב boss_<עולם>_tracks (טקסט), והכניסה שלו כבר בתוך סרטון הכניסה (בלי סדקי קרח ופורטל).
 * Kotlin טהור.
 */
object BossWorld {
    @Volatile
    var key: String = TUNDRA

    val isTundra: Boolean get() = key == TUNDRA

    const val TUNDRA = "tundra"

    /** שם הקובץ של קטע בסרטוני העולם (בלי הקידומת boss_<עולם>_) */
    fun suffix(c: BossClip): String = when (c) {
        BossClip.Intro -> "intro"
        BossClip.Breath -> "breath"
        BossClip.Prepare -> "prepare"
        BossClip.Attack1 -> "attack1"
        BossClip.Attack3 -> "attack3"
        BossClip.Attack4 -> "attack4"
        BossClip.Attack5 -> "attack5"
        BossClip.HitHead -> "hit_head"
        BossClip.HitRightShoulder -> "hit_rshoulder"
        BossClip.HitLeftShoulder -> "hit_lshoulder"
        BossClip.HitRightHand -> "hit_rhand"
        BossClip.HitLeftHand -> "hit_lhand"
        BossClip.Death -> "death"
    }

    /**
     * טוען את קובץ המעקב של עולם: שורות "<קטע>: x y x y ..." (5 נקודות לכל דגימה, כל 2 פריימים ב-24fps,
     * בפיקסלים של וידאו 864×496), ושורות RADIUS ו-SHOULDERS. null = בוס הקרח המקורי.
     */
    fun loadTracks(text: String?) {
        if (text == null) {
            IceBossTracks.custom = null
            return
        }
        val map = HashMap<BossClip, IntArray>()
        var radius: FloatArray? = null
        var shoulders = 0f
        for (raw in text.lines()) {
            val line = raw.trim()
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim()
            val nums = line.substring(colon + 1).trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
            when (name) {
                "RADIUS" -> radius = nums.map { it.toFloat() }.toFloatArray()
                "SHOULDERS" -> shoulders = nums.firstOrNull()?.toFloatOrNull() ?: 0f
                else -> {
                    val clip = BossClip.entries.firstOrNull { suffix(it) == name } ?: continue
                    map[clip] = nums.map { it.toFloat().toInt() }.toIntArray()
                }
            }
        }
        IceBossTracks.custom = if (map.isEmpty()) null else IceBossTracks.Custom(map, radius, shoulders)
    }
}

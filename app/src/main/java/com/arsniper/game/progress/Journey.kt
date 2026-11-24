package com.arsniper.game.progress

/**
 * עולם במסע (World Journey): שם, הארנה שהוא מפעיל (null = עוד לא קיים במשחק, "בקרוב"),
 * ומיקום הרחבה שלו במפה כשבר מרוחב וגובה המפה המלאה (0..1).
 */
data class JourneyWorld(
    val title: String,
    val style: ArenaStyle?,
    val x: Float,
    val y: Float
)

/** שער פרק על הגשר: נפתח כשסך הכוכבים מגיע ל-stars. נמצא לפני העולם firstIndex */
data class JourneyGate(
    val firstIndex: Int,
    val stars: Int,
    val x: Float,
    val y: Float
)

/**
 * מפת המסע: 18 עולמות בזיגזג לאורך 4 תמונות מחוברות (journey_map_1..4), ושני איים נעולים בערפל בסוף.
 * עולם נפתח כשהעולם הפתוח הקודם במסע קיבל לפחות כוכב אחד, ועולם בפרק 2 ומעלה צריך גם את סך הכוכבים של השער.
 * עולמות בלי ארנה (style = null) מדולגים בפתיחה ומוצגים כ"בקרוב". Kotlin טהור: הכוכבים מגיעים מבחוץ.
 */
object Journey {

    /** גודל המפה המלאה בפיקסלים (4 תמונות מחוברות) */
    const val MAP_W = 6536f
    const val MAP_H = 846f
    const val TILES = 4

    /** כמה עולמות בכל פרק */
    const val CHAPTER_SIZE = 6

    val worlds: List<JourneyWorld> = listOf(
        JourneyWorld("CLASSIC", ArenaStyle.Classic, 319f, 612f),
        JourneyWorld("FROZEN TUNDRA", ArenaStyle.Tundra, 596f, 330f),
        JourneyWorld("JUNGLE", ArenaStyle.Jungle, 946f, 615f),
        JourneyWorld("CYBERPUNK", ArenaStyle.Cyber, 1250f, 330f),
        JourneyWorld("MILITARY BASE", ArenaStyle.Military, 1574f, 615f),
        JourneyWorld("VOLCANO", null, 2023f, 585f),
        JourneyWorld("INDUSTRIAL", ArenaStyle.Industrial, 2320f, 330f),
        JourneyWorld("RUINS", ArenaStyle.Ruins, 2599f, 585f),
        JourneyWorld("UNDERWATER", null, 2888f, 330f),
        JourneyWorld("SPACE", null, 3157f, 585f),
        JourneyWorld("BAZAAR", ArenaStyle.Market, 3667f, 535f),
        JourneyWorld("ROCKY FOREST", ArenaStyle.Forest, 3883f, 300f),
        JourneyWorld("CANDY WORLD", null, 4107f, 535f),
        JourneyWorld("DESERT", null, 4406f, 300f),
        JourneyWorld("HAUNTED WORLD", null, 4700f, 560f),
        JourneyWorld("CRYSTAL CAVE", null, 5152f, 490f),
        JourneyWorld("PIRATE", null, 5456f, 300f),
        JourneyWorld("CLOUD WORLD", null, 5720f, 520f)
    ).map { it.copy(x = it.x / MAP_W, y = it.y / MAP_H) }

    /** האיים בערפל בסוף המפה: מקום לעולמות עתידיים */
    val fogIslands: List<Pair<Float, Float>> = listOf(
        6052f to 305f,
        6337f to 510f
    ).map { (x, y) -> x / MAP_W to y / MAP_H }

    /** שערי הפרקים, בים שבין השלטים אחרי עולם 6 ואחרי עולם 12. סך כוכבים מצטבר */
    val gates: List<JourneyGate> = listOf(
        JourneyGate(6, 10, 2177f, 480f),
        JourneyGate(12, 20, 4050f, 445f)
    ).map { it.copy(x = it.x / MAP_W, y = it.y / MAP_H) }

    /** סך הכוכבים שהשער שלפני העולם הזה דורש (0 = אין שער) */
    fun gateStarsFor(index: Int): Int = gates.filter { index >= it.firstIndex }.maxOfOrNull { it.stars } ?: 0

    /**
     * האם העולם פתוח. stars = הכוכבים לכל ארנה, total = סך הכוכבים.
     * עולם שכבר יש בו כוכבים נשאר פתוח תמיד (שחקנים קיימים לא מאבדים ארנות).
     */
    fun isUnlocked(index: Int, stars: (ArenaStyle) -> Int, total: Int): Boolean {
        val world = worlds.getOrNull(index) ?: return false
        val style = world.style ?: return false
        if (index == 0) return true
        if (stars(style) >= 1) return true
        val prev = (index - 1 downTo 0).firstOrNull { worlds[it].style != null } ?: return true
        val prevStyle = worlds[prev].style ?: return true
        return stars(prevStyle) >= 1 && total >= gateStarsFor(index)
    }

    /** מיקום הארנה במסע, או -1 */
    fun indexOf(style: ArenaStyle): Int = worlds.indexOfFirst { it.style == style }

    /** העולם הנוכחי: הפתוח הראשון שעוד אין בו כוכב, ואם כולם נוצחו, הפתוח האחרון */
    fun currentIndex(stars: (ArenaStyle) -> Int, total: Int): Int {
        val open = worlds.indices.filter { isUnlocked(it, stars, total) }
        return open.firstOrNull { worlds[it].style?.let(stars) == 0 } ?: open.lastOrNull() ?: 0
    }

    /** סך הכוכבים האפשרי בעולמות שקיימים במשחק */
    val maxStars: Int get() = worlds.count { it.style != null } * 3
}

package com.arsniper.game.progress

/** סוג החלקיקים שנעים מעל התמונה בארנה */
enum class ParticleKind { Snow, Rain, Rise, Drift }

/**
 * סגנון ארנה: גוון, חשכה בקצוות וחלקיקים שמונחים מעל התמונה של השחקן.
 * הצבעים הם ARGB כמספר שלם. הארנות נפתחות לפי הסדר: צריך לפחות כוכב אחד בקודמת. Kotlin טהור.
 */
enum class ArenaStyle(
    val title: String,
    val tint: Long,
    val tintAlpha: Float,
    val vignette: Float,
    val particle: ParticleKind,
    val particleColor: Long,
    val particleCount: Int,
    /** עוצמת הערפל לאורך האופק (0 = בלי) */
    val fog: Float = 0.12f
) {
    /** הארנה הראשונה: בלי קונספט, התמונה של השחקן כמו שהיא עם המפלצות הרגילות */
    Classic("CLASSIC", 0xFF000000, 0f, 0f, ParticleKind.Drift, 0xFFFFFFFF, 0, fog = 0f),
    Tundra("FROZEN TUNDRA", 0xFF9FD8FF, 0.22f, 0.35f, ParticleKind.Snow, 0xFFFFFFFF, 46, fog = 0.22f),
    Jungle("JUNGLE", 0xFF1E7A3A, 0.26f, 0.45f, ParticleKind.Rise, 0xFFC8FF5A, 26, fog = 0.16f),
    Military("MILITARY BASE", 0xFF6B7A3A, 0.24f, 0.40f, ParticleKind.Drift, 0xFFD8D2B0, 30),
    Ruins("ANCIENT RUINS", 0xFFD9A441, 0.26f, 0.42f, ParticleKind.Drift, 0xFFFFE2A0, 34),
    Market("BAZAAR", 0xFFFF8A3D, 0.20f, 0.30f, ParticleKind.Rise, 0xFFFFD27A, 28),
    Cyber("CYBERPUNK", 0xFFB02CFF, 0.30f, 0.50f, ParticleKind.Rain, 0xFF5CF2FF, 36, fog = 0.18f),
    Forest("ENCHANTED FOREST", 0xFF3FBF8F, 0.24f, 0.45f, ParticleKind.Rise, 0xFFB9FFE6, 30),
    Industrial("INDUSTRIAL", 0xFF8A5A3A, 0.28f, 0.50f, ParticleKind.Rise, 0xFFFF9A3C, 32);

    /** הארנה שצריך לנצח לפניה, או null לראשונה (נשאר לתאימות; הפתיחה בפועל לפי שכנים במפה) */
    val previous: ArenaStyle? get() = values().getOrNull(ordinal - 1)

    /** השכנים במפה (הדרכים בין האיים): ארנה נפתחת כשמנצחים באחד מהשכנים בכוכב אחד לפחות */
    val neighbors: List<ArenaStyle>
        get() = when (this) {
            Classic -> listOf(Tundra, Cyber)
            Tundra -> listOf(Classic, Jungle)
            Jungle -> listOf(Tundra, Military, Cyber)
            Military -> listOf(Jungle, Ruins)
            Ruins -> listOf(Military, Market)
            Market -> listOf(Ruins, Industrial)
            Cyber -> listOf(Classic, Jungle, Forest)
            Forest -> listOf(Cyber, Industrial)
            Industrial -> listOf(Forest, Market)
        }

    /** מיקום האי במפה, כשבר מרוחב וגובה התמונה (0..1) */
    val mapX: Float
        get() = when (this) {
            Classic -> 0.15f; Tundra -> 0.16f; Jungle -> 0.37f; Military -> 0.56f; Ruins -> 0.83f
            Market -> 0.88f; Cyber -> 0.48f; Forest -> 0.68f; Industrial -> 0.88f
        }
    val mapY: Float
        get() = when (this) {
            Classic -> 0.79f; Tundra -> 0.34f; Jungle -> 0.44f; Military -> 0.31f; Ruins -> 0.24f
            Market -> 0.54f; Cyber -> 0.80f; Forest -> 0.78f; Industrial -> 0.87f
        }
}

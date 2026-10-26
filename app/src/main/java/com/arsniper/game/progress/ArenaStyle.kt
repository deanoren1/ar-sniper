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
    Tundra("FROZEN TUNDRA", 0xFF9FD8FF, 0.22f, 0.35f, ParticleKind.Snow, 0xFFFFFFFF, 46, fog = 0.22f),
    Jungle("JUNGLE", 0xFF1E7A3A, 0.26f, 0.45f, ParticleKind.Rise, 0xFFC8FF5A, 26, fog = 0.16f),
    Military("MILITARY BASE", 0xFF6B7A3A, 0.24f, 0.40f, ParticleKind.Drift, 0xFFD8D2B0, 30),
    Ruins("ANCIENT RUINS", 0xFFD9A441, 0.26f, 0.42f, ParticleKind.Drift, 0xFFFFE2A0, 34),
    Market("BAZAAR", 0xFFFF8A3D, 0.20f, 0.30f, ParticleKind.Rise, 0xFFFFD27A, 28),
    Cyber("CYBERPUNK", 0xFFB02CFF, 0.30f, 0.50f, ParticleKind.Rain, 0xFF5CF2FF, 36, fog = 0.18f),
    Forest("ENCHANTED FOREST", 0xFF3FBF8F, 0.24f, 0.45f, ParticleKind.Rise, 0xFFB9FFE6, 30),
    Industrial("INDUSTRIAL", 0xFF8A5A3A, 0.28f, 0.50f, ParticleKind.Rise, 0xFFFF9A3C, 32);

    /** הארנה שצריך לנצח לפניה, או null לראשונה */
    val previous: ArenaStyle? get() = values().getOrNull(ordinal - 1)
}

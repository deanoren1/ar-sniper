package com.arsniper.game.model

/**
 * מצלמת המשחק: איזה חלק מהעולם (התמונה) רואים על המסך.
 * העולם והמסך מנורמלים (0..1). ב-zoom=1 כל התמונה על המסך.
 */
data class GameCamera(
    val center: Vec2 = Vec2(0.5f, 0.5f),
    val zoom: Float = 1f
) {

    /** ממיר נקודה בעולם (0..1) למיקום על המסך (0..1) */
    fun worldToScreen(world: Vec2): Vec2 =
        Vec2(
            (world.x - center.x) * zoom + 0.5f,
            (world.y - center.y) * zoom + 0.5f
        )

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 3f

        /** מצלמה שעוקבת אחרי היעד, בלי לצאת מגבולות התמונה */
        fun following(target: Vec2, zoom: Float): GameCamera {
            val z = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
            val half = 0.5f / z
            return GameCamera(
                center = Vec2(
                    target.x.coerceIn(half, 1f - half),
                    target.y.coerceIn(half, 1f - half)
                ),
                zoom = z
            )
        }
    }
}

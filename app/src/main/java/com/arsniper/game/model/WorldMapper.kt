package com.arsniper.game.model

import kotlin.math.max

/**
 * ממיר בין קואורדינטות התמונה (0..1) לקואורדינטות העולם של המשחק (0..1 ביחס למסך).
 * התמונה מוצגת במצב Crop: ממלאת את המסך וחותכת את העודף.
 * אורכים בציר y וברדיוסים נמדדים ביחידות של גובה המסך.
 */
class WorldMapper(
    /** רוחב/גובה של התמונה */
    val photoAspect: Float,
    /** רוחב/גובה של המסך */
    val screenAspect: Float
) {
    // גודל התמונה כפי שהיא מוצגת, ביחידות של גובה המסך
    private val displayedWidth = max(screenAspect, photoAspect)
    private val displayedHeight = max(screenAspect / photoAspect, 1f)

    fun worldX(photoX: Float): Float =
        (photoX * displayedWidth + (screenAspect - displayedWidth) / 2f) / screenAspect

    fun worldY(photoY: Float): Float =
        photoY * displayedHeight + (1f - displayedHeight) / 2f

    fun photoX(worldX: Float): Float =
        (worldX * screenAspect - (screenAspect - displayedWidth) / 2f) / displayedWidth

    fun photoY(worldY: Float): Float =
        (worldY - (1f - displayedHeight) / 2f) / displayedHeight

    /** גובה בתמונה (חלק מגובה התמונה) ליחידות של גובה המסך */
    fun heightToWorld(photoHeight: Float): Float = photoHeight * displayedHeight
}

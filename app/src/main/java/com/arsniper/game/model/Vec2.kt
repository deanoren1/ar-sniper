package com.arsniper.game.model

/** וקטור דו-ממדי פשוט. בשימוש כקואורדינטות מנורמלות (0..1) או כקלט joystick (-1..1). */
data class Vec2(val x: Float, val y: Float) {
    companion object {
        val ZERO = Vec2(0f, 0f)
    }
}

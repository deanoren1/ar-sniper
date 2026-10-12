package com.arsniper.game.model

import com.arsniper.game.spots.HidingSpot

/** סוג התנהגות של אויב */
enum class EnemyKind {
    /** מציץ מהצד של עצם */
    Peek,

    /** מופיע מתוך חלון או דלת */
    Window,

    /** הולך ורץ גלוי על הקרקע */
    Open,

    /** עומד במקום (גיבוי כשאין ניתוח סצנה) */
    Static
}

/** מצב של אויב במכונת המצבים */
enum class EnemyState { Hidden, Peeking, Exposed, Retreating, Idle, Walking, Running }

/**
 * אויב. המיקום מנורמל (0..1) ביחס למסך, והרדיוס נמדד ביחידות של גובה המסך.
 * exposure: 0 = מוסתר לגמרי, 1 = גלוי לגמרי (למציצים ולחלונות).
 */
data class Enemy(
    val id: Int,
    val position: Vec2,
    val radius: Float = 0.08f,
    val isAlive: Boolean = true,
    val kind: EnemyKind = EnemyKind.Static,
    val state: EnemyState = EnemyState.Exposed,
    val spot: HidingSpot? = null,
    val hidePos: Vec2 = position,
    val peekPos: Vec2 = position,
    val exposure: Float = 1f,
    val timer: Float = 0f,
    val direction: Float = 1f
)

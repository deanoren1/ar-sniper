package com.arsniper.game.model

import com.arsniper.game.spots.HidingSpot

/** סוג התנהגות של אויב */
enum class EnemyKind {
    /** מציץ מהצד של עצם */
    Peek,

    /** מופיע מתוך חלון או דלת */
    Window,

    /** הולך ורץ על הקרקע. נכנס מקצה העולם, או יוצא מאחורי עצם (אז spot != null עד שיצא ממנו) */
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
    val direction: Float = 1f,
    /** שלב מחזור ההליכה (רדיאנים), לאנימציית רגליים וידיים */
    val phase: Float = 0f,
    /** תנועה לעומק: +1 = מתקרב למצלמה, -1 = מתרחק, 0 = הולך לרוחב בלבד */
    val dirY: Float = 0f,
    /** חיים: maxHp = כמה פגיעות מפלצת חזקה סופגת (0 = עוד לא נקבע), hp = כמה נשארו */
    val maxHp: Int = 0,
    val hp: Int = 0,
    /** מתי נפגעה לאחרונה (שניות, לפי System.nanoTime), להבהוב ולרעידה */
    val hurtAt: Double = -100.0
)
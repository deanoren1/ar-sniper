package com.arsniper.game.model

/**
 * אויב. המיקום מנורמל (0..1) ביחס למסך,
 * והרדיוס נמדד ביחידות של גובה המסך.
 */
data class Enemy(
    val id: Int,
    val position: Vec2,
    val radius: Float = 0.08f,
    val isAlive: Boolean = true
)

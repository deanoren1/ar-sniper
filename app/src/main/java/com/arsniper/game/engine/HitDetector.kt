package com.arsniper.game.engine

import com.arsniper.game.model.Enemy
import com.arsniper.game.model.Vec2

object HitDetector {

    /**
     * פגיעה = הכוונת בתוך רדיוס האויב.
     * aspect (רוחב חלקי גובה) מתקן את ציר ה-x כדי שהמרחק יהיה זהה פיזית בשני הצירים.
     */
    fun isHit(crosshair: Vec2, enemy: Enemy, aspect: Float): Boolean {
        val dx = (crosshair.x - enemy.position.x) * aspect
        val dy = crosshair.y - enemy.position.y
        return dx * dx + dy * dy <= enemy.radius * enemy.radius
    }
}

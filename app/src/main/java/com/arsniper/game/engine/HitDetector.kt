package com.arsniper.game.engine

import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import com.arsniper.game.model.Vec2

object HitDetector {

    /** מתחת לחשיפה הזו האויב נחשב מוסתר ואי אפשר לפגוע בו */
    private const val MIN_EXPOSURE = 0.3f

    /**
     * פגיעה = הכוונת בתוך רדיוס האויב, והאויב חשוף, והנקודה בחלק הגלוי שלו.
     * מציץ: לא פוגעים בחלק שמוסתר מאחורי העצם. חלון: פוגעים רק בתוך מלבן החלון.
     * aspect (רוחב חלקי גובה) מתקן את ציר ה-x כדי שהמרחק יהיה זהה פיזית בשני הצירים.
     */
    fun isHit(
        crosshair: Vec2,
        enemy: Enemy,
        aspect: Float,
        scene: SceneContext? = null
    ): Boolean {
        if (!enemy.isAlive) return false
        if (enemy.exposure < MIN_EXPOSURE) return false

        val dx = (crosshair.x - enemy.position.x) * aspect
        val dy = crosshair.y - enemy.position.y
        if (dx * dx + dy * dy > enemy.radius * enemy.radius) return false

        val spot = enemy.spot
        if (spot != null && scene != null) {
            return when (enemy.kind) {
                EnemyKind.Peek -> !scene.spotContains(spot, crosshair.x, crosshair.y)
                EnemyKind.Window -> {
                    val rect = WindowRects.of(spot)
                    rect != null && rect.contains(
                        scene.mapper.photoX(crosshair.x),
                        scene.mapper.photoY(crosshair.y)
                    )
                }
                else -> true
            }
        }
        return true
    }
}

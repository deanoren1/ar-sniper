package com.arsniper.game.engine

import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import com.arsniper.game.model.EnemyState
import com.arsniper.game.model.Vec2

object HitDetector {

    /** מתחת לחשיפה הזו האויב נחשב מוסתר ואי אפשר לפגוע בו */
    private const val MIN_EXPOSURE = 0.3f

    /**
     * פגיעה = הכוונת בתוך רדיוס האויב, והאויב חשוף, והנקודה בחלק הגלוי שלו.
     * מציץ: לא פוגעים בחלק שמוסתר מאחורי העצם. דמות הולכת: לא פוגעים בחלק שעצם קרוב יותר מכסה. חלון: פוגעים רק בתוך מלבן החלון.
     * aspect (רוחב חלקי גובה) מתקן את ציר ה-x כדי שהמרחק יהיה זהה פיזית בשני הצירים.
     */
    fun isHit(
        crosshair: Vec2,
        enemy: Enemy,
        aspect: Float,
        scene: SceneContext? = null
    ): Boolean {
        if (!enemy.isAlive) return false
        // דמות שעדיין מוסתרת לגמרי מאחורי עצם ומחכה לצאת
        if (enemy.kind == EnemyKind.Open && enemy.spot != null && enemy.state == EnemyState.Idle) return false
        if (enemy.exposure < MIN_EXPOSURE) return false

        val dx = (crosshair.x - enemy.position.x) * aspect
        val dy = crosshair.y - enemy.position.y
        if (dx * dx + dy * dy > enemy.radius * enemy.radius) return false

        // עצם שקרוב יותר למצלמה מכסה את הדמות: אי אפשר לפגוע בה דרכו
        if (scene != null) {
            val blockers = Occlusion.nearerSpots(scene, enemy)
            if (blockers.isNotEmpty() && Occlusion.covered(scene, blockers, crosshair.x, crosshair.y)) return false
        }

        val spot = enemy.spot
        if (spot != null && scene != null) {
            return when (enemy.kind) {
                EnemyKind.Peek, EnemyKind.Open -> !scene.spotContains(spot, crosshair.x, crosshair.y)
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
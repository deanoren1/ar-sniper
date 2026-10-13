package com.arsniper.game.engine

import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import com.arsniper.game.model.EnemyState
import com.arsniper.game.model.Vec2
import com.arsniper.game.spots.HidingSpot
import com.arsniper.game.spots.SpotKind
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** תוצאת ירייה */
sealed interface FireResult {
    data class Hit(val enemy: Enemy) : FireResult
    data object Miss : FireResult
}

/**
 * מנוע המשחק: Kotlin טהור, בלי תלות ב-Android.
 * הכוונת והאויבים בקואורדינטות עולם מנורמלות (0..1) ביחס למסך.
 */
class GameEngine(
    /** מהירות מלאה: חלק מרוחב העולם בשנייה (ב-zoom=1) */
    private val crosshairSpeed: Float = 0.4f,
    private val random: Random = Random.Default
) {

    private var nextEnemyId: Int = 1
    private var scene: SceneContext? = null

    /** מגדיר את הסצנה (או null אם אין ניתוח: אז האויבים עומדים במקומות אקראיים) */
    fun configure(scene: SceneContext?) {
        this.scene = scene
    }

    /**
     * input: כיוון ה-joystick (-1..1 בכל ציר)
     * aspect: רוחב המסך חלקי גובהו, כדי שהמהירות תהיה זהה פיזית בשני הצירים
     * zoom: ככל שמתקרבים, הכוונת איטית יותר (כיוון מדויק)
     */
    fun moveCrosshair(
        current: Vec2,
        input: Vec2,
        dtSeconds: Float,
        aspect: Float,
        zoom: Float = 1f
    ): Vec2 {
        val dt = dtSeconds.coerceAtMost(0.05f)
        val speed = crosshairSpeed / zoom.coerceAtLeast(1f)
        val dx = input.x * speed * dt
        val dy = input.y * speed * dt * aspect
        return Vec2(
            (current.x + dx).coerceIn(0f, 1f),
            (current.y + dy).coerceIn(0f, 1f)
        )
    }

    /** ירייה: מחזירה את האויב שנפגע, או Miss אם פספסנו */
    fun fire(
        crosshair: Vec2,
        enemies: List<Enemy>,
        aspect: Float
    ): FireResult {
        val target = enemies.firstOrNull {
            HitDetector.isHit(crosshair, it, aspect, scene)
        }
        return if (target != null) FireResult.Hit(target) else FireResult.Miss
    }

    // ---------------------------------------------------------------
    // התנהגות אויבים
    // ---------------------------------------------------------------

    /** מקדם את כל האויבים החיים בפריים אחד */
    fun update(enemies: List<Enemy>, dtSeconds: Float): List<Enemy> {
        val dt = dtSeconds.coerceAtMost(0.05f)
        return enemies.map { enemy ->
            if (!enemy.isAlive) {
                enemy
            } else {
                when (enemy.kind) {
                    EnemyKind.Peek, EnemyKind.Window -> stepPeeker(enemy, dt)
                    EnemyKind.Open -> stepOpen(enemy, dt)
                    EnemyKind.Static -> enemy
                }
            }
        }
    }

    private fun stepPeeker(enemy: Enemy, dt: Float): Enemy = when (enemy.state) {
        EnemyState.Hidden -> {
            val timer = enemy.timer - dt
            if (timer <= 0f) enemy.copy(state = EnemyState.Peeking, timer = 0f)
            else enemy.copy(timer = timer)
        }

        EnemyState.Peeking -> {
            val exposure = min(enemy.exposure + dt / PEEK_SECONDS, 1f)
            val moved = enemy.copy(exposure = exposure, position = placeAt(enemy, exposure))
            if (exposure >= 1f) {
                moved.copy(state = EnemyState.Exposed, timer = randomBetween(1.0f, 1.8f))
            } else {
                moved
            }
        }

        EnemyState.Exposed -> {
            val timer = enemy.timer - dt
            if (timer <= 0f) enemy.copy(state = EnemyState.Retreating, timer = 0f)
            else enemy.copy(timer = timer)
        }

        EnemyState.Retreating -> {
            val exposure = (enemy.exposure - dt / RETREAT_SECONDS).coerceAtLeast(0f)
            val moved = enemy.copy(exposure = exposure, position = placeAt(enemy, exposure))
            if (exposure <= 0f) {
                // אחרי שהסתתר: לפעמים יציץ מהצד השני
                val relocated = if (enemy.kind == EnemyKind.Peek && random.nextFloat() < 0.5f) {
                    enemy.spot?.let { spawnAtSpot(it) }
                } else {
                    null
                }
                if (relocated != null) {
                    relocated.copy(id = enemy.id, timer = randomBetween(1.0f, 2.5f))
                } else {
                    moved.copy(state = EnemyState.Hidden, timer = randomBetween(1.2f, 3.0f))
                }
            } else {
                moved
            }
        }

        else -> enemy
    }

    private fun stepOpen(enemy: Enemy, dt: Float): Enemy {
        val timer = enemy.timer - dt
        return when (enemy.state) {
            EnemyState.Idle -> {
                if (timer <= 0f) {
                    enemy.copy(state = EnemyState.Walking, timer = randomBetween(1.5f, 4f))
                } else {
                    enemy.copy(timer = timer)
                }
            }

            EnemyState.Walking -> {
                val moved = moveAlongGround(enemy, WALK_SPEED, dt)
                if (timer <= 0f) {
                    if (random.nextFloat() < 0.3f) {
                        moved.copy(state = EnemyState.Running, timer = randomBetween(0.8f, 1.6f))
                    } else {
                        moved.copy(state = EnemyState.Idle, timer = randomBetween(0.5f, 1.5f))
                    }
                } else {
                    moved.copy(timer = timer)
                }
            }

            EnemyState.Running -> {
                val moved = moveAlongGround(enemy, RUN_SPEED, dt)
                if (timer <= 0f) {
                    moved.copy(state = EnemyState.Walking, timer = randomBetween(1.5f, 3f))
                } else {
                    moved.copy(timer = timer)
                }
            }

            else -> enemy
        }
    }

    /** הולך לאורך הקרקע; אם הצעד הבא לא על קרקע, מסתובב */
    private fun moveAlongGround(enemy: Enemy, speedInRadii: Float, dt: Float): Enemy {
        val ctx = scene
        val aspect = ctx?.mapper?.screenAspect ?: 2f
        val stepX = enemy.direction * enemy.radius * speedInRadii * dt / aspect
        val newX = enemy.position.x + stepX
        val feetY = enemy.position.y + enemy.radius
        val canStep = if (ctx != null) {
            ctx.isWalkable(newX, feetY)
        } else {
            newX in 0.05f..0.95f
        }
        return if (canStep) {
            enemy.copy(position = Vec2(newX, enemy.position.y))
        } else {
            enemy.copy(direction = -enemy.direction)
        }
    }

    private fun placeAt(enemy: Enemy, exposure: Float): Vec2 {
        val t = exposure * exposure * (3f - 2f * exposure) // תנועה חלקה
        return Vec2(
            enemy.hidePos.x + (enemy.peekPos.x - enemy.hidePos.x) * t,
            enemy.hidePos.y + (enemy.peekPos.y - enemy.hidePos.y) * t
        )
    }

    // ---------------------------------------------------------------
    // יצירת אויבים
    // ---------------------------------------------------------------

    /**
     * משלים את רשימת האויבים עד targetCount אויבים חיים.
     * אויבים מתים מוסרים, וחדשים נוצרים: בערך 60% מציצים/חלונות (אם יש מחבואים), השאר גלויים.
     */
    fun replenish(
        current: List<Enemy>,
        targetCount: Int,
        @Suppress("UNUSED_PARAMETER") aspect: Float
    ): List<Enemy> {
        val result = current.filter { it.isAlive }.toMutableList()
        var failures = 0
        while (result.size < targetCount && failures < 12) {
            val enemy = spawnOne(result, targetCount)
            if (enemy != null) result.add(enemy) else failures++
        }
        return result
    }

    private fun spawnOne(existing: List<Enemy>, targetCount: Int): Enemy? {
        val ctx = scene ?: return spawnStatic(existing)
        val coverCount = existing.count { it.kind == EnemyKind.Peek || it.kind == EnemyKind.Window }
        val coverWanted = ceil(targetCount * 0.6f).toInt()
        if (ctx.spots.isNotEmpty() && coverCount < coverWanted) {
            spawnCover(existing)?.let { return it }
        }
        return spawnOpen(existing) ?: spawnCover(existing)
    }

    private fun spawnCover(existing: List<Enemy>): Enemy? {
        val ctx = scene ?: return null
        val usedIds = existing.mapNotNull { it.spot?.id }.toSet()
        val candidates = ctx.spots
            .filter { it.id !in usedIds }
            // עדיפות למחבואים גדולים, עם מעט אקראיות
            .sortedByDescending { random.nextFloat() * sqrt(it.cellCount.toFloat()) }
        for (spot in candidates.take(12)) {
            val enemy = spawnAtSpot(spot) ?: continue
            if (!tooClose(enemy.peekPos, existing, ctx.mapper.screenAspect, 0.15f)) {
                return enemy.copy(timer = randomBetween(0.3f, 2.5f))
            }
        }
        return null
    }

    /**
     * גובה הקרקע (y בעולם) שעליה העצם עומד, או null אם העצם לא נוגע בקרקע
     * (למשל צמרת עץ, צמח על שולחן, תקרה). בודקים כמה נקודות מתחת לקצה התחתון של העצם.
     */
    private fun groundLevel(spot: HidingSpot): Float? {
        val ctx = scene ?: return null
        val mapper = ctx.mapper
        var samples = 0
        var grounded = 0
        var bottomSum = 0f
        for (i in 0 until 5) {
            val nx = spot.minX + (spot.maxX - spot.minX) * (0.1f + 0.2f * i)
            val bottom = spot.columnBottom(nx) ?: continue
            samples++
            if (ctx.isWalkable(mapper.worldX(nx), mapper.worldY(bottom + GROUND_PROBE))) {
                grounded++
                bottomSum += bottom
            }
        }
        if (grounded == 0 || grounded * 3 < samples) return null
        return mapper.worldY(bottomSum / grounded)
    }

    /** יוצר אויב שמציץ מהצד של עצם, או יוצא מחלון. null אם המקום לא מתאים. */
    private fun spawnAtSpot(spot: HidingSpot): Enemy? {
        val ctx = scene ?: return null
        val mapper = ctx.mapper
        val aspect = mapper.screenAspect

        val left = mapper.worldX(spot.minX)
        val right = mapper.worldX(spot.maxX)
        val top = mapper.worldY(spot.minY)
        val bottom = mapper.worldY(spot.maxY)
        if (right < 0.05f || left > 0.95f || bottom < 0.05f || top > 0.95f) return null

        val widthInHeights = (right - left) * aspect
        val heightInHeights = bottom - top
        val baseRadius = radiusForDepth(spot.depth)

        return if (spot.kind == SpotKind.WINDOW) {
            val r = min(baseRadius, min(widthInHeights, heightInHeights) * 0.4f)
            if (r < MIN_WINDOW_RADIUS) return null
            val centerX = (left + right) / 2f
            if (centerX < 0.04f || centerX > 0.96f) return null
            // האויב עולה מלמטה אל תוך החלון, ונחתך לגבולות החלון
            val peek = Vec2(centerX, bottom - r)
            // חלון שחלקו העליון חתוך מחוץ למסך לא מתאים
            if (peek.y - r < 0.02f || peek.y > 0.95f) return null
            val hide = Vec2(centerX, bottom + r * 1.1f)
            Enemy(
                id = nextEnemyId++,
                position = hide,
                radius = r,
                kind = EnemyKind.Window,
                state = EnemyState.Hidden,
                spot = spot,
                hidePos = hide,
                peekPos = peek,
                exposure = 0f,
                timer = randomBetween(0.3f, 2.5f)
            )
        } else {
            val r = minOf(baseRadius, widthInHeights / 2.3f, heightInHeights * 0.6f)
            if (r < MIN_COVER_RADIUS) return null

            // האויב עומד על הקרקע ליד העצם: עצמים שלא נוגעים בקרקע (צמרות, תקרה...) לא מתאימים
            val footY = groundLevel(spot) ?: return null
            val centerY = footY - r
            if (centerY < 0.05f || centerY > 0.95f) return null

            val goLeft = random.nextBoolean()
            val outward = if (goLeft) -1f else 1f
            val edgeX = if (goLeft) left else right

            val peekX = edgeX + outward * (0.1f * r) / aspect
            val hideX = edgeX - outward * (1.15f * r) / aspect
            if (peekX < 0.04f || peekX > 0.96f) return null

            // בצד שבו הוא מציץ צריכה להיות קרקע, ובמקום ההסתתרות צריך להיות העצם עצמו
            val groundBesideX = edgeX + outward * (0.6f * r) / aspect
            if (!ctx.isWalkable(groundBesideX, footY + 0.01f)) return null
            if (!ctx.spotContains(spot, hideX, centerY)) return null

            val peek = Vec2(peekX, centerY)
            val hide = Vec2(hideX, centerY)
            Enemy(
                id = nextEnemyId++,
                position = hide,
                radius = r,
                kind = EnemyKind.Peek,
                state = EnemyState.Hidden,
                spot = spot,
                hidePos = hide,
                peekPos = peek,
                exposure = 0f,
                timer = randomBetween(0.3f, 2.5f)
            )
        }
    }

    /** אויב גלוי שהולך על הקרקע (כביש, מדרכה, רצפה...) */
    private fun spawnOpen(existing: List<Enemy>): Enemy? {
        val ctx = scene ?: return null
        repeat(40) {
            val x = random.nextFloat() * 0.8f + 0.1f
            val feetY = random.nextFloat() * 0.5f + 0.45f
            if (!ctx.isWalkable(x, feetY)) return@repeat
            // לא על כתם קרקע קטן: גם משני הצדדים צריכה להיות קרקע
            if (!ctx.isWalkable(x - 0.05f, feetY) || !ctx.isWalkable(x + 0.05f, feetY)) return@repeat
            val r = radiusForDepth(ctx.depthAt(x, feetY))
            val position = Vec2(x, feetY - r)
            if (tooClose(position, existing, ctx.mapper.screenAspect, 0.2f)) return@repeat
            return Enemy(
                id = nextEnemyId++,
                position = position,
                radius = r,
                kind = EnemyKind.Open,
                state = EnemyState.Walking,
                timer = randomBetween(1f, 3f),
                direction = if (random.nextBoolean()) 1f else -1f
            )
        }
        return null
    }

    /** גיבוי: אויב עומד במקום אקראי (כשאין ניתוח, או כשלא נמצא מקום מתאים) */
    private fun spawnStatic(existing: List<Enemy>): Enemy {
        val aspect = scene?.mapper?.screenAspect ?: 2f
        var position = randomPosition()
        repeat(15) {
            if (!tooClose(position, existing, aspect, 0.25f)) {
                return Enemy(id = nextEnemyId++, position = position)
            }
            position = randomPosition()
        }
        return Enemy(id = nextEnemyId++, position = position)
    }

    private fun randomPosition(): Vec2 =
        Vec2(random.nextFloat() * 0.6f + 0.2f, random.nextFloat() * 0.45f + 0.2f)

    private fun tooClose(position: Vec2, existing: List<Enemy>, aspect: Float, minDistance: Float): Boolean =
        existing.any { other ->
            val dx = (position.x - other.peekPos.x) * aspect
            val dy = position.y - other.peekPos.y
            val dx2 = (position.x - other.position.x) * aspect
            val dy2 = position.y - other.position.y
            sqrt(dx * dx + dy * dy) < minDistance || sqrt(dx2 * dx2 + dy2 * dy2) < minDistance
        }

    /**
     * רדיוס אויב לפי עומק: גובה אדם ~1.7 מטר, ובמצלמת טלפון רגילה הוא תופס
     * בערך 1.84/עומק מגובה התמונה. הרדיוס מוגבל כדי שיישאר נוח לכוון.
     */
    private fun radiusForDepth(depthMeters: Float): Float {
        val ctx = scene ?: return 0.08f
        val depth = if (depthMeters.isNaN() || depthMeters < 1f) 8f else depthMeters
        val heightInPhoto = 1.84f / depth
        val heightInWorld = ctx.mapper.heightToWorld(heightInPhoto)
        return (heightInWorld * 0.3f).coerceIn(0.03f, 0.11f)
    }

    private fun randomBetween(from: Float, to: Float): Float =
        from + random.nextFloat() * (to - from)

    companion object {
        private const val PEEK_SECONDS = 0.3f
        private const val RETREAT_SECONDS = 0.25f

        /** מהירות הליכה/ריצה ברדיוסים בשנייה */
        private const val WALK_SPEED = 1.2f
        private const val RUN_SPEED = 3.2f

        /** כמה (ביחידות תמונה) מתחת לעצם בודקים אם יש קרקע */
        private const val GROUND_PROBE = 0.012f

        private const val MIN_COVER_RADIUS = 0.025f
        private const val MIN_WINDOW_RADIUS = 0.02f
    }
}

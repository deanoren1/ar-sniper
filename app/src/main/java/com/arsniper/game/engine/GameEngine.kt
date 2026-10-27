package com.arsniper.game.engine

import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import com.arsniper.game.model.EnemyState
import com.arsniper.game.model.Vec2
import com.arsniper.ml.Ade20kClasses
import com.arsniper.game.spots.HidingSpot
import com.arsniper.game.spots.SpotKind
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** תוצאת ירייה */
sealed interface FireResult {
    data class Hit(val enemy: Enemy) : FireResult
    data object Miss : FireResult
}

/** כמה רדיוסים מתחת לאדן החלון נמצא מרכז האויב כשהוא מוסתר (גם ל-EnemyLayer) */
const val WINDOW_HIDE_OFFSET = 1.3f

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

    /** כמה זמן (שניות) כל דמות הולכת נתקעה במקום (מסתובבת בלי להתקדם); מי שנתקעה זמן רב מוסרת */
    private val stuckTime = HashMap<Int, Float>()
    private var scene: SceneContext? = null

    /** מגדיר את הסצנה (או null אם אין ניתוח: אז האויבים עומדים במקומות אקראיים) */
    fun configure(scene: SceneContext?) {
        this.scene = scene
        stuckTime.clear()
        this.scale = if (scene != null) calibrateScale(scene) else DEFAULT_SCALE
        this.ground = if (scene != null) GroundModel.fit(scene) else GroundModel.default()
    }

    /** מודל הקרקע: גודל אדם לפי מיקום הרגליים בתמונה (פרספקטיבה) */
    private var ground: GroundModel = GroundModel.default()

    /** כמה גדול אדם בתמונה הזו: מכויל לפי עצמים שמוכר גובהם האמיתי (רכב, שולחן, דלת...) */
    private var scale: Float = DEFAULT_SCALE

    /**
     * מכייל את קנה המידה של הסצנה: לכל עצם מוכר, גובה בתמונה * עומק / גובה אמיתי נותן את
     * המקדם k, ואז גובה אדם בתמונה = k * 1.7 / עומק. לוקחים חציון כדי שטעויות בודדות לא ישפיעו.
     * אם אין מספיק עצמים מוכרים, משתמשים בערך ברירת מחדל.
     */
    private fun calibrateScale(ctx: SceneContext): Float {
        val samples = ArrayList<Float>()
        for (spot in ctx.spots) {
            val name = Ade20kClasses.NAMES.getOrNull(spot.classIndex) ?: continue
            val real = REAL_HEIGHTS[name] ?: continue
            if (spot.depth.isNaN() || spot.depth < 0.8f) continue
            // עצם שנחתך בקצה התמונה נראה נמוך מדי
            if (spot.minX < 0.01f || spot.maxX > 0.99f || spot.minY < 0.01f || spot.maxY > 0.99f) continue
            samples.add((spot.maxY - spot.minY) * spot.depth / real)
        }
        if (samples.size < 2) return DEFAULT_SCALE
        samples.sort()
        return samples[samples.size / 2].coerceIn(MIN_SCALE, MAX_SCALE)
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
        // דמות שיצאה מהעולם (הלכה אל מחוץ לתמונה) מוסרת, ובהמשך תיכנס דמות חדשה
        return enemies.mapNotNull { enemy ->
            if (!enemy.isAlive) {
                enemy
            } else {
                when (enemy.kind) {
                    EnemyKind.Peek, EnemyKind.Window -> stepPeeker(enemy, dt)
                    EnemyKind.Open -> stepOpen(enemy, dt, enemies)
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

    private fun stepOpen(enemy: Enemy, dt: Float, all: List<Enemy>): Enemy? {
        val hiding = enemy.spot
        if (hiding != null) return stepEmerging(enemy, hiding, dt)

        val timer = enemy.timer - dt
        return when (enemy.state) {
            EnemyState.Idle -> {
                if (timer <= 0f) {
                    enemy.copy(
                        state = EnemyState.Walking,
                        timer = randomBetween(1.5f, 4f),
                        dirY = pickDepthDirection(enemy)
                    )
                } else {
                    enemy.copy(timer = timer)
                }
            }

            EnemyState.Walking -> {
                val moved = moveAlongGround(enemy, WALK_SPEED, dt, all) ?: return null
                if (isStuck(enemy, moved, dt)) return null
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
                val moved = moveAlongGround(enemy, RUN_SPEED, dt, all) ?: return null
                if (isStuck(enemy, moved, dt)) return null
                if (timer <= 0f) {
                    moved.copy(state = EnemyState.Walking, timer = randomBetween(1.5f, 3f))
                } else {
                    moved.copy(timer = timer)
                }
            }

            else -> enemy
        }
    }

    /**
     * האם הדמות תקועה: לא התקדמה בפריים הזה (רק הסתובבה) כבר זמן רב, למשל כשהיא צמודה לעצם
     * וכל צעד לשני הכיוונים נחסם. דמות תקועה מוסרת, ובמקומה תיכנס אחרת.
     */
    private fun isStuck(before: Enemy, after: Enemy, dt: Float): Boolean {
        if (after.position != before.position || after.radius != before.radius) {
            stuckTime.remove(before.id)
            return false
        }
        val total = (stuckTime[before.id] ?: 0f) + dt
        if (total >= STUCK_SECONDS) {
            stuckTime.remove(before.id)
            return true
        }
        stuckTime[before.id] = total
        return false
    }

    /**
     * דמות שיוצאת מאחורי עצם: קודם מחכה מוסתרת (Idle), ואז הולכת החוצה בכיוון קבוע.
     * ברגע שהיא כולה מחוץ לעצם היא נעשית דמות רגילה.
     */
    private fun stepEmerging(enemy: Enemy, spot: HidingSpot, dt: Float): Enemy {
        if (enemy.state == EnemyState.Idle) {
            val timer = enemy.timer - dt
            return if (timer <= 0f) {
                enemy.copy(state = EnemyState.Walking, timer = 0f)
            } else {
                enemy.copy(timer = timer)
            }
        }
        val ctx = scene
        val aspect = ctx?.mapper?.screenAspect ?: 2f
        val step = enemy.direction * enemy.radius * WALK_SPEED * dt / aspect
        val newX = enemy.position.x + step
        val moved = enemy.copy(
            position = Vec2(newX, enemy.position.y),
            phase = enemy.phase + WALK_SPEED * dt * PHASE_PER_RADIUS
        )
        if (ctx == null) return moved.copy(spot = null)
        val half = FIGURE_HALF_WIDTH * enemy.radius / aspect
        val yTop = enemy.position.y - 1.2f * enemy.radius
        val yBottom = enemy.position.y + enemy.radius
        val stillBehind = coverage(ctx, spot, newX - half, newX + half, yTop, yBottom) > 0.02f
        return if (stillBehind) {
            moved
        } else {
            moved.copy(spot = null, state = EnemyState.Walking, timer = randomBetween(1.5f, 4f))
        }
    }

    /**
     * בוחר לאן הדמות הולכת בעומק: מתקרבת, מתרחקת או לרוחב. מעדיף להישאר בתוך הרצפה הנראית:
     * קרוב מאוד לתחתית המסך (או קרוב לאופק) מכוון בחזרה פנימה.
     */
    private fun pickDepthDirection(enemy: Enemy): Float {
        val ctx = scene ?: return 0f
        val feetPhoto = ctx.mapper.photoY(enemy.position.y + enemy.radius)
        if (feetPhoto > 0.85f) return -1f
        if (feetPhoto - ground.horizon < 0.12f) return 1f
        val roll = random.nextFloat()
        return if (roll < 0.65f) 1f else if (roll < 0.72f) -1f else 0f
    }

    /**
     * הולך על הקרקע, לרוחב ואולי גם לעומק. בהליכה לעומק הרגליים עולות או יורדות במסך, והגודל משתנה
     * לפי הפרספקטיבה. אם הצעד הבא לא על קרקע, מאחורי עצם קרוב יותר, או בתוך דמות אחרת: מסתובב.
     * ליד קצה העולם אין בדיקות: כך דמות נכנסת מהקצה, ויוצאת ממנו (ואז מחזיר null: הוסרה).
     */
    private fun moveAlongGround(enemy: Enemy, speedInRadii: Float, dt: Float, all: List<Enemy>): Enemy? {
        val ctx = scene
        val aspect = ctx?.mapper?.screenAspect ?: 2f
        val halfWidth = FIGURE_HALF_WIDTH * enemy.radius / aspect
        val lateral = if (enemy.dirY != 0f) DEPTH_WALK_LATERAL else 1f
        val stepX = enemy.direction * lateral * enemy.radius * speedInRadii * dt / aspect
        val newX = enemy.position.x + stepX
        if ((enemy.direction > 0f && newX > 1f + halfWidth) || (enemy.direction < 0f && newX < -halfWidth)) {
            return null
        }
        val phase = enemy.phase + speedInRadii * dt * PHASE_PER_RADIUS

        var newFeetY = enemy.position.y + enemy.radius
        var newRadius = enemy.radius
        if (ctx != null && enemy.dirY != 0f) {
            val mapper = ctx.mapper
            val feetPhoto = mapper.photoY(newFeetY)
            val above = max(feetPhoto - ground.horizon, 0.02f)
            val dPhoto = enemy.dirY * DEPTH_SPEED * above * above * (speedInRadii / WALK_SPEED) * dt
            val newFeetPhoto = feetPhoto + dPhoto
            // יציאה מהמסך בתחתית, ובקצה הרחוק מסתובבים
            if (mapper.worldY(newFeetPhoto) > 0.97f) return null
            if (newFeetPhoto - ground.horizon < MIN_ABOVE_HORIZON) {
                return enemy.copy(dirY = 1f)
            }
            newFeetY = mapper.worldY(newFeetPhoto)
            newRadius = radiusForFeet(newFeetY)
            // דמות שהולכת לעבר המצלמה לא נהיית ענקית: מגיעה לגודל מקסימלי ומסתובבת בחזרה
            if (enemy.dirY > 0f && newRadius * FIGURE_HEIGHT_RADII > MAX_WALK_HEIGHT) {
                return enemy.copy(dirY = -1f)
            }
        }

        val inEdgeBand = newX < EDGE_BAND || newX > 1f - EDGE_BAND
        val canStep: Boolean = if (inEdgeBand) {
            true
        } else if (ctx != null) {
            // צריך קרקע, ולא להיכנס מאחורי עצם שקרוב אלינו יותר, ולא לדחוף דמות אחרת
            ctx.isWalkable(newX, newFeetY + GROUND_PROBE) &&
                !behindNearerObject(ctx, newX, newFeetY, newRadius, ctx.depthAt(newX, newFeetY)) &&
                !blockedByOther(enemy, newX, all, aspect)
        } else {
            newX >= 0.05f && newX <= 0.95f
        }
        return if (canStep) {
            enemy.copy(
                position = Vec2(newX, newFeetY - newRadius),
                radius = newRadius,
                phase = phase
            )
        } else {
            enemy.copy(direction = -enemy.direction, dirY = -enemy.dirY)
        }
    }

    /** האם הצעד מקרב אותנו לדמות אחרת עד כדי חפיפה (רק כשהמרחק קטן מסכום הגדלים וגם קטן יותר מקודם) */
    private fun blockedByOther(enemy: Enemy, newX: Float, all: List<Enemy>, aspect: Float): Boolean {
        for (other in all) {
            if (other.id == enemy.id || !other.isAlive) continue
            if (other.kind == EnemyKind.Open) {
                if (other.spot != null && other.state == EnemyState.Idle) continue
            } else if (other.exposure < 0.3f) {
                continue
            }
            val limit = 1.05f * (enemy.radius + other.radius)
            val dy = (enemy.position.y + enemy.radius) - (other.position.y + other.radius)
            val dxOld = (enemy.position.x - other.position.x) * aspect
            val dxNew = (newX - other.position.x) * aspect
            val dOld = sqrt(dxOld * dxOld + dy * dy)
            val dNew = sqrt(dxNew * dxNew + dy * dy)
            if (dNew < limit && dNew < dOld) return true
        }
        return false
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
        // דמות הולכת: נכנסת מקצה העולם או יוצאת מאחורי עצם. אף פעם לא מופיעה סתם באמצע
        return spawnEntrant(existing) ?: spawnCover(existing)
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
            // מחבואים יכולים להיות קרובים זה לזה (שני משטחים זה ליד זה), לכן מרווח קטן יותר
            if (!tooClose(enemy.peekPos, enemy.radius, existing, ctx.mapper.screenAspect, 0.12f, 0.8f)) {
                return enemy.copy(timer = randomBetween(0.3f, 2.5f))
            }
            coverFail[9]++
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
            // עצם שנחתך בתחתית המסך (למשל משטח קרוב): הקרקע ממשיכה מתחתיו, גם אם היא לא נראית
            val cutByScreen = mapper.worldY(bottom) >= SCREEN_BOTTOM_CUT
            if (cutByScreen || ctx.isWalkable(mapper.worldX(nx), mapper.worldY(bottom + GROUND_PROBE))) {
                grounded++
                bottomSum += bottom
            }
        }
        if (grounded == 0 || grounded * 3 < samples) return null
        return mapper.worldY(bottomSum / grounded)
    }

    /**
     * מחזיר את החלק (0..1) מנקודות הדגימה במלבד שנמצא בתוך העצם.
     * המלבן בקואורדינטות עולם: x מ-x0 עד x1 (בכל סדר), y מ-y0 עד y1.
     */
    private fun coverage(
        ctx: SceneContext,
        spot: HidingSpot,
        x0: Float,
        x1: Float,
        y0: Float,
        y1: Float
    ): Float {
        var inside = 0
        var total = 0
        for (i in 0 until COVERAGE_STEPS) {
            val x = x0 + (x1 - x0) * (i + 0.5f) / COVERAGE_STEPS
            for (j in 0 until COVERAGE_STEPS) {
                val y = y0 + (y1 - y0) * (j + 0.5f) / COVERAGE_STEPS
                total++
                if (ctx.spotContains(spot, x, y)) inside++
            }
        }
        return inside.toFloat() / total
    }

    /**
     * האם הגוף של אדם שרגליו ב-(x, feetY) היה מוסתר בפועל על ידי עצם שקרוב אלינו יותר ממנו.
     * עצם מסתיר אדם כשהוא מכסה נקודה בגוף, והבסיס שלו נמוך יותר במסך מרגלי האדם (כלומר הוא קרוב יותר).
     * אם כן, אסור לצייר את האדם שם "מלפני" העצם (כמו לעמוד על משטח). לא משתמשים בעומק, כי העומק
     * בנקודת הרגליים הוא העומק של העצם עצמו. ignore = העצם שהדמות יוצאת ממנו.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun behindNearerObject(
        ctx: SceneContext,
        x: Float,
        feetY: Float,
        r: Float,
        feetDepth: Float,
        ignore: HidingSpot? = null
    ): Boolean {
        val mapper = ctx.mapper
        val aspect = mapper.screenAspect
        val ys = floatArrayOf(feetY - 0.2f * r, feetY - r, feetY - 1.8f * r)
        val dxs = floatArrayOf(-0.4f, 0f, 0.4f)
        for (spot in ctx.spots) {
            if (spot === ignore) continue
            for (y in ys) {
                for (dx in dxs) {
                    val px = x + dx * r / aspect
                    if (!ctx.spotContains(spot, px, y)) continue
                    val bottom = spot.columnBottom(mapper.photoX(px)) ?: continue
                    if (mapper.worldY(bottom) > feetY + OCCLUDER_MARGIN) return true
                }
            }
        }
        return false
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
        if (right < 0.05f || left > 0.95f || bottom < 0.05f || top > 0.95f) return rej(0)

        val widthInHeights = (right - left) * aspect
        val heightInHeights = bottom - top

        if (spot.kind == SpotKind.WINDOW) {
            // מלבן נקי בתוך החלון (לא המסכה המקוטעת)
            val rect = WindowRects.of(spot) ?: return null
            val wLeft = mapper.worldX(rect.minX)
            val wRight = mapper.worldX(rect.maxX)
            val wTop = mapper.worldY(rect.minY)
            val sill = mapper.worldY(rect.maxY)
            // אדם עומד מאחורי חלון רק אם אדן החלון בגובה סביר (לא חלון גבוה ליד התקרה)
            if (sill < WINDOW_MIN_SILL || sill > 0.95f) return null

            val r = radiusForDepth(spot.depth)
            if (!sizeOk(r)) return null
            // האדם צריך להיכנס בחלון: רואים רק את החלק העליון (עד המותניים), כמו מי שמציץ מבפנים
            val halfW = FIGURE_HALF_WIDTH * r / aspect
            if (wRight - wLeft < 2.2f * halfW) return null
            if (sill - wTop < 1.3f * r) return null

            val centerX = wLeft + halfW + (wRight - wLeft - 2f * halfW) * random.nextFloat()
            if (centerX < 0.05f || centerX > 0.95f) return null
            // במצב חשוף המותניים באדן: ראש וחזה בלבד נראים, והחצי התחתון חתוך באדן
            val peek = Vec2(centerX, sill)
            val hide = Vec2(centerX, sill + WINDOW_HIDE_OFFSET * r)
            return Enemy(
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
            // האויב עומד על הקרקע ליד העצם: עצמים שלא נוגעים בקרקע (צמרות, תקרה...) לא מתאימים
            val footY = groundLevel(spot) ?: return rej(1)

            val goLeft = random.nextBoolean()
            val outward = if (goLeft) -1f else 1f
            val edgeX = if (goLeft) left else right

            // גודל האדם לפי העומק בנקודה שבה הוא עומד
            val r = radiusForFeet(footY)
            if (!sizeOk(r)) return rej(2)

            return if (heightInHeights >= TALL_OBJECT_RADII * r) {
                // עצם גבוה כמו אדם: האדם מציץ מהצד שלו
                spawnSidePeek(spot, ctx, r, footY, edgeX, outward, widthInHeights)
            } else {
                // עצם נמוך (רכב, פח, שולחן): האדם עולה מאחוריו ורואים רק את החלק העליון שלו
                spawnRise(spot, ctx, r, widthInHeights)
            }
        }
    }

    /** גודל סביר לדמות שקשורה למחבוא: לא זעירה ולא ענקית */
    private fun sizeOk(r: Float): Boolean {
        val height = r * FIGURE_HEIGHT_RADII
        return height >= SPOT_MIN_HEIGHT && height <= SPOT_MAX_HEIGHT
    }

    /** מציץ מהצד של עצם גבוה, בקצה נקי */
    private fun spawnSidePeek(
        spot: HidingSpot,
        ctx: SceneContext,
        r: Float,
        footY: Float,
        edgeX: Float,
        outward: Float,
        widthInHeights: Float
    ): Enemy? {
        val aspect = ctx.mapper.screenAspect
        if (widthInHeights < 1.8f * r) return null
        val centerY = footY - r
        if (centerY < 0.05f || centerY > 0.95f) return null

        val peekX = edgeX + outward * (0.1f * r) / aspect
        val hideX = edgeX - outward * (0.75f * r) / aspect
        if (peekX < 0.04f || peekX > 0.96f) return null

        // בצד שבו הוא מציץ צריכה להיות קרקע
        val groundBesideX = edgeX + outward * (0.6f * r) / aspect
        if (!ctx.isWalkable(groundBesideX, footY + 0.01f)) return null

        // בדיקת קצה נקי: הקצה של העצם חייב להיות קו ברור לאורך כל גובה הדמות.
        // (1) במקום ההסתתרות הדמות כולה בתוך העצם
        val yTop = centerY - 1.2f * r
        val yBottom = centerY + 0.9f * r
        val hideHalf = (FIGURE_HALF_WIDTH + 0.05f) * r / aspect
        if (coverage(ctx, spot, hideX - hideHalf, hideX + hideHalf, yTop, yBottom) < 0.85f) return null
        // (2) בהצצה: החלק שמאחורי הקצה בתוך העצם, והחלק שבחוץ באמת בחוץ
        val behindEnd = edgeX - outward * (0.45f * r) / aspect
        if (coverage(ctx, spot, behindEnd, edgeX, yTop, yBottom) < 0.85f) return null
        val outEnd = edgeX + outward * (0.65f * r) / aspect
        if (coverage(ctx, spot, edgeX + outward * 0.02f, outEnd, yTop, yBottom) > 0.15f) return null

        val peek = Vec2(peekX, centerY)
        val hide = Vec2(hideX, centerY)
        return Enemy(
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

    /**
     * אדם שעולה מאחורי עצם נמוך: במצב חשוף המותניים בקצה העליון של העצם, כלומר רואים ראש וחזה
     * מעל הקצה, והחלק התחתון מוסתר על ידי העצם. ההסתתרות היא ירידה מאחורי העצם.
     */
    private fun spawnRise(
        spot: HidingSpot,
        ctx: SceneContext,
        r: Float,
        widthInHeights: Float
    ): Enemy? {
        val mapper = ctx.mapper
        val aspect = mapper.screenAspect
        if (widthInHeights < 1.3f * r) return rej(3)

        // מיקום אופקי: הדמות כולה צריכה להיכנס ברוחב העצם (אם העצם צר: במרכז)
        val half = (FIGURE_HALF_WIDTH + 0.05f) * r / aspect
        val halfPhoto = mapper.photoX(0.5f + half) - mapper.photoX(0.5f)
        val lo = spot.minX + halfPhoto
        val hi = spot.maxX - halfPhoto
        val nx = if (lo < hi) lo + (hi - lo) * random.nextFloat() else (spot.minX + spot.maxX) / 2f
        val topPhoto = spot.columnTop(nx) ?: return rej(4)
        val bottomPhoto = spot.columnBottom(nx) ?: return rej(4)
        val objTop = mapper.worldY(topPhoto)
        val objBottom = mapper.worldY(bottomPhoto)
        val cx = mapper.worldX(nx)
        if (cx < 0.05f || cx > 0.95f || objTop < 0.12f || objTop > 0.95f) return rej(5)
        // לעצם צריך להיות מספיק גובה כדי להסתיר את החצי התחתון
        if (objBottom - objTop < 1.1f * r) return rej(6)

        // מעל הקצה העליון: פנוי (שם רואים את האדם)
        if (coverage(ctx, spot, cx - half, cx + half, objTop - 1.15f * r, objTop - 0.08f * r) > 0.15f) return rej(7)
        // מתחת לקצה: העצם מכסה
        val coverBottom = min(objTop + 1.0f * r, objBottom - 0.05f * r)
        if (coverage(ctx, spot, cx - half, cx + half, objTop + 0.08f * r, coverBottom) < 0.85f) return rej(8)

        val peek = Vec2(cx, objTop)
        val hide = Vec2(cx, objTop + WINDOW_HIDE_OFFSET * r)
        return Enemy(
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

    /** דמות הולכת חדשה: יוצאת מאחורי עצם גבוה, נכנסת מקצה התמונה, או מגיעה מקצה הרצפה הרחוק */
    private val failCounts = IntArray(4)

    /** מונה סיבות דחייה של מחבואים (לאבחון): 0 מחוץ למסך, 1 אין קרקע, 2 גודל, 3-8 עולה מאחורי עצם נמוך, 9 קרוב מדי, 10 מציץ מהצד, 11 חלון */
    private val coverFail = IntArray(12)

    private fun rej(reason: Int): Enemy? {
        coverFail[reason]++
        return null
    }

    private fun spawnEntrant(existing: List<Enemy>): Enemy? {
        val order = when (random.nextInt(3)) {
            0 -> listOf(0, 1, 2)
            1 -> listOf(1, 2, 0)
            else -> listOf(2, 0, 1)
        }
        for (way in order) {
            val enemy = when (way) {
                0 -> spawnEmerge(existing)
                1 -> spawnEdge(existing)
                else -> spawnFar(existing)
            }
            if (enemy != null) return enemy
            failCounts[way]++
        }
        failCounts[3]++
        return null
    }

    /**
     * דמות שמגיעה מהקצה הרחוק של הרצפה (למשל סוף המעבר), קטנה ממש, והולכת לעבר המצלמה.
     * מוצאים בעמודה אקראית את הנקודה הראשונה של רצפה מתחת לקו האופק.
     */
    private fun spawnFar(existing: List<Enemy>): Enemy? {
        val ctx = scene ?: return null
        val mapper = ctx.mapper
        val aspect = mapper.screenAspect

        // לכל עמודה: הנקודה הרחוקה ביותר (הגבוהה ביותר בתמונה) של רצפה מתחת לקו האופק
        val columns = ArrayList<Float>()
        val firsts = ArrayList<Float>()
        var px = 0.1f
        while (px <= 0.9f) {
            val wx = mapper.worldX(px)
            if (wx >= 0.1f && wx <= 0.9f) {
                var py = max(ground.horizon + 0.03f, 0.05f)
                while (py < 0.9f) {
                    if (ctx.isWalkable(wx, mapper.worldY(py))) {
                        columns.add(px)
                        firsts.add(py + 0.02f)
                        break
                    }
                    py += 0.01f
                }
            }
            px += 0.02f
        }
        if (columns.isEmpty()) return null
        // רק העמודות שבהן הרצפה מגיעה הכי רחוק (סוף המעבר, קצה החדר)
        val farthest = firsts.min()
        val candidates = columns.indices.filter { firsts[it] <= farthest + FAR_BAND }

        repeat(20) {
            val index = candidates[random.nextInt(candidates.size)]
            val wx = mapper.worldX(columns[index])
            val feetPhoto = firsts[index]
            val feetY = mapper.worldY(feetPhoto)
            val r = radiusForFeet(feetY)
            val figureHeight = r * FIGURE_HEIGHT_RADII
            if (figureHeight < FAR_MIN_HEIGHT || figureHeight > FAR_MAX_HEIGHT) return@repeat

            // הדרך קדימה (לעבר המצלמה) צריכה להיות קרקע
            var clear = true
            for (k in 1..FAR_PATH_STEPS) {
                if (!ctx.isWalkable(wx, mapper.worldY(feetPhoto + k * 0.03f))) {
                    clear = false
                    break
                }
            }
            if (!clear) return@repeat
            if (tooClose(Vec2(wx, feetY - r), r, existing, aspect, 0.1f)) return@repeat
            // לא מתחילים מאחורי עצם קרוב יותר (אז רואים רק חלק מהדמות דרך חורים)
            if (behindNearerObject(ctx, wx, feetY, r, ctx.depthAt(wx, feetY))) return@repeat

            val candidate = Enemy(
                id = nextEnemyId,
                position = Vec2(wx, feetY - r),
                radius = r,
                kind = EnemyKind.Open,
                state = EnemyState.Walking,
                timer = randomBetween(4f, 7f),
                direction = if (random.nextBoolean()) 1f else -1f,
                dirY = 1f
            )
            // חור קטן בין צמחים: הדמות לא יכולה ללכת, הייתה נתקעת ונעלמת מיד
            if (!canRoam(candidate, existing)) return@repeat
            nextEnemyId++
            return candidate
        }
        return null
    }

    /**
     * מדמה שתי שניות הליכה של דמות שעומדת להיווצר, באותם כללי תנועה כמו במשחק.
     * דמות שנתקעת (כל צעד חסום) הייתה מוסרת תוך פחות משנייה, אז לא יוצרים אותה בכלל.
     */
    private fun canRoam(candidate: Enemy, existing: List<Enemy>): Boolean {
        var e = candidate
        var still = 0f
        repeat(ROAM_STEPS) {
            val next = moveAlongGround(e, WALK_SPEED, ROAM_DT, existing) ?: return true // יצאה מהעולם: עברה בדרך
            if (next.position == e.position && next.radius == e.radius) {
                still += ROAM_DT
                if (still >= ROAM_STUCK_SECONDS) return false
            } else {
                still = 0f
            }
            e = next
        }
        return true
    }

    /**
     * דמות שמתחילה מחוץ לתמונה (בקצה ימין או שמאל) על קרקע שמתחילה שם, וצועדת פנימה.
     * בודקים שהדרך הראשונה פנימה פנויה (קרקע, בלי עצמים קרובים יותר), ושהגודל סביר.
     */
    private fun spawnEdge(existing: List<Enemy>): Enemy? =
        spawnEdge(existing, STRICT_MIN_HEIGHT, STRICT_MAX_HEIGHT)
            ?: spawnEdge(existing, RELAXED_MIN_HEIGHT, RELAXED_MAX_HEIGHT)

    private fun spawnEdge(existing: List<Enemy>, minHeight: Float, maxHeight: Float): Enemy? {
        val ctx = scene ?: return null
        val aspect = ctx.mapper.screenAspect
        repeat(30) {
            val fromLeft = random.nextBoolean()
            val inward = if (fromLeft) 1f else -1f
            val gateX = if (fromLeft) EDGE_GATE else 1f - EDGE_GATE
            // יותר ניסיונות לרחוק (למעלה במסך) כדי שהחלל הגדול יתמלא בדמויות קטנות
            val farLimit = (ctx.mapper.worldY(ground.horizon) + 0.05f).coerceIn(0.3f, 0.8f)
            val feetY = farLimit + (0.95f - farLimit) * Math.pow(random.nextDouble(), 1.5).toFloat()
            val r = radiusForFeet(feetY)
            val figureHeight = r * FIGURE_HEIGHT_RADII
            if (figureHeight < minHeight || figureHeight > maxHeight) return@repeat

            // הדרך פנימה: קרקע כל הדרך, ובלי להיעלם מאחורי עצם קרוב יותר
            var clear = true
            for (k in 0..EDGE_PATH_STEPS) {
                val x = gateX + inward * k * EDGE_PATH_STEP
                val depth = ctx.depthAt(x, feetY)
                if (!ctx.isWalkable(x, feetY + GROUND_PROBE) || behindNearerObject(ctx, x, feetY, r, depth)) {
                    clear = false
                    break
                }
            }
            if (!clear) return@repeat
            if (tooClose(Vec2(gateX, feetY - r), r, existing, aspect, 0.2f)) return@repeat

            // מתחילים כולה מחוץ לעולם, כך שאי אפשר לראות אותה "צצה"
            val startX = if (fromLeft) -(FIGURE_HALF_WIDTH * r / aspect) - 0.01f
            else 1f + (FIGURE_HALF_WIDTH * r / aspect) + 0.01f
            val candidate = Enemy(
                id = nextEnemyId,
                position = Vec2(startX, feetY - r),
                radius = r,
                kind = EnemyKind.Open,
                state = EnemyState.Walking,
                timer = randomBetween(3f, 5f),
                direction = inward
            )
            if (!canRoam(candidate, existing)) return@repeat
            nextEnemyId++
            return candidate
        }
        return null
    }

    /** דמות שמוסתרת לגמרי מאחורי עצם גבוה, ויוצאת ממנו אל הקרקע ומתחילה ללכת */
    private fun spawnEmerge(existing: List<Enemy>): Enemy? {
        val ctx = scene ?: return null
        val usedIds = existing.mapNotNull { it.spot?.id }.toSet()
        val candidates = ctx.spots
            .filter { it.kind != SpotKind.WINDOW && it.id !in usedIds }
            .sortedByDescending { random.nextFloat() * sqrt(it.cellCount.toFloat()) }
        for (spot in candidates.take(12)) {
            val enemy = emergeFrom(spot, ctx) ?: continue
            if (!tooClose(enemy.position, enemy.radius, existing, ctx.mapper.screenAspect, 0.15f)) {
                return enemy
            }
        }
        return null
    }

    private fun emergeFrom(spot: HidingSpot, ctx: SceneContext): Enemy? {
        val mapper = ctx.mapper
        val aspect = mapper.screenAspect
        val left = mapper.worldX(spot.minX)
        val right = mapper.worldX(spot.maxX)
        val top = mapper.worldY(spot.minY)
        val bottom = mapper.worldY(spot.maxY)
        if (right < 0.05f || left > 0.95f || bottom < 0.05f || top > 0.95f) return null

        val footY = groundLevel(spot) ?: return null
        val goLeft = random.nextBoolean()
        val outward = if (goLeft) -1f else 1f
        val edgeX = if (goLeft) left else right
        val r = radiusForFeet(footY)
        if (!sizeOk(r)) return null
        // רק עצם גבוה מספיק כדי להסתיר דמות שלמה
        if (bottom - top < TALL_OBJECT_RADII * r) return null

        // אותן בדיקות קצה נקי כמו מציץ מהצד
        val base = spawnSidePeek(spot, ctx, r, footY, edgeX, outward, (right - left) * aspect) ?: return null

        // הדרך החוצה: קרקע, ובלי עצם קרוב יותר שיסתיר אותה
        for (k in 1..EMERGE_PATH_STEPS) {
            val x = edgeX + outward * (k * 0.9f * r) / aspect
            if (x < EDGE_BAND || x > 1f - EDGE_BAND) break
            val depth = ctx.depthAt(x, footY)
            if (!ctx.isWalkable(x, footY + GROUND_PROBE)) return null
            if (behindNearerObject(ctx, x, footY, r, depth, spot)) return null
        }

        return Enemy(
            id = base.id,
            position = base.hidePos,
            radius = r,
            kind = EnemyKind.Open,
            state = EnemyState.Idle,
            spot = spot,
            timer = randomBetween(0.3f, 3f),
            direction = outward
        )
    }

    /** גיבוי: אויב עומד במקום אקראי (כשאין ניתוח, או כשלא נמצא מקום מתאים) */
    private fun spawnStatic(existing: List<Enemy>): Enemy {
        val aspect = scene?.mapper?.screenAspect ?: 2f
        var position = randomPosition()
        repeat(15) {
            if (!tooClose(position, 0.08f, existing, aspect, 0.25f)) {
                return Enemy(id = nextEnemyId++, position = position)
            }
            position = randomPosition()
        }
        return Enemy(id = nextEnemyId++, position = position)
    }

    private fun randomPosition(): Vec2 =
        Vec2(random.nextFloat() * 0.6f + 0.2f, random.nextFloat() * 0.45f + 0.2f)

    /** האם המיקום קרוב מדי לאויב אחר: המרחק המינימלי גדל עם גודל הדמויות, כדי שלא יחפפו */
    private fun tooClose(
        position: Vec2,
        radius: Float,
        existing: List<Enemy>,
        aspect: Float,
        minDistance: Float,
        spacing: Float = 1.6f
    ): Boolean =
        existing.any { other ->
            val limit = max(minDistance, spacing * (radius + other.radius))
            val dx = (position.x - other.peekPos.x) * aspect
            val dy = position.y - other.peekPos.y
            val dx2 = (position.x - other.position.x) * aspect
            val dy2 = position.y - other.position.y
            sqrt(dx * dx + dy * dy) < limit || sqrt(dx2 * dx2 + dy2 * dy2) < limit
        }

    /** גובה אדם בעולם (חלק מגובה המסך) לפי העומק: scale * 1.7 / עומק, בקנה מידה של הסצנה */
    private fun figureHeightWorld(depthMeters: Float): Float {
        val ctx = scene ?: return 0.18f
        val depth = if (depthMeters.isNaN() || depthMeters < 0.8f) 0.8f else depthMeters
        return ctx.mapper.heightToWorld(scale * PERSON_HEIGHT_METERS / depth)
    }

    /**
     * רדיוס אויב לפי עומק: גודל אמיתי ביחס לסצנה (ה-scale מכויל לפי עצמים מוכרים), בלי הגבלות
     * שמעוותות את הסדר: קרוב תמיד גדול מרחוק. מקומות שבהם האדם היה יוצא ענק או זעיר נפסלים בנפרד.
     */
    private fun radiusForDepth(depthMeters: Float): Float =
        if (scene == null) 0.08f else figureHeightWorld(depthMeters) / FIGURE_HEIGHT_RADII

    /** רדיוס אדם שעומד על הקרקע ורגליו בגובה feetY בעולם: לפי הפרספקטיבה של הרצפה */
    private fun radiusForFeet(feetWorldY: Float): Float {
        val ctx = scene ?: return 0.08f
        val photoHeight = ground.personHeight(ctx.mapper.photoY(feetWorldY))
        return ctx.mapper.heightToWorld(photoHeight) / FIGURE_HEIGHT_RADII
    }

    /** שורת אבחון קצרה להצגה במשחק: מודל הקרקע ומספר הדמויות לפי סוג */
    fun debugInfo(enemies: List<Enemy>): String {
        val open = enemies.count { it.kind == EnemyKind.Open && it.isAlive }
        val cover = enemies.count { (it.kind == EnemyKind.Peek || it.kind == EnemyKind.Window) && it.isAlive }
        val spots = scene?.spots?.size ?: 0
        return "horizon %.2f  fit=%s  spots=%d  open=%d  cover=%d  fails emerge/edge/far/all=%d/%d/%d/%d  why=%s".format(
            ground.horizon, ground.fitted, spots, open, cover,
            failCounts[0], failCounts[1], failCounts[2], failCounts[3],
            coverFail.joinToString("/")
        )
    }

    private fun randomBetween(from: Float, to: Float): Float =
        from + random.nextFloat() * (to - from)

    companion object {
        private const val PEEK_SECONDS = 0.3f

        /** דמות הולכת שלא מתקדמת כל כך הרבה זמן (שניות) נחשבת תקועה */
        private const val STUCK_SECONDS = 0.7f

        /** סימולציית הליכה לפני יצירת דמות: כמה צעדים, אורך צעד (שניות), ואחרי כמה זמן בלי תזוזה פוסלים */
        private const val ROAM_STEPS = 40
        private const val ROAM_DT = 0.05f
        private const val ROAM_STUCK_SECONDS = 0.3f

        /** עצם מסתיר אדם אם הבסיס שלו נמוך מרגלי האדם ביותר מזה (יחידות עולם) */
        private const val OCCLUDER_MARGIN = 0.005f
        private const val RETREAT_SECONDS = 0.25f

        /** מהירות הליכה/ריצה ברדיוסים בשנייה */
        private const val WALK_SPEED = 1.4f
        private const val RUN_SPEED = 3.2f

        /** כמה רדיאנים של מחזור הליכה לכל רדיוס שהדמות עוברת */
        private const val PHASE_PER_RADIUS = 3.5f

        /** פס בקצה העולם שבו אין בדיקות הליכה (כניסה ויציאה), ונקודת הכניסה שממנה בודקים את הדרך */
        private const val EDGE_BAND = 0.05f
        private const val EDGE_GATE = 0.07f
        private const val EDGE_PATH_STEPS = 6
        private const val EDGE_PATH_STEP = 0.04f
        private const val EMERGE_PATH_STEPS = 5

        /** הליכה לעומק: מהירות (בתמונה, ליחידה של מרחק מהאופק בריבוע), וכמה מהמהירות לרוחב נשארת */
        private const val DEPTH_SPEED = 0.8f
        private const val DEPTH_WALK_LATERAL = 0.35f
        private const val MIN_ABOVE_HORIZON = 0.03f

        /** דמות שמגיעה מרחוק: טווח גובה (חלק מגובה המסך) ומספר צעדי הבדיקה קדימה */
        private const val FAR_MIN_HEIGHT = 0.02f
        private const val FAR_MAX_HEIGHT = 0.20f

        /** כמה (בתמונה) מעל הנקודה הרחוקה ביותר עוד נחשב קצה רחוק של הרצפה */
        private const val FAR_BAND = 0.08f
        private const val FAR_PATH_STEPS = 4

        /** גובה מקסימלי (חלק מגובה המסך) של דמות הולכת, כשהיא מתקרבת למצלמה */
        private const val MAX_WALK_HEIGHT = 0.40f

        /** עצם שהקצה התחתון שלו (בעולם) לפחות כאן נחשב חתוך על ידי המסך */
        private const val SCREEN_BOTTOM_CUT = 0.96f

        /** כמה (ביחידות תמונה) מתחת לעצם בודקים אם יש קרקע */
        private const val GROUND_PROBE = 0.012f


        /** גובה אדם אמיתי, וגובה הדמות המצוירת ביחידות רדיוס */
        private const val PERSON_HEIGHT_METERS = 1.7f
        private const val FIGURE_HEIGHT_RADII = 2.2f

        /** מקדם גודל בתמונה: גובה אדם (חלק מגובה התמונה) = scale * 1.7 / עומק במטרים */
        private const val DEFAULT_SCALE = 1.2f
        private const val MIN_SCALE = 0.4f
        private const val MAX_SCALE = 2.6f

        /** טווח גובה דמות גלויה (חלק מגובה המסך): מועדף, ואם אין מקום כזה: הרחב */
        private const val STRICT_MIN_HEIGHT = 0.07f
        private const val STRICT_MAX_HEIGHT = 0.34f
        private const val RELAXED_MIN_HEIGHT = 0.04f
        private const val RELAXED_MAX_HEIGHT = 0.45f

        /** טווח גובה דמות שקשורה למחבוא או לחלון */
        private const val SPOT_MIN_HEIGHT = 0.04f
        private const val SPOT_MAX_HEIGHT = 0.45f

        /** עצם שגובהו לפחות כך (ביחידות רדיוס) נחשב גבוה: מציצים מהצד שלו, אחרת עולים מאחוריו */
        private const val TALL_OBJECT_RADII = 2.1f

        /** גבהים אמיתיים (מטרים) של עצמים נפוצים, לכיול הסצנה */
        private val REAL_HEIGHTS = mapOf(
            "car" to 1.5f, "van" to 1.9f, "bus" to 3.0f,
            "ashcan" to 0.7f, "barrel" to 0.9f, "box" to 0.5f,
            "door" to 2.05f, "desk" to 0.75f, "table" to 0.75f,
            "bench" to 0.9f, "wardrobe" to 1.9f, "bookcase" to 1.9f,
            "cabinet" to 1.0f, "bicycle" to 1.0f, "minibike" to 1.1f
        )

        /** חצי רוחב הדמות ביחידות רדיוס (כולל הידיים) */
        private const val FIGURE_HALF_WIDTH = 0.6f

        /** אדן חלון נמוך מזה (גבוה על המסך) לא מתאים לאדם עומד */
        private const val WINDOW_MIN_SILL = 0.45f

        /** צפיפות הדגימה בבדיקות הכיסוי (COVERAGE_STEPS בריבוע נקודות) */
        private const val COVERAGE_STEPS = 5
    }
}
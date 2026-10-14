package com.arsniper.game.engine

import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import com.arsniper.game.model.EnemyState
import com.arsniper.game.model.Vec2
import kotlin.math.max
import kotlin.math.sqrt

/** סוג האביזר ("נקודת תורפה") שנושאת מפלצת. פגיעה בו מפעילה אפקט מיוחד ופרס גדול. */
enum class AccessoryKind(val label: String) {
    /** בלון על חוט: מתפוצץ, המפלצת נופלת */
    Balloon("POP!"),

    /** פצצה: מתפוצצת וגם מפילה מפלצות קרובות (קומבו) */
    Bomb("BOOM!"),

    /** יהלום: הניקוד הגבוה ביותר */
    Gem("JACKPOT!")
}

/** פגיעה באביזר של אויב מסוים. anchor = מיקום האביזר (קואורדינטות עולם) */
data class AccessoryHit(val enemy: Enemy, val kind: AccessoryKind, val anchor: Vec2)

/** תוצאת פגיעה באביזר: מי נפל, כמה נקודות, ומה להציג */
data class AccessoryOutcome(
    val killedIds: Set<Int>,
    val points: Int,
    val label: String,
    val kind: AccessoryKind,
    val anchor: Vec2,
    /** רדיוס האפקט החזותי, ביחידות של גובה המסך */
    val blastRadius: Float
)

/**
 * אביזרי נקודות תורפה. לכל מפלצת יש אביזר אחד, לפי מספר האויב (בלון, פצצה, יהלום חוזר חלילה).
 * האביזר קטן, ולכן כדאי להתקרב אליו עם הזום כדי לפגוע בו.
 * רק מפלצת שעומדת או הולכת חופשי (לא מוסתרת מאחורי עצם) נושאת אביזר שאפשר לפגוע בו.
 */
object Accessories {

    /** רדיוס הפגיעה באביזר ביחידות רדיוס של המפלצת */
    private const val HIT_RADIUS_RADII = 0.6f

    /** רדיוס פגיעה מינימלי (יחידות גובה מסך), כדי שגם מפלצת רחוקה תהיה בת-פגיעה בתקריב */
    private const val MIN_HIT_RADIUS = 0.018f

    /** רדיוס הפיצוץ של פצצה ביחידות רדיוס של המפלצת שנושאת אותה */
    private const val BOMB_BLAST_RADII = 3.4f

    private const val BALLOON_POINTS = 300
    private const val GEM_POINTS = 500
    private const val BOMB_POINTS_PER_KILL = 200
    private const val BOMB_COMBO_BONUS = 100

    fun kindOf(enemy: Enemy): AccessoryKind =
        when (((enemy.id % 3) + 3) % 3) {
            0 -> AccessoryKind.Balloon
            1 -> AccessoryKind.Bomb
            else -> AccessoryKind.Gem
        }

    /** מיקום האביזר ביחס למרכז המפלצת, ביחידות רדיוס (x ימינה, y למטה) */
    fun offsetX(kind: AccessoryKind): Float =
        when (kind) {
            AccessoryKind.Balloon -> 0.6f
            AccessoryKind.Bomb -> 1.25f
            AccessoryKind.Gem -> 0f
        }

    fun offsetY(kind: AccessoryKind): Float =
        when (kind) {
            AccessoryKind.Balloon -> -2.7f
            AccessoryKind.Bomb -> 0.35f
            AccessoryKind.Gem -> -2.3f
        }

    /** רדיוס הפגיעה (והציור) של האביזר, ביחידות גובה מסך */
    fun hitRadius(enemy: Enemy): Float = max(enemy.radius * HIT_RADIUS_RADII, MIN_HIT_RADIUS)

    /** האם לאויב יש כרגע אביזר גלוי שאפשר לפגוע בו */
    fun isActive(enemy: Enemy): Boolean =
        enemy.isAlive &&
            ((enemy.kind == EnemyKind.Open && enemy.spot == null) || enemy.kind == EnemyKind.Static)

    /** מיקום האביזר בעולם (0..1). aspect = רוחב המסך חלקי גובהו */
    fun anchor(enemy: Enemy, aspect: Float): Vec2 {
        val kind = kindOf(enemy)
        return Vec2(
            enemy.position.x + offsetX(kind) * enemy.radius / aspect,
            enemy.position.y + offsetY(kind) * enemy.radius
        )
    }

    /** האביזר הקרוב ביותר לכוונת שנפגע, או null אם הכוונת לא על אף אביזר */
    fun hit(crosshair: Vec2, enemies: List<Enemy>, aspect: Float): AccessoryHit? {
        var best: AccessoryHit? = null
        var bestDistance = Float.MAX_VALUE
        for (enemy in enemies) {
            if (!isActive(enemy)) continue
            val anchor = anchor(enemy, aspect)
            val dx = (crosshair.x - anchor.x) * aspect
            val dy = crosshair.y - anchor.y
            val distance = sqrt(dx * dx + dy * dy)
            if (distance <= hitRadius(enemy) && distance < bestDistance) {
                best = AccessoryHit(enemy, kindOf(enemy), anchor)
                bestDistance = distance
            }
        }
        return best
    }

    /** מחשב מה קורה כשפוגעים באביזר: מי נופל וכמה נקודות מקבלים */
    fun explode(hit: AccessoryHit, enemies: List<Enemy>, aspect: Float): AccessoryOutcome {
        val carrier = hit.enemy
        return when (hit.kind) {
            AccessoryKind.Balloon -> AccessoryOutcome(
                killedIds = setOf(carrier.id),
                points = BALLOON_POINTS,
                label = hit.kind.label,
                kind = hit.kind,
                anchor = hit.anchor,
                blastRadius = carrier.radius * 1.8f
            )
            AccessoryKind.Gem -> AccessoryOutcome(
                killedIds = setOf(carrier.id),
                points = GEM_POINTS,
                label = hit.kind.label,
                kind = hit.kind,
                anchor = hit.anchor,
                blastRadius = carrier.radius * 2f
            )
            AccessoryKind.Bomb -> {
                val blast = BOMB_BLAST_RADII * carrier.radius
                val killed = HashSet<Int>()
                killed.add(carrier.id)
                for (other in enemies) {
                    if (!other.isAlive || other.id == carrier.id) continue
                    // מפלצת שמוסתרת מאחורי עצם לא נפגעת מהפיצוץ
                    if ((other.kind == EnemyKind.Peek || other.kind == EnemyKind.Window) && other.exposure < 0.3f) continue
                    if (other.kind == EnemyKind.Open && other.spot != null && other.state == EnemyState.Idle) continue
                    val dx = (other.position.x - hit.anchor.x) * aspect
                    val dy = other.position.y - hit.anchor.y
                    if (sqrt(dx * dx + dy * dy) <= blast) killed.add(other.id)
                }
                val count = killed.size
                AccessoryOutcome(
                    killedIds = killed,
                    points = BOMB_POINTS_PER_KILL * count + BOMB_COMBO_BONUS * (count - 1),
                    label = if (count > 1) "BOOM x$count!" else hit.kind.label,
                    kind = hit.kind,
                    anchor = hit.anchor,
                    blastRadius = blast
                )
            }
        }
    }
}
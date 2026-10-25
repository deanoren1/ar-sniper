package com.arsniper.game.engine

import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import com.arsniper.game.model.EnemyState
import com.arsniper.game.model.Vec2
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

/**
 * אביזר שצויר עכשיו על המסך. המיקום והגודל נקבעים בזמן הציור (לפי הגודל האמיתי של המפלצת המצוירת),
 * כך שהפגיעה תמיד מתאימה בדיוק למה שרואים.
 * anchor = מרכז האביזר (קואורדינטות עולם), hitRadius ו-bodyHeight ביחידות גובה מסך.
 */
data class AccessoryTarget(
    val enemy: Enemy,
    val kind: AccessoryKind,
    val anchor: Vec2,
    val hitRadius: Float,
    val bodyHeight: Float
)

/** תוצאת פגיעה באביזר: מי נפל, כמה נקודות, ומה להציג */
data class AccessoryOutcome(
    val killedIds: Set<Int>,
    /** מפלצות שנפגעו מהפיצוץ אבל שרדו (יש להן עוד חיים) */
    val damagedIds: Set<Int>,
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

    /** רדיוס הפיצוץ של פצצה ביחידות גובה המפלצת שנושאת אותה */
    private const val BOMB_BLAST_BODIES = 1.5f

    /** כמה נזק גורם פיצוץ פצצה למפלצות קרובות (נקודות חיים) */
    const val BOMB_SPLASH_DAMAGE = 2

    /** ניקוד על כל מפלצת קרובה שנפגעה מהפיצוץ ושרדה */
    private const val BOMB_SPLASH_POINTS = 50

    private const val BALLOON_POINTS = 300
    private const val GEM_POINTS = 500
    private const val BOMB_POINTS_PER_KILL = 200
    private const val BOMB_COMBO_BONUS = 100

    /** פצצה או יהלום לסירוגין. בלון הוא רק של מפלצת הבלונים (הבלונים שבציור שלה), ולא של שאר המפלצות */
    fun kindOf(enemy: Enemy): AccessoryKind =
        if (((enemy.id % 2) + 2) % 2 == 0) AccessoryKind.Bomb else AccessoryKind.Gem

    /** האם לאויב יש כרגע אביזר גלוי שאפשר לפגוע בו */
    fun isActive(enemy: Enemy): Boolean =
        enemy.isAlive &&
            ((enemy.kind == EnemyKind.Open && enemy.spot == null) || enemy.kind == EnemyKind.Static)

    /**
     * האביזר הקרוב ביותר לכוונת שנפגע, או null אם הכוונת לא על אף אביזר.
     * targets = האביזרים שצוירו בפריים האחרון. aspect = רוחב המסך חלקי גובהו.
     */
    fun hit(
        crosshair: Vec2,
        targets: List<AccessoryTarget>,
        enemies: List<Enemy>,
        aspect: Float
    ): AccessoryTarget? {
        var best: AccessoryTarget? = null
        var bestDistance = Float.MAX_VALUE
        for (target in targets) {
            // האויב כבר נפל מאז הציור האחרון
            if (enemies.none { it.id == target.enemy.id && it.isAlive }) continue
            val dx = (crosshair.x - target.anchor.x) * aspect
            val dy = crosshair.y - target.anchor.y
            val distance = sqrt(dx * dx + dy * dy)
            if (distance <= target.hitRadius && distance < bestDistance) {
                best = target
                bestDistance = distance
            }
        }
        return best
    }

    /**
     * מחשב מה קורה כשפוגעים באביזר: מי נופל, מי נפגע וכמה נקודות מקבלים.
     * פגיעה באביזר הורגת את המפלצת שנושאת אותו מיד, בלי קשר לכמה חיים נשארו לה.
     */
    fun explode(hit: AccessoryTarget, enemies: List<Enemy>, aspect: Float): AccessoryOutcome {
        val carrier = hit.enemy
        return when (hit.kind) {
            // בלון אחד מתפוצץ בכל פגיעה: המפלצת נופלת רק כשהבלון האחרון מתפוצץ
            AccessoryKind.Balloon -> AccessoryOutcome(
                killedIds = if (carrier.hp <= 1) setOf(carrier.id) else emptySet(),
                damagedIds = if (carrier.hp <= 1) emptySet() else setOf(carrier.id),
                points = if (carrier.hp <= 1) BALLOON_POINTS else BALLOON_POINTS / 3,
                label = hit.kind.label,
                kind = hit.kind,
                anchor = hit.anchor,
                blastRadius = hit.bodyHeight * 0.55f
            )
            AccessoryKind.Gem -> AccessoryOutcome(
                killedIds = setOf(carrier.id),
                damagedIds = emptySet(),
                points = GEM_POINTS,
                label = hit.kind.label,
                kind = hit.kind,
                anchor = hit.anchor,
                blastRadius = hit.bodyHeight * 0.65f
            )
            AccessoryKind.Bomb -> {
                val blast = BOMB_BLAST_BODIES * hit.bodyHeight
                val killed = HashSet<Int>()
                val damaged = HashSet<Int>()
                killed.add(carrier.id)
                for (other in enemies) {
                    if (!other.isAlive || other.id == carrier.id) continue
                    // מפלצת שמוסתרת מאחורי עצם לא נפגעת מהפיצוץ
                    if ((other.kind == EnemyKind.Peek || other.kind == EnemyKind.Window) && other.exposure < 0.3f) continue
                    if (other.kind == EnemyKind.Open && other.spot != null && other.state == EnemyState.Idle) continue
                    val dx = (other.position.x - hit.anchor.x) * aspect
                    val dy = other.position.y - hit.anchor.y
                    if (sqrt(dx * dx + dy * dy) <= blast) {
                        // מפלצת עם מעט חיים נופלת, חזקה יותר רק נפגעת
                        val hp = if (other.hp > 0) other.hp else 1
                        if (hp <= BOMB_SPLASH_DAMAGE) killed.add(other.id) else damaged.add(other.id)
                    }
                }
                val count = killed.size
                AccessoryOutcome(
                    killedIds = killed,
                    damagedIds = damaged,
                    points = BOMB_POINTS_PER_KILL * count + BOMB_COMBO_BONUS * (count - 1) +
                        BOMB_SPLASH_POINTS * damaged.size,
                    label = if (count > 1) "BOOM x$count!" else hit.kind.label,
                    kind = hit.kind,
                    anchor = hit.anchor,
                    blastRadius = blast
                )
            }
        }
    }
}
package com.arsniper.game.engine

import com.arsniper.game.model.Enemy
import com.arsniper.game.model.EnemyKind
import com.arsniper.game.model.EnemyState
import com.arsniper.game.model.Vec2
import kotlin.math.max

/** סוג הקליע שמפלצת יורה על השחקן */
enum class ShotKind { Orb, Bomb, Gas, Rock, Spike, Fire }

/**
 * סוג מפלצת בארנה. row = שורת הספרייט בגיליון המפלצות (ורוד 0, סגול 1, ירוק 2, כתום 3, כחול 4).
 * כשיגיעו ספרייטים ייעודיים לכל סוג, רק row ישתנה.
 * hp = כמה פגיעות היא סופגת, threat = כמה "איום" היא מוסיפה למסך (מגביל כמה כבדות בו זמנית),
 * flightSeconds = כמה זמן הקליע שלה בדרך לשחקן, shotHp = כמה פגיעות הקליע סופג לפני שמתפוצץ.
 */
enum class Foe(
    val row: Int,
    val hp: Int,
    val threat: Int,
    val shot: ShotKind,
    val flightSeconds: Float,
    val shotHp: Int = 1
) {
    Balloon(row = 3, hp = 3, threat = 1, shot = ShotKind.Bomb, flightSeconds = 1.8f),
    Octopus(row = 1, hp = 3, threat = 2, shot = ShotKind.Orb, flightSeconds = 1.6f),
    Alien(row = 4, hp = 3, threat = 2, shot = ShotKind.Gas, flightSeconds = 1.9f),
    Giant(row = 0, hp = 4, threat = 3, shot = ShotKind.Rock, flightSeconds = 2.1f, shotHp = 2),
    Hedgehog(row = 2, hp = 2, threat = 3, shot = ShotKind.Spike, flightSeconds = 1.5f),

    /** הבוס: דרקון. אין לו שורה בגיליון הרגיל (row = 5), הוא מצויר מגיליון נפרד לפי BOSS_ID */
    Dragon(row = 5, hp = 30, threat = 5, shot = ShotKind.Fire, flightSeconds = 1.7f, shotHp = 2)
}

/**
 * גל אחד: הרשימה לפי סדר הכניסה (הסוג החדש בסוף), תקרת איום בו זמנית, כמה התקפות בו זמנית,
 * הזמן בין התקפות של אותה מפלצת, ומכפיל מהירות הקליעים (קטן = קליעים מהירים יותר).
 */
class WaveSpec(
    val foes: List<Foe>,
    val maxThreat: Int,
    val maxAttacks: Int,
    val cooldownMin: Float,
    val cooldownMax: Float,
    val flightScale: Float
)

/** מפלצת שנעלמה ממקום אחד (תקועה, או יצאה מהמסך) והופיעה במקום אחר: להצגת אפקט בשני המקומות */
class TeleportEvent(val from: Vec2, val to: Vec2)

enum class ArenaPhase {
    /** כרזה "WAVE n" לפני שהגל מתחיל */
    Intro,

    /** הגל רץ */
    Fighting,

    /** הגל נגמר, הפסקה קצרה לפני הבא */
    Clear,

    /** כל הגלים נגמרו */
    Won,

    /** נגמרו הלבבות */
    Lost
}

/**
 * מנהל הארנה: גלים, לבבות ויצירת המפלצות של כל גל. Kotlin טהור.
 *
 * המנוע (GameEngine) בוחר איפה ואיך מפלצת נכנסת. המנהל רק קובע כמה, ומאיזה סוג: מפלצת חדשה
 * שהמנוע יצר מקבלת סוג מהתור, ומספר האויב שלה משתנה כך ש-id % 5 הוא שורת הספרייט שלה
 * (כך הספרייט והחיים נקבעים לפי הסוג בלי לשנות את שכבת הציור).
 */
class ArenaDirector(private val waves: List<WaveSpec> = DEFAULT_WAVES) {

    var phase: ArenaPhase = ArenaPhase.Intro
        private set

    var waveIndex: Int = 0
        private set

    var hearts: Int = MAX_HEARTS
        private set

    /** כמה מפלצות מהגל הנוכחי כבר נהרגו, וכמה יש בו בסך הכול */
    var killedInWave: Int = 0
        private set
    var totalInWave: Int = 0
        private set

    /** כמה זמן נשאר לכרזה או להפסקה הנוכחית */
    var phaseTimer: Float = 0f
        private set

    /** האם בהפסקה האחרונה חזר לב (להצגה בכרזה) */
    var healedOnClear: Boolean = false
        private set

    val waveCount: Int get() = waves.size
    val currentWave: WaveSpec get() = waves[waveIndex.coerceIn(0, waves.size - 1)]
    /** בגל הבוס: כמה "נשאר" להצגה: הבוס עצמו (המפלצות הרגילות ממשיכות להגיע עד שהוא נופל) */
    val leftInWave: Int get() = if (isBossWave) (if (bossAlive) 1 else 0) else totalInWave - killedInWave

    /** מצב הבוס, מתעדכן בכל פריים: חיים, חיים מקסימליים, האם חי */
    var bossHp: Int = 0
        private set
    var bossMax: Int = 0
        private set
    var bossAlive: Boolean = false
        private set

    /** בשליש האחרון של החיים הבוס "כועס": יורה מהר יותר ותדיר יותר */
    val bossEnraged: Boolean get() = bossAlive && bossMax > 0 && bossHp * 3 <= bossMax

    /** שבר החיים שנשארו לבוס (0..1) */
    val bossFraction: Float get() = if (bossMax > 0) bossHp.toFloat() / bossMax else 0f

    private var minionCycle = 0

    /** הבוס נפל: אנימציית מוות רצה, ואז הארנה נוצחת */
    var bossDying: Boolean = false
        private set
    private var winTimer = 0f

    /** האם הגל הנוכחי הוא גל הבוס */
    val isBossWave: Boolean get() = currentWave.foes.contains(Foe.Dragon)

    private val queue = ArrayDeque<Foe>()
    private val foeById = HashMap<Int, Foe>()
    private val counted = HashSet<Int>()

    /** המצב האחרון של כל מפלצת חיה, כדי שאם היא נעלמת מהעולם אפשר להעביר אותה (אותם חיים) למקום אחר */
    private val lastSeen = HashMap<Int, Enemy>()
    private val teleportQueue = ArrayDeque<Enemy>()
    private val teleportEvents = ArrayList<TeleportEvent>()
    private var nextSeq = 0
    private var spawnCooldown = 0f
    private var invulnerableLeft = 0f
    private var pendingBonus = 0

    init {
        restart()
    }

    /** מתחיל ארנה מחדש: לבבות מלאים וגל 1 */
    fun restart() {
        hearts = MAX_HEARTS
        foeById.clear()
        counted.clear()
        lastSeen.clear()
        teleportQueue.clear()
        teleportEvents.clear()
        pendingBonus = 0
        invulnerableLeft = 0f
        healedOnClear = false
        loadWave(0)
    }

    private fun loadWave(index: Int) {
        waveIndex = index
        queue.clear()
        queue.addAll(waves[index].foes)
        killedInWave = 0
        totalInWave = waves[index].foes.size
        bossHp = 0
        bossMax = 0
        bossAlive = false
        minionCycle = 0
        bossDying = false
        phase = ArenaPhase.Intro
        phaseTimer = INTRO_SECONDS
        spawnCooldown = 0f
    }

    /** סוג המפלצת של אויב (null אם זה לא אויב של הארנה) */
    fun foeOf(enemyId: Int): Foe? = foeById[enemyId]

    /** העברות מקום שקרו מאז הקריאה האחרונה (לאפקט "פוף") */
    fun takeTeleports(): List<TeleportEvent> {
        if (teleportEvents.isEmpty()) return emptyList()
        val out = ArrayList(teleportEvents)
        teleportEvents.clear()
        return out
    }

    /** ניקוד בונוס שנצבר מאז הקריאה האחרונה (סיום גל) */
    fun takeBonus(): Int {
        val value = pendingBonus
        pendingBonus = 0
        return value
    }

    /** פרס לב: מחזיר לב אחד. true אם הוסיף (כלומר חסר לב) */
    fun heal(): Boolean {
        if (phase != ArenaPhase.Fighting || hearts >= MAX_HEARTS) return false
        hearts += 1
        return true
    }

    /** כוכבים בסוף ארנה מנצחת: לפי הלבבות שנשארו */
    val stars: Int get() = hearts.coerceIn(1, 3)

    /**
     * הקליע פגע בשחקן. מחזיר true אם ירד לב (אחרי פגיעה יש זמן חסינות קצר, ובמהלכו לא יורד לב).
     */
    fun hitPlayer(): Boolean {
        if (phase != ArenaPhase.Fighting) return false
        if (invulnerableLeft > 0f) return false
        hearts -= 1
        invulnerableLeft = INVULNERABLE_SECONDS
        if (hearts <= 0) {
            hearts = 0
            phase = ArenaPhase.Lost
        }
        return true
    }

    /**
     * מקדם את הארנה בפריים אחד ומחזיר את רשימת האויבים המעודכנת (עם מפלצות חדשות אם נוצרו).
     * engine = המנוע, שיוצר את המפלצות במקום המתאים בסצנה.
     */
    fun step(dt: Float, enemies: List<Enemy>, engine: GameEngine, aspect: Float): List<Enemy> {
        if (phase == ArenaPhase.Won || phase == ArenaPhase.Lost) return enemies
        invulnerableLeft = max(0f, invulnerableLeft - dt)

        when (phase) {
            ArenaPhase.Intro -> {
                phaseTimer -= dt
                if (phaseTimer <= 0f) {
                    phase = ArenaPhase.Fighting
                    spawnCooldown = 0.3f
                }
                return enemies
            }

            ArenaPhase.Clear -> {
                phaseTimer -= dt
                if (phaseTimer <= 0f) loadWave(waveIndex + 1)
                return enemies
            }

            else -> Unit
        }

        if (bossDying) {
            winTimer -= dt
            if (winTimer <= 0f) phase = ArenaPhase.Won
            return enemies
        }

        // הריגות: אויב של הארנה שמסומן מת. אויב שנעלם מהרשימה בלי למות (יצא מהעולם) חוזר לתור
        for (e in enemies) {
            if (!e.isAlive && foeById.containsKey(e.id) && counted.add(e.id)) killedInWave++
        }
        // הבוס: מעדכנים חיים. כשהוא נופל הארנה נוצחת מיד (גם אם נשארו מפלצות רגילות)
        val boss = enemies.firstOrNull { it.id == BOSS_ID }
        if (boss != null) {
            bossMax = boss.maxHp
            bossHp = if (boss.isAlive) boss.hp.coerceAtLeast(1) else 0
            bossAlive = boss.isAlive
            if (!boss.isAlive && isBossWave) {
                // הבוס נפל: כל המפלצות הרגילות מתפוצצות איתו, ואחרי אנימציית המוות הארנה נוצחת
                bossDying = true
                winTimer = BOSS_DEATH_SECONDS
                pendingBonus += BOSS_BONUS
                return enemies.map { if (it.id != BOSS_ID && it.isAlive) it.copy(isAlive = false) else it }
            }
        }
        // בגל הבוס המפלצות הרגילות ממשיכות להגיע עד שהבוס נופל
        if (isBossWave && bossAlive && queue.isEmpty() && teleportQueue.isEmpty()) {
            queue.addLast(MINIONS[minionCycle % MINIONS.size])
            minionCycle++
        }
        val present = HashSet<Int>()
        for (e in enemies) present.add(e.id)
        val iterator = foeById.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in present) {
                if (entry.key !in counted) {
                    // נעלמה בלי למות (תקועה או יצאה מהמסך): תופיע במקום אחר, אותה מפלצת עם אותם חיים
                    val previous = lastSeen[entry.key]
                    if (previous != null && previous.isAlive) teleportQueue.addLast(previous) else queue.addFirst(entry.value)
                }
                counted.remove(entry.key)
                lastSeen.remove(entry.key)
                iterator.remove()
            }
        }

        var alive = 0
        var aliveArena = 0
        var threat = 0
        for (e in enemies) {
            if (!e.isAlive) continue
            alive++
            val foe = foeById[e.id] ?: continue
            aliveArena++
            threat += foe.threat
        }

        // הגל נגמר: התור ריק ואף מפלצת לא חיה
        if (queue.isEmpty() && teleportQueue.isEmpty() && aliveArena == 0) {
            if (waveIndex >= waves.size - 1) {
                phase = ArenaPhase.Won
            } else {
                phase = ArenaPhase.Clear
                phaseTimer = CLEAR_SECONDS
                pendingBonus += CLEAR_BONUS * (waveIndex + 1)
                healedOnClear = hearts < MAX_HEARTS
                if (healedOnClear) hearts += 1
            }
            return enemies
        }

        // יצירת מפלצת הבאה, בכפוף לתקרת האיום
        spawnCooldown -= dt
        if ((queue.isNotEmpty() || teleportQueue.isNotEmpty()) && spawnCooldown <= 0f) {
            val moving = teleportQueue.firstOrNull()
            val next = if (moving != null) foeOfRow(moving) else queue.first()
            if (aliveArena == 0 || threat + next.threat <= currentWave.maxThreat) {
                val grown = engine.replenish(enemies, alive + 1, aspect)
                val adopted = adopt(grown)
                rememberAlive(adopted)
                return adopted
            }
        }
        rememberAlive(enemies)
        return enemies
    }

    /** סוג המפלצת לפי המספר שלה (id % 5 = שורת הספרייט) */
    private fun foeOfRow(enemy: Enemy): Foe = foeById[enemy.id] ?: Foe.values().first { it.row == ((enemy.id % 5) + 5) % 5 }

    private fun rememberAlive(list: List<Enemy>) {
        for (e in list) {
            if (e.isAlive && foeById.containsKey(e.id)) lastSeen[e.id] = e
        }
    }

    /** מפלצת חדשה שהמנוע יצר מקבלת סוג מהתור: מספר חדש (id % 5 = שורת הספרייט) וחיים לפי הסוג */
    private fun adopt(list: List<Enemy>): List<Enemy> {
        var assigned = false
        val out = ArrayList<Enemy>(list.size)
        for (e in list) {
            if (e.isAlive && e.id < ID_BASE) {
                val moving = teleportQueue.removeFirstOrNull()
                if (moving != null) {
                    // אותה מפלצת (אותו מספר וחיים) במקום החדש
                    val foe = foeOfRow(moving)
                    foeById[moving.id] = foe
                    out.add(e.copy(id = moving.id, maxHp = moving.maxHp, hp = moving.hp))
                    teleportEvents.add(TeleportEvent(from = moving.position, to = e.position))
                    assigned = true
                    continue
                }
                val foe = queue.removeFirstOrNull()
                if (foe == null) continue // המנוע יצר יותר מהנדרש: מוותרים על המיותרת
                if (foe == Foe.Dragon) {
                    // הבוס: גדול, עומד במרכז, מספר קבוע (BOSS_ID) כדי שיצויר מהגיליון שלו
                    foeById[BOSS_ID] = foe
                    val center = Vec2(0.5f, 0.56f)
                    out.add(
                        e.copy(
                            id = BOSS_ID, position = center, radius = BOSS_RADIUS, kind = EnemyKind.Static,
                            state = EnemyState.Exposed, spot = null, hidePos = center, peekPos = center,
                            exposure = 1f, maxHp = foe.hp, hp = foe.hp
                        )
                    )
                    assigned = true
                    continue
                }
                val id = ID_BASE + nextSeq * 5 + foe.row
                nextSeq++
                foeById[id] = foe
                out.add(e.copy(id = id, maxHp = foe.hp, hp = foe.hp))
                assigned = true
            } else {
                out.add(e)
            }
        }
        spawnCooldown = if (assigned) SPAWN_GAP_SECONDS else RETRY_SECONDS
        return out
    }

    companion object {
        const val MAX_HEARTS = 3
        const val INTRO_SECONDS = 2.2f
        const val CLEAR_SECONDS = 3.0f

        /** מרווח מינימלי בין יצירת מפלצות, כדי שלא ייכנסו כולן בבת אחת */
        const val SPAWN_GAP_SECONDS = 0.8f
        private const val RETRY_SECONDS = 0.3f
        const val INVULNERABLE_SECONDS = 1.5f

        /** בונוס סיום גל, כפול מספר הגל */
        private const val CLEAR_BONUS = 200
        private const val BOSS_BONUS = 2000
        private const val BOSS_DEATH_SECONDS = 2.4f

        /** המפלצות הרגילות שמצטרפות לבוס, בסבב */
        private val MINIONS = listOf(Foe.Balloon, Foe.Octopus, Foe.Balloon, Foe.Alien)

        /** מספרי אויב מתחת לערך הזה יוצרים המנוע, מעליו המנהל */
        private const val ID_BASE = 100_000

        /** מספר קבוע לבוס (id % 5 אינו 3, כדי שלא ייחשב מפלצת בלונים) */
        const val BOSS_ID = 300_002

        /** רדיוס הבוס ביחידות גובה מסך */
        const val BOSS_RADIUS = 0.19f
        val DEFAULT_WAVES: List<WaveSpec> = listOf(
            // גל 1: בלונים, ובסוף התמנון הראשון
            WaveSpec(
                foes = listOf(Foe.Balloon, Foe.Balloon, Foe.Balloon, Foe.Octopus),
                maxThreat = 3, maxAttacks = 1, cooldownMin = 3.85f, cooldownMax = 6.05f, flightScale = 1.27f
            ),
            // גל 2: מופיע החייזר
            WaveSpec(
                foes = listOf(Foe.Balloon, Foe.Octopus, Foe.Balloon, Foe.Octopus, Foe.Alien),
                maxThreat = 4, maxAttacks = 2, cooldownMin = 3.3f, cooldownMax = 5.5f, flightScale = 1.16f
            ),
            // גל 3: מופיע הענק
            WaveSpec(
                foes = listOf(Foe.Octopus, Foe.Alien, Foe.Octopus, Foe.Alien, Foe.Giant),
                maxThreat = 5, maxAttacks = 2, cooldownMin = 2.86f, cooldownMax = 4.84f, flightScale = 1.1f
            ),
            // גל 4: מופיע הקיפוד
            WaveSpec(
                foes = listOf(Foe.Balloon, Foe.Alien, Foe.Balloon, Foe.Alien, Foe.Giant, Foe.Hedgehog),
                maxThreat = 6, maxAttacks = 2, cooldownMin = 2.42f, cooldownMax = 4.4f, flightScale = 1.05f
            ),
            // גל 5: כולם יחד
            WaveSpec(
                foes = listOf(
                    Foe.Balloon, Foe.Octopus, Foe.Alien, Foe.Balloon, Foe.Hedgehog,
                    Foe.Octopus, Foe.Giant, Foe.Alien, Foe.Hedgehog
                ),
                maxThreat = 7, maxAttacks = 3, cooldownMin = 1.98f, cooldownMax = 3.74f, flightScale = 1.0f
            ),
            // גל 6: הבוס, הדרקון
            WaveSpec(
                foes = listOf(Foe.Dragon, Foe.Balloon, Foe.Octopus),
                maxThreat = 9, maxAttacks = 3, cooldownMin = 2.4f, cooldownMax = 3.6f, flightScale = 1.0f
            )
        )
    }
}

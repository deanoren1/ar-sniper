package com.arsniper.game.engine

import com.arsniper.game.model.Enemy
import kotlin.math.max

/** סוג הקליע שמפלצת יורה על השחקן */
enum class ShotKind { Orb, Bomb, Gas, Rock, Spike }

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
    Balloon(row = 3, hp = 2, threat = 1, shot = ShotKind.Bomb, flightSeconds = 1.8f),
    Octopus(row = 1, hp = 3, threat = 2, shot = ShotKind.Orb, flightSeconds = 1.6f),
    Alien(row = 4, hp = 3, threat = 2, shot = ShotKind.Gas, flightSeconds = 1.9f),
    Giant(row = 0, hp = 4, threat = 3, shot = ShotKind.Rock, flightSeconds = 2.1f, shotHp = 2),
    Hedgehog(row = 2, hp = 2, threat = 3, shot = ShotKind.Spike, flightSeconds = 1.5f)
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
    val leftInWave: Int get() = totalInWave - killedInWave

    private val queue = ArrayDeque<Foe>()
    private val foeById = HashMap<Int, Foe>()
    private val counted = HashSet<Int>()
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
        phase = ArenaPhase.Intro
        phaseTimer = INTRO_SECONDS
        spawnCooldown = 0f
    }

    /** סוג המפלצת של אויב (null אם זה לא אויב של הארנה) */
    fun foeOf(enemyId: Int): Foe? = foeById[enemyId]

    /** ניקוד בונוס שנצבר מאז הקריאה האחרונה (סיום גל) */
    fun takeBonus(): Int {
        val value = pendingBonus
        pendingBonus = 0
        return value
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

        // הריגות: אויב של הארנה שמסומן מת. אויב שנעלם מהרשימה בלי למות (יצא מהעולם) חוזר לתור
        for (e in enemies) {
            if (!e.isAlive && foeById.containsKey(e.id) && counted.add(e.id)) killedInWave++
        }
        val present = HashSet<Int>()
        for (e in enemies) present.add(e.id)
        val iterator = foeById.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in present) {
                if (entry.key !in counted) queue.addFirst(entry.value)
                counted.remove(entry.key)
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
        if (queue.isEmpty() && aliveArena == 0) {
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
        if (queue.isNotEmpty() && spawnCooldown <= 0f) {
            val next = queue.first()
            if (aliveArena == 0 || threat + next.threat <= currentWave.maxThreat) {
                val grown = engine.replenish(enemies, alive + 1, aspect)
                return adopt(grown)
            }
        }
        return enemies
    }

    /** מפלצת חדשה שהמנוע יצר מקבלת סוג מהתור: מספר חדש (id % 5 = שורת הספרייט) וחיים לפי הסוג */
    private fun adopt(list: List<Enemy>): List<Enemy> {
        var assigned = false
        val out = ArrayList<Enemy>(list.size)
        for (e in list) {
            if (e.isAlive && e.id < ID_BASE) {
                val foe = queue.removeFirstOrNull()
                if (foe == null) continue // המנוע יצר יותר מהנדרש: מוותרים על המיותרת
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

        /** מספרי אויב מתחת לערך הזה יוצרים המנוע, מעליו המנהל */
        private const val ID_BASE = 100_000

        val DEFAULT_WAVES: List<WaveSpec> = listOf(
            // גל 1: בלונים, ובסוף התמנון הראשון
            WaveSpec(
                foes = listOf(Foe.Balloon, Foe.Balloon, Foe.Balloon, Foe.Octopus),
                maxThreat = 3, maxAttacks = 1, cooldownMin = 3.5f, cooldownMax = 5.5f, flightScale = 1.15f
            ),
            // גל 2: מופיע החייזר
            WaveSpec(
                foes = listOf(Foe.Balloon, Foe.Octopus, Foe.Balloon, Foe.Octopus, Foe.Alien),
                maxThreat = 4, maxAttacks = 2, cooldownMin = 3.0f, cooldownMax = 5.0f, flightScale = 1.05f
            ),
            // גל 3: מופיע הענק
            WaveSpec(
                foes = listOf(Foe.Octopus, Foe.Alien, Foe.Octopus, Foe.Alien, Foe.Giant),
                maxThreat = 5, maxAttacks = 2, cooldownMin = 2.6f, cooldownMax = 4.4f, flightScale = 1.0f
            ),
            // גל 4: מופיע הקיפוד
            WaveSpec(
                foes = listOf(Foe.Balloon, Foe.Alien, Foe.Balloon, Foe.Alien, Foe.Giant, Foe.Hedgehog),
                maxThreat = 6, maxAttacks = 2, cooldownMin = 2.2f, cooldownMax = 4.0f, flightScale = 0.95f
            ),
            // גל 5 (הגמר, עד שהדרקון יהיה הבוס): כולם יחד
            WaveSpec(
                foes = listOf(
                    Foe.Balloon, Foe.Octopus, Foe.Alien, Foe.Balloon, Foe.Hedgehog,
                    Foe.Octopus, Foe.Giant, Foe.Alien, Foe.Hedgehog
                ),
                maxThreat = 7, maxAttacks = 3, cooldownMin = 1.8f, cooldownMax = 3.4f, flightScale = 0.9f
            )
        )
    }
}

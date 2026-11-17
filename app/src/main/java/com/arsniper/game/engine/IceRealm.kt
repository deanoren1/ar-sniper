package com.arsniper.game.engine

import kotlin.math.hypot
import kotlin.random.Random

/**
 * סרטוני בוס הקרח (בסדר הזה גם ברשימת ההשמעה של הנגן). משך במילישניות, לפי מספר הפריימים ב-24fps.
 * startMs = מאיפה מתחילים: בסרטוני הפגיעה הפריימים הראשונים כמעט זהים לעמידה, אז מדלגים ישר לרגע שהבוס מגיב.
 */
enum class BossClip(val durationMs: Float, val loop: Boolean = false, val startMs: Float = 0f) {
    Intro(6667f),
    Breath(5042f),
    Prepare(5042f),
    Attack1(4042f),
    Attack3(4042f),
    Attack4(4042f),
    Attack5(4042f),
    HitHead(2000f, startMs = 42f),
    HitRightShoulder(2000f, startMs = 250f),
    HitLeftShoulder(2000f, startMs = 167f),
    HitRightHand(2000f, startMs = 250f),
    HitLeftHand(2000f, startMs = 208f),
    Death(4042f)
}

/** מרכז הסינוור של כדור הקרח (סרטון 6), בשברי הווידאו: איפה שהכדור פוגע, בצד ימין של המסך */
const val DAZZLE_X = 0.66f
const val DAZZLE_Y = 0.6f

/**
 * מתקפות הבוס. impactMs = רגע הפגיעה בסרטון (נמדד מהפריים שבו המכה מגיעה למסך), ורק אז השחקן נפגע.
 * הגנה: רגע לפני כל מכה מופיעה מטרה קטנה במקום אקראי (בזעם: שתיים ברצף). פוגעים בה בזמן = המכה נחסמת.
 * פגיעה מורידה לב אחד דרך מערכת הלבבות הקיימת, עם רטט ורעידת מסך.
 */
enum class BossAttack(
    val clip: BossClip,
    val impactMs: Float,
    val shake: Float,
    val dazzle: Boolean = false
) {
    /** סרטון 3 (Attack1): חבטת טופר אל המצלמה. בא תמיד אחרי סרטון 2 (ההכנה) */
    ClawSwipe(BossClip.Attack1, 2083f, 1.0f),

    /** סרטון 5 (Attack3): הלמות שתי האגרופים בקרקע, גל ברקים */
    ThunderSlam(BossClip.Attack3, 1750f, 1.2f),

    /** סרטון 6 (Attack5): כדור קרח שנזרק ופוגע במסך בצד ימין; המסך מסתנוור בצד, לא במרכז */
    FrostOrb(BossClip.Attack5, 2625f, 1.0f, dazzle = true),

    /** סרטון 7 (Attack4): קפיצה ונחיתה, גל קרח לאורך הרצפה */
    GroundPound(BossClip.Attack4, 1750f, 1.1f)
}

/**
 * עולם הקרח: שלב הבוס של ארנת הקרח (גל 6). הבוס הוא סרטונים: עמידה ונשימה, הכנה למתקפה, 5 מתקפות,
 * תגובות פגיעה לפי מקום הפגיעה, ומוות. Kotlin טהור: הנגן והציור ב-IceRealmLayer, החיבור למשחק ב-GameScreen.
 *
 * הזרימה (לפי מספרי הסרטונים של דין):
 *  סדקי קרח זוהרים על רצפת הארנה (2 שניות) → 0 פורטל → 1 כניסה → 2 הכנה → 3 חבטת טופר → 4 נשימה → 5 ברקים → 6 כדור קרח → 7 קפיצה
 *  ואז אקראי, בלי אותו צעד פעמיים ברצף: [2+3] (3 תמיד אחרי 2), 4, 5, 6, 7.
 *  פגיעה בבוס כשהוא נושם (4) = סרטון התגובה של אותו מקום, עד הסוף, ואז ממשיכים בזרימה.
 *  פגיעה בהכנה (2) לא עוצרת את המתקפה (היהלום הזהוב = נזק כפול). מתחמקים מהמתקפה עם הכוונת (ג'ויסטיק).
 *  כל מצב → Dead (Death, המוות האחרון) כשהחיים נגמרים. מ-Dead אין חזרה.
 *
 * חיים: 5 יהלומים (ראש, 2 כתפיים, 2 ידיים) × 3 = 15, כמו קודם. פגיעה = 1, ביהלום הזהוב בהכנה = 2.
 * הבוס פגיע רק בנשימה ובהכנה (שם יש מעקב מדויק של הנקודות); במתקפה ובתגובה הירייה נהדפת.
 * פגיעה בגוף (לא ביהלום) נהדפת בלי נזק, כמו קודם (אין לה סרטון תגובה ייעודי).
 *
 * הזמן בתוך סרטון: לפי מיקום הנגן כשהוא מסונכרן (syncVideo), ואחרת לפי שעון פנימי, כך שאם הווידאו נתקע
 * המשחק לא נתקע איתו.
 */
class IceRealm {

    enum class Stage { Off, Cracks, Portal, Intro, Idle, PreparingAttack, Attacking, HitReaction, Dead }

    /** מקום פגיעה. ימין/שמאל = של הבוס (ימין שלו בצד שמאל של המסך). index = מקום בטבלאות המעקב */
    enum class Zone(val index: Int, val clip: BossClip) {
        Head(0, BossClip.HitHead),
        RightShoulder(1, BossClip.HitRightShoulder),
        LeftShoulder(2, BossClip.HitLeftShoulder),
        RightHand(3, BossClip.HitRightHand),
        LeftHand(4, BossClip.HitLeftHand)
    }

    class Diamond(val zone: Zone, var hp: Int) {
        val alive: Boolean get() = hp > 0
    }

    var stage: Stage = Stage.Off
        private set

    /** שניות מתחילת העולם, ושניות בתוך המצב הנוכחי */
    var time: Float = 0f
        private set
    var stageTime: Float = 0f
        private set

    val diamonds: List<Diamond> = Zone.entries.map { Diamond(it, DIAMOND_HP) }

    // --- הסרטון הנוכחי ---
    var clip: BossClip? = null
        private set

    /** עולה בכל בקשת סרטון חדשה: הנגן מתחיל סרטון רק כשהמספר משתנה (אין השמעה כפולה מאותו אירוע) */
    var clipToken: Int = 0
        private set
    var clipSpeed: Float = 1f
        private set

    /** מיקום בסרטון (ms של הסרטון) */
    var clipMs: Float = 0f
        private set
    private var ownMs = 0f
    private var videoToken = -1
    private var videoMs = 0L
    private var videoEnded = false

    /** המתקפה הנוכחית (במצב Attacking) */
    var attack: BossAttack? = null
        private set
    private var impactDone = false

    /** צעדי הזרימה. PrepClaw = סרטון 2 ואחריו 3 */
    private enum class Step { PrepClaw, Breath, Thunder, Orb, Pound }

    private val script = arrayOf(Step.PrepClaw, Step.Breath, Step.Thunder, Step.Orb, Step.Pound)
    private var scriptIndex = 0
    private var lastStep: Step? = null

    /** היהלום שנדלק בהכנה למתקפה (-1 = אין) */
    var exposed: Int = -1
        private set

    /** הבזקים לציור: פגיעה ביהלום (ומיקומה בשברי מסך), והבזק לבן שמסתיר קפיצה בין סרטונים */
    var hitFlash: Float = 0f
        private set
    var hitX: Float = 0f
        private set
    var hitY: Float = 0f
        private set
    var cutFlash: Float = 0f
        private set

    /** סינוור מכדור הקרח (שניות שנשארו), במרכז DAZZLE_X/Y */
    var dazzle: Float = 0f
        private set

    /**
     * הווידאו של הבוס מכסה את כל המסך (נקבע מהשכבה). כשכן, המשחק לא מצייר את הארנה מתחת (חוסך עבודה, הווידאו חלק יותר)
     */
    var covering: Boolean = false

    /** כמה מתקפות כבר היו בקרב הזה (לרמז ההתחמקות בהתחלה) */
    var attacksSeen: Int = 0
        private set

    /** "התחמקת" על המסך אחרי התחמקות */
    var dodgeFlash: Float = 0f
        private set

    // ---- מטרות החסימה: רגע לפני מכה מופיעה מטרה קטנה במקום אחר בכל פעם; פוגעים בה = המכה נחסמת ----
    /** מיקומי המטרות (שברי מסך כשאין זום): x0, y0, x1, y1 */
    val targets = FloatArray(4)

    /** כמה מטרות יש במתקפה הזו (0 = עוד לא הופיעו), ואיזו מהן הנוכחית */
    var targetCount: Int = 0
        private set
    var targetIndex: Int = 0
        private set

    /** רדיוס המטרה (חלק מגובה המסך) */
    var targetRadius: Float = 0.05f
        private set

    /** המכה נחסמה (כל המטרות נפגעו בזמן) */
    var blocked: Boolean = false
        private set
    var blockFlash: Float = 0f
        private set

    /** המטרות מוצגות עכשיו */
    val targetsActive: Boolean
        get() = targetCount > 0 && !blocked && (
            (stage == Stage.Attacking && !impactDone) || (stage == Stage.PreparingAttack && preSpawned)
        )

    /** כמה זמן נשאר עד המכה, 1 = הרגע הופיעה, 0 = המכה */
    val targetTimeLeft: Float
        get() {
            if (stage == Stage.PreparingAttack) return 1f
            val a = attack ?: return 0f
            val span = if (preSpawned) a.impactMs else warnMs()
            return ((a.impactMs - clipMs) / span).coerceIn(0f, 1f)
        }

    /** המטרה של המתקפה הראשונה בקרב: מופיעה כבר בהכנה, גדולה ובאמצע המסך, כדי שהשחקן יבין מה לעשות */
    private var preSpawned = false

    private fun spawnFirstTarget() {
        targetCount = 1
        targetIndex = 0
        targetRadius = FIRST_TARGET_RADIUS
        targets[0] = 0.5f
        targets[1] = 0.45f
        lastTargetX = 0.5f
        lastTargetY = 0.45f
        blocked = false
        preSpawned = true
    }

    private fun spawnTargets() {
        val n = if (phase == 3) 2 else 1
        targetCount = n
        targetIndex = 0
        targetRadius = when (phase) { 1 -> 0.085f; 2 -> 0.04f; else -> 0.05f }
        for (i in 0 until n) {
            var x: Float
            var y: Float
            var tries = 0
            do {
                x = 0.22f + Random.nextFloat() * 0.56f
                y = 0.26f + Random.nextFloat() * 0.44f
                tries++
                val farFromFirst = i == 0 || kotlin.math.hypot(x - targets[0], y - targets[1]) > 0.28f
                val farFromLast = kotlin.math.hypot(x - lastTargetX, y - lastTargetY) > 0.2f
            } while ((!farFromFirst || !farFromLast) && tries < 20)
            targets[i * 2] = x
            targets[i * 2 + 1] = y
        }
        lastTargetX = targets[(n - 1) * 2]
        lastTargetY = targets[(n - 1) * 2 + 1]
    }

    private var lastTargetX = 0.5f
    private var lastTargetY = 0.5f

    private fun warnMs() = when (phase) { 1 -> WARN_MS_PHASE1; 2 -> WARN_MS_PHASE2; else -> WARN_MS_PHASE3 }

    /** שלב 1: אחרי כל מתקפה הבוס נושם (חלון ירי) */
    private var restPending = false

    private var hitThisTick = false
    private var deathHold = 0f

    /** הבוס נוצח והמנהל קיבל הודעה */
    var finished: Boolean = false
        private set

    /** רץ (מתעדכן). אחרי הניצחון הוא כבר לא רץ, אבל נשאר על המסך (הפריים האחרון של המוות) */
    val active: Boolean get() = stage != Stage.Off && !finished
    val visible: Boolean get() = stage != Stage.Off

    /** בקרב (אפשר לירות). בפורטל, בכניסה ובמוות לא יורים */
    val fighting: Boolean
        get() = stage == Stage.Idle || stage == Stage.PreparingAttack || stage == Stage.Attacking || stage == Stage.HitReaction

    /** הבוס פגיע עכשיו (יש מעקב לנקודות בסרטון הזה) */
    val vulnerable: Boolean
        get() = when (stage) {
            Stage.Idle, Stage.PreparingAttack -> true
            // במתקפה ובתגובה: כל עוד יש מעקב אמין לנקודות (במתקפה עד רגע לפני התנועה המהירה)
            Stage.Attacking, Stage.HitReaction -> clip?.let { IceBossTracks.covers(it, clipMs) } ?: false
            else -> false
        }

    val totalHp: Int get() = diamonds.sumOf { it.hp }
    val fraction: Float get() = totalHp.toFloat() / (diamonds.size * DIAMOND_HP)
    private val broken: Int get() = diamonds.count { !it.alive }

    /**
     * שלב הקרב לפי החיים שנשארו:
     *  1 (100%–70%): מתקפות איטיות, חלונות פגיעה ארוכים, הזהרה ארוכה; השחקן לומד.
     *  2 (70%–35%): מתקפות מהירות יותר, פחות מנוחה (נשימה קצרה), הזהרה קצרה יותר.
     *  3 (35%–0%): זעם: מתקפות משולבות (שתיים ברצף), עוצמה גבוהה, חלונות פגיעה קצרים.
     * ניצחון: כשכל 5 היהלומים נהרסים.
     */
    val phase: Int
        get() = when {
            fraction > PHASE2_AT -> 1
            fraction > PHASE3_AT -> 2
            else -> 3
        }
    val enraged: Boolean get() = phase == 3

    private fun attackSpeed() = when (phase) { 1 -> 0.9f; 2 -> 1.15f; else -> 1.3f }
    private fun prepareSpeed() = when (phase) { 1 -> 1.0f; 2 -> 1.2f; else -> 1.4f }

    /** כמה שניות הבוס נושם (חלון הפגיעה): מלא בשלב 1, מתקצר אחר כך */
    private fun breathSeconds() = when (phase) { 1 -> 5.0f; 2 -> 3.2f; else -> 2.0f }

    /** עוצמת הרעידה של מכה */
    val impactPower: Float get() = when (phase) { 1 -> 1f; 2 -> 1.15f; else -> 1.35f }

    private var comboChained = false

    /** התקדמות ההכנה 0..1 (לטבעת שמתכווצת סביב היהלום הדלוק) */
    val prepareFraction: Float
        get() = if (stage == Stage.PreparingAttack) (clipMs / BossClip.Prepare.durationMs).coerceIn(0f, 1f) else 0f

    fun begin() {
        if (stage != Stage.Off) return
        reset()
        go(Stage.Cracks)
    }

    /** חוזר להתחלה (החייאה, משחק חוזר, או ארנה חדשה). הנגן משתחרר כשהשכבה יורדת מהמסך */
    fun reset() {
        stage = Stage.Off
        time = 0f
        stageTime = 0f
        for (d in diamonds) d.hp = DIAMOND_HP
        clip = null
        clipSpeed = 1f
        clipMs = 0f
        ownMs = 0f
        videoToken = -1
        videoMs = 0L
        videoEnded = false
        attack = null
        impactDone = false
        scriptIndex = 0
        lastStep = null
        exposed = -1
        hitFlash = 0f
        cutFlash = 0f
        dodgeFlash = 0f
        dazzle = 0f
        comboChained = false
        covering = false
        targetCount = 0
        targetIndex = 0
        blocked = false
        blockFlash = 0f
        restPending = false
        preSpawned = false
        attacksSeen = 0
        hitThisTick = false
        deathHold = 0f
        finished = false
    }

    /** מהנגן, בכל פריים: איזה סרטון הוא מנגן (token), איפה, והאם הגיע לסוף. token = -1 אם הנגן לא מוכן */
    fun syncVideo(token: Int, positionMs: Long, ended: Boolean) {
        videoToken = token
        videoMs = positionMs
        videoEnded = ended
    }

    private fun go(next: Stage) {
        stage = next
        stageTime = 0f
    }

    /**
     * נקרא מיד כשמכונת המצבים מחליפה סרטון (מהנגן), כדי שהסרטון יתחיל באותו רגע ולא רק בפריים הבא.
     */
    var onClip: ((BossClip, Int, Float) -> Unit)? = null

    private fun play(c: BossClip, speed: Float = 1f) {
        clip = c
        clipSpeed = speed
        clipToken += 1
        clipMs = c.startMs
        ownMs = c.startMs
        videoEnded = false
        onClip?.invoke(c, clipToken, speed)
    }

    private fun synced(): Boolean = videoToken == clipToken

    private fun advanceClock(dt: Float) {
        val c = clip ?: return
        ownMs += dt * 1000f * clipSpeed
        if (c.loop) ownMs %= c.durationMs
        // הנגן הוא האמת, אלא אם הוא נתקע הרבה מאחורי השעון הפנימי
        val useVideo = synced() && (c.loop || ownMs - videoMs < STALL_MS)
        clipMs = if (useVideo) videoMs.toFloat() else ownMs
    }

    private fun clipDone(): Boolean {
        val c = clip ?: return true
        if (c.loop) return false
        return (synced() && videoEnded) || clipMs >= c.durationMs - END_SLACK_MS
    }

    /**
     * מקדם פריים. onImpact נקרא פעם אחת לכל מתקפה, ברגע הפגיעה שבסרטון. onWin כשהמוות הסתיים.
     */
    fun update(
        dt: Float,
        onImpact: (BossAttack) -> Unit, onBlock: (BossAttack) -> Unit, onWin: () -> Unit
    ) {
        if (!active) return
        time += dt
        stageTime += dt
        hitFlash = (hitFlash - dt).coerceAtLeast(0f)
        dodgeFlash = (dodgeFlash - dt).coerceAtLeast(0f)
        dazzle = (dazzle - dt).coerceAtLeast(0f)
        blockFlash = (blockFlash - dt).coerceAtLeast(0f)
        cutFlash = (cutFlash - dt).coerceAtLeast(0f)
        hitThisTick = false
        advanceClock(dt)
        when (stage) {
            Stage.Cracks -> if (stageTime >= CRACKS_SECONDS) go(Stage.Portal)
            Stage.Portal -> if (stageTime >= PORTAL_SECONDS) {
                go(Stage.Intro)
                play(BossClip.Intro)
            }
            Stage.Intro -> if (clipDone()) nextStep()
            Stage.Idle -> if (clipDone() || stageTime >= breathSeconds()) nextStep()
            Stage.PreparingAttack -> {
                // המתקפה הראשונה בקרב: המטרה מופיעה כבר באמצע ההכנה (הרבה זמן ללמוד)
                if (attacksSeen == 0 && targetCount == 0 && clipMs >= FIRST_TARGET_AT_MS) spawnFirstTarget()
                if (clipDone()) startAttack(BossAttack.ClawSwipe)
            }
            Stage.Attacking -> {
                val a = attack
                if (a == null) {
                    enterIdle()
                } else {
                    if (targetCount == 0 && clipMs >= a.impactMs - warnMs()) spawnTargets()
                    if (!impactDone && clipMs >= a.impactMs) {
                        impactDone = true
                        if (a.dazzle && !blocked) dazzle = DAZZLE_SECONDS
                        if (blocked) {
                            dodgeFlash = DODGE_FLASH_SECONDS
                            onBlock(a)
                        } else {
                            onImpact(a)
                        }
                    }
                    if (clipDone()) {
                        attack = null
                        preSpawned = false
                        // שלב 3: מתקפה משולבת, עוד מתקפה (אחרת) מיד, בלי מנוחה
                        if (phase == 3 && !comboChained && Random.nextFloat() < COMBO_CHANCE) {
                            comboChained = true
                            val others = listOf(BossAttack.ThunderSlam, BossAttack.FrostOrb, BossAttack.GroundPound).filter { it != a }
                            startAttack(others[Random.nextInt(others.size)])
                        } else {
                            comboChained = false
                            if (phase == 1) restPending = true
                            nextStep()
                        }
                    }
                }
            }
            // תגובת הפגיעה מתנגנת עד הסוף, ואז ממשיכים בזרימה
            Stage.HitReaction -> if (clipDone()) nextStep()
            Stage.Dead -> if (clipDone()) {
                deathHold += dt
                if (deathHold >= DEATH_HOLD_SECONDS) {
                    finished = true
                    onWin()
                }
            }
            Stage.Off -> Unit
        }
    }

    /** הצעד הבא: קודם הסדר הקבוע (2+3, 4, 5, 6, 7), ואז אקראי בלי חזרה על אותו צעד ברצף */
    private fun nextStep() {
        // שלב 1: מנוחה (נשימה) אחרי כל מתקפה, לפני הצעד הבא
        if (restPending && phase == 1 && lastStep != Step.Breath) {
            restPending = false
        preSpawned = false
            enterIdle()
            return
        }
        restPending = false
        preSpawned = false
        val step = if (scriptIndex < script.size) {
            script[scriptIndex++]
        } else {
            val pool = Step.entries.filter { it != lastStep }
            pool[Random.nextInt(pool.size)]
        }
        lastStep = step
        when (step) {
            Step.PrepClaw -> startPrepare()
            Step.Breath -> enterIdle()
            Step.Thunder -> startAttack(BossAttack.ThunderSlam)
            Step.Orb -> startAttack(BossAttack.FrostOrb)
            Step.Pound -> startAttack(BossAttack.GroundPound)
        }
    }

    /**
     * אחרי החייאה: חוזרים ישר לבוס (בלי סדקים/פורטל/כניסה מחדש), עם החיים שנשארו לו.
     * אם הבוס היה באמצע מתקפה, הוא חוזר לנשימה, כדי שהשחקן לא ייפגע ברגע שחזר.
     */
    fun resumeAfterRevive() {
        if (!active) return
        if (stage == Stage.Attacking || stage == Stage.PreparingAttack || stage == Stage.HitReaction) {
            attack = null
            impactDone = true
            comboChained = false
            enterIdle()
        }
    }

    private fun enterIdle() {
        go(Stage.Idle)
        exposed = -1
        play(BossClip.Breath)
    }

    private fun startPrepare() {
        val live = diamonds.indices.filter { diamonds[it].alive }
        exposed = if (live.isEmpty()) -1 else live[Random.nextInt(live.size)]
        go(Stage.PreparingAttack)
        play(BossClip.Prepare, prepareSpeed())
    }

    private fun startAttack(a: BossAttack) {
        attacksSeen += 1
        // מטרה שהופיעה כבר בהכנה ממשיכה (וגם אם כבר נפגעה, החסימה נשמרת)
        if (!preSpawned) {
            targetCount = 0
            targetIndex = 0
            blocked = false
        }
        exposed = -1
        attack = a
        impactDone = false
        go(Stage.Attacking)
        play(a.clip, attackSpeed())
    }

    private val pts = FloatArray(IceBossTracks.POINTS * 3)

    /**
     * מיקומי היהלומים על המסך עכשיו: [x, y, r] לכל יהלום (x, y בשברי מסך, r בשברי גובה מסך).
     * מחזיר null אם הבוס לא פגיע בסרטון הנוכחי.
     */
    fun zonesOnScreen(aspect: Float, out: FloatArray = FloatArray(IceBossTracks.POINTS * 3)): FloatArray? {
        val c = clip ?: return null
        if (!vulnerable) return null
        if (!IceBossTracks.points(c, clipMs, pts)) return null
        val r = BossFrame.rect(aspect)
        for (i in 0 until IceBossTracks.POINTS) {
            out[i * 3] = BossFrame.screenX(pts[i * 3], r)
            out[i * 3 + 1] = BossFrame.screenY(pts[i * 3 + 1], r)
            out[i * 3 + 2] = BossFrame.screenLen(pts[i * 3 + 2], r)
        }
        return out
    }

    private val zoneBuf = FloatArray(IceBossTracks.POINTS * 3)

    /** האם הנקודה על גוף הבוס (בערך: בין הידיים, מהראש עד הידיים) */
    private fun inBody(px: Float, py: Float, z: FloatArray): Boolean {
        val left = z[3 * 3] - z[3 * 3 + 2]
        val right = z[4 * 3] + z[4 * 3 + 2]
        val top = z[1] - z[2]
        val bottom = maxOf(z[3 * 3 + 1], z[4 * 3 + 1]) + z[3 * 3 + 2]
        return px in left..right && py in top..bottom
    }

    /**
     * ירייה בנקודה על המסך (px, py בשברי מסך; aspect = רוחב/גובה). מחזיר מה קרה.
     * לכל היותר פגיעה אחת שגורמת נזק בכל פריים, ותגובת פגיעה לא מתחילה מחדש באמצע תגובה.
     */
    fun shoot(px: Float, py: Float, aspect: Float): Hit {
        // מטרת החסימה של המתקפה
        if (targetsActive) {
            if (hitThisTick) return Hit.None
            val tx = targets[targetIndex * 2]
            val ty = targets[targetIndex * 2 + 1]
            if (hypot((px - tx) * aspect, py - ty) <= targetRadius * 1.15f) {
                hitThisTick = true
                hitFlash = HIT_FLASH_SECONDS
                hitX = px
                hitY = py
                targetIndex += 1
                if (targetIndex >= targetCount) {
                    blocked = true
                    blockFlash = BLOCK_FLASH_SECONDS
                    return Hit.Block
                }
                return Hit.Target
            }
            // לא במטרה: אולי ביהלום (הבוס פגיע גם בזמן שהמטרה מוצגת)
        }
        if (!vulnerable) return if (stage == Stage.Attacking || stage == Stage.HitReaction) Hit.Guard else Hit.None
        if (hitThisTick) return Hit.None
        val z = zonesOnScreen(aspect, zoneBuf) ?: return Hit.None
        var best = -1
        var bestD = Float.MAX_VALUE
        for ((i, d) in diamonds.withIndex()) {
            if (!d.alive) continue
            val idx = d.zone.index
            val dist = hypot((px - z[idx * 3]) * aspect, py - z[idx * 3 + 1])
            val radius = z[idx * 3 + 2] * HIT_SCALE
            if (dist < radius && dist < bestD) {
                best = i
                bestD = dist
            }
        }
        if (best < 0) return if (inBody(px, py, z)) Hit.Deflect else Hit.None

        hitThisTick = true
        val d = diamonds[best]
        // היהלום הזהוב בהכנה: נזק כפול, אבל המתקפה לא נעצרת
        val bonus = stage == Stage.PreparingAttack && best == exposed
        d.hp = (d.hp - if (bonus) EXPOSED_DAMAGE else 1).coerceAtLeast(0)
        hitFlash = HIT_FLASH_SECONDS
        hitX = px
        hitY = py

        // החיים נגמרו: מוות, בעדיפות על כל דבר אחר
        if (diamonds.none { it.alive }) {
            exposed = -1
            attack = null
            go(Stage.Dead)
            play(BossClip.Death)
            return Hit.Killed
        }
        // תגובת פגיעה (הסרטון של אותו מקום) כשהבוס נושם; בהכנה למתקפה הפגיעה לא עוצרת אותו
        if (stage == Stage.Idle) {
            exposed = -1
            go(Stage.HitReaction)
            play(d.zone.clip)
        }
        return if (d.hp == 0) Hit.Broke else if (bonus) Hit.Bonus else Hit.Hit
    }

    /** None = לא על הבוס; Deflect = גוף בלי יהלום; Guard = הבוס לא פגיע עכשיו (מתקפה/תגובה) */
    enum class Hit { None, Deflect, Guard, Hit, Bonus, Broke, Killed, Target, Block }

    companion object {
        const val DIAMOND_HP = 3
        const val EXPOSED_DAMAGE = 2

        /** גבולות השלבים (חלק מהחיים) */
        const val PHASE2_AT = 0.70f
        const val PHASE3_AT = 0.35f

        /** בשלב 3: הסיכוי שמתקפה תבוא מיד אחרי מתקפה (משולבת) */
        const val COMBO_CHANCE = 0.65f

        /** סדקי הקרח על הרצפה לפני הפורטל */
        const val CRACKS_SECONDS = 2.0f

        /** כמה זמן לפני הפגיעה מופיע אזור הסכנה (קשה: מספיק רק למי שזז מהר), ובזעם */
        /** כמה זמן לפני המכה מופיעה מטרת החסימה (בזעם: לשתי המטרות יחד) */
        const val WARN_MS_PHASE1 = 1600f
        const val WARN_MS_PHASE2 = 1200f
        const val WARN_MS_PHASE3 = 1700f

        const val BLOCK_FLASH_SECONDS = 0.5f

        /** המטרה הראשונה: מתי בהכנה היא מופיעה, וכמה היא גדולה */
        const val FIRST_TARGET_AT_MS = 2000f
        const val FIRST_TARGET_RADIUS = 0.11f

        const val DODGE_FLASH_SECONDS = 0.8f

        /** הפורטל (סרטון 0, בלי הרקע הירוק) מעל הארנה, לפני סרטון הכניסה */
        const val PORTAL_SECONDS = 29f / 24f

        /** כמה זמן המסך מסתנוור מכדור הקרח (כ-14 פריימים) */
        const val DAZZLE_SECONDS = 0.6f

        /** אחרי שסרטון המוות נגמר: הבוס המובס נשאר על המסך עוד רגע, ואז הארנה נוצחת */
        const val DEATH_HOLD_SECONDS = 1.0f

        const val HIT_FLASH_SECONDS = 0.22f
        const val CUT_FLASH_SECONDS = 0.22f

        /** כמה מהרדיוס של הנקודה נחשב פגיעה */
        const val HIT_SCALE = 1.3f

        /** אם הנגן מפגר יותר מזה אחרי השעון הפנימי, ממשיכים לפי השעון (הנגן נתקע) */
        const val STALL_MS = 600f

        /** סרטון נחשב גמור קצת לפני הפריים האחרון (פריים אחד) */
        const val END_SLACK_MS = 42f
    }
}

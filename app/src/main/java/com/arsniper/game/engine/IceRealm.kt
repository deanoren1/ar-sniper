package com.arsniper.game.engine

import kotlin.math.hypot
import kotlin.random.Random

/**
 * סרטוני בוס הקרח (בסדר הזה גם ברשימת ההשמעה של הנגן). משך במילישניות, לפי מספר הפריימים ב-24fps.
 * startMs = מאיפה מתחילים: בסרטוני הפגיעה הפריימים הראשונים כמעט זהים לעמידה, אז מדלגים ישר לרגע שהבוס מגיב.
 */
enum class BossClip(val durationMs: Float, val loop: Boolean = false, val startMs: Float = 0f) {
    Intro(6750f),
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

/**
 * אזור הסכנה של מתקפה, בשברי הווידאו (x = חלק מהרוחב, y = חלק מהגובה). מרחקים במעגל/פס ביחידות של גובה הווידאו.
 * ברגע הפגיעה: אם הכוונת (שזזה בג'ויסטיק) בתוך האזור, השחקן נפגע; אם הספיק להזיז אותה החוצה, הוא התחמק.
 */
sealed class DangerZone {
    abstract fun contains(vx: Float, vy: Float): Boolean

    class Box(val l: Float, val t: Float, val r: Float, val b: Float) : DangerZone() {
        override fun contains(vx: Float, vy: Float) = vx in l..r && vy in t..b
    }

    class Circle(val cx: Float, val cy: Float, val radius: Float) : DangerZone() {
        override fun contains(vx: Float, vy: Float): Boolean {
            val dx = (vx - cx) * BossFrame.VIDEO_ASPECT
            val dy = vy - cy
            return dx * dx + dy * dy <= radius * radius
        }
    }

    /** פס אלכסוני מ-(x0,y0) ל-(x1,y1), חצי רוחב half */
    class Band(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val half: Float) : DangerZone() {
        override fun contains(vx: Float, vy: Float): Boolean {
            val a = BossFrame.VIDEO_ASPECT
            val px = vx * a; val ax = x0 * a; val bx = x1 * a
            val ex = bx - ax; val ey = y1 - y0
            val k = (((px - ax) * ex + (vy - y0) * ey) / (ex * ex + ey * ey)).coerceIn(0f, 1f)
            val dx = px - (ax + ex * k); val dy = vy - (y0 + ey * k)
            return dx * dx + dy * dy <= half * half
        }
    }
}

/** מרכז הסינוור של כדור הקרח (סרטון 6), בשברי הווידאו: איפה שהכדור פוגע, בצד ימין של המסך */
const val DAZZLE_X = 0.66f
const val DAZZLE_Y = 0.6f

/**
 * מתקפות הבוס. impactMs = רגע הפגיעה בסרטון (נמדד מהפריים שבו המכה מגיעה למסך), ורק אז השחקן נפגע.
 * כל מתקפה פוגעת באזור אחר על המסך (zone), ואזור הסכנה מופיע רק רגע לפני הפגיעה: מזיזים את הכוונת החוצה בג'ויסטיק.
 * פגיעה מורידה לב אחד דרך מערכת הלבבות הקיימת, עם רטט ורעידת מסך.
 */
enum class BossAttack(
    val clip: BossClip,
    val impactMs: Float,
    val shake: Float,
    val zone: DangerZone,
    val dazzle: Boolean = false
) {
    /** סרטון 3 (Attack1): חבטת טופר אל המצלמה. בא תמיד אחרי סרטון 2 (ההכנה) */
    ClawSwipe(BossClip.Attack1, 2083f, 1.0f, DangerZone.Box(0.12f, 0.30f, 0.60f, 1.0f)),

    /** סרטון 5 (Attack3): הלמות שתי האגרופים בקרקע, גל ברקים */
    ThunderSlam(BossClip.Attack3, 1750f, 1.2f, DangerZone.Box(0f, 0.56f, 1f, 1f)),

    /** סרטון 6 (Attack5): כדור קרח שנזרק ופוגע במסך בצד ימין; המסך מסתנוור בצד, לא במרכז */
    FrostOrb(BossClip.Attack5, 2625f, 1.0f, DangerZone.Circle(DAZZLE_X, DAZZLE_Y, 0.27f), dazzle = true),

    /** סרטון 7 (Attack4): קפיצה ונחיתה, גל קרח לאורך הרצפה */
    GroundPound(BossClip.Attack4, 1750f, 1.1f, DangerZone.Box(0.32f, 0f, 0.68f, 1f))
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

    /** "התחמקת" על המסך אחרי התחמקות */
    var dodgeFlash: Float = 0f
        private set

    /** אזור הסכנה שמוצג עכשיו (null = אין), ועוצמת ההתראה 0..1 (מתמלאת עד רגע הפגיעה) */
    val warnZone: DangerZone?
        get() {
            val a = attack ?: return null
            if (stage != Stage.Attacking || impactDone) return null
            return if (clipMs >= a.impactMs - warnMs()) a.zone else null
        }
    val warnFraction: Float
        get() {
            val a = attack ?: return 0f
            if (stage != Stage.Attacking || impactDone) return 0f
            return ((clipMs - (a.impactMs - warnMs())) / warnMs()).coerceIn(0f, 1f)
        }

    private fun warnMs() = when (phase) { 1 -> WARN_MS_PHASE1; 2 -> WARN_MS_PHASE2; else -> WARN_MS_PHASE3 }

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
    val vulnerable: Boolean get() = stage == Stage.Idle || stage == Stage.PreparingAttack

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
        dt: Float, aimX: Float, aimY: Float, aspect: Float,
        onImpact: (BossAttack) -> Unit, onDodge: (BossAttack) -> Unit, onWin: () -> Unit
    ) {
        if (!active) return
        time += dt
        stageTime += dt
        hitFlash = (hitFlash - dt).coerceAtLeast(0f)
        dodgeFlash = (dodgeFlash - dt).coerceAtLeast(0f)
        dazzle = (dazzle - dt).coerceAtLeast(0f)
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
            Stage.PreparingAttack -> if (clipDone()) startAttack(BossAttack.ClawSwipe)
            Stage.Attacking -> {
                val a = attack
                if (a == null) {
                    enterIdle()
                } else {
                    if (!impactDone && clipMs >= a.impactMs) {
                        impactDone = true
                        if (a.dazzle) dazzle = DAZZLE_SECONDS
                        // הכוונת היא המקום של השחקן: בתוך האזור = נפגע, מחוץ = התחמק
                        val r = BossFrame.rect(aspect)
                        val vx = (aimX - r[0]) / r[2]
                        val vy = (aimY - r[1]) / r[3]
                        if (a.zone.contains(vx, vy)) {
                            onImpact(a)
                        } else {
                            dodgeFlash = DODGE_FLASH_SECONDS
                            onDodge(a)
                        }
                    }
                    if (clipDone()) {
                        attack = null
                        // שלב 3: מתקפה משולבת, עוד מתקפה (אחרת) מיד, בלי מנוחה
                        if (phase == 3 && !comboChained && Random.nextFloat() < COMBO_CHANCE) {
                            comboChained = true
                            val others = listOf(BossAttack.ThunderSlam, BossAttack.FrostOrb, BossAttack.GroundPound).filter { it != a }
                            startAttack(others[Random.nextInt(others.size)])
                        } else {
                            comboChained = false
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
        if (stage == Stage.Attacking || stage == Stage.HitReaction) return Hit.Guard
        if (!vulnerable) return Hit.None
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
    enum class Hit { None, Deflect, Guard, Hit, Bonus, Broke, Killed }

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
        const val WARN_MS_PHASE1 = 1100f
        const val WARN_MS_PHASE2 = 800f
        const val WARN_MS_PHASE3 = 600f

        const val DODGE_FLASH_SECONDS = 0.8f

        /** הפורטל (סרטון 0, בלי הרקע הירוק) מעל הארנה, לפני סרטון הכניסה */
        const val PORTAL_SECONDS = 4.04f

        /** כמה זמן המסך מסתנוור מכדור הקרח (כ-14 פריימים) */
        const val DAZZLE_SECONDS = 0.6f

        /** אחרי שסרטון המוות נגמר: הבוס המובס נשאר על המסך עוד רגע, ואז הארנה נוצחת */
        const val DEATH_HOLD_SECONDS = 1.0f

        const val HIT_FLASH_SECONDS = 0.22f
        const val CUT_FLASH_SECONDS = 0.22f

        /** כמה מהרדיוס של הנקודה נחשב פגיעה */
        const val HIT_SCALE = 0.95f

        /** אם הנגן מפגר יותר מזה אחרי השעון הפנימי, ממשיכים לפי השעון (הנגן נתקע) */
        const val STALL_MS = 600f

        /** סרטון נחשב גמור קצת לפני הפריים האחרון (פריים אחד) */
        const val END_SLACK_MS = 42f
    }
}

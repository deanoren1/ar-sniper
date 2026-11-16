package com.arsniper.game.engine

import kotlin.math.hypot
import kotlin.random.Random

/** סרטוני בוס הקרח (בסדר הזה גם ברשימת ההשמעה של הנגן). משך במילישניות, לפי מספר הפריימים ב-24fps */
enum class BossClip(val durationMs: Float, val loop: Boolean = false) {
    Intro(6750f),
    Breath(5042f, loop = true),
    Prepare(5042f),
    Attack1(4042f),
    Attack2(4042f),
    Attack3(4042f),
    Attack4(4042f),
    Attack5(4042f),
    HitHead(2000f),
    HitRightShoulder(2000f),
    HitLeftShoulder(2000f),
    HitRightHand(2000f),
    HitLeftHand(2000f),
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

/**
 * חמש המתקפות של הבוס. impactMs = רגע הפגיעה בסרטון (נמדד מהפריים שבו המכה מגיעה למסך), ורק אז השחקן נפגע.
 * כל מתקפה פוגעת באזור אחר על המסך (zone), ואזור הסכנה מופיע רק רגע לפני הפגיעה: מזיזים את הכוונת החוצה בג'ויסטיק.
 * פגיעה מורידה לב אחד דרך מערכת הלבבות הקיימת (בלי ערכי נזק חדשים); ההבדל ביניהן גם בעוצמת הרעידה,
 * בזמן ההמתנה עד שאותה מתקפה חוזרת, ובתנאי: מכות הקרקע הכבדות (3, 4) רק אחרי שנשבר יהלום ראשון.
 * seamless = הסרטון ממשיך ישר מסוף ההכנה (תקריב); באחרות יש קפיצה שמוסתרת בהבזק קצר.
 */
enum class BossAttack(
    val clip: BossClip,
    val impactMs: Float,
    val cooldownSeconds: Float,
    val minBroken: Int,
    val shake: Float,
    val seamless: Boolean,
    val zone: DangerZone
) {
    /** Attack1: חבטת טופר אל המצלמה */
    ClawSwipe(BossClip.Attack1, 2083f, 6f, 0, 0.75f, true, DangerZone.Box(0.12f, 0.30f, 0.60f, 1.0f)),

    /** Attack2: שריטת טופר עם להב קרח אלכסוני */
    FrostSlash(BossClip.Attack2, 1667f, 6f, 0, 0.7f, true, DangerZone.Band(0.05f, 0.95f, 0.95f, 0.12f, 0.14f)),

    /** Attack3: הלמות שתי האגרופים בקרקע, גל ברקים */
    ThunderSlam(BossClip.Attack3, 1750f, 9f, 1, 0.9f, false, DangerZone.Box(0f, 0.56f, 1f, 1f)),

    /** Attack4: קפיצה ונחיתה, גל קרח לאורך הרצפה */
    GroundPound(BossClip.Attack4, 1750f, 9f, 1, 0.85f, false, DangerZone.Box(0.32f, 0f, 0.68f, 1f)),

    /** Attack5: כדור קרח טעון שנזרק אל המסך */
    FrostOrb(BossClip.Attack5, 2625f, 7f, 0, 0.8f, false, DangerZone.Circle(0.45f, 0.55f, 0.26f))
}

/**
 * עולם הקרח: שלב הבוס של ארנת הקרח (גל 6). הבוס הוא סרטונים: עמידה ונשימה, הכנה למתקפה, 5 מתקפות,
 * תגובות פגיעה לפי מקום הפגיעה, ומוות. Kotlin טהור: הנגן והציור ב-IceRealmLayer, החיבור למשחק ב-GameScreen.
 *
 * מכונת מצבים אחת (stage) היא מקור האמת:
 *  Intro (סרטון הכניסה) → Idle → PreparingAttack → Attacking → Idle
 *  Idle → HitReaction → Idle (או ישר להכנה, אם הגיע זמן מתקפה: התגובה לא נקטעת, אבל גם לא חוסמת מתקפות לעד)
 *  פגיעה בהכנה למתקפה לא עוצרת אותה (היהלום הזהוב = נזק כפול). מתחמקים מהמתקפה עם הכוונת (ג'ויסטיק)
 *  כל מצב → Dead כשהחיים נגמרים. מ-Dead אין חזרה.
 *
 * חיים: 5 יהלומים (ראש, 2 כתפיים, 2 ידיים) × 3 = 15, כמו קודם. פגיעה = 1, ביהלום הזהוב בהכנה = 2.
 * הבוס פגיע רק בעמידה ובהכנה (שם יש מעקב מדויק של הנקודות); במתקפה ובתגובה הירייה נהדפת.
 * פגיעה בגוף (לא ביהלום) נהדפת בלי נזק, כמו קודם (אין לה סרטון תגובה ייעודי).
 *
 * הזמן בתוך סרטון: לפי מיקום הנגן כשהוא מסונכרן (syncVideo), ואחרת לפי שעון פנימי, כך שאם הווידאו נתקע
 * המשחק לא נתקע איתו.
 */
class IceRealm {

    enum class Stage { Off, Intro, Idle, PreparingAttack, Attacking, HitReaction, Dead }

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
    private var lastAttack: BossAttack? = null
    private val attackReadyAt = HashMap<BossAttack, Float>()

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

    private fun warnMs() = if (enraged) WARN_MS_ENRAGED else WARN_MS

    private var attackIn = FIRST_ATTACK_SECONDS
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

    /** נשארו 2 יהלומים או פחות: הבוס כועס (הכנה מהירה יותר, פחות מנוחה בין מתקפות) */
    val enraged: Boolean get() = diamonds.count { it.alive } <= 2

    /** התקדמות ההכנה 0..1 (לטבעת שמתכווצת סביב היהלום הדלוק) */
    val prepareFraction: Float
        get() = if (stage == Stage.PreparingAttack) (clipMs / BossClip.Prepare.durationMs).coerceIn(0f, 1f) else 0f

    fun begin() {
        if (stage != Stage.Off) return
        reset()
        go(Stage.Intro)
        play(BossClip.Intro)
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
        lastAttack = null
        attackReadyAt.clear()
        exposed = -1
        hitFlash = 0f
        cutFlash = 0f
        dodgeFlash = 0f
        attackIn = FIRST_ATTACK_SECONDS
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

    private fun play(c: BossClip, speed: Float = 1f) {
        clip = c
        clipSpeed = speed
        clipToken += 1
        clipMs = 0f
        ownMs = 0f
        videoEnded = false
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
        cutFlash = (cutFlash - dt).coerceAtLeast(0f)
        hitThisTick = false
        advanceClock(dt)
        when (stage) {
            Stage.Intro -> if (clipDone()) enterIdle()
            Stage.Idle -> {
                attackIn -= dt
                if (attackIn <= 0f) startPrepare()
            }
            Stage.PreparingAttack -> if (clipDone()) startAttack()
            Stage.Attacking -> {
                val a = attack
                if (a == null) {
                    enterIdle()
                } else {
                    if (!impactDone && clipMs >= a.impactMs) {
                        impactDone = true
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
                        lastAttack = a
                        attack = null
                        attackIn = nextCooldown()
                        enterIdle()
                    }
                }
            }
            Stage.HitReaction -> {
                attackIn -= dt
                if (clipDone()) {
                    if (attackIn <= 0f) startPrepare() else enterIdle()
                }
            }
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

    private fun enterIdle() {
        go(Stage.Idle)
        exposed = -1
        play(BossClip.Breath)
    }

    private fun startPrepare() {
        val live = diamonds.indices.filter { diamonds[it].alive }
        exposed = if (live.isEmpty()) -1 else live[Random.nextInt(live.size)]
        go(Stage.PreparingAttack)
        play(BossClip.Prepare, if (enraged) PREPARE_SPEED_ENRAGED else 1f)
    }

    private fun startAttack() {
        val a = pickAttack()
        exposed = -1
        attack = a
        impactDone = false
        attackReadyAt[a] = time + a.cooldownSeconds
        if (!a.seamless) cutFlash = CUT_FLASH_SECONDS
        go(Stage.Attacking)
        play(a.clip)
    }

    /** בחירת מתקפה: מהזמינות (תנאי + זמן המתנה), לא אותה מתקפה פעמיים ברצף */
    private fun pickAttack(): BossAttack {
        val all = BossAttack.entries
        val allowed = all.filter { broken >= it.minBroken }
        val ready = allowed.filter { it != lastAttack && time >= (attackReadyAt[it] ?: 0f) }
        val pool = ready.ifEmpty { allowed.filter { it != lastAttack } }.ifEmpty { allowed }.ifEmpty { all }
        return pool[Random.nextInt(pool.size)]
    }

    private fun nextCooldown() = (if (enraged) 2.0f else 3.0f) + Random.nextFloat() * 1.0f

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
        // תגובה לפי מקום הפגיעה: מהעמידה, או כשהמתקפה בוטלה בהכנה
        // תגובת פגיעה רק כשהבוס עומד; בהכנה למתקפה הפגיעה לא עוצרת אותו
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
        const val FIRST_ATTACK_SECONDS = 2.5f

        /** מהירות סרטון ההכנה כשהבוס כועס (מתקפה מגיעה מהר יותר, פחות זמן לבטל) */
        const val PREPARE_SPEED_ENRAGED = 1.3f

        /** כמה זמן לפני הפגיעה מופיע אזור הסכנה (קשה: מספיק רק למי שזז מהר), ובזעם */
        const val WARN_MS = 900f
        const val WARN_MS_ENRAGED = 700f

        const val DODGE_FLASH_SECONDS = 0.8f

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

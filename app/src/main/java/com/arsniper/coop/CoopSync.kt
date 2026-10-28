package com.arsniper.coop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arsniper.game.engine.ArenaDirector
import com.arsniper.game.engine.EnemyFire
import com.arsniper.game.model.Enemy
import com.arsniper.game.model.Vec2
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * סנכרון משחק משותף. המארח מריץ את כל המשחק (מפלצות, קליעים, לבבות) ושולח "תמונת מצב" כמה פעמים בשנייה.
 * האורח רק מציג אותה (עם החלקה בין שתי תמונות מצב, כדי שהתנועה לא תקפוץ), ושולח למארח את מיקום הכוונת שלו
 * ואת הירייה/הקשה שלו. המארח מפעיל אותן כאילו ירה בעצמו.
 *
 * הודעות (טקסט JSON, שדה t):
 *  start  מארח ← אורח: מתחילים (style = מספר ארנה, key = מפתח התמונה)
 *  gready אורח ← מארח: המסך של האורח מוכן
 *  snap   מארח ← אורח: תמונת מצב
 *  aim    אורח ← מארח: מיקום הכוונת
 *  fire   אורח ← מארח: ירייה בנקודה עם נזק
 *  tap    אורח ← מארח: הקשה על קליע של מפלצת
 *  end    מארח ← אורח: המארח יצא מהמשחק
 */
object CoopSync {
    /** הלובי לא מנתק את החיבור כשעוברים למשחק */
    @Volatile var keepConnection = false

    class StartInfo(val style: Int, val key: String)

    /** אורח: המארח לחץ START, והלובי צריך לעבור למשחק */
    var pendingStart by mutableStateOf<StartInfo?>(null)

    /** מארח: האורח מוכן (מסך המשחק שלו נבנה) */
    @Volatile var guestReady = false

    /** אורח: המארח יצא מהמשחק */
    var hostEnded by mutableStateOf(false)
        private set

    // ---------------------------------------------------------------- קלט האורח אצל המארח

    class GuestInput(val tap: Boolean, val x: Float, val y: Float, val damage: Int)

    val inbox = ConcurrentLinkedQueue<GuestInput>()

    /** הכוונת של השותף (קואורדינטות עולם) ומתי התקבלה. null = עוד לא התקבלה */
    @Volatile var partnerAim: Vec2? = null
    @Volatile var partnerAimAt = 0L

    // ---------------------------------------------------------------- תמונת מצב

    class SnapEnemy(
        val id: Int, val x: Float, val y: Float, val r: Float, val alive: Boolean,
        val kind: Int, val state: Int, val spotId: Int,
        val hx: Float, val hy: Float, val px: Float, val py: Float,
        val exposure: Float, val timer: Float, val dir: Float, val phase: Float, val dirY: Float,
        val maxHp: Int, val hp: Int, val hurtAge: Float
    )

    class SnapShot(
        val id: Int, val x: Float, val y: Float, val kind: Int,
        val born: Float, val flight: Float, val baseRadius: Float, val maxHp: Int, val hp: Int, val harmless: Boolean
    )

    class SnapView(val x: Float, val y: Float, val radius: Float, val progress: Float, val enemyId: Int)

    class Snapshot(
        val seq: Int,
        val recvNanos: Long,
        val phase: Int,
        val wave: Int,
        val hearts: Int,
        val left: Int,
        val bossHp: Int,
        val bossMax: Int,
        val bossAlive: Boolean,
        val bossDying: Boolean,
        val healed: Boolean,
        val score: Int,
        val fireTime: Float,
        val enemies: List<SnapEnemy>,
        val shots: List<SnapShot>,
        val views: List<SnapView>,
        val apIds: Set<Int>,
        val apProgress: Map<Int, Float>,
        val bossSince: Float,
        val bossStunned: Boolean,
        val bossEnraged: Boolean,
        val hostX: Float,
        val hostY: Float
    )

    private val buffer = ArrayList<Snapshot>()
    private val lock = Any()
    @Volatile private var lastSeq = -1

    fun resetGame() {
        synchronized(lock) { buffer.clear() }
        lastSeq = -1
        inbox.clear()
        partnerAim = null
        guestReady = false
        hostEnded = false
    }

    private fun r4(v: Float): Double = (v * 10000f).roundToInt() / 10000.0

    // ---------------------------------------------------------------- שליחה (מארח)

    fun sendSnapshot(
        seq: Int,
        enemies: List<Enemy>,
        fire: EnemyFire,
        director: ArenaDirector,
        crosshair: Vec2,
        score: Int,
        nowSeconds: Double,
        apIds: Set<Int>,
        apProgress: Map<Int, Float>,
        bossSince: Float,
        bossStunned: Boolean,
        bossEnraged: Boolean
    ) {
        val o = JSONObject()
        o.put("t", "snap").put("n", seq)
        o.put("ph", director.phase.ordinal).put("w", director.waveIndex).put("h", director.hearts)
        o.put("l", director.leftInWave).put("bh", director.bossHp).put("bm", director.bossMax)
        o.put("ba", if (director.bossAlive) 1 else 0).put("bd", if (director.bossDying) 1 else 0)
        o.put("hl", if (director.healedOnClear) 1 else 0).put("sc", score)
        o.put("ft", r4(fire.time))
        o.put("cx", r4(crosshair.x)).put("cy", r4(crosshair.y))
        val e = JSONArray()
        for (en in enemies) {
            val a = JSONArray()
            a.put(en.id).put(r4(en.position.x)).put(r4(en.position.y)).put(r4(en.radius))
            a.put(if (en.isAlive) 1 else 0).put(en.kind.ordinal).put(en.state.ordinal).put(en.spot?.id ?: -1)
            a.put(r4(en.hidePos.x)).put(r4(en.hidePos.y)).put(r4(en.peekPos.x)).put(r4(en.peekPos.y))
            a.put(r4(en.exposure)).put(r4(en.timer)).put(r4(en.direction)).put(r4(en.phase)).put(r4(en.dirY))
            a.put(en.maxHp).put(en.hp).put(r4((nowSeconds - en.hurtAt).toFloat().coerceIn(-1f, 100f)))
            e.put(a)
        }
        o.put("e", e)
        val s = JSONArray()
        for (sh in fire.shots) {
            val a = JSONArray()
            a.put(sh.id).put(r4(sh.x)).put(r4(sh.y)).put(sh.kind.ordinal)
            a.put(r4(sh.born)).put(r4(sh.flight)).put(r4(sh.baseRadius)).put(sh.maxHp).put(sh.hp)
            a.put(if (sh.harmless) 1 else 0)
            s.put(a)
        }
        o.put("s", s)
        val v = JSONArray()
        for (tv in fire.telegraphViews) {
            v.put(JSONArray().put(r4(tv.x)).put(r4(tv.y)).put(r4(tv.radius)).put(r4(tv.progress)).put(tv.enemyId))
        }
        o.put("v", v)
        val ap = JSONArray()
        for (id in apIds) ap.put(JSONArray().put(id).put(r4(apProgress[id] ?: 0f)))
        o.put("ap", ap)
        o.put("bs", r4(bossSince)).put("bst", if (bossStunned) 1 else 0).put("ben", if (bossEnraged) 1 else 0)
        CoopClient.sendText(o.toString())
    }

    fun sendEnd() {
        CoopClient.sendText("{\"t\":\"end\"}")
    }

    // ---------------------------------------------------------------- שליחה (אורח)

    fun sendAim(x: Float, y: Float) {
        CoopClient.sendText(JSONObject().put("t", "aim").put("x", r4(x)).put("y", r4(y)).toString())
    }

    fun sendFire(x: Float, y: Float, damage: Int) {
        CoopClient.sendText(JSONObject().put("t", "fire").put("x", r4(x)).put("y", r4(y)).put("d", damage).toString())
    }

    fun sendTap(x: Float, y: Float) {
        CoopClient.sendText(JSONObject().put("t", "tap").put("x", r4(x)).put("y", r4(y)).toString())
    }

    fun sendGuestReady() {
        CoopClient.sendText("{\"t\":\"gready\"}")
    }

    fun sendStart(style: Int, key: String) {
        CoopClient.sendText(JSONObject().put("t", "start").put("style", style).put("key", key).toString())
    }

    // ---------------------------------------------------------------- קבלה

    /** הודעת "start" בלובי (מנותבת מ-CoopTransfer) */
    fun onStart(json: JSONObject) {
        if (CoopClient.role == "host") return
        pendingStart = StartInfo(json.optInt("style"), json.optString("key"))
    }

    /** מאזין המשחק: מחליף את מאזין הלובי כל עוד מסך המשחק פתוח */
    fun attachGame() {
        CoopClient.onBinary = null
        CoopClient.onText = { onGameText(it) }
    }

    private fun onGameText(text: String) {
        if (!text.startsWith("{")) return
        try {
            val json = JSONObject(text)
            when (json.optString("t")) {
                "snap" -> if (CoopClient.role != "host") onSnapshot(json)
                "aim" -> if (CoopClient.role == "host") {
                    partnerAim = Vec2(json.getDouble("x").toFloat(), json.getDouble("y").toFloat())
                    partnerAimAt = System.nanoTime()
                }
                "fire" -> if (CoopClient.role == "host") {
                    inbox.add(GuestInput(false, json.getDouble("x").toFloat(), json.getDouble("y").toFloat(), json.optInt("d", 1)))
                }
                "tap" -> if (CoopClient.role == "host") {
                    inbox.add(GuestInput(true, json.getDouble("x").toFloat(), json.getDouble("y").toFloat(), 0))
                }
                "gready" -> if (CoopClient.role == "host") guestReady = true
                "end" -> if (CoopClient.role != "host") hostEnded = true
            }
        } catch (_: Exception) {
        }
    }

    private fun f(a: JSONArray, i: Int): Float = a.getDouble(i).toFloat()

    private fun onSnapshot(j: JSONObject) {
        val seq = j.optInt("n")
        if (seq <= lastSeq && lastSeq - seq < 1000) return
        lastSeq = seq
        val enemies = ArrayList<SnapEnemy>()
        val ea = j.getJSONArray("e")
        for (i in 0 until ea.length()) {
            val a = ea.getJSONArray(i)
            enemies.add(
                SnapEnemy(
                    a.getInt(0), f(a, 1), f(a, 2), f(a, 3), a.getInt(4) == 1, a.getInt(5), a.getInt(6), a.getInt(7),
                    f(a, 8), f(a, 9), f(a, 10), f(a, 11), f(a, 12), f(a, 13), f(a, 14), f(a, 15), f(a, 16),
                    a.getInt(17), a.getInt(18), f(a, 19)
                )
            )
        }
        val shots = ArrayList<SnapShot>()
        val sa = j.getJSONArray("s")
        for (i in 0 until sa.length()) {
            val a = sa.getJSONArray(i)
            shots.add(SnapShot(a.getInt(0), f(a, 1), f(a, 2), a.getInt(3), f(a, 4), f(a, 5), f(a, 6), a.getInt(7), a.getInt(8), a.getInt(9) == 1))
        }
        val views = ArrayList<SnapView>()
        val va = j.getJSONArray("v")
        for (i in 0 until va.length()) {
            val a = va.getJSONArray(i)
            views.add(SnapView(f(a, 0), f(a, 1), f(a, 2), f(a, 3), a.getInt(4)))
        }
        val apIds = HashSet<Int>()
        val apProgress = HashMap<Int, Float>()
        val apa = j.getJSONArray("ap")
        for (i in 0 until apa.length()) {
            val a = apa.getJSONArray(i)
            apIds.add(a.getInt(0))
            apProgress[a.getInt(0)] = f(a, 1)
        }
        val snap = Snapshot(
            seq, System.nanoTime(), j.getInt("ph"), j.getInt("w"), j.getInt("h"), j.getInt("l"),
            j.getInt("bh"), j.getInt("bm"), j.getInt("ba") == 1, j.getInt("bd") == 1, j.getInt("hl") == 1,
            j.getInt("sc"), j.getDouble("ft").toFloat(), enemies, shots, views, apIds, apProgress,
            j.getDouble("bs").toFloat(), j.getInt("bst") == 1, j.getInt("ben") == 1,
            j.getDouble("cx").toFloat(), j.getDouble("cy").toFloat()
        )
        synchronized(lock) {
            buffer.add(snap)
            while (buffer.size > 8) buffer.removeAt(0)
        }
    }

    // ---------------------------------------------------------------- תצוגה (אורח)

    /** מה האורח מציג עכשיו: תמונת המצב האחרונה (לבבות, גל...) והמפלצות מוחלקות בין שתי תמונות */
    class View(val latest: Snapshot, val enemies: List<SnapEnemy>, val fireTime: Float)

    private const val DELAY_NANOS = 120_000_000L

    fun view(nowNanos: Long): View? {
        val list: List<Snapshot> = synchronized(lock) { ArrayList(buffer) }
        if (list.isEmpty()) return null
        val latest = list.last()
        val target = nowNanos - DELAY_NANOS
        var a: Snapshot? = null
        var b: Snapshot? = null
        for (s in list) {
            if (s.recvNanos <= target) a = s else { b = s; break }
        }
        val enemies: List<SnapEnemy> = if (a == null || b == null) {
            (b ?: a ?: latest).enemies
        } else {
            val alpha = ((target - a.recvNanos).toFloat() / (b.recvNanos - a.recvNanos).coerceAtLeast(1L)).coerceIn(0f, 1f)
            val before = a.enemies.associateBy { it.id }
            b.enemies.map { e ->
                val p = before[e.id]
                if (p == null || abs(e.x - p.x) > 0.3f || abs(e.y - p.y) > 0.3f) {
                    e
                } else {
                    SnapEnemy(
                        e.id, p.x + (e.x - p.x) * alpha, p.y + (e.y - p.y) * alpha, p.r + (e.r - p.r) * alpha,
                        e.alive, e.kind, e.state, e.spotId,
                        e.hx, e.hy, e.px, e.py,
                        p.exposure + (e.exposure - p.exposure) * alpha, e.timer, e.dir,
                        p.phase + (e.phase - p.phase) * alpha, e.dirY, e.maxHp, e.hp, e.hurtAge
                    )
                }
            }
        }
        val elapsed = ((nowNanos - latest.recvNanos) / 1e9f).coerceIn(0f, 0.2f)
        return View(latest, enemies, latest.fireTime + elapsed)
    }
}

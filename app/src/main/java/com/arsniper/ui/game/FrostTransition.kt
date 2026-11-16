package com.arsniper.ui.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.caverock.androidsvg.SVG
import com.arsniper.game.engine.BossFrame
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * מעבר הקרח (כ-2 שניות) מהארנה של השחקן אל סרטון הכניסה של הבוס.
 * כל השכבות הן הנכסים המקוריים מ-VFX/Frozen_Tundra/Assets (נארזים כ-assets כמו שהם, בלי העתקה):
 * 6 ה-SVG מרונדרים פעם אחת לתמונות שקופות, וה-PNG נטענים כמו שהם. הנכסים עצמם סטטיים:
 * כל התנועה (קנה מידה, סיבוב, הזזה, שקיפות) נעשית כאן בזמן ריצה.
 * התמונה של השחקן לא משתנה ונשארת גלויה מתחת עד הסוף; האפקטים לא תלויים בתמונה.
 */
object FrostTransition {

    /** אורך המעבר בשניות (בסופו עוברים לפריים הראשון של סרטון הכניסה) */
    const val SECONDS = 2.0f

    /** מרכז המערבולת בפריים הראשון של סרטון הכניסה (פיקסלים של הווידאו), כדי שהחיתוך יתאים */
    private const val SWIRL_VX = 460f
    private const val SWIRL_VY = 236f

    /** עוצמת ההבזק בחיתוך (מבוקר, לא לבן מלא). אותה עוצמה דועכת מעל תחילת סרטון הכניסה */
    const val FINAL_FLASH = 0.3f

    /** הצבע הממוצע של הפריים הראשון של סרטון הכניסה */
    private const val INTRO_BLUE = 0xFF2686D4.toInt()

    // ---- נכסים ----
    private class Assets(
        val vortex: Bitmap, val crack: Bitmap, val ring: Bitmap, val shards: Bitmap, val icicles: Bitmap, val flakes: Bitmap,
        val twirl: Bitmap, val trace: Bitmap, val star: Bitmap, val flare: Bitmap, val light: Bitmap, val circle: Bitmap, val fog: Bitmap
    )

    @Volatile
    private var assets: Assets? = null

    val ready: Boolean get() = assets != null

    /** טעינה מראש (ברקע): ה-SVG מרונדרים פעם אחת. נקרא כשנכנסים לארנת הקרח */
    fun preload(context: Context) {
        if (assets != null) return
        val am = context.applicationContext.assets
        fun svg(name: String, px: Int): Bitmap {
            val s = am.open(name).use { SVG.getFromInputStream(it) }
            s.setDocumentWidth(px.toFloat())
            s.setDocumentHeight(px.toFloat())
            val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            s.renderToCanvas(Canvas(b))
            return b
        }
        fun png(name: String): Bitmap = am.open(name).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })!!
        }
        val a = runCatching {
            Assets(
                vortex = svg("blizzard-vortex.svg", 768),
                crack = svg("frost-crack-overlay.svg", 768),
                ring = svg("frost-nova-ring.svg", 768),
                shards = svg("ice-shards-burst.svg", 768),
                icicles = svg("icicle-fall-overlay.svg", 512),
                flakes = svg("snowflake-particles.svg", 512),
                twirl = png("particle-twirl-01.png"),
                trace = png("particle-trace-01.png"),
                star = png("particle-star-01.png"),
                flare = png("particle-flare-01.png"),
                light = png("particle-light-01.png"),
                circle = png("particle-circle-01.png"),
                fog = png("thick-fog-01.png")
            )
        }.getOrNull()
        assets = a
    }

    /** משחרר את התמונות (יציאה מהמשחק) */
    fun release() {
        assets = null
    }

    // ---- חלקיקים: מערכים קבועים, בלי הקצאות בזמן הציור ----
    private const val N = 90

    /** פתיתי השלג בתוך snowflake-particles.svg (מרכז וחצי גודל, ביחידות של 1024) */
    private val FLAKE_CX = floatArrayOf(180f, 760f, 510f, 230f, 830f)
    private val FLAKE_CY = floatArrayOf(260f, 185f, 555f, 775f, 755f)
    private val FLAKE_R = floatArrayOf(100f, 85f, 135f, 95f, 115f)

    private val pAngle = FloatArray(N)
    private val pRad = FloatArray(N)
    private val pSpawn = FloatArray(N)
    private val pIn = FloatArray(N)
    private val pDur = FloatArray(N)
    private val pSize = FloatArray(N)
    private val pSpin = FloatArray(N)
    private val pKind = IntArray(N)

    init {
        // אקראי קבוע (אותו מעבר בכל פעם), כדי שהחלקיקים יהיו פונקציה של הזמן בלבד
        var seed = 0x2F6E2B1
        fun rnd(): Float {
            seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
            return ((seed ushr 8) and 0xFFFF) / 65535f
        }
        for (i in 0 until N) {
            pAngle[i] = rnd() * 2f * PI.toFloat()
            pRad[i] = 0.45f + rnd() * 0.65f
            pSpawn[i] = if (i < N / 4) rnd() * 0.3f else 0.2f + rnd() * 0.5f
            pIn[i] = 0.35f + rnd() * 0.35f
            pDur[i] = 0.5f + rnd() * 0.35f
            pSize[i] = 0.018f + rnd() * 0.03f
            pSpin[i] = (rnd() - 0.5f) * 4f
            val k = rnd()
            pKind[i] = when {
                k < 0.45f -> (rnd() * 5f).toInt().coerceIn(0, 4) // פתית
                k < 0.75f -> 5                                    // ניצוץ (star)
                else -> 6                                         // פס (trace)
            }
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val add = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val src = Rect()
    private val dst = RectF()
    private var shaderW = -1f
    private var shaderH = -1f
    private var vignette: RadialGradient? = null

    private fun clamp01(x: Float) = min(1f, max(0f, x))
    private fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
    private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
    private fun easeIn(x: Float) = x * x
    private fun smooth(x: Float) = x * x * (3f - 2f * x)

    /** תמונה שלמה, ממורכזת ב-(cx, cy), בגודל size, בסיבוב deg */
    private fun Canvas.sprite(b: Bitmap, cx: Float, cy: Float, w: Float, h: Float, deg: Float, alpha: Float, p: Paint) {
        if (alpha <= 0.004f || w < 1f) return
        p.alpha = (255 * clamp01(alpha)).toInt()
        save()
        translate(cx, cy)
        if (deg != 0f) rotate(deg)
        dst.set(-w / 2f, -h / 2f, w / 2f, h / 2f)
        drawBitmap(b, null, dst, p)
        restore()
    }

    /**
     * מצייר את המעבר בזמן t (שניות, 0..SECONDS) על canvas בגודל w×h. מעל התמונה, מתחת לכוונת ולממשק.
     */
    fun draw(c: Canvas, w: Float, h: Float, t: Float) {
        val a = assets ?: return drawFallback(c, w, h, t)
        val r = BossFrame.rect(w / h)
        val cx = (r[0] + SWIRL_VX / BossFrame.VIDEO_W * r[2]) * w
        val cy = (r[1] + SWIRL_VY / BossFrame.VIDEO_H * r[3]) * h
        val diag = hypot(w, h)

        // 1) גוון כחול וחשכת קצוות (0–0.35), התמונה ממשיכה להיראות
        val tint = smooth(seg(t, 0f, 0.35f))
        fill.shader = null
        fill.color = Color.argb((255 * 0.22f * tint).toInt(), 0x3F, 0xA0, 0xFF)
        c.drawRect(0f, 0f, w, h, fill)
        if (shaderW != w || shaderH != h) {
            vignette = RadialGradient(w / 2f, h / 2f, diag * 0.55f, intArrayOf(0x00000000, 0xC0082048.toInt()), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
            shaderW = w
            shaderH = h
        }
        fill.shader = vignette
        fill.alpha = (255 * tint).toInt()
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null

        // ערפל קר בקצוות התחתון והעליון (thick-fog), נע לאט
        val fogA = 0.35f * smooth(seg(t, 0.05f, 0.6f))
        val fw = w * 1.3f
        val fh = fw * a.fog.height / a.fog.width
        val drift = t * w * 0.05f
        c.sprite(a.fog, w / 2f - drift, h - fh * 0.28f, fw, fh, 0f, fogA, paint)
        c.sprite(a.fog, w / 2f + drift, fh * 0.18f, fw, fh, 180f, fogA * 0.6f, paint)

        // 2) סדקי כפור (0.2–0.8): אחד גדול מהמרכז ושניים בפינות, כמו קרח על זכוכית
        val ck = easeOut(seg(t, 0.2f, 0.8f))
        val crackFade = 1f - 0.6f * seg(t, 1.5f, 1.9f)
        if (ck > 0f) {
            val s = h * (0.45f + 0.95f * ck)
            c.sprite(a.crack, cx, cy, s, s, 12f, 0.75f * ck * crackFade, paint)
            val ck2 = easeOut(seg(t, 0.3f, 0.8f))
            val s2 = h * 0.75f * (0.4f + 0.6f * ck2)
            c.sprite(a.crack, 0f, h, s2, s2, -30f, 0.7f * ck2 * crackFade, paint)
            c.sprite(a.crack, w, 0f, s2, s2, 150f, 0.7f * ck2 * crackFade, paint)
        }

        // נטיפי קרח יורדים מהקצה העליון (0.25–0.6) ועולים בסוף
        val ic = easeOut(seg(t, 0.25f, 0.6f)) * (1f - smooth(seg(t, 1.6f, 1.9f)))
        if (ic > 0f) {
            val tile = w / 3f
            for (k in 0 until 3) {
                c.sprite(a.icicles, tile * (k + 0.5f), -tile / 2f + tile * ic, tile, tile, 0f, 0.92f * ic, paint)
            }
        }

        // 3) מערבולת וטבעת (0.45–1.65), מתרחבות מהמרכז
        val vx = seg(t, 0.45f, 1.65f)
        if (vx > 0f) {
            val grow = easeIn(vx)
            val vs = h * (0.18f + 2.9f * grow)
            val spin = -(t - 0.45f) * (220f + 480f * vx)
            val va = 0.9f * smooth(seg(t, 0.45f, 0.75f))
            // דיסקה רכה מתחת (particle-circle), ואור במרכז (particle-light)
            c.sprite(a.circle, cx, cy, vs * 0.9f, vs * 0.9f, 0f, 0.55f * va, add)
            c.sprite(a.vortex, cx, cy, vs, vs, spin, va, paint)
            c.sprite(a.twirl, cx, cy, vs * 1.1f, vs * 1.1f, spin * 1.4f, 0.6f * va, add)
            c.sprite(a.twirl, cx, cy, vs * 0.8f, vs * 0.8f, -spin * 0.9f + 90f, 0.5f * va, add)
            c.sprite(a.light, cx, cy, vs * 0.7f, vs * 0.7f, 0f, 0.7f * va, add)
            val ring = easeOut(vx)
            val rs = h * (0.12f + 1.9f * ring)
            val ra = smooth(seg(t, 0.45f, 0.65f)) * (1f - 0.5f * seg(t, 1.4f, 1.65f))
            c.sprite(a.ring, cx, cy, rs, rs, t * 40f, ra, paint)
            val ring2 = easeOut(seg(t, 0.7f, 1.65f))
            if (ring2 > 0f) {
                val rs2 = h * (0.1f + 1.3f * ring2)
                c.sprite(a.ring, cx, cy, rs2, rs2, -t * 60f, 0.7f * ra, paint)
            }
        }

        // 4) חלקיקי שלג: מעט בהתחלה, ואז נשאבים בספירלה אל המרכז (0.35–1.3)
        drawParticles(c, a, w, h, cx, cy, diag, t)

        // 5) רסיסי קרח והבזק אנרגיה (1.35–1.9)
        val sh = seg(t, 1.35f, 1.9f)
        if (sh > 0f) {
            val e = easeOut(sh)
            val ss = h * (0.3f + 2.4f * e)
            val sa = if (sh < 0.15f) sh / 0.15f else 1f - 0.7f * seg(t, 1.65f, 1.9f)
            c.sprite(a.shards, cx, cy, ss, ss, 8f * e, sa, paint)
            c.sprite(a.shards, cx, cy, ss * 0.7f, ss * 0.7f, 33f - 12f * e, 0.8f * sa, paint)
            val fl = 1f - seg(t, 1.35f, 1.75f)
            c.sprite(a.flare, cx, cy, w * (0.6f + 1.4f * e), w * (0.6f + 1.4f * e), 0f, 0.9f * fl, add)
            c.sprite(a.star, cx, cy, h * (0.3f + 0.6f * e), h * (0.3f + 0.6f * e), 45f * e, 0.9f * fl, add)
        }

        // 6) סוף (1.72–2.0): הכחול של סרטון הכניסה מתפשט מהמרכז, ואז הבזק מבוקר (לא לבן מלא) לחיתוך
        val fillK = smooth(seg(t, 1.72f, 1.98f))
        if (fillK > 0f) {
            val rad = diag * (0.05f + 1.0f * fillK)
            fill.shader = RadialGradientCache.get(cx, cy, rad)
            fill.alpha = (255 * fillK).toInt()
            c.drawRect(0f, 0f, w, h, fill)
            fill.shader = null
        }
        val flash = FINAL_FLASH * smooth(seg(t, 1.82f, 2.0f))
        if (flash > 0f) {
            fill.color = Color.argb((255 * flash).toInt(), 0xD8, 0xF6, 0xFF)
            c.drawRect(0f, 0f, w, h, fill)
        }
    }

    private fun drawParticles(c: Canvas, a: Assets, w: Float, h: Float, cx: Float, cy: Float, diag: Float, t: Float) {
        for (i in 0 until N) {
            if (t < pSpawn[i]) continue
            val u = seg(t, pIn[i], pIn[i] + pDur[i])
            if (u >= 1f) continue
            val pull = easeIn(u)
            val ang = pAngle[i] + pull * 1.8f
            val rr = diag * 0.5f * pRad[i] * (1f - pull)
            // לפני השאיבה: נסחפים קצת למטה
            val fall = (min(t, pIn[i]) - pSpawn[i]) * h * 0.06f
            val x = cx + cos(ang) * rr
            val y = cy + sin(ang) * rr + fall * (1f - pull)
            val alpha = clamp01((t - pSpawn[i]) / 0.25f) * (1f - smooth(seg(u, 0.8f, 1f)))
            val size = h * pSize[i] * (1f - 0.55f * pull) * 2.2f
            when (val k = pKind[i]) {
                in 0..4 -> {
                    val sc = a.flakes.width / 1024f
                    src.set(
                        ((FLAKE_CX[k] - FLAKE_R[k]) * sc).toInt(), ((FLAKE_CY[k] - FLAKE_R[k]) * sc).toInt(),
                        ((FLAKE_CX[k] + FLAKE_R[k]) * sc).toInt(), ((FLAKE_CY[k] + FLAKE_R[k]) * sc).toInt()
                    )
                    paint.alpha = (255 * 0.9f * alpha).toInt()
                    c.save()
                    c.translate(x, y)
                    c.rotate(t * 60f * pSpin[i])
                    dst.set(-size / 2f, -size / 2f, size / 2f, size / 2f)
                    c.drawBitmap(a.flakes, src, dst, paint)
                    c.restore()
                }
                5 -> c.sprite(a.star, x, y, size * 1.4f, size * 1.4f, 0f, 0.85f * alpha, add)
                else -> {
                    // פס שמכוון אל המרכז, מתארך כשהוא מאיץ
                    val deg = atan2(cy - y, cx - x) * 180f / PI.toFloat() + 90f
                    val len = size * (2f + 5f * pull)
                    c.sprite(a.trace, x, y, size * 0.9f, len, deg, 0.75f * alpha * smooth(seg(t, 0.35f, 0.6f)), add)
                }
            }
        }
    }

    /** בלי הנכסים (טעינה נכשלה): גוון והבזק בלבד, כדי שהמעבר עדיין ייגמר נקי */
    private fun drawFallback(c: Canvas, w: Float, h: Float, t: Float) {
        fill.shader = null
        fill.color = Color.argb((255 * 0.3f * smooth(seg(t, 0f, 0.35f))).toInt(), 0x3F, 0xA0, 0xFF)
        c.drawRect(0f, 0f, w, h, fill)
        val f = smooth(seg(t, 1.5f, 2.0f))
        fill.color = (INTRO_BLUE and 0x00FFFFFF) or ((255 * f).toInt() shl 24)
        c.drawRect(0f, 0f, w, h, fill)
    }

    /** צבע הסיום (הכחול של סרטון הכניסה, מרכז בהיר): גרדיאנט אחד ברדיוס 1, ממוקם ומוגדל במטריצה (בלי הקצאות) */
    private object RadialGradientCache {
        private val shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(0xFFEFFBFF.toInt(), 0xFF7CC8FF.toInt(), INTRO_BLUE, 0xFF0E3E9A.toInt(), 0x000E3E9A),
            floatArrayOf(0f, 0.1f, 0.4f, 0.8f, 1f), Shader.TileMode.CLAMP
        )
        private val m = android.graphics.Matrix()
        fun get(x: Float, y: Float, r: Float): RadialGradient {
            m.setScale(max(1f, r), max(1f, r))
            m.postTranslate(x, y)
            shader.setLocalMatrix(m)
            return shader
        }
    }
}

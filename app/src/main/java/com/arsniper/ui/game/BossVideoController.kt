package com.arsniper.ui.game

import android.content.Context
import android.graphics.Matrix
import android.net.Uri
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.arsniper.R
import com.arsniper.game.engine.BossClip
import com.arsniper.game.engine.BossFrame

/**
 * נגן הווידאו של בוס הקרח: נגן אחד (ExoPlayer) לכל הקרב, עם כל הסרטונים ברשימת השמעה אחת.
 * מעבר בין סרטונים = קפיצה לפריט אחר באותו נגן, על אותו TextureView (הפריים האחרון נשאר עד שהחדש מוכן,
 * בלי מסך שחור ובלי ליצור נגן מחדש). רק סרטון אחד מתנגן בכל רגע.
 * מי שמחליט מה מנגנים הוא IceRealm (מכונת המצבים); הנגן רק מבצע ומדווח מיקום וסוף.
 */
@OptIn(UnstableApi::class)
class BossVideoController(context: Context) {

    private val app = context.applicationContext

    // קבצים מקומיים: מתחילים לנגן מיד אחרי קפיצה, בלי לחכות למאגר של שניות (ברירת המחדל 2.5 שניות)
    val player: ExoPlayer = ExoPlayer.Builder(app)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(1000, 4000, 0, 0)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        )
        .build()

    /** ה-token של הסרטון שהנגן מנגן עכשיו (מ-IceRealm), ו-1- אם עוד לא התחיל */
    private var token = -1
    private var clip: BossClip? = null
    private var ended = false
    private var failed = false
    private var gamePaused = false
    private var lifecyclePaused = false
    private var released = false

    /** האם כבר הוצג פריים של הסרטון הנוכחי (עד אז השכבה משאירה את מה שהיה מתחת) */
    var frameShownToken = -1
        private set
    private var renderedSinceToken = false

    /** הסרטון שהוכן מראש (בזמן הפורטל) והאם הפריים הראשון שלו כבר צויר */
    private var preloadedClip: BossClip? = null
    private var preloadRendered = false

    private var view: TextureView? = null

    init {
        player.setMediaItems(BossClip.entries.map { MediaItem.fromUri(uriOf(it)) })
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.pauseAtEndOfMediaItems = true
        player.playWhenReady = false
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) ended = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) ended = true
            }

            override fun onRenderedFirstFrame() {
                renderedSinceToken = true
                if (token == -1) preloadRendered = true
            }

            override fun onPlayerError(error: PlaybackException) {
                // בלי וידאו המשחק ממשיך לפי השעון הפנימי של IceRealm
                failed = true
            }
        })
        player.setSeekParameters(SeekParameters.EXACT)
        player.prepare()
    }

    private fun uriOf(c: BossClip): Uri = Uri.parse("android.resource://${app.packageName}/${rawOf(c)}")

    /** העולם של הבוס הזה (נקבע כשהנגן נוצר) */
    private val world = com.arsniper.game.engine.BossWorld.key

    private fun rawOf(c: BossClip): Int {
        if (world != com.arsniper.game.engine.BossWorld.TUNDRA) {
            val id = app.resources.getIdentifier("boss_${world}_${com.arsniper.game.engine.BossWorld.suffix(c)}", "raw", app.packageName)
            if (id != 0) return id
        }
        return tundraRaw(c)
    }

    private fun tundraRaw(c: BossClip): Int = when (c) {
        BossClip.Intro -> R.raw.boss_intro
        BossClip.Breath -> R.raw.boss_breath
        BossClip.Prepare -> R.raw.boss_prepare
        BossClip.Attack1 -> R.raw.boss_attack1
        BossClip.Attack3 -> R.raw.boss_attack3
        BossClip.Attack4 -> R.raw.boss_attack4
        BossClip.Attack5 -> R.raw.boss_attack5
        BossClip.HitHead -> R.raw.boss_hit_head
        BossClip.HitRightShoulder -> R.raw.boss_hit_rshoulder
        BossClip.HitLeftShoulder -> R.raw.boss_hit_lshoulder
        BossClip.HitRightHand -> R.raw.boss_hit_rhand
        BossClip.HitLeftHand -> R.raw.boss_hit_lhand
        BossClip.Death -> R.raw.boss_death
    }

    fun attach(v: TextureView) {
        if (released || view === v) return
        view = v
        player.setVideoTextureView(v)
        fit()
    }

    /** הווידאו ממלא את ה-TextureView בלי עיוות, באותו חיתוך של BossFrame (כדי שהפגיעות יתאימו לתמונה) */
    fun fit() {
        val v = view ?: return
        val w = v.width.toFloat()
        val h = v.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val r = BossFrame.rect(w / h)
        val m = Matrix()
        // ב-TextureView הווידאו נמתח לכל ה-view; מתקנים לגודל ולמיקום של הווידאו המוצג
        m.setScale(r[2], r[3])
        m.postTranslate(r[0] * w, r[1] * h)
        v.setTransform(m)
        v.invalidate()
    }

    /** מנגן את הסרטון של token (פעם אחת לכל token, גם אם נקרא בכל פריים) */
    fun show(c: BossClip, newToken: Int, speed: Float) {
        if (released || newToken == token) return
        token = newToken
        clip = c
        ended = false
        // קפיצה למקום שהנגן כבר עומד בו לא מציירת פריים מחדש: אם הסרטון הוכן מראש והפריים שלו כבר צויר, הוא מוצג
        renderedSinceToken = c == preloadedClip && preloadRendered && c.startMs == 0f
        preloadedClip = null
        player.repeatMode = if (c.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.pauseAtEndOfMediaItems = !c.loop
        player.setPlaybackSpeed(speed)
        player.seekTo(c.ordinal, c.startMs.toLong())
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        applyPlay()
    }

    /** הכנה מראש בלי לנגן (בזמן הפורטל): הנגן ממתין על הפריים הראשון של הסרטון */
    fun preload(c: BossClip) {
        if (released || token != -1) return
        if (preloadedClip != c) {
            preloadedClip = c
            preloadRendered = false
        }
        if (player.currentMediaItemIndex != c.ordinal) player.seekTo(c.ordinal, 0L)
    }

    fun setGamePaused(p: Boolean) {
        if (p == gamePaused) return
        gamePaused = p
        applyPlay()
    }

    fun setLifecyclePaused(p: Boolean) {
        if (p == lifecyclePaused) return
        lifecyclePaused = p
        applyPlay()
    }

    fun setMuted(muted: Boolean) {
        if (released) return
        val vol = if (muted) 0f else 1f
        if (player.volume != vol) player.volume = vol
    }

    private fun applyPlay() {
        if (released) return
        // סרטון שהגיע לסוף נשאר עצור על הפריים האחרון (למשל המוות)
        player.playWhenReady = token != -1 && !ended && !gamePaused && !lifecyclePaused
    }

    /** token שהנגן באמת מנגן עכשיו (1- אם לא מוכן / נכשל / עוד בקפיצה) */
    fun syncToken(): Int {
        if (released || failed || token == -1) return -1
        val c = clip ?: return -1
        if (player.currentMediaItemIndex != c.ordinal) return -1
        // גיבוי: אם הנגן כבר מתקדם בסרטון, יש תמונה גם אם האירוע של הפריים הראשון לא הגיע
        if (renderedSinceToken || (player.isPlaying && player.currentPosition > c.startMs + 120)) frameShownToken = token
        return token
    }

    fun positionMs(): Long = if (released) 0L else player.currentPosition.coerceAtLeast(0L)

    /** הגיע לסוף. בודקים גם את המיקום, כדי שאירוע סוף מאוחר של הסרטון הקודם לא יסיים את החדש */
    fun isEnded(): Boolean {
        val c = clip ?: return false
        return ended && positionMs() >= c.durationMs - 400f
    }

    fun release() {
        if (released) return
        released = true
        view?.let { player.clearVideoTextureView(it) }
        view = null
        player.stop()
        player.release()
    }
}

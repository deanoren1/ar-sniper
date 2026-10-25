package com.arsniper.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.arsniper.R

/** כל האפקטים של המשחק. הקבצים ב-res/raw (חוץ מהירייה, כולם מסונתזים) */
enum class Sfx(val res: Int, val volume: Float = 1f) {
    /** ירייה של השחקן */
    Shot(R.raw.gunshot),

    /** מפלצת מתחילה להתכונן לירות */
    Telegraph(R.raw.sfx_telegraph, 0.7f),

    /** קליע של מפלצת יצא */
    Launch(R.raw.sfx_launch, 0.8f),

    /** שאגת הבוס בתחילת ההכנה */
    BossRoar(R.raw.sfx_boss_roar),

    /** קליע הושמד */
    Deflect(R.raw.sfx_deflect),

    /** קליע נסדק (עוד לא הושמד) */
    Crack(R.raw.sfx_crack),

    /** פגיעה בנקודת תורפה */
    Zone(R.raw.sfx_zone, 0.8f),

    /** הקליע פגע בשחקן */
    PlayerHit(R.raw.sfx_player_hit),

    /** מפלצת נפגעה ולא מתה */
    EnemyHurt(R.raw.sfx_enemy_hurt, 0.8f),

    /** מפלצת מתה */
    EnemyDie(R.raw.sfx_enemy_die),

    /** בלון התפוצץ */
    BalloonPop(R.raw.sfx_balloon_pop),

    /** קומבו עלה */
    Combo(R.raw.sfx_combo, 0.7f),

    WaveStart(R.raw.sfx_wave_start),
    WaveClear(R.raw.sfx_wave_clear),
    Win(R.raw.sfx_win),
    Lose(R.raw.sfx_lose),

    /** לחיצה על ירייה כשהמחסנית ריקה או בטעינה */
    Empty(R.raw.sfx_empty, 0.8f),

    /** טעינה */
    Reload(R.raw.sfx_reload, 0.9f)
}

/** עטיפה ל-SoundPool: טוענת את כל האפקטים ומנגנת אותם בהשהיה נמוכה. */
class SoundPlayer(context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = HashMap<Sfx, Int>()
    private val ready = HashSet<Int>()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(ready) { ready.add(sampleId) }
        }
        for (sfx in Sfx.values()) {
            ids[sfx] = soundPool.load(context, sfx.res, 1)
        }
    }

    /** מנגן אפקט. rate = גובה הצליל (1 = רגיל), כדי לגוון חזרות */
    fun play(sfx: Sfx, rate: Float = 1f) {
        val id = ids[sfx] ?: return
        val loaded = synchronized(ready) { ready.contains(id) }
        if (loaded) soundPool.play(id, sfx.volume, sfx.volume, 1, 0, rate)
    }

    fun playShot() = play(Sfx.Shot)

    fun release() {
        soundPool.release()
    }
}

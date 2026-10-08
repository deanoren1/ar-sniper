package com.arsniper.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.arsniper.R

/** עטיפה ל-SoundPool: טוענת את צליל הירייה ומנגנת אותו בהשהיה נמוכה. */
class SoundPlayer(context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var shotId: Int = 0
    private var loaded: Boolean = false

    init {
        soundPool.setOnLoadCompleteListener { _, _, status ->
            loaded = (status == 0)
        }
        shotId = soundPool.load(context, R.raw.gunshot, 1)
    }

    fun playShot() {
        if (loaded) {
            soundPool.play(shotId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun release() {
        soundPool.release()
    }
}

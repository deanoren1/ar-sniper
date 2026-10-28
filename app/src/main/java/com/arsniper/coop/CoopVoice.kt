package com.arsniper.coop

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/**
 * קול בין השחקנים (מיקרופון on/off). הקול נקלט ב-16kHz, מוקטן ל-8kHz ומקודד µ-law (בייט לדגימה, איכות של טלפון),
 * ונשלח בקטעים של 100 מילישניות כהודעות בינאריות דרך אותו ממסר. קטעי שקט לא נשלחים.
 * הודעה: [0x56][מזהה שולח][800 בתים]. אצל המקבל כל שולח מנוגן ב-AudioTrack משלו.
 */
object CoopVoice {
    const val TAG: Byte = 0x56
    private const val CAPTURE_RATE = 16000
    private const val RATE = 8000
    private const val CHUNK = 800 // 100ms ב-8kHz
    private const val MAX_QUEUE_BYTES = 300_000L

    var micOn by mutableStateOf(false)
        private set

    /** מתי שמענו קול מהשותף לאחרונה (מילישניות), להצגת מחוון */
    @Volatile var lastHeardMs = 0L
        private set

    @Volatile private var running = false
    private var recorder: AudioRecord? = null
    private var captureThread: Thread? = null
    private val speakers = HashMap<Int, Speaker>()

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun setMic(context: Context, on: Boolean) {
        if (!on) {
            stopCapture()
            return
        }
        if (micOn) return
        if (!hasPermission(context)) return
        startCapture()
    }

    /** כיבוי מלא: מיקרופון והשמעה (בניתוק מהחדר) */
    fun stop() {
        stopCapture()
        synchronized(speakers) {
            for (s in speakers.values) s.release()
            speakers.clear()
        }
    }

    // ---------------------------------------------------------------- הקלטה ושליחה

    private fun startCapture() {
        val minBuf = AudioRecord.getMinBufferSize(CAPTURE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return
        try {
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, CAPTURE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, CHUNK * 4 * 2)
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                return
            }
            rec.startRecording()
            recorder = rec
        } catch (_: SecurityException) {
            return
        } catch (_: Exception) {
            return
        }
        running = true
        micOn = true
        val rec = recorder ?: return
        captureThread = Thread {
            val pcm = ShortArray(CHUNK * 2)
            var hangover = 0
            while (running) {
                var got = 0
                while (running && got < pcm.size) {
                    val n = rec.read(pcm, got, pcm.size - got)
                    if (n <= 0) break
                    got += n
                }
                if (!running || got < pcm.size) continue
                val frame = ByteArray(2 + CHUNK)
                frame[0] = TAG
                frame[1] = CoopClient.myId.toByte()
                var energy = 0L
                for (i in 0 until CHUNK) {
                    val avg = (pcm[2 * i] + pcm[2 * i + 1]) / 2
                    energy += abs(avg)
                    frame[2 + i] = encodeMu(avg)
                }
                // קטעי שקט לא נשלחים (חוסך נתונים). אחרי דיבור נשלחים עוד קטעים קצרים כדי לא לחתוך סוף מילה
                val loud = energy / CHUNK > 250
                if (loud) hangover = 3 else if (hangover > 0) hangover -= 1
                if (loud || hangover > 0) {
                    if (CoopClient.queueSize() < MAX_QUEUE_BYTES) CoopClient.sendBytes(frame)
                }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun stopCapture() {
        running = false
        micOn = false
        try { recorder?.stop() } catch (_: Exception) { }
        try { recorder?.release() } catch (_: Exception) { }
        recorder = null
        captureThread = null
    }

    // ---------------------------------------------------------------- קבלה והשמעה

    fun onFrame(data: ByteArray) {
        if (data.size < 3 || data[0] != TAG) return
        val sender = data[1].toInt() and 0xFF
        if (sender == (CoopClient.myId and 0xFF)) return
        lastHeardMs = System.currentTimeMillis()
        val speaker = synchronized(speakers) { speakers.getOrPut(sender) { Speaker() } }
        val samples = ShortArray(data.size - 2) { decodeMu(data[2 + it]) }
        speaker.offer(samples)
    }

    private class Speaker {
        private val queue = LinkedBlockingQueue<ShortArray>(20)
        @Volatile private var alive = true
        private val track: AudioTrack
        private val thread: Thread

        init {
            val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(max(minBuf, RATE * 2 / 2))
                .build()
            track.play()
            thread = Thread {
                // מילוי התחלתי קצר כדי להפחית נקישות בהתחלת דיבור
                track.write(ShortArray(CHUNK), 0, CHUNK)
                while (alive) {
                    val chunk = queue.poll(300, TimeUnit.MILLISECONDS) ?: continue
                    try { track.write(chunk, 0, chunk.size) } catch (_: Exception) { }
                }
            }.also { it.isDaemon = true; it.start() }
        }

        fun offer(samples: ShortArray) {
            if (!queue.offer(samples)) {
                queue.poll() // הנגן מפגר: זורקים את הישן
                queue.offer(samples)
            }
        }

        fun release() {
            alive = false
            try { track.stop() } catch (_: Exception) { }
            try { track.release() } catch (_: Exception) { }
        }
    }

    // ---------------------------------------------------------------- µ-law

    private const val BIAS = 0x84
    private const val CLIP = 32635

    private fun encodeMu(sample: Int): Byte {
        var s = sample
        val sign = if (s < 0) 0x80 else 0
        if (s < 0) s = -s
        if (s > CLIP) s = CLIP
        s += BIAS
        var exponent = 7
        var mask = 0x4000
        while (exponent > 0 && (s and mask) == 0) {
            exponent -= 1
            mask = mask shr 1
        }
        val mantissa = (s shr (exponent + 3)) and 0x0F
        return (sign or (exponent shl 4) or mantissa).inv().toByte()
    }

    private fun decodeMu(byte: Byte): Short {
        val u = byte.toInt().inv() and 0xFF
        val sign = u and 0x80
        val exponent = (u shr 4) and 0x07
        val mantissa = u and 0x0F
        var s = ((mantissa shl 3) + BIAS) shl exponent
        s -= BIAS
        return (if (sign != 0) -s else s).toShort()
    }
}

package com.arsniper.coop

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arsniper.data.PhotoLibrary
import com.arsniper.data.PhotoRepository
import com.arsniper.ml.AnalysisCache
import com.arsniper.ml.UserObjects
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * העברת התמונה מהמארח לשותפים: התמונה עצמה, סימון פנורמה, והניתוח השמור של הסצנה (כדי שלשני הצדדים
 * יהיו בדיוק אותם מחבואים, בלי לנתח שוב בכל מכשיר). הכל עובר כקבצים בחלקים של 512KB דרך אותו ממסר.
 *
 * הודעת טקסט "offer" מתארת את הקבצים, ואחריה הודעות בינאריות:
 * [0x43][אינדקס קובץ][מזהה העברה 4 בתים][מספר חלק 4 בתים][נתונים]. האורח עונה "ready" כשהכל נשמר.
 */
object CoopTransfer {
    private const val CHUNK = 512 * 1024
    private const val TAG: Byte = 0x43
    private const val HEADER = 10
    private const val MAX_QUEUE = 3L * 1024 * 1024

    // צד השולח (מארח)
    var sending by mutableStateOf(false)
        private set
    var sendText by mutableStateOf("")
        private set
    var sendProgress by mutableStateOf(0f)
        private set
    var readyCount by mutableStateOf(0)
        private set

    // צד המקבל (אורח)
    var receiving by mutableStateOf(false)
        private set
    var received by mutableStateOf(false)
        private set
    var receiveText by mutableStateOf("")
        private set
    var receiveProgress by mutableStateOf(0f)
        private set

    private var appContext: Context? = null
    @Volatile private var sendId = 0
    @Volatile private var cancelled = false

    private class FileSpec(val name: String, val size: Long, val gz: Boolean)

    private class Incoming(
        val id: Int,
        val key: String,
        val pano: String?,
        val specs: List<FileSpec>,
        val dir: File
    ) {
        val rafs = specs.mapIndexed { i, spec ->
            RandomAccessFile(File(dir, "f$i"), "rw").also { it.setLength(spec.size) }
        }
        val totalChunks = specs.sumOf { chunksOf(it.size) }
        var gotChunks = 0
        val seen = specs.map { BooleanArray(chunksOf(it.size)) }
    }

    private var incoming: Incoming? = null

    private fun chunksOf(size: Long): Int = if (size <= 0L) 0 else ((size + CHUNK - 1) / CHUNK).toInt()

    fun attach(context: Context) {
        appContext = context.applicationContext
        CoopClient.onText = { onText(it) }
        CoopClient.onBinary = { onBinary(it) }
    }

    fun detach(keepHandlers: Boolean = false) {
        cancelled = true
        if (!keepHandlers) {
            CoopClient.onText = null
            CoopClient.onBinary = null
        }
        closeIncoming()
        sending = false
        receiving = false
        received = false
        sendText = ""
        receiveText = ""
        sendProgress = 0f
        receiveProgress = 0f
        readyCount = 0
    }

    // ---------------------------------------------------------------- שליחה

    /** שולח את התמונה הנוכחית (האחרונה ששיחקת) לשאר החדר */
    fun sendCurrent() {
        val context = appContext ?: return
        if (sending) return
        val photo = PhotoRepository.currentFile(context)
        if (photo == null) {
            sendText = "No photo yet. Play or pick a photo first"
            return
        }
        sending = true
        cancelled = false
        readyCount = 0
        sendProgress = 0f
        sendText = "Preparing..."
        val id = (System.nanoTime() and 0x7FFFFFFF).toInt()
        sendId = id
        Thread {
            val temps = ArrayList<File>()
            try {
                val key = AnalysisCache.keyFor(photo)
                val parts = ArrayList<Triple<String, File, Boolean>>()
                parts.add(Triple("photo", photo, false))
                val extras = listOf(
                    "analysis" to AnalysisCache.fileFor(context.filesDir, key),
                    "objects" to UserObjects.file(context.filesDir, key),
                    "sam" to UserObjects.file(context.filesDir, key, "sam")
                )
                for ((name, file) in extras) {
                    if (!file.exists() || file.length() == 0L) continue
                    val tmp = File(context.cacheDir, "coop_out_${id}_$name.gz")
                    GZIPOutputStream(FileOutputStream(tmp)).use { out -> file.inputStream().use { it.copyTo(out) } }
                    temps.add(tmp)
                    parts.add(Triple(name, tmp, true))
                }
                val offer = JSONObject()
                    .put("t", "offer")
                    .put("id", id)
                    .put("key", key)
                    .put("pano", PhotoLibrary.panoMarkerText(context) ?: "")
                    .put("files", JSONArray().apply {
                        for ((name, file, gz) in parts) {
                            put(JSONObject().put("n", name).put("s", file.length()).put("z", if (gz) 1 else 0))
                        }
                    })
                if (!CoopClient.sendText(offer.toString())) throw IllegalStateException("Not connected")

                val total = parts.sumOf { it.second.length() }.coerceAtLeast(1L)
                var sent = 0L
                val buffer = ByteArray(CHUNK)
                for ((index, part) in parts.withIndex()) {
                    FileInputStream(part.second).use { input ->
                        var seq = 0
                        while (true) {
                            if (cancelled) throw IllegalStateException("Cancelled")
                            val n = readFully(input, buffer)
                            if (n <= 0) break
                            val frame = ByteArray(HEADER + n)
                            frame[0] = TAG
                            frame[1] = index.toByte()
                            ByteBuffer.wrap(frame).putInt(2, id).putInt(6, seq)
                            System.arraycopy(buffer, 0, frame, HEADER, n)
                            while (CoopClient.queueSize() > MAX_QUEUE) {
                                if (cancelled) throw IllegalStateException("Cancelled")
                                Thread.sleep(30)
                            }
                            if (!CoopClient.sendBytes(frame)) throw IllegalStateException("Connection lost")
                            seq += 1
                            sent += n
                            sendProgress = sent.toFloat() / total
                            sendText = "Sending photo... ${(sendProgress * 100).toInt()}%"
                        }
                    }
                }
                sendProgress = 1f
                sendText = "Sent. Waiting for the partner to save it..."
            } catch (e: Exception) {
                sendText = "Sending failed: ${e.message ?: e}"
            } finally {
                for (t in temps) t.delete()
                sending = false
            }
        }.start()
    }

    private fun readFully(input: FileInputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    // ---------------------------------------------------------------- קבלה

    private fun onText(text: String) {
        if (!text.startsWith("{")) return
        try {
            val json = JSONObject(text)
            when (json.optString("t")) {
                "start" -> CoopSync.onStart(json)
                "offer" -> if (CoopClient.role != "host") startReceive(json)
                "ready" -> if (CoopClient.role == "host" && json.optInt("id") == sendId) {
                    readyCount += 1
                    sendText = "Partner has the photo ($readyCount)"
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun startReceive(json: JSONObject) {
        val context = appContext ?: return
        closeIncoming()
        try {
            val arr = json.getJSONArray("files")
            val specs = (0 until arr.length()).map {
                val f = arr.getJSONObject(it)
                FileSpec(f.getString("n"), f.getLong("s"), f.optInt("z") == 1)
            }
            val dir = File(context.cacheDir, "coop_in").apply { deleteRecursively(); mkdirs() }
            val pano = json.optString("pano").ifEmpty { null }
            incoming = Incoming(json.getInt("id"), json.optString("key"), pano, specs, dir)
            received = false
            receiving = true
            receiveProgress = 0f
            receiveText = "Receiving the photo... 0%"
        } catch (e: Exception) {
            receiveText = "Bad photo offer: ${e.message}"
        }
    }

    private fun onBinary(data: ByteArray) {
        val inc = incoming ?: return
        if (data.size <= HEADER || data[0] != TAG) return
        val bb = ByteBuffer.wrap(data)
        val index = data[1].toInt()
        if (bb.getInt(2) != inc.id || index !in inc.specs.indices) return
        val seq = bb.getInt(6)
        val seen = inc.seen[index]
        if (seq !in seen.indices || seen[seq]) return
        try {
            inc.rafs[index].seek(seq.toLong() * CHUNK)
            inc.rafs[index].write(data, HEADER, data.size - HEADER)
        } catch (e: Exception) {
            receiveText = "Receiving failed: ${e.message}"
            closeIncoming()
            return
        }
        seen[seq] = true
        inc.gotChunks += 1
        receiveProgress = inc.gotChunks.toFloat() / inc.totalChunks.coerceAtLeast(1)
        receiveText = "Receiving the photo... ${(receiveProgress * 100).toInt()}%"
        if (inc.gotChunks >= inc.totalChunks) finishReceive(inc)
    }

    private fun finishReceive(inc: Incoming) {
        val context = appContext ?: return
        try {
            for (raf in inc.rafs) raf.close()
            val photoIndex = inc.specs.indexOfFirst { it.name == "photo" }
            if (photoIndex < 0) throw IllegalStateException("No photo in the transfer")
            val key = PhotoLibrary.importShared(context, File(inc.dir, "f$photoIndex"), inc.pano)
                ?: throw IllegalStateException("Could not save the photo")
            // הניתוח וסימוני העצמים: רק אם המפתח זהה (אותה גרסת ניתוח), אחרת המכשיר ינתח לבד
            if (key == inc.key) {
                for ((i, spec) in inc.specs.withIndex()) {
                    val target = when (spec.name) {
                        "analysis" -> AnalysisCache.fileFor(context.filesDir, key)
                        "objects" -> UserObjects.file(context.filesDir, key)
                        "sam" -> UserObjects.file(context.filesDir, key, "sam")
                        else -> null
                    } ?: continue
                    target.parentFile?.mkdirs()
                    val source = File(inc.dir, "f$i")
                    if (spec.gz) {
                        GZIPInputStream(FileInputStream(source)).use { input ->
                            target.outputStream().use { input.copyTo(it) }
                        }
                    } else {
                        source.copyTo(target, overwrite = true)
                    }
                }
            }
            inc.dir.deleteRecursively()
            incoming = null
            receiving = false
            received = true
            receiveProgress = 1f
            receiveText = "Photo received"
            CoopClient.sendText(JSONObject().put("t", "ready").put("id", inc.id).toString())
        } catch (e: Exception) {
            receiving = false
            receiveText = "Saving the photo failed: ${e.message ?: e}"
            closeIncoming()
        }
    }

    private fun closeIncoming() {
        val inc = incoming ?: return
        incoming = null
        for (raf in inc.rafs) try { raf.close() } catch (_: Exception) { }
        inc.dir.deleteRecursively()
        receiving = false
    }
}

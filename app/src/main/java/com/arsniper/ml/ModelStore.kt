package com.arsniper.ml

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * ניהול קבצי מודל גדולים שלא נכנסים לאפליקציה: מורידים פעם אחת ושומרים בטלפון.
 * ההורדה ממשיכה מאיפה שנעצרה אם הופסקה באמצע.
 */
object ModelStore {

    private const val TIPS_FILE_NAME = "tipsv2_b14_dpt_fp16.tflite"
    private const val TIPS_URL =
        "https://huggingface.co/litert-community/TIPSv2-B14-DPT-LiteRT/resolve/main/tipsv2_b14_dpt_fp16.tflite"

    /** גודל מינימלי תקין (המודל שוקל כ-318MB) */
    private const val TIPS_MIN_BYTES = 300_000_000L

    fun tipsModelFile(context: Context): File = File(context.filesDir, TIPS_FILE_NAME)

    /**
     * מוודא שמודל הסצנה קיים בטלפון, ומוריד אותו אם צריך.
     * onProgress מקבל כמה בייטים ירדו ומה הגודל הכולל (0 אם לא ידוע).
     */
    suspend fun ensureTipsModel(
        context: Context,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val target = tipsModelFile(context)
        if (target.exists() && target.length() >= TIPS_MIN_BYTES) {
            return@withContext target
        }

        val partial = File(context.filesDir, "$TIPS_FILE_NAME.part")
        var existing = if (partial.exists()) partial.length() else 0L

        val connection = URL(TIPS_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "ARSniper/0.1")
            if (existing > 0L) {
                connection.setRequestProperty("Range", "bytes=$existing-")
            }

            val code = connection.responseCode
            val append: Boolean
            when (code) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    append = true
                }
                HttpURLConnection.HTTP_OK -> {
                    append = false
                    existing = 0L
                }
                else -> {
                    if (code == 416) partial.delete()
                    throw IOException("Download failed (HTTP $code)")
                }
            }

            val remaining = connection.contentLengthLong
            val total = if (remaining > 0L) existing + remaining else 0L

            connection.inputStream.use { input ->
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = existing
                    var lastReported = downloaded
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReported >= 1_000_000L) {
                            lastReported = downloaded
                            onProgress(downloaded, total)
                        }
                    }
                    onProgress(downloaded, total)
                }
            }
        } finally {
            connection.disconnect()
        }

        if (partial.length() < TIPS_MIN_BYTES) {
            partial.delete()
            throw IOException("Downloaded file is incomplete")
        }
        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) {
            throw IOException("Could not save the model file")
        }
        target
    }
}

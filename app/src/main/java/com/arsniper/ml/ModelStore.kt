package com.arsniper.ml

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * מוריד מודלים פעם אחת לזיכרון הפנימי של האפליקציה (עם המשך הורדה אחרי הפסקה).
 * TIPSv2: ניתוח הסצנה. SAM 2.1: סימון עצם בנגיעה.
 */
object ModelStore {

    const val TIPS_FILE_NAME = "tipsv2_b14_dpt_fp16.tflite"
    private const val TIPS_URL =
        "https://huggingface.co/litert-community/TIPSv2-B14-DPT-LiteRT/resolve/main/$TIPS_FILE_NAME"
    private const val TIPS_MIN_BYTES = 300_000_000L

    const val SAM_ENCODER_NAME = "sam2_encoder.tflite"
    const val SAM_DECODER_NAME = "sam2_decoder.tflite"
    const val SAM_PROMPT_NAME = "sam2_prompt.bin"
    private const val SAM_BASE = "https://huggingface.co/mlboydaisuke/SAM2-hiera-tiny-LiteRT/resolve/main/"
    private const val SAM_ENCODER_MIN = 70_000_000L
    private const val SAM_DECODER_MIN = 15_000_000L
    private const val SAM_PROMPT_MIN = 3_000L

    class SamFiles(val encoder: File, val decoder: File, val prompt: File)

    fun tipsModelFile(context: Context): File = File(context.filesDir, TIPS_FILE_NAME)

    fun samReady(context: Context): Boolean =
        File(context.filesDir, SAM_ENCODER_NAME).length() >= SAM_ENCODER_MIN &&
            File(context.filesDir, SAM_DECODER_NAME).length() >= SAM_DECODER_MIN &&
            File(context.filesDir, SAM_PROMPT_NAME).length() >= SAM_PROMPT_MIN

    suspend fun ensureTipsModel(
        context: Context,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File = download(context, TIPS_URL, TIPS_FILE_NAME, TIPS_MIN_BYTES, onProgress)

    /** מוריד את שלושת קבצי SAM (כ-97MB בסך הכל). הדיווח: שם הקובץ + התקדמות */
    suspend fun ensureSamModels(
        context: Context,
        onProgress: (name: String, downloaded: Long, total: Long) -> Unit
    ): SamFiles {
        val prompt = download(context, SAM_BASE + SAM_PROMPT_NAME, SAM_PROMPT_NAME, SAM_PROMPT_MIN) { d, t ->
            onProgress("prompt", d, t)
        }
        val decoder = download(context, SAM_BASE + SAM_DECODER_NAME, SAM_DECODER_NAME, SAM_DECODER_MIN) { d, t ->
            onProgress("decoder", d, t)
        }
        val encoder = download(context, SAM_BASE + SAM_ENCODER_NAME, SAM_ENCODER_NAME, SAM_ENCODER_MIN) { d, t ->
            onProgress("encoder", d, t)
        }
        return SamFiles(encoder, decoder, prompt)
    }

    private suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        minBytes: Long,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val target = File(context.filesDir, fileName)
        if (target.exists() && target.length() >= minBytes) return@withContext target

        val part = File(context.filesDir, "$fileName.part")
        var attempt = 0
        while (true) {
            attempt++
            val resumeFrom = if (part.exists()) part.length() else 0L
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 20_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true
                if (resumeFrom > 0L) connection.setRequestProperty("Range", "bytes=$resumeFrom-")
                connection.connect()
                val code = connection.responseCode
                if (code == 416) {
                    // כבר הורד הכל
                    break
                }
                if (code != 200 && code != 206) error("Download failed: HTTP $code")
                val append = code == 206 && resumeFrom > 0L
                val startAt = if (append) resumeFrom else 0L
                val total = if (connection.contentLengthLong > 0L) startAt + connection.contentLengthLong else -1L
                var downloaded = startAt
                connection.inputStream.use { input ->
                    FileOutputStream(part, append).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var lastReport = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastReport > 250) {
                                lastReport = now
                                onProgress(downloaded, total)
                            }
                        }
                    }
                }
                onProgress(downloaded, total)
                break
            } catch (e: java.io.IOException) {
                if (attempt >= 5) throw e
            } finally {
                connection.disconnect()
            }
        }
        if (part.length() < minBytes) {
            part.delete()
            error("Downloaded file is too small: $fileName")
        }
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) error("Could not save $fileName")
        target
    }
}
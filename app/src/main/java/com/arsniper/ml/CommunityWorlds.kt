package com.arsniper.ml

import android.content.Context
import com.arsniper.coop.CoopClient
import com.arsniper.data.PhotoLibrary
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * עולמות קהילה: שיתוף עולם (תמונת ה-AI + ניתוח הסצנה, בלי תמונת המקור), הורדת עולם אקראי, דיווח וביטול שיתוף.
 * כל הפעולות חוסמות: להריץ מחוץ לתהליך הראשי. אין כאן מפתחות: השרת מזהה מכשיר לפי מזהה אקראי.
 */
object CommunityWorlds {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()

    sealed class Result<out T> {
        class Ok<T>(val value: T) : Result<T>()
        class Error(val message: String) : Result<Nothing>()
    }

    private fun base(context: Context): String? =
        CoopClient.savedServer(context).trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
            .takeIf { it.isNotEmpty() }?.let { "https://$it" }

    private fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences("aiworld", Context.MODE_PRIVATE)
        return prefs.getString("device", null) ?: java.util.UUID.randomUUID().toString().replace("-", "").also {
            prefs.edit().putString("device", it).apply()
        }
    }

    private fun errorOf(text: String): String =
        try { JSONObject(text).optString("error", text) } catch (_: Exception) { text }.take(80)

    /** משתף עולם. photoKey: מפתח התמונה, worldFile: קובץ העולם, serverStyle למשל "jungle_mid". מחזיר את מזהה העולם */
    fun share(context: Context, photoKey: String, serverStyle: String, worldFile: File): Result<String> {
        val server = base(context) ?: return Result.Error("Set the server address first")
        val analysisFile = AnalysisCache.fileFor(context.filesDir, photoKey)
        if (!analysisFile.exists()) return Result.Error("Play this world once first, then share")
        return try {
            val world = worldFile.readBytes()
            val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(analysisFile.readBytes()) } }.toByteArray()
            val body = ByteArrayOutputStream().apply {
                write(byteArrayOf((world.size ushr 24).toByte(), (world.size ushr 16).toByte(), (world.size ushr 8).toByte(), world.size.toByte()))
                write(world); write(gz)
            }.toByteArray()
            val request = Request.Builder().url("$server/worlds/share?style=$serverStyle")
                .header("x-device", deviceId(context))
                .post(body.toRequestBody("application/octet-stream".toMediaType())).build()
            http.newCall(request).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) Result.Error(errorOf(text)) else Result.Ok(JSONObject(text).getString("id"))
            }
        } catch (e: Exception) { Result.Error(e.message ?: "network") }
    }

    /** מוריד עולם אקראי של הארנה, מתקין אותו מקומית (ספרייה + ניתוח + עולם) ומחזיר את קובץ התמונה בספרייה */
    fun downloadRandom(context: Context, serverStyle: String, seenIds: Set<String>): Result<Pair<File, String>> {
        val server = base(context) ?: return Result.Error("Set the server address first")
        return try {
            val device = deviceId(context)
            val pick = Request.Builder().url("$server/worlds/random?style=$serverStyle&exclude=${seenIds.take(50).joinToString(",")}")
                .header("x-device", device).build()
            val id = http.newCall(pick).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) return Result.Error(if (r.code == 404) "none" else errorOf(text))
                JSONObject(text).getString("id")
            }
            val image = fetch(server, "img/$id", device) ?: return Result.Error("download failed")
            val analysisGz = fetch(server, "analysis/$id", device) ?: return Result.Error("download failed")
            val analysis = GZIPInputStream(analysisGz.inputStream()).use { it.readBytes() }
            val temp = File(context.cacheDir, "community_$id.jpg").apply { writeBytes(image) }
            val key = AnalysisCache.keyFor(temp)
            AnalysisCache.fileFor(context.filesDir, key).apply { parentFile?.mkdirs(); writeBytes(analysis) }
            File(File(context.filesDir, "ai_world").apply { mkdirs() }, "${key}_$serverStyle.jpg").writeBytes(image)
            PhotoLibrary.importShared(context, temp, null) ?: return Result.Error("import failed")
            temp.delete()
            Result.Ok(File(File(context.filesDir, "library"), "$key.jpg") to id)
        } catch (e: Exception) { Result.Error(e.message ?: "network") }
    }

    private fun fetch(server: String, path: String, device: String): ByteArray? {
        val request = Request.Builder().url("$server/worlds/$path").header("x-device", device).build()
        return http.newCall(request).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
    }

    fun report(context: Context, id: String): Boolean = post(context, "report", id)
    fun unshare(context: Context, id: String): Boolean = post(context, "unshare", id)

    private fun post(context: Context, op: String, id: String): Boolean {
        val server = base(context) ?: return false
        return try {
            val request = Request.Builder().url("$server/worlds/$op?id=$id").header("x-device", deviceId(context))
                .post(ByteArray(0).toRequestBody(null)).build()
            http.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) { false }
    }
}

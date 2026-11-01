package com.arsniper.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.arsniper.coop.CoopClient
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * יצירת עולם בעזרת AI דרך השרת שלנו (המפתח נשאר בשרת). משתמש באותה כתובת שרת שמוגדרת במסך השיתוף.
 * עיבוד אחד לכל קריאה; בשרת יש מכסה יומית לכל מכשיר ותקרה כללית.
 */
object AiWorld {

    private const val PREFS = "aiworld"
    private const val MAX_SIDE = 1280

    sealed class Result {
        class Image(val bitmap: Bitmap, val provider: String) : Result()
        class Error(val message: String) : Result()
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("device", null) ?: UUID.randomUUID().toString().replace("-", "").also {
            prefs.edit().putString("device", it).apply()
        }
    }

    /** חוסם: להריץ מחוץ לתהליך הראשי. style: למשל "tundra". provider: null = ברירת המחדל של השרת */
    fun generate(context: Context, style: String, source: Bitmap, provider: String? = null, quality: String? = null, model: String? = null): Result {
        val server = CoopClient.savedServer(context).trim()
            .removePrefix("https://").removePrefix("http://").trimEnd('/')
        if (server.isEmpty()) return Result.Error("Set the server address first (Co-op screen)")
        return try {
            val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(source.width, source.height))
            val small = if (scale < 1f) {
                Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
            } else source
            val jpeg = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
            val portrait = source.height > source.width
            val query = buildString {
                append("style=$style&size=${if (portrait) "portrait" else "landscape"}")
                if (provider != null) append("&provider=$provider")
                if (quality != null) append("&quality=$quality")
                if (model != null) append("&model=$model")
            }
            val request = Request.Builder()
                .url("https://$server/restyle?$query")
                .header("x-device", deviceId(context))
                .post(jpeg.toRequestBody("image/jpeg".toMediaType()))
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val text = response.body?.string().orEmpty()
                    val error = try { JSONObject(text).optString("error", text) } catch (_: Exception) { text }
                    return Result.Error(
                        when {
                            error == "daily-limit" -> "Daily AI limit reached for this device"
                            error == "budget-reached" -> "Server AI budget reached for today"
                            error.contains("not-configured") -> "AI is not configured on the server yet (missing key)"
                            else -> "HTTP ${response.code}: ${error.take(160)}"
                        }
                    )
                }
                val bytes = response.body?.bytes() ?: return Result.Error("Empty answer")
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return Result.Error("Could not decode the image")
                Result.Image(bitmap, response.header("x-provider") ?: "?")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: e.toString())
        }
    }

    private fun lastFile(context: Context) = java.io.File(context.filesDir, "ai_last.jpg")

    /** שומר את התוצאה האחרונה בדיסק, כדי שלא תאבד ביציאה מהמסך */
    fun rememberLast(context: Context, bitmap: Bitmap) {
        try {
            lastFile(context).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } catch (_: Exception) {
        }
    }

    fun loadLast(context: Context): Bitmap? =
        lastFile(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }

    /** שומר עותק בגלריה (Pictures/SnapBoo). מחזיר true בהצלחה */
    fun saveToGallery(context: Context, bitmap: Bitmap): Boolean = try {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "snapboo_ai_${System.currentTimeMillis()}.jpg")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapBoo")
        }
        val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) false else {
            context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            true
        }
    } catch (_: Exception) {
        false
    }
}

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
    fun generate(context: Context, style: String, source: Bitmap, provider: String? = null, quality: String? = null, model: String? = null, sandbox: Boolean = false): Result {
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
                append("style=$style&size=${if (portrait) "portrait" else "landscape"}&aspect=${"%.4f".format(java.util.Locale.US, source.width.toFloat() / source.height)}")
                if (provider != null) append("&provider=$provider")
                if (quality != null) append("&quality=$quality")
                if (model != null) append("&model=$model")
                if (sandbox) append("&sandbox=1")
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
                // היחס של התוצאה קרוב ליחס המקור אבל לא זהה: מותחים בקלות ליחס המדויק, כדי שהניתוח של המקור יתאים לפיקסל
                val target = source.width.toFloat() / source.height
                val got = bitmap.width.toFloat() / bitmap.height
                val aligned = if (kotlin.math.abs(got / target - 1f) > 0.005f) {
                    Bitmap.createScaledBitmap(bitmap, bitmap.width, (bitmap.width / target).toInt().coerceAtLeast(1), true)
                } else bitmap
                Result.Image(aligned, response.header("x-provider") ?: "?")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: e.toString())
        }
    }

    /** הסגנון בשרת לכל ארנה, או null אם אין עדיין עולם AI לארנה הזאת */
    fun serverStyle(style: com.arsniper.game.progress.ArenaStyle): String? = when (style) {
        com.arsniper.game.progress.ArenaStyle.Tundra -> "tundra"
        com.arsniper.game.progress.ArenaStyle.Jungle -> "jungle"
        com.arsniper.game.progress.ArenaStyle.Forest -> "rockforest"
        else -> null
    }

    private const val WORLD_MODEL = "seedream-5.0-flash-edit"
    private const val WORLD_FALLBACK_MODEL = "muse-image-edit"

    fun statusFile(context: Context) = java.io.File(context.filesDir, "ai_world_status.txt")

    private fun worldFile(context: Context, photoKey: String, serverStyle: String) =
        java.io.File(java.io.File(context.filesDir, "ai_world").apply { mkdirs() }, "${photoKey}_$serverStyle.jpg")

    /** עולם AI שכבר נוצר לתמונה הזאת (מטמון בדיסק), או null */
    fun cachedWorld(context: Context, photoKey: String, serverStyle: String): Bitmap? =
        worldFile(context, photoKey, serverStyle).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }

    /** יוצר עולם AI לתמונה (seedream, ואם נכשל muse), שומר במטמון. חוסם: להריץ מחוץ לתהליך הראשי. null בכישלון */
    @Volatile var lastError: String = ""

    fun createWorld(context: Context, photoKey: String, serverStyle: String, source: Bitmap): Bitmap? {
        for (model in listOf(WORLD_MODEL, WORLD_FALLBACK_MODEL)) {
            val result = generate(context, serverStyle, source, provider = "muapi", model = model)
            if (result is Result.Image) {
                try {
                    worldFile(context, photoKey, serverStyle).outputStream().use { result.bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                } catch (_: Exception) {
                }
                return result.bitmap
            }
            // מכסה יומית או תקציב: אין טעם לנסות דגם אחר
            val message = (result as Result.Error).message
            lastError = message
            statusFile(context).writeText("${java.util.Date()} $model: $message")
            if (message.contains("limit", true) || message.contains("budget", true) || message.contains("server address", true)) return null
        }
        return null
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

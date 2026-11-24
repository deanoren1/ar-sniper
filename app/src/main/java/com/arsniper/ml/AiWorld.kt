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

    /** כמה יצירות AI נשארו היום למכשיר הזה: זוג (נשאר, מכסה), או null אם אי אפשר לדעת. חוסם: מחוץ לתהליך הראשי */
    fun quotaLeft(context: Context): Pair<Int, Int>? {
        val server = CoopClient.savedServer(context).trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        if (server.isEmpty()) return null
        return try {
            val request = Request.Builder().url("https://$server/quota").header("x-device", deviceId(context)).build()
            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return null
                val o = JSONObject(r.body?.string().orEmpty())
                Pair(o.getInt("left"), o.getInt("limit"))
            }
        } catch (_: Exception) { null }
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
                .apply { OwnerKey.get(context).takeIf { it.isNotEmpty() }?.let { header("x-owner", it) } }
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
        // טונדרה: סגנון משחק כהה (כחולים עמוקים, נטיפים, גבישים, מנורות חמות), נבחר בדף הבדיקה
        com.arsniper.game.progress.ArenaStyle.Tundra -> "tundra_v4"
        com.arsniper.game.progress.ArenaStyle.Jungle -> "jungle_mid"
        com.arsniper.game.progress.ArenaStyle.Forest -> "rockforest_mid"
        com.arsniper.game.progress.ArenaStyle.Cyber -> "cyber_mid"
        com.arsniper.game.progress.ArenaStyle.Military -> "military_mid"
        com.arsniper.game.progress.ArenaStyle.Ruins -> "ruins_mid"
        com.arsniper.game.progress.ArenaStyle.Market -> "market_mid"
        com.arsniper.game.progress.ArenaStyle.Industrial -> "industrial_mid"
        else -> null
    }

    private const val WORLD_MODEL = "seedream-5.0-flash-edit"
    private const val WORLD_FALLBACK_MODEL = "muse-image-edit"

    fun statusFile(context: Context) = java.io.File(context.filesDir, "ai_world_status.txt")

    fun worldFile(context: Context, photoKey: String, serverStyle: String) =
        java.io.File(java.io.File(context.filesDir, "ai_world").apply { mkdirs() }, "${photoKey}_$serverStyle.jpg")

    /** עולם שנוצר בעבר לאותה תמונה ובאותה משפחת ארנה בסגנון ישן (למשל tundra במקום tundra_mid): משתמשים בו במקום ליצור מחדש */
    private fun olderWorldFile(context: Context, photoKey: String, serverStyle: String): java.io.File? {
        val family = serverStyle.substringBefore("_")
        val pattern = Regex("^" + Regex.escape(photoKey) + "_" + Regex.escape(family) + "(_[a-z0-9]+)?\\.jpg$")
        val dir = java.io.File(context.filesDir, "ai_world")
        return (dir.listFiles() ?: emptyArray()).filter { pattern.matches(it.name) }.maxByOrNull { it.lastModified() }
    }

    /** קובץ העולם השמור לתמונה וסגנון (גם בשם הישן של אותה משפחה), או null */
    fun cachedWorldFile(context: Context, photoKey: String, serverStyle: String): java.io.File? =
        worldFile(context, photoKey, serverStyle).takeIf { it.exists() } ?: olderWorldFile(context, photoKey, serverStyle)

    /** עולם AI שכבר נוצר לתמונה הזאת (מטמון בדיסק), או null */
    fun cachedWorld(context: Context, photoKey: String, serverStyle: String): Bitmap? =
        (worldFile(context, photoKey, serverStyle).takeIf { it.exists() } ?: olderWorldFile(context, photoKey, serverStyle))?.let { f ->
            // תמונת AI גדולה מאוד מכבידה על ה-GPU בכל פריים: מקטינים בפענוח לצלע ארוכה של עד ~2200
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            var sample = 1
            // פנורמה רחבה צריכה יותר פיקסלים לאורך (הגובה הוא גובה המסך); תקרה של 4096 לאורך ובערך 5 מיליון פיקסלים
            val wide = bounds.outWidth.toFloat() / bounds.outHeight.coerceAtLeast(1) >= 2.6f
            while (if (wide) (bounds.outWidth / sample > 4096 || bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > 5_000_000L)
                else maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2200) sample *= 2
            BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }

    /** יוצר עולם AI לתמונה (seedream, ואם נכשל muse), שומר במטמון. חוסם: להריץ מחוץ לתהליך הראשי. null בכישלון */
    @Volatile var lastError: String = ""

    fun createWorld(context: Context, photoKey: String, serverStyle: String, source: Bitmap): Bitmap? {
        // פנורמה 180: דגמי התמונה לא תומכים ביחס רחב כל כך, לכן יוצרים בכמה חלקים ומחברים
        if (source.width.toFloat() / source.height >= 2.6f) return createWorldTiled(context, photoKey, serverStyle, source)
        val made = restyleBest(context, serverStyle, source) ?: return null
        try {
            worldFile(context, photoKey, serverStyle).outputStream().use { made.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        } catch (_: Exception) {
        }
        return made
    }

    /** seedream, ואם נכשל muse. null בכישלון (lastError ו-statusFile מעודכנים) */
    private fun restyleBest(context: Context, serverStyle: String, source: Bitmap): Bitmap? {
        for (model in listOf(WORLD_MODEL, WORLD_FALLBACK_MODEL)) {
            val result = generate(context, serverStyle, source, provider = "muapi", model = model)
            if (result is Result.Image) return result.bitmap
            // מכסה יומית או תקציב: אין טעם לנסות דגם אחר
            val message = (result as Result.Error).message
            lastError = message
            try { statusFile(context).writeText("${java.util.Date()} $model: $message") } catch (_: Exception) { }
            if (message.contains("limit", true) || message.contains("budget", true) || message.contains("server address", true)) return null
        }
        return null
    }

    private const val TILE_ASPECT = 16f / 9f
    private const val TILE_OVERLAP = 0.25f
    private const val MAX_TILES = 6

    /**
     * פנורמה רחבה: חותכים לחלקים בחפיפה של 25% (כל חלק כמעט 16:9), יוצרים כל חלק כעולם AI, מתקנים בהירות וצבע
     * לפי החפיפה ומחברים בדהייה הדרגתית. התוצאה ביחס המדויק של המקור, כדי שהניתוח של המקור יתאים. חלקים שנוצרו נשמרים,
     * כך שניסיון חוזר לא מחייב שוב על חלקים שכבר הצליחו.
     */
    private fun createWorldTiled(context: Context, photoKey: String, serverStyle: String, source: Bitmap): Bitmap? {
        val aspect = source.width.toFloat() / source.height
        val o = TILE_OVERLAP
        val k = (Math.ceil(((aspect - TILE_ASPECT) / (TILE_ASPECT * (1f - o))).toDouble()).toInt() + 1).coerceIn(2, MAX_TILES)
        val tw = source.width / (k * (1f - o) + o)
        val step = tw * (1f - o)
        val dir = java.io.File(java.io.File(context.filesDir, "ai_world"), "tiles").apply { mkdirs() }
        val files = (0 until k).map { java.io.File(dir, "${photoKey}_${serverStyle}_${k}_$it.jpg") }
        val results = arrayOfNulls<Bitmap>(k)
        val threads = (0 until k).map { i ->
            Thread {
                val cached = if (files[i].exists()) BitmapFactory.decodeFile(files[i].absolutePath) else null
                if (cached != null) {
                    results[i] = cached
                } else {
                    val x = Math.round(i * step).coerceIn(0, source.width - 1)
                    val w = Math.round(tw).coerceAtMost(source.width - x).coerceAtLeast(1)
                    val made = restyleBest(context, serverStyle, Bitmap.createBitmap(source, x, 0, w, source.height))
                    if (made != null) {
                        try { files[i].outputStream().use { made.compress(Bitmap.CompressFormat.JPEG, 92, it) } } catch (_: Exception) { }
                    }
                    results[i] = made
                }
            }.also { it.start() }
        }
        threads.forEach { it.join() }
        if (results.any { it == null }) return null
        val tiles = results.map { it!! }

        val outH = tiles.maxOf { it.height }
        val outW = Math.round(outH * aspect)
        val twOut = outW / (k * (1f - o) + o)
        val stepOut = twOut * (1f - o)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        for (i in 0 until k) {
            val x0 = Math.round(i * stepOut).coerceAtMost(outW - 1)
            val w = Math.round(twOut).coerceAtMost(outW - x0).coerceAtLeast(1)
            val ov = Math.round(twOut * o).coerceAtMost(w)
            val layer = Bitmap.createBitmap(w, outH, Bitmap.Config.ARGB_8888)
            val lc = android.graphics.Canvas(layer)
            val dst = android.graphics.Rect(0, 0, w, outH)
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            lc.drawBitmap(tiles[i], null, dst, paint)
            if (i > 0 && ov > 4) {
                // בהירות וצבע החלק החדש מותאמים לקודם לפי אזור החפיפה (בגבולות סבירים)
                val prev = meanRgb(out, x0, ov)
                val now = meanRgb(layer, 0, ov)
                val gains = FloatArray(3) { c -> if (now[c] > 1f) (prev[c] / now[c]).coerceIn(0.8f, 1.25f) else 1f }
                paint.colorFilter = android.graphics.ColorMatrixColorFilter(
                    android.graphics.ColorMatrix(floatArrayOf(
                        gains[0], 0f, 0f, 0f, 0f,
                        0f, gains[1], 0f, 0f, 0f,
                        0f, 0f, gains[2], 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                )
                layer.eraseColor(0)
                lc.drawBitmap(tiles[i], null, dst, paint)
                // דהייה הדרגתית בקצה השמאלי של החלק, מעל מה שכבר צויר
                val fade = android.graphics.Paint().apply {
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
                    shader = android.graphics.LinearGradient(0f, 0f, ov.toFloat(), 0f, 0x00000000, 0xFF000000.toInt(), android.graphics.Shader.TileMode.CLAMP)
                }
                lc.drawRect(0f, 0f, w.toFloat(), outH.toFloat(), fade)
            }
            canvas.drawBitmap(layer, x0.toFloat(), 0f, null)
            layer.recycle()
        }
        try {
            worldFile(context, photoKey, serverStyle).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            files.forEach { it.delete() }
        } catch (_: Exception) {
        }
        return out
    }

    /** ממוצע אדום/ירוק/כחול של פס אנכי (x..x+width), בדגימה דלילה */
    private fun meanRgb(bitmap: Bitmap, x: Int, width: Int): FloatArray {
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        val x1 = (x + width).coerceAtMost(bitmap.width)
        var py = 0
        while (py < bitmap.height) {
            var px = x
            while (px < x1) {
                val c = bitmap.getPixel(px, py)
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                px += 6
            }
            py += 6
        }
        return if (n == 0L) floatArrayOf(1f, 1f, 1f) else floatArrayOf(r.toFloat() / n, g.toFloat() / n, b.toFloat() / n)
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

/** מפתח הבעלים (מוקלד בהגדרות בלבד, נשמר במכשיר, לא נכנס לקוד או לצ'אט). מאפשר יצירת AI בלי מכסה */
object OwnerKey {
    private fun prefs(context: Context) = context.getSharedPreferences("owner", Context.MODE_PRIVATE)
    fun get(context: Context): String = prefs(context).getString("key", "") ?: ""
    fun set(context: Context, value: String) { prefs(context).edit().putString("key", value.trim()).apply() }
}

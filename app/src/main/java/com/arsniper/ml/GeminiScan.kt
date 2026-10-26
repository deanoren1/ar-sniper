package com.arsniper.ml

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * שואל את Gemini אילו עצמים בתמונה אפשר להסתתר מאחוריהם, ומקבל תיבות תוחמות.
 * לניסוי בלבד: המפתח נשמר בטלפון. בגרסה להפצה הקריאה צריכה לעבור דרך שרת.
 */
object GeminiScan {

    const val DEFAULT_MODEL = "gemini-2.5-flash"
    private const val MAX_SIDE = 1024

    /** תיבה מנורמלת לתמונה (0..1) ותיאור */
    class Box(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float, val label: String)

    sealed class Result {
        class Boxes(val boxes: List<Box>, val raw: String) : Result()
        class Error(val message: String) : Result()
    }

    private const val PROMPT =
        "You are helping build a cover-based shooting game on top of a photo. Find every object in this photo " +
            "that a small character could hide behind or peek out from: walls corners, pillars, cars, trees, boxes, " +
            "barrels, benches, fences, statues, stalls, doors, large furniture. Ignore sky, floor, people and tiny objects. " +
            "Return a JSON list of at most 25 items. Each item: {\"box_2d\": [ymin, xmin, ymax, xmax], \"label\": \"short name\"} " +
            "with coordinates normalized to 0-1000. Return only the JSON."

    /** חוסם: להריץ מחוץ לתהליך הראשי */
    fun scan(apiKey: String, model: String, source: Bitmap): Result {
        return try {
            val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(source.width, source.height))
            val small = if (scale < 1f) {
                Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
            } else {
                source
            }
            val jpeg = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                    .put(JSONObject().put("inline_data", JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))))
                    .put(JSONObject().put("text", PROMPT)))))
                put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
            }
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
            }
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val text = connection.errorStream?.bufferedReader()?.readText().orEmpty()
                return Result.Error("HTTP $code: ${text.take(400)}")
            }
            val response = JSONObject(connection.inputStream.bufferedReader().readText())
            val parts = response.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
                ?: return Result.Error("No content returned: ${response.toString().take(300)}")
            var text = ""
            for (i in 0 until parts.length()) text += parts.getJSONObject(i).optString("text")
            Result.Boxes(parse(text), text)
        } catch (e: Exception) {
            Result.Error(e.message ?: e.toString())
        }
    }

    /** מפרק את התשובה (גם אם עטופה בגדרות קוד) לתיבות מנורמלות */
    fun parse(raw: String): List<Box> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val array = JSONArray(raw.substring(start, end + 1))
        val out = ArrayList<Box>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val b = item.optJSONArray("box_2d") ?: continue
            if (b.length() < 4) continue
            val ymin = (b.getDouble(0) / 1000.0).toFloat().coerceIn(0f, 1f)
            val xmin = (b.getDouble(1) / 1000.0).toFloat().coerceIn(0f, 1f)
            val ymax = (b.getDouble(2) / 1000.0).toFloat().coerceIn(0f, 1f)
            val xmax = (b.getDouble(3) / 1000.0).toFloat().coerceIn(0f, 1f)
            if (xmax - xmin < 0.01f || ymax - ymin < 0.01f) continue
            out.add(Box(xmin, ymin, xmax, ymax, item.optString("label", "?")))
        }
        return out
    }
}

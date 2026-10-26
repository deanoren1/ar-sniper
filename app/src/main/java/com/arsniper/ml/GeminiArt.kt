package com.arsniper.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * לקוח פשוט ל-API של Gemini לעריכת תמונה (הפיכת תמונה לציור).
 * לניסוי בלבד: המפתח נשמר בטלפון של השחקן. בגרסה להפצה הקריאה צריכה לעבור דרך שרת, כדי שהמפתח לא יהיה באפליקציה.
 */
object GeminiArt {

    const val DEFAULT_MODEL = "gemini-2.5-flash-image"

    /** הצלע הארוכה של התמונה שנשלחת: קטנה יותר = מהיר וזול יותר */
    private const val MAX_SIDE = 1024

    sealed class Result {
        class Image(val bitmap: Bitmap) : Result()
        class Error(val message: String) : Result()
    }

    /** חוסם: להריץ מחוץ לתהליך הראשי */
    fun generate(apiKey: String, model: String, prompt: String, source: Bitmap): Result {
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
                    .put(JSONObject().put("text", prompt))
                    .put(JSONObject().put("inline_data", JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP)))))))
                put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE")))
            }
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 120_000
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
            var note = ""
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                val data = inline?.optString("data").orEmpty()
                if (data.isNotEmpty()) {
                    val bytes = Base64.decode(data, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: return Result.Error("Could not decode the returned image")
                    return Result.Image(bitmap)
                }
                note += part.optString("text")
            }
            Result.Error("No image in the answer. ${note.take(300)}")
        } catch (e: Exception) {
            Result.Error(e.message ?: e.toString())
        }
    }
}

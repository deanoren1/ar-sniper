package com.arsniper.coop

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class CoopStatus { Idle, Connecting, Connected, Closed, Error }

/**
 * חיבור לשרת החדרים (Cloudflare Durable Objects). השרת הוא ממסר: כל הודעה שנשלחת מועברת לשאר החדר כמו שהיא.
 * המארח (role = host) מריץ את המשחק, האורחים מקבלים ממנו מצב ושולחים קלט. הפרוטוקול של השרת מתואר ב-server/src/index.ts.
 *
 * המצב (status, peers, role...) נשמר ב-Compose state, כדי שמסכים יתעדכנו לבד.
 * כתובת השרת נשמרת במכשיר (לא בקוד), ואפשר לשנות אותה במסך CO-OP בלי לבנות מחדש.
 */
object CoopClient {
    private const val PREFS = "coop"
    private const val KEY_SERVER = "server"

    var status by mutableStateOf(CoopStatus.Idle)
        private set
    var code by mutableStateOf("")
        private set
    var role by mutableStateOf("")
        private set
    var myId by mutableStateOf(0)
        private set
    var peers by mutableStateOf(0)
        private set
    var pingMs by mutableStateOf(-1)
        private set
    var message by mutableStateOf("")
        private set

    /** מאזינים להודעות מהשותפים (טקסט ובינארי). רצים על חוט של הרשת, ולא על חוט הממשק */
    var onText: ((String) -> Unit)? = null
    var onBinary: ((ByteArray) -> Unit)? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // חיבור ארוך: בלי קצב זמן לקריאה
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var pingSentAt = 0L

    fun savedServer(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SERVER, "") ?: ""

    fun saveServer(context: Context, server: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SERVER, server.trim()).apply()
    }

    /** מנקה את הכתובת שהוקלדה: בלי https:// ובלי / בסוף (למשל snapboo-rooms.name.workers.dev) */
    private fun host(server: String): String =
        server.trim().removePrefix("https://").removePrefix("http://").removePrefix("wss://").removePrefix("ws://").trimEnd('/')

    /** יוצר חדר חדש אצל השרת ומתחבר אליו כמארח */
    fun createRoom(server: String) {
        val h = host(server)
        if (h.isEmpty()) { fail("Enter the server address first"); return }
        reset(CoopStatus.Connecting, "Creating a room...")
        Thread {
            try {
                val request = Request.Builder().url("https://$h/create").build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) { fail("Server answered ${response.code}"); return@use }
                    val body = response.body?.string().orEmpty()
                    val newCode = JSONObject(body).getString("code")
                    openSocket(h, newCode)
                }
            } catch (e: Exception) {
                fail("Cannot reach the server: ${e.message}")
            }
        }.start()
    }

    /** מצטרף לחדר קיים לפי קוד */
    fun joinRoom(server: String, roomCode: String) {
        val h = host(server)
        val c = roomCode.trim().uppercase()
        if (h.isEmpty()) { fail("Enter the server address first"); return }
        if (c.length < 4) { fail("Enter the room code"); return }
        reset(CoopStatus.Connecting, "Joining $c...")
        openSocket(h, c)
    }

    private fun openSocket(host: String, roomCode: String) {
        code = roomCode
        val request = Request.Builder().url("wss://$host/room/$roomCode").build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text == "pong") {
                    pingMs = (System.currentTimeMillis() - pingSentAt).toInt()
                    return
                }
                if (text.startsWith("{")) {
                    try {
                        val json = JSONObject(text)
                        when (json.optString("t")) {
                            "hello" -> {
                                role = json.optString("role")
                                myId = json.optInt("id")
                                peers = json.optInt("peers")
                                status = CoopStatus.Connected
                                message = if (role == "host") "Room ready. Share the code" else "Joined the room"
                                return
                            }
                            "join", "leave" -> {
                                peers = json.optInt("peers")
                                message = if (json.optString("t") == "join") "A player joined" else "A player left"
                                // ההודעה עוברת גם למאזין, כדי שהמשחק יידע
                            }
                            "closed" -> {
                                status = CoopStatus.Closed
                                message = "The host closed the room"
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
                onText?.invoke(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onBinary?.invoke(bytes.toByteArray())
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (status != CoopStatus.Closed && status != CoopStatus.Error) status = CoopStatus.Closed
                if (message.isEmpty() || status == CoopStatus.Closed) message = "Disconnected"
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val full = response?.code == 409
                fail(if (full) "The room is full" else "Connection failed: ${t.message}")
            }
        })
    }

    fun sendText(text: String): Boolean = socket?.send(text) ?: false

    fun sendBytes(data: ByteArray): Boolean = socket?.send(data.toByteString()) ?: false

    /** כמה בתים ממתינים בתור השליחה (לשליחה איטית של קבצים גדולים) */
    fun queueSize(): Long = socket?.queueSize() ?: 0L

    /** בודק זמן תגובה (הלוך ושוב) לשרת */
    fun ping() {
        pingSentAt = System.currentTimeMillis()
        socket?.send("ping")
    }

    fun disconnect() {
        socket?.close(1000, "bye")
        socket = null
        reset(CoopStatus.Idle, "")
    }

    private fun reset(newStatus: CoopStatus, text: String) {
        socket?.cancel()
        socket = null
        status = newStatus
        message = text
        code = ""
        role = ""
        myId = 0
        peers = 0
        pingMs = -1
    }

    private fun fail(text: String) {
        status = CoopStatus.Error
        message = text
    }
}

package com.arsniper.ui.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.arsniper.data.PhotoLibrary
import com.arsniper.game.progress.ArenaStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.coop.CoopClient
import com.arsniper.coop.CoopStatus
import com.arsniper.coop.CoopSync
import com.arsniper.coop.CoopTransfer
import com.arsniper.ui.components.GameButton
import com.arsniper.ui.components.NavyPanel
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.ScreenHeader
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.ResponsiveBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * משחק משותף (בטא): יצירת חדר, הצטרפות עם קוד, והצגה של מי מחובר.
 * השלב הזה בודק רק את החיבור לשרת. סנכרון המשחק עצמו יתווסף אחר כך.
 */
@Composable
fun CoopScreen(
    onBack: () -> Unit,
    /** מתחילים משחק: מספר ארנה ותפקיד ("host" או "guest") */
    onStart: (style: Int, role: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var server by remember { mutableStateOf(CoopClient.savedServer(context)) }
    var joinCode by remember { mutableStateOf("") }
    val status = CoopClient.status

    // בדיקת זמן תגובה כל כמה שניות כשמחוברים
    LaunchedEffect(status) {
        while (status == CoopStatus.Connected) {
            CoopClient.ping()
            delay(3000)
        }
    }
    DisposableEffect(Unit) {
        CoopSync.keepConnection = false
        CoopSync.pendingStart = null
        CoopTransfer.attach(context)
        onDispose {
            if (CoopSync.keepConnection) {
                // עוברים למשחק: החיבור נשאר, והמשחק לוקח את המאזינים
                CoopTransfer.detach(keepHandlers = true)
            } else {
                CoopTransfer.detach()
                CoopClient.disconnect()
            }
        }
    }

    // אורח: המארח התחיל. בודקים שיש לנו אותה תמונה, ועוברים למשחק
    val pending = CoopSync.pendingStart
    var startError by remember { mutableStateOf("") }
    // המארח בוחר תמונה וארנה (null = אקראית מהפתוחות)
    val library = remember { PhotoLibrary.list(context).filter { PhotoLibrary.isReviewed(it) }.take(12) }
    var chosenName by remember { mutableStateOf<String?>(null) }
    var chosenStyle by remember { mutableStateOf<ArenaStyle?>(null) }
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    var prepText by remember { mutableStateOf("") }
    LaunchedEffect(pending) {
        val info = pending ?: return@LaunchedEffect
        val file = com.arsniper.data.PhotoRepository.currentFile(context)
        val key = try { file?.let { com.arsniper.ml.AnalysisCache.keyFor(it) } } catch (_: Exception) { null }
        if (key == null || key != info.key) {
            startError = "The arena photo did not arrive. Ask the host to press START again"
            CoopSync.pendingStart = null
        } else {
            startError = ""
            CoopSync.pendingStart = null
            onStart(info.style, "guest")
        }
    }

    val fieldColors = TextStyle(color = Color.White, fontSize = 16.sp)

    ResponsiveBox(modifier = modifier.background(GameColors.Navy900)) { ui ->
        com.arsniper.ui.components.MenuBackdrop()
        Column(modifier = Modifier.padding(ui.dp(14f))) {
            ScreenHeader(title = "CO-OP (BETA)", onBack = onBack)
            Spacer(Modifier.height(ui.dp(12f)))
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter
            ) {
                NavyPanel(modifier = Modifier.widthIn(max = ui.dp(520f))) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ui.dp(10f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(ui.dp(16f)).fillMaxWidth()
                    ) {
                        if (status == CoopStatus.Connected) {
                            OutlinedText("ROOM CODE", fontSize = 14.sp, color = Color(0xFFB8C4E0), outline = null)
                            OutlinedText(CoopClient.code, fontSize = 44.sp, color = GameColors.Yellow)
                            OutlinedText(
                                "You are the ${if (CoopClient.role == "host") "HOST" else "GUEST"}  ·  Players: ${CoopClient.peers}/4",
                                fontSize = 16.sp
                            )
                            OutlinedText(
                                if (CoopClient.peers >= 2) "Partner connected" else "Waiting for a partner... share the code",
                                fontSize = 16.sp,
                                color = if (CoopClient.peers >= 2) GameColors.Yellow else Color(0xFFB8C4E0),
                                outline = null
                            )
                            if (CoopClient.pingMs >= 0) {
                                OutlinedText("Ping: ${CoopClient.pingMs} ms", fontSize = 13.sp, color = Color(0xFFB8C4E0), outline = null)
                            }
                            if (CoopClient.role == "host" && CoopClient.peers >= 2) {
                                OutlinedText("PHOTO", fontSize = 12.sp, color = Color(0xFFB8C4E0), outline = null)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                ) {
                                    if (library.isEmpty()) {
                                        OutlinedText("No photos yet. Play a photo first", fontSize = 12.sp, color = Color(0xFFB8C4E0), outline = null)
                                    }
                                    for (file in library) {
                                        val bmp = remember(file.name) { PhotoLibrary.thumbnail(file, 200)?.asImageBitmap() }
                                        val selected = chosenName == file.name
                                        Box(
                                            modifier = Modifier
                                                .size(width = 96.dp, height = 64.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(if (selected) 3.dp else 1.dp, if (selected) GameColors.Yellow else Color(0xFF3D4C7A), RoundedCornerShape(8.dp))
                                                .clickable {
                                                    chosenName = file.name
                                                    PhotoLibrary.select(context, file)
                                                    val scanned = try {
                                                        com.arsniper.ml.AnalysisCache.fileFor(context.filesDir, com.arsniper.ml.AnalysisCache.keyFor(file)).exists()
                                                    } catch (_: Exception) { false }
                                                    startError = if (scanned) "" else "This photo has not been scanned yet. Play it once from My Photos, then come back"
                                                }
                                        ) {
                                            if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        }
                                    }
                                }
                                OutlinedText("ARENA", fontSize = 12.sp, color = Color(0xFFB8C4E0), outline = null)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                ) {
                                    val styles = listOf<ArenaStyle?>(null) + ArenaStyle.values().filter { com.arsniper.data.PlayerProfile.isUnlocked(it) }
                                    for (st in styles) {
                                        val selected = chosenStyle == st
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (selected) Color(0xFF2B5BD7) else Color(0xFF1B2A55))
                                                .border(if (selected) 2.dp else 1.dp, if (selected) GameColors.Yellow else Color(0xFF3D4C7A), RoundedCornerShape(8.dp))
                                                .clickable { chosenStyle = st }
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            OutlinedText(st?.title ?: "RANDOM", fontSize = 12.sp)
                                        }
                                    }
                                }
                                if (CoopTransfer.sendText.isNotEmpty()) {
                                    OutlinedText(CoopTransfer.sendText, fontSize = 13.sp, color = GameColors.Yellow, outline = null)
                                }
                                if (prepText.isNotEmpty()) {
                                    OutlinedText(prepText, fontSize = 13.sp, color = GameColors.Yellow, outline = null)
                                }
                                GameButton(
                                    text = if (preparing) "PREPARING..." else "START GAME",
                                    onClick = {
                                        val file = com.arsniper.data.PhotoRepository.currentFile(context)
                                        if (file == null) {
                                            startError = "No photo yet. Pick a photo first"
                                        } else if (!com.arsniper.ml.AnalysisCache.fileFor(context.filesDir, com.arsniper.ml.AnalysisCache.keyFor(file)).exists()) {
                                            startError = "This photo has not been scanned yet. Play it once from My Photos, then come back"
                                        } else {
                                            startError = ""
                                            val key = com.arsniper.ml.AnalysisCache.keyFor(file)
                                            val open = ArenaStyle.values().filter { com.arsniper.data.PlayerProfile.isUnlocked(it) }
                                            val arena = chosenStyle ?: open.randomOrNull() ?: ArenaStyle.values()[0]
                                            scope.launch {
                                                preparing = true
                                                try {
                                                    // 1. עולם AI (אם יש לארנה): נוצר כאן, לפני ההתחלה, כדי שגם האורח יקבל אותו
                                                    prepText = ""
                                                    val serverStyle = com.arsniper.ml.AiWorld.serverStyle(arena)
                                                    var worldStyle: String? = null
                                                    if (serverStyle != null) {
                                                        prepText = "Preparing the arena world..."
                                                        if (prepareWorld(context, file, key, serverStyle)) worldStyle = serverStyle
                                                    }
                                                    // 2. שליחה אוטומטית של התמונה, הניתוח, הסימונים והעולם, ומחכים שהשותפים יאשרו
                                                    prepText = "Sending the arena to your partner..."
                                                    CoopTransfer.sendCurrent(worldStyle)
                                                    val needed = (CoopClient.peers - 1).coerceAtLeast(1)
                                                    var waited = 0
                                                    var failed = false
                                                    while (CoopTransfer.sending || CoopTransfer.readyCount < needed) {
                                                        delay(200)
                                                        waited++
                                                        val t = CoopTransfer.sendText
                                                        if (!CoopTransfer.sending && (t.startsWith("Sending failed") || t.startsWith("No photo"))) { failed = true; break }
                                                        if (CoopClient.status != CoopStatus.Connected || waited > 900) { failed = true; break }
                                                    }
                                                    if (failed) {
                                                        startError = "Could not send the arena to your partner. Try again"
                                                    } else {
                                                        // 3. כל הנכסים אצל השותפים: מתחילים
                                                        CoopSync.sendStart(arena.ordinal, key)
                                                        onStart(arena.ordinal, "host")
                                                    }
                                                } finally {
                                                    preparing = false
                                                    prepText = ""
                                                }
                                            }
                                        }
                                    },
                                    enabled = !preparing && !CoopTransfer.sending,
                                    style = GameButtonStyle.Primary,
                                    height = 48.dp,
                                    fontSize = 18.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (CoopClient.role != "host" && CoopTransfer.receiveText.isNotEmpty()) {
                                OutlinedText(
                                    CoopTransfer.receiveText,
                                    fontSize = 14.sp,
                                    color = if (CoopTransfer.received) GameColors.Yellow else Color.White,
                                    outline = null
                                )
                            }
                            com.arsniper.ui.components.MicToggle()
                            OutlinedText("Use headphones to avoid echo", fontSize = 11.sp, color = Color(0xFFB8C4E0), outline = null)
                            GameButton(
                                text = "LEAVE ROOM",
                                onClick = { CoopClient.disconnect() },
                                style = GameButtonStyle.Danger,
                                height = 44.dp,
                                fontSize = 16.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            OutlinedText("SERVER ADDRESS", fontSize = 12.sp, color = Color(0xFFB8C4E0), outline = null)
                            OutlinedTextField(
                                value = server,
                                onValueChange = { server = it; CoopClient.saveServer(context, it) },
                                singleLine = true,
                                textStyle = fieldColors,
                                placeholder = { Text("snapboo-rooms.yourname.workers.dev", color = Color(0xFF7F8CB0), fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            GameButton(
                                text = "CREATE ROOM",
                                onClick = { CoopClient.createRoom(server) },
                                enabled = status != CoopStatus.Connecting,
                                style = GameButtonStyle.Success,
                                height = 46.dp,
                                fontSize = 18.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = joinCode,
                                    onValueChange = { joinCode = it.uppercase().take(8) },
                                    singleLine = true,
                                    textStyle = fieldColors,
                                    placeholder = { Text("CODE", color = Color(0xFF7F8CB0)) },
                                    modifier = Modifier.weight(1f)
                                )
                                GameButton(
                                    text = "JOIN",
                                    onClick = { CoopClient.joinRoom(server, joinCode) },
                                    enabled = status != CoopStatus.Connecting,
                                    style = GameButtonStyle.Primary,
                                    height = 46.dp,
                                    fontSize = 18.sp,
                                    modifier = Modifier.widthIn(min = 110.dp)
                                )
                            }
                        }
                        if (startError.isNotEmpty()) {
                            OutlinedText(startError, fontSize = 13.sp, color = Color(0xFFFF8A8A), outline = null)
                        }
                        if (CoopClient.message.isNotEmpty()) {
                            OutlinedText(
                                CoopClient.message,
                                fontSize = 13.sp,
                                color = if (status == CoopStatus.Error) Color(0xFFFF8A8A) else Color(0xFFB8C4E0),
                                outline = null
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * מוודא שיש עולם AI לתמונה ולסגנון: אם אין, מפעיל יצירה ברקע וממתין (עד 4 דקות).
 * מחזיר true רק אם העולם קיים בדיסק. פנורמה רחבה לא מקבלת עולם AI.
 */
private suspend fun prepareWorld(context: android.content.Context, file: java.io.File, key: String, serverStyle: String): Boolean {
    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
    if (opts.outHeight > 0 && opts.outWidth.toFloat() / opts.outHeight >= 2.6f) return false
    val has = { com.arsniper.ml.AiWorld.cachedWorldFile(context, key, serverStyle) != null }
    if (kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { has() }) return true
    val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.arsniper.data.PhotoRepository.load(context) } ?: return false
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.arsniper.ml.AiWorldWorker.enqueue(context, key, serverStyle, bmp) }
    var waited = 0
    while (waited < 240) {
        delay(1000)
        waited++
        if (kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { has() }) return true
        if (kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.arsniper.ml.AiWorldWorker.isDead(context, key, serverStyle) }) return false
    }
    return false
}

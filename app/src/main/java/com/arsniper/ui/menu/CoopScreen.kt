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
import com.arsniper.ml.AiWorld
import com.arsniper.ml.CommunityWorlds
import com.arsniper.ui.arena.CommunityState
import com.arsniper.ui.arena.SavedWorld
import com.arsniper.ui.arena.allSavedWorlds
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
    /** חזרנו מצילום/גלריה וסריקה: בוחרים את התמונה החדשה */
    preselectCurrent: Boolean = false,
    /** צילום חדש (camera) או בחירה מהגלריה: אותו מסלול סקירה וניתוח של המשחק הרגיל */
    onNewPhoto: (camera: Boolean) -> Unit = {},
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
    // מה ה-Host בחר (בבורר המשותף): עולם AI שמור/מהקהילה, או תמונה סרוקה למשחק קלאסי
    val pick = remember { WorldPickerState(if (preselectCurrent) 2 else 0) }
    val picker = com.arsniper.ui.components.rememberGalleryPicker { uri -> com.arsniper.data.PickImport.start(context, uri) }
    val importState = com.arsniper.data.PickImport.state
    LaunchedEffect(importState) {
        when (importState) {
            com.arsniper.data.PickImport.OK -> { com.arsniper.data.PickImport.consume(); onNewPhoto(false) }
            com.arsniper.data.PickImport.FAILED -> { com.arsniper.data.PickImport.consume(); startError = "Could not open that photo" }
        }
    }
    LaunchedEffect(preselectCurrent) {
        if (!preselectCurrent) return@LaunchedEffect
        val cur = com.arsniper.data.PhotoRepository.currentFile(context) ?: return@LaunchedEffect
        val curKey = try { com.arsniper.ml.AnalysisCache.keyFor(cur) } catch (_: Exception) { return@LaunchedEffect }
        val match = library.firstOrNull { try { com.arsniper.ml.AnalysisCache.keyFor(it) == curKey } catch (_: Exception) { false } }
        if (match != null) pick.pickPhoto(match)
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pick.world, pick.photo) { startError = "" }
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
                                WorldPicker(
                                    state = pick,
                                    library = library,
                                    onNewPhoto = onNewPhoto,
                                    onGallery = { picker.launch() },
                                    enabled = !preparing
                                )
                                if (CoopTransfer.sendText.isNotEmpty()) {
                                    OutlinedText(CoopTransfer.sendText, fontSize = 13.sp, color = GameColors.Yellow, outline = null)
                                }
                                if (prepText.isNotEmpty()) {
                                    OutlinedText(prepText, fontSize = 13.sp, color = GameColors.Yellow, outline = null)
                                }
                                GameButton(
                                    text = if (preparing) "SENDING..." else "START GAME",
                                    onClick = {
                                        val w = pick.world
                                        val photo = if (w != null) w.source else pick.photo
                                        if (photo == null) {
                                            startError = "Pick a world or a photo first"
                                        } else if (!photo.exists() || !com.arsniper.ml.AnalysisCache.fileFor(context.filesDir, com.arsniper.ml.AnalysisCache.keyFor(photo)).exists()) {
                                            startError = "This one is not available any more. Pick another"
                                        } else {
                                            val key = com.arsniper.ml.AnalysisCache.keyFor(photo)
                                            val arena = w?.style ?: ArenaStyle.Classic
                                            val worldStyle = if (w != null) AiWorld.serverStyle(w.style) else null
                                            if (worldStyle != null && AiWorld.cachedWorldFile(context, key, worldStyle) == null) {
                                                startError = "This world is not available any more. Pick another"
                                            } else {
                                                startError = ""
                                                PhotoLibrary.select(context, photo)
                                                scope.launch {
                                                    preparing = true
                                                    try {
                                                        // שליחה אוטומטית של התמונה, הניתוח, הסימונים והעולם. מתחילים רק אחרי שכל השותפים אישרו שהכול נשמר אצלם
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
                                                            CoopSync.sendStart(arena.ordinal, key)
                                                            onStart(arena.ordinal, "host")
                                                        }
                                                    } finally {
                                                        preparing = false
                                                        prepText = ""
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    enabled = !preparing && !pick.rolling && !CoopTransfer.sending,
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
                        if (pick.error.isNotEmpty()) {
                            OutlinedText(pick.error, fontSize = 13.sp, color = Color(0xFFFF8A8A), outline = null)
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


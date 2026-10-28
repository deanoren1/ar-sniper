package com.arsniper.ui.menu

import androidx.compose.foundation.background
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
import com.arsniper.ui.components.GameButton
import com.arsniper.ui.components.NavyPanel
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.ScreenHeader
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.ResponsiveBox
import kotlinx.coroutines.delay

/**
 * משחק משותף (בטא): יצירת חדר, הצטרפות עם קוד, והצגה של מי מחובר.
 * השלב הזה בודק רק את החיבור לשרת. סנכרון המשחק עצמו יתווסף אחר כך.
 */
@Composable
fun CoopScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
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
    DisposableEffect(Unit) { onDispose { CoopClient.disconnect() } }

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

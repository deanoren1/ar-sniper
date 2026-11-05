package com.arsniper.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PlayerProfile
import com.arsniper.ui.components.CurrencyBar
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.ScreenHeader
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.ResponsiveBox

/**
 * דף המולטיפלייר: מרכז את מה שקיים (CO-OP נגד מפלצות, בטא) ומציג את מצבי ה-2v2 העתידיים כ"בקרוב".
 * עיצוב בלבד: אין כאן מולטיפלייר חדש.
 */
@Composable
fun MultiplayerScreen(onBack: () -> Unit, onCoop: () -> Unit, modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) { kotlinx.coroutines.delay(2000); message = null }
    }
    ResponsiveBox(modifier = modifier.background(GameColors.Navy900)) { ui ->
        com.arsniper.ui.components.MenuBackdrop()
        Column(modifier = Modifier.fillMaxSize().padding(ui.dp(12f))) {
            ScreenHeader(title = "MULTIPLAYER", onBack = onBack) {
                CurrencyBar(PlayerProfile.coins, PlayerProfile.diamonds)
            }
            Spacer(Modifier.height(ui.dp(6f)))
            OutlinedText("Play together or compete 2v2!", fontSize = 13.sp, color = GameColors.Ice, outline = null)
            Spacer(Modifier.height(ui.dp(10f)))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                ModeTile("TEAM DEATHMATCH", "2v2", Color(0xFF2A6CFF), Modifier.weight(1f)) { message = "2v2 modes are coming soon" }
                ModeTile("CRYSTAL HUNT", "2v2", Color(0xFF8E4DFF), Modifier.weight(1f)) { message = "2v2 modes are coming soon" }
                ModeTile("BOSS RACE", "2v2", Color(0xFFFF6A2B), Modifier.weight(1f)) { message = "2v2 modes are coming soon" }
                ModeTile("CAPTURE THE FLAG", "2v2", Color(0xFF2FCB5B), Modifier.weight(1f)) { message = "2v2 modes are coming soon" }
            }
            Spacer(Modifier.height(ui.dp(10f)))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Banner("CO-OP (BETA)", "Play together vs monsters", Color(0xFF2A8CFF), true, Modifier.weight(1f), onCoop)
                Banner("PVP", "2v2 player vs player (soon)", Color(0xFFD23A4A), false, Modifier.weight(1f)) { message = "PVP is coming soon" }
            }
        }
        message?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                OutlinedText(
                    it, fontSize = 14.sp, outline = null, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 70.dp)
                        .background(Color(0xE6081028), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeTile(title: String, tag: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxHeight().clip(shape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.55f), Color(0xFF0A1230))))
            .border(2.5.dp, color.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick).padding(8.dp)
    ) {
        OutlinedText(tag, fontSize = 22.sp, color = Color.White.copy(alpha = 0.7f), outline = null)
        OutlinedText(title, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f), outline = null, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        OutlinedText("SOON", fontSize = 11.sp, color = GameColors.Yellow, outline = null)
    }
}

@Composable
private fun Banner(title: String, caption: String, color: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = if (enabled) 1f else 0.45f), Color(0xFF0A1230))))
            .border(2.5.dp, color.copy(alpha = if (enabled) 0.9f else 0.4f), shape)
            .clickable(onClick = onClick).padding(vertical = 10.dp)
    ) {
        OutlinedText(title, fontSize = 20.sp, color = Color.White, outlineWidth = 8f)
        OutlinedText(caption, fontSize = 12.sp, color = Color.White, outline = null)
    }
}

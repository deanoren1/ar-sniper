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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PlayerProfile
import com.arsniper.ui.components.GameToggle
import com.arsniper.ui.components.NavyPanel
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.ScreenHeader
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.ResponsiveBox

/** הגדרות: צלילים ורטט. נשמרות בפרופיל ופועלות מיד */
@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    PlayerProfile.revision.hashCode()
    ResponsiveBox(modifier = modifier.background(GameColors.Navy900)) { ui ->
        Column(modifier = Modifier.padding(ui.dp(14f))) {
            ScreenHeader(title = "SETTINGS", onBack = onBack)
            Spacer(Modifier.height(ui.dp(16f)))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                NavyPanel(modifier = Modifier.widthIn(max = ui.dp(460f))) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ui.dp(14f)),
                        modifier = Modifier.padding(ui.dp(18f))
                    ) {
                        SettingRow("SOUND EFFECTS", PlayerProfile.soundOn) { PlayerProfile.soundOn = it }
                        SettingRow("VIBRATION", PlayerProfile.vibrationOn) { PlayerProfile.vibrationOn = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedText(label, fontSize = 20.sp, outline = null, modifier = Modifier.weight(1f))
        GameToggle(checked = checked, onChange = onChange)
    }
}

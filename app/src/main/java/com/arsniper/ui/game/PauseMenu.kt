package com.arsniper.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PlayerProfile
import com.arsniper.ui.components.GameButton
import com.arsniper.ui.components.GameIcon
import com.arsniper.ui.components.GameIconButton
import com.arsniper.ui.components.GameToggle
import com.arsniper.ui.components.NavyPanel
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors

/**
 * תפריט השהיה: המשחק עומד מאחורי. המשך, התחלה מחדש, הגדרות (צליל ורטט) ויציאה לתפריט הראשי.
 * אין כאן לוגיקת משחק: הפעולות מגיעות כפונקציות.
 */
@Composable
fun PauseMenu(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onMainMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSettings by remember { mutableStateOf(false) }
    // "restart" / "menu" כשמחכים לאישור, אחרת null
    var confirm by remember { mutableStateOf<String?>(null) }
    PlayerProfile.revision.hashCode()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xB3081028))
            // בולע מגעים, כדי שלא יגיעו למשחק שמאחור
            .clickable(enabled = true, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { }
    ) {
        NavyPanel(modifier = Modifier.width(300.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedText(
                        if (confirm != null) "SURE?" else if (showSettings) "SETTINGS" else "PAUSED",
                        fontSize = 26.sp,
                        modifier = Modifier.weight(1f)
                    )
                    GameIconButton(GameIcon.Close, onClick = onResume, style = GameButtonStyle.Danger, size = 36.dp)
                }
                if (confirm != null) {
                    OutlinedText(
                        if (confirm == "restart") "Restart this battle? Your progress in it will be lost."
                        else "Leave to the main menu? Your progress in this battle will be lost.",
                        fontSize = 14.sp, outline = null, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    GameButton(
                        "YES", { val c = confirm; confirm = null; if (c == "restart") onRestart() else onMainMenu() },
                        style = GameButtonStyle.Danger, height = 46.dp, fontSize = 18.sp, modifier = Modifier.fillMaxWidth()
                    )
                    GameButton("CANCEL", { confirm = null }, style = GameButtonStyle.Secondary, height = 46.dp, fontSize = 18.sp, modifier = Modifier.fillMaxWidth())
                } else if (showSettings) {
                    ToggleRow("SOUND", PlayerProfile.soundOn) { PlayerProfile.soundOn = it }
                    ToggleRow("VIBRATION", PlayerProfile.vibrationOn) { PlayerProfile.vibrationOn = it }
                    GameButton("BACK", { showSettings = false }, icon = GameIcon.Back, style = GameButtonStyle.Secondary, height = 46.dp, fontSize = 18.sp, modifier = Modifier.fillMaxWidth())
                } else {
                    GameButton("RESUME", onResume, icon = GameIcon.Play, style = GameButtonStyle.Success, height = 46.dp, fontSize = 18.sp, modifier = Modifier.fillMaxWidth())
                    GameButton("RESTART", { confirm = "restart" }, icon = GameIcon.Restart, style = GameButtonStyle.Secondary, height = 46.dp, fontSize = 18.sp, modifier = Modifier.fillMaxWidth())
                    GameButton("SETTINGS", { showSettings = true }, icon = GameIcon.Gear, style = GameButtonStyle.Secondary, height = 46.dp, fontSize = 18.sp, modifier = Modifier.fillMaxWidth())
                    GameButton("MAIN MENU", { confirm = "menu" }, icon = GameIcon.Home, style = GameButtonStyle.Danger, height = 46.dp, fontSize = 18.sp, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedText(label, fontSize = 18.sp, outline = null, modifier = Modifier.weight(1f))
        GameToggle(checked = checked, onChange = onChange)
    }
}

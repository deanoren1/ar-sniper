package com.arsniper.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.arsniper.ui.components.GameButton
import com.arsniper.ui.components.GameToggle
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.components.NavyPanel
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.ScreenHeader
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.ResponsiveBox

/** הגדרות: צלילים ורטט. נשמרות בפרופיל ופועלות מיד */
@Composable
fun SettingsScreen(onBack: () -> Unit, onArtLab: () -> Unit, onScanLab: () -> Unit, onSceneDebug: () -> Unit, onCoop: () -> Unit = {}, modifier: Modifier = Modifier) {
    PlayerProfile.revision.hashCode()
    ResponsiveBox(modifier = modifier.background(GameColors.Navy900)) { ui ->
        com.arsniper.ui.components.MenuBackdrop()
        Column(modifier = Modifier.padding(ui.dp(14f))) {
            ScreenHeader(title = "SETTINGS", onBack = onBack)
            Spacer(Modifier.height(ui.dp(16f)))
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter
            ) {
                NavyPanel(modifier = Modifier.widthIn(max = ui.dp(460f))) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ui.dp(14f)),
                        modifier = Modifier.padding(ui.dp(18f))
                    ) {
                        SettingRow("SOUND EFFECTS", PlayerProfile.soundOn) { PlayerProfile.soundOn = it }
                        SettingRow("VIBRATION", PlayerProfile.vibrationOn) { PlayerProfile.vibrationOn = it }
                        SoundTest()
                        GameButton(
                            text = "CO-OP (BETA)",
                            onClick = onCoop,
                            style = GameButtonStyle.Success,
                            height = 44.dp,
                            fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        GameButton(
                            text = "ART LAB (TEST)",
                            onClick = onArtLab,
                            style = GameButtonStyle.Secondary,
                            height = 44.dp,
                            fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        GameButton(
                            text = "SCENE ANALYSIS (DEBUG)",
                            onClick = onSceneDebug,
                            style = GameButtonStyle.Secondary,
                            height = 44.dp,
                            fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        GameButton(
                            text = "SCAN LAB (TEST)",
                            onClick = onScanLab,
                            style = GameButtonStyle.Secondary,
                            height = 44.dp,
                            fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
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

@Composable
private fun SoundTest() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = androidx.compose.runtime.remember { com.arsniper.audio.SoundPlayer(context) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { player.release() } }
    val items = listOf(
        "BLASTER" to com.arsniper.audio.Sfx.Blaster, "MINIGUN" to com.arsniper.audio.Sfx.Minigun,
        "SNIPER" to com.arsniper.audio.Sfx.SniperShot, "ROCKET" to com.arsniper.audio.Sfx.Rocket,
        "TESLA" to com.arsniper.audio.Sfx.Tesla, "ICE" to com.arsniper.audio.Sfx.Ice,
        "GROWL" to com.arsniper.audio.Sfx.MonsterGrowl, "SPAWN" to com.arsniper.audio.Sfx.MonsterSpawn,
        "SCUTTLE" to com.arsniper.audio.Sfx.MonsterScuttle, "CHITTER" to com.arsniper.audio.Sfx.MonsterChitter,
        "DRAGON ROAR" to com.arsniper.audio.Sfx.DragonRoar, "BOSS ENTRANCE" to com.arsniper.audio.Sfx.BossEntrance,
        "WAVE START" to com.arsniper.audio.Sfx.WaveStartNew, "BOSS WINDUP" to com.arsniper.audio.Sfx.BossWindup,
        "MONSTER HIT" to com.arsniper.audio.Sfx.HitCartoon, "MONSTER POP" to com.arsniper.audio.Sfx.DefeatPop,
        "EXPLOSION" to com.arsniper.audio.Sfx.ExplosionArcade, "FREEZE" to com.arsniper.audio.Sfx.Freeze,
        "COIN" to com.arsniper.audio.Sfx.CoinPickup, "GEM" to com.arsniper.audio.Sfx.GemPickup,
        "COMBO UP" to com.arsniper.audio.Sfx.ComboUp, "BOSS HIT" to com.arsniper.audio.Sfx.BossHit,
        "CLICK" to com.arsniper.audio.Sfx.UiClick, "WEAPON SELECT" to com.arsniper.audio.Sfx.WeaponSelect,
        "RELOAD" to com.arsniper.audio.Sfx.ReloadNew, "UPGRADE" to com.arsniper.audio.Sfx.UpgradeOk,
        "CHEST" to com.arsniper.audio.Sfx.ChestOpen, "VICTORY" to com.arsniper.audio.Sfx.Victory,
        "DEFEAT" to com.arsniper.audio.Sfx.Defeat, "COUNTDOWN" to com.arsniper.audio.Sfx.CountTick,
        "PAUSE/RESUME" to com.arsniper.audio.Sfx.PauseResume
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedText("SOUND TEST", fontSize = 18.sp, outline = null)
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (name, sfx) ->
                    GameButton(
                        text = name,
                        onClick = { player.play(sfx) },
                        style = GameButtonStyle.Secondary,
                        height = 38.dp,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

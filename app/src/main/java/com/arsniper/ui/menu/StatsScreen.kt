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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PlayerProfile
import com.arsniper.game.progress.ArenaStyle
import com.arsniper.ui.components.CurrencyPill
import com.arsniper.ui.components.NavyPanel
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.ScreenHeader
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.ResponsiveBox

/** סטטיסטיקה של השחקן: ריצות, ניצחונות, הגל הטוב ביותר, כוכבים ורמה */
@Composable
fun StatsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    PlayerProfile.revision.hashCode()
    val maxStars = ArenaStyle.values().size * 3
    ResponsiveBox(modifier = modifier.background(GameColors.Navy900)) { ui ->
        Column(modifier = Modifier.padding(ui.dp(14f))) {
            ScreenHeader(title = "STATISTICS", onBack = onBack) {
                CurrencyPill(PlayerProfile.coins, isGems = false)
                CurrencyPill(PlayerProfile.diamonds, isGems = true)
            }
            Spacer(Modifier.height(ui.dp(14f)))
            val tiles = listOf(
                "LEVEL" to "${PlayerProfile.level}",
                "GAMES PLAYED" to "${PlayerProfile.gamesPlayed}",
                "VICTORIES" to "${PlayerProfile.wins}",
                "BEST WAVE" to "${PlayerProfile.bestWave}",
                "BEST RUN (COINS)" to "${PlayerProfile.bestRunCoins}",
                "ARENA STARS" to "${PlayerProfile.totalStars}/$maxStars"
            )
            Column(verticalArrangement = Arrangement.spacedBy(ui.dp(12f))) {
                for (row in tiles.chunked(3)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ui.dp(12f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for ((label, value) in row) {
                            NavyPanel(modifier = Modifier.weight(1f)) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = ui.dp(14f))
                                ) {
                                    OutlinedText(value, fontSize = (ui.factor * 34f).sp, color = GameColors.Yellow)
                                    OutlinedText(label, fontSize = 13.sp, color = GameColors.Ice, outline = null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

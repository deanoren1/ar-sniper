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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PlayerProfile
import com.arsniper.game.progress.Goal
import com.arsniper.game.progress.Goals
import com.arsniper.ui.components.CurrencyBar
import com.arsniper.ui.components.GameButton
import com.arsniper.ui.components.NavyPanel
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.ScreenHeader
import com.arsniper.ui.game.CoinIcon
import com.arsniper.ui.game.DiamondIcon
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.ResponsiveBox

/** יעדים: רשימת משימות עם התקדמות וכפתור CLAIM לפרס, ושורת נתונים קצרה למעלה */
@Composable
fun StatsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    PlayerProfile.revision.hashCode()
    ResponsiveBox(modifier = modifier.background(GameColors.Navy900)) { ui ->
        com.arsniper.ui.components.MenuBackdrop()
        Column(modifier = Modifier.padding(ui.dp(14f))) {
            ScreenHeader(title = "GOALS", onBack = onBack) {
                CurrencyBar(PlayerProfile.coins, PlayerProfile.diamonds)
            }
            Spacer(Modifier.height(ui.dp(8f)))
            val rate = if (PlayerProfile.gamesPlayed > 0) "${PlayerProfile.wins * 100 / PlayerProfile.gamesPlayed}%" else "-"
            Row(horizontalArrangement = Arrangement.spacedBy(ui.dp(8f)), modifier = Modifier.fillMaxWidth()) {
                for ((label, value) in listOf(
                    "GAMES" to "${PlayerProfile.gamesPlayed}",
                    "WINS" to "${PlayerProfile.wins}",
                    "WIN RATE" to rate,
                    "BEST WAVE" to "${PlayerProfile.bestWave}",
                    "STARS" to "${PlayerProfile.totalStars}/27"
                )) {
                    NavyPanel(modifier = Modifier.weight(1f)) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = ui.dp(5f))
                        ) {
                            OutlinedText(value, fontSize = (ui.factor * 20f).sp, color = GameColors.Yellow)
                            OutlinedText(label, fontSize = 11.sp, color = GameColors.Ice, outline = null)
                        }
                    }
                }
            }
            Spacer(Modifier.height(ui.dp(8f)))
            // אפשר לאסוף קודם, ואז הלא-גמורים, ובסוף שנאספו
            val goals = Goals.all.sortedBy {
                when {
                    PlayerProfile.canClaim(it) -> 0
                    PlayerProfile.isClaimed(it) -> 2
                    else -> 1
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(ui.dp(6f)),
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            ) {
                goals.forEach { GoalRow(it, ui.factor) }
            }
        }
    }
}

@Composable
private fun GoalRow(goal: Goal, factor: Float) {
    val value = PlayerProfile.metricValue(goal.metric).coerceAtMost(goal.target)
    val claimed = PlayerProfile.isClaimed(goal)
    val ready = PlayerProfile.canClaim(goal)
    NavyPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                OutlinedText(goal.title, fontSize = 15.sp, outline = null)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(150.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(GameColors.Navy900)
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((value.toFloat() / goal.target).coerceIn(0.02f, 1f))
                                .background(if (ready || claimed) GameColors.Yellow else GameColors.Sky)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedText("$value/${goal.target}", fontSize = 12.sp, color = GameColors.Ice, outline = null)
                }
            }
            if (goal.reward.coins > 0) {
                CoinIcon(size = 22.dp)
                Spacer(Modifier.width(4.dp))
                OutlinedText("${goal.reward.coins}", fontSize = 16.sp, color = GameColors.Yellow, outline = null)
            }
            if (goal.reward.gems > 0) {
                DiamondIcon(size = 22.dp)
                Spacer(Modifier.width(4.dp))
                OutlinedText("${goal.reward.gems}", fontSize = 16.sp, color = GameColors.Yellow, outline = null)
            }
            Spacer(Modifier.width(12.dp))
            GameButton(
                text = if (claimed) "DONE" else "CLAIM",
                onClick = { PlayerProfile.claim(goal) },
                style = if (ready) GameButtonStyle.Success else GameButtonStyle.Locked,
                enabled = ready,
                height = 38.dp,
                fontSize = 14.sp,
                modifier = Modifier.width(92.dp)
            )
        }
    }
}

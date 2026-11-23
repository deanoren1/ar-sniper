package com.arsniper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PlayerProfile
import com.arsniper.game.progress.Difficulty
import com.arsniper.ui.theme.GameColors

/** בחירת רמת קושי: EASY / NORMAL / HARD. נשמרת בפרופיל ומשפיעה על המשחק הבא */
@Composable
fun DifficultyPills(modifier: Modifier = Modifier) {
    PlayerProfile.revision.hashCode()
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        for (d in Difficulty.values()) {
            DifficultyPill(d, PlayerProfile.difficulty == d) { PlayerProfile.difficulty = d }
        }
    }
}

@Composable
private fun DifficultyPill(d: Difficulty, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val top = when (d) {
        Difficulty.EASY -> Color(0xFF45DB55)
        Difficulty.NORMAL -> Color(0xFF2F8BFF)
        Difficulty.HARD -> Color(0xFFFF5A52)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) top else Color(0xFF1C2A55))
            .border(2.dp, if (selected) Color.White else GameColors.Outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        OutlinedText(d.title, fontSize = 11.sp, outline = null, color = if (selected) Color.White else GameColors.Ice)
    }
}

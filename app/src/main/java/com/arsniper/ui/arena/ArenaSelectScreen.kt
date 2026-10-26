package com.arsniper.ui.arena

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PhotoRepository
import com.arsniper.data.PlayerProfile
import com.arsniper.game.progress.ArenaStyle
import com.arsniper.ui.game.ArenaStyleLayer
import com.arsniper.ui.components.CurrencyBar
import com.arsniper.ui.components.GameIcon
import com.arsniper.ui.components.IconGlyph
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.ScreenHeader
import com.arsniper.ui.components.StarRow
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.ResponsiveBox
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.withFrameNanos

/** בחירת ארנה: מפת איים עם דרכים. ארנה נפתחת כשמנצחים באחד משכניה במפה בכוכב אחד לפחות */
@Composable
fun ArenaSelectScreen(
    onPick: (ArenaStyle) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlayerProfile.revision.hashCode()
    var hint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hint) {
        if (hint != null) {
            kotlinx.coroutines.delay(2500)
            hint = null
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(GameColors.Navy900)) {
        val boxWidth = constraints.maxWidth
        val boxHeight = constraints.maxHeight
        Image(
            painter = painterResource(com.arsniper.R.drawable.arena_map),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
        for (style in ArenaStyle.values()) {
            ArenaNode(
                style = style,
                modifier = Modifier.layout { measurable, c ->
                    val placeable = measurable.measure(c.copy(minWidth = 0, minHeight = 0))
                    layout(placeable.width, placeable.height) {
                        placeable.place(
                            (style.mapX * boxWidth - placeable.width / 2f).toInt(),
                            (style.mapY * boxHeight - placeable.height / 2f).toInt()
                        )
                    }
                },
                onClick = {
                    if (PlayerProfile.isUnlocked(style)) onPick(style)
                    else hint = "Win a neighboring arena with at least 1 star to open ${style.title}"
                }
            )
        }
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 2.dp)) {
            com.arsniper.ui.components.GameIconButton(
                icon = GameIcon.Back,
                onClick = onBack,
                style = com.arsniper.ui.theme.GameButtonStyle.Primary,
                size = 32.dp,
                modifier = Modifier.align(Alignment.TopStart)
            )
            Row(modifier = Modifier.align(Alignment.TopEnd)) {
                CurrencyBar(PlayerProfile.coins, PlayerProfile.diamonds)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedText("SELECT", fontSize = 26.sp, color = Color.White)
                    OutlinedText("ARENA", fontSize = 26.sp, color = GameColors.Yellow)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    for (d in com.arsniper.game.progress.Difficulty.values()) {
                        DifficultyPill(d, PlayerProfile.difficulty == d) { PlayerProfile.difficulty = d }
                    }
                }
            }
        }
        hint?.let {
            OutlinedText(
                text = it,
                fontSize = 14.sp,
                outline = null,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE6081028))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

/** שלט של אי במפה: שם, כוכבים, ומנעול כשהארנה סגורה */
@Composable
private fun ArenaNode(style: ArenaStyle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val unlocked = PlayerProfile.isUnlocked(style)
    val stars = PlayerProfile.starsOf(style)
    val shape = RoundedCornerShape(9.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.clickable(onClick = onClick)) {
        if (!unlocked) IconGlyph(GameIcon.Lock, size = 20.dp, tint = GameColors.Ice)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(shape)
                .background(if (unlocked) Color(0xE6102050) else Color(0xCC0A1230))
                .border(1.5.dp, if (unlocked) GameColors.Yellow else GameColors.Outline, shape)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            OutlinedText(style.title, fontSize = 10.sp, outline = null, color = if (unlocked) Color.White else GameColors.Ice)
            StarRow(stars = stars, size = 11.dp)
        }
    }
}

@Composable
private fun DifficultyPill(d: com.arsniper.game.progress.Difficulty, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val top = when (d) {
        com.arsniper.game.progress.Difficulty.EASY -> Color(0xFF45DB55)
        com.arsniper.game.progress.Difficulty.NORMAL -> Color(0xFF2F8BFF)
        com.arsniper.game.progress.Difficulty.HARD -> Color(0xFFFF5A52)
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

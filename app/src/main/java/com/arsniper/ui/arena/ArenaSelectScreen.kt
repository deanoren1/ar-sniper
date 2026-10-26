package com.arsniper.ui.arena

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
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

/** בחירת ארנה: אותה תמונה בשמונה סגנונות. ארנה נפתחת כשמנצחים את הקודמת בכוכב אחד לפחות */
@Composable
fun ArenaSelectScreen(
    onPick: (ArenaStyle) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val photo = remember { PhotoRepository.load(context)?.asImageBitmap() }
    var now by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { now = it }
    }
    PlayerProfile.revision.hashCode()
    val styles = ArenaStyle.values().toList()

    ResponsiveBox(modifier = modifier.background(GameColors.Navy900)) { ui ->
        Column(modifier = Modifier.fillMaxSize().padding(ui.dp(12f))) {
            ScreenHeader(title = "SELECT ARENA", onBack = onBack) {
                CurrencyBar(PlayerProfile.coins, PlayerProfile.diamonds)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(ui.dp(8f)),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = ui.dp(6f)),
            ) {
                Spacer(Modifier.weight(1f))
                OutlinedText("DIFFICULTY", fontSize = (ui.factor * 13f).sp, color = GameColors.Ice, outline = null)
                for (d in com.arsniper.game.progress.Difficulty.values()) {
                    DifficultyPill(d, PlayerProfile.difficulty == d) { PlayerProfile.difficulty = d }
                }
                Spacer(Modifier.weight(1f))
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = ui.dp(6f)),
                verticalArrangement = Arrangement.spacedBy(ui.dp(10f))
            ) {
                for (rowStyles in styles.chunked(4)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(ui.dp(10f))
                    ) {
                        for (style in rowStyles) {
                            ArenaCard(
                                style = style,
                                photo = photo,
                                now = now,
                                onPick = onPick,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArenaCard(
    style: ArenaStyle,
    photo: androidx.compose.ui.graphics.ImageBitmap?,
    now: Long,
    onPick: (ArenaStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val unlocked = PlayerProfile.isUnlocked(style)
    val stars = PlayerProfile.starsOf(style)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(GameColors.Navy800)
            .border(3.dp, if (unlocked) GameColors.Yellow else GameColors.Outline, shape)
            .clickable(enabled = unlocked) { onPick(style) }
    ) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        ArenaStyleLayer(style = style, nowNanos = now, particleScale = 0.5f)
        if (!unlocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC081028)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GameColors.Navy700)
                            .border(3.dp, GameColors.Outline, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) { IconGlyph(GameIcon.Lock, size = 26.dp, tint = GameColors.Ice) }
                    OutlinedText(
                        text = "Beat ${style.previous?.title ?: ""}",
                        fontSize = 11.sp,
                        color = GameColors.Ice,
                        outline = null,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, start = 6.dp, end = 6.dp)
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xB3081028))
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedText(style.title, fontSize = 13.sp, outlineWidth = 5f)
            StarRow(stars = stars, size = 18.dp)
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
            .padding(horizontal = 14.dp, vertical = 5.dp)
    ) {
        OutlinedText(d.title, fontSize = 13.sp, outline = null, color = if (selected) Color.White else GameColors.Ice)
    }
}

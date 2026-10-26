package com.arsniper.ui.arena

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14))
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CHOOSE YOUR ARENA",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "BACK",
                color = Color(0xFFB0BEC5),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (rowStyles in styles.chunked(4)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF151B22))
            .border(2.dp, if (unlocked) Color(style.tint.toInt()) else Color(0xFF37424D), shape)
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
                    .background(Color(0xCC0B0F14)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LOCKED", color = Color(0xFFB0BEC5), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Beat ${style.previous?.title ?: ""}",
                        color = Color(0xFF78909C),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(style.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row {
                for (i in 1..3) {
                    Text(
                        text = "★",
                        color = if (i <= stars) Color(0xFFFFD54F) else Color(0xFF546E7A),
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

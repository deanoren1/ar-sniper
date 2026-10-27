package com.arsniper.ui.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** פקדי זום בעיצוב ה-HUD החדש: מינוס, רמת הזום, פלוס. המצב והלחיצות כמו קודם. */
@Composable
fun ZoomControls(
    zoom: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(width = 190.dp, height = 48.dp)) {
        GlowPill(Modifier.fillMaxSize())
        GlowRoundButton(plus = false, modifier = Modifier.align(Alignment.CenterStart).offset(x = 4.dp).size(40.dp))
        GlowRoundButton(plus = true, modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-4).dp).size(40.dp))
        Text(
            text = "x${(zoom * 10).roundToInt() / 10f}",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
        Box(
            modifier = Modifier.align(Alignment.CenterStart).offset(x = 8.dp)
                .size(44.dp).clip(CircleShape).clickable(onClick = onZoomOut)
        )
        Box(
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-8).dp)
                .size(44.dp).clip(CircleShape).clickable(onClick = onZoomIn)
        )
    }
}

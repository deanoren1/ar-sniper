package com.arsniper.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** הודעת HIT */
@Composable
fun HitIndicator(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (visible) {
        Text(
            text = "HIT",
            color = Color(0xFFFFEB3B),
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            modifier = modifier
        )
    }
}

/** תצוגת ניקוד */
@Composable
fun ScoreDisplay(
    score: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = "SCORE: $score",
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

package com.arsniper.ui.game

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** הודעת HIT. בשלב 7 נוסיף לכאן גם ניקוד. */
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

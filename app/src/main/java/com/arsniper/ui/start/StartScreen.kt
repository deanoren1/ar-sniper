package com.arsniper.ui.start

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StartScreen(
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "AR SNIPER",
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onStart,
                modifier = Modifier
                    .width(220.dp)
                    .height(64.dp)
            ) {
                Text(text = "START", fontSize = 24.sp)
            }
        }
    }
}

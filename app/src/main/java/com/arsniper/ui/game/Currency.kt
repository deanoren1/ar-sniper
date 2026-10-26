package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** מטבע זהב מצויר ידנית (לא אימוג'י, שמוצג שונה בכל מכשיר) */
@Composable
fun CoinIcon(size: Dp = 22.dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.arsniper.R.drawable.icon_coin),
        contentDescription = null,
        modifier = modifier.size(size)
    )
}

/** יהלום: האיור של המשחק */
@Composable
fun DiamondIcon(size: Dp = 22.dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.arsniper.R.drawable.icon_gem),
        contentDescription = null,
        modifier = modifier.size(size)
    )
}

/** מונה מטבעות וגם יהלומים, בפינה של המסך */
@Composable
fun CurrencyBar(coins: Int, diamonds: Int, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        CoinIcon()
        Spacer(Modifier.width(6.dp))
        Text(text = "$coins", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(16.dp))
        DiamondIcon()
        Spacer(Modifier.width(4.dp))
        Text(text = "$diamonds", color = Color(0xFF84FFFF), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

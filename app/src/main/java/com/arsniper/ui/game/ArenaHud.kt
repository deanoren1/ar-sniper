package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** לב אחד, מצויר ידנית (לא טקסט: בסמסונג התו ♥ מוצג כאימוג'י אדום תמיד, בלי קשר לצבע) */
@Composable
fun HeartIcon(filled: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 34.dp, height = 30.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0.5f * w, 0.95f * h)
            cubicTo(-0.1f * w, 0.5f * h, 0.1f * w, -0.05f * h, 0.5f * w, 0.3f * h)
            cubicTo(0.9f * w, -0.05f * h, 1.1f * w, 0.5f * h, 0.5f * w, 0.95f * h)
            close()
        }
        drawPath(path, if (filled) Color(0xFFFF1744) else Color(0xFF555555))
    }
}

/** לבבות החיים: מלאים באדום, ריקים באפור */
@Composable
fun HeartsRow(hearts: Int, max: Int, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        for (i in 0 until max) {
            HeartIcon(filled = i < hearts)
        }
    }
}

/** מצב הגל: מספר הגל וכמה מפלצות נשארו */
@Composable
fun WaveStatus(wave: Int, waves: Int, left: Int, modifier: Modifier = Modifier) {
    Text(
        text = "WAVE $wave/$waves  ·  $left left",
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

/** כרזה גדולה באמצע המסך (תחילת גל, סוף גל) */
@Composable
fun ArenaBanner(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 36.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            color = Color(0xFFFFEB3B),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** הבהוב אדום על כל המסך כשהשחקן נפגע. alpha 0 = לא נראה. לא תופס לחיצות */
@Composable
fun DamageFlash(alpha: Float, modifier: Modifier = Modifier) {
    if (alpha > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFFF1744).copy(alpha = alpha.coerceIn(0f, 0.7f)))
        )
    }
}

/** מסך סיום: ניצחון (עם כוכבים) או הפסד, וכפתור לנסות שוב */
@Composable
fun EndOverlay(
    won: Boolean,
    stars: Int,
    score: Int,
    wave: Int,
    diamondsGained: Int,
    totalCoins: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (won) "ARENA CLEAR!" else "GAME OVER",
                color = if (won) Color(0xFFFFEB3B) else Color(0xFFFF5252),
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold
            )
            if (won) {
                Text(
                    text = "★".repeat(stars) + "☆".repeat(3 - stars),
                    color = Color(0xFFFFD600),
                    fontSize = 44.sp
                )
            } else {
                Text(
                    text = "You reached wave $wave",
                    color = Color.White,
                    fontSize = 20.sp
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                CoinIcon(size = 28.dp)
                Text(
                    text = "  +$score",
                    color = Color(0xFFFFD54F),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                if (diamondsGained > 0) {
                    Text(text = "     ", fontSize = 28.sp)
                    DiamondIcon(size = 28.dp)
                    Text(
                        text = "  +$diamondsGained",
                        color = Color(0xFF84FFFF),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = "Total: $totalCoins coins",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text(text = "PLAY AGAIN", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}


/** קו החיים של הבוס: פס רחב עם שם, שנהיה אדום ורועד קלות כשהוא כועס. fraction = החיים שנשארו (0..1) */
@Composable
fun BossBar(fraction: Float, enraged: Boolean, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = if (enraged) "DRAGON  -  ENRAGED" else "DRAGON",
            color = if (enraged) Color(0xFFFF5252) else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .width(300.dp)
                .height(16.dp)
                .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x33FFFFFF), RoundedCornerShape(6.dp))
            )
            val shown = fraction.coerceIn(0f, 1f)
            if (shown > 0f) {
                Box(
                    modifier = Modifier
                        .width((296f * shown).dp)
                        .height(12.dp)
                        .background(
                            if (enraged) Color(0xFFD50000) else Color(0xFFFF9800),
                            RoundedCornerShape(6.dp)
                        )
                )
            }
            // סימן בשליש: מתי הוא נהיה כועס
            Box(
                modifier = Modifier
                    .padding(start = (296f / 3f).dp)
                    .width(2.dp)
                    .height(12.dp)
                    .background(Color(0xCCFFFFFF))
            )
        }
    }
}

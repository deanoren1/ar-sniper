package com.arsniper.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import com.arsniper.ui.components.CurrencyPill
import com.arsniper.ui.components.GameButton
import com.arsniper.ui.components.GameIcon
import com.arsniper.ui.components.GameIconButton
import com.arsniper.ui.components.NavyPanel
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.StarRow
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors
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

/** מסך סיום: ניצחון (עם כוכבים) או הפסד. REVIVE ביהלומים, PLAY AGAIN, ויציאה לתפריט */
@Composable
fun EndOverlay(
    won: Boolean,
    stars: Int,
    score: Int,
    wave: Int,
    diamondsGained: Int,
    totalCoins: Int,
    reviveCost: Int,
    diamonds: Int,
    onRevive: () -> Unit,
    onRetry: () -> Unit,
    onMenu: () -> Unit = {},
    bonusText: String? = null,
    difficultyTitle: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xB3081028))
    ) {
        NavyPanel(modifier = Modifier.width(380.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                OutlinedText(
                    text = if (won) "ARENA CLEAR!" else "GAME OVER",
                    fontSize = 36.sp,
                    color = if (won) GameColors.Yellow else Color(0xFFFF6E6E),
                    outlineWidth = 10f
                )
                if (won) {
                    StarRow(stars = stars, size = 40.dp)
                } else {
                    OutlinedText("You reached wave $wave", fontSize = 18.sp, outline = null)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CurrencyPill(amount = score, isGems = false)
                    if (diamondsGained > 0) CurrencyPill(amount = diamondsGained, isGems = true)
                }
                if (bonusText != null) {
                    OutlinedText(bonusText, fontSize = 14.sp, color = GameColors.Yellow, outline = null)
                }
                OutlinedText(
                    "Total: $totalCoins coins" + (if (difficultyTitle != null) "  \u00B7  $difficultyTitle" else ""),
                    fontSize = 14.sp, color = GameColors.Ice, outline = null
                )
                if (!won && reviveCost > 0) {
                    GameButton(
                        text = "REVIVE  $reviveCost GEMS",
                        onClick = onRevive,
                        enabled = diamonds >= reviveCost,
                        style = GameButtonStyle.Secondary,
                        height = 46.dp,
                        fontSize = 18.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (diamonds < reviveCost) {
                        OutlinedText("Need $reviveCost gems (you have $diamonds)", fontSize = 12.sp, color = GameColors.Sky, outline = null)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GameIconButton(GameIcon.Home, onClick = onMenu, style = GameButtonStyle.Secondary, size = 50.dp)
                    GameButton(
                        text = "PLAY AGAIN",
                        onClick = onRetry,
                        icon = GameIcon.Restart,
                        style = GameButtonStyle.Success,
                        height = 50.dp,
                        fontSize = 20.sp,
                        modifier = Modifier.width(220.dp)
                    )
                }
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

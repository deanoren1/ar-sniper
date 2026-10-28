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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.Image
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

/** לבבות החיים בעיצוב ה-HUD החדש: תא עם לבבות אדומים (חיים) ואפורים (אבדו) */
@Composable
fun HeartsRow(hearts: Int, max: Int, modifier: Modifier = Modifier) {
    val k = 190f / 400f
    Box(modifier = modifier.size(width = 190.dp, height = 46.dp)) {
        GlowPill(Modifier.fillMaxSize())
        for (i in 0 until max) {
            val fx = 0.5f + (i - (max - 1) / 2f) * 0.275f
            HudImage(
                if (i < hearts) com.arsniper.R.drawable.hud_heart_red else com.arsniper.R.drawable.hud_heart_gray,
                Modifier
                    .size(width = (96f * k).dp, height = (75f * k).dp)
                    .offset(x = (190f * fx - 96f * k / 2f).dp, y = (23f - 75f * k / 2f).dp)
            )
        }
    }
}

/** מצב הגל: מספר הגל וכמה מפלצות נשארו */
@Composable
fun WaveStatus(wave: Int, waves: Int, left: Int, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(width = 215.dp, height = 33.dp)
    ) {
        GlowPill(Modifier.fillMaxSize())
        Row {
            Text(text = "WAVE $wave/$waves  \u00B7  ", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(text = "$left LEFT", color = Color(0xFFFFEB3B), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** כרזה גדולה באמצע המסך (תחילת גל, סוף גל). משתמשת בתמונות הבאנר; כותרת לא מוכרת חוזרת לטקסט */
@Composable
fun ArenaBanner(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    val waveNumber = if (title.startsWith("WAVE ") && title != "WAVE CLEAR!") title.removePrefix("WAVE ").trim().toIntOrNull() else null
    val bannerRes = when {
        waveNumber != null -> com.arsniper.R.drawable.banner_wave
        title == "BOSS!" -> com.arsniper.R.drawable.banner_boss
        title == "WAVE CLEAR!" -> com.arsniper.R.drawable.banner_clear
        title == "ENRAGED!" -> com.arsniper.R.drawable.banner_enraged
        else -> null
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        if (bannerRes != null) {
            val bannerW = 300.dp
            val k = bannerW.value / 481f   // סקאלה לפי באנר ה-WAVE (481 פיקסלים רוחב)
            Box(modifier = Modifier.width(bannerW)) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(bannerRes),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
                if (waveNumber != null) {
                    // החלון הריק של הבאנר: x 315-416, y 61-141 (בפיקסלים של התמונה)
                    val digits = waveNumber.toString().map { it - '0' }
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .offset(x = (315 * k).dp, y = (61 * k).dp)
                            .width((101 * k).dp)
                            .height((80 * k).dp)
                    ) {
                        for (d in digits) {
                            Image(
                                painter = androidx.compose.ui.res.painterResource(LabelArt.goldRes(d)),
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                modifier = Modifier
                                    .height((70 * k).dp)
                                    .width((if (digits.size > 1) 44 * k else 60 * k).dp)
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = title,
                color = Color(0xFFFFEB3B),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (subtitle != null) {
            OutlinedText(
                text = subtitle,
                fontSize = 14.sp,
                color = Color.White
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
    onNext: (() -> Unit)? = null,
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
        if (!won) GameOverPanel(
            wave = wave, score = score, totalCoins = totalCoins, diamonds = diamonds,
            reviveCost = reviveCost, difficultyTitle = difficultyTitle,
            onRevive = onRevive, onRetry = onRetry, onMenu = onMenu
        ) else NavyPanel(modifier = Modifier.width(380.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
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
                OutlinedText("EARNED THIS GAME", fontSize = 12.sp, color = GameColors.Ice, outline = null)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CurrencyPill(amount = score, isGems = false, prefix = "+")
                    if (diamondsGained > 0) CurrencyPill(amount = diamondsGained, isGems = true, prefix = "+")
                }
                if (bonusText != null) {
                    OutlinedText(bonusText, fontSize = 14.sp, color = GameColors.Yellow, outline = null)
                }
                OutlinedText(
                    "Total: $totalCoins coins  \u00B7  $diamonds gems" + (if (difficultyTitle != null) "  \u00B7  $difficultyTitle" else ""),
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
                if (won && onNext != null) {
                    GameButton(
                        text = "NEXT ARENA",
                        onClick = onNext,
                        icon = GameIcon.Play,
                        style = GameButtonStyle.Primary,
                        height = 50.dp,
                        fontSize = 20.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
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


/**
 * מסך GAME OVER מהעיצוב החדש: התמונה היא המסגרת, והמספרים והכפתורים מצוירים עליה.
 * עם revive: התמונה המלאה. בלי revive: גרסה בלי שורת ההחייאה.
 * הקואורדינטות בפיקסלים של התמונה (950 רוחב).
 */
@Composable
private fun GameOverPanel(
    wave: Int,
    score: Int,
    totalCoins: Int,
    diamonds: Int,
    reviveCost: Int,
    difficultyTitle: String?,
    onRevive: () -> Unit,
    onRetry: () -> Unit,
    onMenu: () -> Unit
) {
    val canRevive = reviveCost > 0
    val imgH = if (canRevive) 925f else 798f
    val shift = if (canRevive) 0f else -127f
    androidx.compose.foundation.layout.BoxWithConstraints(contentAlignment = Alignment.Center) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val hPx = constraints.maxHeight * 0.97f
        val wPx = minOf(hPx * 950f / imgH, constraints.maxWidth * 0.86f)
        val k = wPx / 950f
        fun u(px: Float) = with(density) { (px * k).toDp() }
        fun f(px: Float) = with(density) { (px * k).toSp() }
        Box(modifier = Modifier.width(u(950f)).height(u(imgH))) {
            Image(
                painter = androidx.compose.ui.res.painterResource(
                    if (canRevive) com.arsniper.R.drawable.gameover_panel_revive else com.arsniper.R.drawable.gameover_panel
                ),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            // You reached wave N
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.offset(x = u(130f), y = u(296f)).width(u(690f)).height(u(60f))
            ) {
                OutlinedText("You reached wave ", fontSize = f(40f), outline = null)
                OutlinedText("$wave", fontSize = f(40f), color = Color(0xFFFFD43B), outline = null)
            }
            // מטבעות שהרווחת
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.offset(x = u(556f), y = u(447f)).width(u(140f)).height(u(64f))
            ) {
                OutlinedText(
                    "$score",
                    fontSize = f(if ("$score".length > 4) 38f else 46f),
                    outline = null
                )
            }
            // סה"כ
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.offset(x = u(415f), y = u(536f)).width(u(385f)).height(u(40f))
            ) {
                OutlinedText(
                    "Total: $totalCoins coins" + (if (difficultyTitle != null) "  \u00B7  " else ""),
                    fontSize = f(28f), color = Color(0xFFE3E9FF), outline = null
                )
                if (difficultyTitle != null) {
                    OutlinedText(difficultyTitle, fontSize = f(28f), color = Color(0xFF2AA8FF), outline = null)
                }
            }
            if (canRevive) {
                val enabled = diamonds >= reviveCost
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(x = u(68f), y = u(613f)).size(width = u(817f), height = u(109f))
                        .clip(RoundedCornerShape(u(40f)))
                        .then(if (enabled) Modifier else Modifier.background(Color(0xCC0A1230)))
                        .clickable(enabled = enabled, onClick = onRevive)
                ) {
                    if (!enabled) OutlinedText("Need $reviveCost gems (you have $diamonds)", fontSize = f(34f), color = GameColors.Sky, outline = null)
                }
            }
            Box(
                modifier = Modifier
                    .offset(x = u(70f), y = u(745f + shift)).size(width = u(175f), height = u(123f))
                    .clip(RoundedCornerShape(u(30f))).clickable(onClick = onMenu)
            )
            Box(
                modifier = Modifier
                    .offset(x = u(275f), y = u(745f + shift)).size(width = u(608f), height = u(127f))
                    .clip(RoundedCornerShape(u(40f))).clickable(onClick = onRetry)
            )
        }
    }
}

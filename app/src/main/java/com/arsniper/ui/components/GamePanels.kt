package com.arsniper.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.ui.game.CoinIcon
import com.arsniper.ui.game.DiamondIcon
import com.arsniper.ui.game.MonsterSprite
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.GameDims

/** פאנל כחול כהה עם מסגרת עבה, פינות מעוגלות וברק עדין בחלק העליון */
@Composable
fun NavyPanel(
    modifier: Modifier = Modifier,
    corner: Dp = GameDims.CornerLarge,
    borderColor: Color = GameColors.Outline,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(GameColors.PanelGradient)
            .drawBehind {
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                        endY = size.height * 0.35f
                    ),
                    cornerRadius = CornerRadius(corner.toPx())
                )
            }
            .border(GameDims.Border, borderColor, shape),
        content = content
    )
}

/** מסגרת למטבע כמו בעיצוב: פס כחול כהה עם אייקון בתוכו ומספר מודגש */
@Composable
fun CurrencyPill(
    amount: Int,
    isGems: Boolean,
    modifier: Modifier = Modifier,
    onPlus: (() -> Unit)? = null,
    prefix: String = ""
) {
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .background(Color(0xFF0A1E5E).copy(alpha = 0.45f))
            .border(1.3.dp, Color(0xFF3F8EF0).copy(alpha = 0.9f), shape)
            .padding(start = 3.dp, end = 14.dp)
    ) {
        if (isGems) DiamondIcon(size = 28.dp) else CoinIcon(size = 28.dp)
        Spacer(Modifier.width(8.dp))
        OutlinedText("$prefix$amount", fontSize = 18.sp, outline = null)
        if (onPlus != null) {
            Spacer(Modifier.width(8.dp))
            PlusSquare(onPlus)
        }
    }
}

@Composable
fun PlusSquare(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF7BE04A), Color(0xFF2FA21F))))
            .border(1.5.dp, Color(0xFF0F6A22), shape)
            .clickable(onClick = onClick)
    ) { IconGlyph(GameIcon.Plus, size = size * 0.55f, tint = Color.White) }
}

/** מטבעות + יהלומים + כפתור + ירוק אחד בסוף, כמו בלוח העיצוב */
@Composable
fun CurrencyBar(coins: Int, diamonds: Int, modifier: Modifier = Modifier, onPlus: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        CurrencyPill(coins, isGems = false)
        CurrencyPill(diamonds, isGems = true)
        if (onPlus != null) PlusSquare(onPlus)
    }
}

/** כותרת מסך: כפתור חזרה, כותרת במרכז ומטבעות בצד */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        if (onBack != null) {
            GameIconButton(GameIcon.Back, onClick = onBack, style = com.arsniper.ui.theme.GameButtonStyle.Primary, size = 48.dp)
        } else {
            Spacer(Modifier.width(48.dp))
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            OutlinedText(title, fontSize = 28.sp, color = Color.White, letterSpacing = 1.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            trailing()
        }
    }
}

/** מכל עגול/מרובע לאייקון, עם מסגרת ורקע כחול */
@Composable
fun IconContainer(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    background: Color = GameColors.Navy600,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background)
            .border(2.dp, GameColors.Outline, shape),
        content = content
    )
}

/** מסגרת דיוקן למפלצת: משתמשים בספרייט הקיים של המפלצת בלי לשנות אותו */
@Composable
fun MonsterPortraitFrame(
    sprite: MonsterSprite?,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    selected: Boolean = false,
    locked: Boolean = false
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(GameColors.PanelGradient)
            .border(3.dp, if (selected) GameColors.Yellow else GameColors.Outline, shape)
    ) {
        if (sprite != null) {
            Image(
                bitmap = sprite.image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size * 0.9f)
                    .then(if (locked) Modifier.background(Color(0x99081028)) else Modifier)
            )
        } else {
            // תחליף זמני: אין ספרייט זמין
            Box(Modifier.size(size * 0.5f).background(GameColors.Slate, RoundedCornerShape(50)))
        }
        if (locked) IconGlyph(GameIcon.Lock, size = size * 0.4f, tint = GameColors.Ice)
    }
}

/** שלושה כוכבים: מלאים עד stars, והשאר כהים */
@Composable
fun StarRow(stars: Int, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val gray = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
    )
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..3) {
            Image(
                painter = androidx.compose.ui.res.painterResource(com.arsniper.R.drawable.icon_star),
                contentDescription = null,
                colorFilter = if (i <= stars) null else gray,
                alpha = if (i <= stars) 1f else 0.35f,
                modifier = Modifier.size(size)
            )
        }
    }
}

/** תג שחקן: דיוקן, שם, ורמה עם פס התקדמות */
@Composable
fun PlayerBadge(
    name: String,
    level: Int,
    progress: Float,
    portrait: MonsterSprite?,
    modifier: Modifier = Modifier
) {
    NavyPanel(modifier = modifier, corner = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
            MonsterPortraitFrame(portrait, size = 48.dp, selected = true)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.width(96.dp)) {
                OutlinedText(name, fontSize = 15.sp, outline = null)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedText("$level", fontSize = 13.sp, color = GameColors.Sky, outline = null)
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(GameColors.Navy900)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                                .height(8.dp)
                                .background(GameColors.Yellow)
                        )
                    }
                }
            }
        }
    }
}

/** מתג הפעלה/כיבוי בסגנון המשחק */
@Composable
fun GameToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .size(width = 68.dp, height = 34.dp)
            .clip(shape)
            .background(if (checked) GameColors.Green else GameColors.Slate)
            .border(3.dp, GameColors.Outline, shape)
            .androidxClickable { onChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .border(2.dp, GameColors.Outline, RoundedCornerShape(50))
        )
    }
}

private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

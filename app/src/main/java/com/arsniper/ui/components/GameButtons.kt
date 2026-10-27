package com.arsniper.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors

/**
 * ציור הכפתור: צל תחתון עבה (תחושת תלת ממד), מילוי בגרדיאנט, ברק לבן בחצי העליון וקו מתאר כהה.
 * בלחיצה הכפתור יורד אל הצל.
 */
private fun Modifier.buttonSurface(style: GameButtonStyle, corner: Dp, ledge: Dp, pressed: Boolean): Modifier =
    this.drawBehind {
        val radius = corner.toPx()
        val ledgePx = ledge.toPx()
        val drop = if (pressed) ledgePx * 0.7f else 0f
        val bodyHeight = size.height - ledgePx
        // הילה רכה בצבע הכפתור סביב הגוף
        if (!pressed) {
            for ((sw, a) in listOf(9f to 0.10f, 5f to 0.16f, 2.5f to 0.24f)) {
                drawRoundRect(
                    style.top.copy(alpha = a), Offset(0f, 0f), Size(size.width, bodyHeight), CornerRadius(radius),
                    style = Stroke(width = sw.dp.toPx())
                )
            }
        }
        // צל תחתון
        drawRoundRect(
            style.ledge, Offset(0f, ledgePx), Size(size.width, bodyHeight), CornerRadius(radius)
        )
        // גוף
        drawRoundRect(
            Brush.verticalGradient(listOf(style.top, style.bottom), startY = drop, endY = drop + bodyHeight),
            Offset(0f, drop), Size(size.width, bodyHeight), CornerRadius(radius)
        )
        // ברק
        drawRoundRect(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f)),
                startY = drop, endY = drop + bodyHeight * 0.5f
            ),
            Offset(size.width * 0.04f, drop + bodyHeight * 0.06f),
            Size(size.width * 0.92f, bodyHeight * 0.44f),
            CornerRadius(radius * 0.8f)
        )
        // קו פנימי בהיר דק (ברק על הקצה)
        val rim = 1.5.dp.toPx()
        drawRoundRect(
            Color.White.copy(alpha = 0.35f), Offset(rim, drop + rim), Size(size.width - 2 * rim, bodyHeight - 2 * rim),
            CornerRadius((radius - rim).coerceAtLeast(1f)), style = Stroke(width = 1.dp.toPx())
        )
        // קו מתאר דק בגוון כהה של צבע הכפתור (במקום מתאר שחור עבה)
        drawRoundRect(
            androidx.compose.ui.graphics.lerp(style.ledge, Color.Black, 0.55f),
            Offset(0f, drop), Size(size.width, bodyHeight), CornerRadius(radius),
            style = Stroke(width = 1.6.dp.toPx())
        )
    }

/** כפתור עם טקסט (ואפשר גם אייקון). style קובע את הצבע: Primary, Secondary, Success, Danger או Locked */
@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GameButtonStyle = GameButtonStyle.Primary,
    icon: GameIcon? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    fontSize: TextUnit = 22.sp
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val ledge = 5.dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height + ledge)
            .alpha(if (enabled) 1f else 0.55f)
            .buttonSurface(style, 14.dp, ledge, pressed && enabled)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.offset(y = if (pressed && enabled) 3.dp else (-2).dp)
        ) {
            if (icon != null) {
                IconGlyph(icon, size = height * 0.62f, tint = style.text)
                Box(Modifier.width(8.dp))
            }
            OutlinedText(
                text = text,
                fontSize = fontSize,
                color = style.text,
                outline = if (style == GameButtonStyle.Primary) null else GameColors.Outline
            )
        }
    }
}

/** כפתור ריבועי עם אייקון בלבד (הגדרות, חנות, חזרה...) */
@Composable
fun GameIconButton(
    icon: GameIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GameButtonStyle = GameButtonStyle.Secondary,
    enabled: Boolean = true,
    size: Dp = 52.dp
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val ledge = 4.dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = size, height = size + ledge)
            .alpha(if (enabled) 1f else 0.55f)
            .buttonSurface(style, 12.dp, ledge, pressed && enabled)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
    ) {
        Box(Modifier.offset(y = if (pressed && enabled) 2.dp else (-2).dp)) {
            IconGlyph(icon, size = size * 0.52f, tint = style.text)
        }
    }
}

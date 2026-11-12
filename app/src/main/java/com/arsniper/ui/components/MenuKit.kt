package com.arsniper.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * כפתורי התפריט הראשי: ציור מוכן (webp שקוף) עם מצבי לחיצה והשבתה באותו סגנון.
 * לחיצה: הקטנה קלה והכהיה. מושבת: בלי צבע ושקוף חלקית. שטח המגע לפחות 48dp גם כשהציור קטן.
 */
@Composable
fun SpriteButton(
    @DrawableRes sprite: Int,
    contentDescription: String,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.94f else 1f, label = "spriteScale")
    val filter = when {
        !enabled -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        pressed -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            0.8f, 0f, 0f, 0f, 0f,
            0f, 0.8f, 0f, 0f, 0f,
            0f, 0f, 0.8f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
        else -> null
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(maxOf(width, 48.dp), maxOf(height, 48.dp))
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    ) {
        Image(
            painter = painterResource(sprite),
            contentDescription = contentDescription,
            contentScale = ContentScale.FillBounds,
            colorFilter = filter,
            modifier = Modifier.size(width, height).scale(scale).alpha(if (enabled) 1f else 0.55f)
        )
    }
}

/**
 * מונה מטבעות/יהלומים לפי הרפרנס: פס כחול כהה מעוגל, האייקון בולט מעל קצהו השמאלי, המספר במרכז,
 * וכפתור + גדול בולט מעל קצהו הימני. unit = גודל "פיקסל קנבס" (2560 רוחב) ב-dp.
 */
@Composable
fun CurrencyCounter(
    @DrawableRes icon: Int,
    value: Int,
    label: String,
    onPlus: () -> Unit,
    unit: Dp,
    modifier: Modifier = Modifier
) {
    val big = maxOf(unit * 86, 32.dp)       // האייקון והכפתור (הוקטן ב-33%)
    val barH = big * 0.76f
    // גובה השורה לפחות 48dp כדי ששטח המגע של כפתור ה-+ יישאר נגיש גם כשהציור קטן
    Box(modifier = modifier.height(maxOf(big, 48.dp)), contentAlignment = Alignment.CenterStart) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(horizontal = big * 0.42f)
                .height(barH)
                .background(Color(0xEB16285A), RoundedCornerShape(barH / 2))
                .padding(start = big * 0.62f, end = big * 0.62f)
        ) {
            OutlinedText(
                String.format(java.util.Locale.US, "%,d", value),
                fontSize = (big.value * 0.46f).sp,
                color = Color.White,
                outline = null
            )
        }
        Image(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.align(Alignment.CenterStart).size(big)
        )
        SpriteButton(
            sprite = com.arsniper.R.drawable.ui_btn_plus,
            contentDescription = "Add $label",
            onClick = onPlus,
            width = big,
            height = big * 118f / 120f,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

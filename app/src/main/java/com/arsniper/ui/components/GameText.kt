package com.arsniper.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.GameType

/**
 * טקסט בסגנון קריקטורה: משקל כבד, ואופציונלית קו מתאר כהה עבה סביב האותיות וצל קל.
 * outline = null: בלי מתאר (למשל טקסט כהה על כפתור צהוב).
 */
@Composable
fun OutlinedText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp,
    color: Color = Color.White,
    outline: Color? = GameColors.Outline,
    outlineWidth: Float = 6f,
    letterSpacing: TextUnit = 0.sp,
    textAlign: TextAlign? = null
) {
    Box(modifier = modifier) {
        if (outline != null) {
            Text(
                text = text,
                fontSize = fontSize,
                fontFamily = GameType.Family,
                fontWeight = GameType.Heavy,
                letterSpacing = letterSpacing,
                textAlign = textAlign,
                style = LocalTextStyle.current.merge(TextStyle(
                    color = outline,
                    drawStyle = Stroke(width = outlineWidth, join = StrokeJoin.Round),
                    shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 4f), 4f)
                ))
            )
        }
        Text(
            text = text,
            fontSize = fontSize,
            fontFamily = GameType.Family,
            fontWeight = GameType.Heavy,
            letterSpacing = letterSpacing,
            textAlign = textAlign,
            color = color
        )
    }
}

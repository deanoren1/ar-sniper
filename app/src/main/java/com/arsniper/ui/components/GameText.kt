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


/** כותרת מסך: מילוי מדורג (צהוב בהיר למעלה, כתום למטה), מתאר כהה עבה וזוהר זהוב מתחת */
@Composable
fun GradientTitle(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 40.sp, letterSpacing: TextUnit = 1.sp) {
    Box(modifier = modifier) {
        // זוהר רך מאחור
        Text(
            text = text, fontSize = fontSize, fontFamily = GameType.Family, fontWeight = GameType.Heavy, letterSpacing = letterSpacing,
            style = LocalTextStyle.current.merge(TextStyle(
                color = Color(0xFFFFB300),
                shadow = Shadow(Color(0xFFFFA000).copy(alpha = 0.85f), Offset(0f, 6f), 28f)
            ))
        )
        // מתאר כהה
        Text(
            text = text, fontSize = fontSize, fontFamily = GameType.Family, fontWeight = GameType.Heavy, letterSpacing = letterSpacing,
            style = LocalTextStyle.current.merge(TextStyle(
                color = Color(0xFF3A1A00),
                drawStyle = Stroke(width = 9f, join = StrokeJoin.Round),
                shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 6f), 5f)
            ))
        )
        // מילוי מדורג
        Text(
            text = text, fontSize = fontSize, fontFamily = GameType.Family, fontWeight = GameType.Heavy, letterSpacing = letterSpacing,
            style = LocalTextStyle.current.merge(TextStyle(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFFFFF3A0), Color(0xFFFFD21F), Color(0xFFFF9A00))
                )
            ))
        )
    }
}

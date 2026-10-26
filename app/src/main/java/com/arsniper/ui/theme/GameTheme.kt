package com.arsniper.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * הפלטה של הממשק, לפי לוח העיצוב: כחול כהה לפאנלים, צבעים עזים לכפתורים, קו מתאר כהה עבה.
 * כל צבע בממשק מגיע מכאן, כדי שהמסכים יהיו עקביים.
 */
object GameColors {
    val Navy900 = Color(0xFF081028)
    val Navy800 = Color(0xFF0E1D45)
    val Navy700 = Color(0xFF14295E)
    val Navy600 = Color(0xFF1C3A80)
    val Outline = Color(0xFF050B1C)

    val Blue = Color(0xFF1E6BFF)
    val Sky = Color(0xFF27B5FF)
    val Yellow = Color(0xFFFFC62A)
    val Orange = Color(0xFFFF8A1F)
    val Red = Color(0xFFE53935)
    val Green = Color(0xFF2DBE4E)
    val Purple = Color(0xFF8E3CF2)
    val Slate = Color(0xFF5B6C94)
    val Ice = Color(0xFFA9B8DA)
    val White = Color(0xFFFFFFFF)

    val PanelGradient = Brush.verticalGradient(listOf(Navy700, Navy800))
}

/** סוגי הכפתורים: ראשי (צהוב), משני (כחול), הצלחה (ירוק), סכנה (אדום), ונעול (אפור) */
enum class GameButtonStyle(
    val top: Color,
    val bottom: Color,
    val ledge: Color,
    val text: Color
) {
    Primary(Color(0xFFFFD84A), Color(0xFFFFA81C), Color(0xFFB36A00), Color(0xFF2A1A00)),
    Secondary(Color(0xFF2F8BFF), Color(0xFF1252D6), Color(0xFF0A2E80), Color.White),
    Success(Color(0xFF45DB55), Color(0xFF1FA33A), Color(0xFF0F6A22), Color.White),
    Danger(Color(0xFFFF5A52), Color(0xFFD02222), Color(0xFF7F1010), Color.White),
    Locked(Color(0xFF50608A), Color(0xFF34436A), Color(0xFF1D2744), Color(0xFFB8C4E0))
}

object GameType {
    val Family = FontFamily.SansSerif
    val Heavy = FontWeight.Black
}

object GameDims {
    val CornerLarge = 18.dp
    val CornerMedium = 12.dp
    val CornerSmall = 8.dp
    val Border = 3.dp
}

/**
 * מקדם קנה מידה לפי גודל המסך: המסך המיועד הוא 800x360dp בערך (רוחב על גובה, לרוחב).
 * ככה אותו עיצוב מתאים גם למסכים קטנים וגם לטאבלטים.
 */
@Immutable
class UiScale(val factor: Float) {
    fun dp(base: Float): Dp = (base * factor).dp
}

@Composable
fun ResponsiveBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.(UiScale) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val factor = minOf(maxWidth.value / 800f, maxHeight.value / 360f).coerceIn(0.7f, 1.8f)
        content(UiScale(factor))
    }
}

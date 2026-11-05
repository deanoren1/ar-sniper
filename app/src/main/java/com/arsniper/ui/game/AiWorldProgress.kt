package com.arsniper.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.ui.components.GameButton
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors
import kotlin.math.exp

/**
 * מסך יצירת עולם AI: התמונה שלך, שלבים והתקדמות. ההתקדמות מוערכת לפי זמן (השרת לא מדווח אחוזים),
 * ונעצרת ב-95% עד שהעולם מוכן. אפשר להתחיל לשחק עכשיו על התמונה המקורית: היצירה ממשיכה והעולם נכנס כשמוכן.
 */
@Composable
fun AiWorldProgress(
    styleTitle: String,
    photo: ImageBitmap?,
    onPlayNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    var seconds by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) {
            androidx.compose.runtime.withFrameMillis { }
            seconds = (System.nanoTime() - start) / 1e9f
        }
    }
    val progress = 0.95f * (1f - exp(-seconds / 25f))
    val steps = listOf(
        "Analyzing photo..." to true,
        "Applying $styleTitle style..." to (progress > 0.15f),
        "Generating world..." to false
    )
    Box(
        modifier = modifier.fillMaxSize().background(Color(0xF2050B1C)).clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(0.62f)
        ) {
            OutlinedText("CREATING AI WORLD", fontSize = 24.sp, color = Color.White, outlineWidth = 10f)
            Box(
                modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp))
                    .border(2.dp, Color(0xFF2F8BFF), RoundedCornerShape(14.dp)).background(Color(0x55000000))
            ) {
                if (photo != null) Image(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                for ((text, done) in steps) {
                    OutlinedText(
                        (if (done) "✓  " else "○  ") + text,
                        fontSize = 14.sp,
                        color = if (done) GameColors.Green else GameColors.Ice,
                        outline = null
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF0A1D50)).border(2.dp, Color(0xFF2F8BFF), RoundedCornerShape(9.dp))
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Color(0xFF2FCB5B)))
            }
            OutlinedText("${(progress * 100).toInt()}%", fontSize = 14.sp, color = Color.White, outline = null)
            GameButton(
                text = "PLAY NOW",
                onClick = onPlayNow,
                style = GameButtonStyle.Secondary,
                height = 40.dp,
                fontSize = 16.sp
            )
            OutlinedText("The new world appears in the arena when it is ready", fontSize = 11.sp, color = GameColors.Ice, outline = null)
        }
    }
}

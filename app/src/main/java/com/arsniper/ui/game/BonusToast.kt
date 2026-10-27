package com.arsniper.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.ui.components.OutlinedText

/** הודעת בונוס: טקסט והזמן (ננו-שניות של הפריים) שבו הופיעה */
class BonusToastData(val text: String, val bornNanos: Long)

/** כמה זמן ההודעה נשארת על המסך, בשניות */
const val TOAST_SECONDS = 1.4f

/**
 * הודעת הבונוס במקום הקבוע בראש המסך. מופיעה בקפיצה קטנה ונעלמת בהדרגה.
 * יש תמונה לחלק מההודעות; לשאר מציירים טקסט.
 */
@Composable
fun BonusToast(data: BonusToastData?, nowNanos: Long, modifier: Modifier = Modifier) {
    if (data == null) return
    val age = (nowNanos - data.bornNanos) / 1e9f
    if (age < 0f || age > TOAST_SECONDS) return
    val pop = if (age < 0.15f) 0.8f + 0.2f * (age / 0.15f) else 1f
    val fade = if (age > TOAST_SECONDS - 0.4f) ((TOAST_SECONDS - age) / 0.4f).coerceIn(0f, 1f) else 1f
    val res = LabelArt.resFor(data.text)
    Box(modifier = modifier.alpha(fade).scale(pop)) {
        if (res != null) {
            Image(
                painter = painterResource(res),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.width(200.dp)
            )
        } else {
            OutlinedText(text = data.text, fontSize = 22.sp, color = Color(0xFFFFEB3B))
        }
    }
}

package com.arsniper.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** הודעת בונוס: טקסט והזמן (ננו-שניות של הפריים) שבו הופיעה */
class BonusToastData(val text: String, val bornNanos: Long)

/** כמה זמן כל הודעה נשארת על המסך, בשניות */
const val TOAST_SECONDS = 2.6f

/** כמה הודעות לכל היותר בו-זמנית */
const val TOAST_MAX = 4

/**
 * פיד הודעות קטן וחצי שקוף בצד שמאל: ההודעה החדשה למטה, הישנות דועכות ונעלמות.
 */
@Composable
fun BonusFeed(items: List<BonusToastData>, nowNanos: Long, modifier: Modifier = Modifier) {
    val live = items.filter { (nowNanos - it.bornNanos) / 1e9f in 0f..TOAST_SECONDS }.takeLast(TOAST_MAX)
    if (live.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        live.forEach { data ->
            val age = (nowNanos - data.bornNanos) / 1e9f
            val fade = if (age > TOAST_SECONDS - 0.6f) ((TOAST_SECONDS - age) / 0.6f).coerceIn(0f, 1f) else 1f
            Text(
                text = iconFor(data.text) + "  " + data.text,
                color = Color(0xFFFFEB3B),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .alpha(0.6f * fade)
                    .background(Color(0xFF0B1B33), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

/** אייקון קטן לפי תוכן ההודעה */
private fun iconFor(text: String): String {
    val t = text.uppercase()
    return when {
        "HEADSHOT" in t -> "\uD83C\uDFAF"
        "COMBO" in t -> "\uD83D\uDD25"
        "ELIMINATED" in t -> "\uD83D\uDC80"
        "DIAMOND" in t || "GEM" in t -> "\uD83D\uDC8E"
        "HEART" in t -> "\u2764\uFE0F"
        "AMMO" in t || "+15" in t -> "\uD83D\uDD0B"
        "SHIELD" in t || "BLOCK" in t || "STOPPED" in t -> "\uD83D\uDEE1\uFE0F"
        "SHOCK" in t -> "\u26A1"
        "COIN" in t || t.startsWith("+") -> "\uD83E\uDE99"
        "BALLOON" in t -> "\uD83C\uDF88"
        else -> "\u2B50"
    }
}

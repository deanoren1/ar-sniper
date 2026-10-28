package com.arsniper.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.coop.CoopVoice

/**
 * מיקרופון on/off למשחק משותף. לחיצה ראשונה מבקשת הרשאה. "SPEAKING" נדלק כשהשותף מדבר.
 * בלי אוזניות הקול של השותף יחזור למיקרופון: מומלץ אוזניות.
 */
@Composable
fun MicToggle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) CoopVoice.setMic(context, true)
    }
    val on = CoopVoice.micOn
    val speaking = System.currentTimeMillis() - CoopVoice.lastHeardMs < 500L
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (on) Color(0xFF1E8E3E) else Color(0xCC5A1A1A))
                .clickable {
                    if (on) {
                        CoopVoice.setMic(context, false)
                    } else if (CoopVoice.hasPermission(context)) {
                        CoopVoice.setMic(context, true)
                    } else {
                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            OutlinedText(if (on) "MIC ON" else "MIC OFF", fontSize = 13.sp)
        }
        if (speaking) OutlinedText("SPEAKING", fontSize = 11.sp, color = Color(0xFF8CFFB0), modifier = Modifier.padding(top = 2.dp))
    }
}

package com.arsniper

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.arsniper.data.PhotoRepository
import com.arsniper.ui.capture.CaptureScreen
import com.arsniper.ui.start.StartScreen

enum class Screen { Start, Capture, Game }

@Composable
fun AppNavigation() {
    var screen by rememberSaveable { mutableStateOf(Screen.Start) }

    // כפתור/מחוות חזרה מחזיר למסך הפתיחה
    BackHandler(enabled = screen != Screen.Start) {
        screen = Screen.Start
    }

    when (screen) {
        Screen.Start -> StartScreen(onStart = { screen = Screen.Capture })
        Screen.Capture -> CaptureScreen(onPhotoCaptured = { screen = Screen.Game })
        Screen.Game -> GamePlaceholder()
    }
}

// מסך זמני: מציג את התמונה שצולמה, עד שלב 4
@Composable
private fun GamePlaceholder() {
    val context = LocalContext.current
    val photo = remember { PhotoRepository.load(context)?.asImageBitmap() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14)),
        contentAlignment = Alignment.Center
    ) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(text = "GAME (coming soon)", color = Color.White, fontSize = 24.sp)
    }
}

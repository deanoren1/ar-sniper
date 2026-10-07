package com.arsniper

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.arsniper.ui.capture.CaptureScreen
import com.arsniper.ui.game.GameScreen
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
        Screen.Game -> GameScreen()
    }
}

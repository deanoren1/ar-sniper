package com.arsniper

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.arsniper.game.progress.ArenaStyle
import com.arsniper.ui.arena.ArenaSelectScreen
import com.arsniper.ui.capture.CaptureScreen
import com.arsniper.ui.game.GameScreen
import com.arsniper.ui.review.ReviewScreen
import com.arsniper.ui.scene.SceneScreen
import com.arsniper.ui.shop.ShopScreen
import com.arsniper.ui.start.StartScreen

enum class Screen { Start, Capture, Review, Scene, Arena, Game, Shop }

@Composable
fun AppNavigation() {
    var screen by rememberSaveable { mutableStateOf(Screen.Start) }

    // כפתור/מחוות חזרה מחזיר למסך הפתיחה
    var arenaIndex by rememberSaveable { mutableStateOf(0) }

    BackHandler(enabled = screen != Screen.Start) {
        screen = if (screen == Screen.Game) Screen.Arena else Screen.Start
    }

    when (screen) {
        Screen.Start -> StartScreen(
            onStart = { screen = Screen.Capture },
            onPhotoPicked = { screen = Screen.Review },
            onShop = { screen = Screen.Shop }
        )
        Screen.Capture -> CaptureScreen(onPhotoCaptured = { screen = Screen.Review })
        Screen.Review -> ReviewScreen(
            onContinue = { screen = Screen.Scene },
            onRetake = { screen = Screen.Capture }
        )
        Screen.Scene -> SceneScreen(onContinue = { screen = Screen.Arena })
        Screen.Arena -> ArenaSelectScreen(
            onPick = { arenaIndex = it.ordinal; screen = Screen.Game },
            onBack = { screen = Screen.Start }
        )
        Screen.Game -> GameScreen(style = ArenaStyle.values()[arenaIndex.coerceIn(0, ArenaStyle.values().size - 1)])
        Screen.Shop -> ShopScreen(onBack = { screen = Screen.Start })
    }
}

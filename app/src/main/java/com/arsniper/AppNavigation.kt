package com.arsniper

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.arsniper.data.PhotoLibrary
import com.arsniper.data.PlayerProfile
import com.arsniper.game.progress.ArenaStyle
import com.arsniper.ui.menu.PlayScreen
import com.arsniper.ui.arena.ArenaSelectScreen
import com.arsniper.ui.capture.CaptureScreen
import com.arsniper.ui.game.GameScreen
import com.arsniper.ui.review.ReviewScreen
import com.arsniper.ui.scene.SceneScreen
import com.arsniper.ui.shop.ShopScreen
import com.arsniper.ui.menu.ArtLabScreen
import com.arsniper.ui.menu.MainMenuScreen
import com.arsniper.ui.menu.ScanLabScreen
import com.arsniper.ui.menu.SettingsScreen
import com.arsniper.ui.menu.StatsScreen

enum class Screen { Start, Play, PanoView, Capture, Review, Scene, Arena, Game, Shop, Settings, Stats, ArtLab, ScanLab, SceneDebug, Coop }

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.Start) }
    var quick by rememberSaveable { mutableStateOf(false) }

    // כפתור/מחוות חזרה מחזיר למסך הפתיחה
    var arenaIndex by rememberSaveable { mutableStateOf(0) }

    BackHandler(enabled = screen != Screen.Start) {
        screen = when (screen) {
            Screen.Game -> if (quick) Screen.Play else Screen.Arena
            Screen.Capture, Screen.Review, Screen.Scene, Screen.Arena, Screen.PanoView -> Screen.Play
            Screen.Coop -> Screen.Settings
            else -> Screen.Start
        }
    }

    when (screen) {
        Screen.Start -> MainMenuScreen(
            onStart = { screen = Screen.Play },
            onPhotoPicked = { screen = Screen.Review },
            onShop = { screen = Screen.Shop },
            onSettings = { screen = Screen.Settings },
            onStats = { screen = Screen.Stats }
        )
        Screen.Play -> PlayScreen(
            onBack = { screen = Screen.Start },
            onTakePhoto = { PhotoLibrary.clearPending(); com.arsniper.pano.PanoramaStore.clearPendingGame(context); quick = false; screen = Screen.Capture },
            onGalleryPicked = { com.arsniper.pano.PanoramaStore.clearPendingGame(context); quick = false; screen = Screen.Review },
            onPickSaved = { file ->
                PhotoLibrary.select(context, file)
                PhotoLibrary.clearPending()
                com.arsniper.pano.PanoramaStore.clearPendingGame(context)
                quick = false
                // ניתוח אנשים הוא חובה: תמונה שלא נסקרה עוברת קודם במסך הסקירה
                screen = if (PhotoLibrary.isReviewed(file)) Screen.Scene else Screen.Review
            },
            onQuick = {
                val all = PhotoLibrary.list(context)
                val file = all.filter { PhotoLibrary.isReviewed(it) }.randomOrNull() ?: all.randomOrNull()
                if (file != null) {
                    PhotoLibrary.select(context, file)
                    PhotoLibrary.clearPending()
                    com.arsniper.pano.PanoramaStore.clearPendingGame(context)
                    quick = true
                    screen = if (PhotoLibrary.isReviewed(file)) Screen.Scene else Screen.Review
                }
            }
        )
        Screen.Capture -> CaptureScreen(
            onPhotoCaptured = { com.arsniper.pano.PanoramaStore.clearPendingGame(context); screen = Screen.Review },
            onBack = { screen = Screen.Play },
            onGalleryPicked = { screen = Screen.Review },
            onPanorama = { screen = Screen.PanoView }
        )
        Screen.PanoView -> com.arsniper.ui.capture.PanoViewScreen(
            onBack = { screen = Screen.Play },
            onPlay = { quick = false; screen = Screen.Review }
        )
        Screen.Review -> ReviewScreen(
            onContinue = { com.arsniper.pano.PanoGame.finalizeReview(context); screen = Screen.Scene },
            onRetake = { screen = Screen.Capture }
        )
        Screen.Scene -> SceneScreen(
            onContinue = {
                if (quick) {
                    val open = ArenaStyle.values().filter { PlayerProfile.isUnlocked(it) }
                    arenaIndex = (open.randomOrNull() ?: ArenaStyle.values()[0]).ordinal
                    screen = Screen.Game
                } else {
                    screen = Screen.Arena
                }
            },
            auto = true
        )
        Screen.SceneDebug -> SceneScreen(onContinue = { screen = Screen.Settings })
        Screen.Arena -> ArenaSelectScreen(
            onPick = { arenaIndex = it.ordinal; screen = Screen.Game },
            onBack = { screen = Screen.Play }
        )
        Screen.Game -> GameScreen(
            style = ArenaStyle.values()[arenaIndex.coerceIn(0, ArenaStyle.values().size - 1)],
            onExit = { screen = Screen.Play },
            onNextArena = { quick = false; screen = Screen.Arena }
        )
        Screen.Shop -> ShopScreen(onBack = { screen = Screen.Start })
        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Start }, onArtLab = { screen = Screen.ArtLab }, onScanLab = { screen = Screen.ScanLab }, onSceneDebug = { screen = Screen.SceneDebug }, onCoop = { screen = Screen.Coop })
        Screen.ScanLab -> ScanLabScreen(onBack = { screen = Screen.Settings })
        Screen.ArtLab -> ArtLabScreen(onBack = { screen = Screen.Settings }, onUsePhoto = { screen = Screen.Review })
        Screen.Stats -> StatsScreen(onBack = { screen = Screen.Start })
        Screen.Coop -> com.arsniper.ui.menu.CoopScreen(onBack = { screen = Screen.Settings })
    }
}

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

enum class Screen { Start, ChooseGame, WorldPlay, ArenaMode, Play, PanoView, Capture, Review, Scene, Arena, Game, Shop, Armory, Multiplayer, Settings, Stats, ArtLab, ScanLab, SceneDebug, Coop }

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.Start) }
    var quick by rememberSaveable { mutableStateOf(false) }
    // כפתור/מחוות חזרה מחזיר למסך הפתיחה
    var arenaIndex by rememberSaveable { mutableStateOf(0) }
    // משחק משותף: "host" / "guest" בזמן משחק, אחרת ריק
    var coopRole by rememberSaveable { mutableStateOf("") }
    // בחירת ארנה לפני הניתוח: יצירת עולם ה-AI מתחילה מיד ורצה במקביל לניתוח
    var arenaFirst by rememberSaveable { mutableStateOf(false) }
    // אחרי שהמערכת סגרה את האפליקציה ברקע ופתחה אותה במסך המשחק, הניתוח בזיכרון נעלם: חוזרים פעם אחת למסך הניתוח (מהמטמון)
    var restoreTried by rememberSaveable { mutableStateOf(false) }
    // הארנה כבר נבחרה (מסך אופן המשחק): אחרי צילום/בחירת תמונה והסקירה ממשיכים ישר לניתוח, בלי לבחור ארנה שוב
    var modeArena by rememberSaveable { mutableStateOf(false) }
    // המפה נפתחה ממסך הפתיחה (ולא אחרי בחירת תמונה): בחירת ארנה מובילה למסך אופן המשחק
    var mapFromStart by rememberSaveable { mutableStateOf(false) }
    // מאיזה כפתור במסך הראשי נפתח מסך ("shop" / "multi" / "armory"), כדי שהחזרה תחזור למסך הראשי. ריק = נפתח ממסך אחר
    var fromMain by rememberSaveable { mutableStateOf("") }
    // לשונית פתיחה בחנות המטבעות: 0 מטבעות, 1 יהלומים
    var shopTab by rememberSaveable { mutableStateOf(0) }
    // ה-CO-OP נפתח מדף המולטיפלייר (ולא מההגדרות): החזרה אליו
    var coopFromMulti by rememberSaveable { mutableStateOf(false) }
    // צילום/גלריה מתוך לובי ה-CO-OP: אותו מסלול סריקה רגיל (סקירה וניתוח), ובסיומו חוזרים ללובי עם התמונה החדשה נבחרת
    var coopPick by rememberSaveable { mutableStateOf(false) }
    var coopPreselect by rememberSaveable { mutableStateOf(false) }
    // מצב שנבחר במסך CHOOSE YOUR GAME: "journey" / "classic" / "random" / "worlds". ריק = לא נפתח משם
    var chosenMode by rememberSaveable { mutableStateOf("") }
    fun goScene() {
        restoreTried = false
        if (coopPick) { screen = Screen.Scene; return }
        if (coopRole.isNotEmpty()) { screen = Screen.Scene; return }
        if (modeArena) {
            modeArena = false
            val style = ArenaStyle.values()[arenaIndex.coerceIn(0, ArenaStyle.values().size - 1)]
            com.arsniper.ml.AiWorldWorker.startFor(context, style)
            arenaFirst = true
            screen = Screen.Scene
            return
        }
        if (quick) {
            val open = ArenaStyle.values().filter { PlayerProfile.isUnlocked(it) }
            val pick = open.randomOrNull() ?: ArenaStyle.values()[0]
            arenaIndex = pick.ordinal
            com.arsniper.ml.AiWorldWorker.startFor(context, pick)
            arenaFirst = true
            screen = Screen.Scene
        } else {
            arenaFirst = true
            screen = Screen.Arena
        }
    }


    BackHandler(enabled = screen != Screen.Start) {
        screen = when (screen) {
            Screen.Game -> if (coopRole.isNotEmpty()) Screen.Coop else if (quick) Screen.Play else Screen.Arena
            Screen.ArenaMode -> { modeArena = false; Screen.Arena }
            Screen.Capture, Screen.Review, Screen.Scene, Screen.PanoView -> if (coopPick) { coopPick = false; Screen.Coop } else if (modeArena && (chosenMode == "random" || chosenMode == "worlds")) { modeArena = false; Screen.WorldPlay } else if (modeArena) Screen.ArenaMode else Screen.Play
            Screen.Play -> if (chosenMode == "classic") Screen.ChooseGame else Screen.Start
            Screen.ChooseGame -> { chosenMode = ""; Screen.Start }
            Screen.WorldPlay -> Screen.ChooseGame
            Screen.Arena -> if (mapFromStart) (if (chosenMode == "journey") Screen.ChooseGame else Screen.Start) else Screen.Play
            Screen.Coop -> if (coopFromMulti) Screen.Multiplayer else Screen.Settings
            Screen.Multiplayer -> if (fromMain == "multi") Screen.Start else Screen.Arena
            Screen.Armory -> if (fromMain == "armory") Screen.Start else Screen.Shop
            Screen.Shop -> if (fromMain == "choose") Screen.ChooseGame else if (fromMain == "shop") Screen.Start else if (mapFromStart) (if (modeArena) Screen.ArenaMode else Screen.Arena) else Screen.Start
            else -> Screen.Start
        }
    }

    when (screen) {
        Screen.Start -> MainMenuScreen(
            onStart = { screen = Screen.ChooseGame },
            onShop = { tab -> fromMain = "shop"; shopTab = tab; screen = Screen.Shop },
            onMultiplayer = { fromMain = "multi"; screen = Screen.Multiplayer },
            onArmory = { fromMain = "armory"; screen = Screen.Armory },
            onSettings = { screen = Screen.Settings },
            onStats = { screen = Screen.Stats }
        )
        Screen.ChooseGame -> com.arsniper.ui.menu.ChooseGameScreen(
            onBack = { chosenMode = ""; screen = Screen.Start },
            onJourney = { chosenMode = "journey"; arenaFirst = false; mapFromStart = true; screen = Screen.Arena },
            onClassic = { chosenMode = "classic"; arenaFirst = false; mapFromStart = false; screen = Screen.Play },
            onRandom = { chosenMode = "random"; screen = Screen.WorldPlay },
            onMyWorlds = { chosenMode = "worlds"; screen = Screen.WorldPlay },
            onShop = { fromMain = "choose"; shopTab = 0; screen = Screen.Shop }
        )
        Screen.WorldPlay -> com.arsniper.ui.menu.WorldPlayScreen(
            random = chosenMode == "random",
            onBack = { screen = Screen.ChooseGame },
            onPlay = { file, worldStyle ->
                // כמו "שחק עולם" במסך הארנה: העולם כבר במטמון, הניתוח רץ על התמונה המקורית
                arenaIndex = worldStyle.ordinal
                PhotoLibrary.select(context, file)
                PhotoLibrary.clearPending(context)
                com.arsniper.pano.PanoramaStore.clearPendingGame(context)
                quick = false
                modeArena = true
                if (PhotoLibrary.isReviewed(file)) { goScene() } else { screen = Screen.Review }
            }
        )
        Screen.Play -> PlayScreen(
            onBack = { if (chosenMode == "classic") screen = Screen.ChooseGame else if (mapFromStart) { arenaFirst = false; screen = Screen.Arena } else screen = Screen.Start },
            onTakePhoto = { PhotoLibrary.clearPending(context); com.arsniper.pano.PanoramaStore.clearPendingGame(context); quick = false; screen = Screen.Capture },
            onGalleryPicked = { com.arsniper.pano.PanoramaStore.clearPendingGame(context); quick = false; screen = Screen.Review },
            onPickSaved = { file ->
                PhotoLibrary.select(context, file)
                PhotoLibrary.clearPending(context)
                com.arsniper.pano.PanoramaStore.clearPendingGame(context)
                quick = false
                // ניתוח אנשים הוא חובה: תמונה שלא נסקרה עוברת קודם במסך הסקירה
                if (PhotoLibrary.isReviewed(file)) { goScene() } else { screen = Screen.Review }
            },
            onQuick = {
                val all = PhotoLibrary.list(context)
                val file = all.filter { PhotoLibrary.isReviewed(it) }.randomOrNull() ?: all.randomOrNull()
                if (file != null) {
                    PhotoLibrary.select(context, file)
                    PhotoLibrary.clearPending(context)
                    com.arsniper.pano.PanoramaStore.clearPendingGame(context)
                    quick = true
                    if (PhotoLibrary.isReviewed(file)) { goScene() } else { screen = Screen.Review }
                }
            }
        )
        Screen.Capture -> CaptureScreen(
            onPhotoCaptured = { com.arsniper.pano.PanoramaStore.clearPendingGame(context); screen = Screen.Review },
            onBack = { screen = if (coopPick) { coopPick = false; Screen.Coop } else if (modeArena) Screen.ArenaMode else Screen.Play },
            onGalleryPicked = { screen = Screen.Review },
            onPanorama = { screen = Screen.PanoView }
        )
        Screen.PanoView -> com.arsniper.ui.capture.PanoViewScreen(
            onBack = { screen = if (coopPick) { coopPick = false; Screen.Coop } else if (modeArena) Screen.ArenaMode else Screen.Play },
            onPlay = { quick = false; screen = Screen.Review }
        )
        Screen.Review -> ReviewScreen(
            onContinue = {
                try { com.arsniper.pano.PanoGame.finalizeReview(context) } catch (e: Throwable) { android.util.Log.w("SnapBoo", "finalizeReview failed", e) }
                goScene()
            },
            onRetake = { screen = Screen.Capture }
        )
        Screen.Scene -> SceneScreen(
            onContinue = {
                if (coopPick) {
                    // הסריקה הסתיימה: חוזרים ללובי עם התמונה החדשה
                    coopPick = false
                    coopPreselect = true
                    screen = Screen.Coop
                } else if (coopRole.isNotEmpty() || arenaFirst) {
                    arenaFirst = false
                    screen = Screen.Game
                } else if (quick) {
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
            onPick = {
                arenaIndex = it.ordinal
                if (arenaFirst) {
                    com.arsniper.ml.AiWorldWorker.startFor(context, it)
                    screen = Screen.Scene
                } else if (mapFromStart) {
                    screen = Screen.ArenaMode
                } else {
                    screen = Screen.Game
                }
            },
            onMyPhotos = { arenaFirst = false; screen = Screen.Play },
            onShop = { fromMain = ""; shopTab = 0; screen = Screen.Shop },
            onArmory = { fromMain = ""; screen = Screen.Armory },
            onMultiplayer = { fromMain = ""; screen = Screen.Multiplayer },
            onBack = { if (mapFromStart && !arenaFirst) { screen = if (chosenMode == "journey") Screen.ChooseGame else Screen.Start } else { arenaFirst = false; screen = Screen.Play } }
        )
        Screen.ArenaMode -> com.arsniper.ui.arena.ArenaModeScreen(
            style = ArenaStyle.values()[arenaIndex.coerceIn(0, ArenaStyle.values().size - 1)],
            onBack = { modeArena = false; screen = Screen.Arena },
            onShop = { fromMain = ""; shopTab = 0; screen = Screen.Shop },
            onUseLast = { file ->
                PhotoLibrary.select(context, file)
                PhotoLibrary.clearPending(context)
                com.arsniper.pano.PanoramaStore.clearPendingGame(context)
                quick = false
                modeArena = true
                goScene()
            },
            onPlayWorld = { file, worldStyle ->
                arenaIndex = worldStyle.ordinal
                PhotoLibrary.select(context, file)
                PhotoLibrary.clearPending(context)
                com.arsniper.pano.PanoramaStore.clearPendingGame(context)
                quick = false
                modeArena = true
                if (PhotoLibrary.isReviewed(file)) { goScene() } else { screen = Screen.Review }
            },
            onCamera = {
                PhotoLibrary.clearPending(context)
                com.arsniper.pano.PanoramaStore.clearPendingGame(context)
                quick = false
                modeArena = true
                screen = Screen.Capture
            },
            onGalleryImported = {
                com.arsniper.pano.PanoramaStore.clearPendingGame(context)
                quick = false
                modeArena = true
                screen = Screen.Review
            }
        )
        Screen.Game -> if (coopRole.isEmpty() && com.arsniper.ml.SceneSession.analysis == null && !restoreTried) {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                restoreTried = true
                arenaFirst = true
                screen = Screen.Scene
            }
        } else GameScreen(
            style = ArenaStyle.values()[arenaIndex.coerceIn(0, ArenaStyle.values().size - 1)],
            coop = coopRole.ifEmpty { null },
            onExit = {
                if (coopRole.isNotEmpty()) {
                    if (coopRole == "host") com.arsniper.coop.CoopSync.sendEnd()
                    coopRole = ""
                    screen = Screen.Coop
                } else {
                    screen = Screen.Play
                }
            },
            onNextArena = { quick = false; arenaFirst = false; screen = Screen.Arena }
        )
        Screen.Shop -> com.arsniper.ui.shop.CurrencyShopScreen(
            initialTab = shopTab,
            onBack = { screen = if (fromMain == "choose") Screen.ChooseGame else if (fromMain == "shop") Screen.Start else if (mapFromStart) (if (modeArena) Screen.ArenaMode else Screen.Arena) else Screen.Start },
            onArmory = { fromMain = ""; screen = Screen.Armory }
        )
        Screen.Multiplayer -> com.arsniper.ui.menu.MultiplayerScreen(
            onBack = { screen = if (fromMain == "multi") Screen.Start else Screen.Arena },
            onCoop = { coopFromMulti = true; screen = Screen.Coop }
        )
        Screen.Armory -> ShopScreen(onBack = { screen = if (fromMain == "armory") Screen.Start else Screen.Shop })
        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Start }, onArtLab = { screen = Screen.ArtLab }, onScanLab = { screen = Screen.ScanLab }, onSceneDebug = { screen = Screen.SceneDebug }, onCoop = { coopFromMulti = false; screen = Screen.Coop })
        Screen.ScanLab -> ScanLabScreen(onBack = { screen = Screen.Settings })
        Screen.ArtLab -> ArtLabScreen(onBack = { screen = Screen.Settings }, onUsePhoto = { screen = Screen.Review })
        Screen.Stats -> StatsScreen(onBack = { screen = Screen.Start })
        Screen.Coop -> com.arsniper.ui.menu.CoopScreen(
            onBack = { coopPreselect = false; screen = if (coopFromMulti) Screen.Multiplayer else Screen.Settings },
            preselectCurrent = coopPreselect,
            onNewPhoto = { camera ->
                // החדר נשאר מחובר בזמן הצילום והסריקה
                com.arsniper.coop.CoopSync.keepConnection = true
                PhotoLibrary.clearPending(context)
                com.arsniper.pano.PanoramaStore.clearPendingGame(context)
                quick = false
                coopPick = true
                coopPreselect = false
                screen = if (camera) Screen.Capture else Screen.Review
            },
            onStart = { style, role ->
                coopPreselect = false
                // החיבור נשאר פתוח, והמשחק לוקח את ההודעות מהלובי
                com.arsniper.coop.CoopSync.keepConnection = true
                com.arsniper.coop.CoopSync.resetGame()
                com.arsniper.coop.CoopSync.attachGame()
                arenaIndex = style
                coopRole = role
                quick = false
                screen = Screen.Scene
            }
        )
    }
}

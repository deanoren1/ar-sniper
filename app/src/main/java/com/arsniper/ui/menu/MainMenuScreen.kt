package com.arsniper.ui.menu

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PhotoImport
import com.arsniper.data.PhotoRepository
import com.arsniper.data.PlayerProfile
import com.arsniper.game.engine.ArenaDirector
import com.arsniper.ui.components.CurrencyPill
import com.arsniper.ui.components.GameButton
import com.arsniper.ui.components.GameIcon
import com.arsniper.ui.components.GameIconButton
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.PlayerBadge
import com.arsniper.ui.game.MonsterSprite
import com.arsniper.ui.game.MonsterSprites
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.ResponsiveBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ספרייטי המפלצות להצגה בתפריט: לקוחים מהגיליון הקיים בלי שינוי */
private class MenuMonsters(
    val dragon: MonsterSprite?,
    val alien: MonsterSprite?,
    val hedgehog: MonsterSprite?,
    val octopus: MonsterSprite?
)

private fun loadMenuMonsters(context: Context): MenuMonsters? {
    val sprites = MonsterSprites.load(context) ?: return null
    // id % 5 = שורת הגיליון: 0 ענק, 1 תמנון, 2 קיפוד, 3 בלון, 4 חייזר. הבוס לפי BOSS_ID
    return MenuMonsters(
        dragon = sprites.forEnemy(ArenaDirector.BOSS_ID).idle,
        alien = sprites.forEnemy(4).idle,
        hedgehog = sprites.forEnemy(2).idle,
        octopus = sprites.forEnemy(1).idle
    )
}

/**
 * התפריט הראשי: תמונת הרקע של השחקן, כותרת, מפלצות, PLAY גדול, וכפתורי הגדרות / סטטיסטיקה / חנות / תמונה.
 * אין כאן לוגיקת משחק: רק ניווט.
 */
@Composable
fun MainMenuScreen(
    onStart: () -> Unit,
    onPhotoPicked: () -> Unit,
    onShop: () -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val photo = remember { PhotoRepository.load(context)?.asImageBitmap() }
    var monsters by remember { mutableStateOf<MenuMonsters?>(null) }
    LaunchedEffect(Unit) {
        monsters = withContext(Dispatchers.Default) { runCatching { loadMenuMonsters(context) }.getOrNull() }
    }
    // קריאה כדי שהמסך יתעדכן כשהפרופיל משתנה
    PlayerProfile.revision.hashCode()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            failed = false
            scope.launch {
                val ok = withContext(Dispatchers.IO) { PhotoImport.importFromUri(context, uri) }
                busy = false
                if (ok) onPhotoPicked() else failed = true
            }
        }
    }

    val bob = rememberInfiniteTransition(label = "bob")
    val bobY by bob.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "bobY"
    )

    ResponsiveBox(modifier = modifier.background(GameColors.Navy900)) { ui ->
        // רקע: התמונה של השחקן (אם יש) מוחשכת בכחול
        if (photo != null) {
            Image(
                bitmap = photo, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(GameColors.Navy900.copy(alpha = 0.55f), GameColors.Navy800.copy(alpha = 0.85f))
                    )
                )
        )

        // מפלצות: כולן מהספרייטים הקיימים
        val m = monsters
        if (m != null) {
            m.alien?.let {
                Image(
                    bitmap = it.image, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = ui.dp(20f), top = ui.dp(40f))
                        .size(ui.dp(130f))
                        .graphicsLayer { translationY = bobY * 1.3f }
                )
            }
            m.hedgehog?.let {
                Image(
                    bitmap = it.image, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = ui.dp(90f), bottom = ui.dp(20f))
                        .size(ui.dp(90f))
                        .graphicsLayer { translationY = -bobY }
                )
            }
            m.dragon?.let {
                Image(
                    bitmap = it.image, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = ui.dp(20f), top = ui.dp(30f))
                        .size(ui.dp(190f))
                        .graphicsLayer { translationY = bobY }
                )
            }
            m.octopus?.let {
                Image(
                    bitmap = it.image, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = ui.dp(150f), bottom = ui.dp(14f))
                        .size(ui.dp(90f))
                        .graphicsLayer { translationY = bobY * 0.8f }
                )
            }
        }

        // פינה שמאלית עליונה: תג שחקן
        PlayerBadge(
            name = PlayerProfile.playerName,
            level = PlayerProfile.level,
            progress = PlayerProfile.levelProgress,
            portrait = m?.octopus,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(ui.dp(12f))
        )
        // פינה ימנית עליונה: מטבעות ויהלומים
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(ui.dp(12f))
        ) {
            CurrencyPill(PlayerProfile.coins, isGems = false)
            CurrencyPill(PlayerProfile.diamonds, isGems = true, onPlus = onShop)
        }

        // כותרת ופעולות במרכז
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(modifier = Modifier.rotate(-3f), verticalAlignment = Alignment.CenterVertically) {
                OutlinedText("AR ", fontSize = (ui.factor * 52f).sp, color = GameColors.Sky, outlineWidth = 14f)
                OutlinedText("SNIPER", fontSize = (ui.factor * 52f).sp, color = GameColors.Yellow, outlineWidth = 14f)
            }
            Box(Modifier.size(ui.dp(14f)))
            GameButton(
                text = "PLAY",
                onClick = onStart,
                icon = GameIcon.Play,
                enabled = !busy,
                height = ui.dp(64f),
                fontSize = (ui.factor * 30f).sp,
                modifier = Modifier.width(ui.dp(230f))
            )
            Box(Modifier.size(ui.dp(12f)))
            Row(horizontalArrangement = Arrangement.spacedBy(ui.dp(14f))) {
                MenuIcon(GameIcon.Gear, "SETTINGS", onSettings, ui.dp(48f))
                MenuIcon(GameIcon.Stats, "STATS", onStats, ui.dp(48f))
                MenuIcon(GameIcon.Cart, "SHOP", onShop, ui.dp(48f), GameButtonStyle.Success)
                MenuIcon(GameIcon.Photo, if (busy) "LOADING" else "PHOTO", { picker.launch(arrayOf("image/*")) }, ui.dp(48f), enabled = !busy)
            }
            if (failed) {
                OutlinedText("Could not open that photo", fontSize = 14.sp, color = Color(0xFFFF8A80))
            }
        }
    }
}

@Composable
private fun MenuIcon(
    icon: GameIcon,
    label: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    style: GameButtonStyle = GameButtonStyle.Secondary,
    enabled: Boolean = true
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GameIconButton(icon, onClick = onClick, style = style, enabled = enabled, size = size)
        OutlinedText(label, fontSize = 11.sp, outlineWidth = 4f)
    }
}

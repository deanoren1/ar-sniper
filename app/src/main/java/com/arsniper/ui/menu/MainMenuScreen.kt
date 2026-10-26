package com.arsniper.ui.menu

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import com.arsniper.R
import com.arsniper.data.PhotoImport
import com.arsniper.ui.components.GameButton
import com.arsniper.ui.components.GameIcon
import com.arsniper.ui.components.GameIconButton
import com.arsniper.ui.components.CurrencyBar
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.PlayerBadge
import com.arsniper.data.PlayerProfile
import com.arsniper.ui.game.MonsterSprites
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.ResponsiveBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * התפריט הראשי: תמונת הפתיחה של SnapBoo (הלוגו והמפלצות כלולים בתמונה), PLAY גדול,
 * וכפתורי הגדרות / סטטיסטיקה / חנות / תמונה. אין כאן לוגיקת משחק: רק ניווט.
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
    ResponsiveBox(modifier = modifier.background(GameColors.Navy900)) { ui ->
        Image(
            painter = painterResource(R.drawable.title_art),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // מיושר לחלק העליון: החיתוך נלקח מהתחתית, כדי שהלוגו לא ייחתך
            alignment = androidx.compose.ui.BiasAlignment(0f, -1f),
            modifier = Modifier.fillMaxSize()
        )
        // הצללה עדינה בתחתית, כדי שהכפתורים ייקראו על התמונה
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.65f to Color.Transparent,
                        1f to Color(0x88081028)
                    )
                )
        )

        CurrencyBar(
            PlayerProfile.coins, PlayerProfile.diamonds, onPlus = onShop,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = ui.dp(10f), top = ui.dp(8f))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = ui.dp(6f))
        ) {
            GameButton(
                text = "PLAY",
                onClick = onStart,
                icon = GameIcon.Play,
                height = ui.dp(48f),
                fontSize = (ui.factor * 26f).sp,
                modifier = Modifier.width(ui.dp(190f))
            )
            Box(Modifier.size(ui.dp(5f)))
            Row(horizontalArrangement = Arrangement.spacedBy(ui.dp(12f))) {
                MenuIcon(GameIcon.Gear, "SETTINGS", onSettings, ui.dp(38f))
                MenuIcon(GameIcon.Stats, "STATS", onStats, ui.dp(38f))
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
    enabled: Boolean = true
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GameIconButton(icon, onClick = onClick, style = GameButtonStyle.Secondary, enabled = enabled, size = size)
    }
}

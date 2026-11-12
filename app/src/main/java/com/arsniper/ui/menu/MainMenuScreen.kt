package com.arsniper.ui.menu

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
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
import com.arsniper.ui.components.CurrencyCounter
import com.arsniper.ui.components.SpriteButton
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.PlayerBadge
import com.arsniper.data.PlayerProfile
import com.arsniper.ui.game.MonsterSprites
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.ResponsiveBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * התפריט הראשי: תמונת הפתיחה המאושרת (הלוגו והמפלצות כלולים בה ולא נוגעים בה) ומעליה רכיבי ממשק נפרדים:
 * מונה מטבעות ויהלומים בפינה הימנית העליונה, PLAY גדול ושורת ארבעה כפתורים מתחתיו.
 * אין כאן לוגיקת משחק ולא מצב מטבעות משלו: רק ניווט, והמספרים מגיעים מ-PlayerProfile.
 *
 * הקנבס המתוכנן הוא 2560x1180. כל המיקומים נמדדים ביחס לרוחב המסך (כל פיקסל קנבס = UNIT),
 * והרקע והממשק משתמשים באותו מלבן "במה", כך שהאלמנטים נשארים באותו מקום ביחס לדמויות בכל מסך.
 */
private const val CANVAS_W = 2560f
private const val CANVAS_H = 1180f

// מיקומים וגדלים בפיקסלי קנבס (2560x1180)
private const val PLAY_CENTER_X = 1280f
private const val PLAY_CENTER_Y = 790f
private const val PLAY_WIDTH = 850f
private const val PLAY_SPRITE_RATIO = 235f / 900f
private const val ROW_CENTER_Y = 1060f
private const val ICON_HEIGHT = 140f
private const val ICON_WIDTH = ICON_HEIGHT * 300f / 287f
private const val ICON_GAP = 24f
private const val CORNER_MARGIN = 28f
private const val COUNTER_GAP = 24f

@Composable
fun MainMenuScreen(
    onStart: () -> Unit,
    onShop: (tab: Int) -> Unit,
    onMultiplayer: () -> Unit,
    onArmory: () -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(GameColors.Navy900).clipToBounds()) {
        // במה: רוחב המסך, ביחס 2560:1180, ממורכזת אנכית. במסך צר מהיחס היא נכנסת כולה (ושוליים ממולאים ברקע),
        // במסך רחב ממנו היא חותכת מעט למעלה ולמטה, בדיוק כמו הרקע.
        val stageW = maxWidth
        val stageH = stageW * (CANVAS_H / CANVAS_W)
        val unit = stageW / CANVAS_W
        val stageTop = (maxHeight - stageH) / 2

        // שכבת מילוי לשוליים (רק במסך צר מהיחס), ומעליה הרקע במקומו המדויק
        Image(
            painter = painterResource(R.drawable.title_art),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Image(
            painter = painterResource(R.drawable.title_art),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.offset(y = stageTop).size(stageW, stageH)
        )

        // מונים בפינה הימנית העליונה: מטבעות קודם, יהלומים אחריהם. שוליים לפי אזורי המערכת (חריץ, פינות)
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        Row(
            horizontalArrangement = Arrangement.spacedBy(unit * COUNTER_GAP),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = maxOf(unit * CORNER_MARGIN, insets.calculateTopPadding()),
                    end = maxOf(unit * CORNER_MARGIN, insets.calculateEndPadding(layoutDirection))
                )
        ) {
            CurrencyCounter(R.drawable.ui_icon_coin, PlayerProfile.coins, "Coins", { onShop(0) }, unit)
            CurrencyCounter(R.drawable.ui_icon_gem, PlayerProfile.diamonds, "Gems", { onShop(1) }, unit)
        }

        // PLAY: במרכז הקנבס
        val playW = unit * PLAY_WIDTH
        val playH = playW * PLAY_SPRITE_RATIO
        SpriteButton(
            sprite = R.drawable.ui_btn_play,
            contentDescription = "Play",
            onClick = onStart,
            width = playW,
            height = playH,
            modifier = Modifier.offset(
                x = unit * PLAY_CENTER_X - maxOf(playW, 48.dp) / 2,
                y = stageTop + unit * PLAY_CENTER_Y - maxOf(playH, 48.dp) / 2
            )
        )

        // ארבעת הכפתורים: שורה אחת ממורכזת מתחת ל-PLAY. אותו גודל ואותו עיצוב, בלי תוויות
        val iconW = unit * ICON_WIDTH
        val iconH = unit * ICON_HEIGHT
        val rowW = iconW * 4 + unit * ICON_GAP * 3
        val items = listOf(
            Triple(R.drawable.ui_btn_multiplayer, "Multiplayer", onMultiplayer),
            Triple(R.drawable.ui_btn_armory, "Armory", onArmory),
            Triple(R.drawable.ui_btn_settings, "Settings", onSettings),
            Triple(R.drawable.ui_btn_stats, "Stats", onStats)
        )
        items.forEachIndexed { i, (sprite, label, action) ->
            SpriteButton(
                sprite = sprite,
                contentDescription = label,
                onClick = action,
                width = iconW,
                height = iconH,
                modifier = Modifier.offset(
                    x = unit * PLAY_CENTER_X - rowW / 2 + (iconW + unit * ICON_GAP) * i - (maxOf(iconW, 48.dp) - iconW) / 2,
                    y = stageTop + unit * ROW_CENTER_Y - maxOf(iconH, 48.dp) / 2
                )
            )
        }
    }
}

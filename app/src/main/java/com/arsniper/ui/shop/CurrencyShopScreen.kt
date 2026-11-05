package com.arsniper.ui.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PlayerProfile
import com.arsniper.ui.components.CurrencyBar
import com.arsniper.ui.components.GameButton
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.ScreenHeader
import com.arsniper.ui.game.CoinIcon
import com.arsniper.ui.game.DiamondIcon
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.ResponsiveBox

/** חבילת מטבעות שנקנית ביהלומים */
private class CoinPack(val coins: Int, val gems: Int)

private val COIN_PACKS = listOf(CoinPack(500, 5), CoinPack(2500, 20), CoinPack(7500, 50))

/** חבילות יהלומים בכסף אמיתי: מוצגות, אבל עדיין לא פעילות (דורש חיבור ל-Google Play Billing) */
private val GEM_PACKS = listOf(100, 550, 1200)

/**
 * החנות (מטבעות ויהלומים), נפרדת מחנות הנשקים (ARMORY).
 * מטבעות: קונים ביהלומים. יהלומים: חבילות בכסף אמיתי, "בקרוב".
 */
@Composable
fun CurrencyShopScreen(onBack: () -> Unit, onArmory: () -> Unit, modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) { kotlinx.coroutines.delay(2000); message = null }
    }
    val coins = PlayerProfile.coins
    val diamonds = PlayerProfile.diamonds

    ResponsiveBox(modifier = modifier.background(GameColors.Navy900)) { ui ->
        com.arsniper.ui.components.MenuBackdrop()
        Column(modifier = Modifier.fillMaxSize().padding(ui.dp(12f))) {
            ScreenHeader(title = "SHOP", onBack = onBack) {
                CurrencyBar(coins, diamonds)
            }
            Spacer(Modifier.height(ui.dp(10f)))
            Row(modifier = Modifier.fillMaxSize()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(150.dp)) {
                    TabButton("COINS", tab == 0) { tab = 0 }
                    TabButton("GEMS", tab == 1) { tab = 1 }
                    Spacer(Modifier.weight(1f))
                    GameButton(text = "ARMORY", onClick = onArmory, style = GameButtonStyle.Secondary, height = 44.dp, fontSize = 16.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        if (tab == 0) {
                            for (p in COIN_PACKS) {
                                PackCard(
                                    modifier = Modifier.weight(1f),
                                    icon = { CoinIcon(40.dp) },
                                    amount = "${p.coins}",
                                    priceText = "${p.gems} GEMS",
                                    enabled = diamonds >= p.gems,
                                    onClick = {
                                        if (PlayerProfile.spendDiamonds(p.gems)) {
                                            PlayerProfile.addCoins(p.coins)
                                            message = "+${p.coins} coins"
                                        }
                                    }
                                )
                            }
                        } else {
                            for (g in GEM_PACKS) {
                                PackCard(
                                    modifier = Modifier.weight(1f),
                                    icon = { DiamondIcon(40.dp) },
                                    amount = "$g",
                                    priceText = "SOON",
                                    enabled = false,
                                    onClick = { message = "Gem packs are coming soon" }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedText(
                        if (tab == 0) "Buy coins with gems. Gems are earned from boss wins and 3-star clears."
                        else "Real-money gem packs will open when store billing is connected.",
                        fontSize = 12.sp, color = GameColors.Ice, outline = null
                    )
                }
            }
        }
        message?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                OutlinedText(
                    it, fontSize = 14.sp, outline = null, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                        .background(Color(0xE6081028), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, onClick: () -> Unit) {
    GameButton(
        text = text, onClick = onClick,
        style = if (selected) GameButtonStyle.Primary else GameButtonStyle.Secondary,
        height = 44.dp, fontSize = 17.sp, modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PackCard(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    amount: String,
    priceText: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF0A1D50))
            .border(2.5.dp, if (enabled) Color(0xFF2F8BFF) else Color(0xFF28365E), shape)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        icon()
        OutlinedText(amount, fontSize = 24.sp, color = Color.White, outline = null)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (enabled) Color(0xFF1E9E45) else Color(0xFF3A4668)).padding(vertical = 6.dp)
        ) {
            OutlinedText(priceText, fontSize = 14.sp, color = Color.White, outline = null)
        }
    }
}

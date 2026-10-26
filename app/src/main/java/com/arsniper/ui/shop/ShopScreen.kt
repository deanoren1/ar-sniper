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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PlayerProfile
import com.arsniper.game.progress.Catalog
import com.arsniper.game.progress.UpgradeKind
import com.arsniper.game.progress.WeaponId
import com.arsniper.game.progress.WeaponSpec
import com.arsniper.ui.components.CurrencyBar
import com.arsniper.ui.components.GameButton
import com.arsniper.ui.components.NavyPanel
import com.arsniper.ui.components.OutlinedText
import com.arsniper.ui.components.ScreenHeader
import com.arsniper.ui.game.CoinIcon
import com.arsniper.ui.game.DiamondIcon
import com.arsniper.ui.game.WeaponPreview
import com.arsniper.ui.theme.GameButtonStyle
import com.arsniper.ui.theme.GameColors
import com.arsniper.ui.theme.ResponsiveBox

/**
 * החנות: רשימת נשקים (נקנים במטבעות, או ביהלומים לנשקי פרימיום), ובצד הנשק הנבחר עם שדרוגים.
 * כל נשק משודרג בנפרד: נזק, גודל מחסנית ומהירות טעינה, עד רמה 5.
 * בסגנון המשחק: אותם רכיבים כמו התפריט הראשי. הלוגיקה (קנייה, שדרוג) לא השתנתה.
 */
@Composable
fun ShopScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(PlayerProfile.equipped) }
    // קריאה למאזנים גורמת לציור מחדש בכל שינוי בפרופיל
    val coins = PlayerProfile.coins
    val diamonds = PlayerProfile.diamonds

    ResponsiveBox(modifier = modifier.background(GameColors.Navy900)) { ui ->
        Column(modifier = Modifier.fillMaxSize().padding(ui.dp(12f))) {
            ScreenHeader(title = "SHOP", onBack = onBack) {
                CurrencyBar(coins, diamonds)
            }
            Spacer(Modifier.height(ui.dp(10f)))
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(ui.dp(8f)),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    items(Catalog.weapons) { spec ->
                        WeaponCard(spec, spec.id == selected) { selected = spec.id }
                    }
                }
                Spacer(Modifier.width(ui.dp(12f)))
                WeaponDetail(
                    spec = Catalog.spec(selected),
                    coins = coins,
                    diamonds = diamonds,
                    modifier = Modifier
                        .weight(1.4f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun WeaponCard(spec: WeaponSpec, isSelected: Boolean, onClick: () -> Unit) {
    val owned = PlayerProfile.owns(spec.id)
    val equipped = PlayerProfile.equipped == spec.id
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) GameColors.Navy600 else GameColors.Navy800)
            .border(3.dp, if (isSelected) GameColors.Yellow else GameColors.Outline, shape)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        WeaponPreview(spec.id, Modifier.size(width = 84.dp, height = 56.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            OutlinedText(spec.title, fontSize = 16.sp, outline = null)
            OutlinedText(spec.tagline, fontSize = 11.sp, color = GameColors.Ice, outline = null)
        }
        when {
            equipped -> OutlinedText("EQUIPPED", fontSize = 12.sp, color = GameColors.Green, outline = null)
            owned -> OutlinedText("OWNED", fontSize = 12.sp, color = GameColors.Ice, outline = null)
            else -> PriceTag(spec)
        }
    }
}

@Composable
private fun PriceTag(spec: WeaponSpec) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (spec.price > 0) {
            CoinIcon(18.dp)
            OutlinedText(" ${spec.price}", fontSize = 14.sp, color = GameColors.Yellow, outline = null)
        } else {
            DiamondIcon(18.dp)
            OutlinedText(" ${spec.diamondPrice}", fontSize = 14.sp, color = GameColors.Sky, outline = null)
        }
    }
}

@Composable
private fun WeaponDetail(spec: WeaponSpec, coins: Int, diamonds: Int, modifier: Modifier = Modifier) {
    val owned = PlayerProfile.owns(spec.id)
    val equipped = PlayerProfile.equipped == spec.id
    val damageLevel = PlayerProfile.levelOf(spec.id, UpgradeKind.Damage)
    val magLevel = PlayerProfile.levelOf(spec.id, UpgradeKind.Magazine)
    val reloadLevel = PlayerProfile.levelOf(spec.id, UpgradeKind.Reload)
    val stats = Catalog.stats(spec, damageLevel, magLevel, reloadLevel)

    NavyPanel(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeaponPreview(spec.id, Modifier.size(width = 150.dp, height = 96.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    OutlinedText(spec.title, fontSize = 24.sp)
                    OutlinedText(
                        "DAMAGE ${"%.1f".format(stats.damage)}   MAG ${stats.magazine}   RELOAD ${"%.2f".format(stats.reloadSeconds)}s",
                        fontSize = 12.sp, color = GameColors.Ice, outline = null
                    )
                    if (stats.autoRate > 0f) OutlinedText("Hold to fire: ${stats.autoRate}/s", fontSize = 12.sp, color = GameColors.Ice, outline = null)
                    if (stats.pellets > 1) OutlinedText("${stats.pellets} pellets per shot", fontSize = 12.sp, color = GameColors.Ice, outline = null)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (!owned) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (spec.price > 0) {
                        GameButton(
                            text = "BUY ${spec.price} COINS",
                            onClick = { PlayerProfile.buyWithCoins(spec.id) },
                            enabled = coins >= spec.price,
                            style = GameButtonStyle.Primary,
                            height = 46.dp,
                            fontSize = 17.sp
                        )
                    }
                    if (spec.diamondPrice > 0) {
                        GameButton(
                            text = "BUY ${spec.diamondPrice} GEMS",
                            onClick = { PlayerProfile.buyWithDiamonds(spec.id) },
                            enabled = diamonds >= spec.diamondPrice,
                            style = GameButtonStyle.Secondary,
                            height = 46.dp,
                            fontSize = 17.sp
                        )
                    }
                }
            } else {
                GameButton(
                    text = if (equipped) "EQUIPPED" else "EQUIP",
                    onClick = { PlayerProfile.equip(spec.id) },
                    enabled = !equipped,
                    style = GameButtonStyle.Success,
                    height = 46.dp,
                    fontSize = 17.sp
                )
                Spacer(Modifier.height(10.dp))
                for (kind in UpgradeKind.values()) {
                    UpgradeRow(spec.id, kind, coins)
                }
            }
        }
    }
}

@Composable
private fun UpgradeRow(id: WeaponId, kind: UpgradeKind, coins: Int) {
    val level = PlayerProfile.levelOf(id, kind)
    val maxed = level >= Catalog.MAX_LEVEL
    val cost = Catalog.upgradeCost(kind, level)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        OutlinedText(Catalog.upgradeTitle(kind), fontSize = 14.sp, outline = null, modifier = Modifier.width(96.dp))
        for (i in 0 until Catalog.MAX_LEVEL) {
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(width = 18.dp, height = 12.dp)
                    .border(1.5.dp, GameColors.Outline, RoundedCornerShape(3.dp))
                    .background(if (i < level) GameColors.Sky else GameColors.Navy900, RoundedCornerShape(3.dp))
            )
        }
        Spacer(Modifier.weight(1f))
        GameButton(
            text = if (maxed) "MAX" else "$cost",
            onClick = { PlayerProfile.upgrade(id, kind) },
            enabled = !maxed && coins >= cost,
            style = if (maxed) GameButtonStyle.Locked else GameButtonStyle.Primary,
            height = 36.dp,
            fontSize = 15.sp
        )
    }
}

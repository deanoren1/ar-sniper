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

/** קטגוריות חנות הנשקים. רק לנשקים שקיימים במשחק יש תוכן: השאר מסומנות "בקרוב" */
private enum class ArmoryTab(val label: String, val weapons: List<WeaponId>) {
    Guns("GUNS", listOf(WeaponId.Plasma, WeaponId.Auto, WeaponId.Spread)),
    Snipers("SNIPERS", listOf(WeaponId.Sniper)),
    Heavy("HEAVY", emptyList()),
    Special("SPECIAL", emptyList()),
    Skins("SKINS", emptyList())
}

/**
 * חנות הנשקים (ARMORY), בפריסה כמו בעיצוב: קטגוריות מימין לשמאל בצד, רשת כרטיסי נשק, ופס פרטים בתחתית
 * עם נתונים, שדרוגים וכפתור קנייה/הצבה. הלוגיקה (קנייה, שדרוג, הצבה) לא השתנתה.
 */
@Composable
fun ShopScreen(onBack: () -> Unit, modifier: Modifier = Modifier, title: String = "ARMORY") {
    var selected by remember { mutableStateOf(PlayerProfile.equipped) }
    var tab by remember { mutableStateOf(ArmoryTab.values().firstOrNull { PlayerProfile.equipped in it.weapons } ?: ArmoryTab.Guns) }
    // קריאה למאזנים גורמת לציור מחדש בכל שינוי בפרופיל
    val coins = PlayerProfile.coins
    val diamonds = PlayerProfile.diamonds

    ResponsiveBox(modifier = modifier.background(GameColors.Navy900)) { ui ->
        com.arsniper.ui.components.MenuBackdrop()
        Column(modifier = Modifier.fillMaxSize().padding(ui.dp(12f))) {
            ScreenHeader(title = title, onBack = onBack) {
                CurrencyBar(coins, diamonds)
            }
            Spacer(Modifier.height(ui.dp(8f)))
            Row(modifier = Modifier.fillMaxSize()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(120.dp)) {
                    for (t in ArmoryTab.values()) {
                        GameButton(
                            text = t.label,
                            onClick = { tab = t; t.weapons.firstOrNull()?.let { selected = it } },
                            style = if (tab == t) GameButtonStyle.Primary else GameButtonStyle.Secondary,
                            height = 42.dp, fontSize = 14.sp, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // רשת הנשקים של הקטגוריה
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (tab.weapons.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                OutlinedText("${tab.label} - COMING SOON", fontSize = 18.sp, color = GameColors.Ice, outline = null)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                for (id in tab.weapons) {
                                    WeaponTile(Catalog.spec(id), id == selected, Modifier.weight(1f)) { selected = id }
                                }
                                // משלימים לארבע עמודות כדי שהכרטיסים לא יימתחו
                                for (i in tab.weapons.size until 4) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    if (tab.weapons.isNotEmpty() && selected in tab.weapons) {
                        WeaponBar(Catalog.spec(selected), coins, diamonds, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun StarsRow(filled: Int, total: Int = Catalog.MAX_LEVEL) {
    Row {
        for (i in 0 until total) {
            OutlinedText(
                "★", fontSize = 15.sp,
                color = if (i < filled) GameColors.Yellow else androidx.compose.ui.graphics.Color(0xFF3A4668),
                outline = null
            )
        }
    }
}

/** כוכבי הנשק: לפי ממוצע רמות השדרוגים (לפחות כוכב אחד) */
private fun starsOf(id: WeaponId): Int {
    val avg = (PlayerProfile.levelOf(id, UpgradeKind.Damage) + PlayerProfile.levelOf(id, UpgradeKind.Magazine) + PlayerProfile.levelOf(id, UpgradeKind.Reload)) / 3f
    return avg.toInt().coerceIn(1, Catalog.MAX_LEVEL)
}

@Composable
private fun WeaponTile(spec: WeaponSpec, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val owned = PlayerProfile.owns(spec.id)
    val equipped = PlayerProfile.equipped == spec.id
    val shape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(shape)
            .background(if (isSelected) androidx.compose.ui.graphics.Color(0xFF123A8A) else androidx.compose.ui.graphics.Color(0xFF0A1D50))
            .border(2.5.dp, if (isSelected) GameColors.Yellow else androidx.compose.ui.graphics.Color(0xFF2F8BFF), shape)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box {
            WeaponPreview(spec.id, Modifier.size(width = 120.dp, height = 70.dp))
            if (equipped) OutlinedText("✓", fontSize = 18.sp, color = GameColors.Green, outline = null, modifier = Modifier.align(Alignment.TopEnd))
        }
        OutlinedText(spec.title, fontSize = 15.sp, outline = null)
        if (owned) StarsRow(starsOf(spec.id)) else PriceTag(spec)
    }
}

/** פס הפרטים בתחתית: הנשק הנבחר, נתונים עם שדרוגים, וכפתור קנייה/הצבה */
@Composable
private fun WeaponBar(spec: WeaponSpec, coins: Int, diamonds: Int, modifier: Modifier = Modifier) {
    val owned = PlayerProfile.owns(spec.id)
    val equipped = PlayerProfile.equipped == spec.id
    val stats = Catalog.stats(
        spec,
        PlayerProfile.levelOf(spec.id, UpgradeKind.Damage),
        PlayerProfile.levelOf(spec.id, UpgradeKind.Magazine),
        PlayerProfile.levelOf(spec.id, UpgradeKind.Reload)
    )
    NavyPanel(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            WeaponPreview(spec.id, Modifier.size(width = 110.dp, height = 70.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.width(150.dp)) {
                OutlinedText(spec.title, fontSize = 18.sp)
                if (owned) StarsRow(starsOf(spec.id))
                OutlinedText(
                    buildString {
                        if (stats.autoRate > 0f) append("Hold to fire ${stats.autoRate}/s")
                        if (stats.pellets > 1) append("${stats.pellets} pellets")
                        if (isEmpty()) append(spec.tagline)
                    },
                    fontSize = 11.sp, color = GameColors.Ice, outline = null
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (owned) {
                    for (kind in UpgradeKind.values()) UpgradeRow(spec.id, kind, coins)
                } else {
                    OutlinedText("DAMAGE ${"%.1f".format(stats.damage)}   MAG ${stats.magazine}   RELOAD ${"%.2f".format(stats.reloadSeconds)}s", fontSize = 12.sp, color = GameColors.Ice, outline = null)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.width(130.dp)) {
                if (!owned) {
                    if (spec.price > 0) GameButton(
                        text = "BUY ${spec.price}", onClick = { PlayerProfile.buyWithCoins(spec.id) },
                        enabled = coins >= spec.price, style = GameButtonStyle.Primary, height = 40.dp, fontSize = 15.sp
                    )
                    if (spec.diamondPrice > 0) GameButton(
                        text = "BUY ${spec.diamondPrice} GEMS", onClick = { PlayerProfile.buyWithDiamonds(spec.id) },
                        enabled = diamonds >= spec.diamondPrice, style = GameButtonStyle.Secondary, height = 40.dp, fontSize = 13.sp
                    )
                } else {
                    GameButton(
                        text = if (equipped) "EQUIPPED" else "EQUIP", onClick = { PlayerProfile.equip(spec.id) },
                        enabled = !equipped, style = GameButtonStyle.Success, height = 44.dp, fontSize = 15.sp
                    )
                }
            }
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

package com.arsniper.ui.shop

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PlayerProfile
import com.arsniper.game.progress.Catalog
import com.arsniper.game.progress.UpgradeKind
import com.arsniper.game.progress.WeaponId
import com.arsniper.game.progress.WeaponSpec
import com.arsniper.ui.game.CoinIcon
import com.arsniper.ui.game.CurrencyBar
import com.arsniper.ui.game.DiamondIcon
import com.arsniper.ui.game.WeaponPreview

/**
 * החנות: רשימת נשקים (נקנים במטבעות, או ביהלומים לנשקי פרימיום), ובצד הנשק הנבחר עם שדרוגים.
 * כל נשק משודרג בנפרד: נזק, גודל מחסנית ומהירות טעינה, עד רמה 5.
 */
@Composable
fun ShopScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(PlayerProfile.equipped) }
    // קריאה למאזנים גורמת לציור מחדש בכל שינוי בפרופיל
    val coins = PlayerProfile.coins
    val diamonds = PlayerProfile.diamonds

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))) {
                Text("BACK", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Text("SHOP", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            CurrencyBar(coins = coins, diamonds = diamonds)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                items(Catalog.weapons) { spec ->
                    WeaponCard(spec, spec.id == selected) { selected = spec.id }
                }
            }
            Spacer(Modifier.width(16.dp))
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

@Composable
private fun WeaponCard(spec: WeaponSpec, isSelected: Boolean, onClick: () -> Unit) {
    val owned = PlayerProfile.owns(spec.id)
    val equipped = PlayerProfile.equipped == spec.id
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) Color(0xFF1E3A5F) else Color(0xFF161C24),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        WeaponPreview(spec.id, Modifier.size(width = 84.dp, height = 56.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(spec.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(spec.tagline, color = Color(0xFF9AA5B1), fontSize = 12.sp)
        }
        when {
            equipped -> Text("EQUIPPED", color = Color(0xFF69F0AE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            owned -> Text("OWNED", color = Color(0xFF9AA5B1), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            else -> PriceTag(spec)
        }
    }
}

@Composable
private fun PriceTag(spec: WeaponSpec) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (spec.price > 0) {
            CoinIcon(18.dp)
            Text(" ${spec.price}", color = Color(0xFFFFD54F), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        } else {
            DiamondIcon(18.dp)
            Text(" ${spec.diamondPrice}", color = Color(0xFF84FFFF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
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

    Column(
        modifier = modifier
            .background(Color(0xFF161C24), RoundedCornerShape(12.dp))
            .padding(14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeaponPreview(spec.id, Modifier.size(width = 150.dp, height = 96.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(spec.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "DAMAGE ${"%.1f".format(stats.damage)}   MAG ${stats.magazine}   RELOAD ${"%.2f".format(stats.reloadSeconds)}s",
                    color = Color(0xFFB0BEC5), fontSize = 13.sp
                )
                if (stats.autoRate > 0f) Text("Hold to fire: ${stats.autoRate}/s", color = Color(0xFFB0BEC5), fontSize = 13.sp)
                if (stats.pellets > 1) Text("${stats.pellets} pellets per shot", color = Color(0xFFB0BEC5), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (!owned) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (spec.price > 0) {
                    Button(
                        onClick = { PlayerProfile.buyWithCoins(spec.id) },
                        enabled = coins >= spec.price,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9A825))
                    ) {
                        Text("BUY  ", fontWeight = FontWeight.Bold)
                        CoinIcon(18.dp)
                        Text(" ${spec.price}", fontWeight = FontWeight.Bold)
                    }
                }
                if (spec.diamondPrice > 0) {
                    Button(
                        onClick = { PlayerProfile.buyWithDiamonds(spec.id) },
                        enabled = diamonds >= spec.diamondPrice,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ACC1))
                    ) {
                        Text("BUY  ", fontWeight = FontWeight.Bold)
                        DiamondIcon(18.dp)
                        Text(" ${spec.diamondPrice}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Button(
                onClick = { PlayerProfile.equip(spec.id) },
                enabled = !equipped,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text(if (equipped) "EQUIPPED" else "EQUIP", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            for (kind in UpgradeKind.values()) {
                UpgradeRow(spec.id, kind, coins)
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
        Text(Catalog.upgradeTitle(kind), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(96.dp))
        for (i in 0 until Catalog.MAX_LEVEL) {
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(width = 18.dp, height = 12.dp)
                    .background(if (i < level) Color(0xFF40C4FF) else Color(0x33FFFFFF), RoundedCornerShape(3.dp))
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { PlayerProfile.upgrade(id, kind) },
            enabled = !maxed && coins >= cost,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9A825))
        ) {
            if (maxed) {
                Text("MAX", fontWeight = FontWeight.Bold)
            } else {
                CoinIcon(16.dp)
                Text(" $cost", fontWeight = FontWeight.Bold)
            }
        }
    }
}

package com.arsniper.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.arsniper.game.progress.ArenaStyle
import com.arsniper.game.progress.Catalog
import com.arsniper.game.progress.UpgradeKind
import com.arsniper.game.progress.WeaponId
import com.arsniper.game.progress.WeaponStats

/**
 * הפרופיל השמור של השחקן: מטבעות, יהלומים, נשקים בבעלותו, הנשק הנבחר ורמות השדרוג.
 * נשמר ב-SharedPreferences. revision עולה בכל שינוי, ומסכים שקוראים אותו מתעדכנים.
 */
object PlayerProfile {

    private var prefs: SharedPreferences? = null

    /** מספר שמתעדכן בכל שינוי, כדי שמסכי Compose יציירו מחדש */
    var revision by mutableStateOf(0)
        private set

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("profile", Context.MODE_PRIVATE)
        // מעבר חד פעמי לסקאלת מטבעות קטנה פי עשרה
        prefs?.let { p ->
            if (!p.getBoolean("coins_scaled", false)) {
                p.edit().putInt("coins", p.getInt("coins", 0) / 10).putBoolean("coins_scaled", true).apply()
            }
        }
        revision++
    }

    private fun getInt(key: String, default: Int = 0): Int = prefs?.getInt(key, default) ?: default

    private fun putInt(key: String, value: Int) {
        prefs?.edit()?.putInt(key, value)?.apply()
        revision++
    }

    val coins: Int get() = getInt("coins").also { revision.hashCode() }
    val diamonds: Int get() = getInt("diamonds").also { revision.hashCode() }

    fun addCoins(amount: Int) {
        if (amount > 0) putInt("coins", coins + amount)
    }

    fun addDiamonds(amount: Int) {
        if (amount > 0) putInt("diamonds", diamonds + amount)
    }

    /** מוריד יהלומים אם יש מספיק. מחזיר true אם הצליח */
    fun spendDiamonds(amount: Int): Boolean {
        if (diamonds < amount) return false
        putInt("diamonds", diamonds - amount)
        return true
    }

    fun owns(id: WeaponId): Boolean = id == WeaponId.Plasma || getInt("own_${id.name}") == 1

    var equipped: WeaponId
        get() {
            revision.hashCode()
            val name = prefs?.getString("equipped", WeaponId.Plasma.name) ?: WeaponId.Plasma.name
            val id = WeaponId.values().firstOrNull { it.name == name } ?: WeaponId.Plasma
            return if (owns(id)) id else WeaponId.Plasma
        }
        private set(value) {
            prefs?.edit()?.putString("equipped", value.name)?.apply()
            revision++
        }

    fun equip(id: WeaponId) {
        if (owns(id)) equipped = id
    }

    fun levelOf(id: WeaponId, kind: UpgradeKind): Int = getInt("lvl_${id.name}_${kind.name}").also { revision.hashCode() }

    /** קונה נשק במטבעות. מחזיר true אם הצליח (ואז גם נבחר) */
    fun buyWithCoins(id: WeaponId): Boolean {
        val spec = Catalog.spec(id)
        if (owns(id) || spec.price <= 0 || coins < spec.price) return false
        putInt("coins", coins - spec.price)
        putInt("own_${id.name}", 1)
        equipped = id
        return true
    }

    /** קונה נשק פרימיום ביהלומים */
    fun buyWithDiamonds(id: WeaponId): Boolean {
        val spec = Catalog.spec(id)
        if (owns(id) || spec.diamondPrice <= 0 || !spendDiamonds(spec.diamondPrice)) return false
        putInt("own_${id.name}", 1)
        equipped = id
        return true
    }

    /** משדרג נשק שבבעלותו. מחזיר true אם הצליח */
    fun upgrade(id: WeaponId, kind: UpgradeKind): Boolean {
        if (!owns(id)) return false
        val level = levelOf(id, kind)
        if (level >= Catalog.MAX_LEVEL) return false
        val cost = Catalog.upgradeCost(kind, level)
        if (coins < cost) return false
        putInt("coins", coins - cost)
        putInt("lvl_${id.name}_${kind.name}", level + 1)
        return true
    }

    /** המאפיינים של הנשק הנבחר, כולל השדרוגים */
    fun equippedStats(): WeaponStats {
        val id = equipped
        return Catalog.stats(
            Catalog.spec(id),
            levelOf(id, UpgradeKind.Damage),
            levelOf(id, UpgradeKind.Magazine),
            levelOf(id, UpgradeKind.Reload)
        )
    }

    /** הכוכבים הטובים ביותר שהושגו בארנה (0..3) */
    fun starsOf(style: ArenaStyle): Int = getInt("arena_stars_${style.name}").also { revision.hashCode() }

    /** שומר רק אם התוצאה טובה מהקודמת */
    fun recordStars(style: ArenaStyle, stars: Int) {
        val value = stars.coerceIn(0, 3)
        if (value > starsOf(style)) putInt("arena_stars_${style.name}", value)
    }

    /** ארנה פתוחה אם היא הראשונה, או שהקודמת לה נוצחה לפחות בכוכב אחד */
    fun isUnlocked(style: ArenaStyle): Boolean {
        val before = style.previous ?: return true
        return starsOf(before) >= 1
    }
}

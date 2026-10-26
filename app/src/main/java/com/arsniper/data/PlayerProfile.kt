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
        if (amount > 0) {
            putInt("lifetime_coins", getInt("lifetime_coins") + amount)
            putInt("coins", coins + amount)
        }
    }

    // הגדרות
    var soundOn: Boolean
        get() = prefs?.getBoolean("sound_on", true) ?: true
        set(value) {
            prefs?.edit()?.putBoolean("sound_on", value)?.apply()
            revision++
        }

    var vibrationOn: Boolean
        get() = prefs?.getBoolean("vibration_on", true) ?: true
        set(value) {
            prefs?.edit()?.putBoolean("vibration_on", value)?.apply()
            revision++
        }

    val playerName: String get() = "Player"

    /** רמת הקושי הנבחרת */
    var difficulty: com.arsniper.game.progress.Difficulty
        get() = com.arsniper.game.progress.Difficulty.fromName(prefs?.getString("difficulty", null))
        set(value) {
            prefs?.edit()?.putString("difficulty", value.name)?.apply()
            revision++
        }

    /** כמה תמונות שונות כבר שוחק בהן קרב (לבונוס "זירה חדשה") */
    val photoCount: Int get() = (prefs?.getStringSet("played_photos", emptySet()) ?: emptySet()).size.also { revision.hashCode() }

    /** רושם תמונה שבה הסתיים קרב. מחזיר true אם זו תמונה חדשה (פעם ראשונה) */
    fun registerPhoto(key: String): Boolean {
        val p = prefs ?: return false
        val set = HashSet(p.getStringSet("played_photos", emptySet()) ?: emptySet())
        if (!set.add(key)) return false
        p.edit().putStringSet("played_photos", set).apply()
        revision++
        return true
    }


    // סטטיסטיקות
    val gamesPlayed: Int get() = getInt("stat_games").also { revision.hashCode() }
    val wins: Int get() = getInt("stat_wins").also { revision.hashCode() }
    val bestWave: Int get() = getInt("stat_best_wave").also { revision.hashCode() }
    val bestRunCoins: Int get() = getInt("stat_best_run").also { revision.hashCode() }
    val lifetimeCoins: Int get() = getInt("lifetime_coins").also { revision.hashCode() }

    /** מתעד סיום ריצה (ניצחון או הפסד) */
    fun recordRun(won: Boolean, wave: Int, runCoins: Int) {
        putInt("stat_games", gamesPlayed + 1)
        if (won) putInt("stat_wins", wins + 1)
        if (wave > bestWave) putInt("stat_best_wave", wave)
        if (runCoins > bestRunCoins) putInt("stat_best_run", runCoins)
    }

    /** רמת שחקן לפי כל המטבעות שהרוויח אי פעם */
    val level: Int get() = kotlin.math.sqrt(lifetimeCoins / 40f).toInt() + 1

    /** התקדמות (0..1) לרמה הבאה */
    val levelProgress: Float
        get() {
            val l = level - 1
            val lo = 40f * l * l
            val hi = 40f * (l + 1) * (l + 1)
            return ((lifetimeCoins - lo) / (hi - lo)).coerceIn(0f, 1f)
        }

    /** סך הכוכבים בכל הארנות */
    val totalStars: Int
        get() = com.arsniper.game.progress.ArenaStyle.values().sumOf { starsOf(it) }

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

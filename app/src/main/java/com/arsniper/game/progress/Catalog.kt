package com.arsniper.game.progress

import kotlin.math.roundToInt

/** נשקים במשחק. Plasma בחינם מההתחלה, השאר נקנים במטבעות, ו-Sniper רק ביהלומים */
enum class WeaponId { Plasma, Auto, Spread, Sniper }

/** מה אפשר לשדרג בכל נשק */
enum class UpgradeKind { Damage, Magazine, Reload }

/**
 * נשק בקטלוג. price = מחיר במטבעות (0 = חינם / לא נקנה במטבעות), diamondPrice = מחיר ביהלומים (0 = לא נקנה ביהלומים).
 * autoRate = ירי רצוף בהחזקת הכפתור (ירי לשנייה), 0 = לחיצה אחת לירייה. pellets = כדורים בירייה (פיזור).
 */
class WeaponSpec(
    val id: WeaponId,
    val title: String,
    val tagline: String,
    val price: Int,
    val diamondPrice: Int,
    val baseDamage: Float,
    val baseMagazine: Int,
    val baseReloadSeconds: Float,
    val autoRate: Float = 0f,
    val pellets: Int = 1
)

/** המאפיינים בפועל של נשק, אחרי השדרוגים */
class WeaponStats(
    val damage: Float,
    val magazine: Int,
    val reloadSeconds: Float,
    val autoRate: Float,
    val pellets: Int
)

/** קטלוג הנשקים והשדרוגים. Kotlin טהור */
object Catalog {

    const val MAX_LEVEL = 5
    const val DAMAGE_PER_LEVEL = 0.5f

    val weapons: List<WeaponSpec> = listOf(
        WeaponSpec(
            WeaponId.Plasma, "PLASMA BLASTER", "Fast single shot",
            price = 0, diamondPrice = 0, baseDamage = 1f, baseMagazine = 12, baseReloadSeconds = 1.1f
        ),
        WeaponSpec(
            WeaponId.Auto, "AUTO GUN", "Hold to fire",
            price = 400, diamondPrice = 0, baseDamage = 1f, baseMagazine = 30, baseReloadSeconds = 1.6f,
            autoRate = 4.5f
        ),
        WeaponSpec(
            WeaponId.Spread, "SPREAD GUN", "3 pellets, wide shot",
            price = 800, diamondPrice = 0, baseDamage = 1f, baseMagazine = 9, baseReloadSeconds = 1.4f,
            pellets = 3
        ),
        WeaponSpec(
            WeaponId.Sniper, "SNIPER RIFLE", "Double damage",
            price = 0, diamondPrice = 15, baseDamage = 2f, baseMagazine = 5, baseReloadSeconds = 1.7f
        )
    )

    fun spec(id: WeaponId): WeaponSpec = weapons.first { it.id == id }

    /** מחיר שדרוג מהרמה הנוכחית לבאה (במטבעות) */
    fun upgradeCost(kind: UpgradeKind, currentLevel: Int): Int {
        val base = when (kind) {
            UpgradeKind.Damage -> 60
            UpgradeKind.Magazine -> 35
            UpgradeKind.Reload -> 45
        }
        // עלות משולשת: 1, 3, 6, 10, 15 מהבסיס, כך שרמות גבוהות יקרות בהרבה
        return base * (currentLevel + 1) * (currentLevel + 2) / 2
    }

    fun upgradeTitle(kind: UpgradeKind): String = when (kind) {
        UpgradeKind.Damage -> "DAMAGE"
        UpgradeKind.Magazine -> "MAGAZINE"
        UpgradeKind.Reload -> "RELOAD"
    }

    fun stats(spec: WeaponSpec, damageLevel: Int, magazineLevel: Int, reloadLevel: Int): WeaponStats =
        WeaponStats(
            damage = spec.baseDamage + DAMAGE_PER_LEVEL * damageLevel,
            magazine = (spec.baseMagazine * (1f + 0.2f * magazineLevel)).roundToInt(),
            reloadSeconds = spec.baseReloadSeconds * (1f - 0.1f * reloadLevel),
            autoRate = spec.autoRate,
            pellets = spec.pellets
        )

    /** מחזיר נזק שלם מנזק עשרוני: 1.5 נותן 1 או 2 בהסתברות שווה (r = מספר אקראי בין 0 ל-1) */
    fun rollDamage(damage: Float, r: Float): Int {
        val whole = damage.toInt()
        val fraction = damage - whole
        return (whole + if (r < fraction) 1 else 0).coerceAtLeast(1)
    }
}

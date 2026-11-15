package com.cricketsim.logic

import kotlin.random.Random

/**
 * My Player, part twenty-five: the shop (Career tab > Earnings > Shop).
 *
 * He spends his career earnings on batting gloves and pads, in three kinds: Silver, Golden and Diamond. They protect him from injuries: each item that is
 * working cuts the chance of an injury after a match by up to [CUT_PER_ITEM] (20%), so gloves and pads together cut it by up to 40%. How well an item works
 * depends on the level of the match against the kind of gear:
 *   - Silver is made for club cricket, Golden for state cricket, Diamond for international and franchise league cricket (the levels 1, 2, 3).
 *   - Gear at or above the level of the match works fully; one level below works at half strength; two levels below at a quarter ([coverage]).
 *   - A franchise league match counts as level 3, like an international one.
 * Prices follow what a player earns at each level (a club match fee is 25 dollars, a state one 120, an international 1,500 to 4,000, a contract tens of thousands):
 * Silver gloves 120 and pads 180, Golden 1,500 and 2,200, Diamond 18,000 and 26,000 ([price]). Buying a better kind replaces the old one (no refund); gear does not wear out.
 *
 * [SHOP_ENABLED] false closes the shop; [GEAR_PROTECTS] false keeps the shop but gear no longer changes the injury chance.
 */
object CareerShop {
    const val SHOP_ENABLED = true
    const val GEAR_PROTECTS = true
    const val CUT_PER_ITEM = 0.20

    private val TIER_NAMES = listOf("Silver", "Golden", "Diamond")
    private val GLOVES = longArrayOf(0L, 120L, 1_500L, 18_000L)
    private val PADS = longArrayOf(0L, 180L, 2_200L, 26_000L)

    fun tierName(tier: Int): String = if (tier in 1..3) TIER_NAMES[tier - 1] else "No"

    fun price(item: String, tier: Int): Long {
        val t = tier.coerceIn(0, 3)
        return if (item == "gloves") GLOVES[t] else PADS[t]
    }

    fun owned(career: CareerProfile, item: String): Int = if (item == "gloves") career.glovesTier else career.padsTier

    /** 1 = club, 2 = state, 3 = international (and franchise league matches). */
    fun levelOf(career: CareerProfile): Int = when {
        career.isInternational -> 3
        career.isState -> 2
        else -> 1
    }

    fun matchLevel(career: CareerProfile, league: Boolean): Int = if (league) 3 else levelOf(career)

    fun levelName(level: Int): String = when (level) {
        1 -> "club"
        2 -> "state"
        else -> "international and league"
    }

    /** How well a kind of gear works in a match of this level: 1.0 fully, 0.5 half, 0.25 a quarter, 0 none. */
    fun coverage(tier: Int, level: Int): Double = when {
        tier <= 0 -> 0.0
        tier >= level -> 1.0
        level - tier == 1 -> 0.5
        else -> 0.25
    }

    /** What the chance of an injury is multiplied by (1.0 = no change; never below 0.5). */
    fun injuryFactor(career: CareerProfile, level: Int): Double {
        if (!GEAR_PROTECTS) return 1.0
        val cut = CUT_PER_ITEM * coverage(career.glovesTier, level) + CUT_PER_ITEM * coverage(career.padsTier, level)
        return (1.0 - cut).coerceAtLeast(0.5)
    }

    /** Which item took the knock when gear saved him from an injury ("gloves" or "pads"), or null if he wears none that works here. */
    fun saverName(career: CareerProfile, level: Int, random: Random): String? {
        val g = coverage(career.glovesTier, level)
        val p = coverage(career.padsTier, level)
        return when {
            g <= 0.0 && p <= 0.0 -> null
            g <= 0.0 -> "pads"
            p <= 0.0 -> "gloves"
            random.nextDouble() < g / (g + p) -> "gloves"
            else -> "pads"
        }
    }

    /** What a kind of gear is for, in one sentence. */
    fun blurb(tier: Int): String = when (tier) {
        1 -> "Made for club cricket: full protection in club matches, half in state matches, a quarter in international and league matches."
        2 -> "Made for state cricket: full protection in club and state matches, half in international and league matches."
        else -> "Made for international and league cricket: full protection in every match."
    }

    /** Why this cannot be bought now, or null if it can. */
    fun canBuy(career: CareerProfile, item: String, tier: Int): String? {
        if (!SHOP_ENABLED) return "The shop is closed."
        if (career.retired) return "You have retired."
        if (tier !in 1..3) return "There is no such gear."
        val have = owned(career, item)
        if (have == tier) return "You already have these."
        if (have > tier) return "You already have better ${item}."
        val cost = price(item, tier)
        if (career.money < cost) return "You need ${CareerFinance.money(cost - career.money)} more."
        return null
    }

    /** The career after buying (unchanged if it cannot be bought). */
    fun buy(career: CareerProfile, item: String, tier: Int): CareerProfile {
        if (canBuy(career, item, tier) != null) return career
        val paid = career.copy(money = career.money - price(item, tier))
        return if (item == "gloves") paid.copy(glovesTier = tier) else paid.copy(padsTier = tier)
    }

    /** The shop's status: his money, what he wears and how well it works in his matches. */
    fun statusLines(career: CareerProfile): List<String> {
        val level = levelOf(career)
        val out = ArrayList<String>()
        out.add("You have ${CareerFinance.money(career.money)} to spend. Your matches are at ${levelName(level)} level.")
        for (item in listOf("gloves", "pads")) {
            val tier = owned(career, item)
            val label = item.replaceFirstChar { it.uppercase() }
            if (tier == 0) {
                out.add("$label: none.")
            } else {
                val works = when (coverage(tier, level)) {
                    1.0 -> "full protection at your level"
                    0.5 -> "half protection at your level"
                    else -> "a quarter of the protection at your level"
                }
                out.add("$label: ${tierName(tier)}, $works.")
            }
        }
        val cutPercent = Math.round((1.0 - injuryFactor(career, level)) * 100).toInt()
        out.add(if (cutPercent <= 0) "You have no protection from injuries yet." else "Together they cut your chance of an injury by $cutPercent% in your matches.")
        return out
    }
}

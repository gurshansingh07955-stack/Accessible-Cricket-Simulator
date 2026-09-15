package com.cricketsim.logic

import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Ported from helpers/weatherSystem.tsx (the Floot/React web app remains
 * the source of truth for gameplay design). Pure Kotlin, no Android
 * dependencies.
 *
 * Two responsibilities that are related but separable:
 *
 * 1. WEATHER GENERATION — a per-match weather snapshot rolled once at
 *    the toss (pages/play-match.tsx), seeded from the selected
 *    stadium's climate profile so a desert ground (Sharjah) and a damp
 *    Irish ground (Malahide) genuinely feel different rather than
 *    sharing one generic "weather" concept.
 *
 * 2. RAIN INTERRUPTIONS + DLS — a small, honest simplification: at most
 *    ONE rain interruption is allowed per match (see
 *    `alreadyUsedThisMatch` in shouldTriggerRainInterruption), rolled
 *    for once per completed over in a limited-overs match, with the
 *    odds derived from the stadium's baseRainProbability. A real match
 *    can occasionally be interrupted more than once; layering the
 *    resource-percentage accounting for several interruptions back to
 *    back adds a lot of combinatorial complexity for very little extra
 *    gameplay value, so this deliberately caps at one clean stoppage —
 *    enough to deliver an actual revised DLS target without the engine
 *    needing to reason about compounding interruptions.
 *
 *    The resource-percentage curve below (Z0/decay table) is a
 *    deliberately simplified, original approximation of the SHAPE of
 *    the real Duckworth-Lewis-Stern resource table — not a reproduction
 *    of the ICC's actual (proprietary, non-public) parameter set. It's
 *    built to behave sensibly (monotonic in overs and wickets, ~100%
 *    resource at a full fresh innings, asymptoting toward each
 *    wicket-tier's ceiling as overs increase) rather than to match the
 *    official numbers exactly, which is the right level of fidelity for
 *    a game simulation.
 *
 * All numeric constants below (jitter spreads, condition thresholds,
 * Z0 table, decay rate, G50 values) are exact matches to the web
 * source. Overs counts (oversBowledThisInnings, oversLimit, etc.) are
 * modeled as Int here, matching MatchState.oversLimit: number and
 * MatchScore.overs: number in helpers/matchState.tsx, which both track
 * whole completed overs (balls-within-an-over are a separate 0-5 field).
 */

// --- Weather generation ---

enum class WeatherCondition { CLEAR, PARTLY_CLOUDY, OVERCAST, HUMID, LIGHT_RAIN }

data class WeatherSnapshot(
    val condition: WeatherCondition,
    val temperatureC: Int,
    val humidityPercent: Int,
    val windKph: Int,
    val rainChancePercent: Int, // shown to the user pre-match, 0-100
    val isDayNight: Boolean // whether this match is under lights for at least the second innings
)

private fun jitter(base: Double, spread: Double): Double = base + (Random.nextDouble() * 2 - 1) * spread
private fun clampD(value: Double, min: Double, max: Double): Double = value.coerceIn(min, max)

object WeatherSystem {

    /**
     * Rolls a one-off weather snapshot from a stadium's climate profile.
     * Called once, at the toss (pages/play-match.tsx step 5) — the result
     * is shown to the user immediately and stored on the match so it
     * stays consistent for the rest of the game.
     */
    fun generateWeatherForStadium(stadium: Stadium): WeatherSnapshot {
        val climate = stadium.climate
        val temperatureC = jitter(climate.avgTemperatureC.toDouble(), 3.0).roundToInt()
        val humidityPercent = clampD(jitter(climate.humidityPercent.toDouble(), 8.0), 10.0, 100.0).roundToInt()
        val windKph = clampD(jitter(climate.windKph.toDouble(), 6.0), 0.0, 60.0).roundToInt()
        val rainChancePercent = clampD(climate.baseRainProbability * 100 + jitter(0.0, 8.0), 1.0, 92.0).roundToInt()

        val condition = when {
            rainChancePercent >= 65 -> WeatherCondition.LIGHT_RAIN
            rainChancePercent >= 42 -> WeatherCondition.OVERCAST
            humidityPercent >= 72 -> WeatherCondition.HUMID
            rainChancePercent >= 20 -> WeatherCondition.PARTLY_CLOUDY
            else -> WeatherCondition.CLEAR
        }

        return WeatherSnapshot(
            condition = condition,
            temperatureC = temperatureC,
            humidityPercent = humidityPercent,
            windKph = windKph,
            rainChancePercent = rainChancePercent,
            isDayNight = Random.nextDouble() < 0.55
        )
    }

    fun weatherConditionLabel(condition: WeatherCondition): String = when (condition) {
        WeatherCondition.CLEAR -> "Clear skies"
        WeatherCondition.PARTLY_CLOUDY -> "Partly cloudy"
        WeatherCondition.OVERCAST -> "Overcast"
        WeatherCondition.HUMID -> "Hot and humid"
        WeatherCondition.LIGHT_RAIN -> "Light rain around"
    }

    // --- Stadium + weather effects on ball outcomes ---

    /**
     * Computes the stadium- and weather-driven modifiers for the CURRENT
     * ball, layered on top of the existing pitchType-based probabilities
     * in the eventual MatchEngine.kt. isSecondInningsUnderLights should
     * be true only when this is the second innings AND the match is a
     * day/night fixture — dew only ever helps the side batting under
     * lights, chasing a target, exactly as it does in real cricket
     * (harder to grip the ball, easier to time shots).
     */
    fun getStadiumMatchEffects(stadium: Stadium, isSecondInningsUnderLights: Boolean): StadiumMatchEffects {
        val boundaryFactor = when (stadium.boundarySize) {
            BoundarySize.SMALL -> 1.12
            BoundarySize.LARGE -> 0.9
            BoundarySize.MEDIUM -> 1.0
        }
        // Thinner air at real altitude (Johannesburg, Harare, Bulawayo)
        // lets the ball carry further and truer to the keeper/slips — a
        // small but genuine uplift to wicket chances from cleaner edges
        // carrying, offset by boundaryFactor already rewarding the extra
        // carry on well-struck shots too.
        val wicketCarryFactor = if (stadium.altitudeM > 1200) 1.08 else 1.0
        val dewFactor = if (isSecondInningsUnderLights) 1 + stadium.dewFactor * 0.18 else 1.0
        return StadiumMatchEffects(boundaryFactor, wicketCarryFactor, dewFactor)
    }

    // --- Rain interruptions ---

    /**
     * Called once per completed over (from the eventual MatchState.kt's
     * over-completion logic). Deliberately conservative: no interruption
     * in Test cricket (out of scope for this simplified DLS model), none
     * before a full over has been bowled, none inside the last two overs
     * of an innings (nothing meaningful left to revise by then), and
     * never a second interruption in the same match.
     */
    fun shouldTriggerRainInterruption(
        stadium: Stadium,
        format: MatchFormat,
        oversBowledThisInnings: Int,
        oversLimit: Int,
        alreadyUsedThisMatch: Boolean
    ): Boolean {
        if (alreadyUsedThisMatch) return false
        if (format == MatchFormat.TEST) return false
        if (oversBowledThisInnings < 1) return false
        val oversRemaining = oversLimit - oversBowledThisInnings
        if (oversRemaining <= 2) return false

        val eligibleOvers = maxOf(1, oversLimit - 3)
        val perOverChance = stadium.climate.baseRainProbability / eligibleOvers
        return Random.nextDouble() < perOverChance
    }

    /** How many overs the interruption costs this innings. */
    fun pickOversLost(oversRemaining: Int, format: MatchFormat): Int {
        val minLost = if (format == MatchFormat.T20) 2 else 4
        val maxLost = if (format == MatchFormat.T20) minOf(8, oversRemaining - 1) else minOf(20, oversRemaining - 1)
        val safeMax = maxOf(minLost, maxLost)
        return (minLost + Random.nextDouble() * (safeMax - minLost)).roundToInt()
    }

    // --- Resource percentage curve (simplified D/L/S-style approximation) ---

    /**
     * Total batting resource (%) an innings actually got to use,
     * accounting for at most one interruption. With no interruption this
     * is just the resource available at the start of a fresh innings
     * with the given overs allocation (should read close to 100 for a
     * full, uninterrupted innings). With an interruption, it's the
     * resource already consumed before the stoppage PLUS whatever
     * resource remained once the overs were cut — exactly the standard
     * "used resource + remaining resource after the cut" D/L logic, just
     * with this file's own simplified curve.
     */
    fun computeInningsResourcePercent(initialOversAllocated: Int, interruption: RainInterruption?): Double {
        val fullResource = resourcePercent(initialOversAllocated, 0)
        if (interruption == null) return fullResource

        val oversRemainingAtInterruption = maxOf(
            0,
            interruption.originalOversAllocated - interruption.oversBowledAtInterruption
        )
        val resourceJustBeforeCut = resourcePercent(oversRemainingAtInterruption, interruption.wicketsLostAtInterruption)
        val resourceUsedBeforeCut = resourcePercent(interruption.originalOversAllocated, 0) - resourceJustBeforeCut
        val oversRemainingAfterCut = maxOf(0, oversRemainingAtInterruption - interruption.oversLost)
        val resourceAfterCut = resourcePercent(oversRemainingAfterCut, interruption.wicketsLostAtInterruption)
        return minOf(100.0, resourceUsedBeforeCut + resourceAfterCut)
    }

    /**
     * Standard Duckworth-Lewis-Stern par-score logic: if the team
     * batting second has LESS resource than the team that batted first,
     * their target scales down proportionally. If they somehow end up
     * with MORE resource (their innings wasn't cut, but the first
     * innings was), the target instead scales UP using a flat "average
     * total score for this format" constant (G50 in the real method's
     * terminology) rather than a plain ratio, which is what keeps the
     * method fair in both directions.
     */
    fun computeDlsTarget(team1Score: Int, r1: Double, r2: Double, format: MatchFormat): DlsResult {
        val g50 = when (format) {
            MatchFormat.T20 -> 160.0
            MatchFormat.ODI -> 245.0
            MatchFormat.TEST -> team1Score.toDouble()
        }
        val safeR1 = if (r1 <= 0) 1.0 else r1
        val parScore = if (r2 <= safeR1) team1Score * (r2 / safeR1) else team1Score + g50 * ((r2 - safeR1) / 100)
        val target = kotlin.math.floor(parScore).toInt() + 1
        val roundedParScore = kotlin.math.round(parScore * 10) / 10
        return DlsResult(target = target, parScore = roundedParScore)
    }

    // Z0[w] = the asymptotic (unlimited-overs) resource ceiling, in
    // percent, once `w` wickets have already been lost. Shaped after the
    // well-known public description of how real D/L resource tables fall
    // away sharply in the last few wickets, but with hand-picked,
    // original constants — see the file-level doc comment above.
    private val Z0_TABLE = doubleArrayOf(100.0, 93.4, 85.1, 74.9, 62.7, 49.3, 36.2, 24.5, 15.4, 8.5, 0.0)

    // A single shared decay rate (NOT varied by wickets lost) — resource
    // percent is Z0[w] scaled by the same (1 - e^-rate*overs) curve for
    // every wicket tier. Deliberately not varying this by wickets: an
    // earlier version tried a per-wicket decay rate meant to reflect
    // "fewer wickets in hand reaches its ceiling faster", but that let
    // the decay term's growth occasionally outweigh a lower Z0 ceiling,
    // making resource percent INCREASE for a fixed overs-remaining as
    // more wickets fell — nonsensical, and caught by the web app's
    // helpers/stadiumWeather.spec.tsx monotonicity test. A single shared
    // rate guarantees resourcePercent is monotonically non-increasing in
    // wickets lost for any fixed overs remaining, since Z0 alone is
    // monotonically decreasing and the decay factor no longer depends on
    // w at all.
    //
    // 0.07 (rather than the original 0.05) so a full, uninterrupted
    // innings reads closer to real-world D/L norms: ~97% for a full
    // 50-over ODI innings and ~75% for a full 20-over T20 innings, versus
    // the real method's ~100% and ~65-68% respectively. Close enough to
    // feel intuitive without pretending to reproduce the ICC's actual
    // (secret) parameter set — see the file-level doc comment above.
    private const val RESOURCE_DECAY_RATE = 0.07

    /** Resource percent (%) available with `oversRemaining` overs left and `wicketsLost` down. */
    fun resourcePercent(oversRemaining: Int, wicketsLost: Int): Double {
        if (wicketsLost >= 10 || oversRemaining <= 0) return 0.0
        val z0 = Z0_TABLE[wicketsLost]
        return z0 * (1 - exp(-RESOURCE_DECAY_RATE * oversRemaining))
    }
}

data class StadiumMatchEffects(
    val boundaryFactor: Double, // multiplier applied to four/six probability
    val wicketCarryFactor: Double, // multiplier applied to wicket probability from thin-air carry at altitude
    val dewFactor: Double // multiplier applied to boundary probability when dew is in play (2nd innings, under lights)
)

data class RainInterruption(
    // The overs limit that was in effect BEFORE this interruption —
    // needed to correctly reconstruct the resource curve later, since
    // oversLimit itself gets reduced as soon as the interruption is
    // applied.
    val originalOversAllocated: Int,
    val oversBowledAtInterruption: Int,
    val wicketsLostAtInterruption: Int,
    val oversLost: Int
)

/** Return type for WeatherSystem.computeDlsTarget, matching the web source's `{ target, parScore }`. */
data class DlsResult(val target: Int, val parScore: Double)

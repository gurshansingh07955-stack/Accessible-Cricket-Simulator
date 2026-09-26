package com.cricketsim.logic

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.reflect.KMutableProperty1

/**
 * Ported from helpers/bowlingSystem.tsx (the Floot/React web app remains
 * the source of truth for gameplay design). Pure Kotlin, no Android
 * dependencies.
 *
 * MODELING NOTE — the web source's `BowlingVariation = PaceVariation |
 * SpinVariation` union (two overlapping string-literal unions sharing
 * the "stock" value) and its `BowlingLength | MisExecutedLength` union
 * for `actualLength` don't have a direct Kotlin enum equivalent. Two
 * different translations were used, deliberately:
 *   - BowlingVariation is flattened into ONE enum with all 15 distinct
 *     values (STOCK is shared, so it appears once, not twice) — this
 *     matches how it's actually used: a single BowlingVariation value
 *     flows through the whole pipeline regardless of which style list
 *     it came from.
 *   - BowlingLength / MisExecutedLength stay as two SEPARATE enums,
 *     both implementing a shared `DeliveryLength` sealed interface —
 *     this preserves the real type distinction the web source cared
 *     about (an `intendedLength` is always a real, selectable
 *     BowlingLength; a MisExecutedLength can only ever come out of
 *     resolveActualLength as a RESULT, never be intended), which a
 *     single flattened enum would have silently erased.
 *
 * All numeric constants (speed ranges, quality-tier thresholds, penalty
 * caps, probability multipliers, mis-execution chances) are exact
 * matches to the web source, except the wide/no-ball multipliers in
 * applyLine and applyQualityTier below — see their own comments for
 * that rebalance.
 */

// --- Types ---

enum class BowlingLine { OUTSIDE_OFF, OFF_STUMP, MIDDLE_STUMP, LEG_STUMP, OUTSIDE_LEG }

enum class BowlingAngle { OVER_THE_WICKET, AROUND_THE_WICKET }

/** Shared marker for "a length a delivery can actually end up as" — see the file-level modeling note above. */
sealed interface DeliveryLength

/** A length that can be deliberately aimed for. */
enum class BowlingLength : DeliveryLength { BOUNCER, SHORT, BACK_OF_LENGTH, GOOD_LENGTH, FULL, HALF_VOLLEY, YORKER }

/** Results of a poorly-executed attempt at a length — never directly selectable, only ever produced by mis-execution. */
enum class MisExecutedLength : DeliveryLength { FULL_TOSS, LOW_FULL_TOSS, LONG_HOP }

enum class SwingType { NONE, IN_SWING, OUT_SWING }

/** Flattened union of the web source's PaceVariation and SpinVariation (STOCK is shared between both) — see the file-level modeling note above. */
enum class BowlingVariation {
    STOCK, OFF_CUTTER, LEG_CUTTER, SLOWER_BALL, KNUCKLEBALL, CROSS_SEAM, REVERSE_SWING,
    DOOSRA, TEESRA, GOOGLY, ARM_BALL, FLIPPER, CARROM_BALL, TOP_SPINNER, SLIDER
}

enum class BowlingQualityTier { PERFECT, IDEAL, GOOD, BAD, VERY_BAD }

/**
 * Full transparent breakdown of how a delivery's final quality score
 * was reached. Every component is independently inspectable — nothing
 * about the score is a black box. See BowlingSystem.computeBowlingQuality
 * for how they combine.
 */
data class QualityBreakdown(
    val lengthPrecision: Double, // 0-100, primary component
    val smoothnessPenalty: Double, // 0-30, subtracted
    val speedRiskPenalty: Double, // 0-40, subtracted
    val finalScore: Double, // 0-100, clamped
    val tier: BowlingQualityTier
)

/**
 * The fully-resolved outcome of a bowled delivery — line, variation,
 * speed, intended vs. actual length, swing, and overall execution
 * quality — ready to feed into the eventual MatchEngine.kt's
 * simulateBall() to shape outcome probabilities on top of the existing
 * rating/pitch/difficulty model. Produced two ways: resolveActualLength()
 * + computeBowlingQuality() once a user's pitcher-screen gesture
 * completes, or generateAiBowlingDecision() for an AI-controlled bowler.
 * Both paths run through the exact same downstream scoring/probability
 * functions — nothing is special-cased per source.
 */
data class ResolvedBowlingDecision(
    val angle: BowlingAngle,
    val line: BowlingLine,
    val variation: BowlingVariation,
    val bowlingStyle: BowlingStyle,
    val speedKmh: Int,
    val intendedLength: BowlingLength,
    val actualLength: DeliveryLength,
    val swingType: SwingType,
    val qualityScore: Double, // 0-100 (same as quality.finalScore)
    val qualityTier: BowlingQualityTier,
    val quality: QualityBreakdown,
    // True only for a badly missed yorker that balloons into a beamer —
    // an automatic no-ball under the laws of cricket, not a probability.
    val forcedNoBall: Boolean
)

data class AngleOption(val value: BowlingAngle, val label: String, val description: String)
data class LineOption(val value: BowlingLine, val label: String, val description: String)

data class LengthBand(
    val value: BowlingLength,
    val label: String,
    // Fraction range [start, end) of the drag zone's vertical axis
    // (0 = bowler's release point, 1 = batsman's end) this length
    // occupies.
    val rangeStart: Double,
    val rangeEnd: Double
)

data class SpeedRangeKmh(val min: Int, val max: Int)

data class VariationInfo(val value: BowlingVariation, val label: String, val speedRangeKmh: SpeedRangeKmh)

/** Result of BowlingSystem.resolveActualLength. */
data class ActualLengthResult(val actualLength: DeliveryLength, val forcedNoBall: Boolean)

/** Result of the deprecated BowlingSystem.computeFinalQuality. */
data class FinalQualityResult(val score: Double, val tier: BowlingQualityTier)

data class OutcomeProbs(
    var dot: Double,
    var one: Double,
    var two: Double,
    var three: Double,
    var four: Double,
    var six: Double,
    var wicket: Double,
    var wide: Double,
    var noBall: Double
)

object BowlingSystem {

    // Kept nested (not top-level) so it doesn't collide with
    // BattingSystem's own private WeightedItem — two top-level private
    // declarations sharing a name still clash at the JVM level even
    // though "private" here only means file-visible, since both would
    // otherwise generate a class with the identical fully-qualified
    // name in the same package.
    private data class WeightedItem<T>(val value: T, val weight: Double)

    // --- Angle ---
    //
    // Chosen BEFORE the line (both a real user's bowling-setup dialog and
    // generateAiBowlingDecision() resolve angle first) since which side of
    // the stumps the bowler is releasing from changes what a given line
    // even means in practice — an off-stump line from around the wicket
    // crosses the batter at a noticeably different angle than the same
    // line bowled over the wicket. This game doesn't model individual
    // batter handedness (see CricketData.kt — no battingHand field exists
    // on Player), so angle's effect below is intentionally handedness-
    // agnostic: it captures the GENERAL real-world tradeoff (a tighter,
    // more cramping angle that's harder to control) rather than a
    // specific same-hand/opposite-hand matchup this game has no data to
    // represent.

    val ANGLE_OPTIONS: List<AngleOption> = listOf(
        AngleOption(
            BowlingAngle.OVER_THE_WICKET, "Over the Wicket",
            "The standard, natural release angle — reliable and easiest to control."
        ),
        AngleOption(
            BowlingAngle.AROUND_THE_WICKET, "Around the Wicket",
            "A tighter, cramping angle across the batter — extra wicket-taking threat, but harder to keep on a tight line."
        )
    )

    fun angleLabel(angle: BowlingAngle): String =
        if (angle == BowlingAngle.OVER_THE_WICKET) "Over the Wicket" else "Around the Wicket"

    // --- Line ---

    val LINE_OPTIONS: List<LineOption> = listOf(
        LineOption(BowlingLine.OUTSIDE_OFF, "Outside Off Stump", "Tempts the drive, higher edge/wicket chance."),
        LineOption(BowlingLine.OFF_STUMP, "Off Stump", "The classic attacking line — bowled/LBW threat."),
        LineOption(BowlingLine.MIDDLE_STUMP, "Middle Stump", "Straight and safe, but easy to work away."),
        LineOption(BowlingLine.LEG_STUMP, "Leg Stump", "Easy to flick for runs, less wicket-taking."),
        LineOption(BowlingLine.OUTSIDE_LEG, "Outside Leg Stump", "High wide risk, especially in T20.")
    )

    // --- Length ---

    val LENGTH_BANDS: List<LengthBand> = listOf(
        LengthBand(BowlingLength.BOUNCER, "Bouncer", 0.0 / 7, 1.0 / 7),
        LengthBand(BowlingLength.SHORT, "Short", 1.0 / 7, 2.0 / 7),
        LengthBand(BowlingLength.BACK_OF_LENGTH, "Back of a Length", 2.0 / 7, 3.0 / 7),
        LengthBand(BowlingLength.GOOD_LENGTH, "Good Length", 3.0 / 7, 4.0 / 7),
        LengthBand(BowlingLength.FULL, "Full", 4.0 / 7, 5.0 / 7),
        LengthBand(BowlingLength.HALF_VOLLEY, "Half Volley", 5.0 / 7, 6.0 / 7),
        LengthBand(BowlingLength.YORKER, "Yorker", 6.0 / 7, 1.0)
    )

    private val LENGTH_ORDER: List<BowlingLength> = listOf(
        BowlingLength.BOUNCER, BowlingLength.SHORT, BowlingLength.BACK_OF_LENGTH,
        BowlingLength.GOOD_LENGTH, BowlingLength.FULL, BowlingLength.HALF_VOLLEY, BowlingLength.YORKER
    )

    fun classifyLength(verticalFraction: Double): BowlingLength {
        val clamped = verticalFraction.coerceIn(0.0, 1.0)
        val band = LENGTH_BANDS.find { clamped >= it.rangeStart && clamped < it.rangeEnd }
        return band?.value ?: BowlingLength.YORKER // clamped===1 falls through to yorker
    }

    fun lengthLabel(length: DeliveryLength): String = when (length) {
        is BowlingLength -> when (length) {
            BowlingLength.BOUNCER -> "Bouncer"
            BowlingLength.SHORT -> "Short Ball"
            BowlingLength.BACK_OF_LENGTH -> "Back of a Length"
            BowlingLength.GOOD_LENGTH -> "Good Length"
            BowlingLength.FULL -> "Full"
            BowlingLength.HALF_VOLLEY -> "Half Volley"
            BowlingLength.YORKER -> "Yorker"
        }
        is MisExecutedLength -> when (length) {
            MisExecutedLength.FULL_TOSS -> "Full Toss"
            MisExecutedLength.LOW_FULL_TOSS -> "Low Full Toss"
            MisExecutedLength.LONG_HOP -> "Long Hop"
        }
    }

    // --- Swing ---

    // Curve fraction: the peak horizontal deviation of the drag path from
    // a straight line between its start and end points, as a fraction of
    // the drag zone's width. Below this magnitude counts as a straight,
    // non-swinging delivery.
    private const val SWING_CURVE_THRESHOLD = 0.035

    fun classifySwing(curveFraction: Double): SwingType {
        if (abs(curveFraction) < SWING_CURVE_THRESHOLD) return SwingType.NONE
        return if (curveFraction > 0) SwingType.OUT_SWING else SwingType.IN_SWING
    }

    // --- Variation & Speed ---

    val PACE_STOCK_SPEED_RANGE = SpeedRangeKmh(110, 159)
    private val KNUCKLEBALL_SPEED_RANGE = SpeedRangeKmh(95, 125)
    private val CUTTER_SPEED_RANGE = SpeedRangeKmh(115, 145)
    private val SLOWER_BALL_SPEED_RANGE = SpeedRangeKmh(90, 120)
    val SPIN_SPEED_RANGE = SpeedRangeKmh(70, 105)

    val PACE_VARIATIONS: List<VariationInfo> = listOf(
        VariationInfo(BowlingVariation.STOCK, "Stock Delivery", PACE_STOCK_SPEED_RANGE),
        VariationInfo(BowlingVariation.OFF_CUTTER, "Off Cutter", CUTTER_SPEED_RANGE),
        VariationInfo(BowlingVariation.LEG_CUTTER, "Leg Cutter", CUTTER_SPEED_RANGE),
        VariationInfo(BowlingVariation.SLOWER_BALL, "Slower Ball", SLOWER_BALL_SPEED_RANGE),
        VariationInfo(BowlingVariation.KNUCKLEBALL, "Knuckleball", KNUCKLEBALL_SPEED_RANGE),
        VariationInfo(BowlingVariation.CROSS_SEAM, "Cross Seam", CUTTER_SPEED_RANGE),
        VariationInfo(BowlingVariation.REVERSE_SWING, "Reverse Swing", PACE_STOCK_SPEED_RANGE)
    )

    val SPIN_VARIATIONS: List<VariationInfo> = listOf(
        VariationInfo(BowlingVariation.STOCK, "Stock Delivery", SPIN_SPEED_RANGE),
        VariationInfo(BowlingVariation.DOOSRA, "Doosra", SPIN_SPEED_RANGE),
        VariationInfo(BowlingVariation.TEESRA, "Teesra", SPIN_SPEED_RANGE),
        VariationInfo(BowlingVariation.GOOGLY, "Googly", SPIN_SPEED_RANGE),
        VariationInfo(BowlingVariation.ARM_BALL, "Arm Ball", SPIN_SPEED_RANGE),
        VariationInfo(BowlingVariation.FLIPPER, "Flipper", SPIN_SPEED_RANGE),
        VariationInfo(BowlingVariation.CARROM_BALL, "Carrom Ball", SPIN_SPEED_RANGE),
        VariationInfo(BowlingVariation.TOP_SPINNER, "Top Spinner", SPIN_SPEED_RANGE),
        VariationInfo(BowlingVariation.SLIDER, "Slider", SPIN_SPEED_RANGE)
    )

    fun getVariationOptions(style: BowlingStyle): List<VariationInfo> =
        if (style == BowlingStyle.SPIN) SPIN_VARIATIONS else PACE_VARIATIONS

    fun getSpeedRangeForVariation(style: BowlingStyle, variation: BowlingVariation): SpeedRangeKmh {
        val options = getVariationOptions(style)
        val found = options.find { it.value == variation }
        return found?.speedRangeKmh ?: if (style == BowlingStyle.SPIN) SPIN_SPEED_RANGE else PACE_STOCK_SPEED_RANGE
    }

    // ============================================================
    // --- Quality scoring ---
    //
    // A delivery's quality is built from three independently-computed
    // components, combined by computeBowlingQuality(). Every component is
    // deterministic and traceable — nothing here is an unexplained black
    // box. This is the single source of truth for what separates a
    // "Perfect Ball" from a "Very Bad Ball"; the tier labels shown to the
    // player are always derived from this same computation, never set
    // independently.
    //
    // 1. LENGTH PRECISION (primary, 0 to 100 points)
    //    How close the drag's release point landed to the exact center of
    //    the length band the player was aiming for. This is the dominant
    //    factor by design: hitting your intended spot is what bowling
    //    accuracy fundamentally means, and it alone spans the entire
    //    0-100 range.
    //
    // 2. SMOOTHNESS PENALTY (secondary, 0 to -30 points)
    //    Measures whether the player overshot their target during the
    //    drag and had to pull back before releasing — the digital
    //    equivalent of a rushed, uncontrolled bowling action rather than
    //    a single committed motion. A drag that moves smoothly toward its
    //    release point with no overshoot incurs no penalty at all.
    //
    // 3. SPEED RISK PENALTY (secondary, 0 to -40 points)
    //    Only engages once the chosen speed passes 70% of the way through
    //    the variation's allowed range — bowling near your top pace is
    //    inherently harder to control. The penalty scales with both how
    //    far past that 70% mark the choice sits and the bowler's own
    //    Bowling Rating: a highly-rated bowler barely notices it even at
    //    max pace, while a weak bowler pays a steep price for gambling on
    //    speed. Below 70% of the range, this penalty is always exactly 0.
    //
    // FINAL SCORE = Length Precision − Smoothness Penalty − Speed Risk
    // Penalty, clamped to [0, 100].
    //
    // TIER THRESHOLDS (on the final 0-100 score):
    //   Perfect Ball    85-100
    //   Ideal Ball      70-84
    //   Good Ball       50-69
    //   Bad Ball        30-49
    //   Very Bad Ball   0-29
    // ============================================================

    /**
     * Length Precision (0-100): the primary quality component. How close
     * the drag's release point landed to the center of the length band it
     * fell in. Dead center of a band = 100 (a fully committed, clean
     * execution of that length); right at the boundary with the next
     * band, about to bleed into it = 0.
     */
    fun computeLengthPrecision(verticalFraction: Double): Double {
        val clamped = verticalFraction.coerceIn(0.0, 1.0)
        val length = classifyLength(clamped)
        val band = LENGTH_BANDS.find { it.value == length }!!
        val bandCenter = (band.rangeStart + band.rangeEnd) / 2
        val halfWidth = (band.rangeEnd - band.rangeStart) / 2
        val distance = abs(clamped - bandCenter)
        val precision = 100 - (distance / halfWidth) * 100
        return precision.coerceIn(0.0, 100.0)
    }

    /** Alias kept so existing call sites keep working; prefer computeLengthPrecision in new code. */
    fun computeGesturePrecision(verticalFraction: Double): Double = computeLengthPrecision(verticalFraction)

    /**
     * Smoothness Penalty (0 to -30): costs precision when the drag
     * overshot its eventual release point and had to be pulled back
     * before lifting off — an overcorrected, rushed gesture rather than
     * one smooth committed motion toward the target.
     */
    fun computeSmoothnessPenalty(maxVerticalFractionReached: Double, finalVerticalFraction: Double): Double {
        val overshoot = maxOf(0.0, maxVerticalFractionReached - finalVerticalFraction)
        return minOf(30.0, overshoot * 100 * 0.4)
    }

    /**
     * Speed Risk Penalty (0 to -40): bowling a delivery near the top of
     * the variation's speed range is riskier — it can throw off accuracy.
     * A higher-rated bowler is more forgiving of that risk, but this is
     * always a secondary modifier layered on top of the player's own
     * gesture, never a substitute for it.
     */
    fun computeSpeedRiskPenalty(speedKmh: Int, speedRange: SpeedRangeKmh, bowlingRating: Int): Double {
        val span = speedRange.max - speedRange.min
        if (span <= 0) return 0.0
        val speedFraction = ((speedKmh - speedRange.min).toDouble() / span).coerceIn(0.0, 1.0)
        if (speedFraction <= 0.7) return 0.0
        val riskFactor = (speedFraction - 0.7) / 0.3 // 0 at 70% of range, 1 at max
        val ratingMitigation = (100 - bowlingRating.coerceIn(0, 100)) / 100.0
        return riskFactor * ratingMitigation * 40 // up to 40-point penalty for an unrated bowler at max speed
    }

    fun qualityTierFromScore(score: Double): BowlingQualityTier = when {
        score >= 85 -> BowlingQualityTier.PERFECT
        score >= 70 -> BowlingQualityTier.IDEAL
        score >= 50 -> BowlingQualityTier.GOOD
        score >= 30 -> BowlingQualityTier.BAD
        else -> BowlingQualityTier.VERY_BAD
    }

    fun qualityTierLabel(tier: BowlingQualityTier): String = when (tier) {
        BowlingQualityTier.PERFECT -> "Perfect Ball"
        BowlingQualityTier.IDEAL -> "Ideal Ball"
        BowlingQualityTier.GOOD -> "Good Ball"
        BowlingQualityTier.BAD -> "Bad Ball"
        BowlingQualityTier.VERY_BAD -> "Very Bad Ball"
    }

    /**
     * The single entry point for scoring a user-bowled delivery. Combines
     * length precision (primary), the smoothness penalty (overshoot/
     * correction), and the speed risk penalty (secondary, rating-
     * mitigated) into one transparent breakdown — see the block comment
     * above this section for the full rubric.
     */
    fun computeBowlingQuality(
        verticalFraction: Double,
        maxVerticalFractionReached: Double,
        speedKmh: Int,
        speedRange: SpeedRangeKmh,
        bowlingRating: Int
    ): QualityBreakdown {
        val lengthPrecision = computeLengthPrecision(verticalFraction)
        val smoothnessPenalty = computeSmoothnessPenalty(maxVerticalFractionReached, verticalFraction)
        val speedRiskPenalty = computeSpeedRiskPenalty(speedKmh, speedRange, bowlingRating)
        val finalScore = (lengthPrecision - smoothnessPenalty - speedRiskPenalty).coerceIn(0.0, 100.0)
        return QualityBreakdown(lengthPrecision, smoothnessPenalty, speedRiskPenalty, finalScore, qualityTierFromScore(finalScore))
    }

    /**
     * @deprecated Use computeBowlingQuality() instead — it also accounts
     * for drag smoothness (overshoot/correction), not just speed risk.
     * Kept only so any external call site referencing the old two-factor
     * signature doesn't break.
     */
    @Deprecated(
        "Use computeBowlingQuality() instead — it also accounts for drag smoothness, not just speed risk.",
        ReplaceWith("computeBowlingQuality(...)")
    )
    fun computeFinalQuality(
        gesturePrecision: Double,
        speedKmh: Int,
        speedRange: SpeedRangeKmh,
        bowlingRating: Int
    ): FinalQualityResult {
        val penalty = computeSpeedRiskPenalty(speedKmh, speedRange, bowlingRating)
        val score = (gesturePrecision - penalty).coerceIn(0.0, 100.0)
        return FinalQualityResult(score, qualityTierFromScore(score))
    }

    // --- Mis-execution ---

    private fun adjacentLength(length: BowlingLength, towardFuller: Boolean): BowlingLength {
        val idx = LENGTH_ORDER.indexOf(length)
        val nextIdx = if (towardFuller) minOf(LENGTH_ORDER.size - 1, idx + 1) else maxOf(0, idx - 1)
        return LENGTH_ORDER[nextIdx]
    }

    /**
     * Given the intended length and how well it was executed, rolls for
     * whether it actually lands as intended, drifts to a neighboring
     * length, or (on a bad enough miss) balloons into a batsman-friendly
     * mis-hit like a full toss or long hop. A badly missed yorker can
     * also become a beamer, which is an automatic no-ball under the laws
     * of cricket rather than a probability roll.
     */
    fun resolveActualLength(intendedLength: BowlingLength, tier: BowlingQualityTier): ActualLengthResult {
        if (tier == BowlingQualityTier.PERFECT || tier == BowlingQualityTier.IDEAL) {
            return ActualLengthResult(intendedLength, false)
        }

        if (tier == BowlingQualityTier.GOOD) {
            // Small chance of drifting to a neighboring length; otherwise fine.
            if (Random.nextDouble() < 0.15) {
                val towardFuller = Random.nextDouble() < 0.5
                return ActualLengthResult(adjacentLength(intendedLength, towardFuller), false)
            }
            return ActualLengthResult(intendedLength, false)
        }

        val missChance = if (tier == BowlingQualityTier.BAD) 0.4 else 0.7
        if (Random.nextDouble() >= missChance) {
            // Didn't miss badly enough to change length, but drifts a touch.
            val towardFuller = Random.nextDouble() < 0.5
            return ActualLengthResult(adjacentLength(intendedLength, towardFuller), false)
        }

        // A genuine bad miss.
        return when (intendedLength) {
            BowlingLength.YORKER -> {
                if (tier == BowlingQualityTier.VERY_BAD && Random.nextDouble() < 0.25) {
                    ActualLengthResult(MisExecutedLength.FULL_TOSS, true) // beamer -> automatic no-ball
                } else {
                    ActualLengthResult(if (Random.nextDouble() < 0.5) MisExecutedLength.FULL_TOSS else MisExecutedLength.LOW_FULL_TOSS, false)
                }
            }
            BowlingLength.FULL ->
                ActualLengthResult(if (Random.nextDouble() < 0.6) MisExecutedLength.FULL_TOSS else MisExecutedLength.LOW_FULL_TOSS, false)
            BowlingLength.BOUNCER ->
                ActualLengthResult(MisExecutedLength.LONG_HOP, false)
            BowlingLength.SHORT ->
                ActualLengthResult(if (Random.nextDouble() < 0.6) MisExecutedLength.LONG_HOP else BowlingLength.SHORT, false)
            BowlingLength.BACK_OF_LENGTH ->
                // Sits between short and good length — a bad miss tends to
                // sit up (long hop) or slip full toward the good-length
                // zone rather than ballooning all the way to a full toss.
                ActualLengthResult(if (Random.nextDouble() < 0.5) MisExecutedLength.LONG_HOP else BowlingLength.BACK_OF_LENGTH, false)
            BowlingLength.HALF_VOLLEY ->
                // Sits between full and yorker — a bad miss usually sits
                // up as a batsman-friendly full toss rather than drifting
                // short.
                ActualLengthResult(if (Random.nextDouble() < 0.7) MisExecutedLength.FULL_TOSS else MisExecutedLength.LOW_FULL_TOSS, false)
            BowlingLength.GOOD_LENGTH ->
                // good_length badly missed drifts either way
                ActualLengthResult(if (Random.nextDouble() < 0.5) MisExecutedLength.FULL_TOSS else MisExecutedLength.LONG_HOP, false)
        }
    }

    // --- AI bowling decision ---

    /**
     * Picks one value from a weighted list. Weights don't need to sum to
     * any particular total — they're only compared relative to each
     * other. Used throughout generateAiBowlingDecision() so every
     * dimension of an AI-bowled delivery is a plain, inspectable weight
     * table rather than scattered ad-hoc probability checks.
     */
    private fun <T> weightedPick(items: List<WeightedItem<T>>): T {
        val total = items.sumOf { maxOf(0.0, it.weight) }
        if (total <= 0) return items[0].value
        var roll = Random.nextDouble() * total
        for (item in items) {
            roll -= maxOf(0.0, item.weight)
            if (roll <= 0) return item.value
        }
        return items.last().value
    }

    /**
     * Generates a fully-resolved bowling decision for an AI-controlled
     * bowler — used whenever the user is BATTING, so the delivery they
     * face carries the same line/length/variation/swing/quality-tier
     * machinery a user-bowled ball does. There is no separate "AI
     * difficulty" system layered on top: every stage below feeds the
     * exact same functions a real gesture does (resolveActualLength,
     * qualityTierFromScore, applyBowlingDecisionToProbabilities). The
     * only thing that differs for an AI ball is how its *inputs* are
     * produced — weighted random picks in place of a menu choice plus a
     * drag gesture.
     *
     * Dimension by dimension:
     * - ANGLE: resolved first, before line. Weighted toward the reliable
     *   over-the-wicket angle by default, with attacking situations
     *   reaching for around-the-wicket's higher wicket-taking ceiling
     *   more often.
     * - LINE: weighted toward the good, attacking lines (off stump /
     *   outside off / middle stump) over the riskier, batsman-friendly
     *   ones (leg stump / outside leg).
     * - VARIATION: 60% stock, the remaining 40% split evenly across
     *   whatever variations this bowler's style (pace or spin) offers,
     *   with a modest extra spread when attacking.
     * - SPEED: random within the chosen variation's range, but biased
     *   toward the top of that range for a higher-rated bowler.
     * - INTENDED LENGTH: weighted toward the "sensible" lengths (good
     *   length, full) over the extremes (yorker, bouncer) by default —
     *   but this is exactly where situationalBias has the most visible
     *   effect (see below).
     * - EXECUTION QUALITY: since there's no real drag gesture to
     *   measure, a "gesture quality" score is simulated directly from
     *   the bowler's Bowling Rating plus randomness, then run through
     *   the SAME qualityTierFromScore() a real gesture's score would
     *   use. Because this score isn't built from a real drag path,
     *   there's no smoothness or speed-risk component to report; the
     *   breakdown records that plainly rather than inventing fake
     *   sub-scores.
     * - ACTUAL LENGTH: resolved via the existing resolveActualLength(),
     *   unchanged.
     * - SWING: only rolled for a stock delivery, weighted 50% none / 25%
     *   in-swing / 25% out-swing.
     *
     * situationalBias (-1..1, default 0) reflects the match situation
     * from the BOWLING side's perspective, computed from live match
     * state by the caller — not looked up here, so this function stays
     * decoupled from MatchState entirely. Positive = press for wickets:
     * shifts length weight toward the wicket-taking good-length/back-of-
     * length/short/bouncer zone. Negative = contain (classic death-overs
     * run-saving): shifts weight heavily toward yorkers and full
     * lengths. It also widens the AI's execution-quality randomness when
     * attacking and tightens it when containing.
     */
    fun generateAiBowlingDecision(bowler: Player, situationalBias: Double = 0.0): ResolvedBowlingDecision {
        val bias = situationalBias.coerceIn(-1.0, 1.0)
        val attackFactor = maxOf(0.0, bias)
        val containFactor = maxOf(0.0, -bias)

        // Around the wicket is the higher-variance choice — a tighter,
        // cramping angle with real wicket-taking upside but a harder
        // line to keep tidy (see applyAngle below) — so an AI chasing
        // wickets reaches for it more often than one just trying to keep
        // things quiet.
        val angle = weightedPick(
            listOf(
                WeightedItem(BowlingAngle.OVER_THE_WICKET, 65 * (1 + containFactor * 0.3)),
                WeightedItem(BowlingAngle.AROUND_THE_WICKET, 35 * (1 + attackFactor * 0.6))
            )
        )

        val line = weightedPick(
            listOf(
                WeightedItem(BowlingLine.OUTSIDE_OFF, 3 * (1 + attackFactor * 0.3)),
                WeightedItem(BowlingLine.OFF_STUMP, 3 * (1 + attackFactor * 0.4)),
                WeightedItem(BowlingLine.MIDDLE_STUMP, 2.0),
                WeightedItem(BowlingLine.LEG_STUMP, 1.0),
                WeightedItem(BowlingLine.OUTSIDE_LEG, 0.5)
            )
        )

        val variationOptions = getVariationOptions(bowler.bowlingStyle)
        val nonStockOptions = variationOptions.filter { it.value != BowlingVariation.STOCK }
        val nonStockPool = if (nonStockOptions.isNotEmpty()) 40 * (1 + attackFactor * 0.3) else 0.0
        val nonStockWeight = if (nonStockOptions.isNotEmpty()) nonStockPool / nonStockOptions.size else 0.0
        val variation = weightedPick(
            listOf(WeightedItem(BowlingVariation.STOCK, 60.0)) +
                nonStockOptions.map { WeightedItem(it.value, nonStockWeight) }
        )

        val speedRange = getSpeedRangeForVariation(bowler.bowlingStyle, variation)
        val speedFraction = (0.5 + (bowler.bowlingRating / 100.0) * 0.3 + (Random.nextDouble() * 0.3 - 0.15))
            .coerceIn(0.0, 1.0)
        val speedKmh = (speedRange.min + (speedRange.max - speedRange.min) * speedFraction).roundToInt()

        // The clearest expression of situational strategy: attacking
        // shifts weight toward the probing/wicket-taking lengths (good
        // length, back of length, and a bit more short/bouncer
        // aggression); containing shifts toward yorkers and full
        // lengths, the classic death-overs plan since they're hardest to
        // free the arms against. Yorker weighting is deliberately capped
        // at a moderate multiplier — a real bowling side mixes containing
        // lengths rather than bowling yorker after yorker, and every
        // yorker attempt carries a real mis-execution risk.
        val intendedLength = weightedPick(
            listOf(
                WeightedItem(BowlingLength.BOUNCER, 0.5 * (1 + attackFactor * 1.4) * (1 - containFactor * 0.5)),
                WeightedItem(BowlingLength.SHORT, 1 * (1 + attackFactor * 1.1) * (1 - containFactor * 0.4)),
                WeightedItem(BowlingLength.BACK_OF_LENGTH, 1.5 * (1 + attackFactor * 0.5)),
                WeightedItem(BowlingLength.GOOD_LENGTH, 3 * (1 + attackFactor * 0.25) * (1 - containFactor * 0.15)),
                WeightedItem(BowlingLength.FULL, 3 * (1 + containFactor * 0.8)),
                WeightedItem(BowlingLength.HALF_VOLLEY, 1.5),
                WeightedItem(BowlingLength.YORKER, 0.5 * (1 + containFactor * 1.8))
            )
        )

        // Simulated "gesture quality" — see the doc comment above for
        // why this substitutes for a real drag gesture.
        //
        // Baseline sits at 25 + rating*0.45 with a wide +/- spread so
        // EVERY rating has a real, non-trivial chance of landing in
        // every tier — rating still shifts the whole distribution
        // meaningfully (a 95-rated bowler's most likely outcome is still
        // Perfect/Ideal, a 50-rated bowler's is Bad/Very Bad), it just
        // no longer approaches certainty at either end. Randomness
        // widens when attacking (higher-variance, all-out effort) and
        // tightens when containing (a controlled, disciplined spell).
        val baselineScore = 25 + bowler.bowlingRating * 0.45
        val randomSpread = 48 + attackFactor * 12 - containFactor * 10
        val simulatedScore = (baselineScore + (Random.nextDouble() * randomSpread * 2 - randomSpread))
            .coerceIn(0.0, 100.0)
        val tier = qualityTierFromScore(simulatedScore)
        val quality = QualityBreakdown(
            // No real drag path exists for an AI ball, so there's
            // nothing to attribute to smoothness or speed risk
            // specifically — the whole simulated score is reported as
            // length precision rather than faking a breakdown that was
            // never actually computed.
            lengthPrecision = simulatedScore,
            smoothnessPenalty = 0.0,
            speedRiskPenalty = 0.0,
            finalScore = simulatedScore,
            tier = tier
        )

        val (actualLength, forcedNoBall) = resolveActualLength(intendedLength, tier)

        val swingType = if (variation == BowlingVariation.STOCK) {
            weightedPick(
                listOf(
                    WeightedItem(SwingType.NONE, 50.0),
                    WeightedItem(SwingType.IN_SWING, 25.0),
                    WeightedItem(SwingType.OUT_SWING, 25.0)
                )
            )
        } else {
            SwingType.NONE
        }

        return ResolvedBowlingDecision(
            angle = angle,
            line = line,
            variation = variation,
            bowlingStyle = bowler.bowlingStyle,
            speedKmh = speedKmh,
            intendedLength = intendedLength,
            actualLength = actualLength,
            swingType = swingType,
            qualityScore = simulatedScore,
            qualityTier = tier,
            quality = quality,
            forcedNoBall = forcedNoBall
        )
    }

    // --- Probability shaping ---

    private fun multiplyKey(probs: OutcomeProbs, key: KMutableProperty1<OutcomeProbs, Double>, factor: Double) {
        key.set(probs, maxOf(0.001, key.get(probs) * factor))
    }

    /**
     * Around the wicket is the higher-variance angle: a tighter, more
     * cramping line across the batter carries genuine extra wicket-
     * taking threat (more lbw/bowled chances from being squeezed for
     * room), but it's a harder release point to keep precisely on line
     * from — control suffers a little, showing up as extra wide risk.
     * Over the wicket is the reliable baseline and gets no modifier at
     * all. This is deliberately handedness-agnostic (see the "--- Angle
     * ---" section above) rather than trying to model a specific
     * same-hand/opposite-hand matchup this game has no batter-handedness
     * data to represent.
     */
    private fun applyAngle(probs: OutcomeProbs, angle: BowlingAngle) {
        if (angle == BowlingAngle.OVER_THE_WICKET) return
        multiplyKey(probs, OutcomeProbs::wicket, 1.12)
        multiplyKey(probs, OutcomeProbs::dot, 1.05)
        multiplyKey(probs, OutcomeProbs::wide, 1.2)
    }

    private fun applyLine(probs: OutcomeProbs, line: BowlingLine) {
        when (line) {
            BowlingLine.OUTSIDE_OFF -> {
                multiplyKey(probs, OutcomeProbs::dot, 1.15)
                multiplyKey(probs, OutcomeProbs::wicket, 1.15)
                multiplyKey(probs, OutcomeProbs::four, 0.85)
                multiplyKey(probs, OutcomeProbs::six, 0.85)
            }
            BowlingLine.OFF_STUMP -> {
                multiplyKey(probs, OutcomeProbs::wicket, 1.2)
                multiplyKey(probs, OutcomeProbs::dot, 1.05)
                multiplyKey(probs, OutcomeProbs::four, 0.9)
            }
            BowlingLine.MIDDLE_STUMP -> {
                multiplyKey(probs, OutcomeProbs::wicket, 1.05)
                multiplyKey(probs, OutcomeProbs::one, 1.1)
            }
            BowlingLine.LEG_STUMP -> {
                multiplyKey(probs, OutcomeProbs::four, 1.15)
                multiplyKey(probs, OutcomeProbs::six, 1.05)
                multiplyKey(probs, OutcomeProbs::wicket, 0.85)
                // Softened from 1.3 -- see applyQualityTier's own
                // comment on the wide/no-ball rebalance.
                multiplyKey(probs, OutcomeProbs::wide, 1.2)
            }
            BowlingLine.OUTSIDE_LEG -> {
                // Softened from 3.0 -- still clearly the highest-wide-risk
                // line by a wide margin (pun intended), just not so
                // punishing that aiming here felt like an automatic
                // extra. See applyQualityTier's own comment for the
                // matching base-rate reduction this pairs with.
                multiplyKey(probs, OutcomeProbs::wide, 2.0)
                multiplyKey(probs, OutcomeProbs::four, 1.2)
                multiplyKey(probs, OutcomeProbs::wicket, 0.6)
            }
        }
    }

    private fun applyLength(probs: OutcomeProbs, length: DeliveryLength) {
        when (length) {
            is BowlingLength -> when (length) {
                BowlingLength.YORKER -> {
                    multiplyKey(probs, OutcomeProbs::wicket, 1.3)
                    multiplyKey(probs, OutcomeProbs::dot, 1.2)
                    multiplyKey(probs, OutcomeProbs::four, 0.7)
                    multiplyKey(probs, OutcomeProbs::six, 0.6)
                }
                BowlingLength.HALF_VOLLEY -> {
                    // Full enough to drive but not as unplayable as a
                    // yorker — the classic "in the slot" ball that's
                    // easier to find the boundary off if not perfectly
                    // executed.
                    multiplyKey(probs, OutcomeProbs::four, 1.3)
                    multiplyKey(probs, OutcomeProbs::wicket, 1.05)
                    multiplyKey(probs, OutcomeProbs::dot, 0.85)
                }
                BowlingLength.FULL -> {
                    multiplyKey(probs, OutcomeProbs::four, 1.15)
                    multiplyKey(probs, OutcomeProbs::wicket, 1.15)
                }
                BowlingLength.GOOD_LENGTH -> {
                    multiplyKey(probs, OutcomeProbs::wicket, 1.2)
                    multiplyKey(probs, OutcomeProbs::dot, 1.15)
                    multiplyKey(probs, OutcomeProbs::four, 0.85)
                    multiplyKey(probs, OutcomeProbs::six, 0.8)
                }
                BowlingLength.BACK_OF_LENGTH -> {
                    // Awkward, skiddy length between short and good
                    // length — hard to free the arms against, similar
                    // wicket-taking value to a good length ball with
                    // slightly more dot-ball pressure.
                    multiplyKey(probs, OutcomeProbs::wicket, 1.15)
                    multiplyKey(probs, OutcomeProbs::dot, 1.2)
                    multiplyKey(probs, OutcomeProbs::four, 0.8)
                    multiplyKey(probs, OutcomeProbs::six, 0.85)
                }
                BowlingLength.SHORT -> {
                    multiplyKey(probs, OutcomeProbs::six, 1.3)
                    multiplyKey(probs, OutcomeProbs::wicket, 1.15)
                    multiplyKey(probs, OutcomeProbs::dot, 0.9)
                }
                BowlingLength.BOUNCER -> {
                    multiplyKey(probs, OutcomeProbs::six, 1.4)
                    multiplyKey(probs, OutcomeProbs::wicket, 1.2)
                    multiplyKey(probs, OutcomeProbs::dot, 0.8)
                    multiplyKey(probs, OutcomeProbs::four, 0.9)
                }
            }
            is MisExecutedLength -> when (length) {
                MisExecutedLength.FULL_TOSS -> {
                    multiplyKey(probs, OutcomeProbs::four, 1.8)
                    multiplyKey(probs, OutcomeProbs::six, 1.6)
                    multiplyKey(probs, OutcomeProbs::wicket, 0.4)
                }
                MisExecutedLength.LOW_FULL_TOSS -> {
                    multiplyKey(probs, OutcomeProbs::four, 1.5)
                    multiplyKey(probs, OutcomeProbs::six, 1.3)
                    multiplyKey(probs, OutcomeProbs::wicket, 0.5)
                }
                MisExecutedLength.LONG_HOP -> {
                    multiplyKey(probs, OutcomeProbs::six, 1.6)
                    multiplyKey(probs, OutcomeProbs::four, 1.4)
                    multiplyKey(probs, OutcomeProbs::wicket, 0.5)
                }
            }
        }
    }

    private fun applyVariation(probs: OutcomeProbs, variation: BowlingVariation) {
        when (variation) {
            BowlingVariation.KNUCKLEBALL, BowlingVariation.OFF_CUTTER, BowlingVariation.LEG_CUTTER -> {
                multiplyKey(probs, OutcomeProbs::wicket, 1.1)
                multiplyKey(probs, OutcomeProbs::dot, 1.05)
                multiplyKey(probs, OutcomeProbs::four, 0.9)
            }
            BowlingVariation.CROSS_SEAM -> {
                // Unpredictable bounce off the seam rather than swing or
                // pace off the pitch — good for surprise but riskier
                // control, so it also nudges up the extras risk
                // slightly.
                multiplyKey(probs, OutcomeProbs::wicket, 1.1)
                multiplyKey(probs, OutcomeProbs::dot, 1.05)
                multiplyKey(probs, OutcomeProbs::wide, 1.1)
            }
            BowlingVariation.REVERSE_SWING -> {
                // Late, sharp movement with an older ball — the most
                // dangerous pace variation for the batsman when it comes
                // off.
                multiplyKey(probs, OutcomeProbs::wicket, 1.25)
                multiplyKey(probs, OutcomeProbs::dot, 1.1)
                multiplyKey(probs, OutcomeProbs::four, 0.85)
            }
            BowlingVariation.SLOWER_BALL -> {
                multiplyKey(probs, OutcomeProbs::wicket, 1.1)
                multiplyKey(probs, OutcomeProbs::six, 0.8)
            }
            BowlingVariation.DOOSRA, BowlingVariation.TEESRA, BowlingVariation.GOOGLY,
            BowlingVariation.CARROM_BALL, BowlingVariation.FLIPPER -> {
                multiplyKey(probs, OutcomeProbs::wicket, 1.2)
                multiplyKey(probs, OutcomeProbs::dot, 1.1)
            }
            BowlingVariation.TOP_SPINNER -> {
                // Extra bounce and dip rather than sideways turn — a
                // strong catching/lbw threat with skiddy pace off the
                // pitch.
                multiplyKey(probs, OutcomeProbs::wicket, 1.2)
                multiplyKey(probs, OutcomeProbs::dot, 1.05)
            }
            BowlingVariation.SLIDER -> {
                // Skids through low and fast rather than turning —
                // dangerous for lbw/bowled but easier to put away if it
                // doesn't deceive.
                multiplyKey(probs, OutcomeProbs::wicket, 1.15)
                multiplyKey(probs, OutcomeProbs::four, 1.05)
            }
            BowlingVariation.ARM_BALL -> {
                multiplyKey(probs, OutcomeProbs::wicket, 1.15)
                multiplyKey(probs, OutcomeProbs::dot, 1.05)
            }
            BowlingVariation.STOCK -> {}
        }
    }

    private fun applySwing(probs: OutcomeProbs, swing: SwingType) {
        if (swing == SwingType.NONE) return
        multiplyKey(probs, OutcomeProbs::wicket, 1.1)
        multiplyKey(probs, OutcomeProbs::four, 0.95)
    }

    private fun applyQualityTier(probs: OutcomeProbs, tier: BowlingQualityTier) {
        when (tier) {
            BowlingQualityTier.PERFECT -> {
                // Boundary suppression softened slightly (was 0.7/0.6) —
                // a Perfect ball should still be hard to score off, but
                // combined multiplicatively with a batter's own Perfect
                // timing tier (BattingSystem.applyTimingTier once
                // ported), the old values left almost no room for
                // genuinely excellent batting to actually produce a
                // boundary against good bowling — it could only ever
                // claw back to roughly neutral. Wicket threat is
                // unchanged.
                multiplyKey(probs, OutcomeProbs::wicket, 1.3)
                multiplyKey(probs, OutcomeProbs::dot, 1.2)
                multiplyKey(probs, OutcomeProbs::four, 0.8)
                multiplyKey(probs, OutcomeProbs::six, 0.72)
                multiplyKey(probs, OutcomeProbs::wide, 0.3)
                multiplyKey(probs, OutcomeProbs::noBall, 0.2)
            }
            BowlingQualityTier.IDEAL -> {
                multiplyKey(probs, OutcomeProbs::wicket, 1.15)
                multiplyKey(probs, OutcomeProbs::dot, 1.1)
                multiplyKey(probs, OutcomeProbs::four, 0.9)
                multiplyKey(probs, OutcomeProbs::six, 0.87)
                multiplyKey(probs, OutcomeProbs::wide, 0.5)
                multiplyKey(probs, OutcomeProbs::noBall, 0.3)
            }
            BowlingQualityTier.GOOD -> {}
            BowlingQualityTier.BAD -> {
                // wide/noBall softened from 1.8/2.5 -- see the doc
                // comment below on VERY_BAD's matching reduction for
                // the full reasoning (this pairs with MatchEngine.kt's
                // getOutcomeProbabilities base rate also being lowered).
                multiplyKey(probs, OutcomeProbs::wicket, 0.8)
                multiplyKey(probs, OutcomeProbs::four, 1.2)
                multiplyKey(probs, OutcomeProbs::six, 1.15)
                multiplyKey(probs, OutcomeProbs::wide, 1.5)
                multiplyKey(probs, OutcomeProbs::noBall, 1.8)
            }
            BowlingQualityTier.VERY_BAD -> {
                // wide/noBall softened from 2.5/4.5 -- these two
                // multipliers, stacked on top of applyLine's own
                // OUTSIDE_LEG/LEG_STUMP wide bumps and this same
                // function's BAD tier above, could make wides and
                // no-balls feel like they showed up far too often,
                // for the AI as much as the user (this whole
                // probability pipeline is shared identically by both).
                // Reduced here, at the line's own multipliers, AND at
                // MatchEngine.kt's base rate together, rather than in
                // just one place, since compounding across all three
                // was what made the old rate feel excessive.
                multiplyKey(probs, OutcomeProbs::wicket, 0.6)
                multiplyKey(probs, OutcomeProbs::four, 1.4)
                multiplyKey(probs, OutcomeProbs::six, 1.3)
                multiplyKey(probs, OutcomeProbs::wide, 1.8)
                multiplyKey(probs, OutcomeProbs::noBall, 2.5)
            }
        }
    }

    /**
     * Applies every dimension of a resolved bowling decision (line,
     * actual length, variation, swing, and overall execution quality) to
     * a base outcome-probability object. Works identically whether the
     * decision came from a real user gesture or
     * generateAiBowlingDecision() — the probability layer has no concept
     * of who bowled the ball, only what was bowled. Mutates and returns
     * the same object; the caller is expected to re-normalize afterward
     * (same as the existing rating/pitch/difficulty adjustments already
     * do).
     */
    fun applyBowlingDecisionToProbabilities(probs: OutcomeProbs, decision: ResolvedBowlingDecision): OutcomeProbs {
        applyAngle(probs, decision.angle)
        applyLine(probs, decision.line)
        applyLength(probs, decision.actualLength)
        applyVariation(probs, decision.variation)
        applySwing(probs, decision.swingType)
        applyQualityTier(probs, decision.qualityTier)
        return probs
    }
}

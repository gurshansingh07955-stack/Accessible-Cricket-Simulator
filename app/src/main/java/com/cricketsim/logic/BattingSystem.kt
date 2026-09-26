package com.cricketsim.logic

import kotlin.math.abs
import kotlin.random.Random
import kotlin.reflect.KMutableProperty1

/**
 * Ported from helpers/battingSystem.tsx (the Floot/React web app
 * remains the source of truth for gameplay design). Pure Kotlin, no
 * Android dependencies.
 *
 * MODELING NOTE — the web source's `CompatibleLength = BowlingLength |
 * MisExecutedLength` union is EXACTLY the same union BowlingSystem.kt
 * already models as the `DeliveryLength` sealed interface, so this file
 * reuses that type directly (via the `CompatibleLength` typealias
 * below) rather than redefining an equivalent union.
 *
 * All numeric constants (compatibility-tier multipliers, timing
 * thresholds, aggression-chance base rates per bowling-quality tier,
 * timing-score formula coefficients) are exact matches to the web
 * source's ORIGINAL values, except the timing thresholds themselves —
 * see computeTimingTier's doc comment for that rebalance's own history.
 */

// --- Types ---

enum class FootworkType { FRONT_FOOT, BACK_FOOT }

enum class NamedShot {
    FORWARD_DEFENSE, BACKWARD_DEFENSE, STRAIGHT_DRIVE, ON_DRIVE, COVER_DRIVE, SQUARE_DRIVE,
    CUT_SHOT, LATE_CUT, PULL_SHOT, HOOK_SHOT, LEG_GLANCE_FLICK, SWEEP_SHOT, REVERSE_SWEEP,
    UPPER_CUT, SCOOP_SHOT
}

enum class IntentDirection { UP, DOWN, LEFT, RIGHT }

/** Reuses the bowling engine's 5-tier naming for consistency, per the design spec — a "Perfect" swing means the same thing whether it's a bowler's release or a batsman's timing. */
typealias TimingTier = BowlingQualityTier

enum class ShotCompatibilityTier { EXCELLENT, GOOD, BAD }
enum class FootworkMatchTier { MATCHED, NEUTRAL, MISMATCHED }

/** Same union BowlingSystem.kt already models as DeliveryLength — see the file-level modeling note above. */
typealias CompatibleLength = DeliveryLength

/**
 * The fully-resolved batting decision for one ball — footwork, named
 * shot, intent direction (null for the two defensive shots, which skip
 * the intent screen entirely), and timing quality. Produced by the
 * eventual BattingShotScreen once all three (or two, for defense)
 * phases complete, and consumed by BattingSystem.applyBattingDecisionToProbabilities
 * alongside the bowling decision's actual length and bowling style.
 */
data class BattingDecision(
    val footwork: FootworkType,
    val shot: NamedShot,
    val intent: IntentDirection?,
    val timingTier: TimingTier,
    // |actualTapTime - expectedFifthPulseTime| / interval — kept around
    // for on-screen/debug transparency, same spirit as bowling's
    // QualityBreakdown.
    val timingDeltaFraction: Double
)

data class ShotInfo(val value: NamedShot, val label: String, val isDefensive: Boolean)

data class CompatibilityRule(val excellent: List<CompatibleLength>, val good: List<CompatibleLength>)

data class TimingResult(val tier: TimingTier, val deltaFraction: Double)

// ============================================================
// --- Shot direction (for fielding integration) ---
//
// Maps a resolved batting decision to roughly where the ball is likely
// to go, in terms of the same named fielding sectors the captain places
// fielders in (FieldingSystem.kt) — this is what makes field placement
// a genuine tactical layer instead of only gating no-ball legality.
// Deliberately a single "primary" sector per shot rather than a full
// probability spread across the whole ground: a fielder standing in
// EXACTLY the right spot for a given shot is already a fairly specific
// coincidence, so one sharp target per shot is enough to make placement
// meaningfully matter without needing a full directional model. See the
// eventual MatchEngine.kt's applyFieldPlacementToProbabilities() for how
// this actually affects outcome probabilities.
// ============================================================
data class ShotDirection(
    val sector: FieldingSector,
    // Whether the ball is travelling in the air on this particular shot
    // — matters because only a DEEP fielder standing in `sector` can
    // catch an aerial shot, while only a SHORT/close fielder can cut off
    // a grounded one before it reaches the rope.
    val isAerial: Boolean
)

object BattingSystem {

    // Kept nested (not top-level) so it doesn't collide with
    // BowlingSystem's own private WeightedItem — two top-level private
    // declarations sharing a name still clash at the JVM level even
    // though "private" here only means file-visible, since both would
    // otherwise generate a class with the identical fully-qualified
    // name in the same package.
    private data class WeightedItem<T>(val value: T, val weight: Double)

    // --- Named shots ---

    // Order matches the design spec exactly — this is also the vertical
    // drag-zone order shown on the shot-selection screen.
    val SHOT_OPTIONS: List<ShotInfo> = listOf(
        ShotInfo(NamedShot.FORWARD_DEFENSE, "Forward Defense", true),
        ShotInfo(NamedShot.BACKWARD_DEFENSE, "Backward Defense", true),
        ShotInfo(NamedShot.STRAIGHT_DRIVE, "Straight Drive", false),
        ShotInfo(NamedShot.ON_DRIVE, "On Drive", false),
        ShotInfo(NamedShot.COVER_DRIVE, "Cover Drive", false),
        ShotInfo(NamedShot.SQUARE_DRIVE, "Square Drive", false),
        ShotInfo(NamedShot.CUT_SHOT, "Cut Shot", false),
        ShotInfo(NamedShot.LATE_CUT, "Late Cut", false),
        ShotInfo(NamedShot.PULL_SHOT, "Pull Shot", false),
        ShotInfo(NamedShot.HOOK_SHOT, "Hook Shot", false),
        ShotInfo(NamedShot.LEG_GLANCE_FLICK, "Leg Glance / Flick", false),
        ShotInfo(NamedShot.SWEEP_SHOT, "Sweep Shot", false),
        ShotInfo(NamedShot.REVERSE_SWEEP, "Reverse Sweep", false),
        ShotInfo(NamedShot.UPPER_CUT, "Upper Cut", false),
        ShotInfo(NamedShot.SCOOP_SHOT, "Scoop Shot", false)
    )

    fun isDefensiveShot(shot: NamedShot): Boolean =
        shot == NamedShot.FORWARD_DEFENSE || shot == NamedShot.BACKWARD_DEFENSE

    fun shotLabel(shot: NamedShot): String = SHOT_OPTIONS.find { it.value == shot }?.label ?: shot.name

    /**
     * Classifies a vertical drag fraction [0,1) into one of the 15
     * shots, in the SHOT_OPTIONS order — an equal-height band per shot,
     * same mechanic as BowlingSystem.classifyLength().
     */
    fun classifyShot(verticalFraction: Double): NamedShot {
        val clamped = verticalFraction.coerceIn(0.0, 0.999999)
        val index = (clamped * SHOT_OPTIONS.size).toInt()
        return SHOT_OPTIONS[minOf(SHOT_OPTIONS.size - 1, index)].value
    }

    fun intentLabel(intent: IntentDirection): String = when (intent) {
        IntentDirection.UP -> "Aggressive (Aerial)"
        IntentDirection.DOWN -> "Defensive"
        IntentDirection.LEFT -> "Aggressive (Grounded)"
        IntentDirection.RIGHT -> "Step Out / Advance"
    }

    /**
     * Classifies a drag vector's dominant axis into one of the 4 intent
     * directions. Whichever axis has the larger absolute displacement
     * wins; the sign of that axis picks up/down or left/right.
     */
    fun classifyIntentDirection(deltaX: Double, deltaY: Double): IntentDirection {
        if (abs(deltaX) >= abs(deltaY)) {
            return if (deltaX >= 0) IntentDirection.RIGHT else IntentDirection.LEFT
        }
        return if (deltaY < 0) IntentDirection.UP else IntentDirection.DOWN
    }

    // ============================================================
    // --- Shot-Choice Compatibility ---
    //
    // The single most important new factor in the batting model. Every
    // named shot has a set of lengths it's built for (Excellent), a set
    // it's still viable against (Good), and everything else falls to
    // Bad — except the two defensive shots, which are never "Bad" (a
    // suboptimal defensive shot is just a wasted opportunity, not a
    // disaster). Sweep shots additionally depend on bowling style:
    // they're a spin-friendly shot family, and Bad regardless of length
    // against genuine pace (sweeping a fast bowler is a genuinely
    // unorthodox, high-risk shot no matter how full or short the ball
    // is).
    //
    // Tier effects (see applyShotCompatibility below for the exact
    // multipliers):
    //   EXCELLENT  Clearly rewards a correct read: wicket chance drops
    //              sharply, boundary chance rises noticeably.
    //   GOOD       Deliberately kept close to neutral. A sound-but-
    //              ambitious shot (e.g. driving a good-length ball)
    //              should show real variance — sometimes a boundary,
    //              sometimes a single — not be pre-punished just for
    //              being played.
    //   BAD        SEVERE. Playing a shot that doesn't suit the ball
    //              (the canonical example: a Pull Shot to a full ball)
    //              is close to automatic disaster — heavy multipliers
    //              collapse the distribution toward dot-ball-or-wicket.
    // ============================================================

    private val FULL_ISH: List<CompatibleLength> =
        listOf(BowlingLength.FULL, BowlingLength.YORKER, MisExecutedLength.FULL_TOSS, MisExecutedLength.LOW_FULL_TOSS, BowlingLength.HALF_VOLLEY)
    private val SHORT_ISH: List<CompatibleLength> =
        listOf(BowlingLength.SHORT, BowlingLength.BOUNCER, MisExecutedLength.LONG_HOP, BowlingLength.BACK_OF_LENGTH)
    private val ALL_LENGTHS: List<CompatibleLength> = listOf(
        BowlingLength.BOUNCER, BowlingLength.SHORT, BowlingLength.BACK_OF_LENGTH, BowlingLength.GOOD_LENGTH,
        BowlingLength.FULL, BowlingLength.HALF_VOLLEY, BowlingLength.YORKER,
        MisExecutedLength.FULL_TOSS, MisExecutedLength.LOW_FULL_TOSS, MisExecutedLength.LONG_HOP
    )

    /**
     * Unused in the web source itself (defined there but never actually
     * called at time of porting — every shot's "everything else" bucket
     * is handled inline in computeShotCompatibility's fallback branch
     * instead). Ported anyway for parity; flag for removal if a future
     * pass confirms it's genuinely dead code on the web side too.
     */
    private fun remainder(excellent: List<CompatibleLength>, good: List<CompatibleLength>): List<CompatibleLength> =
        ALL_LENGTHS.filter { it !in excellent && it !in good }

    // Rules for every shot EXCEPT sweep/reverse sweep, which branch on
    // bowling style below. (The web source assigns "good" inline for
    // the four drive shots and then redundantly re-assigns the exact
    // same list a few lines later under a stale comment about "shots
    // whose spec only defined an explicit Bad list" — that reassignment
    // is a no-op there, so there's nothing extra to model here.)
    private val SHOT_COMPATIBILITY: Map<NamedShot, CompatibilityRule> = mapOf(
        NamedShot.FORWARD_DEFENSE to CompatibilityRule(
            excellent = listOf(BowlingLength.FULL, BowlingLength.GOOD_LENGTH, MisExecutedLength.FULL_TOSS, MisExecutedLength.LOW_FULL_TOSS, BowlingLength.HALF_VOLLEY),
            good = listOf(BowlingLength.SHORT, BowlingLength.BOUNCER, BowlingLength.BACK_OF_LENGTH, MisExecutedLength.LONG_HOP, BowlingLength.YORKER)
        ),
        NamedShot.BACKWARD_DEFENSE to CompatibilityRule(
            excellent = listOf(BowlingLength.GOOD_LENGTH, BowlingLength.SHORT, BowlingLength.BOUNCER, MisExecutedLength.LONG_HOP, BowlingLength.BACK_OF_LENGTH),
            good = listOf(BowlingLength.FULL, MisExecutedLength.FULL_TOSS, MisExecutedLength.LOW_FULL_TOSS, BowlingLength.HALF_VOLLEY, BowlingLength.YORKER)
        ),
        NamedShot.STRAIGHT_DRIVE to CompatibilityRule(
            excellent = listOf(BowlingLength.FULL, MisExecutedLength.FULL_TOSS, MisExecutedLength.LOW_FULL_TOSS, BowlingLength.HALF_VOLLEY),
            good = listOf(BowlingLength.GOOD_LENGTH)
        ),
        NamedShot.ON_DRIVE to CompatibilityRule(
            excellent = listOf(BowlingLength.FULL, MisExecutedLength.FULL_TOSS, MisExecutedLength.LOW_FULL_TOSS, BowlingLength.HALF_VOLLEY),
            good = listOf(BowlingLength.GOOD_LENGTH)
        ),
        NamedShot.COVER_DRIVE to CompatibilityRule(
            excellent = listOf(BowlingLength.FULL, MisExecutedLength.FULL_TOSS, MisExecutedLength.LOW_FULL_TOSS, BowlingLength.HALF_VOLLEY),
            good = listOf(BowlingLength.GOOD_LENGTH)
        ),
        NamedShot.SQUARE_DRIVE to CompatibilityRule(
            excellent = listOf(BowlingLength.FULL, MisExecutedLength.FULL_TOSS, MisExecutedLength.LOW_FULL_TOSS, BowlingLength.HALF_VOLLEY),
            good = listOf(BowlingLength.GOOD_LENGTH)
        ),
        NamedShot.CUT_SHOT to CompatibilityRule(
            excellent = listOf(BowlingLength.SHORT, BowlingLength.GOOD_LENGTH),
            good = listOf(BowlingLength.BOUNCER, MisExecutedLength.LONG_HOP, BowlingLength.BACK_OF_LENGTH)
        ),
        NamedShot.LATE_CUT to CompatibilityRule(
            excellent = listOf(BowlingLength.GOOD_LENGTH, BowlingLength.SHORT),
            good = listOf(BowlingLength.BOUNCER, MisExecutedLength.LONG_HOP, BowlingLength.BACK_OF_LENGTH)
        ),
        NamedShot.PULL_SHOT to CompatibilityRule(
            // The design's own explicit worked example: a Pull Shot to a
            // full ball must land in the severe "Bad" tier below.
            excellent = listOf(BowlingLength.SHORT, BowlingLength.BOUNCER, MisExecutedLength.LONG_HOP),
            good = listOf(BowlingLength.GOOD_LENGTH, BowlingLength.BACK_OF_LENGTH)
        ),
        NamedShot.HOOK_SHOT to CompatibilityRule(
            excellent = listOf(BowlingLength.BOUNCER, BowlingLength.SHORT),
            good = listOf(MisExecutedLength.LONG_HOP, BowlingLength.GOOD_LENGTH, BowlingLength.BACK_OF_LENGTH)
        ),
        NamedShot.LEG_GLANCE_FLICK to CompatibilityRule(
            excellent = listOf(BowlingLength.FULL, BowlingLength.GOOD_LENGTH, MisExecutedLength.FULL_TOSS, MisExecutedLength.LOW_FULL_TOSS, BowlingLength.HALF_VOLLEY),
            good = listOf(BowlingLength.SHORT, MisExecutedLength.LONG_HOP, BowlingLength.BACK_OF_LENGTH)
        ),
        NamedShot.UPPER_CUT to CompatibilityRule(
            excellent = listOf(BowlingLength.BOUNCER),
            good = listOf(BowlingLength.SHORT, MisExecutedLength.LONG_HOP)
        ),
        NamedShot.SCOOP_SHOT to CompatibilityRule(
            excellent = listOf(BowlingLength.YORKER, MisExecutedLength.FULL_TOSS, MisExecutedLength.LOW_FULL_TOSS, BowlingLength.FULL, BowlingLength.HALF_VOLLEY),
            // No "Good" tier defined for the scoop — it's specifically a
            // full-ball shot, so anything outside "excellent" is "bad".
            good = emptyList()
        )
    )

    // Sweep / reverse sweep vs SPIN bowling — vs pace they're Bad
    // regardless of length (handled separately in
    // computeShotCompatibility).
    private val SWEEP_VS_SPIN = CompatibilityRule(
        excellent = listOf(BowlingLength.GOOD_LENGTH, BowlingLength.FULL),
        good = listOf(MisExecutedLength.FULL_TOSS, MisExecutedLength.LOW_FULL_TOSS, BowlingLength.SHORT, BowlingLength.BACK_OF_LENGTH, BowlingLength.HALF_VOLLEY)
    )

    fun computeShotCompatibility(shot: NamedShot, actualLength: CompatibleLength, bowlingStyle: BowlingStyle): ShotCompatibilityTier {
        if (shot == NamedShot.SWEEP_SHOT || shot == NamedShot.REVERSE_SWEEP) {
            if (bowlingStyle == BowlingStyle.PACE) {
                // Sweeping genuine pace is unorthodox and risky no matter
                // the length — Bad regardless.
                return ShotCompatibilityTier.BAD
            }
            return when {
                actualLength in SWEEP_VS_SPIN.excellent -> ShotCompatibilityTier.EXCELLENT
                actualLength in SWEEP_VS_SPIN.good -> ShotCompatibilityTier.GOOD
                else -> ShotCompatibilityTier.BAD
            }
        }

        val rule = SHOT_COMPATIBILITY.getValue(shot)
        return when {
            actualLength in rule.excellent -> ShotCompatibilityTier.EXCELLENT
            actualLength in rule.good -> ShotCompatibilityTier.GOOD
            isDefensiveShot(shot) -> ShotCompatibilityTier.GOOD // defense is never "Bad"
            else -> ShotCompatibilityTier.BAD
        }
    }

    // --- Footwork Match ---

    /**
     * Front foot suits full-ish lengths, back foot suits short-ish
     * lengths; good_length is neutral to both. Checked against the
     * delivery's ACTUAL length (after any bowling mis-execution), not
     * the intended one — a mis-executed full toss rewards front foot
     * exactly like a genuine full ball would.
     */
    fun computeFootworkMatch(footwork: FootworkType, actualLength: CompatibleLength): FootworkMatchTier {
        if (actualLength == BowlingLength.GOOD_LENGTH || actualLength == BowlingLength.BACK_OF_LENGTH) {
            // back_of_length sits ambiguously between the two footwork
            // zones — treated as neutral, same as good_length.
            return FootworkMatchTier.NEUTRAL
        }
        val isFullIsh = actualLength in FULL_ISH
        return if (footwork == FootworkType.FRONT_FOOT) {
            if (isFullIsh) FootworkMatchTier.MATCHED else FootworkMatchTier.MISMATCHED
        } else {
            if (isFullIsh) FootworkMatchTier.MISMATCHED else FootworkMatchTier.MATCHED
        }
    }

    // --- Timing minigame ---

    // Interpolated across the full practical bowling speed range
    // (spin's slowest to pace's fastest), independent of which
    // variation is currently bowled — a fast bowler's slower ball is
    // still timed against its own actual speed, not its style's usual
    // range.
    private val OVERALL_MIN_SPEED = BowlingSystem.SPIN_SPEED_RANGE.min
    private val OVERALL_MAX_SPEED = BowlingSystem.PACE_STOCK_SPEED_RANGE.max
    private const val SLOWEST_INTERVAL_MS = 380.0

    // Raised from 145ms — at the old value, even a "perfect" tap
    // against top pace required landing inside a ~14ms window, smaller
    // than typical touchscreen input dispatch jitter on its own. Timing
    // against fast bowling should be genuinely harder than against
    // spin, not effectively unhittable regardless of skill.
    private const val FASTEST_INTERVAL_MS = 185.0

    /**
     * The gap between successive vibration/visual/audio pulses, in
     * milliseconds. Faster deliveries compress the rhythm — linear
     * between the slowest practical ball (longest interval, easiest to
     * time) and the fastest (shortest interval, hardest to time).
     */
    fun computeTimingIntervalMs(speedKmh: Int): Double {
        val fraction = ((speedKmh - OVERALL_MIN_SPEED).toDouble() / (OVERALL_MAX_SPEED - OVERALL_MIN_SPEED)).coerceIn(0.0, 1.0)
        return SLOWEST_INTERVAL_MS - fraction * (SLOWEST_INTERVAL_MS - FASTEST_INTERVAL_MS)
    }

    /**
     * Timing tiers are symmetric around the perfect moment — swinging
     * early is exactly as risky as swinging late, since both mistime
     * the shot. Thresholds are expressed as a FRACTION of the
     * inter-pulse interval rather than a fixed millisecond window, so
     * timing is naturally harder against a faster (shorter-interval)
     * delivery without needing a separate difficulty parameter.
     *
     * Calibrated for real touchscreen input on a mobile game, not a
     * precision rhythm-game benchmark: touch event dispatch alone
     * typically adds 10-30ms of jitter on top of genuine human
     * motor-timing variance.
     *
     * WIDENED AGAIN after further feedback that Perfect/Ideal still
     * felt very small and hard to land even after an earlier widening
     * pass (thresholds of roughly 0.16/0.30/0.48/0.68). The core
     * problem with a narrow window compounds: a batter who can't
     * reliably reach Perfect/Ideal never sees the favorable multipliers
     * in applyTimingTier below, which makes batting feel unresponsive
     * to real skill regardless of shot choice or footwork. These
     * thresholds are deliberately generous — Perfect now covers
     * essentially "hit it anywhere near the beat," Ideal covers a good
     * chunk of "reasonably close" — while Bad/Very Bad still require a
     * genuinely mistimed tap (well off the beat, not just a few pixels
     * of touch jitter) to reach. Good is now a comfortable middle band,
     * not the "everything that isn't quite Perfect" bucket it used to
     * be.
     *
     *   Perfect  |delta|/interval <= 0.28
     *   Ideal    |delta|/interval <= 0.45
     *   Good     |delta|/interval <= 0.62
     *   Bad      |delta|/interval <= 0.80
     *   Very Bad anything past that
     */
    fun computeTimingTier(actualTapTimeMs: Double, expectedFifthPulseTimeMs: Double, intervalMs: Double): TimingResult {
        val delta = abs(actualTapTimeMs - expectedFifthPulseTimeMs)
        val deltaFraction = if (intervalMs > 0) delta / intervalMs else 1.0
        val tier = when {
            deltaFraction <= 0.28 -> BowlingQualityTier.PERFECT
            deltaFraction <= 0.45 -> BowlingQualityTier.IDEAL
            deltaFraction <= 0.62 -> BowlingQualityTier.GOOD
            deltaFraction <= 0.80 -> BowlingQualityTier.BAD
            else -> BowlingQualityTier.VERY_BAD
        }
        return TimingResult(tier, deltaFraction)
    }

    // ============================================================
    // --- Probability shaping ---
    //
    // Every factor below layers multiplicatively onto the same shared
    // OutcomeProbs object bowling already uses (BowlingSystem.kt) —
    // batting has no separate probability model, just more layers on
    // top of the same one.
    //
    // REBALANCED after playtesting found that a well-bowled delivery
    // (Perfect/Ideal bowling quality tier, which already suppresses
    // four/six and boosts wicket — see BowlingSystem.applyQualityTier)
    // combined multiplicatively with a blind-guess footwork mismatch
    // could completely swamp even a Perfect batting timing tier: e.g. a
    // Perfect bowling ball's four times 0.7 stacked with a footwork
    // mismatch's wicket times 1.8 could leave a batsman worse off
    // DESPITE perfect timing, since footwork is committed blind before
    // the ball is even revealed. The multipliers below were increased
    // (batting side) and the mismatch penalty reduced, specifically so
    // that correct execution across footwork+shot+timing reliably
    // produces a net boundary/safety swing in the batter's favor even
    // against good bowling — while a genuinely mismatched shot choice
    // (the "Bad" tier) remains deliberately catastrophic, unchanged, per
    // the original design intent. This is a tuning pass based on
    // feedback, not a solved equation — keep iterating on these
    // constants if the balance still feels off in either direction.
    // ============================================================

    private fun multiplyKey(probs: OutcomeProbs, key: KMutableProperty1<OutcomeProbs, Double>, factor: Double) {
        key.set(probs, maxOf(0.001, key.get(probs) * factor))
    }

    private fun applyFootworkMatch(probs: OutcomeProbs, tier: FootworkMatchTier) {
        when (tier) {
            FootworkMatchTier.MATCHED -> {
                multiplyKey(probs, OutcomeProbs::wicket, 0.7)
                multiplyKey(probs, OutcomeProbs::dot, 0.85)
            }
            FootworkMatchTier.NEUTRAL -> {}
            FootworkMatchTier.MISMATCHED -> {
                // Softened from 1.8/1.3 — footwork is committed BLIND,
                // before the ball is even revealed, so guessing wrong is
                // inherent to the mechanic rather than a real batting
                // error. It was previously strong enough to
                // single-handedly overwhelm even Perfect timing.
                multiplyKey(probs, OutcomeProbs::wicket, 1.3)
                multiplyKey(probs, OutcomeProbs::dot, 1.15)
            }
        }
    }

    private fun applyShotCompatibility(probs: OutcomeProbs, tier: ShotCompatibilityTier, shot: NamedShot) {
        // Defensive shots never get the boundary-boosting half of these
        // multipliers — "excellent"/"good" compatibility for a
        // defensive shot means "correctly read the length and defended
        // it safely", not "smashed it for four". The wicket/dot safety
        // effects still apply (a well-chosen defense really is safer
        // than a mismatched one); the four/six rewards are reserved for
        // shots actually meant to score. See applyDefensiveShotCap()
        // below for the hard cap that also neutralizes any boundary
        // chance carried in from OTHER layers (bowling quality, timing)
        // for a defensive shot.
        val isDefensive = isDefensiveShot(shot)
        when (tier) {
            ShotCompatibilityTier.EXCELLENT -> {
                // Boosted from 0.3/1.7/1.8/0.8 — a correctly-read shot
                // should reliably pay off, not just "mostly" pay off.
                multiplyKey(probs, OutcomeProbs::wicket, 0.22)
                multiplyKey(probs, OutcomeProbs::dot, 0.72)
                if (!isDefensive) {
                    multiplyKey(probs, OutcomeProbs::four, 1.9)
                    multiplyKey(probs, OutcomeProbs::six, 2.0)
                }
            }
            ShotCompatibilityTier.GOOD -> {
                // Was near-neutral (1.05/1.05, no wicket effect at all)
                // — nudged up slightly so a sound-but-not-optimal shot
                // still shows a real, if modest, edge over doing
                // nothing, while keeping most of its variance (this is
                // NOT meant to approach "excellent").
                multiplyKey(probs, OutcomeProbs::wicket, 0.88)
                if (!isDefensive) {
                    multiplyKey(probs, OutcomeProbs::four, 1.2)
                    multiplyKey(probs, OutcomeProbs::six, 1.2)
                }
            }
            ShotCompatibilityTier.BAD -> {
                // SEVERE by design — see the doc comment above the
                // compatibility table. This is what makes a Pull Shot
                // to a full ball a near-certain beating or dismissal.
                // Left unchanged by this rebalance.
                multiplyKey(probs, OutcomeProbs::wicket, 17.0)
                multiplyKey(probs, OutcomeProbs::dot, 3.5)
                multiplyKey(probs, OutcomeProbs::one, 0.04)
                multiplyKey(probs, OutcomeProbs::two, 0.04)
                multiplyKey(probs, OutcomeProbs::three, 0.04)
                multiplyKey(probs, OutcomeProbs::four, 0.03)
                multiplyKey(probs, OutcomeProbs::six, 0.03)
            }
        }
    }

    /**
     * Hard cap applied as the FINAL step for a defensive shot (forward
     * or backward defense), overriding whatever the other layers
     * (footwork match, shot compatibility, timing) computed. A
     * defensive shot's whole purpose is to stop the ball dead, not
     * score off it — in real cricket it produces a dot ball or a
     * nudged single the overwhelming majority of the time, a second run
     * occasionally on a firm push into a gap, and a boundary only on a
     * genuine fluke (an inside edge that beats every fielder). Because
     * every layer multiplies onto the same shared probs object,
     * applying this LAST means it reliably suppresses four/six
     * regardless of how "well" the ball was bowled or how well the shot
     * was timed — those still matter for wicket/dot risk, just never
     * for turning a block into a boundary.
     */
    private fun applyDefensiveShotCap(probs: OutcomeProbs) {
        multiplyKey(probs, OutcomeProbs::six, 0.01)
        multiplyKey(probs, OutcomeProbs::four, 0.04)
        multiplyKey(probs, OutcomeProbs::three, 0.08)
        multiplyKey(probs, OutcomeProbs::two, 0.3)
        multiplyKey(probs, OutcomeProbs::one, 1.05)
        multiplyKey(probs, OutcomeProbs::dot, 1.2)
    }

    private fun applyTimingTier(probs: OutcomeProbs, tier: TimingTier) {
        when (tier) {
            BowlingQualityTier.PERFECT -> {
                // Boosted from 0.5/0.7/1.4/1.5 — Perfect timing should
                // reliably swing a ball back in the batter's favor even
                // against good bowling, not just partially offset it.
                multiplyKey(probs, OutcomeProbs::wicket, 0.4)
                multiplyKey(probs, OutcomeProbs::dot, 0.62)
                multiplyKey(probs, OutcomeProbs::four, 1.7)
                multiplyKey(probs, OutcomeProbs::six, 1.85)
            }
            BowlingQualityTier.IDEAL -> {
                multiplyKey(probs, OutcomeProbs::wicket, 0.58)
                multiplyKey(probs, OutcomeProbs::dot, 0.78)
                multiplyKey(probs, OutcomeProbs::four, 1.4)
                multiplyKey(probs, OutcomeProbs::six, 1.5)
            }
            BowlingQualityTier.GOOD -> {}
            BowlingQualityTier.BAD -> {
                multiplyKey(probs, OutcomeProbs::wicket, 1.6)
                multiplyKey(probs, OutcomeProbs::dot, 1.3)
                multiplyKey(probs, OutcomeProbs::four, 0.7)
                multiplyKey(probs, OutcomeProbs::six, 0.6)
            }
            BowlingQualityTier.VERY_BAD -> {
                multiplyKey(probs, OutcomeProbs::wicket, 2.2)
                multiplyKey(probs, OutcomeProbs::dot, 1.5)
                multiplyKey(probs, OutcomeProbs::four, 0.5)
                multiplyKey(probs, OutcomeProbs::six, 0.4)
            }
        }
    }

    // Lengths a batsman can genuinely step into against pace without it
    // being reckless — matches the FULL_ISH set used for footwork, plus
    // good_length (still driveable if you commit early enough).
    private val STEP_OUT_VIABLE_VS_PACE: List<CompatibleLength> = FULL_ISH + listOf(BowlingLength.GOOD_LENGTH)

    /**
     * The RIGHT-direction intent (stepping out / advancing down the
     * pitch) isn't a named shot, but it carries the same shot-choice-
     * style logic: against spin it's favorable regardless of length
     * (matches the design's own callout that it's "especially useful in
     * spin bowling"); against pace it's viable with real variance for
     * full-ish/good-length balls, but genuinely dangerous — charging a
     * fast bowler's bouncer — for anything short.
     */
    private fun applyStepOutNuance(probs: OutcomeProbs, actualLength: CompatibleLength, bowlingStyle: BowlingStyle) {
        if (bowlingStyle == BowlingStyle.SPIN) {
            multiplyKey(probs, OutcomeProbs::wicket, 0.5)
            multiplyKey(probs, OutcomeProbs::four, 1.4)
            multiplyKey(probs, OutcomeProbs::six, 1.5)
            multiplyKey(probs, OutcomeProbs::dot, 0.8)
            return
        }
        if (actualLength in STEP_OUT_VIABLE_VS_PACE) {
            // Viable but not free — kept close to neutral so it shows
            // real variance rather than being auto-rewarded.
            multiplyKey(probs, OutcomeProbs::four, 1.15)
            multiplyKey(probs, OutcomeProbs::six, 1.2)
            return
        }
        // Charging a fast bowler's short ball is genuinely reckless.
        multiplyKey(probs, OutcomeProbs::wicket, 3.0)
        multiplyKey(probs, OutcomeProbs::dot, 2.0)
        multiplyKey(probs, OutcomeProbs::one, 0.3)
        multiplyKey(probs, OutcomeProbs::two, 0.3)
        multiplyKey(probs, OutcomeProbs::three, 0.3)
        multiplyKey(probs, OutcomeProbs::four, 0.3)
        multiplyKey(probs, OutcomeProbs::six, 0.3)
    }

    private fun applyIntentDirection(probs: OutcomeProbs, intent: IntentDirection, actualLength: CompatibleLength, bowlingStyle: BowlingStyle) {
        when (intent) {
            IntentDirection.UP -> {
                // Aggressive, aerial-leaning: higher wicket AND boundary
                // chance, but can still produce dot/1/2/3.
                multiplyKey(probs, OutcomeProbs::wicket, 1.3)
                multiplyKey(probs, OutcomeProbs::four, 1.4)
                multiplyKey(probs, OutcomeProbs::six, 1.6)
                multiplyKey(probs, OutcomeProbs::dot, 0.9)
            }
            IntentDirection.DOWN -> {
                // Defensive intent on a non-defensive shot: the whole
                // point is rotating strike, so singles and doubles are
                // the DOMINANT outcome here, not dots — dots can still
                // happen, they're just not the focus. A four
                // occasionally sneaks through a gap; a six essentially
                // never happens off a deliberately defensive placement
                // shot. Wicket chance stays very low.
                multiplyKey(probs, OutcomeProbs::wicket, 0.25)
                multiplyKey(probs, OutcomeProbs::dot, 0.85)
                multiplyKey(probs, OutcomeProbs::one, 1.6)
                multiplyKey(probs, OutcomeProbs::two, 1.4)
                multiplyKey(probs, OutcomeProbs::three, 1.15)
                multiplyKey(probs, OutcomeProbs::four, 0.55)
                multiplyKey(probs, OutcomeProbs::six, 0.1)
            }
            IntentDirection.LEFT -> {
                // Grounded aggressive — deliberately sits BETWEEN "down"
                // (defensive) and "up" (full aerial aggression): still
                // capable of singles/doubles for rotation, but with a
                // real step up in both boundary chance (favoring fours
                // over sixes, since it's a grounded shot) and wicket
                // risk compared to a defensive placement, while staying
                // below "up"'s aerial risk/reward.
                multiplyKey(probs, OutcomeProbs::wicket, 1.0)
                multiplyKey(probs, OutcomeProbs::dot, 0.95)
                multiplyKey(probs, OutcomeProbs::one, 1.2)
                multiplyKey(probs, OutcomeProbs::two, 1.1)
                multiplyKey(probs, OutcomeProbs::four, 1.6)
                multiplyKey(probs, OutcomeProbs::six, 1.25)
            }
            IntentDirection.RIGHT -> applyStepOutNuance(probs, actualLength, bowlingStyle)
        }
    }

    /**
     * Applies every dimension of a resolved batting decision (footwork
     * match, shot-choice compatibility, intent direction, and timing)
     * to a base outcome-probability object already carrying the
     * bowling decision's own line/length/variation/quality layers.
     * Mutates and returns the same object, mirroring
     * BowlingSystem.applyBowlingDecisionToProbabilities()'s contract
     * exactly.
     */
    fun applyBattingDecisionToProbabilities(
        probs: OutcomeProbs,
        decision: BattingDecision,
        actualLength: CompatibleLength,
        bowlingStyle: BowlingStyle
    ): OutcomeProbs {
        applyFootworkMatch(probs, computeFootworkMatch(decision.footwork, actualLength))
        applyShotCompatibility(probs, computeShotCompatibility(decision.shot, actualLength, bowlingStyle), decision.shot)
        if (decision.intent != null) {
            applyIntentDirection(probs, decision.intent, actualLength, bowlingStyle)
        }
        applyTimingTier(probs, decision.timingTier)
        // Final override for a defensive shot — see
        // applyDefensiveShotCap()'s doc comment for why this has to run
        // LAST, after every other layer (including bowling quality and
        // timing) has already been applied.
        if (isDefensiveShot(decision.shot)) {
            applyDefensiveShotCap(probs)
        }
        return probs
    }

    // --- AI batting decision ---

    private fun <T> weightedPickBatting(items: List<WeightedItem<T>>): T {
        val total = items.sumOf { maxOf(0.0, it.weight) }
        if (total <= 0) return items[0].value
        var roll = Random.nextDouble() * total
        for (item in items) {
            roll -= maxOf(0.0, item.weight)
            if (roll <= 0) return item.value
        }
        return items.last().value
    }

    private val NON_DEFENSIVE_SHOTS: List<NamedShot> = SHOT_OPTIONS.filter { !it.isDefensive }.map { it.value }

    /**
     * Every non-defensive named shot that reaches the given
     * compatibility tier against this actual length/bowling style —
     * i.e. the shots a sensible batsman would actually reach for on
     * this ball.
     */
    private fun shotsAtCompatibility(actualLength: CompatibleLength, bowlingStyle: BowlingStyle, tier: ShotCompatibilityTier): List<NamedShot> =
        NON_DEFENSIVE_SHOTS.filter { computeShotCompatibility(it, actualLength, bowlingStyle) == tier }

    /**
     * Generates a fully-resolved batting decision for an AI-controlled
     * batsman — used whenever the user is BOWLING, so the AI's batting
     * runs through the exact same footwork/shot-compatibility/intent/
     * timing machinery a user-controlled batting turn does (see the
     * eventual BattingShotScreen and applyBattingDecisionToProbabilities
     * above). There is no separate simplified AI-batting model any more:
     * every dimension below feeds the same functions a real
     * gesture-driven decision would, with the only difference being how
     * the *inputs* are produced — weighted random picks reacting to the
     * actual ball, biased by the batsman's own battingRating, instead of
     * a drag gesture.
     *
     * Dimension by dimension:
     * - FOOTWORK: reacts to the delivery's ACTUAL length (after any
     *   bowling mis-execution) — front foot for full-ish lengths, back
     *   foot for short-ish, a coin flip for the ambiguous good-length/
     *   back-of-length zone. A higher-rated batsman picks the
     *   objectively correct footwork more often; a weaker one is more
     *   likely to get caught in two minds.
     * - SHOT: first decides how aggressively to play at all — a
     *   well-bowled ball (Perfect/Ideal quality tier) mostly gets
     *   defended, a badly-bowled one (Bad/Very Bad) mostly gets
     *   attacked, moderated by the batsman's own rating. When
     *   defending, the shot is whichever named defense matches the
     *   chosen footwork (front foot -> Forward Defense, back foot ->
     *   Backward Defense) — this is exactly how a real batsman's
     *   defensive shot is chosen, not a random pick. When attacking,
     *   the shot is drawn from whichever named shots are "Excellent"
     *   (falling back to "Good", then a safe default) against this
     *   actual length/bowling style — i.e. the AI generally reaches for
     *   a shot that actually suits the ball, with a small
     *   rating-dependent chance of a genuine misread that lands on
     *   whatever shot doesn't suit the ball at all (the same way a
     *   human player's own misjudged shot choice would).
     * - INTENT: only rolled for a non-defensive shot. Weighted by the
     *   same aggression level used for the shot decision — a more
     *   aggressive mindset favors "up"/"left"/"right" over "down", and
     *   "right" (step out) is weighted higher specifically against
     *   spin, matching the step-out nuance's own design.
     * - TIMING: simulated the same way
     *   BowlingSystem.generateAiBowlingDecision() simulates bowling
     *   execution quality — a score built from battingRating plus
     *   randomness, run through the same
     *   BowlingSystem.qualityTierFromScore() a real timed tap would
     *   use.
     *
     * situationalAggressionBias (-1..1, default 0) reflects the match
     * situation, computed from live match state by the caller — not
     * looked up here, so this function stays decoupled from MatchState
     * entirely. Negative = pull back (an early collapse with several
     * wickets down, or a comfortable chase with no need to rush):
     * shifts the aggression roll toward defending, and even when still
     * attacking, shifts intent further toward "down" (rotate strike)
     * over the boundary-hunting directions. Positive = must push on (a
     * tough chase, or the death overs with wickets in hand): shifts the
     * same rolls the other way. It also nudges footwork accuracy down
     * slightly under heavy negative pressure — a batsman under real
     * strain is a little more error-prone, same idea as a bowler's
     * execution randomness widening when attacking in
     * generateAiBowlingDecision().
     */
    fun generateAiBattingDecision(
        batsman: Player,
        actualLength: CompatibleLength,
        bowlingStyle: BowlingStyle,
        bowlingQualityTier: BowlingQualityTier,
        situationalAggressionBias: Double = 0.0
    ): BattingDecision {
        val bias = situationalAggressionBias.coerceIn(-1.0, 1.0)

        // --- Footwork: react to the actual length, with rating-dependent accuracy ---
        val isFullIsh = actualLength in FULL_ISH
        val isShortIsh = actualLength in SHORT_ISH
        val idealFootwork = when {
            isFullIsh -> FootworkType.FRONT_FOOT
            isShortIsh -> FootworkType.BACK_FOOT
            else -> if (Random.nextDouble() < 0.5) FootworkType.FRONT_FOOT else FootworkType.BACK_FOOT
        }
        // A batsman under real situational pressure (a collapse in
        // progress) is a little more error-prone with footwork, same
        // idea as a bowler's execution randomness widening when
        // attacking.
        val pressurePenalty = if (bias < 0) minOf(0.12, -bias * 0.15) else 0.0
        val footworkAccuracy = maxOf(0.35, 0.5 + (batsman.battingRating / 100.0) * 0.4 - pressurePenalty)
        val footwork = if (Random.nextDouble() < footworkAccuracy) {
            idealFootwork
        } else {
            if (idealFootwork == FootworkType.FRONT_FOOT) FootworkType.BACK_FOOT else FootworkType.FRONT_FOOT
        }

        // --- Aggression level: how well was this ball bowled, tempered
        // by the batsman's own rating AND the match situation ---
        val qualityAggressionBase = mapOf(
            BowlingQualityTier.PERFECT to 0.15,
            BowlingQualityTier.IDEAL to 0.3,
            BowlingQualityTier.GOOD to 0.55,
            BowlingQualityTier.BAD to 0.8,
            BowlingQualityTier.VERY_BAD to 0.95
        )
        val aggressionChance = (
            qualityAggressionBase.getValue(bowlingQualityTier) +
                (batsman.battingRating - 50) / 100.0 * 0.15 +
                bias * 0.45
            ).coerceIn(0.0, 1.0)
        val isAttacking = Random.nextDouble() < aggressionChance

        val shot: NamedShot
        if (!isAttacking) {
            // A real batsman's defensive shot is determined by
            // footwork, not an independent random pick.
            shot = if (footwork == FootworkType.FRONT_FOOT) NamedShot.FORWARD_DEFENSE else NamedShot.BACKWARD_DEFENSE
        } else {
            // Reach for whatever suits this ball — Excellent first,
            // then Good, then a safe generic fallback if neither tier
            // has any options for this particular length/style
            // combination.
            val excellentOptions = shotsAtCompatibility(actualLength, bowlingStyle, ShotCompatibilityTier.EXCELLENT)
            val goodOptions = shotsAtCompatibility(actualLength, bowlingStyle, ShotCompatibilityTier.GOOD)
            val sensiblePool = when {
                excellentOptions.isNotEmpty() -> excellentOptions
                goodOptions.isNotEmpty() -> goodOptions
                else -> listOf(NamedShot.LEG_GLANCE_FLICK)
            }

            // A small, rating-dependent chance of a genuine misread —
            // playing a shot that doesn't suit this ball at all, same
            // as a human player's own misjudged shot choice would.
            val misreadChance = maxOf(0.03, minOf(0.3, (100 - batsman.battingRating) / 100.0 * 0.35))
            shot = if (Random.nextDouble() < misreadChance) {
                NON_DEFENSIVE_SHOTS[(Random.nextDouble() * NON_DEFENSIVE_SHOTS.size).toInt().coerceAtMost(NON_DEFENSIVE_SHOTS.size - 1)]
            } else {
                sensiblePool[(Random.nextDouble() * sensiblePool.size).toInt().coerceAtMost(sensiblePool.size - 1)]
            }
        }

        // --- Intent: only for non-defensive shots ---
        var intent: IntentDirection? = null
        if (!isDefensiveShot(shot)) {
            val rightWeight = 20 * (if (bowlingStyle == BowlingStyle.SPIN) 1.6 else 0.7)
            intent = weightedPickBatting(
                listOf(
                    WeightedItem(IntentDirection.DOWN, (1 - aggressionChance) * 40 + 10),
                    WeightedItem(IntentDirection.UP, aggressionChance * 30),
                    WeightedItem(IntentDirection.LEFT, aggressionChance * 40),
                    WeightedItem(IntentDirection.RIGHT, aggressionChance * rightWeight)
                )
            )
        }

        // --- Timing: simulated the same way AI bowling quality is simulated ---
        // REBALANCED AGAIN after further feedback: the previous
        // recalibration (baseline 25+rating*0.45, spread +/-48) fixed the
        // original "AI never bowls/times badly" problem, but
        // overcorrected — it gave even a 90-rated batsman roughly a
        // 1-in-3 chance of Bad/Very Bad timing on every single ball,
        // which combined with an already-hard-to-convert bowling side
        // (deliberately tuned bad-ball-heavy in the earlier pass) left
        // the AI unable to actually SCORE, even in a run chase that
        // demanded it — 38 for 2 in 7.4 overs chasing 257 was the
        // reported symptom. Real elite batsmen fail plenty, but not a
        // third of every ball they face. This version keeps real,
        // non-trivial failure chance at every rating (a top batsman
        // still has a genuine Bad/Very-Bad chance, just closer to
        // 15-20% instead of 30%+), while a genuinely weak batsman still
        // fails far more often than not.
        //
        // It's also now the FIRST place situational pressure feeds into
        // batting execution, not just shot selection: bias*10 shifts
        // the baseline itself, not just which shot gets picked — a
        // batter genuinely committing under chase pressure should
        // convert that extra intent into better execution some of the
        // time, not just take wilder swings with the same old miss
        // rate. Negative bias (pulling back, protecting wickets) very
        // slightly loosens focus too, mirroring the existing footwork
        // pressurePenalty above.
        val timingScore = (32 + batsman.battingRating * 0.48 + bias * 10 + (Random.nextDouble() * 84 - 42)).coerceIn(0.0, 100.0)
        val timingTier = BowlingSystem.qualityTierFromScore(timingScore)
        // Representative delta fraction for this tier — used only for
        // display/debug parity with a real gesture's BattingDecision,
        // never for probability shaping (only the tier itself feeds
        // that).
        val representativeDeltaFraction = mapOf(
            BowlingQualityTier.PERFECT to 0.05,
            BowlingQualityTier.IDEAL to 0.15,
            BowlingQualityTier.GOOD to 0.27,
            BowlingQualityTier.BAD to 0.45,
            BowlingQualityTier.VERY_BAD to 0.7
        )

        return BattingDecision(
            footwork = footwork,
            shot = shot,
            intent = intent,
            timingTier = timingTier,
            timingDeltaFraction = representativeDeltaFraction.getValue(timingTier)
        )
    }

    // --- Shot direction (for fielding integration) ---

    private val SHOT_PRIMARY_SECTOR: Map<NamedShot, FieldingSector> = mapOf(
        NamedShot.STRAIGHT_DRIVE to FieldingSector.MID_ON,
        NamedShot.ON_DRIVE to FieldingSector.MID_ON,
        NamedShot.COVER_DRIVE to FieldingSector.COVER,
        NamedShot.SQUARE_DRIVE to FieldingSector.POINT,
        NamedShot.CUT_SHOT to FieldingSector.POINT,
        NamedShot.LATE_CUT to FieldingSector.THIRD_MAN,
        NamedShot.PULL_SHOT to FieldingSector.MID_WICKET,
        NamedShot.HOOK_SHOT to FieldingSector.FINE_LEG,
        NamedShot.LEG_GLANCE_FLICK to FieldingSector.SQUARE_LEG,
        NamedShot.SWEEP_SHOT to FieldingSector.SQUARE_LEG,
        NamedShot.REVERSE_SWEEP to FieldingSector.POINT,
        NamedShot.UPPER_CUT to FieldingSector.THIRD_MAN,
        NamedShot.SCOOP_SHOT to FieldingSector.FINE_LEG
    )

    // Shots with a genuine top-edge/mishit risk built into the shot
    // itself, independent of how aggressively it was played — real
    // cricket's most common catches at square leg, deep square leg,
    // fine leg, and third man come off exactly these shots.
    private val ALWAYS_AERIAL_SHOTS: List<NamedShot> =
        listOf(NamedShot.PULL_SHOT, NamedShot.HOOK_SHOT, NamedShot.UPPER_CUT, NamedShot.SCOOP_SHOT)

    /**
     * Where a resolved batting decision is likely to send the ball.
     * Returns null for the two defensive shots — a block doesn't really
     * have a "direction" worth modeling, and its outcome is already
     * dominated by applyDefensiveShotCap() regardless of anything
     * fielding could do.
     */
    fun getShotDirection(shot: NamedShot, intent: IntentDirection?): ShotDirection? {
        if (isDefensiveShot(shot)) return null
        val sector = SHOT_PRIMARY_SECTOR.getValue(shot)
        val isAerial = shot in ALWAYS_AERIAL_SHOTS || intent == IntentDirection.UP
        return ShotDirection(sector, isAerial)
    }
}

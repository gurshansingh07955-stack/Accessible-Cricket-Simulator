package com.cricketsim.logic

/**
 * How far a six went, in metres: worked out, never random. The same ball, the same shot and the same ground always give the same number.
 *
 * The distance is the ground's boundary (a small ground 66 m, a medium one 73 m, a large one 80 m) plus 2 metres to clear the rope plus a carry of up to 38 metres
 * that depends on how hard the ball was hit ([strength], 0 to 1):
 *   - the TIMING of the shot, the biggest part (Perfect hits it farthest, Very bad barely over the rope): 55%
 *   - the QUALITY of the ball (a bad ball, a full toss or a half-volley, goes farther than a perfect one): 15%
 *   - the SPEED of the ball (the ball's own pace comes back off the bat: a fast ball goes farther than a slow spinner): 10%
 *   - the batter's own batting rating: 10%
 *   - the shot (a helicopter shot or a pull sends it farther than a glance): 10%
 * A six that came off the edge only just clears the rope (the strength is cut to 35%). A high ground (thin air) adds 3% of the carry for every 1,000 m.
 * The strength is squeezed by [CURVE] so that a poorly timed six is short and only a well-timed one reaches 100 m.
 *
 * [SHOW_SIX_DISTANCE] false removes the distance from the ball's text. The weights are a model, not measured on real shots.
 */
object SixDistance {
    const val SHOW_SIX_DISTANCE = true
    private const val ROPE_CLEARANCE = 2.0
    private const val MAX_CARRY = 38.0
    private const val CURVE = 1.3
    private const val RECORD = 125

    fun boundaryMetres(size: BoundarySize): Double = when (size) {
        BoundarySize.SMALL -> 66.0
        BoundarySize.MEDIUM -> 73.0
        BoundarySize.LARGE -> 80.0
    }

    private fun timingPower(t: TimingTier): Double = when (t) {
        BowlingQualityTier.PERFECT -> 1.0
        BowlingQualityTier.IDEAL -> 0.8
        BowlingQualityTier.GOOD -> 0.5
        BowlingQualityTier.BAD -> 0.25
        BowlingQualityTier.VERY_BAD -> 0.1
    }

    /** How easy the ball was to hit far: a bad ball is, a perfect one is not. */
    private fun looseness(q: BowlingQualityTier): Double = when (q) {
        BowlingQualityTier.VERY_BAD -> 1.0
        BowlingQualityTier.BAD -> 0.8
        BowlingQualityTier.GOOD -> 0.5
        BowlingQualityTier.IDEAL -> 0.35
        BowlingQualityTier.PERFECT -> 0.2
    }

    private fun shotPower(s: NamedShot): Double = when (s) {
        NamedShot.HELICOPTER_SHOT, NamedShot.SWITCH_HIT -> 1.0
        NamedShot.PULL_SHOT, NamedShot.HOOK_SHOT, NamedShot.STRAIGHT_DRIVE, NamedShot.OFF_DRIVE, NamedShot.ON_DRIVE -> 0.9
        NamedShot.COVER_DRIVE, NamedShot.SQUARE_DRIVE -> 0.8
        NamedShot.SHORT_PUNCH -> 0.7
        NamedShot.SCOOP_SHOT, NamedShot.SWEEP_SHOT, NamedShot.REVERSE_SWEEP, NamedShot.UPPER_CUT, NamedShot.CUT_SHOT, NamedShot.LATE_CUT, NamedShot.LEG_GLANCE_FLICK -> 0.6
        NamedShot.FORWARD_DEFENSE, NamedShot.BACKWARD_DEFENSE, NamedShot.LEAVE -> 0.3
    }

    /** How hard the ball was hit, 0 to 1. */
    fun strength(outcome: BallOutcome, decision: BattingDecision?, speedKmh: Int, batterRating: Int): Double {
        val timing = decision?.let { timingPower(it.timingTier) } ?: 0.6
        val loose = outcome.bowlingQualityTier?.let { looseness(it) } ?: 0.5
        val pace = ((speedKmh - 70) / 80.0).coerceIn(0.0, 1.0)
        val bat = (batterRating / 100.0).coerceIn(0.0, 1.0)
        val shot = decision?.let { shotPower(it.shot) } ?: 0.7
        val raw = 0.55 * timing + 0.15 * loose + 0.10 * pace + 0.10 * bat + 0.10 * shot
        // The weakest possible combination is about 0.13 and the strongest about 1.0.
        val normal = ((raw - 0.13) / 0.87).coerceIn(0.0, 1.0)
        val squeezed = Math.pow(normal, CURVE)
        return if (outcome.isEdge == true) squeezed * 0.35 else squeezed
    }

    /** The length of this ball's six in whole metres, or null if the ball was not a six off the bat. */
    fun metres(outcome: BallOutcome, decision: BattingDecision?, speedKmh: Int, stadium: Stadium?, batterRating: Int): Int? {
        if (!SHOW_SIX_DISTANCE || outcome.runs != 6 || outcome.isWide || outcome.isWicket) return null
        val boundary = stadium?.let { boundaryMetres(it.boundarySize) } ?: boundaryMetres(BoundarySize.MEDIUM)
        val thinAir = 1.0 + (stadium?.altitudeM ?: 0) * 0.00003
        val carry = MAX_CARRY * strength(outcome, decision, speedKmh, batterRating) * thinAir
        return Math.round(boundary + ROPE_CLEARANCE + carry).toInt().coerceAtMost(RECORD)
    }
}

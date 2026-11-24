package com.cricketsim.logic

import kotlin.random.Random

/**
 * THE UMPIRE'S DECISIONS (patch 142). Before this patch every LBW appeal was given out, and only a ball the game had already decided was LBW could be appealed.
 *
 *  - A ball that hits the batter's PAD OR BODY (`BallOutcome.padHit`: an LBW the game decided, a ball that ran off the body for leg byes, or now and then a
 *    dot ball on the stumps) can be appealed whether or not it would have hit the stumps. The appeal screen does not say which.
 *  - The UMPIRE then decides. Three kinds of decision are ever in doubt ([ReviewKind]): LBW, a catch behind the stumps (an edge to the keeper) and a stumping.
 *    One the game decided is usually given out and sometimes NOT OUT; a pad hit that was not LBW is almost always not out and now and then (a bad decision) OUT.
 *    A catch in the field, a bowled and a run out are given as they happened.
 *  - A NOT OUT can be REVIEWED by the fielding side (it has its own reviews, `MatchState.drsFieldingReviewsUsed`, as many as the batting side), and an OUT can be
 *    reviewed by the batter as before (now caught behind too, DrsSystem). The computer's keenness to review depends on the batter's score, his rating and the
 *    state of the match.
 *
 * Every figure is a dial here. [UMPIRE_CAN_GIVE_NOT_OUT] false returns to the old behaviour (every decision the game made is given out).
 */
object UmpireDecisions {
    const val UMPIRE_CAN_GIVE_NOT_OUT = true

    /** The decisions that can be in doubt, and so reviewed. */
    enum class ReviewKind { LBW, CAUGHT_BEHIND, STUMPED }

    /** The chance a dot ball on the stumps' line hits the pad or body (so the fielding side may appeal). Kept small: the user is asked on each. */
    const val PAD_HIT_DOT_CHANCE = 0.05

    /** The chance the umpire wrongly gives OUT a pad hit the game had as not out. */
    private const val WRONG_OUT = 0.06

    /** The chance the umpire gives OUT a catch behind the stumps, and a stumping, that the game decided. */
    private const val CAUGHT_BEHIND_GIVEN_OUT = 0.88
    private const val STUMPED_GIVEN_OUT = 0.90

    /** The chance the computer side appeals for a pad hit that was not LBW (a wicket the game decided is always appealed). */
    private const val AI_APPEAL_ON_PAD_HIT = 0.35

    /** The computer's last review is used more carefully. */
    private const val LAST_REVIEW_FACTOR = 0.7

    /** A catch taken by the keeper behind the stumps: an edge. */
    fun isCaughtBehind(o: BallOutcome): Boolean =
        o.isWicket && o.dismissalType == DismissalType.CAUGHT && o.fielderPosition?.contains("behind the stumps") == true

    /** Which decision this ball is, or null if it is one that is never in doubt (and for a pad hit that was not a wicket, null too: treat it as LBW). */
    fun reviewKindOf(o: BallOutcome): ReviewKind? = when {
        isCaughtBehind(o) -> ReviewKind.CAUGHT_BEHIND
        o.isWicket && o.dismissalType == DismissalType.STUMPED -> ReviewKind.STUMPED
        o.isWicket && o.dismissalType == DismissalType.LBW -> ReviewKind.LBW
        else -> null
    }

    /** Does the umpire raise the finger? [outcome] is the ball as the game decided it; only LBW, a catch behind and a stumping are ever in doubt. */
    fun umpireGivesOut(outcome: BallOutcome, rng: Random = Random.Default): Boolean {
        if (outcome.isWicket) {
            val kind = reviewKindOf(outcome)
            if (kind == null || !UMPIRE_CAN_GIVE_NOT_OUT) return true
            val chance = when (kind) {
                ReviewKind.CAUGHT_BEHIND -> CAUGHT_BEHIND_GIVEN_OUT
                ReviewKind.STUMPED -> STUMPED_GIVEN_OUT
                ReviewKind.LBW -> when (outcome.bowlingQualityTier) {
                    BowlingQualityTier.PERFECT -> 0.93
                    BowlingQualityTier.IDEAL -> 0.88
                    BowlingQualityTier.GOOD -> 0.82
                    BowlingQualityTier.BAD -> 0.72
                    BowlingQualityTier.VERY_BAD -> 0.62
                    else -> 0.85
                }
            }
            return rng.nextDouble() < chance
        }
        return UMPIRE_CAN_GIVE_NOT_OUT && rng.nextDouble() < WRONG_OUT
    }

    /** Does the computer side appeal for a pad hit that was not a wicket? */
    fun aiAppealsPadHit(rng: Random = Random.Default): Boolean = rng.nextDouble() < AI_APPEAL_ON_PAD_HIT

    /**
     * The verdict of a review by the FIELDING side of a not-out decision. [truthOut]: the game had decided it was a wicket.
     *  - LBW: a good ball is more likely to be overturned to out; if not overturned, a share is Umpire's Call (the not out stands and the review is kept), the rest
     *    the decision stands (review lost). A ball that was not LBW is never overturned.
     *  - A catch behind and a stumping have no Umpire's Call: UltraEdge shows the spike, or the foot is in the air, or it does not.
     */
    fun fieldingReviewVerdict(kind: ReviewKind, truthOut: Boolean, tier: BowlingQualityTier?, rng: Random = Random.Default): DrsVerdict {
        if (kind == ReviewKind.CAUGHT_BEHIND) {
            return if (truthOut && rng.nextDouble() < 0.82) DrsVerdict.OVERTURNED else DrsVerdict.STANDS
        }
        if (kind == ReviewKind.STUMPED) {
            return if (truthOut && rng.nextDouble() < 0.88) DrsVerdict.OVERTURNED else DrsVerdict.STANDS
        }
        if (!truthOut) return if (rng.nextDouble() < 0.15) DrsVerdict.UMPIRES_CALL else DrsVerdict.STANDS
        val overturn = when (tier) {
            BowlingQualityTier.PERFECT -> 0.85
            BowlingQualityTier.IDEAL -> 0.78
            BowlingQualityTier.GOOD -> 0.68
            BowlingQualityTier.BAD -> 0.55
            BowlingQualityTier.VERY_BAD -> 0.45
            else -> 0.65
        }
        return when {
            rng.nextDouble() < overturn -> DrsVerdict.OVERTURNED
            rng.nextDouble() < DrsSystem.UMPIRES_CALL_SHARE -> DrsVerdict.UMPIRES_CALL
            else -> DrsVerdict.STANDS
        }
    }

    /** How much the state of the match matters, 0 to 1: a hard chase and few wickets in hand make a wicket worth more. */
    fun situationPressure(s: MatchState): Double {
        var p = 0.25
        val target = s.target
        if (target != null && s.currentInnings >= 2) {
            val ballsUsed = s.score.overs * 6 + s.score.balls
            val ballsLeft = (s.oversLimit * 6 - ballsUsed).coerceAtLeast(1)
            val needed = (target - s.score.runs).coerceAtLeast(0)
            val requiredRate = needed * 6.0 / ballsLeft
            p += ((requiredRate - 6.0) / 10.0).coerceIn(0.0, 0.5)
        }
        p += (s.score.wickets / 10.0) * 0.25
        return p.coerceIn(0.0, 1.0)
    }

    private fun scoreBonus(runs: Int): Double = runs.coerceIn(0, 100) / 100.0
    private fun ratingBonus(rating: Int): Double = (rating - 50).coerceIn(0, 50) / 50.0

    /**
     * How likely the computer fielding side is to review a NOT OUT decision it appealed for. It is keener when the ball really was out, when the batter has made
     * runs (a big wicket), when he is a top batter, and when the match is tight.
     */
    fun aiFieldingReviewChance(truthOut: Boolean, batterRuns: Int, batterRating: Int, state: MatchState, reviewsLeft: Int): Double {
        if (reviewsLeft <= 0) return 0.0
        var p = if (truthOut) 0.55 else 0.10
        p += scoreBonus(batterRuns) * 0.25 + ratingBonus(batterRating) * 0.15 + situationPressure(state) * 0.20
        p = p.coerceAtMost(0.92)
        return if (reviewsLeft == 1) p * LAST_REVIEW_FACTOR else p
    }

    /**
     * Whether the computer BATTING side asks for a review of an OUT decision: the old chance (from the timing of the batter's shot, DrsSystem) adjusted by his
     * score, his rating and the match: a set batter, or a top one, or a side in trouble, reviews more readily.
     */
    fun aiBatterWantsReview(drsCase: DrsCase, reviewsRemaining: Int, rng: Random = Random.Default): Boolean {
        val base = DrsSystem.aiRequestChance(drsCase.timingTier, reviewsRemaining)
        if (base <= 0.0) return false
        val adjust = scoreBonus(drsCase.batterRuns) * 0.20 + ratingBonus(drsCase.batterRating) * 0.10 + drsCase.situation * 0.15 - 0.10
        return rng.nextDouble() < (base + adjust).coerceIn(0.05, 0.95)
    }
}

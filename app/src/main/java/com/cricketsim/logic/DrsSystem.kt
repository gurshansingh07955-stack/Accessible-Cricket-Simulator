package com.cricketsim.logic

import kotlin.random.Random

/**
 * The Decision Review System (DRS). NEW to the Android game — not a port
 * of anything in the web app. Pure Kotlin, no Android dependencies, so
 * every rule here can be read and tested without Compose. The screen that
 * shows a review is DrsScreens.kt; the wiring around a ball is in
 * MatchScreen.kt; the "not out instead" replay of a ball is
 * MatchSimulation.overturnDismissal. See DRS_NOTES.md for the design.
 *
 * WHAT CAN BE REVIEWED. Only a dismissal of the BATTER, and only LBW,
 * run out and stumped — exactly the three the game's rules allow. Run out
 * and stumped are not in the game yet (DismissalType is currently just
 * BOWLED / CAUGHT / LBW), so reviewable types are matched BY NAME (see
 * isReviewable): the day DismissalType gains RUN_OUT and STUMPED, they are
 * reviewable with no change here, and each already has its own tracking
 * sequence below. Bowled and caught are never reviewable.
 *
 * REAL-CRICKET REVIEW COUNT (T20): two reviews per innings for the batting
 * side. A review is LOST only when the decision stands outright. It is KEPT
 * when the decision is overturned, and also when it stays "out" only
 * because of Umpire's Call (a marginal ball-tracking result, LBW only), as
 * in the real game. The count is `MatchState.drsReviewsUsed` and resets at
 * the start of each innings (MatchStateMachine.switchInnings).
 *
 * WHO DECIDES WHAT, AND HOW LONG THEY HAVE:
 * - The user, when their own batter is out, has USER_DECISION_WINDOW_MS
 *   (10 seconds) to ask for a review; if the time runs out the decision
 *   simply stands and no review is used.
 * - The AI takes a random 1.5 to just-under-5 seconds to decide whether to
 *   review (AI_DECISION_MIN_MS / AI_DECISION_LIMIT_MS), whether it ends up
 *   asking or not, so it feels like a real decision.
 * - Once a review is asked for, by either side, the review itself always
 *   takes REVIEW_DURATION_MS (10 seconds).
 *
 * SURVIVAL — THE BATTER'S TIMING, NOT THE BALL. The chance the dismissal
 * is overturned depends ONLY on how well the BATTER timed the shot
 * (BattingDecision.timingTier), never on the bowler's ball quality: see
 * survivalChance. The same table applies to the AI's batters, using the
 * timing tier the AI's own batting decision produced for that ball.
 */

/** How a review ended. Only OVERTURNED lets the batter continue. */
enum class DrsVerdict {
    /** The dismissal is reversed: the batter is not out. The review is kept. */
    OVERTURNED,

    /** Ball tracking is marginal, so the on-field "out" stands — but the review is KEPT. LBW only. */
    UMPIRES_CALL,

    /** The on-field "out" stands outright. The review is LOST. */
    STANDS
}

/**
 * One line of the review as it plays out. `atMs` is when it appears (from
 * the start of the 10-second review), `title` and `detail` are what is
 * shown, and `spoken` is a deliberately SHORT version for the screen
 * reader / voice: steps arrive 1.5 seconds apart, and speech that is
 * longer than a few words would fall behind the screen.
 */
data class DrsStep(val atMs: Long, val title: String, val detail: String, val spoken: String)

/** Everything the review needs to know about the ball that is being reviewed. */
data class DrsCase(
    val reviewingTeamName: String,
    val batterName: String,
    val bowlerName: String,
    val dismissalType: DismissalType,
    // The delivery, for the tracking details. Null only if unknown.
    val delivery: ResolvedBowlingDecision?,
    // The BATTER's timing tier on that shot — this alone drives survival.
    val timingTier: BowlingQualityTier
)

/** The full, already-decided result of a review, and the steps that reveal it. */
data class DrsReport(
    val verdict: DrsVerdict,
    val steps: List<DrsStep>,
    val headline: String,
    val explanation: String
)

object DrsSystem {

    /** How long the user has to ask for a review after their batter is given out. */
    const val USER_DECISION_WINDOW_MS = 10_000L

    /** The AI decides in at least this long... */
    const val AI_DECISION_MIN_MS = 1_500L

    /** ...and always strictly UNDER this (exclusive upper bound: 5 seconds). */
    const val AI_DECISION_LIMIT_MS = 5_000L

    /** Every review, whoever asks for it, plays out over exactly this long. */
    const val REVIEW_DURATION_MS = 10_000L

    // Seven steps 1.5 seconds apart: the last appears at 9.0s, so there is
    // a full second between it and the verdict at 10.0s.
    private const val STEP_INTERVAL_MS = 1_500L

    /**
     * Of the reviews that do NOT overturn the decision, the share that
     * come back as Umpire's Call (decision stands, review kept) instead of
     * a plain "decision stands" (review lost). LBW only. Set to 0.0 to
     * turn Umpire's Call off entirely.
     */
    const val UMPIRES_CALL_SHARE = 0.25

    // AI appetite for a review: 0.15 plus the batter's survival chance
    // (so a well-timed shot feels more "hard done by"), capped, and
    // cut down when it is down to its last review.
    private const val AI_REQUEST_BASE = 0.15
    private const val AI_REQUEST_CAP = 0.9
    private const val AI_LAST_REVIEW_FACTOR = 0.7

    // --- What is reviewable ---

    private enum class Kind { LBW, RUN_OUT, STUMPED }

    // Matched by NAME so DismissalType.RUN_OUT / STUMPED work the moment
    // they are added to the enum, with no edit here. A few spellings are
    // accepted so the future entry names don't have to match exactly.
    private fun kindOf(type: DismissalType): Kind? = when (type.name.uppercase()) {
        "LBW" -> Kind.LBW
        "RUN_OUT", "RUNOUT" -> Kind.RUN_OUT
        "STUMPED", "STUMP_OUT", "STUMPING" -> Kind.STUMPED
        else -> null
    }

    /** True for LBW, run out and stumped — the only dismissals that can be reviewed. */
    fun isReviewable(type: DismissalType?): Boolean = type != null && kindOf(type) != null

    // --- Review count ---

    /** Reviews per innings for the batting side: 2 in T20 (and ODI), 3 in a Test, as in real cricket. */
    fun reviewsPerInnings(format: MatchFormat): Int = when (format) {
        MatchFormat.T20 -> 2
        MatchFormat.ODI -> 2
        MatchFormat.T10 -> 1
        MatchFormat.FIVE_OVERS -> 1
        MatchFormat.ONE_OVER -> 1
        MatchFormat.TEST -> 3
    }

    /** How many reviews the side currently batting has left this innings. */
    fun reviewsRemaining(state: MatchState): Int =
        (reviewsPerInnings(state.format) - state.drsReviewsUsed).coerceAtLeast(0)

    // --- Survival, by the BATTER's timing ---

    /**
     * The chance a reviewed dismissal is overturned, from how well the
     * batter timed the shot: Perfect 70%, Ideal 60%, Good 50%, Bad 35%,
     * Very Bad 25%. Takes the BATTING timing tier (BattingDecision
     * .timingTier) — deliberately not the bowling quality tier, which
     * shares the same enum type but measures a different thing.
     */
    fun survivalChance(timingTier: BowlingQualityTier): Double = when (timingTier) {
        BowlingQualityTier.PERFECT -> 0.70
        BowlingQualityTier.IDEAL -> 0.60
        BowlingQualityTier.GOOD -> 0.50
        BowlingQualityTier.BAD -> 0.35
        BowlingQualityTier.VERY_BAD -> 0.25
    }

    /** A plain word for a timing tier, for spoken lines ("Your timing was perfect"). */
    fun timingWord(timingTier: BowlingQualityTier): String = when (timingTier) {
        BowlingQualityTier.PERFECT -> "perfect"
        BowlingQualityTier.IDEAL -> "ideal"
        BowlingQualityTier.GOOD -> "good"
        BowlingQualityTier.BAD -> "bad"
        BowlingQualityTier.VERY_BAD -> "very bad"
    }

    /** A short spoken name for a dismissal: "LBW", "run out", "stumped". */
    fun dismissalLabel(type: DismissalType): String = when (kindOf(type)) {
        Kind.LBW -> "LBW"
        Kind.RUN_OUT -> "run out"
        Kind.STUMPED -> "stumped"
        null -> type.name.lowercase().replace('_', ' ')
    }

    /** The commentary for a ball whose dismissal was reversed on review. */
    fun overturnedCommentary(type: DismissalType, batterName: String): String =
        "Not out! The ${dismissalLabel(type)} decision against $batterName is overturned on review."

    // --- The AI ---

    /** How likely the AI is to ask for a review, given its batter's timing and reviews left. */
    fun aiRequestChance(timingTier: BowlingQualityTier, reviewsRemaining: Int): Double {
        if (reviewsRemaining <= 0) return 0.0
        val base = (AI_REQUEST_BASE + survivalChance(timingTier)).coerceAtMost(AI_REQUEST_CAP)
        return if (reviewsRemaining == 1) base * AI_LAST_REVIEW_FACTOR else base
    }

    fun aiWantsReview(timingTier: BowlingQualityTier, reviewsRemaining: Int, rng: Random = Random.Default): Boolean =
        rng.nextDouble() < aiRequestChance(timingTier, reviewsRemaining)

    /** How long the AI takes to decide: 1.5s up to, but never reaching, 5s. */
    fun aiDecisionDelayMs(rng: Random = Random.Default): Long =
        rng.nextLong(AI_DECISION_MIN_MS, AI_DECISION_LIMIT_MS)

    // --- Rolling and describing a review ---

    /** The single dice roll: survive by timing, else Umpire's Call (LBW only) or the decision stands. */
    fun rollVerdict(type: DismissalType, timingTier: BowlingQualityTier, rng: Random = Random.Default): DrsVerdict {
        if (rng.nextDouble() < survivalChance(timingTier)) return DrsVerdict.OVERTURNED
        val umpiresCallPossible = kindOf(type) == Kind.LBW
        return if (umpiresCallPossible && rng.nextDouble() < UMPIRES_CALL_SHARE) DrsVerdict.UMPIRES_CALL else DrsVerdict.STANDS
    }

    fun headline(verdict: DrsVerdict): String = when (verdict) {
        DrsVerdict.OVERTURNED -> "Not out. Decision overturned."
        DrsVerdict.UMPIRES_CALL -> "Out. Umpire's call, the decision stands."
        DrsVerdict.STANDS -> "Out. The decision stands."
    }

    /**
     * Rolls the verdict AND builds the ten seconds of tracking that lead to
     * it, so what the screen reveals always agrees with the result. The
     * verdict is decided first, then the evidence is generated to match.
     */
    fun resolve(drsCase: DrsCase, rng: Random = Random.Default): DrsReport {
        val verdict = rollVerdict(drsCase.dismissalType, drsCase.timingTier, rng)
        return when (kindOf(drsCase.dismissalType)) {
            Kind.LBW -> buildLbw(drsCase, verdict, rng)
            Kind.RUN_OUT -> buildRunOut(drsCase, verdict, rng)
            Kind.STUMPED -> buildStumped(drsCase, verdict, rng)
            null -> buildGeneric(drsCase, verdict)
        }
    }

    // ------------------------------------------------------------
    // Building blocks
    // ------------------------------------------------------------

    private class Line(val title: String, val detail: String, val spoken: String)

    private fun timeline(lines: List<Line>): List<DrsStep> =
        lines.mapIndexed { index, line -> DrsStep(index * STEP_INTERVAL_MS, line.title, line.detail, line.spoken) }

    private fun <T> weightedPick(items: List<Pair<T, Double>>, rng: Random): T {
        val total = items.sumOf { it.second }
        var roll = rng.nextDouble() * total
        for ((value, weight) in items) {
            roll -= weight
            if (roll <= 0.0) return value
        }
        return items.last().first
    }

    private fun describeDelivery(delivery: ResolvedBowlingDecision?): String {
        if (delivery == null) return "Delivery details are not available."
        val length = BowlingSystem.lengthLabel(delivery.actualLength).lowercase()
        val line = BowlingSystem.LINE_OPTIONS.firstOrNull { it.value == delivery.line }?.label ?: "unknown"
        return "${delivery.speedKmh} kilometres per hour, $length, $line line."
    }

    private fun requestLine(drsCase: DrsCase): Line {
        val label = dismissalLabel(drsCase.dismissalType)
        return Line(
            "Review requested",
            "${drsCase.reviewingTeamName} have asked for a review of the $label decision against " +
                "${drsCase.batterName}. On-field decision: out. Delivery: ${describeDelivery(drsCase.delivery)}",
            "Review requested."
        )
    }

    private fun noBallLine(): Line = Line(
        "No-ball check",
        "Front foot checked. It is a legal delivery.",
        "No-ball check. Legal delivery."
    )

    private fun finalLine(): Line = Line(
        "Third umpire",
        "All the evidence is in. The third umpire is confirming the decision.",
        "Third umpire confirming."
    )

    // ------------------------------------------------------------
    // LBW: UltraEdge, then ball tracking of pitching, impact, wickets
    // ------------------------------------------------------------

    private enum class LbwReason { BAT_INVOLVED, PITCHED_OUTSIDE_LEG, IMPACT_OUTSIDE_OFF, MISSING_STUMPS }

    // Why an overturned LBW was overturned. The delivered line tilts the
    // odds so the evidence doesn't contradict the ball: a leg-side line
    // is more likely to be shown pitching outside leg, an outside-off
    // line more likely to be shown with impact outside off.
    private fun pickLbwReason(delivery: ResolvedBowlingDecision?, rng: Random): LbwReason {
        val line = delivery?.line
        val legSide = line == BowlingLine.LEG_STUMP || line == BowlingLine.OUTSIDE_LEG
        return weightedPick(
            listOf(
                LbwReason.BAT_INVOLVED to 2.0,
                LbwReason.PITCHED_OUTSIDE_LEG to (if (legSide) 3.0 else 1.0),
                LbwReason.IMPACT_OUTSIDE_OFF to (if (line == BowlingLine.OUTSIDE_OFF) 3.0 else 1.0),
                LbwReason.MISSING_STUMPS to 2.0
            ),
            rng
        )
    }

    private fun buildLbw(drsCase: DrsCase, verdict: DrsVerdict, rng: Random): DrsReport {
        val reason = if (verdict == DrsVerdict.OVERTURNED) pickLbwReason(drsCase.delivery, rng) else null
        // Umpire's Call is either marginal IMPACT or marginal WICKETS, as on TV.
        val callOnImpact = verdict == DrsVerdict.UMPIRES_CALL && rng.nextBoolean()
        val callOnWickets = verdict == DrsVerdict.UMPIRES_CALL && !callOnImpact
        val batInvolved = reason == LbwReason.BAT_INVOLVED
        val pitchedOutsideOff = drsCase.delivery?.line == BowlingLine.OUTSIDE_OFF

        val notNeeded = "Not needed. The ball hit the bat first."

        val edge = if (batInvolved) {
            Line("UltraEdge", "A spike shows as the ball passes the bat. The bat is involved.", "UltraEdge. Bat involved.")
        } else {
            Line("UltraEdge", "No spike. No bat involved.", "UltraEdge. No bat involved.")
        }

        val pitching = when {
            batInvolved -> Line("Ball tracking: pitching", notNeeded, "Pitching. Not needed.")
            reason == LbwReason.PITCHED_OUTSIDE_LEG ->
                Line("Ball tracking: pitching", "Outside leg stump. A ball pitching outside leg cannot be out LBW.", "Pitching. Outside leg.")
            pitchedOutsideOff ->
                Line("Ball tracking: pitching", "Outside off stump. That is allowed for LBW.", "Pitching. Outside off.")
            else -> Line("Ball tracking: pitching", "In line with the stumps.", "Pitching. In line.")
        }

        val impact = when {
            batInvolved -> Line("Ball tracking: impact", notNeeded, "Impact. Not needed.")
            reason == LbwReason.IMPACT_OUTSIDE_OFF ->
                Line("Ball tracking: impact", "Outside off stump, and the batter was playing a shot.", "Impact. Outside off.")
            callOnImpact ->
                Line("Ball tracking: impact", "Umpire's call. Only a small part of the ball is in line.", "Impact. Umpire's call.")
            else -> Line("Ball tracking: impact", "In line with the stumps.", "Impact. In line.")
        }

        val wickets = when {
            batInvolved -> Line("Ball tracking: wickets", notNeeded, "Wickets. Not needed.")
            reason == LbwReason.MISSING_STUMPS -> {
                val how = if (rng.nextBoolean()) "going over the top" else "sliding down the leg side"
                Line("Ball tracking: wickets", "Missing. The ball is $how.", "Wickets. Missing.")
            }
            callOnWickets ->
                Line("Ball tracking: wickets", "Umpire's call. The ball is only clipping the stumps.", "Wickets. Umpire's call.")
            else -> Line("Ball tracking: wickets", "Hitting the stumps.", "Wickets. Hitting.")
        }

        val explanation = when (verdict) {
            DrsVerdict.OVERTURNED -> when (reason) {
                LbwReason.BAT_INVOLVED -> "The ball hit the bat before the pad."
                LbwReason.PITCHED_OUTSIDE_LEG -> "The ball pitched outside leg stump."
                LbwReason.IMPACT_OUTSIDE_OFF -> "The impact was outside off stump while playing a shot."
                LbwReason.MISSING_STUMPS -> "The ball was missing the stumps."
                null -> "The decision is reversed."
            }
            DrsVerdict.UMPIRES_CALL ->
                if (callOnImpact) "Impact was umpire's call, so the on-field decision stands."
                else "Wickets was umpire's call, so the on-field decision stands."
            DrsVerdict.STANDS -> "The impact was in line and the ball was hitting the stumps."
        }

        val steps = timeline(listOf(requestLine(drsCase), noBallLine(), edge, pitching, impact, wickets, finalLine()))
        return DrsReport(verdict, steps, headline(verdict), explanation)
    }

    // ------------------------------------------------------------
    // Run out: replay angles and where the bat is when the bails come off
    // (used automatically once DismissalType gets a RUN_OUT entry)
    // ------------------------------------------------------------

    private fun buildRunOut(drsCase: DrsCase, verdict: DrsVerdict, rng: Random): DrsReport {
        val overturned = verdict == DrsVerdict.OVERTURNED
        val centimetres = rng.nextInt(2, 15)
        val ground = if (overturned) {
            Line(
                "Batter's ground",
                "The bat is grounded behind the line, $centimetres centimetres in, when the bails come off.",
                "Bat grounded. Batter is in."
            )
        } else {
            Line(
                "Batter's ground",
                "The bat is $centimetres centimetres short of the line when the bails come off.",
                "Bat short. Batter is out."
            )
        }
        val steps = timeline(
            listOf(
                requestLine(drsCase),
                noBallLine(),
                Line("Replay: side-on", "The fielder collects the ball and the bails are broken.", "Replay. Side-on."),
                Line("Replay: front-on", "Checking where the bat and body are as the bails come off.", "Replay. Front-on."),
                Line("Frame by frame", "Stepping through the replay one frame at a time.", "Frame by frame."),
                ground,
                finalLine()
            )
        )
        val explanation = if (overturned) "The batter's bat was grounded behind the line." else "The batter was short of the crease."
        return DrsReport(verdict, steps, headline(verdict), explanation)
    }

    // ------------------------------------------------------------
    // Stumped: keeper's position, edge check, and the batter's foot
    // (used automatically once DismissalType gets a STUMPED entry)
    // ------------------------------------------------------------

    private fun buildStumped(drsCase: DrsCase, verdict: DrsVerdict, rng: Random): DrsReport {
        val overturned = verdict == DrsVerdict.OVERTURNED
        // Two ways a stumping is reversed: the foot was actually grounded,
        // or the keeper took the ball in front of the stumps.
        val collectedInFront = overturned && rng.nextBoolean()
        val footBehind = overturned && !collectedInFront

        val gloves = if (collectedInFront) {
            Line("Keeper's gloves", "The keeper took the ball in front of the stumps. That is not a legal stumping.", "Keeper. Ball taken in front.")
        } else {
            Line("Keeper's gloves", "The keeper collected the ball behind the stumps.", "Keeper. Behind the stumps.")
        }
        val foot = when {
            collectedInFront -> Line("Batter's foot", "Not needed. The keeper took the ball in front of the stumps.", "Foot. Not needed.")
            footBehind -> Line("Batter's foot", "The foot is grounded behind the line when the bails come off.", "Foot grounded. Batter is in.")
            else -> Line("Batter's foot", "The foot is in the air, out of the crease, when the bails come off.", "Foot in the air. Batter is out.")
        }
        val steps = timeline(
            listOf(
                requestLine(drsCase),
                noBallLine(),
                gloves,
                Line("UltraEdge", "No spike. No bat involved.", "UltraEdge. No bat involved."),
                foot,
                Line("Bails", "The keeper removes the bails with the ball in the glove.", "Bails removed."),
                finalLine()
            )
        )
        val explanation = when {
            collectedInFront -> "The keeper took the ball in front of the stumps."
            footBehind -> "The batter's foot was grounded behind the line."
            else -> "The batter's foot was out of the crease."
        }
        return DrsReport(verdict, steps, headline(verdict), explanation)
    }

    // A reviewable dismissal type this file doesn't know by name yet: a
    // plain replay sequence, so a newly added type never breaks the flow.
    private fun buildGeneric(drsCase: DrsCase, verdict: DrsVerdict): DrsReport {
        val steps = timeline(
            listOf(
                requestLine(drsCase),
                noBallLine(),
                Line("Replay: first angle", "Checking the first camera angle.", "Replay. First angle."),
                Line("Replay: second angle", "Checking the second camera angle.", "Replay. Second angle."),
                Line("Slow motion", "Checking the slow-motion replay.", "Slow motion."),
                Line("Evidence check", "Weighing up all the evidence.", "Checking the evidence."),
                finalLine()
            )
        )
        val explanation = if (verdict == DrsVerdict.OVERTURNED) "The evidence does not support the decision." else "The evidence supports the decision."
        return DrsReport(verdict, steps, headline(verdict), explanation)
    }
}

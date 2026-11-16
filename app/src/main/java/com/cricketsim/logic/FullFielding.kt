package com.cricketsim.logic

import com.cricketsim.ui.match.BallResult
import kotlin.math.abs
import kotlin.random.Random

/** What a fielding chance asks of the user. */
enum class FieldingKind { CATCH, STUMPING, RUN_OUT, STOP }

/**
 * My Cricket, manual fielding, part two (Settings > Manual fielding > Full manual): everything except the catch, which is part one ([ManualCatch]).
 *
 *  - STUMPING: the ball is a stumping (the batter is out of his crease). The wicketkeeper's call: SWIPE DOWN with two fingers on the last buzz. Missed (wrong
 *    way, too early, too late, no swipe): the batter scrambles back, NOT OUT, no run.
 *  - RUN OUT: the ball is a run out. SWIPE DOWN TWICE in quick succession (within 0.7 s) with the first swipe on the last buzz. Missed: the batter is home, NOT OUT,
 *    and the run he ran counts.
 *  - STOP: a ball hit along the ground that the computer scored 1, 2 or 3 runs off (not an edge). The fielder nearest to it (in the shot's direction, not the
 *    keeper or the bowler) is the user's: swipe up, left or right (where the ball comes from) on the last buzz. Stopped: ONE RUN FEWER. Missed: the runs stand.
 * The buzzes follow the ball's speed and the shot's timing as for a catch. A better fielder forgives more ([window]); a stop is a little easier than a catch.
 * [FULL_MANUAL_FIELDING] false switches this part off (Full manual then works like Half manual).
 */
object FullFielding {
    const val FULL_MANUAL_FIELDING = true
    private const val SHORTEST_INTERVAL_MS = 150.0
    private const val STRAIGHT_CONE_DEGREES = 25.0
    private const val STOP_EXTRA_WINDOW = 0.05

    private fun keeperOf(team: Team): Player? {
        val id = team.wicketkeeperId ?: team.players.find { it.role == PlayerRole.WICKETKEEPER }?.id
        return team.players.find { it.id == id }
    }

    private fun window(rating: Int): Double = (0.30 + 0.004 * rating).coerceIn(0.35, 0.72)

    private fun shotFactor(tier: BowlingQualityTier): Double = when (tier) {
        BowlingQualityTier.PERFECT -> 0.85
        BowlingQualityTier.IDEAL -> 0.90
        BowlingQualityTier.GOOD -> 1.0
        BowlingQualityTier.BAD -> 1.1
        BowlingQualityTier.VERY_BAD -> 1.2
    }

    /** The stumping, run out or stop of this ball for the user's fielders, or null (it is a catch, a wicket of another kind, or nothing to field). */
    fun build(before: MatchState, result: BallResult): CatchChance? {
        if (!FULL_MANUAL_FIELDING) return null
        val o = result.outcome
        return when {
            o.isWicket && o.dismissalType == DismissalType.STUMPED -> stumping(before, result)
            o.isWicket && o.dismissalType == DismissalType.RUN_OUT -> runOut(before, result)
            !o.isWicket -> stop(before, result)
            else -> null
        }
    }

    private fun batterNamed(before: MatchState, id: String?): String =
        before.currentBatsmen.toList().firstOrNull { it.id == id }?.name ?: before.currentBatsmen.first.name

    private fun stumping(before: MatchState, result: BallResult): CatchChance? {
        val keeper = keeperOf(before.bowlingTeam) ?: return null
        val interval = maxOf(SHORTEST_INTERVAL_MS, BattingSystem.computeTimingIntervalMs(result.bowlingDecision.speedKmh))
        return CatchChance(
            keeper.id, keeper.name, keeper.fieldingRating, "behind the stumps", false, CatchDirection.STRAIGHT,
            3, interval, window(keeper.fieldingRating), FieldingKind.STUMPING, batterNamed(before, result.outcome.batsmanId)
        )
    }

    private fun runOut(before: MatchState, result: BallResult): CatchChance? {
        val o = result.outcome
        val fielder = before.bowlingTeam.players.find { it.name == o.fielderName } ?: keeperOf(before.bowlingTeam) ?: return null
        val interval = maxOf(SHORTEST_INTERVAL_MS, BattingSystem.computeTimingIntervalMs(result.bowlingDecision.speedKmh))
        return CatchChance(
            fielder.id, fielder.name, fielder.fieldingRating, o.fielderPosition, false, CatchDirection.STRAIGHT,
            4, interval, window(fielder.fieldingRating), FieldingKind.RUN_OUT, batterNamed(before, o.outBatsmanId ?: o.batsmanId)
        )
    }

    private fun stop(before: MatchState, result: BallResult): CatchChance? {
        val o = result.outcome
        if (o.runs !in 1..3 || o.isEdge == true || o.leftAlone || o.droppedCatch) return null
        val shot = BattingSystem.getShotDirection(result.battingDecision.shot, result.battingDecision.intent) ?: return null
        if (shot.isAerial) return null
        val keeperId = keeperOf(before.bowlingTeam)?.id
        val placed = before.fieldPlacements.filter { it.playerId.isNotEmpty() && it.playerId != before.currentBowler.id && it.playerId != keeperId }
        val inSector = placed.filter { it.sector == shot.sector }
        val pick = inSector.firstOrNull { it.depth != FieldingDepth.DEEP }
            ?: inSector.firstOrNull()
            ?: placed.minByOrNull { FieldingSystem.sectorGapDegrees(it.sector, shot.sector) }
            ?: return null
        val fielder = before.bowlingTeam.players.find { it.id == pick.playerId } ?: return null
        val gap = FieldingSystem.sectorSignedGapDegrees(pick.sector, shot.sector)
        val direction = when {
            abs(gap) <= STRAIGHT_CONE_DEGREES -> CatchDirection.STRAIGHT
            gap < 0 -> CatchDirection.LEFT
            else -> CatchDirection.RIGHT
        }
        val interval = maxOf(SHORTEST_INTERVAL_MS, BattingSystem.computeTimingIntervalMs(result.bowlingDecision.speedKmh) * shotFactor(result.battingDecision.timingTier))
        val pulses = 3 + (if (pick.depth == FieldingDepth.DEEP) 1 else 0)
        return CatchChance(
            fielder.id, fielder.name, fielder.fieldingRating, "at ${FieldingSystem.getPositionLabel(pick).lowercase()}", false, direction,
            pulses, interval, (window(fielder.fieldingRating) + STOP_EXTRA_WINDOW).coerceAtMost(0.75), FieldingKind.STOP
        )
    }

    /** What the phone's voice says before the buzzes. */
    fun announcement(c: CatchChance): String = when (c.kind) {
        FieldingKind.CATCH -> ManualCatch.announcement(c)
        FieldingKind.STUMPING ->
            "${c.batterName} is out of his crease. ${c.fielderName} is the wicketkeeper, fielding rating ${c.fielderRating}. " +
                "Swipe down with two fingers on the last buzz to stump him."
        FieldingKind.RUN_OUT ->
            "${c.batterName} is short of his ground. ${c.fielderName} has the ball, fielding rating ${c.fielderRating}. " +
                "Swipe down twice, quickly, with two fingers, the first on the last buzz, to break the stumps."
        FieldingKind.STOP ->
            "${c.fielderName} is ${c.position ?: "in the field"}. Fielding rating ${c.fielderRating}. The ball is coming ${c.direction.spoken}. " +
                "Swipe ${c.direction.swipe} with two fingers on the last buzz to stop it."
    }

    /** The short instruction shown on the page. */
    fun instruction(c: CatchChance): String = when (c.kind) {
        FieldingKind.CATCH -> "Coming ${c.direction.spoken}. Swipe ${c.direction.swipe}."
        FieldingKind.STUMPING -> "Swipe down."
        FieldingKind.RUN_OUT -> "Swipe down twice, quickly."
        FieldingKind.STOP -> "Coming ${c.direction.spoken}. Swipe ${c.direction.swipe}."
    }

    fun title(c: CatchChance): String = when (c.kind) {
        FieldingKind.CATCH -> "Catch chance"
        FieldingKind.STUMPING -> "Stumping chance"
        FieldingKind.RUN_OUT -> "Run out chance"
        FieldingKind.STOP -> "Stop the ball"
    }

    /** Was the swipe (or the two swipes) the right ones, on the last buzz? [swiped] null = none. */
    fun judge(c: CatchChance, swiped: IntentDirection?, actualMs: Double, expectedMs: Double, doubleSwipe: Boolean): CatchJudgement {
        if (c.kind == FieldingKind.CATCH) return ManualCatch.judge(c, swiped, actualMs, expectedMs)
        if (c.kind == FieldingKind.STOP) {
            val v = ManualCatch.judge(c, swiped, actualMs, expectedMs)
            return if (v.caught) CatchJudgement(true, "Stopped.") else v
        }
        if (swiped == null) return CatchJudgement(false, "You did not swipe in time.")
        if (swiped != IntentDirection.DOWN) return CatchJudgement(false, "Wrong way.")
        if (c.kind == FieldingKind.RUN_OUT && !doubleSwipe) return CatchJudgement(false, "You need two quick swipes.")
        val off = abs(actualMs - expectedMs) / c.intervalMs
        if (off <= c.window) return CatchJudgement(true, "Done.")
        return CatchJudgement(false, if (actualMs < expectedMs) "Too early." else "Too late.")
    }

    /**
     * The ball after the user's attempt, if it changes it (null = the ball stands as it was simulated):
     *  - a dropped CATCH: not out, 0 to 2 runs (CatchDrops);
     *  - a missed STUMPING: not out, no run, "missedStumping";
     *  - a missed RUN OUT: not out, the run he ran counts, "missedRunOut";
     *  - a STOP that worked: one run fewer, "stopped".
     */
    fun outcomeFor(c: CatchChance, result: CatchOutcome, before: MatchState, original: BallOutcome, random: Random = Random.Default): BallOutcome? {
        val fielder = before.bowlingTeam.players.find { it.id == c.fielderId }
        return when (c.kind) {
            FieldingKind.CATCH ->
                if (result == CatchOutcome.DROPPED && fielder != null) CatchDrops.droppedOutcome(before, original, fielder, random) else null
            FieldingKind.STUMPING ->
                if (result == CatchOutcome.DROPPED) original.copy(
                    isWicket = false, dismissalType = null, runs = 0, outBatsmanId = null, fieldingEvent = "missedStumping",
                    commentary = line(random, listOf(
                        "Missed! ${c.fielderName} whips off the bails, but ${c.batterName} is back in time. Not out.",
                        "${c.fielderName} fumbles it, and ${c.batterName} gets away with a stumping chance.",
                        "So close! The bails are off, but the bat is back behind the line. ${c.batterName} survives."
                    ))
                ) else null
            FieldingKind.RUN_OUT ->
                if (result == CatchOutcome.DROPPED) original.copy(
                    isWicket = false, dismissalType = null, runs = original.runs + 1, outBatsmanId = null, fieldingEvent = "missedRunOut",
                    commentary = line(random, listOf(
                        "${c.fielderName} misses the stumps, and ${c.batterName} is home safe. They get the run.",
                        "A good chance, but the throw is off line. ${c.batterName} makes it. Not out.",
                        "The bails stay on! ${c.batterName} was short, but ${c.fielderName} cannot take advantage."
                    ))
                ) else null
            FieldingKind.STOP ->
                if (result == CatchOutcome.TAKEN) original.copy(
                    runs = (original.runs - 1).coerceAtLeast(0), fieldingEvent = "stopped",
                    commentary = line(random, listOf(
                        "Stopped! ${c.fielderName} dives and cuts it off, a run saved.",
                        "Great fielding by ${c.fielderName}! He gets across and saves a run.",
                        "${c.fielderName} is quick to the ball and holds them to less."
                    ))
                ) else null
        }
    }

    private fun line(random: Random, options: List<String>): String = options[random.nextInt(options.size)]
}

package com.cricketsim.logic

import com.cricketsim.ui.match.BallResult
import kotlin.math.abs
import kotlin.random.Random

/** Where a catch is coming from, as the fielder sees it. The player swipes up, left or right with two fingers to match. */
enum class CatchDirection(val spoken: String, val swipe: String) {
    STRAIGHT("from straight on", "up"),
    LEFT("from the left", "left"),
    RIGHT("from the right", "right")
}

/** How the user's attempt at a catch ended. COMPUTER = he left it to the computer's fielder (which may drop it). */
enum class CatchOutcome { TAKEN, DROPPED, COMPUTER }

/** One catch chance for the user to take: who, where, how good he is, which way it comes from and the rhythm to time the swipe against. */
class CatchChance(
    val fielderId: String,
    val fielderName: String,
    val fielderRating: Int,
    /** "at long-on", "behind the stumps" or null (a catch back to the bowler). */
    val position: String?,
    val bowlerCatch: Boolean,
    val direction: CatchDirection,
    /** Buzzes before the ball arrives; the swipe belongs on the last one. */
    val pulses: Int,
    val intervalMs: Double,
    /** How far from the last buzz a swipe can be and still hold the catch, as a fraction of [intervalMs]. A better fielder has more room. */
    val window: Double
)

class CatchJudgement(val caught: Boolean, val reason: String)

/**
 * My Cricket, manual fielding, part one: taking the catches yourself.
 *
 * WHEN: the user's team is fielding, the ball is a caught dismissal (including a catch back to the bowler) and Settings > Manual fielding is not Automatic.
 * WHAT: the phone's own voice says who is catching, where he stands, his fielding rating and which way the catch comes from; then buzzes (and ticks) come at
 * the speed of the BALL (a fast ball gives short gaps, as in batting) shaped by the TIMING of the shot (a perfectly timed shot is hit hard and arrives
 * sooner, a mistimed one hangs in the air longer), and the user swipes up (straight), left or right with two fingers, on the last buzz.
 * A swipe the wrong way, too early, too late or none: the catch is dropped and the batter is not out. A better fielder forgives more ([window]).
 * [MANUAL_CATCHES] false switches the whole thing off.
 */
object ManualCatch {
    const val MANUAL_CATCHES = true
    private const val STRAIGHT_CONE_DEGREES = 25.0
    private const val SHORTEST_INTERVAL_MS = 150.0

    fun isCatch(o: BallOutcome): Boolean =
        o.isWicket && (o.dismissalType == DismissalType.CAUGHT || o.dismissalType == DismissalType.CAUGHT_AND_BOWLED)

    private fun directionFromGap(gapDegrees: Double): CatchDirection = when {
        abs(gapDegrees) <= STRAIGHT_CONE_DEGREES -> CatchDirection.STRAIGHT
        gapDegrees < 0 -> CatchDirection.LEFT
        else -> CatchDirection.RIGHT
    }

    /** The catch chance of this ball for the user's fielders, or null if it is not a catch (or no fielder can be found). */
    fun build(before: MatchState, result: BallResult): CatchChance? {
        if (!MANUAL_CATCHES) return null
        val o = result.outcome
        if (!isCatch(o)) return null
        val name = o.fielderName ?: return null
        val fielder = before.bowlingTeam.players.find { it.name == name } ?: return null
        val bowlerCatch = o.dismissalType == DismissalType.CAUGHT_AND_BOWLED
        val placement = before.fieldPlacements.find { it.playerId == fielder.id }
        val shotSector = BattingSystem.getShotDirection(result.battingDecision.shot, result.battingDecision.intent)?.sector
        val direction = when {
            bowlerCatch -> CatchDirection.STRAIGHT
            o.fielderPosition == "behind the stumps" -> CatchDirection.STRAIGHT
            placement != null && shotSector != null -> directionFromGap(FieldingSystem.sectorSignedGapDegrees(placement.sector, shotSector))
            placement != null -> if (placement.sector == FieldingSector.LEG_SLIP) CatchDirection.RIGHT else CatchDirection.LEFT
            else -> CatchDirection.STRAIGHT
        }
        val tier = result.battingDecision.timingTier
        val edge = o.isEdge == true
        val baseInterval = BattingSystem.computeTimingIntervalMs(result.bowlingDecision.speedKmh)
        val shotFactor = if (edge) 0.9 else when (tier) {
            BowlingQualityTier.PERFECT -> 0.85
            BowlingQualityTier.IDEAL -> 0.90
            BowlingQualityTier.GOOD -> 1.0
            BowlingQualityTier.BAD -> 1.1
            BowlingQualityTier.VERY_BAD -> 1.2
        }
        val interval = maxOf(SHORTEST_INTERVAL_MS, baseInterval * shotFactor)
        val deep = placement?.depth == FieldingDepth.DEEP
        val pulses = if (edge) 3 else when (tier) {
            BowlingQualityTier.PERFECT, BowlingQualityTier.IDEAL -> 3
            BowlingQualityTier.GOOD -> 4
            BowlingQualityTier.BAD, BowlingQualityTier.VERY_BAD -> 5
        } + (if (deep) 1 else 0)
        val rating = fielder.fieldingRating
        val window = (0.30 + 0.004 * rating).coerceIn(0.35, 0.72)
        return CatchChance(fielder.id, fielder.name, rating, o.fielderPosition, bowlerCatch, direction, pulses, interval, window)
    }

    /** What the phone's voice says before the buzzes. */
    fun announcement(c: CatchChance): String {
        val where = when {
            c.bowlerCatch -> "${c.fielderName} is the bowler."
            c.position != null -> "${c.fielderName} is standing ${c.position}."
            else -> "${c.fielderName} is waiting under it."
        }
        return "$where Fielding rating ${c.fielderRating}. The catch is coming ${c.direction.spoken}. " +
            "Swipe ${c.direction.swipe} with two fingers on the last buzz."
    }

    /** Was the swipe the right way and on the last buzz? [swiped] null = no swipe at all. */
    fun judge(c: CatchChance, swiped: IntentDirection?, actualMs: Double, expectedMs: Double): CatchJudgement {
        if (swiped == null) return CatchJudgement(false, "You did not swipe in time.")
        val way = when (swiped) {
            IntentDirection.UP -> CatchDirection.STRAIGHT
            IntentDirection.LEFT -> CatchDirection.LEFT
            IntentDirection.RIGHT -> CatchDirection.RIGHT
            IntentDirection.DOWN -> null
        }
        if (way != c.direction) return CatchJudgement(false, "Wrong way.")
        val off = abs(actualMs - expectedMs) / c.intervalMs
        if (off <= c.window) return CatchJudgement(true, "Caught.")
        return CatchJudgement(false, if (actualMs < expectedMs) "Too early." else "Too late.")
    }
}

/**
 * Dropped catches. A catch can be put down in two ways:
 *  - AUTOMATIC fielding (the computer's fielders, in every innings, also the user's own when he fields automatically): a caught dismissal is dropped
 *    with a chance that depends on the fielder's fielding rating ([dropChance]: about 6% for a 95, 21% for a 60, 34% for a 30), at most
 *    [MAX_DROPS_PER_INNINGS] in an innings, so an innings has none, one or two. [AUTO_DROPS] false switches it off.
 *  - MANUAL fielding: the user's swipe was wrong or badly timed.
 * A dropped catch is not a wicket: the batter stays, and the ball scores 0 or 1 (a catch near the bat) or 1 or 2 (a catch in the deep).
 */
object CatchDrops {
    const val AUTO_DROPS = true
    const val MAX_DROPS_PER_INNINGS = 2
    private const val DROP_BASE = 0.04
    private const val DROP_BY_RATING = 0.45

    fun dropChance(rating: Int): Double = DROP_BASE + DROP_BY_RATING * (1.0 - rating.coerceIn(0, 100) / 100.0)

    /** The computer's fielder puts this catch down: the ball as it then went, or null if he holds it. */
    fun autoDrop(before: MatchState, result: BallResult, random: Random = Random.Default): BallOutcome? {
        if (!AUTO_DROPS) return null
        val o = result.outcome
        if (!ManualCatch.isCatch(o)) return null
        if (before.ballByBall.count { it.droppedCatch } >= MAX_DROPS_PER_INNINGS) return null
        val fielder = before.bowlingTeam.players.find { it.name == o.fielderName } ?: return null
        if (random.nextDouble() >= dropChance(fielder.fieldingRating)) return null
        return droppedOutcome(before, o, fielder, random)
    }

    /** The same ball as [original], but the catch goes down. */
    fun droppedOutcome(before: MatchState, original: BallOutcome, fielder: Player, random: Random = Random.Default): BallOutcome {
        val deep = before.fieldPlacements.find { it.playerId == fielder.id }?.depth == FieldingDepth.DEEP
        val runs = if (deep) 1 + random.nextInt(2) else random.nextInt(2)
        val batter = before.currentBatsmen.toList().firstOrNull { it.id == original.batsmanId }?.name ?: before.currentBatsmen.first.name
        val where = when {
            original.dismissalType == DismissalType.CAUGHT_AND_BOWLED -> "off his own bowling"
            original.fielderPosition != null -> original.fielderPosition
            else -> "in the field"
        }
        val line = dropLine(batter, fielder.name, where, random) + if (runs > 0) " They run ${if (runs == 1) "one" else "two"}." else ""
        return original.copy(
            isWicket = false, dismissalType = null, runs = runs, outBatsmanId = null, droppedCatch = true,
            fielderName = fielder.name, fielderPosition = original.fielderPosition, commentary = line
        )
    }

    private fun dropLine(batter: String, fielder: String, where: String, random: Random): String {
        val lines = listOf(
            "Dropped! $fielder spills a simple chance $where, and $batter survives.",
            "Oh no, it goes down! $fielder puts the catch down $where. A life for $batter.",
            "Dropped catch! The ball bursts through $fielder's hands $where.",
            "$fielder gets both hands to it $where but cannot hold on. $batter is mighty relieved.",
            "That has gone down! $fielder should have taken that $where.",
            "A chance goes begging! $fielder cannot hang on $where, and $batter stays."
        )
        return lines[random.nextInt(lines.size)]
    }
}

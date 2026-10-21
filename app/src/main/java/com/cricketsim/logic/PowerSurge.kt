package com.cricketsim.logic

import kotlin.random.Random

/**
 * The Big Bash League's POWER SURGE. [MatchState.surge] is null in every other match.
 *
 * The rule as the league announced it: a two-over period in which the fielding side may have only two
 * fielders outside the inner circle (the same limit as the powerplay, which the Big Bash shortens to four
 * overs). The BATTING side chooses when, at the start of any over from the 11th. The game's own powerplay
 * limit is "at most three in the deep" (FieldingSystem.MAX_DEEP_FIELDERS_POWERPLAY), so that is the limit used.
 *
 * Assumption (the rule as published has no deadline): a Power Surge nobody has called starts by itself at
 * the start of the 19th over, so the two overs always fit.
 *
 * Who decides: the user, from Match info (like the Impact Player), while he is batting; the computer, by
 * [aiShouldTake], at the start of every over from the 11th while it is batting. The match screen shows the
 * user a pop-up when the computer takes it, because it changes how he must bowl.
 */
data class SurgeState(
    /** Which innings the surge was taken in (0 = none yet). It can be taken once an innings. */
    val innings: Int = 0,
    /** Balls bowled in that innings when it began; it covers the next [PowerSurge.SURGE_BALLS]. */
    val startBall: Int = -1
)

object PowerSurge {

    const val SURGE_BALLS = 12
    /** The Big Bash powerplay: four overs. */
    const val BBL_POWERPLAY_BALLS = 24
    private const val FIRST_OVER_COMPLETED = 10 // the start of the 11th over
    private const val LATEST_START_OVER = 18 // the start of the 19th over

    fun ballsBowled(s: MatchState): Int = s.score.overs * 6 + s.score.balls

    fun takenThisInnings(s: MatchState): Boolean {
        val z = s.surge ?: return false
        return z.innings == s.currentInnings && z.startBall >= 0
    }

    /** Whether the delivery with this many balls already bowled is inside a Power Surge taken in this innings. */
    fun isActiveAt(s: MatchState, balls: Int): Boolean {
        val z = s.surge ?: return false
        return z.innings == s.currentInnings && z.startBall >= 0 && balls >= z.startBall && balls < z.startBall + SURGE_BALLS
    }

    /** The only moments it can be called: the start of an over, the 11th onwards, once an innings, play not held up. */
    fun canTakeNow(s: MatchState): Boolean =
        s.surge != null &&
            s.matchStatus == MatchStatus.IN_PROGRESS &&
            s.superOverRound == 0 &&
            s.score.balls == 0 &&
            s.score.overs >= FIRST_OVER_COMPLETED &&
            s.score.overs + 2 <= s.oversLimit &&
            !takenThisInnings(s) &&
            s.activeRainDelay == null &&
            s.pendingDismissal == null &&
            !s.needsBowlerSelection &&
            !s.needsOpenerSelection

    /** An uncalled Power Surge begins at the start of the 19th over (or the last two overs of a shortened match). */
    fun mustTakeNow(s: MatchState): Boolean =
        canTakeNow(s) && s.score.overs >= minOf(LATEST_START_OVER, s.oversLimit - 2)

    /**
     * Starts the Power Surge now. When the computer is bowling it brings its field in at once; the user's own
     * field is checked against the limit before his next ball (the match screen already does that).
     */
    fun take(s: MatchState): MatchState {
        val next = s.copy(surge = SurgeState(s.currentInnings, ballsBowled(s)))
        if (s.bowlingTeam.id == s.userTeam.id) return next
        val field = FieldingSystem.generateAiFieldPlacements(
            FieldingSystem.getFieldingPlayers(s.bowlingTeam, s.currentBowler.id),
            true
        )
        return next.copy(fieldPlacements = field)
    }

    /**
     * The computer batting side's choice, asked at the start of each over from the 11th. A chance that grows
     * with the overs, higher with wickets in hand, much higher when the chase is getting away, and certain at
     * the 19th (see [mustTakeNow]).
     */
    fun aiShouldTake(s: MatchState, random: Random = Random.Default): Boolean {
        if (s.battingTeam.id == s.userTeam.id || !canTakeNow(s)) return false
        val over = s.score.overs
        var chance = when {
            over < 12 -> 0.04
            over < 14 -> 0.12
            over < 16 -> 0.30
            else -> 0.55
        }
        if (s.score.wickets <= 2) chance *= 1.4
        if (s.score.wickets >= 5) chance *= 0.4
        val target = s.target
        if (s.currentInnings == 2 && target != null) {
            val ballsLeft = maxOf(1, s.oversLimit * 6 - ballsBowled(s))
            val required = (target - s.score.runs) * 6.0 / ballsLeft
            if (required > 10.5) chance = maxOf(chance, 0.5)
        }
        return random.nextDouble() < chance.coerceAtMost(0.95)
    }

    /** What is said (and shown, when the computer takes it) as the Power Surge begins. */
    fun announcement(s: MatchState, userBatting: Boolean, automatic: Boolean): String {
        val batting = s.battingTeam.name
        return when {
            userBatting && !automatic ->
                "Power Surge taken. For the next two overs the fielding side may have at most three fielders in the deep."
            userBatting ->
                "The Power Surge starts now. You did not call it, so it begins by itself. For the next two overs the " +
                    "fielding side may have at most three fielders in the deep."
            automatic ->
                "The Power Surge starts now. $batting did not call it, so it begins by itself. For the next two overs you " +
                    "may have at most three fielders in the deep. Check your field before you bowl."
            else ->
                "Power Surge! $batting have taken it. For the next two overs you may have at most three fielders in the " +
                    "deep. Check your field before you bowl."
        }
    }
}

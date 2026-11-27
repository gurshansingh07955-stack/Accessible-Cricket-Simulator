package com.cricketsim.logic

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The win chance shown on the match screen (patch 153): in BOTH innings of a limited-overs match, and moving a little with every ball.
 *
 * It is a smooth estimate, not a table of bands:
 *  - A "par" total for the format and the ground (about 8.3 runs an over in a T20 over a ground whose average is 160, more in a T10, less in a 50-over match).
 *  - Resources left: the balls still to come, scaled by how many wickets are in hand compared with what a normal innings would have lost by now.
 *  - First innings: the total he is heading for (runs now plus what the resources left are worth) against par, with the uncertainty of the rest of the innings and of
 *    the chase added; 50 percent at the start of the innings.
 *  - The chase: the runs the resources left are worth against the runs still needed, with the uncertainty of the balls left.
 * Every ball changes the runs, the balls or the wickets, so every ball changes the figure: a dot ball a little down, a boundary up, a wicket down by a good deal.
 * It does NOT know how strong the two teams are, only the state of the match. [MatchEngine.calculateWinProbability] (coarse, chase only) is still what drives the crowd's tension.
 * A Test shows a figure only for the last innings of a chase (that older estimate); a multi-day match is not worth guessing.
 */
object WinProbability {
    /** False hides the figure in the first innings again (the old behaviour). */
    const val FIRST_INNINGS = true

    /** Runs an over expected in a match of this many overs (T10 about 9.5, T20 about 8.3, 50 overs about 5.6). */
    private fun runRate(overs: Int): Double = when {
        overs <= 10 -> 9.5
        overs <= 20 -> 9.5 - (overs - 10) * 0.12
        overs < 50 -> 8.3 - (overs - 20) * 0.09
        else -> 5.6
    }

    /** A smooth version of the normal curve: 0.5 at 0, and about 0.93 at 1.5 standard deviations. */
    private fun phi(x: Double): Double = 1.0 / (1.0 + exp(-1.702 * x))

    private fun par(state: MatchState): Double {
        val overs = state.oversLimit
        val base = runRate(overs) * overs
        val average = StadiumData.STADIUMS.firstOrNull { it.id == state.stadiumId }?.avgT20FirstInningsScore
        // The ground's T20 average says how high-scoring it is (160 is the norm).
        val ground = if (average != null) (average / 160.0).coerceIn(0.8, 1.25) else 1.0
        return base * ground
    }

    /** What the wickets in hand are worth, against the wickets a normal innings would have lost by now (6.5 of 10 by the end). */
    private fun wicketFactor(wicketsLeft: Int, elapsed: Double, maxWickets: Int): Double {
        val expected = maxWickets - 0.65 * maxWickets * elapsed
        return (wicketsLeft / expected).pow(0.6).coerceIn(0.15, 1.6)
    }

    private fun percent(p: Double): Int = (p * 100.0).roundToInt().coerceIn(1, 99)

    /** The batting team's chance of winning, from 1 to 99 (0 and 100 when the match is decided), or null where no figure is shown. */
    fun percentForBatting(state: MatchState): Int? {
        if (state.test != null) {
            val lastInnings = state.target != null && state.currentInnings == TestMatch.inningsCount(state)
            return if (lastInnings) MatchEngine.calculateWinProbability(state) else null
        }
        val total = state.oversLimit * 6
        if (total <= 0) return null
        val maxWickets = (state.battingTeam.players.size - 1).coerceAtLeast(1)
        val wicketsLeft = maxWickets - state.score.wickets
        val bowled = state.score.overs * 6 + state.score.balls
        val left = (total - bowled).coerceAtLeast(0)
        val elapsed = bowled.toDouble() / total
        val par = par(state)
        val target = state.target
        if (target == null || state.currentInnings == 1) {
            if (!FIRST_INNINGS) return null
            val perBall = par / total
            val projected = state.score.runs + perBall * left * wicketFactor(wicketsLeft, elapsed, maxWickets)
            val spread = 0.17 * par
            val spreadLeft = spread * sqrt(left.toDouble() / total)
            return percent(phi((projected - par) / sqrt(spread * spread + spreadLeft * spreadLeft)))
        }
        val needed = target - state.score.runs
        if (needed <= 0) return 100
        if (left <= 0 || wicketsLeft <= 0) return 0
        // A high first innings says the ground is playing high: the chasing side's par goes up with it (and down if it was low).
        val chasePar = par * sqrt(target / par).coerceIn(0.7, 1.4)
        val expected = chasePar / total * left * wicketFactor(wicketsLeft, elapsed, maxWickets)
        val spread = maxOf(3.0, 0.17 * chasePar * sqrt(left.toDouble() / total))
        return percent(phi((expected - needed + 0.5) / spread))
    }
}

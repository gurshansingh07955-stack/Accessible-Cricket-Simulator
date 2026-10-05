package com.cricketsim.logic

/**
 * What the drinks-break screen shows: where the match stands, and how the last stretch of
 * overs went for each side.
 */
data class DrinksBreakInfo(
    /** The over the break comes after (10, 25, 15, 30...). */
    val atOver: Int,
    /** How many overs the "last N overs" figures cover. */
    val windowOvers: Int,
    val battingTeamName: String,
    val bowlingTeamName: String,
    val scoreRuns: Int,
    val scoreWickets: Int,
    val runsInWindow: Int,
    val wicketsInWindow: Int
)

/**
 * Drinks breaks, in the formats that have them:
 *  - T20: one per innings, after 10 overs.
 *  - ODI: one per innings, after 25 overs.
 *  - Test: one every 15 overs (15, 30, 45...), as a day's play has several.
 * T10, five-over and one-over matches are too short and have none.
 *
 * The check is made when an over ends (MatchSimulation.endOfOver). The break itself is just
 * `MatchState.drinksBreak` being set; the match screen shows it until the player taps Resume.
 */
object DrinksBreak {

    const val TITLE = "Drinks break."

    fun intervalFor(format: MatchFormat): Int? = when (format) {
        MatchFormat.T20 -> 10
        MatchFormat.ODI -> 25
        MatchFormat.TEST -> 15
        else -> null
    }

    /** Starts a drinks break if one is due after the over that has just finished; otherwise returns the state unchanged. */
    fun startIfDue(state: MatchState): MatchState {
        if (state.activeRainDelay != null) return state
        val interval = intervalFor(state.format) ?: return state
        val overs = state.score.overs
        if (overs <= 0 || overs <= state.lastDrinksBreakOver) return state
        // No break once the innings is at its limit.
        if (overs >= state.oversLimit) return state
        val due = if (state.format == MatchFormat.TEST) overs % interval == 0 else overs == interval
        if (!due) return state
        return state.copy(lastDrinksBreakOver = overs, drinksBreak = summarise(state, interval))
    }

    /** Runs scored and wickets fallen in the last [window] completed overs of this innings. */
    private fun summarise(state: MatchState, window: Int): DrinksBreakInfo {
        val startOver = (state.score.overs - window).coerceAtLeast(0)
        var legalBalls = 0
        var runs = 0
        var wickets = 0
        for (ball in state.ballByBall) {
            val overIndex = legalBalls / 6
            if (overIndex >= startOver) {
                runs += ball.runs + ball.extraRuns
                if (ball.isWicket) wickets++
            }
            if (!ball.isWide && !ball.isNoBall) legalBalls++
        }
        return DrinksBreakInfo(
            atOver = state.score.overs,
            windowOvers = window,
            battingTeamName = state.battingTeam.name,
            bowlingTeamName = state.bowlingTeam.name,
            scoreRuns = state.score.runs,
            scoreWickets = state.score.wickets,
            runsInWindow = runs,
            wicketsInWindow = wickets
        )
    }

    private fun plural(count: Int, one: String, many: String = one + "s"): String =
        if (count == 1) "$count $one" else "$count $many"

    /** The lines the screen shows, one sentence each. */
    fun lines(info: DrinksBreakInfo): List<String> = listOf(
        "After ${plural(info.atOver, "over")}, ${info.battingTeamName} are ${info.scoreRuns} for ${info.scoreWickets}.",
        "In the last ${plural(info.windowOvers, "over")}, ${info.battingTeamName} scored ${plural(info.runsInWindow, "run")} " +
            "and ${info.bowlingTeamName} took ${plural(info.wicketsInWindow, "wicket")}."
    )

    /** One spoken announcement for a screen reader. */
    fun spokenLine(info: DrinksBreakInfo): String = TITLE + " " + lines(info).joinToString(" ")
}

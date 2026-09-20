package com.cricketsim.ui.match

import com.cricketsim.logic.BatsmanStats
import com.cricketsim.logic.DismissalType
import com.cricketsim.logic.InningsData
import com.cricketsim.logic.MatchState
import java.util.Locale

/**
 * Every line of spoken match information, as plain strings, kept out of
 * the composables so the wording lives in one place and can be tested
 * without Compose. Wording follows the web app's match screen
 * (pages/match.tsx) and MatchScorecard, which were tuned with a real
 * screen-reader user — one short, self-contained sentence per line so
 * each is a single swipe stop.
 *
 * Win probability is deliberately NOT here: the web never displays or
 * speaks it (it only drives the crowd-tension audio), so it belongs with
 * the audio layer, not the text lines.
 */

private fun countOf(n: Int, singular: String, plural: String = singular + "s"): String =
    "$n ${if (n == 1) singular else plural}"

private fun formatDecimal(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

object MatchLines {

    fun strikerLine(state: MatchState): String {
        val striker = state.currentBatsmen.first
        val stats = state.currentInningsData.batsmanStats.firstOrNull { it.playerId == striker.id }
        return "Striker: ${striker.name}, ${countOf(stats?.runs ?: 0, "run")} off ${countOf(stats?.ballsFaced ?: 0, "ball")}."
    }

    fun nonStrikerLine(state: MatchState): String {
        val nonStriker = state.currentBatsmen.second
        val stats = state.currentInningsData.batsmanStats.firstOrNull { it.playerId == nonStriker.id }
        return "Non-striker: ${nonStriker.name}, ${countOf(stats?.runs ?: 0, "run")} off ${countOf(stats?.ballsFaced ?: 0, "ball")}."
    }

    fun bowlerLine(state: MatchState): String {
        val bowler = state.currentBowler
        val stats = state.currentInningsData.bowlerStats.firstOrNull { it.playerId == bowler.id }
        val overs = "${stats?.overs ?: 0}.${stats?.balls ?: 0} overs"
        return "Bowler: ${bowler.name}, $overs, ${countOf(stats?.runsConceded ?: 0, "run")}, ${countOf(stats?.wickets ?: 0, "wicket")}."
    }

    fun scoreLine(state: MatchState): String =
        "Score: ${state.score.runs} for ${state.score.wickets} in ${state.score.overs}.${state.score.balls} overs."

    fun currentRunRateLine(state: MatchState): String {
        val oversBowled = state.score.overs + state.score.balls / 6.0
        val rate = if (oversBowled > 0) state.score.runs / oversBowled else 0.0
        return "Current run rate: ${formatDecimal(rate, 2)} runs per over."
    }

    private data class Chase(val runsNeeded: Int, val ballsRemaining: Int, val requiredRunRate: Double)

    /**
     * Second innings only. Reads `state.oversLimit` (not a fixed
     * format-based total) so a rain-reduced match immediately shows the
     * revised requirement instead of one measured against the original
     * over count — the same reason the web does.
     */
    private fun chase(state: MatchState): Chase? {
        val target = state.target ?: return null
        if (state.currentInnings != 2) return null
        val runsNeeded = maxOf(target - state.score.runs, 0)
        val ballsBowled = state.score.overs * 6 + state.score.balls
        val ballsRemaining = maxOf(state.oversLimit * 6 - ballsBowled, 0)
        val oversRemaining = ballsRemaining / 6.0
        val requiredRunRate = if (oversRemaining > 0) runsNeeded / oversRemaining else 0.0
        return Chase(runsNeeded, ballsRemaining, requiredRunRate)
    }

    fun runsNeededLine(state: MatchState): String? =
        chase(state)?.let { "${countOf(it.runsNeeded, "run")} needed off ${countOf(it.ballsRemaining, "ball")}." }

    fun requiredRunRateLine(state: MatchState): String? =
        chase(state)?.let { "Required run rate: ${formatDecimal(it.requiredRunRate, 2)} runs per over." }

    fun targetLine(state: MatchState): String? {
        val target = state.target ?: return null
        return if (state.dlsRevised) "Target: $target (revised for rain)." else "Target: $target."
    }
}

/**
 * Scorecard rows, one self-contained sentence each. Two gaps inherited
 * from the logic layer (both also true on the web) are deliberately
 * NOT papered over here:
 * - MatchStats never detects maidens (it passes `false`), so bowling
 *   rows omit maidens rather than announce a wrong "0 maidens".
 * - MatchStats doesn't record the team score when a wicket falls, and
 *   batsmanStats is in batting order, not dismissal order. The wickets
 *   list therefore says how many runs the batsman made and who got
 *   him, in batting order, with no team score and no "wicket number".
 */
object ScorecardLines {

    fun inningsHeader(innings: InningsData): String =
        "${innings.battingTeamName}, ${innings.totalRuns} for ${innings.totalWickets} in ${innings.totalOvers}.${innings.totalBalls} overs."

    private fun dismissalText(stats: BatsmanStats): String {
        val by = stats.dismissedBy
        return when (stats.dismissalType) {
            DismissalType.BOWLED -> if (by != null) "bowled by $by" else "bowled"
            DismissalType.LBW -> if (by != null) "lbw, bowled by $by" else "lbw"
            DismissalType.CAUGHT -> if (by != null) "caught, bowler $by" else "caught"
            else -> "out"
        }
    }

    fun battingRows(innings: InningsData): List<String> {
        val batters = innings.batsmanStats.map { stats ->
            val status = if (stats.isOut) dismissalText(stats) else "not out"
            "${stats.playerName}, $status, ${countOf(stats.runs, "run")} off ${countOf(stats.ballsFaced, "ball")}, " +
                "${countOf(stats.fours, "four")}, ${countOf(stats.sixes, "six", "sixes")}, " +
                "strike rate ${formatDecimal(stats.strikeRate, 1)}."
        }
        val extras = innings.extras
        val extrasRow = "Extras: ${extras.total}, ${countOf(extras.wides, "wide")}, ${countOf(extras.noBalls, "no ball")}."
        val totalRow = "Total: ${innings.totalRuns} for ${innings.totalWickets} in ${innings.totalOvers}.${innings.totalBalls} overs."
        return batters + extrasRow + totalRow
    }

    fun bowlingRows(innings: InningsData, currentBowlerId: String?): List<String> =
        innings.bowlerStats.map { stats ->
            val marker = if (stats.playerId == currentBowlerId) " (currently bowling)" else ""
            "${stats.playerName}$marker, ${stats.overs}.${stats.balls} overs, ${countOf(stats.runsConceded, "run")}, " +
                "${countOf(stats.wickets, "wicket")}, economy ${formatDecimal(stats.economy, 2)}, " +
                "${countOf(stats.wides, "wide")}, ${countOf(stats.noBalls, "no ball")}."
        }

    fun partnershipRows(innings: InningsData): List<String> {
        fun describe(p: com.cricketsim.logic.Partnership): String =
            "${p.batsman1Name} ${p.batsman1Runs} and ${p.batsman2Name} ${p.batsman2Runs}, " +
                "${countOf(p.totalRuns, "run")} off ${countOf(p.balls, "ball")}."

        // The live partnership first: it's the one that matters mid-match,
        // and it saves a TalkBack user swiping past every finished one.
        val current = "Current partnership: ${describe(innings.currentPartnership)}"
        val finished = innings.partnerships.mapIndexed { index, p -> "Partnership ${index + 1}: ${describe(p)}" }
        return listOf(current) + finished
    }

    fun wicketRows(innings: InningsData): List<String> {
        val out = innings.batsmanStats.filter { it.isOut }
        if (out.isEmpty()) return listOf("No wickets have fallen yet.")
        return out.map { stats -> "${stats.playerName}, ${countOf(stats.runs, "run")}, ${dismissalText(stats)}." }
    }
}

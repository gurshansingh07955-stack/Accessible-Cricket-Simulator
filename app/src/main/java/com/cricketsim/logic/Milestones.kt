package com.cricketsim.logic

/**
 * MILESTONES (patch 139). After a ball in a match the user plays, was it a milestone? If so, the match screen shows a pop-up (MilestoneScreen)
 * with the figures, the crowd applauds, and he presses OK to carry on.
 *
 * Batting: a 50, a 100, then every 50 to 400, with the balls it took, the fours and sixes and the strike rate at that moment.
 * Bowling: the third wicket of an innings and every wicket after it up to the tenth, with his figures (overs, runs, wickets, economy),
 * and a hat-trick (three wickets in three of his balls), a double hat-trick (four in four) and a triple hat-trick (five in five). A wide or a
 * no ball is not one of his balls, so it does not break the run; a run out is not his wicket. A wicket that is also a hat-trick is one pop-up.
 *
 * WHO: in a career match (`MatchState.careerPlayerId`) only the player's own player; in every other match the user plays (a friendly, a tournament,
 * the IPL, a custom tournament, a net-free match of any kind) every batter and bowler of both sides, the opposition's too, as the crowd would
 * applaud them. Matches the computer plays by itself (a simulated tournament match, Simulate match) show nothing. Settings > Milestone pop-ups switches
 * the pop-ups off; [MILESTONES_ENABLED] switches the whole feature off.
 */
object Milestones {
    const val MILESTONES_ENABLED = true

    class Milestone(val title: String, val lines: List<String>) {
        /** The words spoken as the pop-up appears. */
        val spoken: String get() = title + " " + lines.joinToString(" ")
    }

    private val BAT_STEPS = listOf(50, 100, 150, 200, 250, 300, 350, 400)
    private const val FIRST_HAUL = 3
    private const val LAST_HAUL = 10

    /** The milestones of the ball that took [before] to [after] (the same innings), in the order they should be shown. */
    fun detect(before: MatchState, after: MatchState): List<Milestone> {
        if (!MILESTONES_ENABLED) return emptyList()
        val found = ArrayList<Milestone>()
        val pid = after.careerPlayerId
        if (pid != null) {
            // A career match: only his own player.
            batting(before, after, pid)?.let { found.add(it) }
            bowling(before, after, pid)?.let { found.add(it) }
            return found
        }
        // Every other match: any batter whose score crossed a step, and the bowler of the ball if it was a wicket that earns one.
        after.currentInningsData.batsmanStats.forEach { stats -> batting(before, after, stats.playerId)?.let { found.add(it) } }
        val bowlerId = after.ballByBall.lastOrNull()?.bowlerId
        if (bowlerId != null) bowling(before, after, bowlerId)?.let { found.add(it) }
        return found
    }

    private fun plural(n: Int, one: String, many: String): String = if (n == 1) "$n $one" else "$n $many"

    private fun batting(before: MatchState, after: MatchState, pid: String): Milestone? {
        val now = after.currentInningsData.batsmanStats.firstOrNull { it.playerId == pid } ?: return null
        val was = before.currentInningsData.batsmanStats.firstOrNull { it.playerId == pid }?.runs ?: 0
        val step = BAT_STEPS.lastOrNull { was < it && now.runs >= it } ?: return null
        val title = when (step) {
            50 -> "Fifty!"
            100 -> "Century!"
            150 -> "One hundred and fifty!"
            200 -> "Double century!"
            250 -> "Two hundred and fifty!"
            300 -> "Triple century!"
            350 -> "Three hundred and fifty!"
            else -> "Four hundred!"
        }
        val strikeRate = if (now.ballsFaced > 0) now.runs * 100.0 / now.ballsFaced else 0.0
        return Milestone(
            title,
            listOf(
                "${now.playerName} reaches $step in ${plural(now.ballsFaced, "ball", "balls")}.",
                "${plural(now.fours, "four", "fours")} and ${plural(now.sixes, "six", "sixes")}.",
                "Strike rate ${"%.1f".format(strikeRate)}."
            )
        )
    }

    /** A wicket the bowler is credited with (a run out is not). */
    private fun credited(o: BallOutcome): Boolean = o.isWicket && o.dismissalType != null && o.dismissalType != DismissalType.RUN_OUT

    private fun oversText(overs: Int, balls: Int): String =
        if (balls == 0) (if (overs == 1) "1 over" else "$overs overs") else "$overs.$balls overs"

    private fun haulTitle(n: Int): String = when (n) {
        3 -> "Three wickets!"
        4 -> "Four wickets!"
        5 -> "Five wickets! A five-wicket haul!"
        6 -> "Six wickets!"
        7 -> "Seven wickets!"
        8 -> "Eight wickets!"
        9 -> "Nine wickets!"
        else -> "All ten wickets!"
    }

    private fun bowling(before: MatchState, after: MatchState, pid: String): Milestone? {
        if (after.ballByBall.size <= before.ballByBall.size) return null
        val last = after.ballByBall.last()
        if (last.bowlerId != pid || !credited(last)) return null
        val wickets = after.ballByBall.count { it.bowlerId == pid && credited(it) }

        // His own balls, wides and no balls left out: how many of the latest are wickets?
        val mine = after.ballByBall.filter { it.bowlerId == pid && !it.isWide && !it.isNoBall }
        var streak = 0
        for (i in mine.indices.reversed()) {
            if (credited(mine[i])) streak++ else break
        }
        val hatTitle = when (streak) {
            3 -> "Hat-trick!"
            4 -> "Double hat-trick!"
            5 -> "Triple hat-trick!"
            else -> null
        }
        val haul = wickets in FIRST_HAUL..LAST_HAUL
        if (hatTitle == null && !haul) return null

        val stats = after.currentInningsData.bowlerStats.firstOrNull { it.playerId == pid }
        val name = stats?.playerName ?: after.currentBowler.name
        val figures = if (stats != null) {
            val legal = stats.overs * 6 + stats.balls
            val economy = if (legal > 0) stats.runsConceded * 6.0 / legal else 0.0
            listOf(
                "${oversText(stats.overs, stats.balls)}, ${plural(stats.runsConceded, "run", "runs")}, ${plural(wickets, "wicket", "wickets")}.",
                "Economy ${"%.2f".format(economy)}."
            )
        } else {
            listOf("${plural(wickets, "wicket", "wickets")}.")
        }
        return if (hatTitle != null) {
            Milestone(hatTitle, listOf("$name takes ${streak} wickets in ${streak} balls.") + figures)
        } else {
            Milestone(haulTitle(wickets), listOf("$name has taken ${plural(wickets, "wicket", "wickets")}.") + figures)
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // THE FASTEST 50, 100, 150 AND 200 (patch 141)
    // -----------------------------------------------------------------------------------------------------------------

    val FASTEST_STEPS = listOf(50, 100, 150, 200)

    /**
     * The batters who, on the ball that took [before] to [after], reached 50, 100, 150 or 200, with the balls they had faced by then. A match keeps them
     * (`MatchState.fastestMarks`) while it is played, because a scorecard does not say on which ball a score was reached. In a career match only the career
     * player's; in every other match every batter's. A ball the computer plays for the user ("Simulate") is never seen, so it is never counted.
     */
    fun fastestMarks(before: MatchState, after: MatchState): List<FastestMark> {
        val pid = after.careerPlayerId
        val batters = if (pid != null) after.currentInningsData.batsmanStats.filter { it.playerId == pid } else after.currentInningsData.batsmanStats
        val marks = ArrayList<FastestMark>()
        for (now in batters) {
            val was = before.currentInningsData.batsmanStats.firstOrNull { it.playerId == now.playerId }?.runs ?: 0
            for (step in FASTEST_STEPS) {
                if (was < step && now.runs >= step) {
                    marks.add(FastestMark(step, now.ballsFaced, now.playerName, after.battingTeam.name, after.bowlingTeam.name))
                }
            }
        }
        return marks
    }
}

/** The balls a batter took to reach 50, 100, 150 or 200 in a match (patch 141). */
data class FastestMark(
    val step: Int,
    val balls: Int,
    val playerName: String,
    val teamName: String,
    val against: String
)

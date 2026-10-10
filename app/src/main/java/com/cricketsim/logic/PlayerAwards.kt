package com.cricketsim.logic

/** One player's contribution to a finished match, for the Player of the Match award. */
data class MatchAward(
    val playerId: String,
    val name: String,
    val teamId: String,
    val teamName: String,
    val points: Double,
    /** "78 off 45, 2 for 21 in 4.0 overs and 1 catch", only the parts that apply. */
    val summary: String
)

/**
 * Player of the Match, for EVERY match in the game (quick matches, tournament matches you play,
 * and tournament matches that are simulated), and the impact points the tournament adds up for
 * Player of the Series.
 *
 * IMPACT is one number for a player's whole match, so a batter, a bowler and an all-rounder can be
 * compared:
 *  - BATTING: runs, plus 1 for each four and 2 for each six, plus a bonus or penalty for the
 *    strike rate against 130 (only once he has faced eight balls); +8 for a fifty, +20 for a hundred.
 *  - BOWLING: 25 for each wicket, 8 for each maiden, and for the economy against 8 an over (only
 *    once he has bowled two overs); +8 for three wickets, +20 for five.
 *  - FIELDING: 8 for each catch (including caught and bowled) and each run out.
 *  - WINNING: +12 for everyone on the side that won, so the award leans to the winners, as it
 *    does in real cricket, unless a loser played something special.
 * The highest total wins; a tie goes to the one with more runs.
 */
object PlayerAwards {

    private const val WINNER_BONUS = 12.0

    private class Acc {
        var points = 0.0
        var runs = 0
        var balls = 0
        var notOut = false
        var batted = false
        var wickets = 0
        var runsConceded = 0
        var ballsBowled = 0
        var catches = 0
    }

    /** Everyone who played, best first. */
    fun impacts(state: MatchState): List<MatchAward> {
        val innings = listOfNotNull(state.firstInningsData, state.currentInningsData)
        val players = (state.userTeam.players + state.opponentTeam.players).associateBy { it.id }
        val teamNames = HashMap<String, String>()
        innings.forEach {
            teamNames[it.battingTeamId] = it.battingTeamName
            teamNames[it.bowlingTeamId] = it.bowlingTeamName
        }
        val acc = LinkedHashMap<String, Acc>()
        fun get(id: String): Acc = acc.getOrPut(id) { Acc() }

        for (inn in innings) {
            val fielders = players.values.filter { it.teamId == inn.bowlingTeamId }
            for (b in inn.batsmanStats) {
                val a = get(b.playerId)
                a.batted = true
                a.runs += b.runs
                a.balls += b.ballsFaced
                a.notOut = !b.isOut
                a.points += b.runs + b.fours + 2.0 * b.sixes
                if (b.ballsFaced >= 8) {
                    val strikeRate = b.runs * 100.0 / b.ballsFaced
                    a.points += b.runs * ((strikeRate - 130.0) / 130.0) * 0.5
                }
                if (b.runs >= 100) a.points += 20.0 else if (b.runs >= 50) a.points += 8.0
                if (b.isOut) {
                    val fielderName = when (b.dismissalType) {
                        DismissalType.CAUGHT -> b.caughtBy
                        DismissalType.CAUGHT_AND_BOWLED, DismissalType.RUN_OUT -> b.dismissedBy
                        else -> null
                    }
                    val fielder = fielders.firstOrNull { it.name == fielderName }
                    if (fielder != null) {
                        val f = get(fielder.id)
                        f.catches += 1
                        f.points += 8.0
                    }
                }
            }
            for (w in inn.bowlerStats) {
                val a = get(w.playerId)
                val balls = w.overs * 6 + w.balls
                a.wickets += w.wickets
                a.runsConceded += w.runsConceded
                a.ballsBowled += balls
                a.points += w.wickets * 25.0 + w.maidens * 8.0
                if (balls >= 12) {
                    val economy = w.runsConceded * 6.0 / balls
                    a.points += (balls / 6.0) * (8.0 - economy) * 1.5
                }
                if (w.wickets >= 5) a.points += 20.0 else if (w.wickets >= 3) a.points += 8.0
            }
        }

        val winner = state.winnerId
        val result = ArrayList<MatchAward>()
        for ((id, a) in acc) {
            val player = players[id] ?: continue
            if (winner != null && player.teamId == winner) a.points += WINNER_BONUS
            val parts = ArrayList<String>()
            if (a.batted && a.balls > 0) parts.add("${a.runs}${if (a.notOut) " not out" else ""} off ${a.balls}")
            if (a.ballsBowled > 0) parts.add("${a.wickets} for ${a.runsConceded} in ${a.ballsBowled / 6}.${a.ballsBowled % 6} overs")
            if (a.catches > 0) parts.add(if (a.catches == 1) "1 catch" else "${a.catches} catches")
            val summary = when (parts.size) {
                0 -> ""
                1 -> parts[0]
                else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
            }
            result.add(
                MatchAward(
                    playerId = id, name = player.name, teamId = player.teamId,
                    teamName = teamNames[player.teamId] ?: "", points = a.points, summary = summary
                )
            )
        }
        return result.sortedWith(
            compareByDescending<MatchAward> { it.points }.thenByDescending { acc[it.playerId]?.runs ?: 0 }
        )
    }

    /** The Player of the Match, or null if nobody played (a match with no balls). */
    fun playerOfTheMatch(state: MatchState): MatchAward? = impacts(state).firstOrNull()
}

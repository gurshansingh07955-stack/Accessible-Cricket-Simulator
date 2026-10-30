package com.cricketsim.logic

/** One innings of a first-class match, kept on a fixture's result. */
data class FcInnings(
    val teamId: String,
    val runs: Int,
    val wickets: Int,
    val balls: Int,
    val declared: Boolean = false
)

/** One line of a Ranji Trophy group table. */
class FcRow(
    val team: Team,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val tied: Int,
    val points: Int,
    val quotient: Double
)

/**
 * The points and tables of the Ranji Trophy (and the rest of first-class cricket in India), from the BCCI's rules:
 * an outright win is 6 points, 7 when it is by an innings or by ten wickets; a tie is 3 each; in a draw the side ahead on the first
 * innings gets 3 and the other 1 (1 each when the first innings were level or one of them was not finished). A loss is 0.
 * Tables are ordered by points, then wins, then the quotient (runs per wicket scored over runs per wicket conceded).
 */
object FirstClass {
    const val WIN = 6
    const val WIN_BONUS = 7
    const val TIE = 3
    const val LEAD = 3
    const val TRAIL = 1

    fun innings(s: MatchState): List<FcInnings> {
        val t = s.test ?: return emptyList()
        return t.completed.map { FcInnings(it.data.battingTeamId, it.score.runs, it.score.wickets, it.score.overs * 6 + it.score.balls, it.declared) }
    }

    /** "WIN", "TIE" or "DRAW" for a finished match (its last innings recorded). */
    fun kind(s: MatchState): String = when {
        s.winnerId != null -> "WIN"
        TestMatch.outcome(s) != null -> "TIE"
        else -> "DRAW"
    }

    fun points(s: MatchState, teamId: String): Int {
        val t = s.test ?: return 0
        val list = t.completed
        when (kind(s)) {
            "WIN" -> {
                if (s.winnerId != teamId) return 0
                val bonus = list.size == 3 ||
                    (list.size == 4 && list.last().data.battingTeamId == teamId && list.last().score.wickets == 0)
                return if (bonus) WIN_BONUS else WIN
            }
            "TIE" -> return TIE
        }
        // A draw: the first innings decide.
        if (list.size < 2) return TRAIL
        val finishedFirstInnings = list.size >= 3 || list[1].allOut || list[1].declared
        if (!finishedFirstInnings) return TRAIL
        val a = list[0]
        val b = list[1]
        return when {
            a.score.runs == b.score.runs -> TRAIL
            (a.score.runs > b.score.runs) == (a.data.battingTeamId == teamId) -> LEAD
            else -> TRAIL
        }
    }

    /** The side ahead on the first innings, for a knockout match that is drawn (null when level). */
    fun firstInningsLeaderId(s: MatchState): String? {
        val list = s.test?.completed ?: return null
        if (list.size < 2) return null
        val a = list[0]
        val b = list[1]
        return when {
            a.score.runs > b.score.runs -> a.data.battingTeamId
            b.score.runs > a.score.runs -> b.data.battingTeamId
            else -> null
        }
    }

    private fun teamText(list: List<FcInnings>, team: Team): String {
        val mine = list.filter { it.teamId == team.id }
        if (mine.isEmpty()) return "did not bat"
        return mine.joinToString(" and ") {
            if (it.wickets >= 10) "${it.runs}" else "${it.runs}/${it.wickets}" + (if (it.declared) "d" else "")
        }
    }

    fun summary(s: MatchState, home: Team, away: Team, homePoints: Int, awayPoints: Int, withPoints: Boolean = true): String {
        val list = innings(s)
        val result = TestMatch.resultText(s) ?: "Match drawn."
        return "${CplData.shortName(home.id)} ${teamText(list, home)}, ${CplData.shortName(away.id)} ${teamText(list, away)}. " +
            (if (withPoints) "$result Points: ${CplData.shortName(home.id)} $homePoints, ${CplData.shortName(away.id)} $awayPoints." else result)
    }

    /** The table of one group, every team of it, best first. */
    fun standings(state: TournamentState, stage: String): List<FcRow> {
        val ids = Ranji.groupTeamIds(stage)
        val rows = state.teams.filter { it.id in ids }.map { team ->
            var played = 0
            var won = 0
            var drawn = 0
            var lost = 0
            var tied = 0
            var points = 0
            var runsFor = 0
            var wicketsLost = 0
            var runsAgainst = 0
            var wicketsTaken = 0
            for (f in state.fixtures) {
                if (f.stage != stage) continue
                val r = f.result ?: continue
                if (f.homeId != team.id && f.awayId != team.id) continue
                played++
                points += if (f.homeId == team.id) r.homePoints else r.awayPoints
                when (r.fcKind) {
                    "WIN" -> if (r.winnerId == team.id) won++ else lost++
                    "TIE" -> tied++
                    else -> drawn++
                }
                for (i in r.fcInnings) {
                    if (i.teamId == team.id) {
                        runsFor += i.runs
                        wicketsLost += i.wickets
                    } else {
                        runsAgainst += i.runs
                        wicketsTaken += i.wickets
                    }
                }
            }
            val scored = runsFor.toDouble() / maxOf(1, wicketsLost)
            val conceded = runsAgainst.toDouble() / maxOf(1, wicketsTaken)
            val quotient = if (conceded <= 0.0) (if (scored > 0.0) 99.0 else 0.0) else scored / conceded
            FcRow(team, played, won, drawn, lost, tied, points, quotient)
        }
        return rows.sortedWith(compareByDescending<FcRow> { it.points }.thenByDescending { it.won }.thenByDescending { it.quotient })
    }
}

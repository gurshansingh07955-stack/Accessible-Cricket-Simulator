package com.cricketsim.logic

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

/**
 * The season a career plays in: the district clubs of the player's state in one league, every club twice (home and away),
 * a match on a Saturday each week. It is a [TournamentState] (the game's own tournament record: fixtures, results,
 * scorecards, player statistics), so a season is played, simulated, tabled and counted by the same code as the
 * game's tournaments. Its tournament id is "career_<career id>".
 */
object CareerSeason {

    fun clubId(careerId: String, index: Int): String = "career_${careerId}_c$index"

    /** The team id of the player's own club in the season. */
    fun userClubId(career: CareerProfile): String? {
        val index = career.clubs.indexOfFirst { it.name == career.club }
        return if (index < 0) null else clubId(career.id, index)
    }

    private fun roleOf(label: String): PlayerRole = when (label) {
        "Batsman" -> PlayerRole.BATSMAN
        "Wicketkeeper-batsman" -> PlayerRole.WICKETKEEPER
        "Bowler" -> PlayerRole.BOWLER
        else -> PlayerRole.ALL_ROUNDER
    }

    private fun styleOf(label: String): BowlingStyle =
        if (label == "Off break" || label == "Leg break" || label == "Orthodox spin" || label == "Wrist spin") BowlingStyle.SPIN else BowlingStyle.PACE

    /** A club as the game's team: its made-up players become the game's players (ids "<club id>_p<number>"). */
    fun clubTeam(careerId: String, index: Int, club: CareerClub): Team {
        val teamId = clubId(careerId, index)
        val players = club.players.mapIndexed { i, p ->
            Player(
                id = "${teamId}_p$i",
                teamId = teamId,
                name = p.name,
                role = roleOf(p.role),
                battingRating = p.batting.coerceIn(1, 95),
                bowlingRating = p.bowling.coerceIn(1, 95),
                fieldingRating = p.fielding.coerceIn(1, 95),
                bowlingStyle = styleOf(p.bowlingStyle)
            )
        }
        fun avg(f: (Player) -> Int) = if (players.isEmpty()) 40 else players.sumOf(f) / players.size
        return Team(teamId, club.name, "", avg { it.battingRating }, avg { it.bowlingRating }, avg { it.fieldingRating }, players)
    }

    /** Every pair once, round by round (the circle method). */
    private fun roundRobin(ids: List<String>): List<Pair<String, String>> {
        val rot = ids.toMutableList()
        if (rot.size % 2 == 1) rot.add("")
        val n = rot.size
        val out = ArrayList<Pair<String, String>>()
        repeat(n - 1) { round ->
            for (i in 0 until n / 2) {
                val a = rot[i]
                val b = rot[n - 1 - i]
                if (a.isNotEmpty() && b.isNotEmpty()) out.add(if ((i + round) % 2 == 0) a to b else b to a)
            }
            rot.add(1, rot.removeAt(n - 1))
        }
        return out
    }

    /** The first Saturday at least a week away, at nine in the morning. */
    private fun firstSaturday(): Calendar {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 9); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        var ahead = (Calendar.SATURDAY - c.get(Calendar.DAY_OF_WEEK) + 7) % 7
        if (ahead == 0) ahead = 7
        c.add(Calendar.DAY_OF_YEAR, ahead)
        return c
    }

    /** The season of a career, or null if its state has fewer than two clubs (a career made before the clubs existed). */
    fun newSeason(career: CareerProfile): TournamentState? {
        val clubs = career.clubs
        if (clubs.size < 2) return null
        val teams = clubs.mapIndexed { i, c -> clubTeam(career.id, i, c) }
        val tags = LinkedHashMap<String, SquadTag>()
        teams.forEach { t -> t.players.forEach { tags[it.id] = SquadTag.DOMESTIC } }
        val all = AllStadiums.list()
        val grounds = all.filter { it.country == career.country }.ifEmpty { all }
        val offset = abs(career.state.hashCode()) % grounds.size
        // Each club plays its home matches on a ground of its own.
        val groundOf = teams.indices.associate { teams[it].id to grounds[(offset + it * 3) % grounds.size].id }
        val first = roundRobin(teams.map { it.id })
        val fixtures = ArrayList<TournamentFixture>()
        fun add(home: String, away: String) {
            fixtures.add(TournamentFixture(fixtures.size, fixtures.size + 1, CplData.LEAGUE, home, away, groundOf.getValue(home)))
        }
        first.forEach { add(it.first, it.second) }
        first.forEach { add(it.second, it.first) }
        val start = firstSaturday()
        return TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = "career_${career.id}",
            name = "${career.state} District League ${start.get(Calendar.YEAR)}",
            userTeamId = userClubId(career) ?: teams.first().id,
            teams = teams,
            tags = tags,
            fixtures = fixtures,
            createdAt = start.timeInMillis
        )
    }

    /** Matches played on one Saturday: half the clubs (rounded down), so each club plays once a week. */
    fun roundOf(state: TournamentState, fixture: TournamentFixture): Int = (fixture.number - 1) / maxOf(1, state.teams.size / 2)

    fun dateMillis(state: TournamentState, fixture: TournamentFixture): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = state.createdAt
        c.add(Calendar.DAY_OF_YEAR, 7 * roundOf(state, fixture))
        return c.timeInMillis
    }

    fun dateText(millis: Long): String = SimpleDateFormat("EEEE d MMMM yyyy", Locale.getDefault()).format(Date(millis))

    fun isComplete(state: TournamentState): Boolean = state.fixtures.all { it.result != null }

    /** The club top of the table once every match is played. */
    fun champion(state: TournamentState): Team? = if (isComplete(state)) CplData.standings(state).firstOrNull()?.team else null

    fun nameOf(state: TournamentState, teamId: String?): String = state.teams.find { it.id == teamId }?.name ?: "?"

    /** The game-player id of the career's player in the season (null on a career without club teams). */
    fun userPlayerId(career: CareerProfile): String? {
        val clubIndex = career.clubs.indexOfFirst { it.name == career.club }
        if (clubIndex < 0) return null
        val i = career.clubs[clubIndex].players.indexOfFirst { it.isUser }
        return if (i < 0) null else "${clubId(career.id, clubIndex)}_p$i"
    }

    /** The season's copy of the player gets his current ratings (they grow as he plays). */
    fun withUserPlayer(state: TournamentState, career: CareerProfile): TournamentState {
        val pid = userPlayerId(career) ?: return state
        return state.copy(teams = state.teams.map { t ->
            if (t.players.none { it.id == pid }) t
            else t.copy(players = t.players.map { p ->
                if (p.id == pid) p.copy(
                    battingRating = career.batting.coerceIn(1, 95),
                    bowlingRating = career.bowling.coerceIn(1, 95),
                    fieldingRating = career.fielding.coerceIn(1, 95)
                ) else p
            })
        })
    }

    /** Simulates the player's own fixture with the selectors' decision: he is in the club's XI or left out of it. */
    fun simulateMine(state: TournamentState, fixture: TournamentFixture, career: CareerProfile, random: Random): TournamentState {
        val home = state.teams.find { it.id == fixture.homeId } ?: return state
        val away = state.teams.find { it.id == fixture.awayId } ?: return state
        val stadium = CplData.stadium(fixture.stadiumId) ?: return state
        val pid = userPlayerId(career) ?: return CplSimulator.simulateFixture(state, fixture, random)
        val selected = CareerSelection.decide(career).selected
        val userIsHome = fixture.homeId == state.userTeamId
        val mine = CareerSelection.squadForMatch(if (userIsHome) home else away, state.tags, stadium, pid, selected)
        val sim = CplSimulator.simulateMatch(
            if (userIsHome) mine else home, if (userIsHome) away else mine, state.tags, stadium, random
        ) ?: return state
        return state.copy(
            fixtures = state.fixtures.map { if (it.id == fixture.id) it.copy(result = sim.result, scorecard = sim.scorecard) else it },
            playerStats = CplSimulator.applyStats(state.playerStats, sim.finished, sim.home, sim.away)
        )
    }

    class Advance(val state: TournamentState, val career: CareerProfile, val lines: List<String>)

    /**
     * Moves the season on. The other clubs' matches before the player's are always played. [includeMine] also simulates the player's
     * next match (with the selectors' decision, then his form and ratings); [whole] goes on to the end of the season.
     */
    fun advance(state: TournamentState, career: CareerProfile, random: Random, includeMine: Boolean, whole: Boolean): Advance {
        var s = state
        var c = career
        val lines = ArrayList<String>()
        var guard = 0
        while (guard < 100) {
            guard++
            s = CplSimulator.run(s, SimMode.UNTIL_MINE, random)
            val mine = s.fixtures.firstOrNull { it.result == null && CplSimulator.involves(it, s.userTeamId) } ?: break
            if (!includeMine && !whole) break
            val pid = userPlayerId(c)
            val before = if (pid != null) s.playerStats[pid] else null
            s = simulateMine(s, mine, c, random)
            val result = CareerProgress.afterMatch(c, before, if (pid != null) s.playerStats[pid] else null, random)
            c = result.career
            s = withUserPlayer(s, c)
            lines.add("${nameOf(s, mine.homeId)} v ${nameOf(s, mine.awayId)}: ${s.fixtures.find { it.id == mine.id }?.result?.summary ?: ""} ${result.text}")
            if (!whole) break
        }
        if (whole) s = CplSimulator.run(s, SimMode.ALL, random)
        return Advance(s, c, lines)
    }
}

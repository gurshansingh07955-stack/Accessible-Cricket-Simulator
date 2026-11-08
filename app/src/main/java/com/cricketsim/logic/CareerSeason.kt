package com.cricketsim.logic

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

/**
 * The season a career plays in: THREE competitions between the district clubs of the player's state, each a league in which every club plays the
 * others once: a T20 League (20 overs a side), a 50-Over League and a 3-Day League (three-day matches, two innings each, played like a Test).
 * Each competition is a [TournamentState], the game's own tournament record (fixtures, results, scorecards, player statistics), with its own
 * table and statistics; its id is "career_<career id>_<t20|odi|3day>". The matches take turns week by week: a T20 match on a Saturday, a
 * 50-over match the next week, a three-day match (Friday to Sunday) the week after, and so on.
 */
object CareerSeason {

    class Competition(val key: String, val label: String, val shortLabel: String, val days: Int)

    val COMPETITIONS = listOf(
        Competition("t20", "T20 League", "T20", 1),
        Competition("odi", "50-Over League", "50-over", 1),
        Competition("3day", "3-Day League", "3-day", 3)
    )

    fun tournamentId(careerId: String, key: String): String = "career_${careerId}_$key"

    fun compIndex(state: TournamentState): Int = COMPETITIONS.indexOfFirst { it.key == state.tournamentId.substringAfterLast('_') }.coerceAtLeast(0)

    fun competitionOf(state: TournamentState): Competition = COMPETITIONS[compIndex(state)]

    /** The rules a competition is played by (TournamentRules asks for them by tournament id). */
    fun rulesFor(tournamentId: String): MatchRules? = when (tournamentId.substringAfterLast('_')) {
        "t20" -> MatchRules(overs = 20, restrictionBalls = 36, fieldersOutside = 2, drsReviews = 0, rainEnabled = true, maxOversPerBowler = 4, noSuperOver = true)
        "odi" -> MatchRules(overs = 50, restrictionBalls = 60, fieldersOutside = 2, drsReviews = 0, rainEnabled = true, maxOversPerBowler = 10, noSuperOver = true)
        "3day" -> TestMatch.rulesForDays(3)
        else -> null
    }

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

    /** The three competitions of a career's season (T20, 50-over, 3-day), or null if its state has fewer than two clubs. */
    fun newSeason(career: CareerProfile): List<TournamentState>? {
        val clubs = career.clubs
        if (clubs.size < 2) return null
        val teams = clubs.mapIndexed { i, c -> clubTeam(career.id, i, c) }
        val tags = LinkedHashMap<String, SquadTag>()
        teams.forEach { t -> t.players.forEach { tags[it.id] = SquadTag.DOMESTIC } }
        val all = AllStadiums.list()
        val grounds = all.filter { it.country == career.country }.ifEmpty { all }
        val offset = abs(career.state.hashCode()) % grounds.size
        val groundOf = teams.indices.associate { teams[it].id to grounds[(offset + it * 3) % grounds.size].id }
        val start = firstSaturday()
        val year = start.get(Calendar.YEAR)
        return COMPETITIONS.map { comp ->
            val fixtures = ArrayList<TournamentFixture>()
            roundRobin(teams.map { it.id }).forEach { (home, away) ->
                fixtures.add(TournamentFixture(fixtures.size, fixtures.size + 1, CplData.LEAGUE, home, away, groundOf.getValue(home)))
            }
            TournamentState(
                version = CplData.SAVE_VERSION,
                tournamentId = tournamentId(career.id, comp.key),
                name = "${career.state} ${comp.label} $year",
                userTeamId = userClubId(career) ?: teams.first().id,
                teams = teams,
                tags = tags,
                fixtures = fixtures,
                createdAt = start.timeInMillis
            )
        }
    }

    /** The round of a competition a fixture is in: half the clubs (rounded down) play in a round, so each club plays once. */
    fun roundOf(state: TournamentState, fixture: TournamentFixture): Int = (fixture.number - 1) / maxOf(1, state.teams.size / 2)

    /** The weeks of the three competitions take turns: T20 in week 1, 50-over in week 2, 3-day in week 3, T20 in week 4... */
    fun weekOf(state: TournamentState, fixture: TournamentFixture): Int = roundOf(state, fixture) * COMPETITIONS.size + compIndex(state)

    /** The first day of the match: a Saturday, or the Friday before it for a three-day match. */
    fun dateMillis(state: TournamentState, fixture: TournamentFixture): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = state.createdAt
        c.add(Calendar.DAY_OF_YEAR, 7 * weekOf(state, fixture) - (if (competitionOf(state).days > 1) 1 else 0))
        return c.timeInMillis
    }

    fun dateText(millis: Long): String = SimpleDateFormat("EEEE d MMMM yyyy", Locale.getDefault()).format(Date(millis))

    /** "Saturday 6 December 2026", or "Friday 5 December 2026 to Sunday 7 December 2026" for a three-day match. */
    fun whenText(state: TournamentState, fixture: TournamentFixture): String {
        val start = dateMillis(state, fixture)
        val days = competitionOf(state).days
        if (days <= 1) return dateText(start)
        val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, days - 1) }.timeInMillis
        return "${dateText(start)} to ${dateText(end)}"
    }

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

    /** A competition's copy of the player gets his current ratings (they grow as he plays). */
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

    /** Simulates the player's own fixture with the selectors' decision: he is in the club's XI or left out of it. Any competition's format. */
    fun simulateMine(state: TournamentState, fixture: TournamentFixture, career: CareerProfile, random: Random): TournamentState {
        val stadium = CplData.stadium(fixture.stadiumId) ?: return state
        val pid = userPlayerId(career) ?: return CplSimulator.simulateFixture(state, fixture, random)
        val selected = CareerSelection.decide(career).selected
        val mine = state.teams.find { it.id == state.userTeamId } ?: return state
        val adjusted = CareerSelection.squadForMatch(mine, state.tags, stadium, pid, selected)
        val after = CplSimulator.simulateFixture(state.copy(teams = state.teams.map { if (it.id == state.userTeamId) adjusted else it }), fixture, random)
        return after.copy(teams = state.teams)
    }

    /** The player's next unplayed match in any competition (the one that comes first in the calendar), with its competition's index. */
    fun nextMine(states: List<TournamentState>): Pair<Int, TournamentFixture>? {
        var best: Pair<Int, TournamentFixture>? = null
        var bestDate = Long.MAX_VALUE
        states.forEachIndexed { i, s ->
            val f = s.fixtures.firstOrNull { it.result == null && CplSimulator.involves(it, s.userTeamId) } ?: return@forEachIndexed
            val d = dateMillis(s, f)
            if (d < bestDate) {
                bestDate = d
                best = i to f
            }
        }
        return best
    }

    class Advance(val states: List<TournamentState>, val career: CareerProfile, val lines: List<String>)

    /**
     * Moves the season on, match by match in calendar order. The other clubs' matches before the player's next match (in its competition) are
     * always played. [includeMine] also simulates the player's match (with the selectors' decision, then his form and ratings); [whole] goes
     * on to the end of the season, all three competitions.
     */
    fun advance(states: List<TournamentState>, career: CareerProfile, random: Random, includeMine: Boolean, whole: Boolean): Advance {
        var list = states
        var c = career
        val lines = ArrayList<String>()
        var guard = 0
        while (guard < 200) {
            guard++
            val next = nextMine(list) ?: break
            val ci = next.first
            val ran = CplSimulator.run(list[ci], SimMode.UNTIL_MINE, random)
            list = list.mapIndexed { i, x -> if (i == ci) ran else x }
            if (!includeMine && !whole) break
            val mine = ran.fixtures.firstOrNull { it.result == null && CplSimulator.involves(it, ran.userTeamId) } ?: break
            val pid = userPlayerId(c)
            val before = if (pid != null) ran.playerStats[pid] else null
            val played = simulateMine(ran, mine, c, random)
            val result = CareerProgress.afterMatch(c, before, if (pid != null) played.playerStats[pid] else null, random)
            c = result.career
            val updated = withUserPlayer(played, c)
            list = list.mapIndexed { i, x -> if (i == ci) updated else x }
            lines.add(
                "${competitionOf(updated).label}: ${nameOf(updated, mine.homeId)} v ${nameOf(updated, mine.awayId)}. " +
                    "${updated.fixtures.find { it.id == mine.id }?.result?.summary ?: ""} ${result.text}"
            )
            if (!whole) break
        }
        if (whole) list = list.map { withUserPlayer(CplSimulator.run(it, SimMode.ALL, random), c) }
        return Advance(list, c, lines)
    }
}

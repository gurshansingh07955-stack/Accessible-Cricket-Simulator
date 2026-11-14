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

    val CLUB_KEYS = COMPETITIONS.map { it.key }

    /** A club league ("career_<id>_t20" ...), as opposed to a state tournament of the domestic level. */
    fun isClub(state: TournamentState): Boolean = state.tournamentId.startsWith("career_")

    /** A competition's file key: "t20", "odi", "3day" for the club leagues, the tournament id for a state tournament. */
    fun keyOf(state: TournamentState): String = if (isClub(state)) state.tournamentId.substringAfterLast('_') else state.tournamentId

    /** The file a competition of a season is saved in (the first season's club leagues keep the names the first versions gave them). */
    fun seasonFile(careerId: String, season: Int, key: String): String =
        if (season <= 1 && key in CLUB_KEYS) "career_season_${careerId}_$key.json" else "career_season_${careerId}_s${maxOf(season, 1)}_$key.json"

    fun compIndex(state: TournamentState): Int = COMPETITIONS.indexOfFirst { it.key == keyOf(state) }.coerceAtLeast(0)

    fun competitionOf(state: TournamentState): Competition {
        if (isClub(state)) return COMPETITIONS[compIndex(state)]
        if (CareerInternational.isInternational(state)) return CareerInternational.competition(state)
        if (CareerLeagues.isLeague(state)) return CareerLeagues.competition(state)
        val def = CareerDomestic.defFor(state.tournamentId)
        return Competition(state.tournamentId, def?.label ?: state.name, def?.shortLabel ?: "Domestic", def?.days ?: 1)
    }

    /** The player's id in a competition: his club player's id in the club leagues, his one fixed id in a state tournament. */
    fun playerIdIn(state: TournamentState, career: CareerProfile): String? =
        if (isClub(state)) userPlayerId(career) else CareerDomestic.playerId(career.id)

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

    internal fun roleOf(label: String): PlayerRole = when (label) {
        "Batsman" -> PlayerRole.BATSMAN
        "Wicketkeeper-batsman" -> PlayerRole.WICKETKEEPER
        "Bowler" -> PlayerRole.BOWLER
        else -> PlayerRole.ALL_ROUNDER
    }

    internal fun styleOf(label: String): BowlingStyle =
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
    internal fun firstSaturday(): Calendar {
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

    /** Days between the player's matches in a competition: a franchise league plays its player's matches about every four days, everything else weekly. */
    fun stepDays(state: TournamentState): Int = if (CareerLeagues.isLeague(state)) 4 else 7

    /** Which competition wins when two of his matches fall on the same day (part twenty-two): international cricket, then the better league, then state cricket, then club cricket. */
    fun priorityOf(state: TournamentState): Int = when {
        CareerInternational.isInternational(state) -> 1000
        CareerLeagues.isLeague(state) -> 500 + CareerLeagues.prestigeOf(state)
        isClub(state) -> 100
        else -> 200
    }

    private fun dayNumber(millis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return Math.round(c.timeInMillis / 86_400_000.0)
    }

    /** The days a match takes up: one, three for a three-day match, five for a Test. */
    private fun daysOf(state: TournamentState, fixture: TournamentFixture): LongRange {
        val first = dayNumber(dateMillis(state, fixture))
        return first..(first + competitionOf(state).days - 1)
    }

    /**
     * Why he cannot play this match, or null if he can: another match of his, in a competition that comes before this one ([priorityOf]), is on one of the same days. Between two
     * competitions of the same kind the one that comes earlier in the list wins. The match that loses is played without him.
     */
    fun clashReason(states: List<TournamentState>, index: Int, fixture: TournamentFixture): String? {
        val state = states.getOrNull(index) ?: return null
        val mine = daysOf(state, fixture)
        val myPriority = priorityOf(state)
        for ((i, other) in states.withIndex()) {
            if (i == index) continue
            val p = priorityOf(other)
            if (p < myPriority || (p == myPriority && i > index)) continue
            for (g in other.fixtures) {
                if (!CplSimulator.involves(g, other.userTeamId)) continue
                val theirs = daysOf(other, g)
                if (theirs.first <= mine.last && mine.first <= theirs.last) return "the ${competitionOf(other).label} has a match on ${dateText(dateMillis(other, g))}"
            }
        }
        return null
    }

    /** The selectors' decision for his next match in a competition: out if a match that comes first is on the same day, else the competition's own selectors. */
    fun selectionFor(career: CareerProfile, states: List<TournamentState>, index: Int, fixture: TournamentFixture): CareerSelection.Decision {
        val clash = clashReason(states, index, fixture)
        if (clash != null) {
            return CareerSelection.Decision(
                false, 1, 11, 15, 0,
                "You cannot play this match: $clash. International cricket comes first, then the better league, then state cricket, then club cricket."
            )
        }
        return CareerSelection.forState(career, states[index])
    }

    /** The first day of the match: a Saturday, or the Friday before it for a three-day match. */
    fun dateMillis(state: TournamentState, fixture: TournamentFixture): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = state.createdAt
        if (isClub(state)) {
            c.add(Calendar.DAY_OF_YEAR, 7 * weekOf(state, fixture) - (if (competitionOf(state).days > 1) 1 else 0))
        } else {
            // A state tournament: the player's matches, one a week, from the week the tournament starts.
            val mine = state.fixtures.filter { CplSimulator.involves(it, state.userTeamId) }
            val index = mine.indexOfFirst { it.id == fixture.id }.let { if (it < 0) fixture.number else it }
            c.add(Calendar.DAY_OF_YEAR, stepDays(state) * index)
        }
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

    /** No match that can be played is left (a knockout match whose teams are not known yet does not count: it comes when the table is settled). */
    fun isComplete(state: TournamentState): Boolean = state.fixtures.none { it.result == null && it.homeId != null && it.awayId != null }

    /**
     * Whether he is the captain in this competition (part eighteen): the club captain in a club league, the national captain in a series or an international tournament, the franchise captain
     * in the league he signed for this season, and never in a state tournament. A national captain is no longer the captain of his franchise unless the franchise has made him its captain.
     */
    fun captainIn(career: CareerProfile, state: TournamentState): Boolean = when {
        isClub(state) -> career.leadership == "Captain" && !career.isState && !career.isInternational
        CareerInternational.isInternational(state) -> career.isInternational && career.nationalRole == "Captain"
        CareerLeagues.isLeague(state) -> career.isLeagueCaptain && (career.leagueCaptainTeamId == state.userTeamId || state.userTeamId in career.leagueCaptainTeamIds.orEmpty())
        else -> false
    }

    /** The champions once every match is played: the club top of the table, or the winner of a state tournament. */
    fun champion(state: TournamentState): Team? =
        if (!isComplete(state)) null
        else if (isClub(state)) CplData.standings(state).firstOrNull()?.team
        else if (CareerInternational.isSeries(state)) CareerInternational.seriesWinner(state)
        else CplSimulator.champion(state)

    fun nameOf(state: TournamentState, teamId: String?): String = state.teams.find { it.id == teamId }?.name ?: "?"

    /** A saved result line with any team id in it replaced by the team's name (older saves hold ids; the longest id first, so c1 never eats c10). */
    fun readable(state: TournamentState, text: String): String {
        var out = text
        state.teams.sortedByDescending { it.id.length }.forEach { out = out.replace(it.id, it.name) }
        return out
    }

    /** The game-player id of the career's player in the season (null on a career without club teams). */
    fun userPlayerId(career: CareerProfile): String? {
        val clubIndex = career.clubs.indexOfFirst { it.name == career.club }
        if (clubIndex < 0) return null
        val i = career.clubs[clubIndex].players.indexOfFirst { it.isUser }
        return if (i < 0) null else "${clubId(career.id, clubIndex)}_p$i"
    }

    /** A competition's copy of the player gets his current ratings (they grow as he plays). */
    fun withUserPlayer(state: TournamentState, career: CareerProfile): TournamentState {
        val pid = playerIdIn(state, career) ?: return state
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
    fun simulateMine(state: TournamentState, fixture: TournamentFixture, career: CareerProfile, random: Random, blocked: Boolean = false): TournamentState {
        val stadium = CplData.stadium(fixture.stadiumId) ?: return state
        val pid = playerIdIn(state, career) ?: return CplSimulator.simulateFixture(state, fixture, random)
        val selected = !blocked && CareerSelection.forState(career, state).selected
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
        while (guard < 300) {
            guard++
            var next = nextMine(list)
            if (next == null && whole) {
                // Nothing left for the player: finish the other matches, which may open a knockout match for his team.
                list = list.map { CplSimulator.run(it, SimMode.ALL, random) }
                next = nextMine(list)
            }
            if (next == null) break
            val ci = next.first
            val ran = CplSimulator.run(list[ci], SimMode.UNTIL_MINE, random)
            list = list.mapIndexed { i, x -> if (i == ci) ran else x }
            if (!includeMine && !whole) break
            val mine = ran.fixtures.firstOrNull { it.result == null && CplSimulator.involves(it, ran.userTeamId) } ?: break
            val pid = playerIdIn(ran, c)
            val before = if (pid != null) ran.playerStats[pid] else null
            // A match of higher priority on the same day: he cannot play this one (part twenty-two).
            val blocked = clashReason(list, ci, mine) != null
            val played = simulateMine(ran, mine, c, random, blocked)
            val result = CareerProgress.afterMatch(c, before, if (pid != null) played.playerStats[pid] else null, random, CareerInternational.formatKeyOf(ran), CareerLeagues.isLeague(ran))
            // The full career record (part nineteen) reads the scorecard of the match he has just played.
            c = CareerStatistics.record(result.career, played, mine.id, before, if (pid != null) played.playerStats[pid] else null)
            // The news about the match (part twenty-four).
            c = CareerNews.afterMyMatch(c, played, mine.id, before, if (pid != null) played.playerStats[pid] else null, random)
            // A Player of the Match award calls a press conference (part twenty-four).
            c = CareerPress.afterMyMatch(c, played, mine.id, before, if (pid != null) played.playerStats[pid] else null, random)
            val updated = withUserPlayer(played, c)
            list = list.mapIndexed { i, x -> if (i == ci) updated else x }
            lines.add(
                "${competitionOf(updated).label}: ${nameOf(updated, mine.homeId)} v ${nameOf(updated, mine.awayId)}. " +
                    "${readable(updated, updated.fixtures.find { it.id == mine.id }?.result?.summary ?: "")} ${if (blocked) "You missed it: a match that comes first was on the same day." else result.text}"
            )
            if (!whole) break
        }
        if (whole) list = list.map { withUserPlayer(CplSimulator.run(it, SimMode.ALL, random), c) }
        // The other matches that were just played, and the world (part twenty-four).
        c = CareerNews.afterMatches(c, states, list, random)
        // A competition that has just ended may make him its Player of the Series: a press conference.
        c = CareerPress.afterSeries(c, states, list, random)
        return Advance(list, c, lines)
    }

    class NextSeason(val career: CareerProfile, val states: List<TournamentState>, val keys: List<String>, val text: String)

    /**
     * The next season. The player is a year older. The state selectors look at him (his ratings in the state squad, his form, the difficulty): called up, he plays
     * the state team's tournaments; kept, he plays them again; not called up (or dropped), he plays another club season.
     */
    fun nextSeason(career: CareerProfile): NextSeason {
        // A new season: a year older, a fresh workload, and the close season heals six matches of injury.
        val older = career.copy(age = career.age + 1, season = maxOf(1, career.season) + 1, workload = 0, injuredMatches = maxOf(0, career.injuredMatches - 6), seasonEarnings = 0L, leagueContract = 0)
        var text = "A new season begins. You are ${older.age}. "
        // The national selectors look first (state and international players only): called up or kept, he plays a season of international series.
        if (career.atOrAboveState) {
            val call = CareerInternational.decision(career)
            if (call != null) {
                text += call.reason + " "
                if (call.selected) {
                    val captaincy = CareerInternational.captaincy(older.copy(level = CareerInternational.LEVEL))
                    val intlLevel = captaincy.career
                    val intl = CareerInternational.newSeason(intlLevel)
                    if (intl != null) {
                        val intlKeys = intl.map { keyOf(it) }
                        return NextSeason(intlLevel.copy(seasonKeys = intlKeys), intl, intlKeys, text + captaincy.text + "You play for ${career.nation} this season.")
                    }
                }
            }
        }
        val squad = CareerDomestic.stateSquad(career)
        if (squad != null) {
            val decision = CareerSelection.stateDecision(career, squad)
            text += decision.reason + " "
            if (decision.selected) {
                val withLevel = older.copy(level = "State")
                val domestic = CareerDomestic.newSeason(withLevel)
                if (domestic != null) {
                    val keys = domestic.map { keyOf(it) }
                    return NextSeason(withLevel.copy(seasonKeys = keys), domestic, keys, text + "You play for the ${squad.name} squad this season.")
                }
            }
        }
        val club = older.copy(level = "District")
        val states = newSeason(club) ?: emptyList()
        val keys = states.map { keyOf(it) }
        text += if (career.atOrAboveState) "You go back to district club cricket for ${career.club}." else "You stay with ${career.club} for another season of club cricket."
        return NextSeason(club.copy(seasonKeys = keys), states, keys, text)
    }
}

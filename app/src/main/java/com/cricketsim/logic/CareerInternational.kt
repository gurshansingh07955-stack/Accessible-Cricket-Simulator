package com.cricketsim.logic

import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The international level of a career (My Player, part eight): the national team.
 *
 * HOW A PLAYER GETS THERE. At the end of a state season the national selectors look at him ([decision]): his ratings in his role against the national
 * squad's players of that role, his form (the average impact of his last five matches), his age, the career difficulty, and how many state matches
 * the selectors have seen ([MIN_STATE_MATCHES]). Called up, he plays a season of international series for his country; each season the selectors
 * look again (a player already in the squad gets two places of loyalty). Dropped, he goes back to the state squad.
 *
 * THE SEASON. Four series ([newSeason]): a Test series (two, three or five matches; five against the country's old rival), a one-day series
 * (three or five matches), and two T20 series (three or five, and three). Each series is a small [TournamentState] of two teams (the national sides as the
 * game has them, the player written into his country's squad), so the career hub, the calendar, the tables and the match flow work on it unchanged.
 * The opponent and whether the series is at home or away change every season. Home series use the grounds of the player's country, away series
 * the opponent's.
 *
 * SELECTION FOR A MATCH ([matchDecision]) depends on the format: a Test XI takes four batsmen and four bowlers, a one-day or T20 XI three of each.
 * His first international match is always a debut.
 *
 * Dials: [MIN_STATE_MATCHES], the slot counts in [squadSlots] and [matchSlots].
 */
object CareerInternational {

    const val LEVEL = "International"

    /** Tournament ids of the career's series start with this (they must not start with "career_", which means a club league). */
    const val PREFIX = "careerintl_"

    /** State matches the national selectors want to have seen before they look at a player. */
    const val MIN_STATE_MATCHES = 8

    private val OPPONENTS = listOf(
        "India", "Australia", "England", "Pakistan", "South Africa",
        "New Zealand", "Sri Lanka", "West Indies", "Bangladesh", "Afghanistan"
    )

    /** The old rival of a country: the Test series against it has five matches. */
    private val RIVALS = mapOf(
        "India" to "Australia", "Pakistan" to "India", "Australia" to "England",
        "England" to "Australia", "South Africa" to "Australia", "New Zealand" to "Australia"
    )

    private class Plan(val format: String, val matches: Int)

    /** The game's own tournaments a career plays in at the international level, one a season. */
    private val TOURNAMENT_IDS = listOf(T20WorldCup.TOURNAMENT_ID, ChampionsTrophy.TOURNAMENT_ID, AsiaCup.TOURNAMENT_ID)

    /** A bilateral series (a Test, one-day or T20 series). */
    fun isSeries(state: TournamentState): Boolean = state.tournamentId.startsWith(PREFIX)

    /** The T20 World Cup, the Champions Trophy or the Asia Cup. */
    fun isTournament(state: TournamentState): Boolean = state.tournamentId in TOURNAMENT_IDS

    /** Any competition of the international level. */
    fun isInternational(state: TournamentState): Boolean = isSeries(state) || isTournament(state)

    /** "t20", "odi" or "test" for a competition of the international level, "" for any other (used to keep his form in each format). */
    fun formatKeyOf(state: TournamentState): String = if (isInternational(state) || CareerLeagues.isLeague(state)) formatKey(state.tournamentId) else ""

    /** "t20", "odi" or "test": the last part of a series' tournament id. */
    private fun formatKey(tournamentId: String): String = when {
        tournamentId.startsWith(PREFIX) -> tournamentId.substringAfterLast('_')
        tournamentId == ChampionsTrophy.TOURNAMENT_ID -> "odi" // the Champions Trophy is 50 overs a side
        else -> "t20"
    }

    private fun formatWord(key: String): String = when (key) {
        "test" -> "Test"
        "odi" -> "one-day"
        else -> "T20"
    }

    /** The rules a series is played by (TournamentRules asks for them by tournament id). */
    fun rulesFor(tournamentId: String): MatchRules? = when (formatKey(tournamentId)) {
        "t20" -> TournamentRules.rulesForFormat("T20")
        "odi" -> TournamentRules.rulesForFormat("ODI")
        "test" -> TestMatch.rulesForDays(5)
        else -> null
    }

    /** A series as a competition of the career hub: named after the series, a Test lasts five days. */
    fun competition(state: TournamentState): CareerSeason.Competition {
        val key = formatKey(state.tournamentId)
        val short = when (key) {
            "test" -> "Test"
            "odi" -> "ODI"
            else -> "T20I"
        }
        return CareerSeason.Competition(state.tournamentId, state.name, if (isTournament(state)) "Tournament" else short, if (key == "test") 5 else 1)
    }

    fun nationalTeam(country: String): Team? = CricketData.getAllTeams().find { it.name == country }

    // ------------------------------------------------------------ small helpers

    private fun value(p: Player): Double = when (p.role) {
        PlayerRole.BATSMAN, PlayerRole.WICKETKEEPER -> p.battingRating * 0.8 + p.fieldingRating * 0.2
        PlayerRole.BOWLER -> p.bowlingRating * 0.8 + p.fieldingRating * 0.2
        PlayerRole.ALL_ROUNDER -> p.battingRating * 0.45 + p.bowlingRating * 0.45 + p.fieldingRating * 0.1
    }

    private fun myValue(career: CareerProfile, role: PlayerRole): Double = when (role) {
        PlayerRole.BATSMAN, PlayerRole.WICKETKEEPER -> career.batting * 0.8 + career.fielding * 0.2
        PlayerRole.BOWLER -> career.bowling * 0.8 + career.fielding * 0.2
        PlayerRole.ALL_ROUNDER -> career.batting * 0.45 + career.bowling * 0.45 + career.fielding * 0.1
    }

    private fun roleWord(role: PlayerRole): String = when (role) {
        PlayerRole.BATSMAN -> "batsmen"
        PlayerRole.BOWLER -> "bowlers"
        PlayerRole.ALL_ROUNDER -> "all-rounders"
        PlayerRole.WICKETKEEPER -> "wicketkeepers"
    }

    /** Places in the national squad for a role (the call-up). */
    private fun squadSlots(role: PlayerRole): Int = when (role) {
        PlayerRole.WICKETKEEPER -> 1
        PlayerRole.BATSMAN -> 3
        PlayerRole.BOWLER -> 3
        PlayerRole.ALL_ROUNDER -> 2
    }

    /** Places in the XI for a role in a format of a series ("t20", "odi" or "test"). */
    private fun matchSlots(role: PlayerRole, key: String): Int = when (role) {
        PlayerRole.WICKETKEEPER -> 1
        PlayerRole.BATSMAN -> if (key == "test") 4 else 3
        PlayerRole.BOWLER -> if (key == "test") 4 else 3
        PlayerRole.ALL_ROUNDER -> if (key == "test") 2 else 3
    }

    private fun ordinal(n: Int): String = when {
        n % 100 in 11..13 -> "${n}th"
        n % 10 == 1 -> "${n}st"
        n % 10 == 2 -> "${n}nd"
        n % 10 == 3 -> "${n}rd"
        else -> "${n}th"
    }

    private fun formAdjust(career: CareerProfile, last: Int): Int {
        val form = career.form.orEmpty().takeLast(last)
        return if (form.isEmpty()) 0 else ((form.average() - 25.0) / 10.0).roundToInt().coerceIn(-3, 3)
    }

    /** Form in one format (his last matches in it) if he has played at least two there, else his form overall: a Test batsman in form can fail in T20. */
    private fun formAdjustFor(career: CareerProfile, key: String): Int {
        val inFormat = career.formByFormat?.get(key).orEmpty().takeLast(3)
        if (inFormat.size >= 2) return ((inFormat.average() - 25.0) / 10.0).roundToInt().coerceIn(-3, 3)
        return formAdjust(career, 3)
    }

    private fun formNote(adjust: Int): String = when {
        adjust > 0 -> " Your good form moves you up ${if (adjust == 1) "a place" else "$adjust places"}."
        adjust < 0 -> " Your poor form drops you ${if (adjust == -1) "a place" else "${-adjust} places"}."
        else -> ""
    }

    /** How many rating points short of the last place of the squad (roughly), or "" if he is not short. */
    private fun gapText(group: List<Player>, mine: Double, cutoff: Int): String {
        val values = group.map { value(it) }.sortedDescending()
        val target = values.getOrNull(cutoff - 1) ?: return ""
        if (target <= mine) return ""
        return " You need roughly ${(target - mine).roundToInt() + 1} more rating points in your role to be among them."
    }

    // ------------------------------------------------------------ the call-up

    /**
     * The national selectors' decision at the end of a season: is the player called up to the national squad (or does he keep his place)?
     * Null if the career is not at the state or international level, or the country has no national team.
     */
    fun decision(career: CareerProfile): CareerSelection.Decision? {
        if (!career.atOrAboveState) return null
        val squad = nationalTeam(career.country) ?: return null
        val role = CareerSeason.roleOf(career.role)
        val mine = myValue(career, role)
        val group = squad.players.filter { it.role == role }
        val rank = 1 + group.count { value(it) > mine }
        val factor = CareerWorld.selectionFactor(career.difficulty)
        val cutoff = (squadSlots(role) / factor).roundToInt().coerceIn(1, group.size + 1)
        val adjust = formAdjust(career, 5)
        val grace = (if (career.age <= 21) 1 else 0) + (if (career.isInternational) 2 else 0)
        val seen = career.isInternational || career.stateMatches >= MIN_STATE_MATCHES
        val selected = seen && rank - adjust - grace <= cutoff
        val youth = if (career.age <= 21) " The selectors back young players." else ""
        val loyal = if (career.isInternational) " As a player already in the squad you get two places of loyalty." else ""
        val base = "With your ratings you would rank ${ordinal(rank)} among the ${group.size} ${roleWord(role)} of the ${career.country} squad, and the national selectors on ${career.difficulty.lowercase()} pick the top $cutoff.$youth$loyal${formNote(adjust)}"
        val reason = when {
            !seen -> "The national selectors want to see more of you first: you have played ${career.stateMatches} of the $MIN_STATE_MATCHES state matches they ask for."
            selected && career.isInternational -> "The national selectors keep you in the ${career.country} squad. $base"
            selected -> "The national selectors call you up to the ${career.country} squad! $base"
            career.isInternational -> "The national selectors drop you from the ${career.country} squad. $base${gapText(group, mine, cutoff)}"
            else -> "The national selectors do not pick you this year. $base${gapText(group, mine, cutoff)} Keep scoring runs and taking wickets."
        }
        return CareerSelection.Decision(selected, rank, cutoff, group.size + 1, adjust, reason)
    }

    /**
     * The decision for the player's next match of a series: his rank among the players of his role in the national squad, with the places the
     * format gives that role. The first international match is always a debut; the first three and being 19 or younger each count a place in his favour.
     */
    fun matchDecision(career: CareerProfile, state: TournamentState): CareerSelection.Decision {
        val team = state.teams.find { it.id == state.userTeamId } ?: return CareerSelection.Decision(true, 1, 11, 15, 0, "")
        val me = team.players.find { it.id == CareerDomestic.playerId(career.id) }
            ?: return CareerSelection.Decision(true, 1, 11, team.players.size, 0, "")
        val key = formatKey(state.tournamentId)
        val group = team.players.filter { it.role == me.role }.sortedByDescending { value(it) }
        val rank = group.indexOfFirst { it.id == me.id }.let { if (it < 0) group.size else it + 1 }
        val factor = CareerWorld.selectionFactor(career.difficulty)
        val cutoff = (matchSlots(me.role, key) / factor).roundToInt().coerceIn(1, group.size)
        val adjust = formAdjustFor(career, key)
        val grace = (if (career.internationalMatches < 3) 1 else 0) + (if (career.age <= 19) 1 else 0)
        val debut = career.internationalMatches == 0
        val selected = debut || rank - adjust - grace <= cutoff
        val base = "You rank ${ordinal(rank)} of ${group.size} ${roleWord(me.role)} in the ${team.name} squad, and for a ${formatWord(key)} match the selectors on ${career.difficulty.lowercase()} pick the top $cutoff.${formNote(adjust)}"
        val reason = when {
            debut -> "You are in the XI. The selectors give every new international a debut: this is your first international match. $base"
            selected -> "You are in the XI. $base"
            else -> "You are not in the XI. $base Score runs, take wickets or improve your ratings to move up."
        }
        return CareerSelection.Decision(selected, rank, cutoff, group.size, adjust, reason)
    }

    // ------------------------------------------------------------ the season

    /** The grounds of a country (a country the game has no grounds for plays at the UAE's, or anywhere). */
    private fun groundsOf(all: List<Stadium>, country: String): List<Stadium> =
        all.filter { it.country == country }.ifEmpty { all.filter { it.country == "UAE" } }.ifEmpty { all }

    /**
     * A season of international series for the player's country: a Test series, a one-day series and two T20 series, each against a different
     * opponent, dated one after the other. Null if the country has no national team or too few opponents.
     */
    fun newSeason(career: CareerProfile): List<TournamentState>? {
        val everyone = CricketData.getAllTeams()
        val mine = everyone.find { it.name == career.country } ?: return null
        val season = maxOf(1, career.season)
        val random = Random(career.id.hashCode() * 31 + season)
        val pool = OPPONENTS.filter { it != career.country }.mapNotNull { n -> everyone.find { it.name == n } }.shuffled(random)
        if (pool.size < 4) return null
        val rival = RIVALS[career.country]
        // On odd seasons the old rival is met first, in the Test series.
        val ordered = if (season % 2 == 1 && rival != null) pool.sortedBy { if (it.name == rival) 0 else 1 } else pool
        val plan = listOf(
            Plan("test", if (ordered[0].name == rival) 5 else listOf(2, 3, 3)[random.nextInt(3)]),
            Plan("odi", if (ordered[1].name == rival) 5 else listOf(3, 3, 5)[random.nextInt(3)]),
            Plan("t20", listOf(3, 3, 5)[random.nextInt(3)]),
            Plan("t20", 3)
        )
        val me = CareerDomestic.asPlayer(career, mine.id)
        val myTeam = CareerDomestic.withPlayer(mine, me)
        val all = AllStadiums.list()
        val start = CareerSeason.firstSaturday()
        var offsetWeeks = 0
        val out = ArrayList<TournamentState>()
        plan.forEachIndexed { i, p ->
            val opp = ordered[i % ordered.size]
            val home = (season + i) % 2 == 0
            val grounds = groundsOf(all, if (home) career.country else opp.name)
            val homeId = if (home) myTeam.id else opp.id
            val awayId = if (home) opp.id else myTeam.id
            val fixtures = (0 until p.matches).map { m ->
                TournamentFixture(m, m + 1, CplData.LEAGUE, homeId, awayId, grounds[(m + season) % grounds.size].id)
            }
            val tags = LinkedHashMap<String, SquadTag>()
            (myTeam.players + opp.players).forEach { tags[it.id] = SquadTag.DOMESTIC }
            val label = when (p.format) {
                "test" -> "Test"
                "odi" -> "ODI"
                else -> "T20I"
            }
            val startAt = Calendar.getInstance().apply { timeInMillis = start.timeInMillis; add(Calendar.DAY_OF_YEAR, 7 * offsetWeeks) }.timeInMillis
            out.add(
                TournamentState(
                    version = CplData.SAVE_VERSION,
                    tournamentId = "$PREFIX${career.id}_s${season}_${i}_${p.format}",
                    name = "${myTeam.name} ${p.matches}-match $label series v ${opp.name}",
                    userTeamId = myTeam.id,
                    teams = listOf(myTeam, opp),
                    tags = tags,
                    fixtures = fixtures,
                    createdAt = startAt
                )
            )
            offsetWeeks += p.matches + 1
        }
        // One tournament closes the season, after the series.
        val tournamentStart = Calendar.getInstance().apply { timeInMillis = start.timeInMillis; add(Calendar.DAY_OF_YEAR, 7 * offsetWeeks) }.timeInMillis
        tournamentFor(career, season, me, tournamentStart)?.let { out.add(it) }
        return out
    }

    private class TourDef(
        val label: String,
        val build: () -> Pair<List<Team>, Map<String, SquadTag>>,
        val make: (String, Pair<List<Team>, Map<String, SquadTag>>) -> TournamentState
    )

    /**
     * The tournament of a season, built by the game's own tournament code with the player written into his country's squad: the T20 World Cup in even seasons,
     * the Champions Trophy (50 overs) in odd ones, and for India and Pakistan the Asia Cup in every second odd season. Null if it could not be built.
     */
    private fun tournamentFor(career: CareerProfile, season: Int, me: Player, startAt: Long): TournamentState? {
        val asian = career.country == "India" || career.country == "Pakistan"
        val def = when {
            season % 2 == 0 -> TourDef("T20 World Cup", { T20WorldCup.buildTeams() }, { t, b -> T20WorldCup.newTournament(t, b) })
            asian && season % 4 == 3 -> TourDef("Asia Cup", { AsiaCup.buildTeams() }, { t, b -> AsiaCup.newTournament(t, b) })
            else -> TourDef("Champions Trophy", { ChampionsTrophy.buildTeams() }, { t, b -> ChampionsTrophy.newTournament(t, b) })
        }
        return try {
            val built = def.build()
            val team = built.first.find { it.name == career.country } ?: return null
            val state0 = def.make(team.id, built)
            val mine = me.copy(teamId = team.id)
            val teams = state0.teams.map { t -> if (t.id == team.id) CareerDomestic.withPlayer(t, mine) else t }
            state0.copy(name = "${def.label}, season $season", teams = teams, tags = state0.tags + (mine.id to SquadTag.DOMESTIC), createdAt = startAt)
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------ results

    /** The winner of a finished series (null for a drawn series). */
    fun seriesWinner(state: TournamentState): Team? {
        val a = state.teams.getOrNull(0) ?: return null
        val b = state.teams.getOrNull(1) ?: return null
        val wa = state.fixtures.count { it.result?.winnerId == a.id }
        val wb = state.fixtures.count { it.result?.winnerId == b.id }
        return when {
            wa > wb -> a
            wb > wa -> b
            else -> null
        }
    }

    /** One sentence about the state of a series. */
    fun resultLine(state: TournamentState): String {
        val a = state.teams.getOrNull(0) ?: return state.name
        val b = state.teams.getOrNull(1) ?: return state.name
        val wa = state.fixtures.count { it.result?.winnerId == a.id }
        val wb = state.fixtures.count { it.result?.winnerId == b.id }
        val played = state.fixtures.count { it.result != null }
        return when {
            played < state.fixtures.size -> "${state.name}: $wa-$wb after $played of ${state.fixtures.size} matches."
            wa > wb -> "${state.name}: ${a.name} won $wa-$wb."
            wb > wa -> "${state.name}: ${b.name} won $wb-$wa."
            else -> "${state.name}: the series was drawn $wa-$wb."
        }
    }

    /** One or two sentences on what the player did in a finished series or tournament, and whether he was its Player of the Series. */
    fun awardLine(state: TournamentState, career: CareerProfile): String {
        val pid = CareerDomestic.playerId(career.id)
        val mine = state.playerStats[pid] ?: return "You did not play in it."
        if (mine.matches == 0) return "You did not play in it."
        val parts = ArrayList<String>()
        if (mine.balls > 0) parts.add("${mine.runs} runs")
        if (mine.ballsBowled > 0) parts.add("${mine.wickets} wickets")
        if (mine.catches > 0) parts.add("${mine.catches} catches")
        val best = state.playerStats.values.maxWithOrNull(compareBy<PlayerStats>({ it.impact }, { it.runs + it.wickets * 20 }))
        val star = if (best != null && best.playerId == pid && best.impact > 0.0) " You were Player of the Series!" else ""
        return "Your tally in ${mine.matches} matches: ${parts.joinToString(", ").ifEmpty { "no runs, wickets or catches" }}.$star"
    }

    // ------------------------------------------------------------ the pathway

    private fun summary(d: CareerSelection.Decision): String =
        "you would rank ${ordinal(d.rank)} in your role, and the selectors take the top ${d.cutoff}." + (if (d.selected) " You are on course." else " You are not there yet.")

    /**
     * How far the player is from the next level, in plain sentences: the road (district club, state squad, national team), where he is, and
     * his rank against the squad he is aiming for, with what is missing. Builds squads, so call it off the main thread.
     */
    fun pathway(career: CareerProfile): List<String> {
        val out = ArrayList<String>()
        out.add("The road: district club, then the ${career.state} state squad, then the ${career.country} national team.")
        if (career.isInternational) {
            out.add("You are in the ${career.country} national squad and have played ${career.internationalMatches} international matches.")
            decision(career)?.let { out.add("Keeping your place next season: " + summary(it)) }
        } else if (career.isState) {
            out.add("You are in the ${career.domesticTeams.firstOrNull() ?: career.state} squad and have played ${career.stateMatches} state matches.")
            if (career.stateMatches < MIN_STATE_MATCHES) {
                out.add("The national selectors want at least $MIN_STATE_MATCHES state matches first: ${MIN_STATE_MATCHES - career.stateMatches} to go.")
            }
            decision(career)?.let { out.add("Next step, the ${career.country} national squad: " + summary(it)) }
        } else {
            val squad = CareerDomestic.stateSquad(career)
            if (squad == null) {
                out.add("Your state has no domestic team in the game, so there is no state call-up.")
            } else {
                val d = CareerSelection.stateDecision(career, squad)
                val role = CareerSeason.roleOf(career.role)
                val gap = if (d.selected) "" else gapText(squad.players.filter { it.role == role }, myValue(career, role), d.cutoff)
                out.add("Next step, the ${squad.name} state squad: " + summary(d) + gap)
            }
        }
        CareerHealth.statusLine(career)?.let { out.add(it) }
        return out
    }
}

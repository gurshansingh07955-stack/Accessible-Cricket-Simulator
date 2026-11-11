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

    /** The ODI World Cup of a career season (a tournament of ten national teams, built here; its id must not start with PREFIX, which means a two-team series). */
    const val WC_PREFIX = "careerwc_"

    /** State matches the national selectors want to have seen before they look at a player. */
    const val MIN_STATE_MATCHES = 8

    /** Seasons a player who moved to the country must have lived there before its national selectors look at him. */
    const val RESIDENCY_SEASONS = 2

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
    fun isTournament(state: TournamentState): Boolean = state.tournamentId in TOURNAMENT_IDS || state.tournamentId.startsWith(WC_PREFIX)

    /** Any competition of the international level. */
    fun isInternational(state: TournamentState): Boolean = isSeries(state) || isTournament(state)

    /** "t20", "odi" or "test" for a competition of the international level, "" for any other (used to keep his form in each format). */
    fun formatKeyOf(state: TournamentState): String = if (isInternational(state) || CareerLeagues.isLeague(state)) formatKey(state.tournamentId) else ""

    /** "t20", "odi" or "test": the last part of a series' tournament id. */
    private fun formatKey(tournamentId: String): String = when {
        tournamentId.startsWith(PREFIX) || tournamentId.startsWith(WC_PREFIX) -> tournamentId.substringAfterLast('_')
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

    /** The countries whose national teams a career can aim for (the ten Test nations). */
    fun nations(): List<String> = OPPONENTS

    /** State matches the national selectors want to have seen: twice as many for a player aiming for another country (the residency period). */
    fun neededMatches(career: CareerProfile): Int = if (career.represents != null) MIN_STATE_MATCHES * 2 else MIN_STATE_MATCHES

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
        val squad = nationalTeam(career.nation)?.let { evolved(it, career.season, null) } ?: return null
        val role = CareerSeason.roleOf(career.role)
        val mine = myValue(career, role)
        val group = squad.players.filter { it.role == role }
        val rank = 1 + group.count { value(it) > mine }
        val factor = CareerWorld.selectionFactor(career.difficulty)
        val cutoff = (squadSlots(role) / factor).roundToInt().coerceIn(1, group.size + 1)
        val adjust = formAdjust(career, 5)
        val grace = (if (career.age <= 21) 1 else 0) + (if (career.isInternational) 2 else 0)
        val residencyOk = career.movedSeason == 0 || career.season - career.movedSeason >= RESIDENCY_SEASONS
        val seen = career.isInternational || (career.stateMatches >= neededMatches(career) && residencyOk)
        val selected = seen && rank - adjust - grace <= cutoff
        val youth = if (career.age <= 21) " The selectors back young players." else ""
        val loyal = if (career.isInternational) " As a player already in the squad you get two places of loyalty." else ""
        val base = "With your ratings you would rank ${ordinal(rank)} among the ${group.size} ${roleWord(role)} of the ${career.nation} squad, and the national selectors on ${career.difficulty.lowercase()} pick the top $cutoff.$youth$loyal${formNote(adjust)}"
        val reason = when {
            !residencyOk && !career.isInternational -> "The national selectors cannot pick you yet: you moved to ${career.country} in season ${career.movedSeason} and must live there for $RESIDENCY_SEASONS seasons. You are eligible from season ${career.movedSeason + RESIDENCY_SEASONS}."
            !seen -> "The national selectors want to see more of you first: you have played ${career.stateMatches} of the ${neededMatches(career)} state matches they ask for."
            selected && career.isInternational -> "The national selectors keep you in the ${career.nation} squad. $base"
            selected -> "The national selectors call you up to the ${career.nation} squad! $base"
            career.isInternational -> "The national selectors drop you from the ${career.nation} squad. $base${gapText(group, mine, cutoff)}"
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
        val grace = (if (career.internationalMatches < 3) 1 else 0) + (if (career.age <= 19) 1 else 0) + specialismFit(career, key)
        val debut = career.internationalMatches == 0
        val captain = career.nationalRole == "Captain"
        val selected = debut || captain || rank - adjust - grace <= cutoff
        val base = "You rank ${ordinal(rank)} of ${group.size} ${roleWord(me.role)} in the ${team.name} squad, and for a ${formatWord(key)} match the selectors on ${career.difficulty.lowercase()} pick the top $cutoff.${formNote(adjust)}"
        val reason = when {
            debut -> "You are in the XI. The selectors give every new international a debut: this is your first international match. $base"
            captain -> "You are in the XI: you are the captain of ${team.name}. $base"
            selected -> "You are in the XI. $base"
            else -> "You are not in the XI. $base Score runs, take wickets or improve your ratings to move up."
        }
        return CareerSelection.Decision(selected, rank, cutoff, group.size, adjust, reason + specialismNote(career, key))
    }

    // ------------------------------------------------------------ specialism and captaincy (part thirteen)

    /** +1 if the format is the one he specialises in, -1 if it is the other kind, 0 if he does not specialise. */
    fun specialismFit(career: CareerProfile, key: String): Int = when (career.specialism) {
        "Test" -> if (key == "test") 1 else -1
        "White-ball" -> if (key == "test") -1 else 1
        else -> 0
    }

    private fun specialismNote(career: CareerProfile, key: String): String = when (specialismFit(career, key)) {
        1 -> " As a ${if (career.specialism == "Test") "Test" else "white-ball"} specialist you get a place in your favour in this format."
        -1 -> " As a ${if (career.specialism == "Test") "Test" else "white-ball"} specialist you have a place against you in this format."
        else -> ""
    }

    class Captaincy(val career: CareerProfile, val text: String)

    /** Matches the national selectors want to have seen before they consider a player for the vice-captaincy, and for the captaincy. */
    const val VICE_MATCHES = 12
    const val CAPTAIN_MATCHES = 25

    private fun leadershipScore(career: CareerProfile): Double {
        val squad = nationalTeam(career.nation)?.let { evolved(it, career.season, null) }
        val role = CareerSeason.roleOf(career.role)
        val mine = myValue(career, role)
        val standing = if (squad == null) 99 else 1 + squad.players.count { value(it) > mine }
        val form = career.form.orEmpty()
        val formBonus = if (form.isEmpty()) 0.0 else ((form.average() - 20.0) / 20.0).coerceIn(-1.0, 1.0)
        return career.internationalMatches / 10.0 + formBonus + (if (standing <= 3) 1.5 else if (standing <= 6) 0.5 else 0.0) + (if (career.age >= 26) 1.0 else 0.0)
    }

    /**
     * The national selectors' leadership decision at the start of an international season. A leadership score from his international experience (a point for every ten
     * matches), his form, his standing among the squad's best (top three +1.5, top six +0.5) and his age (26 or over +1): at 4.0 with 12 matches he is made vice-captain; a vice-captain
     * at 6.0 with 25 matches is made captain; a captain or vice-captain whose score falls under 3.0 loses the role. A captain picks the XI and does the toss in the national matches.
     */
    fun captaincy(career: CareerProfile): Captaincy {
        if (!career.isInternational) return Captaincy(career, "")
        val score = leadershipScore(career)
        val current = career.nationalRole
        val matches = career.internationalMatches
        val next = when (current) {
            "Captain" -> if (score >= 3.0) "Captain" else null
            "Vice-captain" -> if (score >= 6.0 && matches >= CAPTAIN_MATCHES) "Captain" else if (score >= 3.0) "Vice-captain" else null
            else -> if (score >= 4.0 && matches >= VICE_MATCHES) "Vice-captain" else null
        }
        if (next == current) return Captaincy(career, "")
        val text = when (next) {
            "Captain" -> "The national selectors appoint you captain of ${career.nation}! "
            "Vice-captain" -> "The national selectors appoint you vice-captain of ${career.nation}. "
            else -> "You have lost the ${current?.lowercase() ?: "leadership"} of ${career.nation}. "
        }
        val honours = if (next != null && next != current) career.honours.orEmpty() + "Season ${career.season}: appointed ${next.lowercase()} of ${career.nation}." else career.honours
        return Captaincy(career.copy(nationalRole = next, honours = honours?.takeLast(60)), text)
    }

    /** A sentence for the Career tab and the pathway about the national captaincy. */
    fun captaincyLine(career: CareerProfile): String = when (career.nationalRole) {
        "Captain" -> "You are the captain of ${career.nation}: you pick the XI and do the toss in the national matches."
        "Vice-captain" -> "You are the vice-captain of ${career.nation}. At $CAPTAIN_MATCHES international matches, with good form and a place among the squad's best, the selectors may make you captain (you have played ${career.internationalMatches})."
        else -> "The selectors look at the vice-captaincy at $VICE_MATCHES international matches, with good form and a place among the squad's best; you have played ${career.internationalMatches}."
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
        // The computer's squads age with the career: the young improve, the old decline (see evolved).
        val everyone = CricketData.getAllTeams().map { evolved(it, maxOf(1, career.season), null) }
        val mine = everyone.find { it.name == career.nation } ?: return null
        val season = maxOf(1, career.season)
        val random = Random(career.id.hashCode() * 31 + season)
        val pool = OPPONENTS.filter { it != career.nation }.mapNotNull { n -> everyone.find { it.name == n } }.shuffled(random)
        if (pool.size < 4) return null
        val rival = RIVALS[career.nation]
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
            val grounds = groundsOf(all, if (home) career.nation else opp.name)
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
        // In odd seasons the two best Test teams play the World Test Championship final (the player's country must be one of them).
        if (season % 2 == 1) {
            val finalStart = Calendar.getInstance().apply { timeInMillis = start.timeInMillis; add(Calendar.DAY_OF_YEAR, 7 * offsetWeeks) }.timeInMillis
            val finalState = wtcFinal(career, season, everyone, mine, me, finalStart)
            if (finalState != null) {
                out.add(finalState)
                offsetWeeks += 2
            }
        }
        // One tournament closes the season, after the series.
        val tournamentStart = Calendar.getInstance().apply { timeInMillis = start.timeInMillis; add(Calendar.DAY_OF_YEAR, 7 * offsetWeeks) }.timeInMillis
        tournamentFor(career, season, me, tournamentStart)?.let { out.add(it) }
        return out
    }

    // ------------------------------------------------------------ part fourteen: ageing squads, the ODI World Cup, the Test championship final

    /**
     * A national or franchise team as it is after [season] - 1 seasons of the career. Players have no age in the game, so each gets one from his name (24 to 37);
     * every season of the career a player under 27 gains 1.5 rating points, one of 33 or over loses 1.5 (never more than 12 in all, and at most about 5 are gained), in batting
     * and bowling, half of it in fielding. [protectId] (the player's own id) is left alone.
     */
    fun evolved(team: Team, season: Int, protectId: String?, nameCountry: String? = null): Team {
        val steps = (season - 1).coerceAtLeast(0)
        if (steps == 0) return team
        return team.copy(players = team.players.map { p ->
            if (p.id == protectId) p
            else {
                val age0 = 24 + (p.name.hashCode() and 0x7fffffff) % 14
                // A veteran who has reached retirement age is replaced by a young player (SquadEvolution).
                if (age0 + steps >= SquadEvolution.RETIRE_AGE) return@map SquadEvolution.successor(team, p, nameCountry)
                var delta = 0.0
                for (y in 0 until steps) {
                    val age = age0 + y
                    delta += when {
                        age < 27 -> 1.5
                        age < 33 -> 0.0
                        else -> -1.5
                    }
                }
                val d = delta.coerceAtLeast(-12.0).roundToInt()
                if (d == 0) p
                else p.copy(
                    battingRating = (p.battingRating + d).coerceIn(10, 99),
                    bowlingRating = (p.bowlingRating + d).coerceIn(10, 99),
                    fieldingRating = (p.fieldingRating + d / 2).coerceIn(10, 99)
                )
            }
        })
    }

    /** Every pair of a list once (the circle method), 45 matches for ten teams. */
    private fun roundRobin(ids: List<String>): List<Pair<String, String>> {
        val list = ids.toMutableList()
        if (list.size % 2 == 1) list.add("")
        val n = list.size
        val out = ArrayList<Pair<String, String>>()
        for (round in 0 until n - 1) {
            for (i in 0 until n / 2) {
                val a = list[i]
                val b = list[n - 1 - i]
                if (a.isNotEmpty() && b.isNotEmpty()) out.add(if (round % 2 == 0) a to b else b to a)
            }
            val last = list.removeAt(n - 1)
            list.add(1, last)
        }
        return out
    }

    /**
     * The ODI World Cup of an international season (seasons 1, 5, 9 ...): the ten Test nations play each other once (45 matches, 50 overs a side), then the top four play two
     * semi-finals (1st v 4th, 2nd v 3rd) and the Final ([advanceWc]). The host country changes every four seasons. Null if it could not be built.
     */
    private fun odiWorldCup(career: CareerProfile, season: Int, me: Player, startAt: Long): TournamentState? {
        return try {
            val all = CricketData.getAllTeams().map { evolved(it, season, null) }
            val nations = OPPONENTS.mapNotNull { n -> all.find { it.name == n } }
            val mineTeam = nations.find { it.name == career.nation } ?: return null
            if (nations.size < 8) return null
            val teams = nations.map { t -> if (t.id == mineTeam.id) CareerDomestic.withPlayer(t, me.copy(teamId = mineTeam.id)) else t }
            val grounds = groundsOf(AllStadiums.list(), OPPONENTS[(season / 4) % OPPONENTS.size])
            val fixtures = ArrayList<TournamentFixture>()
            roundRobin(teams.map { it.id }).forEachIndexed { i, pair ->
                fixtures.add(TournamentFixture(i, i + 1, CplData.LEAGUE, pair.first, pair.second, grounds[i % grounds.size].id))
            }
            var number = fixtures.size
            for (stage in listOf("Semi-final 1", "Semi-final 2", "Final")) {
                fixtures.add(TournamentFixture(number, number + 1, stage, null, null, grounds[0].id))
                number++
            }
            val tags = LinkedHashMap<String, SquadTag>()
            teams.forEach { t -> t.players.forEach { tags[it.id] = SquadTag.DOMESTIC } }
            TournamentState(
                version = CplData.SAVE_VERSION,
                tournamentId = "$WC_PREFIX${career.id}_s${season}_odi",
                name = "ODI World Cup, season $season",
                userTeamId = mineTeam.id,
                teams = teams,
                tags = tags,
                fixtures = fixtures,
                createdAt = startAt
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Fills in the ODI World Cup's semi-finals and Final as the teams become known (called from CplSimulator.advancePlayoffs after every match). */
    fun advanceWc(state: TournamentState): TournamentState {
        val league = state.fixtures.filter { it.stage == CplData.LEAGUE }
        if (league.isEmpty() || league.any { it.result == null }) return state
        val table = CplData.standings(state)
        if (table.size < 4) return state
        var fx = state.fixtures
        fun setTeams(stage: String, home: String?, away: String?) {
            if (home == null || away == null) return
            fx = fx.map { if (it.stage == stage && it.homeId == null) it.copy(homeId = home, awayId = away) else it }
        }
        setTeams("Semi-final 1", table[0].team.id, table[3].team.id)
        setTeams("Semi-final 2", table[1].team.id, table[2].team.id)
        val winner1 = fx.firstOrNull { it.stage == "Semi-final 1" }?.result?.winnerId
        val winner2 = fx.firstOrNull { it.stage == "Semi-final 2" }?.result?.winnerId
        if (winner1 != null && winner2 != null) setTeams("Final", winner1, winner2)
        return state.copy(fixtures = fx)
    }

    /**
     * The World Test Championship final of an odd season: the two strongest Test teams of the season (the average of each team's best eleven, with a little luck drawn from the
     * career and the season) meet in a one-match Test series in England. Only if the player's country is one of them; null otherwise.
     */
    private fun wtcFinal(career: CareerProfile, season: Int, everyone: List<Team>, mine: Team, me: Player, startAt: Long): TournamentState? {
        val random = Random(career.id.hashCode() * 13 + season)
        val strength = OPPONENTS.mapNotNull { n -> everyone.find { it.name == n } }
            .associate { t -> t.id to (t.players.map { value(it) }.sortedDescending().take(11).average() + random.nextDouble() * 6.0 - 3.0) }
        val finalists = strength.entries.sortedByDescending { it.value }.map { it.key }.take(2)
        if (mine.id !in finalists) return null
        val opp = everyone.find { it.id == finalists.first { id -> id != mine.id } } ?: return null
        val myTeam = CareerDomestic.withPlayer(mine, me)
        val ground = groundsOf(AllStadiums.list(), "England")[0]
        val tags = LinkedHashMap<String, SquadTag>()
        (myTeam.players + opp.players).forEach { tags[it.id] = SquadTag.DOMESTIC }
        return TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = "$PREFIX${career.id}_s${season}_wtc_test",
            name = "World Test Championship final: ${myTeam.name} v ${opp.name}",
            userTeamId = myTeam.id,
            teams = listOf(myTeam, opp),
            tags = tags,
            fixtures = listOf(TournamentFixture(0, 1, CplData.LEAGUE, myTeam.id, opp.id, ground.id)),
            createdAt = startAt
        )
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
        if (season % 4 == 1) return odiWorldCup(career, season, me, startAt)
        val asian = career.nation == "India" || career.nation == "Pakistan"
        val def = when {
            season % 2 == 0 -> TourDef("T20 World Cup", { T20WorldCup.buildTeams() }, { t, b -> T20WorldCup.newTournament(t, b) })
            asian && season % 4 == 3 -> TourDef("Asia Cup", { AsiaCup.buildTeams() }, { t, b -> AsiaCup.newTournament(t, b) })
            else -> TourDef("Champions Trophy", { ChampionsTrophy.buildTeams() }, { t, b -> ChampionsTrophy.newTournament(t, b) })
        }
        return try {
            val built = def.build()
            val team = built.first.find { it.name == career.nation } ?: return null
            val state0 = def.make(team.id, built)
            val mine = me.copy(teamId = team.id)
            val teams = state0.teams.map { t ->
                val aged = evolved(t, season, null)
                if (t.id == team.id) CareerDomestic.withPlayer(aged, mine) else aged
            }
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
        out.add("The road: district club, then the ${career.state} state squad, then the ${career.nation} national team.")
        if (career.isInternational) {
            out.add("You are in the ${career.nation} national squad and have played ${career.internationalMatches} international matches.")
            decision(career)?.let { out.add("Keeping your place next season: " + summary(it)) }
            out.add(captaincyLine(career))
        } else if (career.isState) {
            out.add("You are in the ${career.domesticTeams.firstOrNull() ?: career.state} squad and have played ${career.stateMatches} state matches.")
            if (career.stateMatches < neededMatches(career)) {
                out.add("The national selectors want at least ${neededMatches(career)} state matches first: ${neededMatches(career) - career.stateMatches} to go.")
            }
            if (career.movedSeason > 0 && career.season - career.movedSeason < RESIDENCY_SEASONS) {
                out.add("You moved to ${career.country} in season ${career.movedSeason}: the national selectors want you to live there for $RESIDENCY_SEASONS seasons first.")
            }
            decision(career)?.let { out.add("Next step, the ${career.nation} national squad: " + summary(it)) }
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

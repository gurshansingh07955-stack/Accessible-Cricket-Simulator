package com.cricketsim.logic

import kotlin.random.Random

/**
 * LANKA PREMIER LEAGUE 2026 (LPL 6, played 17 July to 8 August 2026).
 *
 *  - FIVE TEAMS, the real ones: Colombo Kaps, Dambulla Sixers, Galle Gallants, Jaffna Kings and Kandy
 *    Royals. The squads are the 2026 draft squads with every announced replacement applied (players who
 *    left on 1 August with no replacement are out: Moeen Ali, Sediqullah Atal, Brandon McMullen).
 *  - THE FORMAT, as played: a double round-robin (every team plays the other four twice: 20 league matches),
 *    then the top four play Qualifier 1 (1st v 2nd), the Eliminator (3rd v 4th), Qualifier 2 and the Final:
 *    24 matches, T20.
 *  - THE GROUNDS, as played: the first five league matches at the SSC Ground, Colombo, the next nine at
 *    Rangiri Dambulla, the last six at Pallekele; the play-offs at the R. Premadasa Stadium, Colombo. All four
 *    were already in the game. The franchises have no home ground.
 *  - THE RULES used: at most four overseas players in the XI, and at least one under-23 player in the XI
 *    (the 2026 rule; the game's "breakout player" tag is used for it). NOT built: the LPL's Impact Player
 *    substitute (the game's Impact Player is IPL-only here).
 *
 * Players already in the game (its roster, a CPL player, an Abu Dhabi T10 player) are COPIED with their
 * ratings; only the others are new, with ESTIMATED ratings (the nw() lines; change them there). It reuses
 * the CPL machinery as it is (TournamentState, CplSimulator, the tabs, saving, scorecards, Player of the Match).
 */
object LankaPremierLeague {

    const val TOURNAMENT_ID = "lpl2026"
    const val TOURNAMENT_NAME = "Lanka Premier League 2026"

    private const val SSC = "ssc-ground"
    private const val DAMBULLA = "dambulla"
    private const val PALLEKELE = "pallekele"
    private const val PREMADASA = "premadasa"

    private class NewSpec(val role: PlayerRole, val bat: Int, val bowl: Int, val style: BowlingStyle)

    /** A squad slot: a player copied from the game (spec null; `lookup` is the game's spelling if it differs) or a new one. */
    private class Slot(val name: String, val lookup: String?, val tag: SquadTag, val spec: NewSpec?)

    private class TeamDef(val id: String, val name: String, val shortName: String, val code: String, val squad: List<Slot>)

    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN
    private val D = SquadTag.DOMESTIC
    private val O = SquadTag.OVERSEAS
    private val U = SquadTag.BREAKOUT // an under-23 Sri Lankan

    private fun ex(name: String, tag: SquadTag = D) = Slot(name, null, tag, null)
    private fun al(name: String, gameName: String, tag: SquadTag = D) = Slot(name, gameName, tag, null)
    private fun nw(name: String, tag: SquadTag, role: PlayerRole, bat: Int, bowl: Int, style: BowlingStyle = PACE) =
        Slot(name, null, tag, NewSpec(role, bat, bowl, style))

    private val DEFS: List<TeamDef> by lazy {
        listOf(
            TeamDef("lpl_kaps", "Colombo Kaps", "Kaps", "COL", listOf(
                nw("Ashen Bandara", D, BAT, 68, 15), ex("Kamindu Mendis"), ex("James Neesham", O), ex("Ben McDermott", O),
                nw("Binura Fernando", D, BWL, 18, 76), ex("Sadeera Samarawickrama"), nw("Milan Rathnayake", D, AR, 62, 66),
                ex("Janith Liyanage"), ex("Jeffrey Vandersay"), nw("Thanuka Dabare", D, WK, 62, 10),
                nw("Movin Subasingha", D, AR, 55, 55, SPIN), nw("Malsha Tharupathi", U, BAT, 54, 15),
                nw("Sharujan Shanmuganathan", U, BWL, 20, 58), nw("Wanuja Sahan", D, AR, 56, 58),
                nw("Anthony Arul Pragasam", D, BAT, 54, 20), ex("Mujeeb Ur Rahman", O), nw("Shahnawaz Dahani", O, BWL, 18, 78),
                nw("Rubin Hermann", O, BAT, 72, 10), nw("Ripon Mondol", O, BWL, 20, 62, SPIN), ex("Kushal Bhurtel", O)
            )),
            TeamDef("lpl_sixers", "Dambulla Sixers", "Sixers", "DAM", listOf(
                ex("Dushmantha Chameera"), nw("Dinesh Chandimal", D, WK, 76, 10), nw("Sahibzada Farhan", O, BAT, 80, 10),
                ex("Reeza Hendricks", O), ex("Maheesh Theekshana"), nw("Niroshan Dickwella", D, WK, 72, 10),
                nw("Pavan Rathnayake", D, AR, 62, 58, SPIN), nw("Akila Dananjaya", D, BWL, 25, 74, SPIN),
                nw("Dhananjaya Lakshan", D, AR, 60, 60), ex("Ramesh Mendis"), nw("Sachitha Jayathilake", D, AR, 55, 60),
                nw("Vishwa Lahiru Kumara", U, BWL, 15, 64), nw("Gayana Weerasinghe", U, AR, 52, 50, SPIN),
                ex("Sikandar Raza", O), ex("Mohammad Wasim Jr", O), ex("Gulbadin Naib", O),
                nw("Marques Ackerman", O, BAT, 70, 15), ex("Shadley van Schalkwyk", O)
            )),
            TeamDef("lpl_gallants", "Galle Gallants", "Gallants", "GAL", listOf(
                ex("Dasun Shanaka"), ex("Eshan Malinga"), nw("Chris Lynn", O, BAT, 82, 10), ex("Nurul Hasan Sohan", O),
                ex("Charith Asalanka"), ex("Pramod Madushan"), nw("Lasith Croospulle", D, AR, 62, 40, SPIN),
                nw("Vijayakanth Viyaskanth", D, BWL, 25, 76, SPIN), ex("Sahan Arachchige"), ex("Chamika Karunaratne"),
                nw("Tharindu Rathnayake", D, BWL, 20, 66), nw("Dinura Kalupahana", U, AR, 54, 56, SPIN),
                nw("Yuri Koththigoda", U, BAT, 52, 15), ex("Kasun Rajitha"), nw("Sachindu Colombage", D, AR, 56, 60, SPIN),
                ex("Mohammad Nawaz", O), ex("Akif Javed", O), nw("Sam Harper", O, WK, 66, 10),
                nw("Tom Rogers", O, AR, 56, 66, SPIN), nw("Virandeep Singh", O, AR, 66, 56, SPIN)
            )),
            TeamDef("lpl_kings", "Jaffna Kings", "Kings", "JAF", listOf(
                ex("Bhanuka Rajapaksa"), ex("Dunith Wellalage"), ex("Shakib Al Hasan", O), ex("Towhid Hridoy", O),
                ex("Avishka Fernando"), ex("Dilshan Madushanka"), ex("Kamil Mishara"), nw("Traveen Mathew", D, AR, 54, 58),
                nw("Mohamed Shiraz", D, AR, 52, 60, SPIN), nw("Chamindu Wickramasinghe", D, AR, 70, 50, SPIN),
                ex("Nuwanidu Fernando"), nw("Kugathas Mathulan", U, BWL, 15, 66, SPIN), nw("Praveen Manisha", U, AR, 56, 56),
                nw("Nishan Madushka", D, BAT, 64, 15), ex("David Wiese", O), al("Khawaja Nafay", "Khwaja Nafay", O),
                ex("Sandeep Lamichhane", O), ex("Lizaad Williams", O), ex("Zahoor Khan", O)
            )),
            TeamDef("lpl_royals", "Kandy Royals", "Royals", "KAN", listOf(
                ex("Wanindu Hasaranga"), ex("Angelo Mathews"), nw("Vijay Shankar", O, AR, 66, 58), ex("Nuwan Thushara"),
                ex("Kusal Perera"), ex("Asitha Fernando"), nw("Garuka Sanketh", D, AR, 54, 58), nw("Kavija Gamage", D, BWL, 20, 60),
                nw("Muditha Lakshan", D, BWL, 20, 62, SPIN), ex("Lahiru Udara"), nw("Sahan Mihira", U, BAT, 54, 15),
                nw("Pavan Sandesh", U, AR, 54, 56), nw("Dushan Hemantha", D, AR, 58, 66, SPIN),
                nw("Isitha Wijesundara", D, AR, 56, 60), al("Shaheen Shah Afridi", "Shaheen Afridi", O),
                nw("Dale Phillips", O, BAT, 62, 20), nw("Zahir Khan", O, BWL, 20, 74, SPIN)
            ))
        )
    }

    fun isTeam(teamId: String): Boolean = DEFS.any { it.id == teamId }

    fun shortNameOrNull(teamId: String): String? = DEFS.find { it.id == teamId }?.shortName

    val stadiums: List<Stadium> by lazy {
        listOf(SSC, DAMBULLA, PALLEKELE, PREMADASA).mapNotNull { StadiumData.getStadiumById(it) }
    }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' }

    private fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)

    /** The five franchises (strongest first), with who is overseas (at most four play) and who is an under-23. */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val roster = CricketData.getAllTeams().flatMap { it.players }.associateBy { norm(it.name) }
        // Players the CPL or the Abu Dhabi T10 added are in the game too: reuse them as well.
        val cpl = CplData.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val adt = AbuDhabiT10.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val tags = LinkedHashMap<String, SquadTag>()
        val teams = DEFS.map { def ->
            val players = def.squad.mapIndexed { index, slot ->
                val id = "${def.id}_${index + 1}"
                val key = norm(slot.lookup ?: slot.name)
                val base = if (slot.spec == null) (roster[key] ?: cpl[key] ?: adt[key]) else null
                val player = if (base != null) {
                    base.copy(id = id, teamId = def.id, name = slot.name)
                } else {
                    val s = slot.spec ?: NewSpec(PlayerRole.BATSMAN, 60, 20, BowlingStyle.PACE)
                    Player(
                        id = id, teamId = def.id, name = slot.name, role = s.role,
                        battingRating = s.bat, bowlingRating = s.bowl,
                        fieldingRating = CricketData.deriveFieldingRating(s.role, s.bat, s.bowl),
                        bowlingStyle = s.style
                    )
                }
                tags[id] = slot.tag
                player
            }.sortedByDescending { maxOf(it.battingRating, it.bowlingRating) }
            Team(
                id = def.id, name = def.name, countryCode = def.code,
                battingRating = avg(players.map { it.battingRating }.sortedDescending().take(8)),
                bowlingRating = avg(players.map { it.bowlingRating }.sortedDescending().take(5)),
                fieldingRating = avg(players.map { it.fieldingRating }),
                players = players
            )
        }
        return Pair(teams, tags)
    }

    private fun clashes(list: List<Pair<String, String>>): Int {
        var count = 0
        for (i in 1 until list.size) {
            val a = list[i - 1]
            val b = list[i]
            if (a.first == b.first || a.first == b.second || a.second == b.first || a.second == b.second) count++
        }
        return count
    }

    /**
     * The league: every pair meets twice (20 matches), in an order where a team seldom plays two
     * matches in a row, at the grounds in the order the 2026 league used them (5 at the SSC, 9 at
     * Dambulla, 6 at Pallekele); then the four play-offs at the R. Premadasa Stadium. Different every time.
     */
    fun buildSchedule(teamIds: List<String>, random: Random): List<TournamentFixture> {
        val pairs = ArrayList<Pair<String, String>>()
        for (i in teamIds.indices) for (j in i + 1 until teamIds.size) {
            pairs.add(teamIds[i] to teamIds[j])
            pairs.add(teamIds[j] to teamIds[i])
        }
        var best = pairs.shuffled(random)
        var bestScore = clashes(best)
        repeat(300) {
            val candidate = pairs.shuffled(random)
            val score = clashes(candidate)
            if (score < bestScore) {
                best = candidate
                bestScore = score
            }
        }
        val fixtures = ArrayList<TournamentFixture>()
        best.forEachIndexed { index, pair ->
            val ground = when {
                index < 5 -> SSC
                index < 14 -> DAMBULLA
                else -> PALLEKELE
            }
            fixtures.add(TournamentFixture(index, index + 1, CplData.LEAGUE, pair.first, pair.second, ground))
        }
        var next = fixtures.size
        fun playoff(stage: String, note: String) {
            fixtures.add(TournamentFixture(next, next + 1, stage, null, null, PREMADASA, placeholder = note))
            next++
        }
        playoff("Qualifier 1", "1st v 2nd in the table")
        playoff("Eliminator", "3rd v 4th in the table")
        playoff("Qualifier 2", "Loser of Qualifier 1 v winner of the Eliminator")
        playoff("Final", "Winners of Qualifier 1 and Qualifier 2")
        return fixtures
    }

    fun newTournament(userTeamId: String, built: Pair<List<Team>, Map<String, SquadTag>>, random: Random): TournamentState =
        TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = TOURNAMENT_ID,
            name = TOURNAMENT_NAME,
            userTeamId = userTeamId,
            teams = built.first,
            tags = built.second,
            fixtures = buildSchedule(built.first.map { it.id }, random),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )
}

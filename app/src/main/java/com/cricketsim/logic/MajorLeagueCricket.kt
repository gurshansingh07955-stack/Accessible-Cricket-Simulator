package com.cricketsim.logic

/**
 * MAJOR LEAGUE CRICKET 2026 (MLC season 4, 18 June to 18 July 2026; Los Angeles Knight Riders won the title).
 *
 *  - SIX TEAMS, the real ones: Los Angeles Knight Riders, MI New York, San Francisco Unicorns, Seattle Orcas,
 *    Texas Super Kings and Washington Freedom, with the 2026 squads (18 to 21 players each).
 *  - THE FORMAT, as played: a double round-robin (30 league matches, in the published order), then the
 *    Qualifier (1st v 2nd), the Eliminator (3rd v 4th), the Challenger (Qualifier loser v Eliminator winner) and the
 *    Final (Qualifier winner v Challenger winner): 34 matches, T20.
 *  - THE GROUNDS: Grand Prairie Stadium (matches 1 to 7 and 23 to 30), Oakland Coliseum (8 to 15 and all four
 *    play-offs) and the new Knight Riders Cricket Ground at Fairplex, Pomona (16 to 22, which the March
 *    schedule had at Broward County before the move). None was in the game; all three were added to StadiumData
 *    with estimated pitch and weather figures.
 *  - THE RULES used: at most six overseas players in the XI (so at least five US players). Squad tags USA
 *    and OVERSEAS carry this; the CPL's four-overseas rule does not apply. NOT built: the mandatory under-23
 *    US player, and the league's own points rules (the game's table gives 2 points a win).
 *
 * Players already in the game (its roster, or one added by the CPL, Abu Dhabi T10, LPL, ILT20 or the Big Bash)
 * are COPIED with their ratings; the others are new, with ESTIMATED ratings (the nw() lines; change them there).
 * Which players count as US players follows the league's own domestic lists as far as they were published; a
 * few are guesses. It reuses the CPL machinery as it is.
 */
object MajorLeagueCricket {

    const val TOURNAMENT_ID = "mlc2026"
    const val TOURNAMENT_NAME = "Major League Cricket 2026"

    private const val PRAIRIE = "grand-prairie"
    private const val OAKLAND = "oakland-coliseum"
    private const val POMONA = "knight-riders-ground"

    private class NewSpec(val role: PlayerRole, val bat: Int, val bowl: Int, val style: BowlingStyle)
    private class Slot(val name: String, val lookup: String?, val tag: SquadTag, val spec: NewSpec?)
    private class TeamDef(val id: String, val name: String, val shortName: String, val code: String, val home: String, val squad: List<Slot>)

    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN
    private val U = SquadTag.USA        // a US (domestic) player
    private val X = SquadTag.OVERSEAS   // an overseas player

    private fun ex(name: String, tag: SquadTag) = Slot(name, null, tag, null)
    private fun al(name: String, gameName: String, tag: SquadTag) = Slot(name, gameName, tag, null)
    private fun nw(name: String, tag: SquadTag, role: PlayerRole, bat: Int, bowl: Int, style: BowlingStyle = PACE) =
        Slot(name, null, tag, NewSpec(role, bat, bowl, style))

    private val DEFS: List<TeamDef> by lazy {
        listOf(
            TeamDef("mlc_lakr", "Los Angeles Knight Riders", "Knight Riders", "LAK", POMONA, listOf(
                ex("Jason Holder", X), ex("Alex Hales", X), ex("Andre Fletcher", X), ex("Rovman Powell", X), ex("Colin Munro", X),
                ex("Andre Russell", X), ex("Sunil Narine", X), ex("Lloyd Pope", X), ex("Shadley van Schalkwyk", X),
                nw("Unmukt Chand", U, BAT, 66, 10), ex("Ali Khan", U), nw("Saif Badar", U, BAT, 58, 15),
                nw("Kartikh Gatepalli", U, AR, 56, 30, SPIN), ex("Matthew Tromp", U), nw("Carmi le Roux", U, AR, 62, 62),
                ex("Jahmar Hamilton", U), ex("Nitish Kumar", U), nw("Kristopher Ramsaran", U, AR, 56, 55)
            )),
            TeamDef("mlc_miny", "MI New York", "MI New York", "MIN", PRAIRIE, listOf(
                ex("Nicholas Pooran", X), ex("Kieron Pollard", X), ex("Quinton de Kock", X), ex("Ryan Rickelton", X),
                ex("Shakib Al Hasan", X), ex("Trent Boult", X), ex("Corbin Bosch", X), ex("Romario Shepherd", X),
                nw("Tristan Luus", X, BAT, 60, 40), al("Allah Ghazanfar", "Allah Mohammad Ghazanfar", X),
                ex("Corey Anderson", U), ex("Monank Patel", U), ex("Tajinder Singh", U), nw("Ehsan Adil", U, BWL, 20, 64),
                ex("Nosthush Kenjige", U), nw("Agni Chopra", U, BAT, 62, 10), nw("Rushil Ugarkar", U, BWL, 20, 68),
                nw("Kunwarjeet Singh", U, AR, 56, 56), nw("Sunny Patel", U, AR, 54, 56, SPIN), nw("Faisal Ahmadzai", U, BWL, 20, 62)
            )),
            TeamDef("mlc_sfu", "San Francisco Unicorns", "Unicorns", "SFU", OAKLAND, listOf(
                ex("Matthew Short", X), ex("Finn Allen", X), ex("Cooper Connolly", X), ex("Aaron Hardie", X),
                nw("Lhuan-dre Pretorius", X, BAT, 84, 10), ex("Xavier Bartlett", X), ex("Haris Rauf", X), ex("Oliver Peake", X),
                ex("Ravichandran Ashwin", X), ex("Hassan Khan", U), ex("Brody Couch", U), ex("Sanjay Krishnamurthi", U),
                nw("Hammad Azam", U, AR, 55, 60), nw("Juanoy Drysdale", U, BWL, 20, 62), nw("Aakarshit Gomel", U, BAT, 54, 15),
                nw("Zia Ul-Haq", U, BWL, 20, 62), nw("Mohammad Ilyas", U, BWL, 20, 66), nw("Anirudh Immanuel", U, AR, 52, 52, SPIN),
                nw("Saideep Ganesh", U, AR, 54, 50, SPIN)
            )),
            TeamDef("mlc_so", "Seattle Orcas", "Orcas", "SEA", PRAIRIE, listOf(
                ex("Marcus Stoinis", X), ex("Tim Seifert", X), ex("Shimron Hetmyer", X), ex("Dasun Shanaka", X),
                ex("Matthew Breetzke", X), ex("Tim Robinson", X), ex("Lungi Ngidi", X), ex("Ottniel Baartman", X),
                ex("Tanveer Sangha", X), ex("Jason Behrendorff", X), nw("Raymon Reifer", X, AR, 58, 62),
                ex("Harmeet Singh", U), nw("Ayan Desai", U, AR, 56, 54, SPIN), nw("Rahul Jariwala", U, WK, 56, 10),
                ex("Shayan Jahangir", U), ex("Jessy Singh", U), nw("Ali Sheikh", U, BWL, 20, 60), nw("Cameron Gannon", U, BWL, 20, 66),
                nw("Sujit Nayak", U, BAT, 54, 15), nw("Shehan Jayasuriya", U, BAT, 60, 15), nw("Sharad Lumba", U, BAT, 56, 15)
            )),
            TeamDef("mlc_tsk", "Texas Super Kings", "Super Kings", "TSK", PRAIRIE, listOf(
                ex("Faf du Plessis", X), ex("Donovan Ferreira", X), ex("Wiaan Mulder", X), nw("Rilee Rossouw", X, BAT, 80, 10),
                ex("Adam Milne", X), ex("Nandre Burger", X), ex("Keshav Maharaj", X), ex("Akeal Hosein", X),
                nw("Hardus Viljoen", X, BWL, 20, 72), ex("Shubham Ranjane", U), nw("Joshua Tromp", U, AR, 54, 52),
                ex("Milind Kumar", U), nw("Saiteja Mukkamalla", U, BAT, 66, 10), nw("Mohammad Mohsin", U, BWL, 20, 60),
                nw("Calvin Savage", U, BAT, 58, 10), nw("Smit Patel", U, WK, 62, 10), ex("Amshi de Silva", U),
                nw("Abhimanyu Lamba", U, AR, 52, 50, SPIN)
            )),
            TeamDef("mlc_wf", "Washington Freedom", "Freedom", "WAS", PRAIRIE, listOf(
                ex("Steve Smith", X), ex("Glenn Maxwell", X), ex("Mitchell Owen", X), ex("Lockie Ferguson", X), ex("Marco Jansen", X),
                ex("Jack Edwards", X), ex("Rachin Ravindra", X), ex("Mark Chapman", X), ex("Ben Dwarshuis", X),
                ex("Andries Gous", U), ex("Saurabh Netravalkar", U), nw("Obus Pienaar", U, BWL, 20, 66), ex("Nikhil Chaudhary", U),
                nw("Mukhtar Ahmed", U, BAT, 60, 10), nw("Ian Holland", U, AR, 60, 66), nw("Lahiru Milantha", U, WK, 60, 10),
                nw("Amila Aponso", U, BWL, 20, 64, SPIN), nw("Abhishek Paradkar", U, BWL, 20, 60, SPIN), ex("Yasir Mohammad", U),
                nw("Asif Mehmood", U, AR, 52, 50)
            ))
        )
    }

    fun isTeam(teamId: String): Boolean = DEFS.any { it.id == teamId }

    fun shortNameOrNull(teamId: String): String? = DEFS.find { it.id == teamId }?.shortName

    fun homeGround(teamId: String): String = DEFS.find { it.id == teamId }?.home ?: PRAIRIE

    val stadiums: List<Stadium> by lazy { listOf(PRAIRIE, OAKLAND, POMONA).mapNotNull { StadiumData.getStadiumById(it) } }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' }

    private fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)

    /** The six franchises (strongest first); US players are tagged USA, everybody else OVERSEAS (at most six play). */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val roster = CricketData.getAllTeams().flatMap { it.players }.associateBy { norm(it.name) }
        val cpl = CplData.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val adt = AbuDhabiT10.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val lpl = LankaPremierLeague.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val ilt = InternationalLeagueT20.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val bbl = BigBashLeague.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val tags = LinkedHashMap<String, SquadTag>()
        val teams = DEFS.map { def ->
            val players = def.squad.mapIndexed { index, slot ->
                val id = "${def.id}_${index + 1}"
                val key = norm(slot.lookup ?: slot.name)
                val base = if (slot.spec == null) (roster[key] ?: cpl[key] ?: adt[key] ?: lpl[key] ?: ilt[key] ?: bbl[key]) else null
                val player = if (base != null) {
                    base.copy(id = id, teamId = def.id, name = slot.name)
                } else {
                    val s = slot.spec ?: NewSpec(PlayerRole.BATSMAN, 60, 20, BowlingStyle.PACE)
                    Player(
                        id = id, teamId = def.id, name = slot.name, role = PlayerProfiles.role(slot.name, s.role),
                        battingRating = s.bat, bowlingRating = s.bowl,
                        fieldingRating = CricketData.deriveFieldingRating(PlayerProfiles.role(slot.name, s.role), s.bat, s.bowl),
                        bowlingStyle = PlayerProfiles.style(slot.name, s.style)
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
        return Customisation.applyToBuilt(Pair(teams, tags), true)
    }

    // L Knight Riders, M MI New York, S Unicorns, O Orcas, T Super Kings, W Freedom: the 30 league matches in the
    // published order (the first team listed is the home side). Matches 1-7 and 23-30 at Grand Prairie, 8-15 at Oakland,
    // 16-22 at Pomona.
    private val LEAGUE_FIXTURES = listOf(
        "T O", "L S", "O W", "T S", "W M", "O L", "T M", "S T", "W O", "M T",
        "S O", "M L", "W T", "L O", "S W", "L W", "O M", "L T", "W S", "L M",
        "O T", "S M", "M S", "W L", "M O", "S L", "T W", "O S", "M W", "T L"
    )

    private val TEAM_OF = mapOf(
        'L' to "mlc_lakr", 'M' to "mlc_miny", 'S' to "mlc_sfu", 'O' to "mlc_so", 'T' to "mlc_tsk", 'W' to "mlc_wf"
    )

    /** The published league fixtures at their grounds, then Qualifier, Eliminator, Challenger and Final at Oakland. */
    fun buildSchedule(): List<TournamentFixture> {
        val fixtures = ArrayList<TournamentFixture>()
        LEAGUE_FIXTURES.forEachIndexed { index, code ->
            val p = code.split(" ")
            val number = index + 1
            val ground = when {
                number <= 7 || number >= 23 -> PRAIRIE
                number <= 15 -> OAKLAND
                else -> POMONA
            }
            fixtures.add(TournamentFixture(index, number, CplData.LEAGUE, TEAM_OF.getValue(p[0][0]), TEAM_OF.getValue(p[1][0]), ground))
        }
        var next = fixtures.size
        fun playoff(stage: String, note: String) {
            fixtures.add(TournamentFixture(next, next + 1, stage, null, null, OAKLAND, placeholder = note))
            next++
        }
        playoff("Qualifier", "1st v 2nd in the table")
        playoff("Eliminator", "3rd v 4th in the table")
        playoff("Challenger", "Loser of the Qualifier v winner of the Eliminator")
        playoff("Final", "Winner of the Qualifier v winner of the Challenger")
        return fixtures
    }

    fun newTournament(userTeamId: String, built: Pair<List<Team>, Map<String, SquadTag>>): TournamentState =
        TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = TOURNAMENT_ID,
            name = TOURNAMENT_NAME,
            userTeamId = userTeamId,
            teams = built.first,
            tags = built.second,
            fixtures = buildSchedule(),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )
}

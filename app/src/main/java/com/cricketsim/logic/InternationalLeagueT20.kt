package com.cricketsim.logic

/**
 * INTERNATIONAL LEAGUE T20 2026 (DP World ILT20 Season 5, 21 November to 20 December 2026).
 *
 *  - SIX TEAMS, the real ones: Abu Dhabi Knight Riders, Desert Vipers (the defending champions), Dubai
 *    Capitals, Gulf Giants, MI Emirates and Sharjah Warriorz, with the squads (22 or 23 players each)
 *    the franchises ended the October 2026 auction with.
 *  - THE FORMAT, as announced: 30 league matches (a double round-robin, in the REAL fixture order and at
 *    the real grounds: Dubai, Sharjah and Abu Dhabi), then Qualifier 1 (1st v 2nd), the Eliminator (3rd v 4th),
 *    Qualifier 2 and the Final: 34 matches, T20. Franchises have a base ground; the real list sends them around
 *    all three.
 *  - THE RULES used (the 2026 playing conditions): the XI must have at least two UAE players, at least two
 *    Afghanistan players and at least one further Associate player (another UAE player counts). There is no
 *    cap on overseas players. The squad tags UAE / AFGHAN / ASSOCIATE / OVERSEAS carry this; the CPL's
 *    four-overseas and breakout rules do not apply here. NOT built: the ILT20 Super-sub.
 *
 * Players already in the game (its roster, a CPL, Abu Dhabi T10 player) are COPIED with their ratings;
 * the others are new, with ESTIMATED ratings (the nw() lines; change them there). Nationalities are the ones
 * published at the auction (Gulf News list); a few UAE or Associate players' roles are guesses.
 * It reuses the CPL machinery as it is (TournamentState, CplSimulator, the tabs, saving, scorecards).
 */
object InternationalLeagueT20 {

    const val TOURNAMENT_ID = "ilt20_2026"
    const val TOURNAMENT_NAME = "International League T20 2026"

    private const val DUBAI = "dubai-international"
    private const val SHARJAH = "sharjah"
    private const val ABU_DHABI = "sheikh-zayed"

    private class NewSpec(val role: PlayerRole, val bat: Int, val bowl: Int, val style: BowlingStyle)
    private class Slot(val name: String, val lookup: String?, val tag: SquadTag, val spec: NewSpec?)
    private class TeamDef(val id: String, val name: String, val shortName: String, val code: String, val home: String, val squad: List<Slot>)

    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN
    private val X = SquadTag.OVERSEAS   // a player from another Full Member nation
    private val U = SquadTag.UAE
    private val F = SquadTag.AFGHAN
    private val A = SquadTag.ASSOCIATE  // USA, Kuwait, Saudi Arabia, Scotland, Netherlands and so on

    private fun ex(name: String, tag: SquadTag) = Slot(name, null, tag, null)
    private fun al(name: String, gameName: String, tag: SquadTag) = Slot(name, gameName, tag, null)
    private fun nw(name: String, tag: SquadTag, role: PlayerRole, bat: Int, bowl: Int, style: BowlingStyle = PACE) =
        Slot(name, null, tag, NewSpec(role, bat, bowl, style))

    private val DEFS: List<TeamDef> by lazy {
        listOf(
            TeamDef("ilt_adkr", "Abu Dhabi Knight Riders", "Knight Riders", "ADK", ABU_DHABI, listOf(
                nw("Abdul Mannan Ali", A, BAT, 52, 20), nw("Abdullah Tarakhail", F, BWL, 15, 62), ex("Ajay Kumar", U),
                ex("Alishan Sharafu", U), ex("Amshi De Silva", A), ex("Andre Fletcher", X), nw("Arab Gul", F, BWL, 15, 66),
                ex("Colin Munro", X), ex("Finn Allen", X), ex("Gulbadin Naib", F), nw("Ibrar Ahmed", U, BWL, 20, 60, SPIN),
                ex("Jakeem Pollard", X), ex("Jason Holder", X), ex("Lorcan Tucker", X),
                nw("Matthew Tromp", A, AR, 56, 60), nw("Michael Pepper", X, WK, 66, 10), ex("Moeen Ali", X),
                ex("Muhammad Rohid", U), ex("Qais Ahmad", F), nw("Ravija Sandaruwan", A, AR, 50, 50),
                ex("Sunil Narine", X), ex("Usman Tariq", X)
            )),
            TeamDef("ilt_vipers", "Desert Vipers", "Vipers", "DVI", DUBAI, listOf(
                nw("Abdullah Ahmadzai", F, BWL, 20, 62), ex("Andries Gous", A), ex("Babar Azam", X),
                nw("Bilal Tahir", A, AR, 52, 60, SPIN), nw("Dan Lawrence", X, BAT, 74, 30), nw("David Payne", X, BWL, 25, 76),
                ex("Fakhar Zaman", X), ex("Harry Tector", X), ex("Hassan Nawaz", X), nw("Ishtiaq Ahmed", A, BWL, 20, 58),
                nw("Joe Clarke", X, WK, 72, 10), ex("Khuzaima Bin Tanveer", U), ex("Mujeeb Ur Rahman", F),
                ex("Nangeyalia Kharote", F), ex("Naseem Shah", X), nw("Nilansh Keswani", U, BAT, 56, 15),
                nw("Saleem Safi", F, BWL, 20, 64), nw("Sanjay Pahal", U, BAT, 52, 20), ex("Shadab Khan", X),
                ex("Shimron Hetmyer", X), ex("Vriitya Aravind", U), ex("Wali Muhammad", U)
            )),
            TeamDef("ilt_capitals", "Dubai Capitals", "Capitals", "DUB", DUBAI, listOf(
                al("Aayan Khan", "Aayan Afzal Khan", U), nw("Anudeep Chenthamara", A, BAT, 52, 20), ex("Darwish Rasooli", F),
                ex("Dasun Shanaka", X), nw("Faf du Plessis", X, BAT, 82, 10), ex("Haider Ali", U), ex("Hassan Khan", A),
                ex("Karim Janat", F), ex("Luke Wood", X), ex("Marcus Stoinis", X), ex("Mark Adair", X),
                al("Masood Ul Rahman Gurbaz", "Masood Gurbaz", U), nw("Matt Potts", X, BWL, 20, 76), nw("Max Holden", X, BAT, 70, 10),
                ex("Muhammad Arfan", U), ex("Mustafizur Rahman", X), ex("Noor Ahmad", F), ex("Phil Salt", X),
                ex("Rovman Powell", X), ex("Sediqullah Atal", F), ex("Tom Moores", X), nw("Usman Najeeb", A, AR, 50, 50),
                nw("Zuhaib Zubair", U, BAT, 58, 15)
            )),
            TeamDef("ilt_giants", "Gulf Giants", "Giants", "GUL", DUBAI, listOf(
                ex("Alick Athanaze", X), ex("Alzarri Joseph", X), nw("Awais Ahmed", U, AR, 50, 50), ex("Azmatullah Omarzai", F),
                ex("Blessing Muzarabani", X), ex("Curtis Campher", X), ex("David Miller", X), ex("David Wiese", X),
                ex("Dhruv Parashar", U), ex("Haider Razzaq", U), ex("Heinrich Klaasen", X), ex("Ibrahim Zadran", F),
                nw("Imran Mir", F, AR, 50, 55, SPIN), nw("Imtiaz Khan", A, AR, 50, 52), ex("Liam Livingstone", X),
                nw("Matiullah Khan", U, BWL, 20, 60), nw("Meet Bhavsar", A, AR, 54, 50), ex("Rahmanullah Gurbaz", F),
                nw("Sagar Kalyan", U, BAT, 55, 15), ex("Shamar Joseph", X), nw("Shubham Ranjane", A, AR, 60, 52, SPIN),
                ex("Tabraiz Shamsi", X), nw("Wahidullah Zadran", U, BAT, 55, 15)
            )),
            TeamDef("ilt_mi", "MI Emirates", "MI Emirates", "MIE", ABU_DHABI, listOf(
                al("Allah Ghazanfar", "Allah Mohammad Ghazanfar", F), nw("Asa Tribe", X, AR, 56, 60, SPIN), ex("Chris Jordan", X),
                nw("Faridoon Dawoodzai", F, BWL, 20, 62), ex("Fazalhaq Farooqi", F), nw("James Coles", X, AR, 62, 60, SPIN),
                ex("Jonny Bairstow", X), nw("Jordan Thompson", X, BWL, 20, 72), ex("Kusal Perera", X), ex("Lizaad Williams", X),
                nw("Mohamed Shafeeq", A, BAT, 54, 15), ex("Muhammad Waseem", U), ex("Nicholas Pooran", X),
                al("Raizal Nadir Nather Shah", "Raizal Nadir", U), ex("Rashid Khan", F), ex("Romario Shepherd", X),
                ex("Ross Adair", X), ex("Sanjay Krishnamurthi", A), ex("Sherfane Rutherford", X),
                nw("Wasim Akram", U, AR, 52, 50), nw("Zain Ul Abidin", A, AR, 50, 52), ex("Zahoor Khan", U)
            )),
            TeamDef("ilt_warriorz", "Sharjah Warriorz", "Warriorz", "SHA", SHARJAH, listOf(
                ex("Adam Milne", X), ex("Adil Rashid", X), al("Ali Aamer Naseer", "Ali Naseer", U),
                nw("Arunraj Selvaraj", A, BAT, 52, 20), ex("Brandon McMullen", A), nw("Craig Overton", X, BWL, 30, 76),
                nw("Fareed Malik", F, BWL, 20, 62), ex("George Dockrell", X), ex("George Linde", X),
                nw("James Rew", X, WK, 66, 10), ex("James Vince", X), ex("Junaid Siddique", U),
                ex("Logan van Beek", A), ex("Mohammad Nabi", F), nw("Mohammad Saqib", A, AR, 50, 52),
                nw("Nabeel Aziz", U, BAT, 50, 20), ex("Paul Walter", X), ex("Richard Gleeson", X),
                ex("Sharafuddin Ashraf", F), ex("Sikandar Raza", X), nw("Tanish Suri", U, BAT, 58, 15),
                nw("Tom Banton", X, WK, 78, 10), ex("Waqar Salamkheil", F)
            ))
        )
    }

    fun isTeam(teamId: String): Boolean = DEFS.any { it.id == teamId }

    fun shortNameOrNull(teamId: String): String? = DEFS.find { it.id == teamId }?.shortName

    fun homeGround(teamId: String): String = DEFS.find { it.id == teamId }?.home ?: DUBAI

    val stadiums: List<Stadium> by lazy {
        listOf(DUBAI, SHARJAH, ABU_DHABI).mapNotNull { StadiumData.getStadiumById(it) }
    }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' }

    private fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)

    /** The six franchises (strongest first) with each player's nationality group as his tag. */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val roster = CricketData.getAllTeams().flatMap { it.players }.associateBy { norm(it.name) }
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
        return Pair(teams, tags)
    }

    // A=Knight Riders, V=Vipers, C=Capitals, G=Giants, M=MI Emirates, W=Warriorz; D=Dubai, S=Sharjah, Z=Abu Dhabi (Zayed).
    // The 30 league matches in the order of the published fixture list; the first team is listed first.
    private val LEAGUE_FIXTURES = listOf(
        "V M D", "W G S", "C A D", "W V S", "G M D", "C V D", "A M Z", "W C S", "M V Z", "G C D",
        "A W Z", "V G D", "M A Z", "G W S", "A V Z", "C M D", "G A D", "V W S", "A G Z", "M W Z",
        "V C D", "M G Z", "C W D", "W A S", "C G D", "W M S", "V A D", "M C Z", "G V D", "A C Z"
    )

    private val TEAM_OF = mapOf(
        'A' to "ilt_adkr", 'V' to "ilt_vipers", 'C' to "ilt_capitals", 'G' to "ilt_giants", 'M' to "ilt_mi", 'W' to "ilt_warriorz"
    )

    /** The published league fixtures at their real grounds, then the four play-offs (Zayed, Dubai, Sharjah, Dubai). */
    fun buildSchedule(): List<TournamentFixture> {
        val fixtures = ArrayList<TournamentFixture>()
        LEAGUE_FIXTURES.forEachIndexed { index, code ->
            val p = code.split(" ")
            val ground = when (p[2]) { "D" -> DUBAI; "S" -> SHARJAH; else -> ABU_DHABI }
            fixtures.add(TournamentFixture(index, index + 1, CplData.LEAGUE, TEAM_OF.getValue(p[0][0]), TEAM_OF.getValue(p[1][0]), ground))
        }
        var next = fixtures.size
        fun playoff(stage: String, ground: String, note: String) {
            fixtures.add(TournamentFixture(next, next + 1, stage, null, null, ground, placeholder = note))
            next++
        }
        playoff("Qualifier 1", ABU_DHABI, "1st v 2nd in the table")
        playoff("Eliminator", DUBAI, "3rd v 4th in the table")
        playoff("Qualifier 2", SHARJAH, "Loser of Qualifier 1 v winner of the Eliminator")
        playoff("Final", DUBAI, "Winners of Qualifier 1 and Qualifier 2")
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

package com.cricketsim.logic

/**
 * BIG BASH LEAGUE 2026-27 (BBL|16, 12 December 2026 to 26 January 2027).
 *
 *  - EIGHT TEAMS, the real ones: Adelaide Strikers, Brisbane Heat, Hobart Hurricanes, Melbourne Renegades,
 *    Melbourne Stars, Perth Scorchers (the defending champions), Sydney Sixers and Sydney Thunder. The squads
 *    are the 115 players confirmed by 15 October 2026 (12 to 17 each; the clubs still had places open, so
 *    the game's squads are as short as the real ones were).
 *  - THE FORMAT, as published: 40 league matches (each team plays ten) in the REAL fixture order at the
 *    real grounds, including the Chennai opener, then the top four play the Qualifier (1st v 2nd), the
 *    Knockout (3rd v 4th), the Challenger (Qualifier loser v Knockout winner) and the Final (Qualifier winner v
 *    Challenger winner): 44 matches, T20.
 *  - THE GROUNDS: ten of the eleven were already in the game; ENGIE Stadium (Sydney Showground), Junction
 *    Oval and the University of Tasmania Stadium (York Park) were added to StadiumData (ratings estimated).
 *    The finals grounds were not announced; here each final is played at the home ground of the higher-placed
 *    side (an assumption).
 *  - THE RULES used: at most three overseas players in a squad (so never more than three in the XI).
 *    The Power Surge is in (PowerSurge.kt); the designated batter and fielder and the over-rate timer are not.
 *
 * Players already in the game (its roster, a CPL / Abu Dhabi T10 / LPL player) are COPIED with their
 * ratings; the others are new, with ESTIMATED ratings (the nw() lines; change them there). It reuses the CPL
 * machinery as it is (TournamentState, CplSimulator, the tabs, saving, scorecards, Player of the Match).
 */
object BigBashLeague {

    const val TOURNAMENT_ID = "bbl2026_27"
    const val TOURNAMENT_NAME = "Big Bash League 2026-27"

    private const val CHENNAI = "chepauk"
    private const val GABBA = "gabba"
    private const val MANUKA = "manuka-oval"
    private const val HOBART = "bellerive-oval"
    private const val MCG = "mcg"
    private const val ENGIE = "sydney-showground"
    private const val OPTUS = "optus-stadium"
    private const val JUNCTION = "junction-oval"
    private const val SCG = "scg"
    private const val ADELAIDE = "adelaide-oval"
    private const val LAUNCESTON = "utas-stadium"

    private class NewSpec(val role: PlayerRole, val bat: Int, val bowl: Int, val style: BowlingStyle)
    private class Slot(val name: String, val lookup: String?, val tag: SquadTag, val spec: NewSpec?)
    private class TeamDef(val id: String, val name: String, val shortName: String, val code: String, val home: String, val squad: List<Slot>)

    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN
    private val D = SquadTag.DOMESTIC // Australian
    private val O = SquadTag.OVERSEAS

    private fun ex(name: String, tag: SquadTag = D) = Slot(name, null, tag, null)
    private fun al(name: String, gameName: String, tag: SquadTag = D) = Slot(name, gameName, tag, null)
    private fun nw(name: String, tag: SquadTag, role: PlayerRole, bat: Int, bowl: Int, style: BowlingStyle = PACE) =
        Slot(name, null, tag, NewSpec(role, bat, bowl, style))

    private val DEFS: List<TeamDef> by lazy {
        listOf(
            TeamDef("bbl_strikers", "Adelaide Strikers", "Strikers", "ADS", ADELAIDE, listOf(
                ex("Alex Carey"), ex("Mark Chapman", O), nw("Mackenzie Harvey", D, BAT, 66, 15), nw("Thomas Kelly", D, WK, 62, 10),
                nw("Lloyd Pope", D, BWL, 20, 74, SPIN), nw("Jason Sangha", D, BAT, 66, 20), nw("Liam Scott", D, AR, 60, 62),
                al("Matt Short", "Matthew Short"), ex("Ben Stokes", O), nw("Tom Straker", D, BWL, 20, 66),
                nw("Henry Thornton", D, BWL, 20, 74), nw("Jerrssis Wadia", D, BAT, 52, 15)
            )),
            TeamDef("bbl_heat", "Brisbane Heat", "Heat", "BRH", GABBA, listOf(
                ex("Xavier Bartlett"), ex("Max Bryant"), nw("Tom Curran", O, AR, 60, 76), nw("Lachlan Hearne", D, BAT, 56, 15),
                ex("Spencer Johnson"), nw("Usman Khawaja", D, BAT, 76, 10), nw("Matthew Kuhnemann", D, BWL, 20, 80, SPIN),
                nw("Nathan McSweeney", D, BAT, 70, 30, SPIN), nw("Michael Neser", D, AR, 62, 74), nw("Matt Renshaw", D, BAT, 72, 15),
                ex("Tim Robinson", O), nw("Callum Vidler", D, BWL, 20, 70), nw("Hugh Weibgen", D, BAT, 62, 15),
                nw("Jack Wildermuth", D, AR, 66, 66)
            )),
            TeamDef("bbl_hurricanes", "Hobart Hurricanes", "Hurricanes", "HBH", HOBART, listOf(
                nw("Marcus Bean", D, BWL, 20, 64, SPIN), ex("Rishad Hossain", O), nw("Nick Hobson", D, BAT, 72, 15),
                nw("Iain Carlisle", D, WK, 58, 10), ex("Nikhil Chaudhary"), ex("Tim David"), ex("Nathan Ellis"), ex("Shai Hope", O),
                ex("Ben McDermott"), nw("Riley Meredith", D, BWL, 20, 82), nw("Mitchell Owen", D, BAT, 80, 15),
                nw("Will Prestwidge", D, BWL, 20, 72), nw("Billy Stanlake", D, BWL, 20, 78), nw("Tim Ward", D, WK, 58, 10),
                nw("Jake Weatherald", D, BAT, 72, 10), nw("Beau Webster", D, AR, 72, 62, SPIN), ex("Adam Zampa")
            )),
            TeamDef("bbl_renegades", "Melbourne Renegades", "Renegades", "MLR", MCG, listOf(
                nw("Michael Archer", D, AR, 52, 60, SPIN), ex("Jonny Bairstow", O), nw("Ben Manenti", D, AR, 62, 56, SPIN),
                ex("Jason Behrendorff"), ex("Michael Bracewell", O), nw("Josh Brown", D, BAT, 68, 10), nw("Harry Dixon", D, BAT, 66, 15),
                nw("Brendan Doggett", D, BWL, 15, 72), nw("Sam Elliott", D, AR, 58, 60), ex("Jake Fraser-McGurk"),
                nw("Caleb Jewell", D, BAT, 62, 15), nw("Oliver Peake", D, AR, 58, 60, SPIN), ex("Tom Rogers"),
                nw("Callum Stow", D, AR, 50, 60), nw("Will Sutherland", D, AR, 70, 74)
            )),
            TeamDef("bbl_stars", "Melbourne Stars", "Stars", "MLS", MCG, listOf(
                ex("Finn Allen", O), nw("Austin Anlezark", D, AR, 52, 62), nw("Hilton Cartwright", D, AR, 68, 56),
                nw("Sam Harper", D, WK, 76, 10), nw("Liam Hatcher", D, BWL, 20, 72), nw("Campbell Kellaway", D, BAT, 66, 15),
                ex("Glenn Maxwell"), nw("Todd Murphy", D, BWL, 25, 72, SPIN), nw("Jamie Overton", O, AR, 56, 76),
                ex("Craig Overton", O), ex("Tom Rogers"), nw("Peter Siddle", D, BWL, 20, 66), ex("Marcus Stoinis"),
                nw("Mitchell Swepson", D, BWL, 20, 74, SPIN)
            )),
            TeamDef("bbl_scorchers", "Perth Scorchers", "Scorchers", "PRS", OPTUS, listOf(
                ex("Ashton Agar"), nw("Mahli Beardman", D, BWL, 20, 72), nw("Cooper Connolly", D, AR, 68, 52, SPIN),
                nw("Brody Couch", D, BWL, 20, 70), nw("Zak Crawley", O, BAT, 78, 10), nw("Joel Curtis", D, WK, 58, 10),
                ex("Aaron Hardie"), nw("Calvin Harrison", O, BAT, 64, 10), nw("Will Malajczuk", D, BWL, 20, 64),
                ex("Mitchell Marsh"), nw("Lance Morris", D, BWL, 20, 80), nw("Joel Paris", D, BWL, 20, 76),
                nw("Jhye Richardson", D, BWL, 20, 84), nw("Ashton Turner", D, BAT, 74, 25)
            )),
            TeamDef("bbl_sixers", "Sydney Sixers", "Sixers", "SYS", SCG, listOf(
                ex("Sean Abbott"), nw("Jack Edwards", D, AR, 60, 64), nw("Joel Davies", D, AR, 56, 54, SPIN),
                nw("Oliver Davies", D, BAT, 72, 10), ex("Ben Dwarshuis"), ex("Chris Jordan", O), nw("Hayden Kerr", D, AR, 62, 56, SPIN),
                nw("Riley Kingsell", D, BAT, 56, 15), nw("Fergus O'Neill", D, BWL, 20, 68), ex("Josh Philippe"),
                nw("Corey Rocchiccioli", D, BWL, 25, 66, SPIN), nw("Jake Scott", D, BAT, 56, 15), ex("Tim Seifert", O),
                nw("Lachlan Shaw", D, BAT, 64, 15), nw("Jordan Silk", D, BAT, 68, 15), ex("Steve Smith")
            )),
            TeamDef("bbl_thunder", "Sydney Thunder", "Thunder", "SYT", ENGIE, listOf(
                nw("Cameron Bancroft", D, BAT, 72, 10), ex("Sam Billings", O), ex("Trent Boult", O), ex("Lockie Ferguson", O),
                nw("Matthew Gilkes", D, WK, 64, 10), ex("Chris Green"), nw("Ryan Hadley", D, BAT, 66, 15),
                nw("Sam Konstas", D, BAT, 70, 10), nw("Aidan O'Connor", D, BWL, 20, 62), nw("Nic Maddinson", D, BAT, 72, 20),
                ex("Daniel Sams"), ex("Tanveer Sangha"), ex("David Warner")
            ))
        )
    }

    fun isTeam(teamId: String): Boolean = DEFS.any { it.id == teamId }

    fun shortNameOrNull(teamId: String): String? = DEFS.find { it.id == teamId }?.shortName

    fun homeGround(teamId: String): String = DEFS.find { it.id == teamId }?.home ?: MCG

    val stadiums: List<Stadium> by lazy {
        listOf(CHENNAI, GABBA, MANUKA, HOBART, MCG, ENGIE, OPTUS, JUNCTION, SCG, ADELAIDE, LAUNCESTON)
            .mapNotNull { StadiumData.getStadiumById(it) }
    }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' }

    private fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)

    /** The eight clubs (strongest first); overseas players are tagged OVERSEAS (the rule allows three in a squad). */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val roster = CricketData.getAllTeams().flatMap { it.players }.associateBy { norm(it.name) }
        val cpl = CplData.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val adt = AbuDhabiT10.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val lpl = LankaPremierLeague.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val tags = LinkedHashMap<String, SquadTag>()
        val teams = DEFS.map { def ->
            val players = def.squad.mapIndexed { index, slot ->
                val id = "${def.id}_${index + 1}"
                val key = norm(slot.lookup ?: slot.name)
                val base = if (slot.spec == null) (roster[key] ?: cpl[key] ?: adt[key] ?: lpl[key]) else null
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

    // The 40 league matches in the published order: home team, visiting team, ground.
    // ADS Strikers, BRH Heat, HBH Hurricanes, MLR Renegades, MLS Stars, PRS Scorchers, SYS Sixers, SYT Thunder.
    private val LEAGUE_FIXTURES = listOf(
        "MLR PRS CHN", "BRH SYS GAB", "SYT ADS MAN", "HBH BRH BEL", "MLS ADS MCG", "SYT SYS ENG", "PRS HBH OPT", "MLS BRH JUN",
        "SYS MLR SCG", "ADS SYT ADL", "MLR HBH JUN", "SYS BRH SCG", "PRS MLS OPT", "SYT MLR ENG", "BRH ADS GAB", "HBH MLS BEL",
        "PRS SYT OPT", "ADS MLS ADL", "MLR SYS MCG", "BRH HBH GAB", "SYT PRS ENG", "SYS ADS MAN", "MLS MLR MCG", "PRS BRH OPT",
        "HBH SYT BEL", "ADS MLR ADL", "MLS SYS MCG", "BRH PRS GAB", "HBH ADS LAU", "SYS PRS SCG", "MLR BRH JUN", "SYT MLS MAN",
        "ADS HBH ADL", "PRS MLR OPT", "MLS HBH MCG", "SYS SYT SCG", "ADS PRS ADL", "MLR MLS MCG", "HBH SYS BEL", "BRH SYT GAB"
    )

    private val TEAM_OF = mapOf(
        "ADS" to "bbl_strikers", "BRH" to "bbl_heat", "HBH" to "bbl_hurricanes", "MLR" to "bbl_renegades",
        "MLS" to "bbl_stars", "PRS" to "bbl_scorchers", "SYS" to "bbl_sixers", "SYT" to "bbl_thunder"
    )

    private val GROUND_OF = mapOf(
        "CHN" to CHENNAI, "GAB" to GABBA, "MAN" to MANUKA, "BEL" to HOBART, "MCG" to MCG, "ENG" to ENGIE,
        "OPT" to OPTUS, "JUN" to JUNCTION, "SCG" to SCG, "ADL" to ADELAIDE, "LAU" to LAUNCESTON
    )

    /** The published league fixtures, then the four finals (their teams and grounds are set once the table is known). */
    fun buildSchedule(): List<TournamentFixture> {
        val fixtures = ArrayList<TournamentFixture>()
        LEAGUE_FIXTURES.forEachIndexed { index, code ->
            val p = code.split(" ")
            fixtures.add(
                TournamentFixture(index, index + 1, CplData.LEAGUE, TEAM_OF.getValue(p[0]), TEAM_OF.getValue(p[1]), GROUND_OF.getValue(p[2]))
            )
        }
        var next = fixtures.size
        fun addFinal(stage: String, note: String) {
            fixtures.add(TournamentFixture(next, next + 1, stage, null, null, MCG, placeholder = note))
            next++
        }
        addFinal("Qualifier", "1st v 2nd in the table")
        addFinal("Knockout", "3rd v 4th in the table")
        addFinal("Challenger", "Loser of the Qualifier v winner of the Knockout")
        addFinal("Final", "Winner of the Qualifier v winner of the Challenger")
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

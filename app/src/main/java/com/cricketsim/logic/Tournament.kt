package com.cricketsim.logic

import kotlin.random.Random

/**
 * League tournaments (first one: CPL 2026). Pure Kotlin, no Android dependencies.
 *
 * WHAT IS HERE: the saved tournament's shape (TournamentState), the CPL 2026 data (seven
 * franchises, their 17-player squads, the venues), the random schedule, the points table and the
 * stat leaders. The screens live in ui/tournament/TournamentScreens.kt and the saving in
 * persistence/TournamentSaveStore.kt.
 *
 * SQUADS. Players already in the game's own roster (CricketData) are COPIED from it, with their
 * ratings, so nobody is recreated; the rest are new, with ratings chosen to sit on the same scale.
 * Player ids are fixed ("cpl_ant_3"), not random, so saved stats keep pointing at the right player.
 *
 * CHANGE-SAFETY. Every field added to a saved class later needs a default value, so older saves
 * still load (Gson skips initialisers).
 */

enum class SquadTag { DOMESTIC, BREAKOUT, OVERSEAS }

/** The result of one finished match. `winnerId` null means no result (rain). */
data class FixtureResult(
    val winnerId: String?,
    val summary: String,
    val firstBattingId: String,
    val firstRuns: Int,
    val firstWickets: Int,
    val firstBalls: Int,
    val secondRuns: Int,
    val secondWickets: Int,
    val secondBalls: Int
)

data class TournamentFixture(
    val id: Int,
    val number: Int,
    /** "League", "Qualifier 1", "Eliminator", "Qualifier 2" or "Final". */
    val stage: String,
    /** Null for a play-off whose teams are not known yet. */
    val homeId: String?,
    val awayId: String?,
    val stadiumId: String,
    /** Shown instead of team names while they are not known ("1st v 2nd"). */
    val placeholder: String = "",
    val result: FixtureResult? = null
)

/** One player's running totals across the tournament. */
data class PlayerStats(
    val playerId: String,
    val teamId: String,
    val name: String,
    val matches: Int = 0,
    val innings: Int = 0,
    val notOuts: Int = 0,
    val runs: Int = 0,
    val balls: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val highScore: Int = 0,
    val highScoreNotOut: Boolean = false,
    val fifties: Int = 0,
    val hundreds: Int = 0,
    val ballsBowled: Int = 0,
    val runsConceded: Int = 0,
    val wickets: Int = 0,
    val fiveWickets: Int = 0,
    val bestWickets: Int = 0,
    val bestRuns: Int = 0,
    val catches: Int = 0
)

data class TournamentState(
    val version: Int,
    val tournamentId: String,
    val name: String,
    val userTeamId: String,
    val teams: List<Team>,
    val tags: Map<String, SquadTag>,
    val fixtures: List<TournamentFixture>,
    val playerStats: Map<String, PlayerStats> = emptyMap(),
    val createdAt: Long = 0L
)

data class StandingRow(
    val team: Team,
    val played: Int,
    val won: Int,
    val lost: Int,
    val noResult: Int,
    val points: Int,
    val netRunRate: Double
)

data class LeaderEntry(val name: String, val teamName: String, val value: String)

data class StatCategory(val title: String, val entries: List<LeaderEntry>)

private class NewPlayer(val role: PlayerRole, val bat: Int, val bowl: Int, val style: BowlingStyle)

/** A squad slot: either a player copied from the game roster (spec null) or a new one. */
private class Entry(val name: String, val tag: SquadTag, val spec: NewPlayer?)

private class TeamDef(
    val id: String,
    val name: String,
    val shortName: String,
    val code: String,
    val homeStadiumId: String,
    val squad: List<Entry>
)

object CplData {

    const val TOURNAMENT_ID = "cpl2026"
    const val TOURNAMENT_NAME = "CPL 2026"
    const val SAVE_VERSION = 1
    const val LEAGUE = "League"

    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN

    // ---- Venues -------------------------------------------------------------------------------

    /** The one CPL ground the game did not have. */
    private val BRIAN_LARA = Stadium(
        id = "brian-lara-tarouba", name = "Brian Lara Stadium", city = "Tarouba, San Fernando", country = "West Indies",
        homeTeamIds = listOf("cpl_tkr"),
        pitchType = PitchType.DUSTY, pitchDescription = "A dry, slow surface that grips and turns for the spinners.",
        boundarySize = BoundarySize.MEDIUM, altitudeM = 10, dewFactor = 0.3,
        avgT20FirstInningsScore = 160,
        climate = StadiumClimate(0.3, 72, 29, 15, "Warm and humid, with a moderate chance of rain.")
    )

    /** Every West Indies ground in the game, plus the one added above. */
    val stadiums: List<Stadium> by lazy {
        listOf(
            "kensington-oval", "sabina-park", "providence-stadium", "vivian-richards-stadium", "warner-park",
            "daren-sammy-st-lucia", "arnos-vale-st-vincent", "queens-park-oval", "national-stadium-grenada"
        ).mapNotNull { StadiumData.getStadiumById(it) } + BRIAN_LARA
    }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    /** Grounds that are nobody's home: a few league matches are played there, as the real CPL tours. */
    private val NEUTRAL_VENUES = listOf("arnos-vale-st-vincent", "queens-park-oval", "national-stadium-grenada")

    // ---- Squads -------------------------------------------------------------------------------

    private fun ex(name: String, tag: SquadTag = SquadTag.DOMESTIC) = Entry(name, tag, null)

    private fun nw(
        name: String, tag: SquadTag, role: PlayerRole, bat: Int, bowl: Int, style: BowlingStyle = BowlingStyle.PACE
    ) = Entry(name, tag, NewPlayer(role, bat, bowl, style))

    private val D = SquadTag.DOMESTIC
    private val B = SquadTag.BREAKOUT
    private val O = SquadTag.OVERSEAS

    private val DEFS: List<TeamDef> by lazy {
        listOf(
            TeamDef("cpl_ant", "Antigua & Barbuda Falcons", "Falcons", "ANT", "vivian-richards-stadium", listOf(
                ex("Alzarri Joseph"), ex("Evin Lewis"), nw("Fabian Allen", D, AR, 62, 72, SPIN), ex("Jayden Seales"),
                ex("Amir Jangoo"), nw("Shamar Springer", D, BWL, 25, 74), nw("Jahmar Hamilton", D, WK, 68, 10),
                nw("Rahkeem Cornwall", D, AR, 70, 68, SPIN), nw("Anderson Phillip", D, BWL, 20, 72),
                nw("Karima Gore", B, AR, 55, 62, SPIN), nw("Anderson Mahase", B, AR, 50, 60), nw("Joshua James", B, BWL, 20, 62),
                ex("Moeen Ali", O), ex("Kusal Perera", O), ex("Shadab Khan", O),
                nw("Sufiyan Muqeem", O, BWL, 15, 78, SPIN), ex("Milind Kumar", O)
            )),
            TeamDef("cpl_bar", "Barbados Tridents", "Tridents", "BAR", "kensington-oval", listOf(
                ex("Gudakesh Motie"), ex("Sherfane Rutherford"), ex("Brandon King"), nw("Zachary Carter", D, BAT, 58, 20),
                nw("Kadeem Alleyne", D, AR, 62, 60), nw("Ramon Simmonds", D, AR, 62, 64), nw("Shadrack Descarte", D, BWL, 20, 66),
                nw("Zishan Motara", D, BWL, 25, 66, SPIN), nw("Rivaldo Clarke", D, AR, 60, 60, SPIN),
                nw("Jakeem Pollard", B, BAT, 50, 20), nw("Johann Layne", B, BWL, 20, 66), nw("Kofi James", B, BWL, 20, 64),
                ex("Quinton de Kock", O), nw("Chris Green", O, AR, 55, 76, SPIN), ex("Mujeeb ur Rahman", O),
                nw("George Linde", O, AR, 62, 74, SPIN), nw("Daniel Sams", O, AR, 66, 76)
            )),
            TeamDef("cpl_guy", "Guyana Amazon Warriors", "Amazon Warriors", "GUY", "providence-stadium", listOf(
                ex("Shimron Hetmyer"), ex("Romario Shepherd"), ex("Shai Hope"), ex("Khary Pierre"), ex("Shamar Joseph"),
                nw("Ronaldo Alimohamed", D, AR, 55, 66, SPIN), nw("Veerasammy Permaul", D, BWL, 22, 70, SPIN),
                nw("Matthew Nandu", D, BAT, 64, 20), nw("Jonathan van Lange", D, BAT, 60, 20),
                nw("Mavendra Dindyal", B, BWL, 20, 62, SPIN), nw("Isai Thorne", B, BWL, 20, 62), nw("Quentin Sampson", B, BAT, 55, 20),
                nw("Imran Tahir", O, BWL, 15, 86, SPIN), ex("Glenn Phillips", O), ex("Mohammad Nabi", O),
                ex("Rahmanullah Gurbaz", O), nw("Dwaine Pretorius", O, AR, 62, 72)
            )),
            TeamDef("cpl_jam", "Jamaica Kingsmen", "Kingsmen", "JAM", "sabina-park", listOf(
                ex("Rovman Powell"), ex("Andre Russell"), nw("Keemo Paul", D, AR, 62, 74), ex("Keacy Carty"),
                nw("Jediah Blades", D, AR, 58, 66), nw("Shaqkere Parris", D, BAT, 66, 30), ex("Odean Smith"),
                nw("Vitel Lawes", D, BWL, 15, 66, SPIN), nw("Romaine Morris", D, BWL, 20, 62),
                ex("Kirk McKenzie", B), nw("Kelvin Pitman", B, BAT, 52, 20), nw("Jeavor Royal", B, WK, 60, 10),
                ex("Saim Ayub", O), nw("Maaz Sadaqat", O, AR, 74, 55, SPIN), ex("Usman Khan", O),
                nw("Hassan Khan", O, AR, 66, 70, SPIN), nw("Hunain Shah", O, BWL, 15, 78)
            )),
            TeamDef("cpl_skn", "St Kitts & Nevis Patriots", "Patriots", "SKN", "warner-park", listOf(
                ex("Johnson Charles"), ex("Jason Holder"), ex("Kyle Mayers"), nw("Andre Fletcher", D, WK, 74, 10),
                nw("Kevin Wickham", D, BAT, 70, 20), ex("Obed McCoy"), nw("Ashmead Nedd", D, BWL, 25, 70, SPIN),
                nw("Jeremiah Louis", D, BWL, 20, 72), ex("Alick Athanaze"),
                nw("Micah McKenzie", B, BAT, 52, 15), nw("Navin Bidaisee", B, BWL, 25, 66, SPIN), nw("Mikyle Louis", B, BWL, 20, 64),
                ex("Naseem Shah", O), ex("Dasun Shanaka", O), nw("Waqar Salamkheil", O, BWL, 15, 74, SPIN),
                ex("Wanindu Hasaranga", O), nw("Nikhil Chaudhary", O, AR, 66, 55, SPIN)
            )),
            TeamDef("cpl_luc", "Saint Lucia Kings", "Kings", "LUC", "daren-sammy-st-lucia", listOf(
                ex("Roston Chase"), nw("Jewel Andrew", D, BAT, 62, 10), ex("Matthew Forde"), nw("Kamil Pooran", D, BAT, 58, 10),
                nw("Darron Nedd", D, BWL, 20, 66), nw("McKenny Clarke", D, AR, 60, 62, SPIN), nw("Joshua Bishop", D, BWL, 25, 72),
                nw("Damion Joachim", D, BWL, 20, 64), nw("Keon Gaston", D, BAT, 56, 15),
                nw("Amari Goodridge", B, BWL, 20, 62), nw("Johann Jeremiah", B, BAT, 52, 20), nw("Ackeem Auguste", B, BWL, 20, 64),
                ex("Noor Ahmad", O), nw("Tim Seifert", O, WK, 82, 10), ex("Maheesh Theekshana", O),
                ex("Charith Asalanka", O), ex("Shadley van Schalkwyk", O)
            )),
            TeamDef("cpl_tkr", "Trinbago Knight Riders", "Knight Riders", "TKR", "brian-lara-tarouba", listOf(
                nw("Sunil Narine", D, AR, 62, 90, SPIN), ex("Nicholas Pooran"), nw("Kieron Pollard", D, AR, 78, 62),
                ex("Akeal Hosein"), ex("Justin Greaves"), nw("Dominic Drakes", D, AR, 66, 68), nw("Jyd Goolie", D, BAT, 60, 15),
                nw("Dexter Sween", D, BAT, 58, 10), nw("Terrance Hinds", D, AR, 55, 62, SPIN),
                nw("Nathan Edward", B, BWL, 15, 72), nw("Joshua da Silva", B, WK, 72, 10), nw("Abdul-Raheem Toppin", B, AR, 50, 64),
                nw("Alex Hales", O, BAT, 86, 10), nw("Usman Tariq", O, BWL, 15, 84, SPIN), nw("Colin Munro", O, BAT, 82, 15),
                ex("Matthew Breetzke", O), nw("Amshi de Silva", O, AR, 62, 55, SPIN)
            ))
        )
    }

    fun shortName(teamId: String): String = DEFS.find { it.id == teamId }?.shortName ?: teamId

    fun homeStadiumId(teamId: String): String = DEFS.find { it.id == teamId }?.homeStadiumId ?: "kensington-oval"

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' }

    private fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)

    /** The seven franchises, each with its players (strongest first), and who is overseas / a breakout player. */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val roster = CricketData.getAllTeams().flatMap { it.players }.associateBy { norm(it.name) }
        val tags = LinkedHashMap<String, SquadTag>()
        val teams = DEFS.map { def ->
            val players = def.squad.mapIndexed { index, entry ->
                val id = "${def.id}_${index + 1}"
                val base = roster[norm(entry.name)]
                val player = if (entry.spec == null && base != null) {
                    base.copy(id = id, teamId = def.id)
                } else {
                    val s = entry.spec ?: NewPlayer(PlayerRole.BATSMAN, 60, 20, BowlingStyle.PACE)
                    Player(
                        id = id, teamId = def.id, name = entry.name, role = s.role,
                        battingRating = s.bat, bowlingRating = s.bowl,
                        fieldingRating = CricketData.deriveFieldingRating(s.role, s.bat, s.bowl),
                        bowlingStyle = s.style
                    )
                }
                tags[id] = entry.tag
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

    // ---- Schedule -----------------------------------------------------------------------------

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
     * A random CPL-style schedule for seven teams: 35 league matches (everyone plays ten: six
     * opponents once and four of them twice, five at home), then Qualifier 1, the Eliminator,
     * Qualifier 2 and the Final at Kensington Oval. Different every time. Mostly each match is at
     * the home team's ground; six are moved to touring grounds nobody calls home.
     */
    fun buildSchedule(teamIds: List<String>, random: Random): List<TournamentFixture> {
        val s = teamIds.shuffled(random)
        val n = s.size
        val pairs = ArrayList<Pair<String, String>>() // home to away
        for (i in 0 until n) for (d in 1..3) pairs.add(s[i] to s[(i + d) % n])
        for (i in 0 until n) {
            pairs.add(s[(i + 1) % n] to s[i])
            pairs.add(s[(i + 2) % n] to s[i])
        }
        // Many shuffles, keep the one where a team least often plays two matches in a row.
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
        val touring = (0 until best.size).shuffled(random).take(6)
        val fixtures = ArrayList<TournamentFixture>()
        best.forEachIndexed { index, pair ->
            val touringSlot = touring.indexOf(index)
            val venue = if (touringSlot >= 0) NEUTRAL_VENUES[touringSlot % NEUTRAL_VENUES.size] else homeStadiumId(pair.first)
            fixtures.add(TournamentFixture(index, index + 1, LEAGUE, pair.first, pair.second, venue))
        }
        val finalVenue = "kensington-oval"
        var next = fixtures.size
        fun playoff(stage: String, note: String) {
            fixtures.add(TournamentFixture(next, next + 1, stage, null, null, finalVenue, placeholder = note))
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
            version = SAVE_VERSION,
            tournamentId = TOURNAMENT_ID,
            name = TOURNAMENT_NAME,
            userTeamId = userTeamId,
            teams = built.first,
            tags = built.second,
            fixtures = buildSchedule(built.first.map { it.id }, random),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )

    // ---- Points table -------------------------------------------------------------------------

    /** League matches only: 2 points for a win, 1 each for no result; run rate counts a full 20 overs when all out. */
    fun standings(state: TournamentState): List<StandingRow> {
        class Acc {
            var played = 0
            var won = 0
            var lost = 0
            var noResult = 0
            var runsFor = 0
            var ballsFaced = 0
            var runsAgainst = 0
            var ballsBowled = 0
        }
        val acc = state.teams.associate { it.id to Acc() }
        for (f in state.fixtures) {
            if (f.stage != LEAGUE) continue
            val r = f.result ?: continue
            val home = f.homeId ?: continue
            val away = f.awayId ?: continue
            val a = acc[home] ?: continue
            val b = acc[away] ?: continue
            a.played++
            b.played++
            if (r.winnerId == null) {
                a.noResult++
                b.noResult++
                continue
            }
            if (r.winnerId == home) { a.won++; b.lost++ } else { b.won++; a.lost++ }
            val firstIsHome = r.firstBattingId == home
            val first = if (firstIsHome) a else b
            val second = if (firstIsHome) b else a
            val firstBalls = if (r.firstWickets >= 10) 120 else r.firstBalls
            val secondBalls = if (r.secondWickets >= 10) 120 else r.secondBalls
            first.runsFor += r.firstRuns
            first.ballsFaced += firstBalls
            second.runsAgainst += r.firstRuns
            second.ballsBowled += firstBalls
            second.runsFor += r.secondRuns
            second.ballsFaced += secondBalls
            first.runsAgainst += r.secondRuns
            first.ballsBowled += secondBalls
        }
        return state.teams.map { team ->
            val x = acc.getValue(team.id)
            val rate = if (x.ballsFaced > 0 && x.ballsBowled > 0) {
                x.runsFor * 6.0 / x.ballsFaced - x.runsAgainst * 6.0 / x.ballsBowled
            } else 0.0
            StandingRow(team, x.played, x.won, x.lost, x.noResult, x.won * 2 + x.noResult, rate)
        }.sortedWith(
            compareByDescending<StandingRow> { it.points }
                .thenByDescending { it.won }
                .thenByDescending { it.netRunRate }
                .thenBy { it.team.name }
        )
    }

    // ---- Stats --------------------------------------------------------------------------------

    private fun twoDecimals(x: Double): String = String.format(java.util.Locale.US, "%.2f", x)

    /** Leaders for the Stats tab, from whichever players are passed in ("my team" or "all"). */
    fun statCategories(stats: Collection<PlayerStats>, teamNameOf: (String) -> String, top: Int = 5): List<StatCategory> {
        fun entries(list: List<PlayerStats>, value: (PlayerStats) -> String): List<LeaderEntry> =
            list.take(top).map { LeaderEntry(it.name, teamNameOf(it.teamId), value(it)) }

        val batters = stats.filter { it.innings > 0 }
        val bowlers = stats.filter { it.ballsBowled > 0 }
        return listOf(
            StatCategory("Most runs", entries(
                batters.filter { it.runs > 0 }.sortedWith(compareByDescending<PlayerStats> { it.runs }.thenBy { it.balls })
            ) { "${it.runs} runs" }),
            StatCategory("Highest score", entries(
                batters.filter { it.highScore > 0 }.sortedByDescending { it.highScore }
            ) { "${it.highScore}${if (it.highScoreNotOut) " not out" else ""}" }),
            StatCategory("Most fifties", entries(
                batters.filter { it.fifties > 0 }.sortedByDescending { it.fifties }
            ) { "${it.fifties}" }),
            StatCategory("Most hundreds", entries(
                batters.filter { it.hundreds > 0 }.sortedByDescending { it.hundreds }
            ) { "${it.hundreds}" }),
            StatCategory("Most fours", entries(
                batters.filter { it.fours > 0 }.sortedByDescending { it.fours }
            ) { "${it.fours}" }),
            StatCategory("Most sixes", entries(
                batters.filter { it.sixes > 0 }.sortedByDescending { it.sixes }
            ) { "${it.sixes}" }),
            StatCategory("Best strike rate (30 balls or more)", entries(
                batters.filter { it.balls >= 30 }.sortedByDescending { it.runs * 100.0 / it.balls }
            ) { twoDecimals(it.runs * 100.0 / it.balls) }),
            StatCategory("Most wickets", entries(
                bowlers.filter { it.wickets > 0 }.sortedWith(compareByDescending<PlayerStats> { it.wickets }.thenBy { it.runsConceded })
            ) { "${it.wickets} wickets" }),
            StatCategory("Most five-wicket hauls", entries(
                bowlers.filter { it.fiveWickets > 0 }.sortedByDescending { it.fiveWickets }
            ) { "${it.fiveWickets}" }),
            StatCategory("Best economy (2 overs or more)", entries(
                bowlers.filter { it.ballsBowled >= 12 }.sortedBy { it.runsConceded * 6.0 / it.ballsBowled }
            ) { twoDecimals(it.runsConceded * 6.0 / it.ballsBowled) + " runs an over" }),
            StatCategory("Best bowling spell", entries(
                bowlers.filter { it.bestWickets > 0 }.sortedWith(compareByDescending<PlayerStats> { it.bestWickets }.thenBy { it.bestRuns })
            ) { "${it.bestWickets} for ${it.bestRuns}" }),
            StatCategory("Most catches", entries(
                stats.filter { it.catches > 0 }.sortedByDescending { it.catches }
            ) { "${it.catches}" })
        )
    }
}

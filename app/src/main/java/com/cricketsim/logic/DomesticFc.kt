package com.cricketsim.logic

/**
 * The two small first-class tournaments: the Duleep Trophy 2026-27 (six zones: two quarter-finals, two semi-finals and the
 * Final, four days each) and the Irani Cup 2026-27 (one five-day match between the Ranji Trophy champions, Jammu & Kashmir, and Rest
 * of India). They use the same first-class machinery as the Ranji Trophy (logic/Ranji.kt, FirstClass.kt, TestMatch.kt): play your own
 * match or let the computer play it; a drawn knockout goes to the side ahead on the first innings. See DOMESTIC_FIRST_CLASS.md.
 *
 * Squads are the ones named for the tournaments (Wikipedia, Wisden, Sportsdunia). A player already in the game (the roster, the
 * leagues, the Ranji Trophy) is copied with his ratings; the rest are new with ESTIMATED ratings from the team's strength, the role and
 * a steady number made from the name. The roles and bowling styles written here are from general knowledge of the players or, where
 * unknown, from the order of the squad list.
 */
object DomesticFc {

    const val DULEEP_ID = "duleep_2026_27"
    const val DULEEP_NAME = "Duleep Trophy 2026-27"
    const val IRANI_ID = "irani_2026_27"
    const val IRANI_NAME = "Irani Cup 2026-27"
    const val DULEEP_DAYS = 4
    const val IRANI_DAYS = 5

    fun isSmall(id: String?): Boolean = id == DULEEP_ID || id == IRANI_ID

    /** The Ranji Trophy, the Duleep Trophy or the Irani Cup: matches played with the first-class rules. */
    fun isFirstClass(id: String?): Boolean = Ranji.isRanji(id) || isSmall(id)

    fun daysFor(id: String?, stage: String): Int = when (id) {
        IRANI_ID -> IRANI_DAYS
        DULEEP_ID -> DULEEP_DAYS
        else -> Ranji.daysFor(stage)
    }

    /** In a knockout a drawn match goes to the first-innings leader; only the Ranji league stage can be drawn. */
    fun isKnockout(id: String?, stage: String): Boolean = if (Ranji.isRanji(id)) !Ranji.isGroupStage(stage) else true

    fun rulesFor(id: String?, stage: String?): MatchRules? =
        if (isFirstClass(id)) TestMatch.rulesForDays(daysFor(id, stage ?: Ranji.STAGES[0])) else null

    // ---------------------------------------------------------------- grounds

    private fun ground(id: String, name: String, city: String, altitude: Int): Stadium = Stadium(
        id = id, name = name, city = city, country = "India", homeTeamIds = emptyList(),
        pitchType = PitchType.BALANCED, pitchDescription = "A balanced surface with something for everyone.",
        boundarySize = BoundarySize.MEDIUM, altitudeM = altitude, dewFactor = 0.25, avgT20FirstInningsScore = 160,
        climate = StadiumClimate(0.2, 55, 27, 10, "Typical conditions for the Indian domestic season.")
    )

    // The BCCI Centre of Excellence grounds (estimated ratings). The Final is at Chepauk and the Irani Cup at Srinagar: both are in Ranji.stadiums.
    private val coe1: Stadium by lazy { ground("coe1", "BCCI Centre of Excellence Ground 1", "Bengaluru", 900) }
    private val coe2: Stadium by lazy { ground("coe2", "BCCI Centre of Excellence Ground 2", "Bengaluru", 900) }

    fun stadium(id: String): Stadium? = when (id) {
        "coe1" -> coe1
        "coe2" -> coe2
        else -> null
    }

    fun stadiumsFor(tournamentId: String): List<Stadium> =
        if (tournamentId == IRANI_ID) listOfNotNull(Ranji.stadium("srinagar"))
        else listOf(coe1, coe2) + listOfNotNull(Ranji.stadium("chepauk"))

    // ---------------------------------------------------------------- teams

    private class Entry(val name: String, val flags: String, val role: Char, val bowl: String)

    private class Def(val id: String, val name: String, val shortName: String, val home: String, val tier: Int, val squad: List<Entry>)

    private fun parseEntry(text: String): Entry {
        val p = text.split("~")
        return Entry(p[0].trim(), p.getOrElse(1) { "" }, p.getOrNull(2)?.firstOrNull() ?: '-', p.getOrNull(4) ?: "")
    }

    private fun parseDefs(text: String): List<Def> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        val p = line.split("|")
        Def(p[0], p[1], p[2], p[3], p[4].toInt(), p[5].split(";").map { parseEntry(it) })
    }

    private val DULEEP_DEFS: List<Def> by lazy { parseDefs(DULEEP_TEXT) }
    private val IRANI_DEFS: List<Def> by lazy { parseDefs(IRANI_TEXT) }
    private val ALL_DEFS: List<Def> by lazy { DULEEP_DEFS + IRANI_DEFS }

    fun isTeam(teamId: String): Boolean = ALL_DEFS.any { it.id == teamId }

    fun shortNameOrNull(teamId: String): String? = ALL_DEFS.find { it.id == teamId }?.shortName

    fun homeGround(teamId: String): String = ALL_DEFS.find { it.id == teamId }?.home ?: "srinagar"

    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' }

    private fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)

    private fun jit(name: String, salt: Int, range: Int): Int {
        var h = salt * 7919
        for (c in name) h = h * 31 + c.code
        return (h and 0x7fffffff) % (2 * range + 1) - range
    }

    private fun assignRoles(entries: List<Entry>): List<PlayerRole> {
        val n = entries.size
        val roles = entries.mapIndexed { i, e ->
            when {
                e.flags.contains("wk") || e.role == 'W' -> WK
                e.role == 'B' -> BAT
                e.role == 'A' -> AR
                e.role == 'L' -> BWL
                i < (n * 4) / 10 -> BAT
                i < (n * 6) / 10 -> AR
                else -> BWL
            }
        }.toMutableList()
        if (roles.none { it == WK }) {
            val pick = (0 until n).firstOrNull { roles[it] == BAT && entries[it].role != 'B' } ?: (0 until n).firstOrNull { roles[it] == BAT } ?: 0
            roles[pick] = WK
        }
        var need = 5 - roles.count { it == BWL || it == AR }
        var k = n - 1
        while (need > 0 && k >= 0) {
            if (roles[k] == BAT && entries[k].role != 'B') { roles[k] = BWL; need-- }
            k--
        }
        k = n - 1
        while (need > 0 && k >= 0) {
            if (roles[k] == BAT) { roles[k] = BWL; need-- }
            k--
        }
        return roles
    }

    private fun styleOf(code: String, name: String, role: PlayerRole): BowlingStyle = when (code) {
        "RF", "RFM", "RMF", "RM", "LF", "LFM", "LMF", "LM" -> PACE
        "OB", "LB", "SLA", "SLC" -> SPIN
        else -> if (role == BWL || role == AR) (if (jit(name, 3, 4) % 3 == 0) SPIN else PACE) else PACE
    }

    /** Everyone the game already has by name: the roster, the leagues and the Ranji Trophy squads. */
    private fun knownPlayers(): Map<String, Player> {
        val out = HashMap<String, Player>()
        fun addAll(players: List<Player>) {
            for (p in players) out.putIfAbsent(norm(p.name), p)
        }
        addAll(CricketData.getAllTeams().flatMap { it.players })
        addAll(Ranji.buildTeams().first.flatMap { it.players })
        addAll(CplData.buildTeams().first.flatMap { it.players })
        addAll(AbuDhabiT10.buildTeams().first.flatMap { it.players })
        addAll(LankaPremierLeague.buildTeams().first.flatMap { it.players })
        addAll(InternationalLeagueT20.buildTeams().first.flatMap { it.players })
        addAll(BigBashLeague.buildTeams().first.flatMap { it.players })
        addAll(MajorLeagueCricket.buildTeams().first.flatMap { it.players })
        addAll(Sa20.buildTeams().first.flatMap { it.players })
        return out
    }

    private fun build(defs: List<Def>): Pair<List<Team>, Map<String, SquadTag>> {
        val known = knownPlayers()
        val tags = LinkedHashMap<String, SquadTag>()
        val teams = defs.map { def ->
            val roles = assignRoles(def.squad)
            val tierDelta = when (def.tier) { 5 -> 7; 4 -> 3; 3 -> 0; 2 -> -4; else -> -9 }
            val base = 56 + tierDelta
            val players = def.squad.mapIndexed { index, e ->
                val id = "${def.id}_${index + 1}"
                val found = known[norm(e.name)]
                val player = if (found != null) {
                    found.copy(id = id, teamId = def.id, name = e.name)
                } else {
                    val role = roles[index]
                    val bat = when (role) {
                        BAT -> base + 12 + jit(e.name, 1, 6) + (if (index < 6) 2 else 0)
                        WK -> base + 9 + jit(e.name, 1, 6)
                        AR -> base + 3 + jit(e.name, 1, 6)
                        else -> base - 22 + jit(e.name, 1, 5)
                    }.coerceIn(8, 92)
                    val bowl = when (role) {
                        BWL -> base + 8 + jit(e.name, 2, 6)
                        AR -> base + 1 + jit(e.name, 2, 6)
                        WK -> 8
                        else -> 14 + jit(e.name, 2, 5)
                    }.coerceIn(5, 92)
                    val fixed = PlayerProfiles.role(e.name, role)
                    Player(
                        id = id, teamId = def.id, name = e.name, role = fixed,
                        battingRating = bat, bowlingRating = bowl,
                        fieldingRating = CricketData.deriveFieldingRating(fixed, bat, bowl),
                        bowlingStyle = PlayerProfiles.style(e.name, styleOf(e.bowl, e.name, role))
                    )
                }
                tags[id] = SquadTag.DOMESTIC
                player
            }.sortedByDescending { maxOf(it.battingRating, it.bowlingRating) }
            Team(
                id = def.id, name = def.name, countryCode = def.shortName,
                battingRating = avg(players.map { it.battingRating }.sortedDescending().take(8)),
                bowlingRating = avg(players.map { it.bowlingRating }.sortedDescending().take(5)),
                fieldingRating = avg(players.map { it.fieldingRating }),
                players = players
            )
        }
        return Customisation.applyToBuilt(Pair(teams, tags), true)
    }

    fun buildDuleep(): Pair<List<Team>, Map<String, SquadTag>> = build(DULEEP_DEFS)

    fun buildIrani(): Pair<List<Team>, Map<String, SquadTag>> = build(IRANI_DEFS)

    // ---------------------------------------------------------------- the tournaments

    fun newDuleep(userTeamId: String, built: Pair<List<Team>, Map<String, SquadTag>>): TournamentState =
        TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = DULEEP_ID,
            name = DULEEP_NAME,
            userTeamId = userTeamId,
            teams = built.first,
            tags = built.second,
            fixtures = listOf(
                TournamentFixture(0, 1, "Quarter-final 1", "dz_east", "dz_northeast", "coe2"),
                TournamentFixture(1, 2, "Quarter-final 2", "dz_north", "dz_west", "coe1")
            ),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )

    /** The Irani Cup is one match: Jammu & Kashmir (the Ranji champions, at home in Srinagar) against Rest of India. Its stage is "Final". */
    fun newIrani(userTeamId: String, built: Pair<List<Team>, Map<String, SquadTag>>): TournamentState =
        TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = IRANI_ID,
            name = IRANI_NAME,
            userTeamId = userTeamId,
            teams = built.first,
            tags = built.second,
            fixtures = listOf(TournamentFixture(0, 1, "Final", "ir_jk", "ir_roi", "srinagar")),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )

    /** The Duleep Trophy's later rounds: Central meet the winner of quarter-final 1, South the winner of quarter-final 2, then the Final at Chepauk. */
    fun advance(state: TournamentState): TournamentState {
        if (state.tournamentId != DULEEP_ID) return state
        var fx = state.fixtures
        fun add(stage: String, home: String?, away: String?, ground: String) {
            if (home == null || away == null) return
            if (fx.any { it.stage == stage }) return
            fx = fx + TournamentFixture(fx.size, fx.size + 1, stage, home, away, ground)
        }
        fun winnerOf(stage: String): String? = fx.firstOrNull { it.stage == stage }?.result?.winnerId
        add("Semi-final 1", "dz_central", winnerOf("Quarter-final 1"), "coe2")
        add("Semi-final 2", "dz_south", winnerOf("Quarter-final 2"), "coe1")
        add("Final", winnerOf("Semi-final 1"), winnerOf("Semi-final 2"), "chepauk")
        return state.copy(fixtures = fx)
    }

    // ---------------------------------------------------------------- the data

    // id | name | short | home ground | tier 1-5 | squad (name~flags~role letter~~bowling style)
    private const val DULEEP_TEXT = """
dz_central|Central Zone|CEN|coe1|4|Rajat Patidar~c~B;Rinku Singh~~B~~OB;Aman Mokhade~~B~~LB;Yash Rathod~~B~~SLA;Kunal Chandela~~B~~RM;Aayush Pandey~wk~W;Saransh Jain~~A~~OB;Arshad Khan~~A~~RM;Harsh Dubey~~A~~SLA;Zeeshan Ansari~~L~~LB;Yash Thakur~~L~~RFM;Nachiket Bhute~~?;Aryan Pandey~~L~~RMF
dz_east|East Zone|EAS|coe1|4|Ishan Kishan~c+wk~W;Vaibhav Sooryavanshi~~B~~SLA;Virat Singh~~B~~OB;Abhimanyu Easwaran~~B~~LB;Sudip Kumar Gharami~~B~~OB;Subhranshu Senapati~~B~~RM;Anukul Roy~~A~~SLA;Shikhar Mohan~~?;Shahbaz Ahmed~~A~~SLA;Suraj Jaiswal~~?;Mohammed Shami~~L~~RF;Mukesh Kumar~~L~~RFM;Denish Das~~B~~LB;Abhijit Sarkar~~L~~RM
dz_north|North Zone|NOR|coe1|4|Kanhaiya Wadhawan~c+wk~W;Qamran Iqbal~~B;Yash Dhull~~B~~OB;Ayush Doseja~~B;Sanat Sangwan~~B;Abid Mushtaq~~A~~SLA;Ayush Badoni~~B~~OB;Nishant Sindhu~~A~~SLA;Arjun Sharma~~?;Yudhvir Singh~~L~~RMF;Sunil Kumar~~L~~RFM;Arshdeep Singh~~L~~LFM;Anshul Kamboj~~A~~RMF;Nikhil Kashyap~~?
dz_northeast|North East Zone|NEZ|coe1|1|Rongsen Jonathan~c~A~~RM;Kishan Lyngdoh~~B;Joseph Lalthankhuma~~B;Techi Neri~~A~~OB;Robin Limboo~wk~W;Imliwati Lemtur~~A~~SLA;Priyojit Singh Kangabam~~?;Ronald Longjam~~?;Pheiroijam Jotin~~L~~RM;Akash Choudhary~~L~~RFM;Lalrempuia~~A~~OB;Vino Zhimomi~~?;Bishworjit Konthoujam~~L~~RMF
dz_south|South Zone|SOU|coe1|4|Tilak Varma~c~B~~OB;Ricky Bhui~wk~W;Shaik Rasheed~~B~~LB;Kodimela Himateja~~B;Karun Nair~~B~~OB;Ravichandran Smaran~~B;Shreyas Gopal~~A~~LB;Aman Khan~~?;Abhinav Tejrana~~B;Vidwath Kaverappa~~L~~RFM;Kavuri Saiteja~~?;Tripurana Vijay~~A~~RMF;Tanay Thyagarajan~~L~~SLA;MD Nidheesh~~L~~RFM
dz_west|West Zone|WES|coe1|5|Ruturaj Gaikwad~c~B;Prithvi Shaw~~B~~OB;Jay Gohil~wk~W;Shivalik Sharma~~A~~LB;Musheer Khan~~A~~SLA;Shams Mulani~~A~~SLA;Tanush Kotian~~A~~OB;Shardul Thakur~~A~~RFM;Atit Sheth~~A~~RMF;Jaymeet Patel~~B;Mukesh Choudhary~~L~~LM;Tushar Deshpande~~L~~RM;Siddharth Desai~~L~~SLA
"""

    private const val IRANI_TEXT = """
ir_jk|Jammu & Kashmir|J&K|srinagar|4|Paras Dogra~c~B;Shubham Khajuria~~B;Qamran Iqbal~~B;Yawer Hassan~~?;Shubham Pundir~~B~~LB;Abdul Samad~~B;Kawalpreet Singh~~?;Kanhaiya Wadhawan~wk~W;Abid Mushtaq~~A~~SLA;Sahil Lotra~~?;Vanshaj Sharma~~A;Sunil Kumar~~L~~RFM;Umar Nazir~~L~~RFM;Lone Nasir Muzaffar~~L~~RMF;Rohit Sharma~~B~~OB
ir_roi|Rest of India|ROI|srinagar|5|Rishabh Pant~c+wk~W;Sanat Sangwan~~B;Shikhar Mohan~~B;Sudip Kumar Gharami~~B~~OB;Sarfaraz Khan~vc~B~~LB;Ravichandran Smaran~~B;Manav Suthar~~A~~SLA;Saransh Jain~~A~~OB;Akash Deep~~L~~RFM;Mukesh Kumar~~L~~RFM;Mukesh Choudhary~~L~~LM;Aryan Juyal~wk~W;Aryan Pandey~~L~~RMF;Anukul Roy~~A~~SLA;Ayush Doseja~~B
"""
}

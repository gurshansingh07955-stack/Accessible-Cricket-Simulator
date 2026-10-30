package com.cricketsim.logic

/**
 * The Ranji Trophy 2026-27: 38 teams, four Elite groups of eight and a Plate group of six, the real group fixtures in their real order
 * and the real squads (the squads named for the first round, from Wisden; Haryana from Sportscafe; Sikkim's is last season's).
 * League matches are four days long (the first-class rules of logic/TestMatch.kt with `testDays` 4). The computer plays them
 * (`CplSimulator.simulateFirstClass`); playing your own match, the knockouts and promotion are later parts. See RANJI_TROPHY.md.
 *
 * Players already in the game (the roster and every league) are copied with their ratings; the rest are new, with ESTIMATED ratings
 * from the team's strength and the role (read from Wikipedia where a page exists, otherwise from the squad order).
 */
object Ranji {

    const val TOURNAMENT_ID = "ranji_2026_27"
    const val TOURNAMENT_NAME = "Ranji Trophy 2026-27"
    const val LEAGUE_DAYS = 4
    const val KNOCKOUT_DAYS = 5

    val STAGES = listOf("Elite Group A", "Elite Group B", "Elite Group C", "Elite Group D", "Plate Group")
    private val STAGE_OF_LETTER = mapOf('A' to STAGES[0], 'B' to STAGES[1], 'C' to STAGES[2], 'D' to STAGES[3], 'P' to STAGES[4])

    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN

    /** name, flags (c, vc, wk), role letter (B, W, A, L or ? / - when unknown), batting hand, bowling style code. */
    private class Entry(val name: String, val flags: String, val role: Char, val bowl: String)

    private class TeamDef(val id: String, val name: String, val shortName: String, val home: String, val tier: Int, val stage: String, val squad: List<Entry>)

    private fun parseEntry(text: String): Entry {
        val p = text.split("~")
        return Entry(p[0].trim(), p.getOrElse(1) { "" }, p.getOrNull(2)?.firstOrNull() ?: '-', p.getOrNull(4) ?: "")
    }

    const val PLATE_FINAL = "Plate Final"

    fun isGroupStage(stage: String): Boolean = stage in STAGES

    /** League matches and the Plate Final are four days, the quarter-finals, semi-finals and the Final five. */
    fun daysFor(stage: String): Int = if (stage in STAGES || stage == PLATE_FINAL) LEAGUE_DAYS else KNOCKOUT_DAYS

    private val DEFS: List<TeamDef> by lazy {
        TEAM_TEXT.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val p = line.split("|")
            TeamDef("rj_" + p[0], p[1], p[2], p[3], p[4].toInt(), STAGE_OF_LETTER.getValue(p[5][0]), p[6].split(";").map { parseEntry(it) })
        }
    }

    fun isRanji(tournamentId: String?): Boolean = tournamentId == TOURNAMENT_ID

    fun isTeam(teamId: String): Boolean = DEFS.any { it.id == teamId }

    fun shortNameOrNull(teamId: String): String? = DEFS.find { it.id == teamId }?.shortName

    fun homeGround(teamId: String): String = DEFS.find { it.id == teamId }?.home ?: "wankhede"

    fun groupTeamIds(stage: String): Set<String> = DEFS.filter { it.stage == stage }.map { it.id }.toSet()

    fun stageOf(teamId: String): String? = DEFS.find { it.id == teamId }?.stage

    // ---------------------------------------------------------------- grounds

    private fun pitchOf(c: Char): PitchType = when (c) {
        'B' -> PitchType.BATTING
        'D' -> PitchType.DUSTY
        'G' -> PitchType.GREEN
        'O' -> PitchType.BOWLING
        else -> PitchType.BALANCED
    }

    private fun pitchText(t: PitchType): String = when (t) {
        PitchType.BATTING -> "A flat, true surface that generally favors batters."
        PitchType.GREEN -> "A grassy surface with seam movement early on."
        PitchType.DUSTY -> "A dry, dusty surface that turns as the match goes on."
        PitchType.BOWLING -> "A surface that helps the bowlers."
        else -> "A balanced surface with something for everyone."
    }

    private fun boundaryOf(c: Char): BoundarySize = when (c) {
        'S' -> BoundarySize.SMALL
        'L' -> BoundarySize.LARGE
        else -> BoundarySize.MEDIUM
    }

    /** The grounds of the competition. Estimated ratings (pitch, boundary, altitude): the game has no data for most of them. */
    val stadiums: List<Stadium> by lazy {
        VENUE_TEXT.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val p = line.split("|")
            val pitch = pitchOf(p[3][0])
            Stadium(
                id = p[0], name = p[1], city = p[2], country = "India", homeTeamIds = emptyList(),
                pitchType = pitch, pitchDescription = pitchText(pitch), boundarySize = boundaryOf(p[4][0]),
                altitudeM = p[5].toInt(), dewFactor = 0.25, avgT20FirstInningsScore = 160,
                climate = StadiumClimate(0.2, 55, 27, 10, "Typical conditions for the Indian domestic season.")
            )
        }
    }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    // ---------------------------------------------------------------- players

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' }

    private fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)

    /** A steady "random" number from a name, so a player's estimated ratings are the same every time. */
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

    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val roster = CricketData.getAllTeams().flatMap { it.players }.associateBy { norm(it.name) }
        val cpl = CplData.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val adt = AbuDhabiT10.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val lpl = LankaPremierLeague.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val ilt = InternationalLeagueT20.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val bbl = BigBashLeague.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val mlc = MajorLeagueCricket.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val sa = Sa20.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val tags = LinkedHashMap<String, SquadTag>()
        val teams = DEFS.map { def ->
            val roles = assignRoles(def.squad)
            val tierDelta = when (def.tier) { 5 -> 7; 4 -> 3; 3 -> 0; 2 -> -4; else -> -9 }
            val base = 56 + tierDelta
            val players = def.squad.mapIndexed { index, e ->
                val id = "${def.id}_${index + 1}"
                val key = norm(e.name)
                val known = roster[key] ?: cpl[key] ?: adt[key] ?: lpl[key] ?: ilt[key] ?: bbl[key] ?: mlc[key] ?: sa[key]
                val player = if (known != null) {
                    known.copy(id = id, teamId = def.id, name = e.name)
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

    // ---------------------------------------------------------------- fixtures

    /** The real group matches in the real order: round by round, group by group. A match with no ground named yet is at the first side's home ground. */
    fun buildSchedule(): List<TournamentFixture> {
        val rows = FIXTURE_TEXT.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { it.split("|") }
        val order = "ABCDP"
        val sorted = rows.sortedWith(compareBy({ it[1].toInt() }, { order.indexOf(it[0][0]) }))
        return sorted.mapIndexed { index, p ->
            val home = "rj_" + p[2]
            val away = "rj_" + p[3]
            val ground = p.getOrNull(4)?.takeIf { it.isNotEmpty() } ?: homeGround(home)
            TournamentFixture(index, index + 1, STAGE_OF_LETTER.getValue(p[0][0]), home, away, ground)
        }
    }

    private fun nameOf(state: TournamentState, id: String): String = state.teams.find { it.id == id }?.name ?: id

    /**
     * The knockouts, created as the teams become known: when the last Elite group match is played the quarter-finals (A1 v C2, B1 v D2,
     * C1 v A2, D1 v B2), then each semi-final and the Final as the matches before them are won; the Plate Final when the Plate group
     * is done. A knockout is never left without a winner (a draw goes to the first-innings leader, see CplSimulator.simulateFirstClass).
     */
    fun advance(state: TournamentState): TournamentState {
        var fx = state.fixtures
        fun complete(stage: String): Boolean {
            val l = fx.filter { it.stage == stage }
            return l.isNotEmpty() && l.all { it.result != null }
        }
        fun add(stage: String, home: String?, away: String?) {
            if (home == null || away == null) return
            if (fx.any { it.stage == stage }) return
            fx = fx + TournamentFixture(fx.size, fx.size + 1, stage, home, away, homeGround(home))
        }
        fun winnerOf(stage: String): String? = fx.firstOrNull { it.stage == stage }?.result?.winnerId
        val elite = STAGES.take(4)
        if (elite.all { complete(it) }) {
            val top = elite.map { g -> FirstClass.standings(state.copy(fixtures = fx), g).map { it.team.id } }
            add("Quarter-final 1", top[0].getOrNull(0), top[2].getOrNull(1))
            add("Quarter-final 2", top[1].getOrNull(0), top[3].getOrNull(1))
            add("Quarter-final 3", top[2].getOrNull(0), top[0].getOrNull(1))
            add("Quarter-final 4", top[3].getOrNull(0), top[1].getOrNull(1))
        }
        add("Semi-final 1", winnerOf("Quarter-final 1"), winnerOf("Quarter-final 4"))
        add("Semi-final 2", winnerOf("Quarter-final 2"), winnerOf("Quarter-final 3"))
        add("Final", winnerOf("Semi-final 1"), winnerOf("Semi-final 2"))
        if (complete(STAGES[4])) {
            val plate = FirstClass.standings(state.copy(fixtures = fx), STAGES[4]).map { it.team.id }
            add(PLATE_FINAL, plate.getOrNull(0), plate.getOrNull(1))
        }
        return state.copy(fixtures = fx)
    }

    /** Promotion and relegation, once they are known: the Plate Final winner goes up, the bottom Elite side goes down. */
    fun seasonEndLines(state: TournamentState): List<String> {
        val lines = ArrayList<String>()
        val promoted = state.fixtures.firstOrNull { it.stage == PLATE_FINAL }?.result?.winnerId
        if (promoted != null) lines.add("${nameOf(state, promoted)} won the Plate Final and are promoted to the Elite groups next season.")
        val elite = STAGES.take(4)
        if (elite.all { g -> state.fixtures.filter { it.stage == g }.all { it.result != null } }) {
            val bottom = elite.flatMap { FirstClass.standings(state, it) }
                .sortedWith(compareBy<FcRow>({ it.points }, { it.won }, { it.quotient })).firstOrNull()
            if (bottom != null) lines.add("${bottom.team.name} finished bottom of the Elite groups and are relegated to the Plate group next season.")
        }
        return lines
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

    // ---------------------------------------------------------------- the data

    // id | ground (id, name, city, pitch B/L/D/G/O, boundary S/M/L, altitude m)
    private const val VENUE_TEXT = """
rajgir|Rajgir International Cricket Stadium|Rajgir|L|M|70
panjim|Panjim Gymkhana Ground|Panaji|G|S|5
srinagar|Sher-i-Kashmir Stadium|Srinagar|G|L|1585
nagpur|Vidarbha Cricket Association Stadium|Nagpur|D|L|310
vadodara_rel|Reliance Stadium|Vadodara|B|M|35
kotambi|Kotambi Stadium|Vadodara|L|M|35
puducherry|CAP Ground, Siechem|Puducherry|L|M|10
kanpur|Green Park|Kanpur|D|L|126
indore|Holkar Cricket Stadium|Indore|B|S|553
porvorim|Goa Cricket Association Academy Ground|Porvorim|G|M|10
secunderabad|Gymkhana Ground|Secunderabad|L|M|536
mulapadu|Dr. DVR Praneetha Cricket Ground|Mulapadu|B|M|20
guwahati|Barsapara Cricket Stadium|Guwahati|G|L|55
mullanpur|Maharaja Yadavindra Singh International Cricket Stadium|New Chandigarh|G|L|300
rajkot|Niranjan Shah Stadium|Rajkot|B|M|130
alur|KSCA Cricket Ground|Alur|L|M|900
mysore|SNR Wadeyar Ground|Mysuru|L|M|770
jamshedpur|Keenan Stadium|Jamshedpur|L|M|135
kalyani|Bengal Cricket Academy Ground|Kalyani|G|M|10
motera|Narendra Modi Stadium|Ahmedabad|L|L|55
palam_a|Palam A Ground|Delhi|L|M|230
palam_b|Palam B Ground|Delhi|L|M|230
pune|Maharashtra Cricket Association Stadium|Pune|B|L|560
greenfield|Greenfield International Stadium|Thiruvananthapuram|L|L|5
ranchi|JSCA International Stadium|Ranchi|D|L|650
bhubaneswar|KIIT Stadium|Bhubaneswar|L|M|45
mangalapuram|KCA Cricket Ground|Mangalapuram|G|M|10
eden|Eden Gardens|Kolkata|L|L|10
rohtak|Chaudhary Bansi Lal Cricket Stadium|Rohtak|B|M|220
brabourne|Brabourne Stadium|Mumbai|B|S|10
jaipur|Sawai Mansingh Stadium|Jaipur|B|L|430
wankhede|Wankhede Stadium|Mumbai|B|M|10
bilaspur|Luhnu Cricket Ground|Bilaspur|G|M|700
chepauk|M. A. Chidambaram Stadium|Chennai|D|L|7
bkc|Sharad Pawar Cricket Academy|Mumbai|L|M|10
dehradun|Abhimanyu Cricket Academy|Dehradun|G|M|640
chandigarh16|Sector 16 Stadium|Chandigarh|G|M|320
rangpo|SICA Ground|Rangpo|G|S|280
nadiad|Nadiad Cricket Ground|Nadiad|L|M|40
shillong|Meghalaya Cricket Association Ground|Shillong|G|M|1500
sovima|Nagaland Cricket Stadium|Sovima|G|M|150
agartala|Maharaja Bir Bikram College Stadium|Agartala|L|M|13
raipur|Shaheed Veer Narayan Singh Stadium|Raipur|B|L|300
"""

    // group | round | home code | away code | ground (blank = the first side's home ground)
    private const val FIXTURE_TEXT = """
A|1|up|bih|rajgir
A|1|bar|goa|panjim
A|1|mp|jak|srinagar
A|1|vid|pon|nagpur
A|2|bih|bar|vadodara_rel
A|2|pon|mp|puducherry
A|2|jak|vid|srinagar
A|2|up|goa|kanpur
A|3|bar|jak|vadodara_rel
A|3|bih|mp|indore
A|3|goa|vid|nagpur
A|3|up|pon|puducherry
A|4|up|bar|kanpur
A|4|goa|bih|
A|4|vid|mp|
A|4|jak|pon|srinagar
A|5|bar|vid|kotambi
A|5|bih|jak|
A|5|mp|up|
A|5|goa|pon|porvorim
A|6|pon|bar|puducherry
A|6|bih|vid|
A|6|mp|goa|
A|6|up|jak|
A|7|bar|mp|vadodara_rel
A|7|pon|bih|
A|7|vid|up|
A|7|jak|goa|
B|1|tri|hyd|secunderabad
B|1|kar|and|mulapadu
B|1|ass|pun|mullanpur
B|1|sau|chh|rajkot
B|2|ass|tri|guwahati
B|2|hyd|and|mulapadu
B|2|kar|sau|alur
B|2|pun|chh|mullanpur
B|3|pun|hyd|secunderabad
B|3|tri|kar|mysore
B|3|ass|sau|rajkot
B|3|and|chh|mulapadu
B|4|chh|hyd|
B|4|ass|kar|
B|4|pun|and|
B|4|tri|sau|
B|5|ass|hyd|
B|5|kar|chh|
B|5|tri|pun|
B|5|and|sau|
B|6|hyd|kar|
B|6|pun|sau|
B|6|ass|chh|
B|6|tri|and|
B|7|sau|hyd|
B|7|kar|pun|
B|7|and|ass|
B|7|chh|tri|
C|1|jha|ker|jamshedpur
C|1|del|ben|kalyani
C|1|odi|guj|motera
C|1|mah|ser|palam_a
C|2|jha|mah|pune
C|2|del|guj|palam_b
C|2|ben|ser|palam_a
C|2|ker|odi|greenfield
C|3|del|jha|ranchi
C|3|odi|ser|bhubaneswar
C|3|guj|mah|pune
C|3|ben|ker|mangalapuram
C|4|odi|jha|
C|4|guj|ker|
C|4|del|ser|
C|4|ben|mah|eden
C|5|jha|ser|
C|5|guj|ben|
C|5|odi|del|
C|5|mah|ker|
C|6|jha|ben|
C|6|ser|guj|
C|6|ker|del|
C|6|mah|odi|
C|7|guj|jha|
C|7|del|mah|
C|7|ben|odi|
C|7|ker|ser|
D|1|hp|har|rohtak
D|1|rai|utt|brabourne
D|1|raj|tn|jaipur
D|1|mum|cha|wankhede
D|2|cha|har|rohtak
D|2|hp|mum|bilaspur
D|2|utt|tn|chepauk
D|2|raj|rai|jaipur
D|3|mum|har|bkc
D|3|raj|utt|dehradun
D|3|rai|tn|chepauk
D|3|cha|hp|chandigarh16
D|4|utt|har|
D|4|hp|raj|
D|4|rai|mum|
D|4|cha|tn|
D|5|rai|har|
D|5|mum|utt|
D|5|raj|cha|
D|5|tn|hp|
D|6|raj|har|
D|6|hp|utt|
D|6|cha|rai|
D|6|tn|mum|
D|7|har|tn|
D|7|utt|cha|
D|7|mum|raj|
D|7|hp|rai|
P|1|sik|meg|rangpo
P|1|miz|man|nadiad
P|1|ap|nag|
P|2|meg|man|shillong
P|2|miz|nag|sovima
P|2|ap|sik|nadiad
P|3|nag|sik|rangpo
P|3|ap|man|motera
P|3|miz|meg|nadiad
P|4|man|sik|
P|4|meg|nag|
P|4|miz|ap|
P|5|nag|man|
P|5|meg|ap|
P|5|sik|miz|
"""

    // code | name | short | home ground | tier 1-5 (strength guess) | group | squad (name~flags~role~hand~bowling style)
    private const val TEAM_TEXT = """
up|Uttar Pradesh|UP|kanpur|4|A|Aryan Juyal~c+wk~W~R~;Abhishek Goswami~~?~?~?;Adarsh Singh~;Priyam Garg~~B~R~RM;Madhav Kaushik~~?~R~OB;Rinku Singh~~B~L~OB;Karan Sharma~~?~?~;Prashant Veer~~A~L~SLA;Abhinandan Singh~;Yash Dayal~~L~R~LFM;Zeeshan Ansari~~?~R~LB;Aaditya Sharma~;Aaradhya Yadav~;Vansh Chaudhary~;Kartik Yadav~;Kunal Tyagi~
bih|Bihar|BIH|rajgir|2|A|Sakibul Gani~c~?~R~RMF;Himanshu Singh~vc;Sraman Nigrodh~wk;Piyush Singh~~?~?~?;Bipin Saurabh~wk;Mohammed Izhar~;Sakib Hussain~~L~R~RFM;Naman Gaurav~;Suraj Kashyap~~L~R~OB;Khalid Alam~;Ayush Loharuka~;Mangal Mahrour~~B~?~?;Raghuvendra Pratap Singh~~A~R~RMF;Prithvi Raj~~?~?~;Akash Raj~;Shabbir Khan~~?~R~RF;Amod Yadav~
bar|Baroda|BAR|vadodara_rel|4|A|Atit Sheth~c~A~R~RMF;Priyanshu Moliya~;Mitesh Patel~wk~?~?~?;Shivalik Sharma~~?~L~?;Aryan Chavda~;Deepak Hooda~~A~R~OB;Bhargav Bhatt~~L~L~SLA;Raj Limbani~;Rasikh Salam~~L~R~RFM;Shashwat Rawat~vc~?~L~RMF;Manan Solanki~;Akshay More~;Dhruv Patel~~?~?~?;Mahesh Pithiya~~?~R~OB;Lakshit Toksiya~
goa|Goa|GOA|porvorim|2|A|Abhinav Tejrana~c;Deepraj Gaonkar~~?~?~?;Snehal Kauthankar~~B~R~OB;Lalit Yadav~~?~?~?;Darshan Misal~~?~?~?;Suyash Prabhudessai~~A~R~RM;Felix Alemao~~L~R~RM;Vasuki Koushik~~L~R~RM;Shivendra Bhujbal~;Rajashekhar Harikant~~?~R~?;Yash Kasvankar~;Manthan Khutkar~;Amulya Pandrekar~;Mohit Redkar~;Ruthvik Naik~;Shubham Tari~
mp|Madhya Pradesh|MP|indore|4|A|Shubham Sharma~c~B~R~OB;Yash Dubey~~?~R~LB;Harsh Gawli~;Himanshu Mantri~wk~W~L~;Saransh Jain~~A~L~OB;Shivang Kumar~;Avesh Khan~~L~R~RFM;Kumar Kartikeya~~L~R~SLA;Aryan Pandey~;Kuldeep Sen~;Anubhav Agarwal~;Akshay Sharma~;Roshan Kewat~;Atharva Mahajan~;Chanchal Rathore~
jak|Jammu & Kashmir|J&K|srinagar|4|A|Abdul Samad~;Paras Dogra~;Shubham Khajuria~~B~R~?;Shubham Pundir~~B~L~LB;Qamran Iqbal~~B~R~?;Kanhaiya Wadhawan~wk~?~?~?;Yawer Hassan~;Abid Mushtaq~~A~?~?;Sahil Lotra~;Auqib Nabi~~L~R~RM;Sunil Kumar~;Rohit Sharma~~B~R~OB;Vanshaj Sharma~;Umran Malik~~L~R~RF
vid|Vidarbha|VID|nagpur|5|A|Akshay Wadkar~c+wk~W~R~?;Danish Malewar~~B~R~LB;Aman Mokhade~~B~R~LB;Yash Rathod~~B~L~SLA;Ravikumar Samarth~~B~R~OB;Dhruv Shorey~~B~R~OB;Nachiket Bhute~~?~?~?;Harsh Dubey~~A~L~SLA;Parth Rekhade~~?~?~?;Atharva Taide~~B~L~SLA;Rajneesh Gurbani~~L~R~RMF;Praful Hinge~~L~R~RMF;Darshan Nalkande~~L~R~RFM;Ganesh Bhosle~;Satyam Bhoyar~
pon|Puducherry|PUD|puducherry|1|A|Mohit Kale~~?~R~LB;Aman Khan~;Nikhil Gangta~~?~R~RM;Ankit Sharma~~L~L~SLA;Gaurav Yadav~~?~R~RM;Neyan Shyam Kangayan~;Karan Kannan~;Logesh Prabagaran~;Prasad Pawar~;Ritesh Gudge~;Murugaiyan Sivamurugan~;Tahir Bhat~;Parandaman Thamaraikannan~~?~R~RM;Sagar Trivedi~~?~?~;Sagar Udeshi~~?~?~?;Vijai Raja~
tri|Tripura|TRI|agartala|2|B|Manisankar Murasingh~c~A~L~RM;Abhijit Sarkar~vc~?~?~?;Bikramkumar Das~;Rajat Dey~~?~?~?;Amit Ali~;Mandeep Singh~~A~R~RM;Sridam Paul~;Hanuma Vihari~~A~R~OB;Parvez Sultan~~?~?~?;Ajay Sarkar~;Swapnil Singh~~L~R~SLA;Arindam Barman~;Bikramjit Debnath~;Hrituraj Roy~;Sandip Sarkar~
hyd|Hyderabad|HYD|secunderabad|3|B|Mohammed Siraj~c~L~R~RF;Chama Milind~vc~L~L~LM;Tanmay Agarwal~~B~L~LB;Kodimela Himateja~;Rakshan Readdi~~?~?~?;Tanay Thyagarajan~~?~L~SLA;Abhirath Reddy~~?~R~?;Aniketh Reddy~;Ajay Dev Goud~~?~R~RM;Ruthik Mende~;Pragnay Reddy~~?~?~?;Rahul Radesh~;Rahul Singh~~?~?~;Sai Vikas Reddy~;Aelgani Varun Goud~
kar|Karnataka|KAR|alur|5|B|Ravichandran Smaran~c;Mayank Agarwal~~B~R~OB;KV Aneesh~;Kruthik Krishna~wk;Karun Nair~~B~R~OB;KL Rahul~wk~W~R~;Krishnan Shrijith~wk~W~L~?;Rithesh Bhatkal~;Aneeshwar Gautam~;Shreyas Gopal~~A~R~LB;Manvanth Kumar~;Shikhar Shetty~;Abhilash Shetty~;LR Kumar~;Prasidh Krishna~~L~R~RFM
and|Andhra|AND|mulapadu|3|B|Ricky Bhui~c~B~R~LB;Shaik Rasheed~~B~R~LB;Saurabh Kumar~~A~L~SLA;Satyanarayana Raju~~L~R~RMF;Kavuri Saiteja~;KV Sasikanth~~A~R~RM;Tripurana Vijay~;M Hemanth Reddy~vc;Uppara Girinath~~?~?~?;Karan Shinde~~?~?~?;Lalith Mohan~~?~?~?;Seeram Rahul~;Abhishek Reddy~~?~R~OB;Revanth Reddy~;Cheepurapalli Stephen~
ass|Assam|ASM|guwahati|2|B|Denish Das~c~B~R~LB;Sibsankar Roy~~B~L~OB;Roshan Alam~~L~R~SLA;Bishal Newar~;Rishav Das~~B~R~OB;Amlanjyoti Das~;Sumit Ghadigaonkar~~?~?~?;Sunil Lachit~~L~L~LMF;Mohit Thakur~;Mukhtar Hussain~~L~R~RFM;Nihar Deka~;Swarupam Purkayastha~~A~R~OB;Rajveer Singh~;Dipjyoti Saikia~;Anurag Talukdar~
pun|Punjab|PUN|mullanpur|3|B|Uday Saharan~c;Salil Arora~wk;Harnoor Singh~;Ramandeep Singh~~?~?~;Emanjot Singh Chahal~;Vihaan Malhotra~~A~L~OB;Sanvir Singh~~A~R~RM;Gurnoor Brar~~L~L~RF;Harpreet Brar~~L~L~SLA;Umesh Yadav~~L~R~RF;Anshul Chaudhary~;Sahaj Dhawan~;Gursimran Singh~;Harjas Singh~~A~L~RM;Jashanpreet Singh~;Anmol Malhotra~
sau|Saurashtra|SAU|rajkot|5|B|Jaydev Unadkat~c~L~R~LM;Harvik Desai~wk~W~R~?;Tarang Gohel~wk;Arpit Vasavada~~A~L~SLA;Ravindra Jadeja~~A~L~SLA;Chirag Jani~~A~R~RM;Prerak Mankad~~A~R~RM;Chetan Sakariya~~?~L~LMF;Parth Bhut~~A~R~SLA;Sammar Gajjar~;Vishvaraj Jadeja~~?~?~?;Jay Gohil~;Hetvik Kotak~;Neel Pandya~;Ankur Panwar~;Arth Yadav~
chh|Chhattisgarh|CHH|raipur|2|B|Sanjeet Desai~~?~R~?;Aayush Pandey~;Ajay Mandal~~?~L~SLA;Dharmendrasinh Jadeja~~A~L~SLA;Sandeep Warrier~~L~R~RMF;Jayant Yadav~~L~R~OB;Ashutosh Singh~~A~R~OB;Rudra Dehari~;Dev Aditya Singh~;Sanidhya Hurkat~~?~R~OB;Amandeep Khare~~B~R~RM;Sourabh Majumdar~~L~R~RMF;Mayank Verma~;Mohammad Sohail~~?~R~OB;Rahul Pradhan~;Anuj Tiwary~;Vasudev Barath~
jha|Jharkhand|JHA|jamshedpur|3|C|Virat Singh~c~B~L~OB;Robin Minz~wk~W~L~SLA;Utkarsh Singh~~?~?~?;Amit Kumar~~?~?~;Sushant Mishra~~L~L~LMF;Saurabh Shekhar~;Md Kounain Quraishi~;Manishi~;Shikhar Mohan~;Jatin Pandey~;Sahil Raj~~?~?~?;Shresht Sagar~;Aryaman Sen~;Sharandeep Singh~;Aditya Singh~~?~?~?
ker|Kerala|KER|greenfield|4|C|Vishnu Vinod~c+wk~W~R~RMF;Ahammed Imran~;Rohan Kunnummal~~B~R~OB;Mohammed Azharuddeen~wk~W~R~?;Varun Nayanar~;Sachin Baby~~B~L~OB;Salman Nizar~~B~L~OB;Eden Apple Tom~;Jalaj Saxena~~A~R~OB;Aditya Sarwate~~L~R~SLA;Nedumankuzhy Basil~~L~R~RFM;MD Nidheesh~~L~R~RFM;JS Anuraj~;Abhishek Nair~
del|Delhi|DEL|palam_a|4|C|Ayush Badoni~c~B~R~OB;Yash Dhull~vc~B~R~OB;Anuj Rawat~wk~W~L~?;Ayush Doseja~;Tejasvi Dahiya~wk;Sarthak Ranjan~~?~R~LB;Sanat Sangwan~;Navdeep Saini~~L~R~RF;Hrithik Shokeen~~L~R~OB;Simarjeet Singh~~L~R~RMF;Dev Lakra~;Dhruv Kaushik~;Money Grewal~;Divij Mehra~;Pranav Rajvanshi~;Sumit Mathur~~?~R~?
ben|Bengal|BEN|kalyani|4|C|Abhimanyu Easwaran~c~B~R~LB;Sudip Kumar Gharami~vc~?~R~OB;Abishek Porel~wk~W~L~;Sudip Chatterjee~~B~L~LB;Akash Deep~~L~R~RFM;Mohammed Shami~~L~R~RF;Mukesh Kumar~~?~?~;Ankur Paul~;Subham Chatterjee~;Aamir Gani~~L~R~OB;Sumanta Gupta~~?~R~LB;Suraj Jaiswal~;Mohammed Kaif~;Pradipta Pramanik~~?~R~SLA;Shakir Gandhi~;Ankit Shukla~;Soumyadip Mandal~
odi|Odisha|ODI|bhubaneswar|2|C|Subhranshu Senapati~c~B~R~RM;Badal Biswal~;Sambit Beja~;Shreyansh Bhardwaj~;Gourav Choudhary~;Lalit Yadav~~?~?~?;Soumya Lenka~;Dinesh Majhi~;Om T Munde~;Sandeep Pattnaik~~?~?~?;Rajesh Mohanty~~L~L~RM;Prasanta Rana~;Ninad Rathva~~A~L~SLA;Swastik Samal~~?~?~?;Sayed Tufayl Ahmad~;Shantanu Mishra~~?~R~OB;Aashirwad Swain~
guj|Gujarat|GUJ|motera|4|C|Chintan Gaja~c~?~?~?;Urvil Patel~vc+wk~W~R~?;Aarya Desai~;Kshitij Patel~~B~R~OB;Jaymeet Patel~;Vedant Trivedi~;Harshal Patel~~A~R~RFM;Siddharth Desai~~L~L~SLA;Arzan Nagwaswalla~~L~R~LFM;Amit Desai~;Abhishek Desai~;Manan Hingrajia~~?~L~OB;Priyajitsing Jadeja~;Saral Prajapati~;Priyesh Patel~~?~R~?;Vishal Jayswal~
mah|Maharashtra|MAH|pune|3|C|Prithvi Shaw~c~B~R~OB;Ankit Bawne~~B~R~OB;Sachin Dhas~~B~R~?;Rajvardhan Hangargekar~~A~R~RF;Siddhesh Veer~;Pradeep Dadhe~~?~?~?;Mukesh Choudhary~~L~L~LM;Vicky Ostwal~;Vaibhav Darkunde~;Atharva Dharmadhikari~;Digvijay Patil~;Harshal Kate~;Saurabh Nawale~;Tanay Sanghvi~;Maya Sonawane~;Hitesh Walunj~~?~R~SLA
ser|Services|SER|palam_b|3|C|Rajat Paliwal~~B~R~OB;Arjun Sharma~~?~?~?;Rahul Singh~~?~?~;Varun Choudhary~~?~?~?;Vineet Dhankhar~;Jayant Goyat~;Devender Lohchab~;Mohit Ahlawat~~W~R~?;Nakul Sharma~~W~R~LB;Pulkit Narang~~?~R~OB;Amit Pachhara~~?~R~OB;Raj Bahadur~~?~L~LM;Ravi Chauhan~;Nitin Tanwar~;Virender Dagar~
hp|Himachal Pradesh|HP|bilaspur|2|D|Pukhraj Mann~c;Innesh Mahajan~wk;Nehal Wadhera~~B~L~LB;Akash Vasisht~~A~L~SLA;Arpit Guleria~;Amanpreet Singh~;Aryaman Singh~;Divesh Sharma~;Harshit Sandal~;Jitender Rana~;Hritik Kalia~;Mukul Negi~;Nitin Sharma~~?~?~?;Shubham Arora~;Vaibhav Kalta~
har|Haryana|HAR|rohtak|3|D|Tanmay Baloda~;Lakshya Suman Dalal~;Yashvardhan Dalal~;Kapil Hooda~;Anshul Kamboj~~A~R~RM;Nikhil Kashyap~;Aman Kumar~;Ankit Kumar~~?~R~OB;Sumit Kumar~~W~R~OB;Amit Rana~~L~R~OB;Rahul Pawan Rathee~;Bhuwan Rohilla~;Mayank Shandilya~;Nishant Sindhu~~A~L~SLA;Dheeru Singh~;Yuvraj Singh~~B~L~SLA;Anuj Hira Thakral~;Parth Shiv Vats~
rai|Railways|RLY|palam_b|3|D|Bhargav Merai~c~?~R~RM;Suraj Ahuja~wk~?~?~?;Kush Marathe~;Ravi Singh~~?~R~?;Vivek Singh~~W~L~RM;Raj Choudhary~;Eshaan Goyal~;Kunal Yadav~;Mohammad Saif~~?~L~SLA;Pradhyumn Parikh~;Sahab Yuvraj Singh~;Himanshu Sangwan~~?~?~?;Adarsh Singh~;Toufik Uddin Mondal~;Zubair Ali~
utt|Uttarakhand|UTK|dehradun|2|D|Kunal Chandela~~?~R~RM;Lakshya Raichandani~;Saurabh Rawat~wk~W~R~?;Avneesh Sudha~;Yuvraj Chaudhary~~A~L~SLA;Mayank Mishra~~A~L~SLA;Agnivesh Ayachi~~L~R~RM;Deepak Dhapola~~?~?~?;Janmejay Joshi~;Aditya Rawat~;Jagadeesha Suchith~~A~L~SLA;Aarav Mahajan~;Devendra Singh Bora~;Tejasvi Jaiswal~;Vaibhav Kandpal~
raj|Rajasthan|RAJ|jaipur|3|D|Manav Suthar~c~A~L~SLA;Mahipal Lomror~vc~A~L~SLA;Mukul Choudhary~wk~W~R~?;Sumit Godara~;Kartik Sharma~wk~W~R~?;Kukna Ajay Singh~~?~L~SLA;Akash Singh~~L~R~LMF;Hemant Kumar~;Anshul Garhwal~;Danish Bhambhu~;Chetan Sharma~~A~R~RFM;Darshan Jain~;Karan Lamba~~B~R~?;Rohan Rajbar~;Sachin Yadav~
tn|Tamil Nadu|TN|chepauk|4|D|Sai Kishore~c;C Andre Siddarth~vc;Guruswamy Ajitesh~wk;Athish SR~wk;Baba Indrajith~;Narayan Jagadeesan~wk;Pradosh Ranjan Paul~wk;Vimal Khumar~;Nidhish Rajagopal~;Sonu Yadav~~?~?~?;Washington Sundar~~A~L~OB;Ajith Ram~;Deepesh Devendran~;Govinth Ganesh~;Pranav Ragavendra~
mum|Mumbai|MUM|wankhede|5|D|Siddhesh Lad~c~B~R~OB;Ayush Mhatre~vc~B~R~OB;Akash Anand~wk;Yashasvi Jaiswal~~B~L~LB;Sarfaraz Khan~~B~R~LB;Suved Parkar~;Hardik Tamore~wk~?~?~?;Atharva Ankolekar~~A~L~SLA;Tanush Kotian~~A~R~OB;Shams Mulani~~?~L~SLA;Musheer Khan~~A~R~SLA;Mohit Avasthi~~?~R~RMF;Tushar Deshpande~~L~L~RM;Onkar Tarmale~;Sylvester D'Souza~
cha|Chandigarh|CHD|chandigarh16|2|D|Manan Vohra~~B~R~RFM;Raj Bawa~;Akshit Rana~;Arjit Pannu~;Arjun Azad~;Arslan Khan~~?~?~;Shivam Bhambri~~?~R~OB;Rohit Dhanda~;Dipender Kush~;Jagjit Singh~~?~?~;Anirudh Kanwar~;Karthik Sandil~;Nishunk Birla~;Nehal Pajni~
sik|Sikkim|SIK|rangpo|1|P|Lee Yong Lepcha~c~A~R~?;Gurinder Singh~~A~R~SLA;K Sai Satwik~;Kranti Kumar~;Amit Rajera~;Ankur Malik~~A~R~OB;Anwesh Karan Sharma~;Arun Chettri~;Ashish Thapa~;Bhim Lall Luitel~;Bijay Prasad~;Nilesh Lamichaney~~?~R~?;Palzor Tamang~~?~R~RM;Pranesh Chettri~;Robin Limboo~
meg|Meghalaya|MEG|shillong|1|P|Arpit Bhatewara~wk;Kishan Lyngdoh~;Abhishek Kumar~;Aryan Bora~;Swastic Chettri~;Kevin Christopher~;Rahul Dalal~~B~R~OB;Ajay Duhan~;Ram Gurung~;Jaskirat Singh Sachdeva~;Joydeepan Deb~;Bipin Ray~;Sudhir Sahani~;PM Santhosh~;Mewada Shylla~
miz|Mizoram|MIZ|nadiad|1|P|Joseph Lalthankhuma~c;Jehu Anderson~vc~?~R~?;Vikash Kumar~~?~?~?;Remruatdika Ralte~;Lalhruaizela~~B~R~?;R Lalnuntluanga~;Lalrempuia~~?~R~OB;Manender Singh~~W~R~?;Nasib Rai~;Sahil Reza~;Sahil Sharma~~?~?~?;Chinmay Sutar~~B~R~LB;Khiangte Vanrotlinga~;Bobby Zothansanga~
man|Manipur|MAN|nadiad|1|P|Kangabam Priyojit~;Pheiroijam Jotin~;Bishworjit Konthoujam~~?~R~RMF;Al Bashid Muhammed~;Aziz Mirza~;Basir Rahman~;Johnson Singh~~?~?~?;Langlonyamba Meitan Keishangbam~;Ulenyai Khwairakpam~~?~?~?;L Kishan Singha~;Ronald Longjam~~?~?~?;Lamabam Ajay~;Soubam Rohit Singh~;Rex Rajkumar~~L~L~LFM;Karnajit Yumnam~~?~?~?
ap|Arunachal Pradesh|ARU|motera|1|P|Neelam Obi~c~?~R~LB;Kamsha Yangfo~vc~W~R~?;Nabam Kama~;Licha Jhon~;Myendung Singpo~;Techi Doria~~?~R~RM;Biki Kumar~;Mibom Mosu~;Yorjum Sera~;Yab Niya~~L~R~RMF;Nabam Tempol~~L~R~RMF;Techi Neri~~?~R~OB;Likha Sonia~;Nabam Dol~;Salil Malhotra~
nag|Nagaland|NAG|sovima|1|P|Chetan Bist~wk;Joshua Ozukum~~?~?~?;Hokaito Zhimomi~~B~R~LM;Rongsen Jonathan~~A~R~RM;Imliwati Lemtur~~A~L~SLA;Chopise Hopongkyu~;Bikram Chetri~;Anil Gupta~~?~?~;Odilemba Kichu~;Saurav Kumar~;Nagaho Chishi~;Sedezhalie Rupero~~?~?~?;Neizekho Rupreo~;Akamu Sumi~;Yugandhar Singh~;Vino Zhimomi~~?~?~?
"""
}

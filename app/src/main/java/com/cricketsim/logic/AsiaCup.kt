package com.cricketsim.logic

/**
 * The Men's T20 Asia Cup 2025 (the UAE, 9 to 28 September 2025): eight teams in two groups of four (every team
 * plays the other three once), the top two of each group go to the Super 4 (a round-robin of the four, six
 * matches, no points carried over), and the top two of the Super 4 play the Final.
 *
 * The 12 group matches, the six Super 4 matches and the Final are in the real order at the real grounds (Dubai
 * and Abu Dhabi). Squads are the 2025 squads (Hong Kong's is the 20-man squad they named). Players the game
 * already had keep their ratings; the rest are new with ESTIMATED ratings; Hong Kong is a new national team.
 *
 * Super 4 places: A1 and A2 are the winner and runner-up of Group A, B1 and B2 those of Group B. The six matches
 * are the real order: B1 v B2, A2 v A1, B1 v A2, A1 v B2, A2 v B2, A1 v B1. The Final is in Dubai (an assumption).
 */
object AsiaCup {
    const val TOURNAMENT_ID = "asia2025"
    const val TOURNAMENT_NAME = "Asia Cup 2025"
    const val SAVE_VERSION = 1

    const val SUPER_4 = "Super 4"
    const val FINAL = "Final"

    private val GROUPS = listOf(
        "A" to listOf("IND", "PAK", "UAE", "OMA"),
        "B" to listOf("SL", "BAN", "AFG", "HK")
    )

    private val KEY_TO_ID = mapOf(
        "IND" to "team_ind", "PAK" to "team_pak", "UAE" to "team_uae", "OMA" to "team_oman",
        "SL" to "team_sl", "BAN" to "team_ban", "AFG" to "team_afg", "HK" to "team_hk"
    )

    private const val DUBAI = "dubai-international"
    private const val ABU_DHABI = "sheikh-zayed"

    /** The six Super 4 matches in the real order: home place, away place, ground. */
    private val SUPER_TEMPLATE = listOf(
        listOf("B1", "B2", DUBAI), listOf("A2", "A1", DUBAI), listOf("B1", "A2", ABU_DHABI),
        listOf("A1", "B2", DUBAI), listOf("A2", "B2", DUBAI), listOf("A1", "B1", DUBAI)
    )

    private const val SUPER_FIRST_ID = 12
    private const val FINAL_ID = 18

    val stadiums: List<Stadium> by lazy { listOf(DUBAI, ABU_DHABI).mapNotNull { StadiumData.getStadiumById(it) } }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    fun isTeam(teamId: String): Boolean = KEY_TO_ID.containsValue(teamId)

    fun shortNameOrNull(teamId: String): String? = defs.find { it.id == teamId }?.code

    // ---- Squads -------------------------------------------------------------------------------

    private class Spec(val role: PlayerRole, val bat: Int, val bowl: Int, val style: BowlingStyle)

    private class Slot(val name: String, val spec: Spec?)

    private class Def(val id: String, val name: String, val code: String, val squad: List<Slot>)

    private fun roleOf(s: String): PlayerRole = when (s) {
        "BWL" -> PlayerRole.BOWLER
        "AR" -> PlayerRole.ALL_ROUNDER
        "WK" -> PlayerRole.WICKETKEEPER
        else -> PlayerRole.BATSMAN
    }

    private val defs: List<Def> by lazy {
        DEFS_TEXT.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val f = line.split('|')
            val slots = f[3].split(';').map { entry ->
                val p = entry.split('~')
                if (p.size < 5) Slot(p[0], null)
                else Slot(p[0], Spec(roleOf(p[1]), p[2].toInt(), p[3].toInt(), if (p[4] == "S") BowlingStyle.SPIN else BowlingStyle.PACE))
            }
            Def(f[0], f[1], f[2], slots)
        }
    }

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' }

    private fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)

    /** The eight national squads (strongest first). Everyone is a plain squad member: no quotas apply. */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val roster = CricketData.getAllTeams().flatMap { it.players }.associateBy { norm(it.name) }
        val tags = LinkedHashMap<String, SquadTag>()
        val teams = defs.map { def ->
            val players = def.squad.mapIndexed { index, slot ->
                val id = "${def.id}_ac${index + 1}"
                val base = roster[norm(slot.name)]
                val player = if (slot.spec == null && base != null) {
                    base.copy(id = id, teamId = def.id)
                } else {
                    val s = slot.spec ?: Spec(PlayerRole.BATSMAN, 60, 20, BowlingStyle.PACE)
                    Player(
                        id = id, teamId = def.id, name = slot.name, role = PlayerProfiles.role(slot.name, s.role),
                        battingRating = s.bat, bowlingRating = s.bowl,
                        fieldingRating = CricketData.deriveFieldingRating(PlayerProfiles.role(slot.name, s.role), s.bat, s.bowl),
                        bowlingStyle = PlayerProfiles.style(slot.name, s.style)
                    )
                }
                tags[id] = SquadTag.DOMESTIC
                player
            }.sortedByDescending { maxOf(it.battingRating, it.bowlingRating) }
            val bat = avg(players.map { it.battingRating }.sortedDescending().take(8))
            val bowl = avg(players.map { it.bowlingRating }.sortedDescending().take(5))
            val field = avg(players.map { it.fieldingRating })
            val national = CricketData.getTeamById(def.id)
            national?.copy(battingRating = bat, bowlingRating = bowl, fieldingRating = field, players = players)
                ?: Team(
                    id = def.id, name = def.name, countryCode = def.code,
                    battingRating = bat, bowlingRating = bowl, fieldingRating = field, players = players
                )
        }
        return Customisation.applyToBuilt(Pair(teams, tags), false)
    }

    // ---- Fixtures -----------------------------------------------------------------------------

    private fun idOf(key: String): String = KEY_TO_ID.getValue(key)

    /** 19 matches: 12 group matches, six Super 4 matches and the Final. */
    fun buildSchedule(): List<TournamentFixture> {
        val list = ArrayList<TournamentFixture>()
        for (line in GROUP_FIXTURES.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
            val p = line.split(',')
            val letter = GROUPS.first { p[0] in it.second }.first
            list.add(TournamentFixture(list.size, list.size + 1, "Group $letter", idOf(p[0]), idOf(p[1]), p[2]))
        }
        for (t in SUPER_TEMPLATE) {
            val note = "Super 4: ${placeName(t[0])} v ${placeName(t[1])}"
            list.add(TournamentFixture(list.size, list.size + 1, SUPER_4, null, null, t[2], placeholder = note))
        }
        list.add(TournamentFixture(FINAL_ID, FINAL_ID + 1, FINAL, null, null, DUBAI,
            placeholder = "The top two of the Super 4"))
        return list
    }

    private fun placeName(place: String): String =
        (if (place[0] == 'A') "Group A " else "Group B ") + (if (place[1] == '1') "winner" else "runner-up")

    fun newTournament(userTeamId: String, built: Pair<List<Team>, Map<String, SquadTag>>): TournamentState =
        TournamentState(
            version = SAVE_VERSION,
            tournamentId = TOURNAMENT_ID,
            name = TOURNAMENT_NAME,
            userTeamId = userTeamId,
            teams = built.first,
            tags = built.second,
            fixtures = buildSchedule(),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )

    // ---- Tables and progress ------------------------------------------------------------------

    private fun teamsOfStage(state: TournamentState, stage: String): Set<String> =
        state.fixtures.filter { it.stage == stage }.flatMap { listOfNotNull(it.homeId, it.awayId) }.toSet()

    private fun stageTable(state: TournamentState, stage: String): List<StandingRow> {
        val ids = teamsOfStage(state, stage)
        return CplData.standings(state, stage).filter { it.team.id in ids }
    }

    /** The two group tables, then the Super 4 table once it is known. */
    fun tables(state: TournamentState): List<Pair<String, List<StandingRow>>> =
        (GROUPS.map { "Group ${it.first}" } + SUPER_4).filter { teamsOfStage(state, it).isNotEmpty() }
            .map { it to stageTable(state, it) }

    /** Fills in the Super 4 and the Final as the teams become known. Safe after every match. */
    fun advance(state: TournamentState): TournamentState {
        var fx = state.fixtures
        fun complete(stage: String): Boolean {
            val l = fx.filter { it.stage == stage }
            return l.isNotEmpty() && l.all { it.result != null }
        }
        if (complete("Group A") && complete("Group B")) {
            val a = stageTable(state, "Group A").map { it.team.id }
            val b = stageTable(state, "Group B").map { it.team.id }
            if (a.size >= 2 && b.size >= 2) {
                val places = mapOf("A1" to a[0], "A2" to a[1], "B1" to b[0], "B2" to b[1])
                fx = fx.map { f ->
                    val t = f.id - SUPER_FIRST_ID
                    if (f.homeId == null && t >= 0 && t < SUPER_TEMPLATE.size) {
                        f.copy(homeId = places[SUPER_TEMPLATE[t][0]], awayId = places[SUPER_TEMPLATE[t][1]])
                    } else f
                }
            }
        }
        val now = state.copy(fixtures = fx)
        if (complete(SUPER_4)) {
            val top = stageTable(now, SUPER_4).map { it.team.id }
            if (top.size >= 2) {
                fx = fx.map { f -> if (f.id == FINAL_ID && f.homeId == null) f.copy(homeId = top[0], awayId = top[1]) else f }
            }
        }
        return state.copy(fixtures = fx)
    }

    // ---- Data ---------------------------------------------------------------------------------

    private const val GROUP_FIXTURES = """
AFG,HK,sheikh-zayed
UAE,IND,dubai-international
HK,BAN,sheikh-zayed
PAK,OMA,dubai-international
BAN,SL,sheikh-zayed
PAK,IND,dubai-international
UAE,OMA,sheikh-zayed
HK,SL,dubai-international
BAN,AFG,sheikh-zayed
PAK,UAE,dubai-international
AFG,SL,sheikh-zayed
IND,OMA,sheikh-zayed
"""

    /** Team id | name | short name | squad. "Name" copies a game player; "Name~ROLE~bat~bowl~S or P" has its ratings written here. */
    private const val DEFS_TEXT = """
team_afg|Afghanistan|AFG|Rashid Khan;Fareed Ahmad;Noor Ahmad;Abdullah Ahmadzai~BWL~20~62~P;Sediqullah Atal;Sharafuddin Ashraf;Fazalhaq Farooqi;Allah Mohammad Ghazanfar;Rahmanullah Gurbaz;Mohammad Ishaq;Karim Janat;Gulbadin Naib;Mohammad Nabi;Azmatullah Omarzai;Mujeeb Ur Rahman;Darwish Rasooli;Ibrahim Zadran
team_ban|Bangladesh|BAN|Litton Das;Nasum Ahmed;Taskin Ahmed;Jaker Ali;Parvez Hossain Emon;Nurul Hasan Sohan;Tanzid Hasan;Tanzim Hasan Sakib;Saif Hassan~BAT~66~25~S;Rishad Hossain;Shamim Hossain;Towhid Hridoy;Shoriful Islam;Mehidy Hasan Miraz;Mustafizur Rahman;Mohammad Saifuddin~AR~54~68~P
team_hk|Hong Kong|HKG|Yasim Murtaza~AR~62~55~S;Babar Hayat~BAT~70~20~S;Zeeshan Ali~WK~62~10~P;Haroon Arshad~AR~60~50~S;Kalhan Challu~BAT~62~20~S;Martin Coetzee~BAT~62~25~P;Mohammad Ghazanfar~BWL~20~62~P;Ali Hassan~BWL~25~60~P;Ateeq Iqbal~BWL~25~60~P;Aizaz Khan~AR~55~60~P;Anas Khan~AR~50~58~S;Ehsan Khan~BWL~25~66~S;Nizakat Khan~AR~66~52~S;Adil Mehmood~BWL~25~60~P;Nasrulla Rana~BAT~55~20~P;Anshuman Rath~BAT~68~25~S;Kinchit Shah~AR~62~50~P;Ayush Shukla~BWL~30~66~S;Mohammad Waheed~BWL~20~58~S;Shahid Wasif~WK~60~10~P
team_ind|India|IND|Suryakumar Yadav;Shubman Gill;Abhishek Sharma;Tilak Varma;Hardik Pandya;Shivam Dube;Axar Patel;Jitesh Sharma~WK~72~8~P;Jasprit Bumrah;Arshdeep Singh;Varun Chakravarthy;Kuldeep Yadav;Sanju Samson;Harshit Rana;Rinku Singh
team_oman|Oman|OMA|Jatinder Singh~BAT~72~25~S;Shakeel Ahmed;Wasim Ali;Aryan Bisht~BAT~56~20~P;Shah Faisal~BWL~30~66~P;Muhammed Imran~BAT~55~30~P;Zikria Islam~BWL~25~58~P;Aamir Kaleem~AR~60~64~S;Nadeem Khan;Sufyan Mehmood~BWL~20~68~P;Hammad Mirza~WK~66~10~P;Mohammad Nadeem~BWL~20~66~P;Ashish Odedara~AR~50~60~S;Jiten Ramanandi~AR~62~50~P;Hassnain Shah~BAT~58~20~P;Samay Shrivastava;Vinayak Shukla;Karan Sonavale~AR~58~55~S;Sufyan Yousuf~WK~60~10~P
team_pak|Pakistan|PAK|Salman Agha;Shaheen Afridi;Abrar Ahmed;Hasan Ali;Faheem Ashraf;Saim Ayub;Sahibzada Farhan~BAT~80~10~P;Mohammad Haris;Salman Mirza~BWL~20~74~P;Sufiyan Muqeem~BWL~15~78~S;Hassan Nawaz~BAT~74~10~P;Mohammad Nawaz;Haris Rauf;Khushdil Shah;Hussain Talat~AR~62~50~P;Mohammad Wasim Jr;Fakhar Zaman
team_sl|Sri Lanka|SL|Charith Asalanka;Dushmantha Chameera~BWL~16~78~P;Binura Fernando~BWL~18~76~P;Nuwanidu Fernando;Wanindu Hasaranga;Chamika Karunaratne;Janith Liyanage;Kamindu Mendis;Kusal Mendis;Kamil Mishara;Pathum Nissanka;Matheesha Pathirana;Kusal Perera;Dasun Shanaka;Maheesh Theekshana;Nuwan Thushara;Dunith Wellalage
team_uae|United Arab Emirates|UAE|Muhammad Waseem;Haider Ali~BAT~66~10~P;Rahul Chopra~WK~62~10~P;Ethan D'Souza~BAT~60~20~P;Muhammad Farooq~BWL~25~62~S;Muhammad Jawadullah;Harshit Kaushik~BWL~20~60~P;Asif Khan;Matiullah Khan~BWL~20~60~P;Saghir Khan~BWL~20~62~P;Dhruv Parashar~AR~55~62~P;Muhammad Rohid~BAT~62~10~P;Alishan Sharafu;Aryansh Sharma~WK~64~10~P;Junaid Siddique;Simranjeet Singh;Muhammad Zohaib~BAT~58~20~P
"""
}

package com.cricketsim.logic

/**
 * The men's cricket event of the 2026 Asian Games (Kōrogi Sports Park, Nisshin, Japan, 24 September to
 * 3 October 2026), Twenty20. Ten teams: the six lowest seeds are drawn into two groups of three (a single
 * round-robin; the top two of each group go on), and the four highest seeds (India, Pakistan, Sri Lanka and
 * Bangladesh) wait in the quarter-finals. Then the semi-finals, the bronze medal match and the gold medal match
 * (the "Final" here, so its winner is the champion).
 *
 * Group A: Nepal, Afghanistan, Japan. Group B: Malaysia, Hong Kong, Oman. The quarter-finals are the real draw:
 * Pakistan v the Group B runner-up, India v the Group A runner-up, Bangladesh v the Group B winner, Sri Lanka v
 * the Group A winner. The semi-finals: Pakistan's quarter-final winner v Bangladesh's, India's v Sri Lanka's.
 * Every match is at Kōrogi Sports Park (a new ground in the game, ratings estimated).
 *
 * Squads are the 15-man squads. Players the game already had keep their ratings; 66 are new with ESTIMATED
 * ratings (Japan and Malaysia are new national teams; for the smaller squads the ratings are a tier guess).
 */
object AsianGames {
    const val TOURNAMENT_ID = "asiangames2026"
    const val TOURNAMENT_NAME = "Asian Games 2026"
    const val SAVE_VERSION = 1

    const val BRONZE = "Bronze medal match"
    const val FINAL = "Final"

    private const val GROUND = "korogi-sports-park"

    private val GROUPS = listOf(
        "A" to listOf("NEP", "AFG", "JPN"),
        "B" to listOf("MAS", "HK", "OMA")
    )

    private val KEY_TO_ID = mapOf(
        "NEP" to "team_nep", "AFG" to "team_afg", "JPN" to "team_jpn", "MAS" to "team_mas", "HK" to "team_hk",
        "OMA" to "team_oman", "IND" to "team_ind", "PAK" to "team_pak", "SL" to "team_sl", "BAN" to "team_ban"
    )

    private const val QF_1_ID = 6
    private const val QF_2_ID = 7
    private const val QF_3_ID = 8
    private const val QF_4_ID = 9
    private const val SEMI_1_ID = 10
    private const val SEMI_2_ID = 11
    private const val BRONZE_ID = 12
    private const val FINAL_ID = 13

    val stadiums: List<Stadium> by lazy { listOfNotNull(StadiumData.getStadiumById(GROUND)) }

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

    /** The ten squads (strongest first). Everyone is a plain squad member: no quotas apply. */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val roster = CricketData.getAllTeams().flatMap { it.players }.associateBy { norm(it.name) }
        val tags = LinkedHashMap<String, SquadTag>()
        val teams = defs.map { def ->
            val players = def.squad.mapIndexed { index, slot ->
                val id = "${def.id}_ag${index + 1}"
                val base = roster[norm(slot.name)]
                val player = if (slot.spec == null && base != null) {
                    base.copy(id = id, teamId = def.id)
                } else {
                    val s = slot.spec ?: Spec(PlayerRole.BATSMAN, 55, 15, BowlingStyle.PACE)
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
        return Pair(teams, tags)
    }

    // ---- Fixtures -----------------------------------------------------------------------------

    private fun idOf(key: String): String = KEY_TO_ID.getValue(key)

    /** 14 matches: six group matches, four quarter-finals, two semi-finals, the bronze medal match and the Final. */
    fun buildSchedule(): List<TournamentFixture> {
        val list = ArrayList<TournamentFixture>()
        val groupMatches = listOf(
            "AFG" to "JPN", "HK" to "MAS", "AFG" to "NEP", "HK" to "OMA", "NEP" to "JPN", "MAS" to "OMA"
        )
        for ((a, b) in groupMatches) {
            val letter = GROUPS.first { a in it.second }.first
            list.add(TournamentFixture(list.size, list.size + 1, "Group $letter", idOf(a), idOf(b), GROUND))
        }
        fun knockout(stage: String, note: String) =
            list.add(TournamentFixture(list.size, list.size + 1, stage, null, null, GROUND, placeholder = note))
        knockout("Quarter-final 1", "Pakistan v the Group B runner-up")
        knockout("Quarter-final 2", "India v the Group A runner-up")
        knockout("Quarter-final 3", "Bangladesh v the Group B winner")
        knockout("Quarter-final 4", "Sri Lanka v the Group A winner")
        knockout("Semi-final 1", "Winner of quarter-final 1 v winner of quarter-final 3")
        knockout("Semi-final 2", "Winner of quarter-final 2 v winner of quarter-final 4")
        knockout(BRONZE, "The losers of the two semi-finals")
        knockout(FINAL, "Gold medal match: the winners of the two semi-finals")
        return list
    }

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

    /** The two group tables, with their headings. */
    fun tables(state: TournamentState): List<Pair<String, List<StandingRow>>> =
        GROUPS.map { "Group ${it.first}" }.filter { teamsOfStage(state, it).isNotEmpty() }
            .map { it to stageTable(state, it) }

    /** Fills in the quarter-finals, semi-finals, bronze match and Final as the teams become known. Safe after every match. */
    fun advance(state: TournamentState): TournamentState {
        var fx = state.fixtures
        fun complete(stage: String): Boolean {
            val l = fx.filter { it.stage == stage }
            return l.isNotEmpty() && l.all { it.result != null }
        }
        fun winnerOf(id: Int): String? = fx.firstOrNull { it.id == id }?.result?.winnerId
        fun loserOf(id: Int): String? {
            val f = fx.firstOrNull { it.id == id } ?: return null
            val w = f.result?.winnerId ?: return null
            return if (f.homeId == w) f.awayId else f.homeId
        }
        fun fill(id: Int, home: String?, away: String?) {
            if (home == null || away == null) return
            fx = fx.map { f -> if (f.id == id && f.homeId == null) f.copy(homeId = home, awayId = away) else f }
        }
        if (complete("Group A") && complete("Group B")) {
            val a = stageTable(state, "Group A").map { it.team.id }
            val b = stageTable(state, "Group B").map { it.team.id }
            if (a.size >= 2 && b.size >= 2) {
                fill(QF_1_ID, idOf("PAK"), b[1])
                fill(QF_2_ID, idOf("IND"), a[1])
                fill(QF_3_ID, idOf("BAN"), b[0])
                fill(QF_4_ID, idOf("SL"), a[0])
            }
        }
        fill(SEMI_1_ID, winnerOf(QF_1_ID), winnerOf(QF_3_ID))
        fill(SEMI_2_ID, winnerOf(QF_2_ID), winnerOf(QF_4_ID))
        fill(BRONZE_ID, loserOf(SEMI_1_ID), loserOf(SEMI_2_ID))
        fill(FINAL_ID, winnerOf(SEMI_1_ID), winnerOf(SEMI_2_ID))
        return state.copy(fixtures = fx)
    }

    // ---- Data ---------------------------------------------------------------------------------

    /** Team id | name | short name | squad. "Name" copies a game player; "Name~ROLE~bat~bowl~S or P" has its ratings written here. */
    private const val DEFS_TEXT = """
team_afg|Afghanistan|AFG|Darwish Rasooli;Abdullah Ahmadzai~BWL~20~62~P;Mohammad Akram~AR~54~52~P;Ismat Alam~AR~54~52~P;Faridoon Dawoodzai~BWL~20~62~P;Hassan Eisakhil~BAT~60~10~P;Mohammad Ishaq;Karim Janat;Faisal Shinozada~BAT~60~10~P;Nangeyalia Kharote;Imran Mir~AR~50~55~S;Arab Gul Momand~BAT~60~10~P;Farmanullah Safi~BAT~60~10~P;Khalid Taniwal~BAT~60~10~P;Najibullah Zadran
team_ban|Bangladesh|BAN|Litton Das;Towhid Hridoy;Nasum Ahmed;Yasir Ali~BAT~66~10~P;Parvez Hossain Emon;Mehidy Hasan Miraz;Tanzid Hasan;Saif Hassan~BAT~66~25~S;Mosaddek Hossain~AR~62~58~S;Rishad Hossain;Aliss Islam~BWL~25~66~P;Shoriful Islam;Hasan Mahmud;Mustafizur Rahman;Najmul Hossain Shanto
team_hk|Hong Kong|HKG|Yasim Murtaza~AR~62~55~S;Babar Hayat~BAT~70~20~S;Anas Khan~AR~50~58~S;Anshuman Rath~BAT~68~25~S;Shiv Mathur~BAT~58~16~P;Shahid Wasif~WK~60~10~P;Hafeez Khan~BAT~58~10~P;Nasrulla Rana~BAT~55~20~P;Nizakat Khan~AR~66~52~S;Rajab Hussain~BAT~58~10~P;Ehsan Khan~BWL~25~66~S;Mohammad Ghazanfar~BWL~20~62~P;Mohammad Waheed~BWL~20~58~S;Ayush Shukla~BWL~30~66~S;Mohammad Hassan Khan~BAT~58~10~P
team_ind|India|IND|Shreyas Iyer;Tilak Varma;Ravi Bishnoi~BWL~20~79~S;Jasprit Bumrah;Shivam Dube;Ishan Kishan;Vipraj Nigam~AR~51~46~P;Axar Patel;Sanju Samson;Abhishek Sharma;Arshdeep Singh;Washington Sundar;Vaibhav Sooryavanshi~BAT~56~12~P;Yash Thakur~BWL~16~58~P
team_jpn|Japan|JPN|Kendel Kadowaki-Fleming~BAT~50~16~S;Charlie Hara-Hinze~AR~44~42~S;Benjamin Ito-Davis~BAT~50~10~P;Alester Kadowaki-Fleming~BAT~50~10~P;Kohei Kubota~BAT~50~10~P;Jake Kusuda-Nairn~BAT~50~10~P;Wataru Miyauchi~WK~50~8~P;Reo Sakurano-Thomas~BAT~50~10~P;Alex Shirai-Patmore~WK~50~8~P;Shoma Sugaya-Slater~BAT~50~10~P;Declan Suzuki~BAT~50~10~P;Tsuyoshi Takada~BAT~50~10~P;Ibrahim Takahashi~AR~44~42~S;Makoto Taniyama~BAT~50~10~P;Lachlan Yamamoto-Lake~BAT~50~10~P
team_mas|Malaysia|MAS|Syed Aziz~BAT~55~10~P;Ainool Hafizs~WK~55~8~P;Muhamad Syahadat~BAT~55~10~P;Muhammad Amir~BAT~55~10~P;Virandeep Singh~AR~66~56~S;Pavandeep Singh~AR~49~47~S;Muhammad Haziq Aiman~BAT~55~10~P;Sharvin Muniandy~BAT~55~10~P;Muhammad Akram~BAT~55~10~P;Muhammad Wafiq~AR~49~47~P;Vijay Unni~BAT~55~10~P;Azri Azhar~BAT~55~10~P;Amir Khan~BAT~55~10~P;Aslam Khan~AR~49~47~S;Rahim Khan~BAT~55~10~P
team_nep|Nepal|NEP|Dipendra Singh Airee;Lokesh Bam~AR~55~50~P;Kushal Bhurtel;Gulshan Jha;Sundeep Jora;Sompal Kami;Karan KC;Sandeep Lamichhane;Kushal Malla;Sher Malla~AR~58~55~S;Rohit Paudel;Lalit Rajbanshi;Aarif Sheikh;Aasif Sheikh;Nandan Yadav~BWL~20~66~P
team_oman|Oman|OMA|Sufyan Mehmood~BWL~20~68~P;Muzaffar Shiralkar~WK~58~8~P;Faris Al Balushi~BAT~58~10~P;Issa Al Balushi~BAT~58~10~P;Mohammed Al Balushi~BAT~58~10~P;Nawed Al Balushi~BAT~58~10~P;Qais Al Balushi~BAT~58~10~P;Shuaib Al Balushi~BAT~58~10~P;Waheed Al Balushi~BAT~58~10~P;Wasim Al Balushi~BAT~58~10~P;Zubair Al Balushi~BAT~58~10~P;Abdul Jalil~WK~58~8~P;Mohammed Othman~BAT~58~10~P;Sameer Othman~BAT~58~10~P;Ahmed Al Zadjali~BAT~58~10~P
team_pak|Pakistan|PAK|Sahibzada Farhan~BAT~80~10~P;Abdul Samad~AR~63~58~P;Abrar Ahmed;Haider Ali~BAT~66~10~P;Saim Ayub;Ahmed Daniyal~BWL~15~68~P;Akif Javed~BWL~18~70~P;Usman Khan;Arafat Minhas~BWL~20~70~S;Saad Masood~BAT~64~10~P;Sufiyan Muqeem~BWL~15~78~S;Hassan Nawaz~BAT~74~10~P;Ali Raza~BWL~20~68~P;Maaz Sadaqat~AR~74~55~S;Mohammad Wasim Jr
team_sl|Sri Lanka|SL|Sahan Arachchige;Ashen Bandara~BAT~68~15~P;Lasith Croospulle~AR~62~40~S;Keshan de Livera~BAT~60~10~P;Niroshan Dickwella~WK~72~10~P;Binura Fernando~BWL~18~76~P;Nuwanidu Fernando;Tharindu Hansaka~BAT~60~10~P;Dushan Hemantha~AR~58~66~S;Sineth Jayawardena~AR~55~55~S;Chamika Karunaratne;Pramod Madushan;Wanuja Sahan~AR~56~58~P;Nuwan Thushara;Vijayakanth Viyaskanth~BWL~25~76~S
"""
}

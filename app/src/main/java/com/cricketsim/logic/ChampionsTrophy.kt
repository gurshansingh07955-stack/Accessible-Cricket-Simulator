package com.cricketsim.logic

/**
 * The ICC Champions Trophy 2025 (Pakistan and the UAE, 19 February to 9 March 2025): the game's second
 * international tournament and its first 50-over one. Eight teams in two groups of four (every team plays the
 * other three once), the top two of each group go to the semi-finals, then the Final.
 *
 * The 12 group matches are the real fixtures in the real order at the real grounds (India played all their
 * matches in Dubai). The squads are the final 15-man squads, replacements included. Players the game already
 * had keep their ratings; five are new with ESTIMATED ratings.
 *
 * Semi-finals: winner of Group A v runner-up of Group B, and winner of Group B v runner-up of Group A. As in
 * real life any match India play is in Dubai; otherwise the first semi-final is in Karachi, the second and the
 * Final in Lahore (an assumption for the cases the real tournament did not have).
 *
 * The matches are 50 overs a side. Between computer sides the ball model has an ODI setting (see AiBallModel).
 */
object ChampionsTrophy {
    const val TOURNAMENT_ID = "ct2025"
    const val TOURNAMENT_NAME = "Champions Trophy 2025"
    const val SAVE_VERSION = 1

    const val SEMI_1 = "Semi-final 1"
    const val SEMI_2 = "Semi-final 2"
    const val FINAL = "Final"

    private val GROUPS = listOf(
        "A" to listOf("IND", "PAK", "BAN", "NZ"),
        "B" to listOf("AFG", "AUS", "ENG", "SA")
    )

    private val KEY_TO_ID = mapOf(
        "IND" to "team_ind", "PAK" to "team_pak", "BAN" to "team_ban", "NZ" to "team_nz",
        "AFG" to "team_afg", "AUS" to "team_aus", "ENG" to "team_eng", "SA" to "team_sa"
    )

    private const val INDIA_ID = "team_ind"
    private const val DUBAI = "dubai-international"
    private const val KARACHI = "national-stadium-karachi"
    private const val LAHORE = "gaddafi"

    private const val SEMI_1_ID = 12
    private const val SEMI_2_ID = 13
    private const val FINAL_ID = 14

    /** The four grounds (two in Pakistan's Punjab, one in Karachi, one in Dubai). */
    val stadiums: List<Stadium> by lazy {
        listOf(LAHORE, KARACHI, "rawalpindi", DUBAI).mapNotNull { StadiumData.getStadiumById(it) }
    }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    fun isTeam(teamId: String): Boolean = KEY_TO_ID.containsValue(teamId)

    fun shortNameOrNull(teamId: String): String? = defs.find { it.id == teamId }?.code

    // ---- Squads -------------------------------------------------------------------------------

    private class Spec(val role: PlayerRole, val bat: Int, val bowl: Int, val style: BowlingStyle)

    /** A squad place: a player copied from the game (spec null) or one with ratings written here (estimated or copied). */
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
                val id = "${def.id}_ct${index + 1}"
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
        return Pair(teams, tags)
    }

    // ---- Fixtures -----------------------------------------------------------------------------

    private fun idOf(key: String): String = KEY_TO_ID.getValue(key)

    /** 15 matches: 12 group matches (real order), two semi-finals and the Final. */
    fun buildSchedule(): List<TournamentFixture> {
        val list = ArrayList<TournamentFixture>()
        for (line in GROUP_FIXTURES.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
            val p = line.split(',')
            val letter = GROUPS.first { p[0] in it.second }.first
            list.add(TournamentFixture(list.size, list.size + 1, "Group $letter", idOf(p[0]), idOf(p[1]), p[2]))
        }
        list.add(TournamentFixture(SEMI_1_ID, SEMI_1_ID + 1, SEMI_1, null, null, DUBAI,
            placeholder = "Winner of Group A v runner-up of Group B"))
        list.add(TournamentFixture(SEMI_2_ID, SEMI_2_ID + 1, SEMI_2, null, null, LAHORE,
            placeholder = "Winner of Group B v runner-up of Group A"))
        list.add(TournamentFixture(FINAL_ID, FINAL_ID + 1, FINAL, null, null, LAHORE,
            placeholder = "Winners of the two semi-finals"))
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

    private fun venueFor(home: String?, away: String?, otherwise: String): String =
        if (home == INDIA_ID || away == INDIA_ID) DUBAI else otherwise

    /** Fills in the semi-finals and the Final (and their grounds) as the teams become known. Safe after every match. */
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
                fx = fx.map { f ->
                    when {
                        f.id == SEMI_1_ID && f.homeId == null ->
                            f.copy(homeId = a[0], awayId = b[1], stadiumId = venueFor(a[0], b[1], KARACHI))
                        f.id == SEMI_2_ID && f.homeId == null ->
                            f.copy(homeId = b[0], awayId = a[1], stadiumId = venueFor(b[0], a[1], LAHORE))
                        else -> f
                    }
                }
            }
        }
        val semi1 = fx.firstOrNull { it.id == SEMI_1_ID }?.result?.winnerId
        val semi2 = fx.firstOrNull { it.id == SEMI_2_ID }?.result?.winnerId
        if (semi1 != null && semi2 != null) {
            fx = fx.map { f ->
                if (f.id == FINAL_ID && f.homeId == null) {
                    f.copy(homeId = semi1, awayId = semi2, stadiumId = venueFor(semi1, semi2, LAHORE))
                } else f
            }
        }
        return state.copy(fixtures = fx)
    }

    // ---- Data ---------------------------------------------------------------------------------

    private const val GROUP_FIXTURES = """
NZ,PAK,national-stadium-karachi
BAN,IND,dubai-international
SA,AFG,national-stadium-karachi
ENG,AUS,gaddafi
PAK,IND,dubai-international
BAN,NZ,rawalpindi
AUS,SA,rawalpindi
AFG,ENG,gaddafi
PAK,BAN,rawalpindi
AFG,AUS,gaddafi
ENG,SA,national-stadium-karachi
IND,NZ,dubai-international
"""

    /** Team id | name | short name | squad. "Name" copies a game player; "Name~ROLE~bat~bowl~S or P" has its ratings written here. */
    private const val DEFS_TEXT = """
team_ind|India|IND|Rohit Sharma;Shubman Gill;Varun Chakravarthy;Shreyas Iyer;Ravindra Jadeja;Virat Kohli;Hardik Pandya;Rishabh Pant;Axar Patel;KL Rahul;Harshit Rana;Mohammed Shami~BWL~20~80~P;Arshdeep Singh;Washington Sundar;Kuldeep Yadav
team_pak|Pakistan|PAK|Mohammad Rizwan;Salman Agha;Shaheen Afridi;Abrar Ahmed;Faheem Ashraf;Babar Azam;Kamran Ghulam;Mohammad Hasnain~BWL~15~74~P;Usman Khan;Haris Rauf;Khushdil Shah;Naseem Shah;Saud Shakeel;Tayyab Tahir~BAT~68~10~P;Imam-ul-Haq
team_ban|Bangladesh|BAN|Najmul Hossain Shanto;Nasum Ahmed;Taskin Ahmed;Jaker Ali;Parvez Hossain Emon;Tanzid Hasan;Rishad Hossain;Towhid Hridoy;Mahmudullah;Mehidy Hasan Miraz;Mushfiqur Rahim;Mustafizur Rahman;Nahid Rana~BWL~20~78~P;Tanzim Hasan Sakib;Soumya Sarkar
team_nz|New Zealand|NZ|Mitchell Santner;Michael Bracewell;Mark Chapman;Devon Conway;Jacob Duffy;Matt Henry;Kyle Jamieson;Tom Latham;Daryl Mitchell;William O'Rourke;Glenn Phillips;Rachin Ravindra;Nathan Smith~AR~45~62~P;Kane Williamson;Will Young
team_aus|Australia|AUS|Steve Smith;Sean Abbott;Alex Carey;Cooper Connolly~AR~68~52~S;Ben Dwarshuis;Nathan Ellis;Jake Fraser-McGurk;Aaron Hardie;Travis Head;Josh Inglis;Spencer Johnson;Marnus Labuschagne;Glenn Maxwell;Tanveer Sangha;Adam Zampa
team_sa|South Africa|RSA|Temba Bavuma;Corbin Bosch;Tony de Zorzi;Marco Jansen;Heinrich Klaasen;Keshav Maharaj;Aiden Markram;David Miller;Wiaan Mulder;Lungi Ngidi;Kagiso Rabada;Ryan Rickelton;Tabraiz Shamsi;Tristan Stubbs;Rassie van der Dussen
team_afg|Afghanistan|AFG|Hashmatullah Shahidi;Fareed Ahmad;Noor Ahmad;Ikram Alikhil;Sediqullah Atal;Fazalhaq Farooqi;Rahmanullah Gurbaz;Rashid Khan;Nangeyalia Kharote;Mohammad Nabi;Gulbadin Naib;Azmatullah Omarzai;Rahmat Shah;Ibrahim Zadran;Naveed Zadran~BAT~60~10~P
team_eng|England|ENG|Jos Buttler;Rehan Ahmed;Jofra Archer;Gus Atkinson;Tom Banton~WK~78~10~P;Harry Brook;Ben Duckett;Liam Livingstone;Saqib Mahmood;Jamie Overton~AR~56~76~P;Adil Rashid;Joe Root;Phil Salt;Jamie Smith;Mark Wood
"""
}

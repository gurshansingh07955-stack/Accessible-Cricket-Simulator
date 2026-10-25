package com.cricketsim.logic

/**
 * The ICC Men's T20 World Cup 2026 (India and Sri Lanka, 7 February to 8 March 2026): the game's first
 * international tournament. Twenty national teams in four groups of five (every team plays the other four
 * once), the top two of each group go to the Super 8 (two groups of four, round-robin), the top two of each
 * Super 8 group go to the semi-finals, then the Final. The 40 group matches are the real fixtures in the
 * real order at the real grounds (all eight grounds were already in the game).
 *
 * Squads are the final 2026 squads (players the game already had keep their ratings; the rest are new with
 * ESTIMATED ratings). Bangladesh did not play: Scotland took their place, as in real life.
 *
 * The Super 8 uses the ICC's pre-seeded places: Group 1 places A1 India, B1 Australia, C1 West Indies, D1
 * South Africa; Group 2 places A2 Pakistan, B2 Sri Lanka, C2 England, D2 New Zealand. A seeded team that
 * qualifies takes its own place; an unseeded team that qualifies takes the place of a seeded team that
 * did not (as Zimbabwe took Australia's). The order and grounds of the 12 Super 8 matches are the real ones.
 */
object T20WorldCup {
    const val TOURNAMENT_ID = "t20wc2026"
    const val TOURNAMENT_NAME = "T20 World Cup 2026"
    const val SAVE_VERSION = 1

    const val SUPER_1 = "Super 8 Group 1"
    const val SUPER_2 = "Super 8 Group 2"
    const val SEMI_1 = "Semi-final 1"
    const val SEMI_2 = "Semi-final 2"
    const val FINAL = "Final"

    /** Group letter to the five teams (team keys, see KEY_TO_ID). */
    private val GROUPS = listOf(
        "A" to listOf("IND", "NAM", "NED", "PAK", "USA"),
        "B" to listOf("AUS", "IRE", "OMA", "SL", "ZIM"),
        "C" to listOf("ENG", "ITA", "NEP", "SCO", "WI"),
        "D" to listOf("AFG", "CAN", "NZ", "SA", "UAE")
    )

    /** The seeded Super 8 places: group letter + 1 (Super 8 Group 1) or + 2 (Super 8 Group 2). */
    private val SEEDS = mapOf(
        "A1" to "IND", "A2" to "PAK", "B1" to "AUS", "B2" to "SL",
        "C1" to "WI", "C2" to "ENG", "D1" to "SA", "D2" to "NZ"
    )

    private val KEY_TO_ID = mapOf(
        "IND" to "team_ind", "AUS" to "team_aus", "ENG" to "team_eng", "PAK" to "team_pak", "SA" to "team_sa",
        "NZ" to "team_nz", "SL" to "team_sl", "WI" to "team_wi", "AFG" to "team_afg", "IRE" to "team_ire",
        "ZIM" to "team_zim", "NED" to "team_ned", "SCO" to "team_sco", "NEP" to "team_nep", "UAE" to "team_uae",
        "OMA" to "team_oman", "USA" to "team_usa", "NAM" to "team_nam", "ITA" to "team_ita", "CAN" to "team_can"
    )

    /** The 12 Super 8 matches in the real order: home place, away place, group number, ground. */
    private val SUPER_TEMPLATE = listOf(
        listOf("A2", "D2", "2", "premadasa"), listOf("C2", "B2", "2", "pallekele"),
        listOf("D1", "A1", "1", "narendra-modi-ahmedabad"), listOf("C1", "B1", "1", "wankhede"),
        listOf("A2", "C2", "2", "pallekele"), listOf("D2", "B2", "2", "premadasa"),
        listOf("C1", "D1", "1", "narendra-modi-ahmedabad"), listOf("A1", "B1", "1", "chepauk"),
        listOf("D2", "C2", "2", "premadasa"), listOf("A2", "B2", "2", "pallekele"),
        listOf("B1", "D1", "1", "arun-jaitley"), listOf("C1", "A1", "1", "eden-gardens")
    )

    private const val SUPER_FIRST_ID = 40
    private const val SEMI_1_ID = 52
    private const val SEMI_2_ID = 53
    private const val FINAL_ID = 54

    /** The eight grounds of the tournament (five in India, three in Sri Lanka); all were already in the game. */
    val stadiums: List<Stadium> by lazy {
        listOf(
            "narendra-modi-ahmedabad", "chepauk", "arun-jaitley", "eden-gardens", "wankhede",
            "premadasa", "ssc-ground", "pallekele"
        ).mapNotNull { StadiumData.getStadiumById(it) }
    }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    fun isTeam(teamId: String): Boolean = KEY_TO_ID.containsValue(teamId)

    fun shortNameOrNull(teamId: String): String? = defs.find { it.id == teamId }?.code

    // ---- Squads -------------------------------------------------------------------------------

    private class Spec(val role: PlayerRole, val bat: Int, val bowl: Int, val style: BowlingStyle)

    /** A squad place: a player copied from the game roster (spec null) or a new one with estimated ratings. */
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

    /** The twenty national squads (strongest first). Everyone is a plain squad member: no quotas apply. */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val roster = CricketData.getAllTeams().flatMap { it.players }.associateBy { norm(it.name) }
        val tags = LinkedHashMap<String, SquadTag>()
        val teams = defs.map { def ->
            val players = def.squad.mapIndexed { index, slot ->
                val id = "${def.id}_wc${index + 1}"
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

    private fun nameOfSeed(place: String): String = defs.firstOrNull { it.id == idOf(SEEDS.getValue(place)) }?.name ?: place

    /** All 55 matches: 40 group matches (real order), 12 Super 8 matches, two semi-finals and the Final. */
    fun buildSchedule(): List<TournamentFixture> {
        val list = ArrayList<TournamentFixture>()
        for (line in GROUP_FIXTURES.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
            val p = line.split(',')
            val letter = GROUPS.first { p[0] in it.second }.first
            list.add(TournamentFixture(list.size, list.size + 1, "Group $letter", idOf(p[0]), idOf(p[1]), p[2]))
        }
        for (t in SUPER_TEMPLATE) {
            val stage = if (t[2] == "1") SUPER_1 else SUPER_2
            val note = "The place seeded for ${nameOfSeed(t[0])} v the place seeded for ${nameOfSeed(t[1])}"
            list.add(TournamentFixture(list.size, list.size + 1, stage, null, null, t[3], placeholder = note))
        }
        list.add(TournamentFixture(SEMI_1_ID, SEMI_1_ID + 1, SEMI_1, null, null, "eden-gardens",
            placeholder = "Winner of Super 8 Group 1 v runner-up of Super 8 Group 2"))
        list.add(TournamentFixture(SEMI_2_ID, SEMI_2_ID + 1, SEMI_2, null, null, "wankhede",
            placeholder = "Winner of Super 8 Group 2 v runner-up of Super 8 Group 1"))
        list.add(TournamentFixture(FINAL_ID, FINAL_ID + 1, FINAL, null, null, "narendra-modi-ahmedabad",
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

    /** The table of one stage (a group, or a Super 8 group), best first, only the teams of that stage. */
    private fun stageTable(state: TournamentState, stage: String): List<StandingRow> {
        val ids = teamsOfStage(state, stage)
        return CplData.standings(state, stage).filter { it.team.id in ids }
    }

    /** Every table that exists so far, with its heading: the four groups, then the Super 8 groups once known. */
    fun tables(state: TournamentState): List<Pair<String, List<StandingRow>>> {
        val out = ArrayList<Pair<String, List<StandingRow>>>()
        val stages = GROUPS.map { "Group ${it.first}" } + listOf(SUPER_1, SUPER_2)
        for (stage in stages) {
            if (teamsOfStage(state, stage).isEmpty()) continue
            out.add(stage to stageTable(state, stage))
        }
        return out
    }

    /** Which team takes each Super 8 place (A1 .. D2) once the groups are over. */
    private fun placeMap(state: TournamentState): Map<String, String> {
        val map = HashMap<String, String>()
        for ((letter, _) in GROUPS) {
            val top = stageTable(state, "Group $letter").take(2).map { it.team.id }
            val seed1 = idOf(SEEDS.getValue(letter + "1"))
            val seed2 = idOf(SEEDS.getValue(letter + "2"))
            val rest = top.toMutableList()
            var first: String? = if (seed1 in rest) seed1 else null
            first?.let { rest.remove(it) }
            var second: String? = if (seed2 in rest) seed2 else null
            second?.let { rest.remove(it) }
            if (first == null) {
                first = rest.firstOrNull()
                first?.let { rest.remove(it) }
            }
            if (second == null) second = rest.firstOrNull()
            map[letter + "1"] = first ?: continue
            map[letter + "2"] = second ?: continue
        }
        return map
    }

    /** Fills in the Super 8, the semi-finals and the Final as their teams become known. Safe after every match. */
    fun advance(state: TournamentState): TournamentState {
        var fx = state.fixtures
        fun complete(stage: String): Boolean {
            val l = fx.filter { it.stage == stage }
            return l.isNotEmpty() && l.all { it.result != null }
        }
        if (GROUPS.all { complete("Group ${it.first}") }) {
            val places = placeMap(state)
            fx = fx.map { f ->
                val t = f.id - SUPER_FIRST_ID
                if (f.homeId == null && t >= 0 && t < SUPER_TEMPLATE.size) {
                    f.copy(homeId = places[SUPER_TEMPLATE[t][0]], awayId = places[SUPER_TEMPLATE[t][1]])
                } else f
            }
        }
        val now = state.copy(fixtures = fx)
        if (complete(SUPER_1) && complete(SUPER_2)) {
            val r1 = stageTable(now, SUPER_1).map { it.team.id }
            val r2 = stageTable(now, SUPER_2).map { it.team.id }
            if (r1.size >= 2 && r2.size >= 2) {
                fx = fx.map { f ->
                    when {
                        f.id == SEMI_1_ID && f.homeId == null -> f.copy(homeId = r1[0], awayId = r2[1])
                        f.id == SEMI_2_ID && f.homeId == null -> f.copy(homeId = r2[0], awayId = r1[1])
                        else -> f
                    }
                }
            }
        }
        val semi1 = fx.firstOrNull { it.id == SEMI_1_ID }?.result?.winnerId
        val semi2 = fx.firstOrNull { it.id == SEMI_2_ID }?.result?.winnerId
        if (semi1 != null && semi2 != null) {
            fx = fx.map { f -> if (f.id == FINAL_ID && f.homeId == null) f.copy(homeId = semi1, awayId = semi2) else f }
        }
        return state.copy(fixtures = fx)
    }

    // ---- Data ---------------------------------------------------------------------------------

    private const val GROUP_FIXTURES = """
NED,PAK,ssc-ground
WI,SCO,eden-gardens
IND,USA,wankhede
AFG,NZ,chepauk
ENG,NEP,wankhede
SL,IRE,premadasa
SCO,ITA,eden-gardens
OMA,ZIM,ssc-ground
SA,CAN,narendra-modi-ahmedabad
NAM,NED,arun-jaitley
UAE,NZ,chepauk
PAK,USA,ssc-ground
SA,AFG,narendra-modi-ahmedabad
AUS,IRE,premadasa
WI,ENG,wankhede
SL,OMA,pallekele
NEP,ITA,wankhede
IND,NAM,arun-jaitley
ZIM,AUS,premadasa
CAN,UAE,arun-jaitley
USA,NED,chepauk
IRE,OMA,ssc-ground
SCO,ENG,eden-gardens
NZ,SA,narendra-modi-ahmedabad
NEP,WI,wankhede
USA,NAM,chepauk
IND,PAK,premadasa
UAE,AFG,arun-jaitley
ENG,ITA,eden-gardens
AUS,SL,pallekele
CAN,NZ,chepauk
IRE,ZIM,pallekele
SCO,NEP,wankhede
UAE,SA,arun-jaitley
PAK,NAM,ssc-ground
IND,NED,narendra-modi-ahmedabad
WI,ITA,eden-gardens
SL,ZIM,premadasa
AFG,CAN,chepauk
OMA,AUS,pallekele
"""

    /** Team id | name | short name | squad. "Name" copies a game player; "Name~ROLE~bat~bowl~S or P" is new (estimated). */
    private const val DEFS_TEXT = """
team_ind|India|IND|Arshdeep Singh;Abhishek Sharma;Washington Sundar;Sanju Samson;Axar Patel;Kuldeep Yadav;Shivam Dube;Varun Chakravarthy;Ishan Kishan;Hardik Pandya;Rinku Singh;Suryakumar Yadav;Tilak Varma;Mohammed Siraj;Jasprit Bumrah
team_nam|Namibia|NAM|Bernard Scholtz~BWL~25~74~S;Gerhard Erasmus~AR~74~58~S;JJ Smit~AR~70~68~P;Jan Nicol Loftie-Eaton~BAT~74~20~P;Malan Kruger~BAT~68~10~P;Dylan Leicher~BAT~64~20~P;Willem Myburgh~BAT~62~20~P;Jack Brassell~BWL~25~68~P;Louren Steenkamp~BAT~70~25~S;Zane Green~WK~70~10~P;Jan Frylinck~AR~76~72~P;Max Heingo~BWL~25~66~P;Jan Balt~BAT~62~30~S;Ruben Trumpelmann~BWL~30~78~P;Alexander Volschenk~AR~50~62~P
team_ned|Netherlands|NED|Kyle Klein;Max O'Dowd;Bas de Leede;Timm van der Gugten~BWL~20~72~P;Fred Klaassen;Zach Lion-Cachet~AR~60~58~S;Logan van Beek;Scott Edwards;Noah Croes;Paul van Meekeren;Colin Ackermann~AR~70~62~S;Roelof van der Merwe~BWL~40~70~S;Michael Levitt;Saqib Zulfiqar;Aryan Dutt
team_pak|Pakistan|PAK|Shadab Khan;Shaheen Afridi;Mohammad Nawaz;Khwaja Nafay~BAT~70~10~P;Fakhar Zaman;Abrar Ahmed;Faheem Ashraf;Sahibzada Farhan~BAT~80~10~P;Babar Azam;Saim Ayub;Salman Agha;Naseem Shah;Usman Tariq~BWL~15~84~S;Usman Khan;Salman Mirza~BWL~20~74~P
team_usa|United States|USA|Monank Patel;Harmeet Singh;Mohammad Mohsin~BWL~20~60~P;Saiteja Mukkamalla~BAT~66~10~P;Milind Kumar;Shadley van Schalkwyk;Saurabh Netravalkar;Ali Khan;Shubham Ranjane~AR~60~52~S;Sanjay Krishnamurthi~BAT~60~10~P;Shayan Jahangir;Shehan Jayasuriya~BAT~60~15~P;Nosthush Kenjige;Andries Gous;Ehsan Adil~BWL~20~64~P
team_aus|Australia|AUS|Matt Renshaw~BAT~72~15~P;Mitchell Marsh;Cooper Connolly~AR~68~52~S;Nathan Ellis;Xavier Bartlett;Marcus Stoinis;Glenn Maxwell;Cameron Green;Josh Inglis;Steve Smith;Matthew Kuhnemann~BWL~20~80~S;Travis Head;Ben Dwarshuis;Tim David;Adam Zampa;Sean Abbott
team_ire|Ireland|IRE|Lorcan Tucker;Tim Tector~BAT~66~25~S;Matthew Humphreys;Sam Topping~BAT~62~10~P;Harry Tector;Ross Adair;Mark Adair;Craig Young;George Dockrell;Barry McCarthy;Gareth Delany;Ben Calitz~BAT~64~30~S;Josh Little;Curtis Campher;Ben White
team_oman|Oman|OMA|Hammad Mirza~WK~66~10~P;Jay Odedra~BAT~62~25~S;Aamir Kaleem~AR~60~64~S;Nadeem Khan;Shakeel Ahmed;Jatinder Singh~BAT~72~25~S;Shah Faisal~BWL~30~66~P;Karan Sonavale~AR~58~55~S;Wasim Ali;Sufyan Mehmood~BWL~20~68~P;Ashish Odedara~AR~50~60~S;Shafiq Jan~BWL~25~68~P;Vinayak Shukla;Jiten Ramanandi~AR~62~50~P;Mohammad Nadeem~BWL~20~66~P
team_sl|Sri Lanka|SL|Dushmantha Chameera~BWL~16~78~P;Dasun Shanaka;Dunith Wellalage;Kusal Mendis;Pathum Nissanka;Kamindu Mendis;Dushan Hemantha~AR~58~66~S;Pramod Madushan;Kamil Mishara;Pavan Rathnayake~AR~62~58~S;Kusal Perera;Maheesh Theekshana;Charith Asalanka;Janith Liyanage;Dilshan Madushanka
team_zim|Zimbabwe|ZIM|Dion Myers;Wellington Masakadza;Tashinga Musekiwa;Sikandar Raza;Graeme Cremer~BWL~40~66~S;Tony Munyonga;Richard Ngarava;Blessing Muzarabani;Clive Madande;Tadiwanashe Marumani;Ryan Burl;Ben Curran~BAT~66~25~S;Tinotenda Maposa~BWL~20~66~P;Brad Evans~BWL~35~74~P;Brian Bennett
team_eng|England|ENG|Ben Duckett;Jofra Archer;Rehan Ahmed;Josh Tongue~BWL~25~80~P;Luke Wood;Sam Curran;Phil Salt;Jos Buttler;Jamie Overton~AR~56~76~P;Jacob Bethell;Liam Dawson~AR~50~76~S;Will Jacks;Harry Brook;Adil Rashid;Tom Banton~WK~78~10~P
team_ita|Italy|ITA|Grant Stewart~AR~55~62~P;Gian-Piero Meade~WK~66~10~P;Jaspreet Singh~BWL~20~62~P;Anthony Mosca~BAT~62~10~P;Syed Naqvi~BWL~25~60~P;Zain Ali~AR~55~50~P;JJ Smuts~AR~55~62~S;Marcus Campopiano~AR~55~55~S;Harry Manenti~AR~60~58~P;Ali Hasan~BWL~30~62~P;Ben Manenti~AR~62~56~S;Thomas Draca~AR~58~58~P;Justin Mosca~BAT~66~10~P;Wayne Madsen~BAT~72~35~S;Crishan Kalugamage~AR~52~60~S
team_nep|Nepal|NEP|Lokesh Bam~AR~55~50~P;Aasif Sheikh;Sompal Kami;Kushal Bhurtel;Gulshan Jha;Rohit Paudel;Sundeep Jora;Nandan Yadav~BWL~20~66~P;Aarif Sheikh;Sandeep Lamichhane;Lalit Rajbanshi;Karan KC;Sher Malla~AR~58~55~S;Dipendra Singh Airee;Basir Ahamad~BWL~25~62~S
team_sco|Scotland|SCO|Zainullah Ihsan~BWL~25~62~P;Bradley Currie;Oliver Davidson~AR~55~58~S;Matthew Cross;Chris Greaves;Brandon McMullen;Finlay McCreath~AR~55~58~P;Michael Leask;Tom Bruce~BAT~70~20~S;Richie Berrington;Michael Jones;Mark Watt;Brad Wheal;Jack Jarvis;George Munsey;Jasper Davidson~BWL~35~62~P
team_wi|West Indies|WI|Shimron Hetmyer;Shai Hope;Matthew Forde;Akeal Hosein;Roston Chase;Romario Shepherd;Johnson Charles;Jayden Seales;Quentin Sampson~BAT~55~20~P;Rovman Powell;Brandon King;Gudakesh Motie;Sherfane Rutherford;Shamar Joseph;Jason Holder
team_afg|Afghanistan|AFG|Fazalhaq Farooqi;Mohammad Nabi;Azmatullah Omarzai;Gulbadin Naib;Noor Ahmad;Ibrahim Zadran;Rashid Khan;Rahmanullah Gurbaz;Shahidullah Kamal;Sediqullah Atal;Mohammad Ishaq;Abdullah Ahmadzai~BWL~20~62~P;Zia-ur-Rehman;Mujeeb Ur Rahman;Darwish Rasooli;Ijaz Ahmad Ahmadzai~BWL~20~66~P;Fareed Ahmad;Allah Ghazanfar~BWL~18~68~S
team_can|Canada|CAN|Shreyas Movva~WK~70~10~P;Kaleem Sana~BWL~25~68~P;Ansh Patel~BWL~35~62~S;Ajayveer Hundal~BWL~30~62~P;Jaskaran Singh~AR~60~45~P;Harsh Thaker~AR~65~50~S;Dillon Heyliger~AR~62~62~P;Saad Bin Zafar~AR~62~62~S;Navneet Dhaliwal~BAT~70~20~P;Ravinderpal Singh~BAT~62~10~P;Shivam Sharma~AR~55~55~S;Kanwarpal Tathgur~WK~62~10~P;Nicholas Kirton~BAT~72~30~S;Dilpreet Bajwa~AR~66~60~S;Yuvraj Samra~BAT~66~15~P
team_nz|New Zealand|NZ|Rachin Ravindra;Kyle Jamieson;Finn Allen;Matt Henry;Glenn Phillips;Jacob Duffy;Tim Seifert~WK~82~10~P;Cole McConchie;James Neesham;Ish Sodhi;Lockie Ferguson;Mitchell Santner;Daryl Mitchell;Mark Chapman;Devon Conway;Ben Sears
team_sa|South Africa|RSA|Aiden Markram;David Miller;Quinton de Kock;Keshav Maharaj;Anrich Nortje;Lungi Ngidi;Kagiso Rabada;George Linde~AR~62~74~S;Tristan Stubbs;Corbin Bosch;Ryan Rickelton;Dewald Brevis;Jason Smith~AR~62~56~P;Marco Jansen;Kwena Maphaka
team_uae|United Arab Emirates|UAE|Aryansh Sharma~WK~64~10~P;Muhammad Jawadullah;Alishan Sharafu;Muhammad Waseem;Simranjeet Singh;Harshit Kaushik~BWL~20~60~P;Dhruv Parashar~AR~55~62~P;Mayank Kumar~BAT~62~20~P;Sohaib Khan~BAT~60~10~P;Syed Haider~WK~62~10~P;Muhammad Rohid~BAT~62~10~P;Muhammad Arfan~BWL~20~66~P;Junaid Siddique;Haider Ali~BAT~66~10~P;Muhammad Farooq~BWL~25~62~S
"""
}

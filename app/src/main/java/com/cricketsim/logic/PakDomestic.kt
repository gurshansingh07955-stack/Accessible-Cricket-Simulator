package com.cricketsim.logic

/**
 * Pakistan domestic cricket 2026-27: the Quaid-e-Azam Trophy Gold (the top eight regional teams) and Silver (the other twelve), both first-class.
 * Team ids are "pk_xx". See PAKISTAN_DOMESTIC.md. Part two (the National T20 Cup, whose fixtures are not published yet) is not included.
 *
 * Fixtures: the real published lists (PCB, 2 November 2026), in the real order and with the real grounds. The 69th Quaid-e-Azam Trophy began
 * on 3 November; the game starts both divisions from the beginning and simulates every result.
 *
 *  - Gold: eight teams, each plays the other seven once (28 matches), then a five-day Final between the top two (15 to 19 December; the
 *    ground is not announced: the game uses the National Cricket Ground, Islamabad).
 *  - Silver: twelve teams in two groups of six (each plays the other five, 30 matches), then a Final between the two group winners (3 to
 *    7 December; ground not announced: Multan Cricket Stadium). The Silver winner is promoted to Gold in 2027-28 (not built).
 *
 * Points: the PCB's exact scheme could not be confirmed, so this is an ASSUMPTION: 16 for a win, 8 for a tie, 5 for a draw, plus batting
 * bonus (1 point at 200 runs, then 1 for each 50 more, up to 5) and bowling bonus (1 point for 3 wickets, 2 for 6, 3 for 9) from each side's first
 * innings. A drawn Final goes to the side that led on first innings.
 *
 * Squads: the PCB rosters of 2 November 2026 (the Gold squads include the reserves who are national-duty stars). A player already in the
 * game keeps his ratings; the others get ESTIMATED ratings; most have an unknown role (guessed).
 */
object PakDomestic {
    const val GOLD_ID = "qeag_2026_27"
    const val GOLD_NAME = "Quaid-e-Azam Trophy Gold 2026-27"
    const val SILVER_ID = "qeas_2026_27"
    const val SILVER_NAME = "Quaid-e-Azam Trophy Silver 2026-27"

    private const val FINAL = "Final"
    private const val GROUP_A = "Silver Group A"
    private const val GROUP_B = "Silver Group B"
    private const val GOLD_FINAL_GROUND = "national-cricket-ground-isb"
    private const val SILVER_FINAL_GROUND = "multan-cricket-stadium"

    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN

    fun isGold(id: String?): Boolean = id == GOLD_ID
    fun isSilver(id: String?): Boolean = id == SILVER_ID
    fun isPak(id: String?): Boolean = id == GOLD_ID || id == SILVER_ID

    /** Four days in the league, five in the Final. */
    fun daysFor(stage: String): Int = if (stage == FINAL) 5 else 4

    fun introOf(id: String?): String =
        if (id == GOLD_ID) {
            "Sixteen points for a win, plus batting and bowling bonus points from the first innings (the PCB's exact scheme is assumed). " +
                "Each of the eight sides plays the other seven once; the top two play a five-day Final."
        } else {
            "Twelve teams in two groups of six, each side playing the other five. The two group winners play the Final, and the winner is promoted to the Gold division next season."
        }

    // ---------------------------------------------------------------- teams and grounds

    private class Entry(val name: String, val flags: String, val role: Char, val bowl: String)

    private class TeamDef(val id: String, val name: String, val shortName: String, val home: String, val tier: Int, val squad: List<Entry>)

    private fun parseEntry(text: String): Entry {
        val p = text.split("~")
        return Entry(p[0].trim(), p.getOrElse(1) { "" }, p.getOrNull(2)?.firstOrNull() ?: '-', p.getOrNull(4) ?: "")
    }

    private val DEFS: List<TeamDef> by lazy {
        (GOLD_TEXT + "\n" + SILVER_TEXT).lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val p = line.split("|")
            TeamDef("pk_" + p[0], p[1], p[2], p[3], p[4].toInt(), p[5].split(";").map { parseEntry(it) })
        }
    }

    private val GOLD_CODES = listOf("fsd", "fata", "isb", "kbl", "lwh", "mul", "pbl", "sbl")

    fun isTeam(teamId: String): Boolean = DEFS.any { it.id == teamId }

    fun shortNameOrNull(teamId: String): String? = DEFS.find { it.id == teamId }?.shortName

    fun homeGround(teamId: String): String = DEFS.find { it.id == teamId }?.home ?: "gaddafi"

    private val GROUND = mapOf(
        "nc" to "national-cricket-ground-isb", "dm" to "diamond-cricket-ground", "ab" to "abbottabad-stadium", "sa" to "shoaib-akhtar-stadium",
        "sb" to "sbp-sports-complex", "ub" to "ubl-sports-complex", "kc" to "kcca-stadium",
        "mu" to "multan-cricket-stadium", "ry" to "rahim-yar-khan-sports-complex", "dr" to "dring-stadium"
    )

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

    /** The grounds the game did not have already (estimated ratings). Multan, Rawalpindi, Faisalabad, Gaddafi and the National Stadium were in StadiumData. */
    val stadiums: List<Stadium> by lazy {
        VENUE_TEXT.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val p = line.split("|")
            val pitch = pitchOf(p[3][0])
            Stadium(
                id = p[0], name = p[1], city = p[2], country = "Pakistan", homeTeamIds = emptyList(),
                pitchType = pitch, pitchDescription = pitchText(pitch), boundarySize = boundaryOf(p[4][0]),
                altitudeM = p[5].toInt(), dewFactor = 0.2, avgT20FirstInningsScore = 165,
                climate = StadiumClimate(0.1, 40, 22, 14, "Dry, mild winter conditions.")
            )
        }
    }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    fun stadiumsFor(id: String): List<Stadium> =
        (buildSchedule(id).map { it.stadiumId } + (if (id == GOLD_ID) GOLD_FINAL_GROUND else SILVER_FINAL_GROUND))
            .distinct().mapNotNull { CplData.stadium(it) }

    // ---------------------------------------------------------------- players

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' }

    private fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)

    private fun jit(name: String, salt: Int, range: Int): Int {
        var h = salt * 7919
        for (c in name) h = h * 31 + c.code
        return (h and 0x7fffffff) % (2 * range + 1) - range
    }

    private val FIRST_NAME_FORMS = listOf(listOf("muhammad", "mohammad", "mohd", "mohamad"), listOf("shaheen shah", "shaheen"), listOf("ul-haq", "ulhaq"))

    /** The player the game already has under this name or a spelling variant (Muhammad/Mohammad ...). */
    private fun lookup(known: Map<String, Player>, name: String): Player? {
        known[norm(name)]?.let { return it }
        val parts = name.split(" ")
        if (parts.size >= 2) {
            val first = norm(parts[0])
            val rest = parts.drop(1).joinToString("") { norm(it) }
            for (forms in FIRST_NAME_FORMS) {
                if (first in forms) {
                    for (f in forms) known[f + rest]?.let { return it }
                }
            }
        }
        return null
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
            val pick = (0 until n).firstOrNull { roles[it] == BAT } ?: 0
            roles[pick] = WK
        }
        var need = 5 - roles.count { it == BWL || it == AR }
        var k = n - 1
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

    /** The 20 regional teams (8 Gold, 12 Silver). A player already in the game is copied with his ratings; the rest get estimates. */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val known = HashMap<String, Player>()
        fun addAll(players: List<Player>) {
            for (p in players) known.putIfAbsent(norm(p.name), p)
        }
        addAll(CricketData.getAllTeams().flatMap { it.players })
        addAll(CplData.buildTeams().first.flatMap { it.players })
        addAll(AbuDhabiT10.buildTeams().first.flatMap { it.players })
        addAll(InternationalLeagueT20.buildTeams().first.flatMap { it.players })
        addAll(LankaPremierLeague.buildTeams().first.flatMap { it.players })
        addAll(BigBashLeague.buildTeams().first.flatMap { it.players })
        addAll(MajorLeagueCricket.buildTeams().first.flatMap { it.players })
        addAll(Sa20.buildTeams().first.flatMap { it.players })
        addAll(IplData.buildPool().map { it.player })
        val tags = LinkedHashMap<String, SquadTag>()
        val teams = DEFS.map { def ->
            val roles = assignRoles(def.squad)
            val tierDelta = when (def.tier) { 5 -> 7; 4 -> 3; 3 -> 0; 2 -> -4; else -> -9 }
            val base = 56 + tierDelta
            val players = def.squad.mapIndexed { index, e ->
                val id = "${def.id}_${index + 1}"
                val found = lookup(known, e.name)
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

    // ---------------------------------------------------------------- fixtures

    private fun rows(text: String): List<List<String>> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { it.split("|") }

    /** The published league matches in the published order. The Final is added by [advance]. */
    fun buildSchedule(id: String): List<TournamentFixture> =
        if (id == GOLD_ID) {
            rows(GOLD_FIX_TEXT).mapIndexed { i, p ->
                TournamentFixture(i, i + 1, CplData.LEAGUE, "pk_" + p[0], "pk_" + p[1], GROUND.getValue(p[2]))
            }
        } else {
            rows(SILVER_FIX_TEXT).mapIndexed { i, p ->
                TournamentFixture(i, i + 1, if (p[0] == "A") GROUP_A else GROUP_B, "pk_" + p[1], "pk_" + p[2], GROUND.getValue(p[3]))
            }
        }

    /** Only the teams that play in this tournament: the eight Gold teams or the twelve Silver teams. */
    fun teamsFor(id: String, built: Pair<List<Team>, Map<String, SquadTag>>): Pair<List<Team>, Map<String, SquadTag>> {
        val gold = built.first.filter { t -> GOLD_CODES.contains(t.id.removePrefix("pk_")) }
        val chosen = if (id == GOLD_ID) gold else built.first.filter { t -> !GOLD_CODES.contains(t.id.removePrefix("pk_")) }
        val ids = chosen.map { it.id }.toSet()
        return Pair(chosen, built.second.filterKeys { key -> ids.any { key.startsWith(it + "_") } })
    }

    fun newTournament(id: String, userTeamId: String, built: Pair<List<Team>, Map<String, SquadTag>>): TournamentState {
        val own = teamsFor(id, built)
        return TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = id,
            name = if (id == GOLD_ID) GOLD_NAME else SILVER_NAME,
            userTeamId = userTeamId,
            teams = own.first,
            tags = own.second,
            fixtures = buildSchedule(id),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )
    }

    // ---------------------------------------------------------------- points and tables

    private fun battingBonus(runs: Int): Int = if (runs < 200) 0 else minOf(5, 1 + (runs - 200) / 50)

    private fun bowlingBonus(wickets: Int): Int = minOf(3, wickets / 3)

    /** The assumed scheme: 16 for a win, 8 for a tie, 5 for a draw, plus the first-innings batting and bowling bonus points. */
    fun points(s: MatchState, teamId: String): Int {
        val base = when (FirstClass.kind(s)) {
            "WIN" -> if (s.winnerId == teamId) 16 else 0
            "TIE" -> 8
            else -> 5
        }
        val list = s.test?.completed ?: return base
        val mine = list.firstOrNull { it.data.battingTeamId == teamId }
        val theirs = list.firstOrNull { it.data.battingTeamId != teamId }
        return base + (if (mine != null) battingBonus(mine.score.runs) else 0) + (if (theirs != null) bowlingBonus(theirs.score.wickets) else 0)
    }

    /** One group's (or the Gold league's) table: points, then wins, then runs per wicket scored over conceded. */
    fun table(state: TournamentState, stage: String): List<FcRow> {
        val ids = state.fixtures.filter { it.stage == stage }.flatMap { listOfNotNull(it.homeId, it.awayId) }.toSet()
        val rows = state.teams.filter { it.id in ids }.map { team ->
            var played = 0
            var won = 0
            var drawn = 0
            var lost = 0
            var tied = 0
            var pts = 0
            var runsFor = 0
            var wicketsLost = 0
            var runsAgainst = 0
            var wicketsTaken = 0
            for (f in state.fixtures) {
                if (f.stage != stage) continue
                val r = f.result ?: continue
                if (f.homeId != team.id && f.awayId != team.id) continue
                played++
                pts += if (f.homeId == team.id) r.homePoints else r.awayPoints
                when (r.fcKind) {
                    "WIN" -> if (r.winnerId == team.id) won++ else lost++
                    "TIE" -> tied++
                    else -> drawn++
                }
                for (i in r.fcInnings) {
                    if (i.teamId == team.id) {
                        runsFor += i.runs
                        wicketsLost += i.wickets
                    } else {
                        runsAgainst += i.runs
                        wicketsTaken += i.wickets
                    }
                }
            }
            val scored = runsFor.toDouble() / maxOf(1, wicketsLost)
            val conceded = runsAgainst.toDouble() / maxOf(1, wicketsTaken)
            val quotient = if (conceded <= 0.0) (if (scored > 0.0) 99.0 else 0.0) else scored / conceded
            FcRow(team, played, won, drawn, lost, tied, pts, quotient)
        }
        return rows.sortedWith(compareByDescending<FcRow> { it.points }.thenByDescending { it.won }.thenByDescending { it.quotient })
    }

    /** The tables to show: the Gold league, or the two Silver groups. */
    fun tables(state: TournamentState): List<Pair<String, List<FcRow>>> =
        if (state.tournamentId == GOLD_ID) listOf("Gold table" to table(state, CplData.LEAGUE))
        else listOf(GROUP_A, GROUP_B).map { it to table(state, it) }

    private fun complete(fx: List<TournamentFixture>, stage: String): Boolean {
        val l = fx.filter { it.stage == stage }
        return l.isNotEmpty() && l.all { it.result != null }
    }

    /** Adds the Final: the top two of Gold, or the winners of the two Silver groups. Safe after every match. */
    fun advance(state: TournamentState): TournamentState {
        val fx = state.fixtures
        if (fx.any { it.stage == FINAL }) return state
        if (state.tournamentId == GOLD_ID) {
            if (!complete(fx, CplData.LEAGUE)) return state
            val top = table(state, CplData.LEAGUE).map { it.team.id }.take(2)
            if (top.size < 2) return state
            return state.copy(fixtures = fx + TournamentFixture(fx.size, fx.size + 1, FINAL, top[0], top[1], GOLD_FINAL_GROUND))
        }
        if (!complete(fx, GROUP_A) || !complete(fx, GROUP_B)) return state
        val a = table(state, GROUP_A).firstOrNull()?.team?.id
        val b = table(state, GROUP_B).firstOrNull()?.team?.id
        if (a == null || b == null) return state
        return state.copy(fixtures = fx + TournamentFixture(fx.size, fx.size + 1, FINAL, a, b, SILVER_FINAL_GROUND))
    }

    // ---------------------------------------------------------------- the data

    // code | name | short | home ground | strength tier (1-5) | squad: name ~ flags ~ role (B batter, A all-rounder, W keeper, L bowler, - unknown) ~ - ~ bowling style
    private const val GOLD_TEXT = """
fsd|Faisalabad|FSD|iqbal-stadium|3|Muhammad Irfan Khan~~-~~;Abdul Samad~~-~~;Ahmed Safi Abdullah~~-~~;Ali Asfand~~-~~;Ali Shan~wk~W~~;Asad Raza~~-~~;Asim Ali Nasir~wk~W~~;Atiq-ur-Rehman~~-~~;Faheem Ashraf~~A~~RM;Faham-ul-Haq~~-~~;Khurram Shahzad~~L~~RM;Malik Abdul Rafay~~-~~;Muhammad Awais Zafar~~-~~;Sameer Saqib~~-~~;Shehzad Gul~~-~~;Daniyal Ali Khan~~-~~;Mudasir Zunair~~-~~
fata|FATA|FAT|rawalpindi|4|Shaheen Shah Afridi~~L~~RM;Muhammad Farooq~~-~~;Salman Khan Jnr~~-~~;Kashif Noor~~-~~;Muhammad Sarwar~~-~~;Muhammad Wasim Khan~~-~~;Khushdil Shah~~A~~SLA;Shahid Aziz~~-~~;Rehan Afridi~wk~W~~;Afaq Afridi~~-~~;Sameen Gul~~L~~RM;Sirajuddin~~-~~;Hayatullah~~-~~;Fawad Arshad~~-~~;Muhammad Usman Khan~~-~~;Adnan Khan~~-~~;Akif Javed~~L~~RM
isb|Islamabad|ISB|rawalpindi|3|Shamyl Hussain~~B~~;Raja Hamza Waheed~~-~~;Ali Imran~~-~~;Azim Bari~~-~~;Sarmad Bhatti~~-~~;Muhammad Hammad Khan~~-~~;Hasan Nawaz~~B~~;Azan Tariq~~-~~;Rohail Nazir~wk~W~~;Musa Khan~~-~~;Muhammad Waeem Jnr~~-~~;Sajid Raza~~-~~;Mohammad Qaiser~~-~~;Asfand Yar~~-~~;Umer Hameed~~-~~
kbl|Karachi Blues|KBL|national-stadium-karachi|5|Saud Shakeel~~B~~;Saim Ayub~~A~~SLA;Omair Bin Yousuf~~A~~RM;Abdul Basit~~-~~;Umer Farooq~~-~~;Danish Aziz~~-~~;Saifullah Bangash~~-~~;Rameez Aziz~~-~~;Usman Khan~wk~W~~;Haroon Arshad~~-~~;Afnan Khan~~-~~;Muhammad Ghazi Ghori~wk~W~~;Muhammad Asghar~~-~~;Muhammad Umar~~-~~;Muhammad Hamza Sohail~~-~~;Muhammad Raza Chandio~~-~~;Asad Umar~~-~~;Shan Masood~~B~~;Abrar Ahmed~~L~~LB;Mir Hamza~~L~~RM
lwh|Lahore Whites|LWH|gaddafi|4|Aamir Jamal~~A~~RM;Babar Azam~~B~~;Ali Zaryab~~-~~;Mohib Haider~~-~~;Muhammad Faiq~~-~~;Muhammad Imran~~-~~;Tayyab Tahir~~B~~;Muhammad Mohsin~~-~~;Muhammad Akhlaq~~-~~;Zain Bin Farooq~wk~W~~;Noman Ali~~L~~SLA;Muhammad Kamran Afzal~~-~~;Muhammad Salman Mirza~~-~~;Zulqarnain~~-~~;Hunain Shah~~-~~;Usman Sohail~~-~~
mul|Multan|MUL|multan-cricket-stadium|4|Imam-ul-Haq~~B~~;Uzair Mumtaz~~-~~;Yousuf Babar~~-~~;Moheer Saeed~~-~~;Sharoon Siraj~~-~~;Imran Rafiq~~-~~;Waseem Akram Jr~~L~~RM;Mohammad Ismail~~L~~RM;Muhammad Shan~~-~~;Aamer Yamin~~A~~RM;Hasan Hafeez~~-~~;Faisal Akram~~L~~SLC;Bilal Khan~~-~~;Waqar Ahmed~wk~W~~
pbl|Peshawar Blues|PBL|national-cricket-ground-isb|4|Muhammad Rizwan~wk~W~~;Sahibzada Farhan~~B~~;Maaz Sadaqat~~-~~;Muhammad Haris~wk~W~~;Israrullah~~-~~;Iftikhar Ahmed~~A~~OB;Najab Khan~~-~~;Nabi Gul~~-~~;Muhammad Haleem~~-~~;Sajid Khan~~L~~OB;Muhammad Irfan~~-~~;Muhammad Abbas Afridi~~L~~RM;Muhammad Amir Khan~~L~~RM;Niaz Khan~~-~~;Muhammad Adnan~~-~~;Sahibzada Abdullah~~-~~;Najeebullah~~-~~
sbl|Sialkot Blues|SBL|national-cricket-ground-isb|4|Abdullah Shafique~~B~~;Muhammad Tayyab Arif~~-~~;Hamza Sheraz~~-~~;Muhammad Huraira~~B~~;Ahmed Hassan~~-~~;Mohsin Riaz~~-~~;Muhammad Abdul Rehman~~-~~;Hamza Nazar~~-~~;Muhammad Hamza~~-~~;Muhammad Ali~~-~~;Hasan Ali~~L~~RM;Ammad Butt~~-~~;Athar Mehmood~~-~~;Shahzaib Bhatti~~-~~;Tayyab Ali~~-~~;Afzaal Manzoor~wk~W~~
"""

    private const val SILVER_TEXT = """
abb|Abbottabad|ABB|abbottabad-stadium|2|Khalid Usman~~-~~;Shahzaib Khan~~-~~;Kamran Ghulam~~B~~;Fakhar Zaman~~B~~;Zain Ishaq~~-~~;Ahmed Khan~~-~~;Mohsin Khan~~-~~;Muhammad Riazullah~~-~~;Umair Badshah~~-~~;Muhammad Shayan~~-~~;Arshad Iqbal~~-~~;Razaullah~~-~~;Israr Hussain~~-~~;Aqib Khan~~-~~;Junaid Ahmed~~-~~;Khayyam Khan~~-~~;Mehroz Rasheed~~-~~;Aftab Khan~~-~~;Muhammad Arif~~-~~;Khan Zaib~~-~~
ajk|Azad Jammu and Kashmir|AJK|rawalpindi|2|Hasan Raza~~-~~;Husnain Nadeem~~-~~;Hamad Islam~~-~~;Hassan Ali~~-~~;Umer Hayat~~-~~;Muhammad Ammar~~-~~;Saad Mumtaz~~-~~;Naveed Malik~~-~~;Awais Akram~wk~W~~;Aqib Liaqat~~-~~;Sufyan Moqim~~-~~;Zaman Khan~~-~~;Muhammad Faizan Saleem~~-~~;Muhammad Jawad~~-~~;Adil Azad~~-~~;Usman Maroof~~-~~;Aman Arshad~~-~~;Usman Yousuf~~-~~;Raja Farhan~wk~W~~;Shadab Majeed~~-~~
bah|Bahawalpur|BAH|dring-stadium|2|Muhammad Imran Randhawa~~-~~;Mohammad Ammar~~-~~;Muhammad Aon Shahzad~~-~~;Ali Hamza Wasim~~-~~;Muhammad Sudais~~-~~;Mohammad Alamgir~~-~~;Mohammad Shahiryar~~-~~;Ali Afzal~~-~~;Muhammad Umair~~-~~;Muhammad Akram~wk~W~~;Muhammad Junaid~~-~~;Muhammad Khaqan Bashir~~-~~;Muhammad Azab~~-~~;Muhammad Basit~~-~~;Shoaib Manzoor~~-~~;Ali Shabbir~~-~~;Rana Muhammad Burhan Basharat~~-~~;Mohammad Faizan Zafar~~-~~;Muhammad Huzaifa~~-~~;Ibrar Amin~~-~~;Amjad Ali~~-~~;Hasnain Majid~wk~W~~;Muhammad Nabeel Arshad~wk~W~~
dmj|Dera Murad Jamali|DMJ|rahim-yar-khan-sports-complex|1|Fahad Hussain~~-~~;Nasir Khan~~-~~;Aqib Junaid~~-~~;Muhammad Uzair~~-~~;Ubaid Ullah~~-~~;Taimur Ali~~-~~;Dawood Khan~~-~~;Muhammad Deen~~-~~;Muhammad Shahid~~-~~;Shahbaz Ali~wk~W~~;Abu Huraira~~-~~;Saleem Mal~~-~~;Farhan Ullah~~-~~;Abu Bakar~~-~~;Allah Bakhsh~~-~~;Shahid Khan~~-~~;Sajjad Ahmed~~-~~;Atal Khan~~-~~;Mir Hazar~wk~W~~
hyd|Hyderabad|HYD|niaz-stadium-hyderabad-pak|2|Saad Khan~~-~~;Zain-ul-Abdin~~-~~;Muhammad Saddam~~-~~;Haris Khan~~-~~;Ubaidullah Rahupoto~~-~~;Muhammad Suleman~~-~~;Daniyal Hussain Rajput~~-~~;Rizwan Mehmood~~-~~;Nabeel Ahmed~wk~W~~;Jawad Ali~~-~~;Asad Malik~~-~~;Mohammad Hasnain~~-~~;Mustafa Nasir~~-~~;Ayaz Khan~~-~~;Muhammad Ahsan~~-~~;Muhammad Sikandar~~-~~;Akbar Khan~wk~W~~;Kashif Bhatti~~-~~;Muhammad Waleed~~-~~
kwh|Karachi Whites|KWH|kcca-stadium|2|Jahanzaib Sultan~~-~~;Muhammad Taha~~-~~;Aibaad Khan~~-~~;Muhammad Usman Rahim~~-~~;Khawaja Muhammad Nafay~~-~~;Huzaifa Ahsan~~-~~;Muhammad Taha Mehmood~~-~~;Kashif Ali~~-~~;Ghulam Ashraf~wk~W~~;Saifullah~~-~~;Rehman Ghani~~-~~;Maaz Khurram Amin~~-~~;Fahad Amin~~-~~;Abdul Rauf~~-~~;Asadullah Hamza~~-~~;Imran Shah~~-~~;Muhammad Ali Butt~~-~~;Sabeh Siddiqui~~-~~;Fahad Ansari~wk~W~~;Syed Shah Raza Naqvi~~-~~;Najeeb-ur-Rehman~~-~~;Arif Yaqoob~~-~~;Muhammad Makki~~-~~;Muhammad Azaan~~-~~;Asad Akhtar~~-~~
lbl|Lahore Blues|LBL|gaddafi|2|Imran Butt~~-~~;Umer Siddiq~~-~~;Muhammad Saleem~~-~~;Hashim Ibrahim~~-~~;Qasim Akram~~-~~;Ali Razzaq~~-~~;Ali Hassan Baloch~~-~~;Obaid Shahid~~-~~;Khawaja Muhammad Abdullah~wk~W~~;Sajjad Khan~~-~~;Usman Qadir~~-~~;Muhammad Salman~~-~~;Muhammad Zahid~~-~~;Ahmed Bashir~~-~~;Nisar Ahmed~~-~~;Samama Riaz~~-~~;Muhammad Huzaifa Shujaat~~-~~;Mohsin Irshad~~-~~;Sagar Ali~wk~W~~
lar|Larkana|LAR|sheikhupura-stadium|2|Zahid Mehmood~~-~~;Malhar Rasool~~-~~;Muhammad Ibrahim~~-~~;Muhammad Aqeel Danish~~-~~;Sabit Ali~~-~~;Ghulam Raza~~-~~;Ishfaque Ahmed~~-~~;Najaf Ali~~-~~;Faraz Aziz~~-~~;Asif Ali Jnr~~-~~;Shahnawaz Dahani~~-~~;Mushtaq Ahmed~~-~~;Saqlain Nawaz~~-~~;Muhammad Mustafa~~-~~;Mohsin Raza~wk~W~~;Shahzaib Aziz~~-~~;Feroz Ali~~-~~;Muhammad Umer~~-~~;Umer Khalid~~-~~;Muhammad Saboor~~-~~
pwh|Peshawar Whites|PWH|national-cricket-ground-isb|2|Waqar Ahmed~~-~~;Muhammad Mohsin Khan~~-~~;Muhammad Amir Barki~~-~~;Abubakar Khan~~-~~;Abbas Ali~~-~~;Adil Amin~~-~~;Sajjad~~-~~;Adil Khan~~-~~;Haris Khan~~-~~;Mujtaba Khan~~-~~;Abuzar Tariq~~-~~;Muhammad Ilyas~~-~~;Muhammad Imran Jnr~~-~~;Muhammad Abuzar~~-~~;Muhammad Shoaib Afridi~~-~~;Afqar Durrani~~-~~;Salman Khan~~-~~;Sarmad Khattak~~-~~;Azam Khan~~-~~;Ahmad Hussain~~-~~;Usman Tariq~~-~~;Ihsanullah~~L~~RM
que|Quetta|QUE|national-stadium-karachi|2|Abdul Wahid Bangalzai~~-~~;Bismillah Khan~wk~W~~;Salahuddin~~-~~;Shahzad~~-~~;Hazrat Wali~~-~~;Nadeem Ahmed~~-~~;Abdul Ghafar~~-~~;Muhammad Umar~~-~~;Haseebullah~wk~W~~;Syed Zain Ullah~~-~~;Muhammad Ibrahim Snr~~-~~;Najeebullah Achakzai~~-~~;Khalil Ahmed~~-~~;Muhammad Ghous~~-~~;Naqeebullah~~-~~;Ghulam Nabi~~-~~;Zulfiqar Ali~~-~~;Eshan Ullah~~-~~;Bilal Ahmed~~-~~;Abdul Hameed~~-~~
rwp|Rawalpindi|RWP|rawalpindi|2|Mohammad Nawaz~~A~~SLA;Yasir Khan~~-~~;Abdul Faseeh~~-~~;Affan Ishaq~~-~~;Nasir Nawaz~~-~~;Mehran Mumtaz~~-~~;Haider Ali~~-~~;Mubasir Khan~~L~~OB;Aqib Shah~~-~~;Shehbaz Tabraiz Kyani~~-~~;Kashif Ali~~-~~;Muhammad Nabeel~~-~~;Muhammad Faizan Ghani~~-~~;Badruddin~~-~~;Taimur Khan~wk~W~~;Abdul Qadir~wk~W~~;Jahandad Khan~~L~~SLA;Shadab Khan~~A~~LB;Saad Masood~~-~~;Raza-ul-Mustafa~wk~W~~
swh|Sialkot Whites|SWH|national-cricket-ground-isb|2|Muhammad Husnain Khan~~-~~;Khawaja Arham~~-~~;Aashir Mehmood~~-~~;Mirza Tahir Baig~~-~~;Muhammad Tayyab~~-~~;Qasim Sajjad~~-~~;Muhammad Hammad Sarwar~~-~~;Rizwan Ahmed~~-~~;Mubashir Aslam~~-~~;Abdullah Nawaz~~-~~;Salman Aslam~~-~~;Abdul Rehman Saad~~-~~;Sharjeel Ahmed~~-~~;Inzamam-ul-Haq~~-~~;Raza Shahzaib~wk~W~~;Musa Azeem~~-~~;Saleem~~-~~;Ameer Hassan~~-~~;Syed Hussain Sajjad Bukhari~~-~~;Muhammad Taha Tariq~wk~W~~
"""

    // id | name | city | pitch (B batting, G green, D dusty, O bowling) | boundary (S, M, L) | altitude in metres
    private const val VENUE_TEXT = """
national-cricket-ground-isb|National Cricket Ground|Islamabad|B|M|540
diamond-cricket-ground|Diamond Cricket Ground|Islamabad|B|S|540
abbottabad-stadium|Abbottabad Cricket Stadium|Abbottabad|G|S|1260
shoaib-akhtar-stadium|Shoaib Akhtar Stadium|Rawalpindi|B|M|510
sbp-sports-complex|SBP Sports Complex|Karachi|B|M|10
ubl-sports-complex|UBL Sports Complex|Karachi|B|M|10
kcca-stadium|KCCA Stadium|Karachi|B|M|10
rahim-yar-khan-sports-complex|Sports Complex Rahim Yar Khan|Rahim Yar Khan|D|M|80
dring-stadium|Dring Stadium|Bahawalpur|D|M|115
"""

    // home team | visiting team | ground, in the published order
    private const val GOLD_FIX_TEXT = """
kbl|sbl|nc
fsd|pbl|ab
lwh|fata|dm
isb|mul|sa
kbl|mul|dm
sbl|fsd|sa
isb|lwh|ab
fata|pbl|nc
isb|kbl|sa
sbl|fata|ab
mul|fsd|nc
lwh|pbl|dm
fsd|kbl|ab
pbl|sbl|sa
mul|lwh|nc
fata|isb|dm
lwh|kbl|ab
sbl|isb|nc
fata|fsd|sa
mul|pbl|dm
kbl|pbl|nc
lwh|sbl|sa
fsd|isb|dm
mul|fata|ab
fata|kbl|dm
sbl|mul|sa
fsd|lwh|nc
pbl|isb|ab
"""

    // group (A or B) | home team | visiting team | ground, in the published order
    private const val SILVER_FIX_TEXT = """
A|lbl|rwp|sb
A|lar|que|ub
A|bah|pwh|kc
B|kwh|ajk|mu
B|hyd|dmj|ry
B|abb|swh|dr
A|bah|que|sb
A|lbl|pwh|ub
A|lar|rwp|kc
B|abb|dmj|mu
B|kwh|swh|ry
B|hyd|ajk|dr
A|lar|bah|sb
A|rwp|pwh|ub
A|lbl|que|kc
B|hyd|abb|mu
B|ajk|swh|ry
B|kwh|dmj|dr
A|pwh|lar|sb
A|lbl|bah|ub
A|rwp|que|kc
B|swh|hyd|mu
B|kwh|abb|ry
B|ajk|dmj|dr
A|lbl|lar|sb
A|rwp|bah|ub
A|pwh|que|kc
B|kwh|hyd|mu
B|ajk|abb|ry
B|swh|dmj|dr
"""
}

package com.cricketsim.logic

/**
 * Bangladesh domestic cricket 2026-27, part one: the National Cricket League (first-class), the eight divisions. Team ids are "bd_xx".
 * See BANGLADESH_DOMESTIC.md. The one-day and T20 competitions are not included (see the document).
 *
 * Fixtures: the real published list (BCB, 25 September 2026): seven rounds, 28 matches, each division plays the other seven once, in the
 * published order, at the published grounds (the grounds are shared out among the divisions, so the "home" side is just the first named).
 * The league began on 3 October and has run to round 5 in real life; the game starts from the beginning and simulates every result.
 * There is no final: the top of the table are the champions.
 *
 * Points: the BCB's scheme could not be confirmed, so this is an ASSUMPTION: 12 for a win, 6 for a tie, 4 for a draw, plus batting bonus
 * (1 point each at 200, 250, 300 and 350 runs) and bowling bonus (1 point each for 3, 5, 7 and 9 wickets) from each side's first innings.
 *
 * Squads: the BCB's final 15-man squads of 25 September 2026 (the national players are not in them). A player already in the game keeps
 * his ratings; the others get ESTIMATED ratings; most have an unknown role (guessed).
 */
object BdDomestic {
    const val NCL_ID = "ncl_2026_27"
    const val NCL_NAME = "National Cricket League 2026-27"
    const val DAYS = 4

    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN

    fun isNcl(id: String?): Boolean = id == NCL_ID

    fun introOf(): String =
        "Twelve points for a win, six for a tie, four for a draw, plus batting and bowling bonus points from the first innings (the BCB's exact scheme is assumed). " +
            "Each of the eight divisions plays the other seven once, in seven rounds. There is no final: the top of the table are the champions."

    // ---------------------------------------------------------------- teams and grounds

    private class Entry(val name: String, val flags: String, val role: Char, val bowl: String)

    private class TeamDef(val id: String, val name: String, val shortName: String, val home: String, val tier: Int, val squad: List<Entry>)

    private fun parseEntry(text: String): Entry {
        val p = text.split("~")
        return Entry(p[0].trim(), p.getOrElse(1) { "" }, p.getOrNull(2)?.firstOrNull() ?: '-', p.getOrNull(4) ?: "")
    }

    private val DEFS: List<TeamDef> by lazy {
        TEAM_TEXT.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val p = line.split("|")
            TeamDef("bd_" + p[0], p[1], p[2], p[3], p[4].toInt(), p[5].split(";").map { parseEntry(it) })
        }
    }

    fun isTeam(teamId: String): Boolean = DEFS.any { it.id == teamId }

    fun shortNameOrNull(teamId: String): String? = DEFS.find { it.id == teamId }?.shortName

    fun homeGround(teamId: String): String = DEFS.find { it.id == teamId }?.home ?: "mirpur"

    private val GROUND = mapOf(
        "sy" to "sylhet", "mi" to "mirpur", "kh" to "khulna", "ct" to "chattogram", "so" to "sylhet-academy",
        "cb" to "cox-bazar-academy", "rj" to "rajshahi-stadium", "bg" to "bogura"
    )

    private fun pitchText(): String = "A flat, true surface that turns as the match goes on."

    /** The two grounds the game did not have already (estimated ratings). Mirpur, Sylhet, Chattogram, Khulna, Rajshahi and Bogura were in StadiumData. */
    val stadiums: List<Stadium> by lazy {
        VENUE_TEXT.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val p = line.split("|")
            Stadium(
                id = p[0], name = p[1], city = p[2], country = "Bangladesh", homeTeamIds = emptyList(),
                pitchType = PitchType.DUSTY, pitchDescription = pitchText(), boundarySize = BoundarySize.MEDIUM,
                altitudeM = p[5].toInt(), dewFactor = 0.2, avgT20FirstInningsScore = 150,
                climate = StadiumClimate(0.2, 70, 27, 10, "Warm and humid.")
            )
        }
    }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    fun stadiumsFor(id: String): List<Stadium> = buildSchedule(id).map { it.stadiumId }.distinct().mapNotNull { CplData.stadium(it) }

    // ---------------------------------------------------------------- players

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' }

    private fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)

    private fun jit(name: String, salt: Int, range: Int): Int {
        var h = salt * 7919
        for (c in name) h = h * 31 + c.code
        return (h and 0x7fffffff) % (2 * range + 1) - range
    }

    /** The game's spellings of a few Bangladeshi names. */
    private val ALIAS = mapOf(
        "nurulhasansohan" to "nurulhasan", "shamimhossainpatwary" to "shamimhossain", "afifhossaindrubo" to "afifhossain",
        "mahidulislamankon" to "mahidulislamankon", "mohammedmithun" to "mohammadmithun", "zakirhasan" to "zakirhasan"
    )

    private fun lookup(known: Map<String, Player>, name: String): Player? {
        known[norm(name)]?.let { return it }
        ALIAS[norm(name)]?.let { a -> known[a]?.let { return it } }
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

    /** The eight divisions. A player already in the game is copied with his ratings; the rest get estimates by the division's strength. */
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

    /** The 28 published matches in the published order. */
    fun buildSchedule(id: String): List<TournamentFixture> =
        rows(NCL_TEXT).mapIndexed { i, p ->
            TournamentFixture(i, i + 1, CplData.LEAGUE, "bd_" + p[0], "bd_" + p[1], GROUND.getValue(p[2]))
        }

    fun newTournament(id: String, userTeamId: String, built: Pair<List<Team>, Map<String, SquadTag>>): TournamentState =
        TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = id,
            name = NCL_NAME,
            userTeamId = userTeamId,
            teams = built.first,
            tags = built.second,
            fixtures = buildSchedule(id),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )

    // ---------------------------------------------------------------- points and table

    private fun battingBonus(runs: Int): Int = (if (runs >= 200) 1 else 0) + (if (runs >= 250) 1 else 0) + (if (runs >= 300) 1 else 0) + (if (runs >= 350) 1 else 0)

    private fun bowlingBonus(wickets: Int): Int = (if (wickets >= 3) 1 else 0) + (if (wickets >= 5) 1 else 0) + (if (wickets >= 7) 1 else 0) + (if (wickets >= 9) 1 else 0)

    /** The assumed scheme: 12 for a win, 6 for a tie, 4 for a draw, plus the first-innings batting and bowling bonus points. */
    fun points(s: MatchState, teamId: String): Int {
        val base = when (FirstClass.kind(s)) {
            "WIN" -> if (s.winnerId == teamId) 12 else 0
            "TIE" -> 6
            else -> 4
        }
        val list = s.test?.completed ?: return base
        val mine = list.firstOrNull { it.data.battingTeamId == teamId }
        val theirs = list.firstOrNull { it.data.battingTeamId != teamId }
        return base + (if (mine != null) battingBonus(mine.score.runs) else 0) + (if (theirs != null) bowlingBonus(theirs.score.wickets) else 0)
    }

    /** The table: points, then wins, then runs per wicket scored over conceded. */
    fun table(state: TournamentState): List<FcRow> {
        val rows = state.teams.map { team ->
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
                if (f.stage != CplData.LEAGUE) continue
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

    /** The champions: the top of the table once all 28 matches are played. */
    fun champion(state: TournamentState): Team? {
        if (state.fixtures.isEmpty() || state.fixtures.any { it.result == null }) return null
        return table(state).firstOrNull()?.team
    }

    fun seasonEndLines(state: TournamentState): List<String> {
        val c = champion(state) ?: return emptyList()
        return listOf("${c.name} are the National Cricket League champions.")
    }

    // ---------------------------------------------------------------- the data

    // code | name | short | home ground | strength tier (1-5) | squad: name ~ flags ~ role (B batter, A all-rounder, W keeper, L bowler, - unknown) ~ - ~ bowling style
    private const val TEAM_TEXT = """
syl|Sylhet Division|SYL|sylhet|3|Zakir Hasan~~B~~;Amite Hasan~~-~~;Md Tanvir Hussain Sojib~~-~~;Ashraful Hasan Rihad~~-~~;Asadullah Al Galib~~-~~;Mubin Ahmed Dishan~~-~~;Tofael Ahmed Rayhan~~-~~;Shahanur Rahman~~-~~;Shykat Ali~~-~~;Nayeem Ahmed~~-~~;Nabil Samad~~-~~;Tawhidul Islam Ferdus~~-~~;Naim Hossain Sakib~~-~~;Mohiuddin Tarek~~-~~;Anik Debbarma~~-~~
dha|Dhaka Division|DHA|mirpur|5|Jishan Alam~~B~~;Ashiqur Rahman Shibli~~B~~;Anamul Haque~wk~W~~;Arif Ahmed~~-~~;Sumon Khan~~L~~RM;Marshall Ayub~~B~~;Anisul Islam Emon~~B~~;Salauddin Sakil~~-~~;Toibur Rahman Parvej~~-~~;Easin Hossain~~-~~;Ripon Mondol~~L~~RM;Mahidul Islam Ankon~~B~~;Aminul Islam Biplob~~B~~;Taufike Ahmed~~-~~;Naim Sheikh~~B~~
raj|Rajshahi Division|RAJ|rajshahi-stadium|3|Sabbir Rahman~~B~~;Meherob Hasan Ahin~~-~~;Habibur Rahman Sohan~~-~~;Sabbir Hosan~~-~~;Sunzamul Islam~~-~~;Nihaduzzaman~~-~~;Ekramuzzaman~~-~~;Shafiqul Islam~~L~~RM;Nayeem Ahmed Rajshahi~~-~~;Ali Mohammad Walid~~-~~;Wasi Siddique~~-~~;Rahim Ahamed~~-~~;Shakhir Hossain Shuvro~~-~~;Pritom Kumar~~B~~;Mohor Sheikh Antor~~-~~
ran|Rangpur Division|RAN|bogura|5|Anik Sarker Shatu~~-~~;Jahid Javed~~-~~;Abdullah Al Mamun~~-~~;Akbar Ali~wk~W~~;Mehedi Hasan~~-~~;Abu Hasim~~L~~RM;Shadhin Islam~~-~~;Chowdhury Md Rizwan~~-~~;Shahria Al Amin~~-~~;Iqbal Hossain~~B~~;Nobin Islam~~-~~;Mahmudul Hasan Limon~~-~~;Hasibul Hosen Siyam~~-~~;Mim Mosaddeak~~-~~;Alauddin Babu~~L~~OB
cht|Chattogram Division|CHT|chattogram|4|Irfan Shukkur~~B~~;Shahadat Hossain~~B~~;Mahmudul Hasan Joy~~B~~;Hasan Murad~~L~~SLA;Md Rubel~~-~~;Sadikur Rahman~~-~~;Shamim Hossain Patwary~~A~~OB;Ashraful Hasan Rohan~~-~~;Ahmed Sharif~~-~~;Fahad Hossain~~-~~;Irfan Hossain~~-~~;Enamul Haque Ashiq~~-~~;Sabbir Hossain Sikder~~-~~;Md Shah Paran Sohel~~-~~;Tasamul Haque~~-~~
bar|Barishal Division|BAR|chattogram|3|MoJammel Hasan Shakil~~-~~;Zawad Abrar~~B~~;Md Rafsan Al Mahmud~~-~~;Mohammad Salman Hossain Emon~~-~~;Tanvir Islam~~L~~SLA;Ovi Ahmed Nobel~~-~~;Moin Khan~~-~~;Tawhidul Islam Rasel~~-~~;Iftakhar Hossain Ifti~~A~~OB;Abdullah Shifat~~-~~;Hafizur Rahman~~-~~;Md Shohidul Islam~~-~~;Ruyel Mia~~-~~;Md Shamsul Islam Anik~~-~~;Moinul Islam~~-~~
mym|Mymensingh Division|MYM|sylhet|3|Abdul Majid~~L~~RM;Rony Chowhun~~-~~;Al Amin~~L~~RM;Mossabbek Hossain Sun~~-~~;Abu Hider Rony~~L~~RM;Munim Shahriar~~B~~;Asadullah Hill Galib~~-~~;Rakibul Hasan~~-~~;Al Amin Hossen~~-~~;Khalid Hasan~~-~~;Azijul Hakim Rony~~-~~;Md Fahim~~-~~;Md Mahfijul Islam~~-~~;Ariful Islam~~-~~;Rizan Hossan~~-~~
khu|Khulna Division|KHU|khulna|4|Anamul Haque Bijoy~~B~~;Azizul Hakim Tamim~~-~~;Imranuzzaman~~-~~;Mohammed Mithun~~B~~;Nurul Hasan Sohan~wk~W~~;Samiun Bashir Ratul~~-~~;Kalam Siddiki Allen~~-~~;Nahidul Islam~~L~~RM;Sheikh Parvej Jibon~~-~~;Afif Hossain Drubo~~A~~OB;Tipu Sultan~~-~~;Rakib Hawlader~~-~~;Sofor Ali~~-~~;Abdul Halim~~-~~;Mehdi Hasan Rana~~L~~RM
"""

    // id | name | city | pitch | boundary | altitude in metres
    private const val VENUE_TEXT = """
cox-bazar-academy|Cox's Bazar Academy Ground|Cox's Bazar|B|M|5
sylhet-academy|SICS Academy Ground|Sylhet|B|M|30
"""

    // home team | visiting team | ground, in the published order
    private const val NCL_TEXT = """
syl|mym|sy
dha|ran|mi
khu|bar|kh
cht|raj|ct
syl|dha|sy
mym|ran|so
khu|raj|mi
cht|bar|cb
syl|ran|ct
mym|dha|cb
khu|cht|so
raj|bar|rj
syl|khu|cb
mym|cht|bg
dha|raj|rj
ran|bar|so
syl|cht|ct
mym|khu|kh
dha|bar|rj
ran|raj|bg
syl|raj|bg
mym|bar|ct
dha|khu|mi
ran|cht|kh
syl|bar|kh
mym|raj|mi
dha|cht|rj
ran|khu|so
"""
}

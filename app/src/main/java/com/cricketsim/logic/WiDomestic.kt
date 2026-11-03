package com.cricketsim.logic

/**
 * West Indies domestic cricket, 2027 edition: the West Indies Championship (four-day) and the Super50 Cup (50 overs), played by the six
 * regional teams. Team ids are "wi_xx". See WEST_INDIES_DOMESTIC.md.
 *
 * There are NO published fixtures for the next editions yet (Cricket West Indies has cut its calendar), so the schedules are made by the
 * game in the published formats; only the venues follow the 2026 and 2025-26 editions.
 *
 *  - West Indies Championship (2026 format): three simultaneous three-match series between Windward and Guyana, Jamaica and Barbados,
 *    Leeward and Trinidad and Tobago (the seeding pairs of 2026: 1 v 6, 3 v 4, 2 v 5). The winner of each series (more points) is ranked 1 to 3
 *    by points; the top one goes straight to the Final, the other two play a Playoff, and the winner of the Playoff meets the top one in the Final
 *    (four days each, at the Sir Vivian Richards Stadium in Antigua). The real top side also played the West Indies Academy as a warm-up:
 *    that match is not built. Points: 16 for a win, 6 for a tie, 3 for a draw (3 each), plus batting bonus (1 point for each 50 runs from 250, up
 *    to 5) and bowling bonus (1 point for 3 wickets, 2 for 5, 3 for 7), from the first innings. The real rule counts only the first 110 overs and
 *    adds 0.2 for each wicket of a designated pace bowler: not built.
 *  - Super50 Cup (2025-26 format): six teams, each plays the other five once (15 matches, all in Trinidad), 4 points a win and 2 for a tie
 *    or no result (the real bonus point is not built), then a Final between the top two. A tied league match is shared.
 *
 * Squads: the 2026 championship squads (13 or 14 players each, Wikipedia). A player already in the game keeps his ratings; the others get
 * ESTIMATED ratings. The West Indies Academy squad is not included.
 */
object WiDomestic {
    const val FOURDAY_ID = "wi4d_2027"
    const val FOURDAY_NAME = "West Indies Championship 2027"
    const val ODC_ID = "wi50_2026_27"
    const val ODC_NAME = "Super50 Cup 2026-27 (West Indies)"
    const val DAYS = 4

    private const val PLAYOFF = "Playoff"
    private const val FINAL = "Final"
    private const val FOURDAY_FINALS_GROUND = "vivian-richards-stadium"
    private const val ODC_FINAL_GROUND = "queens-park-oval"

    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN

    /** The bilateral series: team codes and the ground of the series. */
    private val SERIES = listOf(
        Triple("win", "guy", "vivian-richards-stadium"),
        Triple("jam", "bar", "sabina-park"),
        Triple("lee", "tt", "vivian-richards-stadium")
    )

    fun isFourDay(id: String?): Boolean = id == FOURDAY_ID
    fun isOdc(id: String?): Boolean = id == ODC_ID
    fun isWi(id: String?): Boolean = id == FOURDAY_ID || id == ODC_ID

    /** The Super50 Cup: 50 overs, no DRS, a tied league match is shared. */
    fun rulesFor(id: String?, stage: String?): MatchRules? {
        if (id != ODC_ID) return null
        val base = TournamentRules.rulesForFormat("ODI") ?: return null
        return base.copy(drsReviews = 0, noSuperOver = stage != FINAL)
    }

    fun introOf(id: String?): String =
        if (id == FOURDAY_ID) {
            "Sixteen points for a win, six for a tie, three for a draw, plus batting and bowling bonus points from the first innings. " +
                "Three series of three matches: Windward v Guyana, Jamaica v Barbados, Leeward v Trinidad and Tobago. The three series winners are ranked by points: " +
                "the top one goes to the Final, the other two play a Playoff, and its winner meets the top one in the Final in Antigua."
        } else {
            "Four points for a win, two each for a tie or no result. Each side plays the other five once, then the top two play the Final."
        }

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
            TeamDef("wi_" + p[0], p[1], p[2], p[3], p[4].toInt(), p[5].split(";").map { parseEntry(it) })
        }
    }

    fun isTeam(teamId: String): Boolean = DEFS.any { it.id == teamId }

    fun shortNameOrNull(teamId: String): String? = DEFS.find { it.id == teamId }?.shortName

    fun homeGround(teamId: String): String = DEFS.find { it.id == teamId }?.home ?: "queens-park-oval"

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

    /** The one ground the game did not have already (estimated ratings). */
    val stadiums: List<Stadium> by lazy {
        VENUE_TEXT.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val p = line.split("|")
            val pitch = pitchOf(p[3][0])
            Stadium(
                id = p[0], name = p[1], city = p[2], country = "West Indies", homeTeamIds = emptyList(),
                pitchType = pitch, pitchDescription = pitchText(pitch), boundarySize = boundaryOf(p[4][0]),
                altitudeM = p[5].toInt(), dewFactor = 0.15, avgT20FirstInningsScore = 160,
                climate = StadiumClimate(0.3, 75, 29, 8, "Hot and humid with the odd shower.")
            )
        }
    }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    fun stadiumsFor(id: String): List<Stadium> =
        (buildSchedule(id).map { it.stadiumId } + (if (id == FOURDAY_ID) FOURDAY_FINALS_GROUND else ODC_FINAL_GROUND))
            .distinct().mapNotNull { CplData.stadium(it) }

    // ---------------------------------------------------------------- players

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

    /** The six teams. A player already in the game (the roster, the CPL, the leagues) is copied with his ratings; the rest get estimates. */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val known = HashMap<String, Player>()
        fun addAll(players: List<Player>) {
            for (p in players) known.putIfAbsent(norm(p.name), p)
        }
        addAll(CricketData.getAllTeams().flatMap { it.players })
        addAll(CplData.buildTeams().first.flatMap { it.players })
        addAll(MajorLeagueCricket.buildTeams().first.flatMap { it.players })
        addAll(Sa20.buildTeams().first.flatMap { it.players })
        addAll(AbuDhabiT10.buildTeams().first.flatMap { it.players })
        addAll(LankaPremierLeague.buildTeams().first.flatMap { it.players })
        addAll(InternationalLeagueT20.buildTeams().first.flatMap { it.players })
        addAll(BigBashLeague.buildTeams().first.flatMap { it.players })
        addAll(IplData.buildPool().map { it.player })
        val tags = LinkedHashMap<String, SquadTag>()
        val teams = DEFS.map { def ->
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

    // ---------------------------------------------------------------- fixtures

    /** Circle method: the rounds of a single round robin of six. Home and away alternate. */
    private fun rounds(teams: List<String>): List<List<Pair<String, String>>> {
        val list = ArrayList<String>(teams)
        val n = list.size
        val out = ArrayList<List<Pair<String, String>>>()
        for (r in 0 until n - 1) {
            val ms = ArrayList<Pair<String, String>>()
            for (i in 0 until n / 2) {
                val a = list[i]
                val b = list[n - 1 - i]
                ms.add(if ((r + i) % 2 == 0) a to b else b to a)
            }
            out.add(ms)
            list.add(1, list.removeAt(n - 1))
        }
        return out
    }

    /** Championship: three rounds, each with one match of each series (the ground of a series never changes). */
    private fun fourDaySchedule(): List<TournamentFixture> {
        val out = ArrayList<TournamentFixture>()
        for (round in 0 until 3) {
            for ((a, b, ground) in SERIES) {
                val home = if (round % 2 == 0) a else b
                val away = if (round % 2 == 0) b else a
                out.add(TournamentFixture(out.size, out.size + 1, CplData.LEAGUE, "wi_$home", "wi_$away", ground))
            }
        }
        return out
    }

    /** Super50 Cup: 15 matches in Trinidad, three grounds in turn. */
    private fun odcSchedule(): List<TournamentFixture> {
        val grounds = listOf("brian-lara-tarouba", "queens-park-oval", "brian-lara-academy")
        val out = ArrayList<TournamentFixture>()
        for (round in rounds(DEFS.map { it.id.removePrefix("wi_") })) {
            for ((h, a) in round) out.add(TournamentFixture(out.size, out.size + 1, CplData.LEAGUE, "wi_$h", "wi_$a", grounds[out.size % 3]))
        }
        return out
    }

    fun buildSchedule(id: String): List<TournamentFixture> = if (id == FOURDAY_ID) fourDaySchedule() else odcSchedule()

    fun newTournament(id: String, userTeamId: String, built: Pair<List<Team>, Map<String, SquadTag>>): TournamentState =
        TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = id,
            name = if (id == FOURDAY_ID) FOURDAY_NAME else ODC_NAME,
            userTeamId = userTeamId,
            teams = built.first,
            tags = built.second,
            fixtures = buildSchedule(id),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )

    // ---------------------------------------------------------------- Championship points and table

    private fun battingBonus(runs: Int): Int = if (runs < 250) 0 else minOf(5, (runs - 200) / 50)

    private fun bowlingBonus(wickets: Int): Int = if (wickets < 3) 0 else minOf(3, (wickets - 1) / 2)

    /** 16 for a win, 6 for a tie, 3 for a draw, plus the first-innings bonus points. */
    fun points(s: MatchState, teamId: String): Int {
        val base = when (FirstClass.kind(s)) {
            "WIN" -> if (s.winnerId == teamId) 16 else 0
            "TIE" -> 6
            else -> 3
        }
        val list = s.test?.completed ?: return base
        val mine = list.firstOrNull { it.data.battingTeamId == teamId }
        val theirs = list.firstOrNull { it.data.battingTeamId != teamId }
        return base + (if (mine != null) battingBonus(mine.score.runs) else 0) + (if (theirs != null) bowlingBonus(theirs.score.wickets) else 0)
    }

    /** The Championship table over the nine league matches: points, then wins, then runs per wicket scored over conceded. */
    fun fourDayTable(state: TournamentState): List<FcRow> {
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

    /** The Super50 Cup table: 4 points for a win, 2 for a tie or no result. */
    fun odcTable(state: TournamentState): List<StandingRow> =
        CplData.standings(state, CplData.LEAGUE).map { it.copy(points = it.won * 4 + it.noResult * 2) }

    // ---------------------------------------------------------------- the knockouts

    private fun winnerOf(fx: List<TournamentFixture>, stage: String): String? =
        fx.firstOrNull { it.stage == stage }?.result?.winnerId

    /** The three series winners (the better placed of each pair in the table), ranked by points: 1st goes to the Final, 2nd and 3rd play the Playoff. */
    private fun rankedWinners(state: TournamentState): List<String> {
        val table = fourDayTable(state)
        val points = table.associate { it.team.id to it.points }
        val order = table.map { it.team.id }
        return SERIES.map { (a, b, _) ->
            val ta = "wi_$a"
            val tb = "wi_$b"
            if (order.indexOf(ta) <= order.indexOf(tb)) ta else tb
        }.sortedWith(compareByDescending<String> { points[it] ?: 0 }.thenBy { order.indexOf(it) })
    }

    /** Championship: after nine matches the Playoff (2nd v 3rd) is added, then the Final. Super50: the Final between the top two. Safe after every match. */
    fun advance(state: TournamentState): TournamentState {
        var fx = state.fixtures
        val league = fx.filter { it.stage == CplData.LEAGUE }
        val leagueDone = league.isNotEmpty() && league.all { it.result != null }
        if (!leagueDone) return state
        if (state.tournamentId == ODC_ID) {
            if (fx.none { it.stage == FINAL }) {
                val top = odcTable(state).map { it.team.id }.take(2)
                if (top.size == 2) fx = fx + TournamentFixture(fx.size, fx.size + 1, FINAL, top[0], top[1], ODC_FINAL_GROUND)
            }
            return state.copy(fixtures = fx)
        }
        val seeds = rankedWinners(state)
        if (seeds.size == 3) {
            if (fx.none { it.stage == PLAYOFF }) {
                fx = fx + TournamentFixture(fx.size, fx.size + 1, PLAYOFF, seeds[1], seeds[2], FOURDAY_FINALS_GROUND)
            }
            val w = winnerOf(fx, PLAYOFF)
            if (w != null && fx.none { it.stage == FINAL }) {
                fx = fx + TournamentFixture(fx.size, fx.size + 1, FINAL, seeds[0], w, FOURDAY_FINALS_GROUND)
            }
        }
        return state.copy(fixtures = fx)
    }

    // ---------------------------------------------------------------- the data

    // code | name | short | home ground | strength tier (1-5) | squad: name ~ flags ~ role (B batter, A all-rounder, W keeper, L bowler, - unknown) ~ - ~ bowling style
    private const val TEAM_TEXT = """
bar|Barbados Pride|BAR|kensington-oval|4|Kraigg Brathwaite~~B~~OB;Joshua Bishop~~L~~RM;Jediah Blades~~L~~RM;Leniko Boucher~~B~~;Shian Brathwaite~~-~~;Jonathan Drakes~~-~~;Johann Layne~~A~~OB;Kyle Mayers~~A~~RM;Jair McAllister~~-~~;Shayne Moseley~~B~~;Ramon Simmonds~~A~~RM;Shamar Springer~~L~~RM;Jomel Warrican~~L~~SLA;Kevin Wickham~wk~W~~
guy|Guyana Harpy Eagles|GUY|providence-stadium|5|Tevin Imlach~wk~W~~;Matthew Nandu~~B~~;Kevlon Anderson~~L~~RM;Tagenarine Chanderpaul~~B~~;Richie Looknauth~~L~~SLA;Gudakesh Motie~~L~~SLA;Keemo Paul~~A~~RM;Raymond Perez~~L~~SLA;Veerasammy Permaul~~L~~SLA;Zeynul Ramsammy~~-~~;Kemol Savory~~B~~;Nial Smith~~L~~RM;Isai Thorne~~B~~;Jonathan van Lange~~B~~
jam|Jamaica Scorpions|JAM|sabina-park|3|John Campbell~~B~~;Brad Barnes~~B~~;Carlos Brown~~-~~;Javelle Glen~~A~~SLA;Brandon King~~B~~;Abhijai Mansingh~~-~~;Kirk McKenzie~~A~~OB;Marquino Mindley~~L~~RM;Romaine Morris~~-~~;Jeavor Royal~~B~~;Pete Salmon~~L~~SLA;Ojay Shields~~L~~RM;Odean Smith~~A~~RM
lee|Leeward Islands Hurricanes|LEE|vivian-richards-stadium|3|Justin Greaves~~A~~RM;Jewel Andrew~wk~W~~;Keacy Carty~~B~~;Rahkeem Cornwall~~A~~OB;Daniel Doram~~-~~;Nathan Edward~~L~~RM;Karima Gore~~L~~SLA;Jahmar Hamilton~wk~W~~;Kadeem Henry~~-~~;Jeremiah Louis~~B~~;Mikyle Louis~~B~~;Kelvin Pitman~~A~~RM;Oshane Thomas~~L~~RM
tt|Trinidad and Tobago Red Force|TTO|queens-park-oval|5|Joshua Da Silva~wk~W~~;Cephas Cooper~~B~~;Jason Mohammed~~B~~;Jyd Goolie~~B~~;Amir Jangoo~~B~~;Yannic Cariah~~L~~LB;Evin Lewis~~B~~;Bryan Charles~~A~~RM;Jayden Seales~~L~~RM;Terrance Hinds~~L~~RM;Anderson Phillip~~L~~RM;Joshua James~~A~~RM;Khary Pierre~~L~~SLA
win|Windward Islands Volcanoes|WIN|arnos-vale-st-vincent|2|Alick Athanaze~~B~~OB;Teddy Bishop~~-~~;Sunil Ambris~~B~~;Darel Cyrus~~-~~;Kenneth Dember~~-~~;Shadrack Descarte~~-~~;Stephan Pascal~~L~~RM;Noelle Leo~~-~~;Ackeem Auguste~~B~~;Kavem Hodge~~B~~OB;Ryan John~~L~~SLA;Gilon Tyson~~L~~RM;Darron Nedd~~-~~
"""

    // id | name | city | pitch (B batting, G green, D dusty, O bowling) | boundary (S, M, L) | altitude in metres
    private const val VENUE_TEXT = """
brian-lara-academy|Brian Lara Cricket Academy|Couva|B|M|10
"""
}

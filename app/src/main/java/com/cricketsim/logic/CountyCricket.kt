package com.cricketsim.logic

/**
 * English county cricket, 2027 season: the One-Day Cup (50 overs) and the Vitality Blast (T20), played by the 18 first-class counties.
 * Team ids are "cc_xx" (the County Championship uses the same teams). See COUNTY_CRICKET.md.
 *
 * The 2027 fixture lists are not published yet (the ECB releases them in November), so the schedules here are made by the game in the
 * published format: only the Blast's Finals Day (Edgbaston) is known. Groups are the 2026 groups (assumption).
 *
 *  - One-Day Cup: two groups of nine, each county plays the other eight once (4 points a win, 2 for a tie or no result); the top three
 *    of each group go on: A2 v B3 and B2 v A3 (quarter-finals), A1 and B1 meet the winners in the semi-finals, then the Final at Trent Bridge.
 *  - Vitality Blast: three groups of six (North, Central and West, South); 12 group matches each (the other five twice, one home match
 *    against a county from outside the group and one away); the top two of each group and the two best third-placed counties go to the
 *    quarter-finals (1 v 8, 2 v 7, 3 v 6, 4 v 5, seeded by points: winners, then runners-up, then thirds), then the semi-finals and the
 *    Final on Finals Day at Edgbaston.
 *
 * Squads are the clubs' squad lists (Wikipedia, 1 November 2026) with ESTIMATED ratings; a player already in the game keeps his ratings.
 */
object CountyCricket {
    const val ODC_ID = "odc_2027"
    const val ODC_NAME = "One-Day Cup 2027"
    const val BLAST_ID = "blast_2027"
    const val BLAST_NAME = "Vitality Blast 2027"
    const val CC_ID = "cc_2027"
    const val CC_NAME = "County Championship 2027"

    private const val GROUP_A = "Group A"
    private const val GROUP_B = "Group B"
    private const val LEAGUE_STAGE = "League"
    private const val ODC_FINAL_GROUND = "trent-bridge"
    private const val BLAST_FINALS_DAY_GROUND = "edgbaston"

    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN

    /** The 2026 groups. One-Day Cup: A then B. Blast: North, Central and West, South. */
    private val ODC_GROUPS = listOf(
        GROUP_A to listOf("lei", "lan", "not", "war", "sur", "ken", "nor", "glo", "som"),
        GROUP_B to listOf("mid", "yor", "dur", "wor", "ham", "der", "ess", "gla", "sus")
    )
    private val BLAST_GROUPS = listOf(
        "North Group" to listOf("not", "yor", "lan", "dur", "der", "lei"),
        "Central and West Group" to listOf("nor", "som", "glo", "war", "gla", "wor"),
        "South Group" to listOf("ham", "sur", "ess", "ken", "mid", "sus")
    )

    fun isCounty(id: String?): Boolean = id == ODC_ID || id == BLAST_ID || id == CC_ID

    /** 50 overs (One-Day Cup, a tied league match is shared) or 20 overs (Blast), no DRS; rain as in the game's formats. */
    fun rulesFor(id: String?, stage: String?): MatchRules? {
        val key = when (id) {
            ODC_ID -> "ODI"
            BLAST_ID -> "T20"
            else -> return null
        }
        val base = TournamentRules.rulesForFormat(key) ?: return null
        val league = stage == null || stage == GROUP_A || stage == GROUP_B || stage == LEAGUE_STAGE
        return base.copy(drsReviews = 0, noSuperOver = id == ODC_ID && league)
    }

    fun introOf(id: String?): String =
        if (id == ODC_ID) {
            "Four points for a win, two each for a tie or no result. The top three of each group go through. " +
                "Quarter-finals: second in A v third in B, and second in B v third in A. " +
                "The winners of each group play the quarter-final winners in the semi-finals, then the Final at Trent Bridge."
        } else {
            "Two points for a win, one each for a tie or no result. The top two of each group and the two best third-placed counties " +
                "reach the quarter-finals, seeded by points. The semi-finals and the Final are on Finals Day at Edgbaston."
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
            TeamDef("cc_" + p[0], p[1], p[2], p[3], p[4].toInt(), p[5].split(";").map { parseEntry(it) })
        }
    }

    fun isTeam(teamId: String): Boolean = DEFS.any { it.id == teamId }

    fun shortNameOrNull(teamId: String): String? = DEFS.find { it.id == teamId }?.shortName

    fun homeGround(teamId: String): String = DEFS.find { it.id == teamId }?.home ?: "lords"

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

    /** The county grounds the game did not have already (estimated ratings). The others (Lord's, The Oval, Edgbaston ...) are in StadiumData. */
    val stadiums: List<Stadium> by lazy {
        VENUE_TEXT.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val p = line.split("|")
            val pitch = pitchOf(p[3][0])
            Stadium(
                id = p[0], name = p[1], city = p[2], country = "England",
                homeTeamIds = DEFS.filter { it.home == p[0] }.map { it.id },
                pitchType = pitch, pitchDescription = pitchText(pitch), boundarySize = boundaryOf(p[4][0]),
                altitudeM = p[5].toInt(), dewFactor = 0.1, avgT20FirstInningsScore = 165,
                climate = StadiumClimate(0.45, 72, 17, 14, "Genuinely rain-prone conditions.")
            )
        }
    }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    fun stadiumsFor(id: String): List<Stadium> =
        (buildSchedule(id).map { it.stadiumId } + (if (id == ODC_ID) ODC_FINAL_GROUND else BLAST_FINALS_DAY_GROUND))
            .distinct().mapNotNull { CplData.stadium(it) }

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

    /** The 18 counties. A player already in the game (the roster, the IPL pool or any league) is copied with his ratings; the rest get estimates. */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val known = HashMap<String, Player>()
        fun addAll(players: List<Player>) {
            for (p in players) known.putIfAbsent(norm(p.name), p)
        }
        addAll(CricketData.getAllTeams().flatMap { it.players })
        addAll(CplData.buildTeams().first.flatMap { it.players })
        addAll(AbuDhabiT10.buildTeams().first.flatMap { it.players })
        addAll(LankaPremierLeague.buildTeams().first.flatMap { it.players })
        addAll(InternationalLeagueT20.buildTeams().first.flatMap { it.players })
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

    /** Circle method: the rounds of a single round robin (an odd number of teams gets a bye). Home and away alternate. */
    private fun rounds(teams: List<String>): List<List<Pair<String, String>>> {
        val list = ArrayList<String?>(teams)
        if (list.size % 2 == 1) list.add(null)
        val n = list.size
        val out = ArrayList<List<Pair<String, String>>>()
        for (r in 0 until n - 1) {
            val ms = ArrayList<Pair<String, String>>()
            for (i in 0 until n / 2) {
                val a = list[i]
                val b = list[n - 1 - i]
                if (a != null && b != null) ms.add(if ((r + i) % 2 == 0) a to b else b to a)
            }
            out.add(ms)
            list.add(1, list.removeAt(n - 1))
        }
        return out
    }

    private fun fixture(index: Int, stage: String, home: String, away: String): TournamentFixture =
        TournamentFixture(index, index + 1, stage, home, away, homeGround(home))

    /** One-Day Cup: 72 group matches, round by round, group A then group B. The knockouts come from [advance]. */
    private fun odcSchedule(): List<TournamentFixture> {
        val rs = ODC_GROUPS.map { g -> rounds(g.second.map { "cc_$it" }) }
        val out = ArrayList<TournamentFixture>()
        for (r in 0 until rs[0].size) {
            for (gi in ODC_GROUPS.indices) {
                for ((h, a) in rs[gi][r]) out.add(fixture(out.size, ODC_GROUPS[gi].first, h, a))
            }
        }
        return out
    }

    /** Blast: 108 group matches (all stage "League"): home and away inside each group, and one home and one away match across groups. */
    private fun blastSchedule(): List<TournamentFixture> {
        val groups = BLAST_GROUPS.map { g -> g.second.map { "cc_$it" } }
        val first = groups.map { rounds(it) }
        val across = ArrayList<Pair<String, String>>()
        for (g in 0 until 3) {
            for (k in 0 until 6) across.add(groups[g][k] to groups[(g + 1) % 3][(k + g) % 6])
        }
        val out = ArrayList<TournamentFixture>()
        var chunk = 0
        for (r in 0 until 10) {
            val leg = r / 5
            val round = r % 5
            for (g in groups.indices) {
                for ((h, a) in first[g][round]) {
                    out.add(if (leg == 0) fixture(out.size, LEAGUE_STAGE, h, a) else fixture(out.size, LEAGUE_STAGE, a, h))
                }
            }
            if (r % 5 != 0 && chunk < 6) {
                for (m in 0 until 3) {
                    val (h, a) = across[chunk * 3 + m]
                    out.add(fixture(out.size, LEAGUE_STAGE, h, a))
                }
                chunk++
            }
        }
        while (chunk < 6) {
            for (m in 0 until 3) {
                val (h, a) = across[chunk * 3 + m]
                out.add(fixture(out.size, LEAGUE_STAGE, h, a))
            }
            chunk++
        }
        return out
    }

    fun buildSchedule(id: String): List<TournamentFixture> = when (id) {
        ODC_ID -> odcSchedule()
        CC_ID -> ccSchedule()
        else -> blastSchedule()
    }

    fun newTournament(id: String, userTeamId: String, built: Pair<List<Team>, Map<String, SquadTag>>): TournamentState =
        TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = id,
            name = when (id) { ODC_ID -> ODC_NAME; CC_ID -> CC_NAME; else -> BLAST_NAME },
            userTeamId = userTeamId,
            teams = built.first,
            tags = built.second,
            fixtures = buildSchedule(id),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )

    // ---------------------------------------------------------------- tables and progress

    private fun teamsOfStage(state: TournamentState, stage: String): Set<String> =
        state.fixtures.filter { it.stage == stage }.flatMap { listOfNotNull(it.homeId, it.awayId) }.toSet()

    /** One-Day Cup table: 4 points for a win, 2 for a tie or no result. */
    private fun odcTable(state: TournamentState, stage: String): List<StandingRow> {
        val ids = teamsOfStage(state, stage)
        return CplData.standings(state, stage).filter { it.team.id in ids }
            .map { it.copy(points = it.won * 4 + it.noResult * 2) }
    }

    fun tables(state: TournamentState): List<Pair<String, List<StandingRow>>> =
        when (state.tournamentId) {
            ODC_ID -> ODC_GROUPS.map { it.first }.filter { teamsOfStage(state, it).isNotEmpty() }.map { it to odcTable(state, it) }
            BLAST_ID -> {
                val rows = CplData.standings(state, LEAGUE_STAGE)
                BLAST_GROUPS.map { g -> g.first to rows.filter { it.team.id.removePrefix("cc_") in g.second } }
            }
            else -> emptyList()
        }

    private fun complete(fx: List<TournamentFixture>, stage: String): Boolean {
        val l = fx.filter { it.stage == stage }
        return l.isNotEmpty() && l.all { it.result != null }
    }

    private fun winnerOf(fx: List<TournamentFixture>, stage: String): String? =
        fx.firstOrNull { it.stage == stage }?.result?.winnerId

    private fun advanceOdc(state: TournamentState): TournamentState {
        var fx = state.fixtures
        fun add(stage: String, home: String?, away: String?, ground: String) {
            if (home == null || away == null) return
            if (fx.any { it.stage == stage }) return
            fx = fx + TournamentFixture(fx.size, fx.size + 1, stage, home, away, ground)
        }
        if (complete(fx, GROUP_A) && complete(fx, GROUP_B)) {
            val ta = odcTable(state.copy(fixtures = fx), GROUP_A).map { it.team.id }
            val tb = odcTable(state.copy(fixtures = fx), GROUP_B).map { it.team.id }
            if (ta.size >= 3 && tb.size >= 3) {
                add("Quarter-final 1", ta[1], tb[2], homeGround(ta[1]))
                add("Quarter-final 2", tb[1], ta[2], homeGround(tb[1]))
                add("Semi-final 1", ta[0], winnerOf(fx, "Quarter-final 2"), homeGround(ta[0]))
                add("Semi-final 2", tb[0], winnerOf(fx, "Quarter-final 1"), homeGround(tb[0]))
            }
        }
        add("Final", winnerOf(fx, "Semi-final 1"), winnerOf(fx, "Semi-final 2"), ODC_FINAL_GROUND)
        return state.copy(fixtures = fx)
    }

    private fun advanceBlast(state: TournamentState): TournamentState {
        var fx = state.fixtures
        fun add(stage: String, home: String?, away: String?, ground: String) {
            if (home == null || away == null) return
            if (fx.any { it.stage == stage }) return
            fx = fx + TournamentFixture(fx.size, fx.size + 1, stage, home, away, ground)
        }
        if (complete(fx, LEAGUE_STAGE) && fx.none { it.stage == "Quarter-final 1" }) {
            val rows = CplData.standings(state.copy(fixtures = fx), LEAGUE_STAGE)
            val byGroup = BLAST_GROUPS.map { g -> rows.filter { it.team.id.removePrefix("cc_") in g.second } }
            if (byGroup.all { it.size >= 3 }) {
                val cmp = compareByDescending<StandingRow> { it.points }.thenByDescending { it.won }.thenByDescending { it.netRunRate }
                val seeds = byGroup.map { it[0] }.sortedWith(cmp) + byGroup.map { it[1] }.sortedWith(cmp) +
                    byGroup.map { it[2] }.sortedWith(cmp).take(2)
                val ids = seeds.map { it.team.id }
                for (q in 0 until 4) add("Quarter-final ${q + 1}", ids[q], ids[7 - q], homeGround(ids[q]))
            }
        }
        add("Semi-final 1", winnerOf(fx, "Quarter-final 1"), winnerOf(fx, "Quarter-final 4"), BLAST_FINALS_DAY_GROUND)
        add("Semi-final 2", winnerOf(fx, "Quarter-final 2"), winnerOf(fx, "Quarter-final 3"), BLAST_FINALS_DAY_GROUND)
        add("Final", winnerOf(fx, "Semi-final 1"), winnerOf(fx, "Semi-final 2"), BLAST_FINALS_DAY_GROUND)
        return state.copy(fixtures = fx)
    }

    /** Fills in the knockouts as the teams become known. Safe after every match. */
    fun advance(state: TournamentState): TournamentState = when (state.tournamentId) {
        ODC_ID -> advanceOdc(state)
        BLAST_ID -> advanceBlast(state)
        else -> state
    }

    // ---------------------------------------------------------------- the County Championship

    const val DAYS = 4
    private const val DIV_ONE = "Division One"
    private const val DIV_TWO = "Division Two"

    /** The 2027 divisions: the 2026 table with Durham and Kent promoted and Hampshire and Leicestershire relegated. */
    private val DIVISIONS = listOf(
        DIV_ONE to listOf("war", "som", "sus", "not", "yor", "gla", "sur", "ess", "dur", "ken"),
        DIV_TWO to listOf("nor", "mid", "wor", "der", "lan", "glo", "lei", "ham")
    )

    fun isChampionship(id: String?): Boolean = id == CC_ID

    /**
     * Division One: each county plays five others home and away and the other four once (14 matches; the same circle design every
     * season, the real list is not out yet). Division Two: home and away against all seven.
     */
    private fun divisionMatches(division: Int): List<Pair<String, String>> {
        val ids = DIVISIONS[division].second.map { "cc_$it" }
        val n = ids.size
        val out = ArrayList<Pair<String, String>>()
        if (division == 0) {
            for (i in 0 until n) {
                for (d in 1..5) {
                    val j = (i + d) % n
                    if (d <= 2) {
                        out.add(ids[i] to ids[j])
                    } else if (d < 5 || i < j) {
                        out.add(ids[i] to ids[j])
                        out.add(ids[j] to ids[i])
                    }
                }
            }
        } else {
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    out.add(ids[i] to ids[j])
                    out.add(ids[j] to ids[i])
                }
            }
        }
        return out
    }

    /** Puts the matches into rounds so that no county plays twice in a round. */
    private fun inRounds(matches: List<Pair<String, String>>): List<List<Pair<String, String>>> {
        val left = ArrayList(matches)
        val rounds = ArrayList<List<Pair<String, String>>>()
        while (left.isNotEmpty()) {
            val used = HashSet<String>()
            val round = ArrayList<Pair<String, String>>()
            val iter = left.iterator()
            while (iter.hasNext()) {
                val m = iter.next()
                if (m.first !in used && m.second !in used) {
                    used.add(m.first)
                    used.add(m.second)
                    round.add(m)
                    iter.remove()
                }
            }
            rounds.add(round)
        }
        return rounds
    }

    /** 126 four-day matches (70 in Division One, 56 in Division Two), round by round, Division One first. */
    private fun ccSchedule(): List<TournamentFixture> {
        val rs = DIVISIONS.indices.map { inRounds(divisionMatches(it)) }
        val out = ArrayList<TournamentFixture>()
        for (r in 0 until rs.maxOf { it.size }) {
            for (d in rs.indices) {
                val round = rs[d].getOrNull(r) ?: continue
                for ((h, a) in round) out.add(fixture(out.size, DIVISIONS[d].first, h, a))
            }
        }
        return out
    }

    private fun battingBonus(runs: Int): Int = when {
        runs >= 400 -> 5
        runs >= 350 -> 4
        runs >= 300 -> 3
        runs >= 250 -> 2
        runs >= 200 -> 1
        else -> 0
    }

    private fun bowlingBonus(wickets: Int): Int = when {
        wickets >= 9 -> 3
        wickets >= 6 -> 2
        wickets >= 3 -> 1
        else -> 0
    }

    /**
     * The ECB's points: 16 for a win, 8 each for a draw or a tie, and bonus points for each side's first innings: batting 1 to 5
     * (200, 250, 300, 350, 400 runs) and bowling 1 to 3 (3, 6, 9 wickets). The real rule counts only the first 110 overs of an
     * innings; the game does not, a small difference.
     */
    fun points(s: MatchState, teamId: String): Int {
        val list = s.test?.completed ?: return 0
        var pts = if (FirstClass.kind(s) == "WIN") (if (s.winnerId == teamId) 16 else 0) else 8
        val mine = list.firstOrNull { it.data.battingTeamId == teamId }
        val theirs = list.firstOrNull { it.data.battingTeamId != teamId }
        if (mine != null) pts += battingBonus(mine.score.runs)
        if (theirs != null) pts += bowlingBonus(theirs.score.wickets)
        return pts
    }

    /** One division's table: points, then wins, then runs per wicket scored over conceded. */
    fun championshipTable(state: TournamentState, stage: String): List<FcRow> {
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

    fun championshipTables(state: TournamentState): List<Pair<String, List<FcRow>>> =
        DIVISIONS.map { it.first }.map { it to championshipTable(state, it) }

    /** The County Champions: the top of Division One, once every match of the season has been played. */
    fun champion(state: TournamentState): Team? {
        if (state.fixtures.isEmpty() || state.fixtures.any { it.result == null }) return null
        return championshipTable(state, DIV_ONE).firstOrNull()?.team
    }

    /** Champions, promotion and relegation (two up, two down) at the end of the season. */
    fun seasonEndLines(state: TournamentState): List<String> {
        if (champion(state) == null) return emptyList()
        val one = championshipTable(state, DIV_ONE)
        val two = championshipTable(state, DIV_TWO)
        if (one.size < 10 || two.size < 2) return emptyList()
        return listOf(
            "${one[0].team.name} are the County Champions.",
            "Promoted to Division One: ${two[0].team.name} and ${two[1].team.name}.",
            "Relegated to Division Two: ${one[8].team.name} and ${one[9].team.name}."
        )
    }

    // ---------------------------------------------------------------- the data

    // code | name | short | home ground | strength tier (1-5) | squad: name ~ flags ~ role (B batter, A all-rounder, W keeper, L bowler) ~ - ~ bowling style
    private const val TEAM_TEXT = """
der|Derbyshire|DER|derby|2|Harry Came~~B~~OB;Yousaf Bin Naeem~~B~~RM;Ross Whiteley~~B~~RM;Amrit Basra~~B~~RM;Wayne Madsen~~B~~OB;Martin Andersson~~A~~RM;Luis Reece~~A~~RM;Matt Montgomery~~A~~OB;Zak Chappell~~A~~RM;Aneurin Donald~wk~W~~OB;Brooke Guest~wk~W~~;Shoaib Bashir~~L~~OB;Ben Aitchison~~L~~RM;Harry Moore~~L~~RM;Jack Morley~~L~~SLA;Nick Potts~~L~~RM;Pat Brown~~L~~RM;Mohammad Abbas~~L~~RM;Rory Haydon~~L~~RM
dur|Durham|DUR|riverside-durham|4|David Bedingham~~B~~;Graham Clark~~B~~LB;Ben McKinney~~B~~OB;Will Rhodes~~B~~RM;Alex Lees~~B~~LB;Emilio Gay~~B~~RM;Colin Ackermann~~B~~OB;Ben Stokes~~A~~RM;Ben Raine~~A~~RM;Kasey Aldridge~~A~~RM;Ollie Robinson~wk~W~~;Haydon Mustard~wk~W~~RM;Robbie Bowman~wk~W~~;Shafiqullah Ghafari~~L~~LB;Luke Robinson~~L~~RM;Callum Parkinson~~L~~SLA;Daniel Hogg~~L~~RM;Archie Bailey~~L~~RM;Mark Wood~~L~~RM;Matthew Potts~~L~~RM;Sam Conners~~L~~RM;James Minto~~L~~RM;Brydon Carse~~L~~RM
ess|Essex|ESS|chelmsford|3|Tom Westley~~B~~OB;Laurie Evans~~B~~RM;Charlie Allison~~B~~OB;Zaman Akhter~~A~~RM;Noah Thain~~A~~RM;Matt Critchley~~A~~LB;Paul Walter~~A~~RM;Luc Benkenstein~~A~~LB;George Bell~wk~W~~;Michael Pepper~wk~W~~;Simon Fernandes~wk~W~~OB;Jordan Cox~wk~W~~OB;Mitchell Killeen~~L~~RM;Sam Cook~~L~~RM;Shane Snater~~L~~RM;Jamie Porter~~L~~RM;Mackenzie Jones~~L~~RM;Charlie Bennett~~L~~RM
gla|Glamorgan|GLA|sophia-gardens-cardiff|3|Kiran Carlson~~B~~OB;Callum Nicholls~~B~~RM;Jack Hope-Bell~~B~~OB;Sean Dickson~~B~~RM;Asa Tribe~~B~~OB;Eddie Byrom~~B~~LB;Ben Kellaway~~A~~OB;Zain-ul-Hassan~~A~~RM;Dan Douthwaite~~A~~RM;Ben Mike~~A~~RM;Henry Hurle~wk~W~~;Will Smale~wk~W~~;Alex Horton~wk~W~~;Mason Crane~~L~~LB;Andy Gorvin~~L~~RM;Tom Norton~~L~~RM;Connor O'Riordan~~L~~RM;Ned Leonard~~L~~RM;Jamie McIlroy~~L~~RM;Timm van der Gugten~~L~~RM;Romano Franco~~L~~SLA
glo|Gloucestershire|GLO|bristol|3|Cameron Bancroft~~B~~;Jack Taylor~~B~~LB;Dawid Malan~~B~~LB;Joe Phillips~~B~~OB;Tommy Boorman~~B~~OB;Kamran Dhariwal~~B~~;Miles Hammond~~B~~OB;Tom Alsop~~B~~;Graeme van Buuren~~A~~SLA;Ollie Price~~A~~OB;Daz Ahmed~~A~~RM;James Bracey~wk~W~~;Will Williams~~L~~RM;James Hayes~~L~~RM;David Payne~~L~~RM;Aman Rao~~L~~RM;Henry Brookes~~L~~RM;Matthew Taylor~~L~~RM;Alfie Johnson~~L~~OB;Marchant de Lange~~L~~RM;Che Simmons~~L~~RM
ham|Hampshire|HAM|ageas-bowl|3|Joe Weatherley~~B~~OB;James Vince~~B~~RM;Toby Albert~~B~~OB;Fletcha Middleton~~B~~OB;Ben Mayes~~B~~RM;Nick Gubbins~~B~~LB;Jake Lehmann~~B~~SLA;Rishi Patel~~B~~LB;Felix Organ~~A~~OB;Liam Dawson~~A~~SLA;Andrew Neal~~A~~SLA;Tom Prest~~A~~OB;James Fuller~~A~~RM;Fynn Hudson-Prentice~~A~~RM;Saif Zaib~~A~~SLA;Ben Brown~wk~W~~SLA;Chris Wood~~L~~RM;Manny Lumsden~~L~~RM;Scott Currie~~L~~RM;Kyle Abbott~~L~~RM;Eddie Jack~~L~~RM;Sonny Baker~~L~~RM
ken|Kent|KEN|canterbury|3|Ben Compton~~B~~OB;Ekansh Singh~~B~~RM;Tawanda Muyeye~~B~~OB;Zak Crawley~~B~~RM;Sam Northeast~~B~~OB;Daniel Bell-Drummond~~B~~RM;Grant Stewart~~A~~RM;Corey Flintoff~~A~~RM;Olly Curtiss~~A~~RM;Jaydn Denly~~A~~SLA;Sam Billings~wk~W~~;Chris Benjamin~wk~W~~;Ben Dawkins~wk~W~~;Harry Finch~wk~W~~RM;Matt Milnes~~L~~RM;Fred Klaassen~~L~~RM;Jas Singh~~L~~RM;Matt Parkinson~~L~~LB;Matt Quinn~~L~~RM
lan|Lancashire|LAN|old-trafford|3|Keaton Jennings~~B~~RM;Rocky Flintoff~~B~~RM;Marcus Harris~~B~~;Josh Bohannon~~B~~RM;Liam Livingstone~~B~~LB;Michael Jones~~B~~OB;Luke Wells~~A~~LB;George Balderson~~A~~RM;Arav Shetty~~A~~OB;Ben Walkden~~A~~RM;Paul Coughlin~~A~~RM;Chris Green~~A~~OB;Ben Charlesworth~~A~~RM;Jos Buttler~wk~W~~;Phil Salt~wk~W~~OB;Matthew Hurst~wk~W~~;Joe Moores~wk~W~~;Tom Hartley~~L~~SLA;Jack Blatherwick~~L~~RM;Charlie Barnard~~L~~SLA;Tom Bailey~~L~~RM;Tom Aspinwall~~L~~RM;Luke Wood~~L~~RM;Ollie Sutton~~L~~RM;Saqib Mahmood~~L~~RM;Mitchell Stanley~~L~~RM;Ajeet Singh Dale~~L~~RM;Luke Hands~~L~~RM
lei|Leicestershire|LEI|grace-road|3|Sheridon Gumbs~~B~~LB;Sol Budinger~~B~~OB;Hamza Shaikh~~B~~LB;Stephen Eskinazi~~B~~;Peter Handscomb~~B~~;Ben Green~~A~~RM;Ian Holland~~A~~RM;Rehan Ahmed~~A~~LB;Jonny Tattersall~wk~W~~LB;Josh Blake~wk~W~~LB;Josh Hull~~L~~RM;Josh Davey~~L~~RM;Alex Green~~L~~RM;Tom Scriven~~L~~RM
mid|Middlesex|MID|lords|4|Max Holden~~B~~OB;Aaryan Sawant~~B~~OB;Sam Robson~~B~~LB;Ben Geddes~~B~~RM;Ali Orr~~B~~RM;Nathan Fernandes~~A~~SLA;Caleb Falconer~~A~~RM;Josh de Caires~~A~~OB;Ryan Higgins~~A~~RM;Luke Hollman~~A~~LB;Jack Nelson~~A~~LB;Harry Duke~wk~W~~;Joe Cracknell~wk~W~~;Naavya Sharma~~L~~RM;Tom Helm~~L~~RM;Noah Cornwell~~L~~RM;James Feldman~~L~~RM;Sebastian Morgan~~L~~RM;Blake Cullen~~L~~RM;Toby Roland-Jones~~L~~RM;Ishaan Kaushal~~L~~RM;Zafar Gohar~~L~~SLA
nor|Northamptonshire|NOR|northampton|4|Matthew Breetzke~~B~~;George Bartlett~~B~~OB;Louis Kimber~~B~~OB;Stuart van der Merwe~~B~~RM;Ricardo Vasconcelos~~B~~;Nathan McSweeney~~B~~OB;Luke Procter~~A~~RM;James Sales~~A~~RM;David Willey~~A~~RM;Gus Miller~~A~~RM;Calvin Harrison~~A~~LB;Justin Broad~~A~~RM;Lewis McManus~wk~W~~;Harry Conway~~L~~RM;Ben Sanderson~~L~~RM;Dom Leech~~L~~RM;Liam Guthrie~~L~~RM;Tiaan Louw~~L~~RM;Nirvan Ramesh~~L~~OB;Raphy Weatherall~~L~~RM;George Scrimshaw~~L~~RM
not|Nottinghamshire|NOT|trent-bridge|4|Ben Martindale~~B~~RM;Ben Duckett~~B~~;Ben Slater~~B~~RM;Jack Haynes~~B~~OB;Freddie McCann~~B~~OB;George Munsey~~B~~RM;Sam Seecharan~~B~~RM;Haseeb Hameed~~B~~LB;Lyndon James~~A~~RM;Benny Howell~~A~~RM;Joe Pocklington~~A~~SLA;Liam Patterson-White~~A~~SLA;Byron Hatton-Lowe~~A~~RM;Tom Moores~wk~W~~;Joe Clarke~wk~W~~;John Simpson~wk~W~~;Conor McKerr~~L~~RM;Robert Lord~~L~~RM;Mohammad Amir~~L~~RM;Farhan Ahmed~~L~~OB;Olly Stone~~L~~RM;Fergus O'Neill~~L~~RM;Brett Hutton~~L~~RM;Dillon Pennington~~L~~RM;Josh Tongue~~L~~RM;Tom Giles~~L~~RM
som|Somerset|SOM|taunton|4|Fin Hill~~B~~OB;Tom Lammonby~~B~~RM;Will Smeed~~B~~OB;Tom Abell~~B~~RM;Tom Kohler-Cadmore~~B~~OB;Craig Overton~~A~~RM;Josh Thomas~~A~~SLA;Lewis Gregory~~A~~RM;Lewis Goldsworthy~~A~~SLA;Archie Vaughan~~A~~OB;Tom Banton~wk~W~~OB;Thomas Rew~wk~W~~;James Rew~wk~W~~SLA;Alfie Ogborne~~L~~RM;Josh Shaw~~L~~RM;Jake Ball~~L~~RM;Jack Leach~~L~~SLA;JT Langridge~~L~~RM;James Theedom~~L~~RM
sur|Surrey|SUR|oval|4|Rory Burns~~B~~RM;Jason Roy~~B~~RM;Dom Sibley~~B~~LB;Adam Thomas~~B~~RM;Nikhil Gorantla~~B~~RM;Tommy Ealham~~A~~OB;Will Jacks~~A~~OB;Jordan Clark~~A~~RM;Ralphie Albert~~A~~SLA;Ryan Patel~~A~~RM;Dan Lawrence~~A~~OB;Tom Lawes~~A~~RM;Chris Jordan~~A~~RM;Ollie Sykes~~A~~RM;Sam Curran~~A~~RM;Tom Curran~~A~~RM;Ben Foakes~wk~W~~;Jamie Smith~wk~W~~;Ollie Pope~wk~W~~LB;Daniel Worrall~~L~~RM;Matthew Fisher~~L~~RM;Alex French~~L~~RM;Reece Topley~~L~~RM;Nathan Barnwell~~L~~RM;Gus Atkinson~~L~~RM;Yousef Majid~~L~~SLA;Jamie Overton~~L~~RM;Henry Crocombe~~L~~RM;Nathan Sowter~~L~~LB
sus|Sussex|SUS|hove|4|Tom Haines~~B~~RM;Tom Clark~~B~~RM;James Coles~~B~~SLA;Jack Leaning~~B~~OB;Danial Ibrahim~~A~~RM;Joey Evison~~A~~RM;Tom Price~~A~~RM;Oli Carter~wk~W~~;Charlie Tear~wk~W~~;Danny Briggs~~L~~SLA;Tymal Mills~~L~~RM;Jack Carson~~L~~OB;Sean Hunt~~L~~RM;Jofra Archer~~L~~RM;Ollie Robinson~~L~~RM;Dom Goodman~~L~~RM;Keith Dudgeon~~L~~RM;Ari Karvelas~~L~~RM
war|Warwickshire|WAR|edgbaston|5|Zen Malik~~B~~LB;Sam Hain~~B~~OB;Rob Yates~~B~~OB;Vansh Jani~~B~~OB;Jacob Bethell~~A~~SLA;Charlie Taylor~~A~~RM;Chris Woakes~~A~~RM;Ed Barnard~~A~~RM;Dan Mousley~~A~~OB;Kai Smith~wk~W~~;Alex Davies~wk~W~~;George Garton~~L~~RM;Tazeem Ali~~L~~LB;Nathan Gilchrist~~L~~RM;Oliver Hannon-Dalby~~L~~RM;Michael Booth~~L~~RM;Richard Gleeson~~L~~RM;Jordan Thompson~~L~~RM;Ethan Bamber~~L~~RM
wor|Worcestershire|WOR|new-road|3|Jake Libby~~B~~OB;Rehaan Edavalath~~B~~OB;Isaac Mohammed~~B~~RM;Dan Lategan~~B~~OB;Kashif Ali~~B~~LB;Adam Hose~~B~~RM;George Thomas~~B~~RM;Matthew Waite~~A~~RM;Tom Taylor~~A~~RM;Brett D'Oliveira~~A~~LB;Ethan Brookes~~A~~RM;Gareth Roderick~wk~W~~RM;Henry Cullen~wk~W~~;Fateh Singh~~L~~OB;Ben Gibbon~~L~~RM;Usama Mir~~L~~LB;Jack Home~~L~~RM;Adam Finch~~L~~RM;Ben Allison~~L~~RM
yor|Yorkshire|YOR|headingley|4|Sam Whiteman~~B~~;Adam Lyth~~B~~OB;James Wharton~~B~~OB;Will Bennison~~B~~LB;Joe Root~~B~~OB;Will Luxton~~B~~RM;Harry Brook~~B~~RM;Moeen Ali~~A~~OB;Dom Bess~~A~~OB;George Hill~~A~~RM;Matt Revis~~A~~RM;Finlay Bean~wk~W~~;Jonny Bairstow~wk~W~~RM;Adil Rashid~~L~~LB;Jafer Chohan~~L~~LB;Isaac Bepey~~L~~RM;Ben Coad~~L~~RM;Jack White~~L~~RM;Ben Cliff~~L~~RM;Andrew Tye~~L~~RM;Matthew Firbank~~L~~RM;James Taylor~~L~~RM
"""

    // id | name | city | pitch (B batting, G green, D dusty, O bowling) | boundary (S, M, L) | altitude in metres
    private const val VENUE_TEXT = """
derby|County Ground|Derby|G|M|60
chelmsford|County Ground|Chelmsford|B|S|30
bristol|Bristol County Ground|Bristol|B|S|20
canterbury|St Lawrence Ground|Canterbury|B|M|40
grace-road|Grace Road|Leicester|G|M|70
northampton|County Ground|Northampton|B|S|100
hove|1st Central County Ground|Hove|B|M|15
taunton|Cooper Associates County Ground|Taunton|B|S|25
new-road|New Road|Worcester|G|M|20
"""
}

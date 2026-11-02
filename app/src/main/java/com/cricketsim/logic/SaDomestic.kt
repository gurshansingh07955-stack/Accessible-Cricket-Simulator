package com.cricketsim.logic

/**
 * South African domestic cricket 2026-27, part one: the CSA 4-Day Series Division 1 (first-class), the CSA Pro50 Cup (Division 1 one-day)
 * and the CSA Pro20 Cup (the new 16-team T20 cup). Team ids are "sa_xx". Division 2 competitions come in part two. See SOUTH_AFRICA_DOMESTIC.md.
 *
 * Fixtures: Cricinfo and CSA (31 October 2026), in the published order. The season is already under way in real life (the 4-Day Series began
 * on 10 September, the Pro20 Cup on 25 September); the game starts all three from the beginning and simulates every result.
 *
 *  - 4-Day Series Division 1: eight teams, 40 matches (each plays ten), then a five-day Final between the top two at the top side's home
 *    ground. Points (CSA): 16 for a win, 8 for a tie, 6 for a draw, plus batting bonus (1 at 150 runs, then 0.02 per run) and bowling bonus
 *    (1 for 3 wickets, then 1 for every two more) from the first innings, kept in hundredths. The real rule counts only the first 100 overs.
 *  - Pro50 Cup: eight teams, 28 matches (each plays seven), 4 points a win and 2 for a tie or no result (the real bonus point is not
 *    built), then a Final between the top two (assumption: the published list has no Final yet).
 *  - Pro20 Cup: 16 teams in two pools of eight (56 matches, everyone plays the other seven once), the top four of each pool go to the
 *    Super Eights where every qualifier from one pool plays every qualifier from the other (16 matches), the top four of the Super Eights
 *    play the semi-finals (1 v 4, 2 v 3) and the Final at Newlands. 4 points a win and 2 for a tie or no result (assumption).
 *
 * Squads: the clubs' contracted squads for 2026-27 (Club Cricket SA, 20 October 2026), including the national-contract players. A player
 * already in the game keeps his ratings; the others get ESTIMATED ratings. The "SA Emerging" squad is not published: it is made from the
 * young high-performance-contract players.
 */
object SaDomestic {
    const val FOURDAY_ID = "sa4d_2026_27"
    const val FOURDAY_NAME = "CSA 4-Day Series Division 1 2026-27"
    const val ODC_ID = "sa50_2026_27"
    const val ODC_NAME = "CSA Pro50 Cup 2026-27"
    const val T20_ID = "pro20_2026_27"
    const val T20_NAME = "CSA Pro20 Cup 2026-27"

    private const val FINAL = "Final"
    private const val POOL_A = "Pool A"
    private const val POOL_B = "Pool B"
    private const val SUPER8 = "Super Eights"
    private const val SF1 = "Semi-final 1"
    private const val SF2 = "Semi-final 2"
    private const val FINAL_GROUND = "newlands"

    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN

    fun isFourDay(id: String?): Boolean = id == FOURDAY_ID
    fun isOdc(id: String?): Boolean = id == ODC_ID
    fun isT20(id: String?): Boolean = id == T20_ID
    fun isSa(id: String?): Boolean = id == FOURDAY_ID || id == ODC_ID || id == T20_ID

    /** Four days in the league, five in the Final. */
    fun daysFor(stage: String): Int = if (stage == FINAL) 5 else 4

    /** Pro50 Cup: 50 overs, a tied league match is shared. Pro20 Cup: 20 overs, a tie goes to a Super Over. No DRS. */
    fun rulesFor(id: String?, stage: String?): MatchRules? {
        val key = when (id) {
            ODC_ID -> "ODI"
            T20_ID -> "T20"
            else -> return null
        }
        val base = TournamentRules.rulesForFormat(key) ?: return null
        return base.copy(drsReviews = 0, noSuperOver = id == ODC_ID && stage != FINAL)
    }

    fun introOf(id: String?): String = when (id) {
        FOURDAY_ID -> "Sixteen points for a win, eight for a tie, six for a draw, plus batting and bowling bonus points from the first innings. " +
            "After the 40 league matches the top two play a five-day Final at the home ground of the top side."
        ODC_ID -> "Four points for a win, two each for a tie or no result. After the 28 league matches the top two play the Final."
        else -> "Four points for a win, two each for a tie or no result. The top four of each pool go to the Super Eights, where every qualifier " +
            "from one pool plays every qualifier from the other. The top four of the Super Eights play the semi-finals and the Final at Newlands."
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
            TeamDef("sa_" + p[0], p[1], p[2], p[3], p[4].toInt(), p[5].split(";").map { parseEntry(it) })
        }
    }

    fun isTeam(teamId: String): Boolean = DEFS.any { it.id == teamId }

    fun shortNameOrNull(teamId: String): String? = DEFS.find { it.id == teamId }?.shortName

    fun homeGround(teamId: String): String = DEFS.find { it.id == teamId }?.home ?: "wanderers"

    private val GROUND = mapOf(
        "nl" to "newlands", "bf" to "mangaung-oval", "wd" to "wanderers", "ct" to "supersport-park", "pl" to "boland-park",
        "gq" to "st-georges-park", "pt" to "tuks-oval", "db" to "kingsmead", "pk" to "senwes-park-potchefstroom", "km" to "diamond-oval",
        "wr" to "white-river", "kg" to "buffalo-park-eastlondon", "bn" to "willowmoore-park", "od" to "oudtshoorn-ground",
        "pb" to "pietersburg-oval", "pm" to "city-oval"
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

    /** The grounds the game did not have already (estimated ratings). Newlands, the Wanderers, SuperSport Park, Kingsmead and others are in StadiumData. */
    val stadiums: List<Stadium> by lazy {
        VENUE_TEXT.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val p = line.split("|")
            val pitch = pitchOf(p[3][0])
            Stadium(
                id = p[0], name = p[1], city = p[2], country = "South Africa", homeTeamIds = emptyList(),
                pitchType = pitch, pitchDescription = pitchText(pitch), boundarySize = boundaryOf(p[4][0]),
                altitudeM = p[5].toInt(), dewFactor = 0.1, avgT20FirstInningsScore = 165,
                climate = StadiumClimate(0.2, 50, 24, 12, "Warm, mostly dry conditions.")
            )
        }
    }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    fun stadiumsFor(id: String): List<Stadium> =
        (buildSchedule(id).map { it.stadiumId } + (if (id == T20_ID) listOf(FINAL_GROUND) else DEFS.map { it.home }))
            .distinct().mapNotNull { CplData.stadium(it) }

    // ---------------------------------------------------------------- players

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' }

    private fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)

    private fun jit(name: String, salt: Int, range: Int): Int {
        var h = salt * 7919
        for (c in name) h = h * 31 + c.code
        return (h and 0x7fffffff) % (2 * range + 1) - range
    }

    private val SHORT_FIRST = mapOf(
        "mitchell" to "mitch", "matthew" to "matt", "steven" to "steve", "joshua" to "josh", "nicholas" to "nic",
        "benjamin" to "ben", "samuel" to "sam", "alexander" to "alex", "thomas" to "tom", "william" to "will",
        "timothy" to "tim", "daniel" to "dan", "christopher" to "chris", "jacob" to "jake"
    )

    private fun lookup(known: Map<String, Player>, name: String): Player? {
        known[norm(name)]?.let { return it }
        val parts = name.split(" ")
        if (parts.size >= 2) {
            val first = norm(parts[0])
            val rest = parts.drop(1).joinToString("") { norm(it) }
            SHORT_FIRST[first]?.let { s -> known[s + rest]?.let { return it } }
            SHORT_FIRST.entries.firstOrNull { it.value == first }?.let { e -> known[e.key + rest]?.let { return it } }
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

    /** The 16 teams. A player already in the game is copied with his ratings; the rest get estimates by the team's strength. */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val known = HashMap<String, Player>()
        fun addAll(players: List<Player>) {
            for (p in players) known.putIfAbsent(norm(p.name), p)
        }
        addAll(CricketData.getAllTeams().flatMap { it.players })
        addAll(Sa20.buildTeams().first.flatMap { it.players })
        addAll(CplData.buildTeams().first.flatMap { it.players })
        addAll(AbuDhabiT10.buildTeams().first.flatMap { it.players })
        addAll(LankaPremierLeague.buildTeams().first.flatMap { it.players })
        addAll(InternationalLeagueT20.buildTeams().first.flatMap { it.players })
        addAll(BigBashLeague.buildTeams().first.flatMap { it.players })
        addAll(MajorLeagueCricket.buildTeams().first.flatMap { it.players })
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

    /** The published matches in the published order. Later stages are added by [advance]. */
    fun buildSchedule(id: String): List<TournamentFixture> = when (id) {
        FOURDAY_ID -> rows(FOURDAY_TEXT).mapIndexed { i, p ->
            TournamentFixture(i, i + 1, CplData.LEAGUE, "sa_" + p[0], "sa_" + p[1], GROUND.getValue(p[2]))
        }
        ODC_ID -> rows(PRO50_TEXT).mapIndexed { i, p ->
            TournamentFixture(i, i + 1, CplData.LEAGUE, "sa_" + p[0], "sa_" + p[1], homeGround("sa_" + p[0]))
        }
        else -> rows(T20_TEXT).mapIndexed { i, p ->
            TournamentFixture(i, i + 1, if (p[0] == "A") POOL_A else POOL_B, "sa_" + p[1], "sa_" + p[2], GROUND.getValue(p[3]))
        }
    }

    fun newTournament(id: String, userTeamId: String, built: Pair<List<Team>, Map<String, SquadTag>>): TournamentState =
        TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = id,
            name = when (id) { FOURDAY_ID -> FOURDAY_NAME; ODC_ID -> ODC_NAME; else -> T20_NAME },
            userTeamId = userTeamId,
            teams = built.first,
            tags = built.second,
            fixtures = buildSchedule(id),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )

    // ---------------------------------------------------------------- 4-Day Series points and table (hundredths of a point)

    /** 1 point at 150 runs, then 0.02 for each run more, from the first innings. */
    private fun battingBonus(runs: Int): Int = if (runs < 150) 0 else 100 + 2 * (runs - 150)

    /** 1 point for 3 wickets, then 1 for every two more (5, 7, 9), from the opposition's first innings. */
    private fun bowlingBonus(wickets: Int): Int = if (wickets < 3) 0 else 100 * (1 + (wickets - 3) / 2)

    fun points(s: MatchState, teamId: String): Int {
        val base = when (FirstClass.kind(s)) {
            "WIN" -> if (s.winnerId == teamId) 1600 else 0
            "TIE" -> 800
            else -> 600
        }
        val list = s.test?.completed ?: return base
        val mine = list.firstOrNull { it.data.battingTeamId == teamId }
        val theirs = list.firstOrNull { it.data.battingTeamId != teamId }
        return base + (if (mine != null) battingBonus(mine.score.runs) else 0) + (if (theirs != null) bowlingBonus(theirs.score.wickets) else 0)
    }

    fun format(hundredths: Int): String = "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"

    fun pointsText(home: Team, away: Team, homePoints: Int, awayPoints: Int): String =
        " Points: ${CplData.shortName(home.id)} ${format(homePoints)}, ${CplData.shortName(away.id)} ${format(awayPoints)}."

    /** The 4-Day Series table: points, then wins, then runs per wicket scored over conceded. */
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

    // ---------------------------------------------------------------- limited-overs tables and progress

    private fun teamsOfStage(state: TournamentState, stage: String): Set<String> =
        state.fixtures.filter { it.stage == stage }.flatMap { listOfNotNull(it.homeId, it.awayId) }.toSet()

    /** A stage's table with 4 points for a win and 2 for a tie or no result. */
    fun stageTable(state: TournamentState, stage: String): List<StandingRow> {
        val ids = teamsOfStage(state, stage)
        return CplData.standings(state, stage).filter { it.team.id in ids }.map { it.copy(points = it.won * 4 + it.noResult * 2) }
    }

    /** The Pro20 Cup's tables: the two pools, then the Super Eights once it exists. */
    fun t20Tables(state: TournamentState): List<Pair<String, List<StandingRow>>> =
        listOf(POOL_A, POOL_B, SUPER8).filter { teamsOfStage(state, it).isNotEmpty() }.map { it to stageTable(state, it) }

    private fun complete(fx: List<TournamentFixture>, stage: String): Boolean {
        val l = fx.filter { it.stage == stage }
        return l.isNotEmpty() && l.all { it.result != null }
    }

    private fun winnerOf(fx: List<TournamentFixture>, stage: String): String? =
        fx.firstOrNull { it.stage == stage }?.result?.winnerId

    private fun advanceT20(state: TournamentState): TournamentState {
        var fx = state.fixtures
        fun add(stage: String, home: String?, away: String?, ground: String) {
            if (home == null || away == null) return
            fx = fx + TournamentFixture(fx.size, fx.size + 1, stage, home, away, ground)
        }
        if (complete(fx, POOL_A) && complete(fx, POOL_B) && fx.none { it.stage == SUPER8 }) {
            val a = stageTable(state.copy(fixtures = fx), POOL_A).map { it.team.id }.take(4)
            val b = stageTable(state.copy(fixtures = fx), POOL_B).map { it.team.id }.take(4)
            if (a.size == 4 && b.size == 4) {
                for (x in a) for (y in b) add(SUPER8, x, y, homeGround(x))
            }
        }
        if (complete(fx, SUPER8) && fx.none { it.stage == SF1 }) {
            val top = stageTable(state.copy(fixtures = fx), SUPER8).map { it.team.id }.take(4)
            if (top.size == 4) {
                add(SF1, top[0], top[3], FINAL_GROUND)
                add(SF2, top[1], top[2], FINAL_GROUND)
            }
        }
        if (fx.none { it.stage == FINAL }) add(FINAL, winnerOf(fx, SF1), winnerOf(fx, SF2), FINAL_GROUND)
        return state.copy(fixtures = fx)
    }

    /** Adds the Final (or, in the Pro20 Cup, the Super Eights and the knockouts) as the teams become known. */
    fun advance(state: TournamentState): TournamentState {
        if (state.tournamentId == T20_ID) return advanceT20(state)
        val fx = state.fixtures
        val league = fx.filter { it.stage == CplData.LEAGUE }
        if (league.isEmpty() || league.any { it.result == null } || fx.any { it.stage == FINAL }) return state
        val top = (if (state.tournamentId == FOURDAY_ID) fourDayTable(state).map { it.team.id } else stageTable(state, CplData.LEAGUE).map { it.team.id }).take(2)
        if (top.size < 2) return state
        return state.copy(fixtures = fx + TournamentFixture(fx.size, fx.size + 1, FINAL, top[0], top[1], homeGround(top[0])))
    }

    // ---------------------------------------------------------------- the data

    // code | name | short | home ground | strength tier (1-5) | squad: name ~ flags ~ role (B batter, A all-rounder, W keeper, L bowler, - unknown) ~ - ~ bowling style
    private const val TEAM_TEXT = """
tit|Titans|TIT|supersport-park|5|Rivaldo Moonsamy~wk~W~~;Keegan Petersen~~B~~;Tshepang Dithole~~B~~;Evan Jones~~A~~RM;Andile Phehlukwayo~~A~~RM;Sinethemba Qeshile~wk~W~~;Duan Jansen~~A~~RM;Dayyaan Galiem~~A~~OB;Keagan Lion-Cachet~~L~~RM;Tsepo Ndwandwa~~A~~RM;Lhuan-dre Pretorius~wk~W~~;Junior Dala~~L~~RM;Donovan Ferreira~~B~~;Eathan Bosch~~A~~RM;Leus du Plooy~~B~~SLA;Aphiwe Mnyanda~~L~~RM;Beyers Swanepoel~~A~~RM;Simon Harmer~~L~~OB;Corbin Bosch~~A~~RM;Aiden Markram~~B~~OB;Lungi Ngidi~~L~~RM;Dewald Brevis~~B~~LB;Marco Jansen~~A~~RM
lio|Lions|LIO|wanderers|5|Bjorn Fortuin~~A~~SLA;Reeza Hendricks~~B~~;Rassie van der Dussen~~B~~;Dominic Hendricks~~B~~;Lutho Sipamla~~L~~RM;Mitchell van Buuren~~A~~SLA;Codi Yusuf~~L~~RM;Delano Potgieter~~A~~RM;Connor Esterhuizen~wk~W~~;Zubayr Hamza~~B~~;Nqaba Peter~~L~~RM;Richard Seletswane~~B~~;Wandile Makwetu~~B~~;Siya Plaatjie~~-~~;Tiaan Brits~~B~~;Gerald Coetzee~~L~~RM;Quinton de Kock~wk~W~~;Kagiso Rabada~~L~~RM;Ryan Rickelton~wk~W~~;Temba Bavuma~~B~~;Kwena Maphaka~~L~~RM;Wiaan Mulder~~A~~RM
dol|Dolphins|DOL|kingsmead|4|Jason Smith~~B~~;Prenelan Subrayen~~L~~OB;Marques Ackerman~~B~~;Okuhle Cele~~L~~SLA;Nqobani Mokoena~~A~~RM;Khaya Zondo~~B~~OB;Grant Roelofsen~wk~W~~;Josh Richards~~B~~;Andile Simelane~~L~~RM;Keith Dudgeon~~L~~RM;Bryce Parsons~~A~~RM;Hanu Viljoen~~-~~;Romashan Pillay~~-~~;Tristan Luus~~L~~RM;David Miller~~B~~;Bayanda Majola~~L~~RM;Semal Pillay~~-~~;Keshav Maharaj~~L~~SLA;Ottniel Baartman~~L~~RM;Ntando Zuma~~B~~;Sean Gilson~~-~~
war|Warriors|WAR|st-georges-park|4|Thomas Kaber~wk~W~~;JP King~~B~~;Patrick Kruger~~A~~RM;Matthew de Villiers~~-~~;Beuran Hendricks~~L~~RM;Matthew Boast~~A~~RM;Wesley Bedja~~L~~RM;Modiri Litheko~~-~~;Sibonelo Makhanya~~L~~RM;Jordan Hermann~~B~~;Jean du Plessis~~B~~;Duanne Olivier~~L~~RM;Andile Mokgakane~~B~~;Gideon Peters~~L~~RM;Kerwin Mungroo~~L~~SLA;Muhammad Manack~~-~~;CJ King~~L~~RM;Tladi Bokako~~L~~RM;Matthew Breetzke~~B~~;Tristan Stubbs~~B~~OB;Senuran Muthusamy~~A~~SLA
kni|Knights|KNI|mangaung-oval|3|Jacques Snyman~~B~~;Isaac Dikgale~wk~W~~;Gihahn Cloete~~L~~RM;Cole Abrahams~~-~~;Ruan Haasbroek~~-~~;Tiaan van Vuuren~~L~~RM;Dane Piedt~~L~~OB;Malusi Siboto~~L~~RM;Ruben Maree~~A~~RM;George van Heerden~~B~~;Wian Ruthven~~B~~;Lesego Senokwane~~B~~;Emmanuel Motswiri~~A~~RM;Ntando Soni~~L~~RM;Nico van Zyl~~L~~RM;Junaid Dawood~~L~~LB;Seth Fledermaus~~-~~;Mbongi Mhlanga~~-~~;Jono Bird~~B~~;Ross Boast~~-~~
wpr|Western Province|WPR|newlands|4|Dane Paterson~~L~~RM;Daniel Smith~wk~W~~;Eddie Moore~~B~~SLA;George Linde~~A~~SLA;Jiveshan Pillay~~B~~;Josh van Heerden~~B~~;Josh Breed~~A~~SLC;Kyle Simmonds~~A~~SLA;Mihlali Mpongwana~~A~~RM;Mbulelo Dube~~L~~RM;Nandre Burger~~L~~RM;Oliver Whitehead~~A~~RM;Raeeq Daniels~~L~~RM;Tshepo Moreki~~L~~RM;Valentine Kitime~~B~~RM;Bongile Mfunelwa~wk~W~~;Kashief Joseph~~B~~;David Bedingham~~B~~SLA;Kyle Verreynne~wk~W~~;Tony de Zorzi~~B~~SLA
bol|Boland|BOL|boland-park|3|Lizaad Williams~~L~~RM;Neil Brand~~A~~SLA;Glenton Stuurman~~L~~RM;Gavin Kaplan~~B~~SLA;Jhedli van Briesies~wk~W~~;Akhona Mnyaka~~L~~RM;Blayde Capell~wk~W~~;Jevano Baron~~L~~RM;Ethan Cunningham~~B~~RM;Imraan Manack~~L~~SLA;Ferisco Adams~~A~~RM;Lehan Botha~~A~~RM;Siyabonga Mahima~~L~~SLA;Nathan Jacobs~~A~~RM;Ruan Terblanche~~B~~SLA;Kaden Solomons~~-~~;Yves Kamanzi~~L~~SLA
nwd|North West|NWD|senwes-park-potchefstroom|3|Wihan Lubbe~~A~~SLA;Raynard van Tonder~~B~~SLC;Daryn Dupavillon~~L~~RM;Rubin Hermann~wk~W~~;Dian Forrester~~A~~RM;Ruan de Swardt~~A~~RM;Ludwich Schuld~~B~~;Lesiba Ngoepe~~B~~SLA;Onke Nyaku~~A~~RM;Alfred Mothoa~~L~~RM;Caleb Seleka~~L~~SLC;Mthiwekhaya Nabe~~L~~RM;Diego Rosier~~B~~SLC;Jade de Klerk~~A~~SLA;Meeka-eel Prince~wk~W~~;Juan James~~A~~SLA;Paul James~~B~~RM;Nealen van Heerden~~L~~RM;Lutendo Tsanwani~~L~~RM;Callie Fryer~~L~~RM
bor|Border|BOR|buffalo-park-eastlondon|2|Nathan Roux~wk~W~~;Jerome Bossr~~B~~RM;Lihle Sizani~~A~~SLA;Christiaan du Toit~~B~~SLC;Jason Niemand~~B~~;Mncedisi Malika~wk~W~~;Michael Copeland~~A~~;Kgaudisi Molefe~~L~~;Hardus Coetzer~~A~~RM;Thando Ntini~~A~~;Sithembile Langa~~L~~;Riley Miller~~B~~RM;Matthew Fourie~~L~~RM;Alindile Mhletywa~~A~~RM;Thozama Totana~~L~~
lim|Limpopo|LIM|pietersburg-oval|2|Marcello Piedt~~L~~RM;Ernest Kemm~~A~~SLA;Abdallah Bayoumy~~A~~RM;Tylor Trenoweth~~B~~;Don Radebe~~L~~RM;Atwell Mokgoloboto~~L~~SLA;Liam Peters~~B~~RM;CP Klijnhans~~B~~;Maphekgola Pootona~~L~~RM;Shelton Ngobeni~~B~~;Kyle Landsberg~~-~~;Lesego Kokohlabana~~-~~;Irvin Modimokoane~~L~~RM
tus|KZN Inland|KZN|city-oval|2|Michael Erlank~~A~~SLA;Malcolm Nofal~~A~~SLA;Ludwig Kaestner~~B~~RM;Cameron Shekleton~wk~W~~;Jack Lees~~B~~SLA;Bamanye Xenxe~~A~~RM;Chad Laycock~~A~~RM;Liyema Waqu~~L~~RM;Thabiso Ndlela~~L~~RM;Ziyaad Abrahams~~L~~RM;Jesse Albanie~~L~~RM;Kagiso Rapulana~~B~~RM;Rowan Rajah~~L~~RM;Muhammed Bulbulia~~B~~;Parth Patel~~L~~SLC;Joshua van Biljon~wk~W~~RM;Ismaeel Omar~~B~~;Daelynn Fynn~~A~~SLA;Jared Meiring~~A~~RM;Matthew Urquhart~~B~~SLA
eas|Easterns|EAS|willowmoore-park|3|Ronan Herrmann~~B~~;Merrick Brett~~L~~RM;Aryan Gopalan~~L~~SLA;Chad Classen~~L~~RM;Jurie Snyman~~A~~SLA;Tumelo Simelane~~L~~SLA;Amaan Khan~~L~~RM;Dewan Marais~~B~~;Martin Khumalo~~L~~RM;Chris Britz~~B~~;Kabelo Sekhukhune~~B~~;Brayden Hicks~~B~~SLA;Thabiso Lekhooana~~L~~RM;Sabelo Mabanga~~A~~SLA;Leander Lubbe~~-~~
swd|South Western Districts|SWD|oudtshoorn-ground|2|Liam Alder~~L~~SLA;Banele Cele~~L~~RM;Beni Hansen~wk~W~~;Zack Momberg~~L~~RM;Pheko Moletsane~~A~~SLA;Nathan Engelbrecht~wk~W~~SLA;Yaseen Valli~~B~~SLA;Janneman Malan~~B~~SLC;Slade van Staden~wk~W~~SLC;Leo Sadler~~A~~RM;Modise Maloka~~L~~RM;Enathi Kitshini~~L~~SLA;Nipho Mpungose~~L~~RM;Jade Richter~~L~~RM
mpu|Mpumalanga|MPU|white-river|2|Yassar Cook~~-~~;Muhammed Mayet~~-~~;Zakir Kathrada~~-~~;Tetelo Maphaka~~-~~;Thobile Hlatukha~~-~~;Karabo Magotsi~~-~~;Jon Hinrichsen~~-~~;Garnett Tarr~~-~~;Bryn Brokensha~~-~~;Ricus Kramm~~-~~;Benjamin van Niekerk~~-~~
nca|Northern Cape|NCA|diamond-oval|2|Steve Stolk~~B~~;Odirile Modimokoane~~L~~SLA;Kyle Jacobs~~A~~SLC;Charl Prinsloo~~A~~RM;Nonelela Yikha~~B~~;GJ Maree~~B~~;Johan van Dyk~~L~~RM;Tshepo Ntuli~~L~~SLA;Blake Simpson~~A~~SLC;Romano Terblanche~~A~~RM;Tahir Isaacs~~B~~RM;Tristan Janse van Rensburg~~B~~SLA;Liam Doherty~~B~~RM;Orapeleng Motlhoaring~~A~~RM;Ulrich Haasbroek~~B~~;Ruan van Rooyen~~A~~SLA;Hernus Marais~~L~~RM;Abel Mokwena~~B~~SLA;Moegamat Alexander~~B~~SLA;Rowan Bezuidenhout~~L~~RM
sae|SA Emerging|SAE|tuks-oval|2|Jorich van Schalkwyk~~-~~;Lethabo Phahlamohlaka~~-~~;Jarren Bacher~~-~~;Kagiso Ngwenya~~-~~;Roelof van der Merwe~~-~~;Divan de Villiers~~-~~;Esosa Aihevba~~-~~;Janco Smit~~L~~RM;Schalk Engelbrecht~~-~~;Gomolemo Phiri~~-~~;Bandile Mbatha~~-~~;Tim Saulez~~-~~;Jason Raubenheimer~~-~~;Jordan Morris~~-~~;Eathan Frosler~~-~~
"""

    // id | name | city | pitch (B batting, G green, D dusty, O bowling) | boundary (S, M, L) | altitude in metres
    private const val VENUE_TEXT = """
mangaung-oval|Mangaung Oval|Bloemfontein|B|M|1400
tuks-oval|LC de Villiers Oval|Pretoria|B|S|1330
white-river|White River Cricket Ground|White River|B|S|900
willowmoore-park|Willowmoore Park|Benoni|B|M|1600
oudtshoorn-ground|Recreation Ground|Oudtshoorn|B|M|330
pietersburg-oval|Polokwane Cricket Club Oval|Polokwane|B|M|1230
city-oval|City Oval|Pietermaritzburg|B|S|650
"""

    // home team | visiting team | ground, in the published order
    private const val FOURDAY_TEXT = """
wpr|war|nl
kni|nwd|bf
lio|bol|wd
tit|dol|ct
bol|nwd|pl
wpr|dol|nl
kni|lio|bf
war|tit|gq
tit|war|pt
dol|wpr|db
kni|bol|bf
nwd|lio|pk
bol|kni|pl
wpr|tit|nl
war|dol|gq
lio|nwd|wd
dol|bol|db
kni|war|bf
tit|lio|ct
nwd|wpr|pk
lio|war|wd
bol|lio|pl
war|wpr|gq
dol|tit|db
nwd|kni|pk
bol|wpr|pl
kni|tit|bf
nwd|dol|pk
wpr|kni|nl
war|bol|gq
lio|dol|wd
tit|nwd|ct
bol|tit|pl
wpr|lio|nl
war|nwd|gq
dol|kni|db
dol|war|db
lio|kni|wd
tit|wpr|ct
nwd|bol|pk
"""

    // home team | visiting team (played at the home side's ground)
    private const val PRO50_TEXT = """
kni|bol
nwd|war
wpr|lio
tit|dol
bol|wpr
war|lio
kni|tit
nwd|dol
bol|tit
dol|kni
war|wpr
lio|nwd
wpr|tit
dol|war
lio|bol
nwd|kni
wpr|dol
lio|kni
war|bol
nwd|tit
bol|nwd
dol|lio
kni|wpr
tit|war
wpr|nwd
tit|lio
bol|dol
kni|war
"""

    // pool (A or B) | first-named team | second-named team | ground, in the published order
    private const val T20_TEXT = """
B|war|nwd|ct
A|wpr|tit|ct
B|nca|bor|km
B|lio|dol|db
A|mpu|tit|wr
A|kni|wpr|nl
B|bor|nwd|kg
A|sae|tus|pt
B|war|lim|gq
A|kni|bol|pl
B|dol|nca|db
A|eas|mpu|bn
B|swd|bor|od
A|mpu|tus|wr
A|tit|kni|bf
B|lio|nwd|wd
B|lim|dol|pb
A|bol|tus|pm
A|sae|wpr|nl
B|war|lio|gq
B|lim|swd|pb
A|eas|wpr|nl
B|lio|swd|od
A|kni|tus|bf
A|sae|tit|ct
A|bol|eas|pl
B|nca|war|gq
B|bor|dol|kg
A|wpr|mpu|wr
B|nwd|swd|pk
A|sae|bol|ct
A|tit|eas|ct
B|nca|lim|km
A|tit|tus|pm
B|lio|bor|wd
B|nwd|dol|pk
A|bol|wpr|nl
A|eas|kni|bf
B|swd|war|od
B|bor|lim|pb
A|bol|mpu|pl
B|nca|lio|wd
A|kni|sae|pt
B|dol|war|db
B|nca|swd|km
B|lim|nwd|pk
A|sae|mpu|wr
A|eas|tus|bn
B|swd|dol|db
B|lim|lio|wd
B|war|bor|kg
A|kni|mpu|bf
A|tit|bol|pl
B|nca|nwd|pk
A|tus|wpr|pm
A|sae|eas|bn
"""
}

package com.cricketsim.logic

/**
 * New Zealand domestic cricket 2026-27: the Plunket Shield (first-class), the Ford Trophy (50 overs) and the Super Smash (T20), played
 * by the six major associations. Team ids are "nz_xx". See NEW_ZEALAND_DOMESTIC.md.
 *
 * Fixtures: the real published lists (NZC, 17 August, 1 September and 12 October 2026), in the real order. The Ford Trophy began on
 * 28 October, so a few of its matches have been played in real life; the game starts all three from the beginning and simulates every
 * result.
 *
 *  - Plunket Shield: eight rounds (24 matches, each side plays eight), no final: the table decides the champion. Points (NZC): 12 for a
 *    win, 6 for a tie, 0 for a draw or a loss, plus batting bonus (1 point each at 200, 250, 300, 350 runs) and bowling bonus (1 point each
 *    for 3, 5, 7, 9 wickets) from each side's first innings. The real rule counts only the first 110 overs; the game counts the whole
 *    innings. Order: points, wins, net runs per wicket.
 *  - Ford Trophy: ten rounds (30 matches), then an Elimination Final (2nd v 3rd) and the Grand Final (1st v the winner), both at the Basin
 *    Reserve. 4 points a win, 2 for a tie or no result (the real bonus point is not built); a tied league match is shared.
 *  - Super Smash: ten rounds (30 matches, each plays the others twice), then the Elimination Final (2nd v 3rd) and the Final at Kennards
 *    Hire Community Oval (Auckland), the last Super Smash before the new NZ20. 4 points a win, 2 for a tie or no result (assumption);
 *    a tie goes to a Super Over.
 *
 * Squads: the 2026-27 association contracts (NZC, 9 July 2026) and the BLACKCAPS on central contracts, each placed with his association.
 * A player already in the game keeps his ratings; the others get ESTIMATED ratings.
 */
object NzDomestic {
    const val PLUNKET_ID = "plunket_2026_27"
    const val PLUNKET_NAME = "Plunket Shield 2026-27"
    const val FORD_ID = "ford_2026_27"
    const val FORD_NAME = "Ford Trophy 2026-27"
    const val SMASH_ID = "smash_2026_27"
    const val SMASH_NAME = "Super Smash 2026-27"
    const val DAYS = 4

    private const val ELIM = "Elimination Final"
    private const val FINAL = "Final"
    private const val FORD_FINALS_GROUND = "basin-reserve"
    private const val SMASH_FINALS_GROUND = "kennards-oval"

    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN

    fun isPlunket(id: String?): Boolean = id == PLUNKET_ID
    fun isFord(id: String?): Boolean = id == FORD_ID
    fun isSmash(id: String?): Boolean = id == SMASH_ID
    fun isNz(id: String?): Boolean = id == PLUNKET_ID || id == FORD_ID || id == SMASH_ID

    /** The Ford Trophy (50 overs; a tied league match is shared) and the Super Smash (20 overs). No DRS. */
    fun rulesFor(id: String?, stage: String?): MatchRules? {
        val key = when (id) {
            FORD_ID -> "ODI"
            SMASH_ID -> "T20"
            else -> return null
        }
        val base = TournamentRules.rulesForFormat(key) ?: return null
        return base.copy(drsReviews = 0, noSuperOver = id == FORD_ID && stage != ELIM && stage != FINAL)
    }

    fun introOf(id: String?): String = when (id) {
        PLUNKET_ID -> "Twelve points for a win, six for a tie, none for a draw, plus batting bonus points (200, 250, 300, 350 runs) and bowling bonus points " +
            "(3, 5, 7, 9 wickets) from the first innings. Eight rounds, no final: the top of the table are the champions."
        FORD_ID -> "Four points for a win, two each for a tie or no result. The top side goes straight to the Grand Final; the second and third play an " +
            "Elimination Final, and its winner meets the top side in the Grand Final at the Basin Reserve."
        else -> "Four points for a win, two each for a tie or no result. The second and third sides play an Elimination Final, and its winner meets the top " +
            "side in the Final at Kennards Hire Community Oval in Auckland."
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
            TeamDef("nz_" + p[0], p[1], p[2], p[3], p[4].toInt(), p[5].split(";").map { parseEntry(it) })
        }
    }

    fun isTeam(teamId: String): Boolean = DEFS.any { it.id == teamId }

    fun shortNameOrNull(teamId: String): String? = DEFS.find { it.id == teamId }?.shortName

    fun homeGround(teamId: String): String = DEFS.find { it.id == teamId }?.home ?: "basin-reserve"

    private val GROUND = mapOf(
        "sd" to "seddon-park", "fz" to "fitzherbert-park", "uo" to "university-oval", "by" to "bay-oval", "ka" to "kennards-oval",
        "mc" to "mclean-park-napier", "ho" to "hagley-oval", "br" to "basin-reserve", "cm" to "colin-maiden-park", "sx" to "saxton-oval",
        "ck" to "cobham-oval", "pk" to "pukekura-park", "qt" to "sir-john-davies-oval", "iv" to "queens-park-invercargill", "ml" to "molyneux-park"
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

    /** The grounds the game did not have already (estimated ratings). Eden Park, Seddon Park, the Basin Reserve, Hagley Oval ... are in StadiumData. */
    val stadiums: List<Stadium> by lazy {
        VENUE_TEXT.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val p = line.split("|")
            val pitch = pitchOf(p[3][0])
            Stadium(
                id = p[0], name = p[1], city = p[2], country = "New Zealand", homeTeamIds = emptyList(),
                pitchType = pitch, pitchDescription = pitchText(pitch), boundarySize = boundaryOf(p[4][0]),
                altitudeM = p[5].toInt(), dewFactor = 0.1, avgT20FirstInningsScore = 160,
                climate = StadiumClimate(0.35, 65, 18, 10, "Mild conditions with a chance of showers.")
            )
        }
    }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    fun stadiumsFor(id: String): List<Stadium> {
        val finals = when (id) { FORD_ID -> listOf(FORD_FINALS_GROUND); SMASH_ID -> listOf(SMASH_FINALS_GROUND); else -> emptyList() }
        return (buildSchedule(id).map { it.stadiumId } + finals).distinct().mapNotNull { CplData.stadium(it) }
    }

    // ---------------------------------------------------------------- players

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' }

    private fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)

    private fun jit(name: String, salt: Int, range: Int): Int {
        var h = salt * 7919
        for (c in name) h = h * 31 + c.code
        return (h and 0x7fffffff) % (2 * range + 1) - range
    }

    private val SHORT_FIRST = mapOf(
        "mitchell" to "mitch", "matthew" to "matt", "steven" to "steve", "joshua" to "josh", "nicholas" to "nick",
        "benjamin" to "ben", "samuel" to "sam", "alexander" to "alex", "thomas" to "tom", "william" to "will",
        "timothy" to "tim", "daniel" to "dan", "christopher" to "chris", "james" to "jimmy", "jacob" to "jake"
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

    /** The six associations. A player already in the game is copied with his ratings; the rest get estimates by the team's strength. */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val known = HashMap<String, Player>()
        fun addAll(players: List<Player>) {
            for (p in players) known.putIfAbsent(norm(p.name), p)
        }
        addAll(CricketData.getAllTeams().flatMap { it.players })
        addAll(BigBashLeague.buildTeams().first.flatMap { it.players })
        addAll(MajorLeagueCricket.buildTeams().first.flatMap { it.players })
        addAll(Sa20.buildTeams().first.flatMap { it.players })
        addAll(CplData.buildTeams().first.flatMap { it.players })
        addAll(AbuDhabiT10.buildTeams().first.flatMap { it.players })
        addAll(LankaPremierLeague.buildTeams().first.flatMap { it.players })
        addAll(InternationalLeagueT20.buildTeams().first.flatMap { it.players })
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

    /** The published league matches in the published order. The finals are added by [advance]. */
    fun buildSchedule(id: String): List<TournamentFixture> =
        rows(when (id) { PLUNKET_ID -> PLUNKET_TEXT; FORD_ID -> FORD_TEXT; else -> SMASH_TEXT }).mapIndexed { i, p ->
            TournamentFixture(i, i + 1, CplData.LEAGUE, "nz_" + p[0], "nz_" + p[1], GROUND.getValue(p[2]))
        }

    fun newTournament(id: String, userTeamId: String, built: Pair<List<Team>, Map<String, SquadTag>>): TournamentState =
        TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = id,
            name = when (id) { PLUNKET_ID -> PLUNKET_NAME; FORD_ID -> FORD_NAME; else -> SMASH_NAME },
            userTeamId = userTeamId,
            teams = built.first,
            tags = built.second,
            fixtures = buildSchedule(id),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )

    // ---------------------------------------------------------------- Plunket Shield points and table

    private fun battingBonus(runs: Int): Int = (if (runs >= 200) 1 else 0) + (if (runs >= 250) 1 else 0) + (if (runs >= 300) 1 else 0) + (if (runs >= 350) 1 else 0)

    private fun bowlingBonus(wickets: Int): Int = (if (wickets >= 3) 1 else 0) + (if (wickets >= 5) 1 else 0) + (if (wickets >= 7) 1 else 0) + (if (wickets >= 9) 1 else 0)

    /** 12 for a win, 6 for a tie, 0 for a draw, plus the first-innings batting and bowling bonus points. */
    fun points(s: MatchState, teamId: String): Int {
        val base = when (FirstClass.kind(s)) {
            "WIN" -> if (s.winnerId == teamId) 12 else 0
            "TIE" -> 6
            else -> 0
        }
        val list = s.test?.completed ?: return base
        val mine = list.firstOrNull { it.data.battingTeamId == teamId }
        val theirs = list.firstOrNull { it.data.battingTeamId != teamId }
        return base + (if (mine != null) battingBonus(mine.score.runs) else 0) + (if (theirs != null) bowlingBonus(theirs.score.wickets) else 0)
    }

    /** The Plunket Shield table: points, then wins, then net runs per wicket. */
    fun plunketTable(state: TournamentState): List<FcRow> {
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

    /** The Plunket Shield champions: the top of the table once all 24 matches are played. */
    fun champion(state: TournamentState): Team? {
        if (state.fixtures.isEmpty() || state.fixtures.any { it.result == null }) return null
        return plunketTable(state).firstOrNull()?.team
    }

    fun seasonEndLines(state: TournamentState): List<String> {
        val c = champion(state) ?: return emptyList()
        return listOf("${c.name} are the Plunket Shield champions.")
    }

    /** The Ford Trophy and Super Smash league table: 4 points for a win, 2 for a tie or no result. */
    fun limitedTable(state: TournamentState): List<StandingRow> =
        CplData.standings(state, CplData.LEAGUE).map { it.copy(points = it.won * 4 + it.noResult * 2) }

    // ---------------------------------------------------------------- the finals

    private fun winnerOf(fx: List<TournamentFixture>, stage: String): String? =
        fx.firstOrNull { it.stage == stage }?.result?.winnerId

    /** Adds the Elimination Final (2nd v 3rd) once the league is over, then the Final (1st v the winner). Safe after every match. */
    fun advance(state: TournamentState): TournamentState {
        if (state.tournamentId == PLUNKET_ID) return state
        var fx = state.fixtures
        val ground = if (state.tournamentId == FORD_ID) FORD_FINALS_GROUND else SMASH_FINALS_GROUND
        val league = fx.filter { it.stage == CplData.LEAGUE }
        if (league.isNotEmpty() && league.all { it.result != null } && fx.none { it.stage == ELIM }) {
            val top = limitedTable(state.copy(fixtures = fx)).map { it.team.id }
            if (top.size >= 3) fx = fx + TournamentFixture(fx.size, fx.size + 1, ELIM, top[1], top[2], ground)
        }
        if (fx.none { it.stage == FINAL } && fx.any { it.stage == ELIM }) {
            val w = winnerOf(fx, ELIM)
            val top = limitedTable(state.copy(fixtures = fx)).map { it.team.id }
            if (w != null && top.isNotEmpty()) fx = fx + TournamentFixture(fx.size, fx.size + 1, FINAL, top[0], w, ground)
        }
        return state.copy(fixtures = fx)
    }

    // ---------------------------------------------------------------- the data

    // code | name | short | home ground | strength tier (1-5) | squad: name ~ flags ~ role (B batter, A all-rounder, W keeper, L bowler, - unknown) ~ - ~ bowling style
    private const val TEAM_TEXT = """
auc|Auckland Aces|AUC|kennards-oval|3|Adithya Ashok~~L~~LB;Angus Olliver~~L~~RM;Ben Lister~~L~~RM;Bevon Jacobs~~B~~;Cam Fletcher~wk~W~~;Dale Phillips~~B~~OB;Jimmy Neesham~~A~~RM;Jock McKenzie~~L~~RM;Lachlan Stackpole~~B~~SLA;Luke Watson~~B~~;Rohit Kumar~~-~~;Ryan Harrison~~L~~RM;Jordan Sussex~~-~~;Samrath Singh~~-~~;Sean Solia~~-~~;Simon Keene~~B~~;Mark Chapman~~A~~SLA
can|Canterbury Kings|CAN|hagley-oval|5|Matt Boyle~~B~~LB;Chad Bowes~~B~~;Leo Carter~~B~~;Sean Davey~~L~~RM;Jesse Frew~~-~~;Lachie Harper~~A~~RM;Scott Janett~~-~~;Ish Sodhi~~L~~LB;Rhys Mariu~~B~~;Cole McConchie~~A~~OB;Gus McKenzie~~L~~RM;Cam Paul~~B~~;Michael Rae~~L~~RM;Matt Rowe~~L~~RM;Fraser Sheat~~L~~RM;Raunaq Kapur~~-~~;Zak Foulkes~~A~~RM;Mitch Hay~wk~W~~;Matt Henry~~L~~RM;Kyle Jamieson~~L~~RM;Tom Latham~wk~W~~;Daryl Mitchell~~A~~RM;Henry Nicholls~~B~~OB;Will O'Rourke~~L~~RM
cen|Central Stags|CEN|mclean-park-napier|4|Taylor Bettelheim~~B~~;Tom Bruce~~B~~OB;Sam Cassidy~~B~~;Josh Clarkson~~A~~RM;Dane Cleaver~wk~W~~LB;Joey Field~~L~~RM;Toby Findlay~~-~~;Dean Foxcroft~~B~~OB;Jake Gibson~~A~~RM;Curtis Heaphy~~B~~;Jayden Lennox~~L~~SLA;Ajaz Patel~~L~~SLA;Brett Randell~~L~~RM;Brad Schmulian~~B~~LB;Angus Schaw~~-~~;Ray Toole~~L~~RM;Blair Tickner~~L~~RM;Will Young~~B~~OB
nd|Northern Districts|ND|seddon-park|4|Xavier Bell~~B~~;Josh Brown~~B~~;Joe Carter~~B~~;Katene Clarke~~L~~RM;Kristian Clarke~~L~~RM;Henry Cooper~~B~~;Matt Fisher~~L~~RM;Zak Gibson~~L~~RM;Brett Hampton~~B~~;Nick Kelly~~B~~;Scott Kuggeleijn~~A~~RM;James Naylor~~B~~;Robbie O'Donnell~~B~~;Ben Pomare~~L~~RM;Tim Pringle~~-~~;Snehith Reddy~~-~~;Mitchell Santner~~A~~SLA
ota|Otago Volts|OTA|university-oval|4|Matt Bacon~~-~~;Jack Boyle~~A~~RM;Max Chu~wk~W~~;Mason Clarke~~L~~RM;Jacob Cumming~~B~~;Zac Cumming~~L~~LB;Danru Ferns~~L~~RM;Luke Georgeson~~B~~;Toby Hart~~L~~RM;Troy Johnson~~A~~OB;Tom Jones~~B~~;Ben Lockrose~~L~~RM;Jarrod McKay~~L~~RM;Thomas O'Connor~~L~~RM;Thorn Parkes~~-~~;Jamal Todd~~-~~;Jacob Duffy~~L~~RM;Glenn Phillips~~A~~OB
wel|Wellington Firebirds|WEL|basin-reserve|4|Callum McLachlan~wk~W~~;Gareth Severin~~B~~RM;Henry Shipley~~L~~RM;James Hartshorn~~L~~RM;Jesse Tashkoff~~A~~SLA;Liam Dudding~~L~~RM;Logan van Beek~~A~~RM;Muhammad Abbas~~B~~RM;Nick Greenwood~~B~~OB;Oscar Jackson~~B~~;Peter Younghusband~~A~~LB;Sam Mycock~~-~~;Samuel Morgan~~-~~;Amogh Paranjpe~~B~~;Tim Robinson~~B~~;Yahya Zeb~~-~~;Tom Blundell~wk~W~~;Michael Bracewell~~A~~OB;Devon Conway~wk~W~~;Rachin Ravindra~~A~~SLA;Ben Sears~~L~~RM;Nathan Smith~~A~~RM
"""

    // id | name | city | pitch (B batting, G green, D dusty, O bowling) | boundary (S, M, L) | altitude in metres
    private const val VENUE_TEXT = """
fitzherbert-park|Fitzherbert Park|Palmerston North|B|M|30
cobham-oval|Cobham Oval|Whangarei|B|M|20
kennards-oval|Kennards Hire Community Oval|Auckland|B|S|30
colin-maiden-park|Colin Maiden Park|Auckland|B|M|30
pukekura-park|Pukekura Park|New Plymouth|B|S|40
saxton-oval|Saxton Oval|Nelson|B|M|10
sir-john-davies-oval|Sir John Davies Oval|Queenstown|G|L|330
queens-park-invercargill|Queen's Park|Invercargill|G|M|10
molyneux-park|Molyneux Park|Alexandra|G|M|140
"""

    // home team | visiting team | ground, in the published order
    private const val PLUNKET_TEXT = """
nd|auc|sd
cen|wel|fz
ota|can|uo
nd|cen|by
ota|wel|uo
auc|can|ka
auc|wel|ka
nd|can|sd
cen|ota|mc
cen|auc|mc
can|wel|ho
ota|nd|uo
cen|nd|mc
wel|can|br
ota|auc|uo
auc|cen|cm
wel|ota|br
can|nd|ho
nd|ota|sd
wel|cen|br
can|auc|ho
auc|ota|cm
wel|nd|br
can|cen|ho
"""

    private const val FORD_TEXT = """
auc|can|ka
cen|nd|sx
wel|ota|br
nd|auc|by
wel|can|br
ota|cen|uo
cen|auc|sx
wel|nd|br
ota|can|uo
nd|ota|ck
wel|auc|br
cen|can|mc
auc|ota|ka
cen|wel|mc
can|nd|ho
auc|wel|ka
cen|can|pk
ota|nd|qt
can|auc|ho
nd|wel|ck
cen|ota|pk
auc|cen|ka
nd|can|ck
ota|wel|uo
nd|cen|by
can|wel|ho
ota|auc|iv
auc|nd|ka
wel|cen|br
can|ota|ho
"""

    private const val SMASH_TEXT = """
cen|wel|fz
auc|nd|ka
ota|can|ml
can|auc|ho
ota|wel|ml
nd|cen|sd
cen|can|pk
nd|auc|sd
wel|ota|br
wel|nd|br
auc|cen|ka
can|ota|ho
can|nd|ho
wel|cen|br
ota|auc|uo
cen|auc|sx
ota|nd|uo
can|wel|ho
nd|ota|by
auc|wel|ka
can|cen|ho
cen|ota|pk
auc|can|ka
nd|wel|sd
wel|can|br
auc|ota|ka
cen|nd|mc
wel|auc|br
ota|cen|uo
nd|can|sd
"""
}

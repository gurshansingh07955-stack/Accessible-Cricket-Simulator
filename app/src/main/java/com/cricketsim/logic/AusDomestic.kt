package com.cricketsim.logic

/**
 * Australian domestic cricket, 2026-27: the Sheffield Shield (first-class) and the One-Day Cup (50 overs, the Dean Jones Trophy),
 * played by the six states. Team ids are "au_xx". See AUSTRALIA_DOMESTIC.md.
 *
 * Fixtures: the real published lists (cricket.com.au, 7 July 2026), in the real order. The season is already under way in real life
 * (the Shield started on 7 October, the One-Day Cup on 18 September); the game starts both from the beginning and simulates the results.
 *
 *  - Sheffield Shield: ten rounds (30 matches, each state plays ten), then a five-day Final between the top two at the home ground of
 *    the top-ranked side. Points: 6 for an outright win, 3 for a tie, 1 for a draw, plus batting bonus points (0.01 for each run over 200,
 *    up to 400, in the first innings) and bowling bonus points (0.1 for each wicket in the opposition's first innings). If the Final is
 *    drawn or tied, the side with more first-innings bonus points wins. The real rule counts only the first 100 overs of an innings;
 *    the game counts the whole innings. Points are kept in hundredths.
 *  - One-Day Cup: 21 league matches (each state plays seven), 4 points a win and 2 for a tie or no result (a tied league match is
 *    shared), then a Final between the top two at the top side's ground.
 *
 * Squads are the 2026-27 state lists (cricket.com.au, ESPNcricinfo, Cricket NSW, 3 October 2026). A player already in the game keeps his
 * ratings; the others get ESTIMATED ratings. The six players on national contracts are in the squads; in real life they play only when free.
 */
object AusDomestic {
    const val SHIELD_ID = "shield_2026_27"
    const val SHIELD_NAME = "Sheffield Shield 2026-27"
    const val ODC_ID = "audc_2026_27"
    const val ODC_NAME = "One-Day Cup 2026-27 (Australia)"
    private const val FINAL = "Final"

    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN

    fun isShield(id: String?): Boolean = id == SHIELD_ID
    fun isOdc(id: String?): Boolean = id == ODC_ID
    fun isAus(id: String?): Boolean = id == SHIELD_ID || id == ODC_ID

    /** Four days in the league, five in the Final. */
    fun daysFor(stage: String): Int = if (stage == FINAL) 5 else 4

    /** The One-Day Cup: 50 overs, no DRS, rain as in the game's other 50-over matches; only the Final can go to a Super Over. */
    fun rulesFor(id: String?, stage: String?): MatchRules? {
        if (id != ODC_ID) return null
        val base = TournamentRules.rulesForFormat("ODI") ?: return null
        return base.copy(drsReviews = 0, noSuperOver = stage != FINAL)
    }

    fun introOf(id: String?): String =
        if (id == SHIELD_ID) {
            "Six points for an outright win, three for a tie, one for a draw, plus batting and bowling bonus points from the first innings. " +
                "After ten rounds the top two play a five-day Final at the home ground of the top side; if it is drawn, the side with more first-innings bonus points wins."
        } else {
            "Four points for a win, two each for a tie or no result. After the 21 league matches the top two play the Final for the Dean Jones Trophy."
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
            TeamDef("au_" + p[0], p[1], p[2], p[3], p[4].toInt(), p[5].split(";").map { parseEntry(it) })
        }
    }

    fun isTeam(teamId: String): Boolean = DEFS.any { it.id == teamId }

    fun shortNameOrNull(teamId: String): String? = DEFS.find { it.id == teamId }?.shortName

    fun homeGround(teamId: String): String = DEFS.find { it.id == teamId }?.home ?: "mcg"

    private val GROUND = mapOf(
        "jo" to "junction-oval", "cc" to "cricket-central", "wa" to "waca-ground", "ao" to "adelaide-oval",
        "bl" to "bellerive-oval", "gb" to "gabba", "mc" to "mcg", "ps" to "optus-stadium", "sc" to "scg",
        "ab" to "allan-border-field", "kr" to "karen-rolton-oval"
    )
    private val SHIELD_FINAL_GROUND = mapOf(
        "au_nsw" to "scg", "au_vic" to "mcg", "au_qld" to "gabba", "au_sa" to "adelaide-oval", "au_wa" to "waca-ground", "au_tas" to "bellerive-oval"
    )
    private val ODC_FINAL_GROUND = mapOf(
        "au_nsw" to "cricket-central", "au_vic" to "junction-oval", "au_qld" to "allan-border-field",
        "au_sa" to "karen-rolton-oval", "au_wa" to "waca-ground", "au_tas" to "bellerive-oval"
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
        PitchType.BOWLING -> "A fast, bouncy surface that helps the quick bowlers."
        else -> "A balanced surface with something for everyone."
    }

    private fun boundaryOf(c: Char): BoundarySize = when (c) {
        'S' -> BoundarySize.SMALL
        'L' -> BoundarySize.LARGE
        else -> BoundarySize.MEDIUM
    }

    /** The grounds the game did not have already (estimated ratings). The others (MCG, SCG, Gabba, Adelaide Oval ...) are in StadiumData. */
    val stadiums: List<Stadium> by lazy {
        VENUE_TEXT.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val p = line.split("|")
            val pitch = pitchOf(p[3][0])
            Stadium(
                id = p[0], name = p[1], city = p[2], country = "Australia", homeTeamIds = emptyList(),
                pitchType = pitch, pitchDescription = pitchText(pitch), boundarySize = boundaryOf(p[4][0]),
                altitudeM = p[5].toInt(), dewFactor = 0.15, avgT20FirstInningsScore = 165,
                climate = StadiumClimate(0.15, 45, 25, 12, "Warm, mostly dry conditions.")
            )
        }
    }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    fun stadiumsFor(id: String): List<Stadium> {
        val finals = if (id == SHIELD_ID) SHIELD_FINAL_GROUND.values else ODC_FINAL_GROUND.values
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
        "mitchell" to "mitch", "matthew" to "matt", "steven" to "steve", "joshua" to "josh", "nicholas" to "nic",
        "benjamin" to "ben", "samuel" to "sam", "alexander" to "alex", "thomas" to "tom", "william" to "will",
        "timothy" to "tim", "daniel" to "dan", "christopher" to "chris", "jacob" to "jake"
    )

    /** The player the game already has under this name or a short form of it (Mitchell/Mitch, Matthew/Matt ...). */
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

    /** The six states. A player already in the game is copied with his ratings; the rest get estimates by the state's strength. */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val known = HashMap<String, Player>()
        fun addAll(players: List<Player>) {
            for (p in players) known.putIfAbsent(norm(p.name), p)
        }
        addAll(CricketData.getAllTeams().flatMap { it.players })
        addAll(BigBashLeague.buildTeams().first.flatMap { it.players })
        addAll(CplData.buildTeams().first.flatMap { it.players })
        addAll(AbuDhabiT10.buildTeams().first.flatMap { it.players })
        addAll(LankaPremierLeague.buildTeams().first.flatMap { it.players })
        addAll(InternationalLeagueT20.buildTeams().first.flatMap { it.players })
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

    /** The published league matches in the real order (home team, visiting team, ground). The Final is added by [advance]. */
    fun buildSchedule(id: String): List<TournamentFixture> =
        rows(if (id == SHIELD_ID) SHIELD_TEXT else ODC_TEXT).mapIndexed { i, p ->
            TournamentFixture(i, i + 1, CplData.LEAGUE, "au_" + p[0], "au_" + p[1], GROUND.getValue(p[2]))
        }

    fun newTournament(id: String, userTeamId: String, built: Pair<List<Team>, Map<String, SquadTag>>): TournamentState =
        TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = id,
            name = if (id == SHIELD_ID) SHIELD_NAME else ODC_NAME,
            userTeamId = userTeamId,
            teams = built.first,
            tags = built.second,
            fixtures = buildSchedule(id),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )

    // ---------------------------------------------------------------- Sheffield Shield points and table (in hundredths of a point)

    /** 0.01 for each run over 200 in the side's first innings, up to 400 runs. */
    private fun battingBonus(runs: Int): Int = (minOf(runs, 400) - 200).coerceAtLeast(0)

    /** 0.1 for each wicket taken in the opposition's first innings. */
    private fun bowlingBonus(wickets: Int): Int = wickets * 10

    /** The first-innings bonus points of one side, in hundredths. */
    fun bonus(s: MatchState, teamId: String): Int {
        val list = s.test?.completed ?: return 0
        val mine = list.firstOrNull { it.data.battingTeamId == teamId }
        val theirs = list.firstOrNull { it.data.battingTeamId != teamId }
        return (if (mine != null) battingBonus(mine.score.runs) else 0) + (if (theirs != null) bowlingBonus(theirs.score.wickets) else 0)
    }

    /** Points in hundredths: 6 for an outright win, 3 for a tie, 1 for a draw, plus the bonus points. */
    fun points(s: MatchState, teamId: String): Int {
        val base = when (FirstClass.kind(s)) {
            "WIN" -> if (s.winnerId == teamId) 600 else 0
            "TIE" -> 300
            else -> 100
        }
        return base + bonus(s, teamId)
    }

    /** A drawn or tied Final: the side with more first-innings bonus points wins (then the first-innings leader, then the home side). */
    fun finalWinnerOnDraw(s: MatchState, homeId: String, awayId: String): String {
        val h = bonus(s, homeId)
        val a = bonus(s, awayId)
        return when {
            h > a -> homeId
            a > h -> awayId
            else -> FirstClass.firstInningsLeaderId(s) ?: homeId
        }
    }

    fun format(hundredths: Int): String = "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"

    fun pointsText(home: Team, away: Team, homePoints: Int, awayPoints: Int): String =
        " Points: ${CplData.shortName(home.id)} ${format(homePoints)}, ${CplData.shortName(away.id)} ${format(awayPoints)}."

    /** The Shield table: points, then wins, then runs per wicket scored over conceded. */
    fun shieldTable(state: TournamentState): List<FcRow> {
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

    /** The One-Day Cup table: 4 points for a win, 2 for a tie or no result. */
    fun odcTable(state: TournamentState): List<StandingRow> =
        CplData.standings(state, CplData.LEAGUE).map { it.copy(points = it.won * 4 + it.noResult * 2) }

    /** Adds the Final once every league match is played: the top two, at the top side's ground. */
    fun advance(state: TournamentState): TournamentState {
        val fx = state.fixtures
        val league = fx.filter { it.stage == CplData.LEAGUE }
        if (league.isEmpty() || league.any { it.result == null } || fx.any { it.stage == FINAL }) return state
        val shield = state.tournamentId == SHIELD_ID
        val top = (if (shield) shieldTable(state).map { it.team.id } else odcTable(state).map { it.team.id }).take(2)
        if (top.size < 2) return state
        val ground = (if (shield) SHIELD_FINAL_GROUND else ODC_FINAL_GROUND)[top[0]] ?: "mcg"
        return state.copy(fixtures = fx + TournamentFixture(fx.size, fx.size + 1, FINAL, top[0], top[1], ground))
    }

    // ---------------------------------------------------------------- the data

    // code | name | short | home ground | strength tier (1-5) | squad: name ~ flags ~ role (B batter, A all-rounder, W keeper, L bowler, - unknown) ~ - ~ bowling style
    private const val TEAM_TEXT = """
nsw|New South Wales|NSW|scg|4|Pat Cummins~~L~~RM;Josh Hazlewood~~L~~RM;Nathan Lyon~~L~~OB;Steve Smith~~B~~LB;Mitchell Starc~~L~~RM;Sean Abbott~~A~~RM;Joel Davies~~A~~RM;Ollie Davies~~A~~OB;Ben Dwarshuis~~L~~RM;Jack Edwards~~A~~RM;Ryan Hadley~~L~~RM;Liam Hatcher~~L~~RM;Ryan Hicks~wk~W~~;Bryce Jackson~~L~~RM;Riley Kingsell~~B~~;Sam Konstas~~B~~OB;Nic Maddinson~~B~~SLA;Jack Nisbet~~L~~RM;Kurtis Patterson~~B~~;Josh Philippe~wk~W~~;Will Salzmann~~A~~RM;Tanveer Sangha~~L~~LB;Lachlan Shaw~~L~~SLA;Charlie Stobo~~L~~RM;Charlie Anderson~~-~~;Jake Scott~~-~~;Harjas Singh~~B~~
vic|Victoria|VIC|mcg|4|Scott Boland~~L~~RM;Todd Murphy~~L~~OB;Austin Anlezark~~L~~RM;Liam Blackford~~-~~;Dylan Brasher~~B~~;Ashley Chandrasinghe~~B~~;Harry Dixon~~B~~OB;Sam Elliott~~A~~RM;Peter Handscomb~~B~~;Sam Harper~wk~W~~;Marcus Harris~~B~~OB;Campbell Kellaway~~B~~;Blake Macdonald~~-~~;Cam McClure~~-~~;David Moody~~L~~RM;Fergus O'Neill~~L~~RM;Oliver Peake~~A~~OB;Mitch Perry~~-~~;Tyler Pearson~~-~~;Tom Rogers~~L~~RM;Will Sutherland~~A~~RM;Doug Warre~~L~~RM;Harry Hoekstra~~-~~;Tom Paddington~~-~~;Aryan Sharma~~-~~
qld|Queensland|QLD|gabba|3|Adam Zampa~~L~~LB;Xavier Bartlett~~L~~RM;Marnus Labuschagne~~B~~LB;Michael Neser~~A~~RM;Tom Balkin~~L~~RM;Hugo Burdon~~L~~RM;Jack Clayton~~A~~SLC;Cameron Gannon~~L~~RM;Lachlan Hearne~~B~~;Hayden Kerr~~A~~RM;Angus Lovell~~B~~;Dylan McLachlan~~-~~;Jimmy Peirson~wk~W~~;Matthew Renshaw~~B~~SLA;Jem Ryan~~L~~RM;Jack Sinfield~~A~~LB;Mark Steketee~~L~~RM;Tom Straker~~L~~RM;Mitchell Swepson~~L~~LB;Callum Vidler~~L~~RM;Hugh Weibgen~~B~~;Tom Whitney~~L~~RM;Jack Wildermuth~~A~~RM;Tom Campbell~~-~~;Steven Hogan~~-~~;Charlie Lachmund~~-~~;Oli Patterson~~-~~
sa|South Australia|SA|adelaide-oval|5|Alex Carey~wk~W~~;Brendan Doggett~~L~~RM;Travis Head~~B~~OB;Jake Weatherald~~B~~LB;Wes Agar~~L~~RM;Jordan Buckingham~~A~~RM;Jake Fraser-McGurk~~B~~LB;Matthew Gilkes~wk~W~~;Henry Hunt~~B~~;Hanno Jacobs~~-~~;Spencer Johnson~~L~~RM;Jake Lehmann~~B~~;Ben Manenti~~A~~OB;Nathan McAndrew~~L~~RM;Noah McFadyen~~-~~;Nathan McSweeney~~B~~OB;Harry Nielsen~wk~W~~;Lloyd Pope~~L~~LB;Henry Thornton~~L~~RM;Campbell Thompson~~-~~;Jason Sangha~~B~~LB;Liam Scott~~A~~SLA;Mackenzie Harvey~~B~~;Jerrssis Wadia~~-~~;Thomas Brown~~-~~;Kane Halfpenny~~-~~;Douwtjie Hoogenboezem~~-~~;Hayden Schiller~~-~~
tas|Tasmania|TAS|bellerive-oval|3|Nathan Ellis~~L~~RM;Matthew Kuhnemann~~L~~SLA;Beau Webster~~A~~RM;Marcus Bean~~L~~SLC;Gabe Bell~~L~~RM;Jackson Bird~~L~~RM;Iain Carlisle~~-~~;Nikhil Chaudhary~~A~~OB;Jake Doran~~B~~;Brad Hope~~A~~RM;Caleb Jewell~~B~~;Raf MacMillan~~A~~OB;Caelan Maladay~~-~~;Ben McDermott~wk~W~~;Riley Meredith~~L~~RM;Lawrence Neil-Smith~~-~~;Mitch Owen~~B~~;Will Prestwidge~~L~~RM;Nivethan Radhakrishnan~~L~~RM;Jordan Silk~~B~~;Billy Stanlake~~L~~RM;Tim Ward~~B~~;Teague Wyllie~~B~~;Kasey Barton~~-~~;Zac Curtain~~-~~;Aidan O'Connor~~-~~;Nitesh Samuel~~-~~;William Taylor~~-~~
wa|Western Australia|WA|waca-ground|4|Cameron Green~~A~~RM;Josh Inglis~wk~W~~;Mitchell Marsh~~A~~RM;Cameron Bancroft~~B~~;Mahli Beardman~~L~~RM;Hilton Cartwright~~B~~RM;Cooper Connolly~~A~~SLA;Brody Couch~~L~~RM;Kieran Elliott~~L~~RM;Sam Fanning~~B~~;Jayden Goodwin~~B~~LB;Aaron Hardie~~A~~RM;Liam Haskett~~L~~RM;Will Malajczuk~~L~~RM;Tom Murray~~L~~RM;Lance Morris~~L~~RM;Joel Paris~~L~~RM;Jhye Richardson~~L~~RM;Corey Rocchiccioli~~-~~;Ashton Turner~~B~~OB;Sam Whiteman~wk~W~~;Simon Budge~~-~~;Albert Esterhuysen~~-~~;Jordan Quiggin~~-~~;Corey Wasley~~-~~
"""

    // id | name | city | pitch (B batting, G green, D dusty, O bowling) | boundary (S, M, L) | altitude in metres
    private const val VENUE_TEXT = """
cricket-central|Cricket Central|Sydney|B|M|20
waca-ground|WACA Ground|Perth|O|M|30
allan-border-field|Allan Border Field|Brisbane|B|M|20
karen-rolton-oval|Karen Rolton Oval|Adelaide|B|S|40
"""

    // home team | visiting team | ground, in the real order of the published fixture lists
    private const val SHIELD_TEXT = """
vic|sa|jo
nsw|tas|cc
wa|qld|wa
sa|qld|ao
tas|vic|bl
wa|nsw|wa
sa|wa|ao
tas|nsw|bl
qld|vic|gb
qld|tas|gb
nsw|sa|cc
vic|wa|mc
nsw|qld|cc
wa|tas|ps
sa|vic|ao
nsw|wa|sc
vic|qld|mc
sa|tas|ao
qld|wa|gb
vic|tas|mc
sa|nsw|ao
nsw|vic|sc
tas|qld|bl
wa|sa|wa
qld|nsw|ab
tas|sa|bl
wa|vic|wa
qld|sa|ab
vic|nsw|jo
tas|wa|bl
"""

    private const val ODC_TEXT = """
wa|sa|wa
qld|vic|ab
wa|tas|wa
qld|nsw|ab
sa|tas|kr
vic|nsw|jo
vic|sa|jo
nsw|tas|cc
wa|qld|wa
sa|qld|kr
tas|vic|bl
wa|nsw|wa
qld|tas|gb
nsw|sa|cc
vic|wa|jo
nsw|vic|cc
tas|qld|bl
sa|wa|kr
qld|wa|ab
tas|nsw|bl
sa|vic|kr
"""
}

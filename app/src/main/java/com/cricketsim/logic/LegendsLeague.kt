package com.cricketsim.logic

/**
 * Legends League Cricket 2026 (LLC season 4, 11 to 27 March 2026, India): the league of retired international and
 * veteran stars. Royal Riders Punjab won it.
 *
 *  - SIX TEAMS, the real ones: Mumbai Spartans, India Capitals, India Tigers, Konark Suryas Odisha, Southern Super
 *    Stars and Royal Riders Punjab, with the squads they finished the tournament with. (The league's own site says
 *    "India Capitals"; a few news sites print "India Captains", so the name is the one thing sources disagree on.)
 *  - THE FORMAT, as published: a single round-robin (15 league matches), then the top four play two semi-finals
 *    (1st v 4th, 2nd v 3rd) and the Final, all T20. The semi-finals and the Final were at Haldwani.
 *  - THE FIXTURES: the 15 league matches in the published order. The real tournament was disturbed (matches were
 *    moved, abandoned, or decided by walkover or disqualification), so the game plays the list as it was
 *    scheduled; the game simulates every result. The grounds of matches 14 and 15 were not published in the
 *    sources I could read (they are an assumption: Gwalior).
 *  - THE RULES used: none beyond the XI of eleven. There is no overseas rule, so every player is tagged DOMESTIC.
 *    NOT built: the league's points rules (the game's table gives 2 a win).
 *
 * Players already in the game (Ashwin, Udana) are COPIED with their ratings. Everybody else is a retired star or an
 * Indian domestic player the game did not have, so every one of them is new with ESTIMATED ratings (the nw() lines).
 * Players the early announcements named who were not in the final squads (Dhawan, Finch, Wagner, Tye and others)
 * are not included. The four grounds are new in the game (StadiumData, ratings estimated).
 * It reuses the CPL machinery as it is.
 */
object LegendsLeague {

    const val TOURNAMENT_ID = "llc_2026"
    const val TOURNAMENT_NAME = "Legends League Cricket 2026"

    private const val HALDWANI = "indira-gandhi-haldwani"
    private const val COIMBATORE = "sri-ramakrishna-coimbatore"
    private const val AMRITSAR = "gandhi-sports-amritsar"
    private const val GWALIOR = "roop-singh-gwalior"

    private class NewSpec(val role: PlayerRole, val bat: Int, val bowl: Int, val style: BowlingStyle)
    private class Slot(val name: String, val spec: NewSpec?)
    private class TeamDef(val id: String, val name: String, val shortName: String, val code: String, val squad: List<Slot>)

    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN

    private fun ex(name: String) = Slot(name, null)
    private fun nw(name: String, role: PlayerRole, bat: Int, bowl: Int, style: BowlingStyle = PACE) =
        Slot(name, NewSpec(role, bat, bowl, style))

    private val DEFS: List<TeamDef> by lazy {
        listOf(
            TeamDef("llc_msp", "Mumbai Spartans", "Spartans", "MSP", listOf(
                nw("Suresh Raina", BAT, 80, 35, SPIN), nw("Carlos Brathwaite", AR, 68, 70), nw("S Sreesanth", BWL, 20, 70),
                nw("Manan Sharma", AR, 55, 62, SPIN), nw("Chadwick Walton", WK, 72, 10), ex("Isuru Udana"),
                nw("Bipul Sharma", AR, 55, 60, SPIN), nw("Shoaib Khan", BAT, 56, 15), nw("Bharath Chipli", BAT, 58, 10),
                nw("Amit Verma", BAT, 60, 28, SPIN), nw("Subodh Bati", AR, 52, 52), nw("KC Cariappa", BWL, 18, 64, SPIN),
                nw("Asad Pathan", AR, 54, 54), nw("Faiz Fazal", BAT, 62, 12), nw("Ishwar Choudhury", BWL, 20, 58),
                nw("Jaykishan Kolsawala", BAT, 54, 10)
            )),
            TeamDef("llc_inc", "India Capitals", "Capitals", "INC", listOf(
                nw("Irfan Pathan", AR, 62, 76), nw("Hashim Amla", BAT, 86, 10), nw("Lahiru Thirimanne", BAT, 68, 20),
                nw("Hamilton Masakadza", BAT, 72, 22), nw("Parvinder Awana", BWL, 18, 70), nw("Asela Gunaratne", AR, 60, 56),
                nw("Iqbal Abdullah", AR, 50, 70, SPIN), nw("Shreevats Goswami", WK, 72, 10), nw("Diwesh Pathania", AR, 55, 55),
                nw("Baltej Singh Dhanda", BWL, 20, 60), nw("Abhishek Sakuja", AR, 55, 62, SPIN), nw("Priyank Panchal", BAT, 68, 10),
                nw("Kuldeep Hooda", AR, 56, 56, SPIN), nw("Robin Bist", BAT, 66, 20), nw("Rajesh Bishnoi", BWL, 18, 62, SPIN),
                nw("Sheldon Jackson", WK, 72, 10)
            )),
            TeamDef("llc_ing", "India Tigers", "Tigers", "ING", listOf(
                nw("Chris Gayle", BAT, 88, 40, SPIN), nw("Robin Uthappa", WK, 78, 10), nw("Tillakaratne Dilshan", AR, 80, 48, SPIN),
                nw("Pawan Negi", AR, 60, 66, SPIN), nw("Amitoze Singh", AR, 56, 56), nw("Akalanka Ganegama", AR, 60, 60),
                nw("Samit Patel", AR, 66, 72, SPIN), nw("Shahbaz Nadeem", BWL, 28, 68, SPIN), nw("Ambati Rayudu", BAT, 74, 15, SPIN),
                nw("Jeevan Mendis", AR, 62, 60, SPIN), nw("Milinda Siriwardana", AR, 60, 62, SPIN), nw("Thilan Thushara", BWL, 20, 62),
                nw("Abhimanyu Mithun", BWL, 22, 68), nw("Shadab Jakati", AR, 50, 66, SPIN), nw("Abu Nechim", BWL, 18, 66),
                nw("Kirk Edwards", BAT, 66, 10), nw("Kamau Leverock", BWL, 15, 66), nw("Sumit Singh", BWL, 22, 60),
                nw("Amit Paunikar", BAT, 60, 10)
            )),
            TeamDef("llc_kso", "Konark Suryas Odisha", "Suryas", "KSO", listOf(
                ex("Ravichandran Ashwin"), nw("Ben Dunk", WK, 78, 10), nw("Ross Taylor", BAT, 84, 15, SPIN),
                nw("Piyush Chawla", BWL, 17, 63, SPIN), nw("Chanderpaul Hemraj", BAT, 62, 12), nw("Miguel Cummins", BWL, 20, 70),
                nw("Jermaine Blackwood", BAT, 68, 10), nw("Lasith Lakshan", AR, 52, 56), nw("Kennar Lewis", BAT, 60, 10),
                nw("Jesal Karia", BWL, 18, 62, SPIN), nw("Rayad Emrit", AR, 56, 64), nw("Ankit Rajpoot", BWL, 20, 66),
                nw("Surain Thakar", BAT, 56, 10), nw("Akshay Wakhre", BWL, 25, 62, SPIN), nw("Imtiaz Ahmad", AR, 52, 54),
                nw("Vikas Tokas", BWL, 20, 60), nw("Monu Kumar", BWL, 20, 60), nw("Rituraj Rajeev Singh", BAT, 54, 10),
                nw("Junaid Siddiqui", BWL, 20, 58), nw("Malinda Pushpakumara", BWL, 20, 68, SPIN), nw("Chirag Gandhi", BAT, 60, 10)
            )),
            TeamDef("llc_sss", "Southern Super Stars", "Super Stars", "SSS", listOf(
                nw("Harbhajan Singh", BWL, 40, 84, SPIN), nw("Martin Guptill", BAT, 86, 12, SPIN), nw("Dinesh Karthik", WK, 78, 10),
                nw("Saurabh Tiwary", BAT, 66, 10), nw("Dilshan Munaweera", BAT, 64, 10), nw("Waqarullah Ishaq", WK, 62, 10),
                nw("Hamid Hassan", BWL, 15, 72), nw("Sarabjit Ladda", AR, 50, 62), nw("Sidharth Trivedi", BWL, 20, 68),
                nw("Rahul Yadav", BAT, 56, 10), nw("Sudeep Tyagi", BWL, 20, 66), nw("Adeesha Thilanchana", AR, 52, 58)
            )),
            TeamDef("llc_rrp", "Royal Riders Punjab", "Riders", "RRP", listOf(
                nw("Thisara Perera", AR, 72, 70), nw("Cheteshwar Pujara", BAT, 66, 8, SPIN), nw("Rishi Dhawan", AR, 62, 62),
                nw("Angelo Perera", BAT, 68, 15), nw("Chaturanga de Silva", AR, 60, 60, SPIN), nw("Trevon Griffith", BAT, 58, 10),
                nw("Seekkuge Prasanna", AR, 62, 62, SPIN), nw("Asghar Afghan", BAT, 66, 20), nw("Mark Deyal", AR, 54, 54),
                nw("William Perkins", AR, 52, 52), nw("Anureet Singh", BWL, 20, 64), nw("Farman Ahmed", BWL, 20, 58),
                nw("Ayaan Khan", AR, 52, 54), nw("Mohammad Shahzad", WK, 74, 10), nw("Pawan Suyal", BWL, 22, 66),
                nw("Sarul Kanwar", BAT, 56, 10), nw("Aftab Alam", BWL, 18, 70), nw("Samiullah Shinwari", AR, 62, 60, SPIN)
            ))
        )
    }

    fun isTeam(teamId: String): Boolean = DEFS.any { it.id == teamId }

    fun shortNameOrNull(teamId: String): String? = DEFS.find { it.id == teamId }?.shortName

    val stadiums: List<Stadium> by lazy {
        listOf(HALDWANI, COIMBATORE, AMRITSAR, GWALIOR).mapNotNull { StadiumData.getStadiumById(it) }
    }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' }

    private fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)

    /** The six teams (strongest first). Everybody is tagged DOMESTIC: no squad rule applies. */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val roster = CricketData.getAllTeams().flatMap { it.players }.associateBy { norm(it.name) }
        val tags = LinkedHashMap<String, SquadTag>()
        val teams = DEFS.map { def ->
            val players = def.squad.mapIndexed { index, slot ->
                val id = "${def.id}_${index + 1}"
                val base = if (slot.spec == null) roster[norm(slot.name)] else null
                val player = if (base != null) {
                    base.copy(id = id, teamId = def.id, name = slot.name)
                } else {
                    val s = slot.spec ?: NewSpec(PlayerRole.BATSMAN, 60, 20, BowlingStyle.PACE)
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
            Team(
                id = def.id, name = def.name, countryCode = def.code,
                battingRating = avg(players.map { it.battingRating }.sortedDescending().take(8)),
                bowlingRating = avg(players.map { it.bowlingRating }.sortedDescending().take(5)),
                fieldingRating = avg(players.map { it.fieldingRating }),
                players = players
            )
        }
        return Customisation.applyToBuilt(Pair(teams, tags), true)
    }

    // M Mumbai Spartans, C India Capitals, T India Tigers, K Konark Suryas Odisha, S Southern Super Stars, R Royal Riders Punjab.
    private class LeagueMatch(val code: String, val ground: String)

    private val LEAGUE_MATCHES = listOf(
        LeagueMatch("M C", HALDWANI), LeagueMatch("R T", HALDWANI), LeagueMatch("K S", COIMBATORE), LeagueMatch("M T", HALDWANI),
        LeagueMatch("K R", COIMBATORE), LeagueMatch("C S", AMRITSAR), LeagueMatch("R S", AMRITSAR), LeagueMatch("M K", GWALIOR),
        LeagueMatch("C T", COIMBATORE), LeagueMatch("M S", GWALIOR), LeagueMatch("K T", AMRITSAR), LeagueMatch("C R", GWALIOR),
        LeagueMatch("S T", AMRITSAR), LeagueMatch("K C", GWALIOR), LeagueMatch("M R", GWALIOR)
    )

    private val TEAM_OF = mapOf('M' to "llc_msp", 'C' to "llc_inc", 'T' to "llc_ing", 'K' to "llc_kso", 'S' to "llc_sss", 'R' to "llc_rrp")

    /** The 15 league matches in the published order, then Semi-final 1, Semi-final 2 and the Final, all at Haldwani. */
    fun buildSchedule(): List<TournamentFixture> {
        val fixtures = ArrayList<TournamentFixture>()
        LEAGUE_MATCHES.forEachIndexed { index, m ->
            val p = m.code.split(" ")
            fixtures.add(TournamentFixture(index, index + 1, CplData.LEAGUE, TEAM_OF.getValue(p[0][0]), TEAM_OF.getValue(p[1][0]), m.ground))
        }
        var next = fixtures.size
        fun playoff(stage: String, note: String) {
            fixtures.add(TournamentFixture(next, next + 1, stage, null, null, HALDWANI, placeholder = note))
            next++
        }
        playoff("Semi-final 1", "1st v 4th in the table")
        playoff("Semi-final 2", "2nd v 3rd in the table")
        playoff("Final", "Winners of the two semi-finals")
        return fixtures
    }

    fun newTournament(userTeamId: String, built: Pair<List<Team>, Map<String, SquadTag>>): TournamentState =
        TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = TOURNAMENT_ID,
            name = TOURNAMENT_NAME,
            userTeamId = userTeamId,
            teams = built.first,
            tags = built.second,
            fixtures = buildSchedule(),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )
}

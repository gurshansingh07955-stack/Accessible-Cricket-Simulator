package com.cricketsim.logic

/**
 * The two Indian domestic white-ball tournaments of 2026-27: the Syed Mushtaq Ali Trophy (T20, 14 November to 6 December) and the Vijay
 * Hazare Trophy (50 overs, 14 December to 8 January). Both are played by the 38 Ranji Trophy teams: four Elite groups of eight and a Plate
 * group of six, every team playing the others in its group once. The fixtures are the real ones in their real order (Cricinfo, 31 October
 * 2026). See DOMESTIC_WHITE_BALL.md.
 *
 * Teams and squads: the Ranji Trophy teams (logic/Ranji.kt, ids "rj_xx") and their squads, plus a few well-known players for each state taken
 * from the game's own players (the roster, the IPL pool and the leagues). Nobody is created here. [ADD_STARS] = false gives plain Ranji squads.
 * The real squads for these tournaments are named a few days before they start.
 *
 * Stages: "Elite Group A" to "Elite Group D", "Plate Group", then
 *  - T20: "Super League Group A" (A1, B1, C2, D2) and "Super League Group B" (C1, D1, A2, B2), each a round robin of four, the group
 *    winners play the "Final"; "Plate Final" between the top two of the Plate group.
 *  - 50 overs: quarter-finals A1 v C2, B1 v D2, C1 v A2, D1 v B2, semi-finals, the "Final"; the "Plate Final".
 * Points: 4 for a win, 2 each for a tie or no result (shown in the tables; the game stores 2 for a win and they are converted).
 */
object DomesticWhiteBall {
    const val SMAT_ID = "smat_2026_27"
    const val SMAT_NAME = "Syed Mushtaq Ali Trophy 2026-27"
    const val VHT_ID = "vht_2026_27"
    const val VHT_NAME = "Vijay Hazare Trophy 2026-27"

    /** True: each state's squad is its Ranji squad plus well-known players the game already has. False: Ranji squads only. */
    const val ADD_STARS = true

    const val PLATE = "Plate Group"
    const val PLATE_FINAL = "Plate Final"
    const val FINAL = "Final"
    const val SL_A = "Super League Group A"
    const val SL_B = "Super League Group B"
    val ELITE = listOf("Elite Group A", "Elite Group B", "Elite Group C", "Elite Group D")
    private val STAGE_OF_LETTER = mapOf('A' to ELITE[0], 'B' to ELITE[1], 'C' to ELITE[2], 'D' to ELITE[3], 'P' to PLATE)

    // Grounds: all exist in the game already (StadiumData or Ranji.stadiums). Group B's and C's cities in the 50-over event are an assumption.
    private val SMAT_GROUND = mapOf(
        "m" to "bkc", "w" to "wankhede", "v" to "aca-vdca-vizag", "p" to "mohali",
        "n" to "mullanpur", "k" to "kalyani", "e" to "eden-gardens", "s" to "sovima"
    )
    private val VHT_GROUND = mapOf(
        "r" to "saurashtra-rajkot", "a" to "narendra-modi-ahmedabad", "b" to "alur", "j" to "jsca-ranchi", "i" to "holkar-indore"
    )
    private const val SMAT_KNOCKOUT_GROUND = "vca-nagpur"
    private const val SMAT_PLATE_FINAL_GROUND = "sovima"
    private const val VHT_KNOCKOUT_GROUND = "aca-vdca-vizag"
    private const val VHT_PLATE_FINAL_GROUND = "holkar-indore"

    fun isSmat(id: String?): Boolean = id == SMAT_ID
    fun isVht(id: String?): Boolean = id == VHT_ID
    fun isWhiteBall(id: String?): Boolean = id == SMAT_ID || id == VHT_ID

    /** 20 overs (T20) or 50 overs, no DRS and no rain, as in Indian domestic cricket. In the 50-over event a tied league match is shared. */
    fun rulesFor(id: String?, stage: String?): MatchRules? {
        val key = when (id) {
            SMAT_ID -> "T20"
            VHT_ID -> "ODI"
            else -> return null
        }
        val base = TournamentRules.rulesForFormat(key) ?: return null
        val league = stage != null && (stage in ELITE || stage == PLATE)
        return base.copy(drsReviews = 0, rainEnabled = false, noSuperOver = id == VHT_ID && league)
    }

    fun introOf(id: String?): String =
        if (id == SMAT_ID) {
            "Four points for a win, two each for a tie or no result. The top two of each Elite group go to the Super League. " +
                "Super League Group A is the winners of Elite groups A and B and the runners-up of C and D; Super League Group B is the winners " +
                "of C and D and the runners-up of A and B. The winners of the two Super League groups play the Final. " +
                "The top two of the Plate group play the Plate Final."
        } else {
            "Four points for a win, two each for a tie or no result. The top two of each Elite group go to the quarter-finals: " +
                "A1 v C2, B1 v D2, C1 v A2, D1 v B2. Then the semi-finals and the Final. The top two of the Plate group play the Plate Final."
        }

    // ---------------------------------------------------------------- teams

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' }

    private fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)

    /** Everyone the game already has by name: the roster, the IPL pool and the leagues. */
    private fun knownPlayers(): Map<String, Player> {
        val out = HashMap<String, Player>()
        fun addAll(players: List<Player>) {
            for (p in players) out.putIfAbsent(norm(p.name), p)
        }
        addAll(CricketData.getAllTeams().flatMap { it.players })
        addAll(IplData.buildPool().map { it.player })
        addAll(CplData.buildTeams().first.flatMap { it.players })
        addAll(AbuDhabiT10.buildTeams().first.flatMap { it.players })
        addAll(LankaPremierLeague.buildTeams().first.flatMap { it.players })
        addAll(InternationalLeagueT20.buildTeams().first.flatMap { it.players })
        addAll(BigBashLeague.buildTeams().first.flatMap { it.players })
        addAll(MajorLeagueCricket.buildTeams().first.flatMap { it.players })
        addAll(Sa20.buildTeams().first.flatMap { it.players })
        return out
    }

    /** The 38 teams. A name in [STARS_TEXT] that the game does not have is simply left out. */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val ranji = Ranji.buildTeams()
        if (!ADD_STARS) return ranji
        val known = knownPlayers()
        val tags = LinkedHashMap<String, SquadTag>(ranji.second)
        val extra = STARS_TEXT.lines().map { it.trim() }.filter { it.isNotEmpty() }.associate { line ->
            val p = line.split("|")
            p[0] to p[1].split(";").map { it.trim() }
        }
        val teams = ranji.first.map { team ->
            val names = extra[team.id.removePrefix("rj_")] ?: return@map team
            val have = team.players.map { norm(it.name) }.toMutableSet()
            val added = ArrayList<Player>()
            for (n in names) {
                val found = known[norm(n)] ?: continue
                if (!have.add(norm(n))) continue
                val id = "${team.id}_s${added.size + 1}"
                added.add(found.copy(id = id, teamId = team.id, name = n))
                tags[id] = SquadTag.DOMESTIC
            }
            if (added.isEmpty()) {
                team
            } else {
                val players = (team.players + added).sortedByDescending { maxOf(it.battingRating, it.bowlingRating) }
                team.copy(
                    players = players,
                    battingRating = avg(players.map { it.battingRating }.sortedDescending().take(8)),
                    bowlingRating = avg(players.map { it.bowlingRating }.sortedDescending().take(5)),
                    fieldingRating = avg(players.map { it.fieldingRating })
                )
            }
        }
        return Pair(teams, tags)
    }

    // ---------------------------------------------------------------- fixtures

    private fun rows(text: String): List<List<String>> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { it.split("|") }

    /** The 127 real group matches in the real order (by date, then as listed). The later stages are added by [advance]. */
    fun buildSchedule(id: String): List<TournamentFixture> {
        val smat = id == SMAT_ID
        val grounds = if (smat) SMAT_GROUND else VHT_GROUND
        return rows(if (smat) SMAT_TEXT else VHT_TEXT).mapIndexed { i, p ->
            TournamentFixture(i, i + 1, STAGE_OF_LETTER.getValue(p[0][0]), "rj_" + p[1], "rj_" + p[2], grounds.getValue(p[3]))
        }
    }

    fun newTournament(id: String, userTeamId: String, built: Pair<List<Team>, Map<String, SquadTag>>): TournamentState =
        TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = id,
            name = if (id == SMAT_ID) SMAT_NAME else VHT_NAME,
            userTeamId = userTeamId,
            teams = built.first,
            tags = built.second,
            fixtures = buildSchedule(id),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )

    fun stadiumsFor(id: String): List<Stadium> {
        val extra = if (id == SMAT_ID) listOf(SMAT_KNOCKOUT_GROUND, SMAT_PLATE_FINAL_GROUND) else listOf(VHT_KNOCKOUT_GROUND, VHT_PLATE_FINAL_GROUND)
        return (buildSchedule(id).map { it.stadiumId } + extra).distinct().mapNotNull { CplData.stadium(it) }
    }

    // ---------------------------------------------------------------- tables and progress

    private fun teamsOfStage(state: TournamentState, stage: String): Set<String> =
        state.fixtures.filter { it.stage == stage }.flatMap { listOfNotNull(it.homeId, it.awayId) }.toSet()

    /** The table of one stage with the BCCI's points: 4 for a win, 2 for a tie or no result. */
    private fun stageTable(state: TournamentState, stage: String): List<StandingRow> {
        val ids = teamsOfStage(state, stage)
        return CplData.standings(state, stage).filter { it.team.id in ids }
            .map { it.copy(points = it.won * 4 + it.noResult * 2) }
    }

    /** One table per group (and per Super League group once it exists), each with its heading. */
    fun tables(state: TournamentState): List<Pair<String, List<StandingRow>>> {
        val stages = ELITE + listOf(PLATE) + (if (state.tournamentId == SMAT_ID) listOf(SL_A, SL_B) else emptyList())
        return stages.filter { teamsOfStage(state, it).isNotEmpty() }.map { it to stageTable(state, it) }
    }

    private fun complete(fx: List<TournamentFixture>, stage: String): Boolean {
        val l = fx.filter { it.stage == stage }
        return l.isNotEmpty() && l.all { it.result != null }
    }

    private fun winnerOf(fx: List<TournamentFixture>, stage: String): String? =
        fx.firstOrNull { it.stage == stage }?.result?.winnerId

    private fun topTwo(state: TournamentState, fx: List<TournamentFixture>, stage: String): List<String> =
        stageTable(state.copy(fixtures = fx), stage).map { it.team.id }.take(2)

    private fun addPlateFinal(state: TournamentState, fx: List<TournamentFixture>, ground: String): List<TournamentFixture> {
        if (fx.any { it.stage == PLATE_FINAL } || !complete(fx, PLATE)) return fx
        val top = topTwo(state, fx, PLATE)
        if (top.size < 2) return fx
        return fx + TournamentFixture(fx.size, fx.size + 1, PLATE_FINAL, top[0], top[1], ground)
    }

    private val SL_ROUNDS = listOf(listOf(0 to 1, 2 to 3), listOf(0 to 2, 1 to 3), listOf(0 to 3, 1 to 2))

    private fun advanceSmat(state: TournamentState): TournamentState {
        var fx = addPlateFinal(state, state.fixtures, SMAT_PLATE_FINAL_GROUND)
        if (ELITE.all { complete(fx, it) } && fx.none { it.stage == SL_A }) {
            val t = ELITE.map { topTwo(state, fx, it) }
            if (t.all { it.size == 2 }) {
                val a = listOf(t[0][0], t[1][0], t[2][1], t[3][1])
                val b = listOf(t[2][0], t[3][0], t[0][1], t[1][1])
                for (round in SL_ROUNDS) {
                    for ((x, y) in round) {
                        fx = fx + TournamentFixture(fx.size, fx.size + 1, SL_A, a[x], a[y], SMAT_KNOCKOUT_GROUND)
                        fx = fx + TournamentFixture(fx.size, fx.size + 1, SL_B, b[x], b[y], SMAT_KNOCKOUT_GROUND)
                    }
                }
            }
        }
        if (complete(fx, SL_A) && complete(fx, SL_B) && fx.none { it.stage == FINAL }) {
            val a = topTwo(state, fx, SL_A)
            val b = topTwo(state, fx, SL_B)
            if (a.isNotEmpty() && b.isNotEmpty()) {
                fx = fx + TournamentFixture(fx.size, fx.size + 1, FINAL, a[0], b[0], SMAT_KNOCKOUT_GROUND)
            }
        }
        return state.copy(fixtures = fx)
    }

    private fun advanceVht(state: TournamentState): TournamentState {
        var fx = addPlateFinal(state, state.fixtures, VHT_PLATE_FINAL_GROUND)
        fun add(stage: String, home: String?, away: String?) {
            if (home == null || away == null) return
            if (fx.any { it.stage == stage }) return
            fx = fx + TournamentFixture(fx.size, fx.size + 1, stage, home, away, VHT_KNOCKOUT_GROUND)
        }
        if (ELITE.all { complete(fx, it) }) {
            val t = ELITE.map { topTwo(state, fx, it) }
            fun at(g: Int, p: Int): String? = t[g].getOrNull(p)
            add("Quarter-final 1", at(0, 0), at(2, 1))
            add("Quarter-final 2", at(1, 0), at(3, 1))
            add("Quarter-final 3", at(2, 0), at(0, 1))
            add("Quarter-final 4", at(3, 0), at(1, 1))
        }
        add("Semi-final 1", winnerOf(fx, "Quarter-final 1"), winnerOf(fx, "Quarter-final 4"))
        add("Semi-final 2", winnerOf(fx, "Quarter-final 2"), winnerOf(fx, "Quarter-final 3"))
        add(FINAL, winnerOf(fx, "Semi-final 1"), winnerOf(fx, "Semi-final 2"))
        return state.copy(fixtures = fx)
    }

    /** Fills in the later stages as the teams become known. Safe after every match. */
    fun advance(state: TournamentState): TournamentState = when (state.tournamentId) {
        SMAT_ID -> advanceSmat(state)
        VHT_ID -> advanceVht(state)
        else -> state
    }

    // ---------------------------------------------------------------- the data

    // stage letter (A-D Elite groups, P Plate) | home team code (Ranji's "rj_" + code) | away team code | ground code
    private const val SMAT_TEXT = """
P|nag|meg|s
P|ap|man|s
A|cha|bar|m
B|sau|bih|v
B|rai|tn|v
B|pun|har|v
B|jak|del|v
C|mah|pon|n
D|hyd|mum|k
C|tri|and|p
A|miz|jha|w
D|guj|odi|e
P|ser|sik|s
A|raj|up|m
C|hp|ass|n
D|vid|goa|k
A|kar|ben|w
C|mp|ker|p
D|utt|chh|e
P|sik|ap|s
P|nag|man|s
A|raj|jha|m
B|rai|bih|v
B|jak|tn|v
B|sau|har|v
B|del|pun|v
C|hp|and|n
D|vid|odi|k
C|mp|ass|p
A|kar|up|w
D|utt|goa|e
P|ser|meg|s
A|miz|bar|m
C|tri|pon|n
D|guj|mum|k
A|ben|cha|w
C|ker|mah|p
D|chh|hyd|e
A|ben|jha|m
P|sik|nag|s
P|ser|ap|s
B|del|bih|v
B|pun|rai|v
B|tn|har|v
B|jak|sau|v
C|ker|and|n
D|chh|odi|k
D|hyd|vid|e
A|cha|raj|w
C|mah|hp|p
P|meg|man|s
A|up|bar|m
C|ass|pon|n
D|goa|mum|k
A|kar|miz|w
C|mp|tri|p
D|utt|guj|e
P|ap|meg|s
P|ser|nag|s
A|cha|up|m
B|pun|tn|v
B|jak|bih|v
B|del|sau|v
B|rai|har|v
C|mah|ass|n
D|hyd|goa|k
A|kar|jha|w
C|mp|and|p
D|utt|odi|e
P|sik|man|s
A|ben|miz|m
C|ker|tri|n
D|chh|guj|k
A|raj|bar|w
C|hp|pon|p
D|vid|mum|e
C|hp|tri|n
D|vid|guj|k
P|ser|man|s
P|sik|meg|s
A|raj|miz|m
B|rai|sau|v
B|del|har|v
B|jak|pun|v
B|tn|bih|v
C|ker|pon|p
D|chh|mum|e
A|ben|bar|w
P|ap|nag|s
A|kar|cha|m
C|mp|mah|n
D|utt|hyd|k
A|up|jha|w
C|ass|and|p
D|goa|odi|e
A|ben|up|m
B|del|tn|v
B|pun|sau|v
B|jak|rai|v
B|bih|har|v
C|ker|ass|n
D|chh|goa|k
A|cha|miz|w
C|mah|tri|p
D|hyd|guj|e
A|kar|raj|m
C|mp|hp|n
D|utt|vid|k
A|jha|bar|w
C|and|pon|p
D|odi|mum|e
A|kar|bar|m
B|jak|har|v
B|del|rai|v
B|pun|bih|v
B|sau|tn|v
C|mp|pon|n
D|utt|mum|k
C|ker|hp|p
D|chh|vid|e
A|ben|raj|w
A|cha|jha|m
C|mah|and|n
D|hyd|odi|k
A|miz|up|w
C|tri|ass|p
D|guj|goa|e
"""

    private const val VHT_TEXT = """
A|bar|vid|r
A|rai|utt|r
A|tn|bih|r
A|mp|and|r
B|har|hyd|a
B|jak|guj|a
B|sau|jha|a
B|ser|mum|a
C|mah|chh|b
C|pun|hp|b
C|cha|raj|b
C|goa|del|b
D|ass|ker|j
D|kar|tri|j
D|pon|odi|j
D|up|ben|j
P|sik|miz|i
P|meg|nag|i
P|ap|man|i
A|rai|and|r
A|utt|tn|r
A|mp|vid|r
A|bar|bih|r
B|jak|mum|a
B|guj|sau|a
B|ser|hyd|a
B|har|jha|a
C|pun|del|b
C|hp|cha|b
C|goa|chh|b
C|mah|raj|b
D|kar|ben|j
D|tri|pon|j
D|up|ker|j
D|ass|odi|j
P|nag|ap|i
P|meg|miz|i
P|sik|man|i
A|tn|mp|r
A|rai|bar|r
A|utt|vid|r
A|and|bih|r
B|sau|ser|a
B|jak|har|a
B|guj|hyd|a
B|mum|jha|a
C|cha|goa|b
C|pun|mah|b
C|hp|chh|b
C|del|raj|b
D|pon|up|j
D|kar|ass|j
D|tri|ker|j
D|ben|odi|j
P|nag|sik|i
P|miz|man|i
P|meg|ap|i
A|rai|vid|r
A|mp|bih|r
A|tn|and|r
A|utt|bar|r
B|jak|hyd|a
B|ser|jha|a
B|sau|mum|a
B|guj|har|a
C|pun|chh|b
C|goa|raj|b
C|cha|del|b
C|hp|mah|b
D|kar|ker|j
D|up|odi|j
D|pon|ben|j
D|tri|ass|j
P|ap|miz|i
P|nag|man|i
P|meg|sik|i
A|utt|bih|r
A|and|vid|r
A|mp|bar|r
A|rai|tn|r
B|guj|jha|a
B|mum|hyd|a
B|ser|har|a
B|jak|sau|a
C|hp|raj|b
C|del|chh|b
C|goa|mah|b
C|pun|cha|b
D|tri|odi|j
D|ben|ker|j
D|up|ass|j
D|kar|pon|j
P|meg|man|i
P|ap|sik|i
P|nag|miz|i
A|tn|bar|r
A|vid|bih|r
A|utt|and|r
A|rai|mp|r
B|sau|har|a
B|hyd|jha|a
B|guj|mum|a
B|jak|ser|a
C|cha|mah|b
C|chh|raj|b
C|hp|del|b
C|pun|goa|b
D|pon|ass|j
D|ker|odi|j
D|tri|ben|j
D|kar|up|j
A|utt|mp|r
A|bar|and|r
A|rai|bih|r
A|tn|vid|r
B|guj|ser|a
B|har|mum|a
B|jak|jha|a
B|sau|hyd|a
C|cha|chh|b
C|hp|goa|b
C|mah|del|b
C|pun|raj|b
D|tri|up|j
D|ass|ben|j
D|kar|odi|j
D|pon|ker|j
"""

    // state code | well-known players (copied from the game when it has them; a name it does not have is skipped)
    private const val STARS_TEXT = """
mum|Suryakumar Yadav;Shivam Dube;Shardul Thakur;Ajinkya Rahane;Sarfaraz Khan;Yashasvi Jaiswal;Shreyas Iyer;Rohit Sharma;Tushar Deshpande;Musheer Khan;Tanush Kotian;Ayush Mhatre;Siddhesh Lad;Shams Mulani
del|Virat Kohli;Rishabh Pant;Nitish Rana;Ishant Sharma;Harshit Rana;Ayush Badoni;Priyansh Arya
kar|KL Rahul;Devdutt Padikkal;Mayank Agarwal;Prasidh Krishna;Karun Nair;Abhinav Manohar
pun|Shubman Gill;Abhishek Sharma;Arshdeep Singh;Prabhsimran Singh;Nehal Wadhera;Harpreet Brar;Ramandeep Singh;Anmolpreet Singh;Mayank Markande
ker|Sanju Samson;Vishnu Vinod;Shreyas Gopal;Vignesh Puthur;Sachin Baby;Basil Thampi;Jalaj Saxena
tn|Varun Chakravarthy;Washington Sundar;Sai Sudharsan;Sai Kishore;Narayan Jagadeesan;Shahrukh Khan;T Natarajan;Vijay Shankar
har|Yuzvendra Chahal;Rahul Tewatia;Anshul Kamboj;Harshal Patel;Nishant Sindhu;Ankit Kumar;Himanshu Rana;Sumit Kumar
raj|Deepak Chahar;Rahul Chahar;Mahipal Lomror;Khaleel Ahmed;Kunal Singh Rathore;Ashok Sharma;Ravi Bishnoi
jha|Ishan Kishan;Kumar Kushagra;Anukul Roy;Shahbaz Nadeem;Robin Minz;Virat Singh
ben|Mohammed Shami;Akash Deep;Abhimanyu Easwaran;Shahbaz Ahmed;Sudip Kumar Gharami;Mukesh Kumar;Anustup Majumdar;Ishan Porel;Karan Lal
up|Rinku Singh;Dhruv Jurel;Bhuvneshwar Kumar;Kuldeep Yadav;Yash Dayal;Mohsin Khan;Madhav Kaushik;Shivam Mavi;Ankit Rajpoot;Priyam Garg;Sameer Rizvi;Shivam Sharma
guj|Jasprit Bumrah;Axar Patel;Urvil Patel;Arzan Nagwaswalla;Priyank Panchal;Chintan Gaja
bar|Hardik Pandya;Krunal Pandya;Deepak Hooda;Shivalik Sharma;Mahesh Pithiya
hyd|Tilak Varma;Mohammed Siraj;Tanmay Agarwal;Rahul Buddhi;Rohit Rayudu;Rahul Singh
sau|Ravindra Jadeja;Jaydev Unadkat;Cheteshwar Pujara;Chirag Jani;Prerak Mankad;Harvik Desai;Arpit Vasavada
mah|Ruturaj Gaikwad;Rahul Tripathi;Ankit Bawne;Prithvi Shaw;Mukesh Choudhary;Azim Kazi;Satyajeet Bachhav;Nikhil Naik
vid|Dhruv Shorey;Atharva Taide;Akshay Wadkar;Darshan Nalkande;Harsh Dubey;Aman Mokhade;Yash Thakur;Danish Malewar;Yash Rathod
mp|Rajat Patidar;Venkatesh Iyer;Avesh Khan;Kumar Kartikeya;Kuldeep Sen;Shubham Sharma;Harpreet Singh Bhatia;Arshad Khan;Himanshu Mantri
ass|Riyan Parag;Denish Das;Abhishek Thakuri;Mukhtar Hussain;Rishav Das
and|Nitish Kumar Reddy;Ricky Bhui;KS Bharat;Shaik Rasheed;Ashwin Hebbar
chh|Shashank Singh;Ajay Mandal;Amandeep Khare
rai|Ashutosh Sharma;Upendra Yadav;Karn Sharma;Pratham Singh;Himanshu Sangwan
jak|Abdul Samad;Auqib Nabi;Umran Malik;Parvez Rasool;Qamran Iqbal;Vanshaj Sharma;Shubham Khajuria;Yudhvir Singh
bih|Vaibhav Sooryavanshi;Sakibul Gani
goa|Arjun Tendulkar;Suyash Prabhudessai;Snehal Kauthankar
"""
}

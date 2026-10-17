package com.cricketsim.logic

import kotlin.random.Random

/**
 * The IPL season, played with the squads built in the IPL auction (IplAuction).
 *
 * It reuses the CPL tournament machinery as it is (TournamentState, CplSimulator, the Matches / Table /
 * Venues / Stats tabs, saving, scorecards, Player of the Match, the celebration). What is IPL-only is here:
 *
 *  - THE FORMAT, as the real IPL: ten teams in two groups of five. Every team plays the four others
 *    in its group twice, its "rival" in the other group twice and the other four once: 14 matches each,
 *    70 league matches in all. The top four go to the play-offs: Qualifier 1 (1st v 2nd), the Eliminator
 *    (3rd v 4th), Qualifier 2 and the Final. 74 matches. The order of play is different every season.
 *  - THE GROUNDS of the real IPL: each team plays at home (seven home matches each), Punjab also at
 *    Dharamshala, Delhi also at Visakhapatnam and Rajasthan also at Guwahati, as in recent seasons. The
 *    play-offs are at Mullanpur and Ahmedabad. Mullanpur is the only ground the game did not have.
 *  - THE CAPS: the Orange Cap (most runs) and the Purple Cap (most wickets).
 *
 * The squads are copied from the auction (nobody is recreated). Overseas players are tagged so the
 * "at most four overseas in the XI" rule applies; there is no breakout-player rule in the IPL.
 */
object IplSeason {

    const val TOURNAMENT_ID = "ipl2027"
    const val TOURNAMENT_NAME = "IPL 2027"

    private const val NEW_PCA = "new-pca-mullanpur"
    private const val FINAL_GROUND = "narendra-modi-ahmedabad"

    /** The one IPL ground the game did not have (Punjab Kings' home since 2024). */
    private val NEW_PCA_STADIUM = Stadium(
        id = NEW_PCA, name = "Maharaja Yadavindra Singh Stadium", city = "New Chandigarh, Mullanpur", country = "India",
        homeTeamIds = listOf("ipl_pbks"),
        pitchType = PitchType.BALANCED, pitchDescription = "A fair surface with some bounce; batters score freely once set, and the spinners get a little help.",
        boundarySize = BoundarySize.MEDIUM, altitudeM = 320, dewFactor = 0.4,
        avgT20FirstInningsScore = 172,
        climate = StadiumClimate(0.1, 40, 33, 14, "Hot and dry, with a low chance of rain.")
    )

    private val VENUE_IDS = listOf(
        "chepauk", "wankhede", "eden-gardens", "chinnaswamy", "uppal", FINAL_GROUND, "arun-jaitley",
        "sawai-mansingh-jaipur", "ekana-lucknow", NEW_PCA, "hpca-dharamsala", "barsapara-guwahati", "aca-vdca-vizag"
    )

    /** Every ground of the real IPL. */
    val stadiums: List<Stadium> by lazy {
        VENUE_IDS.mapNotNull { id -> if (id == NEW_PCA) NEW_PCA_STADIUM else StadiumData.getStadiumById(id) }
    }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    /** A team's main ground (Punjab now play at Mullanpur, not Mohali). */
    fun homeStadiumId(teamId: String): String =
        if (teamId == "ipl_pbks") NEW_PCA else IplData.team(teamId)?.homeStadiumId ?: "wankhede"

    // ---- Format -------------------------------------------------------------------------------

    private val GROUP_A = listOf("ipl_mi", "ipl_kkr", "ipl_rr", "ipl_dc", "ipl_lsg")
    private val GROUP_B = listOf("ipl_csk", "ipl_rcb", "ipl_pbks", "ipl_gt", "ipl_srh")
    private val RIVALS = listOf(
        "ipl_mi" to "ipl_csk", "ipl_kkr" to "ipl_rcb", "ipl_rr" to "ipl_pbks", "ipl_dc" to "ipl_gt", "ipl_lsg" to "ipl_srh"
    )

    /** A second ground for some teams' home matches: team, ground, how many of the seven home matches. */
    private val SECOND_GROUNDS = listOf(
        Triple("ipl_pbks", "hpca-dharamsala", 3),
        Triple("ipl_dc", "aca-vdca-vizag", 2),
        Triple("ipl_rr", "barsapara-guwahati", 2)
    )

    /** All 70 league matches as home-to-away pairs. */
    private fun buildPairs(random: Random): List<Pair<String, String>> {
        val pairs = ArrayList<Pair<String, String>>()
        val homes = HashMap<String, Int>()
        fun add(home: String, away: String) {
            pairs.add(home to away)
            homes[home] = (homes[home] ?: 0) + 1
        }
        // The four others in the group: home and away.
        for (group in listOf(GROUP_A, GROUP_B)) {
            for (i in group.indices) for (j in i + 1 until group.size) {
                add(group[i], group[j])
                add(group[j], group[i])
            }
        }
        // The rival in the other group: home and away.
        for ((a, b) in RIVALS) {
            add(a, b)
            add(b, a)
        }
        // The other four in the other group: once. The home side goes to whoever has had fewer home matches.
        val cross = ArrayList<Pair<String, String>>()
        for (a in GROUP_A) for (b in GROUP_B) {
            if (RIVALS.none { (x, y) -> (x == a && y == b) || (x == b && y == a) }) cross.add(a to b)
        }
        for ((a, b) in cross.shuffled(random)) {
            val ha = homes[a] ?: 0
            val hb = homes[b] ?: 0
            if (ha < hb || (ha == hb && random.nextBoolean())) add(a, b) else add(b, a)
        }
        return pairs
    }

    /**
     * 70 league matches in a random order that keeps teams from playing back to back and finishes
     * every team's fixtures at about the same time, then the four play-off matches.
     */
    fun buildSchedule(random: Random): List<TournamentFixture> {
        val remaining = buildPairs(random).shuffled(random).toMutableList()
        val left = HashMap<String, Int>()
        for ((a, b) in remaining) {
            left[a] = (left[a] ?: 0) + 1
            left[b] = (left[b] ?: 0) + 1
        }
        val ordered = ArrayList<Pair<String, String>>()
        var last: Pair<String, String>? = null
        while (remaining.isNotEmpty()) {
            val previous = last
            val fresh = remaining.filter {
                previous == null || (it.first != previous.first && it.first != previous.second &&
                    it.second != previous.first && it.second != previous.second)
            }
            val pool = if (fresh.isEmpty()) remaining else fresh
            val pick = pool.maxByOrNull { (left[it.first] ?: 0) + (left[it.second] ?: 0) + random.nextDouble() * 1.5 } ?: break
            remaining.remove(pick)
            left[pick.first] = (left[pick.first] ?: 1) - 1
            left[pick.second] = (left[pick.second] ?: 1) - 1
            ordered.add(pick)
            last = pick
        }
        val venues = ordered.map { homeStadiumId(it.first) }.toMutableList()
        for ((team, ground, count) in SECOND_GROUNDS) {
            ordered.indices.filter { ordered[it].first == team }.shuffled(random).take(count).forEach { venues[it] = ground }
        }
        val fixtures = ArrayList<TournamentFixture>()
        ordered.forEachIndexed { index, pair ->
            fixtures.add(TournamentFixture(index, index + 1, CplData.LEAGUE, pair.first, pair.second, venues[index]))
        }
        var next = fixtures.size
        fun playoff(stage: String, note: String, ground: String) {
            fixtures.add(TournamentFixture(next, next + 1, stage, null, null, ground, placeholder = note))
            next++
        }
        playoff("Qualifier 1", "1st v 2nd in the table", NEW_PCA)
        playoff("Eliminator", "3rd v 4th in the table", NEW_PCA)
        playoff("Qualifier 2", "Loser of Qualifier 1 v winner of the Eliminator", FINAL_GROUND)
        playoff("Final", "Winners of Qualifier 1 and Qualifier 2", FINAL_GROUND)
        return fixtures
    }

    // ---- Teams from the auction ---------------------------------------------------------------

    private fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)

    /** The ten squads exactly as the auction ended, and who is overseas. */
    fun buildTeams(auction: IplAuctionState): Pair<List<Team>, Map<String, SquadTag>> {
        val tags = LinkedHashMap<String, SquadTag>()
        val teams = IplData.TEAMS.map { info ->
            val bought = auction.squads[info.id].orEmpty().mapNotNull { IplAuction.playerOf(auction, it) }
            val players = bought.mapIndexed { index, ap ->
                val id = "${info.id}_${index + 1}"
                tags[id] = if (ap.overseas) SquadTag.OVERSEAS else SquadTag.DOMESTIC
                ap.player.copy(id = id, teamId = info.id)
            }.sortedByDescending { maxOf(it.battingRating, it.bowlingRating) }
            Team(
                id = info.id, name = info.name, countryCode = info.id.removePrefix("ipl_").uppercase(),
                battingRating = avg(players.map { it.battingRating }.sortedDescending().take(8)),
                bowlingRating = avg(players.map { it.bowlingRating }.sortedDescending().take(5)),
                fieldingRating = avg(players.map { it.fieldingRating }),
                players = players
            )
        }
        return Pair(teams, tags)
    }

    fun newTournament(auction: IplAuctionState, random: Random): TournamentState {
        val built = buildTeams(auction)
        return TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = TOURNAMENT_ID,
            name = TOURNAMENT_NAME,
            userTeamId = auction.userTeamId,
            teams = built.first,
            tags = built.second,
            fixtures = buildSchedule(random),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )
    }

    // ---- The caps -----------------------------------------------------------------------------

    /** Orange Cap: the most runs (fewer balls faced wins a tie). */
    fun orangeCap(stats: Collection<PlayerStats>): PlayerStats? =
        stats.filter { it.runs > 0 }.maxWithOrNull(compareBy<PlayerStats>({ it.runs }, { -it.balls }))

    /** Purple Cap: the most wickets (fewer runs conceded wins a tie). */
    fun purpleCap(stats: Collection<PlayerStats>): PlayerStats? =
        stats.filter { it.wickets > 0 }.maxWithOrNull(compareBy<PlayerStats>({ it.wickets }, { -it.runsConceded }))
}

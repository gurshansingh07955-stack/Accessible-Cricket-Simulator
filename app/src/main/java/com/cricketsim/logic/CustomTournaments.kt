package com.cricketsim.logic

import kotlin.random.Random

/**
 * Playing a custom tournament (Tournament > Custom tournaments): building its teams and fixtures from the
 * [CustomTournamentDef] the player saved, moving winners on, and its tables. The rules its matches are played by
 * are in [TournamentRules].
 */
object CustomTournaments {
    fun def(id: String): CustomTournamentDef? = Customisation.customTournaments().find { it.id == id }

    fun nameOf(id: String): String = def(id)?.name ?: "Custom tournament"

    fun scheduleOf(d: CustomTournamentDef): ScheduleType =
        try { ScheduleType.valueOf(d.schedule) } catch (e: Exception) { ScheduleType.SINGLE_RR }

    fun introOf(id: String): String {
        val d = def(id) ?: return "This custom tournament could not be found."
        val format = TournamentFormats.find(d.formatKey, Customisation.customFormats())?.name ?: d.formatKey
        val s = scheduleOf(d)
        val n = d.teamKeys.size
        val drs = if (!d.drsEnabled) "DRS off" else if (d.drsReviews == CustomTournamentDef.UNLIMITED_REVIEWS) "DRS with unlimited reviews" else "DRS with ${d.drsReviews} review${if (d.drsReviews == 1) "" else "s"} an innings"
        return "$format, ${s.label}: $n teams, ${s.matchCount(n)} matches at ${d.stadiumIds.size} stadium${if (d.stadiumIds.size == 1) "" else "s"}. $drs, rain ${if (d.rainEnabled) "on" else "off"}."
    }

    fun stadiumsFor(tournamentId: String): List<Stadium>? =
        def(tournamentId)?.stadiumIds?.mapNotNull { StadiumData.getStadiumById(it) }

    /** A three-letter name for a custom team (the other teams already have theirs). */
    fun shortNameOrNull(teamId: String): String? =
        Customisation.customTeams().find { it.id == teamId }?.let { c ->
            c.name.filter { it.isLetter() }.take(3).uppercase().ifEmpty { "CUS" }
        }

    /** Round robin plays the table; a round robin with the IPL's playoffs uses the game's own play-off code. */
    fun usesDefaultPlayoffs(tournamentId: String): Boolean = def(tournamentId)?.let { scheduleOf(it) == ScheduleType.RR_IPL } ?: false

    /** The teams of a saved tournament (every squad taken as the game has it now), with plain squad tags. */
    fun buildTeams(id: String): Pair<List<Team>, Map<String, SquadTag>> {
        val d = def(id) ?: return Pair(emptyList(), emptyMap())
        val catalog = TeamCatalog.load().associateBy { it.key }
        val teams = d.teamKeys.mapNotNull { catalog[it]?.team }
        val tags = LinkedHashMap<String, SquadTag>()
        teams.forEach { t -> t.players.forEach { tags[it.id] = SquadTag.DOMESTIC } }
        return Pair(teams, tags)
    }

    fun newTournament(id: String, userTeamId: String, built: Pair<List<Team>, Map<String, SquadTag>>): TournamentState {
        val d = def(id)
        val fixtures = if (d == null) emptyList() else buildSchedule(d, built.first.map { it.id }, Random.Default)
        return TournamentState(
            version = 1, tournamentId = id, name = d?.name ?: "Custom tournament", userTeamId = userTeamId,
            teams = built.first, tags = built.second, fixtures = fixtures, playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )
    }

    // ---- Fixtures -----------------------------------------------------------------------------

    /** Every pair once, round by round (the circle method), so a team does not play twice running when it can be avoided. */
    private fun roundRobin(ids: List<String>): List<Pair<String, String>> {
        val rot = ids.toMutableList()
        if (rot.size % 2 == 1) rot.add("")
        val n = rot.size
        val out = ArrayList<Pair<String, String>>()
        repeat(n - 1) { round ->
            for (i in 0 until n / 2) {
                val a = rot[i]
                val b = rot[n - 1 - i]
                if (a.isNotEmpty() && b.isNotEmpty()) out.add(if ((i + round) % 2 == 0) a to b else b to a)
            }
            rot.add(1, rot.removeAt(n - 1))
        }
        return out
    }

    fun buildSchedule(d: CustomTournamentDef, teamIds: List<String>, random: Random): List<TournamentFixture> {
        val ids = teamIds.shuffled(random)
        val grounds = d.stadiumIds.ifEmpty { listOf("wankhede") }
        val list = ArrayList<TournamentFixture>()
        fun ground() = grounds[list.size % grounds.size]
        fun add(stage: String, home: String?, away: String?, note: String = "") {
            list.add(TournamentFixture(list.size, list.size + 1, stage, home, away, ground(), placeholder = note))
        }
        when (scheduleOf(d)) {
            ScheduleType.SINGLE_RR -> roundRobin(ids).forEach { add(CplData.LEAGUE, it.first, it.second) }
            ScheduleType.DOUBLE_RR -> {
                val first = roundRobin(ids)
                first.forEach { add(CplData.LEAGUE, it.first, it.second) }
                first.forEach { add(CplData.LEAGUE, it.second, it.first) }
            }
            ScheduleType.RR_FINAL -> {
                roundRobin(ids).forEach { add(CplData.LEAGUE, it.first, it.second) }
                add("Final", null, null, "1st v 2nd in the table")
            }
            ScheduleType.RR_SEMIS -> {
                roundRobin(ids).forEach { add(CplData.LEAGUE, it.first, it.second) }
                add("Semi-final 1", null, null, "1st v 4th in the table")
                add("Semi-final 2", null, null, "2nd v 3rd in the table")
                add("Final", null, null, "Winners of the two semi-finals")
            }
            ScheduleType.RR_IPL -> {
                roundRobin(ids).forEach { add(CplData.LEAGUE, it.first, it.second) }
                add("Qualifier 1", null, null, "1st v 2nd in the table")
                add("Eliminator", null, null, "3rd v 4th in the table")
                add("Qualifier 2", null, null, "Loser of Qualifier 1 v winner of the Eliminator")
                add("Final", null, null, "Winners of Qualifier 1 and Qualifier 2")
            }
            ScheduleType.GROUPS_SEMIS -> {
                val half = (ids.size + 1) / 2
                roundRobin(ids.take(half)).forEach { add("Group A", it.first, it.second) }
                roundRobin(ids.drop(half)).forEach { add("Group B", it.first, it.second) }
                add("Semi-final 1", null, null, "Group A winner v Group B runner-up")
                add("Semi-final 2", null, null, "Group B winner v Group A runner-up")
                add("Final", null, null, "Winners of the two semi-finals")
            }
            ScheduleType.KNOCKOUT -> buildKnockout(ids, ::add, { list.size + 1 })
        }
        return list
    }

    /** One place in a knockout round: a team that is known, or the winner of an earlier match (by its number). */
    private class Place(val team: String?, val match: Int?)

    private fun buildKnockout(ids: List<String>, add: (String, String?, String?, String) -> Unit, nextNumber: () -> Int) {
        val n = ids.size
        var p = 1
        while (p < n) p *= 2
        val byes = p - n
        // Byes go to the first teams of the (random) draw; the rest play the first round.
        val byeTeams = ids.take(byes).map { Place(it, null) }
        val playing = ids.drop(byes).map { Place(it, null) }
        fun round(stage: String, places: List<Place>): List<Place> {
            val winners = ArrayList<Place>()
            var i = 0
            while (i + 1 < places.size) {
                val a = places[i]
                val b = places[i + 1]
                val number = nextNumber()
                val parts = ArrayList<String>()
                if (a.match != null) parts.add("winner of match ${a.match}")
                if (b.match != null) parts.add("winner of match ${b.match}")
                val note = if (parts.isEmpty()) "" else parts.joinToString(" v ").replaceFirstChar { it.uppercase() }
                add(stage, a.team, b.team, note)
                winners.add(Place(null, number))
                i += 2
            }
            return winners
        }
        var current: List<Place>
        if (byes > 0) {
            val firstRound = round("Preliminary round", playing)
            // A bye team meets a first-round winner, so nobody has two byes in a row.
            val mixed = ArrayList<Place>()
            val a = byeTeams.iterator()
            val b = firstRound.iterator()
            while (a.hasNext() || b.hasNext()) {
                if (a.hasNext()) mixed.add(a.next())
                if (b.hasNext()) mixed.add(b.next())
            }
            current = mixed
        } else {
            current = playing
        }
        while (current.size > 1) {
            val matches = current.size / 2
            val stage = when (matches) {
                1 -> "Final"
                2 -> "Semi-final"
                4 -> "Quarter-final"
                else -> "Round of ${matches * 2}"
            }
            current = round(stage, current)
        }
    }

    // ---- Tables and progress ------------------------------------------------------------------

    private fun teamsOfStage(state: TournamentState, stage: String): Set<String> =
        state.fixtures.filter { it.stage == stage }.flatMap { listOfNotNull(it.homeId, it.awayId) }.toSet()

    private fun stageTable(state: TournamentState, stage: String): List<StandingRow> {
        val ids = teamsOfStage(state, stage)
        return CplData.standings(state, stage).filter { it.team.id in ids }
    }

    /** The tables to show: one league table, or the two groups; a knockout has none. */
    fun tables(state: TournamentState): List<Pair<String, List<StandingRow>>> {
        val d = def(state.tournamentId) ?: return emptyList()
        return when (scheduleOf(d)) {
            ScheduleType.KNOCKOUT -> emptyList()
            ScheduleType.GROUPS_SEMIS -> listOf("Group A", "Group B").filter { teamsOfStage(state, it).isNotEmpty() }.map { it to stageTable(state, it) }
            else -> listOf("League table" to CplData.standings(state))
        }
    }

    /** The winner of a tournament that has no Final: the team on top of the finished table. */
    fun leagueChampion(state: TournamentState): Team? {
        val d = def(state.tournamentId) ?: return null
        val s = scheduleOf(d)
        if (s != ScheduleType.SINGLE_RR && s != ScheduleType.DOUBLE_RR) return null
        val league = state.fixtures.filter { it.stage == CplData.LEAGUE }
        if (league.isEmpty() || league.any { it.result == null }) return null
        return CplData.standings(state).firstOrNull()?.team
    }

    /** Fills in the semi-finals, Final and knockout rounds as their teams become known. Safe after every match. */
    fun advance(state: TournamentState): TournamentState {
        val d = def(state.tournamentId) ?: return state
        var fx = state.fixtures
        fun complete(stage: String): Boolean {
            val l = fx.filter { it.stage == stage }
            return l.isNotEmpty() && l.all { it.result != null }
        }
        fun winner(f: TournamentFixture?): String? = f?.result?.winnerId
        fun fill(stage: String, home: String?, away: String?) {
            if (home == null || away == null) return
            fx = fx.map { f -> if (f.stage == stage && f.homeId == null) f.copy(homeId = home, awayId = away) else f }
        }
        fun stageFixture(stage: String) = fx.firstOrNull { it.stage == stage }
        when (scheduleOf(d)) {
            ScheduleType.SINGLE_RR, ScheduleType.DOUBLE_RR, ScheduleType.RR_IPL -> return state
            ScheduleType.RR_FINAL -> {
                if (complete(CplData.LEAGUE)) {
                    val t = CplData.standings(state)
                    if (t.size >= 2) fill("Final", t[0].team.id, t[1].team.id)
                }
            }
            ScheduleType.RR_SEMIS -> {
                if (complete(CplData.LEAGUE)) {
                    val t = CplData.standings(state)
                    if (t.size >= 4) {
                        fill("Semi-final 1", t[0].team.id, t[3].team.id)
                        fill("Semi-final 2", t[1].team.id, t[2].team.id)
                    }
                }
                if (winner(stageFixture("Semi-final 1")) != null && winner(stageFixture("Semi-final 2")) != null) {
                    fill("Final", winner(stageFixture("Semi-final 1")), winner(stageFixture("Semi-final 2")))
                }
            }
            ScheduleType.GROUPS_SEMIS -> {
                if (complete("Group A") && complete("Group B")) {
                    val a = stageTable(state, "Group A").map { it.team.id }
                    val b = stageTable(state, "Group B").map { it.team.id }
                    if (a.size >= 2 && b.size >= 2) {
                        fill("Semi-final 1", a[0], b[1])
                        fill("Semi-final 2", b[0], a[1])
                    }
                }
                if (winner(stageFixture("Semi-final 1")) != null && winner(stageFixture("Semi-final 2")) != null) {
                    fill("Final", winner(stageFixture("Semi-final 1")), winner(stageFixture("Semi-final 2")))
                }
            }
            ScheduleType.KNOCKOUT -> {
                // A place that was "winner of match N" is filled once match N has a result.
                val byNumber = fx.associateBy { it.number }
                fx = fx.map { f ->
                    if (f.homeId != null && f.awayId != null) return@map f
                    val nums = Regex("""[Mm]atch (\d+)""").findAll(f.placeholder).map { it.groupValues[1].toInt() }.toList()
                    var k = 0
                    val home = if (f.homeId == null) { val w = nums.getOrNull(k)?.let { winner(byNumber[it]) }; k++; w } else f.homeId
                    val away = if (f.awayId == null) { val w = nums.getOrNull(k)?.let { winner(byNumber[it]) }; k++; w } else f.awayId
                    if (home != f.homeId || away != f.awayId) f.copy(homeId = home, awayId = away) else f
                }
            }
        }
        return state.copy(fixtures = fx)
    }
}

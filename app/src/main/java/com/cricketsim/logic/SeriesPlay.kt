package com.cricketsim.logic

/**
 * Playing a saved series (patch 147, part 2). A series is played by the tournament screens: it becomes a (hidden) custom tournament whose
 * [CustomTournamentDef.series] is set, so its squads, fixtures, tables, saving and resuming all use the machinery custom tournaments already have.
 *
 * What is different about a series:
 *  - The fixtures: for every format ("leg") in turn, that many matches, the pairs of teams taking turns (two teams alternate home and away),
 *    at the ticked grounds in turn. A single-format series has one stage ("League"); a multi-format series has one stage per format ("Group T20", "Group ODI"...).
 *  - The rules of a match come from its stage: [rulesFor] (a Test is five days; a tied limited-overs match stands, no Super Over).
 *  - The tables: one per format (a multi-format series) or one series table; the winner is the team with most wins over the whole series, or nobody if two share the most.
 *  - No knockout: nothing is filled in after a match.
 */
object SeriesPlay {
    fun tournamentId(seriesId: String): String = TournamentRules.CUSTOM_PREFIX + seriesId

    /** The hidden custom tournament a series is played as. */
    fun defFor(series: SeriesDef): CustomTournamentDef = CustomTournamentDef(
        id = tournamentId(series.id),
        name = series.name,
        formatKey = series.legs.firstOrNull()?.formatKey ?: "T20",
        schedule = "SINGLE_RR",
        teamKeys = series.teamKeys,
        stadiumIds = series.stadiumIds,
        series = series
    )

    private fun title(key: String): String = when {
        key == SeriesRules.TEST_KEY -> "Test"
        key == "T20" -> "T20"
        key == "ODI" -> "ODI"
        key == "T10" -> "T10"
        key == "FIVE_OVERS" -> "Five-over"
        key == "ONE_OVER" -> "One-over"
        key.startsWith("custom:") -> Customisation.customFormats().find { it.id == key.removePrefix("custom:") }?.name ?: key
        else -> key
    }

    /** The stage of the matches of one format: "League" when the series has one format, "Group <format>" when it has several (a "Group" stage may end tied). */
    fun stageName(series: SeriesDef, legIndex: Int): String =
        if (series.legs.size <= 1) CplData.LEAGUE else "Group " + title(series.legs[legIndex].formatKey)

    private fun legIndexFor(series: SeriesDef, stage: String?): Int {
        if (series.legs.size <= 1 || stage == null) return 0
        return series.legs.indices.firstOrNull { stageName(series, it) == stage } ?: 0
    }

    /** The rules of the matches of a stage: the rules of its format, a tied limited-overs match stands. */
    fun rulesFor(series: SeriesDef, stage: String?): MatchRules? {
        val leg = series.legs.getOrNull(legIndexFor(series, stage)) ?: return null
        val base = TournamentRules.rulesForFormat(leg.formatKey) ?: return null
        return base.copy(noSuperOver = true)
    }

    fun introOf(series: SeriesDef): String {
        val names = SeriesRules.teamsFor(series.teamKeys).joinToString(", ") { it.name }
        val legs = series.legs.joinToString(", ") { "${it.matches} ${title(it.formatKey)} ${if (it.matches == 1) "match" else "matches"}" }
        val total = SeriesRules.totalMatches(series)
        val grounds = series.stadiumIds.size
        return "${SeriesRules.kindOf(series).label} series between $names: $legs ($total in all), at $grounds ground${if (grounds == 1) "" else "s"}."
    }

    /** Every pair once, round by round (the circle method). */
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

    /** The fixtures: each format in turn, its matches taking the pairs in turn (and swapping home and away on every second time round). */
    fun schedule(series: SeriesDef, teamIds: List<String>, stadiumIds: List<String>): List<TournamentFixture> {
        val grounds = stadiumIds.ifEmpty { listOf("wankhede") }
        val pairs = if (teamIds.size <= 2) listOf(teamIds[0] to teamIds[teamIds.size - 1]) else roundRobin(teamIds)
        val list = ArrayList<TournamentFixture>()
        series.legs.forEachIndexed { index, leg ->
            val stage = stageName(series, index)
            repeat(leg.matches) { k ->
                val p = pairs[k % pairs.size]
                val flip = (k / pairs.size) % 2 == 1
                val home = if (flip) p.second else p.first
                val away = if (flip) p.first else p.second
                list.add(TournamentFixture(list.size, list.size + 1, stage, home, away, grounds[list.size % grounds.size]))
            }
        }
        return list
    }

    private fun stageTable(state: TournamentState, stage: String): List<StandingRow> {
        val ids = state.fixtures.filter { it.stage == stage }.flatMap { listOfNotNull(it.homeId, it.awayId) }.toSet()
        return CplData.standings(state, stage).filter { it.team.id in ids }
    }

    /** One table for each format (or one series table). */
    fun tables(state: TournamentState): List<Pair<String, List<StandingRow>>> {
        val stages = state.fixtures.map { it.stage }.distinct()
        return stages.map { (if (it == CplData.LEAGUE) "Series table" else it) to stageTable(state, it) }
    }

    /** The team with most wins over the whole series once every match is played; null while it is going on, or when two teams share the most wins. */
    fun champion(state: TournamentState): Team? {
        if (state.fixtures.isEmpty() || state.fixtures.any { it.result == null }) return null
        val wins = HashMap<String, Int>()
        state.fixtures.forEach { f -> f.result?.winnerId?.let { wins[it] = (wins[it] ?: 0) + 1 } }
        val best = wins.values.maxOrNull() ?: return null
        val top = wins.filterValues { it == best }.keys
        if (top.size != 1) return null
        return state.teams.find { it.id == top.first() }
    }
}

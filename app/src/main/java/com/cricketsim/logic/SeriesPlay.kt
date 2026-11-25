package com.cricketsim.logic

/**
 * Playing a saved series (patch 147, part 2). A series is played by the tournament screens: it becomes a (hidden) custom tournament whose
 * [CustomTournamentDef.series] is set, so its squads, fixtures, tables, saving and resuming all use the machinery custom tournaments already have.
 *
 * What is different about a series:
 *  - One vs one: for every format ("leg") in turn, the number of matches the player chose, the two teams swapping home and away.
 *  - Triangular and quadrangular: for every format in turn, a round robin (every team plays every other once) and then a Final between the top two of that
 *    format's table, filled in by [advance] once the round robin is played. Then the next format's round robin and Final.
 *  - Stages: a single-format series has "League" (and "Final"); a multi-format series has "Group T20", "T20 Final", "Group ODI", "ODI Final"...
 *  - The rules of a match come from its stage: [rulesFor] (a Test is five days; a tied limited-overs match stands, except in a Final, where a Super Over is played).
 *  - The tables: one per format (a multi-format series) or one series table. The winner: the winner of the Final (most finals won in a multi-format series), then
 *    most wins over the whole series, or nobody if still level.
 *  - The grounds are the ticked grounds of the one host country, used in turn.
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

    private fun oneVsOne(series: SeriesDef): Boolean = SeriesRules.kindOf(series) == SeriesKind.ONE_VS_ONE

    /** The stage of the matches of one format: "League" when the series has one format, "Group <format>" when it has several (a "Group" stage may end tied). */
    fun stageName(series: SeriesDef, legIndex: Int): String =
        if (series.legs.size <= 1) CplData.LEAGUE else "Group " + title(series.legs[legIndex].formatKey)

    /** The stage of the Final of one format (triangular and quadrangular series only). */
    fun finalStageName(series: SeriesDef, legIndex: Int): String =
        if (series.legs.size <= 1) "Final" else title(series.legs[legIndex].formatKey) + " Final"

    private fun isFinalStage(series: SeriesDef, stage: String?): Boolean =
        !oneVsOne(series) && stage != null && series.legs.indices.any { finalStageName(series, it) == stage }

    private fun legIndexFor(series: SeriesDef, stage: String?): Int {
        if (series.legs.size <= 1 || stage == null) return 0
        return series.legs.indices.firstOrNull { stageName(series, it) == stage || finalStageName(series, it) == stage } ?: 0
    }

    /** The rules of the matches of a stage: the rules of its format; a tied limited-overs match stands, except in a Final (it goes to a Super Over). */
    fun rulesFor(series: SeriesDef, stage: String?): MatchRules? {
        val leg = series.legs.getOrNull(legIndexFor(series, stage)) ?: return null
        val base = TournamentRules.rulesForFormat(leg.formatKey) ?: return null
        return if (isFinalStage(series, stage)) base else base.copy(noSuperOver = true)
    }

    fun introOf(series: SeriesDef): String {
        val names = SeriesRules.teamsFor(series.teamKeys).joinToString(", ") { it.name }
        val kind = SeriesRules.kindOf(series)
        val legs = if (kind == SeriesKind.ONE_VS_ONE) {
            series.legs.joinToString(", ") { "${it.matches} ${title(it.formatKey)} ${if (it.matches == 1) "match" else "matches"}" }
        } else {
            series.legs.joinToString("; ") { "${title(it.formatKey)}: round robin, then a final" }
        }
        val total = SeriesRules.totalMatches(series)
        val grounds = series.stadiumIds.size
        return "${kind.label} series between $names: $legs ($total matches in all), at $grounds ground${if (grounds == 1) "" else "s"}."
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

    /**
     * The fixtures. One vs one: each format in turn, the chosen number of matches, the two teams swapping home and away. Triangular and quadrangular: each
     * format in turn, a round robin and then its Final (the two teams are filled in by [advance] when the round robin is over).
     */
    fun schedule(series: SeriesDef, teamIds: List<String>, stadiumIds: List<String>): List<TournamentFixture> {
        val grounds = stadiumIds.ifEmpty { listOf("wankhede") }
        val list = ArrayList<TournamentFixture>()
        series.legs.forEachIndexed { index, leg ->
            val stage = stageName(series, index)
            if (oneVsOne(series)) {
                repeat(leg.matches) { k ->
                    val home = if (k % 2 == 1) teamIds[teamIds.size - 1] else teamIds[0]
                    val away = if (k % 2 == 1) teamIds[0] else teamIds[teamIds.size - 1]
                    list.add(TournamentFixture(list.size, list.size + 1, stage, home, away, grounds[list.size % grounds.size]))
                }
            } else {
                roundRobin(teamIds).forEach { pair ->
                    list.add(TournamentFixture(list.size, list.size + 1, stage, pair.first, pair.second, grounds[list.size % grounds.size]))
                }
                list.add(TournamentFixture(list.size, list.size + 1, finalStageName(series, index), null, null, grounds[list.size % grounds.size], placeholder = "The top two teams of the round robin"))
            }
        }
        return list
    }

    /** Fills in the two teams of a Final once the round robin of its format is played: the top two of that format's table. */
    fun advance(state: TournamentState, series: SeriesDef): TournamentState {
        if (oneVsOne(series)) return state
        var fixtures = state.fixtures
        series.legs.indices.forEach { index ->
            val roundRobinStage = stageName(series, index)
            val finalStage = finalStageName(series, index)
            val played = fixtures.filter { it.stage == roundRobinStage }
            if (played.isNotEmpty() && played.all { it.result != null }) {
                val table = stageTable(state, roundRobinStage)
                if (table.size >= 2) {
                    val first = table[0].team.id
                    val second = table[1].team.id
                    fixtures = fixtures.map { f -> if (f.stage == finalStage && f.homeId == null) f.copy(homeId = first, awayId = second) else f }
                }
            }
        }
        return state.copy(fixtures = fixtures)
    }

    private fun stageTable(state: TournamentState, stage: String): List<StandingRow> {
        val ids = state.fixtures.filter { it.stage == stage }.flatMap { listOfNotNull(it.homeId, it.awayId) }.toSet()
        return CplData.standings(state, stage).filter { it.team.id in ids }
    }

    private fun isFinalName(stage: String): Boolean = stage == "Final" || stage.endsWith(" Final")

    /** One table for each format (or one series table); a Final is one match and has no table. */
    fun tables(state: TournamentState): List<Pair<String, List<StandingRow>>> {
        val stages = state.fixtures.map { it.stage }.distinct().filter { !isFinalName(it) }
        return stages.map { (if (it == CplData.LEAGUE) "Series table" else it) to stageTable(state, it) }
    }

    private fun uniqueTop(wins: Map<String, Int>): String? {
        val best = wins.values.maxOrNull() ?: return null
        val top = wins.filterValues { it == best }.keys
        return if (top.size == 1) top.first() else null
    }

    /**
     * The winner once every match is played. With Finals (triangular, quadrangular): the team that won the Final, or the most Finals; if that is level, the team
     * with most wins over the whole series. One vs one: most wins. Null while it is going on, or when still level (a drawn series).
     */
    fun champion(state: TournamentState): Team? {
        if (state.fixtures.isEmpty() || state.fixtures.any { it.result == null }) return null
        val all = HashMap<String, Int>()
        val finals = HashMap<String, Int>()
        state.fixtures.forEach { f ->
            f.result?.winnerId?.let { id ->
                all[id] = (all[id] ?: 0) + 1
                if (isFinalName(f.stage)) finals[id] = (finals[id] ?: 0) + 1
            }
        }
        val id = uniqueTop(finals) ?: uniqueTop(all) ?: return null
        return state.teams.find { it.id == id }
    }
}

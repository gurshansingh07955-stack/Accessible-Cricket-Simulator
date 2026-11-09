package com.cricketsim.logic

import java.util.Locale

/**
 * The points tables of a career competition, as lines of text. A first-class tournament shows the points the game gives (a win, a draw, the bonus points);
 * the others show one table for each group or stage that is not a knockout (played, won, lost, no result, points, net run rate). The player's own club or
 * team is marked by the screen (each row carries the team id).
 */
object CareerTables {

    class Table(val title: String, val rows: List<Pair<String, String>>)

    private val NOT_A_TABLE = listOf(
        "final", "qualifier", "eliminator", "knockout", "challenger", "semi", "quarter", "bronze",
        "play-off", "playoff", "preliminary", "medal"
    )

    fun tables(state: TournamentState): List<Table> {
        val out = ArrayList<Table>()
        val multiDay = CareerSeason.competitionOf(state).days > 1
        val firstClass = if (CareerSeason.isClub(state)) emptyList() else state.fixtures.filter { it.result?.fcKind?.isNotEmpty() == true }
        if (firstClass.isNotEmpty()) {
            class Row(val id: String, val name: String) {
                var played = 0
                var won = 0
                var drawn = 0
                var points = 0
            }
            val rows = LinkedHashMap<String, Row>()
            for (f in firstClass) {
                val r = f.result ?: continue
                for ((id, pts) in listOf(f.homeId to r.homePoints, f.awayId to r.awayPoints)) {
                    if (id == null) continue
                    val teamName = state.teams.find { it.id == id }?.name ?: continue
                    val row = rows.getOrPut(id) { Row(id, teamName) }
                    row.played++
                    row.points += pts
                    if (r.winnerId == id) row.won++ else if (r.winnerId == null && r.fcKind == "DRAW") row.drawn++
                }
            }
            val sorted = rows.values.sortedWith(compareByDescending<Row> { it.points }.thenByDescending { it.won }.thenBy { it.name })
            out.add(Table("Points table", sorted.mapIndexed { i, row ->
                row.id to "${i + 1}. ${row.name}: played ${row.played}, won ${row.won}, drawn ${row.drawn}, points ${row.points}"
            }))
            return out
        }
        val stages = state.fixtures.map { it.stage }.distinct()
            .filter { stage -> NOT_A_TABLE.none { stage.lowercase(Locale.ROOT).contains(it) } }
        for (stage in stages) {
            val ids = state.fixtures.filter { it.stage == stage }.flatMap { listOfNotNull(it.homeId, it.awayId) }.toSet()
            val table = CplData.standings(state, stage).filter { it.team.id in ids && it.played > 0 }
            if (table.isEmpty()) continue
            out.add(Table(if (stages.size > 1) stage else "Points table", table.mapIndexed { i, r ->
                val nrr = String.format(Locale.US, "%+.3f", r.netRunRate)
                r.team.id to "${i + 1}. ${r.team.name}: played ${r.played}, won ${r.won}, lost ${r.lost}, " +
                    "${if (multiDay) "drawn" else "no result"} ${r.noResult}, points ${r.points}" + (if (multiDay) "" else ", net run rate $nrr")
            }))
        }
        return out
    }
}

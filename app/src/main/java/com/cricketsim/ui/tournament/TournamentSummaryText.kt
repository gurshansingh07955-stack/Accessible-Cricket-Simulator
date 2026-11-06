package com.cricketsim.ui.tournament

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.cricketsim.logic.CplData
import com.cricketsim.logic.CplSimulator
import com.cricketsim.logic.IplSeason
import com.cricketsim.logic.TournamentState
import java.util.Locale

/**
 * The finished tournament as clear PLAIN TEXT, for the Share and Copy buttons of the celebration screen: the champions,
 * the runners-up, the Final, the Player of the Match in the Final, the Player of the Series, your team's record, the points
 * table (or tables) and the leaders of every statistic.
 */
internal object TournamentSummaryText {

    // A stage with one of these words in its name is a knockout match, not a table.
    private val NOT_A_TABLE = listOf(
        "final", "qualifier", "eliminator", "knockout", "challenger", "semi", "quarter", "bronze",
        "play-off", "playoff", "preliminary", "medal"
    )

    private fun nrr(v: Double): String = String.format(Locale.US, "%+.3f", v)

    fun build(state: TournamentState): String {
        val sb = StringBuilder()
        sb.append(state.name.uppercase(Locale.ROOT)).append(": TOURNAMENT SUMMARY\n\n")

        // ---- The result
        val champion = CplSimulator.champion(state)
        if (champion != null) sb.append("Champions: ").append(champion.name).append("\n")
        val finalMatch = state.fixtures.firstOrNull { it.stage == "Final" }
        val finalResult = finalMatch?.result
        if (finalMatch != null && finalResult != null) {
            val winnerId = finalResult.winnerId
            if (winnerId != null) {
                val loserId = if (finalMatch.homeId == winnerId) finalMatch.awayId else finalMatch.homeId
                state.teams.find { it.id == loserId }?.let { sb.append("Runners-up: ").append(it.name).append("\n") }
            }
            if (finalResult.summary.isNotBlank()) sb.append("Final: ").append(finalResult.summary).append("\n")
            val finalAward = finalResult.playerOfMatch
            if (!finalAward.isNullOrEmpty()) sb.append("Player of the Match in the Final: ").append(finalAward).append("\n")
        }
        val series = CplSimulator.playerOfTheSeries(state)
        if (series != null) {
            sb.append("Player of the Series: ").append(series.name).append(", ").append(CplData.shortName(series.teamId))
                .append(", with ").append(series.runs).append(" runs and ").append(series.wickets).append(" wickets\n")
        }
        val isIpl = state.tournamentId == IplSeason.TOURNAMENT_ID
        if (isIpl) {
            IplSeason.orangeCap(state.playerStats.values)?.let {
                sb.append("Orange Cap (most runs): ").append(it.name).append(", ").append(CplData.shortName(it.teamId))
                    .append(", ").append(it.runs).append(" runs\n")
            }
            IplSeason.purpleCap(state.playerStats.values)?.let {
                sb.append("Purple Cap (most wickets): ").append(it.name).append(", ").append(CplData.shortName(it.teamId))
                    .append(", ").append(it.wickets).append(" wickets\n")
            }
        }

        // ---- Your team
        val userName = state.teams.find { it.id == state.userTeamId }?.name
        if (userName != null) {
            val mine = state.fixtures.filter { it.result != null && (it.homeId == state.userTeamId || it.awayId == state.userTeamId) }
            val won = mine.count { it.result?.winnerId == state.userTeamId }
            val noResult = mine.count { it.result?.winnerId == null }
            val lost = mine.size - won - noResult
            sb.append("\nYOUR TEAM\n").append(userName).append(": played ").append(mine.size).append(", won ").append(won)
                .append(", lost ").append(lost).append(", no result or drawn ").append(noResult).append("\n")
        }

        // ---- The points table(s)
        val firstClass = state.fixtures.filter { it.result?.fcKind?.isNotEmpty() == true }
        if (firstClass.isNotEmpty()) {
            class Row(val name: String) {
                var played = 0
                var won = 0
                var drawn = 0
                var points = 0
            }
            val rows = HashMap<String, Row>()
            for (f in firstClass) {
                val r = f.result ?: continue
                for ((id, pts) in listOf(f.homeId to r.homePoints, f.awayId to r.awayPoints)) {
                    if (id == null) continue
                    val teamName = state.teams.find { it.id == id }?.name ?: continue
                    val row = rows.getOrPut(id) { Row(teamName) }
                    row.played++
                    row.points += pts
                    if (r.winnerId == id) row.won++ else if (r.winnerId == null && r.fcKind == "DRAW") row.drawn++
                }
            }
            sb.append("\nPOINTS TABLE\n")
            rows.values.sortedWith(compareByDescending<Row> { it.points }.thenByDescending { it.won }.thenBy { it.name })
                .forEachIndexed { i, row ->
                    sb.append(i + 1).append(". ").append(row.name).append(": played ").append(row.played).append(", won ").append(row.won)
                        .append(", drawn ").append(row.drawn).append(", points ").append(row.points).append("\n")
                }
        } else {
            val stages = state.fixtures.map { it.stage }.distinct()
                .filter { stage -> NOT_A_TABLE.none { stage.lowercase(Locale.ROOT).contains(it) } }
            for (stage in stages) {
                val ids = state.fixtures.filter { it.stage == stage }.flatMap { listOfNotNull(it.homeId, it.awayId) }.toSet()
                val table = CplData.standings(state, stage).filter { it.team.id in ids && it.played > 0 }
                if (table.isEmpty()) continue
                sb.append("\n").append(if (stages.size > 1) stage.uppercase(Locale.ROOT) + " TABLE" else "POINTS TABLE").append("\n")
                table.forEachIndexed { i, r ->
                    sb.append(i + 1).append(". ").append(r.team.name).append(": played ").append(r.played).append(", won ").append(r.won)
                        .append(", lost ").append(r.lost).append(", no result ").append(r.noResult).append(", points ").append(r.points)
                        .append(", net run rate ").append(nrr(r.netRunRate)).append("\n")
                }
            }
        }

        // ---- The statistics
        val categories = CplData.statCategories(state.playerStats.values, teamNameOf = { CplData.shortName(it) }, caps = isIpl)
            .filter { it.entries.isNotEmpty() }
        if (categories.isNotEmpty()) {
            sb.append("\nTOURNAMENT STATS (top five of each)\n")
            for (c in categories) {
                sb.append("\n").append(c.title).append("\n")
                c.entries.forEachIndexed { i, e ->
                    sb.append(i + 1).append(". ").append(e.name).append(", ").append(e.teamName).append(": ").append(e.value).append("\n")
                }
            }
        }
        return sb.toString().trimEnd() + "\n"
    }

    /** Opens the phone's share sheet with the summary. */
    fun share(context: Context, subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, "Share the tournament summary").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { context.startActivity(chooser) }
    }

    /** Puts the summary on the clipboard and says so. */
    fun copy(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Tournament summary", text))
        Toast.makeText(context, "The tournament summary is copied. You can paste it anywhere.", Toast.LENGTH_SHORT).show()
    }
}

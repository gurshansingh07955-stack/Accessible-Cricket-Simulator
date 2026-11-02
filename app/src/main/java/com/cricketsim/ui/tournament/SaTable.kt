package com.cricketsim.ui.tournament

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.CplData
import com.cricketsim.logic.SaDomestic
import com.cricketsim.logic.StandingRow
import com.cricketsim.logic.TournamentState

/**
 * The tables of the South African competitions: the 4-Day Series (points in hundredths), the Pro50 Cup, and the Pro20 Cup's two pools
 * and Super Eights. One line per team that a screen reader reads whole.
 */
@Composable
fun SaTableTab(state: TournamentState) {
    val id = state.tournamentId
    val fourDay = remember(state) { if (SaDomestic.isFourDay(id)) SaDomestic.fourDayTable(state) else emptyList() }
    val oneDay: List<Pair<String, List<StandingRow>>> = remember(state) {
        when {
            SaDomestic.isOdc(id) -> listOf("Pro50 Cup table" to SaDomestic.stageTable(state, CplData.LEAGUE))
            SaDomestic.isT20(id) -> SaDomestic.t20Tables(state)
            else -> emptyList()
        }
    }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = SaDomestic.introOf(id), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
        if (fourDay.isNotEmpty()) {
            Text(text = "4-Day Series table", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(modifier = Modifier.height(4.dp))
            fourDay.forEachIndexed { index, row ->
                val mine = if (row.team.id == state.userTeamId) " Your team." else ""
                Text(
                    text = "${index + 1}. ${row.team.name}. Played ${row.played}, won ${row.won}, drawn ${row.drawn}, lost ${row.lost}" +
                        (if (row.tied > 0) ", tied ${row.tied}" else "") + ". ${SaDomestic.format(row.points)} points.$mine",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
        oneDay.forEach { (title, rows) ->
            Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(modifier = Modifier.height(4.dp))
            rows.forEachIndexed { index, row ->
                val mine = if (row.team.id == state.userTeamId) " Your team." else ""
                Text(
                    text = "${index + 1}. ${row.team.name}. Played ${row.played}, won ${row.won}, lost ${row.lost}, no result ${row.noResult}" +
                        ". ${row.points} points. Net run rate ${"%.3f".format(row.netRunRate)}.$mine",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

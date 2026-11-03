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
import com.cricketsim.logic.TournamentState
import com.cricketsim.logic.WiDomestic

/**
 * The table of the West Indies Championship or the Super50 Cup: one line per team that a screen reader reads whole
 * ("1. Guyana Harpy Eagles. Played 3, won 2, drawn 1, lost 0. 41 points.").
 */
@Composable
fun WiTableTab(state: TournamentState) {
    val id = state.tournamentId
    val fourDay = WiDomestic.isFourDay(id)
    val fcRows = remember(state) { if (fourDay) WiDomestic.fourDayTable(state) else emptyList() }
    val oneDay = remember(state) { if (fourDay) emptyList() else WiDomestic.odcTable(state) }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = WiDomestic.introOf(id), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Table", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(modifier = Modifier.height(4.dp))
        if (fourDay) {
            fcRows.forEachIndexed { index, row ->
                val mine = if (row.team.id == state.userTeamId) " Your team." else ""
                Text(
                    text = "${index + 1}. ${row.team.name}. Played ${row.played}, won ${row.won}, drawn ${row.drawn}, lost ${row.lost}" +
                        (if (row.tied > 0) ", tied ${row.tied}" else "") + ". ${row.points} points.$mine",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        } else {
            oneDay.forEachIndexed { index, row ->
                val mine = if (row.team.id == state.userTeamId) " Your team." else ""
                Text(
                    text = "${index + 1}. ${row.team.name}. Played ${row.played}, won ${row.won}, lost ${row.lost}, no result ${row.noResult}" +
                        ". ${row.points} points. Net run rate ${"%.3f".format(row.netRunRate)}.$mine",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

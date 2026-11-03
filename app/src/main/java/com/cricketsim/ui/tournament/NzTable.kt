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
import com.cricketsim.logic.NzDomestic
import com.cricketsim.logic.TournamentState

/**
 * The table of the Plunket Shield, the Ford Trophy or the Super Smash: one line per team that a screen reader reads whole
 * ("1. Canterbury Kings. Played 3, won 2, drawn 1, lost 0. 31 points."), and for the Plunket Shield the champions at the end.
 */
@Composable
fun NzTableTab(state: TournamentState) {
    val id = state.tournamentId
    val plunket = NzDomestic.isPlunket(id)
    val shield = remember(state) { if (plunket) NzDomestic.plunketTable(state) else emptyList() }
    val limited = remember(state) { if (plunket) emptyList() else NzDomestic.limitedTable(state) }
    val endLines = remember(state) { if (plunket) NzDomestic.seasonEndLines(state) else emptyList() }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = NzDomestic.introOf(id), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Table", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(modifier = Modifier.height(4.dp))
        if (plunket) {
            shield.forEachIndexed { index, row ->
                val mine = if (row.team.id == state.userTeamId) " Your team." else ""
                Text(
                    text = "${index + 1}. ${row.team.name}. Played ${row.played}, won ${row.won}, drawn ${row.drawn}, lost ${row.lost}" +
                        (if (row.tied > 0) ", tied ${row.tied}" else "") + ". ${row.points} points.$mine",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        } else {
            limited.forEachIndexed { index, row ->
                val mine = if (row.team.id == state.userTeamId) " Your team." else ""
                Text(
                    text = "${index + 1}. ${row.team.name}. Played ${row.played}, won ${row.won}, lost ${row.lost}, no result ${row.noResult}" +
                        ". ${row.points} points. Net run rate ${"%.3f".format(row.netRunRate)}.$mine",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
        if (endLines.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "End of the season", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            endLines.forEach { Text(text = it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
}

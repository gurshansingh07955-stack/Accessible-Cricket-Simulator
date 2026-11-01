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
import com.cricketsim.logic.AusDomestic
import com.cricketsim.logic.TournamentState

/**
 * The table of the Sheffield Shield or the Australian One-Day Cup: one line per state that a screen reader reads whole
 * ("1. South Australia. Played 3, won 2, drawn 1, lost 0. 13.42 points.").
 */
@Composable
fun AusTableTab(state: TournamentState) {
    val shield = AusDomestic.isShield(state.tournamentId)
    val shieldRows = remember(state) { if (shield) AusDomestic.shieldTable(state) else emptyList() }
    val odcRows = remember(state) { if (shield) emptyList() else AusDomestic.odcTable(state) }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = AusDomestic.introOf(state.tournamentId), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (shield) "Sheffield Shield table" else "One-Day Cup table",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (shield) {
            shieldRows.forEachIndexed { index, row ->
                val mine = if (row.team.id == state.userTeamId) " Your team." else ""
                Text(
                    text = "${index + 1}. ${row.team.name}. Played ${row.played}, won ${row.won}, drawn ${row.drawn}, lost ${row.lost}" +
                        (if (row.tied > 0) ", tied ${row.tied}" else "") +
                        ". ${AusDomestic.format(row.points)} points.$mine",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        } else {
            odcRows.forEachIndexed { index, row ->
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

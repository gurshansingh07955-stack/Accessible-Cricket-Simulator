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
import com.cricketsim.logic.PakDomestic
import com.cricketsim.logic.TournamentState

/**
 * The tables of the Quaid-e-Azam Trophy Gold (one table) or Silver (two group tables): one line per team that a screen reader reads whole
 * ("1. Karachi Blues. Played 3, won 2, drawn 1, lost 0. 41 points.").
 */
@Composable
fun PakTableTab(state: TournamentState) {
    val tables = remember(state) { PakDomestic.tables(state) }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = PakDomestic.introOf(state.tournamentId), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
        tables.forEach { (title, rows) ->
            Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(modifier = Modifier.height(4.dp))
            rows.forEachIndexed { index, row ->
                val mine = if (row.team.id == state.userTeamId) " Your team." else ""
                Text(
                    text = "${index + 1}. ${row.team.name}. Played ${row.played}, won ${row.won}, drawn ${row.drawn}, lost ${row.lost}" +
                        (if (row.tied > 0) ", tied ${row.tied}" else "") + ". ${row.points} points.$mine",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

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
import com.cricketsim.logic.BdDomestic
import com.cricketsim.logic.TournamentState

/**
 * The National Cricket League table: one line per division that a screen reader reads whole
 * ("1. Dhaka Division. Played 3, won 2, drawn 1, lost 0. 36 points."), and the champions at the end.
 */
@Composable
fun BdTableTab(state: TournamentState) {
    val rows = remember(state) { BdDomestic.table(state) }
    val endLines = remember(state) { BdDomestic.seasonEndLines(state) }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = BdDomestic.introOf(), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Table", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
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
        if (endLines.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "End of the season", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            endLines.forEach { Text(text = it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
}

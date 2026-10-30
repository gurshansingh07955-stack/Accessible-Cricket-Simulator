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
import com.cricketsim.logic.FirstClass
import com.cricketsim.logic.Ranji
import com.cricketsim.logic.TournamentState

/**
 * The Ranji Trophy's tables: one for each Elite group and the Plate group, each team as one line a screen reader reads whole
 * ("1. Mumbai. Played 3, won 2, drawn 1, lost 0. 15 points. Quotient 1.34.").
 */
@Composable
fun RanjiTableTab(state: TournamentState) {
    val tables = remember(state) { Ranji.STAGES.map { it to FirstClass.standings(state, it) } }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        tables.forEach { (stage, rows) ->
            Text(
                text = stage,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(4.dp))
            rows.forEachIndexed { index, row ->
                val mine = if (row.team.id == state.userTeamId) " Your team." else ""
                Text(
                    text = "${index + 1}. ${row.team.name}. Played ${row.played}, won ${row.won}, drawn ${row.drawn}, lost ${row.lost}" +
                        (if (row.tied > 0) ", tied ${row.tied}" else "") +
                        ". ${row.points} points. Quotient ${"%.3f".format(row.quotient)}.$mine",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

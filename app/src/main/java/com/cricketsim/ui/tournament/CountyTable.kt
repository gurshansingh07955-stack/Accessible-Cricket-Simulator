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
import com.cricketsim.logic.CountyCricket
import com.cricketsim.logic.TournamentState

/**
 * The County Championship's tables: Division One and Division Two, each team as one line a screen reader reads whole
 * ("1. Warwickshire. Played 3, won 2, drawn 1, lost 0. 52 points."), then the champions, promotion and relegation at the end.
 */
@Composable
fun CountyTableTab(state: TournamentState) {
    val tables = remember(state) { CountyCricket.championshipTables(state) }
    val endLines = remember(state) { CountyCricket.seasonEndLines(state) }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "16 points for a win, 8 each for a draw or a tie, plus batting bonus points (1 to 5 for 200 to 400 runs) and bowling bonus points (1 to 3 for 3, 6 or 9 wickets) from the first innings.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
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
                        ". ${row.points} points.$mine",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (endLines.isNotEmpty()) {
            Text(
                text = "End of the season",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            endLines.forEach {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

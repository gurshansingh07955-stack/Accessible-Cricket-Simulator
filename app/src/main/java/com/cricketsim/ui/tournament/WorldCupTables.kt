package com.cricketsim.ui.tournament

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.ChampionsTrophy
import com.cricketsim.logic.CplData
import com.cricketsim.logic.T20WorldCup
import com.cricketsim.logic.TournamentState

/**
 * The Table tab of the T20 World Cup: one table per group (A to D), then one per Super 8 group once the
 * groups are over. Each table has a heading a screen reader can jump to.
 */
@Composable
internal fun WorldCupTableTab(state: TournamentState) {
    val champions = state.tournamentId == ChampionsTrophy.TOURNAMENT_ID
    val tables = remember(state) { if (champions) ChampionsTrophy.tables(state) else T20WorldCup.tables(state) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "intro") {
            Text(
                text = if (champions) {
                    "Two points for a win, one each for no result. The top two of each group go to the semi-finals."
                } else {
                    "Two points for a win, one each for no result. The top two of each group of five go to the Super 8, " +
                        "and the top two of each Super 8 group go to the semi-finals."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        tables.forEach { (title, rows) ->
            item(key = "title_$title") {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 4.dp)
                        .semantics { heading() }
                )
            }
            item(key = "head_$title") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("#", modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.labelLarge)
                    Text("Team", modifier = Modifier.weight(2.6f), style = MaterialTheme.typography.labelLarge)
                    Text("P", modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.labelLarge)
                    Text("W", modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.labelLarge)
                    Text("L", modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.labelLarge)
                    Text("Pts", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
                    Text("NRR", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelLarge)
                }
            }
            itemsIndexed(rows, key = { _, r -> title + r.team.id }) { index, r ->
                val nrr = (if (r.netRunRate >= 0) "+" else "") + String.format(java.util.Locale.US, "%.3f", r.netRunRate)
                val mine = r.team.id == state.userTeamId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("${index + 1}", modifier = Modifier.weight(0.5f))
                    Text(
                        text = CplData.shortName(r.team.id) + if (mine) " (you)" else "",
                        modifier = Modifier.weight(2.6f),
                        fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal
                    )
                    Text("${r.played}", modifier = Modifier.weight(0.6f))
                    Text("${r.won}", modifier = Modifier.weight(0.6f))
                    Text("${r.lost}", modifier = Modifier.weight(0.6f))
                    Text("${r.points}", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold)
                    Text(nrr, modifier = Modifier.weight(1.2f))
                }
            }
        }
    }
}

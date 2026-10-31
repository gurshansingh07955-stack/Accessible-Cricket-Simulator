package com.cricketsim.ui.tournament

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.DomesticFc
import com.cricketsim.logic.TournamentState

/**
 * The "Table" page of the Duleep Trophy and the Irani Cup, which have no table: how the competition works, then each match
 * with its stage, the two sides and the result, in order.
 */
@Composable
fun DomesticKnockoutTab(state: TournamentState) {
    val intro = if (state.tournamentId == DomesticFc.IRANI_ID) {
        "The Irani Cup is one five-day match between the Ranji Trophy champions, Jammu & Kashmir, and Rest of India. If it is drawn, the side ahead on the first innings wins the cup."
    } else {
        "The Duleep Trophy is a knockout between six zones. East meet North East and North meet West in the quarter-finals; Central then meet the winner of the first and South the winner of the second in the semi-finals; the winners play the Final at Chepauk. Four days each. A drawn match goes to the side ahead on the first innings."
    }
    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Text(text = intro, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Matches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        state.fixtures.forEach { f ->
            val home = state.teams.find { it.id == f.homeId }?.name ?: "To be decided"
            val away = state.teams.find { it.id == f.awayId }?.name ?: "To be decided"
            Text(
                text = "${f.stage}: $home v $away. ${f.result?.summary ?: "Not played yet."}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

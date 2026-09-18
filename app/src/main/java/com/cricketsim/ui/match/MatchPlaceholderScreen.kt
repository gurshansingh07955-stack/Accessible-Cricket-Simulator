package com.cricketsim.ui.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.MatchFormat
import com.cricketsim.logic.Stadium
import com.cricketsim.logic.Team
import com.cricketsim.logic.TossDecision
import com.cricketsim.logic.TossResult

/**
 * Stand-in for the match screen itself — by far the largest remaining
 * piece of UI work (live score, the three custom gesture surfaces for
 * pitching/batting/fielding, scorecard, commentary, win probability,
 * rain delay, wicket/new-batsman flow, bowler-change flow). This
 * completes the pre-match setup flow end to end; a future session
 * should replace this with the real match screen (see UI_NOTES.md)
 * rather than extend it.
 */
@Composable
fun MatchPlaceholderScreen(
    format: MatchFormat,
    stadium: Stadium,
    userTeam: Team,
    opponentTeam: Team,
    toss: TossResult,
    onBack: () -> Unit
) {
    val winnerName = if (toss.winnerId == userTeam.id) userTeam.name else opponentTeam.name
    val decisionLabel = if (toss.decision == TossDecision.BAT) "bat" else "bowl"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Match screen coming soon",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("${userTeam.name} vs ${opponentTeam.name}")
        Spacer(modifier = Modifier.height(8.dp))
        Text("${format.name} at ${stadium.name}")
        Spacer(modifier = Modifier.height(8.dp))
        Text("$winnerName won the toss and chose to $decisionLabel first.")
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

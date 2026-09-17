package com.cricketsim.ui.setup

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

/**
 * Stand-in for step 4 of the setup flow (playing XI selection — pick
 * 11 from the full squad, then designate Captain / Vice-Captain /
 * Wicketkeeper from that XI; see CricketData.buildMatchSquad and
 * CricketData.autoSelectPlayingXI). Exists only to prove the
 * navigation flow works end to end after team selection; a future
 * session should replace this with the real screen (see UI_NOTES.md)
 * rather than extend it.
 */
@Composable
fun PlayingXIPlaceholderScreen(
    format: MatchFormat,
    stadium: Stadium,
    userTeam: Team,
    opponentTeam: Team,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Playing XI selection coming soon",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("${userTeam.name} vs ${opponentTeam.name}")
        Spacer(modifier = Modifier.height(8.dp))
        Text("${format.name} at ${stadium.name}")
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

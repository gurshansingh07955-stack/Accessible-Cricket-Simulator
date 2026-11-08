package com.cricketsim.ui.career

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.Team
import com.cricketsim.logic.TossDecision
import com.cricketsim.logic.TossResult
import com.cricketsim.logic.TossRoles
import kotlin.random.Random

/**
 * The toss of a career match when the player is not the captain: the captains do it. The screen only says who flips the coin, who calls, what it
 * landed on and who chose to bat or bowl, and the player continues to the match. (Away captain flips, home captain calls: [TossRoles.HOME_TEAM_CALLS].)
 */
@Composable
fun CareerTossScreen(userXI: Team, oppXI: Team, userIsHome: Boolean, onDone: (TossResult) -> Unit, onBack: () -> Unit) {
    val services = LocalGameServices.current
    val outcome = remember {
        val home = if (userIsHome) userXI else oppXI
        val away = if (userIsHome) oppXI else userXI
        val caller = if (TossRoles.HOME_TEAM_CALLS) home else away
        val flipper = if (caller === home) away else home
        val call = if (Random.nextBoolean()) "heads" else "tails"
        val coin = if (Random.nextBoolean()) "heads" else "tails"
        val winner = if (call == coin) caller else flipper
        val decision = if (Random.nextBoolean()) TossDecision.BAT else TossDecision.BOWL
        val text = "The ${flipper.name} captain flips the coin. The ${caller.name} captain calls $call. It lands on $coin. " +
            "${winner.name} won the toss and chose to ${if (decision == TossDecision.BAT) "bat" else "bowl"} first."
        Pair(TossResult(winnerId = winner.id, decision = decision), text)
    }
    LaunchedEffect(Unit) {
        services?.sound?.playCoinFlip()
        kotlinx.coroutines.delay(900)
        services?.announceSpoken(outcome.second)
    }
    BackHandler(onBack = onBack)
    Column(modifier = Modifier.padding(20.dp)) {
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        Spacer(modifier = Modifier.height(16.dp))
        Text("The toss", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = outcome.second,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { onDone(outcome.first) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Start the match") }
    }
}

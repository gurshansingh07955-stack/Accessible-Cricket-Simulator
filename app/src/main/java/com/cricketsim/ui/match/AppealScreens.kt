package com.cricketsim.ui.match

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.LocalGameServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The LBW APPEAL, which comes before the umpire's decision (and before any review):
 *
 *  - You are BOWLING and the ball has hit the batter's pad in front of the stumps: this screen asks you to appeal.
 *    You have six seconds. Appeal and the fielders' appeal sound plays, then the umpire gives it out. Do not appeal
 *    and the umpire says not out and play goes on (the batter survives that ball).
 *  - You are BATTING and the fielding side hit you on the pad: the computer side appeals by itself (the appeal sound),
 *    the umpire gives you out, and you may then review as before (DrsScreens.kt).
 *
 * The umpire speaks in his own recorded voice (audio/UmpireLines.kt, "um_out" and the like).
 */
const val APPEAL_WINDOW_MS = 6_000L

@Composable
fun AppealScreen(
    userIsBowling: Boolean,
    bowlerName: String,
    batterName: String,
    onDecided: (appealed: Boolean) -> Unit
) {
    BackHandler(onBack = {})
    val services = LocalGameServices.current
    val currentServices by rememberUpdatedState(services)
    val currentOnDecided by rememberUpdatedState(onDecided)
    val scope = rememberCoroutineScope()
    var decided by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var secondsLeft by remember { mutableStateOf((APPEAL_WINDOW_MS / 1000L).toInt()) }

    fun decide(appealed: Boolean) {
        if (decided) return
        decided = true
        scope.launch {
            val s = currentServices
            if (appealed) {
                status = "Howzat! The fielders appeal."
                s?.sound?.playAppeal()
                delay(1700L)
                s?.umpireSay(listOf(listOf("um_out", "um_out2").random()), "Out!")
                s?.awaitUmpire()
            } else {
                status = "No appeal."
                s?.umpireSay(listOf(listOf("um_not_out", "um_not_out2").random()), "Not out.")
                s?.awaitUmpire()
            }
            currentOnDecided(appealed)
        }
    }

    LaunchedEffect(Unit) {
        if (userIsBowling) {
            currentServices?.announceSpoken(
                "The ball has hit $batterName on the pad. Appeal, or let it go. You have ${APPEAL_WINDOW_MS / 1000L} seconds."
            )
            var left = APPEAL_WINDOW_MS
            while (left > 0L && !decided) {
                secondsLeft = ((left + 999L) / 1000L).toInt()
                delay(200L)
                left -= 200L
            }
            // The window closed with no appeal.
            decide(false)
        } else {
            currentServices?.announceSpoken("The ball has hit $batterName on the pad. The fielding side appeal for LBW.")
            delay(400L)
            decide(true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = if (userIsBowling) "The ball hit the pad" else "Appeal for LBW",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (userIsBowling) {
            Text(
                "$bowlerName's ball has struck $batterName on the pad, in front of the stumps. Do you appeal?",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            // The two choices come first, so they are the first things reached with a screen reader.
            Button(onClick = { decide(true) }, enabled = !decided, modifier = Modifier.fillMaxWidth()) { Text("Appeal!") }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { decide(false) }, enabled = !decided, modifier = Modifier.fillMaxWidth()) {
                Text("Don't appeal")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Time left: $secondsLeft seconds.", style = MaterialTheme.typography.bodyLarge)
        } else {
            Text(
                "$bowlerName's ball has hit $batterName on the pad. The fielding side appeal for LBW. The umpire is deciding.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        if (status.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(status, style = MaterialTheme.typography.titleMedium)
        }
    }
}

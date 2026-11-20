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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.CoinSide
import com.cricketsim.logic.Stadium
import com.cricketsim.logic.Team
import com.cricketsim.logic.TossDecision
import com.cricketsim.logic.TossResult
import com.cricketsim.logic.TossRoles
import com.cricketsim.ui.match.TossShow
import com.cricketsim.ui.setup.PitchReportScreen
import kotlin.random.Random

/** What the captains did at the toss of a career match when the player is not the captain. */
private class CareerTossOutcome(
    val result: TossResult,
    val text: String,
    val callerIsUser: Boolean,
    val call: CoinSide,
    val coin: CoinSide,
    val userWon: Boolean
)

/**
 * The toss of a career match when the player is not the captain: the captains do it. The screen only says who flips the coin, who calls, what it
 * landed on and who chose to bat or bowl, and the player continues to the match. (Away captain flips, home captain calls: [TossRoles.HOME_TEAM_CALLS].)
 *
 * With a [referee] (international series and tournaments and franchise leagues only, patch 132) and the clips in the audio pack the toss presentation plays
 * (ui/match/TossShow.kt): the presenter's welcome and the match referee, the call, the coin, the winner's answer, the other captain's answer, the closing; the
 * phone then says the result as before. "Start the match" cuts the voices off.
 */
@Composable
fun CareerTossScreen(userXI: Team, oppXI: Team, userIsHome: Boolean, onDone: (TossResult) -> Unit, onBack: () -> Unit, referee: String? = null, stadium: Stadium? = null) {
    val services = LocalGameServices.current
    val show = remember(referee) { TossShow.create(services?.sound, referee) }
    // The pitch report comes first (patch 134): the ground, the pitch and the weather, with the commentators. Without a ground there is none.
    var reportDone by remember { mutableStateOf(stadium == null) }
    if (!reportDone && stadium != null) {
        PitchReportScreen(stadium = stadium, onContinue = { reportDone = true }, onBack = onBack)
        return
    }
    val toss = remember {
        val home = if (userIsHome) userXI else oppXI
        val away = if (userIsHome) oppXI else userXI
        val caller = if (!TossRoles.CAREER_HOME_CALLS) (if (Random.nextBoolean()) home else away) else if (TossRoles.HOME_TEAM_CALLS) home else away
        val flipper = if (caller === home) away else home
        val call = if (Random.nextBoolean()) CoinSide.HEADS else CoinSide.TAILS
        val coin = if (Random.nextBoolean()) CoinSide.HEADS else CoinSide.TAILS
        val callWord = if (call == CoinSide.HEADS) "heads" else "tails"
        val coinWord = if (coin == CoinSide.HEADS) "heads" else "tails"
        val winner = if (call == coin) caller else flipper
        val decision = if (Random.nextBoolean()) TossDecision.BAT else TossDecision.BOWL
        val text = "The ${flipper.name} captain flips the coin. The ${caller.name} captain calls $callWord. It lands on $coinWord. " +
            "${winner.name} won the toss and chose to ${if (decision == TossDecision.BAT) "bat" else "bowl"} first."
        CareerTossOutcome(
            result = TossResult(winnerId = winner.id, decision = decision),
            text = text,
            callerIsUser = caller.id == userXI.id,
            call = call,
            coin = coin,
            userWon = winner.id == userXI.id
        )
    }
    LaunchedEffect(Unit) {
        if (show != null) {
            // The welcome, the match referee and the invitation to call, before the coin goes up.
            show.play(show.opening(userFlips = false))
            show.waitUntilDone()
        }
        services?.sound?.playCoinFlip()
        kotlinx.coroutines.delay(900)
        if (show != null) {
            show.play(show.afterCoin(toss.callerIsUser, toss.call, toss.coin, toss.userWon) + show.afterDecision(toss.userWon, toss.result.decision))
            show.waitUntilDone()
        }
        services?.announceSpoken(toss.text)
    }
    DisposableEffect(show) { onDispose { show?.stop() } }
    BackHandler(onBack = { show?.stop(); onBack() })
    Column(modifier = Modifier.padding(20.dp)) {
        OutlinedButton(onClick = { show?.stop(); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        Spacer(modifier = Modifier.height(16.dp))
        Text("The toss", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = toss.text,
            style = MaterialTheme.typography.bodyLarge,
            // With the presentation the phone announces it after the voices, so a screen reader does not talk over them.
            modifier = if (show != null) Modifier else Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { show?.stop(); onDone(toss.result) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Start the match") }
    }
}

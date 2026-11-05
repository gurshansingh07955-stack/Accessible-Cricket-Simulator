package com.cricketsim.ui.setup

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.CoinSide
import com.cricketsim.logic.CommentaryCategory
import com.cricketsim.logic.Team
import com.cricketsim.logic.TossDecision
import com.cricketsim.logic.TossResult
import kotlin.random.Random

/**
 * Step 5 of the pre-match setup flow: the toss, plus a bat/bowl decision for the user when they win it.
 *
 * WHO CALLS AND WHO FLIPS (logic/TossRoles.kt): sometimes the user calls heads or tails and the other captain
 * flips the coin; sometimes the user flips ("Flip the coin" button) and the other captain calls it in the air.
 * [userCalls] says which: true = the user calls, false = the user flips, null = a neutral ground, settled by
 * chance once for this toss. After the coin lands, the screen says what was called and what the coin showed.
 *
 * Sub-steps, each a flat single-swipe list or a short result readout:
 * 1. The user calls Heads/Tails, OR flips the coin.
 * 2a. The user's side won: pick bat or bowl first.
 * 2b. The opponent won: a readout of what the AI decided (a live region, so it is announced on arrival).
 *
 * AUDIO: the coin-flip sound plays when the coin is flipped, and the toss commentary is queued once the
 * decision is known (the user's own choice, or as soon as the AI's readout appears).
 */
@Composable
fun TossScreen(userTeam: Team, opponentTeam: Team, onTossComplete: (TossResult) -> Unit, onBack: () -> Unit, userCalls: Boolean? = null) {
    val services = LocalGameServices.current
    val userCallsCoin = remember { userCalls ?: Random.nextBoolean() }
    var tossResult by remember { mutableStateOf<TossResult?>(null) }
    var coinNote by remember { mutableStateOf("") }
    val result = tossResult

    if (result == null) {
        if (userCallsCoin) {
            CoinCallStep(
                flipperName = opponentTeam.name,
                onCoinCalled = { choice ->
                    services?.sound?.playCoinFlip()
                    val coin = randomSide()
                    coinNote = "You called ${sideName(choice)}. The coin landed ${sideName(coin)}."
                    tossResult = makeToss(userTeam, opponentTeam, userWon = choice == coin)
                },
                onBack = onBack
            )
        } else {
            CoinFlipStep(
                callerName = opponentTeam.name,
                onFlip = {
                    services?.sound?.playCoinFlip()
                    val aiCall = randomSide()
                    val coin = randomSide()
                    coinNote = "${opponentTeam.name} called ${sideName(aiCall)}. The coin landed ${sideName(coin)}."
                    tossResult = makeToss(userTeam, opponentTeam, userWon = coin != aiCall)
                },
                onBack = onBack
            )
        }
        return
    }

    val userWonToss = result.winnerId == userTeam.id
    val winnerName = if (userWonToss) userTeam.name else opponentTeam.name

    if (userWonToss) {
        BatBowlChoiceStep(
            winnerName = winnerName,
            note = coinNote,
            onChoice = { decision ->
                services?.sound?.enqueueCommentary(
                    if (decision == TossDecision.BAT) CommentaryCategory.TOSS_BAT else CommentaryCategory.TOSS_BOWL
                )
                onTossComplete(result.copy(decision = decision))
            },
            onBack = { tossResult = null }
        )
    } else {
        LaunchedEffect(result) {
            services?.sound?.enqueueCommentary(
                if (result.decision == TossDecision.BAT) CommentaryCategory.TOSS_BAT else CommentaryCategory.TOSS_BOWL
            )
        }
        TossResultStep(
            winnerName = winnerName,
            note = coinNote,
            decision = result.decision,
            onContinue = { onTossComplete(result) }
        )
    }
}

private fun randomSide(): CoinSide = if (Random.nextDouble() < 0.5) CoinSide.HEADS else CoinSide.TAILS

private fun sideName(side: CoinSide): String = if (side == CoinSide.HEADS) "heads" else "tails"

/** The toss outcome. A user win carries a placeholder decision (BAT) that the bat/bowl step overwrites; the AI picks at random. */
private fun makeToss(userTeam: Team, opponentTeam: Team, userWon: Boolean): TossResult {
    val aiDecision = if (Random.nextDouble() > 0.5) TossDecision.BAT else TossDecision.BOWL
    return TossResult(
        winnerId = if (userWon) userTeam.id else opponentTeam.id,
        decision = if (userWon) TossDecision.BAT else aiDecision
    )
}

@Composable
private fun CoinCallStep(flipperName: String, onCoinCalled: (CoinSide) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Call the coin",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("The $flipperName captain will flip the coin. Call heads or tails.")
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Heads",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = "Call heads",
                    role = Role.Button,
                    onClick = { onCoinCalled(CoinSide.HEADS) }
                )
                .padding(vertical = 14.dp, horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tails",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = "Call tails",
                    role = Role.Button,
                    onClick = { onCoinCalled(CoinSide.TAILS) }
                )
                .padding(vertical = 14.dp, horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

/** The user flips the coin; the other captain calls it. */
@Composable
private fun CoinFlipStep(callerName: String, onFlip: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Flip the coin",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("You flip the coin. The $callerName captain will call it in the air.")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onFlip, modifier = Modifier.fillMaxWidth()) {
            Text("Flip the coin")
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
private fun BatBowlChoiceStep(winnerName: String, note: String, onChoice: (TossDecision) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "$note $winnerName won the toss",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("What would you like to do?")
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Bat First",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = "Choose to bat first",
                    role = Role.Button,
                    onClick = { onChoice(TossDecision.BAT) }
                )
                .padding(vertical = 14.dp, horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Bowl First",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = "Choose to bowl first",
                    role = Role.Button,
                    onClick = { onChoice(TossDecision.BOWL) }
                )
                .padding(vertical = 14.dp, horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
private fun TossResultStep(winnerName: String, note: String, decision: TossDecision, onContinue: () -> Unit) {
    val decisionLabel = if (decision == TossDecision.BAT) "bat" else "bowl"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "$note $winnerName won the toss and chose to $decisionLabel first.",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            }
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}

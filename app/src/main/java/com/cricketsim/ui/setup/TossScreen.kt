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
import com.cricketsim.logic.MatchEngine
import com.cricketsim.logic.Team
import com.cricketsim.logic.TossDecision
import com.cricketsim.logic.TossResult

/**
 * Step 5 of the pre-match setup flow: the toss
 * (MatchEngine.simulateToss), plus a bat/bowl decision for the user
 * when they win it.
 *
 * Three sequential sub-steps, each a flat single-swipe list or a
 * short result readout, following the established setup-flow
 * patterns:
 * 1. Call the coin (Heads/Tails) — a one-way pick-and-advance list,
 *    same `Modifier.clickable(..., role = Role.Button)` pattern as
 *    every other one-shot choice in this flow.
 * 2a. If the user's side won the toss: a second pick-and-advance list
 *     to choose bat or bowl first. `MatchEngine.simulateToss` already
 *     returns a placeholder `TossDecision.BAT` for a user win (its own
 *     doc comment says this is meant to be overwritten by the UI) —
 *     this step is exactly that override, replacing it with the
 *     user's real choice via `TossResult.copy(decision = ...)`.
 * 2b. If the opponent won: a short result readout announcing what the
 *     AI decided (already resolved by simulateToss, nothing left for
 *     the user to choose) before continuing on.
 *
 * The result readout in 2b uses `liveRegion = LiveRegionMode.Polite`
 * on its heading — unlike every earlier setup screen, this text
 * appears already-resolved on screen entry rather than being the
 * result of an action just taken on the same screen, so a live region
 * ensures it's actually announced rather than requiring the user to
 * navigate to it manually.
 *
 * AUDIO (as the web's play-match.tsx does it): the coin-flip sound plays
 * when the coin is called, and the toss commentary ("they've won the
 * toss and will bat first" / "send the opposition in") is queued once the
 * decision is known — on the user's own choice, or as soon as the AI's
 * result readout appears. It keeps playing into the match until the
 * first ball cuts it off.
 */
@Composable
fun TossScreen(userTeam: Team, opponentTeam: Team, onTossComplete: (TossResult) -> Unit, onBack: () -> Unit) {
    val services = LocalGameServices.current
    var tossResult by remember { mutableStateOf<TossResult?>(null) }
    val result = tossResult

    if (result == null) {
        CoinCallStep(
            onCoinCalled = { choice ->
                services?.sound?.playCoinFlip()
                tossResult = MatchEngine.simulateToss(userTeam, opponentTeam, choice)
            },
            onBack = onBack
        )
        return
    }

    val userWonToss = result.winnerId == userTeam.id
    val winnerName = if (userWonToss) userTeam.name else opponentTeam.name

    if (userWonToss) {
        BatBowlChoiceStep(
            winnerName = winnerName,
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
            decision = result.decision,
            onContinue = { onTossComplete(result) }
        )
    }
}

@Composable
private fun CoinCallStep(onCoinCalled: (CoinSide) -> Unit, onBack: () -> Unit) {
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

@Composable
private fun BatBowlChoiceStep(winnerName: String, onChoice: (TossDecision) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "$winnerName won the toss",
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
private fun TossResultStep(winnerName: String, decision: TossDecision, onContinue: () -> Unit) {
    val decisionLabel = if (decision == TossDecision.BAT) "bat" else "bowl"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "$winnerName won the toss and chose to $decisionLabel first.",
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

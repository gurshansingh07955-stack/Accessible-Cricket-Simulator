package com.cricketsim.ui.match

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.cricketsim.logic.Difficulty
import com.cricketsim.logic.MatchFormat
import com.cricketsim.logic.MatchStateMachine
import com.cricketsim.logic.Stadium
import com.cricketsim.logic.Team
import com.cricketsim.logic.TossResult
import com.cricketsim.logic.WeatherSystem

/**
 * ⚠️ FIRST SLICE of the match screen — NOT the real design. Both sides
 * are simulated by MatchSimulation.kt's temporary AI-vs-AI loop (see
 * its own file-level doc comment) rather than the user actually
 * playing their own side, since none of the three custom gesture
 * surfaces (pitching/batting/fielding) exist yet. This screen's
 * purpose is narrow: prove the full MatchEngine/MatchStateMachine
 * wiring — ball simulation, wicket handling, strike rotation, over
 * completion, bowler changes, innings switching, match completion —
 * works correctly end to end inside the real Android UI, one manually-
 * triggered ball at a time.
 *
 * A future session builds the real match screen: the actual gesture
 * surfaces (replacing the user's own side's AI decisions here),
 * scorecard, proper commentary/audio, rain delays, and the
 * wicket/bowler-selection prompts this screen currently skips by
 * always auto-picking. See UI_NOTES.md's "Not started" section for the
 * full list — treat this file as scaffolding to build on top of, not
 * a screen to extend piecemeal into the real thing.
 */
@Composable
fun MatchScreen(format: MatchFormat, stadium: Stadium, userTeam: Team, opponentTeam: Team, toss: TossResult, onBack: () -> Unit) {
    var matchState by remember {
        mutableStateOf(
            MatchStateMachine.createNewMatch(
                format = format,
                pitchType = stadium.pitchType,
                userTeam = userTeam,
                opponentTeam = opponentTeam,
                tossWinnerId = toss.winnerId,
                tossDecision = toss.decision,
                stadiumId = stadium.id,
                weather = WeatherSystem.generateWeatherForStadium(stadium)
            )
        )
    }
    var recentCommentary by remember { mutableStateOf<List<String>>(emptyList()) }
    var matchOver by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }

    fun advanceOneBall() {
        if (matchOver) return
        val (nextState, outcome) = MatchSimulation.simulateOneBall(matchState, stadium, Difficulty.MEDIUM)
        matchState = nextState
        recentCommentary = (recentCommentary + outcome.commentary).takeLast(6)

        when {
            MatchSimulation.isTargetReached(nextState) -> {
                matchOver = true
                resultText = MatchSimulation.matchResultText(nextState)
            }
            MatchSimulation.isInningsOver(nextState) -> {
                if (nextState.currentInnings == 1) {
                    matchState = MatchStateMachine.switchInnings(nextState)
                    recentCommentary = emptyList()
                } else {
                    matchOver = true
                    resultText = MatchSimulation.matchResultText(nextState)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "${userTeam.name} vs ${opponentTeam.name}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))

        val score = matchState.score
        Text(
            text = "${matchState.battingTeam.name}: ${score.runs}/${score.wickets} (${score.overs}.${score.balls} ov)",
            style = MaterialTheme.typography.titleLarge,
            // Every ball changes this line, and it's the single most
            // important running fact about the match, so it stays a
            // live region for the whole screen's lifetime rather than
            // only announcing on entry.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        matchState.target?.let { target ->
            Text("Target: $target", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${matchState.currentBatsmen.first.name}* & ${matchState.currentBatsmen.second.name} \u00b7 Bowler: ${matchState.currentBowler.name}",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (matchOver) {
            Text(
                text = resultText ?: "Match complete.",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                }
            )
        } else {
            Button(onClick = { advanceOneBall() }, modifier = Modifier.fillMaxWidth()) {
                Text("Simulate Next Ball")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Recent commentary",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(recentCommentary.reversed()) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

package com.cricketsim.ui.match

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.cricketsim.logic.BattingDecision
import com.cricketsim.logic.Difficulty
import com.cricketsim.logic.FieldingSystem
import com.cricketsim.logic.MatchFormat
import com.cricketsim.logic.MatchStateMachine
import com.cricketsim.logic.ResolvedBowlingDecision
import com.cricketsim.logic.Stadium
import com.cricketsim.logic.Team
import com.cricketsim.logic.TossResult
import com.cricketsim.logic.WeatherSystem

/**
 * ⚠️ FIRST SLICE of the match screen — NOT the real design. This screen
 * routes to the three working gesture surfaces: PitchingScreen when the
 * user's own team is bowling, BattingScreen when it is batting, and
 * FieldingScreen from a Set field button (editable while the user is
 * bowling) or a Hear the field button (read-only while the user is
 * batting). It also opens the ScorecardScreen. Everything else is still
 * MatchSimulation.kt's temporary path (see its own file-level doc
 * comment): everything the opposing side does is AI-driven. This
 * screen's remaining purpose is still narrow: verify the integrations
 * work correctly end to end inside the real Android UI, one
 * manually-triggered ball at a time.
 *
 * Reading order follows the web's match screen, which was tuned with a
 * real screen-reader user: striker, non-striker and bowler lines with
 * their live figures, then the action buttons, then the last ball, the
 * score, the chase figures and the run rates. The whole screen scrolls
 * (a plain scrolling Column) so nothing can be pushed off a small
 * screen.
 *
 * The user's team is always either batting or bowling, so every ball
 * goes through PitchingScreen or BattingScreen; the earlier AI-vs-AI
 * "Simulate Next Ball" button is gone because nothing can reach it any
 * more.
 *
 * A future session builds the real match screen: proper commentary/
 * audio, rain delays, and the opener/wicket/bowler-selection prompts
 * this screen currently skips by always auto-picking. See UI_NOTES.md's
 * "Not started" section for the full list — treat this file as
 * scaffolding to build on top of, not a screen to extend piecemeal into
 * the real thing.
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
    var showPitchingScreen by remember { mutableStateOf(false) }
    var showBattingScreen by remember { mutableStateOf(false) }
    var showFieldScreen by remember { mutableStateOf(false) }
    var showScorecard by remember { mutableStateOf(false) }
    // Outcome of the last Set field, announced on this screen (the
    // fielding screen is gone by then). Cleared as soon as a ball is played.
    var fieldMessage by remember { mutableStateOf("") }

    fun advanceOneBall(
        presetBowlingDecision: ResolvedBowlingDecision? = null,
        presetBattingDecision: BattingDecision? = null
    ) {
        if (matchOver) return
        fieldMessage = ""
        val (nextState, outcome) = MatchSimulation.simulateOneBall(
            matchState,
            stadium,
            Difficulty.MEDIUM,
            presetBowlingDecision,
            presetBattingDecision
        )
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

    if (showPitchingScreen) {
        PitchingScreen(
            bowler = matchState.currentBowler,
            onDeliveryResolved = { decision ->
                showPitchingScreen = false
                advanceOneBall(presetBowlingDecision = decision)
            },
            onBack = { showPitchingScreen = false }
        )
        return
    }

    if (showBattingScreen) {
        BattingScreen(
            batsman = matchState.currentBatsmen.first,
            generateDelivery = { MatchSimulation.generateBowlingDecision(matchState) },
            onBallPlayed = { delivery, decision ->
                showBattingScreen = false
                advanceOneBall(presetBowlingDecision = delivery, presetBattingDecision = decision)
            },
            onBack = { showBattingScreen = false }
        )
        return
    }

    val isPowerplayNow = FieldingSystem.isPowerplayOver(matchState.format, matchState.score.overs)
    val isUserBowling = matchState.bowlingTeam.id == userTeam.id

    if (showFieldScreen) {
        FieldingScreen(
            teamName = matchState.bowlingTeam.name,
            players = matchState.bowlingTeam.players,
            placements = matchState.fieldPlacements,
            isPowerplay = isPowerplayNow,
            isReadOnly = !isUserBowling,
            onConfirm = { placements ->
                matchState = MatchStateMachine.setFieldPlacements(matchState, placements)
                fieldMessage = fieldSetMessage(placements, isPowerplayNow)
                showFieldScreen = false
            },
            onBack = { showFieldScreen = false }
        )
        return
    }

    if (showScorecard) {
        ScorecardScreen(state = matchState, onBack = { showScorecard = false })
        return
    }

    val userFieldReason = if (isUserBowling) {
        FieldingSystem.getIllegalFieldReason(matchState.fieldPlacements, isPowerplayNow)
    } else {
        null
    }
    val lastBallLine = recentCommentary.lastOrNull()?.let { "Last ball: $it" } ?: "No ball bowled yet."

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "${userTeam.name} vs ${opponentTeam.name}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(MatchLines.strikerLine(matchState), style = MaterialTheme.typography.bodyLarge)
        Text(MatchLines.nonStrikerLine(matchState), style = MaterialTheme.typography.bodyLarge)
        Text(MatchLines.bowlerLine(matchState), style = MaterialTheme.typography.bodyLarge)

        if (fieldMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = fieldMessage,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        if (userFieldReason != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your field is illegal. $userFieldReason Every delivery will be a no-ball until it is fixed.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

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
        } else if (isUserBowling) {
            Button(onClick = { showPitchingScreen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Bowl")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { showFieldScreen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Set field")
            }
        } else {
            Button(onClick = { showBattingScreen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Face next ball")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { showFieldScreen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Hear the field")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { showScorecard = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Scorecard")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // The outcome of the ball just played and the new score are the two
        // things that change every delivery, so both are polite live
        // regions — the outcome first, so it is spoken before the score.
        // (Previously only the score was live, so a screen-reader user
        // never heard what actually happened on the ball.)
        Text(
            text = lastBallLine,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Text(
            text = MatchLines.scoreLine(matchState),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        MatchLines.targetLine(matchState)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        MatchLines.runsNeededLine(matchState)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text(MatchLines.currentRunRateLine(matchState), style = MaterialTheme.typography.bodyMedium)
        MatchLines.requiredRunRateLine(matchState)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        if (recentCommentary.size > 1) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Earlier this innings",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Everything except the last ball (already spoken above), newest first.
            recentCommentary.dropLast(1).reversed().forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

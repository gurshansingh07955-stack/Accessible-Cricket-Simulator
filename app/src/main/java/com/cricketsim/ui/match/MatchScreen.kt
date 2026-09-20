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
import com.cricketsim.audio.GameSettings
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.audio.MatchAudioDirector
import com.cricketsim.logic.BattingDecision
import com.cricketsim.logic.FieldingSystem
import com.cricketsim.logic.MatchFormat
import com.cricketsim.logic.MatchStateMachine
import com.cricketsim.logic.ResolvedBowlingDecision
import com.cricketsim.logic.Stadium
import com.cricketsim.logic.Team
import com.cricketsim.logic.TossResult
import com.cricketsim.logic.WeatherSystem
import com.cricketsim.ui.settings.SettingsScreen

/**
 * ⚠️ FIRST SLICE of the match screen — NOT the real design. This screen
 * routes to the three working gesture surfaces: PitchingScreen when the
 * user's own team is bowling, BattingScreen when it is batting, and
 * FieldingScreen from a Set field button (editable while the user is
 * bowling) or a Hear the field button (read-only while the user is
 * batting). It also owns everything around them: the scorecard, the
 * three selection screens (openers, next batsman, next bowler), the
 * "play has stopped" screens (rain delay, innings break, match result,
 * leave confirmation — see MatchFlowScreens.kt), a settings overlay, and
 * all of the match's SOUND (through MatchAudioDirector — the crowd bed,
 * the ball and outcome effects, the AI voice commentary, rain). It is
 * still MatchSimulation.kt's temporary path underneath: everything the
 * opposing side does is AI-driven.
 *
 * WHICH SCREEN WINS when several apply, in order:
 *   0. the leave-match confirmation,
 *   1. the settings overlay (opened from the ordinary screen; it is an
 *      overlay, not a route, because leaving this composable would throw
 *      the match away),
 *   2. the scorecard (only ever opened from a screen that can go back
 *      to where it came from),
 *   3. the match result,
 *   4. a rain delay,
 *   5. the innings break,
 *   6. a pick the user's own side owes (openers, next batsman, bowler),
 *   7. the gesture surfaces and the field screen,
 *   8. the ordinary match screen.
 * Rain outranks a pending pick because play has stopped; the pick simply
 * appears the moment play resumes.
 *
 * Reading order on the ordinary screen follows the web's match screen,
 * which was tuned with a real screen-reader user: striker, non-striker
 * and bowler lines with their live figures, then the action buttons,
 * then the last ball (with ball quality, shot and timing), the score, the
 * chase figures, the run rates and (in a chase) the win probability. The
 * whole screen scrolls (a plain scrolling Column) so nothing can be
 * pushed off a small screen.
 *
 * `onBack` abandons the match (MainActivity sends you back to the toss)
 * and is only reachable through the leave confirmation;
 * `onMatchFinished` is the finished match's Return to home.
 *
 * A future session builds the real match screen. See UI_NOTES.md's "Not
 * started" section for the full list — treat this file as scaffolding
 * to build on top of, not a screen to extend piecemeal into the real
 * thing.
 */
@Composable
fun MatchScreen(
    format: MatchFormat,
    stadium: Stadium,
    userTeam: Team,
    opponentTeam: Team,
    toss: TossResult,
    onBack: () -> Unit,
    onMatchFinished: () -> Unit
) {
    val services = LocalGameServices.current
    val settings = services?.settings ?: GameSettings()
    val director = remember(services) { services?.let { MatchAudioDirector(it) } }

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
    var showInningsBreak by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var confirmingLeave by remember { mutableStateOf(false) }
    // The final ball of the first innings, kept for the innings-break
    // screen because the commentary list is cleared for the new innings.
    var breakLastBall by remember { mutableStateOf("") }
    // Outcome of the last Set field, announced on this screen (the
    // fielding screen is gone by then). Cleared as soon as a ball is played.
    var fieldMessage by remember { mutableStateOf("") }
    // "Play resumes." / the revised target, announced after a rain delay.
    var playNotice by remember { mutableStateOf("") }

    // The crowd bed runs while the match is live and sound effects are on;
    // it stops when the match ends, sound is turned off, or this screen is
    // left (the director then also silences rain and commentary).
    DisposableEffect(director) {
        onDispose { director?.onMatchScreenLeft() }
    }
    LaunchedEffect(director, settings.soundEffects, matchOver) {
        if (director != null) {
            if (settings.soundEffects && !matchOver) director.startAmbience(matchState) else director.stopAmbience()
        }
    }

    fun advanceOneBall(
        presetBowlingDecision: ResolvedBowlingDecision? = null,
        presetBattingDecision: BattingDecision? = null
    ) {
        if (matchOver) return
        fieldMessage = ""
        playNotice = ""
        val before = matchState
        director?.onDelivery()
        val result = MatchSimulation.simulateOneBall(
            before,
            stadium,
            settings.difficulty,
            presetBowlingDecision,
            presetBattingDecision
        )
        val nextState = result.state
        val summary = MatchLines.ballSummary(result.outcome, result.battingDecision)
        matchState = nextState
        recentCommentary = (recentCommentary + summary).takeLast(6)

        director?.onBallResolved(before, nextState, result.outcome)
        // A wicket for the user's side is announced by the new-batsman
        // screen instead, exactly as the web skips its own announcement.
        if (nextState.pendingDismissal == null) services?.announceSpoken(summary)
        if (nextState.activeRainDelay != null && before.activeRainDelay == null) {
            director?.onRainStarted()
            services?.announceSpoken("Rain has stopped play.")
        }

        // MatchSimulation only leaves a pick pending when the innings is
        // NOT ending on this ball, so these checks never fight a prompt.
        when {
            MatchSimulation.isTargetReached(nextState) -> {
                matchState = MatchSimulation.finishMatch(nextState)
                matchOver = true
                resultText = MatchSimulation.matchResultText(nextState)
                director?.onMatchEnded()
                resultText?.let { services?.announceSpoken(it) }
            }
            MatchSimulation.isInningsOver(nextState) -> {
                if (nextState.currentInnings == 1) {
                    breakLastBall = summary
                    val switched = MatchStateMachine.switchInnings(nextState)
                    matchState = switched
                    recentCommentary = emptyList()
                    showInningsBreak = true
                    director?.onInningsBreak(switched)
                    val targetWord = if (switched.dlsRevised) "DLS-revised target" else "Target"
                    services?.announceSpoken("Innings break. $targetWord is ${switched.target}.")
                } else {
                    matchState = MatchSimulation.finishMatch(nextState)
                    matchOver = true
                    resultText = MatchSimulation.matchResultText(nextState)
                    director?.onMatchEnded()
                    resultText?.let { services?.announceSpoken(it) }
                }
            }
            else -> director?.updateTension(nextState)
        }
    }

    if (confirmingLeave) {
        ConfirmLeaveScreen(onStay = { confirmingLeave = false }, onLeave = onBack)
        return
    }

    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    if (showScorecard) {
        ScorecardScreen(
            state = matchState,
            startOnFirstInnings = showInningsBreak && !matchOver,
            onBack = { showScorecard = false }
        )
        return
    }

    if (matchOver) {
        MatchResultScreen(
            resultText = resultText ?: "Match complete.",
            summaryLines = MatchLines.resultSummaryLines(matchState),
            lastBall = recentCommentary.lastOrNull(),
            onScorecard = { showScorecard = true },
            onHome = onMatchFinished
        )
        return
    }

    // Rain has stopped play: nothing else can happen until it's dismissed.
    if (matchState.activeRainDelay != null) {
        val revised = matchState.currentInnings == 2 && matchState.secondInningsInterruption != null
        RainDelayScreen(
            format = matchState.format,
            newOversLimit = matchState.oversLimit,
            innings = matchState.currentInnings,
            dlsRevised = revised,
            revisedTarget = if (revised) matchState.target else null,
            onResume = {
                playNotice = if (revised) "Play resumes. Revised target is ${matchState.target}." else "Play resumes."
                services?.announceSpoken(playNotice)
                director?.onRainResumed(matchState.secondInningsInterruption != null)
                matchState = MatchStateMachine.resumeFromRainDelay(matchState)
            }
        )
        return
    }

    if (showInningsBreak) {
        InningsBreakScreen(
            state = matchState,
            lastBall = breakLastBall,
            onStart = { showInningsBreak = false },
            onScorecard = { showScorecard = true }
        )
        return
    }

    // Picks the user's own side owes. These replace the whole screen,
    // like the gesture surfaces do.
    if (matchState.needsOpenerSelection) {
        OpenerSelectionScreen(
            players = matchState.battingTeam.players,
            onConfirm = { striker, nonStriker ->
                matchState = MatchStateMachine.setOpeners(matchState, striker, nonStriker)
            }
        )
        return
    }

    val dismissal = matchState.pendingDismissal
    if (dismissal != null) {
        NewBatsmanScreen(
            dismissal = dismissal,
            scoreLine = MatchLines.scoreLine(matchState),
            players = MatchStateMachine.getAvailableBatsmen(matchState),
            onConfirm = { batsman ->
                matchState = MatchSimulation.completeWicketReplacement(matchState, batsman, stadium)
            }
        )
        return
    }

    if (matchState.needsBowlerSelection) {
        val noBallsBowledYet = matchState.currentInningsData.totalBalls == 0 && matchState.currentInningsData.totalOvers == 0
        BowlerSelectionScreen(
            title = if (noBallsBowledYet) "Select your opening bowler" else "Select your next bowler",
            players = MatchSimulation.bowlerChoices(matchState),
            bowlerStats = matchState.currentInningsData.bowlerStats,
            maxOversPerBowler = MatchStateMachine.getMaxOversPerBowler(matchState.format),
            onConfirm = { bowler ->
                matchState = MatchStateMachine.selectBowler(matchState, bowler)
            }
        )
        return
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
            // Called once, after the footwork commit: the AI captain reads
            // the situation, decides the delivery and sets its field for it
            // (all before the batter picks a shot), and the batter is told
            // the delivery and who moved where.
            generateDelivery = {
                val prepared = MatchSimulation.prepareAiDelivery(matchState)
                matchState = prepared.state
                director?.onFieldChanges(prepared.fieldChanges)
                DeliveryReveal(prepared.bowling, prepared.fieldChanges.joinToString(" "))
            },
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

        if (playNotice.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = playNotice,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
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

        if (isUserBowling) {
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
        MatchLines.winProbabilityLine(matchState)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

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
        // Sound, vibration, commentary and difficulty, changeable mid-match.
        Button(onClick = { showSettings = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Settings")
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Asks first: leaving abandons the match, and there is no save yet.
        Button(onClick = { confirmingLeave = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Leave match")
        }
    }
}

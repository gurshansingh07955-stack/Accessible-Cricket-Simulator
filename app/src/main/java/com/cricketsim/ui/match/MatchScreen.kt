package com.cricketsim.ui.match

import androidx.activity.compose.BackHandler
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
import com.cricketsim.persistence.MatchSaveStore
import com.cricketsim.persistence.MatchSnapshot
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
 * leave confirmation — see MatchFlowScreens.kt), a settings overlay, all of
 * the match's SOUND (through MatchAudioDirector — the crowd bed, the ball
 * and outcome effects, the AI voice commentary, rain), and the
 * AUTOSAVE that lets the match be resumed. It is still
 * MatchSimulation.kt's temporary path underneath: everything the
 * opposing side does is AI-driven.
 *
 * SAVING AND RESUMING. The whole match — MatchState plus what this screen
 * keeps (stadium, toss, recent commentary, whether the innings break is
 * still to be shown) — is written as a MatchSnapshot after every change,
 * off the main thread, and deleted when the match ends. `resume` puts a
 * saved one back: it just seeds the state this screen would otherwise
 * create fresh, so everything downstream (pending picks, a rain delay, the
 * field, the innings break) comes back for free. Two rules keep it from
 * ever doing damage:
 *  - A NEW match does not save until its first ball has been bowled (or
 *    it has reached the second innings), so opening a new match by
 *    mistake cannot overwrite a saved one.
 *  - Which sub-screen was open (a delivery in progress, the field screen)
 *    is not saved: a resumed match opens on its ordinary screen, at the
 *    state after the last completed action.
 *
 * WHICH SCREEN WINS when several apply, in order:
 *   0. the leave-match confirmation,
 *   1. the settings overlay (opened from the ordinary screen; it is an
 *      overlay, not a route, because leaving this composable would take
 *      the live match with it),
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
 * Reading order on the ordinary screen (tuned against real on-device
 * TalkBack testing, which is why this no longer matches the web's own
 * order): heading, then Leave match, Settings and Scorecard — the three
 * navigation actions — reachable immediately without swiping through the
 * live match state first. Then, while BOWLING: Set field (a pre-delivery
 * decision, so it comes before the delivery itself), the striker/
 * non-striker/bowler lines, then Bowl. While BATTING: Face next ball,
 * the same three player lines, then Hear the field (read-only field
 * info — secondary to the actual action). Everything else — status
 * messages, the last ball, the score, the chase figures, the run rates,
 * the win probability and the earlier-innings list — comes after that
 * primary action button. The whole screen scrolls (a plain scrolling
 * Column) so nothing can be pushed off a small screen.
 *
 * SYSTEM BACK BUTTON. Every sub-view below (a gesture surface, the field
 * screen, the scorecard, the settings overlay, the leave confirmation)
 * carries its own BackHandler that does exactly what that sub-view's own
 * on-screen back affordance already does — so system back is never a
 * different behavior from the button, and it can never fall through to
 * the default "close the app" behavior. On the ordinary match screen
 * itself, with no sub-view open, back opens the leave confirmation, same
 * as tapping the Leave match button — a live match is never discarded by
 * an unconfirmed back press, even though it would be safe to (it's
 * autosaved) — for the same reason the button itself asks first.
 *
 * `onBack` leaves the match (MainActivity sends you to the first screen,
 * where the autosave is offered as Resume) and is only reachable through
 * the leave confirmation; `onMatchFinished` is the finished match's Return
 * to home.
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
    onMatchFinished: () -> Unit,
    resume: MatchSnapshot? = null
) {
    val services = LocalGameServices.current
    val settings = services?.settings ?: GameSettings()
    val director = remember(services) { services?.let { MatchAudioDirector(it) } }

    var matchState by remember {
        mutableStateOf(
            resume?.state ?: MatchStateMachine.createNewMatch(
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
    var recentCommentary by remember { mutableStateOf<List<String>>(resume?.recentCommentary ?: emptyList()) }
    var matchOver by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var showPitchingScreen by remember { mutableStateOf(false) }
    var showBattingScreen by remember { mutableStateOf(false) }
    var showFieldScreen by remember { mutableStateOf(false) }
    var showScorecard by remember { mutableStateOf(false) }
    var showInningsBreak by remember { mutableStateOf(resume?.showInningsBreak ?: false) }
    var showSettings by remember { mutableStateOf(false) }
    var confirmingLeave by remember { mutableStateOf(false) }
    // The final ball of the first innings, kept for the innings-break
    // screen because the commentary list is cleared for the new innings.
    var breakLastBall by remember { mutableStateOf(resume?.breakLastBall ?: "") }
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

    // Autosave. Re-runs (cancelling any earlier run) whenever anything that
    // goes into a snapshot changes. A finished match deletes its save;
    // a brand-new one waits for its first ball so it cannot overwrite an
    // older saved match just by being opened.
    val saves = services?.saves
    LaunchedEffect(saves, matchState, recentCommentary, showInningsBreak, breakLastBall, matchOver) {
        if (saves == null) return@LaunchedEffect
        if (matchOver) {
            saves.clear()
        } else if (matchState.ballByBall.isNotEmpty() || matchState.currentInnings == 2) {
            saves.save(
                MatchSnapshot(
                    version = MatchSaveStore.CURRENT_VERSION,
                    stadium = stadium,
                    toss = toss,
                    state = matchState,
                    recentCommentary = recentCommentary,
                    showInningsBreak = showInningsBreak,
                    breakLastBall = breakLastBall
                )
            )
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
        // Back here cancels, same as the Stay button — it never silently
        // discards a live match.
        BackHandler(onBack = { confirmingLeave = false })
        ConfirmLeaveScreen(onStay = { confirmingLeave = false }, onLeave = onBack)
        return
    }

    if (showSettings) {
        BackHandler(onBack = { showSettings = false })
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    if (showScorecard) {
        BackHandler(onBack = { showScorecard = false })
        ScorecardScreen(
            state = matchState,
            startOnFirstInnings = showInningsBreak && !matchOver,
            onBack = { showScorecard = false }
        )
        return
    }

    if (matchOver) {
        // No BackHandler here on purpose: the match is finished and
        // already cleared from autosave, so back falls through to
        // whatever the platform normally does, same as the Return to
        // home button's destination is reached through the button, not
        // by intercepting back into a redundant path.
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
    // like the gesture surfaces do. No BackHandler: a pending pick isn't
    // optional (the match cannot proceed without it), so there's nothing
    // for back to do here that isn't already handled by the match's own
    // top-level leave confirmation once this pick is resolved.
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
        BackHandler(onBack = { showPitchingScreen = false })
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
        BackHandler(onBack = { showBattingScreen = false })
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
        BackHandler(onBack = { showFieldScreen = false })
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

    // The ordinary match screen: no sub-view open. Back here opens the
    // leave confirmation, exactly like tapping the Leave match button —
    // see the doc comment above for why this asks first rather than
    // leaving straight away.
    BackHandler(onBack = { confirmingLeave = true })

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
        Spacer(modifier = Modifier.height(16.dp))

        // Leave match / Settings / Scorecard: the three navigation
        // actions, reachable right after the heading without swiping
        // through any live match state first.
        Button(onClick = { confirmingLeave = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Leave match")
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Sound, vibration, commentary and difficulty, changeable mid-match.
        Button(onClick = { showSettings = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Settings")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { showScorecard = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Scorecard")
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (isUserBowling) {
            // Set the field BEFORE bowling — it's a pre-delivery decision.
            Button(onClick = { showFieldScreen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Set field")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(MatchLines.strikerLine(matchState), style = MaterialTheme.typography.bodyLarge)
            Text(MatchLines.nonStrikerLine(matchState), style = MaterialTheme.typography.bodyLarge)
            Text(MatchLines.bowlerLine(matchState), style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { showPitchingScreen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Bowl")
            }
        } else {
            Button(onClick = { showBattingScreen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Face next ball")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(MatchLines.strikerLine(matchState), style = MaterialTheme.typography.bodyLarge)
            Text(MatchLines.nonStrikerLine(matchState), style = MaterialTheme.typography.bodyLarge)
            Text(MatchLines.bowlerLine(matchState), style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))

            // Read-only field info — secondary to the actual action, so
            // it comes after it.
            Button(onClick = { showFieldScreen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Hear the field")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Everything from here down comes after the primary action
        // button, for both roles.
        if (playNotice.isNotEmpty()) {
            Text(
                text = playNotice,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (fieldMessage.isNotEmpty()) {
            Text(
                text = fieldMessage,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (userFieldReason != null) {
            Text(
                text = "Your field is illegal. $userFieldReason Every delivery will be a no-ball until it is fixed.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

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
    }
}

package com.cricketsim.ui.match

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.cricketsim.audio.GameSettings
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.audio.MatchAudioDirector
import com.cricketsim.logic.BattingDecision
import com.cricketsim.logic.DrsCase
import com.cricketsim.logic.DrsSystem
import com.cricketsim.logic.DrsVerdict
import com.cricketsim.logic.FieldingSystem
import com.cricketsim.logic.FootworkType
import com.cricketsim.logic.MatchFormat
import com.cricketsim.logic.MatchState
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
 *   2b. a Decision Review in progress (a reviewable dismissal held back
 *      from the match until the review settles it — see below),
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
 * non-striker/bowler lines, then Bowl. While BATTING: Face next ball
 * (also where footwork is chosen — see FaceNextBallControl below), the
 * same three player lines, then Hear the field (read-only field info —
 * secondary to the actual action). Everything else — status messages,
 * the last ball, the score, the chase figures, the run rates, the win
 * probability and the earlier-innings list — comes after that primary
 * action button. The whole screen scrolls (a plain scrolling Column) so
 * nothing can be pushed off a small screen.
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
 * autosaved) — for the same reason the button itself asks first. Note
 * that BattingScreen itself no longer takes an onBack — see
 * FaceNextBallControl's doc comment for why leaving a ball in progress
 * is entirely this screen's own job now, via the BackHandler wrapping
 * showBattingScreen below, same as every other sub-view.
 *
 * `onBack` leaves the match (MainActivity sends you to the first screen,
 * where the autosave is offered as Resume) and is only reachable through
 * the leave confirmation; `onMatchFinished` is the finished match's Return
 * to home.
 *
 * DECISION REVIEWS (DRS — see DrsSystem.kt and DRS_NOTES.md). A ball is
 * always SIMULATED first, wicket and all, but when its dismissal is
 * reviewable and the batting side has a review left, advanceOneBall
 * holds the result in `pendingReview` instead of applying it, and
 * DrsReviewScreen plays the review. When it finishes, the held ball is
 * committed one of three ways: as simulated (no review asked for, or the
 * decision stood), with `drsReviewsUsed` raised by one (it stood
 * outright: the review is lost), or REPLAYED from the state before the
 * ball as a not-out (overturned; MatchSimulation.overturnDismissal).
 * Holding the ball back, rather than applying it and undoing it, is what
 * keeps an overturn from having to unpick a wicket, a replacement
 * batsman and an over change. Nothing about a review in progress is
 * saved: a resumed match replays the ball from before it.
 *
 * BOWLING SETUP MEMORY. `bowlingSetupMemory` remembers the last
 * PitchingScreen setup (angle/line/variation/speed) actually bowled with
 * — see PitchingScreen.kt's own "WHY THE BOWLING SETUP CARRIES BETWEEN
 * BALLS" doc comment for the full reasoning. It's cleared the moment
 * matchState.score.overs changes, so it only ever carries across balls
 * WITHIN the same over, never into the next one.
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
    // Set the instant "Face next ball" is tapped/held — see
    // FaceNextBallControl. Read once, when BattingScreen mounts.
    var pendingFootwork by remember { mutableStateOf(FootworkType.FRONT_FOOT) }
    // A reviewable dismissal that has been simulated but NOT yet applied to
    // the match, while the Decision Review System decides what happens to
    // it — see the class doc comment's "DECISION REVIEWS". Deliberately not
    // saved: a resumed match replays the ball from the state before it.
    var pendingReview by remember { mutableStateOf<PendingReview?>(null) }
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
    // See the class doc comment's "BOWLING SETUP MEMORY".
    var bowlingSetupMemory by remember { mutableStateOf<BowlingSetupMemory?>(null) }

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

    // A new over always starts with a fresh bowling plan — see the class
    // doc comment's "BOWLING SETUP MEMORY" and PitchingScreen.kt's "WHY
    // THE BOWLING SETUP CARRIES BETWEEN BALLS".
    LaunchedEffect(matchState.score.overs) {
        bowlingSetupMemory = null
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

    // Everything that happens once a ball's FINAL result is known: put it
    // in the match, record and announce it, and handle an innings or the
    // match ending. Split out of advanceOneBall so a ball held back for a
    // Decision Review can be committed later, either as it was simulated
    // or replayed as a not-out. `before` is the state the ball started from.
    fun commitBall(before: MatchState, result: BallResult) {
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

        // A dismissal that can be reviewed (LBW today; run out and stumped
        // as soon as the game has them) is HELD BACK rather than applied:
        // the Decision Review System decides whether it stands. Nothing is
        // committed until it does, so an overturned ball never has to be
        // undone. With no reviews left the dismissal simply goes ahead.
        val dismissal = result.outcome.dismissalType
        if (result.outcome.isWicket && dismissal != null && DrsSystem.isReviewable(dismissal)) {
            val reviewsLeft = DrsSystem.reviewsRemaining(before)
            val userBatting = before.battingTeam.id == before.userTeam.id
            if (reviewsLeft > 0) {
                pendingReview = PendingReview(
                    before = before,
                    result = result,
                    drsCase = DrsCase(
                        reviewingTeamName = before.battingTeam.name,
                        batterName = before.currentBatsmen.first.name,
                        bowlerName = before.currentBowler.name,
                        dismissalType = dismissal,
                        delivery = result.bowlingDecision,
                        // The BATTER's timing on the shot — the only thing
                        // that decides the odds (see DrsSystem.survivalChance).
                        timingTier = result.battingDecision.timingTier
                    ),
                    userReviewing = userBatting,
                    reviewsRemaining = reviewsLeft,
                    nextBatsmanNeeded = !MatchSimulation.isInningsOver(result.state)
                )
                return
            }
            if (userBatting) services?.announceSpoken("You have no reviews left.")
        }
        commitBall(before, result)
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

    // A dismissal is being reviewed (see the class doc comment's
    // "DECISION REVIEWS"). The screen swallows the system back button itself.
    val review = pendingReview
    if (review != null) {
        DrsReviewScreen(
            drsCase = review.drsCase,
            userIsReviewing = review.userReviewing,
            reviewsRemaining = review.reviewsRemaining,
            nextBatsmanNeeded = review.nextBatsmanNeeded,
            onFinished = { outcome ->
                pendingReview = null
                val finalResult = when (outcome) {
                    is DrsOutcome.Reviewed -> when (outcome.verdict) {
                        // Not out: replay the same ball without the wicket.
                        // The review is kept, so the count is untouched.
                        DrsVerdict.OVERTURNED -> MatchSimulation.overturnDismissal(
                            before = review.before,
                            original = review.result,
                            stadium = stadium,
                            difficulty = settings.difficulty,
                            commentary = DrsSystem.overturnedCommentary(
                                review.drsCase.dismissalType,
                                review.drsCase.batterName
                            )
                        )
                        // Out stays, but only because it was marginal: the
                        // review is kept.
                        DrsVerdict.UMPIRES_CALL -> review.result
                        // Out stays outright: the review is lost.
                        DrsVerdict.STANDS -> review.result.copy(
                            state = review.result.state.copy(
                                drsReviewsUsed = review.result.state.drsReviewsUsed + 1
                            )
                        )
                    }
                    // No review asked for (declined, timed out, or the AI
                    // chose not to): the dismissal goes ahead, review kept.
                    DrsOutcome.NotRequested -> review.result
                }
                commitBall(review.before, finalResult)
            }
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
            initialSetup = bowlingSetupMemory,
            onSetupChanged = { bowlingSetupMemory = it },
            onDeliveryResolved = { decision ->
                showPitchingScreen = false
                advanceOneBall(presetBowlingDecision = decision)
            },
            onBack = { showPitchingScreen = false }
        )
        return
    }

    if (showBattingScreen) {
        // BattingScreen itself takes no onBack — footwork (and so the
        // delivery reveal) is already committed by the time it's shown,
        // so there is nothing left inside it to safely back out of. This
        // BackHandler is what leaving a ball in progress actually means:
        // it discards this attempt and returns to the ordinary screen,
        // same as every other sub-view here.
        BackHandler(onBack = { showBattingScreen = false })
        BattingScreen(
            batsman = matchState.currentBatsmen.first,
            footwork = pendingFootwork,
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
            }
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
            FaceNextBallControl(
                onSelected = { footwork ->
                    pendingFootwork = footwork
                    showBattingScreen = true
                }
            )
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

/**
 * A reviewable dismissal held back while the Decision Review System
 * decides it. `before` is the match state the ball started from (what an
 * overturned ball is replayed from) and `result` is the ball as it was
 * simulated, wicket and all (what goes ahead if the dismissal stands).
 * See MatchScreen's "DECISION REVIEWS" doc paragraph.
 */
private class PendingReview(
    val before: MatchState,
    val result: BallResult,
    val drsCase: DrsCase,
    val userReviewing: Boolean,
    val reviewsRemaining: Int,
    val nextBatsmanNeeded: Boolean
)

/**
 * The single control that both starts the next ball AND commits
 * footwork in one gesture — the web plan's "single tap 'Next ball' /
 * double-tap-and-hold 'Next ball'" (see GESTURE_REDESIGN.md section 3),
 * folded into the SAME control that already read "Face next ball",
 * rather than a separate footwork step shown after it. An earlier pass
 * put footwork as BattingScreen's own first step with its own "Next
 * ball" control, which meant tapping this button and then immediately
 * being shown a second, near-identical "Next ball" control to tap or
 * hold. Folding the two together here means BattingScreen itself is
 * only ever shown once footwork (and, via `generateDelivery`, the ball
 * itself) is already decided, so it has no footwork step of its own and
 * no way to back out of one — see that file's "NO BACK ONCE THIS SCREEN
 * IS SHOWING".
 *
 * Built the same way as BattingScreen's other custom TalkBack-native
 * tap/press-and-hold controls: Compose's own `combinedClickable`
 * (TalkBack exposes click and long-click as distinct, natively
 * supported actions, so no custom gesture-detection code is needed here
 * the way the shot/intent swipe surfaces need it). Styled as a `Surface`
 * rather than a `Button`: `Button`'s own internal click handling would
 * intercept a tap before a `combinedClickable` layered on top of it
 * ever saw a chance to distinguish a long-press from it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FaceNextBallControl(onSelected: (FootworkType) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClickLabel = "Front foot",
                onLongClickLabel = "Back foot",
                role = Role.Button,
                onClick = { onSelected(FootworkType.FRONT_FOOT) },
                onLongClick = { onSelected(FootworkType.BACK_FOOT) }
            ),
        shape = ButtonDefaults.shape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Face next ball", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Tap for front foot. Press and hold for back foot.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

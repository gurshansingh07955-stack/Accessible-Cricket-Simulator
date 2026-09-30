package com.cricketsim.ui.match

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.CommentaryCategory
import com.cricketsim.logic.DrsCase
import com.cricketsim.logic.DrsReport
import com.cricketsim.logic.DrsSystem
import com.cricketsim.logic.DrsVerdict
import kotlinx.coroutines.delay

/**
 * How a review flow ended, handed back to MatchScreen, which owns what
 * that means for the match (see MatchScreen's pendingReview handling).
 */
sealed interface DrsOutcome {
    /**
     * No review happened: the user let the 10 seconds run out or chose to
     * move on, or the AI decided not to ask. The decision stands and no
     * review is used.
     */
    object NotRequested : DrsOutcome

    /** A review was asked for and played out to this verdict. */
    data class Reviewed(val verdict: DrsVerdict) : DrsOutcome
}

private enum class DrsStage { OFFER, AI_DECIDING, PROCESS, RESULT }

/**
 * The whole Decision Review System flow for one dismissal, as a single
 * screen that moves through its stages itself:
 *
 *   USER out:  OFFER (10 seconds to ask, or move on) -> PROCESS -> RESULT
 *   AI out:    AI_DECIDING (under 5 seconds) -> PROCESS -> RESULT
 *              (or straight out, if the AI decides not to review)
 *
 * The result of a review is rolled ONCE, when this screen first appears
 * (DrsSystem.resolve), but nothing about it is shown or used until a
 * review is actually requested, so declining or timing out simply throws
 * it away. See DrsSystem for the rules, including why the chance depends
 * on the BATTER's timing.
 *
 * SPEECH. Each tracking step is spoken through `announceSpoken` (a
 * TalkBack announcement when a screen reader is running, otherwise the
 * game's own voice) using the step's SHORT `spoken` text, because steps
 * arrive 1.5 seconds apart and longer speech would fall behind. The full
 * `detail` text is on screen. Nothing here is also a live region, so
 * TalkBack doesn't read anything twice. Every stage swallows the system
 * back button: there is no leaving a review half way, and no back button
 * drawn either.
 *
 * TIME. Every clock here is SystemClock.elapsedRealtime, not a counter of
 * delays, so a slow frame or a busy TalkBack never stretches the 10
 * seconds. All of this is UNTESTED on a device with TalkBack.
 *
 * AUDIO (see SoundEngine's "Decision Review System" section). The ten
 * seconds of checks open with a short third-umpire tone and a heartbeat
 * plus a deliberately quiet music bed underneath it (SoundEngine
 * .startDrsReviewAudio, stopped again the moment the process ends), and
 * one line of commentary (CommentaryCategory.REVIEW_REQUESTED). The
 * verdict plays its own commentary line (REVIEW_OVERTURNED /
 * REVIEW_UMPIRES_CALL / REVIEW_STANDS, chosen purely by what happened,
 * same convention as every other category in CommentaryLibrary) and a
 * sound effect chosen by who the verdict FAVORS rather than what it is: a
 * firecracker when it favors the user, a different sound when it doesn't,
 * and a third, distinct sound for an Umpire's Call that goes against the
 * user — see DrsResultStage.
 *
 * @param reviewsRemaining the batting side's reviews left BEFORE this one.
 * @param nextBatsmanNeeded false when the dismissal ended the innings, so
 *   there is no "choose next batsman" to offer.
 */
@Composable
fun DrsReviewScreen(
    drsCase: DrsCase,
    userIsReviewing: Boolean,
    reviewsRemaining: Int,
    nextBatsmanNeeded: Boolean,
    onFinished: (DrsOutcome) -> Unit
) {
    BackHandler(onBack = {})

    val currentOnFinished by rememberUpdatedState(onFinished)
    var stage by remember { mutableStateOf(if (userIsReviewing) DrsStage.OFFER else DrsStage.AI_DECIDING) }
    val report = remember(drsCase) { DrsSystem.resolve(drsCase) }

    // Guards against the button and the timer both finishing the flow.
    val finished = remember { booleanArrayOf(false) }
    // Set the instant a review is asked for, so the 10-second timer
    // expiring in the same frame can't override the request with "move on".
    val requested = remember { booleanArrayOf(false) }
    fun finish(outcome: DrsOutcome) {
        if (finished[0]) return
        finished[0] = true
        currentOnFinished(outcome)
    }

    when (stage) {
        DrsStage.OFFER -> DrsOfferStage(
            drsCase = drsCase,
            reviewsRemaining = reviewsRemaining,
            nextBatsmanNeeded = nextBatsmanNeeded,
            onRequestReview = {
                if (!finished[0]) {
                    requested[0] = true
                    stage = DrsStage.PROCESS
                }
            },
            onMoveOn = { if (!requested[0]) finish(DrsOutcome.NotRequested) }
        )
        DrsStage.AI_DECIDING -> DrsAiDecidingStage(
            drsCase = drsCase,
            reviewsRemaining = reviewsRemaining,
            onDecided = { wantsReview ->
                if (wantsReview) stage = DrsStage.PROCESS else finish(DrsOutcome.NotRequested)
            }
        )
        DrsStage.PROCESS -> DrsProcessStage(
            drsCase = drsCase,
            report = report,
            onComplete = { stage = DrsStage.RESULT }
        )
        DrsStage.RESULT -> DrsResultStage(
            drsCase = drsCase,
            report = report,
            userIsReviewing = userIsReviewing,
            reviewsRemaining = reviewsRemaining,
            nextBatsmanNeeded = nextBatsmanNeeded,
            onContinue = { finish(DrsOutcome.Reviewed(report.verdict)) }
        )
    }
}

/** The user's own batter is out: DRS or move on, within 10 seconds. */
@Composable
private fun DrsOfferStage(
    drsCase: DrsCase,
    reviewsRemaining: Int,
    nextBatsmanNeeded: Boolean,
    onRequestReview: () -> Unit,
    onMoveOn: () -> Unit
) {
    val services = LocalGameServices.current
    val currentServices by rememberUpdatedState(services)
    val currentOnMoveOn by rememberUpdatedState(onMoveOn)
    val windowSeconds = (DrsSystem.USER_DECISION_WINDOW_MS / 1000L).toInt()
    var secondsLeft by remember { mutableStateOf(windowSeconds) }
    val label = DrsSystem.dismissalLabel(drsCase.dismissalType)
    val moveOnText = if (nextBatsmanNeeded) "Choose next batsman" else "Accept decision"

    LaunchedEffect(Unit) {
        currentServices?.announceSpoken(
            "${drsCase.batterName} is out, $label. Request a review, or " +
                (if (nextBatsmanNeeded) "choose the next batsman" else "accept the decision") +
                ". You have $windowSeconds seconds."
        )
        val start = SystemClock.elapsedRealtime()
        var halfwayAnnounced = false
        while (true) {
            val left = DrsSystem.USER_DECISION_WINDOW_MS - (SystemClock.elapsedRealtime() - start)
            if (left <= 0L) break
            secondsLeft = ((left + 999L) / 1000L).toInt()
            if (!halfwayAnnounced && left <= 5_000L) {
                halfwayAnnounced = true
                currentServices?.announceSpoken("5 seconds left.")
            }
            delay(200)
        }
        // The window closed with no review asked for.
        currentOnMoveOn()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "${drsCase.batterName} is out, $label",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // The two choices come first, so they are the first things reached
        // with a screen reader while the clock is running.
        Button(onClick = onRequestReview, modifier = Modifier.fillMaxWidth()) {
            Text("DRS review, ${countOf(reviewsRemaining, "review")} left")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onMoveOn, modifier = Modifier.fillMaxWidth()) {
            Text(moveOnText)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("Time left: ${countOf(secondsLeft, "second")}.", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Your timing on that shot was ${DrsSystem.timingWord(drsCase.timingTier)}.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text("Bowler: ${drsCase.bowlerName}.", style = MaterialTheme.typography.bodyMedium)
    }
}

/** The AI's batter is out: it takes a few seconds, under five, to decide. */
@Composable
private fun DrsAiDecidingStage(
    drsCase: DrsCase,
    reviewsRemaining: Int,
    onDecided: (Boolean) -> Unit
) {
    val services = LocalGameServices.current
    val currentServices by rememberUpdatedState(services)
    val currentOnDecided by rememberUpdatedState(onDecided)
    // Both settled up front, so what happens is fixed the moment the wait begins.
    val wantsReview = remember { DrsSystem.aiWantsReview(drsCase.timingTier, reviewsRemaining) }
    val delayMs = remember { DrsSystem.aiDecisionDelayMs() }
    val label = DrsSystem.dismissalLabel(drsCase.dismissalType)

    LaunchedEffect(Unit) {
        currentServices?.announceSpoken(
            "${drsCase.batterName} is out, $label. ${drsCase.reviewingTeamName} are deciding whether to review."
        )
        delay(delayMs)
        currentServices?.announceSpoken(
            if (wantsReview) "${drsCase.reviewingTeamName} have asked for a review." else "No review."
        )
        currentOnDecided(wantsReview)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "${drsCase.batterName} is out, $label",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "${drsCase.reviewingTeamName} are deciding whether to review.",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            "${countOf(reviewsRemaining, "review")} left.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** The ten seconds of checks: each step appears, and is spoken, in turn. */
@Composable
private fun DrsProcessStage(
    drsCase: DrsCase,
    report: DrsReport,
    onComplete: () -> Unit
) {
    val services = LocalGameServices.current
    val currentServices by rememberUpdatedState(services)
    val currentOnComplete by rememberUpdatedState(onComplete)
    var elapsedMs by remember { mutableStateOf(0L) }
    val label = DrsSystem.dismissalLabel(drsCase.dismissalType)

    LaunchedEffect(Unit) {
        // The moment a review actually begins: the third umpire's checking
        // tone, the heartbeat and the (deliberately quiet) music bed, and
        // one line of commentary. All three sounds are stopped together
        // when the process ends, a few lines down.
        currentServices?.sound?.playDrsReviewCheck()
        currentServices?.sound?.startDrsReviewAudio()
        currentServices?.sound?.enqueueCommentary(CommentaryCategory.REVIEW_REQUESTED)

        val start = SystemClock.elapsedRealtime()
        var announced = 0
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - start
            elapsedMs = elapsed.coerceAtMost(DrsSystem.REVIEW_DURATION_MS)
            val revealed = report.steps.count { it.atMs <= elapsed }
            while (announced < revealed) {
                currentServices?.announceSpoken(report.steps[announced].spoken)
                announced++
            }
            if (elapsed >= DrsSystem.REVIEW_DURATION_MS) break
            delay(100)
        }
        currentServices?.sound?.stopDrsReviewAudio()
        currentOnComplete()
    }

    val secondsLeft = ((DrsSystem.REVIEW_DURATION_MS - elapsedMs + 999L) / 1000L).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Decision review",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            "$label decision against ${drsCase.batterName}.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(12.dp))
        DrsProgressBar(fraction = elapsedMs.toFloat() / DrsSystem.REVIEW_DURATION_MS.toFloat())
        Spacer(modifier = Modifier.height(4.dp))
        Text("Time remaining: ${countOf(secondsLeft, "second")}.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        report.steps.filter { it.atMs <= elapsedMs }.forEach { step ->
            Text(step.title, style = MaterialTheme.typography.titleSmall)
            Text(step.detail, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/** The verdict, what it means for the reviews, and the one way forward. */
@Composable
private fun DrsResultStage(
    drsCase: DrsCase,
    report: DrsReport,
    userIsReviewing: Boolean,
    reviewsRemaining: Int,
    nextBatsmanNeeded: Boolean,
    onContinue: () -> Unit
) {
    val services = LocalGameServices.current
    val currentServices by rememberUpdatedState(services)

    // Only a decision that stands outright costs a review.
    val remainingAfter = if (report.verdict == DrsVerdict.STANDS) reviewsRemaining - 1 else reviewsRemaining
    val who = if (userIsReviewing) "You" else drsCase.reviewingTeamName
    val keep = if (userIsReviewing) "You keep" else "${drsCase.reviewingTeamName} keep"
    val left = if (userIsReviewing) "You have" else "${drsCase.reviewingTeamName} have"
    val reviewLine = when (report.verdict) {
        DrsVerdict.OVERTURNED ->
            "Review successful. ${drsCase.batterName} is not out. $keep the review. " +
                "$left ${countOf(remainingAfter, "review")} left."
        DrsVerdict.UMPIRES_CALL ->
            "Umpire's call. $keep the review. $left ${countOf(remainingAfter, "review")} left."
        DrsVerdict.STANDS ->
            "Review unsuccessful. $who lose the review. $left ${countOf(remainingAfter, "review")} left."
    }

    // The user faces the next ball if they survive; otherwise picks a
    // replacement (unless that was the last wicket). The AI's side just continues.
    val buttonText = if (userIsReviewing && report.verdict != DrsVerdict.OVERTURNED && nextBatsmanNeeded) {
        "Choose next batsman"
    } else {
        "Continue"
    }

    LaunchedEffect(Unit) {
        // The commentary category reflects what HAPPENED (same convention
        // as every other category in CommentaryLibrary — a wicket line
        // plays the same regardless of which side is happy about it); the
        // sound effect reflects who it FAVORS, which is what the user
        // asked to hear distinctly. Out stands and benefits whichever side
        // is bowling; overturned benefits whichever side is batting.
        val sound = currentServices?.sound
        sound?.enqueueCommentary(
            when (report.verdict) {
                DrsVerdict.OVERTURNED -> CommentaryCategory.REVIEW_OVERTURNED
                DrsVerdict.UMPIRES_CALL -> CommentaryCategory.REVIEW_UMPIRES_CALL
                DrsVerdict.STANDS -> CommentaryCategory.REVIEW_STANDS
            }
        )
        when (report.verdict) {
            DrsVerdict.OVERTURNED -> if (userIsReviewing) sound?.playDrsFirecracker() else sound?.playDrsLose()
            DrsVerdict.STANDS -> if (userIsReviewing) sound?.playDrsLose() else sound?.playDrsFirecracker()
            DrsVerdict.UMPIRES_CALL -> if (userIsReviewing) sound?.playDrsUmpiresCallAgainst() else sound?.playDrsFirecracker()
        }
        currentServices?.announceSpoken("${report.headline} ${report.explanation} $reviewLine")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = report.headline,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(report.explanation, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(reviewLine, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(buttonText)
        }
    }
}

/** A plain filled bar. Its semantics are cleared so a screen reader never chatters about progress. */
@Composable
private fun DrsProgressBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clearAndSetSemantics { }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

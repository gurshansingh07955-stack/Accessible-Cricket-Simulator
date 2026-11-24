package com.cricketsim.ui.match

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.GameServices
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.audio.UmpireLines
import com.cricketsim.logic.BowlingQualityTier
import com.cricketsim.logic.DrsSystem
import com.cricketsim.logic.DrsVerdict
import com.cricketsim.logic.UmpireDecisions
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * The FIELDING side's review of a NOT OUT decision (patch 142). The umpire has turned down an appeal for an LBW, a catch behind the stumps or a stumping; the
 * side that was bowling may ask the third umpire.
 *
 *  - OFFER (the user is bowling): "Review" or "Accept the decision", ten seconds ([DrsSystem.USER_DECISION_WINDOW_MS]); running out of time is accepting.
 *  - AI_DECIDING (the computer is bowling): it thinks for a moment and asks, or not, by [aiChance] (UmpireDecisions.aiFieldingReviewChance).
 *  - PROCESS: the on-field umpire refers the decision, then the third umpire's checks, each spoken in HIS RECORDED VOICE (the same clips the batting review uses,
 *    and a few new ones; UmpireLines), one after the other, ending in the verdict, which was rolled when the screen opened ([UmpireDecisions.fieldingReviewVerdict])
 *    so the checks agree with it. Without a recording the phone's voice reads the line.
 *  - RESULT: OVERTURNED = the decision is changed to OUT (the review is kept); UMPIRE'S CALL = not out stands (kept); STANDS = not out stands (review lost).
 *
 * [onFinished] is told whether a review was asked for and the verdict (only meaningful if it was). The batting side's review is DrsScreens.kt, which is built
 * around the batter's side; this is its counterpart for the bowling side.
 */
private enum class FieldStage { OFFER, AI_DECIDING, PROCESS, RESULT }

/** One of the third umpire's lines in his recorded voice, or the phone's voice if the recording is missing. Returns when he has finished. */
private suspend fun speakThird(services: GameServices?, text: String) {
    val clip = UmpireLines.thirdClipFor(text)
    if (services != null && clip != null && services.sound.playUmpire(clip)) {
        services.awaitUmpire()
        delay(350L)
    } else {
        services?.announceSpoken(text)
        delay(1500L)
    }
}

@Composable
fun FieldingReviewScreen(
    userReviewing: Boolean,
    kind: UmpireDecisions.ReviewKind,
    teamName: String,
    batterName: String,
    bowlerName: String,
    reviewsRemaining: Int,
    truthOut: Boolean,
    qualityTier: BowlingQualityTier?,
    aiChance: Double,
    onFinished: (requested: Boolean, verdict: DrsVerdict) -> Unit
) {
    BackHandler(onBack = {})
    val services = LocalGameServices.current
    val currentServices by rememberUpdatedState(services)
    val currentOnFinished by rememberUpdatedState(onFinished)
    val verdict = remember { UmpireDecisions.fieldingReviewVerdict(kind, truthOut, qualityTier) }
    val aiWants = remember { Random.nextDouble() < aiChance }
    var stage by remember { mutableStateOf(if (userReviewing) FieldStage.OFFER else FieldStage.AI_DECIDING) }
    var secondsLeft by remember { mutableStateOf((DrsSystem.USER_DECISION_WINDOW_MS / 1000L).toInt()) }
    var step by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    val reviewsText = DrsSystem.reviewsText(reviewsRemaining)
    val who = if (userReviewing) "You" else teamName
    val label = when (kind) {
        UmpireDecisions.ReviewKind.LBW -> "LBW"
        UmpireDecisions.ReviewKind.CAUGHT_BEHIND -> "caught behind"
        UmpireDecisions.ReviewKind.STUMPED -> "stumping"
    }

    fun finish(requested: Boolean) {
        if (done) return
        done = true
        currentServices?.sound?.stopDrsReviewAudio()
        currentOnFinished(requested, verdict)
    }

    // The checks the third umpire makes, agreeing with the verdict that was rolled. Every text is exactly one of UmpireLines.THIRD, so it is spoken in his voice.
    val noBall = "Checking for a no-ball. The front foot is behind the line. Legal delivery."
    val steps: List<String> = when (kind) {
        UmpireDecisions.ReviewKind.LBW -> when (verdict) {
            DrsVerdict.OVERTURNED -> listOf(
                noBall, "Checking for an edge. No spike. No bat involved.", "Ball tracking. Pitching: in line.", "Impact: in line.", "Wickets: hitting."
            )
            DrsVerdict.UMPIRES_CALL -> listOf(
                noBall, "Checking for an edge. No spike. No bat involved.", "Ball tracking. Pitching: in line.", "Impact: in line.", "Wickets: umpire's call."
            )
            DrsVerdict.STANDS -> listOf(
                noBall,
                "Checking for an edge. No spike. No bat involved.",
                if (truthOut) "Ball tracking. Pitching: in line." else "Ball tracking. Pitching: outside leg.",
                if (truthOut) "Impact: in line." else "Impact: outside off, and the batter was playing a shot.",
                "Wickets: missing."
            )
        }
        UmpireDecisions.ReviewKind.CAUGHT_BEHIND -> when (verdict) {
            DrsVerdict.OVERTURNED -> listOf(
                noBall, "Checking for an edge. There is a spike. The bat is involved.", "Checking the catch. The ball has carried cleanly to the keeper."
            )
            else -> listOf(
                noBall,
                if (truthOut) "Checking for an edge. The spike is not clear. There is no conclusive evidence." else "Checking for an edge. No spike. No bat involved.",
                "Slow motion replay."
            )
        }
        UmpireDecisions.ReviewKind.STUMPED -> when (verdict) {
            DrsVerdict.OVERTURNED -> listOf(
                noBall,
                "Checking the keeper. The ball was collected behind the stumps.",
                "The foot is in the air. The batter is out.",
                "The bails are removed with the ball in the keeper's glove."
            )
            else -> listOf(
                noBall,
                if (truthOut) "Checking the keeper. The ball was collected behind the stumps." else "Checking the keeper. The ball was taken in front of the stumps.",
                "The foot is grounded behind the line. The batter is in.",
                "Checking the evidence."
            )
        }
    }
    val resultLine = when (verdict) {
        DrsVerdict.OVERTURNED -> "Out. The decision is overturned."
        DrsVerdict.UMPIRES_CALL -> "Umpire's call. The on-field decision stands. Not out."
        DrsVerdict.STANDS -> "Not out. The on-field decision stands."
    }
    val keptLine = if (verdict == DrsVerdict.STANDS) "The review is lost." else "The review is kept."

    // The offer's countdown, or the computer's decision.
    LaunchedEffect(stage) {
        if (stage == FieldStage.OFFER) {
            currentServices?.announceSpoken("The umpire has given $batterName not out. Do you review the $label decision? You have $reviewsText. You have ${DrsSystem.USER_DECISION_WINDOW_MS / 1000L} seconds.")
            var left = DrsSystem.USER_DECISION_WINDOW_MS
            while (left > 0L && stage == FieldStage.OFFER) {
                secondsLeft = ((left + 999L) / 1000L).toInt()
                delay(200L)
                left -= 200L
            }
            if (stage == FieldStage.OFFER) finish(false)
        } else if (stage == FieldStage.AI_DECIDING) {
            currentServices?.announceSpoken("The umpire has given $batterName not out. $teamName are thinking about a review.")
            delay(DrsSystem.aiDecisionDelayMs())
            if (aiWants) {
                currentServices?.announceSpoken("$teamName have asked for a review.")
                delay(1200L)
                stage = FieldStage.PROCESS
            } else {
                currentServices?.announceSpoken("$teamName do not review.")
                delay(1200L)
                finish(false)
            }
        }
    }
    // The umpires' words: the on-field umpire refers it, the third umpire checks, one after the other, then the verdict.
    LaunchedEffect(stage) {
        if (stage == FieldStage.PROCESS) {
            val s = currentServices
            s?.sound?.playDrsReviewCheck()
            s?.sound?.startDrsReviewAudio()
            s?.umpireSay(listOf("um_review_field"), "The fielding side has asked for a review. I am referring the decision to the third umpire.")
            s?.awaitUmpire()
            for (line in steps) {
                step = line
                speakThird(s, line)
                // The ball is hitting the stumps: the bowled sound, as the third umpire's line ends.
                if (line == "Wickets: hitting.") {
                    s?.sound?.playBowledHit()
                    delay(900L)
                }
            }
            s?.sound?.stopDrsReviewAudio()
            stage = FieldStage.RESULT
        } else if (stage == FieldStage.RESULT) {
            val s = currentServices
            val favoursUser = (verdict == DrsVerdict.OVERTURNED) == userReviewing
            when (verdict) {
                DrsVerdict.UMPIRES_CALL -> if (userReviewing) s?.sound?.playDrsUmpiresCallAgainst() else s?.sound?.playDrsFirecracker()
                else -> if (favoursUser) s?.sound?.playDrsFirecracker() else s?.sound?.playDrsLose()
            }
            // The verdict in the third umpire's voice, then whether the review was kept.
            val clip = when (verdict) {
                DrsVerdict.OVERTURNED -> "tu_f_overturned"
                DrsVerdict.UMPIRES_CALL -> "tu_f_call"
                DrsVerdict.STANDS -> "tu_f_stands"
            }
            if (s != null && s.sound.playUmpire(clip)) {
                s.awaitUmpire()
            } else {
                s?.announceSpoken(resultLine)
                delay(1500L)
            }
            s?.announceSpoken(keptLine)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        when (stage) {
            FieldStage.OFFER -> {
                Text("Review the not out?", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Spacer(modifier = Modifier.height(12.dp))
                Text("The umpire has given $batterName not out. $bowlerName's side may ask the third umpire about the $label decision. You have $reviewsText.", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                // The two choices come first, so they are the first things reached with a screen reader.
                Button(onClick = { stage = FieldStage.PROCESS }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text("Review") }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { finish(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Accept the decision") }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Time left: $secondsLeft seconds.", style = MaterialTheme.typography.bodyLarge)
            }
            FieldStage.AI_DECIDING -> {
                Text("Not out", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Spacer(modifier = Modifier.height(12.dp))
                Text("The umpire has given $batterName not out. $teamName are deciding whether to review.", style = MaterialTheme.typography.bodyLarge)
            }
            FieldStage.PROCESS -> {
                Text("Third umpire review", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Spacer(modifier = Modifier.height(12.dp))
                Text("$who asked for a review of the not out $label decision.", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(12.dp))
                Text(step, style = MaterialTheme.typography.titleMedium)
            }
            FieldStage.RESULT -> {
                Button(onClick = { finish(true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text("Continue") }
                Spacer(modifier = Modifier.height(24.dp))
                Text(resultLine, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Spacer(modifier = Modifier.height(12.dp))
                Text(keptLine, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

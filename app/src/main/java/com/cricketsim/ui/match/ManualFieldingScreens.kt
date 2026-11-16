package com.cricketsim.ui.match

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.BattingSystem
import com.cricketsim.logic.CatchChance
import com.cricketsim.logic.CatchOutcome
import com.cricketsim.logic.FieldingKind
import com.cricketsim.logic.FullFielding
import com.cricketsim.logic.IntentDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.sqrt

private const val CATCH_LEAD_IN_MS_STANDARD = 550L
private const val CATCH_LEAD_IN_MS_SCREEN_READER = 1400L
private const val CATCH_NO_SWIPE_GRACE_INTERVALS = 2.0
private const val RUN_OUT_SECOND_SWIPE_MS = 700L
private val CATCH_SWIPE_THRESHOLD_DP = 24.dp

/**
 * A fielding chance for the user (manual fielding): a catch, a stumping, a run out or a ball to stop. A full-screen page that comes by itself.
 *  1. The phone's own voice (always on, with or without TalkBack) says who, where, his fielding rating and what to do. The buzzes start after it has finished.
 *  2. Buzzes (a vibration and a tick each, the last one accented, as in batting) at the speed of the ball and the timing of the shot.
 *  3. The swipe on the last buzz: up, left or right (a catch, a stop), down (a stumping), down twice quickly (a run out).
 * The page ends by itself the moment the swipe is judged; "Let the computer take it" is the way out for anyone who cannot swipe.
 */
@Composable
fun CatchScreen(chance: CatchChance, onResolved: (CatchOutcome) -> Unit) {
    val context = LocalContext.current
    val services = LocalGameServices.current
    val sound = services?.sound
    val scope = rememberCoroutineScope()
    val currentOnResolved by rememberUpdatedState(onResolved)
    val screenReaderActive = remember {
        (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true
    }
    val leadInMs = if (screenReaderActive) CATCH_LEAD_IN_MS_SCREEN_READER else CATCH_LEAD_IN_MS_STANDARD
    // Milliseconds after the clock starts at which the last buzz lands.
    val expectedMs = leadInMs + (chance.pulses - 1) * chance.intervalMs
    var startMs by remember(chance) { mutableStateOf(0L) }
    var pulsesFired by remember(chance) { mutableStateOf(0) }
    var resolved by remember(chance) { mutableStateOf(false) }
    var status by remember(chance) { mutableStateOf("The phone is telling you what to do.") }
    // A run out needs two swipes down: when the first came, and how well timed it was.
    var firstDownAt by remember(chance) { mutableStateOf(0L) }
    var firstDownActualMs by remember(chance) { mutableStateOf(0.0) }

    fun finish(outcome: CatchOutcome, note: String) {
        if (resolved) return
        resolved = true
        status = note
        when (outcome) {
            CatchOutcome.TAKEN -> sound?.vibrate(60, 40, 160)
            CatchOutcome.DROPPED -> sound?.vibrate(350)
            CatchOutcome.COMPUTER -> Unit
        }
        currentOnResolved(outcome)
    }

    BackHandler { }

    LaunchedEffect(chance) {
        // 1. The call, in the phone's own voice; wait for it to end (a missing or silent voice does not hold things up for long).
        val phone = services?.phoneSpeech
        if (phone != null) {
            withTimeoutOrNull(15_000L) {
                suspendCancellableCoroutine<Unit> { cont ->
                    phone.speak(FullFielding.announcement(chance)) { if (cont.isActive) cont.resume(Unit) }
                    cont.invokeOnCancellation { phone.stop() }
                }
            }
        }
        if (resolved) return@LaunchedEffect
        // 2. The buzzes, each scheduled against the start time so the gaps never drift.
        val start = SystemClock.elapsedRealtime()
        startMs = start
        status = "Get ready."
        for (i in 0 until chance.pulses) {
            val target = start + leadInMs + (i * chance.intervalMs).toLong()
            val wait = target - SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait)
            if (resolved) return@LaunchedEffect
            pulsesFired = i + 1
            sound?.vibratePulse()
            sound?.playTimingTick(accent = i == chance.pulses - 1)
        }
        // 3. No swipe at all: after a grace period it goes wrong.
        delay((chance.intervalMs * CATCH_NO_SWIPE_GRACE_INTERVALS).toLong())
        val verdict = FullFielding.judge(chance, null, 0.0, expectedMs, false)
        finish(CatchOutcome.DROPPED, verdict.reason)
    }

    fun handleSwipe(direction: IntentDirection) {
        // A swipe before the buzzes have started is ignored (the voice is still talking).
        if (startMs == 0L || resolved) return
        val actualMs = (SystemClock.elapsedRealtime() - startMs).toDouble()
        if (chance.kind == FieldingKind.RUN_OUT) {
            if (direction != IntentDirection.DOWN) {
                finish(CatchOutcome.DROPPED, "Wrong way.")
                return
            }
            if (firstDownAt == 0L) {
                firstDownAt = SystemClock.elapsedRealtime()
                firstDownActualMs = actualMs
                status = "Swipe down once more."
                scope.launch {
                    delay(RUN_OUT_SECOND_SWIPE_MS)
                    if (!resolved) finish(CatchOutcome.DROPPED, "You need two quick swipes.")
                }
            } else if (SystemClock.elapsedRealtime() - firstDownAt <= RUN_OUT_SECOND_SWIPE_MS) {
                // Judged on the FIRST swipe's timing, now that the second has come.
                val verdict = FullFielding.judge(chance, IntentDirection.DOWN, firstDownActualMs, expectedMs, true)
                finish(if (verdict.caught) CatchOutcome.TAKEN else CatchOutcome.DROPPED, verdict.reason)
            }
            return
        }
        val verdict = FullFielding.judge(chance, direction, actualMs, expectedMs, false)
        finish(if (verdict.caught) CatchOutcome.TAKEN else CatchOutcome.DROPPED, verdict.reason)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(chance) {
                    detectCatchSwipe(CATCH_SWIPE_THRESHOLD_DP.toPx()) { handleSwipe(it) }
                }
                .semantics {
                    contentDescription = when (chance.kind) {
                        FieldingKind.STUMPING -> "Stumping chance. Swipe down with two fingers on the last buzz."
                        FieldingKind.RUN_OUT -> "Run out chance. Swipe down twice quickly with two fingers on the last buzz."
                        FieldingKind.STOP -> "Stop the ball. Swipe up, left or right with two fingers on the last buzz."
                        FieldingKind.CATCH -> "Catch. Swipe up, left or right with two fingers on the last buzz."
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Purely visual: the phone's voice does the talking, so a screen reader must not read this a second time.
            Column(
                modifier = Modifier.clearAndSetSemantics { },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(FullFielding.title(chance), style = MaterialTheme.typography.headlineMedium)
                Text(
                    when {
                        chance.bowlerCatch -> "${chance.fielderName}, rating ${chance.fielderRating}: a catch back to the bowler."
                        chance.kind == FieldingKind.STUMPING -> "${chance.batterName} is out of his crease. ${chance.fielderName}, rating ${chance.fielderRating}."
                        chance.kind == FieldingKind.RUN_OUT -> "${chance.batterName} is short. ${chance.fielderName}, rating ${chance.fielderRating}."
                        else -> "${chance.fielderName}${chance.position?.let { ", $it" } ?: ""}, rating ${chance.fielderRating}."
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(FullFielding.instruction(chance), style = MaterialTheme.typography.titleLarge)
                Text(
                    if (pulsesFired == 0) status else "Buzz $pulsesFired of ${chance.pulses}",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { finish(CatchOutcome.COMPUTER, "The computer takes it.") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Let the computer take it") }
    }
}

/** One swipe per touch: decided the moment the first finger has moved far enough (the first finger is followed, a second one riding along is ignored). */
private suspend fun PointerInputScope.detectCatchSwipe(thresholdPx: Float, onDirection: (IntentDirection) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val pointerId = down.id
        val start = down.position
        var decided = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull() ?: return@awaitEachGesture
            change.consume()
            val dx = (change.position.x - start.x).toDouble()
            val dy = (change.position.y - start.y).toDouble()
            if (!decided && sqrt(dx * dx + dy * dy) >= thresholdPx) {
                decided = true
                onDirection(BattingSystem.classifyIntentDirection(dx, dy))
            }
            if (!change.pressed) return@awaitEachGesture
        }
    }
}

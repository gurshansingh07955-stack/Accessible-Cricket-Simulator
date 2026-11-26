package com.cricketsim.ui.match

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.BattingSystem
import com.cricketsim.logic.IntentDirection
import com.cricketsim.logic.RunChance
import com.cricketsim.logic.RunDecisions
import com.cricketsim.logic.RunVerdict
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.sqrt

private val RUN_SWIPE_THRESHOLD_DP = 24.dp

/**
 * Running between the wickets (patch 126), a full-screen page that comes by itself when the user is batting and the ball is worth runs (RunDecisions).
 *  1. The phone's own voice (always on, with or without TalkBack) says where the ball went, who is closing in and what to do.
 *  2. Five seconds (counted from the end of the voice) to answer: swipe UP with two fingers (run) or DOWN (stay), or, when Settings > Running between the
 *     wickets > "Buttons on the screen" is chosen (patch 129, [byButtons]), press Run or Stay. The two ways are never both on the screen. No answer = no run.
 *  3. The answer is spoken ("Yes!" / "No!" in recorded voices), then after a run the runs completed and where the ball and the fielder are, and the next decision.
 * A run out, a refusal, the last run he may take or a timeout ends the page with the runs completed ([RunVerdict]).
 */
@Composable
fun RunDecisionScreen(chance: RunChance, byButtons: Boolean = false, onDone: (RunVerdict) -> Unit) {
    val services = LocalGameServices.current
    val sound = services?.sound
    val scope = rememberCoroutineScope()
    val currentOnDone by rememberUpdatedState(onDone)
    var completed by remember(chance) { mutableStateOf(0) }
    var finished by remember(chance) { mutableStateOf(false) }
    var deciding by remember(chance) { mutableStateOf(false) }
    var secondsLeft by remember(chance) { mutableStateOf(0) }
    // Overthrows so far, and what was said about the last throw (patch 151).
    var overthrows by remember(chance) { mutableStateOf(0) }
    var throwNote by remember(chance) { mutableStateOf("") }
    var shown by remember(chance) { mutableStateOf(RunDecisions.opening(chance, byButtons)) }
    val random = remember(chance) { kotlin.random.Random.Default }

    // [maxMs]: the lines that end the page are only waited for briefly (patch 150): the page used to sit still until the whole sentence had been spoken.
    suspend fun say(text: String, maxMs: Long = 15_000L) {
        val phone = services?.phoneSpeech ?: return
        withTimeoutOrNull(maxMs) {
            suspendCancellableCoroutine<Unit> { cont ->
                phone.speak(text) { if (cont.isActive) cont.resume(Unit) }
                cont.invokeOnCancellation { phone.stop() }
            }
        }
    }

    // The recorded "Yes!" / "No!" (run_yes and run_no in the audio pack, the player's voice); the phone's own voice says [fallback] if the clip is missing
    // or sound effects are off. Waits until the clip has finished, so the next line does not cut it off.
    suspend fun call(clip: String, fallback: String) {
        if (sound != null && sound.playPress(clip)) {
            val until = System.currentTimeMillis() + 2500L
            delay(150L)
            while (sound.isPressSpeaking() && System.currentTimeMillis() < until) delay(50L)
        } else {
            say(fallback)
        }
    }

    fun conclude(runs: Int, out: Boolean, fielder: String? = null, line: String? = null) {
        if (finished) return
        finished = true
        currentOnDone(RunVerdict(runs, out, overthrows, fielder, line))
    }

    fun decide(yes: Boolean, timedOut: Boolean) {
        if (finished || deciding) return
        deciding = true
        scope.launch {
            if (!yes) {
                val line = if (timedOut) "Time is up. No run." else "No!"
                sound?.vibrate(350)
                shown = line
                if (timedOut) say(line, 2000L) else call("run_no", line)
                conclude(completed, false)
                return@launch
            }
            sound?.vibrate(60, 40, 160)
            shown = "Yes!"
            call("run_yes", "Yes!")
            // The throw (patch 151): to the keeper or a fielder nearby, or straight at the stumps; it can run the batter out or go astray for overthrows.
            val thrown = RunDecisions.resolveAttempt(chance, completed, random)
            if (thrown.out) {
                shown = thrown.line
                say(thrown.line, 2500L)
                conclude(completed, true, thrown.fielder, thrown.line)
                return@launch
            }
            overthrows = (overthrows + thrown.overthrows).coerceAtMost(3)
            throwNote = thrown.line
            val n = completed + 1
            if (n >= RunDecisions.maxRunsFor(chance)) {
                val line = RunDecisions.afterRun(chance, n, true, byButtons, throwNote)
                shown = line
                say(line, 2500L)
                conclude(n, false)
            } else {
                // The next decision: the effect below announces it and starts the five seconds again.
                deciding = false
                completed = n
            }
        }
    }

    BackHandler { }

    LaunchedEffect(chance, completed) {
        if (finished) return@LaunchedEffect
        val text = if (completed == 0) RunDecisions.opening(chance, byButtons) else RunDecisions.afterRun(chance, completed, false, byButtons, throwNote)
        shown = text
        secondsLeft = 0
        say(text)
        if (finished || deciding) return@LaunchedEffect
        for (s in RunDecisions.DECISION_SECONDS downTo 1) {
            secondsLeft = s
            delay(1000L)
            if (finished || deciding) return@LaunchedEffect
        }
        secondsLeft = 0
        decide(false, true)
    }

    // Swipes only when he chose gestures; with buttons chosen the page ignores touches outside the buttons.
    val swipeModifier = if (byButtons) Modifier else Modifier.pointerInput(chance) {
        detectRunSwipe(RUN_SWIPE_THRESHOLD_DP.toPx()) { direction ->
            when (direction) {
                IntentDirection.UP -> decide(true, false)
                IntentDirection.DOWN -> decide(false, false)
                else -> Unit
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(swipeModifier)
                .semantics {
                    contentDescription = when {
                        byButtons && chance.partnerCall -> "Your partner calls for a run. Choose Accept the run or Refuse the run."
                        byButtons -> "Running decision. Choose Run or Stay."
                        chance.partnerCall -> "Your partner calls for a run. Swipe up with two fingers to accept, down with two fingers to refuse."
                        else -> "Running decision. Swipe up with two fingers to run, down with two fingers to stay."
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
                Text(if (chance.partnerCall) "Your partner calls" else "Take a run?", style = MaterialTheme.typography.headlineMedium)
                Text("Runs so far: $completed", style = MaterialTheme.typography.titleMedium)
                Text(shown, style = MaterialTheme.typography.titleMedium)
                Text(if (secondsLeft > 0) "Time left: $secondsLeft" else "", style = MaterialTheme.typography.headlineSmall)
            }
        }
        if (byButtons) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { decide(true, false) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text(if (chance.partnerCall) "Accept the run" else "Run") }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { decide(false, false) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text(if (chance.partnerCall) "Refuse the run" else "Stay, no run") }
        }
    }
}

/** One swipe per touch: decided the moment the first finger has moved far enough (the first finger is followed, a second one riding along is ignored). */
private suspend fun PointerInputScope.detectRunSwipe(thresholdPx: Float, onDirection: (IntentDirection) -> Unit) {
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

package com.cricketsim.ui.career

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
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.CareerPress
import com.cricketsim.logic.PressAnswer
import com.cricketsim.logic.PressTrigger
import com.cricketsim.ui.custom.OptionButton
import kotlinx.coroutines.delay

/**
 * The press conference (My Player, part twenty-four). A full page that comes by itself after a Player of the Match or Player of the Series award.
 * The first button is always Skip (so a screen reader reaches it at once); then the opening words, and for each question: the reporter's question, the choices
 * (each says its tone), then what he answered and one button to go on. The last button finishes it.
 *
 * THE VOICES (third part): each question is asked in the recorded voice of a different reporter (four reporters take turns), the player answers in his own
 * recorded voice, and the room has its sounds (camera flashes at the start and after each answer, a hush before each question, applause at the end).
 * A clip that is missing from the audio pack, or sound effects switched off, falls back to the phone's own speech, so nothing is ever silent. When a
 * recorded voice says a line, the screen reader does not read it a second time (the text is still on the page to explore).
 */
@Composable
fun CareerPressScreen(trigger: PressTrigger, onFinish: (List<PressAnswer>) -> Unit, onSkip: () -> Unit) {
    val services = LocalGameServices.current
    val sound = services?.sound
    val questions = remember(trigger.seed) { CareerPress.questionsFor(trigger) }
    // -1 = the opening words; then the questions one by one.
    var index by remember(trigger.seed) { mutableStateOf(-1) }
    var chosen by remember(trigger.seed) { mutableStateOf<PressAnswer?>(null) }
    var given by remember(trigger.seed) { mutableStateOf<List<PressAnswer>>(emptyList()) }
    val award = if (trigger.kind == "SERIES") "Player of the Series" else "Player of the Match"
    val intro = if (trigger.kind == "TITLE") {
        "Your team have won the ${trigger.place}, and you are the captain. The press are waiting for you, with ${questions.size} questions. Choose your answer to each one."
    } else {
        "You have been named $award in the ${trigger.place}. The press are waiting for you, with ${questions.size} questions. Choose your answer to each one."
    }
    val current = questions.getOrNull(index)
    val answered = chosen
    val questionClip = current?.let { CareerPress.questionClipId(it, index) }
    // When a recorded voice says the line, the screen reader must not read it again.
    val questionRecorded = remember(questionClip) { questionClip != null && sound?.hasPressClip(questionClip) == true }
    val answerRecorded = remember(answered?.id) { answered != null && sound?.hasPressClip(answered.id) == true }

    LaunchedEffect(index, chosen) {
        when {
            index < 0 -> {
                sound?.playPressRoom("press_camera")
                delay(1200)
                services?.announceSpoken(intro)
            }
            answered != null -> {
                if (sound != null && sound.playPress(answered.id)) {
                    // His answer, then the photographers.
                    var waited = 0L
                    while (sound.isPressSpeaking() && waited < 20000L) {
                        delay(100)
                        waited += 100
                    }
                    sound.playPressRoom("press_camera")
                } else {
                    services?.announceSpoken("You answered: ${answered.text}")
                }
            }
            current != null && questionClip != null -> {
                // The room goes quiet, then the reporter asks.
                sound?.playPressRoom("press_hush")
                delay(700)
                if (sound == null || !sound.playPress(questionClip)) services?.announceSpoken("A reporter asks: ${current.text}")
            }
        }
    }
    // Leaving the page stops the voices (a room sound that is playing, such as the applause, finishes).
    DisposableEffect(Unit) { onDispose { sound?.stopPressVoice() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Skip the press conference") }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Press conference", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(modifier = Modifier.height(12.dp))
        if (index < 0 || current == null) {
            Text(intro, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { if (questions.isEmpty()) onFinish(emptyList()) else index = 0 },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text(if (questions.isEmpty()) "Finish" else "Start the press conference") }
        } else {
            Text("Question ${index + 1} of ${questions.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "A reporter asks: ${current.text}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { if (!questionRecorded) liveRegion = LiveRegionMode.Polite }
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (answered == null) {
                current.answers.forEach { answer ->
                    OptionButton(answer.text, "Tone: ${answer.tone}.", true, false) {
                        chosen = answer
                        given = given + answer
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                Text(
                    "You answered: ${answered.text}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { if (!answerRecorded) liveRegion = LiveRegionMode.Polite }
                )
                Spacer(modifier = Modifier.height(24.dp))
                val last = index >= questions.size - 1
                Button(
                    onClick = {
                        if (last) {
                            sound?.playPressRoom("press_applause")
                            onFinish(given)
                        } else {
                            chosen = null
                            index += 1
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text(if (last) "Finish the press conference" else "Next question") }
            }
        }
    }
}

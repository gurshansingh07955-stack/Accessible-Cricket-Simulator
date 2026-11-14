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

/**
 * The press conference (My Player, part twenty-four). A full page that comes by itself after a Player of the Match or Player of the Series award.
 * The first button is always Skip (so a screen reader reaches it at once); then the opening words, and for each question: the reporter's question, the choices
 * (each says its tone), then what he answered and one button to go on. The last button finishes it. Spoken like the toss screen (part three adds the voices).
 */
@Composable
fun CareerPressScreen(trigger: PressTrigger, onFinish: (List<PressAnswer>) -> Unit, onSkip: () -> Unit) {
    val services = LocalGameServices.current
    val questions = remember(trigger.seed) { CareerPress.questionsFor(trigger) }
    // -1 = the opening words; then the questions one by one.
    var index by remember(trigger.seed) { mutableStateOf(-1) }
    var chosen by remember(trigger.seed) { mutableStateOf<PressAnswer?>(null) }
    var given by remember(trigger.seed) { mutableStateOf<List<PressAnswer>>(emptyList()) }
    val award = if (trigger.kind == "SERIES") "Player of the Series" else "Player of the Match"
    val intro = "You have been named $award in the ${trigger.place}. The press are waiting for you, with ${questions.size} questions. Choose your answer to each one."
    val current = questions.getOrNull(index)
    val answered = chosen

    LaunchedEffect(index, chosen) {
        kotlinx.coroutines.delay(500)
        val text = when {
            index < 0 -> intro
            answered != null -> "You answered: ${answered.text}"
            current != null -> "A reporter asks: ${current.text}"
            else -> null
        }
        if (text != null) services?.announceSpoken(text)
    }

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
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
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
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                Spacer(modifier = Modifier.height(24.dp))
                val last = index >= questions.size - 1
                Button(
                    onClick = {
                        if (last) {
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

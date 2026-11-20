package com.cricketsim.ui.match

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.FastForwardRequest
import com.cricketsim.logic.MatchFastForward
import com.cricketsim.logic.MatchState

/**
 * The Simulate page (Match info > Simulate match, patch 133). Back first, then the heading, then (in a Test only) "By session" or "By over", the number to type
 * (a box that takes digits and is focused as the page opens), for "By over" the extra balls (0 to 6, 0 at the start; 12 overs and 5 balls is 12.5 overs), a live
 * line that says what will be simulated, and the Simulate button, which only works when the numbers make sense. What the simulation does is in logic/MatchFastForward.kt.
 */
@Composable
fun SimulateMatchScreen(state: MatchState, onBack: () -> Unit, onSimulate: (FastForwardRequest) -> Unit) {
    val isTest = state.test != null
    var bySession by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var extraBalls by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }
    // The number box is focused as the page opens (and when the way of counting changes), so a screen reader reads it and the keyboard is ready.
    LaunchedEffect(bySession) {
        try {
            focus.requestFocus()
        } catch (e: Exception) {
            // The box is not on the screen yet: the user can still tap it.
        }
    }
    val number = amount.toIntOrNull() ?: 0
    val valid = if (bySession) number in 1..15 else number * 6 + extraBalls >= 1
    val summary = when {
        bySession -> if (number in 1..15) "Simulate $number session${if (number == 1) "" else "s"}." else "Type how many sessions to simulate, from 1 to 15."
        number * 6 + extraBalls == 0 -> "Type how many overs to simulate, or choose some balls."
        else -> "Simulate ${MatchFastForward.overText(number, extraBalls)}" + (if (extraBalls > 0) ", that is $number.$extraBalls overs." else ".")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to match") }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Simulate the match", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "The computer plays both sides from here, without the screens, and the match stops where you ask. It also stops if the innings ends, if a team declares, or when the match is over."
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (isTest) {
            Text("How to count", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Column(modifier = Modifier.selectableGroup()) {
                ChoiceRow("By session", bySession) { bySession = true }
                ChoiceRow("By over", !bySession) { bySession = false }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = amount,
            onValueChange = { v -> amount = v.filter { it.isDigit() }.take(3) },
            label = { Text(if (bySession) "Number of sessions, 1 to 15" else "Number of overs") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )

        if (!bySession) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Choose balls (0 to 6)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Column(modifier = Modifier.selectableGroup()) {
                for (b in 0..6) {
                    ChoiceRow(if (b == 1) "1 ball" else "$b balls", extraBalls == b) { extraBalls = b }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(summary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            enabled = valid,
            onClick = {
                onSimulate(
                    if (bySession) FastForwardRequest(overs = null, balls = 0, sessions = number)
                    else FastForwardRequest(overs = number, balls = extraBalls, sessions = null)
                )
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text("Simulate") }
    }
}

/** One radio row of a list of choices. */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

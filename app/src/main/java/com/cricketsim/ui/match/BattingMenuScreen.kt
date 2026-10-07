package com.cricketsim.ui.match

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.BattingSystem
import com.cricketsim.logic.FootworkType
import com.cricketsim.logic.IntentDirection
import com.cricketsim.logic.NamedShot

/**
 * Batting without gestures (Settings > Batting controls). Instead of the tap / long-press for
 * footwork and the two swipe surfaces for shot and intent, everything is picked from plain lists
 * that a screen reader walks with its ordinary swipes: footwork, then the shot, then the intent,
 * then one Play button. Only the timing tap that follows stays a tap, because hitting the ball
 * on the beat is the game itself.
 *
 * The delivery is told first (it is also spoken as the screen opens), so the choice is made with
 * the same information the gesture version gives.
 */
@Composable
internal fun BatMenuStep(
    deliverySummary: String,
    fieldNote: String,
    callout: String,
    initialFootwork: FootworkType,
    onPlay: (FootworkType, NamedShot, IntentDirection?) -> Unit
) {
    val services = LocalGameServices.current
    var footwork by remember { mutableStateOf(initialFootwork) }
    var shot by remember { mutableStateOf<NamedShot?>(null) }
    var intent by remember { mutableStateOf<IntentDirection?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(problem) {
        problem?.let { services?.announceSpoken(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Choose your shot",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = callout + "Delivery: $deliverySummary. $fieldNote",
            style = MaterialTheme.typography.bodyLarge
        )

        MenuHeading("Footwork")
        Column(modifier = Modifier.selectableGroup()) {
            MenuRadioRow("Front foot", footwork == FootworkType.FRONT_FOOT) { footwork = FootworkType.FRONT_FOOT }
            MenuRadioRow("Back foot", footwork == FootworkType.BACK_FOOT) { footwork = FootworkType.BACK_FOOT }
        }

        MenuHeading("Shot")
        Column(modifier = Modifier.selectableGroup()) {
            BattingSystem.SHOT_OPTIONS.forEach { option ->
                MenuRadioRow(option.label, shot == option.value) {
                    shot = option.value
                    problem = null
                }
            }
        }

        MenuHeading("Intent")
        Text(
            "Needed for every shot except Leave and the two defensive shots.",
            style = MaterialTheme.typography.bodySmall
        )
        Column(modifier = Modifier.selectableGroup()) {
            listOf(IntentDirection.UP, IntentDirection.LEFT, IntentDirection.DOWN, IntentDirection.RIGHT).forEach { option ->
                MenuRadioRow(BattingSystem.intentLabel(option), intent == option) {
                    intent = option
                    problem = null
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        problem?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(
            onClick = {
                val chosen = shot
                val needsIntent = chosen != null && chosen != NamedShot.LEAVE && !BattingSystem.isDefensiveShot(chosen)
                when {
                    chosen == null -> problem = "Choose a shot first."
                    needsIntent && intent == null -> problem = "Choose an intent for this shot."
                    else -> onPlay(footwork, chosen, if (needsIntent) intent else null)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Play this shot")
        }
    }
}

@Composable
private fun MenuHeading(text: String) {
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun MenuRadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

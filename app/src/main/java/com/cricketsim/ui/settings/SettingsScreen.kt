package com.cricketsim.ui.settings

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.AiCommentaryMode
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.Difficulty
import kotlin.math.roundToInt

/**
 * The web's settings page, in the same accessible patterns as the setup
 * flow: one linear scrolling column, Back FIRST (it's a long page, so Back
 * is not a dozen swipes away), section headings, and the built-in
 * `toggleable`/`selectable` modifiers so each row is one item that
 * TalkBack announces with its role and state ("Sound effects, switch,
 * on"). The whole row is the tap target, not just the small control.
 *
 * It reads and writes GameServices, so changes apply and are saved the
 * moment they are made, both from the first screen and from inside a
 * match (where it is an overlay on the match screen, since leaving the
 * match screen would throw the match away).
 *
 * Difficulty is here because the web's settings have it and the match
 * screen had it hardcoded to Medium. Its labels are just the four level
 * names: what each level changes lives in MatchEngine's difficulty
 * modifier and is not described in the web's settings UI either.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val services = LocalGameServices.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )

        if (services == null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Settings aren't available right now.")
            return@Column
        }

        val settings = services.settings

        SectionHeading("Difficulty")
        Column(modifier = Modifier.selectableGroup()) {
            Difficulty.values().forEach { level ->
                RadioRow(
                    label = level.name.lowercase().replaceFirstChar { it.uppercase() },
                    description = null,
                    selected = settings.difficulty == level,
                    onSelect = { services.update { it.copy(difficulty = level) } }
                )
            }
        }

        SectionHeading("Sound and feel")
        SwitchRow(
            label = "Sound effects",
            description = "Crowd, bat and ball, rain, and the timing ticks for batting and bowling.",
            checked = settings.soundEffects,
            onChange = { on -> services.update { it.copy(soundEffects = on) } }
        )
        SwitchRow(
            label = "Vibration",
            description = "Buzzes for the timing rhythm, wickets and boundaries.",
            checked = settings.vibration,
            onChange = { on -> services.update { it.copy(vibration = on) } }
        )
        SwitchRow(
            label = "Spoken commentary",
            description = "Reads each ball aloud. Only used when TalkBack is off, because TalkBack already reads it.",
            checked = settings.spokenCommentary,
            onChange = { on -> services.update { it.copy(spokenCommentary = on) } }
        )

        val crowdPercent = (settings.crowdVolume * 100).roundToInt()
        Spacer(modifier = Modifier.height(8.dp))
        Text("Crowd volume: $crowdPercent percent", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = settings.crowdVolume,
            onValueChange = { value -> services.update { it.copy(crowdVolume = value) } },
            valueRange = 0f..1f,
            steps = 9,
            modifier = Modifier.semantics {
                contentDescription = "Crowd volume"
                stateDescription = "$crowdPercent percent"
            }
        )

        SectionHeading("Voice commentary")
        Text(
            "Pre-recorded commentators react to what happens in the match.",
            style = MaterialTheme.typography.bodySmall
        )
        Column(modifier = Modifier.selectableGroup()) {
            AiCommentaryMode.values().forEach { mode ->
                RadioRow(
                    label = mode.label,
                    description = mode.description,
                    selected = settings.aiCommentaryMode == mode,
                    onSelect = { services.update { it.copy(aiCommentaryMode = mode) } }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { services.resetSettings() }, modifier = Modifier.fillMaxWidth()) {
            Text("Reset to defaults")
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun SwitchRow(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.width(16.dp))
        // onCheckedChange = null: the enclosing Row's toggleable() owns the
        // interaction, so the row reads and acts as one switch.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun RadioRow(label: String, description: String?, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

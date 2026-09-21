package com.cricketsim.ui.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.cricketsim.logic.MatchFormat

/**
 * Step 1 of the pre-match setup flow (format -> stadium -> teams ->
 * playing XI -> toss, matching pages/play-match.tsx's step order in
 * the web app), and the app's first screen. This is the first real
 * gameplay screen in the Android port and establishes the accessibility
 * approach later setup/match screens should follow — see UI_NOTES.md for
 * the full rationale, but in short:
 *
 * - A single vertical LazyColumn of options, one per screen-reader
 *   swipe, mirroring the web app's "single-swipe, list-based
 *   navigation instead of spatial/grid layouts" principle. There's no
 *   grid of format cards here even though three short options might
 *   visually fit side by side — a linear list is what TalkBack's
 *   linear swipe cursor handles predictably.
 * - Each option uses Compose's built-in `Modifier.selectable(...,
 *   role = Role.RadioButton)` rather than a hand-rolled
 *   `Modifier.semantics {}` block. This is a deliberate choice: it's
 *   Google's own accessibility-tested implementation of "one option
 *   selected from a list" (TalkBack announces "T20, radio button, not
 *   selected" / "selected" automatically), so it's less likely to
 *   develop the kind of subtle cross-screen-reader inconsistency the
 *   web app's custom ARIA code had to work around by hand (see the
 *   accessibility section of the original project handoff). Prefer
 *   this built-in pattern for future single-select lists before
 *   reaching for custom semantics.
 * - The RadioButton itself is given `onClick = null` and the
 *   selection handling lives on the enclosing `Row`'s `selectable`
 *   modifier — tapping anywhere in the row (not just the small radio
 *   circle) selects it, which matters for anyone using switch access
 *   or has limited fine motor precision, not just screen-reader users.
 * - RESUME goes FIRST, straight after the heading, and only appears when
 *   there is a saved match. It is one button that reads as one item
 *   ("Resume saved match" plus a one-line description of the match: the
 *   teams, format, innings and score), so a screen-reader user knows what
 *   they are resuming before committing, and continuing a match in
 *   progress is the fastest thing on the screen rather than something to
 *   swipe past the format list to find.
 * - The Settings button sits AFTER Continue so it never competes with the
 *   main path through the list, but is still the last thing on the
 *   screen rather than buried.
 */
@Composable
fun FormatSelectionScreen(
    onFormatSelected: (MatchFormat) -> Unit,
    onOpenSettings: () -> Unit = {},
    resumeSummary: String? = null,
    onResume: () -> Unit = {}
) {
    var selected by remember { mutableStateOf<MatchFormat?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Choose a match format",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (resumeSummary != null) {
            Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Resume saved match", style = MaterialTheme.typography.titleMedium)
                    Text(resumeSummary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .selectableGroup()
        ) {
            items(FORMAT_OPTIONS) { option ->
                FormatOptionRow(
                    option = option,
                    isSelected = selected == option.format,
                    onSelect = { selected = option.format }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Button(
            onClick = { selected?.let(onFormatSelected) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Settings")
        }
    }
}

private data class FormatOption(val format: MatchFormat, val label: String, val description: String)

// Order and wording matches the format choice as presented in the web
// app's pre-match setup flow.
private val FORMAT_OPTIONS = listOf(
    FormatOption(MatchFormat.T20, "T20", "20 overs per side. The shortest, fastest format."),
    FormatOption(MatchFormat.ODI, "One Day International", "50 overs per side. A full day of cricket."),
    FormatOption(MatchFormat.TEST, "Test Match", "No over limit per innings. The longest format.")
)

@Composable
private fun FormatOptionRow(option: FormatOption, isSelected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onSelect,
                role = Role.RadioButton
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // onClick = null: selection is handled by the enclosing Row's
        // selectable() above, not the radio circle itself — see the
        // file-level doc comment on why the whole row is the tap
        // target.
        RadioButton(selected = isSelected, onClick = null)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = option.label, style = MaterialTheme.typography.titleMedium)
            Text(text = option.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

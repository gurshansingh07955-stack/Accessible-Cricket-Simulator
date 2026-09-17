package com.cricketsim.ui.setup

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.CricketData
import com.cricketsim.logic.Player
import com.cricketsim.logic.PlayerRole
import com.cricketsim.logic.Team

/**
 * Step 4 of the pre-match setup flow: pick the user's Playing XI from
 * their full squad, then designate Captain, Vice-Captain, and
 * Wicketkeeper from that XI (matches the web app's playing-XI step —
 * see CricketData.buildMatchSquad and CricketData.autoSelectPlayingXI,
 * which this screen's manual flow is the user-driven counterpart to).
 * The opponent side isn't shown here at all — the AI opponent's XI is
 * auto-picked (autoSelectPlayingXI) by the caller once this screen
 * completes, same as the original design intent.
 *
 * Four sequential sub-steps, each its own flat single-swipe list:
 * squad multi-select, then three single-picks (captain, vice-captain,
 * wicketkeeper). Splitting what could be one long screen into four
 * short ones keeps every list a manageable size to swipe through and
 * keeps each step's TalkBack announcement focused on one decision at a
 * time.
 *
 * The squad step uses `Modifier.toggleable(..., role = Role.Checkbox)`
 * — a genuine multi-select where multiple items stay selected at once
 * on the same screen, unlike every other list in the setup flow so
 * far. The live "N of 11 selected" counter is marked
 * `liveRegion = LiveRegionMode.Polite` so a screen-reader user hears
 * the running count update as they check/uncheck players, without
 * needing to navigate back up to it after every toggle.
 */
@Composable
fun PlayingXIScreen(userTeam: Team, onXIConfirmed: (Team) -> Unit, onBack: () -> Unit) {
    var step by remember { mutableStateOf(XIStep.SQUAD) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var captainId by remember { mutableStateOf<String?>(null) }
    var viceCaptainId by remember { mutableStateOf<String?>(null) }

    when (step) {
        XIStep.SQUAD -> SquadSelectionStep(
            team = userTeam,
            selectedIds = selectedIds,
            onToggle = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
            onContinue = { step = XIStep.CAPTAIN },
            onBack = onBack
        )
        XIStep.CAPTAIN -> {
            val xiPlayers = remember(selectedIds) { userTeam.players.filter { it.id in selectedIds } }
            RoleSelectionStep(
                title = "Choose your captain",
                players = xiPlayers,
                onSelected = { id ->
                    captainId = id
                    step = XIStep.VICE_CAPTAIN
                },
                onBack = { step = XIStep.SQUAD }
            )
        }
        XIStep.VICE_CAPTAIN -> {
            val xiPlayers = remember(selectedIds, captainId) {
                userTeam.players.filter { it.id in selectedIds && it.id != captainId }
            }
            RoleSelectionStep(
                title = "Choose your vice-captain",
                players = xiPlayers,
                onSelected = { id ->
                    viceCaptainId = id
                    step = XIStep.WICKETKEEPER
                },
                onBack = { step = XIStep.CAPTAIN }
            )
        }
        XIStep.WICKETKEEPER -> {
            val xiPlayers = remember(selectedIds) { userTeam.players.filter { it.id in selectedIds } }
            // Prefer a genuine wicketkeeper from the chosen XI; only
            // fall back to the full XI if the user somehow picked a
            // squad with no specialist keeper in it at all.
            val keeperCandidates = remember(xiPlayers) {
                xiPlayers.filter { it.role == PlayerRole.WICKETKEEPER }.ifEmpty { xiPlayers }
            }
            RoleSelectionStep(
                title = "Choose your wicketkeeper",
                players = keeperCandidates,
                onSelected = { id ->
                    val finalCaptainId = requireNotNull(captainId) { "captainId must be set before reaching the wicketkeeper step" }
                    val finalViceCaptainId = requireNotNull(viceCaptainId) { "viceCaptainId must be set before reaching the wicketkeeper step" }
                    val finalTeam = CricketData.buildMatchSquad(
                        fullTeam = userTeam,
                        selectedPlayerIds = selectedIds.toList(),
                        captainId = finalCaptainId,
                        viceCaptainId = finalViceCaptainId,
                        wicketkeeperId = id
                    )
                    onXIConfirmed(finalTeam)
                },
                onBack = { step = XIStep.VICE_CAPTAIN }
            )
        }
    }
}

private enum class XIStep { SQUAD, CAPTAIN, VICE_CAPTAIN, WICKETKEEPER }

private const val PLAYING_XI_SIZE = 11

@Composable
private fun SquadSelectionStep(
    team: Team,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Pick your Playing XI",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${selectedIds.size} of $PLAYING_XI_SIZE selected",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(team.players, key = { it.id }) { player ->
                val isSelected = player.id in selectedIds
                // Once 11 are checked, unchecked players are disabled
                // rather than hidden — a disabled-but-visible row lets a
                // screen-reader user understand WHY they can't add an
                // 12th player, instead of the list mysteriously
                // shrinking around them.
                val enabled = isSelected || selectedIds.size < PLAYING_XI_SIZE
                PlayerCheckRow(
                    player = player,
                    isSelected = isSelected,
                    enabled = enabled,
                    onToggle = { onToggle(player.id) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onContinue,
            enabled = selectedIds.size == PLAYING_XI_SIZE,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
private fun PlayerCheckRow(player: Player, isSelected: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = isSelected,
                enabled = enabled,
                onValueChange = { onToggle() },
                role = Role.Checkbox
            )
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // onCheckedChange = null: the toggle is handled by the
        // enclosing Row's toggleable() above, same whole-row-as-tap-
        // target reasoning as FormatSelectionScreen's RadioButton rows.
        Checkbox(checked = isSelected, onCheckedChange = null, enabled = enabled)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = player.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${roleLabel(player.role)} \u00b7 Bat ${player.battingRating} \u00b7 Bowl ${player.bowlingRating}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun roleLabel(role: PlayerRole): String = when (role) {
    PlayerRole.BATSMAN -> "Batsman"
    PlayerRole.BOWLER -> "Bowler"
    PlayerRole.ALL_ROUNDER -> "All-rounder"
    PlayerRole.WICKETKEEPER -> "Wicketkeeper"
}

/**
 * A single-pick-and-advance list, same `Modifier.clickable(..., role =
 * Role.Button)` pattern as StadiumSelectionScreen/TeamSelectionScreen —
 * picking a captain/vice-captain/wicketkeeper here immediately
 * navigates to the next sub-step rather than leaving a persistent
 * selection visible next to unchosen alternatives, so a one-way
 * navigation Button is the right role, not a RadioButton.
 */
@Composable
private fun RoleSelectionStep(title: String, players: List<Player>, onSelected: (String) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(players, key = { it.id }) { player ->
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClickLabel = "Select ${player.name}",
                            role = Role.Button,
                            onClick = { onSelected(player.id) }
                        )
                        .padding(vertical = 14.dp, horizontal = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

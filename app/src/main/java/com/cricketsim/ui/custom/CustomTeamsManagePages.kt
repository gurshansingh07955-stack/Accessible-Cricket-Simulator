package com.cricketsim.ui.custom

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.Customisation
import com.cricketsim.logic.Player
import com.cricketsim.logic.PlayerProfiles
import com.cricketsim.logic.Team

/**
 * Edit teams > Delete custom teams, Reset game teams to default, and (from a team's page) Add players from other
 * teams. Each of the first two offers "all of them" or "choose which"; both ask before they change anything.
 */

// ---- Delete custom teams ----------------------------------------------------------------------

@Composable
internal fun DeleteTeamsMenuPage(version: Int, onBack: () -> Unit, onChoose: () -> Unit, onDone: (String) -> Unit) {
    val teams = remember(version) { Customisation.customTeamsAsTeams() }
    var confirmAll by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Delete custom teams")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (teams.isEmpty()) "You have no custom teams." else "You have ${teams.size} custom ${if (teams.size == 1) "team" else "teams"}.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        OptionButton(
            title = "Delete all custom teams",
            description = "Delete every custom team and all the players in them.",
            enabled = teams.isNotEmpty(), emphasised = false, onClick = { confirmAll = true }
        )
        Spacer(modifier = Modifier.height(12.dp))
        OptionButton(
            title = "Choose custom teams to delete",
            description = "Pick the teams you want to delete.",
            enabled = teams.isNotEmpty(), emphasised = false, onClick = onChoose
        )
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Delete all custom teams?") },
            text = { Text("Delete all ${teams.size} custom teams and their players? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmAll = false
                    Customisation.deleteCustomTeams(teams.map { it.id }.toSet())
                    onDone("Deleted all ${teams.size} custom teams.")
                }) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancel") } }
        )
    }
}

@Composable
internal fun DeleteTeamsPickPage(version: Int, onBack: () -> Unit, onDone: (String) -> Unit) {
    val teams = remember(version) { Customisation.customTeamsAsTeams() }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirm by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Choose custom teams to delete")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Chosen: ${picked.size}.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { confirm = true },
            enabled = picked.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text("Delete the ${picked.size} chosen") }
        Spacer(modifier = Modifier.height(8.dp))
        teams.forEach { t ->
            PickRow(t.name, "${t.players.size} players", t.id in picked, true) { on -> picked = if (on) picked + t.id else picked - t.id }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete these teams?") },
            text = { Text("Delete the ${picked.size} chosen custom teams and their players? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    val n = picked.size
                    Customisation.deleteCustomTeams(picked)
                    onDone("Deleted $n custom ${if (n == 1) "team" else "teams"}.")
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

// ---- Reset game teams ---------------------------------------------------------------------------

/** The game's teams the player has changed, with the heading they are listed under. */
private fun changedGameTeams(): List<Pair<String, Team>> {
    val ids = Customisation.editedGameTeamIds()
    if (ids.isEmpty()) return emptyList()
    val out = ArrayList<Pair<String, Team>>()
    internationalTeams().filter { it.id in ids }.forEach { out.add("International teams" to it) }
    leagueGroups().forEach { (league, teams) -> teams.filter { it.id in ids }.forEach { out.add(league to it) } }
    return out
}

@Composable
internal fun ResetTeamsMenuPage(version: Int, onBack: () -> Unit, onChoose: () -> Unit, onDone: (String) -> Unit) {
    val count = remember(version) { Customisation.editedGameTeamIds().size }
    var confirmAll by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Reset game teams to default")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (count == 0) "None of the game's teams has been changed."
            else "$count of the game's ${if (count == 1) "team has" else "teams have"} been changed (renamed, players edited, or players added).",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        OptionButton(
            title = "Reset all teams",
            description = "Put every game team back as the game made it. Your custom teams are not touched.",
            enabled = count > 0, emphasised = false, onClick = { confirmAll = true }
        )
        Spacer(modifier = Modifier.height(12.dp))
        OptionButton(
            title = "Choose which teams to reset",
            description = "Pick the teams to put back as the game made them.",
            enabled = count > 0, emphasised = false, onClick = onChoose
        )
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Reset all game teams?") },
            text = { Text("Every change to the game's teams (names, edited players, created and copied players) is removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmAll = false
                    Customisation.resetGameTeams(null)
                    onDone("Reset all game teams to default.")
                }) { Text("Reset all") }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancel") } }
        )
    }
}

@Composable
internal fun ResetTeamsPickPage(version: Int, onBack: () -> Unit, onDone: (String) -> Unit) {
    val changed = remember(version) { changedGameTeams() }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirm by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Choose which teams to reset")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Chosen: ${picked.size}.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { confirm = true },
            enabled = picked.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text("Reset the ${picked.size} chosen") }
        changed.groupBy { it.first }.forEach { (group, list) ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(group, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            list.forEach { (_, t) ->
                PickRow(t.name, "${t.players.size} players", t.id in picked, true) { on -> picked = if (on) picked + t.id else picked - t.id }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Reset these teams?") },
            text = { Text("Put the ${picked.size} chosen teams back as the game made them? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    val n = picked.size
                    Customisation.resetGameTeams(picked)
                    onDone("Reset $n ${if (n == 1) "team" else "teams"} to default.")
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

// ---- Add players from other teams ----------------------------------------------------------------

@Composable
internal fun AddPlayersPage(dest: Team, onBack: () -> Unit, onDone: (String) -> Unit) {
    var kind by remember { mutableStateOf(TeamKind.International) }
    var sourceId by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<List<Player>>(emptyList()) }
    val international = remember { internationalTeams().filter { it.id != dest.id } }
    val leagues = remember(kind) {
        if (kind == TeamKind.League) leagueGroups().map { (name, teams) -> name to teams.filter { it.id != dest.id } } else emptyList()
    }
    val custom = remember { Customisation.customTeamsAsTeams().filter { it.id != dest.id } }
    val sourceTeam: Team? = sourceId?.let { id ->
        when (kind) {
            TeamKind.International -> international.find { it.id == id }
            TeamKind.League -> leagues.flatMap { it.second }.find { it.id == id }
            TeamKind.Custom -> custom.find { it.id == id }
        }
    }
    val inDestination = remember(dest.id) { dest.players.map { it.name.lowercase() }.toSet() }
    val back: () -> Unit = { if (sourceId != null) sourceId = null else onBack() }
    BackHandler(onBack = back)
    PageColumn {
        BackButton(back)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Add players to ${dest.name}")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Chosen so far: ${picked.size}. They are copied into ${dest.name}; the original players stay where they are.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val n = Customisation.copyPlayers(dest.id, picked)
                onDone("Added $n ${if (n == 1) "player" else "players"} to ${dest.name}.")
            },
            enabled = picked.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text("Add the ${picked.size} chosen ${if (picked.size == 1) "player" else "players"}") }
        Spacer(modifier = Modifier.height(12.dp))

        if (sourceTeam == null) {
            listOf(
                TeamKind.International to "International teams",
                TeamKind.League to "League teams",
                TeamKind.Custom to "Custom teams"
            ).forEach { (k, label) ->
                val on = kind == k
                val m = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { this.selected = on; role = Role.Tab }
                if (on) Button(onClick = { kind = k }, modifier = m) { Text(label) }
                else OutlinedButton(onClick = { kind = k }, modifier = m) { Text(label) }
                Spacer(modifier = Modifier.height(6.dp))
            }
            val groups: List<Pair<String, List<Team>>> = when (kind) {
                TeamKind.International -> listOf("International teams" to international)
                TeamKind.League -> leagues
                TeamKind.Custom -> listOf("Custom teams" to custom)
            }
            if (kind == TeamKind.Custom && custom.isEmpty()) {
                Text("You have no other custom teams.", style = MaterialTheme.typography.bodyLarge)
            }
            groups.forEach { (title, teams) ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                teams.forEach { t ->
                    OutlinedButton(
                        onClick = { sourceId = t.id },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text("${t.players.size} players", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        } else {
            Text(sourceTeam.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            PlayerProfiles.grouped(sourceTeam.players).forEach { (title, players) ->
                Spacer(modifier = Modifier.height(8.dp))
                Text("$title (${players.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                players.forEach { p ->
                    val isPicked = picked.any { it.id == p.id }
                    val alreadyThere = p.name.lowercase() in inDestination
                    val sameNamePicked = !isPicked && picked.any { it.name.equals(p.name, ignoreCase = true) }
                    val sub = when {
                        alreadyThere -> "Already in ${dest.name}"
                        sameNamePicked -> "A player with this name is already chosen"
                        else -> "${PlayerProfiles.roleLabel(PlayerProfiles.displayRole(p))}. ${PlayerProfiles.battingText(p)} ${PlayerProfiles.bowlingText(p)}"
                    }
                    PickRow(p.name, sub, isPicked, !alreadyThere && !sameNamePicked) { on ->
                        picked = if (on) picked + p else picked.filter { it.id != p.id }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(text: String, sub: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(modifier = Modifier.size(12.dp))
        Column {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

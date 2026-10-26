package com.cricketsim.ui.custom

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.CustomFormat
import com.cricketsim.logic.CustomTournamentDef
import com.cricketsim.logic.Customisation
import com.cricketsim.logic.GameTournaments
import com.cricketsim.logic.ScheduleType
import com.cricketsim.logic.StadiumData
import com.cricketsim.logic.TeamCatalog
import com.cricketsim.logic.TeamEntry
import com.cricketsim.logic.TournamentFormats
import com.cricketsim.persistence.CustomFormatStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Home > Advanced Customisation > Create Custom Tournaments.
 *
 *  - Edit Tournament: every tournament of the game and every custom one; open one to change its name and format
 *    (all the game's formats and the player's custom formats are offered). A custom tournament can be deleted.
 *  - Create New Custom Tournament: name, format, schedule, teams (four tabs), stadiums (a country menu, then its
 *    stadiums), DRS and rain, then a summary (with how many matches there will be) and Save.
 *
 * What a saved tournament does in play is in logic/CustomTournament.kt and the Tournament > Custom tournaments page.
 */
private enum class TStep { Hub, EditList, EditOne, Name, Format, Schedule, Teams, Stadiums, Rules, Summary }

private val TEAM_TABS = listOf(
    TeamCatalog.INTERNATIONAL to "International Teams",
    TeamCatalog.TOURNAMENT to "International Tournament Teams",
    TeamCatalog.LEAGUE to "League Teams",
    TeamCatalog.CUSTOM to "Custom Teams"
)

@Composable
fun CustomTournamentsFlow(onExit: () -> Unit) {
    val context = LocalContext.current
    val formatStore = remember { CustomFormatStore(context) }
    var customFormats by remember { mutableStateOf<List<CustomFormat>>(emptyList()) }
    LaunchedEffect(Unit) { customFormats = formatStore.load() }

    var step by remember { mutableStateOf(TStep.Hub) }
    var version by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf("") }

    // the tournament being created
    var name by remember { mutableStateOf("") }
    var formatKey by remember { mutableStateOf("T20") }
    var schedule by remember { mutableStateOf(ScheduleType.SINGLE_RR) }
    var teamKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var stadiumIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var drsOn by remember { mutableStateOf(true) }
    var drsReviews by remember { mutableStateOf(2) }
    var rainOn by remember { mutableStateOf(true) }
    var catalog by remember { mutableStateOf<List<TeamEntry>?>(null) }

    LaunchedEffect(step) {
        if ((step == TStep.Teams || step == TStep.Summary) && catalog == null) {
            catalog = withContext(Dispatchers.Default) { TeamCatalog.load() }
        }
    }

    fun startNew() {
        name = ""
        formatKey = "T20"
        schedule = ScheduleType.SINGLE_RR
        teamKeys = emptySet()
        stadiumIds = emptySet()
        drsOn = true
        drsReviews = 2
        rainOn = true
        message = null
        step = TStep.Name
    }

    val formats = TournamentFormats.all(customFormats)

    when (step) {
        TStep.Hub -> {
            BackHandler(onBack = onExit)
            PageColumn {
                BackButton(onExit)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Create Custom Tournaments")
                message?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                Spacer(modifier = Modifier.height(16.dp))
                OptionButton(
                    title = "Create New Custom Tournament",
                    description = "Choose a format, a schedule, teams, stadiums and the DRS and rain rules.",
                    enabled = true, emphasised = true, onClick = { startNew() }
                )
                Spacer(modifier = Modifier.height(12.dp))
                OptionButton(
                    title = "Edit Tournament",
                    description = "Change the name and format of the game's tournaments and of your own.",
                    enabled = true, emphasised = false, onClick = { message = null; step = TStep.EditList }
                )
            }
        }

        TStep.EditList -> {
            BackHandler(onBack = { step = TStep.Hub })
            val custom = remember(version) { Customisation.customTournaments() }
            PageColumn {
                BackButton { step = TStep.Hub }
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Edit Tournament")
                message?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                GameTournaments.all.groupBy { it.group }.forEach { (group, list) ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(group, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    list.forEach { g ->
                        val shownName = Customisation.tournamentName(g.id, g.name)
                        val fmt = TournamentFormats.find(Customisation.tournamentFormatKey(g.id, g.formatKey), customFormats)?.name ?: "Unknown format"
                        TournamentRow(shownName, "Format: $fmt") { editingId = g.id; message = null; step = TStep.EditOne }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Custom tournaments (${custom.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                if (custom.isEmpty()) Text("You have not made any custom tournaments yet.", style = MaterialTheme.typography.bodyMedium)
                custom.forEach { c ->
                    val fmt = TournamentFormats.find(c.formatKey, customFormats)?.name ?: "Unknown format"
                    TournamentRow(c.name, "Format: $fmt. ${ScheduleType.valueOf(c.schedule).label}.") { editingId = c.id; message = null; step = TStep.EditOne }
                }
            }
        }

        TStep.EditOne -> {
            val game = GameTournaments.all.find { it.id == editingId }
            val custom = remember(version, editingId) { Customisation.customTournaments().find { it.id == editingId } }
            if (game == null && custom == null) {
                LaunchedEffect(Unit) { step = TStep.EditList }
            } else {
                EditTournamentPage(
                    defaultName = game?.name ?: custom!!.name,
                    startName = if (game != null) Customisation.tournamentName(game.id, game.name) else custom!!.name,
                    startFormat = if (game != null) Customisation.tournamentFormatKey(game.id, game.formatKey) else custom!!.formatKey,
                    isCustom = custom != null,
                    isEdited = game != null && Customisation.tournamentEdit(game.id) != null,
                    formats = formats,
                    otherNames = Customisation.customTournaments().filter { it.id != editingId }.map { it.name },
                    onBack = { step = TStep.EditList },
                    onSave = { newName, newFormat ->
                        if (game != null) {
                            Customisation.setTournamentEdit(
                                game.id,
                                if (newName != game.name) newName else null,
                                if (newFormat != game.formatKey) newFormat else null
                            )
                        } else if (custom != null) {
                            Customisation.saveCustomTournament(custom.copy(name = newName, formatKey = newFormat))
                        }
                        version++
                        message = "Saved $newName."
                        step = TStep.EditList
                    },
                    onRestore = {
                        game?.let { Customisation.setTournamentEdit(it.id, null, null) }
                        version++
                        message = "Restored the original name and format."
                        step = TStep.EditList
                    },
                    onDelete = {
                        custom?.let { Customisation.deleteCustomTournament(it.id) }
                        version++
                        message = "Deleted the tournament."
                        step = TStep.EditList
                    }
                )
            }
        }

        TStep.Name -> TextInputPage(
            title = "Name your tournament",
            hint = "Type a name for the tournament, for example Friday Night Cup.",
            label = "Tournament name",
            value = name,
            onValueChange = { name = it.take(CustomTournamentDef.MAX_NAME_LENGTH) },
            numeric = false,
            validate = { v ->
                val t = v.trim()
                when {
                    t.isEmpty() -> "Please type a name."
                    Customisation.customTournaments().any { it.name.equals(t, ignoreCase = true) } -> "You already have a tournament called $t."
                    else -> null
                }
            },
            onContinue = { step = TStep.Format },
            onBack = { step = TStep.Hub }
        )

        TStep.Format -> {
            BackHandler(onBack = { step = TStep.Name })
            PageColumn {
                BackButton { step = TStep.Name }
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Choose the format")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Every match of the tournament is played in this format. Your own formats are listed after the game's.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.selectableGroup()) {
                    formats.forEach { f -> ChoiceRadio(f.name, f.description, formatKey == f.key) { formatKey = f.key } }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { step = TStep.Schedule }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Continue") }
            }
        }

        TStep.Schedule -> {
            BackHandler(onBack = { step = TStep.Format })
            PageColumn {
                BackButton { step = TStep.Format }
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Choose the schedule")
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.selectableGroup()) {
                    ScheduleType.values().forEach { s ->
                        ChoiceRadio(s.label, s.description + " For at least ${s.minTeams} teams.", schedule == s) { schedule = s }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { step = TStep.Teams }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Continue") }
            }
        }

        TStep.Teams -> TeamsPage(
            schedule = schedule,
            catalog = catalog,
            selected = teamKeys,
            onSelected = { teamKeys = it },
            onBack = { step = TStep.Schedule },
            onContinue = { step = TStep.Stadiums }
        )

        TStep.Stadiums -> StadiumsPage(
            selected = stadiumIds,
            onSelected = { stadiumIds = it },
            onBack = { step = TStep.Teams },
            onContinue = { step = TStep.Rules }
        )

        TStep.Rules -> {
            BackHandler(onBack = { step = TStep.Stadiums })
            PageColumn {
                BackButton { step = TStep.Stadiums }
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("DRS and rain")
                Spacer(modifier = Modifier.height(8.dp))
                SwitchRow("DRS", "Let teams review decisions.", drsOn) { drsOn = it }
                if (drsOn) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Reviews each team gets per innings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Column(modifier = Modifier.selectableGroup()) {
                        listOf(1, 2, 3, 4, CustomTournamentDef.UNLIMITED_REVIEWS).forEach { n ->
                            ChoiceRadio(
                                if (n == CustomTournamentDef.UNLIMITED_REVIEWS) "Unlimited reviews" else if (n == 1) "1 review" else "$n reviews",
                                null, drsReviews == n
                            ) { drsReviews = n }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                SwitchRow("Rain", "Rain can interrupt matches, with overs lost and a revised target.", rainOn) { rainOn = it }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { step = TStep.Summary }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Continue") }
            }
        }

        TStep.Summary -> {
            BackHandler(onBack = { step = TStep.Rules })
            val cat = catalog
            val picked = cat?.filter { it.key in teamKeys } ?: emptyList()
            val n = teamKeys.size
            val formatName = TournamentFormats.find(formatKey, customFormats)?.name ?: formatKey
            val stadiumNames = stadiumIds.mapNotNull { StadiumData.getStadiumById(it)?.name }
            PageColumn {
                BackButton { step = TStep.Rules }
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Check your tournament")
                Spacer(modifier = Modifier.height(12.dp))
                Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Text(name.trim(), style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Format: $formatName.", style = MaterialTheme.typography.bodyLarge)
                    Text("Schedule: ${schedule.label}.", style = MaterialTheme.typography.bodyLarge)
                    Text("Teams ($n): " + picked.joinToString(", ") { it.team.name } + ".", style = MaterialTheme.typography.bodyLarge)
                    Text("Stadiums (${stadiumNames.size}): " + stadiumNames.joinToString(", ") + ".", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "DRS: " + if (!drsOn) "off." else if (drsReviews == CustomTournamentDef.UNLIMITED_REVIEWS) "on, unlimited reviews per innings." else "on, $drsReviews ${if (drsReviews == 1) "review" else "reviews"} per innings.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text("Rain: " + if (rainOn) "on." else "off.", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Number of matches", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    schedule.breakdown(n).forEach { (label, count) ->
                        Text("$label: $count", style = MaterialTheme.typography.bodyLarge)
                    }
                    Text("In total: ${schedule.matchCount(n)} matches.", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        Customisation.saveCustomTournament(
                            CustomTournamentDef(
                                id = "ctour_" + UUID.randomUUID().toString().take(8),
                                name = name.trim(), formatKey = formatKey, schedule = schedule.name,
                                teamKeys = teamKeys.toList(), stadiumIds = stadiumIds.toList(),
                                drsEnabled = drsOn, drsReviews = drsReviews, rainEnabled = rainOn
                            )
                        )
                        version++
                        message = "Saved ${name.trim()}, ${schedule.matchCount(n)} matches."
                        step = TStep.Hub
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Save tournament") }
            }
        }
    }
}

// ---- Edit one tournament ------------------------------------------------------------------------

@Composable
private fun EditTournamentPage(
    defaultName: String,
    startName: String,
    startFormat: String,
    isCustom: Boolean,
    isEdited: Boolean,
    formats: List<com.cricketsim.logic.FormatOption>,
    otherNames: List<String>,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(startName) }
    var format by remember { mutableStateOf(startFormat) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Edit $startName")
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { error = null; name = it.take(CustomTournamentDef.MAX_NAME_LENGTH) },
            label = { Text("Tournament name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Format", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Column(modifier = Modifier.selectableGroup()) {
            formats.forEach { f -> ChoiceRadio(f.name, f.description, format == f.key) { error = null; format = f.key } }
        }
        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val t = name.trim()
                when {
                    t.isEmpty() -> error = "Please type a name."
                    otherNames.any { it.equals(t, ignoreCase = true) } -> error = "You already have a tournament called $t."
                    else -> onSave(t, format)
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text("Save changes") }
        if (isEdited) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Restore the original ($defaultName)")
            }
        }
        if (isCustom) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Delete this tournament")
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete tournament?") },
            text = { Text("Delete $startName? This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

// ---- Teams ------------------------------------------------------------------------------------

@Composable
private fun TeamsPage(
    schedule: ScheduleType,
    catalog: List<TeamEntry>?,
    selected: Set<String>,
    onSelected: (Set<String>) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    var tab by remember { mutableStateOf(TeamCatalog.INTERNATIONAL) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Choose the teams")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${schedule.label} needs ${schedule.minTeams} to ${ScheduleType.MAX_TEAMS} teams. You have chosen ${selected.size}.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(modifier = Modifier.height(12.dp))
        TEAM_TABS.forEach { (kind, label) ->
            val on = tab == kind
            val m = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { this.selected = on; role = Role.Tab }
            if (on) Button(onClick = { tab = kind }, modifier = m) { Text(label) }
            else OutlinedButton(onClick = { tab = kind }, modifier = m) { Text(label) }
            Spacer(modifier = Modifier.height(6.dp))
        }
        if (catalog == null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Loading the teams...", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        } else {
            val entries = catalog.filter { it.kind == tab }
            if (tab == TeamCatalog.CUSTOM && entries.isEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("You have no custom teams yet. Create one in Create Custom Teams and Players.", style = MaterialTheme.typography.bodyLarge)
            }
            if (tab == TeamCatalog.CUSTOM && entries.isNotEmpty()) {
                Text("A custom team needs at least ${TeamCatalog.MIN_PLAYERS} players to play.", style = MaterialTheme.typography.bodyMedium)
            }
            entries.groupBy { it.group }.forEach { (group, list) ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(group, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                list.forEach { e ->
                    val tooSmall = e.kind == TeamCatalog.CUSTOM && e.team.players.size < TeamCatalog.MIN_PLAYERS
                    CheckRow(
                        text = e.team.name,
                        sub = if (tooSmall) "${e.team.players.size} players: needs ${TeamCatalog.MIN_PLAYERS}" else "${e.team.players.size} players",
                        checked = e.key in selected,
                        enabled = !tooSmall
                    ) { on ->
                        error = null
                        onSelected(if (on) selected + e.key else selected - e.key)
                    }
                }
            }
        }
        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val ids = catalog?.filter { it.key in selected }?.map { it.team.id } ?: emptyList()
                val dup = ids.groupBy { it }.filter { it.value.size > 1 }.keys.firstOrNull()
                val dupName = catalog?.firstOrNull { it.team.id == dup }?.team?.name
                error = when {
                    catalog == null -> "The teams are still loading."
                    selected.size < schedule.minTeams -> "Choose at least ${schedule.minTeams} teams."
                    selected.size > ScheduleType.MAX_TEAMS -> "Choose at most ${ScheduleType.MAX_TEAMS} teams."
                    dup != null -> "You chose two teams that are both $dupName. Keep only one of them."
                    else -> null
                }
                if (error == null) onContinue()
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text("Continue") }
    }
}

// ---- Stadiums -----------------------------------------------------------------------------------

@Composable
private fun StadiumsPage(selected: Set<String>, onSelected: (Set<String>) -> Unit, onBack: () -> Unit, onContinue: () -> Unit) {
    val byCountry = remember { StadiumData.getAllStadiums().groupBy { it.country }.toSortedMap() }
    var country by remember { mutableStateOf(byCountry.keys.firstOrNull() ?: "") }
    var open by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val here = byCountry[country] ?: emptyList()
    BackHandler(onBack = onBack)
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Choose the stadiums")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Pick a country, then tick its stadiums. You can pick stadiums from several countries. Chosen so far: ${selected.size}.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Country: $country. Tap to change.")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                byCountry.keys.forEach { c ->
                    DropdownMenuItem(
                        text = { Text("$c (${byCountry[c]?.count { it.id in selected } ?: 0} chosen)") },
                        onClick = { country = c; open = false }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            OutlinedButton(
                onClick = { error = null; onSelected(selected + here.map { it.id }) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) { Text("Select all in $country") }
            Spacer(modifier = Modifier.size(8.dp))
            OutlinedButton(
                onClick = { onSelected(selected - here.map { it.id }.toSet()) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) { Text("Clear $country") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        here.forEach { s ->
            CheckRow(text = s.name, sub = s.city, checked = s.id in selected, enabled = true) { on ->
                error = null
                onSelected(if (on) selected + s.id else selected - s.id)
            }
        }
        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { if (selected.isEmpty()) error = "Choose at least one stadium." else onContinue() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text("Continue") }
    }
}

// ---- Small pieces -------------------------------------------------------------------------------

@Composable
private fun TournamentRow(title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 3.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ChoiceRadio(title: String, description: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.size(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CheckRow(text: String, sub: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
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

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

package com.cricketsim.ui.custom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.AbuDhabiT10
import com.cricketsim.logic.BigBashLeague
import com.cricketsim.logic.CplData
import com.cricketsim.logic.CricketData
import com.cricketsim.logic.Customisation
import com.cricketsim.logic.InternationalLeagueT20
import com.cricketsim.logic.LankaPremierLeague
import com.cricketsim.logic.MajorLeagueCricket
import com.cricketsim.logic.Player
import com.cricketsim.logic.PlayerProfiles
import com.cricketsim.logic.PlayerRole
import com.cricketsim.logic.BowlingStyle
import com.cricketsim.logic.Sa20
import com.cricketsim.logic.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

/**
 * Home > Advanced Customisation > Create Custom Teams and Players.
 *
 *  - Create a new custom team: a name and, optionally, a logo from the gallery.
 *  - Edit teams: tabs for International, League and Custom teams; open a team to see all its players; press and
 *    hold a team name to rename it (or use the "Rename team" action a screen reader offers); every player has an
 *    Edit button (role, ratings, batting hand, bowling style); the last choice in a team is Create a new player;
 *    a custom team can also be deleted.
 *
 * What the edits change is explained in logic/Customisation.kt.
 */
private enum class TeamKind { International, League, Custom }

private sealed interface CtPage {
    object Hub : CtPage
    object Create : CtPage
    object EditList : CtPage
    class TeamView(val kind: TeamKind, val teamId: String) : CtPage
    /** playerName null = create a new player. */
    class PlayerEdit(val kind: TeamKind, val teamId: String, val playerName: String?) : CtPage
}

@Composable
fun CustomTeamsFlow(onExit: () -> Unit) {
    var page by remember { mutableStateOf<CtPage>(CtPage.Hub) }
    var tab by remember { mutableStateOf(TeamKind.International) }
    var version by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Pair<TeamKind, Team>?>(null) }

    when (val p = page) {
        is CtPage.Hub -> {
            BackHandler(onBack = onExit)
            PageColumn {
                BackButton(onExit)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Create Custom Teams and Players")
                Spacer(modifier = Modifier.height(16.dp))
                OptionButton(
                    title = "Create a new custom team",
                    description = "Name your team and, if you like, choose a logo from your gallery.",
                    enabled = true,
                    emphasised = true,
                    onClick = { message = null; page = CtPage.Create }
                )
                Spacer(modifier = Modifier.height(12.dp))
                OptionButton(
                    title = "Edit teams",
                    description = "Change teams and players: international, league and your own custom teams.",
                    enabled = true,
                    emphasised = false,
                    onClick = { message = null; page = CtPage.EditList }
                )
            }
        }

        is CtPage.Create -> CreateTeamPage(
            onBack = { page = CtPage.Hub },
            onSaved = { id ->
                version++
                tab = TeamKind.Custom
                message = "Team saved. Add its players with Create a new player, at the end of the team."
                page = CtPage.TeamView(TeamKind.Custom, id)
            }
        )

        is CtPage.EditList -> {
            BackHandler(onBack = { page = CtPage.Hub })
            EditTeamsPage(
                tab = tab,
                version = version,
                message = message,
                onTab = { tab = it },
                onBack = { page = CtPage.Hub },
                onOpen = { kind, id -> message = null; page = CtPage.TeamView(kind, id) },
                onRename = { kind, team -> renaming = kind to team }
            )
        }

        is CtPage.TeamView -> {
            val team = remember(version, p.teamId) { findTeam(p.kind, p.teamId) }
            if (team == null) {
                LaunchedEffect(Unit) { page = CtPage.EditList }
            } else {
                BackHandler(onBack = { page = CtPage.EditList })
                TeamPage(
                    kind = p.kind,
                    team = team,
                    message = message,
                    onBack = { page = CtPage.EditList },
                    onEditPlayer = { player -> message = null; page = CtPage.PlayerEdit(p.kind, p.teamId, player.name) },
                    onCreatePlayer = { message = null; page = CtPage.PlayerEdit(p.kind, p.teamId, null) },
                    onRename = { renaming = p.kind to team },
                    onDeleteTeam = {
                        Customisation.deleteCustomTeam(team.id)
                        version++
                        message = "Deleted ${team.name}."
                        page = CtPage.EditList
                    }
                )
            }
        }

        is CtPage.PlayerEdit -> {
            val team = remember(version, p.teamId) { findTeam(p.kind, p.teamId) }
            val player = team?.players?.find { it.name == p.playerName }
            if (team == null || (p.playerName != null && player == null)) {
                LaunchedEffect(Unit) { page = CtPage.EditList }
            } else {
                BackHandler(onBack = { page = CtPage.TeamView(p.kind, p.teamId) })
                PlayerEditorPage(
                    team = team,
                    player = player,
                    onBack = { page = CtPage.TeamView(p.kind, p.teamId) },
                    onDone = { text ->
                        version++
                        message = text
                        page = CtPage.TeamView(p.kind, p.teamId)
                    }
                )
            }
        }
    }

    renaming?.let { (kind, team) ->
        RenameDialog(
            current = team.name,
            onDismiss = { renaming = null },
            onSave = { newName ->
                val t = newName.trim()
                when {
                    t.isEmpty() -> "Please type a name."
                    kind == TeamKind.Custom && Customisation.customTeams().any { it.id != team.id && it.name.equals(t, ignoreCase = true) } ->
                        "You already have a custom team called $t."
                    else -> {
                        if (kind == TeamKind.Custom) Customisation.renameCustomTeam(team.id, t) else Customisation.setTeamName(team.id, t)
                        version++
                        message = "Renamed to $t."
                        renaming = null
                        null
                    }
                }
            }
        )
    }
}

// ---- Finding teams ---------------------------------------------------------------------------

private fun internationalTeams(): List<Team> = CricketData.getAllTeams().sortedBy { it.name }

/** Every league the game has, with its teams as they are built (the IPL is made by its auction, so it is not here). */
private fun leagueGroups(): List<Pair<String, List<Team>>> = listOf(
    "Caribbean Premier League" to CplData.buildTeams().first,
    "Abu Dhabi T10" to AbuDhabiT10.buildTeams().first,
    "Lanka Premier League" to LankaPremierLeague.buildTeams().first,
    "International League T20" to InternationalLeagueT20.buildTeams().first,
    "Big Bash League" to BigBashLeague.buildTeams().first,
    "Major League Cricket" to MajorLeagueCricket.buildTeams().first,
    "SA20" to Sa20.buildTeams().first
)

private fun findTeam(kind: TeamKind, id: String): Team? = when (kind) {
    TeamKind.International -> CricketData.getTeamById(id)
    TeamKind.League -> leagueGroups().flatMap { it.second }.find { it.id == id }
    TeamKind.Custom -> Customisation.customTeamsAsTeams().find { it.id == id }
}

// ---- Create a team ---------------------------------------------------------------------------

private fun loadLogo(context: Context, uri: Uri): Bitmap? = try {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    if (raw == null) {
        null
    } else {
        val scale = 256f / maxOf(raw.width, raw.height)
        if (scale < 1f) {
            Bitmap.createScaledBitmap(
                raw, (raw.width * scale).toInt().coerceAtLeast(1), (raw.height * scale).toInt().coerceAtLeast(1), true
            )
        } else raw
    }
} catch (e: Exception) {
    null
}

private fun saveLogo(teamId: String, bitmap: Bitmap) {
    try {
        val f = Customisation.logoFile(teamId) ?: return
        f.parentFile?.mkdirs()
        FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } catch (e: Exception) {
        // The logo is only decoration; the team is saved either way.
    }
}

@Composable
private fun CreateTeamPage(onBack: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var logo by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: Exception) {
            // Not on screen yet; the user can tap the box.
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { loadLogo(context, uri) }
                if (bitmap == null) error = "That picture could not be opened." else { logo = bitmap; error = null }
            }
        }
    }
    BackHandler(onBack = onBack)
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Create a new custom team")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Type a name for your team. A logo is optional.", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { error = null; name = it.take(Customisation.MAX_NAME_LENGTH) },
            label = { Text("Team name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {}),
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (logo == null) "No logo chosen." else "Logo chosen.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        logo?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Image(bitmap = it.asImageBitmap(), contentDescription = "Preview of the team logo", modifier = Modifier.size(120.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { picker.launch("image/*") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text("Choose a logo from your gallery (optional)") }
        if (logo != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { logo = null },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text("Remove the logo") }
        }
        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val t = name.trim()
                when {
                    t.isEmpty() -> error = "Please type a team name."
                    Customisation.customTeams().any { it.name.equals(t, ignoreCase = true) } ->
                        error = "You already have a custom team called $t."
                    else -> {
                        val team = Customisation.createCustomTeam(t, logo != null)
                        logo?.let { saveLogo(team.id, it) }
                        onSaved(team.id)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text("Save team") }
    }
}

// ---- Edit teams ------------------------------------------------------------------------------

@Composable
private fun EditTeamsPage(
    tab: TeamKind,
    version: Int,
    message: String?,
    onTab: (TeamKind) -> Unit,
    onBack: () -> Unit,
    onOpen: (TeamKind, String) -> Unit,
    onRename: (TeamKind, Team) -> Unit
) {
    val international = remember(version) { internationalTeams() }
    val leagues = remember(version, tab) { if (tab == TeamKind.League) leagueGroups() else emptyList() }
    val custom = remember(version) { Customisation.customTeamsAsTeams() }
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Edit teams")
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabButton("International teams", tab == TeamKind.International, Modifier.weight(1f)) { onTab(TeamKind.International) }
            TabButton("League teams", tab == TeamKind.League, Modifier.weight(1f)) { onTab(TeamKind.League) }
            TabButton("Custom teams", tab == TeamKind.Custom, Modifier.weight(1f)) { onTab(TeamKind.Custom) }
        }
        message?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Open a team to see its players. Press and hold a team name to rename the team.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        when (tab) {
            TeamKind.International -> international.forEach { t ->
                TeamRow(t.name, "${t.players.size} players", { onOpen(tab, t.id) }, { onRename(tab, t) })
            }
            TeamKind.League -> {
                Text(
                    text = "The IPL is not here: its teams are made by the auction.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                leagues.forEach { (league, teams) ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(league, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    teams.forEach { t -> TeamRow(t.name, "${t.players.size} players", { onOpen(tab, t.id) }, { onRename(tab, t) }) }
                }
            }
            TeamKind.Custom -> {
                if (custom.isEmpty()) {
                    Text("You have no custom teams yet. Go back and choose Create a new custom team.", style = MaterialTheme.typography.bodyLarge)
                }
                custom.forEach { t ->
                    TeamRow(t.name, "${t.players.size} players", { onOpen(tab, t.id) }, { onRename(tab, t) })
                }
            }
        }
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val m = modifier.heightIn(min = 56.dp).semantics { this.selected = selected; role = Role.Tab }
    if (selected) Button(onClick = onClick, modifier = m) { Text(text) }
    else OutlinedButton(onClick = onClick, modifier = m) { Text(text) }
}

/** A team in a list: tap to open, press and hold (or the screen reader's "Rename team" action) to rename. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TeamRow(title: String, subtitle: String, onClick: () -> Unit, onRename: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .combinedClickable(
                onClickLabel = "Open team",
                role = Role.Button,
                onLongClickLabel = "Rename team",
                onLongClick = onRename,
                onClick = onClick
            )
            .semantics { customActions = listOf(CustomAccessibilityAction("Rename team") { onRename(); true }) }
            .padding(vertical = 10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> String?) {
    var text by remember { mutableStateOf(current) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: Exception) {
            // Not on screen yet.
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename team") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { error = null; text = it.take(Customisation.MAX_NAME_LENGTH) },
                    label = { Text("Team name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { error = onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---- A team and its players --------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TeamPage(
    kind: TeamKind,
    team: Team,
    message: String?,
    onBack: () -> Unit,
    onEditPlayer: (Player) -> Unit,
    onCreatePlayer: () -> Unit,
    onRename: () -> Unit,
    onDeleteTeam: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val logo = remember(team.id) {
        if (kind == TeamKind.Custom) {
            Customisation.logoFile(team.id)?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        } else null
    }
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = team.name,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onLongClickLabel = "Rename team",
                    onLongClick = onRename,
                    onClick = {}
                )
                .semantics {
                    heading()
                    customActions = listOf(CustomAccessibilityAction("Rename team") { onRename(); true })
                }
        )
        Text(
            text = when (kind) {
                TeamKind.International -> "International team"
                TeamKind.League -> "League team"
                TeamKind.Custom -> "Custom team"
            } + ". ${team.players.size} players. Press and hold the name to rename the team." +
                (if (kind == TeamKind.Custom) (Customisation.playProblem(team)?.let { " Not ready to play: $it." } ?: " Ready to play.") else ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        logo?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Image(bitmap = it.asImageBitmap(), contentDescription = "${team.name} logo", modifier = Modifier.size(96.dp))
        }
        message?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (kind == TeamKind.Custom) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text("Delete this team") }
        }
        if (team.players.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("This team has no players yet.", style = MaterialTheme.typography.bodyLarge)
        }
        PlayerProfiles.grouped(team.players).forEach { (title, players) ->
            Spacer(modifier = Modifier.height(16.dp))
            Text("$title (${players.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            players.forEach { p ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${PlayerProfiles.roleLabel(PlayerProfiles.displayRole(p))}. ${PlayerProfiles.battingText(p)} ${PlayerProfiles.bowlingText(p)} Fielding: rating ${p.fieldingRating}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    OutlinedButton(
                        onClick = { onEditPlayer(p) },
                        modifier = Modifier.semantics { contentDescription = "Edit ${p.name}" }
                    ) { Text("Edit") }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onCreatePlayer,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text("Create a new player") }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete team?") },
            text = { Text("Delete ${team.name} and all its players? This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteTeam() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

// ---- Create or edit a player -------------------------------------------------------------------

private val RIGHT_TYPES = listOf("fast", "fast-medium", "medium-fast", "medium", "off break", "leg break")
private val LEFT_TYPES = RIGHT_TYPES + listOf("orthodox", "wrist spin")

private fun typeName(type: String): String = when (type) {
    "orthodox" -> "Orthodox spin"
    else -> type.replaceFirstChar { it.uppercase() }
}

/** 'R', 'L' or 'N' (does not bowl) from a style text such as "Left-arm orthodox". */
private fun armOf(label: String?, player: Player?): Char = when {
    label == null -> if (player == null || player.bowlingRating > 25) 'R' else 'N'
    label.isEmpty() -> 'N'
    label.startsWith("Left") -> 'L'
    else -> 'R'
}

private fun typeOf(label: String?, player: Player?): String {
    if (label != null && label.isNotEmpty()) {
        val t = label.substringAfter("-arm ", label).lowercase()
        return when {
            t in LEFT_TYPES -> t
            t.contains("spin") -> "off break"
            else -> "medium-fast"
        }
    }
    return if (player?.bowlingStyle == BowlingStyle.SPIN) "off break" else "medium-fast"
}

@Composable
private fun PlayerEditorPage(team: Team, player: Player?, onBack: () -> Unit, onDone: (String) -> Unit) {
    val maxField = remember { CricketData.getAllTeams().flatMap { it.players }.maxOfOrNull { it.fieldingRating }?.coerceAtLeast(90) ?: 95 }
    val initLabel = remember { player?.let { PlayerProfiles.bowlLabelOf(it) } }
    var name by remember { mutableStateOf(player?.name ?: "") }
    var role by remember { mutableStateOf(player?.let { PlayerProfiles.displayRole(it) } ?: PlayerRole.BATSMAN) }
    var hand by remember { mutableStateOf(player?.let { PlayerProfiles.handOf(it) } ?: 'R') }
    var arm by remember { mutableStateOf(armOf(initLabel, player)) }
    var type by remember { mutableStateOf(typeOf(initLabel, player)) }
    var batText by remember { mutableStateOf((player?.battingRating ?: 50).toString()) }
    var bowlText by remember { mutableStateOf((player?.bowlingRating ?: 30).toString()) }
    var fieldText by remember { mutableStateOf((player?.fieldingRating ?: 50).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (player == null) {
            try {
                focus.requestFocus()
            } catch (e: Exception) {
                // Not on screen yet.
            }
        }
    }
    val isAdded = player != null && Customisation.isAdded(player.id)
    // Left-arm bowlers have two more styles; a style the chosen hand does not have falls back to off break.
    val types = if (arm == 'L') LEFT_TYPES else RIGHT_TYPES
    val effType = if (type in types) type else "off break"

    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading(if (player == null) "Create a new player" else "Edit ${player.name}")
        Text("Team: ${team.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))

        if (player == null) {
            OutlinedTextField(
                value = name,
                onValueChange = { error = null; name = it.take(Customisation.MAX_NAME_LENGTH) },
                label = { Text("Player name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        SectionTitle("Batting hand")
        Column(modifier = Modifier.selectableGroup()) {
            RadioRow("Right-handed batter", hand == 'R') { hand = 'R' }
            RadioRow("Left-handed batter", hand == 'L') { hand = 'L' }
        }

        SectionTitle("Bowling hand")
        Column(modifier = Modifier.selectableGroup()) {
            RadioRow("Right-arm bowler", arm == 'R') { arm = 'R' }
            RadioRow("Left-arm bowler", arm == 'L') { arm = 'L' }
            RadioRow("Does not bowl", arm == 'N') { arm = 'N' }
        }

        if (arm != 'N') {
            SectionTitle("Bowling style")
            Column(modifier = Modifier.selectableGroup()) {
                types.forEach { t -> RadioRow(typeName(t), effType == t) { type = t } }
            }
        }

        SectionTitle("Role")
        Column(modifier = Modifier.selectableGroup()) {
            listOf(PlayerRole.BATSMAN, PlayerRole.WICKETKEEPER, PlayerRole.ALL_ROUNDER, PlayerRole.BOWLER).forEach { r ->
                RadioRow(PlayerProfiles.roleLabel(r), role == r) { role = r }
            }
        }

        SectionTitle("Ratings")
        RatingField("Batting rating, 1 to ${Customisation.MAX_BATTING}", batText) { error = null; batText = it.filter(Char::isDigit).take(3) }
        Spacer(modifier = Modifier.height(8.dp))
        RatingField("Bowling rating, 1 to ${Customisation.MAX_BOWLING}", bowlText) { error = null; bowlText = it.filter(Char::isDigit).take(3) }
        Spacer(modifier = Modifier.height(8.dp))
        RatingField("Fielding rating, 1 to $maxField", fieldText) { error = null; fieldText = it.filter(Char::isDigit).take(3) }

        error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val bat = batText.toIntOrNull()
                val bowl = bowlText.toIntOrNull()
                val field = fieldText.toIntOrNull()
                val t = name.trim()
                val label = when (arm) {
                    'N' -> ""
                    'L' -> "Left-arm $effType"
                    else -> "Right-arm $effType"
                }
                error = when {
                    player == null && t.isEmpty() -> "Please type the player's name."
                    player == null && team.players.any { it.name.equals(t, ignoreCase = true) } -> "This team already has a player called $t."
                    bat == null || bat < 1 || bat > Customisation.MAX_BATTING -> "The batting rating must be from 1 to ${Customisation.MAX_BATTING}."
                    bowl == null || bowl < 1 || bowl > Customisation.MAX_BOWLING -> "The bowling rating must be from 1 to ${Customisation.MAX_BOWLING}."
                    field == null || field < 1 || field > maxField -> "The fielding rating must be from 1 to $maxField."
                    (role == PlayerRole.BOWLER || role == PlayerRole.ALL_ROUNDER) && arm == 'N' ->
                        "A bowler or all-rounder needs a bowling hand and style."
                    else -> null
                }
                if (error == null && bat != null && bowl != null && field != null) {
                    when {
                        player == null -> {
                            Customisation.addPlayer(team.id, t, role, bat, bowl, field, hand, label)
                            onDone("Saved $t.")
                        }
                        isAdded -> {
                            Customisation.updateAdded(player.id, role, bat, bowl, field, hand, label)
                            onDone("Saved ${player.name}.")
                        }
                        else -> {
                            Customisation.saveEdit(team.id, player.name, role, bat, bowl, field, hand, label)
                            onDone("Saved ${player.name}.")
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text(if (player == null) "Save player" else "Save changes") }

        if (isAdded && player != null) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text("Delete this player") }
        }
    }
    if (confirmDelete && player != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete player?") },
            text = { Text("Delete ${player.name}? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Customisation.deleteAdded(player.id)
                    onDone("Deleted ${player.name}.")
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RatingField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth()
    )
}

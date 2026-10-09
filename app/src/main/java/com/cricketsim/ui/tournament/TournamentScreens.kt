package com.cricketsim.ui.tournament

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.CplData
import com.cricketsim.logic.PlayerRole
import com.cricketsim.logic.SquadTag
import com.cricketsim.logic.Team
import com.cricketsim.logic.TournamentFixture
import com.cricketsim.logic.TournamentState
import com.cricketsim.persistence.TournamentSaveStore
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * The Tournament section, reached from Home. It is one self-contained flow with its own pages and
 * its own Back handling, so MainActivity only needs one entry for it:
 *
 *   Tournament (4 kinds; only League is playable yet)
 *     -> League tournaments (CPL 2026)
 *       -> CPL 2026: Start, or Resume / Delete when a tournament is saved
 *         -> Choose your team (with View squad for every team)
 *           -> the tournament: Matches, Table, Venues and Stats tabs
 *
 * The tournament saves itself whenever it changes, so leaving and coming back resumes it.
 *
 * ACCESSIBILITY: every list row is one merged TalkBack item that reads in full; pages open with a
 * heading; every button's label says what it does and for which team; unavailable options are shown
 * as unavailable (with "Coming soon") rather than hidden, so positions never move.
 */
private sealed interface TPage {
    object Menu : TPage
    object Leagues : TPage
    object Cpl : TPage
    object TeamSelect : TPage
    data class Squad(val teamId: String) : TPage
    object Hub : TPage
}

@Composable
fun TournamentFlow(onExit: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TournamentSaveStore(context) }
    val scope = rememberCoroutineScope()
    val built = remember { CplData.buildTeams() }

    var page by remember { mutableStateOf<TPage>(TPage.Menu) }
    var saved by remember { mutableStateOf<TournamentState?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        saved = store.load()
        loaded = true
    }

    when (val current = page) {
        is TPage.Menu -> {
            BackHandler(onBack = onExit)
            MenuPage(onLeague = { page = TPage.Leagues }, onBack = onExit)
        }
        is TPage.Leagues -> {
            val back = { page = TPage.Menu }
            BackHandler(onBack = back)
            LeaguesPage(hasSaved = saved != null, onCpl = { page = TPage.Cpl }, onBack = back)
        }
        is TPage.Cpl -> {
            val back = { page = TPage.Leagues }
            BackHandler(onBack = back)
            CplPage(
                loaded = loaded,
                saved = saved,
                onStart = { page = TPage.TeamSelect },
                onResume = { page = TPage.Hub },
                onDelete = { confirmDelete = true },
                onBack = back
            )
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text("Delete this tournament?") },
                    text = { Text("Your saved CPL 2026 tournament, with its results and stats, will be removed. This cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDelete = false
                            saved = null
                            scope.launch { store.delete() }
                        }) { Text("Delete tournament") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDelete = false }) { Text("Keep it") }
                    }
                )
            }
        }
        is TPage.TeamSelect -> {
            val back = { page = TPage.Cpl }
            BackHandler(onBack = back)
            TeamSelectPage(
                teams = built.first,
                onViewSquad = { teamId -> page = TPage.Squad(teamId) },
                onChoose = { teamId ->
                    val state = CplData.newTournament(teamId, built, Random.Default)
                    saved = state
                    scope.launch { store.save(state) }
                    page = TPage.Hub
                },
                onBack = back
            )
        }
        is TPage.Squad -> {
            val back = { page = TPage.TeamSelect }
            BackHandler(onBack = back)
            val team = built.first.find { it.id == current.teamId }
            if (team != null) SquadPage(team = team, tags = built.second, onBack = back) else back()
        }
        is TPage.Hub -> {
            val back = { page = TPage.Cpl }
            BackHandler(onBack = back)
            val state = saved
            if (state != null) TournamentHub(state = state, onLeave = back) else back()
        }
    }
}

// ---- Shared pieces --------------------------------------------------------------------------

@Composable
private fun PageTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() }
    )
}

/** A big tappable row: title and description read together as one item; dimmed when unavailable. */
@Composable
private fun ChoiceRow(title: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .border(1.5.dp, colors.outline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun BackButton(label: String = "Back", onBack: () -> Unit) {
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

private fun roleLabel(role: PlayerRole): String = when (role) {
    PlayerRole.BATSMAN -> "Batsman"
    PlayerRole.BOWLER -> "Bowler"
    PlayerRole.ALL_ROUNDER -> "All-rounder"
    PlayerRole.WICKETKEEPER -> "Wicketkeeper"
}

private fun tagLabel(tag: SquadTag?): String = when (tag) {
    SquadTag.OVERSEAS -> "Overseas"
    SquadTag.BREAKOUT -> "Breakout player"
    else -> "West Indies"
}

// ---- Menu pages -----------------------------------------------------------------------------

@Composable
private fun MenuPage(onLeague: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        PageTitle("Tournament")
        Spacer(modifier = Modifier.height(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            ChoiceRow("International tournament", "Coming soon.", enabled = false, onClick = {})
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow("League tournament", "Play a full league season, saved as you go.", enabled = true, onClick = onLeague)
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow("Domestic tournament", "Coming soon.", enabled = false, onClick = {})
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow("Custom tournament", "Coming soon.", enabled = false, onClick = {})
        }
        Spacer(modifier = Modifier.height(8.dp))
        BackButton(onBack = onBack)
    }
}

@Composable
private fun LeaguesPage(hasSaved: Boolean, onCpl: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        PageTitle("League tournaments")
        Spacer(modifier = Modifier.height(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            ChoiceRow(
                title = "CPL 2026",
                description = "Caribbean Premier League: seven teams, 35 league matches and the play-offs." +
                    if (hasSaved) " You have a saved tournament." else "",
                enabled = true,
                onClick = onCpl
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        BackButton(onBack = onBack)
    }
}

@Composable
private fun CplPage(
    loaded: Boolean,
    saved: TournamentState?,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        PageTitle("CPL 2026")
        Spacer(modifier = Modifier.height(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (!loaded) {
                Text("Looking for a saved tournament...", style = MaterialTheme.typography.bodyLarge)
            } else if (saved != null) {
                val team = saved.teams.find { it.id == saved.userTeamId }?.name ?: "your team"
                val played = saved.fixtures.count { it.result != null }
                Text(
                    text = "Saved tournament. You are playing as $team. $played of ${saved.fixtures.size} matches played.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("Resume tournament") }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Delete saved tournament") }
            } else {
                Text(
                    text = "The Caribbean Premier League: seven franchises, a random fixture list every time, and the play-offs at Kensington Oval.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start tournament") }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        BackButton(onBack = onBack)
    }
}

// ---- Team choice and squads -----------------------------------------------------------------

@Composable
private fun TeamSelectPage(
    teams: List<Team>,
    onViewSquad: (String) -> Unit,
    onChoose: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        PageTitle("Choose your team")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Look at a squad first if you like, then choose.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(teams, key = { it.id }) { team ->
                val short = CplData.shortName(team.id)
                val home = CplData.stadium(CplData.homeStadiumId(team.id))?.name ?: ""
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                        Text(text = team.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Home ground: $home",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(onClick = { onViewSquad(team.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("View $short squad")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(onClick = { onChoose(team.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Choose $short")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        BackButton(onBack = onBack)
    }
}

@Composable
private fun SquadPage(team: Team, tags: Map<String, SquadTag>, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        PageTitle("${team.name} squad")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${team.players.size} players. Strongest first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(team.players, key = { it.id }) { p ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .semantics(mergeDescendants = true) {}
                ) {
                    Text(text = p.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${roleLabel(p.role)}. ${tagLabel(tags[p.id])}. Batting ${p.battingRating}, bowling ${p.bowlingRating}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        BackButton("Back to teams", onBack)
    }
}

// ---- The tournament itself ------------------------------------------------------------------

@Composable
private fun TournamentHub(state: TournamentState, onLeave: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val titles = listOf("Matches", "Table", "Venues", "Stats")
    val myTeam = state.teams.find { it.id == state.userTeamId }?.name ?: ""
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp)) {
        PageTitle(state.name)
        Text(
            text = "You are playing as $myTeam.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            when (tab) {
                0 -> MatchesTab(state)
                1 -> TableTab(state)
                2 -> VenuesTab(state)
                else -> StatsTab(state)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        BackButton("Leave tournament (it is saved)", onLeave)
    }
}

private fun nameOf(state: TournamentState, id: String?): String =
    state.teams.find { it.id == id }?.name ?: "To be decided"

@Composable
private fun MatchesTab(state: TournamentState) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "The fixture list is random for every tournament. Playing your matches and simulating the others comes in the next update.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(state.fixtures, key = { it.id }) { f -> FixtureCard(state, f) }
    }
}

@Composable
private fun FixtureCard(state: TournamentState, f: TournamentFixture) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val stadium = CplData.stadium(f.stadiumId)
    val mine = f.homeId == state.userTeamId || f.awayId == state.userTeamId
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .border(1.dp, if (mine) colors.primary else colors.outlineVariant, shape)
            .padding(14.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Text(
            text = "Match ${f.number}, ${f.stage}" + if (mine) ", your match" else "",
            style = MaterialTheme.typography.labelLarge,
            color = if (mine) colors.primary else colors.onSurfaceVariant
        )
        if (f.homeId != null && f.awayId != null) {
            Text(
                text = "${nameOf(state, f.homeId)} v ${nameOf(state, f.awayId)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(text = "Teams to be decided", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = f.placeholder, style = MaterialTheme.typography.bodyMedium)
        }
        if (stadium != null) {
            Text(text = "${stadium.name}, ${stadium.city}", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = f.result?.summary ?: "Not played yet",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun TableTab(state: TournamentState) {
    val rows = remember(state) { CplData.standings(state) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "Two points for a win, one each for no result. The top four go to the play-offs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("#", modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.labelLarge)
                Text("Team", modifier = Modifier.weight(2.6f), style = MaterialTheme.typography.labelLarge)
                Text("P", modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.labelLarge)
                Text("W", modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.labelLarge)
                Text("L", modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.labelLarge)
                Text("Pts", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge)
                Text("NRR", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelLarge)
            }
        }
        itemsIndexed(rows, key = { _, r -> r.team.id }) { index, r ->
            val nrr = (if (r.netRunRate >= 0) "+" else "") + String.format(java.util.Locale.US, "%.3f", r.netRunRate)
            val mine = r.team.id == state.userTeamId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("${index + 1}", modifier = Modifier.weight(0.5f))
                Text(
                    text = CplData.shortName(r.team.id) + if (mine) " (you)" else "",
                    modifier = Modifier.weight(2.6f),
                    fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal
                )
                Text("${r.played}", modifier = Modifier.weight(0.6f))
                Text("${r.won}", modifier = Modifier.weight(0.6f))
                Text("${r.lost}", modifier = Modifier.weight(0.6f))
                Text("${r.points}", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold)
                Text(nrr, modifier = Modifier.weight(1.2f))
            }
        }
    }
}

@Composable
private fun VenuesTab(state: TournamentState) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(CplData.stadiums, key = { it.id }) { s ->
            val homeOf = state.teams.filter { CplData.homeStadiumId(it.id) == s.id }.joinToString(", ") { it.name }
            val matches = state.fixtures.count { it.stadiumId == s.id }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .semantics(mergeDescendants = true) {}
            ) {
                Text(text = s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = "${s.city}, ${s.country}", style = MaterialTheme.typography.bodyMedium)
                Text(text = s.pitchDescription, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Boundaries: ${s.boundarySize.name.lowercase()}. Typical first-innings score: ${s.avgT20FirstInningsScore}.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(text = s.climate.description, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = (if (homeOf.isNotEmpty()) "Home of $homeOf. " else "No home team; a touring ground. ") +
                        "Matches this tournament: $matches.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatsTab(state: TournamentState) {
    var scope by remember { mutableStateOf(0) } // 0 = my team, 1 = all
    val stats = remember(state, scope) {
        val all = state.playerStats.values
        if (scope == 0) all.filter { it.teamId == state.userTeamId } else all.toList()
    }
    val categories = remember(stats) {
        CplData.statCategories(stats, teamNameOf = { id -> CplData.shortName(id) })
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = scope) {
            Tab(selected = scope == 0, onClick = { scope = 0 }, text = { Text("My team") })
            Tab(selected = scope == 1, onClick = { scope = 1 }, text = { Text("All") })
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(categories, key = { it.title }) { c ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        text = c.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (c.entries.isEmpty()) {
                        Text(
                            text = "No matches played yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        c.entries.forEachIndexed { i, e ->
                            Text(
                                text = "${i + 1}. ${e.name}, ${e.teamName}: ${e.value}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

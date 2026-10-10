package com.cricketsim.ui.tournament

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.CommentaryCategory
import com.cricketsim.logic.CplData
import com.cricketsim.logic.CplSimulator
import com.cricketsim.logic.MatchFormat
import com.cricketsim.logic.PlayerRole
import com.cricketsim.logic.SimMode
import com.cricketsim.logic.SquadTag
import com.cricketsim.logic.Team
import com.cricketsim.logic.TossResult
import com.cricketsim.logic.TournamentFixture
import com.cricketsim.logic.TournamentState
import com.cricketsim.persistence.MatchSaveStore
import com.cricketsim.persistence.MatchSnapshot
import com.cricketsim.persistence.TournamentSaveStore
import com.cricketsim.persistence.summary
import com.cricketsim.ui.match.MatchScreen
import com.cricketsim.ui.match.ScorecardScreen
import com.cricketsim.ui.setup.PlayingXIScreen
import com.cricketsim.ui.setup.TossScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 *              (Matches: play your own match or simulate matches, open any scorecard)
 *
 * PLAYING YOUR MATCH: Playing XI (CPL rules checked) -> toss -> the normal live match screen. The
 * franchise sides have no anthems. A tournament match autosaves itself part-way into its OWN file
 * (saved_tournament_match.json; the normal Resume match slot is left alone) and is offered again
 * as "Resume match" on the CPL 2026 page and on the Matches tab. The result, scorecard and stats
 * are recorded the moment the match ends, which also clears that save.
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
    object Celebration : TPage
    data class PickXI(val fixtureId: Int, val attempt: Int = 0) : TPage
    data class Toss(val fixtureId: Int, val userXI: Team, val opponentXI: Team) : TPage
    data class PlayMatch(
        val fixtureId: Int,
        val userXI: Team,
        val opponentXI: Team,
        val toss: TossResult,
        /** Set when picking a saved match back up. */
        val resume: MatchSnapshot? = null
    ) : TPage
    data class Scorecard(val fixtureId: Int) : TPage
}

@Composable
fun TournamentFlow(onExit: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TournamentSaveStore(context) }
    // A tournament match saves itself part-way into its OWN slot, so the normal Resume match is never touched.
    val matchStore = remember { MatchSaveStore(context, "saved_tournament_match.json") }
    val scope = rememberCoroutineScope()
    val built = remember { CplData.buildTeams() }

    var page by remember { mutableStateOf<TPage>(TPage.Menu) }
    var saved by remember { mutableStateOf<TournamentState?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var xiProblem by remember { mutableStateOf<String?>(null) }
    var pendingMatch by remember { mutableStateOf<MatchSnapshot?>(null) }

    // Runs the simulation off the main thread, then saves. Only one run at a time.
    val simulate: (SimMode) -> Unit = { mode ->
        val current = saved
        if (current != null && !busy) {
            busy = true
            message = "Simulating, please wait."
            // Simulating past the user's own fixture replaces a match left part-way.
            if (mode != SimMode.UNTIL_MINE && pendingMatch != null) {
                pendingMatch = null
                scope.launch { matchStore.clear() }
            }
            scope.launch {
                val updated = withContext(Dispatchers.Default) { CplSimulator.run(current, mode, Random.Default) }
                val played = updated.fixtures.count { it.result != null } - current.fixtures.count { it.result != null }
                saved = updated
                store.save(updated)
                message = when {
                    played <= 0 -> "Nothing to simulate right now."
                    played == 1 -> "Simulated 1 match."
                    else -> "Simulated $played matches."
                }
                if (CplSimulator.champion(current) == null && CplSimulator.champion(updated) != null) {
                    CplSimulator.seriesAwardsText(updated)?.let { message = "$message $it" }
                }
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val tournament = store.load()
        saved = tournament
        // A saved match counts only if there is a tournament it belongs to.
        val snapshot = matchStore.load()
        if (tournament != null && snapshot != null && snapshot.state.userTeam.id == tournament.userTeamId) {
            pendingMatch = snapshot
        } else if (snapshot != null) {
            matchStore.clear()
        }
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
                pendingSummary = pendingMatch?.summary(),
                onResumeMatch = {
                    val snapshot = pendingMatch
                    val state = saved
                    val fixtureId = if (snapshot != null && state != null) {
                        CplSimulator.fixtureFor(state, snapshot.state.userTeam.id, snapshot.state.opponentTeam.id)
                    } else null
                    if (snapshot != null && fixtureId != null) {
                        page = TPage.PlayMatch(fixtureId, snapshot.state.userTeam, snapshot.state.opponentTeam, snapshot.toss, snapshot)
                    }
                },
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
                            pendingMatch = null
                            scope.launch {
                                store.delete()
                                matchStore.clear()
                            }
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
            if (state != null) {
                // The Final has just been decided and not yet celebrated: go to the celebration first.
                val needsCelebration = CplSimulator.champion(state) != null && !state.celebrated
                LaunchedEffect(needsCelebration) {
                    if (needsCelebration) page = TPage.Celebration
                }
                TournamentHub(
                    state = state,
                    busy = busy,
                    message = message,
                    onSimulate = simulate,
                    onScorecard = { id -> page = TPage.Scorecard(id) },
                    onPlay = { id ->
                        // Starting this match from the beginning drops any half-played save of it.
                        if (pendingMatch != null) {
                            pendingMatch = null
                            scope.launch { matchStore.clear() }
                        }
                        page = TPage.PickXI(id)
                    },
                    matchInProgress = pendingMatch != null,
                    onResumeMatch = {
                        val snapshot = pendingMatch
                        val fixtureId = if (snapshot != null) {
                            CplSimulator.fixtureFor(state, snapshot.state.userTeam.id, snapshot.state.opponentTeam.id)
                        } else null
                        if (snapshot != null && fixtureId != null) {
                            page = TPage.PlayMatch(fixtureId, snapshot.state.userTeam, snapshot.state.opponentTeam, snapshot.toss, snapshot)
                        }
                    },
                    onDiscardMatch = {
                        pendingMatch = null
                        scope.launch { matchStore.clear() }
                    },
                    onLeave = back
                )
            } else back()
        }
        is TPage.PickXI -> {
            val back = { page = TPage.Hub }
            BackHandler(onBack = back)
            val state = saved
            val fixture = state?.fixtures?.find { it.id == current.fixtureId }
            val userId = state?.userTeamId
            val userSquad = state?.teams?.find { it.id == userId }
            val opponentId = if (fixture?.homeId == userId) fixture?.awayId else fixture?.homeId
            val opponentSquad = state?.teams?.find { it.id == opponentId }
            val stadium = fixture?.let { CplData.stadium(it.stadiumId) }
            if (state != null && fixture != null && userSquad != null && opponentSquad != null && stadium != null) {
                // The suggested XI comes first, so the screen's Auto-pick gives a legal XI for this ground.
                val selection = remember(current.fixtureId) { CplSimulator.squadForSelection(userSquad, state.tags, stadium) }
                key(current.attempt) {
                    PlayingXIScreen(
                        userTeam = selection,
                        onXIConfirmed = { xi ->
                            val problem = CplSimulator.xiProblem(xi, state.tags)
                            if (problem != null) {
                                xiProblem = problem
                                page = TPage.PickXI(current.fixtureId, current.attempt + 1)
                            } else {
                                val opponentXI = CplSimulator.pickXI(opponentSquad, state.tags, stadium)
                                page = TPage.Toss(current.fixtureId, xi, opponentXI)
                            }
                        },
                        onBack = back
                    )
                }
                val problem = xiProblem
                if (problem != null) {
                    AlertDialog(
                        onDismissRequest = { xiProblem = null },
                        title = { Text("Change your team") },
                        text = { Text(problem) },
                        confirmButton = { TextButton(onClick = { xiProblem = null }) { Text("OK") } }
                    )
                }
            } else back()
        }
        is TPage.Toss -> {
            val back = { page = TPage.PickXI(current.fixtureId) }
            BackHandler(onBack = back)
            TossScreen(
                userTeam = current.userXI,
                opponentTeam = current.opponentXI,
                onTossComplete = { toss -> page = TPage.PlayMatch(current.fixtureId, current.userXI, current.opponentXI, toss) },
                onBack = back
            )
        }
        is TPage.PlayMatch -> {
            // Leaving mid-match: the match has saved itself, so read it back for the Resume buttons.
            val back = {
                page = TPage.Hub
                scope.launch { pendingMatch = matchStore.load() }
                Unit
            }
            val stadium = current.resume?.stadium
                ?: saved?.fixtures?.find { it.id == current.fixtureId }?.let { CplData.stadium(it.stadiumId) }
            if (stadium != null) {
                MatchScreen(
                    format = MatchFormat.T20,
                    stadium = stadium,
                    userTeam = current.userXI,
                    opponentTeam = current.opponentXI,
                    toss = current.toss,
                    onBack = back,
                    onMatchFinished = { page = TPage.Hub },
                    resume = current.resume,
                    saveStore = matchStore,
                    onMatchComplete = { finished ->
                        pendingMatch = null
                        val state = saved
                        if (state != null) {
                            val updated = CplSimulator.recordPlayedMatch(state, current.fixtureId, finished, current.userXI, current.opponentXI)
                            saved = updated
                            // After the Final: the match award has just been shown on the result screen; now the series award.
                            if (CplSimulator.champion(state) == null && CplSimulator.champion(updated) != null) {
                                message = CplSimulator.seriesAwardsText(updated) ?: ""
                            }
                            scope.launch { store.save(updated) }
                        }
                    }
                )
            } else back()
        }
        is TPage.Celebration -> {
            val state = saved
            val done = {
                val latest = saved
                if (latest != null) {
                    val updated = latest.copy(celebrated = true)
                    saved = updated
                    scope.launch { store.save(updated) }
                }
                page = TPage.Hub
            }
            if (state != null) CelebrationScreen(state = state, onContinue = done) else done()
        }
        is TPage.Scorecard -> {
            val back = { page = TPage.Hub }
            BackHandler(onBack = back)
            val card = saved?.fixtures?.find { it.id == current.fixtureId }?.scorecard
            if (card != null) ScorecardScreen(state = card, onBack = back) else back()
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
    pendingSummary: String?,
    onResumeMatch: () -> Unit,
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
                if (pendingSummary != null) {
                    Text(
                        text = "A match is in progress. $pendingSummary",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onResumeMatch, modifier = Modifier.fillMaxWidth()) { Text("Resume match") }
                    Spacer(modifier = Modifier.height(8.dp))
                }
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

// ---- The celebration -------------------------------------------------------------------------

/**
 * Shown once, when the Final has been decided (played by you or simulated): a fanfare with
 * fireworks and a roar, the commentators crowning the champions, then the Player of the Match in
 * the Final and the Player of the Series. Everything shown is also spoken, in this order, so a
 * screen-reader user hears the same ceremony: the sound first, then the commentary, then the names.
 */
@Composable
private fun CelebrationScreen(state: TournamentState, onContinue: () -> Unit) {
    val champion = CplSimulator.champion(state)
    val userWon = champion?.id == state.userTeamId
    val series = CplSimulator.playerOfTheSeries(state)
    val finalAward = state.fixtures.firstOrNull { it.stage == "Final" }?.result?.playerOfMatch
    val headline = if (userWon) "You are the champions!" else "Tournament complete"
    val championLine = when {
        champion == null -> ""
        userWon -> "Congratulations! ${champion.name} have won ${state.name}."
        else -> "${champion.name} are the ${state.name} champions."
    }
    val finalLine = if (!finalAward.isNullOrEmpty()) "Player of the match in the Final: $finalAward." else ""
    val seriesLine = if (series != null) {
        "Player of the series: ${series.name}, ${CplData.shortName(series.teamId)}, with ${series.runs} runs and ${series.wickets} wickets." +
            if (series.teamId == state.userTeamId) " One of your own players!" else ""
    } else ""
    val services = LocalGameServices.current
    BackHandler(onBack = onContinue)

    LaunchedEffect(Unit) {
        val sound = services?.sound
        sound?.playTournamentCelebration()
        delay(2800)
        sound?.enqueueCommentary(if (userWon) CommentaryCategory.TOURNAMENT_CHAMPIONS else CommentaryCategory.TOURNAMENT_CROWNED)
        delay(600)
        while (sound?.isCommentaryBusy() == true) delay(300)
        services?.announceSpoken(listOf(headline, championLine, finalLine).filter { it.isNotEmpty() }.joinToString(" "))
        if (series != null) {
            delay(1500)
            sound?.enqueueCommentary(CommentaryCategory.PLAYER_OF_SERIES)
            delay(600)
            while (sound?.isCommentaryBusy() == true) delay(300)
            services?.announceSpoken(seriesLine)
        }
    }

    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.primaryContainer)
                .padding(24.dp)
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimaryContainer,
                modifier = Modifier.semantics { heading() }
            )
            if (championLine.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = championLine, style = MaterialTheme.typography.titleLarge, color = colors.onPrimaryContainer)
            }
            if (finalLine.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = finalLine, style = MaterialTheme.typography.bodyLarge, color = colors.onPrimaryContainer)
            }
            if (seriesLine.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = seriesLine, style = MaterialTheme.typography.bodyLarge, color = colors.onPrimaryContainer)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue to the tournament") }
    }
}

// ---- The tournament itself ------------------------------------------------------------------

@Composable
private fun TournamentHub(
    state: TournamentState,
    busy: Boolean,
    message: String,
    onSimulate: (SimMode) -> Unit,
    onScorecard: (Int) -> Unit,
    onPlay: (Int) -> Unit,
    matchInProgress: Boolean,
    onResumeMatch: () -> Unit,
    onDiscardMatch: () -> Unit,
    onLeave: () -> Unit
) {
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
                0 -> MatchesTab(state, busy, message, onSimulate, onScorecard, onPlay, matchInProgress, onResumeMatch, onDiscardMatch)
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
private fun MatchesTab(
    state: TournamentState,
    busy: Boolean,
    message: String,
    onSimulate: (SimMode) -> Unit,
    onScorecard: (Int) -> Unit,
    onPlay: (Int) -> Unit,
    matchInProgress: Boolean,
    onResumeMatch: () -> Unit,
    onDiscardMatch: () -> Unit
) {
    val next = CplSimulator.nextFixture(state)
    val champion = CplSimulator.champion(state)
    val nextIsMine = next != null && CplSimulator.involves(next, state.userTeamId)
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                if (champion != null) {
                    Text(
                        text = "Champions: ${champion.name}.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    CplSimulator.seriesAwardsText(state)?.let { awards ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = awards, style = MaterialTheme.typography.bodyLarge)
                    }
                } else if (next != null) {
                    val ground = CplData.stadium(next.stadiumId)?.name ?: ""
                    Text(
                        text = "Next: Match ${next.number}, ${nameOf(state, next.homeId)} v ${nameOf(state, next.awayId)} at $ground." +
                            if (nextIsMine) " This is your match." else "",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (next != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (nextIsMine && matchInProgress) {
                        Button(onClick = onResumeMatch, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Resume my match")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(onClick = { onPlay(next.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Discard saved match and start again")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(onClick = { onSimulate(SimMode.NEXT) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Simulate my match instead")
                        }
                    } else if (nextIsMine) {
                        Button(onClick = { onPlay(next.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Play my match")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(onClick = { onSimulate(SimMode.NEXT) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Simulate my match instead")
                        }
                    } else {
                        Button(onClick = { onSimulate(SimMode.NEXT) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Simulate next match")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { onSimulate(SimMode.UNTIL_MINE) },
                        enabled = !busy && !nextIsMine,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Simulate until my next match") }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(onClick = { onSimulate(SimMode.ALL) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Simulate the rest of the tournament")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Matches are played by the game's own match engine on each ground's real pitch and weather. " +
                            "You can play your own matches yourself or simulate them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (message.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        }
        items(state.fixtures, key = { it.id }) { f ->
            Column(modifier = Modifier.fillMaxWidth()) { FixtureCard(state, f, onScorecard) }
        }
    }
}

@Composable
private fun FixtureCard(state: TournamentState, f: TournamentFixture, onScorecard: (Int) -> Unit) {
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
        val potm = f.result?.playerOfMatch
        if (!potm.isNullOrEmpty()) {
            Text(text = "Player of the match: $potm", style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (f.scorecard != null) {
        OutlinedButton(onClick = { onScorecard(f.id) }, modifier = Modifier.fillMaxWidth()) {
            Text("View scorecard, match ${f.number}")
        }
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

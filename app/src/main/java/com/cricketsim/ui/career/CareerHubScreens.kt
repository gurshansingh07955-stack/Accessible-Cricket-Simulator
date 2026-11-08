package com.cricketsim.ui.career

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.CareerProfile
import com.cricketsim.logic.CareerProgress
import com.cricketsim.logic.CareerSeason
import com.cricketsim.logic.CareerSelection
import com.cricketsim.logic.CareerWorld
import com.cricketsim.logic.CplData
import com.cricketsim.logic.CplSimulator
import com.cricketsim.logic.MatchFormat
import com.cricketsim.logic.MatchRules
import com.cricketsim.logic.SimMode
import com.cricketsim.logic.TossResult
import com.cricketsim.logic.TossRoles
import com.cricketsim.logic.TournamentFixture
import com.cricketsim.logic.TournamentState
import com.cricketsim.persistence.MatchSaveStore
import com.cricketsim.persistence.MatchSnapshot
import com.cricketsim.persistence.TournamentSaveStore
import com.cricketsim.persistence.summary
import com.cricketsim.ui.custom.OptionButton
import com.cricketsim.ui.custom.PageHeading
import com.cricketsim.ui.match.MatchScreen
import com.cricketsim.ui.setup.PlayingXIScreen
import com.cricketsim.ui.setup.TossScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** District club cricket: 20 overs a side, no DRS, rain possible, and a tie is shared (no Super Over). */
private val CAREER_RULES = MatchRules(
    overs = 20, restrictionBalls = 36, fieldersOutside = 2, drsReviews = 0, rainEnabled = true, maxOversPerBowler = 4, noSuperOver = true
)

private enum class HubTab(val label: String) { Home("Home"), Play("Game play"), Calendar("Calendar"), Table("Points table") }

/** Where the player is in playing one of his club's matches (full screen: the tabs are hidden while a match is set up or played). */
private sealed interface CPlay {
    class PickXI(val fixtureId: Int, val attempt: Int = 0) : CPlay
    class Toss(val fixtureId: Int, val userXI: com.cricketsim.logic.Team, val oppXI: com.cricketsim.logic.Team) : CPlay
    class Match(
        val fixtureId: Int,
        val userXI: com.cricketsim.logic.Team,
        val oppXI: com.cricketsim.logic.Team,
        val toss: TossResult,
        val resume: MatchSnapshot? = null
    ) : CPlay
}

/**
 * The career's home page: four tabs along the bottom of the screen, next to the phone's own buttons.
 *  Home: the squad information and everything made so far (the club squad, the clubs of the state, the player profile).
 *  Game play: the next match of the player's club, to play or simulate; the season's progress.
 *  Calendar: the season's schedule, round by round, with dates and results.
 *  Points table: the league table, and the statistics (All players, or My team), as sub-tabs.
 * The season is one league of the state's district clubs ([CareerSeason]); it is saved in its own file per career.
 */
@Composable
fun CareerHub(
    career: CareerProfile,
    onBack: () -> Unit,
    onOpenSquad: () -> Unit,
    onOpenClubs: () -> Unit,
    onOpenProfile: () -> Unit,
    onCareerChanged: (CareerProfile) -> Unit
) {
    val context = LocalContext.current
    val services = LocalGameServices.current
    val scope = rememberCoroutineScope()
    val seasonStore = remember(career.id) { TournamentSaveStore(context, "career_season_${career.id}.json") }
    val matchStore = remember(career.id) { MatchSaveStore(context, "career_match_${career.id}.json") }

    var tab by remember { mutableStateOf(HubTab.Home) }
    var season by remember { mutableStateOf<TournamentState?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var pendingMatch by remember { mutableStateOf<MatchSnapshot?>(null) }
    var play by remember { mutableStateOf<CPlay?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var xiProblem by remember { mutableStateOf<String?>(null) }
    var tableTab by remember { mutableStateOf(0) }
    var statsTab by remember { mutableStateOf(0) }
    // The selectors' decision for the player's next match (his rank in the club, the difficulty and his form).
    val decision = CareerSelection.decide(career)

    LaunchedEffect(career.id) {
        val existing = seasonStore.load()
        val state = existing ?: CareerSeason.newSeason(career)?.also { seasonStore.save(it) }
        season = state
        pendingMatch = matchStore.load()
        loaded = true
    }

    // The crowd, as in the game's tournaments: from the toss to the end of the match.
    val crowdWanted = play is CPlay.Toss || play is CPlay.Match
    val effectsOn = services?.settings?.soundEffects ?: false
    LaunchedEffect(crowdWanted, effectsOn) {
        val sound = services?.sound ?: return@LaunchedEffect
        if (!crowdWanted) sound.stopCrowdAmbience() else if (effectsOn) sound.startCrowdAmbience()
    }

    BackHandler(enabled = play == null) { if (tab != HubTab.Home) tab = HubTab.Home else onBack() }

    // Simulates off the main thread, then saves. includeMine plays the player's own next match too (the selectors' decision applies:
    // he is in the XI or left out), whole goes on to the end of the season. His form and ratings follow what he does.
    val simulate: (Boolean, Boolean) -> Unit = { includeMine, whole ->
        val current = season
        if (current != null && !busy) {
            busy = true
            message = "Simulating."
            scope.launch {
                val adv = withContext(Dispatchers.Default) { CareerSeason.advance(current, career, Random.Default, includeMine, whole) }
                season = adv.state
                seasonStore.save(adv.state)
                if (adv.career !== career) onCareerChanged(adv.career)
                busy = false
                message = adv.lines.joinToString("\n").ifEmpty { lastResultText(adv.state) }
            }
        }
    }

    // ---------------------------------------------------------------- playing a match (full screen)
    val flow = play
    val state = season
    if (flow != null && state != null) {
        when (flow) {
            is CPlay.PickXI -> {
                val back = { play = null }
                BackHandler(onBack = back)
                val fixture = state.fixtures.find { it.id == flow.fixtureId }
                val userSquad = state.teams.find { it.id == state.userTeamId }
                val oppId = if (fixture?.homeId == state.userTeamId) fixture?.awayId else fixture?.homeId
                val oppSquad = state.teams.find { it.id == oppId }
                val stadium = fixture?.let { CplData.stadium(it.stadiumId) }
                if (fixture != null && userSquad != null && oppSquad != null && stadium != null) {
                    val selection = remember(flow.fixtureId) { CplSimulator.squadForSelection(userSquad, state.tags, stadium) }
                    key(flow.attempt) {
                        PlayingXIScreen(
                            userTeam = selection,
                            onXIConfirmed = { xi ->
                                val problem = CplSimulator.xiProblem(xi, state.tags, state.tournamentId)
                                if (problem != null) {
                                    xiProblem = problem
                                    play = CPlay.PickXI(flow.fixtureId, flow.attempt + 1)
                                } else {
                                    play = CPlay.Toss(flow.fixtureId, xi, CplSimulator.pickXI(oppSquad, state.tags, stadium))
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
                } else {
                    Column(modifier = Modifier.padding(20.dp)) { OutlinedButton(onClick = back) { Text("Back") } }
                }
            }

            is CPlay.Toss -> {
                val back = { play = CPlay.PickXI(flow.fixtureId) }
                BackHandler(onBack = back)
                val fixture = state.fixtures.find { it.id == flow.fixtureId }
                val userHome = fixture?.homeId == state.userTeamId
                TossScreen(
                    userTeam = flow.userXI,
                    opponentTeam = flow.oppXI,
                    userCalls = TossRoles.userCalls(userHome, !userHome),
                    onTossComplete = { toss -> play = CPlay.Match(flow.fixtureId, flow.userXI, flow.oppXI, toss) },
                    onBack = back
                )
            }

            is CPlay.Match -> {
                val back = {
                    play = null
                    scope.launch { pendingMatch = matchStore.load() }
                    Unit
                }
                val fixture = state.fixtures.find { it.id == flow.fixtureId }
                val stadium = flow.resume?.stadium ?: fixture?.let { CplData.stadium(it.stadiumId) }
                if (stadium != null) {
                    MatchScreen(
                        tournamentName = state.name,
                        format = MatchFormat.T20,
                        rules = CAREER_RULES,
                        careerPlayerId = CareerSeason.userPlayerId(career),
                        stadium = stadium,
                        userTeam = flow.userXI,
                        opponentTeam = flow.oppXI,
                        toss = flow.toss,
                        onBack = back,
                        onMatchFinished = { play = null },
                        resume = flow.resume,
                        saveStore = matchStore,
                        onMatchComplete = { finished ->
                            pendingMatch = null
                            val latest = season ?: state
                            val pid = CareerSeason.userPlayerId(career)
                            val recorded = CplSimulator.recordPlayedMatch(latest, flow.fixtureId, finished, flow.userXI, flow.oppXI)
                            // What the match did to the player: his form and his ratings.
                            val progress = CareerProgress.afterMatch(
                                career,
                                if (pid != null) latest.playerStats[pid] else null,
                                if (pid != null) recorded.playerStats[pid] else null,
                                Random.Default
                            )
                            val updated = CareerSeason.withUserPlayer(recorded, progress.career)
                            season = updated
                            if (progress.career !== career) onCareerChanged(progress.career)
                            message = lastResultText(updated) + " " + progress.text
                            scope.launch { seasonStore.save(updated) }
                        }
                    )
                } else {
                    Column(modifier = Modifier.padding(20.dp)) { OutlinedButton(onClick = back) { Text("Back") } }
                }
            }
        }
        return
    }

    // ---------------------------------------------------------------- the four tabs
    Scaffold(
        bottomBar = {
            NavigationBar {
                HubTab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Spacer(modifier = Modifier.size(0.dp)) },
                        label = { Text(t.label) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            when (tab) {
                HubTab.Home -> HomeTab(career, decision, onBack, onOpenSquad, onOpenClubs, onOpenProfile)
                HubTab.Play -> PlayTab(
                    career = career,
                    season = state,
                    loaded = loaded,
                    busy = busy,
                    message = message,
                    pending = pendingMatch,
                    decision = decision,
                    onPlay = {
                        val current = season
                        if (current != null && !busy) {
                            busy = true
                            message = "Getting ready."
                            scope.launch {
                                if (pendingMatch != null) {
                                    pendingMatch = null
                                    matchStore.clear()
                                }
                                // The matches of the other clubs that come before yours are played first, so the table is up to date.
                                val adv = withContext(Dispatchers.Default) { CareerSeason.advance(current, career, Random.Default, false, false) }
                                season = adv.state
                                seasonStore.save(adv.state)
                                busy = false
                                message = ""
                                val updated = adv.state
                                val mine = updated.fixtures.firstOrNull { it.result == null && CplSimulator.involves(it, updated.userTeamId) }
                                if (mine != null) {
                                    val pid = CareerSeason.userPlayerId(career)
                                    val chosen = CareerSelection.decide(career)
                                    val squad = updated.teams.find { it.id == updated.userTeamId }
                                    val oppId = if (mine.homeId == updated.userTeamId) mine.awayId else mine.homeId
                                    val oppSquad = updated.teams.find { it.id == oppId }
                                    val stadium = CplData.stadium(mine.stadiumId)
                                    if (!chosen.selected) {
                                        message = chosen.reason + " You can simulate the match instead."
                                    } else if (pid != null && squad != null && oppSquad != null && stadium != null) {
                                        // The selectors name the XI (you are in it): straight to the toss.
                                        val xi = CareerSelection.matchXI(squad, updated.tags, stadium, pid, career.battingNumber)
                                        play = CPlay.Toss(mine.id, xi, CplSimulator.pickXI(oppSquad, updated.tags, stadium))
                                    }
                                }
                            }
                        }
                    },
                    onSimulateMine = { simulate(true, false) },
                    onSimulateAll = { simulate(true, true) },
                    onResume = {
                        val snapshot = pendingMatch
                        val current = season
                        if (snapshot != null && current != null) {
                            val fixtureId = CplSimulator.fixtureFor(current, snapshot.state.userTeam.id, snapshot.state.opponentTeam.id)
                            if (fixtureId != null) {
                                play = CPlay.Match(fixtureId, snapshot.state.userTeam, snapshot.state.opponentTeam, snapshot.toss, snapshot)
                            }
                        }
                    },
                    onDiscard = {
                        pendingMatch = null
                        scope.launch { matchStore.clear() }
                    }
                )
                HubTab.Calendar -> CalendarTab(state, loaded)
                HubTab.Table -> TableTab(state, loaded, tableTab, { tableTab = it }, statsTab, { statsTab = it })
            }
        }
    }
}

/** One line about the player's latest finished match of the season. */
private fun lastResultText(state: TournamentState): String {
    val last = state.fixtures.lastOrNull { it.result != null && CplSimulator.involves(it, state.userTeamId) } ?: return ""
    val r = last.result ?: return ""
    return "Latest result: ${CareerSeason.nameOf(state, last.homeId)} v ${CareerSeason.nameOf(state, last.awayId)}. ${r.summary}"
}

private fun fixtureText(state: TournamentState, f: TournamentFixture): String {
    val ground = CplData.stadium(f.stadiumId)?.name ?: ""
    return "${CareerSeason.nameOf(state, f.homeId)} v ${CareerSeason.nameOf(state, f.awayId)}${if (ground.isNotEmpty()) " at $ground" else ""}"
}

@Composable
private fun HomeTab(
    career: CareerProfile,
    decision: CareerSelection.Decision,
    onBack: () -> Unit,
    onOpenSquad: () -> Unit,
    onOpenClubs: () -> Unit,
    onOpenProfile: () -> Unit
) {
    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to My Player") }
    Spacer(modifier = Modifier.height(16.dp))
    PageHeading(career.fullName)
    Spacer(modifier = Modifier.height(8.dp))
    CareerPhoto(career)
    Text("${career.club}, ${career.state}, ${career.country}. Jersey number ${career.jersey}.", style = MaterialTheme.typography.bodyLarge)
    Spacer(modifier = Modifier.height(4.dp))
    Text(career.playingStyle(), style = MaterialTheme.typography.bodyMedium)
    if (career.plan().isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(career.plan(), style = MaterialTheme.typography.bodyMedium)
    }
    if (career.growthLine().isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(career.growthLine(), style = MaterialTheme.typography.bodyMedium)
    }
    if (career.clubs.isNotEmpty() && decision.reason.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text("Selection for your next match: ${decision.reason}", style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(modifier = Modifier.height(16.dp))
    if (career.clubs.isEmpty()) {
        Text(
            text = "This career was made before the club teams existed, so it has no squads or season. Delete it from the player profile and start a new one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
    } else {
        OptionButton("My club squad", "${career.club}: ${career.myClub?.players?.size ?: 0} players.", true, true) { onOpenSquad() }
        Spacer(modifier = Modifier.height(12.dp))
        OptionButton("Clubs of ${career.state}", "${career.clubs.size} district clubs and their squads.", true, false) { onOpenClubs() }
        Spacer(modifier = Modifier.height(12.dp))
    }
    OptionButton("Player profile", "Your details, ratings and choices. Delete this career here.", true, false) { onOpenProfile() }
}

@Composable
private fun PlayTab(
    career: CareerProfile,
    season: TournamentState?,
    loaded: Boolean,
    busy: Boolean,
    message: String,
    pending: MatchSnapshot?,
    decision: CareerSelection.Decision,
    onPlay: () -> Unit,
    onSimulateMine: () -> Unit,
    onSimulateAll: () -> Unit,
    onResume: () -> Unit,
    onDiscard: () -> Unit
) {
    PageHeading("Game play")
    Spacer(modifier = Modifier.height(8.dp))
    if (!loaded) {
        Text("Loading the season.", style = MaterialTheme.typography.bodyLarge)
        return
    }
    if (season == null) {
        Text(
            text = if (career.clubs.isEmpty()) "This career has no season: it was made before the club teams existed." else "The season could not be made.",
            style = MaterialTheme.typography.bodyLarge
        )
        return
    }
    Text(season.name, style = MaterialTheme.typography.titleMedium)
    val played = season.fixtures.count { it.result != null }
    Text("$played of ${season.fixtures.size} matches played.", style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(12.dp))
    val mine = season.fixtures.firstOrNull { it.result == null && CplSimulator.involves(it, season.userTeamId) }
    if (mine == null) {
        val champion = CareerSeason.champion(season)
        val position = CplData.standings(season).indexOfFirst { it.team.id == season.userTeamId } + 1
        Text(
            text = "Your season is complete." + (if (champion != null) " Champions: ${champion.name}." else "") +
                (if (position > 0) " ${career.club} finished ${ordinal(position)}." else ""),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "The state and domestic tournaments come in the next parts of career mode.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Text("Your next match", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(
            text = "Match ${mine.number}: ${fixtureText(season, mine)}. ${CareerSeason.dateText(CareerSeason.dateMillis(season, mine))}.",
            style = MaterialTheme.typography.bodyLarge
        )
        if (decision.reason.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(decision.reason, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (pending != null) {
            OptionButton("Resume my match", pending.summary(), !busy, true) { onResume() }
            Spacer(modifier = Modifier.height(8.dp))
            OptionButton("Discard the half-played match", "Start it again from the beginning instead.", !busy, false) { onDiscard() }
            Spacer(modifier = Modifier.height(12.dp))
        }
        OptionButton(
            title = "Play my match",
            description = if (decision.selected) "The selectors name the XI with you in it. After the toss you take over only your own batting and bowling; the game plays the rest of your team." else "You are not in the XI, so you cannot play this match.",
            enabled = !busy && decision.selected,
            emphasised = pending == null
        ) { onPlay() }
        Spacer(modifier = Modifier.height(12.dp))
        OptionButton("Simulate my match", "The other clubs' earlier matches are played too. You play if you are in the XI.", !busy, false) { onSimulateMine() }
    }
    if (mine != null || !CareerSeason.isComplete(season)) {
        Spacer(modifier = Modifier.height(12.dp))
        OptionButton("Simulate the whole season", "Every match that is left, yours too.", !busy && !CareerSeason.isComplete(season), false) { onSimulateAll() }
    }
    if (message.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

private fun ordinal(n: Int): String = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}

@Composable
private fun CalendarTab(state: TournamentState?, loaded: Boolean) {
    PageHeading("Calendar")
    Spacer(modifier = Modifier.height(8.dp))
    if (!loaded) {
        Text("Loading the season.", style = MaterialTheme.typography.bodyLarge)
        return
    }
    if (state == null) {
        Text("This career has no season.", style = MaterialTheme.typography.bodyLarge)
        return
    }
    Text("${state.name}: one match a week, every Saturday.", style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(12.dp))
    state.fixtures.groupBy { CareerSeason.roundOf(state, it) }.forEach { (round, fixtures) ->
        Text(
            text = "Round ${round + 1}: ${CareerSeason.dateText(CareerSeason.dateMillis(state, fixtures.first()))}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(4.dp))
        fixtures.forEach { f ->
            val yours = CplSimulator.involves(f, state.userTeamId)
            val result = f.result
            Text(
                text = "Match ${f.number}: ${fixtureText(state, f)}." + (if (yours) " Your match." else "") +
                    (if (result != null) " Result: ${result.summary}" else " Not played yet."),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** A row of tabs inside a page: the chosen one is filled and announced as selected. */
@Composable
private fun SubTabs(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            val chosen = i == selectedIndex
            val modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .semantics { role = Role.Tab; selected = chosen }
            if (chosen) Button(onClick = { onSelect(i) }, modifier = modifier) { Text(label) }
            else OutlinedButton(onClick = { onSelect(i) }, modifier = modifier) { Text(label) }
        }
    }
}

@Composable
private fun TableTab(
    state: TournamentState?,
    loaded: Boolean,
    tableTab: Int,
    onTableTab: (Int) -> Unit,
    statsTab: Int,
    onStatsTab: (Int) -> Unit
) {
    PageHeading("Points table")
    Spacer(modifier = Modifier.height(8.dp))
    if (!loaded) {
        Text("Loading the season.", style = MaterialTheme.typography.bodyLarge)
        return
    }
    if (state == null) {
        Text("This career has no season.", style = MaterialTheme.typography.bodyLarge)
        return
    }
    SubTabs(listOf("Points table", "Stats"), tableTab, onTableTab)
    Spacer(modifier = Modifier.height(12.dp))
    Text(state.name, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    if (tableTab == 0) {
        val rows = CplData.standings(state)
        val champion = CareerSeason.champion(state)
        if (champion != null) {
            Text("Champions: ${champion.name}.", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
        }
        rows.forEachIndexed { i, r ->
            val nrr = String.format(java.util.Locale.US, "%+.3f", r.netRunRate)
            Text(
                text = "${i + 1}. ${r.team.name}${if (r.team.id == state.userTeamId) " (your club)" else ""}: played ${r.played}, won ${r.won}, " +
                    "lost ${r.lost}, no result ${r.noResult}, points ${r.points}, net run rate $nrr",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    } else {
        SubTabs(listOf("All", "My team"), statsTab, onStatsTab)
        Spacer(modifier = Modifier.height(12.dp))
        val stats = if (statsTab == 0) state.playerStats.values else state.playerStats.values.filter { it.teamId == state.userTeamId }
        val categories = CplData.statCategories(stats, teamNameOf = { id -> CareerSeason.nameOf(state, id) }).filter { it.entries.isNotEmpty() }
        if (categories.isEmpty()) {
            Text("No matches have been played yet, so there are no statistics.", style = MaterialTheme.typography.bodyLarge)
        }
        categories.forEach { c ->
            Text(c.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(modifier = Modifier.height(4.dp))
            c.entries.forEachIndexed { i, e ->
                Text("${i + 1}. ${e.name}, ${e.teamName}: ${e.value}", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(4.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.cricketsim.logic.AbuDhabiT10
import com.cricketsim.logic.CareerProfile
import com.cricketsim.logic.CareerProgress
import com.cricketsim.logic.CareerSeason
import com.cricketsim.logic.CareerDomestic
import com.cricketsim.logic.CareerFinance
import com.cricketsim.logic.CareerInternational
import com.cricketsim.logic.BigBashLeague
import com.cricketsim.logic.CareerLeagues
import com.cricketsim.logic.CareerMove
import com.cricketsim.logic.CareerRecords
import com.cricketsim.logic.LeagueOffer
import com.cricketsim.logic.CareerSelection
import com.cricketsim.logic.CareerTables
import com.cricketsim.logic.CplData
import com.cricketsim.logic.CplSimulator
import com.cricketsim.logic.Team
import com.cricketsim.logic.TossResult
import com.cricketsim.logic.TossRoles
import com.cricketsim.logic.TournamentFixture
import com.cricketsim.logic.TournamentRules
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

private enum class HubTab(val label: String) { Home("Home"), Play("Game play"), Calendar("Calendar"), Table("Points table"), Career("Career") }

/** Where the player is in playing one of his club's matches (full screen: the tabs are hidden while a match is set up or played). */
private sealed interface CPlay {
    class PickXI(val comp: Int, val fixtureId: Int, val attempt: Int = 0) : CPlay
    class Toss(val comp: Int, val fixtureId: Int, val userXI: Team, val oppXI: Team) : CPlay
    class Match(val comp: Int, val fixtureId: Int, val userXI: Team, val oppXI: Team, val toss: TossResult, val resume: MatchSnapshot? = null) : CPlay
}

/**
 * The career's home page: four tabs along the bottom of the screen, next to the phone's own buttons.
 *  Home: the squad information and everything made so far (the club squad, the clubs of the state, the player profile).
 *  Game play: the player's next match (T20, 50-over or 3-day), to play or simulate; the selectors' decision.
 *  Calendar: the whole season, week by week, with dates, formats and results.
 *  Points table: pick a competition; its points table, and its statistics (All players, or My team), as sub-tabs.
 * The season is three leagues of the state's district clubs ([CareerSeason]), each saved in its own file per career.
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
    // The competitions of the current season: the three club leagues, or the state's domestic tournaments once he has been called up.
    val seasonKeys = career.seasonKeys ?: CareerSeason.CLUB_KEYS
    val seasonStores = remember(career.id, career.season, seasonKeys) {
        seasonKeys.map { TournamentSaveStore(context, CareerSeason.seasonFile(career.id, career.season, it)) }
    }
    val matchStore = remember(career.id) { MatchSaveStore(context, "career_match_${career.id}.json") }

    var tab by remember { mutableStateOf(HubTab.Home) }
    var seasons by remember { mutableStateOf<List<TournamentState>?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var pendingMatch by remember { mutableStateOf<MatchSnapshot?>(null) }
    var play by remember { mutableStateOf<CPlay?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var compTab by remember { mutableStateOf(0) }
    var tableTab by remember { mutableStateOf(0) }
    var statsTab by remember { mutableStateOf(0) }
    var xiProblem by remember { mutableStateOf<String?>(null) }
    // The selectors' decision for the player's next match (his rank in his role at the club, the difficulty and his form).
    // At the club: his rank among his club-mates. Once called up: his rank in the state squad. Read for the match that comes next.
    val sList = seasons
    val nextForDecision = if (sList != null) CareerSeason.nextMine(sList) else null
    val decision = if (sList != null && nextForDecision != null) CareerSelection.forState(career, sList[nextForDecision.first]) else CareerSelection.decide(career)

    LaunchedEffect(career.id, career.season, seasonKeys) {
        seasons = null
        loaded = false
        val existing = seasonStores.map { it.load() }
        val list = if (existing.isNotEmpty() && existing.all { it != null }) {
            existing.filterNotNull()
        } else {
            // The first time a season is opened: make its competitions (the club leagues, or the state's tournaments) and remember their files.
            val fresh = withContext(Dispatchers.Default) {
                when {
                    career.isInternational -> CareerInternational.newSeason(career)
                    career.isState -> CareerDomestic.newSeason(career)
                    else -> CareerSeason.newSeason(career)
                }
            }
            if (fresh != null) {
                val keys = fresh.map { CareerSeason.keyOf(it) }
                fresh.forEachIndexed { i, s -> TournamentSaveStore(context, CareerSeason.seasonFile(career.id, career.season, keys[i])).save(s) }
                if (keys != seasonKeys) onCareerChanged(career.copy(seasonKeys = keys))
            }
            fresh
        }
        seasons = list
        pendingMatch = matchStore.load()
        loaded = true
    }

    // Where the player stands on the road to the next level (worked out off the main thread: it builds the squads he is compared with).
    var pathway by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(career.id, career.season, career.level, career.matchesPlayed) {
        pathway = withContext(Dispatchers.Default) { try { CareerInternational.pathway(career) } catch (e: Exception) { emptyList() } }
    }

    // The crowd, as in the game's tournaments: from the toss to the end of the match.
    val crowdWanted = play != null
    val effectsOn = services?.settings?.soundEffects ?: false
    LaunchedEffect(crowdWanted, effectsOn) {
        val sound = services?.sound ?: return@LaunchedEffect
        if (!crowdWanted) sound.stopCrowdAmbience() else if (effectsOn) sound.startCrowdAmbience()
    }

    BackHandler(enabled = play == null) { if (tab != HubTab.Home) tab = HubTab.Home else onBack() }

    val saveSeasons: (List<TournamentState>) -> Unit = { list -> scope.launch { list.forEachIndexed { i, s -> seasonStores[i].save(s) } } }

    // Simulates off the main thread, then saves. includeMine plays the player's own next match too (the selectors' decision applies:
    // he is in the XI or left out), whole goes on to the end of the season. His form and ratings follow what he does.
    val simulate: (Boolean, Boolean) -> Unit = { includeMine, whole ->
        val current = seasons
        if (current != null && !busy) {
            busy = true
            message = "Simulating."
            scope.launch {
                val adv = withContext(Dispatchers.Default) { CareerSeason.advance(current, career, Random.Default, includeMine, whole) }
                seasons = adv.states
                adv.states.forEachIndexed { i, s -> seasonStores[i].save(s) }
                if (adv.career !== career) onCareerChanged(adv.career)
                busy = false
                message = adv.lines.joinToString("\n")
            }
        }
    }

    // Part twelve: retiring by his own choice (offered when the season is finished): the season is settled first, then the career ends.
    val retireNow: () -> Unit = {
        if (!busy && !career.retired) {
            busy = true
            scope.launch {
                val settled = CareerRecords.settleSeason(career, seasons.orEmpty())
                busy = false
                message = (if (settled.text.isEmpty()) "" else settled.text + " ") + "You have retired from cricket. Your career summary is on the Career tab."
                onCareerChanged(settled.career.copy(retired = true, retiredSeason = career.season))
            }
        }
    }

    // Part eleven: his place among the players of the game, worked out off the main thread (it builds the national teams).
    var rankLines by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(career.id, career.season, career.matchesPlayed) {
        rankLines = withContext(Dispatchers.Default) { try { CareerRecords.rankings(career) } catch (e: Exception) { emptyList() } }
    }

    // League offers (part ten): ask the franchises, sign for one league for the season, or turn them all down. A signed league's season is saved in its
    // own file first and then added to the season's competitions, so the season loads again with it.
    val checkOffers: () -> Unit = {
        if (!busy) {
            busy = true
            message = "The franchises are looking at you."
            scope.launch {
                val offers = withContext(Dispatchers.Default) { try { CareerLeagues.offersFor(career) } catch (e: Exception) { emptyList() } }
                busy = false
                message = if (offers.isEmpty()) CareerLeagues.noOfferReason(career) else "You have ${offers.size} league offers."
                onCareerChanged(career.copy(leagueOffers = offers, leagueOffersSeason = career.season))
            }
        }
    }
    val acceptOffer: (LeagueOffer) -> Unit = { offer ->
        val list = seasons
        if (!busy && list != null) {
            busy = true
            message = "Joining ${offer.teamName}."
            scope.launch {
                val startAt = (list.maxOfOrNull { it.createdAt } ?: System.currentTimeMillis()) + 10L * 7L * 24L * 3600L * 1000L
                val signed = withContext(Dispatchers.Default) { CareerLeagues.buildSeason(career, offer, startAt) }
                if (signed == null) {
                    busy = false
                    message = "That league could not be set up. Please try another offer."
                } else {
                    val leagueKey = CareerSeason.keyOf(signed)
                    val deal = CareerFinance.contract(career, offer)
                    TournamentSaveStore(context, CareerSeason.seasonFile(career.id, career.season, leagueKey)).save(signed)
                    busy = false
                    message = "You have signed for ${offer.teamName} in the ${offer.leagueName}. Its matches are in your calendar."
                    onCareerChanged(
                        career.copy(
                            seasonKeys = seasonKeys + leagueKey,
                            leagueOffers = null,
                            leagueDecidedSeason = career.season,
                            leagueContract = deal,
                            leagueCaptainTeamId = if (offer.captain) offer.teamId else null,
                            honours = if (offer.captain) (career.honours.orEmpty() + "Season ${career.season}: appointed captain of ${offer.teamName}.").takeLast(60) else career.honours,
                            lastLeagueId = offer.leagueId,
                            lastLeagueTeamId = offer.teamId,
                            leagueLine = "This season you also play for ${offer.teamName} in the ${offer.leagueName}. Your contract is worth ${CareerFinance.money(deal.toLong())}.${if (offer.captain) " You are the captain." else ""}"
                        )
                    )
                }
            }
        }
    }
    val declineOffers: () -> Unit = {
        message = "You turned the offers down."
        onCareerChanged(career.copy(leagueOffers = null, leagueDecidedSeason = career.season, leagueLine = "You turned down this season's league offers."))
    }

    // ---------------------------------------------------------------- playing a match (full screen)
    val flow = play
    val current = seasons
    if (flow != null && current != null) {
        when (flow) {
            is CPlay.PickXI -> {
                // The captain picks the XI himself.
                val back = { play = null }
                BackHandler(onBack = back)
                val state = current[flow.comp]
                val fixture = state.fixtures.find { it.id == flow.fixtureId }
                val userSquad = state.teams.find { it.id == state.userTeamId }
                val oppId = if (fixture?.homeId == state.userTeamId) fixture?.awayId else fixture?.homeId
                val oppSquad = state.teams.find { it.id == oppId }
                val stadium = fixture?.let { CplData.stadium(it.stadiumId) }
                val pid = CareerSeason.playerIdIn(state, career)
                if (fixture != null && userSquad != null && oppSquad != null && stadium != null && pid != null) {
                    val selection = remember(flow.fixtureId) { CplSimulator.squadForSelection(userSquad, state.tags, stadium) }
                    key(flow.attempt) {
                        PlayingXIScreen(
                            userTeam = selection,
                            onXIConfirmed = { xi ->
                                val problem = CplSimulator.xiProblem(xi, state.tags, state.tournamentId)
                                if (problem != null) {
                                    xiProblem = problem
                                    play = CPlay.PickXI(flow.comp, flow.fixtureId, flow.attempt + 1)
                                } else if (xi.players.none { it.id == pid }) {
                                    xiProblem = "You are the captain and a player: your own name must be in your XI."
                                    play = CPlay.PickXI(flow.comp, flow.fixtureId, flow.attempt + 1)
                                } else {
                                    play = CPlay.Toss(flow.comp, flow.fixtureId, xi, CplSimulator.pickXI(oppSquad, state.tags, stadium))
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
                val back = { play = if (CareerSeason.captainIn(career, current[flow.comp])) CPlay.PickXI(flow.comp, flow.fixtureId) else null }
                BackHandler(onBack = back)
                val state = current[flow.comp]
                val fixture = state.fixtures.find { it.id == flow.fixtureId }
                val userHome = fixture?.homeId == state.userTeamId
                if (CareerSeason.captainIn(career, state)) {
                    // The captain does the toss himself: he calls or flips, and chooses to bat or bowl.
                    TossScreen(
                        userTeam = flow.userXI,
                        opponentTeam = flow.oppXI,
                        userCalls = TossRoles.userCalls(userHome, !userHome),
                        onTossComplete = { toss -> play = CPlay.Match(flow.comp, flow.fixtureId, flow.userXI, flow.oppXI, toss) },
                        onBack = back
                    )
                } else {
                    // The captains do it: the screen only says who flipped, who called and who chose.
                    CareerTossScreen(
                        userXI = flow.userXI,
                        oppXI = flow.oppXI,
                        userIsHome = userHome,
                        onDone = { toss -> play = CPlay.Match(flow.comp, flow.fixtureId, flow.userXI, flow.oppXI, toss) },
                        onBack = back
                    )
                }
            }

            is CPlay.Match -> {
                val back = {
                    play = null
                    scope.launch { pendingMatch = matchStore.load() }
                    Unit
                }
                val state = current[flow.comp]
                val fixture = state.fixtures.find { it.id == flow.fixtureId }
                val stadium = flow.resume?.stadium ?: fixture?.let { CplData.stadium(it.stadiumId) }
                if (stadium != null) {
                    MatchScreen(
                        tournamentName = state.name,
                        format = AbuDhabiT10.formatFor(state.tournamentId),
                        rules = TournamentRules.rulesFor(state.tournamentId, fixture?.stage),
                        careerPlayerId = CareerSeason.playerIdIn(state, career),
                        careerCaptain = CareerSeason.captainIn(career, state),
                        powerSurge = state.tournamentId == BigBashLeague.TOURNAMENT_ID,
                        impact = CareerLeagues.impactFor(state, flow.userXI, flow.oppXI, CareerSeason.captainIn(career, state)),
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
                            val latestList = seasons ?: current
                            val latest = latestList[flow.comp]
                            val pid = CareerSeason.playerIdIn(latest, career)
                            val recorded = CplSimulator.recordPlayedMatch(latest, flow.fixtureId, finished, flow.userXI, flow.oppXI)
                            // What the match did to the player: his form and his ratings.
                            val progress = CareerProgress.afterMatch(
                                career,
                                if (pid != null) latest.playerStats[pid] else null,
                                if (pid != null) recorded.playerStats[pid] else null,
                                Random.Default,
                                CareerInternational.formatKeyOf(latest),
                                CareerLeagues.isLeague(latest)
                            )
                            val newList = latestList.mapIndexed { i, s ->
                                CareerSeason.withUserPlayer(if (i == flow.comp) recorded else s, progress.career)
                            }
                            seasons = newList
                            if (progress.career !== career) onCareerChanged(progress.career)
                            message = lastResultText(newList[flow.comp]) + " " + progress.text
                            saveSeasons(newList)
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
                HubTab.Home -> HomeTab(career, decision, pathway, { LeagueSection(career, busy, message, checkOffers, acceptOffer, declineOffers) }, onBack, onOpenSquad, onOpenClubs, onOpenProfile)
                HubTab.Play -> PlayTab(
                    career = career,
                    seasons = current,
                    loaded = loaded,
                    busy = busy,
                    message = message,
                    pending = pendingMatch,
                    decision = decision,
                    onPlay = {
                        val list = seasons
                        if (list != null && !busy) {
                            busy = true
                            message = "Getting ready."
                            scope.launch {
                                if (pendingMatch != null) {
                                    pendingMatch = null
                                    matchStore.clear()
                                }
                                // The other clubs' matches that come before yours (in that competition) are played first, so the table is up to date.
                                val adv = withContext(Dispatchers.Default) { CareerSeason.advance(list, career, Random.Default, false, false) }
                                seasons = adv.states
                                adv.states.forEachIndexed { i, s -> seasonStores[i].save(s) }
                                busy = false
                                message = ""
                                val next = CareerSeason.nextMine(adv.states)
                                if (next != null) {
                                    val ci = next.first
                                    val mine = next.second
                                    val updated = adv.states[ci]
                                    val pid = CareerSeason.playerIdIn(updated, career)
                                    val chosen = CareerSelection.forState(career, updated)
                                    val squad = updated.teams.find { it.id == updated.userTeamId }
                                    val oppId = if (mine.homeId == updated.userTeamId) mine.awayId else mine.homeId
                                    val oppSquad = updated.teams.find { it.id == oppId }
                                    val stadium = CplData.stadium(mine.stadiumId)
                                    if (!chosen.selected) {
                                        message = chosen.reason + " You can simulate the match instead."
                                    } else if (CareerSeason.captainIn(career, updated)) {
                                        // The captain picks his own XI, then does the toss.
                                        play = CPlay.PickXI(ci, mine.id)
                                    } else if (pid != null && squad != null && oppSquad != null && stadium != null) {
                                        // The selectors name the XI (you are in it, at your batting number): straight to the toss.
                                        val xi = CareerSelection.matchXI(squad, updated.tags, stadium, pid, career.battingNumber)
                                        play = CPlay.Toss(ci, mine.id, xi, CplSimulator.pickXI(oppSquad, updated.tags, stadium))
                                    }
                                }
                            }
                        }
                    },
                    onSimulateMine = { simulate(true, false) },
                    onSimulateAll = { simulate(true, true) },
                    onResume = {
                        val snapshot = pendingMatch
                        val list = seasons
                        if (snapshot != null && list != null) {
                            val ci = list.indexOfFirst { it.name == snapshot.state.tournamentName }.coerceAtLeast(0)
                            val fixtureId = CplSimulator.fixtureFor(list[ci], snapshot.state.userTeam.id, snapshot.state.opponentTeam.id)
                            if (fixtureId != null) {
                                play = CPlay.Match(ci, fixtureId, snapshot.state.userTeam, snapshot.state.opponentTeam, snapshot.toss, snapshot)
                            }
                        }
                    },
                    onDiscard = {
                        pendingMatch = null
                        scope.launch { matchStore.clear() }
                    },
                    onNextSeason = {
                        if (career.retired) {
                            message = "You have retired. Your career summary is on the Career tab."
                        } else if (!busy) {
                            busy = true
                            message = "The selectors are meeting."
                            scope.launch {
                                if (pendingMatch != null) {
                                    pendingMatch = null
                                    matchStore.clear()
                                }
                                // The season's honours and objectives are settled first (ratings, honours), then the selectors meet.
                                val settled = CareerRecords.settleSeason(career, seasons.orEmpty())
                                if (settled.career.retired) {
                                    // The ageing selectors' season ends his career: no new season.
                                    busy = false
                                    message = settled.text
                                    onCareerChanged(settled.career)
                                    return@launch
                                }
                                // A planned move abroad happens now: the player starts again at district level in the new country.
                                val moved = CareerMove.apply(settled.career.copy(represents = null), System.nanoTime())
                                val next = withContext(Dispatchers.Default) { CareerSeason.nextSeason(moved) }
                                next.states.forEachIndexed { i, s ->
                                    TournamentSaveStore(context, CareerSeason.seasonFile(career.id, next.career.season, next.keys[i])).save(s)
                                }
                                message = (if (settled.text.isEmpty()) "" else settled.text + " ") + CareerMove.text(settled.career, moved) + next.text
                                busy = false
                                onCareerChanged(next.career)
                            }
                        }
                    }
                )
                HubTab.Calendar -> CalendarTab(current, loaded)
                HubTab.Table -> TableTab(current, loaded, compTab, { compTab = it }, tableTab, { tableTab = it }, statsTab, { statsTab = it })
                HubTab.Career -> CareerRecordTab(career, current, rankLines, { focus -> onCareerChanged(career.copy(trainingFocus = focus)) }, { spec -> onCareerChanged(career.copy(specialism = spec)) }, { move -> onCareerChanged(career.copy(pendingMove = move)) }, retireNow)
            }
        }
    }
}

/** One line about the player's latest finished match in a competition. */
private fun lastResultText(state: TournamentState): String {
    val last = state.fixtures.lastOrNull { it.result != null && CplSimulator.involves(it, state.userTeamId) } ?: return ""
    val r = last.result ?: return ""
    return "Latest result, ${CareerSeason.competitionOf(state).label}: ${CareerSeason.nameOf(state, last.homeId)} v ${CareerSeason.nameOf(state, last.awayId)}. ${r.summary}"
}

private fun fixtureText(state: TournamentState, f: TournamentFixture): String {
    val ground = CplData.stadium(f.stadiumId)?.name ?: ""
    return "${CareerSeason.nameOf(state, f.homeId)} v ${CareerSeason.nameOf(state, f.awayId)}${if (ground.isNotEmpty()) " at $ground" else ""}"
}

@Composable
private fun HomeTab(
    career: CareerProfile,
    decision: CareerSelection.Decision,
    pathway: List<String>,
    leagueSection: @Composable () -> Unit,
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
    Spacer(modifier = Modifier.height(4.dp))
    Text(career.levelLine(), style = MaterialTheme.typography.bodyMedium)
    if (pathway.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text("Your pathway", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        pathway.forEach { line ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(line, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
    leagueSection()
    if (career.leadershipLine().isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(career.leadershipLine(), style = MaterialTheme.typography.bodyMedium)
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

private fun ordinal(n: Int): String = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}

@Composable
private fun PlayTab(
    career: CareerProfile,
    seasons: List<TournamentState>?,
    loaded: Boolean,
    busy: Boolean,
    message: String,
    pending: MatchSnapshot?,
    decision: CareerSelection.Decision,
    onPlay: () -> Unit,
    onSimulateMine: () -> Unit,
    onSimulateAll: () -> Unit,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onNextSeason: () -> Unit
) {
    PageHeading("Game play")
    Spacer(modifier = Modifier.height(8.dp))
    if (!loaded) {
        Text("Loading the season.", style = MaterialTheme.typography.bodyLarge)
        return
    }
    if (seasons == null) {
        Text(
            text = if (career.clubs.isEmpty()) "This career has no season: it was made before the club teams existed." else "The season could not be made.",
            style = MaterialTheme.typography.bodyLarge
        )
        return
    }
    val played = seasons.sumOf { s -> s.fixtures.count { it.result != null } }
    val total = seasons.sumOf { it.fixtures.size }
    Text("$played of $total matches played this season. ${career.levelLine()}", style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(12.dp))
    val next = CareerSeason.nextMine(seasons)
    if (next == null) {
        val allDone = seasons.all { CareerSeason.isComplete(it) }
        if (!allDone) {
            Text(
                text = "You have played all your matches so far. Some matches of the other teams are still to be played: simulate them to see whether your team goes through to a knockout match.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(12.dp))
            OptionButton("Simulate the rest of the season", "The other teams' matches, and any knockout match your team reaches.", !busy, true) { onSimulateAll() }
        } else {
            Text("Your season is complete.", style = MaterialTheme.typography.bodyLarge)
            seasons.forEach { s ->
                val champion = CareerSeason.champion(s)
                val position = CplData.standings(s).indexOfFirst { it.team.id == s.userTeamId } + 1
                Text(
                    text = (if (CareerInternational.isSeries(s)) CareerInternational.resultLine(s) else "${CareerSeason.competitionOf(s).label}: champions ${champion?.name ?: "not decided"}.") +
                        (if (CareerInternational.isInternational(s) || CareerLeagues.isLeague(s)) " " + CareerInternational.awardLine(s, career) else "") +
                        (if (position > 0 && CareerSeason.isClub(s)) " ${career.club} finished ${ordinal(position)}." else ""),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (career.isInternational) "The national selectors will decide whether you keep your place in the national squad for next season. If you are dropped you go back to state cricket."
                else if (career.isState) "The national selectors look at you first, then the state selectors decide whether you keep your place in the squad for next season. If you are dropped you go back to district cricket."
                else "The state selectors will look at your ratings and your form and decide whether to call you up to your state squad. Either way, a new season begins and you are a year older.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            OptionButton("Start next season", "The selectors decide, then the new season's competitions are made.", !busy, true) { onNextSeason() }
        }
    } else {
        val state = seasons[next.first]
        val mine = next.second
        val comp = CareerSeason.competitionOf(state)
        Text("Your next match", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(
            text = "${comp.label}, match ${mine.number}: ${fixtureText(state, mine)}. ${CareerSeason.whenText(state, mine)}.",
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
            description = if (next != null && CareerSeason.captainIn(career, seasons[next.first])) "You are the captain: you pick the XI and do the toss, choose the bowlers, set the field and decide the batting order. You face and bowl your own balls; the game plays the rest."
            else if (decision.selected) "The selectors name the XI with you in it. The captains do the toss. You take over only your own batting and bowling; the game plays the rest of your team." else "You are not in the XI, so you cannot play this match.",
            enabled = !busy && decision.selected,
            emphasised = pending == null
        ) { onPlay() }
        Spacer(modifier = Modifier.height(12.dp))
        OptionButton("Simulate my match", "The other clubs' earlier matches are played too. You play if you are in the XI.", !busy, false) { onSimulateMine() }
        Spacer(modifier = Modifier.height(12.dp))
        OptionButton("Simulate the whole season", "Every match that is left, in every competition.", !busy, false) { onSimulateAll() }
    }
    if (message.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun CalendarTab(seasons: List<TournamentState>?, loaded: Boolean) {
    PageHeading("Calendar")
    Spacer(modifier = Modifier.height(8.dp))
    if (!loaded) {
        Text("Loading the season.", style = MaterialTheme.typography.bodyLarge)
        return
    }
    if (seasons == null) {
        Text("This career has no season.", style = MaterialTheme.typography.bodyLarge)
        return
    }
    Text(
        text = "In the club leagues every match is listed; in the state tournaments, your team's matches. One match a week.",
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.height(12.dp))
    val entries = seasons.flatMap { s ->
        s.fixtures
            .filter { it.homeId != null && it.awayId != null && (CareerSeason.isClub(s) || CplSimulator.involves(it, s.userTeamId)) }
            .map { f -> Pair(s, f) }
    }.sortedWith(compareBy({ CareerSeason.dateMillis(it.first, it.second) }, { it.second.number }))
    entries.groupBy { (s, f) -> CareerSeason.dateMillis(s, f) }.forEach { (_, list) ->
        val (firstState, firstFixture) = list.first()
        Text(
            text = CareerSeason.whenText(firstState, firstFixture),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(4.dp))
        list.forEach { (s, f) ->
            val yours = CplSimulator.involves(f, s.userTeamId)
            val result = f.result
            Text(
                text = "${CareerSeason.competitionOf(s).label}${if (f.stage != CplData.LEAGUE) ", ${f.stage}" else ""}, match ${f.number}: ${fixtureText(s, f)}." +
                    (if (yours) " Your match." else "") + (if (result != null) " Result: ${result.summary}" else " Not played yet."),
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
    seasons: List<TournamentState>?,
    loaded: Boolean,
    compTab: Int,
    onCompTab: (Int) -> Unit,
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
    if (seasons == null || seasons.isEmpty()) {
        Text("This career has no season.", style = MaterialTheme.typography.bodyLarge)
        return
    }
    // Which competition: each has its own table and its own statistics.
    SubTabs(seasons.map { CareerSeason.competitionOf(it).shortLabel }, compTab.coerceIn(0, seasons.size - 1), onCompTab)
    Spacer(modifier = Modifier.height(8.dp))
    val state = seasons[compTab.coerceIn(0, seasons.size - 1)]
    SubTabs(listOf("Points table", "Stats"), tableTab, onTableTab)
    Spacer(modifier = Modifier.height(12.dp))
    Text(state.name, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    if (tableTab == 0) {
        val champion = CareerSeason.champion(state)
        if (champion != null) {
            Text("Champions: ${champion.name}.", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
        }
        val tables = CareerTables.tables(state)
        if (tables.isEmpty()) Text("No matches have been played in this competition yet, so there is no table.", style = MaterialTheme.typography.bodyLarge)
        tables.forEach { table ->
            if (tables.size > 1) {
                Text(table.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Spacer(modifier = Modifier.height(4.dp))
            }
            table.rows.forEach { (teamId, text) ->
                Text(text + if (teamId == state.userTeamId) " (your team)" else "", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    } else {
        SubTabs(listOf("All", "My team"), statsTab, onStatsTab)
        Spacer(modifier = Modifier.height(12.dp))
        val stats = if (statsTab == 0) state.playerStats.values else state.playerStats.values.filter { it.teamId == state.userTeamId }
        val categories = CplData.statCategories(stats, teamNameOf = { id -> CareerSeason.nameOf(state, id) }).filter { it.entries.isNotEmpty() }
        if (categories.isEmpty()) {
            Text("No matches have been played in this competition yet, so there are no statistics.", style = MaterialTheme.typography.bodyLarge)
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

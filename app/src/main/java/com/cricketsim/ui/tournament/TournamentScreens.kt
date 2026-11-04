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
import com.cricketsim.logic.AbuDhabiT10
import com.cricketsim.logic.BigBashLeague
import com.cricketsim.logic.InternationalLeagueT20
import com.cricketsim.logic.IplSeason
import com.cricketsim.logic.LankaPremierLeague
import com.cricketsim.logic.MajorLeagueCricket
import com.cricketsim.logic.DomesticFc
import com.cricketsim.logic.AusDomestic
import com.cricketsim.logic.NzDomestic
import com.cricketsim.logic.PakDomestic
import com.cricketsim.logic.WiDomestic
import com.cricketsim.logic.SaDomestic
import com.cricketsim.logic.CountyCricket
import com.cricketsim.logic.DomesticWhiteBall
import com.cricketsim.logic.Ranji
import com.cricketsim.logic.Sa20
import com.cricketsim.logic.AsiaCup
import com.cricketsim.logic.AsianGames
import com.cricketsim.logic.CustomTournaments
import com.cricketsim.logic.Customisation
import com.cricketsim.logic.GameTournaments
import com.cricketsim.logic.TournamentRules
import com.cricketsim.logic.ChampionsTrophy
import com.cricketsim.logic.T20WorldCup
import com.cricketsim.logic.ImpactPlayer
import com.cricketsim.logic.ImpactSetup
import com.cricketsim.logic.CplSimulator
import com.cricketsim.logic.MatchFormat
import com.cricketsim.logic.PlayerProfiles
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
import com.cricketsim.ui.ipl.IplFlow
import com.cricketsim.ui.match.ImpactSelectionScreen
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
    /** The list of international tournaments (the T20 World Cup, the Champions Trophy). */
    object International : TPage
    /** The custom tournaments the player designed (Tournament > Custom tournaments). */
    object CustomList : TPage
    /** The domestic tournaments: the Ranji Trophy, the Duleep Trophy and the Irani Cup. */
    object Domestic : TPage
    object Cpl : TPage
    /** The Abu Dhabi T10 page (the same page as the CPL's, for the T10 tournament). */
    object Adt : TPage
    object TeamSelect : TPage
    data class Squad(val teamId: String) : TPage
    object Hub : TPage
    object Celebration : TPage
    object Ipl : TPage
    /** IPL only: the toss comes first, before the Playing XI. */
    data class TossFirst(val fixtureId: Int) : TPage
    data class PickXI(val fixtureId: Int, val attempt: Int = 0, val toss: TossResult? = null) : TPage
    /** IPL only: naming the five Impact Player substitutes, between the Playing XI and the toss. */
    data class Impact(val fixtureId: Int, val userXI: Team, val opponentXI: Team, val toss: TossResult? = null) : TPage
    data class Toss(val fixtureId: Int, val userXI: Team, val opponentXI: Team, val impact: ImpactSetup? = null) : TPage
    data class PlayMatch(
        val fixtureId: Int,
        val userXI: Team,
        val opponentXI: Team,
        val toss: TossResult,
        /** Set when picking a saved match back up. */
        val resume: MatchSnapshot? = null,
        /** IPL: the Impact Player substitutes of both sides (a resumed match carries them in its own state). */
        val impact: ImpactSetup? = null
    ) : TPage
    data class Scorecard(val fixtureId: Int) : TPage
}

@Composable
fun TournamentFlow(onExit: () -> Unit) {
    val context = LocalContext.current
    // Two tournaments share this whole flow: the CPL and the IPL season. Each has its own saved
    // tournament and its own part-played-match slot, so neither can touch the other (or the normal Resume match).
    var kind by remember { mutableStateOf("cpl") } // "cpl" or "ipl"
    var iplSeason by remember { mutableStateOf(false) } // is there a saved IPL season?
    var adtSaved by remember { mutableStateOf(false) } // is there a saved Abu Dhabi T10 tournament?
    var lplSaved by remember { mutableStateOf(false) } // is there a saved Lanka Premier League tournament?
    var iltSaved by remember { mutableStateOf(false) } // is there a saved International League T20 tournament?
    var bblSaved by remember { mutableStateOf(false) } // is there a saved Big Bash League tournament?
    var mlcSaved by remember { mutableStateOf(false) } // is there a saved Major League Cricket tournament?
    var saSaved by remember { mutableStateOf(false) } // is there a saved SA20 tournament?
    val cplStore = remember { TournamentSaveStore(context) }
    val iplStore = remember { TournamentSaveStore(context, "saved_tournament_ipl2027.json") }
    val cplMatchStore = remember { MatchSaveStore(context, "saved_tournament_match.json") }
    val iplMatchStore = remember { MatchSaveStore(context, "saved_ipl_match.json") }
    val adtStore = remember { TournamentSaveStore(context, "saved_tournament_adt10_2026.json") }
    val adtMatchStore = remember { MatchSaveStore(context, "saved_adt10_match.json") }
    val lplStore = remember { TournamentSaveStore(context, "saved_tournament_lpl2026.json") }
    val lplMatchStore = remember { MatchSaveStore(context, "saved_lpl_match.json") }
    val iltStore = remember { TournamentSaveStore(context, "saved_tournament_ilt20_2026.json") }
    val iltMatchStore = remember { MatchSaveStore(context, "saved_ilt20_match.json") }
    val bblStore = remember { TournamentSaveStore(context, "saved_tournament_bbl2026_27.json") }
    val bblMatchStore = remember { MatchSaveStore(context, "saved_bbl_match.json") }
    val saStore = remember { TournamentSaveStore(context, "saved_tournament_sa20_2027.json") }
    val saMatchStore = remember { MatchSaveStore(context, "saved_sa20_match.json") }
    val ranjiStore = remember { TournamentSaveStore(context, "saved_tournament_ranji_2026_27.json") }
    val ranjiMatchStore = remember { MatchSaveStore(context, "saved_ranji_match.json") }
    val duleepStore = remember { TournamentSaveStore(context, "saved_tournament_duleep_2026.json") }
    val duleepMatchStore = remember { MatchSaveStore(context, "saved_duleep_match.json") }
    val iraniStore = remember { TournamentSaveStore(context, "saved_tournament_irani_2026.json") }
    val iraniMatchStore = remember { MatchSaveStore(context, "saved_irani_match.json") }
    val smatStore = remember { TournamentSaveStore(context, "saved_tournament_smat_2026_27.json") }
    val smatMatchStore = remember { MatchSaveStore(context, "saved_smat_match.json") }
    val vhtStore = remember { TournamentSaveStore(context, "saved_tournament_vht_2026_27.json") }
    val vhtMatchStore = remember { MatchSaveStore(context, "saved_vht_match.json") }
    val odcStore = remember { TournamentSaveStore(context, "saved_tournament_odc_2027.json") }
    val odcMatchStore = remember { MatchSaveStore(context, "saved_odc_match.json") }
    val blastStore = remember { TournamentSaveStore(context, "saved_tournament_blast_2027.json") }
    val blastMatchStore = remember { MatchSaveStore(context, "saved_blast_match.json") }
    val ccStore = remember { TournamentSaveStore(context, "saved_tournament_cc_2027.json") }
    val ccMatchStore = remember { MatchSaveStore(context, "saved_cc_match.json") }
    val shieldStore = remember { TournamentSaveStore(context, "saved_tournament_shield2026.json") }
    val shieldMatchStore = remember { MatchSaveStore(context, "saved_shield_match.json") }
    val audcStore = remember { TournamentSaveStore(context, "saved_tournament_audc2026.json") }
    val audcMatchStore = remember { MatchSaveStore(context, "saved_audc_match.json") }
    val sa4dStore = remember { TournamentSaveStore(context, "saved_tournament_sa4d2026.json") }
    val sa4dMatchStore = remember { MatchSaveStore(context, "saved_sa4d_match.json") }
    val sa50Store = remember { TournamentSaveStore(context, "saved_tournament_sa50c2026.json") }
    val sa50MatchStore = remember { MatchSaveStore(context, "saved_sa50c_match.json") }
    val pro20Store = remember { TournamentSaveStore(context, "saved_tournament_pro20c2026.json") }
    val pro20MatchStore = remember { MatchSaveStore(context, "saved_pro20c_match.json") }
    val plunketStore = remember { TournamentSaveStore(context, "saved_tournament_plunket2026.json") }
    val plunketMatchStore = remember { MatchSaveStore(context, "saved_plunket_match.json") }
    val fordStore = remember { TournamentSaveStore(context, "saved_tournament_ford2026.json") }
    val fordMatchStore = remember { MatchSaveStore(context, "saved_ford_match.json") }
    val smashStore = remember { TournamentSaveStore(context, "saved_tournament_smash2026.json") }
    val smashMatchStore = remember { MatchSaveStore(context, "saved_smash_match.json") }
    val wi4dStore = remember { TournamentSaveStore(context, "saved_tournament_wi4d2027.json") }
    val wi4dMatchStore = remember { MatchSaveStore(context, "saved_wi4d_match.json") }
    val wi50Store = remember { TournamentSaveStore(context, "saved_tournament_wi50c2026.json") }
    val wi50MatchStore = remember { MatchSaveStore(context, "saved_wi50c_match.json") }
    val qeagStore = remember { TournamentSaveStore(context, "saved_tournament_qeag2026.json") }
    val qeagMatchStore = remember { MatchSaveStore(context, "saved_qeag_match.json") }
    val qeasStore = remember { TournamentSaveStore(context, "saved_tournament_qeas2026.json") }
    val qeasMatchStore = remember { MatchSaveStore(context, "saved_qeas_match.json") }
    val mlcStore = remember { TournamentSaveStore(context, "saved_tournament_mlc2026.json") }
    val mlcMatchStore = remember { MatchSaveStore(context, "saved_mlc_match.json") }
    val wcStore = remember { TournamentSaveStore(context, "saved_tournament_t20wc2026.json") }
    val wcMatchStore = remember { MatchSaveStore(context, "saved_t20wc_match.json") }
    val ctStore = remember { TournamentSaveStore(context, "saved_tournament_ct2025.json") }
    val ctMatchStore = remember { MatchSaveStore(context, "saved_ct2025_match.json") }
    val acStore = remember { TournamentSaveStore(context, "saved_tournament_asia2025.json") }
    val acMatchStore = remember { MatchSaveStore(context, "saved_asia2025_match.json") }
    val agStore = remember { TournamentSaveStore(context, "saved_tournament_asiangames2026.json") }
    val agMatchStore = remember { MatchSaveStore(context, "saved_asiangames2026_match.json") }
    // A custom tournament's kind is "custom:<id>"; each has its own saved tournament and match.
    val isCustomKind = kind.startsWith("custom:")
    val customId = GameTournaments.idForKind(kind)
    val customStore = remember(customId) { TournamentSaveStore(context, "saved_tournament_$customId.json") }
    val customMatchStore = remember(customId) { MatchSaveStore(context, "saved_${customId}_match.json") }
    val store = when (kind) { "ipl" -> iplStore; "adt10" -> adtStore; "lpl" -> lplStore; "ilt" -> iltStore; "bbl" -> bblStore; "mlc" -> mlcStore; "sa20" -> saStore; "ranji2027" -> ranjiStore; "duleep2026" -> duleepStore; "irani2026" -> iraniStore; "smat2026" -> smatStore; "vht2026" -> vhtStore; "odc2027" -> odcStore; "blast2027" -> blastStore; "cc2027" -> ccStore; "shield2026" -> shieldStore; "audc2026" -> audcStore; "sa4d2026" -> sa4dStore; "sa50c2026" -> sa50Store; "pro20c2026" -> pro20Store; "plunket2026" -> plunketStore; "ford2026" -> fordStore; "smash2026" -> smashStore; "wi4d2027" -> wi4dStore; "wi50c2026" -> wi50Store; "qeag2026" -> qeagStore; "qeas2026" -> qeasStore; "wc2026" -> wcStore; "ct2025" -> ctStore; "asia2025" -> acStore; "ag2026" -> agStore; else -> if (isCustomKind) customStore else cplStore }
    val matchStore = when (kind) { "ipl" -> iplMatchStore; "adt10" -> adtMatchStore; "lpl" -> lplMatchStore; "ilt" -> iltMatchStore; "bbl" -> bblMatchStore; "mlc" -> mlcMatchStore; "sa20" -> saMatchStore; "ranji2027" -> ranjiMatchStore; "duleep2026" -> duleepMatchStore; "irani2026" -> iraniMatchStore; "smat2026" -> smatMatchStore; "vht2026" -> vhtMatchStore; "odc2027" -> odcMatchStore; "blast2027" -> blastMatchStore; "cc2027" -> ccMatchStore; "shield2026" -> shieldMatchStore; "audc2026" -> audcMatchStore; "sa4d2026" -> sa4dMatchStore; "sa50c2026" -> sa50MatchStore; "pro20c2026" -> pro20MatchStore; "plunket2026" -> plunketMatchStore; "ford2026" -> fordMatchStore; "smash2026" -> smashMatchStore; "wi4d2027" -> wi4dMatchStore; "wi50c2026" -> wi50MatchStore; "qeag2026" -> qeagMatchStore; "qeas2026" -> qeasMatchStore; "wc2026" -> wcMatchStore; "ct2025" -> ctMatchStore; "asia2025" -> acMatchStore; "ag2026" -> agMatchStore; else -> if (isCustomKind) customMatchStore else cplMatchStore }
    val scope = rememberCoroutineScope()
    val cplBuilt = remember { CplData.buildTeams() }
    val adtBuilt = remember { AbuDhabiT10.buildTeams() }
    val lplBuilt = remember { LankaPremierLeague.buildTeams() }
    // The squads of the tournament in hand (team choice, squads, tags).
    val iltBuilt = remember { InternationalLeagueT20.buildTeams() }
    val bblBuilt = remember { BigBashLeague.buildTeams() }
    val mlcBuilt = remember { MajorLeagueCricket.buildTeams() }
    val saBuilt = remember { Sa20.buildTeams() }
    // The Ranji squads are only built when the Ranji Trophy is opened (they are the biggest).
    val ranjiBuilt = remember { lazy { Ranji.buildTeams() } }
    val duleepBuilt = remember { lazy { DomesticFc.buildDuleep() } }
    val iraniBuilt = remember { lazy { DomesticFc.buildIrani() } }
    // The two white-ball tournaments share their 38 teams (built only when one is opened).
    val whiteBallBuilt = remember { lazy { DomesticWhiteBall.buildTeams() } }
    // The two English county tournaments share their 18 counties (built only when one is opened).
    val countyBuilt = remember { lazy { CountyCricket.buildTeams() } }
    // The two Australian tournaments share their six states (built only when one is opened).
    val ausBuilt = remember { lazy { AusDomestic.buildTeams() } }
    // The three South African tournaments share their 16 teams (built only when one is opened).
    val saDomBuilt = remember { lazy { SaDomestic.buildTeams() } }
    // The three New Zealand tournaments share their six associations (built only when one is opened).
    val nzBuilt = remember { lazy { NzDomestic.buildTeams() } }
    // The two West Indies tournaments share their six teams (built only when one is opened).
    val wiDomBuilt = remember { lazy { WiDomestic.buildTeams() } }
    // The two Pakistani first-class divisions share their 20 regional teams (built only when one is opened).
    val pakDomBuilt = remember { lazy { PakDomestic.buildTeams() } }
    val wcBuilt = remember { T20WorldCup.buildTeams() }
    val ctBuilt = remember { ChampionsTrophy.buildTeams() }
    val acBuilt = remember { AsiaCup.buildTeams() }
    val agBuilt = remember { AsianGames.buildTeams() }
    val customBuilt = remember(customId) { if (isCustomKind) CustomTournaments.buildTeams(customId) else cplBuilt }
    val built = when (kind) { "adt10" -> adtBuilt; "lpl" -> lplBuilt; "ilt" -> iltBuilt; "bbl" -> bblBuilt; "mlc" -> mlcBuilt; "sa20" -> saBuilt; "ranji2027" -> ranjiBuilt.value; "duleep2026" -> duleepBuilt.value; "irani2026" -> iraniBuilt.value; "smat2026", "vht2026" -> whiteBallBuilt.value; "odc2027", "blast2027", "cc2027" -> countyBuilt.value; "shield2026", "audc2026" -> ausBuilt.value; "sa4d2026", "sa50c2026", "pro20c2026" -> saDomBuilt.value; "plunket2026", "ford2026", "smash2026" -> nzBuilt.value; "wi4d2027", "wi50c2026" -> wiDomBuilt.value; "qeag2026" -> PakDomestic.teamsFor(PakDomestic.GOLD_ID, pakDomBuilt.value); "qeas2026" -> PakDomestic.teamsFor(PakDomestic.SILVER_ID, pakDomBuilt.value); "wc2026" -> wcBuilt; "ct2025" -> ctBuilt; "asia2025" -> acBuilt; "ag2026" -> agBuilt; else -> if (isCustomKind) customBuilt else cplBuilt }

    var page by remember { mutableStateOf<TPage>(TPage.Menu) }
    // The "menu open" sound for each menu-type page (not the toss, the match, the celebration or the IPL
    // pages, which play it themselves).
    val menuSoundServices = LocalGameServices.current
    LaunchedEffect(page::class) {
        val opened = page
        if (opened !is TPage.PlayMatch && opened !is TPage.Toss && opened !is TPage.TossFirst && opened !is TPage.Celebration && opened !is TPage.Ipl) {
            menuSoundServices?.sound?.playMenuOpen()
        }
    }
    // THE CROWD STARTS AT THE TOSS, as in a normal match (MainActivity): from the toss through the Playing XI and Impact
    // Player screens that follow it (the IPL holds the toss first) to the end of the match. The match screen's own start
    // request is then a no-op, and it stops the crowd when the match ends or is left. Backing out of the toss stops it.
    val shownPage = page
    val crowdWanted = shownPage is TPage.Toss || shownPage is TPage.TossFirst || shownPage is TPage.PlayMatch ||
        shownPage is TPage.Impact || (shownPage is TPage.PickXI && shownPage.toss != null)
    val crowdEffectsOn = menuSoundServices?.settings?.soundEffects ?: false
    LaunchedEffect(crowdWanted, crowdEffectsOn) {
        val sound = menuSoundServices?.sound ?: return@LaunchedEffect
        if (!crowdWanted) {
            sound.stopCrowdAmbience()
        } else if (crowdEffectsOn) {
            sound.startCrowdAmbience()
        }
    }
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
        iplSeason = iplStore.load() != null
        adtSaved = adtStore.load() != null
        lplSaved = lplStore.load() != null
        iltSaved = iltStore.load() != null
        bblSaved = bblStore.load() != null
        mlcSaved = mlcStore.load() != null
        saSaved = saStore.load() != null
    }
    LaunchedEffect(kind) {
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
            MenuPage(
                onLeague = { page = TPage.Leagues },
                onInternational = { page = TPage.International },
                onCustom = { page = TPage.CustomList },
                onDomestic = { page = TPage.Domestic },
                onBack = onExit
            )
        }
        is TPage.CustomList -> {
            val back = { page = TPage.Menu }
            BackHandler(onBack = back)
            CustomTournamentListPage(
                defs = remember { Customisation.customTournaments() },
                onOpen = { id ->
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "custom:$id"
                    page = TPage.Adt
                },
                onBack = back
            )
        }
        is TPage.International -> {
            val back = { page = TPage.Menu }
            BackHandler(onBack = back)
            InternationalPage(
                onWorldCup = {
                    // Each international tournament has its own saved tournament and match slot: load them for this kind.
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "wc2026"
                    page = TPage.Adt
                },
                onChampionsTrophy = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "ct2025"
                    page = TPage.Adt
                },
                onAsiaCup = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "asia2025"
                    page = TPage.Adt
                },
                onAsianGames = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "ag2026"
                    page = TPage.Adt
                },
                onBack = back
            )
        }
        is TPage.Domestic -> {
            val back = { page = TPage.Menu }
            BackHandler(onBack = back)
            DomesticPage(
                onRanji = {
                    // Each domestic tournament has its own saved tournament and match slot.
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "ranji2027"
                    page = TPage.Adt
                },
                onDuleep = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "duleep2026"
                    page = TPage.Adt
                },
                onSmat = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "smat2026"
                    page = TPage.Adt
                },
                onOdc = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "odc2027"
                    page = TPage.Adt
                },
                onQeag = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "qeag2026"
                    page = TPage.Adt
                },
                onQeas = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "qeas2026"
                    page = TPage.Adt
                },
                onWi4d = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "wi4d2027"
                    page = TPage.Adt
                },
                onWi50 = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "wi50c2026"
                    page = TPage.Adt
                },
                onPlunket = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "plunket2026"
                    page = TPage.Adt
                },
                onFord = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "ford2026"
                    page = TPage.Adt
                },
                onSmash = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "smash2026"
                    page = TPage.Adt
                },
                onSa4d = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "sa4d2026"
                    page = TPage.Adt
                },
                onSa50 = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "sa50c2026"
                    page = TPage.Adt
                },
                onPro20 = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "pro20c2026"
                    page = TPage.Adt
                },
                onShield = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "shield2026"
                    page = TPage.Adt
                },
                onAudc = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "audc2026"
                    page = TPage.Adt
                },
                onCc = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "cc2027"
                    page = TPage.Adt
                },
                onBlast = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "blast2027"
                    page = TPage.Adt
                },
                onVht = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "vht2026"
                    page = TPage.Adt
                },
                onIrani = {
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "irani2026"
                    page = TPage.Adt
                },
                onBack = back
            )
        }
        is TPage.Leagues -> {
            val back = { page = TPage.Menu }
            BackHandler(onBack = back)
            LeaguesPage(
                hasSaved = saved != null,
                adtSaved = adtSaved,
                lplSaved = lplSaved,
                iltSaved = iltSaved,
                bblSaved = bblSaved,
                mlcSaved = mlcSaved,
                saSaved = saSaved,
                onCpl = { page = TPage.Cpl },
                onAdt = {
                    // The Abu Dhabi T10 has its own saved tournament and match slot: load them for this kind.
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "adt10"
                    page = TPage.Adt
                },
                onLpl = {
                    // The Lanka Premier League has its own saved tournament and match slot too.
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "lpl"
                    page = TPage.Adt
                },
                onIlt = {
                    // The International League T20 has its own saved tournament and match slot too.
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "ilt"
                    page = TPage.Adt
                },
                onBbl = {
                    // The Big Bash League has its own saved tournament and match slot too.
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "bbl"
                    page = TPage.Adt
                },
                onMlc = {
                    // Major League Cricket has its own saved tournament and match slot too.
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "mlc"
                    page = TPage.Adt
                },
                onSa20 = {
                    // The SA20 has its own saved tournament and match slot too.
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "sa20"
                    page = TPage.Adt
                },
                onIpl = { page = TPage.Ipl },
                onBack = back
            )
        }
        is TPage.Ipl -> IplFlow(
            onExit = {
                // Back to the league list: the CPL is the tournament in hand again.
                if (kind != "cpl") {
                    saved = null
                    pendingMatch = null
                    kind = "cpl"
                }
                page = TPage.Leagues
            },
            seasonSaved = iplSeason,
            onStartSeason = { auction ->
                val season = IplSeason.newTournament(auction, Random.Default)
                scope.launch {
                    iplStore.save(season)
                    iplMatchStore.clear()
                    iplSeason = true
                    saved = season
                    pendingMatch = null
                    kind = "ipl"
                    page = TPage.Hub
                }
            },
            onOpenSeason = {
                scope.launch {
                    val season = iplStore.load()
                    if (season != null) {
                        saved = season
                        pendingMatch = null
                        kind = "ipl"
                        page = TPage.Hub
                    }
                }
            },
            onDeleteSeason = {
                scope.launch {
                    iplStore.delete()
                    iplMatchStore.clear()
                    iplSeason = false
                }
            }
        )
        is TPage.Cpl, is TPage.Adt -> {
            val isAdt = kind == "adt10"
            val isLpl = kind == "lpl"
            val isIlt = kind == "ilt"
            val isBbl = kind == "bbl"
            val isMlc = kind == "mlc"
            val isSa = kind == "sa20"
            val isWc = kind == "wc2026"
            val isCt = kind == "ct2025"
            val isAc = kind == "asia2025"
            val isAg = kind == "ag2026"
            val isRanji = kind == "ranji2027"
            val isDuleep = kind == "duleep2026"
            val isIrani = kind == "irani2026"
            val isSmat = kind == "smat2026"
            val isVht = kind == "vht2026"
            val isOdc = kind == "odc2027"
            val isBlast = kind == "blast2027"
            val isCc = kind == "cc2027"
            val isQeag = kind == "qeag2026"
            val isQeas = kind == "qeas2026"
            val isWi4d = kind == "wi4d2027"
            val isWi50 = kind == "wi50c2026"
            val isPlunket = kind == "plunket2026"
            val isFord = kind == "ford2026"
            val isSmash = kind == "smash2026"
            val isSa4d = kind == "sa4d2026"
            val isSa50 = kind == "sa50c2026"
            val isPro20 = kind == "pro20c2026"
            val isShield = kind == "shield2026"
            val isAudc = kind == "audc2026"
            val isDomestic = isRanji || isDuleep || isIrani || isSmat || isVht || isOdc || isBlast || isCc || isShield || isAudc || isSa4d || isSa50 || isPro20 || isPlunket || isFord || isSmash || isWi4d || isWi50 || isQeag || isQeas
            val tournamentLabel = when {
                isAdt -> AbuDhabiT10.TOURNAMENT_NAME
                isLpl -> LankaPremierLeague.TOURNAMENT_NAME
                isIlt -> InternationalLeagueT20.TOURNAMENT_NAME
                isBbl -> BigBashLeague.TOURNAMENT_NAME
                isMlc -> MajorLeagueCricket.TOURNAMENT_NAME
                isSa -> Sa20.TOURNAMENT_NAME
                isWc -> T20WorldCup.TOURNAMENT_NAME
                isCt -> ChampionsTrophy.TOURNAMENT_NAME
                isAc -> AsiaCup.TOURNAMENT_NAME
                isAg -> AsianGames.TOURNAMENT_NAME
                isRanji -> Ranji.TOURNAMENT_NAME
                isDuleep -> DomesticFc.DULEEP_NAME
                isIrani -> DomesticFc.IRANI_NAME
                isSmat -> DomesticWhiteBall.SMAT_NAME
                isVht -> DomesticWhiteBall.VHT_NAME
                isOdc -> CountyCricket.ODC_NAME
                isBlast -> CountyCricket.BLAST_NAME
                isCc -> CountyCricket.CC_NAME
                isShield -> AusDomestic.SHIELD_NAME
                isAudc -> AusDomestic.ODC_NAME
                isSa4d -> SaDomestic.FOURDAY_NAME
                isSa50 -> SaDomestic.ODC_NAME
                isPro20 -> SaDomestic.T20_NAME
                isPlunket -> NzDomestic.PLUNKET_NAME
                isFord -> NzDomestic.FORD_NAME
                isSmash -> NzDomestic.SMASH_NAME
                isWi4d -> WiDomestic.FOURDAY_NAME
                isWi50 -> WiDomestic.ODC_NAME
                isQeag -> PakDomestic.GOLD_NAME
                isQeas -> PakDomestic.SILVER_NAME
                isCustomKind -> CustomTournaments.nameOf(customId)
                else -> "CPL 2026"
            }.let { Customisation.tournamentName(GameTournaments.idForKind(kind), it) }
            val back = {
                if (isAdt || isLpl || isIlt || isBbl || isMlc || isSa || isWc || isCt || isAc || isAg || isDomestic || isCustomKind) {
                    // Back to the league list: the CPL is the tournament in hand again.
                    when {
                        isAdt -> adtSaved = saved != null
                        isLpl -> lplSaved = saved != null
                        isIlt -> iltSaved = saved != null
                        isBbl -> bblSaved = saved != null
                        isMlc -> mlcSaved = saved != null
                        isWc || isCt || isAc || isAg || isDomestic || isCustomKind -> Unit
                        else -> saSaved = saved != null
                    }
                    saved = null
                    pendingMatch = null
                    loaded = false
                    kind = "cpl"
                }
                page = if (isCustomKind) TPage.CustomList else if (isDomestic) TPage.Domestic else if (isWc || isCt || isAc || isAg) TPage.International else TPage.Leagues
            }
            BackHandler(onBack = back)
            CplPage(
                title = tournamentLabel,
                intro = when {
                    isAdt -> "The Abu Dhabi T10: the six real teams and their 2026 squads, 10 overs a side, 30 league matches " +
                        "and then the Qualifier and the Final, all at Sheikh Zayed Stadium."
                    isLpl -> "The Lanka Premier League: the five real teams and their 2026 squads, 20 league matches, then Qualifier 1, " +
                        "the Eliminator, Qualifier 2 and the Final. At most four overseas players, and one under-23 player, in your XI."
                    isIlt -> "The International League T20: the six real teams and their Season 5 squads, the real fixture list across Dubai, " +
                        "Sharjah and Abu Dhabi, then the play-offs. Your XI needs two UAE players, two Afghanistan players and one more Associate player."
                    isBbl -> "The Big Bash League: the eight real teams and their 2026-27 squads, the real fixture list from the Chennai opener " +
                        "to the Australia Day Final, then the Qualifier, the Knockout, the Challenger and the Final."
                    isMlc -> "Major League Cricket: the six real teams and their 2026 squads, the real fixture list across Texas, " +
                        "California and the new Pomona ground, then the Qualifier, the Eliminator, the Challenger and the Final. " +
                        "At most six overseas players in your XI."
                    isSa -> "The SA20: the six real teams and their Season 5 squads, every team hosting every other at its own ground, " +
                        "then Qualifier 1, the Eliminator, Qualifier 2 and the Final at Newlands. At most four overseas players in your XI."
                    isWc -> "The ICC Men's T20 World Cup 2026: the 20 real teams and squads in their four groups, the real group fixtures across India and Sri Lanka, then the Super 8, the semi-finals and the Final at Ahmedabad."
                    isRanji -> "The Ranji Trophy 2026-27: the 38 real teams and squads in four Elite groups and the Plate group, and the real group fixtures, in four-day matches. " +
                        "Play your own four-day matches or let the computer play them. The top two of each Elite group go through to the quarter-finals, then the semi-finals and the Final (five days each); a drawn knockout goes to the side ahead on the first innings. The Plate Final decides the promotion."
                    isDuleep -> "The Duleep Trophy 2026-27: the six zones and their squads. East meet North East and North meet West in the quarter-finals, Central and South wait in the semi-finals, then the Final at Chepauk. Four-day matches; a drawn match goes to the side ahead on the first innings."
                    isIrani -> "The Irani Cup 2026-27: one five-day match at Srinagar between the Ranji Trophy champions, Jammu & Kashmir, and Rest of India. If it is drawn, the side ahead on the first innings wins the cup."
                    isSmat -> "The Syed Mushtaq Ali Trophy 2026-27, 20 overs a side: the 38 real teams in four Elite groups of eight and the Plate group, and the real group fixtures. " +
                        "The top two of each Elite group go to the Super League at Nagpur, and the winners of the two Super League groups play the Final. " +
                        "Squads are the Ranji Trophy squads plus well-known players the game already has; the real squads are named just before the tournament."
                    isVht -> "The Vijay Hazare Trophy 2026-27, 50 overs a side: the 38 real teams in four Elite groups of eight and the Plate group, and the real group fixtures. " +
                        "The top two of each Elite group go to the quarter-finals, then the semi-finals and the Final at Visakhapatnam. " +
                        "Squads are the Ranji Trophy squads plus well-known players the game already has; the real squads are named just before the tournament."
                    isOdc -> "The English One-Day Cup 2027, 50 overs a side: the 18 first-class counties in two groups of nine, each playing the other eight once. " +
                        "The top three of each group go through to the quarter-finals, the semi-finals and the Final at Trent Bridge. " +
                        "The 2027 fixture list is not published yet, so the game makes the schedule; the squads are the clubs' current squads."
                    isBlast -> "The Vitality Blast 2027, 20 overs a side: the 18 counties in three groups of six, 12 group matches each. " +
                        "The top two of each group and the two best third-placed counties go to the quarter-finals, then Finals Day at Edgbaston. " +
                        "The 2027 fixture list is not published yet, so the game makes the schedule; the squads are the clubs' current squads."
                    isCc -> "The English County Championship 2027, four-day matches: Division One of ten counties and Division Two of eight, 14 matches each. " +
                        "16 points for a win, 8 for a draw, plus batting and bowling bonus points. The top of Division One are the champions; two go up and two go down. " +
                        "The 2027 fixture list is not published yet, so the game makes the schedule; the squads are the clubs' current squads."
                    isShield -> "The Sheffield Shield 2026-27, four-day matches: Australia's six states, ten rounds with the real fixtures, then a five-day Final between the top two. " +
                        "Six points for an outright win, one for a draw, plus bonus points from the first innings. The real season is already under way; the game simulates every result."
                    isAudc -> "The Australian One-Day Cup 2026-27, 50 overs a side: the six states, 21 league matches with the real fixtures, then the Final for the Dean Jones Trophy between the top two. " +
                        "The real season is already under way; the game simulates every result."
                    isSa4d -> "The CSA 4-Day Series Division 1 2026-27, four-day matches: eight teams, 40 league matches with the published fixtures, then a five-day Final between the top two. " +
                        "The real season is already under way; the game simulates every result. Squads are the contracted squads of 2026-27."
                    isSa50 -> "The CSA Pro50 Cup 2026-27, 50 overs a side: eight teams, 28 matches with the published fixtures, then a Final between the top two. " +
                        "The game simulates every result. Squads are the contracted squads of 2026-27."
                    isPro20 -> "The CSA Pro20 Cup 2026-27, 20 overs a side: 16 teams in two pools of eight, then the Super Eights, the semi-finals and the Final at Newlands. " +
                        "The real season is already under way; the game simulates every result. Squads are the contracted squads of 2026-27."
                    isPlunket -> "The Plunket Shield 2026-27, four-day matches: New Zealand's six major associations, eight rounds with the published fixtures, no final: the top of the table are the champions. " +
                        "Twelve points for a win, plus bonus points from the first innings. The game simulates every result."
                    isFord -> "The Ford Trophy 2026-27, 50 overs a side: six associations, ten rounds with the published fixtures, then an Elimination Final and the Grand Final at the Basin Reserve. " +
                        "The real competition has already begun; the game simulates every result."
                    isSmash -> "The Super Smash 2026-27, 20 overs a side, the last season in its current form: six associations, ten rounds with the published fixtures, then the Elimination Final and the Final in Auckland. " +
                        "The game simulates every result."
                    isWi4d -> "The West Indies Championship 2027, four-day matches: Barbados, Guyana, Jamaica, Leeward Islands, Trinidad and Tobago and Windward Islands in three bilateral series, then a Playoff and the Final in Antigua. " +
                        "Cricket West Indies has not published the next fixtures, so the game makes the schedule in the 2026 format. Squads are the 2026 squads."
                    isWi50 -> "The West Indies Super50 Cup 2026-27, 50 overs a side: the six regional teams each play the other five once, then a Final. " +
                        "Cricket West Indies has not published the next fixtures, so the game makes the schedule in the 2025-26 format. Squads are the 2026 squads."
                    isQeag -> "The Quaid-e-Azam Trophy Gold 2026-27, four-day first-class matches: the top eight regional teams, 28 matches with the published fixtures, then a five-day Final between the top two. " +
                        "The real competition began on 3 November; the game simulates every result. Squads are the PCB rosters of 2 November."
                    isQeas -> "The Quaid-e-Azam Trophy Silver 2026-27, four-day first-class matches: twelve regional teams in two groups of six with the published fixtures, then a Final between the group winners. " +
                        "The real competition began on 3 November; the game simulates every result. Squads are the PCB rosters of 2 November."
                    isCustomKind -> CustomTournaments.introOf(customId)
                    isAg -> "The men's cricket event of the 2026 Asian Games in Nisshin, Japan: ten teams, two groups of three, the quarter-finals with India, Pakistan, Sri Lanka and Bangladesh seeded, the semi-finals, the bronze medal match and the gold medal match."
                    isAc -> "The men's T20 Asia Cup 2025: the eight real teams and squads in two groups of four, the real group fixtures in Dubai and Abu Dhabi, then the Super 4 and the Final."
                    isCt -> "The ICC Champions Trophy 2025, 50 overs a side: the eight real teams and 15-man squads in two groups of four, the real group fixtures in Pakistan and Dubai, then the semi-finals and the Final."
                    else -> "The Caribbean Premier League: seven franchises, a random fixture list every time, and the play-offs at Kensington Oval."
                },
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
                    text = { Text("Your saved $tournamentLabel tournament, with its results and stats, will be removed. This cannot be undone.") },
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
            val back = { page = if (kind == "adt10" || kind == "lpl" || kind == "ilt" || kind == "bbl" || kind == "mlc" || kind == "sa20" || kind == "wc2026" || kind == "ct2025" || kind == "asia2025" || kind == "ag2026" || kind == "ranji2027" || kind == "duleep2026" || kind == "irani2026" || kind == "smat2026" || kind == "vht2026" || kind == "odc2027" || kind == "blast2027" || kind == "cc2027" || kind == "shield2026" || kind == "audc2026" || kind == "sa4d2026" || kind == "sa50c2026" || kind == "pro20c2026" || kind == "plunket2026" || kind == "ford2026" || kind == "smash2026" || kind == "wi4d2027" || kind == "wi50c2026" || kind == "qeag2026" || kind == "qeas2026" || isCustomKind) TPage.Adt else TPage.Cpl }
            BackHandler(onBack = back)
            TeamSelectPage(
                teams = built.first,
                onViewSquad = { teamId -> page = TPage.Squad(teamId) },
                onChoose = { teamId ->
                    val state = when (kind) {
                        "adt10" -> AbuDhabiT10.newTournament(teamId, built, Random.Default)
                        "lpl" -> LankaPremierLeague.newTournament(teamId, built, Random.Default)
                        "ilt" -> InternationalLeagueT20.newTournament(teamId, built)
                        "bbl" -> BigBashLeague.newTournament(teamId, built)
                        "mlc" -> MajorLeagueCricket.newTournament(teamId, built)
                        "sa20" -> Sa20.newTournament(teamId, built)
                        "wc2026" -> T20WorldCup.newTournament(teamId, built)
                        "ct2025" -> ChampionsTrophy.newTournament(teamId, built)
                        "asia2025" -> AsiaCup.newTournament(teamId, built)
                        "ag2026" -> AsianGames.newTournament(teamId, built)
                        "ranji2027" -> Ranji.newTournament(teamId, built)
                        "duleep2026" -> DomesticFc.newDuleep(teamId, built)
                        "irani2026" -> DomesticFc.newIrani(teamId, built)
                        "smat2026" -> DomesticWhiteBall.newTournament(DomesticWhiteBall.SMAT_ID, teamId, built)
                        "vht2026" -> DomesticWhiteBall.newTournament(DomesticWhiteBall.VHT_ID, teamId, built)
                        "odc2027" -> CountyCricket.newTournament(CountyCricket.ODC_ID, teamId, built)
                        "blast2027" -> CountyCricket.newTournament(CountyCricket.BLAST_ID, teamId, built)
                        "cc2027" -> CountyCricket.newTournament(CountyCricket.CC_ID, teamId, built)
                        "shield2026" -> AusDomestic.newTournament(AusDomestic.SHIELD_ID, teamId, built)
                        "audc2026" -> AusDomestic.newTournament(AusDomestic.ODC_ID, teamId, built)
                        "sa4d2026" -> SaDomestic.newTournament(SaDomestic.FOURDAY_ID, teamId, built)
                        "sa50c2026" -> SaDomestic.newTournament(SaDomestic.ODC_ID, teamId, built)
                        "pro20c2026" -> SaDomestic.newTournament(SaDomestic.T20_ID, teamId, built)
                        "plunket2026" -> NzDomestic.newTournament(NzDomestic.PLUNKET_ID, teamId, built)
                        "ford2026" -> NzDomestic.newTournament(NzDomestic.FORD_ID, teamId, built)
                        "smash2026" -> NzDomestic.newTournament(NzDomestic.SMASH_ID, teamId, built)
                        "wi4d2027" -> WiDomestic.newTournament(WiDomestic.FOURDAY_ID, teamId, built)
                        "wi50c2026" -> WiDomestic.newTournament(WiDomestic.ODC_ID, teamId, built)
                        "qeag2026" -> PakDomestic.newTournament(PakDomestic.GOLD_ID, teamId, built)
                        "qeas2026" -> PakDomestic.newTournament(PakDomestic.SILVER_ID, teamId, built)
                        else -> if (isCustomKind) CustomTournaments.newTournament(customId, teamId, built) else CplData.newTournament(teamId, built, Random.Default)
                    }
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
            if (team != null) {
                SquadPage(
                    team = team, tags = built.second, showOrigin = kind != "adt10" && kind != "wc2026" && kind != "ct2025" && kind != "asia2025" && kind != "ag2026" && kind != "ranji2027" && kind != "duleep2026" && kind != "irani2026" && kind != "smat2026" && kind != "vht2026" && kind != "odc2027" && kind != "blast2027" && kind != "cc2027" && kind != "shield2026" && kind != "audc2026" && kind != "sa4d2026" && kind != "sa50c2026" && kind != "pro20c2026" && kind != "plunket2026" && kind != "ford2026" && kind != "smash2026" && kind != "wi4d2027" && kind != "wi50c2026" && kind != "qeag2026" && kind != "qeas2026" && !isCustomKind,
                    domesticLabel = when (kind) { "lpl" -> "Sri Lanka"; "bbl" -> "Australia"; "sa20" -> "South Africa"; else -> "West Indies" },
                    breakoutLabel = if (kind == "lpl") "Sri Lanka, under 23" else "Breakout player",
                    onBack = back
                )
            } else back()
        }
        is TPage.Hub -> {
            val back = { page = when (kind) { "ipl" -> TPage.Ipl; "adt10", "lpl", "ilt", "bbl", "mlc", "sa20", "wc2026", "ct2025", "asia2025", "ag2026", "ranji2027", "duleep2026", "irani2026", "smat2026", "vht2026", "odc2027", "blast2027", "cc2027", "shield2026", "audc2026", "sa4d2026", "sa50c2026", "pro20c2026", "plunket2026", "ford2026", "smash2026", "wi4d2027", "wi50c2026", "qeag2026", "qeas2026" -> TPage.Adt; else -> if (isCustomKind) TPage.Adt else TPage.Cpl } }
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
                        // The IPL holds the toss first; the CPL still picks its XI first.
                        page = if (state.tournamentId == IplSeason.TOURNAMENT_ID) TPage.TossFirst(id) else TPage.PickXI(id)
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
        is TPage.TossFirst -> {
            val back = { page = TPage.Hub }
            BackHandler(onBack = back)
            val state = saved
            val fixture = state?.fixtures?.find { it.id == current.fixtureId }
            val userId = state?.userTeamId
            val userSquad = state?.teams?.find { it.id == userId }
            val opponentId = if (fixture?.homeId == userId) fixture?.awayId else fixture?.homeId
            val opponentSquad = state?.teams?.find { it.id == opponentId }
            if (userSquad != null && opponentSquad != null) {
                TossScreen(
                    userTeam = userSquad,
                    opponentTeam = opponentSquad,
                    onTossComplete = { toss -> page = TPage.PickXI(current.fixtureId, toss = toss) },
                    onBack = back
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
                            val problem = CplSimulator.xiProblem(xi, state.tags, state.tournamentId)
                            if (problem != null) {
                                xiProblem = problem
                                page = TPage.PickXI(current.fixtureId, current.attempt + 1, current.toss)
                            } else {
                                val opponentXI = CplSimulator.pickXI(opponentSquad, state.tags, stadium)
                                // The IPL: the toss has been held, so now come the Impact Player substitutes, then the match.
                                val tossDone = current.toss
                                page = if (state.tournamentId == IplSeason.TOURNAMENT_ID && tossDone != null) {
                                    TPage.Impact(current.fixtureId, xi, opponentXI, tossDone)
                                } else {
                                    TPage.Toss(current.fixtureId, xi, opponentXI)
                                }
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
        is TPage.Impact -> {
            val back = { page = TPage.PickXI(current.fixtureId, 0, current.toss) }
            BackHandler(onBack = back)
            val state = saved
            val userSquad = state?.teams?.find { it.id == current.userXI.id }
            val opponentSquad = state?.teams?.find { it.id == current.opponentXI.id }
            if (state != null && userSquad != null && opponentSquad != null) {
                val overseas = state.tags.filterValues { it == SquadTag.OVERSEAS }.keys.toList()
                val reserves = userSquad.players.filter { p -> current.userXI.players.none { it.id == p.id } }
                ImpactSelectionScreen(
                    teamName = userSquad.name,
                    reserves = reserves,
                    xi = current.userXI.players,
                    overseasIds = overseas,
                    onConfirm = { subs ->
                        val opponentSubs = ImpactPlayer.aiSubstitutes(opponentSquad.players, current.opponentXI.players, overseas.toSet())
                        val setup = ImpactSetup(subs, opponentSubs, overseas)
                        val tossDone = current.toss
                        page = if (tossDone != null) {
                            TPage.PlayMatch(current.fixtureId, current.userXI, current.opponentXI, tossDone, impact = setup)
                        } else {
                            TPage.Toss(current.fixtureId, current.userXI, current.opponentXI, setup)
                        }
                    },
                    onBack = back
                )
            } else back()
        }
        is TPage.Toss -> {
            val back = {
                page = if (current.impact != null) TPage.Impact(current.fixtureId, current.userXI, current.opponentXI) else TPage.PickXI(current.fixtureId)
            }
            BackHandler(onBack = back)
            TossScreen(
                userTeam = current.userXI,
                opponentTeam = current.opponentXI,
                onTossComplete = { toss -> page = TPage.PlayMatch(current.fixtureId, current.userXI, current.opponentXI, toss, impact = current.impact) },
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
                    format = AbuDhabiT10.formatFor(saved?.tournamentId),
                    rules = TournamentRules.rulesFor(saved?.tournamentId, saved?.fixtures?.find { it.id == current.fixtureId }?.stage),
                    stadium = stadium,
                    userTeam = current.userXI,
                    opponentTeam = current.opponentXI,
                    toss = current.toss,
                    onBack = back,
                    onMatchFinished = { page = TPage.Hub },
                    resume = current.resume,
                    saveStore = matchStore,
                    impact = current.impact,
                    powerSurge = saved?.tournamentId == BigBashLeague.TOURNAMENT_ID,
                    onMatchComplete = { finished ->
                        pendingMatch = null
                        val state = saved
                        if (state != null) {
                            // An Impact Player who came on and the player he replaced both played: both count in the stats.
                            val updated = CplSimulator.recordPlayedMatch(
                                state, current.fixtureId, finished,
                                current.userXI.copy(players = ImpactPlayer.participants(finished, current.userXI)),
                                current.opponentXI.copy(players = ImpactPlayer.participants(finished, current.opponentXI))
                            )
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

private fun tagLabelFor(tag: SquadTag?, domestic: String, breakout: String): String = when (tag) {
    SquadTag.OVERSEAS -> "Overseas"
    SquadTag.BREAKOUT -> breakout
    SquadTag.UAE -> "UAE"
    SquadTag.AFGHAN -> "Afghanistan"
    SquadTag.ASSOCIATE -> "Associate nation"
    SquadTag.USA -> "USA"
    else -> domestic
}

private fun tagLabel(tag: SquadTag?): String = when (tag) {
    SquadTag.OVERSEAS -> "Overseas"
    SquadTag.BREAKOUT -> "Breakout player"
    else -> "West Indies"
}

// ---- Menu pages -----------------------------------------------------------------------------

@Composable
private fun MenuPage(onLeague: () -> Unit, onInternational: () -> Unit, onCustom: () -> Unit, onDomestic: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        PageTitle("Tournament")
        Spacer(modifier = Modifier.height(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            ChoiceRow("International tournament", "The T20 World Cup 2026, the Champions Trophy 2025, the Asia Cup 2025 and the Asian Games 2026, with the real teams, squads and fixtures.", enabled = true, onClick = onInternational)
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow("League tournament", "Play a full league season, saved as you go.", enabled = true, onClick = onLeague)
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow("Domestic tournament", "The Ranji Trophy, the Duleep Trophy and the Irani Cup, then the Syed Mushtaq Ali Trophy (T20) and the Vijay Hazare Trophy (50 overs), 2026-27, with the real teams and fixtures; the English County Championship, One-Day Cup and Vitality Blast 2027; the Australian Sheffield Shield and One-Day Cup 2026-27; the South African 4-Day Series, Pro50 Cup and Pro20 Cup 2026-27; the New Zealand Plunket Shield, Ford Trophy and Super Smash 2026-27; and the West Indies Championship and Super50 Cup; and the Pakistani Quaid-e-Azam Trophy Gold and Silver 2026-27.", enabled = true, onClick = onDomestic)
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow("Custom tournaments", "Play the tournaments you designed in Advanced Customisation.", enabled = true, onClick = onCustom)
        }
        Spacer(modifier = Modifier.height(8.dp))
        BackButton(onBack = onBack)
    }
}

@Composable
private fun DomesticPage(onRanji: () -> Unit, onDuleep: () -> Unit, onIrani: () -> Unit, onSmat: () -> Unit, onVht: () -> Unit, onOdc: () -> Unit, onBlast: () -> Unit, onCc: () -> Unit, onShield: () -> Unit, onAudc: () -> Unit, onSa4d: () -> Unit, onSa50: () -> Unit, onPro20: () -> Unit, onPlunket: () -> Unit, onFord: () -> Unit, onSmash: () -> Unit, onWi4d: () -> Unit, onWi50: () -> Unit, onQeag: () -> Unit, onQeas: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        PageTitle("Domestic tournaments")
        Spacer(modifier = Modifier.height(16.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ChoiceRow(
                title = "Ranji Trophy 2026-27",
                description = "38 teams in four Elite groups and the Plate group, the real fixtures, then the knockouts. Four-day matches.",
                enabled = true,
                onClick = onRanji
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Duleep Trophy 2026-27",
                description = "Six zones: two quarter-finals, two semi-finals and the Final at Chepauk. Four-day matches.",
                enabled = true,
                onClick = onDuleep
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Irani Cup 2026-27",
                description = "One five-day match: Jammu & Kashmir, the Ranji champions, against Rest of India, in Srinagar.",
                enabled = true,
                onClick = onIrani
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Syed Mushtaq Ali Trophy 2026-27",
                description = "The T20 competition: 38 teams in four Elite groups and the Plate group, the real fixtures, then the Super League and the Final.",
                enabled = true,
                onClick = onSmat
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Vijay Hazare Trophy 2026-27",
                description = "The 50-over competition: 38 teams in four Elite groups and the Plate group, the real fixtures, then the quarter-finals, semi-finals and the Final.",
                enabled = true,
                onClick = onVht
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "One-Day Cup 2027 (England)",
                description = "The English county 50-over competition: 18 counties in two groups of nine, then the knockouts and the Final at Trent Bridge.",
                enabled = true,
                onClick = onOdc
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Vitality Blast 2027 (England)",
                description = "The English county T20 competition: 18 counties in three groups of six, then the quarter-finals and Finals Day at Edgbaston.",
                enabled = true,
                onClick = onBlast
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "County Championship 2027 (England)",
                description = "The English four-day competition: Division One of ten counties and Division Two of eight, 14 matches each, with bonus points.",
                enabled = true,
                onClick = onCc
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Sheffield Shield 2026-27 (Australia)",
                description = "The Australian four-day competition: six states, ten rounds with the real fixtures, then a five-day Final.",
                enabled = true,
                onClick = onShield
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "One-Day Cup 2026-27 (Australia)",
                description = "The Australian 50-over competition for the Dean Jones Trophy: six states, 21 league matches, then the Final.",
                enabled = true,
                onClick = onAudc
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "CSA 4-Day Series Division 1 2026-27 (South Africa)",
                description = "The South African four-day competition: eight teams, 40 matches with the published fixtures, then a five-day Final.",
                enabled = true,
                onClick = onSa4d
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "CSA Pro50 Cup 2026-27 (South Africa)",
                description = "The South African 50-over cup: eight teams, 28 matches with the published fixtures, then the Final.",
                enabled = true,
                onClick = onSa50
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "CSA Pro20 Cup 2026-27 (South Africa)",
                description = "The new South African T20 cup: 16 teams in two pools, the Super Eights, semi-finals and the Final at Newlands.",
                enabled = true,
                onClick = onPro20
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Plunket Shield 2026-27 (New Zealand)",
                description = "The New Zealand four-day competition: six associations, eight rounds with the published fixtures, no final.",
                enabled = true,
                onClick = onPlunket
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Ford Trophy 2026-27 (New Zealand)",
                description = "The New Zealand 50-over competition: six associations, ten rounds, then the Elimination Final and the Grand Final.",
                enabled = true,
                onClick = onFord
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Super Smash 2026-27 (New Zealand)",
                description = "The New Zealand T20 competition, the last season in its current form: six associations, ten rounds, then the finals in Auckland.",
                enabled = true,
                onClick = onSmash
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "West Indies Championship 2027",
                description = "The West Indies four-day competition: six regional teams in three bilateral series, then a Playoff and the Final. The next fixtures are not published, so the game makes the schedule.",
                enabled = true,
                onClick = onWi4d
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Super50 Cup 2026-27 (West Indies)",
                description = "The West Indies 50-over competition: six regional teams, each playing the other five, then the Final. The game makes the schedule.",
                enabled = true,
                onClick = onWi50
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Quaid-e-Azam Trophy Gold 2026-27 (Pakistan)",
                description = "The Pakistani first-class championship, top division: eight regional teams, 28 matches with the published fixtures, then a five-day Final.",
                enabled = true,
                onClick = onQeag
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Quaid-e-Azam Trophy Silver 2026-27 (Pakistan)",
                description = "The Pakistani first-class second division: twelve regional teams in two groups, then the Final.",
                enabled = true,
                onClick = onQeas
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        BackButton(onBack = onBack)
    }
}

@Composable
private fun InternationalPage(onWorldCup: () -> Unit, onChampionsTrophy: () -> Unit, onAsiaCup: () -> Unit, onAsianGames: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        PageTitle("International tournaments")
        Spacer(modifier = Modifier.height(16.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ChoiceRow(
                title = "T20 World Cup 2026",
                description = "20 national teams, the real groups and fixtures across India and Sri Lanka, the Super 8, semi-finals and Final.",
                enabled = true,
                onClick = onWorldCup
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Champions Trophy 2025",
                description = "50 overs a side: eight teams in two groups, the real fixtures in Pakistan and Dubai, semi-finals and Final.",
                enabled = true,
                onClick = onChampionsTrophy
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Asia Cup 2025",
                description = "T20, the eight real teams in two groups, the Super 4 and the Final, in Dubai and Abu Dhabi.",
                enabled = true,
                onClick = onAsiaCup
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Asian Games 2026",
                description = "T20 in Nisshin, Japan: ten teams, a group stage, quarter-finals with India, Pakistan, Sri Lanka and Bangladesh seeded, then the medal matches.",
                enabled = true,
                onClick = onAsianGames
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        BackButton(onBack = onBack)
    }
}

@Composable
private fun LeaguesPage(
    hasSaved: Boolean,
    adtSaved: Boolean,
    lplSaved: Boolean,
    iltSaved: Boolean,
    bblSaved: Boolean,
    mlcSaved: Boolean,
    saSaved: Boolean,
    onCpl: () -> Unit,
    onAdt: () -> Unit,
    onLpl: () -> Unit,
    onIlt: () -> Unit,
    onBbl: () -> Unit,
    onMlc: () -> Unit,
    onSa20: () -> Unit,
    onIpl: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        PageTitle("League tournaments")
        Spacer(modifier = Modifier.height(16.dp))
        // The list is longer than a phone screen now (eight tournaments), so it scrolls; Back stays at the bottom.
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ChoiceRow(
                title = "CPL 2026",
                description = "Caribbean Premier League: seven teams, 35 league matches and the play-offs." +
                    if (hasSaved) " You have a saved tournament." else "",
                enabled = true,
                onClick = onCpl
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Abu Dhabi T10 2026",
                description = "The real six teams and squads, 10 overs a side: 30 league matches, the Qualifier and the Final." +
                    if (adtSaved) " You have a saved tournament." else "",
                enabled = true,
                onClick = onAdt
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "SA20 2027",
                description = "The six real teams and Season 5 squads: 34 matches from Gqeberha to the Final at Newlands." +
                    if (saSaved) " You have a saved tournament." else "",
                enabled = true,
                onClick = onSa20
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Major League Cricket 2026",
                description = "The six real teams and squads, the real fixture list: 34 matches across Texas and California." +
                    if (mlcSaved) " You have a saved tournament." else "",
                enabled = true,
                onClick = onMlc
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Big Bash League 2026-27",
                description = "The eight real teams and squads, the real fixture list: 44 matches, from Chennai to Australia Day." +
                    if (bblSaved) " You have a saved tournament." else "",
                enabled = true,
                onClick = onBbl
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "International League T20 2026",
                description = "The six real UAE teams, their Season 5 squads and the real fixture list: 34 matches." +
                    if (iltSaved) " You have a saved tournament." else "",
                enabled = true,
                onClick = onIlt
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "Lanka Premier League 2026",
                description = "The five real teams and squads: 20 league matches, then the play-offs." +
                    if (lplSaved) " You have a saved tournament." else "",
                enabled = true,
                onClick = onLpl
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChoiceRow(
                title = "IPL",
                description = "Indian Premier League: build your own team in a full player auction, then play the 74-match season.",
                enabled = true,
                onClick = onIpl
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
    onBack: () -> Unit,
    title: String,
    intro: String
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        PageTitle(title)
        Spacer(modifier = Modifier.height(12.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
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
                    text = intro,
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
                            text = if (home.isNotEmpty()) "Home ground: $home" else "No home ground.",
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
private fun SquadPage(
    team: Team,
    tags: Map<String, SquadTag>,
    showOrigin: Boolean = true,
    domesticLabel: String = "West Indies",
    breakoutLabel: String = "Breakout player",
    onBack: () -> Unit
) {
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
            PlayerProfiles.grouped(team.players).forEach { (title, groupPlayers) ->
                item(key = "group_$title") {
                    Text(
                        text = "$title (${groupPlayers.size})",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 4.dp)
                            .semantics { heading() }
                    )
                }
                items(groupPlayers, key = { it.id }) { p ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .semantics(mergeDescendants = true) {}
                    ) {
                        Text(text = p.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${PlayerProfiles.roleLabel(PlayerProfiles.displayRole(p))}. ${if (showOrigin) tagLabelFor(tags[p.id], domesticLabel, breakoutLabel) + ". " else ""}${PlayerProfiles.battingText(p)} ${PlayerProfiles.bowlingText(p)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
    // The IPL also gives the Orange Cap (most runs) and the Purple Cap (most wickets).
    val isIpl = state.tournamentId == IplSeason.TOURNAMENT_ID
    val orange = if (isIpl) IplSeason.orangeCap(state.playerStats.values) else null
    val purple = if (isIpl) IplSeason.purpleCap(state.playerStats.values) else null
    val orangeLine = if (orange != null) "Orange Cap, most runs: ${orange.name}, ${CplData.shortName(orange.teamId)}, with ${orange.runs} runs." else ""
    val purpleLine = if (purple != null) "Purple Cap, most wickets: ${purple.name}, ${CplData.shortName(purple.teamId)}, with ${purple.wickets} wickets." else ""
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
        val capsSpoken = listOf(orangeLine, purpleLine).filter { it.isNotEmpty() }.joinToString(" ")
        if (capsSpoken.isNotEmpty()) {
            delay(1500)
            while (sound?.isCommentaryBusy() == true) delay(300)
            services?.announceSpoken(capsSpoken)
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
            if (orangeLine.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = orangeLine, style = MaterialTheme.typography.bodyLarge, color = colors.onPrimaryContainer)
            }
            if (purpleLine.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = purpleLine, style = MaterialTheme.typography.bodyLarge, color = colors.onPrimaryContainer)
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
    // Every tournament, the Ranji Trophy included, lets you play your own match.
    val canPlay = true
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
                    if (nextIsMine && matchInProgress && canPlay) {
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
                    } else if (nextIsMine && canPlay) {
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
    if (CountyCricket.isChampionship(state.tournamentId)) {
        CountyTableTab(state)
        return
    }
    if (AusDomestic.isAus(state.tournamentId)) {
        AusTableTab(state)
        return
    }
    if (SaDomestic.isSa(state.tournamentId)) {
        SaTableTab(state)
        return
    }
    if (NzDomestic.isNz(state.tournamentId)) {
        NzTableTab(state)
        return
    }
    if (WiDomestic.isWi(state.tournamentId)) {
        WiTableTab(state)
        return
    }
    if (PakDomestic.isPak(state.tournamentId)) {
        PakTableTab(state)
        return
    }
    // The World Cup has a table for every group, in its own file.
    if (state.tournamentId == T20WorldCup.TOURNAMENT_ID || state.tournamentId == ChampionsTrophy.TOURNAMENT_ID || state.tournamentId == AsiaCup.TOURNAMENT_ID || state.tournamentId == AsianGames.TOURNAMENT_ID || DomesticWhiteBall.isWhiteBall(state.tournamentId) || CountyCricket.isCounty(state.tournamentId) || TournamentRules.isCustom(state.tournamentId)) {
        WorldCupTableTab(state)
        return
    }
    if (state.tournamentId == Ranji.TOURNAMENT_ID) {
        RanjiTableTab(state)
        return
    }
    if (DomesticFc.isSmall(state.tournamentId)) {
        DomesticKnockoutTab(state)
        return
    }
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
        items(
            when (state.tournamentId) {
                IplSeason.TOURNAMENT_ID -> IplSeason.stadiums
                AbuDhabiT10.TOURNAMENT_ID -> AbuDhabiT10.stadiums
                LankaPremierLeague.TOURNAMENT_ID -> LankaPremierLeague.stadiums
                InternationalLeagueT20.TOURNAMENT_ID -> InternationalLeagueT20.stadiums
                BigBashLeague.TOURNAMENT_ID -> BigBashLeague.stadiums
                MajorLeagueCricket.TOURNAMENT_ID -> MajorLeagueCricket.stadiums
                Sa20.TOURNAMENT_ID -> Sa20.stadiums
                Ranji.TOURNAMENT_ID -> Ranji.stadiums
                DomesticFc.DULEEP_ID, DomesticFc.IRANI_ID -> DomesticFc.stadiumsFor(state.tournamentId)
                DomesticWhiteBall.SMAT_ID, DomesticWhiteBall.VHT_ID -> DomesticWhiteBall.stadiumsFor(state.tournamentId)
                CountyCricket.ODC_ID, CountyCricket.BLAST_ID, CountyCricket.CC_ID -> CountyCricket.stadiumsFor(state.tournamentId)
                AusDomestic.SHIELD_ID, AusDomestic.ODC_ID -> AusDomestic.stadiumsFor(state.tournamentId)
                SaDomestic.FOURDAY_ID, SaDomestic.ODC_ID, SaDomestic.T20_ID -> SaDomestic.stadiumsFor(state.tournamentId)
                NzDomestic.PLUNKET_ID, NzDomestic.FORD_ID, NzDomestic.SMASH_ID -> NzDomestic.stadiumsFor(state.tournamentId)
                WiDomestic.FOURDAY_ID, WiDomestic.ODC_ID -> WiDomestic.stadiumsFor(state.tournamentId)
                PakDomestic.GOLD_ID, PakDomestic.SILVER_ID -> PakDomestic.stadiumsFor(state.tournamentId)
                T20WorldCup.TOURNAMENT_ID -> T20WorldCup.stadiums
                ChampionsTrophy.TOURNAMENT_ID -> ChampionsTrophy.stadiums
                AsiaCup.TOURNAMENT_ID -> AsiaCup.stadiums
                AsianGames.TOURNAMENT_ID -> AsianGames.stadiums
                else -> CustomTournaments.stadiumsFor(state.tournamentId) ?: CplData.stadiums
            },
            key = { it.id }
        ) { s ->
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
        CplData.statCategories(
            stats,
            teamNameOf = { id -> CplData.shortName(id) },
            caps = state.tournamentId == IplSeason.TOURNAMENT_ID
        )
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

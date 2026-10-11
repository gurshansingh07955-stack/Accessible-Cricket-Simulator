package com.cricketsim.ui.ipl

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.IplAuction
import com.cricketsim.logic.IplAuctionState
import com.cricketsim.logic.IplData
import com.cricketsim.logic.LotState
import com.cricketsim.persistence.IplSaveStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/**
 * The IPL section, reached from Tournament > League tournaments > IPL.
 *
 *   IPL: "IPL 2027" (coming soon) and "Build your own team"
 *     -> choose one of the ten franchises
 *       -> the auction hall: every player in his set, you bid or pass, the other nine teams bid
 *         -> the results: your squad and every team's purse
 *
 * The auction saves itself after every player, so leaving and coming back resumes it.
 *
 * THE BIDDING RUNS ON ITS OWN, like a real auction. After every bid there is a pause (BID_WAIT_MS)
 * in which you can bid; then another team bids if it wants the player, and the pause starts again.
 * When nobody wants to go higher the auctioneer counts "Going once ... going twice ..." and the hammer
 * falls. You can bid at any moment, including during the count; a bid stops the count. "Pass on this
 * player" sits you out and the other teams finish it quickly.
 *
 * ACCESSIBILITY: the auctioneer's calls are spoken by the phone's own speech engine in a British
 * voice (until recorded auctioneer clips replace it), independent of the screen reader and of the
 * commentary setting, so they are always heard. The buttons say what they do and what it costs.
 */
/** How long the hall waits after a bid for you to bid before another team may, and between the count's steps. */
private const val BID_WAIT_MS = 3200L
private const val COUNT_STEP_MS = 2400L

/**
 * The auctioneer's speech: the phone's text-to-speech in a British voice, queued so calls never cut
 * each other off. It is the game's own speech, not the screen reader's, so it works with or without TalkBack.
 */
private class Auctioneer(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts
                if (engine != null) {
                    val result = engine.setLanguage(Locale.UK)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.getDefault())
                    }
                    engine.setSpeechRate(1.0f)
                    engine.setPitch(0.9f)
                    ready = true
                }
            }
        }
    }

    fun say(text: String) {
        if (!ready || text.isEmpty()) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
    }
}

private sealed interface IPage {
    object Menu : IPage
    object TeamSelect : IPage
    object Hall : IPage
    object Summary : IPage
}

@Composable
fun IplFlow(onExit: () -> Unit) {
    val context = LocalContext.current
    val store = remember { IplSaveStore(context) }
    val scope = rememberCoroutineScope()
    val services = LocalGameServices.current
    val auctioneer = remember { Auctioneer(context) }
    DisposableEffect(Unit) { onDispose { auctioneer.shutdown() } }

    var page by remember { mutableStateOf<IPage>(IPage.Menu) }
    var auction by remember { mutableStateOf<IplAuctionState?>(null) }
    var lot by remember { mutableStateOf<LotState?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val speak: (String) -> Unit = { text -> auctioneer.say(text) }

    // Opens the bidding on the next player, with the first bid from another team if there is one.
    val beginLot: (IplAuctionState) -> Unit = { s ->
        val id = IplAuction.currentPlayerId(s)
        val p = if (id != null) IplAuction.playerOf(s, id) else null
        if (s.finished || id == null || p == null) {
            lot = null
            page = IPage.Summary
        } else {
            lot = LotState(id)
            speak(
                "Next up: ${p.player.name}, ${IplAuction.roleText(p)} from ${IplAuction.countryName(p.country)}. " +
                    "Base price ${IplAuction.priceText(p.baseLakhs)}."
            )
        }
    }

    val update: (LotState) -> Unit = { n ->
        val before = lot?.log?.size ?: 0
        lot = n
        val fresh = n.log.drop(before).takeLast(3)
        if (fresh.isNotEmpty()) speak(fresh.joinToString(" "))
    }

    LaunchedEffect(Unit) {
        auction = store.load()
        loaded = true
    }

    // THE BIDDING LOOP. Runs again whenever the bid, the leader, or your decision changes, so a bid
    // from you (or from a team) restarts the wait. Wait; then either another team bids, or the count
    // begins; if nobody bids during the count the lot closes.
    val inHall = page is IPage.Hall
    LaunchedEffect(inHall, busy, lot?.playerId, lot?.bidLakhs, lot?.userOut, lot?.closed) {
        val l = lot
        val s = auction
        if (!inHall || busy || l == null || s == null || l.closed) return@LaunchedEffect
        val p = IplAuction.playerOf(s, l.playerId) ?: return@LaunchedEffect
        val quick = l.userOut || !IplAuction.userCanBid(s, l, p)
        delay(if (quick) 800L else BID_WAIT_MS)
        val bid = IplAuction.aiBid(s, l)
        if (bid != null) {
            update(bid)
            return@LaunchedEffect
        }
        val step = if (quick) 700L else COUNT_STEP_MS
        lot = l.copy(countdown = 1)
        speak("Going once.")
        delay(step)
        lot = l.copy(countdown = 2)
        speak("Going twice.")
        delay(step)
        lot = l.copy(countdown = 0, closed = true)
    }

    // The hammer falls: knock, then the auctioneer says what happened.
    val closed = lot?.closed == true
    val closedId = lot?.playerId
    LaunchedEffect(closed, closedId) {
        val l = lot
        val s = auction
        if (l != null && l.closed && s != null) {
            val p = IplAuction.playerOf(s, l.playerId)
            val name = p?.player?.name ?: "The player"
            val leader = l.leaderId
            if (leader != null && l.bidLakhs > 0) {
                services?.sound?.playAuctionHammer()
                delay(700)
                speak("Sold! $name to ${IplData.team(leader)?.name} for ${IplAuction.priceText(l.bidLakhs)}.")
            } else {
                speak("No bids. $name goes unsold.")
            }
        }
    }

    when (page) {
        is IPage.Menu -> {
            BackHandler(onBack = onExit)
            IplMenuPage(
                loaded = loaded,
                saved = auction,
                onNew = { page = IPage.TeamSelect },
                onResume = {
                    val s = auction
                    if (s != null) {
                        if (s.finished) page = IPage.Summary else {
                            page = IPage.Hall
                            beginLot(s)
                        }
                    }
                },
                onDelete = { confirmDelete = true },
                onBack = onExit
            )
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text("Delete this auction?") },
                    text = { Text("Your saved auction and everything bought in it will be removed. This cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDelete = false
                            auction = null
                            lot = null
                            scope.launch { store.delete() }
                        }) { Text("Delete auction") }
                    },
                    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } }
                )
            }
        }
        is IPage.TeamSelect -> {
            val back = { page = IPage.Menu }
            BackHandler(onBack = back)
            IplTeamSelectPage(
                onChoose = { teamId ->
                    val fresh = IplAuction.newAuction(teamId, IplData.buildPool(), System.currentTimeMillis())
                    auction = fresh
                    scope.launch { store.save(fresh) }
                    page = IPage.Hall
                    beginLot(fresh)
                },
                onBack = back
            )
        }
        is IPage.Hall -> {
            val leave = {
                page = IPage.Menu
                lot = null
            }
            BackHandler(onBack = leave)
            val s = auction
            val l = lot
            if (s != null && l != null) {
                AuctionHall(
                    state = s,
                    lot = l,
                    busy = busy,
                    onBid = { update(IplAuction.userBid(s, l).copy(countdown = 0)) },
                    onPass = { update(l.copy(userOut = true, countdown = 0)) },
                    onNext = {
                        val next = IplAuction.finishLot(s, l)
                        auction = next
                        scope.launch { store.save(next) }
                        lot = null
                        beginLot(next)
                    },
                    onSkipSet = {
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.Default) {
                                val resolved = IplAuction.finishLot(s, IplAuction.advance(s, l.copy(userOut = true)))
                                IplAuction.runToEndOfSet(resolved)
                            }
                            auction = result
                            store.save(result)
                            busy = false
                            beginLot(result)
                        }
                    },
                    onSimulateAll = {
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.Default) {
                                val resolved = IplAuction.finishLot(s, IplAuction.advance(s, l.copy(userOut = true)))
                                IplAuction.runAll(resolved)
                            }
                            auction = result
                            store.save(result)
                            busy = false
                            beginLot(result)
                        }
                    },
                    onLeave = leave
                )
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text("Getting the auction hall ready...", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        is IPage.Summary -> {
            val back = { page = IPage.Menu }
            BackHandler(onBack = back)
            val s = auction
            if (s != null) IplSummaryPage(state = s, onBack = back) else back()
        }
    }
}

// ---- Shared pieces --------------------------------------------------------------------------

@Composable
private fun Title(text: String) {
    Text(text = text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun OptionRow(title: String, description: String, enabled: Boolean, onClick: () -> Unit) {
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

// ---- Menu and team choice -------------------------------------------------------------------

@Composable
private fun IplMenuPage(
    loaded: Boolean,
    saved: IplAuctionState?,
    onNew: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Title("IPL")
        Spacer(modifier = Modifier.height(16.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            OptionRow("IPL 2027", "Coming soon.", enabled = false, onClick = {})
            Spacer(modifier = Modifier.height(12.dp))
            if (!loaded) {
                Text("Looking for a saved auction...", style = MaterialTheme.typography.bodyLarge)
            } else if (saved != null) {
                val team = IplData.team(saved.userTeamId)?.name ?: "your team"
                val done = saved.setIndex.coerceAtMost(saved.sets.size)
                Text(
                    text = if (saved.finished) "Saved auction finished. You ran $team."
                    else "Saved auction. You are running $team. $done of ${saved.sets.size} sets done.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                    Text(if (saved.finished) "View auction results" else "Resume auction")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Delete saved auction") }
            } else {
                OptionRow(
                    "Build your own team",
                    "Pick one of the ten franchises and run a full auction: 120 crore, every player in his set.",
                    enabled = true,
                    onClick = onNew
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun IplTeamSelectPage(onChoose: (String) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Title("Choose your franchise")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Every team starts with 120 crore and an empty squad. Squads hold 18 to 25 players, at most 8 overseas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(IplData.TEAMS, key = { it.id }) { team ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Button(onClick = { onChoose(team.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Choose ${team.name}")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

// ---- The auction hall -----------------------------------------------------------------------

@Composable
private fun AuctionHall(
    state: IplAuctionState,
    lot: LotState,
    busy: Boolean,
    onBid: () -> Unit,
    onPass: () -> Unit,
    onNext: () -> Unit,
    onSkipSet: () -> Unit,
    onSimulateAll: () -> Unit,
    onLeave: () -> Unit
) {
    val p = IplAuction.playerOf(state, lot.playerId)
    val setName = IplAuction.currentSetName(state)
    val inSet = state.sets.getOrNull(state.setIndex)?.playerIds?.size ?: 0
    val mine = IplData.team(state.userTeamId)
    val squad = state.squads[state.userTeamId].orEmpty()
    val overseas = squad.count { IplAuction.playerOf(state, it)?.overseas == true }
    val purse = state.purse[state.userTeamId] ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Set ${state.setIndex + 1} of ${state.sets.size}: $setName. Player ${state.lotIndex + 1} of $inSet.",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (p != null) {
            Text(text = p.player.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "${IplAuction.roleText(p).replaceFirstChar { it.uppercase() }} from ${IplAuction.countryName(p.country)}. " +
                    (if (p.capped) "Capped. " else "Uncapped. ") + (if (p.overseas) "Overseas. " else "") +
                    "Base price ${IplAuction.priceText(p.baseLakhs)}. " +
                    "Batting ${p.player.battingRating}, bowling ${p.player.bowlingRating}.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        val leader = lot.leaderId
        Text(
            text = when {
                lot.closed && leader != null ->
                    "SOLD to ${IplData.team(leader)?.name} for ${IplAuction.priceText(lot.bidLakhs)}."
                lot.closed -> "UNSOLD. No team bid."
                leader != null -> "Highest bid: ${IplAuction.priceText(lot.bidLakhs)} from ${IplData.team(leader)?.name}."
                else -> "No bids yet."
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (!lot.closed && lot.countdown > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (lot.countdown == 1) "Going once..." else "Going twice...",
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${mine?.name}: purse ${IplAuction.priceText(purse)}, squad ${squad.size} of ${IplData.MAX_SQUAD}, overseas $overseas of ${IplData.MAX_OVERSEAS}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (lot.closed) {
            Button(onClick = onNext, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Next player") }
        } else if (p != null) {
            if (IplAuction.userCanBid(state, lot, p)) {
                val price = IplAuction.askingPrice(lot, p)
                Button(onClick = onBid, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (lot.bidLakhs == 0) "Open the bidding at ${IplAuction.priceText(price)}" else "Bid ${IplAuction.priceText(price)}")
                }
                Spacer(modifier = Modifier.height(6.dp))
            } else if (!lot.userOut && lot.leaderId == state.userTeamId) {
                Text(
                    text = "You hold the highest bid. Waiting to see if another team goes higher.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
            } else if (!lot.userOut) {
                Text(
                    text = "You cannot bid for this player: squad full, overseas limit reached, or not enough purse.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            OutlinedButton(onClick = onPass, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Pass on this player")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onSkipSet, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Skip the rest of this set")
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(onClick = onSimulateAll, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Simulate the rest of the auction")
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(onClick = onLeave, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Leave the auction (saved)")
        }
        if (busy) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Running the auction, please wait.", style = MaterialTheme.typography.bodyMedium)
        }
        if (lot.log.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("This player so far", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            lot.log.takeLast(6).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

// ---- The results ----------------------------------------------------------------------------

@Composable
private fun IplSummaryPage(state: IplAuctionState, onBack: () -> Unit) {
    val mine = state.squads[state.userTeamId].orEmpty().mapNotNull { IplAuction.playerOf(state, it) }
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Title("Auction complete")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${IplData.team(state.userTeamId)?.name}: ${mine.size} players, purse left ${IplAuction.priceText(state.purse[state.userTeamId] ?: 0)}." +
                (if (mine.size < IplData.MIN_SQUAD) " Your squad is below the minimum of ${IplData.MIN_SQUAD}." else "") +
                " The IPL 2027 season is coming soon.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Text("Your squad", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            items(mine, key = { it.id }) { p ->
                Text(
                    text = "${p.player.name}, ${IplAuction.roleText(p)}, ${IplAuction.countryName(p.country)}: " +
                        IplAuction.priceText(state.soldPrice[p.id] ?: p.baseLakhs),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text("All teams", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            items(IplData.TEAMS, key = { it.id }) { t ->
                val n = state.squads[t.id].orEmpty().size
                val ov = state.squads[t.id].orEmpty().count { IplAuction.playerOf(state, it)?.overseas == true }
                Text(
                    text = "${t.name}: $n players, $ov overseas, purse left ${IplAuction.priceText(state.purse[t.id] ?: 0)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to IPL") }
    }
}

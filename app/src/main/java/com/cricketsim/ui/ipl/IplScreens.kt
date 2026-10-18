package com.cricketsim.ui.ipl

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.AuctionVoice
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.audio.Synth
import com.cricketsim.logic.AuctionPlayer
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
/**
 * The hammer, played straight from here on its own audio track, so it does not depend on the game's
 * sound engine, its settings or its load list. Two raps of the synthesized gavel (the second a little
 * softer), at full volume.
 */
private class Hammer {
    private var track: AudioTrack? = null

    fun knock() {
        runCatching {
            track?.release()
            val single = Synth.gavel()
            val gap = ShortArray((Synth.SAMPLE_RATE * 0.22).toInt())
            val pcm = ShortArray(single.size + gap.size + single.size)
            single.copyInto(pcm, 0)
            for (i in single.indices) pcm[single.size + gap.size + i] = (single[i] * 0.8f).toInt().toShort()
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(Synth.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val t = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(pcm, 0, pcm.size)
            t.setVolume(1.0f)
            t.play()
            track = t
        }
    }

    fun release() {
        runCatching { track?.stop() }
        runCatching { track?.release() }
    }
}

/** How long the hall waits after a bid for you to bid before another team may, and between the count's steps. */
private const val BID_WAIT_MS = 2400L
private const val COUNT_STEP_MS = 2400L

/**
 * The FAST auction (chosen after the team): the same rhythm, quicker. The pauses are about 2.4 times shorter and the
 * auctioneer's recorded voice plays at 1.35 times speed (same pitch). The hall still waits for him to finish each call
 * before timing the next step, so the voice and the bidding stay in step exactly as in the normal speed.
 */
private const val FAST_BID_WAIT_MS = 1000L
private const val FAST_COUNT_STEP_MS = 1100L
private const val FAST_VOICE_SPEED = 1.35f

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
    object Squads : IPage
    /** After choosing a team: the speed of the auction. */
    class Speed(val teamId: String) : IPage
}

@Composable
fun IplFlow(
    onExit: () -> Unit,
    /** Is there a saved IPL season (played with the auctioned squads)? */
    seasonSaved: Boolean = false,
    onStartSeason: (IplAuctionState) -> Unit = {},
    onOpenSeason: () -> Unit = {},
    onDeleteSeason: () -> Unit = {}
) {
    val context = LocalContext.current
    val store = remember { IplSaveStore(context) }
    val scope = rememberCoroutineScope()
    val services = LocalGameServices.current
    val auctioneer = remember { Auctioneer(context) }
    val hammer = remember { Hammer() }
    val voice = remember { AuctionVoice(context) }
    DisposableEffect(Unit) {
        onDispose {
            auctioneer.shutdown()
            hammer.release()
            voice.stop()
        }
    }

    var page by remember { mutableStateOf<IPage>(IPage.Menu) }
    // The "menu open" sound for each IPL menu page (not the auction hall itself).
    val menuSoundServices = LocalGameServices.current
    LaunchedEffect(page::class) {
        if (page is IPage.Hall) {
            // The auctioneer speaks here: no music under him.
            menuSoundServices?.sound?.stopMenuMusic()
        } else {
            menuSoundServices?.sound?.playMenuOpen()
            menuSoundServices?.sound?.startMenuMusic()
        }
    }
    var auction by remember { mutableStateOf<IplAuctionState?>(null) }
    // Where the squads page was opened from (the hall or the results), so Back returns there.
    var squadsFrom by remember { mutableStateOf<IPage>(IPage.Hall) }
    var lot by remember { mutableStateOf<LotState?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val speak: (String) -> Unit = { text -> auctioneer.say(text) }

    // A call is a run of recorded clips (name, team, price ...) in the auctioneer's own voice. If any
    // clip is not in the pack, the whole call is spoken by the phone's voice instead.
    val slug: (String) -> String = { s -> s.lowercase().filter { it in 'a'..'z' } }
    val teamKey: (String) -> String = { id -> id.removePrefix("ipl_") }
    val roleClip: (AuctionPlayer) -> String = { p ->
        "au_r_" + when (IplAuction.group(p)) {
            "WK" -> "wicketkeeper"
            "AL" -> "allrounder"
            "FA" -> "fast"
            "SP" -> "spin"
            else -> "batter"
        }
    }
    val call: (List<String>, String) -> Unit = { clips, fallback ->
        voice.speed = if (auction?.fast == true) FAST_VOICE_SPEED else 1f
        if (!voice.play(clips)) speak(fallback)
    }
    // A bid call cuts off whatever the auctioneer was still saying, so the spoken price always matches the screen.
    val callNow: (List<String>, String) -> Unit = { clips, fallback ->
        voice.speed = if (auction?.fast == true) FAST_VOICE_SPEED else 1f
        if (!voice.play(clips, interrupt = true)) speak(fallback)
    }

    // Opens the bidding on the next player, with the first bid from another team if there is one.
    val beginLot: (IplAuctionState) -> Unit = { s ->
        val id = IplAuction.currentPlayerId(s)
        val p = if (id != null) IplAuction.playerOf(s, id) else null
        if (s.finished || id == null || p == null) {
            lot = null
            page = IPage.Summary
        } else {
            lot = LotState(id)
            call(
                listOf(
                    "au_x_nextup", "au_n_" + slug(p.player.name), roleClip(p), "au_c_" + p.country.lowercase(),
                    "au_x_baseprice", "au_p_${p.baseLakhs}"
                ),
                "Next up: ${p.player.name}, ${IplAuction.roleText(p)} from ${IplAuction.countryName(p.country)}. " +
                    "Base price ${IplAuction.priceText(p.baseLakhs)}."
            )
        }
    }

    val update: (LotState) -> Unit = { n ->
        val old = lot
        lot = n
        val leaderNow = n.leaderId
        if (leaderNow != null && n.bidLakhs > 0 && (old?.bidLakhs != n.bidLakhs || old?.leaderId != leaderNow)) {
            callNow(
                listOf("au_t_${teamKey(leaderNow)}_short", "au_x_bid", "au_p_${n.bidLakhs}"),
                "${IplData.shortName(leaderNow)} bid ${IplAuction.priceText(n.bidLakhs)}."
            )
        }
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
        // The pause starts only once the auctioneer has finished speaking, so the call is never still
        // being said while the next bid arrives (safety cap 8 s in case a clip stalls).
        var waited = 0L
        while (voice.isSpeaking && waited < 8000L) {
            delay(100L)
            waited += 100L
        }
        delay(if (quick) (if (s.fast) 350L else 800L) else (if (s.fast) FAST_BID_WAIT_MS else BID_WAIT_MS))
        val bid = IplAuction.aiBid(s, l)
        if (bid != null) {
            update(bid)
            return@LaunchedEffect
        }
        val step = if (quick) (if (s.fast) 350L else 700L) else (if (s.fast) FAST_COUNT_STEP_MS else COUNT_STEP_MS)
        lot = l.copy(countdown = 1)
        call(listOf("au_x_goingonce"), "Going once.")
        delay(step)
        lot = l.copy(countdown = 2)
        call(listOf("au_x_goingtwice"), "Going twice.")
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
                hammer.knock()
                services?.sound?.playAuctionHammer()
                delay(if (s.fast) 450L else 900L)
                call(
                    listOf("au_x_sold", "au_n_" + slug(name), "au_x_to", "au_t_${teamKey(leader)}_full", "au_x_for", "au_p_${l.bidLakhs}"),
                    "Sold! $name to ${IplData.team(leader)?.name} for ${IplAuction.priceText(l.bidLakhs)}."
                )
            } else {
                call(listOf("au_x_nobids", "au_n_" + slug(name), "au_x_goesunsold"), "No bids. $name goes unsold.")
            }
            if (l.userOut) {
                // You passed on this player: once the result has been said (sold or unsold) the auction
                // moves on to the next player by itself. If you left the hall meanwhile, nothing happens.
                delay(if (s.fast) 700L else 1500L)
                var waited = 0L
                while (voice.isSpeaking && waited < 10000L) {
                    delay(100L)
                    waited += 100L
                }
                delay(if (s.fast) 300L else 600L)
                val s2 = auction
                val l2 = lot
                if (page is IPage.Hall && !busy && s2 != null && l2 != null && l2.closed && l2.playerId == l.playerId) {
                    val next = IplAuction.finishLot(s2, l2)
                    auction = next
                    scope.launch { store.save(next) }
                    lot = null
                    beginLot(next)
                }
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
                seasonSaved = seasonSaved,
                onSeason = {
                    val s = auction
                    if (seasonSaved) onOpenSeason() else if (s != null) onStartSeason(s)
                },
                onDeleteSeason = onDeleteSeason,
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
        is IPage.Speed -> {
            val chosenTeam = (page as? IPage.Speed)?.teamId
            val back = { page = IPage.TeamSelect }
            BackHandler(onBack = back)
            if (chosenTeam == null) {
                back()
            } else {
                IplSpeedPage(
                    teamName = IplData.team(chosenTeam)?.name ?: chosenTeam,
                    onChoose = { fast ->
                        val fresh = IplAuction.newAuction(chosenTeam, IplData.buildPool(), System.currentTimeMillis()).copy(fast = fast)
                        auction = fresh
                        scope.launch { store.save(fresh) }
                        page = IPage.Hall
                        beginLot(fresh)
                    },
                    onBack = back
                )
            }
        }
        is IPage.TeamSelect -> {
            val back = { page = IPage.Menu }
            BackHandler(onBack = back)
            IplTeamSelectPage(
                onChoose = { teamId -> page = IPage.Speed(teamId) },
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
                    onPass = {
                        // Pass: the other teams settle this player at once (no bid-by-bid talk), then the
                        // result is announced and the auction moves on by itself.
                        busy = true
                        voice.stop()
                        scope.launch {
                            val resolved = withContext(Dispatchers.Default) {
                                IplAuction.advance(s, l.copy(userOut = true, countdown = 0))
                            }
                            lot = resolved.copy(closed = true, countdown = 0)
                            busy = false
                        }
                    },
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
                    onSquads = {
                        voice.stop()
                        squadsFrom = IPage.Hall
                        page = IPage.Squads
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
            if (s != null) {
                IplSummaryPage(
                    state = s,
                    onBack = back,
                    onSquads = {
                        squadsFrom = IPage.Summary
                        page = IPage.Squads
                    },
                    onShare = { shareTeam(context, s) },
                    onCopy = { copyTeam(context, s) },
                    seasonSaved = seasonSaved,
                    onSeason = { if (seasonSaved) onOpenSeason() else onStartSeason(s) }
                )
            } else back()
        }
        is IPage.Squads -> {
            val back = { page = squadsFrom }
            BackHandler(onBack = back)
            val s = auction
            if (s != null) AllSquadsPage(state = s, onBack = back) else back()
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
    seasonSaved: Boolean,
    onSeason: () -> Unit,
    onDeleteSeason: () -> Unit,
    onBack: () -> Unit
) {
    var confirmSeasonDelete by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Title("IPL")
        Spacer(modifier = Modifier.height(16.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            OptionRow("IPL 2027", "Run the auction, then play the full 74-match season with your squad.", enabled = false, onClick = {})
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
                if (saved.finished) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onSeason, modifier = Modifier.fillMaxWidth()) {
                        Text(if (seasonSaved) "Resume the IPL season" else "Start the IPL season")
                    }
                    if (seasonSaved) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { confirmSeasonDelete = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Delete the IPL season")
                        }
                    }
                }
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
        if (confirmSeasonDelete) {
            AlertDialog(
                onDismissRequest = { confirmSeasonDelete = false },
                title = { Text("Delete the IPL season?") },
                text = { Text("Your saved IPL season, with its results and stats, will be removed. The auction stays. This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmSeasonDelete = false
                        onDeleteSeason()
                    }) { Text("Delete season") }
                },
                dismissButton = { TextButton(onClick = { confirmSeasonDelete = false }) { Text("Keep it") } }
            )
        }
    }
}

@Composable
private fun IplSpeedPage(teamName: String, onChoose: (Boolean) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Title("Choose the speed of the auction")
        Spacer(modifier = Modifier.height(8.dp))
        Text("You are bidding for $teamName.", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            OptionRow("Normal (default)", "The auctioneer takes his time between bids, as in a real auction.", enabled = true, onClick = { onChoose(false) })
            Spacer(modifier = Modifier.height(10.dp))
            OptionRow("Fast", "Bids come quicker and the auctioneer speaks faster, in step with the bidding. A shorter auction.", enabled = true, onClick = { onChoose(true) })
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun IplTeamSelectPage(onChoose: (String) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Title("Choose your franchise")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Every team starts with 120 crore and an empty squad. Every squad ends with ${IplData.MIN_SQUAD} to ${IplData.MAX_SQUAD} players, at most ${IplData.MAX_OVERSEAS} overseas.",
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
    onSquads: () -> Unit,
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
        OutlinedButton(onClick = onSquads, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Check squads")
        }
        Spacer(modifier = Modifier.height(6.dp))
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
private fun IplSummaryPage(
    state: IplAuctionState,
    onBack: () -> Unit,
    onSquads: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    seasonSaved: Boolean,
    onSeason: () -> Unit
) {
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
        Button(onClick = onSquads, modifier = Modifier.fillMaxWidth()) { Text("Check all teams") }
        Spacer(modifier = Modifier.height(6.dp))
        Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text("Share my team") }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) { Text("Copy my team") }
        Spacer(modifier = Modifier.height(6.dp))
        Button(onClick = onSeason, modifier = Modifier.fillMaxWidth()) {
            Text(if (seasonSaved) "Resume the IPL season" else "Start the IPL season")
        }
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

// ---- Squads of all ten teams, sharing -------------------------------------------------------

/** Your team first, then the other nine in the usual order. */
private fun teamsUserFirst(state: IplAuctionState): List<String> =
    listOf(state.userTeamId) + IplData.TEAMS.map { it.id }.filter { it != state.userTeamId }

private fun paidFor(state: IplAuctionState, p: AuctionPlayer): Int = state.soldPrice[p.id] ?: p.baseLakhs

/** A team's players, the dearest first. */
private fun squadByPrice(state: IplAuctionState, teamId: String): List<AuctionPlayer> =
    state.squads[teamId].orEmpty()
        .mapNotNull { IplAuction.playerOf(state, it) }
        .sortedByDescending { paidFor(state, it) }

/** The team as clear plain text, for sharing and copying. */
private fun teamShareText(state: IplAuctionState, teamId: String): String {
    val squad = squadByPrice(state, teamId)
    val sb = StringBuilder()
    sb.append("My IPL auction team: ").append(IplData.team(teamId)?.name ?: teamId).append("\n")
    sb.append("Players: ").append(squad.size).append(" (").append(squad.count { it.overseas }).append(" overseas)\n")
    sb.append("Money spent: ").append(IplAuction.priceText(squad.sumOf { paidFor(state, it) })).append("\n")
    sb.append("Purse left: ").append(IplAuction.priceText(state.purse[teamId] ?: 0)).append("\n\n")
    squad.forEachIndexed { i, p ->
        sb.append(i + 1).append(". ").append(p.player.name)
            .append(", ").append(IplAuction.roleText(p))
            .append(", ").append(IplAuction.countryName(p.country))
            .append(", ").append(IplAuction.priceText(paidFor(state, p)))
            .append("\n")
    }
    sb.append("\nBuilt in the Accessible Cricket Simulator.")
    return sb.toString()
}

private fun shareTeam(context: Context, state: IplAuctionState) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "My IPL auction team")
        putExtra(Intent.EXTRA_TEXT, teamShareText(state, state.userTeamId))
    }
    val chooser = Intent.createChooser(send, "Share your team").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    runCatching { context.startActivity(chooser) }
}

private fun copyTeam(context: Context, state: IplAuctionState) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("IPL auction team", teamShareText(state, state.userTeamId)))
    Toast.makeText(context, "Your team is copied. You can paste it anywhere.", Toast.LENGTH_SHORT).show()
}

/** One tab per team, yours first: every player with his role and the price he was bought for. */
@Composable
private fun AllSquadsPage(state: IplAuctionState, onBack: () -> Unit) {
    val teams = teamsUserFirst(state)
    var tab by remember { mutableStateOf(0) }
    val teamId = teams.getOrElse(tab) { teams.first() }
    val squad = squadByPrice(state, teamId)
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Title("Squads of all ten teams")
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            teams.forEachIndexed { i, id ->
                val label = (if (i == 0) "Your team: " else "") + IplData.shortName(id)
                val on = i == tab
                if (on) {
                    Button(onClick = { tab = i }, modifier = Modifier.semantics { this.selected = true }) { Text(label) }
                } else {
                    OutlinedButton(onClick = { tab = i }, modifier = Modifier.semantics { this.selected = false }) { Text(label) }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "${IplData.team(teamId)?.name}: ${squad.size} players, ${squad.count { it.overseas }} overseas, " +
                "spent ${IplAuction.priceText(squad.sumOf { paidFor(state, it) })}, " +
                "purse left ${IplAuction.priceText(state.purse[teamId] ?: 0)}.",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (squad.isEmpty()) {
                item { Text("No players bought yet.", style = MaterialTheme.typography.bodyLarge) }
            }
            items(squad.size) { i ->
                val p = squad[i]
                Text(
                    text = "${i + 1}. ${p.player.name}, ${IplAuction.roleText(p)}" + (if (p.overseas) ", overseas" else "") +
                        ". Bought for ${IplAuction.priceText(paidFor(state, p))}.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 5.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

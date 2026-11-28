package com.cricketsim.ui.challenge

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.ChallengeBuilder
import com.cricketsim.logic.ChallengeData
import com.cricketsim.logic.ChallengeEntry
import com.cricketsim.logic.ChallengePlanner
import com.cricketsim.logic.CricketData
import com.cricketsim.logic.StadiumData
import com.cricketsim.persistence.ChallengeStore
import com.cricketsim.persistence.MatchSaveStore
import com.cricketsim.persistence.MatchSnapshot
import com.cricketsim.ui.custom.BackButton
import com.cricketsim.ui.custom.OptionButton
import com.cricketsim.ui.custom.PageColumn
import com.cricketsim.ui.custom.PageHeading
import com.cricketsim.ui.match.MatchScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home > Challenge mode (patch 161): today's five challenges, a page for each, and the match itself.
 *
 * The list opens at once. A new day's challenges are planned from the date, then BUILT one after another in the background (each needs whole innings played
 * by the computer, a few seconds): a row says "Preparing this challenge." until it is ready, and is announced when it becomes ready. Opening a challenge shows
 * the situation, the teams, the ground, the toss and your chance of winning at the start; Start challenge opens the match (no Playing XI or toss screens: the
 * game has decided both), and winning it completes the challenge. A match left half way is kept and offered as Resume challenge.
 *
 * ACCESSIBILITY as in Advanced Customisation: Back first on every page, then a heading; each challenge is one button that reads its title and its details;
 * changes of state are live regions.
 */
private sealed class CPage {
    object Overview : CPage()
    class Detail(val id: String) : CPage()
    class Play(val id: String, val snapshot: MatchSnapshot, val playStore: MatchSaveStore) : CPage()
}

private fun formatName(name: String): String = when (name) {
    "T10" -> "T10"
    "ODI" -> "ODI"
    else -> "T20"
}

private fun statusText(e: ChallengeEntry): String = when {
    e.status == "PREPARING" -> "Preparing this challenge."
    e.status == "UNAVAILABLE" -> "This challenge could not be prepared. Open Challenge mode again to try once more."
    e.result == "WON" -> "Completed."
    e.result == "LOST" -> "Not completed yet. You can try again."
    else -> "Not played yet."
}

@Composable
fun ChallengeFlow(onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ChallengeStore(context) }
    var data by remember { mutableStateOf<ChallengeData?>(null) }
    var page by remember { mutableStateOf<CPage>(CPage.Overview) }
    var message by remember { mutableStateOf<String?>(null) }

    // Load today's challenges (new ones when the day has changed), then build the ones that are not ready, one by one.
    LaunchedEffect(Unit) {
        var current = store.load()
        val today = ChallengePlanner.today()
        if (current.day != today || current.entries.isEmpty()) {
            withContext(Dispatchers.IO) { store.clearMatches(current) }
            current = ChallengeData(
                day = today, entries = withContext(Dispatchers.Default) { ChallengePlanner.plan(today) },
                completedAllTime = current.completedAllTime, attemptsAllTime = current.attemptsAllTime
            )
        } else {
            current = current.copy(entries = current.entries.map { if (it.status == "UNAVAILABLE") it.copy(status = "PREPARING") else it })
        }
        store.save(current)
        data = current
        for (entry in current.entries) {
            if (entry.status != "PREPARING") continue
            // Building plays whole innings: wait while a challenge is being played, so it never slows the match down.
            while (page is CPage.Play) delay(1000)
            val built = withContext(Dispatchers.Default) {
                try {
                    ChallengeBuilder.build(entry)
                } catch (e: Exception) {
                    null
                }
            }
            val updated = if (built != null) {
                withContext(Dispatchers.IO) { store.startStore(entry.id).save(built.snapshot) }
                entry.copy(status = "READY", title = built.title, description = built.description, winChance = built.winChance)
            } else {
                entry.copy(status = "UNAVAILABLE")
            }
            val latest = data ?: current
            val next = latest.copy(entries = latest.entries.map { if (it.id == entry.id) updated else it })
            data = next
            store.save(next)
            if (built != null) message = "Challenge ${next.entries.indexOfFirst { it.id == entry.id } + 1} is ready."
        }
    }

    val d = data
    val current = page
    when (current) {
        is CPage.Overview -> {
            BackHandler(onBack = onExit)
            PageColumn {
                BackButton(onExit)
                Spacer(modifier = Modifier.height(16.dp))
                PageHeading("Challenge mode")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Five new challenges every day. Win the match from the situation you are given. New challenges come at midnight.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (d != null) {
                    val done = d.entries.count { it.result == "WON" }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Today: $done of ${d.entries.size} completed. All time: ${d.completedAllTime} completed.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                message?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (d == null) {
                    Text("Loading today's challenges.", style = MaterialTheme.typography.bodyLarge)
                } else {
                    d.entries.forEachIndexed { i, e ->
                        val ready = e.status == "READY"
                        val title = if (ready) "Challenge ${i + 1}: ${e.title}" else "Challenge ${i + 1}"
                        val teams = "${CricketData.getTeamById(e.userTeamId)?.name ?: e.userTeamId} versus ${CricketData.getTeamById(e.opponentTeamId)?.name ?: e.opponentTeamId}"
                        val ground = StadiumData.getStadiumById(e.stadiumId)?.name ?: ""
                        OptionButton(title, "${formatName(e.formatName)}, $teams, $ground. ${statusText(e)}", ready, e.result == "WON") {
                            page = CPage.Detail(e.id)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }

        is CPage.Detail -> {
            val back = { page = CPage.Overview; message = null }
            BackHandler(onBack = back)
            val entry = d?.entries?.find { it.id == current.id }
            val number = (d?.entries?.indexOfFirst { it.id == current.id } ?: 0) + 1
            var hasProgress by remember(current.id) { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(current.id) { hasProgress = store.playStore(current.id).load() != null }
            PageColumn {
                BackButton(back)
                Spacer(modifier = Modifier.height(16.dp))
                if (entry == null || hasProgress == null) {
                    PageHeading("Challenge $number")
                    Text("Loading.", style = MaterialTheme.typography.bodyLarge)
                } else {
                    PageHeading("Challenge $number: ${entry.title}")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = entry.description, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusText(entry) + if (entry.attempts > 0) " Tries so far: ${entry.attempts}." else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    message?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    val begin: (Boolean) -> Unit = { resume ->
                        scope.launch {
                            val playStore = store.playStore(entry.id)
                            val snapshot = if (resume) playStore.load() else store.startStore(entry.id).load()
                            if (snapshot == null) {
                                message = "This challenge could not be opened. Please go back and try again."
                            } else {
                                if (!resume) {
                                    playStore.clear()
                                    data = store.recordAttempt(entry.id)
                                }
                                page = CPage.Play(entry.id, snapshot, playStore)
                            }
                        }
                    }
                    if (hasProgress == true) {
                        Button(onClick = { begin(true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Resume challenge") }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = { begin(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Start again from the beginning") }
                    } else {
                        Button(onClick = { begin(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text(if (entry.attempts > 0) "Try again" else "Start challenge")
                        }
                    }
                }
            }
        }

        is CPage.Play -> {
            val entry = d?.entries?.find { it.id == current.id }
            val number = (d?.entries?.indexOfFirst { it.id == current.id } ?: 0) + 1
            val services = LocalGameServices.current
            // The crowd of a match is there in a challenge too; the match screen stops it at the end, and leaving the page stops it as well.
            DisposableEffect(Unit) {
                services?.sound?.startCrowdAmbience()
                onDispose { services?.sound?.stopCrowdAmbience() }
            }
            val leave = { page = CPage.Detail(current.id) }
            MatchScreen(
                format = current.snapshot.state.format,
                stadium = current.snapshot.stadium,
                userTeam = current.snapshot.state.userTeam,
                opponentTeam = current.snapshot.state.opponentTeam,
                toss = current.snapshot.toss,
                onBack = leave,
                onMatchFinished = leave,
                resume = current.snapshot,
                saveStore = current.playStore,
                tournamentName = "Challenge $number" + (entry?.let { ": ${it.title}" } ?: ""),
                onMatchComplete = { finished ->
                    val won = finished.winnerId == finished.userTeam.id
                    scope.launch {
                        data = store.recordResult(current.id, won)
                        message = if (won) "Challenge completed." else "Challenge not completed. You can try again."
                    }
                }
            )
        }
    }
}

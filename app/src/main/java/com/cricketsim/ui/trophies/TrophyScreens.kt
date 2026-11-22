package com.cricketsim.ui.trophies

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.MatchState
import com.cricketsim.logic.TrophyGroup
import com.cricketsim.logic.TrophyRecord
import com.cricketsim.logic.Trophies
import com.cricketsim.persistence.TrophyStore
import com.cricketsim.ui.custom.BackButton
import com.cricketsim.ui.custom.OptionButton
import com.cricketsim.ui.custom.PageColumn
import com.cricketsim.ui.custom.PageHeading
import com.cricketsim.ui.match.ScorecardScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home > Trophies (patch 140): every tournament the player has won in the game, excluding Career mode (which keeps its own trophies in its Career tab).
 *
 * The list has one button for each competition won, with how many times ("1 trophy"). A competition won once opens its Final's scorecard straight away; one
 * won several times opens the list of its wins, and each win opens its own Final's scorecard. The scorecard is the normal scorecard screen.
 * On opening, tournaments already saved on the phone whose champion was the player's team are added first ([TrophyStore.backfill]), so earlier wins show.
 */
@Composable
fun TrophiesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var trophies by remember { mutableStateOf<List<TrophyRecord>?>(null) }
    var card by remember { mutableStateOf<MatchState?>(null) }

    LaunchedEffect(Unit) {
        val store = TrophyStore(context)
        store.backfill()
        trophies = store.load()
    }

    val shown = card
    if (shown != null) {
        BackHandler { card = null }
        ScorecardScreen(state = shown, onBack = { card = null })
        return
    }

    BackHandler(onBack = onBack)
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Trophies")
        Spacer(modifier = Modifier.height(8.dp))
        val list = trophies
        if (list == null) {
            Text("Loading your trophies.", style = MaterialTheme.typography.bodyLarge)
        } else {
            TrophyList(
                trophies = list,
                emptyText = "You have not won a trophy yet. Win a tournament with your team and it will be kept here, with the scorecard of the final. Trophies won in Career mode are in the Career tab of your career.",
                onViewScorecard = { card = it }
            )
        }
    }
}

private fun dateText(millis: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(millis))

private fun describe(t: TrophyRecord): String {
    val parts = ArrayList<String>()
    parts.add("Won with ${t.teamName}.")
    if (t.note.isNotEmpty()) parts.add("${t.note}.")
    else if (t.wonAt > 0L) parts.add("${dateText(t.wonAt)}.")
    if (t.finalLabel.isNotEmpty()) parts.add("${t.finalLabel}.")
    if (t.finalSummary.isNotEmpty()) parts.add(t.finalSummary.trimEnd('.') + ".")
    parts.add(if (t.scorecard != null) "Tap to view the scorecard." else "No scorecard was kept for this one.")
    return parts.joinToString(" ")
}

/**
 * The trophies as a list of buttons, to be placed in a page of its own: used by Home > Trophies and by the Career tab's Trophies page. It does not draw a
 * Back button of its own for the list; when a competition with several wins is open it puts "Back to trophies" first.
 */
@Composable
fun TrophyList(trophies: List<TrophyRecord>, emptyText: String, onViewScorecard: (MatchState) -> Unit) {
    var openId by remember { mutableStateOf<String?>(null) }
    val groups = remember(trophies) { Trophies.groups(trophies) }
    val open: TrophyGroup? = groups.firstOrNull { it.tournamentId == openId }

    if (open != null) {
        OptionButton(title = "Back to trophies", description = "Return to the list of trophies.", enabled = true, emphasised = false, onClick = { openId = null })
        Spacer(modifier = Modifier.height(12.dp))
        PageHeading(open.name)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Won ${open.trophies.size} times. Choose one to see the scorecard of its final.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        open.trophies.forEach { t ->
            val scorecard = t.scorecard
            OptionButton(
                title = t.name,
                description = describe(t),
                enabled = scorecard != null,
                emphasised = false,
                onClick = { if (scorecard != null) onViewScorecard(scorecard) }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        return
    }

    if (trophies.isEmpty()) {
        Text(emptyText, style = MaterialTheme.typography.bodyLarge)
        return
    }
    Text("You have won ${Trophies.countText(trophies.size)}.", style = MaterialTheme.typography.bodyLarge)
    Spacer(modifier = Modifier.height(12.dp))
    groups.forEach { g ->
        val only = g.trophies.singleOrNull()
        val onlyCard = only?.scorecard
        OptionButton(
            title = g.name,
            description = if (only != null) describe(only) else "${Trophies.countText(g.trophies.size)}. Tap to see each one.",
            enabled = only == null || onlyCard != null,
            emphasised = false,
            onClick = {
                if (only == null) openId = g.tournamentId
                else if (onlyCard != null) onViewScorecard(onlyCard)
            }
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
}

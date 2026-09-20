package com.cricketsim.ui.match

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.BowlerStats
import com.cricketsim.logic.PendingDismissal
import com.cricketsim.logic.Player

/**
 * The three "who?" decisions a captain makes, each replacing the match
 * screen while it is pending (like the gesture surfaces do) rather than
 * floating over it as the web's modal dialogs do:
 *
 *   OpenerSelectionScreen — pick the striker, then the non-striker.
 *   NewBatsmanScreen      — after one of your batsmen is out.
 *   BowlerSelectionScreen — the opening bowler, and each new over.
 *
 * All three are plain single-swipe lists that pick-and-advance, the same
 * pattern as the setup flow — no separate confirm button. The web uses
 * drop-down selects plus a confirm button; a drop-down inside a modal is
 * a poor fit for TalkBack, and a single pick per screen is quicker. The
 * one undo is Back from the non-striker step to the striker step.
 *
 * Every row leads with the name, then the numbers that make the choice
 * meaningful, the same information the web puts beside each name:
 * batting and bowling ratings, and for bowlers their figures so far.
 * For bowlers it also says how many overs they have left where the
 * format caps overs, which is what a captain actually needs to know.
 *
 * NewBatsmanScreen puts the whole dismissal in its heading ("Wicket!
 * X is out for 12 runs off 9 balls. Caught, bowled by Y."), so the one
 * thing TalkBack reads on arrival is the news, then the score, then the
 * list. It replaces the match screen at exactly the moment a ball's
 * outcome would otherwise be announced, so it must carry that news.
 */

private data class PickRow(val label: String, val detail: String)

@Composable
private fun PlayerPickList(
    title: String,
    introLines: List<String>,
    rows: List<PickRow>,
    onPick: (Int) -> Unit,
    onBack: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        introLines.forEach { line ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(line, style = MaterialTheme.typography.bodyMedium)
        }
        if (onBack != null) {
            // Above a list of up to eleven, so it isn't eleven swipes away.
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(rows) { index, row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClickLabel = "Select ${row.label}",
                            role = Role.Button,
                            onClick = { onPick(index) }
                        )
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    Text(row.label, style = MaterialTheme.typography.titleMedium)
                    Text(row.detail, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun batsmanRow(player: Player): PickRow =
    PickRow(player.name, "Batting ${player.battingRating}, bowling ${player.bowlingRating}.")

@Composable
fun OpenerSelectionScreen(players: List<Player>, onConfirm: (striker: Player, nonStriker: Player) -> Unit) {
    var striker by remember { mutableStateOf<Player?>(null) }
    val chosenStriker = striker

    if (chosenStriker == null) {
        PlayerPickList(
            title = "Select your striker",
            introLines = listOf("Choose who faces the first ball."),
            rows = players.map { batsmanRow(it) },
            onPick = { index -> striker = players[index] }
        )
    } else {
        val remaining = players.filter { it.id != chosenStriker.id }
        PlayerPickList(
            title = "Select your non-striker",
            introLines = listOf("Striker: ${chosenStriker.name}."),
            rows = remaining.map { batsmanRow(it) },
            onPick = { index -> onConfirm(chosenStriker, remaining[index]) },
            onBack = { striker = null }
        )
    }
}

@Composable
fun NewBatsmanScreen(
    dismissal: PendingDismissal,
    scoreLine: String,
    players: List<Player>,
    onConfirm: (Player) -> Unit
) {
    PlayerPickList(
        title = "Wicket! ${MatchLines.dismissalSummary(dismissal)}",
        introLines = listOf(scoreLine, "Choose your next batsman."),
        rows = players.map { batsmanRow(it) },
        onPick = { index -> onConfirm(players[index]) }
    )
}

/**
 * @param maxOversPerBowler the format's per-bowler cap; Int.MAX_VALUE for
 *   Tests, in which case no "overs left" is spoken.
 */
@Composable
fun BowlerSelectionScreen(
    title: String,
    players: List<Player>,
    bowlerStats: List<BowlerStats>,
    maxOversPerBowler: Int,
    onConfirm: (Player) -> Unit
) {
    val rows = players.map { player ->
        val stats = bowlerStats.firstOrNull { it.playerId == player.id }
        val overs = stats?.overs ?: 0
        val balls = stats?.balls ?: 0
        val figures = "$overs.$balls overs, ${countOf(stats?.runsConceded ?: 0, "run")}, " +
            "${countOf(stats?.wickets ?: 0, "wicket")}."
        val overCap = if (maxOversPerBowler == Int.MAX_VALUE) {
            ""
        } else {
            " ${countOf(maxOf(maxOversPerBowler - overs, 0), "over")} left."
        }
        PickRow(player.name, "Bowling ${player.bowlingRating}. $figures$overCap")
    }
    PlayerPickList(
        title = title,
        introLines = listOf("Choose who bowls next."),
        rows = rows,
        onPick = { index -> onConfirm(players[index]) }
    )
}

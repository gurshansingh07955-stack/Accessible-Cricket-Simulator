package com.cricketsim.ui.match

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.cricketsim.logic.BowlingStyle
import com.cricketsim.logic.ImpactPlayer
import com.cricketsim.logic.MatchState
import com.cricketsim.logic.Player
import com.cricketsim.logic.PlayerRole

/**
 * The screens of the IPL's Impact Player rule (see logic/ImpactPlayer.kt for the rule itself):
 *
 *   ImpactSelectionScreen — before the toss: name your five substitutes.
 *   ImpactPromptScreen    — when your batter is out: use your Impact Player now, or not.
 *   ImpactUseScreen       — in the match: choose the Impact Player, then the player he replaces.
 *
 * Plain lists with one row per player and Back / Confirm above them, the same way as the other selection screens,
 * so a screen reader reaches the buttons without swiping through the whole squad first.
 */

private fun roleText(p: Player): String = when (p.role) {
    PlayerRole.BATSMAN -> "batter"
    PlayerRole.WICKETKEEPER -> "wicketkeeper"
    PlayerRole.ALL_ROUNDER -> "all-rounder"
    PlayerRole.BOWLER -> if (p.bowlingStyle == BowlingStyle.SPIN) "spin bowler" else "fast bowler"
}

private fun playerLine(p: Player, overseas: Boolean): String =
    "${p.name}, ${roleText(p)}. Batting ${p.battingRating}, bowling ${p.bowlingRating}." + (if (overseas) " Overseas." else "")

/** Before the toss: the five substitutes. */
@Composable
fun ImpactSelectionScreen(
    teamName: String,
    reserves: List<Player>,
    xi: List<Player>,
    overseasIds: List<String>,
    onConfirm: (List<Player>) -> Unit,
    onBack: () -> Unit
) {
    val need = minOf(ImpactPlayer.SUBSTITUTES, reserves.size)
    var chosen by remember { mutableStateOf<List<String>>(emptyList()) }
    val overseas = remember(overseasIds) { overseasIds.toSet() }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Name your Impact Players",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "$teamName: choose $need substitutes. During the match you may bring ONE of them on in place of any player " +
                "of your XI, when an over ends or a wicket falls, up to the end of the 14th over. The player who goes off " +
                "takes no further part. Your XI may never have more than four overseas players.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { chosen = ImpactPlayer.aiSubstitutes(reserves + xi, xi, overseas).map { it.id }.take(need) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Auto-pick") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onConfirm(reserves.filter { it.id in chosen }) },
            enabled = chosen.size == need,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Confirm") }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Chosen ${chosen.size} of $need.", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(reserves, key = { it.id }) { p ->
                val on = p.id in chosen
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = on,
                            role = Role.Checkbox,
                            onValueChange = { now ->
                                chosen = if (now) {
                                    if (chosen.size < need) chosen + p.id else chosen
                                } else {
                                    chosen - p.id
                                }
                            }
                        )
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    Text(playerLine(p, p.id in overseas), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** When a batter of yours is out and you still have your Impact Player. */
@Composable
fun ImpactPromptScreen(scoreLine: String, onUse: () -> Unit, onSkip: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "A wicket has fallen",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(scoreLine, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "You still have your Impact Player. This is a moment the rule allows you to use him.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onUse, modifier = Modifier.fillMaxWidth()) { Text("Use Impact Player") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
    }
}

/** In the match: who comes on, and who goes off. */
@Composable
fun ImpactUseScreen(
    state: MatchState,
    teamId: String,
    onConfirm: (inId: String, outId: String) -> Unit,
    onBack: () -> Unit
) {
    val subs = ImpactPlayer.substitutes(state, teamId)
    val outs = ImpactPlayer.replaceable(state, teamId)
    val overseas = remember(state.impact) { state.impact?.overseasIds.orEmpty().toSet() }
    var chosenIn by remember { mutableStateOf<String?>(null) }
    var chosenOut by remember { mutableStateOf<String?>(null) }
    val incoming = subs.firstOrNull { it.id == chosenIn }
    val outgoing = outs.firstOrNull { it.id == chosenOut }
    val allowed = incoming != null && outgoing != null && ImpactPlayer.overseasOk(state, teamId, outgoing, incoming)
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Impact Player",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Choose the Impact Player, then the player he replaces. The player who goes off takes no further part. " +
                "You can only do this once.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { if (incoming != null && outgoing != null) onConfirm(incoming.id, outgoing.id) },
            enabled = allowed,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Confirm Impact Player") }
        if (incoming != null && outgoing != null && !allowed) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Not allowed: your XI would have more than four overseas players. Choose an overseas player to replace.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Text(
                    "1. Who comes on",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }.padding(vertical = 8.dp)
                )
            }
            items(subs, key = { "in_" + it.id }) { p ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = p.id == chosenIn, role = Role.RadioButton, onClick = { chosenIn = p.id })
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    Text(playerLine(p, p.id in overseas), style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                Text(
                    "2. Who goes off",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }.padding(vertical = 8.dp)
                )
            }
            items(outs, key = { "out_" + it.id }) { p ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = p.id == chosenOut, role = Role.RadioButton, onClick = { chosenOut = p.id })
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    Text(playerLine(p, p.id in overseas), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

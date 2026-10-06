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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.cricketsim.logic.Player

/**
 * Picking the user's Super Over team: a short explanation, then three batters in batting
 * order (striker, non-striker, reserve), then the one bowler. Everything is a plain list of
 * buttons so a screen reader can walk it, and each step can be undone with Back.
 */
@Composable
fun SuperOverNominationScreen(
    introLines: List<String>,
    batters: List<Player>,
    bowlers: List<Player>,
    onConfirm: (batters: List<Player>, bowler: Player) -> Unit
) {
    var started by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<List<Player>>(emptyList()) }

    if (!started) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = "Super Over.",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            introLines.forEach { line ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(line, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { started = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Choose my Super Over team")
            }
        }
        return
    }

    if (chosen.size < 3) {
        val remaining = batters.filter { candidate -> chosen.none { it.id == candidate.id } }
        val role = when (chosen.size) {
            0 -> "Choose the striker, who faces the first ball."
            1 -> "Choose the non-striker."
            else -> "Choose the reserve batter, who comes in if a wicket falls."
        }
        val soFar = if (chosen.isEmpty()) emptyList() else listOf("Chosen so far: ${chosen.joinToString { it.name }}.")
        SuperOverPickList(
            title = "Super Over batter ${chosen.size + 1} of 3",
            introLines = listOf(role) + soFar,
            rows = remaining.map { it.name to "Batting ${it.battingRating}, bowling ${it.bowlingRating}." },
            onPick = { index -> chosen = chosen + remaining[index] },
            onBack = if (chosen.isNotEmpty()) ({ chosen = chosen.dropLast(1) }) else null
        )
    } else {
        SuperOverPickList(
            title = "Super Over bowler",
            introLines = listOf("Batters: ${chosen.joinToString { it.name }}.", "Choose the bowler who will bowl the whole over."),
            rows = bowlers.map { it.name to "Bowling ${it.bowlingRating}, ${it.bowlingStyle.name.lowercase().replace('_', ' ')}." },
            onPick = { index -> onConfirm(chosen, bowlers[index]) },
            onBack = { chosen = chosen.dropLast(1) }
        )
    }
}

@Composable
private fun SuperOverPickList(
    title: String,
    introLines: List<String>,
    rows: List<Pair<String, String>>,
    onPick: (Int) -> Unit,
    onBack: (() -> Unit)?
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
                            onClickLabel = "Select ${row.first}",
                            role = Role.Button,
                            onClick = { onPick(index) }
                        )
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    Text(row.first, style = MaterialTheme.typography.titleMedium)
                    Text(row.second, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

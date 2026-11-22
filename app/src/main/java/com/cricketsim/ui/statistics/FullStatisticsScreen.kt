package com.cricketsim.ui.statistics

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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.GameStatistics
import com.cricketsim.logic.GameStats
import com.cricketsim.persistence.GameStatsStore
import com.cricketsim.ui.custom.BackButton
import com.cricketsim.ui.custom.OptionButton
import com.cricketsim.ui.custom.PageColumn
import com.cricketsim.ui.custom.PageHeading

/**
 * Home > Full statistics (patch 141): the records of every match the player has played himself, in every mode except Career mode (see logic/GameStats.kt).
 *
 * Tabs: All, Test, ODI, T20 and T10, chosen from a short list of buttons the way the Career record chooses its tabs. A tab shows its overview, its
 * records, the fastest 50, 100, 150 and 200, and the leaders (most runs, wickets, hundreds and sixes). Back comes first, then the heading.
 */
@Composable
fun FullStatisticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<GameStats?>(null) }
    var tab by remember { mutableStateOf("All") }

    LaunchedEffect(Unit) { stats = GameStatsStore(context).load() }

    BackHandler(onBack = onBack)
    PageColumn {
        BackButton(onBack)
        Spacer(modifier = Modifier.height(16.dp))
        PageHeading("Full statistics")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "The records of every match you have played yourself, in every mode except Career mode. A match you handed to Simulate, and the matches the computer plays by itself, are not counted.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(12.dp))
        val loaded = stats
        if (loaded == null) {
            Text("Loading your statistics.", style = MaterialTheme.typography.bodyLarge)
            return@PageColumn
        }
        Text("Showing: $tab. Choose another tab:", style = MaterialTheme.typography.bodyLarge)
        GameStatistics.TABS.forEach { (label, _) ->
            Spacer(modifier = Modifier.height(6.dp))
            OptionButton(
                title = if (label == tab) "$label (showing)" else label,
                description = "Show the statistics for $label.",
                enabled = label != tab,
                emphasised = false,
                onClick = { tab = label }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        val view = GameStatistics.view(loaded, tab)
        if (view.matches == 0) {
            Text("No matches yet in this tab. Play a match and it will be counted here.", style = MaterialTheme.typography.bodyLarge)
            return@PageColumn
        }
        GameStatistics.sections(view).forEach { (title, lines) ->
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(6.dp))
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

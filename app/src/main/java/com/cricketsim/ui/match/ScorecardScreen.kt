package com.cricketsim.ui.match

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.MatchState

/**
 * The scorecard: the web's MatchScorecard (an innings switch, then
 * Batting / Bowling / Partnerships / Fall of Wickets tables) rebuilt for
 * a linear screen reader. Tables become one self-contained sentence per
 * row (wording in ScorecardLines), so each row is a single swipe stop
 * rather than a grid a user has to navigate cell by cell.
 *
 * Selectors are `Modifier.selectable(role = Role.Tab)` inside a
 * `selectableGroup()`, i.e. foundation's own accessible pattern, so
 * TalkBack announces them as tabs with selected state. They sit in a
 * horizontally scrollable row so long labels ("Partnerships") and large
 * font sizes don't get squeezed.
 *
 * Focus order is deliberate: Back to match FIRST (this is a long,
 * scrollable page, so Back is at the top rather than nine or more swipes
 * away at the bottom), then the heading, the innings switch (only once a
 * first innings exists), the innings total, the section switch, then
 * the rows. The rows are a LazyColumn, so the section switch and the
 * innings total stay put while the rows scroll.
 *
 * See ScorecardLines for two gaps inherited from the logic layer (no
 * maiden detection; no team score at fall of wicket) that are surfaced
 * honestly rather than papered over.
 */

private enum class CardSection(val label: String) {
    BATTING("Batting"),
    BOWLING("Bowling"),
    PARTNERSHIPS("Partnerships"),
    WICKETS("Wickets")
}

@Composable
fun ScorecardScreen(state: MatchState, onBack: () -> Unit) {
    val firstInnings = state.firstInningsData
    var showFirstInnings by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(CardSection.BATTING) }

    // firstInningsData only exists once the second innings is under way.
    val viewingFirst = showFirstInnings && firstInnings != null
    val innings = if (viewingFirst && firstInnings != null) firstInnings else state.currentInningsData

    val rows = when (section) {
        CardSection.BATTING -> ScorecardLines.battingRows(innings)
        CardSection.BOWLING -> ScorecardLines.bowlingRows(innings, if (viewingFirst) null else state.currentBowler.id)
        CardSection.PARTNERSHIPS -> ScorecardLines.partnershipRows(innings)
        CardSection.WICKETS -> ScorecardLines.wicketRows(innings)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to match") }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Scorecard",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (firstInnings != null) {
            TabStrip(
                labels = listOf("Second innings", "First innings"),
                selectedIndex = if (viewingFirst) 1 else 0,
                onSelect = { index -> showFirstInnings = index == 1 }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = ScorecardLines.inningsHeader(innings),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))

        TabStrip(
            labels = CardSection.values().map { it.label },
            selectedIndex = section.ordinal,
            onSelect = { index -> section = CardSection.values()[index] }
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(rows) { row ->
                Text(
                    text = row,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun TabStrip(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                textDecoration = if (selected) TextDecoration.Underline else null,
                modifier = Modifier
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

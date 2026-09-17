package com.cricketsim.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.cricketsim.logic.CricketData
import com.cricketsim.logic.Team

/**
 * Step 3 of the pre-match setup flow: pick the user's own team, then
 * the opponent, from CricketData.kt's 18 national squads.
 *
 * Same two-step drill-down shape as StadiumSelectionScreen (pick one
 * thing, then pick a second thing filtered by the first), and the same
 * reasoning applies: each step stays a single flat, single-swipe list.
 * The opponent list excludes whichever team was just chosen, so the
 * user can never end up facing their own side.
 */
@Composable
fun TeamSelectionScreen(onTeamsSelected: (userTeam: Team, opponentTeam: Team) -> Unit, onBack: () -> Unit) {
    var userTeam by remember { mutableStateOf<Team?>(null) }
    val chosenUserTeam = userTeam

    if (chosenUserTeam == null) {
        TeamListStep(
            title = "Choose your team",
            teams = remember { CricketData.getAllTeams() },
            onTeamSelected = { userTeam = it },
            onBack = onBack
        )
    } else {
        TeamListStep(
            title = "Choose the opponent",
            teams = remember(chosenUserTeam) { CricketData.getAllTeams().filter { it.id != chosenUserTeam.id } },
            onTeamSelected = { opponent -> onTeamsSelected(chosenUserTeam, opponent) },
            onBack = { userTeam = null }
        )
    }
}

@Composable
private fun TeamListStep(title: String, teams: List<Team>, onTeamSelected: (Team) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(teams) { team ->
                TeamRow(team = team, onClick = { onTeamSelected(team) })
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
private fun TeamRow(team: Team, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "Select ${team.name}",
                role = Role.Button,
                onClick = onClick
            )
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Text(text = team.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Batting ${team.battingRating} \u00b7 Bowling ${team.bowlingRating} \u00b7 Fielding ${team.fieldingRating}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

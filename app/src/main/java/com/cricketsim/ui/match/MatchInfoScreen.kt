package com.cricketsim.ui.match

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.ImpactPlayer
import com.cricketsim.logic.MatchFormat
import com.cricketsim.logic.MatchState
import com.cricketsim.logic.Stadium

/**
 * Match info: everything about the match in one place, opened from the match screen's Match info button.
 * Back to match comes FIRST (a long page, so it is never swipes away), then the Impact Player button when
 * the rule allows it right now (the Impact Player choice lives here, not on the match screen), then the
 * heading, the teams, the ground, the toss, the weather, both Playing XIs with captain, vice-captain and
 * wicketkeeper, and the Impact Player substitutes. Every fact is its own line, so each is one swipe stop.
 */
@Composable
fun MatchInfoScreen(state: MatchState, stadium: Stadium, onUseImpact: () -> Unit, onBack: () -> Unit) {
    val user = state.userTeam
    val opponent = state.opponentTeam
    val impact = state.impact
    val canUseImpact = ImpactPlayer.canUse(state, user.id)
    val overseas = impact?.overseasIds.orEmpty().toSet()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to match") }
        if (canUseImpact) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onUseImpact, modifier = Modifier.fillMaxWidth()) { Text("Impact Player") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Match info",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )

        MatchInfoHeading("Teams")
        MatchInfoLine("${user.name} (your team) versus ${opponent.name}.")
        val overs = if (state.format == MatchFormat.TEST) "" else " ${countOf(state.oversLimit, "over")} an innings."
        MatchInfoLine("Format: ${ScorecardText.formatName(state.format)}.$overs")
        MatchInfoLine("Right now: ${MatchLines.scoreLine(state)}")

        MatchInfoHeading("Venue")
        MatchInfoLine(stadium.name)
        MatchInfoLine("${stadium.city}, ${stadium.country}.")
        val homeNames = listOf(user, opponent).filter { it.id in stadium.homeTeamIds }.map { it.name }
        if (homeNames.isNotEmpty()) MatchInfoLine("Home ground of ${homeNames.joinToString(" and ")}.")
        MatchInfoLine("Pitch: ${ScorecardText.pitchName(stadium.pitchType)}.")
        MatchInfoLine(stadium.pitchDescription)
        MatchInfoLine("Boundaries: ${ScorecardText.boundaryName(stadium.boundarySize)}. Altitude: ${stadium.altitudeM} metres. ${ScorecardText.dewName(stadium.dewFactor).replaceFirstChar { it.uppercase() }}.")
        MatchInfoLine("Average T20 first innings score here: ${stadium.avgT20FirstInningsScore}.")
        MatchInfoLine(stadium.climate.description)

        MatchInfoHeading("Toss")
        MatchInfoLine(ScorecardText.tossLine(state))

        MatchInfoHeading("Weather today")
        val w = state.weather
        MatchInfoLine("${ScorecardText.weatherName(w.condition)}, ${w.temperatureC} degrees Celsius, humidity ${w.humidityPercent} percent, wind ${w.windKph} kilometres an hour.")
        MatchInfoLine("Chance of rain: ${w.rainChancePercent} percent.")
        MatchInfoLine(if (w.isDayNight) "A day-night match, under lights for at least the second innings." else "A day match.")

        listOf(user, opponent).forEach { team ->
            val lead = ScorecardText.leaders(team)
            MatchInfoHeading("${team.name} playing XI")
            MatchInfoLine(
                "Captain: ${lead.captain?.name ?: "not named"}. " +
                    "Vice-captain: ${lead.vice?.name ?: "not named"}. " +
                    "Wicketkeeper: ${lead.keeper?.name ?: "not named"}."
            )
            team.players.forEachIndexed { index, p ->
                val tags = buildList {
                    if (p.id == lead.captain?.id) add("captain")
                    if (p.id == lead.vice?.id) add("vice-captain")
                    if (p.id == lead.keeper?.id) add("wicketkeeper")
                    if (p.id in overseas) add("overseas player")
                }
                val tagText = if (tags.isEmpty()) "" else ", " + tags.joinToString(", ")
                MatchInfoLine("${index + 1}. ${p.name}, ${ScorecardText.roleName(p.role)}$tagText")
            }
        }

        if (impact != null) {
            MatchInfoHeading("Impact Players")
            MatchInfoLine(ImpactPlayer.statusLine(state, user.id))
            listOf(user, opponent).forEach { team ->
                val subs = ImpactPlayer.substitutes(state, team.id)
                MatchInfoLine("${team.name} substitutes: ${if (subs.isEmpty()) "none" else subs.joinToString(", ") { it.name }}.")
            }
            impact.swaps.filter { it.teamId != user.id }.forEach { MatchInfoLine(ImpactPlayer.announcement(it)) }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MatchInfoHeading(text: String) {
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun MatchInfoLine(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
}

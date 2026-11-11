package com.cricketsim.ui.career

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.CareerFinance
import com.cricketsim.logic.CareerHealth
import com.cricketsim.logic.CareerInternational
import com.cricketsim.logic.CareerProfile
import com.cricketsim.logic.CareerRecords
import com.cricketsim.logic.CareerSeason
import com.cricketsim.logic.TournamentState
import com.cricketsim.ui.custom.OptionButton
import com.cricketsim.ui.custom.PageHeading

/**
 * The career's Career tab (My Player, parts eleven to sixteen): this season's objectives, the training focus, fitness, the rankings, the career record,
 * the honours and retirement; for a retired player the career summary comes first and the rest of the playing parts are left out.
 * Every part has a heading, then plain sentences, so a screen reader can jump from heading to heading.
 */
@Composable
internal fun CareerRecordTab(
    career: CareerProfile,
    seasons: List<TournamentState>?,
    rankLines: List<String>,
    onFocus: (String?) -> Unit,
    onSpecialism: (String?) -> Unit,
    onAffiliation: (String?) -> Unit,
    onRetire: () -> Unit
) {
    PageHeading("Career")
    Spacer(modifier = Modifier.height(8.dp))

    if (career.retired) {
        SectionHeading("Career summary")
        CareerHealth.summaryLines(career).forEach { BodyLine(it) }
    } else {
        SectionHeading("Season objectives")
        val objectives = if (seasons == null) emptyList() else CareerRecords.objectiveLines(career, seasons)
        if (objectives.isEmpty()) {
            BodyLine("Objectives appear when the season has loaded.")
        } else {
            objectives.forEach { BodyLine(it) }
            BodyLine("Every objective you reach gives +1 to the rating it is about when the season ends.")
        }

        SectionHeading("Fitness")
        BodyLine(CareerHealth.statusLine(career) ?: "Fitness: you are fit and well.")
        BodyLine("Injuries are more likely in Test cricket, for bowlers, after thirty and when you are tired. From 33 your ratings slowly fall with age.")

        SectionHeading("Training focus")
        BodyLine("The skill you work on grows faster after matches, the others a little slower. Now: ${career.trainingFocus ?: "balanced"}.")
        listOf<Pair<String?, String>>(
            null to "Balanced",
            "Batting" to "Batting",
            "Bowling" to "Bowling",
            "Fielding" to "Fielding"
        ).forEach { (value, label) ->
            val chosen = career.trainingFocus == value
            Spacer(modifier = Modifier.height(6.dp))
            OptionButton(
                if (chosen) "$label (chosen)" else label,
                if (value == null) "All three skills grow at the normal rate." else "$label grows faster, the other skills slower.",
                !chosen, false
            ) { onFocus(value) }
        }
    }

    if (!career.retired && !career.isInternational && career.internationalMatches == 0) {
        SectionHeading("International affiliation")
        BodyLine(
            "The national team you are aiming for. Now: ${career.represents ?: "your own country, ${career.country}"}. " +
                "Aiming for another country means a residency period: its selectors want twice as many state matches (${CareerInternational.MIN_STATE_MATCHES * 2}) before they look at you. " +
                "You can change it until your first international match."
        )
        val choices = listOf<Pair<String?, String>>(null to "Your own country, ${career.country}") +
            CareerInternational.nations().filter { it != career.country }.map { it to it }
        choices.forEach { (value, label) ->
            val chosen = career.represents == value
            Spacer(modifier = Modifier.height(6.dp))
            OptionButton(
                if (chosen) "$label (chosen)" else label,
                if (value == null) "Aim for the national team of your own country." else "Aim for the $value national team.",
                !chosen, false
            ) { onAffiliation(value) }
        }
    }
    if (!career.retired && career.atOrAboveState) {
        SectionHeading("Format specialism")
        BodyLine(
            "A specialist is preferred by the selectors in his format (a place in his favour) and less in the other (a place against). " +
                "A white-ball specialist also gets more franchise league offers; a Test specialist gets fewer. Now: ${career.specialism?.let { if (it == "Test") "Test specialist" else "white-ball specialist" } ?: "all formats"}."
        )
        listOf<Pair<String?, String>>(
            null to "All formats",
            "Test" to "Test specialist",
            "White-ball" to "White-ball specialist"
        ).forEach { (value, label) ->
            val chosen = career.specialism == value
            Spacer(modifier = Modifier.height(6.dp))
            OptionButton(
                if (chosen) "$label (chosen)" else label,
                when (value) {
                    "Test" -> "Test series are yours; one-day and T20 matches and leagues are harder to get into."
                    "White-ball" -> "One-day and T20 matches and leagues are yours; Test series are harder to get into."
                    else -> "No preference: every format is judged the same."
                },
                !chosen, false
            ) { onSpecialism(value) }
        }
    }
    if (!career.retired && career.isInternational) {
        SectionHeading("National captaincy")
        BodyLine(CareerInternational.captaincyLine(career))
    }

    SectionHeading("Rankings")
    if (rankLines.isEmpty()) BodyLine("Working out your rankings.") else rankLines.forEach { BodyLine(it) }

    SectionHeading("Career record")
    val record = CareerRecords.recordLines(career)
    if (record.isEmpty()) BodyLine("No matches yet. Your record grows with every match you play.") else record.forEach { BodyLine(it) }

    SectionHeading("Honours")
    val honours = career.honours.orEmpty()
    if (honours.isEmpty()) BodyLine("None yet. Win a competition or be Player of the Series.") else honours.reversed().forEach { BodyLine(it) }

    SectionHeading("Earnings")
    CareerFinance.lines(career).forEach { BodyLine(it) }

    SectionHeading("In the news")
    val news = career.media.orEmpty()
    if (news.isEmpty()) BodyLine("Nothing yet. The papers write about you when a competition ends.") else news.reversed().forEach { BodyLine(it) }

    if (!career.retired) {
        SectionHeading("Retirement")
        BodyLine("You are ${career.age}. Players usually retire between 36 and 42, earlier if their ratings fall.")
        var confirm by remember { mutableStateOf(false) }
        val seasonDone = seasons != null && seasons.isNotEmpty() && seasons.all { CareerSeason.isComplete(it) }
        Spacer(modifier = Modifier.height(6.dp))
        if (!confirm) {
            OptionButton(
                "Retire from cricket",
                if (seasonDone) "End your career and see your career summary." else "You can retire when the season is finished.",
                seasonDone, false
            ) { confirm = true }
        } else {
            BodyLine("Are you sure? Retiring ends your career for good.")
            OptionButton("Yes, retire now", "Your career ends and the summary appears here.", true, true) {
                confirm = false
                onRetire()
            }
            Spacer(modifier = Modifier.height(6.dp))
            OptionButton("No, keep playing", "Go back to your career.", true, false) { confirm = false }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun BodyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(4.dp))
}

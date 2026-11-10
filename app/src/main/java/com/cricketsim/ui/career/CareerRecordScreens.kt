package com.cricketsim.ui.career

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.CareerProfile
import com.cricketsim.logic.CareerRecords
import com.cricketsim.logic.TournamentState
import com.cricketsim.ui.custom.OptionButton
import com.cricketsim.ui.custom.PageHeading

/**
 * The career's Career tab (My Player, part eleven): this season's objectives, the training focus, the rankings, the career record and the honours.
 * Every part has a heading, then plain sentences, so a screen reader can jump from heading to heading.
 */
@Composable
internal fun CareerRecordTab(
    career: CareerProfile,
    seasons: List<TournamentState>?,
    rankLines: List<String>,
    onFocus: (String?) -> Unit
) {
    PageHeading("Career")
    Spacer(modifier = Modifier.height(8.dp))

    SectionHeading("Season objectives")
    val objectives = if (seasons == null) emptyList() else CareerRecords.objectiveLines(career, seasons)
    if (objectives.isEmpty()) {
        BodyLine("Objectives appear when the season has loaded.")
    } else {
        objectives.forEach { BodyLine(it) }
        BodyLine("Every objective you reach gives +1 to the rating it is about when the season ends.")
    }

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

    SectionHeading("Rankings")
    if (rankLines.isEmpty()) BodyLine("Working out your rankings.") else rankLines.forEach { BodyLine(it) }

    SectionHeading("Career record")
    val record = CareerRecords.recordLines(career)
    if (record.isEmpty()) BodyLine("No matches yet. Your record grows with every match you play.") else record.forEach { BodyLine(it) }

    SectionHeading("Honours")
    val honours = career.honours.orEmpty()
    if (honours.isEmpty()) BodyLine("None yet. Win a competition or be Player of the Series.") else honours.reversed().forEach { BodyLine(it) }
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

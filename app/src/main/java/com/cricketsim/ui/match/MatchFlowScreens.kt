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
import com.cricketsim.logic.MatchFormat
import com.cricketsim.logic.MatchState

/**
 * The full-screen "the match has stopped" moments: rain, the innings
 * break, the end of the match, and the confirmation before leaving a
 * match. Each replaces the match screen (like the gesture and selection
 * screens do) and has the same shape on purpose — a heading that carries
 * the news, a few plain lines, then the buttons — so TalkBack lands on
 * the important thing first and the layout is instantly familiar.
 *
 * The heading is where the news goes because it's what TalkBack reads on
 * arrival: "Innings break. Target is 165.", "Match completed. India won
 * by 5 wickets.". The lines under it are the detail.
 *
 * RainDelayScreen follows the web's RainDelayDialog wording and
 * behaviour: it blocks play until Resume, and explains what changed
 * (overs cut, revised target) in plain language rather than showing the
 * raw resource-percentage numbers. Its detail is ONE paragraph rather
 * than several lines, so it is a single swipe stop. The web auto-focuses
 * the Resume button; here focus is deliberately left on the heading, so
 * a TalkBack user hears what happened before they can dismiss it.
 *
 * InningsBreakScreen and MatchResultScreen are additions: the web only
 * announces "Innings Break. Target is N." and shows a bare "Match
 * Completed" page. Here the break also says how the chase looks and
 * which side you're on, and the result also gives both innings totals
 * and the last ball, which would otherwise never be heard (the result
 * screen replaces the match screen at the very moment the final ball's
 * outcome would have been announced).
 *
 * ConfirmLeaveScreen guards the button that takes you out of the match.
 * The match is autosaved after every change, so leaving no longer throws
 * anything away — but it still ends the current session and drops you on
 * the first screen, so it asks first, and says plainly that the match is
 * safe and how to get back to it. "Keep playing" is FIRST, so it is the
 * default an unsure user lands on, and the heading is a question.
 */

@Composable
private fun InfoScreen(title: String, lines: List<String>, actions: List<Pair<String, () -> Unit>>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        lines.forEach { line ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(line, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(modifier = Modifier.height(24.dp))
        actions.forEachIndexed { index, (label, onClick) ->
            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
    }
}

@Composable
fun RainDelayScreen(
    format: MatchFormat,
    newOversLimit: Int,
    innings: Int,
    dlsRevised: Boolean,
    revisedTarget: Int?,
    onResume: () -> Unit
) {
    val detail = buildList {
        add(
            if (innings == 1) {
                "The heavens have opened and the covers are on. The ground staff expect this innings to be shortened."
            } else {
                "The heavens have opened and the covers are on mid-chase. The ground staff expect this innings to be shortened."
            }
        )
        val cutNote = if (format == MatchFormat.T20 || format == MatchFormat.ODI) " (down from the original limit)." else "."
        add("When play resumes, this innings will be capped at ${countOf(newOversLimit, "over")}$cutNote")
        if (dlsRevised && revisedTarget != null) {
            add("Under the Duckworth-Lewis-Stern method, the revised target is now ${countOf(revisedTarget, "run")} to win.")
        }
        if (innings == 1) {
            add("The chasing target will be finalized, and revised by DLS if needed, once this innings is complete.")
        }
    }.joinToString(" ")

    InfoScreen(
        title = "Rain stops play.",
        lines = listOf(detail),
        actions = listOf("Resume play" to onResume)
    )
}

@Composable
fun InningsBreakScreen(state: MatchState, lastBall: String, onStart: () -> Unit, onScorecard: () -> Unit) {
    val targetWord = if (state.dlsRevised) "DLS-revised target" else "Target"
    val first = state.firstInningsData
    val lines = listOfNotNull(
        first?.let { "${it.battingTeamName} scored ${it.totalRuns} for ${it.totalWickets} in ${it.totalOvers}.${it.totalBalls} overs." },
        if (lastBall.isNotEmpty()) "Last ball: $lastBall" else null,
        MatchLines.runsNeededLine(state),
        MatchLines.requiredRunRateLine(state),
        if (state.battingTeam.id == state.userTeam.id) "You are batting next." else "You are bowling next."
    )
    InfoScreen(
        title = "Innings break. $targetWord is ${state.target}.",
        lines = lines,
        actions = listOf(
            "Start second innings" to onStart,
            "Scorecard" to onScorecard
        )
    )
}

@Composable
fun MatchResultScreen(
    resultText: String,
    summaryLines: List<String>,
    lastBall: String?,
    onScorecard: () -> Unit,
    onHome: () -> Unit
) {
    val lines = summaryLines + listOfNotNull(lastBall?.let { "Last ball: $it" })
    InfoScreen(
        title = "Match completed. $resultText",
        lines = lines,
        actions = listOf(
            "View scorecard" to onScorecard,
            "Return to home" to onHome
        )
    )
}

@Composable
fun ConfirmLeaveScreen(onStay: () -> Unit, onLeave: () -> Unit) {
    InfoScreen(
        title = "Save and leave this match?",
        lines = listOf(
            "Your match is saved automatically. You can pick it up again from the first screen with Resume saved match."
        ),
        actions = listOf(
            "Keep playing" to onStay,
            "Save and leave" to onLeave
        )
    )
}

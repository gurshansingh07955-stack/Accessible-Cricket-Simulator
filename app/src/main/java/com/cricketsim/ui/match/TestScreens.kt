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

/**
 * The pop-ups of a five-day Test (logic/TestMatch.kt): lunch, tea and stumps, the start of the next session or day,
 * the innings break, the follow-on choice and the declaration check. The same words are spoken as each one appears.
 * [actionsFirst] puts the buttons above the text for a decision, so a screen reader reaches them first.
 */
@Composable
private fun TestPopup(
    title: String,
    lines: List<String>,
    actions: List<Pair<String, () -> Unit>>,
    actionsFirst: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        if (actionsFirst) {
            actions.forEachIndexed { index, (label, onClick) ->
                if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        lines.forEach { line ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(line, style = MaterialTheme.typography.bodyLarge)
        }
        if (!actionsFirst) {
            Spacer(modifier = Modifier.height(24.dp))
            actions.forEachIndexed { index, (label, onClick) ->
                if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
    }
}

/** Lunch, tea or stumps, and then the start of the next session or day. */
@Composable
fun TestBreakScreen(
    title: String,
    lines: List<String>,
    continueLabel: String,
    onContinue: () -> Unit,
    onScorecard: () -> Unit
) {
    TestPopup(
        title = title,
        lines = lines,
        actions = listOf(continueLabel to onContinue, "Scorecard" to onScorecard)
    )
}

/** Between two innings of a Test. */
@Composable
fun TestInningsBreakScreen(
    title: String,
    lines: List<String>,
    startLabel: String,
    onStart: () -> Unit,
    onScorecard: () -> Unit
) {
    TestPopup(
        title = title,
        lines = lines,
        actions = listOf(startLabel to onStart, "Scorecard" to onScorecard)
    )
}

/** The side that batted first leads by 200 or more: enforce the follow-on or bat again. */
@Composable
fun TestFollowOnScreen(lines: List<String>, onEnforce: () -> Unit, onBatAgain: () -> Unit) {
    TestPopup(
        title = "Follow-on",
        lines = lines,
        actions = listOf("Enforce the follow-on" to onEnforce, "Bat again" to onBatAgain),
        actionsFirst = true
    )
}

/** Asked before a declaration, so a stray tap cannot end the innings. */
@Composable
fun TestDeclareConfirmScreen(text: String, onDeclare: () -> Unit, onCancel: () -> Unit) {
    TestPopup(
        title = "Declare the innings?",
        lines = listOf(text),
        actions = listOf("Yes, declare" to onDeclare, "No, keep batting" to onCancel),
        actionsFirst = true
    )
}

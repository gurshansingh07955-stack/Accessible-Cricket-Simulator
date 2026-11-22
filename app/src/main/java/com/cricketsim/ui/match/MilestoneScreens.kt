package com.cricketsim.ui.match

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.Milestones
import kotlinx.coroutines.delay

/**
 * The pop-up for a career milestone (patch 139; logic/Milestones.kt): the heading ("Fifty!", "Hat-trick!"), the figures, and one OK button.
 * As it appears the crowd applauds (the press conference's applause) and, a moment after the ball's own announcement so it does not cut it off,
 * the same words are spoken. Pressing OK (or Back) carries on with the match.
 */
@Composable
fun MilestoneScreen(milestone: Milestones.Milestone, onOk: () -> Unit) {
    val services = LocalGameServices.current
    LaunchedEffect(milestone) {
        services?.sound?.playPressRoom("press_applause")
        delay(1300L)
        services?.announceSpoken(milestone.spoken)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = milestone.title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(12.dp))
        milestone.lines.forEach { line ->
            Text(text = line, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOk, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text("OK") }
    }
}

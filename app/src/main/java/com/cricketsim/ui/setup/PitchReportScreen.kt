package com.cricketsim.ui.setup

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.MatchWeatherCache
import com.cricketsim.logic.PitchReport
import com.cricketsim.logic.Stadium
import kotlinx.coroutines.delay

/**
 * The pitch report (patch 134), the screen before the toss in every match that has a ground. The report is written from the ground the match is played at and the
 * weather rolled for it (logic/PitchReport.kt; nothing random but the weather, which the match then keeps), shown as text a screen reader can read line by line, and
 * spoken by the two commentators in turn (the same recorded voices as the match commentary; the commentary setting decides which of them speak). The crowd, which
 * the toss page starts, is already running. Back first, then "Continue to the toss", then the report. Leaving the screen stops the commentators.
 */
@Composable
fun PitchReportScreen(stadium: Stadium, onContinue: () -> Unit, onBack: () -> Unit) {
    val services = LocalGameServices.current
    val weather = remember(stadium.id) { MatchWeatherCache.forReport(stadium) }
    val report = remember(stadium.id, weather) { PitchReport.build(stadium, weather) }

    LaunchedEffect(report) {
        val sound = services?.sound ?: return@LaunchedEffect
        // The crowd starts here (the page that holds this screen already asks for it; this makes sure of it).
        if (services?.settings?.soundEffects == true) sound.startCrowdAmbience()
        delay(700L)
        report.voice.forEach { (category, lead) -> sound.enqueueCommentaryLine(category, lead) }
    }
    DisposableEffect(Unit) { onDispose { services?.sound?.stopCommentary() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Back") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Continue to the toss") }
        Spacer(modifier = Modifier.height(16.dp))
        Text(report.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(modifier = Modifier.height(8.dp))
        report.sections.forEach { section ->
            Text(section.heading, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(section.text, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

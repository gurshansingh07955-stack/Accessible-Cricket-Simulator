package com.cricketsim.ui.match

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.Anthems
import com.cricketsim.logic.CommentaryCategory
import com.cricketsim.logic.Team
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/**
 * The national anthems, between the toss and the first ball. For each team in turn, in the order
 * given (home side first): the commentators ask everyone to rise, then the team's ten-second
 * anthem plays. A team with no anthem recording in the audio pack is skipped, and if neither
 * has one the screen finishes at once. Skip anthems (or the system Back button) moves straight on.
 */
@Composable
fun AnthemScreen(teams: List<Team>, onFinished: () -> Unit) {
    val services = LocalGameServices.current
    val sound = services?.sound
    var status by remember { mutableStateOf("The national anthems are about to begin.") }
    var advanced by remember { mutableStateOf(false) }
    val finish by rememberUpdatedState {
        if (!advanced) {
            advanced = true
            sound?.stopAnthem()
            sound?.stopCommentary()
            onFinished()
        }
    }

    BackHandler(onBack = { finish() })

    LaunchedEffect(Unit) {
        if (services == null || sound == null || !services.settings.soundEffects) {
            finish()
            return@LaunchedEffect
        }
        var played = 0
        for (team in teams) {
            val anthem = Anthems.forTeam(team) ?: continue
            val file = Anthems.fileName(anthem.code)
            if (!sound.hasAnthem(file)) continue
            status = "National anthem of ${team.name}."
            services.announceSpoken(status)
            delay(1800)
            sound.enqueueCommentary(if (played == 0) CommentaryCategory.ANTHEM_FIRST else CommentaryCategory.ANTHEM_SECOND)
            played++
            // Let the commentators finish asking everyone to rise before the music starts.
            delay(600)
            var waited = 0
            while (sound.isCommentaryBusy() && waited < 20_000) {
                delay(200)
                waited += 200
            }
            val ended = CompletableDeferred<Unit>()
            if (sound.playAnthem(file) { ended.complete(Unit) }) ended.await()
            delay(700)
        }
        finish()
    }

    DisposableEffect(Unit) {
        onDispose {
            sound?.stopAnthem()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "National anthems",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
            Text("Skip anthems")
        }
    }
}

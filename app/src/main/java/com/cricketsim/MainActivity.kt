package com.cricketsim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cricketsim.ui.Screen
import com.cricketsim.ui.setup.FormatSelectionScreen
import com.cricketsim.ui.setup.PlayingXIPlaceholderScreen
import com.cricketsim.ui.setup.StadiumSelectionScreen
import com.cricketsim.ui.setup.TeamSelectionScreen

/**
 * Entry point. The logic layer (see PORTING_NOTES.md) is fully ported;
 * this hosts the start of the real gameplay UI (see UI_NOTES.md),
 * currently covering the first three steps of the pre-match setup flow
 * (format, stadium, then team selection). Everything past
 * PlayingXIPlaceholderScreen is not built yet.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CricketSimApp()
                }
            }
        }
    }
}

@Composable
fun CricketSimApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.FormatSelection) }

    when (val current = screen) {
        is Screen.FormatSelection -> FormatSelectionScreen(
            onFormatSelected = { format -> screen = Screen.StadiumSelection(format) }
        )
        is Screen.StadiumSelection -> StadiumSelectionScreen(
            onStadiumSelected = { stadium -> screen = Screen.TeamSelection(current.format, stadium) },
            onBack = { screen = Screen.FormatSelection }
        )
        is Screen.TeamSelection -> TeamSelectionScreen(
            onTeamsSelected = { userTeam, opponentTeam ->
                screen = Screen.PlayingXIPlaceholder(current.format, current.stadium, userTeam, opponentTeam)
            },
            onBack = { screen = Screen.StadiumSelection(current.format) }
        )
        is Screen.PlayingXIPlaceholder -> PlayingXIPlaceholderScreen(
            format = current.format,
            stadium = current.stadium,
            userTeam = current.userTeam,
            opponentTeam = current.opponentTeam,
            onBack = { screen = Screen.TeamSelection(current.format, current.stadium) }
        )
    }
}

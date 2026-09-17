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
import com.cricketsim.ui.setup.StadiumSelectionScreen
import com.cricketsim.ui.setup.TeamSelectionPlaceholderScreen

/**
 * Entry point. The logic layer (see PORTING_NOTES.md) is fully ported;
 * this hosts the start of the real gameplay UI (see UI_NOTES.md),
 * currently covering the first two steps of the pre-match setup flow
 * (format, then stadium selection). Everything past
 * TeamSelectionPlaceholderScreen is not built yet.
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
            onStadiumSelected = { stadium -> screen = Screen.TeamSelectionPlaceholder(current.format, stadium) },
            onBack = { screen = Screen.FormatSelection }
        )
        is Screen.TeamSelectionPlaceholder -> TeamSelectionPlaceholderScreen(
            format = current.format,
            stadium = current.stadium,
            onBack = { screen = Screen.StadiumSelection(current.format) }
        )
    }
}

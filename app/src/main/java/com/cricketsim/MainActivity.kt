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
import com.cricketsim.ui.setup.StadiumSelectionPlaceholderScreen

/**
 * Entry point. The logic layer (see PORTING_NOTES.md) is fully ported;
 * this hosts the start of the real gameplay UI (see UI_NOTES.md),
 * beginning with the pre-match setup flow's first step, format
 * selection. Everything past StadiumSelectionPlaceholderScreen is not
 * built yet.
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
            onFormatSelected = { format -> screen = Screen.StadiumSelectionPlaceholder(format) }
        )
        is Screen.StadiumSelectionPlaceholder -> StadiumSelectionPlaceholderScreen(
            format = current.format,
            onBack = { screen = Screen.FormatSelection }
        )
    }
}

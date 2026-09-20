package com.cricketsim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cricketsim.audio.GameServices
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.CricketData
import com.cricketsim.logic.Team
import com.cricketsim.ui.Screen
import com.cricketsim.ui.match.MatchScreen
import com.cricketsim.ui.settings.SettingsScreen
import com.cricketsim.ui.setup.FormatSelectionScreen
import com.cricketsim.ui.setup.PlayingXIScreen
import com.cricketsim.ui.setup.StadiumSelectionScreen
import com.cricketsim.ui.setup.TeamSelectionScreen
import com.cricketsim.ui.setup.TossScreen

/**
 * Entry point. The logic layer (see PORTING_NOTES.md) is fully ported;
 * this hosts the real gameplay UI (see UI_NOTES.md): the whole pre-match
 * setup flow (format, stadium, team, playing XI, toss), then the match
 * screen, which runs a real, user-controlled match — pitching, batting,
 * fielding, selections, scorecard, rain delays and the innings break —
 * against an AI opponent, with sound. The match screen itself is still a
 * first-slice scaffold (see MatchScreen.kt's own doc comment).
 *
 * It also owns the app-wide GameServices (settings + the sound engine),
 * created once here and handed to every screen through LocalGameServices.
 * The sound engine is paused when the app leaves the foreground (so the
 * crowd doesn't keep playing behind another app) and released when the
 * activity is really finishing.
 */
class MainActivity : ComponentActivity() {

    private var services: GameServices? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gameServices = GameServices(applicationContext)
        services = gameServices
        setContent {
            CompositionLocalProvider(LocalGameServices provides gameServices) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        CricketSimApp()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        services?.sound?.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        services?.sound?.onAppBackgrounded()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) services?.release()
    }
}

/**
 * Auto-picks the AI opponent's Playing XI (CricketData.autoSelectPlayingXI)
 * and builds the finalized 11-player Team from it — the AI side never
 * gets a selection screen, matching the original design intent ("The
 * AI opponent gets an auto-picked XI").
 */
private fun autoPickOpponentXI(opponentTeam: Team): Team {
    val auto = CricketData.autoSelectPlayingXI(opponentTeam)
    return CricketData.buildMatchSquad(
        fullTeam = opponentTeam,
        selectedPlayerIds = auto.players.map { it.id },
        captainId = auto.captainId,
        viceCaptainId = auto.viceCaptainId,
        wicketkeeperId = auto.wicketkeeperId
    )
}

@Composable
fun CricketSimApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.FormatSelection) }

    when (val current = screen) {
        is Screen.FormatSelection -> FormatSelectionScreen(
            onFormatSelected = { format -> screen = Screen.StadiumSelection(format) },
            onOpenSettings = { screen = Screen.Settings(returnTo = current) }
        )
        is Screen.Settings -> SettingsScreen(
            onBack = { screen = current.returnTo }
        )
        is Screen.StadiumSelection -> StadiumSelectionScreen(
            onStadiumSelected = { stadium -> screen = Screen.TeamSelection(current.format, stadium) },
            onBack = { screen = Screen.FormatSelection }
        )
        is Screen.TeamSelection -> TeamSelectionScreen(
            onTeamsSelected = { userTeam, opponentTeam ->
                screen = Screen.PlayingXISelection(current.format, current.stadium, userTeam, opponentTeam)
            },
            onBack = { screen = Screen.StadiumSelection(current.format) }
        )
        is Screen.PlayingXISelection -> PlayingXIScreen(
            userTeam = current.userTeam,
            onXIConfirmed = { finalUserTeam ->
                val finalOpponentTeam = autoPickOpponentXI(current.opponentTeam)
                screen = Screen.TossSelection(current.format, current.stadium, finalUserTeam, finalOpponentTeam)
            },
            onBack = { screen = Screen.TeamSelection(current.format, current.stadium) }
        )
        is Screen.TossSelection -> TossScreen(
            userTeam = current.userTeam,
            opponentTeam = current.opponentTeam,
            onTossComplete = { toss ->
                screen = Screen.Match(current.format, current.stadium, current.userTeam, current.opponentTeam, toss)
            },
            onBack = { screen = Screen.TeamSelection(current.format, current.stadium) }
        )
        is Screen.Match -> MatchScreen(
            format = current.format,
            stadium = current.stadium,
            userTeam = current.userTeam,
            opponentTeam = current.opponentTeam,
            toss = current.toss,
            // Abandons the match and returns to the toss.
            onBack = { screen = Screen.TossSelection(current.format, current.stadium, current.userTeam, current.opponentTeam) },
            // A finished match: back to the start, ready for a new one.
            onMatchFinished = { screen = Screen.FormatSelection }
        )
    }
}

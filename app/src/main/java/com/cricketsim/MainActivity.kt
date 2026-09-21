package com.cricketsim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cricketsim.audio.GameServices
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.CricketData
import com.cricketsim.logic.Team
import com.cricketsim.persistence.MatchSnapshot
import com.cricketsim.persistence.summary
import com.cricketsim.ui.Screen
import com.cricketsim.ui.home.AboutScreen
import com.cricketsim.ui.home.HomeScreen
import com.cricketsim.ui.match.MatchScreen
import com.cricketsim.ui.settings.SettingsScreen
import com.cricketsim.ui.setup.FormatSelectionScreen
import com.cricketsim.ui.setup.PlayingXIScreen
import com.cricketsim.ui.setup.StadiumSelectionScreen
import com.cricketsim.ui.setup.TeamSelectionScreen
import com.cricketsim.ui.setup.TossScreen
import com.cricketsim.ui.theme.CricketTheme

/**
 * Entry point. The logic layer (see PORTING_NOTES.md) is fully ported;
 * this hosts the real gameplay UI (see UI_NOTES.md): the home screen (Play
 * match, Resume match, Settings, About), the whole pre-match setup flow
 * (format, stadium, team, playing XI, toss), then the match screen, which
 * runs a real, user-controlled match — pitching, batting, fielding,
 * selections, scorecard, rain delays and the innings break — against an AI
 * opponent, with sound, saved automatically so it can be resumed from the
 * home screen. The match screen itself is still a first-slice scaffold (see
 * MatchScreen.kt's own doc comment).
 *
 * It also owns the app-wide GameServices (settings, the sound engine and
 * the saved match), created once here and handed to every screen through
 * LocalGameServices. The sound engine is paused when the app leaves the
 * foreground (so the crowd doesn't keep playing behind another app) and
 * released when the activity is really finishing.
 */
class MainActivity : ComponentActivity() {

    private var services: GameServices? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gameServices = GameServices(applicationContext)
        services = gameServices
        setContent {
            CompositionLocalProvider(LocalGameServices provides gameServices) {
                CricketTheme {
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
    val services = LocalGameServices.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    // The saved match, if any, offered by Home's Resume match. Re-read every
    // time Home is (re)entered — including after leaving a match, whose last
    // autosave has just landed — and loaded off the main thread.
    var savedMatch by remember { mutableStateOf<MatchSnapshot?>(null) }
    val onHome = screen is Screen.Home
    LaunchedEffect(onHome) {
        if (onHome) savedMatch = services?.saves?.load()
    }

    when (val current = screen) {
        is Screen.Home -> HomeScreen(
            savedMatchSummary = savedMatch?.summary(),
            onPlay = { screen = Screen.FormatSelection },
            onResume = {
                savedMatch?.let { saved ->
                    screen = Screen.Match(
                        format = saved.state.format,
                        stadium = saved.stadium,
                        userTeam = saved.state.userTeam,
                        opponentTeam = saved.state.opponentTeam,
                        toss = saved.toss,
                        resume = saved
                    )
                }
            },
            onSettings = { screen = Screen.Settings(returnTo = current) },
            onAbout = { screen = Screen.About }
        )
        is Screen.About -> AboutScreen(
            onBack = { screen = Screen.Home }
        )
        is Screen.FormatSelection -> FormatSelectionScreen(
            onFormatSelected = { format -> screen = Screen.StadiumSelection(format) },
            onBack = { screen = Screen.Home }
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
            resume = current.resume,
            // The match is autosaved as it goes, so leaving is safe: back to
            // Home, where Resume match picks it up again.
            onBack = { screen = Screen.Home },
            // A finished match (its save is already cleared): back to Home.
            onMatchFinished = { screen = Screen.Home }
        )
    }
}

package com.cricketsim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import com.cricketsim.logic.Anthems
import com.cricketsim.logic.CricketData
import com.cricketsim.logic.Team
import com.cricketsim.persistence.MatchSnapshot
import com.cricketsim.persistence.summary
import com.cricketsim.ui.Screen
import com.cricketsim.ui.home.AboutScreen
import com.cricketsim.ui.home.HomeScreen
import com.cricketsim.ui.match.AnthemScreen
import com.cricketsim.ui.match.MatchScreen
import com.cricketsim.ui.settings.SettingsScreen
import com.cricketsim.ui.tournament.TournamentFlow
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
 * released whenever the activity is destroyed — for ANY reason, not just
 * when it is finishing. GameServices is created fresh in every onCreate,
 * so an activity that is destroyed and recreated (a rotation, a system
 * kill of a backgrounded activity) would otherwise leave the old
 * instance's sound engine alive and playing with nothing left to stop it.
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
        services?.release()
        services = null
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

/**
 * WHAT THE SYSTEM BACK BUTTON DOES. Previously nothing intercepted it
 * anywhere in the app, so it always fell through to the default Activity
 * behavior — finish() — meaning back closed the whole app from any screen,
 * including mid-match. Every screen below except Home now gets a
 * BackHandler that does exactly what that screen's own on-screen back /
 * leave affordance already does, so system back and the in-UI button are
 * never two different behaviors. Home has none: back at the app's own
 * root screen is the one place the default "close the app" behavior is
 * actually correct.
 *
 * MatchScreen has its own further BackHandlers for its sub-views
 * (pitching, batting, fielding, scorecard, the settings overlay) and for
 * the ordinary match screen itself — see its own doc comment — so this
 * one BackHandler for Screen.Match only ever fires when none of those are
 * showing, which in practice does not happen while a match is mounted
 * (the ordinary screen's own BackHandler always wins first); it exists
 * only as a defensive fallback.
 *
 * THE CROWD STARTS AT THE TOSS. The stadium crowd bed begins on the toss
 * screen and carries straight on into the match, so the atmosphere is
 * there from the walk out. It is started here, keyed on whether the
 * toss-or-match part of the flow is showing, rather than inside either
 * screen: the toss screen is disposed the instant the match screen
 * appears, so a start/stop tied to the toss screen alone would cut the
 * crowd and restart it on every toss-to-match change. Here the key stays
 * true across that change, so the bed simply keeps playing; the match
 * screen's own audio director then takes over the mood (tension) and its
 * own start request is a no-op because the bed is already running. Going
 * back out of the toss, or leaving the match, makes the key false and the
 * bed fades out (stopping it is safe to repeat — the match screen also
 * stops it as it leaves).
 */
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

    // See "THE CROWD STARTS AT THE TOSS" in the doc comment above.
    val crowdWanted = screen is Screen.TossSelection || screen is Screen.Anthems || screen is Screen.Match
    val soundEffectsOn = services?.settings?.soundEffects ?: false
    LaunchedEffect(crowdWanted, soundEffectsOn) {
        val sound = services?.sound ?: return@LaunchedEffect
        if (!crowdWanted) {
            sound.stopCrowdAmbience()
        } else if (soundEffectsOn) {
            sound.startCrowdAmbience()
        }
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
            onTournament = { screen = Screen.Tournament },
            onSettings = { screen = Screen.Settings(returnTo = current) },
            onAbout = { screen = Screen.About }
        )
        is Screen.Tournament -> TournamentFlow(onExit = { screen = Screen.Home })
        is Screen.About -> {
            val onBack = { screen = Screen.Home }
            BackHandler(onBack = onBack)
            AboutScreen(onBack = onBack)
        }
        is Screen.FormatSelection -> {
            val onBack = { screen = Screen.Home }
            BackHandler(onBack = onBack)
            FormatSelectionScreen(
                onFormatSelected = { format -> screen = Screen.StadiumSelection(format) },
                onBack = onBack
            )
        }
        is Screen.Settings -> {
            val onBack = { screen = current.returnTo }
            BackHandler(onBack = onBack)
            SettingsScreen(onBack = onBack)
        }
        is Screen.StadiumSelection -> {
            val onBack = { screen = Screen.FormatSelection }
            BackHandler(onBack = onBack)
            StadiumSelectionScreen(
                onStadiumSelected = { stadium -> screen = Screen.TeamSelection(current.format, stadium) },
                onBack = onBack
            )
        }
        is Screen.TeamSelection -> {
            val onBack = { screen = Screen.StadiumSelection(current.format) }
            BackHandler(onBack = onBack)
            TeamSelectionScreen(
                onTeamsSelected = { userTeam, opponentTeam ->
                    screen = Screen.PlayingXISelection(current.format, current.stadium, userTeam, opponentTeam)
                },
                onBack = onBack
            )
        }
        is Screen.PlayingXISelection -> {
            val onBack = { screen = Screen.TeamSelection(current.format, current.stadium) }
            BackHandler(onBack = onBack)
            PlayingXIScreen(
                userTeam = current.userTeam,
                onXIConfirmed = { finalUserTeam ->
                    val finalOpponentTeam = autoPickOpponentXI(current.opponentTeam)
                    screen = Screen.TossSelection(current.format, current.stadium, finalUserTeam, finalOpponentTeam)
                },
                onBack = onBack
            )
        }
        is Screen.TossSelection -> {
            val onBack = { screen = Screen.TeamSelection(current.format, current.stadium) }
            BackHandler(onBack = onBack)
            TossScreen(
                userTeam = current.userTeam,
                opponentTeam = current.opponentTeam,
                onTossComplete = { toss ->
                    // The anthems come between the toss and the first ball (skipped if neither team has one).
                    screen = if (Anthems.hasAny(current.userTeam, current.opponentTeam)) {
                        Screen.Anthems(current.format, current.stadium, current.userTeam, current.opponentTeam, toss)
                    } else {
                        Screen.Match(current.format, current.stadium, current.userTeam, current.opponentTeam, toss)
                    }
                },
                onBack = onBack
            )
        }
        is Screen.Anthems -> {
            AnthemScreen(
                teams = Anthems.order(current.userTeam, current.opponentTeam, current.stadium),
                onFinished = {
                    screen = Screen.Match(current.format, current.stadium, current.userTeam, current.opponentTeam, current.toss)
                }
            )
        }
        is Screen.Match -> {
            // Defensive fallback only — see the doc comment above.
            BackHandler(onBack = { screen = Screen.Home })
            MatchScreen(
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
}

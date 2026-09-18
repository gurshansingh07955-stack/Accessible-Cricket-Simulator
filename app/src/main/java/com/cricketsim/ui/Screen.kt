package com.cricketsim.ui

import com.cricketsim.logic.MatchFormat
import com.cricketsim.logic.Stadium
import com.cricketsim.logic.Team
import com.cricketsim.logic.TossResult

/**
 * The app's screen graph, modeled as a plain sealed interface switched
 * on in a single composable (see MainActivity.CricketSimApp) rather
 * than pulling in the Navigation-Compose library. The setup flow is
 * short and linear (format -> stadium -> teams -> playing XI -> toss
 * -> match), so a hand-rolled `when` is simpler to reason about than a
 * nav graph for now; revisit if the flow grows branchy enough to need
 * one (e.g. deep-linking into an in-progress match once persistence
 * exists).
 */
sealed interface Screen {
    object FormatSelection : Screen

    data class StadiumSelection(val format: MatchFormat) : Screen

    data class TeamSelection(val format: MatchFormat, val stadium: Stadium) : Screen

    // userTeam/opponentTeam here are full squads — PlayingXIScreen
    // narrows the user's own down to an 11-player XI; the opponent's
    // XI is auto-picked (CricketData.autoSelectPlayingXI) once this
    // step completes, before advancing to TossSelection.
    data class PlayingXISelection(val format: MatchFormat, val stadium: Stadium, val userTeam: Team, val opponentTeam: Team) : Screen

    // Both teams here are already finalized 11-player XIs.
    data class TossSelection(val format: MatchFormat, val stadium: Stadium, val userTeam: Team, val opponentTeam: Team) : Screen

    // The match itself. See MatchScreen.kt's own doc comment — this is
    // currently a fully-automated AI-vs-AI preview loop, not the real
    // gesture-driven match screen.
    data class Match(
        val format: MatchFormat,
        val stadium: Stadium,
        val userTeam: Team,
        val opponentTeam: Team,
        val toss: TossResult
    ) : Screen
}

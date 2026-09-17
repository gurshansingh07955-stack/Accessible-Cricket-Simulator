package com.cricketsim.ui

import com.cricketsim.logic.MatchFormat
import com.cricketsim.logic.Stadium
import com.cricketsim.logic.Team

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

    // Placeholder for the next step in the setup flow (playing XI
    // selection). Carries everything chosen so far forward; will be
    // replaced by a real screen in a future session (see UI_NOTES.md).
    data class PlayingXIPlaceholder(
        val format: MatchFormat,
        val stadium: Stadium,
        val userTeam: Team,
        val opponentTeam: Team
    ) : Screen
}

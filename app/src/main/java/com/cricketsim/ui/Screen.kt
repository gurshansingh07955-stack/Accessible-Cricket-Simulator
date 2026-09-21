package com.cricketsim.ui

import com.cricketsim.logic.MatchFormat
import com.cricketsim.logic.Stadium
import com.cricketsim.logic.Team
import com.cricketsim.logic.TossResult
import com.cricketsim.persistence.MatchSnapshot

/**
 * The app's screen graph, modeled as a plain sealed interface switched
 * on in a single composable (see MainActivity.CricketSimApp) rather
 * than pulling in the Navigation-Compose library. The setup flow is
 * short and linear (format -> stadium -> teams -> playing XI -> toss
 * -> match), so a hand-rolled `when` is simpler to reason about than a
 * nav graph for now.
 */
sealed interface Screen {
    /** The front door: Play match, Resume match, Settings, About. */
    object Home : Screen

    /** What this game is and how it is played. */
    object About : Screen

    /** Step 1 of setting up a NEW match (reached from Home's Play match). */
    object FormatSelection : Screen

    /**
     * The settings page, opened from Home. `returnTo` is where Back goes.
     * (Inside a match, settings is an overlay on the match screen instead —
     * leaving the match screen would take the live match with it.)
     */
    data class Settings(val returnTo: Screen) : Screen

    data class StadiumSelection(val format: MatchFormat) : Screen

    data class TeamSelection(val format: MatchFormat, val stadium: Stadium) : Screen

    // userTeam/opponentTeam here are full squads — PlayingXIScreen
    // narrows the user's own down to an 11-player XI; the opponent's
    // XI is auto-picked (CricketData.autoSelectPlayingXI) once this
    // step completes, before advancing to TossSelection.
    data class PlayingXISelection(val format: MatchFormat, val stadium: Stadium, val userTeam: Team, val opponentTeam: Team) : Screen

    // Both teams here are already finalized 11-player XIs.
    data class TossSelection(val format: MatchFormat, val stadium: Stadium, val userTeam: Team, val opponentTeam: Team) : Screen

    /**
     * The match itself: a real, user-controlled match against an AI
     * opponent. See MatchScreen.kt's own doc comment. `resume` is set when
     * this is a saved match being picked back up rather than a new one;
     * the other fields are then just taken from it.
     */
    data class Match(
        val format: MatchFormat,
        val stadium: Stadium,
        val userTeam: Team,
        val opponentTeam: Team,
        val toss: TossResult,
        val resume: MatchSnapshot? = null
    ) : Screen
}

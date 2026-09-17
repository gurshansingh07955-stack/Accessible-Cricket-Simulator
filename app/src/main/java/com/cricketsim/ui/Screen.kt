package com.cricketsim.ui

import com.cricketsim.logic.MatchFormat

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

    // Placeholder for the next step in the setup flow (stadium picker).
    // Carries the chosen format forward; will be replaced by a real
    // screen in a future session (see UI_NOTES.md).
    data class StadiumSelectionPlaceholder(val format: MatchFormat) : Screen
}

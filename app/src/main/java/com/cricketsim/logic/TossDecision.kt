package com.cricketsim.logic

/**
 * Ported from an inline union type used in helpers/matchEngine.tsx's
 * TossResult and helpers/matchState.tsx's MatchState.tossDecision
 * (`"bat" | "bowl"`). Pulled out into its own file since
 * MatchEngine.kt's TossResult needs it now, ahead of the full
 * MatchState.kt port.
 */
enum class TossDecision { BAT, BOWL }

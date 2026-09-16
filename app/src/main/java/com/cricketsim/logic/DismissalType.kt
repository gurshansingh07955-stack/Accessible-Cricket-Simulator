package com.cricketsim.logic

/**
 * Ported from an inline union type used throughout helpers/matchState.tsx
 * and helpers/matchEngine.tsx (`"bowled" | "caught" | "lbw"`, appearing
 * on BallOutcome.dismissalType and PendingDismissal.dismissalType).
 * Pulled out into its own file since BallOutcome.kt needs it now, ahead
 * of the full MatchState.kt port.
 */
enum class DismissalType { BOWLED, CAUGHT, LBW }

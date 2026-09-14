package com.cricketsim.logic

/**
 * Ported from a type alias in helpers/matchEngine.tsx (`export type
 * PitchType = "BATTING" | "BOWLING" | "BALANCED" | "DUSTY" | "GREEN"`).
 * Pulled out into its own file since both StadiumData.kt and the
 * eventual MatchEngine.kt port depend on it, and StadiumData.kt was
 * ported first.
 */
enum class PitchType { BATTING, BOWLING, BALANCED, DUSTY, GREEN }

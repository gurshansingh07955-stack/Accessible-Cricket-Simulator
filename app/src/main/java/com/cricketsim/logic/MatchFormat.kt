package com.cricketsim.logic

/**
 * Ported from a type alias in helpers/matchEngine.tsx (`export type
 * MatchFormat = "TEST" | "T20" | "ODI"`). Pulled out into its own file
 * since WeatherSystem.kt needs it now and the eventual MatchEngine.kt /
 * MatchState.kt port will too, following the same split-out pattern as
 * PitchType.kt.
 */
enum class MatchFormat { TEST, T20, ODI }

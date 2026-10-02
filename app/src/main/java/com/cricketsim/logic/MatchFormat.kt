package com.cricketsim.logic

/**
 * Ported from a type alias in helpers/matchEngine.tsx (`export type
 * MatchFormat = "TEST" | "T20" | "ODI"`). Pulled out into its own file
 * since WeatherSystem.kt needs it now and the eventual MatchEngine.kt /
 * MatchState.kt port will too, following the same split-out pattern as
 * PitchType.kt.
 */
// T10, FIVE_OVERS and ONE_OVER are Android-only additions; the web app
// has only TEST, T20 and ODI. New entries go at the END so nothing that
// depends on the older ones' order changes.
enum class MatchFormat { TEST, T20, ODI, T10, FIVE_OVERS, ONE_OVER }

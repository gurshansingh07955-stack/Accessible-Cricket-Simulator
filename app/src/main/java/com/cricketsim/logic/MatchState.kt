package com.cricketsim.logic

/**
 * ⚠️ PARTIAL PORT — read before touching this file.
 *
 * helpers/matchState.tsx's real `MatchState` interface is much larger
 * than this (id, tossWinnerId, matchStatus, ballByBall history,
 * firstInningsData/secondInningsData/currentInningsData, opener/bowler-
 * selection flags, pendingDismissal, stadium/weather fields, rain-
 * interruption tracking, etc.) and depends on `InningsData` from
 * helpers/matchStats.tsx — a completely separate file that hasn't been
 * touched at all yet. Porting the full state machine (createNewMatch,
 * applyBallOutcome, switchInnings, selectBowler, changeBowler,
 * applyRainInterruption, and the rest) is a larger task for a future
 * session and needs matchStats.tsx ported first (or alongside it).
 *
 * This file exists ONLY because MatchEngine.kt's `simulateBall` and
 * `calculateWinProbability` need to read a handful of MatchState
 * fields right now. It contains exactly those fields — nothing more.
 * When the full port happens, expect this data class to be REPLACED
 * (not just added to) with the complete interface; don't assume this
 * shape is stable.
 */
data class MatchScore(val runs: Int, val wickets: Int, val overs: Int, val balls: Int)

/**
 * See the ⚠️ PARTIAL PORT warning above — this is only the subset of
 * fields helpers/matchEngine.tsx's simulateBall() and
 * calculateWinProbability() actually read, not the full interface.
 */
data class MatchState(
    val format: MatchFormat,
    val userTeam: Team,
    val battingTeam: Team,
    val bowlingTeam: Team,
    val currentInnings: Int, // 1 or 2
    val score: MatchScore,
    val target: Int?,
    // (Striker, Non-Striker) — matches the web source's `[Player, Player]` tuple.
    val currentBatsmen: Pair<Player, Player>,
    val currentBowler: Player,
    val fieldPlacements: List<FieldPlacement>,
    // The web source's type is non-optional (`oversLimit: number`), with
    // loadMatch() defensively backfilling it for old saved matches
    // predating the field — a JSON-deserialization concern that doesn't
    // apply the same way once Kotlin's type system guarantees the field
    // is always present, so this is modeled as non-null here.
    val oversLimit: Int
)

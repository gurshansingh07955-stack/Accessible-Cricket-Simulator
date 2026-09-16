package com.cricketsim.logic

/**
 * Ported from helpers/matchState.tsx's `BallOutcome` interface. Pulled
 * out into its own file since MatchEngine.kt's `simulateBall` needs it
 * as a return type now, ahead of the full MatchState.kt port (which
 * also depends on helpers/matchStats.tsx's `InningsData` — a
 * completely separate, not-yet-touched file — so is a larger task for
 * a future session).
 */
data class BallOutcome(
    val runs: Int,
    val isWicket: Boolean,
    val isWide: Boolean,
    val isNoBall: Boolean,
    val extraRuns: Int,
    val batsmanId: String,
    val bowlerId: String,
    val commentary: String,
    // Only set when isWicket is true. Determined once at ball-outcome
    // creation time and reused everywhere (scorecard, on-screen/TTS
    // commentary, AI voice commentary) so they never disagree.
    val dismissalType: DismissalType? = null,
    // Set for four/six (was it a clean shot or an edge/mishit that
    // still went for the boundary) and for caught dismissals (edge
    // caught vs. mistimed/skied and caught in the outfield). Determined
    // once in MatchEngine.simulateBall and reused by the on-screen
    // text, screen-reader announcement, and AI voice commentary so they
    // always agree.
    val isEdge: Boolean? = null,
    // Nullable per the web source's type, but in practice MatchEngine.kt
    // always sets this now (see simulateBall) — bowlingDecision is
    // required for both user AND AI-controlled bowling as of the
    // batting/bowling overhaul, so qualityTier is always available. The
    // web source's own doc comment here ("only set when bowled by the
    // user... undefined for AI") predates that change and is stale;
    // kept nullable anyway since nothing currently depends on it being
    // guaranteed non-null.
    val bowlingQualityTier: BowlingQualityTier? = null,
    val bowlingActualLength: DeliveryLength? = null
)

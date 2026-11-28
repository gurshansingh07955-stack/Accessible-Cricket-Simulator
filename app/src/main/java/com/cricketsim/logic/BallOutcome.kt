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
    val bowlingActualLength: DeliveryLength? = null,
    /** Who was dismissed when it was NOT the striker (a run out can take either batter); null = the striker. */
    val outBatsmanId: String? = null,
    /** The fielder involved in a run out, for the scorecard; null for every other ball. */
    val fielderName: String? = null,
    // For a catch: where the fielder took it, as a phrase ("at long-on", "behind the stumps").
    val fielderPosition: String? = null,
    /** The batter chose to leave the ball (no shot): it went to the keeper, or hit the stumps. */
    val leftAlone: Boolean = false,
    /** A catch was put down (by a computer fielder, or by the user's own attempt): the batter is NOT out. */
    val droppedCatch: Boolean = false,
    /** A fielding event other than a dropped catch: "missedStumping", "missedRunOut" or "stopped" (a run saved). Null otherwise. */
    val fieldingEvent: String? = null,
    /** Byes (patch 128): runs run off a legal ball that the batter did not hit and the keeper did not stop. Not off the bat and not charged to the bowler; [extraRuns] carries the same runs, so the team score and the partnership count them. */
    val byes: Int = 0,
    /** Leg byes: runs run off a legal ball that hit the batter's body. Counted like byes. */
    val legByes: Int = 0,
    /** Patch 142: the ball hit the batter's pad or body (an LBW the game decided, leg byes, or now and then a dot ball on the stumps), so the fielding side may appeal. */
    val padHit: Boolean = false,
    // Patch 158 (Cricbuzz-style commentary): how fast and where the ball was pitched, how it was played and where it went. Null on a ball from an older save.
    val speedKmh: Int? = null,
    val bowlingLine: BowlingLine? = null,
    val bowlingVariation: BowlingVariation? = null,
    val bowlingSwing: SwingType? = null,
    val shot: NamedShot? = null,
    val shotIntent: IntentDirection? = null,
    val shotTiming: BowlingQualityTier? = null,
    val shotSector: FieldingSector? = null,
    val shotAerial: Boolean = false,
    // Patch 159: the fielder going after the ball and where he stands ("at deep mid-wicket"), the same one manual running names for this ball.
    val chaserName: String? = null,
    val chaserPosition: String? = null
)

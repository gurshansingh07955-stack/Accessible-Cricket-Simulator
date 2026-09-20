package com.cricketsim.ui.match

import com.cricketsim.logic.FieldingSystem
import com.cricketsim.logic.MatchFormat
import com.cricketsim.logic.MatchState
import com.cricketsim.logic.Player
import kotlin.math.abs

/**
 * How the AI reads the match, ported from pages/match.tsx's
 * computeBattingSituationalBias, computeBowlingSituationalBias and
 * selectAiNextBatsman (plus the over-by-over tally behind the batting
 * one). They live in the web's match.tsx rather than helpers/ — the
 * web keeps them out of battingSystem.tsx/bowlingSystem.tsx so those
 * stay decoupled from MatchState — which is why they weren't part of the
 * logic-layer port. Every consumer already accepts the number:
 * BattingSystem.generateAiBattingDecision (situationalAggressionBias),
 * BowlingSystem.generateAiBowlingDecision and
 * FieldingSystem.generateAiFieldPlacements (situationalBias) and
 * MatchStateMachine.changeBowler (situationalBias). Until now
 * MatchSimulation passed 0.0 to all of them, so the AI played a flat,
 * situation-blind game: it never pressed harder when behind, never
 * protected wickets in a collapse and never went for the death overs.
 *
 * All thresholds and coefficients below are exact matches to the web
 * source; read its doc comments for why each sits where it does (they
 * record real rebalancing passes).
 */
object AiSituation {

    /**
     * Runs conceded in each of the last three COMPLETED overs of this
     * innings (wides and no-balls count, same as the web's tally),
     * oldest first. The web keeps this in a ref that it updates as overs
     * finish; deriving it from the ball-by-ball history instead gives
     * the same numbers with no extra state to keep in sync — it resets
     * with each innings because `ballByBall` does.
     */
    fun recentOverRuns(state: MatchState): List<Int> {
        val overs = mutableListOf<Int>()
        var runsThisOver = 0
        var legalBalls = 0
        for (ball in state.ballByBall) {
            runsThisOver += ball.runs + ball.extraRuns
            if (!ball.isWide && !ball.isNoBall) {
                legalBalls++
                if (legalBalls == 6) {
                    overs.add(runsThisOver)
                    runsThisOver = 0
                    legalBalls = 0
                }
            }
        }
        return overs.takeLast(3)
    }

    /**
     * How the AI BATTING side should feel right now: -1 (pull back,
     * protect wickets) to +1 (must push on). Built around the three
     * phases of a limited-overs innings:
     * - Powerplay: counter-attack the fielding restrictions (0.5).
     * - Middle overs: build at a real tempo (0.3), and if the last 2-3
     *   overs have all fallen short of 7 an over, break out into the
     *   same full attack the death overs use.
     * - Death overs (last 20%, never in Tests): attack in full almost
     *   regardless (0.95; 0.55 with only one wicket left).
     * An early collapse (2+ down inside the first 35%) overrides the
     * baseline to rebuild first, and a chase's required run rate pushes
     * the other way. Bowler matchup goes on top: go after a weak
     * part-timer, respect a genuine strike bowler.
     */
    fun battingBias(state: MatchState, recentOverRuns: List<Int>): Double {
        val totalOvers = state.oversLimit
        val oversDone = state.score.overs + state.score.balls / 6.0
        val wicketsDown = state.score.wickets
        val progress = if (totalOvers > 0) oversDone / totalOvers else 0.0
        val isPowerplayNow = FieldingSystem.isPowerplayOver(state.format, state.score.overs)
        val isDeathOversNow = state.format != MatchFormat.TEST && totalOvers > 0 && progress >= 0.8
        // Used for BOTH the death overs and the "middle build has stalled"
        // break-out, so breaking out attacks exactly as hard as the death
        // overs do, not a watered-down version.
        val fullAttackBias = if (wicketsDown >= 9) 0.55 else 0.95

        // --- Phase baseline ---
        var bias: Double
        if (isDeathOversNow) {
            bias = fullAttackBias
        } else if (isPowerplayNow) {
            bias = 0.5
        } else {
            bias = 0.3
            val recentWindow = recentOverRuns.takeLast(3)
            val buildHasStalled = recentWindow.size >= 2 && recentWindow.all { it < 7 }
            if (buildHasStalled) bias = fullAttackBias
        }

        // --- Wicket preservation (skipped in the death overs) ---
        if (!isDeathOversNow) {
            if (progress < 0.35 && wicketsDown >= 2) {
                bias -= minOf(0.9, 0.35 + (wicketsDown - 1) * 0.2) // 2 -> -0.55, 3 -> -0.75, 4+ -> -0.9
            } else {
                val expectedWicketsByNow = progress * 7
                if (wicketsDown > expectedWicketsByNow + 1.5) {
                    bias -= minOf(0.4, (wicketsDown - expectedWicketsByNow) * 0.12)
                }
            }
        }

        // --- Chasing: required run rate pushes the other way ---
        val target = state.target
        if (state.currentInnings == 2 && target != null) {
            val runsNeeded = maxOf(0, target - state.score.runs)
            val ballsBowled = state.score.overs * 6 + state.score.balls
            val ballsRemaining = maxOf(1, totalOvers * 6 - ballsBowled)
            val requiredRunRate = runsNeeded / (ballsRemaining / 6.0)
            if (requiredRunRate > 8) {
                bias += minOf(0.9, (requiredRunRate - 8) * 0.1)
            } else if (requiredRunRate < 4 && wicketsDown <= 4) {
                bias -= 0.15 // no rush, protect wickets
            }
        }

        // --- Bowler targeting ---
        val bowlerRating = state.currentBowler.bowlingRating
        if (bowlerRating < 65) bias += 0.25 else if (bowlerRating > 88) bias -= 0.2

        return bias.coerceIn(-1.0, 1.0)
    }

    /**
     * How the AI BOWLING side should approach the match right now, from
     * ITS OWN perspective: -1 (contain, classic run-saving) to +1 (attack
     * for wickets). Reacts to the current batting side's situation
     * whether that side is the user or the AI. Also used to pick the
     * AI's field (attacking / balanced / containing) and its next bowler.
     */
    fun bowlingBias(state: MatchState): Double {
        val totalOvers = state.oversLimit
        val oversDone = state.score.overs + state.score.balls / 6.0
        val wicketsDown = state.score.wickets
        val progress = if (totalOvers > 0) oversDone / totalOvers else 0.0

        var bias = 0.0

        // Batting side already fragile: press home the advantage.
        if (progress < 0.35 && wicketsDown >= 3) {
            bias += minOf(0.6, 0.2 + (wicketsDown - 2) * 0.12)
        }

        val target = state.target
        if (state.currentInnings == 2 && target != null) {
            val runsNeeded = maxOf(0, target - state.score.runs)
            val ballsBowled = state.score.overs * 6 + state.score.balls
            val ballsRemaining = maxOf(1, totalOvers * 6 - ballsBowled)
            val requiredRunRate = runsNeeded / (ballsRemaining / 6.0)
            // A cruising chase can't be stopped by containing alone: attack.
            if (requiredRunRate < 5 && wicketsDown <= 5) {
                bias += 0.25
            } else if (requiredRunRate > 11) {
                // The batting side is forced to gamble anyway: containing
                // pressure makes that gamble riskier.
                bias -= 0.2
            }
        }

        // Classic containing phase (death overs), unless the tail is exposed.
        if (progress > 0.85 && 10 - wicketsDown >= 3) {
            bias -= 0.4
        }

        return bias.coerceIn(-1.0, 1.0)
    }

    /**
     * Picks the AI's next batsman from those yet to bat. Under real
     * pressure in EITHER direction (|bias| >= 0.3 — protecting a wobbling
     * innings or needing to accelerate) it sends in the best available
     * batsman; otherwise it follows the fixed squad order, like a real
     * side's established lineup.
     */
    fun selectNextBatsman(available: List<Player>, situationalBias: Double): Player? =
        if (abs(situationalBias) >= 0.3) available.maxByOrNull { it.battingRating } else available.firstOrNull()
}

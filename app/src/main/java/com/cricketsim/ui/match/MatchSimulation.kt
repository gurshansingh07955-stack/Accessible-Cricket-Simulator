package com.cricketsim.ui.match

import com.cricketsim.logic.BattingSystem
import com.cricketsim.logic.BallOutcome
import com.cricketsim.logic.BowlingSystem
import com.cricketsim.logic.Difficulty
import com.cricketsim.logic.DismissalType
import com.cricketsim.logic.FieldingSystem
import com.cricketsim.logic.MatchEngine
import com.cricketsim.logic.MatchState
import com.cricketsim.logic.MatchStateMachine
import com.cricketsim.logic.ResolvedBowlingDecision
import com.cricketsim.logic.Stadium
import com.cricketsim.logic.WeatherSystem

/**
 * ⚠️ TEMPORARY, MOSTLY-AUTOMATED MATCH LOOP — not the real match
 * screen's mechanic, and not a port of anything in the web app (the
 * web app never has one side's decisions come from this file's AI
 * generators while the OTHER side is genuinely user-controlled in
 * quite this loop-driven way). This exists to verify the full
 * MatchEngine/MatchStateMachine wiring works correctly end-to-end
 * inside the actual Android UI, and now also hosts the real bridge
 * point for the first working gesture surface: when the user's own
 * team is bowling, MatchScreen.kt collects a real
 * ResolvedBowlingDecision from PitchingScreen and passes it in here as
 * `userBowlingDecision` instead of letting this function generate one
 * with AI. Batting and fielding are NOT wired to a gesture surface yet
 * — both sides' batting and fielding remain fully AI-driven regardless
 * of which side the user is on, until those two surfaces exist (see
 * UI_NOTES.md).
 *
 * Simplifications specific to THIS temporary loop (not permanent
 * design decisions, and not modeled on anything in the web app):
 * - Situational bias is always 0 (neutral) for every AI decision and
 *   for changeBowler's rotation scoring. A real situational-bias
 *   calculation (from score, overs remaining, wickets in hand) is
 *   separate future work — MatchEngine/BattingSystem/BowlingSystem
 *   already accept it as a parameter, nothing there needs to change.
 * - `needsOpenerSelection` / `needsBowlerSelection` on MatchState are
 *   ignored — the sensible defaults MatchStateMachine already picks
 *   are used automatically, since no user-facing picker is wired up in
 *   the match screen yet for either discipline.
 * - The next batsman after a wicket is always the next available
 *   player in squad order (no user choice yet).
 */
object MatchSimulation {

    /**
     * Simulates exactly one delivery (legal or not) and applies it to
     * the match state. Pass `userBowlingDecision` when the user's own
     * team is bowling and has just completed PitchingScreen for this
     * ball; leave it null (the default) to have the bowling side
     * generate an AI decision instead — this is what keeps the
     * opposing (AI-controlled) side's bowling working exactly as
     * before with no caller changes needed.
     */
    fun simulateOneBall(
        state: MatchState,
        stadium: Stadium,
        difficulty: Difficulty,
        userBowlingDecision: ResolvedBowlingDecision? = null
    ): Pair<MatchState, BallOutcome> {
        val striker = state.currentBatsmen.first
        val bowler = state.currentBowler

        val bowlingDecision = userBowlingDecision
            ?: BowlingSystem.generateAiBowlingDecision(bowler, situationalBias = 0.0)
        val battingDecision = BattingSystem.generateAiBattingDecision(
            batsman = striker,
            actualLength = bowlingDecision.actualLength,
            bowlingStyle = bowlingDecision.bowlingStyle,
            bowlingQualityTier = bowlingDecision.qualityTier,
            situationalAggressionBias = 0.0
        )

        val isPowerplay = FieldingSystem.isPowerplayOver(state.format, state.score.overs)
        val illegalField = !FieldingSystem.isFieldLegal(state.fieldPlacements, isPowerplay)

        val isSecondInningsUnderLights = state.currentInnings == 2 && state.weather.isDayNight
        val stadiumEffects = WeatherSystem.getStadiumMatchEffects(stadium, isSecondInningsUnderLights)

        val outcome = MatchEngine.simulateBall(
            matchState = state,
            difficulty = difficulty,
            pitchType = state.pitchType,
            bowlingDecision = bowlingDecision,
            battingDecision = battingDecision,
            illegalField = illegalField,
            stadiumEffects = stadiumEffects
        )

        var newState = MatchStateMachine.applyBallOutcome(state, outcome)
        val isLegal = !outcome.isWide && !outcome.isNoBall
        val overJustCompleted = isLegal && newState.score.balls == 0 && newState.score.overs > state.score.overs

        if (outcome.isWicket) {
            newState = MatchStateMachine.recordWicketFall(newState, striker.id, outcome.dismissalType ?: DismissalType.BOWLED)
            if (newState.score.wickets < 10) {
                val nextBatsman = MatchStateMachine.getAvailableBatsmen(newState).firstOrNull()
                if (nextBatsman != null) {
                    newState = MatchStateMachine.bringInNewBatsman(newState, striker.id, nextBatsman)
                }
            }
        } else if (isLegal && outcome.runs % 2 == 1) {
            newState = MatchStateMachine.rotateStrike(newState)
        }

        if (overJustCompleted && newState.score.wickets < 10) {
            newState = MatchStateMachine.rotateStrike(newState)
            newState = MatchStateMachine.changeBowler(newState, situationalBias = 0.0)
        }

        return newState to outcome
    }

    /** True once the current innings should end: all out, or the overs limit is used up. */
    fun isInningsOver(state: MatchState): Boolean {
        if (state.score.wickets >= 10) return true
        return state.score.overs >= state.oversLimit && state.score.balls == 0
    }

    /** True once the second innings has reached (or passed) the target — the chase is won. */
    fun isTargetReached(state: MatchState): Boolean {
        val target = state.target ?: return false
        return state.currentInnings == 2 && state.score.runs >= target
    }

    /** A short plain-text result summary once the match is over. Null while the match is still in progress. */
    fun matchResultText(state: MatchState): String? {
        if (state.currentInnings != 2) return null
        if (!isTargetReached(state) && !isInningsOver(state)) return null

        val target = state.target ?: return null

        if (isTargetReached(state)) {
            val wicketsInHand = 10 - state.score.wickets
            val plural = if (wicketsInHand == 1) "" else "s"
            return "${state.battingTeam.name} won by $wicketsInHand wicket$plural."
        }

        val runsShort = (target - 1) - state.score.runs
        return when {
            runsShort == 0 -> "Match tied."
            runsShort > 0 -> {
                val plural = if (runsShort == 1) "" else "s"
                "${state.bowlingTeam.name} won by $runsShort run$plural."
            }
            else -> null // Shouldn't happen: target already reached is handled above.
        }
    }
}

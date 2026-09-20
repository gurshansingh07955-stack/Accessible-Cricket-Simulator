package com.cricketsim.ui.match

import com.cricketsim.logic.BattingDecision
import com.cricketsim.logic.BattingSystem
import com.cricketsim.logic.BallOutcome
import com.cricketsim.logic.BowlingSystem
import com.cricketsim.logic.Difficulty
import com.cricketsim.logic.DismissalType
import com.cricketsim.logic.FieldingSystem
import com.cricketsim.logic.MatchEngine
import com.cricketsim.logic.MatchState
import com.cricketsim.logic.MatchStateMachine
import com.cricketsim.logic.PendingDismissal
import com.cricketsim.logic.Player
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
 * inside the actual Android UI, and hosts the bridge points for the
 * working gesture surfaces:
 *
 * - User BOWLING: MatchScreen.kt collects a real ResolvedBowlingDecision
 *   from PitchingScreen and passes it in as `presetBowlingDecision`.
 * - User BATTING: MatchScreen.kt calls `generateBowlingDecision` for the
 *   AI's delivery (after BattingScreen's blind footwork commit, so the
 *   batter can be shown the ball), then passes BOTH that delivery as
 *   `presetBowlingDecision` and the batter's choices as
 *   `presetBattingDecision`. The delivery is passed back in rather than
 *   regenerated so the ball the batter was shown is the ball that is
 *   actually bowled.
 * - User FIELDING: FieldingScreen edits `state.fieldPlacements` through
 *   MatchStateMachine.setFieldPlacements, and this loop then uses that
 *   field as it stands (including its legality) for every delivery.
 * - AI FIELDING (the user is batting): the AI captain sets a fresh
 *   field for every delivery once that delivery's actual length is
 *   known — a bouncer trap for short balls, a yorker field for full
 *   ones, the powerplay ring during the powerplay — exactly as the web
 *   does. The field it set is kept in the returned state.
 *
 * WHO PICKS WHOM, matching the web's match.tsx:
 * - The user's own side is never auto-picked. A wicket in the user's
 *   innings sets `pendingDismissal` (and, if it fell on the last ball of
 *   an over, `deferredOverEnd` so the strike rotation and bowler change
 *   wait for the replacement); the end of an over while the user is
 *   bowling sets `needsBowlerSelection`. The opening pair / opening
 *   bowler flags come from MatchStateMachine itself. MatchScreen shows
 *   the matching selection screen and calls `completeWicketReplacement`
 *   or MatchStateMachine.setOpeners / selectBowler when the user picks.
 * - The AI's own side is auto-picked, as before: the next available
 *   batsman in squad order, and MatchStateMachine.changeBowler.
 * - Nobody is prompted when the innings (or the chase) ends on that
 *   very ball — there is no next batsman or over to pick for.
 *
 * Simplifications specific to THIS temporary loop (not permanent
 * design decisions):
 * - Situational bias is always 0 (neutral) for every AI decision and
 *   for changeBowler's rotation scoring. A real situational-bias
 *   calculation (from score, overs remaining, wickets in hand) is
 *   separate future work — MatchEngine/BattingSystem/BowlingSystem/
 *   FieldingSystem already accept it as a parameter, nothing there
 *   needs to change.
 * - The AI's next batsman is simply the next in squad order (the web
 *   uses a situational selectAiNextBatsman that lives in match.tsx, not
 *   in helpers/, so it wasn't part of the logic port).
 * - No rain interruptions are rolled yet.
 */
object MatchSimulation {

    /**
     * The AI bowler's decision for the next delivery. Split out from
     * simulateOneBall so a user-controlled batting turn can be shown the
     * ball BEFORE choosing a shot — see the file-level doc comment.
     */
    fun generateBowlingDecision(state: MatchState): ResolvedBowlingDecision =
        BowlingSystem.generateAiBowlingDecision(state.currentBowler, situationalBias = 0.0)

    /**
     * Everyone who can be offered when the user picks a bowler. This is
     * MatchStateMachine.getEligibleBowlers (bowlers and all-rounders under
     * the format's per-bowler cap, never the bowler who just bowled),
     * with a safety net the web lacks: a squad with few genuine bowlers
     * can leave that list EMPTY once they've all bowled their quota, and
     * the web would then show an empty dialog with no way forward. In
     * that case this widens to anyone still under the cap, then to
     * anyone but the last bowler, so the match can never dead-end.
     */
    fun bowlerChoices(state: MatchState): List<Player> {
        val eligible = MatchStateMachine.getEligibleBowlers(state)
        if (eligible.isNotEmpty()) return eligible

        val maxOvers = MatchStateMachine.getMaxOversPerBowler(state.format)
        val underCap = state.bowlingTeam.players.filter { player ->
            (state.currentInningsData.bowlerStats.firstOrNull { it.playerId == player.id }?.overs ?: 0) < maxOvers
        }
        val notLastBowler = underCap.filter { it.id != state.currentBowler.id }
        return notLastBowler
            .ifEmpty { underCap }
            .ifEmpty { state.bowlingTeam.players.filter { it.id != state.currentBowler.id } }
    }

    /**
     * Sends in the user's chosen replacement after a wicket, then
     * finishes the over if the wicket fell on its last ball (see
     * `deferredOverEnd`): strike rotation, and the AI's bowling change.
     * The deferral only ever happens for the user's own batting side, so
     * the bowling side here is always the AI.
     */
    fun completeWicketReplacement(state: MatchState, newBatsman: Player): MatchState {
        val pending = state.pendingDismissal ?: return state
        var updated = MatchStateMachine.bringInNewBatsman(state, pending.playerId, newBatsman)
        if (updated.deferredOverEnd) {
            updated = endOfOver(updated).copy(deferredOverEnd = false)
        }
        return updated
    }

    /**
     * The end-of-over housekeeping: swap the batsmen's ends, then either
     * hand the choice of next bowler to the user (their side is bowling)
     * or let the AI rotate its attack. Skipped when the innings or chase
     * is over, since there is nobody left to bowl to.
     */
    private fun endOfOver(state: MatchState): MatchState {
        val rotated = MatchStateMachine.rotateStrike(state)
        if (isInningsOver(rotated) || isTargetReached(rotated)) return rotated
        return if (rotated.bowlingTeam.id == rotated.userTeam.id) {
            rotated.copy(needsBowlerSelection = true)
        } else {
            MatchStateMachine.changeBowler(rotated, situationalBias = 0.0)
        }
    }

    /**
     * Simulates exactly one delivery (legal or not) and applies it to
     * the match state.
     *
     * `presetBowlingDecision`: a delivery the caller has already
     * resolved — either the user's own via PitchingScreen, or the AI's
     * from `generateBowlingDecision` when the user is batting and has
     * already been shown it. Null (the default) generates an AI
     * decision here.
     *
     * `presetBattingDecision`: the user's own batting decision from
     * BattingScreen. Null (the default) generates an AI decision here.
     */
    fun simulateOneBall(
        state: MatchState,
        stadium: Stadium,
        difficulty: Difficulty,
        presetBowlingDecision: ResolvedBowlingDecision? = null,
        presetBattingDecision: BattingDecision? = null
    ): Pair<MatchState, BallOutcome> {
        val striker = state.currentBatsmen.first

        val bowlingDecision = presetBowlingDecision ?: generateBowlingDecision(state)
        val battingDecision = presetBattingDecision ?: BattingSystem.generateAiBattingDecision(
            batsman = striker,
            actualLength = bowlingDecision.actualLength,
            bowlingStyle = bowlingDecision.bowlingStyle,
            bowlingQualityTier = bowlingDecision.qualityTier,
            situationalAggressionBias = 0.0
        )

        val isPowerplay = FieldingSystem.isPowerplayOver(state.format, state.score.overs)

        // When the AI is bowling, its captain sets the field for THIS
        // delivery now that the ball's actual length is known. The user's
        // own bowling side keeps exactly the field the user set.
        val aiIsBowling = state.bowlingTeam.id != state.userTeam.id
        val ballState = if (aiIsBowling) {
            MatchStateMachine.setFieldPlacements(
                state,
                FieldingSystem.generateAiFieldPlacements(
                    fieldingPlayers = FieldingSystem.getFieldingPlayers(state.bowlingTeam, state.currentBowler.id),
                    isPowerplay = isPowerplay,
                    situationalBias = 0.0,
                    upcomingLength = bowlingDecision.actualLength
                )
            )
        } else {
            state
        }

        val illegalField = !FieldingSystem.isFieldLegal(ballState.fieldPlacements, isPowerplay)

        val isSecondInningsUnderLights = state.currentInnings == 2 && state.weather.isDayNight
        val stadiumEffects = WeatherSystem.getStadiumMatchEffects(stadium, isSecondInningsUnderLights)

        val outcome = MatchEngine.simulateBall(
            matchState = ballState,
            difficulty = difficulty,
            pitchType = state.pitchType,
            bowlingDecision = bowlingDecision,
            battingDecision = battingDecision,
            illegalField = illegalField,
            stadiumEffects = stadiumEffects
        )

        var newState = MatchStateMachine.applyBallOutcome(ballState, outcome)
        val isLegal = !outcome.isWide && !outcome.isNoBall
        val overJustCompleted = isLegal && newState.score.balls == 0 && newState.score.overs > state.score.overs
        var overEndDeferred = false

        if (outcome.isWicket) {
            val dismissalType = outcome.dismissalType ?: DismissalType.BOWLED
            newState = MatchStateMachine.recordWicketFall(newState, striker.id, dismissalType)

            // If the innings ends on this exact ball, nobody needs to be sent in.
            if (!isInningsOver(newState)) {
                if (newState.battingTeam.id == newState.userTeam.id) {
                    // The user picks their own next batsman.
                    val outStats = newState.currentInningsData.batsmanStats.firstOrNull { it.playerId == striker.id }
                    newState = newState.copy(
                        pendingDismissal = PendingDismissal(
                            playerId = striker.id,
                            playerName = striker.name,
                            runs = outStats?.runs ?: 0,
                            ballsFaced = outStats?.ballsFaced ?: 0,
                            dismissalType = dismissalType,
                            dismissedBy = newState.currentBowler.name
                        )
                    )
                    if (overJustCompleted) {
                        // Who is on strike for the new over depends on who
                        // comes in, so the end-of-over waits for the pick.
                        overEndDeferred = true
                        newState = newState.copy(deferredOverEnd = true)
                    }
                } else {
                    val nextBatsman = MatchStateMachine.getAvailableBatsmen(newState).firstOrNull()
                    if (nextBatsman != null) {
                        newState = MatchStateMachine.bringInNewBatsman(newState, striker.id, nextBatsman)
                    }
                }
            }
        } else if (isLegal && outcome.runs % 2 == 1) {
            newState = MatchStateMachine.rotateStrike(newState)
        }

        if (overJustCompleted && !overEndDeferred) {
            newState = endOfOver(newState)
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

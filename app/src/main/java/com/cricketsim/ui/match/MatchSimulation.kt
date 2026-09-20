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
import com.cricketsim.logic.MatchStatus
import com.cricketsim.logic.PendingDismissal
import com.cricketsim.logic.Player
import com.cricketsim.logic.RainInterruption
import com.cricketsim.logic.ResolvedBowlingDecision
import com.cricketsim.logic.Stadium
import com.cricketsim.logic.WeatherSystem

/**
 * One delivery, fully resolved: the new match state, what happened, and
 * the batting decision it was played against (the user's own, or the
 * AI's). The batting decision is returned because the last-ball line
 * reports the shot and timing for BOTH sides, as the web does.
 */
data class BallResult(val state: MatchState, val outcome: BallOutcome, val battingDecision: BattingDecision)

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
 *   ones, the powerplay ring during the powerplay, otherwise an
 *   attacking / balanced / containing spread chosen by its situational
 *   bias — exactly as the web does. The field it set is kept in the
 *   returned state.
 *
 * HOW THE AI THINKS. Every AI decision now reads the match through the
 * web's own situational-bias formulas, ported in AiSituation.kt: the AI
 * batting side attacks in the powerplay and the death overs, rebuilds
 * after an early collapse, chases its required run rate and targets weak
 * bowlers; the AI bowling side presses home a collapse, attacks a
 * cruising chase, contains in the death overs and picks its field and
 * its next bowler to match; and under real pressure the AI sends in its
 * best available batsman instead of following squad order. This replaces
 * a flat 0.0 bias that made the AI play the same situation-blind game
 * all match. It also makes the AI markedly stronger and more varied than
 * before, as it is on the web — game balance was tuned there, not here.
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
 * - The AI's own side is auto-picked: its next batsman by
 *   AiSituation.selectNextBatsman, and its bowler by
 *   MatchStateMachine.changeBowler.
 * - Nobody is prompted when the innings (or the chase) ends on that
 *   very ball — there is no next batsman or over to pick for.
 *
 * RAIN, matching the web's once-per-completed-over roll: at the end of
 * every over that doesn't also end the innings, after the bowling
 * change, WeatherSystem.shouldTriggerRainInterruption is rolled. If it
 * fires, the interruption is built from the state at that moment and
 * applied with MatchStateMachine.applyRainInterruption (which cuts
 * `oversLimit`, marks the one-interruption-per-match flag, revises the
 * target by DLS if a chase is under way, and sets `activeRainDelay`).
 * MatchScreen then blocks play behind RainDelayScreen until Resume. One
 * small difference: the web only rolls when an over ends normally, so an
 * over whose last ball was a wicket for the user's side (deferred until
 * the replacement is picked) never rolled; here that over-end rolls too,
 * because it is the same over-end, just later.
 */
object MatchSimulation {

    /**
     * The AI bowler's decision for the next delivery, shaped by the
     * bowling side's situational bias. Split out from simulateOneBall so
     * a user-controlled batting turn can be shown the ball BEFORE
     * choosing a shot — see the file-level doc comment.
     */
    fun generateBowlingDecision(state: MatchState): ResolvedBowlingDecision =
        BowlingSystem.generateAiBowlingDecision(state.currentBowler, AiSituation.bowlingBias(state))

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
     * `deferredOverEnd`): strike rotation, the AI's bowling change, and
     * the rain roll. The deferral only ever happens for the user's own
     * batting side, so the bowling side here is always the AI.
     */
    fun completeWicketReplacement(state: MatchState, newBatsman: Player, stadium: Stadium): MatchState {
        val pending = state.pendingDismissal ?: return state
        var updated = MatchStateMachine.bringInNewBatsman(state, pending.playerId, newBatsman)
        if (updated.deferredOverEnd) {
            updated = endOfOver(updated, stadium).copy(deferredOverEnd = false)
        }
        return updated
    }

    /**
     * The end-of-over housekeeping: swap the batsmen's ends, then either
     * hand the choice of next bowler to the user (their side is bowling)
     * or let the AI rotate its attack (its choice shaped by the bowling
     * situational bias), then roll for rain. Skipped when the innings or
     * chase is over, since there is nobody left to bowl to.
     */
    private fun endOfOver(state: MatchState, stadium: Stadium): MatchState {
        val rotated = MatchStateMachine.rotateStrike(state)
        if (isInningsOver(rotated) || isTargetReached(rotated)) return rotated
        val withBowlerHandled = if (rotated.bowlingTeam.id == rotated.userTeam.id) {
            rotated.copy(needsBowlerSelection = true)
        } else {
            MatchStateMachine.changeBowler(rotated, AiSituation.bowlingBias(rotated))
        }
        return maybeInterruptForRain(withBowlerHandled, stadium)
    }

    /**
     * Rolls once for rain. `shouldTriggerRainInterruption` itself rules
     * out Tests, the first over, the last two overs, and a second
     * interruption in the same match.
     */
    private fun maybeInterruptForRain(state: MatchState, stadium: Stadium): MatchState {
        val triggers = WeatherSystem.shouldTriggerRainInterruption(
            stadium, state.format, state.score.overs, state.oversLimit, state.rainInterruptionUsed
        )
        if (!triggers) return state

        val oversRemaining = state.oversLimit - state.score.overs
        val interruption = RainInterruption(
            originalOversAllocated = state.oversLimit,
            oversBowledAtInterruption = state.score.overs,
            wicketsLostAtInterruption = state.score.wickets,
            oversLost = WeatherSystem.pickOversLost(oversRemaining, state.format)
        )
        return MatchStateMachine.applyRainInterruption(state, interruption)
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
     * BattingScreen. Null (the default) generates an AI decision here,
     * shaped by the batting side's situational bias.
     */
    fun simulateOneBall(
        state: MatchState,
        stadium: Stadium,
        difficulty: Difficulty,
        presetBowlingDecision: ResolvedBowlingDecision? = null,
        presetBattingDecision: BattingDecision? = null
    ): BallResult {
        val striker = state.currentBatsmen.first

        val bowlingDecision = presetBowlingDecision ?: generateBowlingDecision(state)
        val battingDecision = presetBattingDecision ?: BattingSystem.generateAiBattingDecision(
            batsman = striker,
            actualLength = bowlingDecision.actualLength,
            bowlingStyle = bowlingDecision.bowlingStyle,
            bowlingQualityTier = bowlingDecision.qualityTier,
            situationalAggressionBias = AiSituation.battingBias(state, AiSituation.recentOverRuns(state))
        )

        val isPowerplay = FieldingSystem.isPowerplayOver(state.format, state.score.overs)

        // When the AI is bowling, its captain sets the field for THIS
        // delivery now that the ball's actual length is known, using the
        // same situational bias as its bowling decision. The user's own
        // bowling side keeps exactly the field the user set.
        val aiIsBowling = state.bowlingTeam.id != state.userTeam.id
        val ballState = if (aiIsBowling) {
            MatchStateMachine.setFieldPlacements(
                state,
                FieldingSystem.generateAiFieldPlacements(
                    fieldingPlayers = FieldingSystem.getFieldingPlayers(state.bowlingTeam, state.currentBowler.id),
                    isPowerplay = isPowerplay,
                    situationalBias = AiSituation.bowlingBias(state),
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
                    // The AI picks by the situation AFTER the wicket: the best
                    // available batsman under real pressure, else squad order.
                    val pickBias = AiSituation.battingBias(newState, AiSituation.recentOverRuns(newState))
                    val nextBatsman = AiSituation.selectNextBatsman(MatchStateMachine.getAvailableBatsmen(newState), pickBias)
                    if (nextBatsman != null) {
                        newState = MatchStateMachine.bringInNewBatsman(newState, striker.id, nextBatsman)
                    }
                }
            }
        } else if (isLegal && outcome.runs % 2 == 1) {
            newState = MatchStateMachine.rotateStrike(newState)
        }

        if (overJustCompleted && !overEndDeferred) {
            newState = endOfOver(newState, stadium)
        }

        return BallResult(newState, outcome, battingDecision)
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

    /**
     * Marks the match completed and records who won, as the web does
     * (matchStatus = completed, winnerId). The winner is the chasing
     * side if the target was reached, nobody on a tie, otherwise the
     * side that was bowling.
     */
    fun finishMatch(state: MatchState): MatchState {
        val target = state.target
        val winnerId = when {
            isTargetReached(state) -> state.battingTeam.id
            target != null && state.score.runs == target - 1 -> null
            else -> state.bowlingTeam.id
        }
        return state.copy(matchStatus = MatchStatus.COMPLETED, winnerId = winnerId)
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

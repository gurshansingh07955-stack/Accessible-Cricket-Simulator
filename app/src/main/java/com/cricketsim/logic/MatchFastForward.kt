package com.cricketsim.logic

import com.cricketsim.ui.match.MatchSimulation
import kotlin.random.Random

/** What the user asked for on the Simulate page: a number of overs (and balls), or, in a Test, a number of sessions. */
class FastForwardRequest(val overs: Int?, val balls: Int, val sessions: Int?)

/** How the simulation ended: the match as it now stands, the balls bowled, the sessions passed and why it stopped ("done", "session", "innings", "declared"). */
class FastForwardResult(val state: MatchState, val ballsBowled: Int, val sessionsPassed: Int, val stop: String)

/**
 * SIMULATE (Match info > Simulate match, patch 133). Both sides are played by the computer, ball by ball without any screen, from where the match stands now:
 *  - limited overs, or a Test "by over": the number of overs and balls asked for (12 overs and 5 balls is 12.5 overs, counted from now, inside the innings being played);
 *  - a Test "by session": until the end of that many sessions (lunch, tea and stumps are the ends; the first is the end of the session being played).
 * The same ball model as the matches the computer plays against itself ([AiBallModel]), the same engine for everything else, the computer's choices for both
 * sides' bowling changes, fields, new batters, Power Surges, declarations and the new ball. It STOPS early, and leaves the match as it is, when the innings ends
 * (the match screen then does what it always does: the innings break, the next innings, the result), when the computer declares, at the end of the sessions asked
 * for, and at the end of the match. Not available in a Super Over or while a batter is being replaced or rain has stopped play.
 * Rain delays and drinks breaks in the stretch simulated are skipped, and so are the DRS, the appeals and the Impact Player.
 */
object MatchFastForward {
    private const val OBSERVER_ID = "fast_forward_observer"
    private const val MAX_STEPS = 20000

    private fun inningsBalls(s: MatchState): Int = s.score.overs * 6 + s.score.balls

    /** "12 overs", "12 overs and 5 balls", "5 balls". */
    fun overText(overs: Int, balls: Int): String {
        val o = if (overs == 1) "1 over" else "$overs overs"
        val b = if (balls == 1) "1 ball" else "$balls balls"
        return when {
            overs > 0 && balls > 0 -> "$o and $b"
            overs > 0 -> o
            else -> b
        }
    }

    fun canSimulate(s: MatchState): Boolean {
        if (s.matchStatus != MatchStatus.IN_PROGRESS || s.superOverRound != 0 || s.superOverNominating) return false
        if (s.activeRainDelay != null || s.pendingDismissal != null) return false
        val t = s.test
        if (t != null && (t.breakPhase != 0 || t.followOnPending || t.currentRecorded)) return false
        return true
    }

    fun run(start: MatchState, stadium: Stadium, request: FastForwardRequest, random: Random = Random.Default): FastForwardResult {
        val realUser = start.userTeam
        // Neither side is "the user" while the computer plays (as in the matches it plays against itself): batters, bowlers, fields, declarations are all its own.
        var s = start.copy(userTeam = realUser.copy(id = OBSERVER_ID), needsOpenerSelection = false, needsBowlerSelection = false)
        val wantBalls = request.overs?.let { it * 6 + request.balls }
        val startBalls = inningsBalls(s)
        var sessionsPassed = 0
        var stop = "done"
        var steps = 0
        while (steps++ < MAX_STEPS) {
            if (s.drinksBreak != null) s = s.copy(drinksBreak = null)
            if (s.activeRainDelay != null) s = MatchStateMachine.resumeFromRainDelay(s)
            if (s.pendingDismissal != null) {
                val next = MatchStateMachine.getAvailableBatsmen(s).firstOrNull()
                s = if (next != null) MatchSimulation.completeWicketReplacement(s, next, stadium) else s.copy(pendingDismissal = null)
                continue
            }
            val t = s.test
            if (t != null) {
                // A break is showing: play on through it (the break itself counts as one session passed, below).
                if (t.breakPhase != 0) {
                    s = TestMatch.continueBreak(s)
                    continue
                }
                if (TestMatch.breakDue(s)) {
                    sessionsPassed++
                    if (request.sessions != null && sessionsPassed >= request.sessions) {
                        stop = "session"
                        break
                    }
                    s = TestMatch.beginBreak(s)
                    continue
                }
                if (TestMatch.aiShouldDeclare(s)) {
                    stop = "declared"
                    break
                }
                if (TestMatch.aiShouldTakeNewBall(s)) s = TestMatch.takeNewBall(s)
            }
            if (MatchSimulation.isTargetReached(s) || MatchSimulation.isInningsOver(s) || (t != null && TestMatch.timeUp(s))) {
                stop = "innings"
                break
            }
            if (wantBalls != null && inningsBalls(s) - startBalls >= wantBalls) break
            // The Big Bash League's Power Surge, as the computer decides it for either batting side.
            if (s.surge != null && s.score.balls == 0 && (PowerSurge.mustTakeNow(s) || PowerSurge.aiShouldTake(s))) s = PowerSurge.take(s)
            val delivery = MatchSimulation.prepareAiDelivery(s)
            val outcome = AiBallModel.sample(delivery.state, stadium, random)
            s = MatchSimulation.simulateOneBall(
                state = delivery.state,
                stadium = stadium,
                difficulty = Difficulty.MEDIUM,
                presetBowlingDecision = delivery.bowling,
                outcomeOverride = outcome
            ).state
        }
        var out = s.copy(userTeam = realUser)
        val ended = MatchSimulation.isInningsOver(out) || MatchSimulation.isTargetReached(out) || (out.test != null && TestMatch.timeUp(out))
        // The user chooses the next bowler at the end of an over, as he always does, when his side is bowling and he is the one who decides.
        if (!ended && stop != "declared" && out.score.balls == 0 && out.ballByBall.isNotEmpty() && out.bowlingTeam.id == realUser.id && out.userSelects) {
            out = out.copy(needsBowlerSelection = true)
        }
        return FastForwardResult(out, inningsBalls(s) - startBalls, sessionsPassed, stop)
    }

    /** What is said and shown afterwards. */
    fun summary(request: FastForwardRequest, result: FastForwardResult): String {
        val s = result.state
        val sessions = request.sessions
        val asked = if (sessions != null) "$sessions session${if (sessions == 1) "" else "s"}" else overText(request.overs ?: 0, request.balls)
        val why = when (result.stop) {
            "innings" -> " The innings ended before that."
            "declared" -> " The innings was declared."
            "session" -> " The session is over."
            else -> ""
        }
        return "Simulated $asked.$why ${s.battingTeam.name} are ${s.score.runs} for ${s.score.wickets} after ${s.score.overs}.${s.score.balls} overs."
    }
}

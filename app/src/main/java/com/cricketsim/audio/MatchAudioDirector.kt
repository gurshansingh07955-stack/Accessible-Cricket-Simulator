package com.cricketsim.audio

import com.cricketsim.logic.BallOutcome
import com.cricketsim.logic.CommentaryCategory
import com.cricketsim.logic.CommentaryLibrary
import com.cricketsim.logic.MatchEngine
import com.cricketsim.logic.MatchState
import kotlin.math.abs
import kotlin.math.min

/**
 * Decides what to play, and when, for each thing that happens in a match.
 * It is the audio half of pages/match.tsx's handlePlayBall and its
 * neighbours (checkInningsAndMatchEnd, handleResumeFromRain,
 * beginBattingFlow's fielding-change announcement, computeCrowdTension),
 * which live in the web's match screen rather than in helpers/ — so, like
 * AiSituation, they were never part of the logic port. The audio helpers
 * themselves are in SoundEngine.
 *
 * It keeps the running state the web keeps in refs and needs for the
 * "special event" commentary: the previous boundary (for back-to-back
 * fours and sixes), how many fours and sixes this over, the runs this
 * over (for tight and expensive overs), and the current bowler's run of
 * consecutive wickets (for hat-tricks). All of it counts legal deliveries
 * only, as the web does, and is reset at each innings.
 *
 * One instance per match screen.
 */
class MatchAudioDirector(private val services: GameServices) {

    private val sound: SoundEngine get() = services.sound

    private var lastBoundary = 0 // 0 none, 4 or 6
    private var overRuns = 0
    private var overFours = 0
    private var overSixes = 0
    private var streakBowlerId = ""
    private var streak = 0

    private fun resetTrackers() {
        lastBoundary = 0
        overRuns = 0
        overFours = 0
        overSixes = 0
        streakBowlerId = ""
        streak = 0
    }

    /**
     * How close the match feels, 0..1 (web: computeCrowdTension). In the
     * second innings it comes straight from the win probability — near 50
     * is a nail-biter (full tension), a near-certain result either way is
     * calm. The first innings has no target to be close to, so tension just
     * creeps up gently as wickets fall.
     */
    private fun tensionFor(state: MatchState): Float {
        if (state.currentInnings == 2) {
            val winProbability = MatchEngine.calculateWinProbability(state)
            return (1.0 - abs(winProbability - 50) / 50.0).coerceIn(0.0, 1.0).toFloat()
        }
        return (min(1.0, state.score.wickets / 10.0) * 0.5).toFloat()
    }

    fun startAmbience(state: MatchState) {
        sound.startCrowdAmbience()
        sound.setCrowdTension(tensionFor(state))
    }

    fun stopAmbience() {
        sound.stopCrowdAmbience()
    }

    fun updateTension(state: MatchState) {
        sound.setCrowdTension(tensionFor(state))
    }

    /** The ball leaves the bowler's hand: the whoosh, and cut off any commentary still running from the last ball. */
    fun onDelivery() {
        sound.playBallDelivery()
        sound.stopCommentary()
    }

    /**
     * The AI captain has just re-set its field for the coming delivery.
     * Every change is announced on screen; a full voice line is reserved for
     * a real tactical reset (three or more fielders moving at once, like a
     * bouncer or yorker trap), so a one-fielder shuffle isn't constant chatter.
     */
    fun onFieldChanges(changes: List<String>) {
        if (changes.size >= 3) sound.enqueueCommentary(CommentaryCategory.FIELDING_CHANGE)
    }

    /**
     * A ball has been resolved. `before` is the state it was bowled in and
     * `after` the state straight after it (before any innings switch).
     * The outcome's sound and its commentary play 400ms later, as on the
     * web, standing in for the ball's travel time.
     */
    fun onBallResolved(before: MatchState, after: MatchState, outcome: BallOutcome) {
        val strikerId = before.currentBatsmen.first.id
        val strikerRunsBefore = before.currentInningsData.batsmanStats.firstOrNull { it.playerId == strikerId }?.runs ?: 0
        val strikerRunsAfter = after.currentInningsData.batsmanStats.firstOrNull { it.playerId == strikerId }?.runs ?: strikerRunsBefore
        val partnershipBefore = before.currentInningsData.currentPartnership.totalRuns
        val partnershipAfter = after.currentInningsData.currentPartnership.totalRuns
        val bowlerId = before.currentBowler.id
        val bowlerWicketsBefore = before.currentInningsData.bowlerStats.firstOrNull { it.playerId == bowlerId }?.wickets ?: 0
        val bowlerWicketsAfter = after.currentInningsData.bowlerStats.firstOrNull { it.playerId == bowlerId }?.wickets ?: bowlerWicketsBefore
        val isOverComplete = after.score.balls == 0 && after.score.overs > before.score.overs

        val milestone = CommentaryLibrary.getMilestoneCrossed(strikerRunsBefore, strikerRunsAfter)
        // On a wicket ball the partnership total is unchanged, so this only
        // ever fires on a genuine scoring ball.
        val partnershipMilestone = CommentaryLibrary.getPartnershipMilestoneCrossed(partnershipBefore, partnershipAfter)
        val bowlerWicketMilestone = if (outcome.isWicket) {
            CommentaryLibrary.getBowlerWicketMilestoneCrossed(bowlerWicketsBefore, bowlerWicketsAfter)
        } else {
            null
        }

        var backToBack: CommentaryCategory? = null
        var tripleBoundary: CommentaryCategory? = null
        var hatTrick: CommentaryCategory? = null
        if (!outcome.isWide && !outcome.isNoBall) {
            if (outcome.isWicket) {
                lastBoundary = 0
                streak = if (streakBowlerId == bowlerId) streak + 1 else 1
                streakBowlerId = bowlerId
                hatTrick = CommentaryLibrary.getHatTrickCategory(streak)
            } else if (outcome.runs == 4) {
                if (lastBoundary == 4) backToBack = CommentaryCategory.BACK_TO_BACK_FOUR
                lastBoundary = 4
                overFours++
                if (overFours == 3) tripleBoundary = CommentaryCategory.TRIPLE_FOUR_OVER
                streakBowlerId = bowlerId
                streak = 0
            } else if (outcome.runs == 6) {
                if (lastBoundary == 6) backToBack = CommentaryCategory.BACK_TO_BACK_SIX
                lastBoundary = 6
                overSixes++
                if (overSixes == 3) tripleBoundary = CommentaryCategory.TRIPLE_SIX_OVER
                streakBowlerId = bowlerId
                streak = 0
            } else {
                lastBoundary = 0
                streakBowlerId = bowlerId
                streak = 0
            }
        }

        // Wides and no-balls DO count toward runs conceded in the over.
        overRuns += outcome.runs + outcome.extraRuns
        var overSummary: CommentaryCategory? = null
        if (isOverComplete) {
            if (overRuns < 5) overSummary = CommentaryCategory.TIGHT_OVER
            else if (overRuns > 12) overSummary = CommentaryCategory.EXPENSIVE_OVER
            overRuns = 0
            overFours = 0
            overSixes = 0
        }

        val category = CommentaryLibrary.categorizeBallOutcome(outcome)
        sound.schedule(400) {
            sound.playOutcomeSounds(outcome.isWicket, outcome.runs)
            if (outcome.isWicket) sound.vibrate(200, 100, 200) else if (outcome.runs >= 4) sound.vibrate(100)

            sound.enqueueCommentary(category)
            hatTrick?.let { sound.enqueueCommentary(it) }
            bowlerWicketMilestone?.let { sound.enqueueCommentary(it) }
            backToBack?.let { sound.enqueueCommentary(it) }
            tripleBoundary?.let { sound.enqueueCommentary(it) }
            milestone?.let { sound.enqueueCommentary(it) }
            partnershipMilestone?.let { sound.enqueueCommentary(it) }
            if (isOverComplete) sound.enqueueCommentary(CommentaryCategory.OVER_COMPLETE)
            overSummary?.let { sound.enqueueCommentary(it) }
        }
    }

    fun onRainStarted() {
        sound.startRainAmbience()
        sound.enqueueCommentary(CommentaryCategory.RAIN_START)
    }

    /**
     * Play resumes. A second-innings interruption revises the chase target
     * immediately, so its DLS commentary belongs here; a first-innings one
     * is announced at the innings break, once the final score is known.
     */
    fun onRainResumed(secondInningsInterruption: Boolean) {
        sound.stopRainAmbience()
        sound.enqueueCommentary(CommentaryCategory.RAIN_STOP)
        if (secondInningsInterruption) sound.enqueueCommentary(CommentaryCategory.DLS_REVISED)
    }

    /** `switched` is the state after MatchStateMachine.switchInnings. */
    fun onInningsBreak(switched: MatchState) {
        if (switched.dlsRevised) sound.enqueueCommentary(CommentaryCategory.DLS_REVISED)
        sound.enqueueCommentary(CommentaryCategory.INNINGS_BREAK)
        resetTrackers()
        sound.setCrowdTension(tensionFor(switched))
    }

    /** The commentary carries on over the result screen; only the crowd stops. */
    fun onMatchEnded() {
        sound.enqueueCommentary(CommentaryCategory.MATCH_WIN)
        sound.stopCrowdAmbience()
    }

    fun onMatchScreenLeft() {
        sound.stopCrowdAmbience()
        sound.stopRainAmbience()
        sound.stopCommentary()
    }
}

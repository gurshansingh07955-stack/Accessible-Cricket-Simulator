package com.cricketsim.logic

import java.util.UUID
import kotlin.random.Random

/**
 * Full port of helpers/matchState.tsx (the Floot/React web app remains
 * the source of truth for gameplay design). Pure Kotlin, no Android
 * dependencies. This REPLACES the earlier partial MatchState.kt (see
 * git history) that only carried the handful of fields MatchEngine.kt
 * needed ahead of this full port, exactly as that file's own warning
 * said it would be.
 *
 * NOT ported here, deliberately (see PORTING_NOTES.md's "Not ported"
 * section):
 * - `saveMatch` / `loadMatch` / `clearMatch` / `hasActiveMatch` — the
 *   web source backs these with browser `localStorage`. The Android
 *   equivalent is `DataStore` or a small `Room` database, which is a
 *   real platform-specific persistence layer to design, not a
 *   line-by-line translation — out of scope for this pass. Everything
 *   else here is a pure state-transition function with no I/O, so it's
 *   fully usable without persistence; a future session just needs to
 *   wire loading/saving around these.
 * - `loadMatch`'s defensive backfill logic for old saved matches
 *   missing newer fields (fieldPlacements, stadium/weather, etc.) is
 *   inherently about migrating persisted JSON and doesn't apply until
 *   persistence itself exists.
 *
 * MODELING NOTE — `nanoid()` (used for MatchState.id) has no Kotlin
 * equivalent in this project; `java.util.UUID.randomUUID().toString()`
 * is used instead (see generateId() below) — a longer string than
 * nanoid produces, but the same role: a unique, opaque match id. This
 * mirrors how CricketData.kt already generates player ids the same way.
 *
 * All state-transition logic (rain-interruption overs-limit math,
 * DLS target revision, field-placement carry-over on a bowler change,
 * bowler-rotation scoring) is an exact match to the web source.
 */

enum class MatchStatus { SETUP, IN_PROGRESS, COMPLETED }

data class MatchScore(val runs: Int, val wickets: Int, val overs: Int, val balls: Int)

/**
 * Details of a batsman who has just been dismissed and is awaiting a
 * user-selected replacement. Only ever set for the user's own batting
 * team — the AI batting team picks its next batsman automatically.
 */
data class PendingDismissal(
    val playerId: String,
    val playerName: String,
    val runs: Int,
    val ballsFaced: Int,
    val dismissalType: DismissalType,
    val dismissedBy: String
)

data class MatchState(
    val id: String,
    val format: MatchFormat,
    val pitchType: PitchType,
    val userTeam: Team,
    val opponentTeam: Team,
    val tossWinnerId: String,
    val tossDecision: TossDecision,
    val currentInnings: Int, // 1 or 2
    val battingTeam: Team,
    val bowlingTeam: Team,
    val score: MatchScore,
    val target: Int?,
    // (Striker, Non-Striker) — matches the web source's `[Player, Player]` tuple.
    val currentBatsmen: Pair<Player, Player>,
    val currentBowler: Player,
    val matchStatus: MatchStatus,
    val winnerId: String?,
    val ballByBall: List<BallOutcome>, // History of current innings
    val firstInningsScore: MatchScore? = null, // Store first innings score when in second innings
    // New statistics tracking
    val firstInningsData: InningsData?,
    val secondInningsData: InningsData?,
    val currentInningsData: InningsData,
    val lastSixBalls: List<BallOutcome>,
    // True at the start of an innings when the user's team is batting and
    // hasn't yet chosen its two openers. Play is paused until resolved.
    val needsOpenerSelection: Boolean,
    // True at the start of an innings (opening bowler) or right after an
    // over completes, whenever the user's team is bowling and hasn't yet
    // chosen who bowls next. Play is paused until resolved.
    val needsBowlerSelection: Boolean,
    // Set when a batsman from the user's own team is out and a
    // replacement hasn't been chosen yet. Play is paused until resolved.
    val pendingDismissal: PendingDismissal?,
    // True when a wicket fell on the last ball of an over for the user's
    // own batting team: the end-of-over strike rotation (and any AI
    // bowling change) has to wait until the replacement batsman is
    // chosen, since who ends up on strike for the new over depends on it.
    val deferredOverEnd: Boolean,
    // The bowling side's current fielding arrangement (9 outfielders,
    // excluding the wicketkeeper and whoever is currently bowling).
    val fieldPlacements: List<FieldPlacement>,

    // --- Stadium & weather (see StadiumData.kt / WeatherSystem.kt) ---

    // Id into StadiumData's stadium list. Chosen at the pre-match setup
    // flow; pitchType above is DERIVED from the chosen stadium at
    // creation time rather than picked independently.
    val stadiumId: String,
    // Rolled once at the toss and fixed for the rest of the match.
    val weather: WeatherSnapshot,
    // The overs-per-innings cap currently in effect. Starts equal to
    // MatchEngine.getFormatOvers(format) and can be permanently reduced
    // by exactly one rain interruption (see rainInterruptionUsed below)
    // — from that point on, BOTH innings play to this reduced number
    // rather than the format's normal one.
    val oversLimit: Int,
    // This game allows at most one rain interruption per match (see the
    // file-level doc comment in WeatherSystem.kt for why) — once true,
    // no further interruption will ever be rolled for the rest of the
    // match.
    val rainInterruptionUsed: Boolean,
    // Set the moment a rain interruption fires; cleared when the user
    // dismisses the rain-delay screen. While set, ball-by-ball play
    // should pause and show a rain-delay dialog (eventual UI layer).
    val activeRainDelay: RainInterruption?,
    // Recorded interruption details, kept around so switchInnings() (if
    // the interruption happened in innings 1) or a chase-revision (if it
    // happened in innings 2) can compute the correct DLS resource
    // percentages later. At most one of these two will ever be set,
    // since rainInterruptionUsed caps the whole match to a single
    // interruption.
    val firstInningsInterruption: RainInterruption?,
    val secondInningsInterruption: RainInterruption?,
    // True once target reflects a DLS revision rather than a plain
    // "first innings score + 1" — surfaced in the UI so a revised target
    // is never presented as if it were the original one.
    val dlsRevised: Boolean
)

object MatchStateMachine {

    private fun generateId(): String = UUID.randomUUID().toString()

    /**
     * Picks the starting field for whichever side is about to bowl. The
     * user's own bowling side always gets the neutral, always-legal
     * default — they'll set their own field via the eventual Field
     * screen. An AI-controlled bowling side instead gets a real
     * auto-generated arrangement (FieldingSystem.generateAiFieldPlacements),
     * reflecting the powerplay state at the point the bowler is starting
     * their spell. No specific delivery is known yet at this point (that
     * reactive, ball-by-ball refinement happens separately, right before
     * each ball the AI actually bowls), so this call only picks the
     * general powerplay/attacking/balanced/containing shape for the over.
     */
    private fun pickStartingFieldPlacements(
        bowlingTeam: Team,
        bowlerId: String,
        userTeamId: String,
        format: MatchFormat,
        oversSoFar: Int
    ): List<FieldPlacement> {
        val fieldingPlayers = FieldingSystem.getFieldingPlayers(bowlingTeam, bowlerId)
        if (bowlingTeam.id == userTeamId) {
            return FieldingSystem.createDefaultFieldPlacements(fieldingPlayers)
        }
        return FieldingSystem.generateAiFieldPlacements(fieldingPlayers, FieldingSystem.isPowerplayOver(format, oversSoFar))
    }

    /**
     * Creates a fresh match state. Openers and the opening bowler are
     * given sensible defaults (top order / best bowling rating), but if
     * the user's team controls that discipline, needsOpenerSelection /
     * needsBowlerSelection is set so the eventual match screen prompts
     * the user to confirm or change the pick before the first ball is
     * bowled.
     */
    fun createNewMatch(
        format: MatchFormat,
        pitchType: PitchType,
        userTeam: Team,
        opponentTeam: Team,
        tossWinnerId: String,
        tossDecision: TossDecision,
        stadiumId: String,
        weather: WeatherSnapshot
    ): MatchState {
        // Determine who bats first
        val battingTeam: Team
        val bowlingTeam: Team
        if (tossWinnerId == userTeam.id) {
            if (tossDecision == TossDecision.BAT) {
                battingTeam = userTeam
                bowlingTeam = opponentTeam
            } else {
                battingTeam = opponentTeam
                bowlingTeam = userTeam
            }
        } else {
            // Opponent won toss
            if (tossDecision == TossDecision.BAT) {
                battingTeam = opponentTeam
                bowlingTeam = userTeam
            } else {
                battingTeam = userTeam
                bowlingTeam = opponentTeam
            }
        }

        // Select initial players (defaults; may be overridden by the
        // user before the first ball if they control that discipline).
        val striker = battingTeam.players[0]
        val nonStriker = battingTeam.players[1]
        val bowler = bowlingTeam.players.sortedByDescending { it.bowlingRating }[0]

        val currentInningsData = MatchStats.createEmptyInningsData(battingTeam, bowlingTeam, Pair(striker, nonStriker), bowler)

        val fieldPlacements = pickStartingFieldPlacements(bowlingTeam, bowler.id, userTeam.id, format, 0)

        return MatchState(
            id = generateId(),
            format = format,
            pitchType = pitchType,
            userTeam = userTeam,
            opponentTeam = opponentTeam,
            tossWinnerId = tossWinnerId,
            tossDecision = tossDecision,
            currentInnings = 1,
            battingTeam = battingTeam,
            bowlingTeam = bowlingTeam,
            score = MatchScore(runs = 0, wickets = 0, overs = 0, balls = 0),
            target = null,
            currentBatsmen = Pair(striker, nonStriker),
            currentBowler = bowler,
            matchStatus = MatchStatus.IN_PROGRESS,
            winnerId = null,
            ballByBall = emptyList(),
            firstInningsData = null,
            secondInningsData = null,
            currentInningsData = currentInningsData,
            lastSixBalls = emptyList(),
            needsOpenerSelection = battingTeam.id == userTeam.id,
            needsBowlerSelection = bowlingTeam.id == userTeam.id,
            pendingDismissal = null,
            deferredOverEnd = false,
            fieldPlacements = fieldPlacements,
            stadiumId = stadiumId,
            weather = weather,
            oversLimit = MatchEngine.getFormatOvers(format),
            rainInterruptionUsed = false,
            activeRainDelay = null,
            firstInningsInterruption = null,
            secondInningsInterruption = null,
            dlsRevised = false
        )
        // Note: the web source calls saveMatch(newState) here — omitted,
        // persistence not ported yet (see file header).
    }

    /** Helper to rotate strike. */
    fun rotateStrike(state: MatchState): MatchState {
        val newBatsmen = Pair(state.currentBatsmen.second, state.currentBatsmen.first)
        return state.copy(currentBatsmen = newBatsmen)
    }

    /**
     * Maximum overs a single bowler may bowl in an innings, per format.
     * Test matches have no such limit (the web source uses `Infinity`;
     * Kotlin models that as Int.MAX_VALUE, which is practically infinite
     * for this comparison's purposes).
     */
    fun getMaxOversPerBowler(format: MatchFormat): Int = when (format) {
        MatchFormat.T20 -> 4
        MatchFormat.ODI -> 10
        MatchFormat.TEST -> Int.MAX_VALUE
    }

    private fun oversBowledBy(state: MatchState, playerId: String): Int =
        state.currentInningsData.bowlerStats.find { it.playerId == playerId }?.overs ?: 0

    /**
     * Batsmen from the batting team who haven't been to the crease yet
     * this innings — used to populate the next-batsman selection list.
     */
    fun getAvailableBatsmen(state: MatchState): List<Player> =
        state.battingTeam.players.filter { p -> state.currentInningsData.batsmanStats.none { it.playerId == p.id } }

    /**
     * Bowlers eligible to bowl the next over: bowler/all-rounder role
     * and under the format's per-bowler over cap. Excludes the current
     * bowler (no back-to-back overs) — except for the opening bowler
     * pick, where "current bowler" is just an unconfirmed placeholder
     * default and shouldn't be excluded from the choices.
     */
    fun getEligibleBowlers(state: MatchState): List<Player> {
        val maxOvers = getMaxOversPerBowler(state.format)
        val noBallsBowledYet = state.currentInningsData.totalBalls == 0 && state.currentInningsData.totalOvers == 0

        val underCap = state.bowlingTeam.players
            .filter { it.role == PlayerRole.BOWLER || it.role == PlayerRole.ALL_ROUNDER }
            .filter { oversBowledBy(state, it.id) < maxOvers }

        if (noBallsBowledYet) return underCap

        val excludingCurrent = underCap.filter { it.id != state.currentBowler.id }
        // Fallback: if excluding the current bowler leaves nobody (very
        // small bowling attack), allow reusing them rather than
        // returning an empty list.
        return excludingCurrent.ifEmpty { underCap }
    }

    /**
     * Confirms the batting team's openers. Only valid before any ball
     * has been bowled this innings.
     */
    fun setOpeners(state: MatchState, striker: Player, nonStriker: Player): MatchState {
        val currentInningsData = MatchStats.createEmptyInningsData(
            state.battingTeam, state.bowlingTeam, Pair(striker, nonStriker), state.currentBowler
        )
        return state.copy(
            currentBatsmen = Pair(striker, nonStriker),
            currentInningsData = currentInningsData,
            needsOpenerSelection = false
        )
    }

    /**
     * Carries a captain's existing field forward across a bowler
     * change, instead of resetting to a fresh default every single
     * over. The incoming bowler was previously fielding somewhere in
     * `previous` (they weren't bowling last over); the outgoing bowler
     * now steps off the bowling crease and into that exact vacated
     * slot, so the arrangement keeps its shape and only the two
     * bowlers' roles swap. Returns null if the incoming bowler can't be
     * found in the previous arrangement at all (e.g. a fresh/backfilled
     * roster) — the caller falls back to a default in that case.
     */
    private fun carryOverFieldPlacements(
        previous: List<FieldPlacement>,
        outgoingBowlerId: String,
        incomingBowlerId: String
    ): List<FieldPlacement>? {
        val incomingSlotExists = previous.any { it.playerId == incomingBowlerId }
        if (!incomingSlotExists) return null
        return previous.map { if (it.playerId == incomingBowlerId) it.copy(playerId = outgoingBowlerId) else it }
    }

    /**
     * Confirms who bowls next. If no ball has been bowled yet this
     * innings, this is the opening bowler pick and resets the
     * (still-empty) innings data around the chosen bowler. Otherwise
     * it's a normal mid-innings bowling change.
     *
     * Field placements carry forward over to over (see
     * carryOverFieldPlacements), only resetting once the powerplay ends
     * (the legal deep-fielder cap changes then, so the old arrangement
     * may no longer fit) or for the very first bowler of the innings.
     * Carrying forward means the incoming bowler steps out of whatever
     * spot they were fielding, and the outgoing bowler — who can now
     * field, since they're done bowling this over — takes that exact
     * vacated slot. Same shape, just the two bowlers' roles swapped.
     */
    fun selectBowler(state: MatchState, bowler: Player): MatchState {
        val noBallsBowledYet = state.currentInningsData.totalBalls == 0 && state.currentInningsData.totalOvers == 0

        val previousOverWasPowerplay = state.score.overs > 0 && FieldingSystem.isPowerplayOver(state.format, state.score.overs - 1)
        val thisOverIsPowerplay = FieldingSystem.isPowerplayOver(state.format, state.score.overs)
        val powerplayJustEnded = previousOverWasPowerplay && !thisOverIsPowerplay

        val fieldPlacements = if (noBallsBowledYet || powerplayJustEnded) {
            FieldingSystem.createDefaultFieldPlacements(FieldingSystem.getFieldingPlayers(state.bowlingTeam, bowler.id))
        } else {
            carryOverFieldPlacements(state.fieldPlacements, state.currentBowler.id, bowler.id)
                ?: FieldingSystem.createDefaultFieldPlacements(FieldingSystem.getFieldingPlayers(state.bowlingTeam, bowler.id))
        }

        if (noBallsBowledYet) {
            val currentInningsData = MatchStats.createEmptyInningsData(state.battingTeam, state.bowlingTeam, state.currentBatsmen, bowler)
            return state.copy(
                currentBowler = bowler, currentInningsData = currentInningsData,
                needsBowlerSelection = false, fieldPlacements = fieldPlacements
            )
        }

        val currentInningsData = MatchStats.addBowlerIfNotExists(state.currentInningsData, bowler)
        return state.copy(
            currentBowler = bowler, currentInningsData = currentInningsData,
            needsBowlerSelection = false, fieldPlacements = fieldPlacements
        )
    }

    /**
     * Helper to change bowler automatically (used for the AI-controlled
     * bowling side, which doesn't get a selection prompt). Excludes the
     * outgoing bowler and anyone who has reached their format's
     * per-bowler over limit.
     *
     * situationalBias (-1..1, from the eventual computeBowlingSituationalBias
     * on the UI/match-loop side) drives WHICH eligible bowler gets
     * picked, not just whether one exists: a captain doesn't bowl their
     * strike bowler every over back to back just because the rules
     * allow it — they're used in bursts and brought back when the
     * situation actually calls for them. Each eligible bowler gets a
     * score of bowlingRating minus a penalty for overs they've already
     * bowled this innings; how steep that penalty is depends on urgency
     * (how positive situationalBias is right now). With no pressing
     * need for wickets, a bowler who's already sent down a few overs
     * scores much lower than an unused bowler of similar quality, so
     * the attack naturally rotates through the team. When the situation
     * IS urgent (an early collapse to press home, a cruising chase to
     * strangle, the death overs with the tail exposed), that penalty
     * nearly disappears and the single best eligible bowler comes back
     * into the attack regardless of recent workload — the "used the
     * strike bowler for one over, then brought them back when needed"
     * pattern.
     */
    fun changeBowler(state: MatchState, situationalBias: Double = 0.0): MatchState {
        val pool = getEligibleBowlers(state)
        if (pool.isEmpty()) return state

        val urgency = situationalBias.coerceIn(0.0, 1.0) // 0 (calm) .. 1 (must attack)
        val rotationPenaltyPerOver = (1 - urgency) * 8

        val scored = pool.map { p ->
            // A little random jitter so the rotation doesn't feel
            // robotically deterministic when two bowlers' scores are
            // close.
            p to (p.bowlingRating - oversBowledBy(state, p.id) * rotationPenaltyPerOver + (Random.nextDouble() * 6 - 3))
        }
        val nextBowler = scored.sortedByDescending { it.second }[0].first

        val currentInningsData = MatchStats.addBowlerIfNotExists(state.currentInningsData, nextBowler)

        val fieldPlacements = FieldingSystem.generateAiFieldPlacements(
            FieldingSystem.getFieldingPlayers(state.bowlingTeam, nextBowler.id),
            FieldingSystem.isPowerplayOver(state.format, state.score.overs),
            situationalBias
        )

        return state.copy(currentBowler = nextBowler, currentInningsData = currentInningsData, fieldPlacements = fieldPlacements)
    }

    /**
     * Records a wicket falling: increments the score's wicket count and
     * marks the outgoing batsman's stats as out. Does NOT bring in a
     * new batsman — call bringInNewBatsman() (after a user pick, for
     * the user's own team) or handle the all-out case separately.
     *
     * outBatsmanId and dismissalType are passed in explicitly (captured
     * at the start of the ball, before any other mutation to the match
     * state) rather than read off state.currentBatsmen, so this is
     * correct even if an end-of-over strike rotation has already been
     * applied to `state`.
     */
    fun recordWicketFall(state: MatchState, outBatsmanId: String, dismissalType: DismissalType = DismissalType.BOWLED): MatchState {
        val currentWickets = state.score.wickets + 1
        val dismissedBy = state.currentBowler.name

        val currentInningsData = MatchStats.recordDismissal(state.currentInningsData, outBatsmanId, dismissalType, dismissedBy)

        return state.copy(score = state.score.copy(wickets = currentWickets), currentInningsData = currentInningsData)
    }

    /**
     * Brings in a new batsman after a wicket (chosen by the user for
     * their own team, or auto-picked in batting order for the AI team).
     * The continuing (not-out) batsman stays; the new batsman takes the
     * spot of whichever tuple slot the given outBatsmanId occupies, so
     * this is safe to call regardless of whether an end-of-over
     * rotation has already happened.
     */
    fun bringInNewBatsman(state: MatchState, outBatsmanId: String, newBatsman: Player): MatchState {
        val outIsFirst = state.currentBatsmen.first.id == outBatsmanId
        val continuingBatsman = if (outIsFirst) state.currentBatsmen.second else state.currentBatsmen.first
        val newBatsmenPair = if (outIsFirst) Pair(newBatsman, continuingBatsman) else Pair(continuingBatsman, newBatsman)

        val currentInningsData = MatchStats.endPartnership(state.currentInningsData, newBatsman, continuingBatsman)

        return state.copy(currentBatsmen = newBatsmenPair, currentInningsData = currentInningsData, pendingDismissal = null)
    }

    /** Applies a ball outcome to the match state, updating all statistics. */
    fun applyBallOutcome(state: MatchState, outcome: BallOutcome): MatchState {
        val isLegalDelivery = !outcome.isWide && !outcome.isNoBall

        // Update innings data with ball outcome
        val currentInningsData = MatchStats.updateInningsDataForBall(
            state.currentInningsData, outcome, state.currentBatsmen.first.id, isLegalDelivery
        )

        // Update ball by ball history
        val ballByBall = state.ballByBall + outcome

        // Update last six balls
        val lastSixBalls = MatchStats.getLastSixBalls(ballByBall)

        // Update score
        var newScore = state.score.copy(runs = state.score.runs + outcome.runs + outcome.extraRuns)
        if (isLegalDelivery) {
            var balls = newScore.balls + 1
            var overs = newScore.overs
            if (balls == 6) {
                overs += 1
                balls = 0
            }
            newScore = newScore.copy(overs = overs, balls = balls)
        }

        // Note: wicket count is incremented in recordWicketFall(), which
        // is called separately when outcome.isWicket is true.
        // Incrementing it here as well would double-count every wicket.

        return state.copy(score = newScore, ballByBall = ballByBall, lastSixBalls = lastSixBalls, currentInningsData = currentInningsData)
    }

    /**
     * Switches from first innings to second innings. As with
     * createNewMatch, sensible default openers/bowler are picked, but
     * needsOpenerSelection / needsBowlerSelection are set if the user's
     * team controls that discipline, so the eventual match screen
     * prompts for a pick before play resumes.
     */
    fun switchInnings(state: MatchState): MatchState {
        if (state.currentInnings != 1) {
            // Web source: console.error + return state unchanged.
            return state
        }

        // Archive first innings data
        val firstInningsData = state.currentInningsData
        val firstInningsScore = state.score

        // Swap teams
        val battingTeam = state.bowlingTeam
        val bowlingTeam = state.battingTeam

        // Set target — plain "+1" UNLESS the first innings itself was
        // cut short by rain, in which case the target is a DLS-revised
        // par score instead. R1 (team 1's resource) reflects however
        // much of their allocated overs they actually got to use; R2
        // (team 2's resource) is a FULL, uninterrupted innings at the
        // CURRENT oversLimit — under this game's one-interruption-per-
        // match rule, a first-innings interruption means the chase
        // itself can't ALSO be interrupted, so team 2 gets to use its
        // full (possibly already-reduced) oversLimit.
        val target: Int
        var dlsRevised = false
        if (state.firstInningsInterruption != null) {
            val r1 = WeatherSystem.computeInningsResourcePercent(
                state.firstInningsInterruption.originalOversAllocated, state.firstInningsInterruption
            )
            val r2 = WeatherSystem.computeInningsResourcePercent(state.oversLimit, null)
            val dls = WeatherSystem.computeDlsTarget(firstInningsScore.runs, r1, r2, state.format)
            target = dls.target
            dlsRevised = true
        } else {
            target = firstInningsScore.runs + 1
        }

        // Select initial players for second innings (defaults; may be
        // overridden by the user before the first ball if they control
        // that discipline).
        val striker = battingTeam.players[0]
        val nonStriker = battingTeam.players[1]
        val bowler = bowlingTeam.players.sortedByDescending { it.bowlingRating }[0]

        val currentInningsData = MatchStats.createEmptyInningsData(battingTeam, bowlingTeam, Pair(striker, nonStriker), bowler)

        val fieldPlacements = pickStartingFieldPlacements(bowlingTeam, bowler.id, state.userTeam.id, state.format, 0)

        return state.copy(
            currentInnings = 2,
            battingTeam = battingTeam,
            bowlingTeam = bowlingTeam,
            score = MatchScore(runs = 0, wickets = 0, overs = 0, balls = 0),
            target = target,
            currentBatsmen = Pair(striker, nonStriker),
            currentBowler = bowler,
            ballByBall = emptyList(),
            lastSixBalls = emptyList(),
            firstInningsScore = firstInningsScore,
            firstInningsData = firstInningsData,
            secondInningsData = null,
            currentInningsData = currentInningsData,
            needsOpenerSelection = battingTeam.id == state.userTeam.id,
            needsBowlerSelection = bowlingTeam.id == state.userTeam.id,
            pendingDismissal = null,
            deferredOverEnd = false,
            fieldPlacements = fieldPlacements,
            dlsRevised = dlsRevised,
            activeRainDelay = null
        )
    }

    /**
     * Pure updater for a captain's fielding changes made on the
     * eventual Field screen — swaps in the given placement list
     * unchanged.
     */
    fun setFieldPlacements(state: MatchState, placements: List<FieldPlacement>): MatchState =
        state.copy(fieldPlacements = placements)

    /**
     * Applies a rain interruption rolled by
     * WeatherSystem.shouldTriggerRainInterruption(). Reduces oversLimit
     * for the REST OF THE MATCH (both innings, if this happened in the
     * first), marks the one-shot rainInterruptionUsed flag so no
     * further interruption is ever rolled, and — if this is happening
     * during the SECOND innings (a live chase) — immediately recomputes
     * a DLS-revised target, since unlike a first-innings interruption
     * (handled later in switchInnings, once the first innings' final
     * score is known), the chase target is knowable and revisable right
     * away.
     *
     * Sets activeRainDelay, which should pause ball-by-ball play in the
     * eventual match UI until resumeFromRainDelay() clears it.
     */
    fun applyRainInterruption(state: MatchState, interruption: RainInterruption): MatchState {
        val oversRemainingAtInterruption = maxOf(0, interruption.originalOversAllocated - interruption.oversBowledAtInterruption)
        val newOversLimit = interruption.oversBowledAtInterruption + maxOf(1, oversRemainingAtInterruption - interruption.oversLost)

        if (state.currentInnings == 1) {
            return state.copy(
                oversLimit = newOversLimit,
                rainInterruptionUsed = true,
                firstInningsInterruption = interruption,
                activeRainDelay = interruption
            )
        }

        // Second innings (live chase): revise the target right now. R1
        // is a FULL, uninterrupted innings at the current oversLimit —
        // under this game's one-interruption-per-match rule, the first
        // innings was never itself interrupted if we're here, so team 1
        // used its full allocation.
        val r1 = WeatherSystem.computeInningsResourcePercent(state.oversLimit, null)
        val r2 = WeatherSystem.computeInningsResourcePercent(interruption.originalOversAllocated, interruption)
        val firstInningsRuns = state.firstInningsScore?.runs ?: 0
        val dls = WeatherSystem.computeDlsTarget(firstInningsRuns, r1, r2, state.format)

        return state.copy(
            oversLimit = newOversLimit,
            rainInterruptionUsed = true,
            secondInningsInterruption = interruption,
            target = dls.target,
            dlsRevised = true,
            activeRainDelay = interruption
        )
    }

    /**
     * Dismisses the active rain-delay screen once the user has read the
     * revised overs/target and is ready to resume play.
     */
    fun resumeFromRainDelay(state: MatchState): MatchState = state.copy(activeRainDelay = null)
}

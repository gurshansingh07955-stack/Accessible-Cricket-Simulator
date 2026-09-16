package com.cricketsim.logic

import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.reflect.KMutableProperty1

/**
 * Ported from helpers/matchEngine.tsx (the Floot/React web app remains
 * the source of truth for gameplay design). Pure Kotlin, no Android
 * dependencies.
 *
 * Note: this file's web source ALSO re-declares its own copies of the
 * `MatchFormat` and `PitchType` type aliases (matchEngine.tsx is
 * actually their original home — PitchType.kt and MatchFormat.kt were
 * already split out from here in earlier sessions, ahead of this
 * file's own port, exactly as their doc comments say). Nothing new to
 * port for those two.
 *
 * MODELING NOTE — the web source's `StadiumEffects` interface here is
 * structurally IDENTICAL to WeatherSystem.kt's `StadiumMatchEffects`
 * (boundaryFactor/wicketCarryFactor/dewFactor). The web source
 * deliberately redeclares it locally rather than importing
 * StadiumMatchEffects from weatherSystem.tsx, specifically to avoid a
 * circular import (weatherSystem.tsx imports MatchFormat FROM this
 * file). Kotlin has no equivalent restriction — everything in this
 * package can reference everything else freely — so this port simply
 * reuses `StadiumMatchEffects` directly instead of redefining an
 * identical type.
 *
 * See the ⚠️ PARTIAL PORT warning in MatchState.kt: this file's
 * dependency on MatchState only touches a handful of fields, ported
 * ahead of the full match-state-machine port.
 */

enum class Difficulty { EASY, MEDIUM, HARD, HARDEST }

/** Which side of the coin the user called. */
enum class CoinSide { HEADS, TAILS }

data class TossResult(val winnerId: String, val decision: TossDecision)

object MatchEngine {

    private val FORMAT_OVERS: Map<MatchFormat, Int> = mapOf(
        MatchFormat.TEST to 90, // Per day/innings usually, but simplified for simulation
        MatchFormat.ODI to 50,
        MatchFormat.T20 to 20
    )

    /**
     * The format's standard overs-per-innings limit. Used to seed
     * MatchState.oversLimit when a match is created (the eventual
     * MatchState.kt) — oversLimit starts equal to this, but can be
     * permanently reduced by a rain interruption
     * (WeatherSystem.shouldTriggerRainInterruption), at which point
     * downstream code should read matchState.oversLimit instead of
     * recomputing this fixed value.
     */
    fun getFormatOvers(format: MatchFormat): Int = FORMAT_OVERS.getValue(format)

    private fun bumpProb(probs: OutcomeProbs, key: KMutableProperty1<OutcomeProbs, Double>, factor: Double) {
        key.set(probs, maxOf(0.001, key.get(probs) * factor))
    }

    private fun applyStadiumEffectsToProbabilities(probs: OutcomeProbs, effects: StadiumMatchEffects) {
        val combinedBoundaryFactor = effects.boundaryFactor * effects.dewFactor
        bumpProb(probs, OutcomeProbs::four, combinedBoundaryFactor)
        bumpProb(probs, OutcomeProbs::six, combinedBoundaryFactor)
        bumpProb(probs, OutcomeProbs::wicket, effects.wicketCarryFactor)
    }

    /** Simulates the toss between two teams. */
    fun simulateToss(userTeam: Team, opponentTeam: Team, userChoice: CoinSide): TossResult {
        val coinFlip = if (Random.nextDouble() < 0.5) CoinSide.HEADS else CoinSide.TAILS
        val userWon = userChoice == coinFlip
        val winner = if (userWon) userTeam else opponentTeam

        // AI Decision Logic
        var decision = TossDecision.BAT
        if (!userWon) {
            // Simple AI: Randomly choose for now, or bias towards batting on batting pitches
            decision = if (Random.nextDouble() > 0.5) TossDecision.BAT else TossDecision.BOWL
        }

        return TossResult(
            winnerId = winner.id,
            // If user won, this will be overwritten by UI selection later, but default to bat
            decision = if (userWon) TossDecision.BAT else decision
        )
    }

    /**
     * Calculates the probability of a specific outcome based on ratings
     * and conditions. This is a simplified probability engine.
     */
    private fun getOutcomeProbabilities(batsman: Player, bowler: Player, pitchType: PitchType, difficulty: Difficulty): OutcomeProbs {
        // Base probabilities (sum to 1.0)
        // 0, 1, 2, 3, 4, 6, W, WD, NB
        val probs = OutcomeProbs(
            dot = 0.35, one = 0.25, two = 0.1, three = 0.02, four = 0.1,
            six = 0.05, wicket = 0.05, wide = 0.04, noBall = 0.04
        )

        // Adjust based on ratings
        val batSkill = batsman.battingRating / 100.0
        val bowlSkill = bowler.bowlingRating / 100.0
        val skillDiff = batSkill - bowlSkill // Positive means batsman is better

        // Modify probs based on skill difference
        probs.dot -= skillDiff * 0.1
        probs.four += skillDiff * 0.05
        probs.six += skillDiff * 0.03
        probs.wicket -= skillDiff * 0.05

        // Adjust based on Pitch
        when (pitchType) {
            PitchType.BATTING -> {
                probs.four += 0.05
                probs.six += 0.03
                probs.wicket -= 0.02
            }
            PitchType.BOWLING, PitchType.GREEN -> {
                probs.wicket += 0.04
                probs.dot += 0.05
                probs.four -= 0.03
            }
            PitchType.DUSTY -> {
                // Spin friendly
                probs.wicket += 0.03
                probs.dot += 0.03
            }
            PitchType.BALANCED -> {}
        }

        // Adjust based on Difficulty (if user is batting)
        // If user is bowling, we invert the difficulty effect?
        // For simplicity, let's assume difficulty makes the OPPONENT play better/harder to beat.
        // But here we need context of who is batting.
        // We will handle difficulty adjustment in the main simulate function by passing a modifier.

        // Normalize probabilities to ensure no negative values
        probs.dot = maxOf(probs.dot, 0.01)
        probs.one = maxOf(probs.one, 0.01)
        probs.two = maxOf(probs.two, 0.01)
        probs.three = maxOf(probs.three, 0.01)
        probs.four = maxOf(probs.four, 0.01)
        probs.six = maxOf(probs.six, 0.01)
        probs.wicket = maxOf(probs.wicket, 0.01)
        probs.wide = maxOf(probs.wide, 0.01)
        probs.noBall = maxOf(probs.noBall, 0.01)

        return probs
    }

    /**
     * Applies the bowling side's field placement to the shared outcome
     * probabilities, based on where this specific shot is headed
     * (BattingSystem.getShotDirection) and whether — and how well — the
     * fielding side has a fielder standing in that exact spot. This is
     * what makes field placement a genuine tactical layer instead of
     * only gating no-ball legality.
     *
     * - An AERIAL shot into a sector with a DEEP fielder converts some
     *   of what would have been a boundary into a caught dismissal
     *   instead — how much depends on that fielder's own
     *   fieldingRating, so a placed part-time bowler drops far more
     *   chances than a specialist would.
     * - A GROUNDED shot into a sector with a SHORT/close fielder gets
     *   cut off before it reaches the rope — fewer fours, fewer easy
     *   singles and twos, more dot balls.
     * - A GROUNDED shot into a sector where only a DEEP fielder is
     *   standing still gets a boundary save at the rope, just later and
     *   less reliably, and can't produce a catch (the ball never left
     *   the ground).
     * - No fielder at all in the required spot is exactly what "finding
     *   the gap" means in real cricket — a modest boost on top of
     *   whatever this shot's outcome already was, rewarding a batter
     *   (human or AI) who picks a shot the current field doesn't cover,
     *   symmetrically for whichever side is batting.
     *
     * Returns whether this ball had a genuine "well-placed fielder
     * could catch this" opportunity, so a wicket that actually gets
     * rolled can be attributed to the field rather than a random
     * bowled/lbw/caught pick — see the dismissalType resolution in
     * simulateBall.
     */
    private fun applyFieldPlacementToProbabilities(
        probs: OutcomeProbs,
        decision: BattingDecision,
        fieldPlacements: List<FieldPlacement>,
        fieldingTeamPlayers: List<Player>
    ): Boolean {
        val direction = BattingSystem.getShotDirection(decision.shot, decision.intent) ?: return false

        fun ratingOf(playerId: String): Int = fieldingTeamPlayers.find { it.id == playerId }?.fieldingRating ?: 60

        val shortOrCloseFielder = fieldPlacements.find {
            it.sector == direction.sector && (it.depth == FieldingDepth.SHORT || it.depth == FieldingDepth.CLOSE)
        }
        val deepFielder = fieldPlacements.find { it.sector == direction.sector && it.depth == FieldingDepth.DEEP }

        if (direction.isAerial) {
            if (deepFielder != null) {
                val quality = ratingOf(deepFielder.playerId) / 100.0
                bumpProb(probs, OutcomeProbs::six, 1 - 0.55 * quality)
                bumpProb(probs, OutcomeProbs::four, 1 - 0.3 * quality)
                bumpProb(probs, OutcomeProbs::wicket, 1 + 1.6 * quality)
                return true
            }
            // Cleared everyone in that direction — a genuine gap in the
            // outfield. A short/close fielder is irrelevant to a shot
            // that's clearing the infield entirely, so deliberately no
            // effect from one here.
            bumpProb(probs, OutcomeProbs::six, 1.2)
            bumpProb(probs, OutcomeProbs::four, 1.1)
            return false
        }

        if (shortOrCloseFielder != null) {
            val quality = ratingOf(shortOrCloseFielder.playerId) / 100.0
            bumpProb(probs, OutcomeProbs::four, 1 - 0.5 * quality)
            bumpProb(probs, OutcomeProbs::one, 1 - 0.25 * quality)
            bumpProb(probs, OutcomeProbs::two, 1 - 0.35 * quality)
            bumpProb(probs, OutcomeProbs::three, 1 - 0.45 * quality)
            bumpProb(probs, OutcomeProbs::dot, 1 + 0.5 * quality)
            return false
        }
        if (deepFielder != null) {
            val quality = ratingOf(deepFielder.playerId) / 100.0
            bumpProb(probs, OutcomeProbs::four, 1 - 0.4 * quality)
            bumpProb(probs, OutcomeProbs::three, 1.15)
            return false
        }
        // A clean gap along the ground.
        bumpProb(probs, OutcomeProbs::four, 1.15)
        bumpProb(probs, OutcomeProbs::dot, 0.9)
        return false
    }

    /**
     * Simulates a single ball. bowlingDecision is always required now:
     * for user-controlled bowling it's the ResolvedBowlingDecision
     * produced by the line/variation/speed/pitcher-gesture flow, and
     * for AI-controlled bowling (the user is batting) it's
     * BowlingSystem.generateAiBowlingDecision()'s output. battingDecision
     * is only ever present when the USER is batting for a real
     * gesture-driven turn — though as of BattingSystem.kt's port,
     * AI batting produces the exact same BattingDecision shape via
     * generateAiBattingDecision(), so callers should pass that too once
     * the eventual MatchState.kt/pages/match.tsx-equivalent wires it up.
     * Both decisions' layers are applied to the same shared OutcomeProbs
     * object; there is no separate probability model per source.
     */
    fun simulateBall(
        matchState: MatchState,
        difficulty: Difficulty,
        pitchType: PitchType,
        bowlingDecision: ResolvedBowlingDecision,
        battingDecision: BattingDecision? = null,
        // True when the bowling side's current fielding arrangement
        // breaks the powerplay/overall deep-fielder cap. An illegal
        // field is called by the umpire before the delivery even
        // happens, so — like the beamer no-ball below — this
        // short-circuits the normal outcome sampling entirely rather
        // than being folded into the probabilities.
        illegalField: Boolean = false,
        // Ground/weather modifiers for the stadium this match is being
        // played at — see WeatherSystem.getStadiumMatchEffects(). Kept
        // nullable (rather than required) so any older saved match
        // state missing a stadium selection still simulates fine with
        // no ground effects applied.
        stadiumEffects: StadiumMatchEffects? = null
    ): BallOutcome {
        val batsman = matchState.currentBatsmen.first // Striker
        val bowler = matchState.currentBowler

        if (illegalField) {
            return BallOutcome(
                runs = 0, isWicket = false, isWide = false, isNoBall = true, extraRuns = 1,
                batsmanId = batsman.id, bowlerId = bowler.id,
                commentary = "No ball! The umpire has spotted too many fielders in the deep — illegal field.",
                bowlingQualityTier = bowlingDecision.qualityTier,
                bowlingActualLength = bowlingDecision.actualLength
            )
        }

        // A badly missed yorker that balloons into a beamer is an
        // automatic no-ball under the laws of cricket — not a
        // probability roll, so it short-circuits the normal outcome
        // sampling entirely.
        if (bowlingDecision.forcedNoBall) {
            return BallOutcome(
                runs = 0, isWicket = false, isWide = false, isNoBall = true, extraRuns = 1,
                batsmanId = batsman.id, bowlerId = bowler.id,
                commentary = "No ball! That's a beamer, way too full and dangerously high from ${bowler.name}.",
                bowlingQualityTier = bowlingDecision.qualityTier,
                bowlingActualLength = bowlingDecision.actualLength
            )
        }

        // Determine if user is batting or bowling to apply difficulty correctly
        val isUserBatting = matchState.battingTeam.id == matchState.userTeam.id

        // Harder difficulty = higher wicket chance/lower run chance for
        // the user (whichever side they're on); easier = the opposite.
        // The modifier's VALUE doesn't depend on which side the user is
        // on — only how it gets APPLIED below does.
        val difficultyModifier = when (difficulty) {
            Difficulty.EASY -> 0.1 // Bonus to user
            Difficulty.MEDIUM -> 0.0
            Difficulty.HARD -> -0.1 // Penalty to user
            Difficulty.HARDEST -> -0.2
        }

        val probs = getOutcomeProbabilities(batsman, bowler, pitchType, difficulty)

        // Ground/weather modifiers apply unconditionally (both user and
        // AI batting, independent of the fielding/batting-decision
        // layers below) — a small boundary or a heavy-dew night chase
        // is a property of the GROUND and the CONDITIONS, not of how
        // any one shot was played.
        if (stadiumEffects != null) {
            applyStadiumEffectsToProbabilities(probs, stadiumEffects)
        }

        // Apply difficulty modifier.
        // If modifier is positive (User advantage): Increase runs, decrease wickets.
        // If modifier is negative (User disadvantage): Decrease runs, increase wickets.
        if (isUserBatting) {
            probs.four += difficultyModifier * 0.5
            probs.six += difficultyModifier * 0.3
            probs.wicket -= difficultyModifier * 0.3
        } else {
            // User is bowling. Positive modifier means user advantage ->
            // opponent (batsman) scores less, gets out more.
            probs.four -= difficultyModifier * 0.5
            probs.six -= difficultyModifier * 0.3
            probs.wicket += difficultyModifier * 0.3
        }

        // Layer the bowling decision (line, actual length, variation,
        // swing, execution quality) on top of everything else — always
        // present now, whether it came from the user's gesture or the
        // AI generator.
        BowlingSystem.applyBowlingDecisionToProbabilities(probs, bowlingDecision)

        // Layer the batting decision (footwork match, shot-choice
        // compatibility, intent direction, timing) on top — present for
        // BOTH user and AI batting, since generateAiBattingDecision()
        // produces the exact same BattingDecision shape a real gesture
        // would.
        var fieldingCatchOpportunity = false
        if (battingDecision != null) {
            BattingSystem.applyBattingDecisionToProbabilities(
                probs, battingDecision, bowlingDecision.actualLength, bowlingDecision.bowlingStyle
            )
            // Field placement layered on last — see
            // applyFieldPlacementToProbabilities' doc comment. Uses
            // whichever side is currently bowling's fielders and field,
            // so this applies symmetrically whether the user or the AI
            // is fielding.
            fieldingCatchOpportunity = applyFieldPlacementToProbabilities(
                probs, battingDecision, matchState.fieldPlacements, matchState.bowlingTeam.players
            )
        }

        // Normalize again and sample. LinkedHashMap preserves this
        // exact insertion order, matching the web source's
        // Object.entries(probs) iteration order (dot, one, two, three,
        // four, six, wicket, wide, noBall) — the base OutcomeProbs
        // literal's own key order in getOutcomeProbabilities.
        val probsByKey = linkedMapOf(
            "dot" to probs.dot, "one" to probs.one, "two" to probs.two, "three" to probs.three,
            "four" to probs.four, "six" to probs.six, "wicket" to probs.wicket,
            "wide" to probs.wide, "noBall" to probs.noBall
        )
        val totalProb = probsByKey.values.sum()
        val rand = Random.nextDouble() * totalProb
        var cumulative = 0.0
        var outcomeType = "dot"
        for ((key, value) in probsByKey) {
            cumulative += value
            if (rand <= cumulative) {
                outcomeType = key
                break
            }
        }

        // A mis-executed length that ballooned into a batsman-friendly
        // full toss / long hop makes an edge far less likely — the ball
        // basically has no lateral movement or pace variation for the
        // bat to mistime against.
        val isMishitLength = bowlingDecision.actualLength == MisExecutedLength.FULL_TOSS ||
            bowlingDecision.actualLength == MisExecutedLength.LOW_FULL_TOSS ||
            bowlingDecision.actualLength == MisExecutedLength.LONG_HOP

        var runs = 0
        var isWicket = false
        var isWide = false
        var isNoBall = false
        var extraRuns = 0
        var dismissalType: DismissalType? = null
        var isEdge: Boolean? = null

        when (outcomeType) {
            "one" -> runs = 1
            "two" -> runs = 2
            "three" -> runs = 3
            "four" -> {
                runs = 4
                // Decide once whether this boundary was a clean shot or
                // an edge. Every downstream description (on-screen
                // text, screen-reader announcement, AI voice
                // commentary) reads this same flag.
                isEdge = Random.nextDouble() < (if (isMishitLength) 0.08 else 0.3)
            }
            "six" -> {
                runs = 6
                isEdge = Random.nextDouble() < (if (isMishitLength) 0.05 else 0.2)
            }
            "wicket" -> {
                isWicket = true
                // Decide the dismissal type once here. The eventual
                // MatchState.recordWicketFall()/scorecard/stats consume
                // this exact value, and generateCommentary below picks
                // its phrase from the matching pool, so the text, the
                // AI voice clip, and the recorded dismissal can never
                // disagree.
                //
                // A wicket that came from
                // applyFieldPlacementToProbabilities' wicket boost (an
                // aerial shot into a well-placed deep fielder) should
                // actually BE a caught dismissal, not a random bowled/
                // lbw/caught pick — otherwise the commentary would
                // describe a clean-bowled delivery on a ball the field
                // placement is what actually got the batter out.
                // Weighted rather than forced, since the base
                // (non-fielding) wicket chance the ball already carried
                // could still have been a genuine bowled/lbw on its own
                // merits.
                val dismissalTypes = if (fieldingCatchOpportunity) {
                    listOf(DismissalType.CAUGHT, DismissalType.CAUGHT, DismissalType.CAUGHT, DismissalType.BOWLED, DismissalType.LBW)
                } else {
                    listOf(DismissalType.BOWLED, DismissalType.CAUGHT, DismissalType.LBW)
                }
                dismissalType = dismissalTypes[(Random.nextDouble() * dismissalTypes.size).toInt().coerceAtMost(dismissalTypes.size - 1)]
                if (dismissalType == DismissalType.CAUGHT) {
                    // Edge caught behind/in the slips vs. mistimed and
                    // caught in the outfield — again decided once and
                    // reused everywhere. A fielding-assisted catch is
                    // specifically the latter: a skied shot taken by a
                    // fielder standing in the right spot, not an edge.
                    isEdge = if (fieldingCatchOpportunity) false else Random.nextDouble() < 0.5
                }
            }
            "wide" -> {
                isWide = true
                extraRuns = 1
            }
            "noBall" -> {
                isNoBall = true
                extraRuns = 1
                // Free hit logic could be added here
            }
            else -> runs = 0 // dot
        }

        val result = BallOutcome(
            runs = runs, isWicket = isWicket, isWide = isWide, isNoBall = isNoBall, extraRuns = extraRuns,
            batsmanId = batsman.id, bowlerId = bowler.id, commentary = "",
            dismissalType = dismissalType, isEdge = isEdge,
            bowlingQualityTier = bowlingDecision.qualityTier, bowlingActualLength = bowlingDecision.actualLength
        )
        return result.copy(commentary = generateCommentary(result, batsman.name, bowler.name))
    }

    private fun randomIndex(size: Int): Int = (Random.nextDouble() * size).toInt().coerceAtMost(size - 1)

    /**
     * Generates commentary string based on outcome. Every phrase pool
     * here corresponds 1:1 with a commentary-library category used for
     * the AI voice duo (the eventual CommentaryLibrary.kt), keyed off
     * the same outcome fields (dismissalType, isEdge) so the text and
     * the AI voice never describe the ball differently.
     */
    fun generateCommentary(outcome: BallOutcome, batsmanName: String, bowlerName: String): String {
        val actualLength = outcome.bowlingActualLength
        val lengthPrefix = if (
            actualLength != null &&
            (actualLength == MisExecutedLength.FULL_TOSS || actualLength == MisExecutedLength.LOW_FULL_TOSS || actualLength == MisExecutedLength.LONG_HOP)
        ) {
            "${BowlingSystem.lengthLabel(actualLength)}! "
        } else {
            ""
        }

        if (outcome.isWicket) {
            if (outcome.dismissalType == DismissalType.BOWLED) {
                val phrases = listOf(
                    "OUT! $batsmanName has to go, bowled by $bowlerName!",
                    "Clean bowled! $bowlerName shatters the stumps!"
                )
                return phrases[randomIndex(phrases.size)]
            }
            if (outcome.dismissalType == DismissalType.LBW) {
                val phrases = listOf(
                    "LBW! Huge appeal and the finger goes up! $batsmanName is gone.",
                    "OUT! Trapped right in front, $batsmanName given out LBW."
                )
                return phrases[randomIndex(phrases.size)]
            }
            // caught
            if (outcome.isEdge == true) {
                val phrases = listOf(
                    "Edged and caught! $batsmanName has to depart after that thin edge.",
                    "There's the edge, and it's safely taken! $batsmanName is out."
                )
                return phrases[randomIndex(phrases.size)]
            }
            val phrases = listOf(
                "Caught! $batsmanName mistimes it and is caught in the deep.",
                "Skied it! Well taken by the fielder, $batsmanName has to go."
            )
            return phrases[randomIndex(phrases.size)]
        }

        if (outcome.runs == 6) {
            if (outcome.isEdge == true) {
                val phrases = listOf(
                    "Top edge! But that's sailed all the way for six!",
                    "Mistimed it completely, but it's still gone the distance for six!"
                )
                return lengthPrefix + phrases[randomIndex(phrases.size)]
            }
            val phrases = listOf(
                "SIX! $batsmanName smashes it out of the park!",
                "Huge hit! That's gone all the way for six!",
                "What a shot! $batsmanName clears the boundary with ease."
            )
            return lengthPrefix + phrases[randomIndex(phrases.size)]
        }

        if (outcome.runs == 4) {
            if (outcome.isEdge == true) {
                val phrases = listOf(
                    "Edged but safe! Runs away for four.",
                    "Thick outside edge, but that races away for four runs."
                )
                return lengthPrefix + phrases[randomIndex(phrases.size)]
            }
            val phrases = listOf(
                "FOUR! Beautiful cover drive by $batsmanName.",
                "Cracked away to the boundary for four!",
                "Pulled away powerfully by $batsmanName for four runs."
            )
            return lengthPrefix + phrases[randomIndex(phrases.size)]
        }

        if (outcome.runs == 1) return "$batsmanName pushes it into the gap for a single."
        if (outcome.runs == 2) return "Good running between the wickets, they come back for two."
        if (outcome.runs == 3) return "Great fielding in the deep, keeps it to three."
        if (outcome.runs == 5) return "Five runs! A mix-up in the field lets them steal an extra one."
        if (outcome.isWide) return "Wide ball signaled by the umpire."
        if (outcome.isNoBall) return "No ball! $bowlerName oversteps."

        val dotPhrases = listOf(
            "No run. Solid defense by $batsmanName.",
            "Beaten! Lovely delivery from $bowlerName.",
            "Straight to the fielder, no run.",
            "Dot ball."
        )
        return dotPhrases[randomIndex(dotPhrases.size)]
    }

    /**
     * Calculates win probability for the batting team. Very simplified
     * heuristic.
     */
    fun calculateWinProbability(matchState: MatchState): Int {
        if (matchState.currentInnings == 1) return 50 // Start of match roughly equal

        val target = matchState.target ?: 0
        val currentRuns = matchState.score.runs
        val runsNeeded = target - currentRuns
        val wicketsLeft = 10 - matchState.score.wickets

        val maxOvers = matchState.oversLimit
        val ballsBowled = matchState.score.overs * 6 + matchState.score.balls
        val ballsRemaining = maxOvers * 6 - ballsBowled

        if (runsNeeded <= 0) return 100
        if (ballsRemaining <= 0 && runsNeeded > 0) return 0
        if (wicketsLeft <= 0) return 0

        val rrr = runsNeeded / (ballsRemaining / 6.0) // Required Run Rate

        // Heuristic:
        // RRR < 6: High chance (>80%)
        // RRR 6-8: Good chance (60-80%)
        // RRR 8-10: Competitive (40-60%)
        // RRR 10-12: Tough (20-40%)
        // RRR > 12: Very tough (<20%)
        var prob = when {
            rrr < 6 -> 85.0
            rrr < 8 -> 70.0
            rrr < 10 -> 50.0
            rrr < 12 -> 30.0
            else -> 10.0
        }

        // Adjust for wickets in hand. If wickets are low, probability drops drastically.
        if (wicketsLeft < 3) prob *= 0.3
        else if (wicketsLeft < 5) prob *= 0.6

        return prob.roundToInt().coerceIn(0, 100)
    }
}

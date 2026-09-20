package com.cricketsim.logic

import kotlin.math.round

/**
 * Ported from helpers/matchStats.tsx (the Floot/React web app remains
 * the source of truth for gameplay design). Pure Kotlin, no Android
 * dependencies.
 *
 * Discovered as an undocumented dependency of helpers/matchState.tsx
 * while porting MatchEngine.kt — see the "Newly discovered dependency"
 * note in PORTING_NOTES.md. This file has no dependency on MatchState
 * itself other than `BallOutcome` (already ported, see BallOutcome.kt),
 * so it's fully portable on its own ahead of the full MatchState.kt
 * state-machine port.
 *
 * MODELING NOTE — the web source types `BatsmanStats.dismissalType` as
 * a bare `string | null`, but every real call site
 * (helpers/matchState.tsx's recordWicketFall) always passes a value
 * from the `"bowled" | "caught" | "lbw"` domain — exactly
 * `DismissalType` (already ported, see DismissalType.kt). This port
 * uses `DismissalType?` instead of `String?` for that field: a tighter,
 * equally-accurate type with no loss of behavior, consistent with how
 * BallOutcome.dismissalType is already modeled.
 *
 * All numeric formulas (strike rate and economy rounding, over/ball
 * carry-over arithmetic) are exact matches to the web source.
 *
 * ⚠️ DELIBERATE ADDITIONS BEYOND THE WEB SOURCE. The web source has
 * two gaps its own scorecard visibly works around (a hardcoded
 * `false` for maiden detection, and a Fall of Wickets table that shows
 * the batsman's own runs and numbers wickets by batting order because
 * the team score at each fall was never recorded). Both are fixed here,
 * additively — nothing the web source computes has changed:
 * - MAIDENS: InningsData.currentOverRuns tallies what the current over
 *   has cost (runs plus wides/no-balls, which count against the
 *   bowler, same as real cricket). When a legal delivery completes the
 *   over with that tally still at zero, the bowler is credited a
 *   maiden. Only wides and no-balls exist as extras in this game, so
 *   there is no bye/leg-bye handling to worry about. Bowler changes
 *   only ever happen at over boundaries, so one tally per innings is
 *   enough.
 * - FALL OF WICKETS: InningsData.fallOfWickets records, at the moment
 *   recordDismissal runs, the wicket number (in the order wickets
 *   actually fell), the batsman's runs and balls, the TEAM score, and
 *   the overs. recordDismissal is called after updateInningsDataForBall
 *   has already added the dismissal ball to the totals, so the figures
 *   are the score immediately after that ball.
 * Both new InningsData fields have defaults, so nothing constructing an
 * InningsData needs to change.
 */

data class BatsmanStats(
    val playerId: String,
    val playerName: String,
    val runs: Int,
    val ballsFaced: Int,
    val fours: Int,
    val sixes: Int,
    val strikeRate: Double,
    val isOut: Boolean,
    val dismissalType: DismissalType? = null,
    val dismissedBy: String? = null,
    val isCurrentlyBatting: Boolean
)

data class BowlerStats(
    val playerId: String,
    val playerName: String,
    val overs: Int,
    val balls: Int,
    val maidens: Int,
    val runsConceded: Int,
    val wickets: Int,
    val economy: Double,
    val wides: Int,
    val noBalls: Int
)

data class Partnership(
    val batsman1Id: String,
    val batsman1Name: String,
    val batsman1Runs: Int,
    val batsman2Id: String,
    val batsman2Name: String,
    val batsman2Runs: Int,
    val totalRuns: Int,
    val balls: Int
)

data class Extras(val wides: Int, val noBalls: Int, val total: Int)

/**
 * One wicket, as it fell. Not in the web source — see the file header.
 * `teamRuns`/`overs`/`balls` are the team score and over count
 * immediately after the dismissal ball.
 */
data class FallOfWicket(
    val wicketNumber: Int,
    val batsmanId: String,
    val batsmanName: String,
    val batsmanRuns: Int,
    val batsmanBalls: Int,
    val teamRuns: Int,
    val overs: Int,
    val balls: Int,
    val dismissalType: DismissalType,
    val dismissedBy: String
)

data class InningsData(
    val battingTeamId: String,
    val battingTeamName: String,
    val bowlingTeamId: String,
    val bowlingTeamName: String,
    val batsmanStats: List<BatsmanStats>,
    val bowlerStats: List<BowlerStats>,
    val partnerships: List<Partnership>,
    val currentPartnership: Partnership,
    val totalRuns: Int,
    val totalWickets: Int,
    val totalOvers: Int,
    val totalBalls: Int,
    val extras: Extras,
    // Not in the web source — see the file header.
    val fallOfWickets: List<FallOfWicket> = emptyList(),
    // Runs (including wides/no-balls) conceded so far in the over in
    // progress; reset to 0 whenever an over completes. Drives maiden
    // detection.
    val currentOverRuns: Int = 0
)

object MatchStats {

    fun calculateStrikeRate(runs: Int, ballsFaced: Int): Double {
        if (ballsFaced == 0) return 0.0
        val raw = (runs.toDouble() / ballsFaced) * 100
        return round(raw * 100) / 100.0
    }

    fun calculateEconomy(runsConceded: Int, overs: Int, balls: Int): Double {
        val totalOvers = overs + balls / 6.0
        if (totalOvers == 0.0) return 0.0
        val raw = runsConceded / totalOvers
        return round(raw * 100) / 100.0
    }

    fun createBatsmanStats(player: Player, isCurrentlyBatting: Boolean = false): BatsmanStats {
        return BatsmanStats(
            playerId = player.id,
            playerName = player.name,
            runs = 0,
            ballsFaced = 0,
            fours = 0,
            sixes = 0,
            strikeRate = 0.0,
            isOut = false,
            dismissalType = null,
            dismissedBy = null,
            isCurrentlyBatting = isCurrentlyBatting
        )
    }

    fun createBowlerStats(player: Player): BowlerStats {
        return BowlerStats(
            playerId = player.id,
            playerName = player.name,
            overs = 0,
            balls = 0,
            maidens = 0,
            runsConceded = 0,
            wickets = 0,
            economy = 0.0,
            wides = 0,
            noBalls = 0
        )
    }

    fun createPartnership(batsman1: Player, batsman2: Player): Partnership {
        return Partnership(
            batsman1Id = batsman1.id,
            batsman1Name = batsman1.name,
            batsman1Runs = 0,
            batsman2Id = batsman2.id,
            batsman2Name = batsman2.name,
            batsman2Runs = 0,
            totalRuns = 0,
            balls = 0
        )
    }

    fun createEmptyInningsData(
        battingTeam: Team,
        bowlingTeam: Team,
        initialBatsmen: Pair<Player, Player>,
        initialBowler: Player
    ): InningsData {
        // Create stats for the two opening batsmen
        val batsmanStats = listOf(
            createBatsmanStats(initialBatsmen.first, true),
            createBatsmanStats(initialBatsmen.second, true)
        )

        // Create stats for the opening bowler
        val bowlerStats = listOf(createBowlerStats(initialBowler))

        // Create initial partnership
        val currentPartnership = createPartnership(initialBatsmen.first, initialBatsmen.second)

        return InningsData(
            battingTeamId = battingTeam.id,
            battingTeamName = battingTeam.name,
            bowlingTeamId = bowlingTeam.id,
            bowlingTeamName = bowlingTeam.name,
            batsmanStats = batsmanStats,
            bowlerStats = bowlerStats,
            partnerships = emptyList(),
            currentPartnership = currentPartnership,
            totalRuns = 0,
            totalWickets = 0,
            totalOvers = 0,
            totalBalls = 0,
            extras = Extras(wides = 0, noBalls = 0, total = 0)
        )
    }

    fun updateBatsmanStats(
        inningsData: InningsData,
        batsmanId: String,
        runs: Int,
        isBoundary4: Boolean,
        isBoundary6: Boolean,
        ballFaced: Boolean
    ): InningsData {
        val batsmanStats = inningsData.batsmanStats.map { stats ->
            if (stats.playerId == batsmanId) {
                val newRuns = stats.runs + runs
                val newBallsFaced = stats.ballsFaced + if (ballFaced) 1 else 0
                val newFours = stats.fours + if (isBoundary4) 1 else 0
                val newSixes = stats.sixes + if (isBoundary6) 1 else 0
                stats.copy(
                    runs = newRuns,
                    ballsFaced = newBallsFaced,
                    fours = newFours,
                    sixes = newSixes,
                    strikeRate = calculateStrikeRate(newRuns, newBallsFaced)
                )
            } else {
                stats
            }
        }
        return inningsData.copy(batsmanStats = batsmanStats)
    }

    fun updateBowlerStats(
        inningsData: InningsData,
        bowlerId: String,
        runs: Int,
        isWicket: Boolean,
        isWide: Boolean,
        isNoBall: Boolean,
        isMaiden: Boolean
    ): InningsData {
        val bowlerStats = inningsData.bowlerStats.map { stats ->
            if (stats.playerId == bowlerId) {
                var newBalls = stats.balls
                var newOvers = stats.overs

                // Only increment balls for legal deliveries
                if (!isWide && !isNoBall) {
                    newBalls += 1
                    if (newBalls == 6) {
                        newOvers += 1
                        newBalls = 0
                    }
                }

                val newRunsConceded = stats.runsConceded + runs
                val newWickets = stats.wickets + if (isWicket) 1 else 0
                val newMaidens = stats.maidens + if (isMaiden) 1 else 0
                val newWides = stats.wides + if (isWide) 1 else 0
                val newNoBalls = stats.noBalls + if (isNoBall) 1 else 0

                stats.copy(
                    overs = newOvers,
                    balls = newBalls,
                    maidens = newMaidens,
                    runsConceded = newRunsConceded,
                    wickets = newWickets,
                    economy = calculateEconomy(newRunsConceded, newOvers, newBalls),
                    wides = newWides,
                    noBalls = newNoBalls
                )
            } else {
                stats
            }
        }
        return inningsData.copy(bowlerStats = bowlerStats)
    }

    /**
     * Marks the batsman out and (not in the web source — see the file
     * header) records the fall of wicket. Must be called AFTER
     * updateInningsDataForBall has added the dismissal ball to the
     * innings totals, which is the order MatchStateMachine.applyBallOutcome
     * then recordWicketFall already runs in, so the team score recorded
     * is the score right after the dismissal ball.
     */
    fun recordDismissal(
        inningsData: InningsData,
        batsmanId: String,
        dismissalType: DismissalType,
        dismissedBy: String
    ): InningsData {
        val dismissed = inningsData.batsmanStats.firstOrNull { it.playerId == batsmanId }

        val batsmanStats = inningsData.batsmanStats.map { stats ->
            if (stats.playerId == batsmanId) {
                stats.copy(
                    isOut = true,
                    dismissalType = dismissalType,
                    dismissedBy = dismissedBy,
                    isCurrentlyBatting = false
                )
            } else {
                stats
            }
        }

        val fallOfWickets = if (dismissed == null) {
            inningsData.fallOfWickets
        } else {
            inningsData.fallOfWickets + FallOfWicket(
                wicketNumber = inningsData.fallOfWickets.size + 1,
                batsmanId = dismissed.playerId,
                batsmanName = dismissed.playerName,
                batsmanRuns = dismissed.runs,
                batsmanBalls = dismissed.ballsFaced,
                teamRuns = inningsData.totalRuns,
                overs = inningsData.totalOvers,
                balls = inningsData.totalBalls,
                dismissalType = dismissalType,
                dismissedBy = dismissedBy
            )
        }

        return inningsData.copy(batsmanStats = batsmanStats, fallOfWickets = fallOfWickets)
    }

    fun updatePartnership(inningsData: InningsData, strikerId: String, runs: Int, ballFaced: Boolean): InningsData {
        val partnership = inningsData.currentPartnership

        val newPartnership = if (partnership.batsman1Id == strikerId) {
            partnership.copy(
                batsman1Runs = partnership.batsman1Runs + runs,
                totalRuns = partnership.totalRuns + runs,
                balls = partnership.balls + if (ballFaced) 1 else 0
            )
        } else {
            partnership.copy(
                batsman2Runs = partnership.batsman2Runs + runs,
                totalRuns = partnership.totalRuns + runs,
                balls = partnership.balls + if (ballFaced) 1 else 0
            )
        }

        return inningsData.copy(currentPartnership = newPartnership)
    }

    fun endPartnership(inningsData: InningsData, newBatsman: Player, continuingBatsman: Player): InningsData {
        // Archive the current partnership
        val partnerships = inningsData.partnerships + inningsData.currentPartnership

        // Create new partnership
        val currentPartnership = createPartnership(continuingBatsman, newBatsman)

        // Add the new batsman to stats
        val batsmanStats = inningsData.batsmanStats + createBatsmanStats(newBatsman, true)

        return inningsData.copy(partnerships = partnerships, currentPartnership = currentPartnership, batsmanStats = batsmanStats)
    }

    fun addBowlerIfNotExists(inningsData: InningsData, bowler: Player): InningsData {
        val exists = inningsData.bowlerStats.any { it.playerId == bowler.id }
        if (exists) return inningsData

        val bowlerStats = inningsData.bowlerStats + createBowlerStats(bowler)
        return inningsData.copy(bowlerStats = bowlerStats)
    }

    fun getLastSixBalls(ballByBall: List<BallOutcome>): List<BallOutcome> {
        // Get only legal deliveries (not wides or no-balls)
        val legalBalls = ballByBall.filter { !it.isWide && !it.isNoBall }
        // Return last 6 legal balls
        return legalBalls.takeLast(6)
    }

    fun updateInningsDataForBall(
        inningsData: InningsData,
        outcome: BallOutcome,
        strikerId: String,
        isLegalDelivery: Boolean
    ): InningsData {
        var updated = inningsData

        // Update batsman stats
        val isBoundary4 = outcome.runs == 4 && !outcome.isWide && !outcome.isNoBall
        val isBoundary6 = outcome.runs == 6
        updated = updateBatsmanStats(updated, strikerId, outcome.runs, isBoundary4, isBoundary6, isLegalDelivery)

        // Maiden detection (not in the web source — see the file header):
        // this delivery completes the over when it's legal and it's the
        // 6th legal ball (inningsData.totalBalls is still the PRE-ball
        // count here, so 5 means this ball makes six), and it's a maiden
        // when nothing at all was conceded in that over.
        val runsConcededThisBall = outcome.runs + outcome.extraRuns
        val overRunsSoFar = inningsData.currentOverRuns + runsConcededThisBall
        val completesOver = isLegalDelivery && inningsData.totalBalls == 5
        val isMaiden = completesOver && overRunsSoFar == 0

        // Update bowler stats
        updated = updateBowlerStats(
            updated, outcome.bowlerId, runsConcededThisBall,
            outcome.isWicket, outcome.isWide, outcome.isNoBall,
            isMaiden
        )

        // Update partnership
        if (!outcome.isWicket) {
            updated = updatePartnership(updated, strikerId, outcome.runs, isLegalDelivery)
        }

        // Update totals
        val totalRuns = updated.totalRuns + outcome.runs + outcome.extraRuns
        val totalWickets = updated.totalWickets + if (outcome.isWicket) 1 else 0

        var totalBalls = updated.totalBalls
        var totalOvers = updated.totalOvers

        if (isLegalDelivery) {
            totalBalls += 1
            if (totalBalls == 6) {
                totalOvers += 1
                totalBalls = 0
            }
        }

        // Update extras
        var extras = updated.extras
        if (outcome.isWide) {
            extras = extras.copy(wides = extras.wides + 1, total = extras.total + outcome.extraRuns)
        }
        if (outcome.isNoBall) {
            extras = extras.copy(noBalls = extras.noBalls + 1, total = extras.total + outcome.extraRuns)
        }

        return updated.copy(
            totalRuns = totalRuns,
            totalWickets = totalWickets,
            totalOvers = totalOvers,
            totalBalls = totalBalls,
            extras = extras,
            currentOverRuns = if (completesOver) 0 else overRunsSoFar
        )
    }
}

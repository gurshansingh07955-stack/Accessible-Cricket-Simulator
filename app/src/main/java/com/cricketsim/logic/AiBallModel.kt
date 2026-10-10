package com.cricketsim.logic

import kotlin.random.Random

/**
 * How one ball turns out when NOBODY is controlling either side (tournament matches the user
 * does not play). The live match screen's engine (MatchEngine.simulateBall) is built around a
 * human player's gestures: its "AI batter" times the ball better than its "AI bowler" bowls it,
 * which is fine when a person is bowling or batting against it, but between two AI sides it
 * produced 230-260 runs an innings, a hundred nearly every match and bowlers who never took
 * wickets. This model replaces ONLY the dice for AI-v-AI balls; everything after the dice (the
 * scorecard, strike rotation, bowler changes, fields, rain and DLS, Super Overs) still runs
 * through the normal match code, because the ball is handed back through
 * MatchSimulation.simulateOneBall's `outcomeOverride`.
 *
 * WHAT DECIDES A BALL (all of it is real match context, none of it is a free dice roll):
 *  - the BATTER's rating against the BOWLER's rating (a star against a part-timer scores faster
 *    and is out less; a number eleven against a front-line bowler is the reverse);
 *  - how SET the batter is (new batters are vulnerable, settled ones score freer), and a small
 *    pull-back after a big score so hundreds stay rare;
 *  - the PHASE of the innings (powerplay: boundaries; middle: strike rotation; death: big hitting
 *    and wickets);
 *  - the PITCH (flat, green, dusty: spinners get more from a dusty one) and the GROUND
 *    (boundary size, dew in a night chase, altitude), straight from WeatherSystem's
 *    stadium effects;
 *  - the CHASE: a steep required rate means risks (more boundaries and more wickets), a
 *    comfortable one means caution.
 *
 * CALIBRATION. The numbers below were tuned against a simulation of whole innings until an
 * average side makes about 150 on a balanced pitch (about 135 green, 145 dusty, 165 flat),
 * loses about six wickets, makes a hundred roughly once in fifteen matches, and a chasing side
 * wins a little over half the time. Change them together and re-check those figures.
 */
object AiBallModel {

    // Base chances for one delivery on a balanced pitch between evenly matched players.
    private const val WIDE = 0.026
    private const val NO_BALL = 0.007
    private const val DOT = 0.36
    private const val ONE = 0.325
    private const val TWO = 0.075
    private const val THREE = 0.006
    private const val FOUR = 0.10
    private const val SIX = 0.056
    private const val WICKET = 0.043

    // How strongly the batter-minus-bowler rating gap moves boundaries, dots and wickets.
    private const val SKILL_BOUNDARY = 1.0
    private const val SKILL_DOT = 0.5
    private const val SKILL_WICKET = 1.5

    private const val RUN_OUT_CHANCE = 0.012
    private const val MAX_RUN_OUTS = 2

    fun sample(state: MatchState, stadium: Stadium, random: Random): BallOutcome {
        val striker = state.currentBatsmen.first
        val bowler = state.currentBowler
        val balls = state.score.overs * 6 + state.score.balls
        val limit = maxOf(1, state.oversLimit)
        val stats = state.currentInningsData.batsmanStats.firstOrNull { it.playerId == striker.id }
        val faced = stats?.ballsFaced ?: 0
        val batterScore = stats?.runs ?: 0
        val spin = bowler.bowlingStyle == BowlingStyle.SPIN
        val freeHit = state.freeHit
        val effects = WeatherSystem.getStadiumMatchEffects(stadium, state.currentInnings == 2 && state.weather.isDayNight)

        // Rating gap: positive favours the batter.
        val edge = (striker.battingRating - bowler.bowlingRating) / 100.0
        var fBoundary = (1 + SKILL_BOUNDARY * edge).coerceIn(0.6, 1.7)
        var fDot = (1 - SKILL_DOT * edge).coerceIn(0.7, 1.3)
        var fWicket = (1 - SKILL_WICKET * edge).coerceIn(0.45, 1.9)
        var fRuns = 1.0

        // How set the batter is, and a pull-back once he is well past fifty.
        fWicket *= when {
            batterScore >= 75 -> 1.9
            batterScore >= 45 -> 1.3
            else -> 1.0
        }
        fWicket *= when {
            faced < 6 -> 1.35
            faced < 15 -> 1.1
            faced < 30 -> 0.9
            else -> 0.75
        }
        fBoundary *= when {
            faced < 6 -> 0.8
            faced >= 20 -> 1.12
            else -> 1.0
        }

        // Phase of the innings.
        val fraction = balls.toDouble() / (limit * 6)
        when {
            fraction < 0.3 -> { fBoundary *= 1.15; fDot *= 0.95 }
            fraction < 0.75 -> { fBoundary *= 0.85; fRuns *= 1.08; fWicket *= 0.95 }
            else -> { fBoundary *= 1.4; fWicket *= 1.3; fDot *= 0.85 }
        }

        // The pitch.
        when (state.pitchType) {
            PitchType.BATTING -> { fBoundary *= 1.1; fWicket *= 0.9 }
            PitchType.GREEN, PitchType.BOWLING -> { fBoundary *= 0.92; fWicket *= 1.15; fDot *= 1.05 }
            PitchType.DUSTY -> if (spin) { fWicket *= 1.15; fDot *= 1.05; fBoundary *= 0.95 } else fBoundary *= 0.97
            PitchType.BALANCED -> {}
        }

        // The ground and the conditions (boundary size, dew in a night chase, altitude).
        fBoundary *= effects.boundaryFactor * effects.dewFactor
        fWicket *= effects.wicketCarryFactor

        // The chase.
        val target = state.target
        if (state.currentInnings == 2 && target != null) {
            val need = target - state.score.runs
            val ballsLeft = maxOf(1, limit * 6 - balls)
            val required = need * 6.0 / ballsLeft
            when {
                required > 12 -> { fBoundary *= 1.25; fWicket *= 1.25 }
                required > 9.5 -> { fBoundary *= 1.12; fWicket *= 1.1 }
                required < 6 -> { fBoundary *= 0.9; fWicket *= 0.85 }
            }
        }

        val wicketChance = if (freeHit) 0.0 else WICKET * fWicket
        val weights = doubleArrayOf(
            DOT * fDot, ONE * fRuns, TWO * fRuns, THREE * fRuns, FOUR * fBoundary, SIX * fBoundary, wicketChance
        )

        // Extras first: a wide, or a no-ball (which the batter still plays, and which makes the next a free hit).
        val extra = random.nextDouble()
        if (extra < WIDE) {
            return BallOutcome(
                runs = 0, isWicket = false, isWide = true, isNoBall = false, extraRuns = 1,
                batsmanId = striker.id, bowlerId = bowler.id, commentary = ""
            )
        }
        val noBall = extra < WIDE + NO_BALL
        val protectedBall = noBall || freeHit
        if (protectedBall) weights[6] = 0.0

        val total = weights.sum()
        var roll = random.nextDouble() * total
        var index = 0
        for (i in weights.indices) {
            roll -= weights[i]
            if (roll <= 0) { index = i; break }
            index = i
        }
        val runs = when (index) { 1 -> 1; 2 -> 2; 3 -> 3; 4 -> 4; 5 -> 6; else -> 0 }

        if (index == 6) return wicketBall(state, striker, bowler, spin, random)

        var isEdge: Boolean? = null
        if (index == 4) isEdge = random.nextDouble() < 0.3
        if (index == 5) isEdge = random.nextDouble() < 0.2

        // A run out: only while running (1 to 3), never on a no-ball or free hit, and not more than twice an innings.
        if (!protectedBall && runs in 1..3 && random.nextDouble() < RUN_OUT_CHANCE) {
            val runOutsSoFar = state.ballByBall.count { it.dismissalType == DismissalType.RUN_OUT }
            if (runOutsSoFar < MAX_RUN_OUTS) {
                val out = if (random.nextBoolean()) state.currentBatsmen.first else state.currentBatsmen.second
                val fielder = state.bowlingTeam.players.filter { it.id != bowler.id }.randomOrNull(random)?.name ?: bowler.name
                return BallOutcome(
                    runs = runs - 1, isWicket = true, isWide = false, isNoBall = false, extraRuns = 0,
                    batsmanId = striker.id, bowlerId = bowler.id, commentary = "",
                    dismissalType = DismissalType.RUN_OUT, isEdge = null,
                    outBatsmanId = out.id, fielderName = fielder
                )
            }
        }

        return BallOutcome(
            runs = runs, isWicket = false, isWide = false, isNoBall = noBall, extraRuns = if (noBall) 1 else 0,
            batsmanId = striker.id, bowlerId = bowler.id, commentary = "", isEdge = isEdge
        )
    }

    private fun pickDismissal(spin: Boolean, random: Random): DismissalType {
        val table = if (spin) {
            listOf(
                DismissalType.BOWLED to 22, DismissalType.LBW to 14, DismissalType.CAUGHT to 50,
                DismissalType.CAUGHT_AND_BOWLED to 6, DismissalType.STUMPED to 8
            )
        } else {
            listOf(
                DismissalType.BOWLED to 27, DismissalType.LBW to 13, DismissalType.CAUGHT to 54,
                DismissalType.CAUGHT_AND_BOWLED to 5, DismissalType.STUMPED to 1
            )
        }
        var roll = random.nextInt(table.sumOf { it.second })
        for ((type, weight) in table) {
            roll -= weight
            if (roll < 0) return type
        }
        return DismissalType.BOWLED
    }

    private fun wicketBall(state: MatchState, striker: Player, bowler: Player, spin: Boolean, random: Random): BallOutcome {
        val type = pickDismissal(spin, random)
        var isEdge: Boolean? = null
        var fielderName: String? = null
        var fielderPosition: String? = null
        if (type == DismissalType.CAUGHT) {
            isEdge = random.nextDouble() < 0.45
            val taken = catcher(state, bowler, isEdge, random)
            fielderName = taken?.first
            fielderPosition = taken?.second
        } else if (type == DismissalType.CAUGHT_AND_BOWLED) {
            fielderName = bowler.name
        }
        return BallOutcome(
            runs = 0, isWicket = true, isWide = false, isNoBall = false, extraRuns = 0,
            batsmanId = striker.id, bowlerId = bowler.id, commentary = "",
            dismissalType = type, isEdge = isEdge, fielderName = fielderName, fielderPosition = fielderPosition
        )
    }

    /**
     * Who takes a catch, and where. An edge goes behind: the keeper, or a slip or gully when the
     * field has one. A ball struck in the air goes to a fielder in the deep (else any fielder).
     */
    private fun catcher(state: MatchState, bowler: Player, isEdge: Boolean, random: Random): Pair<String, String>? {
        val team = state.bowlingTeam
        val keeperId = team.wicketkeeperId ?: team.players.find { it.role == PlayerRole.WICKETKEEPER }?.id
        val keeper = team.players.find { it.id == keeperId && it.id != bowler.id }
        val placed = state.fieldPlacements.filter { it.playerId != bowler.id && it.playerId != keeperId }
        fun taken(p: FieldPlacement): Pair<String, String>? =
            team.players.find { it.id == p.playerId }?.let { it.name to "at " + FieldingSystem.getPositionLabel(p).lowercase() }

        if (isEdge) {
            val cordon = placed.filter { it.sector == FieldingSector.SLIP_GULLY || it.sector == FieldingSector.LEG_SLIP }
            if (keeper != null && (cordon.isEmpty() || random.nextDouble() < 0.6)) return keeper.name to "behind the stumps"
            cordon.randomOrNull(random)?.let { p -> taken(p)?.let { return it } }
        } else {
            val deep = placed.filter { it.depth == FieldingDepth.DEEP }
            (if (deep.isNotEmpty()) deep else placed).randomOrNull(random)?.let { p -> taken(p)?.let { return it } }
        }
        return keeper?.let { it.name to "behind the stumps" }
    }
}

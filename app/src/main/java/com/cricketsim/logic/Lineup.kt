package com.cricketsim.logic

/**
 * Who bats where, and who the AI bowls when. One place for the cricket sense that used
 * to be missing: the AI picked its XI as "the eleven players best at ANYTHING" and sent
 * the first two in to open, so the best fast bowlers (Starc, Cummins, Mustafizur) opened
 * the batting.
 *
 * Every team's roster in CricketData is written with that side's usual XI first, in
 * batting order, and the reserves after. That order is the source of truth for "the
 * usual order"; the tiers below only repair what a roster order can't guarantee (a
 * reserve brought in, a user-chosen XI).
 */
object Lineup {

    /** 0 = specialist batsman or keeper, 1 = all-rounder, 2 = specialist bowler. */
    private fun tier(p: Player): Int = when (p.role) {
        PlayerRole.BATSMAN, PlayerRole.WICKETKEEPER -> 0
        PlayerRole.ALL_ROUNDER -> 1
        PlayerRole.BOWLER -> 2
    }

    /**
     * Puts an XI into its batting order: specialist batsmen and the keeper first (openers
     * and the middle order), then the all-rounders, then the specialist bowlers as the
     * tail, best batter first. Within the first two groups the order is the roster's own
     * (the side's usual order); `roster` is the full squad the XI was picked from.
     */
    fun battingOrder(xi: List<Player>, roster: List<Player>): List<Player> {
        val usual = HashMap<String, Int>()
        roster.forEachIndexed { index, player -> usual[player.id] = index }
        return xi.sortedWith(
            compareBy<Player> { tier(it) }
                .thenBy { if (tier(it) == 2) -it.battingRating else 0 }
                .thenBy { usual[it.id] ?: Int.MAX_VALUE }
        )
    }

    /**
     * The new-ball bowler: the best PACE bowler or all-rounder, the way a real side
     * opens. Falls back to the best bowling rating if there is no pace bowler at all.
     */
    fun openingBowler(team: Team): Player {
        val canBowl = team.players.filter { it.role == PlayerRole.BOWLER || it.role == PlayerRole.ALL_ROUNDER }
        val pace = canBowl.filter { it.bowlingStyle == BowlingStyle.PACE }
        return pace.ifEmpty { canBowl }.maxByOrNull { it.bowlingRating }
            ?: team.players.maxByOrNull { it.bowlingRating }
            ?: team.players.first()
    }

    /**
     * How much the AI favours or avoids a bowler right now, added to his rating when it
     * picks the next over's bowler: pace with the new ball and at the death (yorkers),
     * spin through the middle overs, and the pitch's own say (dusty turns, green seams).
     * A nudge, not a rule: a far better bowler of the "wrong" kind still gets the ball.
     */
    fun phaseBonus(state: MatchState, bowler: Player): Double {
        val pace = bowler.bowlingStyle == BowlingStyle.PACE
        var bonus = when (state.pitchType) {
            PitchType.DUSTY -> if (pace) -2.0 else 5.0
            PitchType.GREEN -> if (pace) 5.0 else -3.0
            PitchType.BOWLING -> if (pace) 2.0 else 0.0
            PitchType.BATTING, PitchType.BALANCED -> 0.0
        }
        if (state.format == MatchFormat.TEST || state.oversLimit <= 0) {
            // A Test: the new ball belongs to the quicks; after that, spin is a fair option.
            bonus += if (state.score.overs < 20) (if (pace) 4.0 else -3.0) else (if (pace) 0.0 else 2.0)
            return bonus
        }
        val progress = (state.score.overs + state.score.balls / 6.0) / state.oversLimit
        bonus += when {
            progress < 0.2 -> if (pace) 5.0 else -4.0 // new ball / powerplay
            progress < 0.75 -> if (pace) -1.0 else 4.0 // middle overs: spin holds the batters
            else -> if (pace) 6.0 else -5.0 // death overs: the yorker bowlers
        }
        return bonus
    }
}

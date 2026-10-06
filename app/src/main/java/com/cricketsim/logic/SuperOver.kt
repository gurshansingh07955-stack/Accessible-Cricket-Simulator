package com.cricketsim.logic

/** One finished Super Over round, for the result screen. */
data class SuperOverResult(
    val round: Int,
    val firstBattingTeamName: String,
    val firstRuns: Int,
    val firstWickets: Int,
    val secondBattingTeamName: String,
    val secondRuns: Int,
    val secondWickets: Int
)

/** The three batters (in batting order) and the one bowler a team has named for a Super Over. */
data class SuperOverNomination(val batterIds: List<String>, val bowlerId: String)

/**
 * The Super Over, with the real rules:
 *  - It decides a tied limited-overs match (not a Test, which can simply end level).
 *  - Each team faces ONE over, and its innings ends at the end of that over or when it has
 *    lost TWO wickets (it nominates three batters, so the second wicket leaves one standing).
 *  - Each team nominates three batters and one bowler. The bowler bowls the whole over.
 *  - The team that batted SECOND in the match bats first; the other side then chases.
 *  - Normal rules apply otherwise: wides and no-balls are re-bowled, free hits, fielding
 *    restrictions, reviews.
 *  - If the Super Over is tied as well, there is another one, with the batting order reversed,
 *    and players who were used in an earlier Super Over cannot be used again, for as many
 *    rounds as it takes.
 *
 * HOW IT IS BUILT: a Super Over innings reuses the ordinary innings machinery. It is a normal
 * "first innings" then "second innings" (currentInnings 1 and 2, with a target for the second),
 * with oversLimit = 1, and with the batting team swapped for a copy that holds only the three
 * nominated batters. That one trick is what ends the innings at two wickets: an innings is over
 * when wickets reach (batters in the team - 1), which is 10 for an XI and 2 for these three.
 * The finished match itself is kept in MatchState.regulationState so its scorecards survive.
 */
object SuperOver {

    const val BATTERS = 3

    /** True when an innings that has just ended leaves the scores level in a format that has a Super Over. */
    fun isTieAtEnd(state: MatchState): Boolean {
        if (state.format == MatchFormat.TEST) return false
        val target = state.target ?: return false
        return state.currentInnings == 2 && state.score.runs == target - 1
    }

    private fun fullTeam(state: MatchState, teamId: String): Team =
        if (state.userTeam.id == teamId) state.userTeam else state.opponentTeam

    /** Moves a level match (or a level Super Over) on to the next Super Over, which now waits for the nominations. */
    fun begin(state: MatchState): MatchState {
        val results = if (state.superOverRound > 0) state.superOverResults + resultOf(state) else state.superOverResults
        return state.copy(
            superOverRound = state.superOverRound + 1,
            superOverResults = results,
            regulationState = state.regulationState ?: state.copy(pendingDismissal = null, drinksBreak = null),
            // The side that batted second (the chasers) bats first in the next Super Over.
            superOverFirstBattingId = state.battingTeam.id,
            superOverNominating = true,
            pendingDismissal = null,
            drinksBreak = null,
            activeRainDelay = null,
            deferredOverEnd = false
        )
    }

    /** Batters the user can choose from: anyone not used in an earlier Super Over (or everyone if that leaves too few). */
    fun eligibleBatters(state: MatchState): List<Player> {
        val free = state.userTeam.players.filter { it.id !in state.superOverUsedIds }
        val pool = if (free.size >= BATTERS) free else state.userTeam.players
        return pool.sortedByDescending { it.battingRating }
    }

    /** Bowlers the user can choose from, best first. */
    fun eligibleBowlers(state: MatchState): List<Player> {
        val team = state.userTeam
        val bowlers = team.players.filter { it.role == PlayerRole.BOWLER || it.role == PlayerRole.ALL_ROUNDER }
        val free = bowlers.filter { it.id !in state.superOverUsedIds }
        val pool = when {
            free.isNotEmpty() -> free
            bowlers.isNotEmpty() -> bowlers
            else -> team.players.sortedByDescending { it.bowlingRating }.take(5)
        }
        return pool.sortedByDescending { it.bowlingRating }
    }

    /** The computer team's choice: its three best batters and its best bowler (not one of those batters if it can help it). */
    fun aiNomination(team: Team, used: List<String>): SuperOverNomination {
        val free = team.players.filter { it.id !in used }.ifEmpty { team.players }
        val bestBatters = free.sortedByDescending { it.battingRating }.take(BATTERS)
        val batters = if (bestBatters.size == BATTERS) bestBatters else team.players.sortedByDescending { it.battingRating }.take(BATTERS)
        val bowlerPool = free.filter { it.role == PlayerRole.BOWLER || it.role == PlayerRole.ALL_ROUNDER }.ifEmpty { free }
        val bowler = bowlerPool.filter { candidate -> batters.none { it.id == candidate.id } }.maxByOrNull { it.bowlingRating }
            ?: bowlerPool.maxByOrNull { it.bowlingRating }
            ?: team.players.first()
        return SuperOverNomination(batters.map { it.id }, bowler.id)
    }

    /** Takes the user's nominations, makes the computer's, and starts the first innings of this Super Over. */
    fun nominate(state: MatchState, userBatters: List<Player>, userBowler: Player): MatchState {
        val first = fullTeam(state, state.superOverFirstBattingId ?: state.battingTeam.id)
        val second = if (first.id == state.userTeam.id) state.opponentTeam else state.userTeam
        fun nominationFor(team: Team): SuperOverNomination =
            if (team.id == state.userTeam.id) {
                SuperOverNomination(userBatters.map { it.id }, userBowler.id)
            } else {
                aiNomination(team, state.superOverUsedIds)
            }
        val nominations = mapOf(first.id to nominationFor(first), second.id to nominationFor(second))
        val used = state.superOverUsedIds + nominations.values.flatMap { it.batterIds + it.bowlerId }
        val ready = state.copy(superOverNominations = nominations, superOverUsedIds = used, superOverNominating = false)
        return startInnings(ready, first, second, chase = false)
    }

    /** Called when the first innings of a Super Over ends: the other side now chases. */
    fun switchToChase(state: MatchState): MatchState {
        val chasing = fullTeam(state, state.bowlingTeam.id)
        val bowling = fullTeam(state, state.battingTeam.id)
        return startInnings(state, chasing, bowling, chase = true)
    }

    private fun startInnings(state: MatchState, batting: Team, bowling: Team, chase: Boolean): MatchState {
        val nomination = state.superOverNominations[batting.id]
        val named = nomination?.batterIds?.mapNotNull { id -> batting.players.firstOrNull { it.id == id } } ?: emptyList()
        val batters = if (named.size >= 2) named else batting.players.take(BATTERS)
        val battingSide = batting.copy(players = batters)
        val bowlerId = state.superOverNominations[bowling.id]?.bowlerId
        val bowler = bowling.players.firstOrNull { it.id == bowlerId } ?: Lineup.openingBowler(bowling)
        val striker = batters[0]
        val nonStriker = batters[1]
        val inningsData = MatchStats.createEmptyInningsData(battingSide, bowling, Pair(striker, nonStriker), bowler)
        val field = FieldingSystem.createDefaultFieldPlacements(FieldingSystem.getFieldingPlayers(bowling, bowler.id))
        return state.copy(
            currentInnings = if (chase) 2 else 1,
            battingTeam = battingSide,
            bowlingTeam = bowling,
            score = MatchScore(runs = 0, wickets = 0, overs = 0, balls = 0),
            target = if (chase) state.score.runs + 1 else null,
            currentBatsmen = Pair(striker, nonStriker),
            currentBowler = bowler,
            ballByBall = emptyList(),
            lastSixBalls = emptyList(),
            firstInningsScore = if (chase) state.score else null,
            firstInningsData = if (chase) state.currentInningsData else null,
            secondInningsData = null,
            currentInningsData = inningsData,
            needsOpenerSelection = false,
            needsBowlerSelection = false,
            pendingDismissal = null,
            deferredOverEnd = false,
            fieldPlacements = field,
            oversLimit = 1,
            // No rain, no DLS, no drinks break in a Super Over.
            rainInterruptionUsed = true,
            activeRainDelay = null,
            firstInningsInterruption = null,
            secondInningsInterruption = null,
            dlsRevised = false,
            drsReviewsUsed = 0,
            freeHit = false,
            lastAiFieldChangeBall = 0,
            lastDrinksBreakOver = 0,
            drinksBreak = null
        )
    }

    private fun resultOf(state: MatchState): SuperOverResult {
        val first = state.firstInningsScore ?: MatchScore(runs = 0, wickets = 0, overs = 0, balls = 0)
        return SuperOverResult(
            round = state.superOverRound,
            firstBattingTeamName = state.firstInningsData?.battingTeamName ?: state.bowlingTeam.name,
            firstRuns = first.runs,
            firstWickets = first.wickets,
            secondBattingTeamName = state.battingTeam.name,
            secondRuns = state.score.runs,
            secondWickets = state.score.wickets
        )
    }

    /** The finished match, with the Super Over's winner recorded; null if this is not a Super Over that has been decided. */
    fun finalise(state: MatchState): MatchState? {
        if (state.superOverRound == 0) return null
        val regulation = state.regulationState ?: return null
        val target = state.target ?: return null
        val chasersWon = state.score.runs >= target
        val winnerId = if (chasersWon) state.battingTeam.id else state.bowlingTeam.id
        return regulation.copy(
            matchStatus = MatchStatus.COMPLETED,
            winnerId = winnerId,
            superOverRound = state.superOverRound,
            superOverResults = state.superOverResults + resultOf(state),
            regulationState = null,
            pendingDismissal = null
        )
    }

    fun resultText(finished: MatchState): String? {
        if (finished.superOverResults.isEmpty()) return null
        val winner = if (finished.winnerId == finished.userTeam.id) finished.userTeam.name else finished.opponentTeam.name
        return if (finished.superOverRound <= 1) {
            "Match tied. $winner won the Super Over."
        } else {
            "Match tied. $winner won in Super Over number ${finished.superOverRound}."
        }
    }

    fun summaryLines(finished: MatchState): List<String> = finished.superOverResults.map {
        "Super Over ${it.round}: ${it.firstBattingTeamName} ${it.firstRuns} for ${it.firstWickets}, " +
            "${it.secondBattingTeamName} ${it.secondRuns} for ${it.secondWickets}."
    }

    fun introLines(state: MatchState): List<String> {
        val first = fullTeam(state, state.superOverFirstBattingId ?: state.battingTeam.id)
        return listOf(
            if (state.superOverRound <= 1) {
                "The match is tied, so a Super Over will decide it."
            } else {
                "Still level after Super Over ${state.superOverRound - 1}, so another Super Over will decide it."
            },
            "Each team faces one over, and its innings ends if it loses two wickets.",
            "${first.name} bat first. The other side then chases.",
            "Choose three batters in batting order, and one bowler. Players used in an earlier Super Over cannot be used again."
        )
    }

    fun tieAnnouncement(state: MatchState): String =
        if (state.superOverRound <= 1) "The match is tied! We are going to a Super Over."
        else "Still level! Super Over number ${state.superOverRound}."

    fun startAnnouncement(state: MatchState): String =
        "Super Over. ${state.battingTeam.name} bat first, facing ${state.currentBowler.name}. One over, two wickets."
}

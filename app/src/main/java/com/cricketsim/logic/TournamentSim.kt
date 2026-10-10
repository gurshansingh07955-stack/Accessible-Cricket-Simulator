package com.cricketsim.logic

import com.cricketsim.ui.match.MatchSimulation
import kotlin.random.Random

/** How much of the tournament to simulate in one go. */
enum class SimMode { NEXT, UNTIL_MINE, ALL }

/**
 * Plays the tournament's matches without anyone watching: picks each side's XI for the ground,
 * does the toss, then plays the match ball by ball through the game's own match code (scorecard,
 * strike rotation, bowling changes, fields, rain and DLS, Super Overs). Each ball's result comes
 * from AiBallModel, which depends on the real context of the ball and not on dice alone:
 *
 *  - the STADIUM: its pitch type, boundary size, dew and altitude (WeatherSystem's stadium
 *    effects), and the weather rolled for that ground, including rain and DLS;
 *  - the PLAYERS: batter against bowler ratings, how set the batter is, the phase of the
 *    innings, and the pressure of a chase;
 *  - the PLAN: each captain picks his XI for the pitch (an extra spinner on a dusty pitch, a
 *    seamer on a green one) and chooses to bat or bowl from the pitch and the dew.
 * The only dice are the coin toss and the ball-by-ball cricket itself. A tied match goes to a Super
 * Over with the game's real Super Over rules (see SuperOver), repeated until one side wins.
 *
 * WHY NOT THE LIVE ENGINE'S DICE: MatchEngine.simulateBall is built for a person on one side; with
 * two AI sides it scored 230-260 and made bowlers wicketless. See AiBallModel for how the
 * replacement was calibrated.
 *
 * Runs on a background thread: a whole tournament is some eight thousand balls.
 */
object CplSimulator {

    private const val OBSERVER_ID = "cpl_observer"
    private const val MAX_DELIVERIES = 2500
    private const val MAX_OVERSEAS = 4

    class SimResult(val result: FixtureResult, val scorecard: MatchState, val home: Team, val away: Team, val finished: MatchState)

    // ---- Picking the XI ----------------------------------------------------------------------

    /**
     * The XI for a match at this ground, in batting order. The CPL rules: at most four overseas
     * players, and at least one breakout player. Beyond that: a wicketkeeper, at least five
     * bowling options, and the best players for the PITCH (spinners earn a bonus on a dusty pitch,
     * seamers on a green or bowling one, batters on a flat one).
     */
    fun pickXI(team: Team, tags: Map<String, SquadTag>, stadium: Stadium?): Team {
        val pitch = stadium?.pitchType ?: PitchType.BALANCED
        fun bowlingOption(p: Player) = p.role == PlayerRole.BOWLER || p.role == PlayerRole.ALL_ROUNDER
        fun overseas(p: Player) = tags[p.id] == SquadTag.OVERSEAS
        fun breakout(p: Player) = tags[p.id] == SquadTag.BREAKOUT
        fun value(p: Player): Double {
            var v = maxOf(p.battingRating, p.bowlingRating) + p.fieldingRating * 0.1
            if (bowlingOption(p)) {
                val spin = p.bowlingStyle == BowlingStyle.SPIN
                v += when (pitch) {
                    PitchType.DUSTY -> if (spin) 6.0 else -2.0
                    PitchType.GREEN, PitchType.BOWLING -> if (spin) -2.0 else 5.0
                    else -> 0.0
                }
            } else if (pitch == PitchType.BATTING) {
                v += 2.0
            }
            return v
        }

        val pool = team.players.sortedByDescending { value(it) }
        val xi = ArrayList<Player>()
        pool.firstOrNull { it.role == PlayerRole.WICKETKEEPER }?.let { xi.add(it) }
        pool.firstOrNull { breakout(it) && it !in xi }?.let { xi.add(it) }
        for (p in pool) {
            if (xi.size >= 11) break
            if (p in xi) continue
            if (overseas(p) && xi.count { overseas(it) } >= MAX_OVERSEAS) continue
            xi.add(p)
        }
        // At least five bowling options: a spare bowler replaces the weakest non-bowling batter.
        while (xi.count { bowlingOption(it) } < 5) {
            val spare = pool.firstOrNull {
                bowlingOption(it) && it !in xi && !(overseas(it) && xi.count { x -> overseas(x) } >= MAX_OVERSEAS)
            } ?: break
            val drop = xi.filter {
                !bowlingOption(it) && it.role != PlayerRole.WICKETKEEPER && !(breakout(it) && xi.count { x -> breakout(x) } == 1)
            }.minByOrNull { value(it) } ?: break
            xi[xi.indexOf(drop)] = spare
        }

        val ordered = Lineup.battingOrder(xi, team.players)
        val keeper = ordered.firstOrNull { it.role == PlayerRole.WICKETKEEPER } ?: ordered.first()
        val byValue = ordered.sortedByDescending { value(it) }
        val captain = byValue.first()
        val vice = byValue.firstOrNull { it.id != captain.id } ?: captain
        return CricketData.buildMatchSquad(team, ordered.map { it.id }, captain.id, vice.id, keeper.id)
    }

    /**
     * Bat or bowl, from the ground and the conditions: heavy dew under lights makes the chase
     * easier, a dusty pitch wears and turns (bat first), a green or bowling pitch helps the
     * seamers early (bowl first); otherwise a big-scoring ground favours setting a total.
     */
    private fun tossDecision(stadium: Stadium, weather: WeatherSnapshot): TossDecision = when {
        stadium.dewFactor >= 0.5 && weather.isDayNight -> TossDecision.BOWL
        stadium.pitchType == PitchType.DUSTY -> TossDecision.BAT
        stadium.pitchType == PitchType.GREEN || stadium.pitchType == PitchType.BOWLING -> TossDecision.BOWL
        stadium.avgT20FirstInningsScore >= 170 -> TossDecision.BAT
        else -> TossDecision.BOWL
    }

    // ---- One match ---------------------------------------------------------------------------

    fun simulateMatch(
        homeSquad: Team,
        awaySquad: Team,
        tags: Map<String, SquadTag>,
        stadium: Stadium,
        random: Random
    ): SimResult? {
        val home = pickXI(homeSquad, tags, stadium)
        val away = pickXI(awaySquad, tags, stadium)
        val weather = WeatherSystem.generateWeatherForStadium(stadium)
        val tossWinner = if (random.nextBoolean()) home else away
        val decision = tossDecision(stadium, weather)

        var s = MatchStateMachine.createNewMatch(
            MatchFormat.T20, stadium.pitchType, home, away, tossWinner.id, decision, stadium.id, weather
        )
        // Neither side is "the user": the engine then treats both as AI-controlled everywhere
        // (next batter, bowling changes, fields), and its difficulty setting favours nobody.
        s = s.copy(
            userTeam = home.copy(id = OBSERVER_ID),
            needsOpenerSelection = false,
            needsBowlerSelection = false,
            fieldPlacements = FieldingSystem.generateAiFieldPlacements(
                FieldingSystem.getFieldingPlayers(s.bowlingTeam, s.currentBowler.id),
                FieldingSystem.isPowerplayOver(s.format, 0)
            )
        )

        s = playInnings(s, stadium, random)
        if (s.currentInnings != 2) return null

        // The regulation result; a tie goes to a Super Over (and more, until one side wins), played
        // on the same engine, ground and weather as the match itself.
        var finished = MatchSimulation.finishMatch(s)
        if (SuperOver.isTieAtEnd(s)) {
            playSuperOvers(s, home, away, stadium, random)?.let { finished = it }
        }
        val result = buildResult(finished, home, away) ?: return null
        // The scorecard needs only the two innings; the ball-by-ball history would only bloat the save.
        val slim = finished.copy(
            ballByBall = emptyList(), lastSixBalls = emptyList(), fieldPlacements = emptyList(),
            regulationState = null, drinksBreak = null, activeRainDelay = null
        )
        return SimResult(result, slim, home, away, finished)
    }

    /** The fixture result (scores, winner, summary line) for a finished match, whoever played it. */
    private fun buildResult(finished: MatchState, home: Team, away: Team): FixtureResult? {
        val first = finished.firstInningsData ?: return null
        val second = finished.currentInningsData
        val firstScore = finished.firstInningsScore ?: return null

        val tied = finished.superOverResults.isNotEmpty()
        // Only if no Super Over could settle it (should never happen): fewer wickets lost, then batting strength.
        val winnerId = finished.winnerId ?: when {
            second.totalWickets < first.totalWickets -> second.battingTeamId
            second.totalWickets > first.totalWickets -> first.battingTeamId
            else -> listOf(home, away).maxByOrNull { it.battingRating }?.id ?: home.id
        }
        val winnerName = listOf(home, away).first { it.id == winnerId }.name
        val resultText = when {
            tied -> (SuperOver.resultText(finished) ?: "Match tied. $winnerName won.") + " " +
                SuperOver.summaryLines(finished).joinToString(" ")
            else -> (MatchSimulation.matchResultText(finished) ?: "$winnerName won.") +
                // After rain the margin is measured against the revised (DLS) target, not the first-innings score.
                (if (finished.dlsRevised && finished.target != null) " Target revised to ${finished.target} by DLS." else "")
        }
        val summary = "${CplData.shortName(first.battingTeamId)} ${firstScore.runs}/${firstScore.wickets}, " +
            "${CplData.shortName(second.battingTeamId)} ${finished.score.runs}/${finished.score.wickets}. $resultText"

        val award = PlayerAwards.playerOfTheMatch(finished)
        val result = FixtureResult(
            winnerId = winnerId,
            summary = summary,
            firstBattingId = first.battingTeamId,
            firstRuns = firstScore.runs,
            firstWickets = firstScore.wickets,
            firstBalls = firstScore.overs * 6 + firstScore.balls,
            secondRuns = finished.score.runs,
            secondWickets = finished.score.wickets,
            secondBalls = finished.score.overs * 6 + finished.score.balls,
            playerOfMatchId = award?.playerId,
            playerOfMatch = award?.let { "${it.name} (${CplData.shortName(it.teamId)}), ${it.summary}" }
        )
        return result
    }

    /**
     * Plays an innings, then (for a first innings) the chase, ball by ball, until the match or
     * Super Over is over. Both sides are AI-controlled here, so a batter who is out is replaced
     * at once, rain delays and drinks breaks are skipped, and the loop cannot run forever.
     */
    private fun playInnings(start: MatchState, stadium: Stadium, random: Random): MatchState {
        var s = start
        var deliveries = 0
        var loops = 0
        while (deliveries < MAX_DELIVERIES && loops < MAX_DELIVERIES * 3) {
            loops++
            if (s.drinksBreak != null) s = s.copy(drinksBreak = null)
            if (s.activeRainDelay != null) s = MatchStateMachine.resumeFromRainDelay(s)
            if (s.pendingDismissal != null) {
                // Only happens in a Super Over, where the home side is flagged as "the user's".
                val next = MatchStateMachine.getAvailableBatsmen(s).firstOrNull()
                s = if (next != null) MatchSimulation.completeWicketReplacement(s, next, stadium) else s.copy(pendingDismissal = null)
                continue
            }
            if (MatchSimulation.isInningsOver(s) || MatchSimulation.isTargetReached(s)) {
                if (s.currentInnings == 1) {
                    s = MatchStateMachine.switchInnings(s)
                    continue
                }
                break
            }
            val delivery = MatchSimulation.prepareAiDelivery(s)
            // The ball itself comes from AiBallModel (ratings, pitch, ground, phase, chase); the match
            // code then applies it exactly as it applies any ball.
            val outcome = AiBallModel.sample(delivery.state, stadium, random)
            s = MatchSimulation.simulateOneBall(
                state = delivery.state,
                stadium = stadium,
                difficulty = Difficulty.MEDIUM,
                presetBowlingDecision = delivery.bowling,
                outcomeOverride = outcome
            ).state
            deliveries++
        }
        return s
    }

    /**
     * The Super Over(s) after a tied match, with the real rules from SuperOver: one over each, two
     * wickets, the chasers bat first, and another Super Over if that is level too. Each side
     * nominates its best three batters and best bowler. Returns the finished match with the
     * winner recorded, or null if it could not be played.
     */
    private fun playSuperOvers(regulationEnd: MatchState, home: Team, away: Team, stadium: Stadium, random: Random): MatchState? {
        // The Super Over machinery looks the full squads up through userTeam / opponentTeam.
        var s = regulationEnd.copy(userTeam = home, opponentTeam = away)
        var rounds = 0
        while (rounds < 8) {
            rounds++
            s = SuperOver.begin(s)
            val nomination = SuperOver.aiNomination(home, s.superOverUsedIds)
            val batters = nomination.batterIds.mapNotNull { id -> home.players.firstOrNull { it.id == id } }
            val bowler = home.players.firstOrNull { it.id == nomination.bowlerId } ?: home.players.first()
            s = SuperOver.nominate(s, batters, bowler)
            s = playInnings(s, stadium, random)
            if (s.currentInnings != 2 || !SuperOver.isTieAtEnd(s)) break
        }
        return SuperOver.finalise(s)
    }

    // ---- Stats -------------------------------------------------------------------------------

    fun applyStats(prev: Map<String, PlayerStats>, finished: MatchState, home: Team, away: Team): Map<String, PlayerStats> {
        val all = home.players + away.players
        val byId = all.associateBy { it.id }
        val teamOf = HashMap<String, String>()
        home.players.forEach { teamOf[it.id] = home.id }
        away.players.forEach { teamOf[it.id] = away.id }
        val m = HashMap(prev)
        fun get(id: String): PlayerStats =
            m[id] ?: PlayerStats(playerId = id, teamId = teamOf[id] ?: "", name = byId[id]?.name ?: "")

        all.forEach { p -> m[p.id] = get(p.id).let { it.copy(matches = it.matches + 1) } }

        for (data in listOfNotNull(finished.firstInningsData, finished.currentInningsData)) {
            val fielders = if (data.bowlingTeamId == home.id) home.players else away.players
            for (b in data.batsmanStats) {
                val cur = get(b.playerId)
                val notOut = !b.isOut
                val newHigh = b.runs > cur.highScore || (b.runs == cur.highScore && notOut && !cur.highScoreNotOut)
                m[b.playerId] = cur.copy(
                    innings = cur.innings + 1,
                    notOuts = cur.notOuts + (if (notOut) 1 else 0),
                    runs = cur.runs + b.runs,
                    balls = cur.balls + b.ballsFaced,
                    fours = cur.fours + b.fours,
                    sixes = cur.sixes + b.sixes,
                    highScore = if (newHigh) b.runs else cur.highScore,
                    highScoreNotOut = if (newHigh) notOut else cur.highScoreNotOut,
                    fifties = cur.fifties + (if (b.runs in 50..99) 1 else 0),
                    hundreds = cur.hundreds + (if (b.runs >= 100) 1 else 0)
                )
                if (b.isOut) {
                    val catcherName = when (b.dismissalType) {
                        DismissalType.CAUGHT -> b.caughtBy
                        DismissalType.CAUGHT_AND_BOWLED -> b.dismissedBy
                        else -> null
                    }
                    val catcher = fielders.firstOrNull { it.name == catcherName }
                    if (catcher != null) {
                        m[catcher.id] = get(catcher.id).let { it.copy(catches = it.catches + 1) }
                    }
                }
            }
            for (w in data.bowlerStats) {
                val cur = get(w.playerId)
                val better = w.wickets > cur.bestWickets || (w.wickets == cur.bestWickets && w.wickets > 0 && w.runsConceded < cur.bestRuns)
                m[w.playerId] = cur.copy(
                    ballsBowled = cur.ballsBowled + w.overs * 6 + w.balls,
                    runsConceded = cur.runsConceded + w.runsConceded,
                    wickets = cur.wickets + w.wickets,
                    fiveWickets = cur.fiveWickets + (if (w.wickets >= 5) 1 else 0),
                    bestWickets = if (better) w.wickets else cur.bestWickets,
                    bestRuns = if (better) w.runsConceded else cur.bestRuns
                )
            }
        }
        // Impact points (for Player of the Series) and Player of the Match awards.
        PlayerAwards.impacts(finished).forEachIndexed { index, a ->
            val cur = get(a.playerId)
            m[a.playerId] = cur.copy(impact = cur.impact + a.points, awards = cur.awards + (if (index == 0) 1 else 0))
        }
        return m
    }

    // ---- The tournament ----------------------------------------------------------------------

    /**
     * The squad as the Playing XI screen should show it: the XI this ground and these rules
     * suggest FIRST (so "Auto-pick" gives a legal, sensible XI), then everyone else.
     */
    fun squadForSelection(team: Team, tags: Map<String, SquadTag>, stadium: Stadium?): Team {
        val xi = pickXI(team, tags, stadium).players
        val ids = xi.map { it.id }.toSet()
        val rest = team.players.filter { it.id !in ids }
        return team.copy(players = xi + rest)
    }

    /** Why this XI breaks the CPL rules (at most four overseas players, at least one breakout player), or null if it does not. */
    fun xiProblem(xi: Team, tags: Map<String, SquadTag>): String? {
        val overseas = xi.players.count { tags[it.id] == SquadTag.OVERSEAS }
        if (overseas > MAX_OVERSEAS) {
            return "Your XI has $overseas overseas players. The CPL allows at most $MAX_OVERSEAS. Please change your team."
        }
        if (xi.players.none { tags[it.id] == SquadTag.BREAKOUT }) {
            return "Your XI needs at least one breakout player. Please change your team."
        }
        return null
    }

    /**
     * Records a match the user played on the live match screen: result, scorecard, stats, and the
     * play-offs as they become known. Does nothing if that fixture already has a result.
     */
    fun recordPlayedMatch(state: TournamentState, fixtureId: Int, finished: MatchState, userXI: Team, opponentXI: Team): TournamentState {
        val fixture = state.fixtures.find { it.id == fixtureId } ?: return state
        if (fixture.result != null) return state
        val home = if (fixture.homeId == userXI.id) userXI else opponentXI
        val away = if (fixture.homeId == userXI.id) opponentXI else userXI
        val result = buildResult(finished, home, away) ?: return state
        val slim = finished.copy(
            ballByBall = emptyList(), lastSixBalls = emptyList(), fieldPlacements = emptyList(),
            regulationState = null, drinksBreak = null, activeRainDelay = null, pendingDismissal = null
        )
        val fixtures = state.fixtures.map { if (it.id == fixtureId) it.copy(result = result, scorecard = slim) else it }
        val updated = state.copy(
            fixtures = fixtures,
            playerStats = applyStats(state.playerStats, finished, home, away)
        )
        return advancePlayoffs(updated)
    }

    /** The next match to be played: the first one without a result whose teams are known. */
    fun nextFixture(state: TournamentState): TournamentFixture? =
        state.fixtures.firstOrNull { it.result == null && it.homeId != null && it.awayId != null }

    /** Winner of the Final, once it has been played. */
    fun champion(state: TournamentState): Team? {
        val finalMatch = state.fixtures.firstOrNull { it.stage == "Final" } ?: return null
        val id = finalMatch.result?.winnerId ?: return null
        return state.teams.find { it.id == id }
    }

    /** Player of the Series: the most impact over the whole tournament; only once the Final is played. */
    fun playerOfTheSeries(state: TournamentState): PlayerStats? {
        if (champion(state) == null) return null
        val best = state.playerStats.values.maxWithOrNull(
            compareBy<PlayerStats>({ it.impact }, { it.runs + it.wickets * 20 })
        ) ?: return null
        return if (best.impact > 0.0) best else null
    }

    /** The Final's Player of the Match, then the Player of the Series, as one sentence pair. */
    fun seriesAwardsText(state: TournamentState): String? {
        val finalMatch = state.fixtures.firstOrNull { it.stage == "Final" }
        val finalAward = finalMatch?.result?.playerOfMatch
        val series = playerOfTheSeries(state)
        val parts = ArrayList<String>()
        if (!finalAward.isNullOrEmpty()) parts.add("Player of the match in the Final: $finalAward.")
        if (series != null) {
            val team = CplData.shortName(series.teamId)
            parts.add("Player of the series: ${series.name}, $team, with ${series.runs} runs and ${series.wickets} wickets.")
        }
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }

    /** The fixture a saved match belongs to: the earliest unplayed one between these two teams. */
    fun fixtureFor(state: TournamentState, userId: String, opponentId: String): Int? =
        state.fixtures.firstOrNull {
            it.result == null &&
                ((it.homeId == userId && it.awayId == opponentId) || (it.homeId == opponentId && it.awayId == userId))
        }?.id

    fun involves(f: TournamentFixture, teamId: String) = f.homeId == teamId || f.awayId == teamId

    private fun winnerOf(f: TournamentFixture?): String? = f?.result?.winnerId

    private fun loserOf(f: TournamentFixture?): String? {
        val fx = f ?: return null
        val w = winnerOf(fx) ?: return null
        return if (w == fx.homeId) fx.awayId else fx.homeId
    }

    /** Fills in the play-offs as their teams become known. Safe to call after every match. */
    fun advancePlayoffs(state: TournamentState): TournamentState {
        val league = state.fixtures.filter { it.stage == CplData.LEAGUE }
        if (league.isEmpty() || league.any { it.result == null }) return state
        val table = CplData.standings(state)
        if (table.size < 4) return state
        var fixtures = state.fixtures
        fun stage(name: String) = fixtures.firstOrNull { it.stage == name }
        fun setTeams(name: String, home: String?, away: String?) {
            if (home == null || away == null) return
            fixtures = fixtures.map { if (it.stage == name && it.homeId == null) it.copy(homeId = home, awayId = away) else it }
        }
        setTeams("Qualifier 1", table[0].team.id, table[1].team.id)
        setTeams("Eliminator", table[2].team.id, table[3].team.id)
        val q1 = stage("Qualifier 1")
        val eliminator = stage("Eliminator")
        if (q1?.result != null && eliminator?.result != null) setTeams("Qualifier 2", loserOf(q1), winnerOf(eliminator))
        val q2 = stage("Qualifier 2")
        if (q1?.result != null && q2?.result != null) setTeams("Final", winnerOf(q1), winnerOf(q2))
        return state.copy(fixtures = fixtures)
    }

    /** Simulates one fixture and records its result, scorecard and the players' stats. */
    fun simulateFixture(state: TournamentState, fixture: TournamentFixture, random: Random): TournamentState {
        val home = state.teams.find { it.id == fixture.homeId } ?: return state
        val away = state.teams.find { it.id == fixture.awayId } ?: return state
        val stadium = CplData.stadium(fixture.stadiumId) ?: return state
        val sim = simulateMatch(home, away, state.tags, stadium, random) ?: return state
        val fixtures = state.fixtures.map {
            if (it.id == fixture.id) it.copy(result = sim.result, scorecard = sim.scorecard) else it
        }
        val updated = state.copy(
            fixtures = fixtures,
            playerStats = applyStats(state.playerStats, sim.finished, sim.home, sim.away)
        )
        return advancePlayoffs(updated)
    }

    fun run(state: TournamentState, mode: SimMode, random: Random): TournamentState {
        var s = state
        var guard = 0
        while (guard < 60) {
            guard++
            val next = nextFixture(s) ?: break
            if (mode == SimMode.UNTIL_MINE && involves(next, s.userTeamId)) break
            val after = simulateFixture(s, next, random)
            if (after === s) break // could not be played; do not loop forever
            s = after
            if (mode == SimMode.NEXT) break
        }
        return s
    }
}

package com.cricketsim.logic

import kotlin.random.Random

/**
 * NET PRACTICE (Home > Net practice, patch 135). Practice away from a match: one bowler, one or two batsmen, ball after ball, and a
 * coach who explains every ball. This file holds everything that is not screen: who can be picked, the little match state each
 * ball is resolved in, the deliveries, and the coach's words.
 *
 * It does NOT have a match loop of its own. A ball is played by the real BattingScreen and resolved by the real MatchEngine.simulateBall,
 * so practice feels exactly like a match ball, at the difficulty chosen in Settings. The state handed to the engine never advances (no
 * score, no innings): a wicket costs nothing, the batter simply carries on.
 *
 * Part 1: batting practice. Part 2 (bowling practice) will use the same lists.
 */
object NetPractice {
    /** Switch for the whole feature: false greys the Home button out. */
    const val NET_PRACTICE_ENABLED = true

    /** Only players rated this high, or higher, can be picked (batsmen and fast bowlers). */
    const val MIN_RATING = 90

    const val BALLS_PER_OVER = 6

    /** The five speeds of fast bowling practice. The last one runs to the fastest ball the game bowls. */
    enum class SpeedBand(val label: String, val low: Int, val high: Int) {
        BAND_110("110 to 120 kilometres an hour", 110, 120),
        BAND_120("120 to 130 kilometres an hour", 120, 130),
        BAND_130("130 to 140 kilometres an hour", 130, 140),
        BAND_140("140 to 150 kilometres an hour", 140, 150),
        BAND_150("150 kilometres an hour and above", 150, 159)
    }

    /** What one batter has done in a practice session. */
    data class Tally(
        val name: String,
        val balls: Int = 0,
        val runs: Int = 0,
        val outs: Int = 0,
        val fours: Int = 0,
        val sixes: Int = 0,
        val shots: Int = 0,
        val perfect: Int = 0,
        val sensible: Int = 0,
        val early: Int = 0,
        val late: Int = 0
    )

    private val SPINNER_NAMES = listOf("Kuldeep Yadav", "Ravindra Jadeja", "Rashid Khan", "Sikandar Raza", "Nathan Lyon", "Adam Zampa")

    /** Everyone in the game's national squads, once each (by name), with the player's own edits applied. */
    private fun roster(): List<Player> {
        val seen = HashSet<String>()
        return CricketData.getAllTeams().flatMap { it.players }.filter { seen.add(it.name) }
    }

    /** The batsmen who can be picked: rated 90 or more. (If edits left fewer than four, the eight best.) */
    fun batters(): List<Player> {
        val all = roster()
        val elite = all.filter { it.battingRating >= MIN_RATING }.sortedByDescending { it.battingRating }
        return if (elite.size >= 4) elite else all.sortedByDescending { it.battingRating }.take(8)
    }

    /** The fast bowlers rated 90 or more. (If edits left fewer than three, the six best.) */
    fun fastBowlers(): List<Player> {
        val pace = roster().filter { it.bowlingStyle == BowlingStyle.PACE }
        val elite = pace.filter { it.bowlingRating >= MIN_RATING }.sortedByDescending { it.bowlingRating }
        return if (elite.size >= 3) elite else pace.sortedByDescending { it.bowlingRating }.take(6)
    }

    /** The spinners of net practice, by name. */
    fun spinBowlers(): List<Player> {
        val all = roster()
        val named = SPINNER_NAMES.mapNotNull { name -> all.firstOrNull { it.name == name } }
        return if (named.size >= 2) named else all.filter { it.bowlingStyle == BowlingStyle.SPIN }.sortedByDescending { it.bowlingRating }.take(6)
    }

    /** A bowler at random from the fast or the spin list, never the one who has just bowled. */
    fun nextBowler(fast: Boolean, previous: Player?): Player {
        val pool = if (fast) fastBowlers() else spinBowlers()
        val choices = pool.filter { it.id != previous?.id }.ifEmpty { pool }
        return choices[Random.nextInt(choices.size)]
    }

    /** The ground the net is "at": a balanced pitch. Only its id and size matter (the engine gets no ground effects). */
    fun netStadium(): Stadium {
        val all = StadiumData.getAllStadiums()
        return all.firstOrNull { it.pitchType == PitchType.BALANCED } ?: all.first()
    }

    /** The bowler's own national squad (so there are eleven fielders), and the bowler as found in it. */
    private fun bowlingSideFor(bowler: Player): Pair<Team, Player> {
        val teams = CricketData.getAllTeams()
        val team = teams.firstOrNull { t -> t.players.any { it.id == bowler.id } }
            ?: teams.first { it.id == bowler.teamId }
        val inSide = team.players.firstOrNull { it.id == bowler.id } ?: team.players.first { it.name == bowler.name }
        return Pair(team, inSide)
    }

    /**
     * The match state each practice ball is resolved in: a T20 innings at ball zero, the striker facing the chosen bowler against
     * his own side's field. It is built once per batter-and-bowler pairing and never advanced.
     */
    fun buildNetState(striker: Player, partner: Player, bowler: Player): MatchState {
        val stadium = netStadium()
        val sides = bowlingSideFor(bowler)
        val battingSide = Team(
            id = "net_batters",
            name = "Net batters",
            countryCode = "NET",
            battingRating = 90,
            bowlingRating = 40,
            fieldingRating = 70,
            players = listOf(striker, partner)
        )
        val weather = WeatherSnapshot(WeatherCondition.CLEAR, 28, 45, 8, 0, false)
        var state = MatchStateMachine.createNewMatch(
            format = MatchFormat.T20,
            pitchType = PitchType.BALANCED,
            userTeam = battingSide,
            opponentTeam = sides.first,
            tossWinnerId = battingSide.id,
            tossDecision = TossDecision.BAT,
            stadiumId = stadium.id,
            weather = weather
        )
        state = MatchStateMachine.changeBowlerBeforeFirstBall(state, sides.second)
        return MatchStateMachine.setOpeners(state, striker, partner)
    }

    /**
     * The next ball. Spin: the computer bowler's own delivery. Fast: his delivery too, but a stock ball (a slower ball would
     * not fit a fast band) at a speed inside the chosen band.
     */
    fun deliveryFor(fast: Boolean, band: SpeedBand?, bowler: Player): ResolvedBowlingDecision {
        // School cricket caps pace for a match; practice is never capped.
        BowlingSystem.paceCapKmh = null
        var decision = BowlingSystem.generateAiBowlingDecision(bowler, 0.0)
        if (!fast || band == null) return decision
        var tries = 0
        while (decision.variation != BowlingVariation.STOCK && tries < 15) {
            decision = BowlingSystem.generateAiBowlingDecision(bowler, 0.0)
            tries++
        }
        val low = maxOf(band.low, BowlingSystem.PACE_STOCK_SPEED_RANGE.min)
        val high = minOf(band.high, BowlingSystem.PACE_STOCK_SPEED_RANGE.max)
        return decision.copy(speedKmh = Random.nextInt(low, high + 1))
    }

    /** The shots (never Leave) that are an excellent fit for this ball, in the order of the shot list. */
    fun shotsThatSuit(delivery: ResolvedBowlingDecision): List<NamedShot> =
        BattingSystem.SHOT_OPTIONS.map { it.value }.filter {
            it != NamedShot.LEAVE &&
                BattingSystem.computeShotCompatibility(it, delivery.actualLength, delivery.bowlingStyle) == ShotCompatibilityTier.EXCELLENT
        }

    /** What the coach says as the ball is revealed (spoken after the delivery). */
    fun coachTip(delivery: ResolvedBowlingDecision): String {
        val foot = when (BattingSystem.computeFootworkMatch(FootworkType.FRONT_FOOT, delivery.actualLength)) {
            FootworkMatchTier.MATCHED -> "the front foot"
            FootworkMatchTier.MISMATCHED -> "the back foot"
            FootworkMatchTier.NEUTRAL -> "either foot"
        }
        val suits = shotsThatSuit(delivery).take(4).map { BattingSystem.shotLabel(it) }
        return if (suits.isEmpty()) {
            "Coach tip: this ball suits $foot. No shot is a perfect fit, so play safe."
        } else {
            "Coach tip: this ball suits $foot. Shots that suit it: ${suits.joinToString(", ")}."
        }
    }

    /**
     * What the coach says after the ball, beyond the match's own ball summary (which already gives the result, the ball quality, the
     * shot and the timing): footwork, shot choice and how to fix the timing.
     */
    fun feedback(delivery: ResolvedBowlingDecision, decision: BattingDecision, swingNote: String?): List<String> {
        if (decision.shot == NamedShot.LEAVE) {
            return listOf("Coach: you left that ball alone. A ball that is on the stumps can still get you out if you leave it.")
        }
        val lines = ArrayList<String>()
        val lengthName = BowlingSystem.lengthLabel(delivery.actualLength).lowercase()
        val front = decision.footwork == FootworkType.FRONT_FOOT
        val foot = if (front) "front foot" else "back foot"
        val otherFoot = if (front) "back foot" else "front foot"
        when (BattingSystem.computeFootworkMatch(decision.footwork, delivery.actualLength)) {
            FootworkMatchTier.MATCHED -> lines.add("Coach, footwork: your $foot suited a $lengthName.")
            FootworkMatchTier.NEUTRAL -> lines.add("Coach, footwork: either foot is fine for a $lengthName.")
            FootworkMatchTier.MISMATCHED -> lines.add("Coach, footwork: the $foot did not suit a $lengthName. Use the $otherFoot for this length.")
        }
        val shotName = BattingSystem.shotLabel(decision.shot)
        val better = shotsThatSuit(delivery).filter { it != decision.shot }.take(3).map { BattingSystem.shotLabel(it) }
        val betterText = if (better.isEmpty()) "" else " Better choices: ${better.joinToString(", ")}."
        when (BattingSystem.computeShotCompatibility(decision.shot, delivery.actualLength, delivery.bowlingStyle)) {
            ShotCompatibilityTier.EXCELLENT -> lines.add("Coach, shot choice: the $shotName was an excellent choice for that ball.")
            ShotCompatibilityTier.GOOD -> lines.add("Coach, shot choice: the $shotName was an acceptable choice.$betterText")
            ShotCompatibilityTier.BAD -> lines.add("Coach, shot choice: the $shotName did not suit that ball.$betterText")
        }
        if (decision.timingTier != BowlingQualityTier.PERFECT) {
            val note = swingNote.orEmpty()
            lines.add(
                when {
                    note.contains("early") -> "Coach, timing: you tapped too soon. Wait for the fifth, highest buzz."
                    note.contains("late") -> "Coach, timing: you tapped too late. Tap a touch sooner, right on the fifth buzz."
                    note.contains("in time") -> "Coach, timing: you did not tap in time. Tap on the fifth buzz, the highest and loudest one."
                    else -> "Coach, timing: aim to tap exactly on the fifth buzz."
                }
            )
        }
        return lines
    }

    private fun plural(n: Int, one: String, many: String): String = if (n == 1) "$n $one" else "$n $many"

    /** The end-of-practice summary, spoken and shown. */
    fun summaryLines(tallies: List<Tally>): List<String> {
        val faced = tallies.sumOf { it.balls }
        if (faced == 0) return listOf("You did not face a ball.")
        val lines = ArrayList<String>()
        lines.add("Practice finished. You faced ${plural(faced, "ball", "balls")}.")
        tallies.filter { it.balls > 0 }.forEach {
            lines.add(
                "${it.name}: ${plural(it.runs, "run", "runs")} from ${plural(it.balls, "ball", "balls")}, " +
                    "out ${plural(it.outs, "time", "times")}, ${plural(it.fours, "four", "fours")} and ${plural(it.sixes, "six", "sixes")}."
            )
        }
        val shots = tallies.sumOf { it.shots }
        if (shots > 0) {
            val perfect = tallies.sumOf { it.perfect }
            val sensible = tallies.sumOf { it.sensible }
            val early = tallies.sumOf { it.early }
            val late = tallies.sumOf { it.late }
            lines.add("Timing: $perfect of ${plural(shots, "shot", "shots")} were perfectly timed. You tapped early on $early and late on $late.")
            lines.add("Shot choice: $sensible of $shots suited the ball.")
            lines.add(
                when {
                    early > late + 1 -> "Your habit is tapping early. Wait for the fifth buzz."
                    late > early + 1 -> "Your habit is tapping late. Tap a touch sooner."
                    else -> "Your timing was balanced. Keep practising."
                }
            )
        }
        return lines
    }
}

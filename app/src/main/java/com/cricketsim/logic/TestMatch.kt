package com.cricketsim.logic

import kotlin.random.Random

/** One finished innings of a Test match. */
data class TestInnings(
    val data: InningsData,
    val score: MatchScore,
    /** Ended by the batting captain's declaration. */
    val declared: Boolean = false,
    /** Ended because every wicket had fallen. */
    val allOut: Boolean = false
)

/**
 * What a five-day Test remembers beyond [MatchState]'s own fields. Null on every other match.
 * While a Test is on, `MatchState.currentInnings` counts 1 to 4 and `currentInningsData` is the innings being played;
 * finished innings are kept here.
 */
data class TestState(
    val completed: List<TestInnings> = emptyList(),
    /** True once the current innings has been added to [completed] (the match is between innings, or over). */
    val currentRecorded: Boolean = false,
    val followOn: Boolean = false,
    /** The user may enforce the follow-on and has not yet chosen. */
    val followOnPending: Boolean = false,
    /** Lunch, tea and stumps taken so far (3 a day). */
    val breaksDone: Int = 0,
    /** 0 = playing, 1 = the "break begins" pop-up is showing, 2 = the "play resumes" pop-up is showing. */
    val breakPhase: Int = 0,
    val sessionStartRuns: Int = 0,
    val sessionStartWickets: Int = 0,
    /** Legal balls of the match at which the ball in use was taken (0 = the match started with it). */
    val ballStartBalls: Int = 0,
    val newBallsTaken: Int = 0,
    /** The user has been told that the new ball is available. */
    val newBallNotified: Boolean = false,
    /** The length of the match: 5 days in a Test, 4 in a Ranji Trophy league match. */
    val days: Int = 5,
    /** Balls of play lost to rain so far. They use up the match clock (see [TestMatch.clockBalls]) but are never bowled. */
    val lostBalls: Int = 0,
    /** Rain stoppages so far in this match. */
    val rainStops: Int = 0,
    /** The lines of the rain pop-up while [breakPhase] is 3; null otherwise (and on old saves). */
    val rainLines: List<String>? = null,
    // The settings of a custom multi-day format. Each is stored so that 0 / false means "a Test's own" (old saves read right).
    /** Overs a day when it is not the Test's 90; 0 = 90. */
    val oversPerDayCustom: Int = 0,
    /** Two innings (one each) instead of four. */
    val twoInnings: Boolean = false,
    val noDeclarations: Boolean = false,
    val noFollowOn: Boolean = false,
    /** Lead that allows the follow-on; 0 = 200 (150 in a four-day match). */
    val followOnLeadCustom: Int = 0,
    val noNewBall: Boolean = false,
    /** Overs with the old ball before the new one is available when it is not 80; 0 = 80. */
    val newBallOversCustom: Int = 0
)

data class TestOutcome(val winnerId: String?, val text: String)

/**
 * The rules of a five-day Test: 90 overs a day in three sessions of 30 overs, four innings, the follow-on, declarations,
 * a draw when the 450 overs run out, and a pitch that wears. See TEST_MATCH.md.
 * Everything here is switched off by [FIVE_DAY_TEST] = false (the old two-innings, 90-over Test comes back).
 */
object TestMatch {

    const val FIVE_DAY_TEST = true

    const val DAYS = 5
    const val OVERS_PER_DAY = 90
    const val SESSION_OVERS = 30
    const val MATCH_OVERS = DAYS * OVERS_PER_DAY
    const val BALLS_PER_SESSION = SESSION_OVERS * 6
    const val BALLS_PER_DAY = OVERS_PER_DAY * 6
    const val MATCH_BALLS = MATCH_OVERS * 6

    /** A side that bats first and leads by this many runs or more may make the other side follow on (five-day Test). */
    const val FOLLOW_ON_LEAD = 200

    /** The fielding side may take a new ball after this many overs with the old one. */
    const val NEW_BALL_OVERS = 80

    /** A fresh ball helps the pace bowlers for this many balls (20 overs); after that they are a little less dangerous. Not measured: dials. */
    const val NEW_BALL_BOOST_BALLS = 120
    const val NEW_BALL_WICKET = 1.3
    const val OLD_BALL_WICKET = 0.96

    // Test tempo. The ball model is a T20 one (7.4 runs an over, all out in 34 overs); these weights bring a Test innings to about
    // 330 runs in 100 overs at 3.2 an over, measured with tools/difficulty_twin.js (AI v AI, Medium). Turn them to change the pace.
    const val SCALE_WICKET = 0.36
    const val SCALE_FOUR = 0.5
    const val SCALE_SIX = 0.22
    const val SCALE_DOT = 1.8
    const val SCALE_TWO = 0.8
    const val SCALE_THREE = 0.7

    /** A Test, or a first-class match given its days by the rules (the Ranji Trophy). */
    fun applies(format: MatchFormat, rules: MatchRules?): Boolean =
        FIVE_DAY_TEST && format == MatchFormat.TEST && (rules == null || rules.testDays > 0)

    fun start(rules: MatchRules? = null): TestState {
        val r = rules?.takeIf { it.testDays > 0 }
        return TestState(
            days = r?.testDays ?: DAYS,
            oversPerDayCustom = r?.oversPerDay?.takeIf { it in 1..89 } ?: 0,
            twoInnings = r?.testInnings == 2,
            noDeclarations = r != null && !r.declarations,
            noFollowOn = r != null && !r.followOn,
            followOnLeadCustom = r?.followOnLead?.takeIf { it > 0 } ?: 0,
            noNewBall = r != null && !r.newBall,
            newBallOversCustom = r?.newBallOvers?.takeIf { it > 0 && it != NEW_BALL_OVERS } ?: 0
        )
    }

    /** The rules of a first-class match of this many days played by two computer sides (no DRS, no Super Over; rain as in a Test). */
    fun rulesForDays(days: Int): MatchRules = MatchRules(
        overs = days * OVERS_PER_DAY, restrictionBalls = 0, fieldersOutside = 5, drsReviews = 0,
        rainEnabled = true, maxOversPerBowler = 999, noSuperOver = true, testDays = days
    )

    fun totalBalls(s: MatchState): Int = (s.test?.days ?: DAYS) * dayBalls(s)

    /** Overs bowled in a day: 90 in a Test, or the custom format's own. */
    fun perDayOvers(s: MatchState): Int = s.test?.oversPerDayCustom?.takeIf { it > 0 } ?: OVERS_PER_DAY

    fun dayBalls(s: MatchState): Int = perDayOvers(s) * 6

    /** 1.0 for a 90-over day: the dials written for a Test day are scaled by this for a shorter one. */
    private fun dayScale(s: MatchState): Double = perDayOvers(s) / 90.0

    /** Two innings (one each) or four. */
    fun inningsCount(s: MatchState): Int = if (s.test?.twoInnings == true) 2 else 4

    /** Overs with the old ball before the new one is available: 80 in a Test. */
    fun newBallOvers(s: MatchState): Int = s.test?.newBallOversCustom?.takeIf { it > 0 } ?: NEW_BALL_OVERS

    /**
     * The clock ball at which break number k is due (1 = day one's lunch, 2 = tea, 3 = stumps, 4 = day two's lunch...):
     * a day is three sessions of a third each, and the last session ends exactly on the day's last over.
     */
    fun breakAt(s: MatchState, k: Int): Int {
        if (k <= 0) return 0
        val d = perDayOvers(s)
        val day = (k - 1) / 3
        val pos = (k - 1) % 3 + 1
        return (day * d + pos * d / 3) * 6
    }

    /** How many breaks the clock has already run through. */
    fun breaksReached(s: MatchState, clock: Int): Int {
        var k = 0
        while (k < 60 && breakAt(s, k + 1) <= clock) k++
        return k
    }

    /** 200 in a five-day match, 150 in a four-day one, or the custom format's own. */
    fun followOnLead(s: MatchState): Int =
        s.test?.followOnLeadCustom?.takeIf { it > 0 } ?: if ((s.test?.days ?: DAYS) >= 5) FOLLOW_ON_LEAD else 150

    private fun ballsOf(score: MatchScore): Int = score.overs * 6 + score.balls

    private fun plural(n: Int, one: String, many: String = one + "s"): String = if (n == 1) "$n $one" else "$n $many"

    private fun oversText(sc: MatchScore): String = "${sc.overs}.${sc.balls}"

    private fun ordinal(n: Int): String = when (n) {
        1 -> "first"
        2 -> "second"
        3 -> "third"
        else -> "fourth"
    }

    // Looked up among the two sides that are playing: in a match between two computer sides the "user" team has another id.
    private fun teamOf(s: MatchState, id: String): Team =
        if (s.battingTeam.id == id) s.battingTeam else if (s.bowlingTeam.id == id) s.bowlingTeam else if (s.userTeam.id == id) s.userTeam else s.opponentTeam

    private fun otherTeam(s: MatchState, id: String): Team = if (s.battingTeam.id == id) s.bowlingTeam else s.battingTeam

    // ---------------------------------------------------------------- the clock

    /** Legal balls bowled in the whole match so far. */
    fun matchBalls(s: MatchState): Int {
        val t = s.test ?: return 0
        val done = t.completed.sumOf { ballsOf(it.score) }
        return if (t.currentRecorded) done else done + ballsOf(s.score)
    }

    /**
     * The match clock: balls bowled plus balls lost to rain. Sessions, days, the overs that remain and the draw all run on it,
     * so a rain stoppage uses up time exactly as it does in a real match.
     */
    fun clockBalls(s: MatchState): Int = matchBalls(s) + (s.test?.lostBalls ?: 0)

    fun ballsLeft(s: MatchState): Int = (totalBalls(s) - clockBalls(s)).coerceAtLeast(0)

    fun oversLeft(s: MatchState): Int = ballsLeft(s) / 6

    /** The 450 overs are bowled (checked when an over ends, or when an innings ends). */
    fun timeUp(s: MatchState): Boolean {
        val t = s.test ?: return false
        return clockBalls(s) >= totalBalls(s) && (t.currentRecorded || s.score.balls == 0)
    }

    private fun teamRuns(s: MatchState, teamId: String): Int {
        val t = s.test ?: return 0
        val done = t.completed.filter { it.data.battingTeamId == teamId }.sumOf { it.score.runs }
        val live = if (!t.currentRecorded && s.battingTeam.id == teamId) s.score.runs else 0
        return done + live
    }

    private fun totalRuns(s: MatchState): Int = teamRuns(s, s.userTeam.id) + teamRuns(s, s.opponentTeam.id)

    private fun totalWickets(s: MatchState): Int {
        val t = s.test ?: return 0
        val done = t.completed.sumOf { it.score.wickets }
        return if (t.currentRecorded) done else done + s.score.wickets
    }

    // ---------------------------------------------------------------- the pitch

    /** 0 (day one) to about 1 (day five); a dusty pitch wears faster, a flat one slower. Used by MatchEngine. */
    fun wearFactor(s: MatchState): Double {
        if (s.test == null) return 0.0
        val base = (clockBalls(s).toDouble() / totalBalls(s)).coerceIn(0.0, 1.0)
        val rate = when (s.pitchType) {
            PitchType.DUSTY -> 1.3
            PitchType.BATTING -> 0.7
            PitchType.GREEN -> 0.9
            else -> 1.0
        }
        return (base * rate).coerceIn(0.0, 1.0)
    }

    fun pitchReport(s: MatchState): String {
        val w = wearFactor(s)
        return when {
            w < 0.12 -> "The pitch is hard and true, the best of it for batting."
            w < 0.35 -> "The pitch is still good, with a little more help for the bowlers."
            w < 0.6 -> "The pitch is starting to wear, with some variable bounce and turn."
            w < 0.8 -> "The pitch is wearing badly now. The spinners are in the game and the ball is keeping low."
            else -> "The pitch is cracked and dry. Batting is hard work and spin is a big weapon."
        }
    }

    // ---------------------------------------------------------------- sessions and days

    private fun breakWord(k: Int): String = when ((k - 1) % 3) {
        0 -> "Lunch"
        1 -> "Tea"
        else -> "Stumps"
    }

    private fun breakDay(k: Int): Int = (k - 1) / 3 + 1

    private fun sessionName(n: Int): String = when (n) {
        1 -> "first"
        2 -> "second"
        else -> "third"
    }

    /** A session or day has just run its 30 overs (checked when an over has finished and nobody is being replaced). */
    fun breakDue(s: MatchState): Boolean {
        val t = s.test ?: return false
        if (t.breakPhase != 0 || t.followOnPending || t.currentRecorded) return false
        if (s.matchStatus != MatchStatus.IN_PROGRESS || s.superOverRound != 0) return false
        if (s.score.balls != 0 || s.pendingDismissal != null || s.activeRainDelay != null || s.drinksBreak != null) return false
        val mb = clockBalls(s)
        return mb < totalBalls(s) && mb >= breakAt(s, t.breaksDone + 1)
    }

    fun beginBreak(s: MatchState): MatchState {
        val t = s.test ?: return s
        return s.copy(test = t.copy(breaksDone = t.breaksDone + 1, breakPhase = 1))
    }

    /** Pop-up one (the break) moves on to pop-up two (play resumes); pop-up two ends the break. */
    fun continueBreak(s: MatchState): MatchState {
        val t = s.test ?: return s
        // The rain pop-up has one step: Resume play.
        if (t.breakPhase == 3) return s.copy(test = t.copy(breakPhase = 0, rainLines = null))
        return if (t.breakPhase == 1) {
            s.copy(test = t.copy(breakPhase = 2, sessionStartRuns = totalRuns(s), sessionStartWickets = totalWickets(s)))
        } else {
            s.copy(test = t.copy(breakPhase = 0))
        }
    }

    /** One drinks break in the middle of each session (the 15th over of it). */
    fun drinksDue(s: MatchState): Boolean {
        if (s.test == null) return false
        val mb = clockBalls(s)
        val k = breaksReached(s, mb)
        val start = breakAt(s, k)
        val size = breakAt(s, k + 1) - start
        if (size < 48) return false // a session of fewer than 8 overs has no drinks break
        return s.score.balls == 0 && (mb - start) in (size / 2) until (size / 2 + 6)
    }

    /** The break that has just been called (or the one that has just ended) is stumps, not lunch or tea. */
    fun isStumps(s: MatchState): Boolean = (s.test?.breaksDone ?: 0) % 3 == 0

    fun breakTitle(s: MatchState): String {
        val t = s.test ?: return ""
        if (t.breakPhase == 3) return "Rain delay"
        val k = t.breaksDone
        return if (t.breakPhase == 2) {
            if (k % 3 == 0) "Day ${k / 3 + 1} begins" else "Day ${k / 3 + 1}, ${sessionName(k % 3 + 1)} session begins"
        } else {
            "${breakWord(k)} on day ${breakDay(k)}"
        }
    }

    private fun scoreLines(s: MatchState): List<String> = listOf(
        "${s.battingTeam.name} are ${s.score.runs} for ${s.score.wickets} after ${oversText(s.score)} overs in the ${ordinal(s.currentInnings)} innings.",
        "${s.bowlingTeam.name} have taken ${plural(s.score.wickets, "wicket")}."
    )

    fun situationLine(s: MatchState): String? {
        if (s.test == null) return null
        val bat = s.battingTeam
        if (s.currentInnings == 1) return "${bat.name} are batting first."
        val target = s.target
        if (s.currentInnings == inningsCount(s) && target != null) {
            val need = (target - s.score.runs).coerceAtLeast(0)
            val inHand = bat.players.size - 1 - s.score.wickets
            return "${bat.name} need ${plural(need, "run")} to win, with ${plural(inHand, "wicket")} in hand."
        }
        val lead = teamRuns(s, bat.id) - teamRuns(s, s.bowlingTeam.id)
        return when {
            lead > 0 -> "${bat.name} lead by ${plural(lead, "run")}."
            lead < 0 -> "${bat.name} trail by ${plural(-lead, "run")}."
            else -> "The scores are level."
        }
    }

    private fun timeLine(s: MatchState): String {
        val t = s.test ?: return ""
        val today = (((t.breaksDone / 3) + 1) * dayBalls(s) - clockBalls(s)).coerceIn(0, dayBalls(s)) / 6
        return "${plural(oversLeft(s), "over")} remain in the match, ${today} of them today."
    }

    /** The lines of whichever break pop-up is showing. */
    fun screenLines(s: MatchState): List<String> {
        val t = s.test ?: return emptyList()
        val k = t.breaksDone
        if (t.breakPhase == 3) return t.rainLines ?: emptyList()
        val lines = ArrayList<String>()
        if (t.breakPhase == 1) {
            lines.add(
                if (k % 3 == 0) "That is the end of play on day ${breakDay(k)}."
                else "That is the end of the ${sessionName(k % 3)} session on day ${breakDay(k)}."
            )
            lines.addAll(scoreLines(s))
            val runs = totalRuns(s) - t.sessionStartRuns
            val wickets = totalWickets(s) - t.sessionStartWickets
            lines.add("In the session, ${plural(runs, "run")} were scored and ${plural(wickets, "wicket")} fell.")
            situationLine(s)?.let { lines.add(it) }
            lines.add(timeLine(s))
        } else {
            lines.addAll(scoreLines(s))
            situationLine(s)?.let { lines.add(it) }
            lines.add(timeLine(s))
            if (k % 3 == 0) lines.add(pitchReport(s))
            if (newBallAvailable(s)) lines.add("The new ball is available to ${s.bowlingTeam.name}.")
        }
        return lines
    }

    fun breakSpoken(s: MatchState): String = breakTitle(s) + ". " + screenLines(s).joinToString(" ")

    // ---------------------------------------------------------------- rain

    /** Expected rain stoppages in a match = RAIN_FACTOR x the match's chance of rain (a 30 percent chance gives about 0.6). A dial. */
    const val RAIN_FACTOR = 2.0

    /** At most this many stoppages in one match. */
    const val RAIN_MAX_STOPS = 4

    /** Rain never takes the last overs of the match: the game still gets a finish. */
    const val RAIN_KEEP_OVERS = 6

    /**
     * Rolled once at the end of every over (MatchSimulation, also for the computer's own first-class matches). If it rains,
     * a stretch of the match is lost: usually 8 to 20 overs, sometimes 20 to 40 (a session), now and then the rest of the
     * day. The lost overs use up the match clock (so lunch, tea, stumps and the draw all move), and a pop-up (break phase 3)
     * tells the player. There is no overs cut and no DLS in a Test: only less time to win. Null = no rain.
     */
    fun rainStoppage(s: MatchState, stadium: Stadium): MatchState? {
        val t = s.test ?: return null
        if (t.breakPhase != 0 || t.followOnPending || t.currentRecorded || t.rainStops >= RAIN_MAX_STOPS) return null
        if (s.matchStatus != MatchStatus.IN_PROGRESS || s.superOverRound != 0 || s.score.balls != 0) return null
        if (s.activeRainDelay != null || s.drinksBreak != null) return null
        if (oversLeft(s) <= RAIN_KEEP_OVERS + 12) return null
        val wet = if (s.weather.condition == WeatherCondition.LIGHT_RAIN) 1.5 else 1.0
        val perOver = s.weather.rainChancePercent / 100.0 * wet * RAIN_FACTOR / (totalBalls(s) / 6.0)
        if (Random.nextDouble() >= perOver) return null

        val clock = clockBalls(s)
        val toDayEnd = (dayBalls(s) - clock % dayBalls(s)) / 6
        val roll = Random.nextDouble()
        val wanted = when {
            roll < 0.5 -> 8 + Random.nextInt(13)
            roll < 0.85 -> 20 + Random.nextInt(21)
            else -> maxOf(toDayEnd, 12)
        }
        // A shorter day loses proportionally fewer overs to a stoppage (the day-ending washout is already in the day's own length).
        val scaled = if (roll < 0.85) (wanted * minOf(1.0, dayScale(s))).toInt() else wanted
        val lostOvers = scaled.coerceAtMost(oversLeft(s) - RAIN_KEEP_OVERS).coerceAtLeast(1)
        val lost = lostOvers * 6
        // Breaks the lost time ran through are not taken: play simply resumes in the session the clock has reached.
        val breaks = maxOf(t.breaksDone, breaksReached(s, clock + lost))
        val after = t.copy(lostBalls = t.lostBalls + lost, rainStops = t.rainStops + 1, breaksDone = breaks, breakPhase = 3)
        val moved = s.copy(test = after)
        val resume = if (breaks % 3 == 0) "Play will resume on day ${breaks / 3 + 1}."
        else "Play resumes in the ${sessionName(breaks % 3 + 1)} session of day ${breaks / 3 + 1}."
        val lines = listOf("Rain has stopped play.") + scoreLines(moved) +
            listOf("${plural(lostOvers, "over")} of the match were lost to rain.", resume, timeLine(moved))
        return moved.copy(test = after.copy(rainLines = lines))
    }

    /** Added to a drawn result when rain took overs. */
    private fun rainSuffix(s: MatchState): String {
        val lost = (s.test?.lostBalls ?: 0) / 6
        return if (lost > 0) " Rain cost ${plural(lost, "over")}." else ""
    }

    // ---------------------------------------------------------------- innings

    /** Adds the innings being played to the finished ones. */
    fun recordCurrent(s: MatchState, declared: Boolean = false): MatchState {
        val t = s.test ?: return s
        if (t.currentRecorded) return s
        val allOut = s.score.wickets >= s.battingTeam.players.size - 1
        val record = TestInnings(data = s.currentInningsData, score = s.score, declared = declared, allOut = allOut)
        val base = if (t.completed.isEmpty()) s.copy(firstInningsData = s.currentInningsData, firstInningsScore = s.score) else s
        return base.copy(test = t.copy(completed = t.completed + record, currentRecorded = true))
    }

    fun recordIfTest(s: MatchState): MatchState = if (s.test == null) s else recordCurrent(s)

    /** Every innings of the match so far, in order (the one being played last), for the scorecards. */
    fun allInnings(s: MatchState): List<InningsData> {
        val t = s.test ?: return listOfNotNull(s.firstInningsData, s.currentInningsData)
        val done = t.completed.map { it.data }
        return if (t.currentRecorded) done else done + s.currentInningsData
    }

    fun inningsSummaryLines(s: MatchState): List<String> {
        val t = s.test ?: return emptyList()
        val names = listOf("First", "Second", "Third", "Fourth")
        val lines = ArrayList<String>()
        t.completed.forEachIndexed { i, inn ->
            val how = if (inn.declared) ", declared" else ""
            lines.add("${names.getOrElse(i) { "Next" }} innings: ${inn.data.battingTeamName}, ${inn.score.runs} for ${inn.score.wickets} in ${oversText(inn.score)} overs$how.")
        }
        if (!t.currentRecorded) {
            lines.add("${names.getOrElse(t.completed.size) { "Next" }} innings: ${s.battingTeam.name}, ${s.score.runs} for ${s.score.wickets} in ${oversText(s.score)} overs.")
        }
        if (t.followOn) lines.add("The follow-on was enforced.")
        return lines
    }

    // ---------------------------------------------------------------- the result

    /** Null while the match is on. Needs the current innings to be recorded. */
    fun outcome(s: MatchState): TestOutcome? {
        val t = s.test ?: return null
        if (!t.currentRecorded) return null
        val list = t.completed
        val n = list.size
        val total = inningsCount(s)
        if (n < total - 1 || (n == total - 1 && total != 4)) return null
        val last = list[n - 1]
        fun runsOf(teamId: String): Int = list.filter { it.data.battingTeamId == teamId }.sumOf { it.score.runs }
        if (n == 3 && total == 4) {
            if (!(last.allOut || last.declared)) return null
            val twice = teamOf(s, last.data.battingTeamId)
            val once = otherTeam(s, twice.id)
            val diff = runsOf(once.id) - runsOf(twice.id)
            return if (diff > 0) TestOutcome(once.id, "${once.name} won by an innings and ${plural(diff, "run")}.") else null
        }
        val chaser = teamOf(s, last.data.battingTeamId)
        val defender = otherTeam(s, chaser.id)
        val chaserTotal = runsOf(chaser.id)
        val defenderTotal = runsOf(defender.id)
        return when {
            chaserTotal > defenderTotal -> TestOutcome(chaser.id, "${chaser.name} won by ${plural(chaser.players.size - 1 - last.score.wickets, "wicket")}.")
            last.allOut && chaserTotal == defenderTotal -> TestOutcome(null, "Match tied.")
            last.allOut -> TestOutcome(defender.id, "${defender.name} won by ${plural(defenderTotal - chaserTotal, "run")}.")
            else -> null
        }
    }

    private fun drawText(s: MatchState): String {
        val t = s.test ?: return "Match drawn."
        val last = t.completed.lastOrNull() ?: return "Match drawn."
        val target = s.target
        if (t.completed.size == inningsCount(s) && target != null) {
            val need = (target - last.score.runs).coerceAtLeast(0)
            return "Match drawn. ${last.data.battingTeamName} finished on ${last.score.runs} for ${last.score.wickets}, needing ${plural(need, "more run")}."
        }
        return "Match drawn."
    }

    fun resultText(s: MatchState): String? {
        if (s.test == null) return null
        val rec = recordIfTest(s)
        return (outcome(rec) ?: TestOutcome(null, drawText(rec) + rainSuffix(rec))).text
    }

    /** The match is over: a result, or a draw because the overs ran out. Null when this is not a five-day Test. */
    fun finish(s: MatchState): MatchState? {
        if (s.test == null) return null
        val rec = recordIfTest(s)
        val out = outcome(rec) ?: TestOutcome(null, drawText(rec) + rainSuffix(rec))
        return rec.copy(matchStatus = MatchStatus.COMPLETED, winnerId = out.winnerId)
    }

    // ---------------------------------------------------------------- follow-on

    fun followOnAvailable(s: MatchState): Boolean {
        val t = s.test ?: return false
        if (t.completed.size != 2 || !t.currentRecorded || t.twoInnings || t.noFollowOn) return false
        return t.completed[0].score.runs - t.completed[1].score.runs >= followOnLead(s)
    }

    /** The side that batted first is the one that may enforce it. */
    fun followOnEnforcerId(s: MatchState): String = s.test?.completed?.firstOrNull()?.data?.battingTeamId ?: s.battingTeam.id

    fun followOnLines(s: MatchState): List<String> {
        val t = s.test ?: return emptyList()
        if (t.completed.size < 2) return emptyList()
        val a = t.completed[0]
        val b = t.completed[1]
        return listOf(
            "${a.data.battingTeamName} scored ${a.score.runs}. ${b.data.battingTeamName} finished on ${b.score.runs}.",
            "${a.data.battingTeamName} lead by ${plural(a.score.runs - b.score.runs, "run")}, enough to enforce the follow-on.",
            timeLine(s)
        )
    }

    fun followOnSpoken(s: MatchState): String =
        "Follow-on. " + followOnLines(s).joinToString(" ") + " Enforce the follow-on, or bat again?"

    fun setFollowOn(s: MatchState, enforce: Boolean): MatchState =
        s.copy(test = s.test?.copy(followOn = enforce, followOnPending = false))

    fun aiEnforcesFollowOn(s: MatchState): Boolean {
        val t = s.test ?: return false
        if (t.completed.size < 2) return false
        val lead = t.completed[0].score.runs - t.completed[1].score.runs
        val p = when {
            oversLeft(s) < perDayOvers(s) -> 0.0
            oversLeft(s) < perDayOvers(s) * 13 / 9 -> if (lead >= 300) 0.4 else 0.15
            else -> if (lead >= 300) 0.9 else 0.6
        }
        return Random.nextDouble() < p
    }

    // ---------------------------------------------------------------- the next innings

    fun startNextInnings(s: MatchState): MatchState {
        val t = s.test ?: return s
        val next = t.completed.size + 1
        val firstBatId = t.completed.first().data.battingTeamId
        val a = teamOf(s, firstBatId)
        val b = otherTeam(s, firstBatId)
        val batting = when (next) {
            2 -> b
            3 -> if (t.followOn) b else a
            else -> if (t.followOn) a else b
        }
        val bowling = otherTeam(s, batting.id)
        val target: Int? = if (next == inningsCount(s)) {
            val own = t.completed.filter { it.data.battingTeamId == batting.id }.sumOf { it.score.runs }
            val opposition = t.completed.filter { it.data.battingTeamId != batting.id }.sumOf { it.score.runs }
            opposition - own + 1
        } else {
            null
        }
        val ready = s.copy(test = t.copy(currentRecorded = false, followOnPending = false))
        return MatchStateMachine.startTestInnings(ready, next, batting, bowling, target)
    }

    fun inningsBreakTitle(s: MatchState): String = "End of the ${ordinal(s.currentInnings - 1)} innings."

    fun startLabel(s: MatchState): String = "Start the ${ordinal(s.currentInnings)} innings"

    fun inningsBreakLines(s: MatchState, lastBall: String): List<String> {
        val t = s.test ?: return emptyList()
        val last = t.completed.lastOrNull()
        val lines = ArrayList<String>()
        if (last != null) {
            val how = when {
                last.declared -> ", declared"
                last.allOut -> ", all out"
                else -> ""
            }
            lines.add("${last.data.battingTeamName} finished on ${last.score.runs} for ${last.score.wickets} after ${oversText(last.score)} overs$how.")
        }
        if (t.followOn && s.currentInnings == 3) lines.add("${s.battingTeam.name} must follow on.")
        if (lastBall.isNotEmpty()) lines.add("Last ball: $lastBall")
        situationLine(s)?.let { lines.add(it) }
        lines.add(timeLine(s))
        lines.add(if (s.battingTeam.id == s.userTeam.id) "You are batting next." else "You are bowling next.")
        return lines
    }

    fun inningsBreakSpoken(s: MatchState): String = inningsBreakTitle(s) + " " + inningsBreakLines(s, "").joinToString(" ")

    // ---------------------------------------------------------------- declarations

    fun declarationLine(s: MatchState): String = "${s.battingTeam.name} declare at ${s.score.runs} for ${s.score.wickets}."

    /** The user's side is batting in the first three innings and the ball is dead. */
    fun canDeclare(s: MatchState): Boolean {
        val t = s.test ?: return false
        return !t.currentRecorded && !t.noDeclarations && t.breakPhase == 0 && !t.followOnPending &&
            s.matchStatus == MatchStatus.IN_PROGRESS && s.battingTeam.id == s.userTeam.id &&
            s.currentInnings in 1 until inningsCount(s) && s.pendingDismissal == null && !s.needsOpenerSelection &&
            s.ballByBall.isNotEmpty()
    }

    /** The computer declares at the start of an over, when its lead is safe for the time left. */
    fun aiShouldDeclare(s: MatchState): Boolean {
        val t = s.test ?: return false
        if (t.currentRecorded || t.noDeclarations || t.breakPhase != 0 || t.followOnPending) return false
        if (s.matchStatus != MatchStatus.IN_PROGRESS || s.battingTeam.id == s.userTeam.id) return false
        if (s.currentInnings !in 1 until inningsCount(s) || s.score.balls != 0 || s.pendingDismissal != null) return false
        if (s.score.overs < (40 * dayScale(s)).toInt()) return false
        if (s.currentInnings == 1) {
            return s.score.runs >= (520 * dayScale(s)).toInt() && s.score.overs >= (120 * dayScale(s)).toInt() && Random.nextDouble() < 0.3
        }
        val timeLeft = oversLeft(s)
        if (timeLeft < (50 * dayScale(s)).toInt()) return false
        val lead = teamRuns(s, s.battingTeam.id) - teamRuns(s, s.bowlingTeam.id)
        val required = maxOf(120.0 * dayScale(s), 4.2 * (timeLeft - 48 * dayScale(s)))
        return lead >= required && Random.nextDouble() < 0.5
    }

    // ---------------------------------------------------------------- the computer's approach

    fun battingBias(s: MatchState): Double {
        var bias = -0.3
        val target = s.target
        val left = ballsLeft(s).coerceAtLeast(1)
        if (s.currentInnings == inningsCount(s) && target != null) {
            val need = (target - s.score.runs).coerceAtLeast(0)
            val rate = need / (left / 6.0)
            bias = when {
                rate > 6.5 -> -0.55
                rate > 4.5 -> 0.4
                rate > 3.2 -> 0.0
                else -> -0.3
            }
        } else if (s.currentInnings >= 2) {
            val lead = teamRuns(s, s.battingTeam.id) - teamRuns(s, s.bowlingTeam.id)
            if (lead > 150 && left < (660 * dayScale(s)).toInt()) bias = 0.3
        }
        if (s.score.wickets >= 8) bias -= 0.1
        if (s.currentBowler.bowlingRating < 65) bias += 0.15
        return bias.coerceIn(-1.0, 1.0)
    }

    fun bowlingBias(s: MatchState): Double {
        var bias = 0.0
        if (s.score.wickets >= 7) bias += 0.3
        val target = s.target
        if (s.currentInnings == inningsCount(s) && target != null) {
            val need = (target - s.score.runs).coerceAtLeast(0)
            val rate = need / (ballsLeft(s).coerceAtLeast(1) / 6.0)
            if (rate < 3.0) bias += 0.2 else if (rate > 6.0) bias -= 0.3
        }
        return bias.coerceIn(-1.0, 1.0)
    }

    // ---------------------------------------------------------------- the new ball

    /** Legal balls bowled with the ball in use. */
    fun ballAge(s: MatchState): Int {
        val t = s.test ?: return 0
        return (matchBalls(s) - t.ballStartBalls).coerceAtLeast(0)
    }

    fun newBallAvailable(s: MatchState): Boolean {
        val t = s.test ?: return false
        if (t.noNewBall) return false
        return !t.currentRecorded && ballAge(s) >= newBallOvers(s) * 6
    }

    /** The user is bowling, the old ball is 80 overs old and the ball is dead. */
    fun canTakeNewBall(s: MatchState): Boolean {
        val t = s.test ?: return false
        return newBallAvailable(s) && t.breakPhase == 0 && !t.followOnPending &&
            s.matchStatus == MatchStatus.IN_PROGRESS && s.bowlingTeam.id == s.userTeam.id &&
            s.pendingDismissal == null && !s.needsOpenerSelection
    }

    fun takeNewBall(s: MatchState): MatchState {
        val t = s.test ?: return s
        return s.copy(test = t.copy(ballStartBalls = matchBalls(s), newBallsTaken = t.newBallsTaken + 1, newBallNotified = false))
    }

    fun newBallLine(s: MatchState): String = "${s.bowlingTeam.name} take the new ball."

    fun newBallReminder(): String = "The new ball is available. You can take it from Match info."

    /** The computer takes the new ball at the start of an over once it is available (not every time, so it is not instant). */
    fun aiShouldTakeNewBall(s: MatchState): Boolean {
        val t = s.test ?: return false
        if (!newBallAvailable(s) || t.breakPhase != 0 || t.followOnPending) return false
        if (s.matchStatus != MatchStatus.IN_PROGRESS || s.bowlingTeam.id == s.userTeam.id) return false
        if (s.score.balls != 0 || s.pendingDismissal != null || s.needsOpenerSelection) return false
        return Random.nextDouble() < 0.7
    }

    /** Tells the user once that the new ball is available. */
    fun shouldRemindNewBall(s: MatchState): Boolean {
        val t = s.test ?: return false
        return newBallAvailable(s) && !t.newBallNotified && t.breakPhase == 0 && !t.followOnPending &&
            s.bowlingTeam.id == s.userTeam.id && s.score.balls == 0 && s.pendingDismissal == null
    }

    fun markNewBallNotified(s: MatchState): MatchState {
        val t = s.test ?: return s
        return s.copy(test = t.copy(newBallNotified = true))
    }

    fun ballLine(s: MatchState): String {
        val overs = ballAge(s) / 6
        if (s.test?.noNewBall == true) return "The ball is ${overs} overs old."
        return if (newBallAvailable(s)) "The ball is ${overs} overs old. The new ball is available."
        else "The ball is ${overs} overs old. The new ball is due in ${newBallOvers(s) - overs} overs."
    }

    // ---------------------------------------------------------------- Match info

    fun statusLines(s: MatchState): List<String> {
        val t = s.test ?: return emptyList()
        val k = t.breaksDone
        val lines = ArrayList<String>()
        lines.add("Day ${k / 3 + 1} of ${t.days}, ${sessionName(k % 3 + 1)} session. Innings ${s.currentInnings} of ${inningsCount(s)}.")
        situationLine(s)?.let { lines.add(it) }
        lines.add(timeLine(s))
        lines.add(pitchReport(s))
        lines.add(ballLine(s))
        if (t.lostBalls > 0) lines.add("${plural(t.lostBalls / 6, "over")} have been lost to rain so far.")
        if (t.followOn) lines.add("The follow-on was enforced.")
        return lines
    }
}

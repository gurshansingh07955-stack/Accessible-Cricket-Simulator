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
    val sessionStartWickets: Int = 0
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

    // Test tempo. The ball model is a T20 one (7.4 runs an over, all out in 34 overs); these weights bring a Test innings to about
    // 330 runs in 100 overs at 3.2 an over, measured with tools/difficulty_twin.js (AI v AI, Medium). Turn them to change the pace.
    const val SCALE_WICKET = 0.36
    const val SCALE_FOUR = 0.5
    const val SCALE_SIX = 0.22
    const val SCALE_DOT = 1.8
    const val SCALE_TWO = 0.8
    const val SCALE_THREE = 0.7

    fun applies(format: MatchFormat, rules: MatchRules?): Boolean = FIVE_DAY_TEST && format == MatchFormat.TEST && rules == null

    fun start(): TestState = TestState()

    private fun ballsOf(score: MatchScore): Int = score.overs * 6 + score.balls

    private fun plural(n: Int, one: String, many: String = one + "s"): String = if (n == 1) "$n $one" else "$n $many"

    private fun oversText(sc: MatchScore): String = "${sc.overs}.${sc.balls}"

    private fun ordinal(n: Int): String = when (n) {
        1 -> "first"
        2 -> "second"
        3 -> "third"
        else -> "fourth"
    }

    private fun teamOf(s: MatchState, id: String): Team = if (s.userTeam.id == id) s.userTeam else s.opponentTeam

    private fun otherTeam(s: MatchState, id: String): Team = if (s.userTeam.id == id) s.opponentTeam else s.userTeam

    // ---------------------------------------------------------------- the clock

    /** Legal balls bowled in the whole match so far. */
    fun matchBalls(s: MatchState): Int {
        val t = s.test ?: return 0
        val done = t.completed.sumOf { ballsOf(it.score) }
        return if (t.currentRecorded) done else done + ballsOf(s.score)
    }

    fun ballsLeft(s: MatchState): Int = (MATCH_BALLS - matchBalls(s)).coerceAtLeast(0)

    fun oversLeft(s: MatchState): Int = ballsLeft(s) / 6

    /** The 450 overs are bowled (checked when an over ends, or when an innings ends). */
    fun timeUp(s: MatchState): Boolean {
        val t = s.test ?: return false
        return matchBalls(s) >= MATCH_BALLS && (t.currentRecorded || s.score.balls == 0)
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
        val base = (matchBalls(s).toDouble() / MATCH_BALLS).coerceIn(0.0, 1.0)
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
        val mb = matchBalls(s)
        return mb < MATCH_BALLS && mb >= (t.breaksDone + 1) * BALLS_PER_SESSION
    }

    fun beginBreak(s: MatchState): MatchState {
        val t = s.test ?: return s
        return s.copy(test = t.copy(breaksDone = t.breaksDone + 1, breakPhase = 1))
    }

    /** Pop-up one (the break) moves on to pop-up two (play resumes); pop-up two ends the break. */
    fun continueBreak(s: MatchState): MatchState {
        val t = s.test ?: return s
        return if (t.breakPhase == 1) {
            s.copy(test = t.copy(breakPhase = 2, sessionStartRuns = totalRuns(s), sessionStartWickets = totalWickets(s)))
        } else {
            s.copy(test = t.copy(breakPhase = 0))
        }
    }

    /** One drinks break in the middle of each session (the 15th over of it). */
    fun drinksDue(s: MatchState): Boolean {
        if (s.test == null) return false
        val mb = matchBalls(s)
        return s.score.balls == 0 && (mb % BALLS_PER_SESSION) in 90..95
    }

    fun breakTitle(s: MatchState): String {
        val t = s.test ?: return ""
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
        if (s.currentInnings == 4 && target != null) {
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
        val today = (((t.breaksDone / 3) + 1) * BALLS_PER_DAY - matchBalls(s)).coerceIn(0, BALLS_PER_DAY) / 6
        return "${plural(oversLeft(s), "over")} remain in the match, ${today} of them today."
    }

    /** The lines of whichever break pop-up is showing. */
    fun screenLines(s: MatchState): List<String> {
        val t = s.test ?: return emptyList()
        val k = t.breaksDone
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
        }
        return lines
    }

    fun breakSpoken(s: MatchState): String = breakTitle(s) + ". " + screenLines(s).joinToString(" ")

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
        if (n < 3) return null
        val last = list[n - 1]
        fun runsOf(teamId: String): Int = list.filter { it.data.battingTeamId == teamId }.sumOf { it.score.runs }
        if (n == 3) {
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
        if (t.completed.size == 4 && target != null) {
            val need = (target - last.score.runs).coerceAtLeast(0)
            return "Match drawn. ${last.data.battingTeamName} finished on ${last.score.runs} for ${last.score.wickets}, needing ${plural(need, "more run")}."
        }
        return "Match drawn."
    }

    fun resultText(s: MatchState): String? {
        if (s.test == null) return null
        val rec = recordIfTest(s)
        return (outcome(rec) ?: TestOutcome(null, drawText(rec))).text
    }

    /** The match is over: a result, or a draw because the overs ran out. Null when this is not a five-day Test. */
    fun finish(s: MatchState): MatchState? {
        if (s.test == null) return null
        val rec = recordIfTest(s)
        val out = outcome(rec) ?: TestOutcome(null, drawText(rec))
        return rec.copy(matchStatus = MatchStatus.COMPLETED, winnerId = out.winnerId)
    }

    // ---------------------------------------------------------------- follow-on

    fun followOnAvailable(s: MatchState): Boolean {
        val t = s.test ?: return false
        if (t.completed.size != 2 || !t.currentRecorded) return false
        return t.completed[0].score.runs - t.completed[1].score.runs >= FOLLOW_ON_LEAD
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
            oversLeft(s) < 90 -> 0.0
            oversLeft(s) < 130 -> if (lead >= 300) 0.4 else 0.15
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
        val target: Int? = if (next == 4) {
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
        return !t.currentRecorded && t.breakPhase == 0 && !t.followOnPending &&
            s.matchStatus == MatchStatus.IN_PROGRESS && s.battingTeam.id == s.userTeam.id &&
            s.currentInnings in 1..3 && s.pendingDismissal == null && !s.needsOpenerSelection &&
            s.ballByBall.isNotEmpty()
    }

    /** The computer declares at the start of an over, when its lead is safe for the time left. */
    fun aiShouldDeclare(s: MatchState): Boolean {
        val t = s.test ?: return false
        if (t.currentRecorded || t.breakPhase != 0 || t.followOnPending) return false
        if (s.matchStatus != MatchStatus.IN_PROGRESS || s.battingTeam.id == s.userTeam.id) return false
        if (s.currentInnings !in 1..3 || s.score.balls != 0 || s.pendingDismissal != null) return false
        if (s.score.overs < 40) return false
        if (s.currentInnings == 1) {
            return s.score.runs >= 520 && s.score.overs >= 120 && Random.nextDouble() < 0.3
        }
        val timeLeft = oversLeft(s)
        if (timeLeft < 50) return false
        val lead = teamRuns(s, s.battingTeam.id) - teamRuns(s, s.bowlingTeam.id)
        val required = maxOf(120.0, 4.2 * (timeLeft - 48))
        return lead >= required && Random.nextDouble() < 0.5
    }

    // ---------------------------------------------------------------- the computer's approach

    fun battingBias(s: MatchState): Double {
        var bias = -0.3
        val target = s.target
        val left = ballsLeft(s).coerceAtLeast(1)
        if (s.currentInnings == 4 && target != null) {
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
            if (lead > 150 && left < 660) bias = 0.3
        }
        if (s.score.wickets >= 8) bias -= 0.1
        if (s.currentBowler.bowlingRating < 65) bias += 0.15
        return bias.coerceIn(-1.0, 1.0)
    }

    fun bowlingBias(s: MatchState): Double {
        var bias = 0.0
        if (s.score.wickets >= 7) bias += 0.3
        val target = s.target
        if (s.currentInnings == 4 && target != null) {
            val need = (target - s.score.runs).coerceAtLeast(0)
            val rate = need / (ballsLeft(s).coerceAtLeast(1) / 6.0)
            if (rate < 3.0) bias += 0.2 else if (rate > 6.0) bias -= 0.3
        }
        return bias.coerceIn(-1.0, 1.0)
    }

    // ---------------------------------------------------------------- Match info

    fun statusLines(s: MatchState): List<String> {
        val t = s.test ?: return emptyList()
        val k = t.breaksDone
        val lines = ArrayList<String>()
        lines.add("Day ${k / 3 + 1} of $DAYS, ${sessionName(k % 3 + 1)} session. Innings ${s.currentInnings} of 4.")
        situationLine(s)?.let { lines.add(it) }
        lines.add(timeLine(s))
        lines.add(pitchReport(s))
        if (t.followOn) lines.add("The follow-on was enforced.")
        return lines
    }
}

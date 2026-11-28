package com.cricketsim.logic

import com.cricketsim.ui.match.BallResult
import kotlin.random.Random

/** What the user is told and asked about one ball he can run on (Settings > Running between the wickets). */
class RunChance(
    /** The runs the ball is worth (1 to 3). When [dangerous] the last of them is a run out. */
    val potential: Int,
    /** The simulated ball is a run out: the last run of [potential] ends in it. */
    val dangerous: Boolean,
    /** Career mode, the player is the non-striker: his partner calls and he accepts or refuses each run. */
    val partnerCall: Boolean,
    val strikerName: String,
    val fielderName: String,
    /** "at long on", "at mid wicket", "behind the stumps", "in the field". */
    val fielderPosition: String,
    val depth: FieldingDepth,
    val fielderRating: Int,
    /** Where the ball went: "along the ground through the covers". */
    val wherePhrase: String,
    /** 0 = runs off the bat, 1 = byes, 2 = leg byes (patch 129: he may run those too). */
    val kind: Int = 0,
    /** The ball is a no ball (patch 139): he may run on it, and on a no ball only a run out can get him out. */
    val noBall: Boolean = false
) {
    /** The wicketkeeper's name (patch 151): a throw from the deep goes to him or to [relayName] unless the fielder backs himself to hit the stumps. */
    var keeperName: String = "the keeper"

    /** The nearest fielder in the inner ring to the one chasing the ball (patch 151), or "" when there is none. */
    var relayName: String = ""
}

/**
 * What he did: the runs he completed, and whether the next run he went for was a run out. Patch 151: [overthrows] are the extra runs from throws that went astray,
 * [fielder] is who broke the stumps in a run out, and [line] what was said about it.
 */
class RunVerdict(val runs: Int, val runOut: Boolean, val overthrows: Int = 0, val fielder: String? = null, val line: String? = null)

/**
 * Running between the wickets (patch 126, byes in patch 129). When the user is batting and a ball is worth 1, 2 or 3 runs, off the bat OR byes and leg byes,
 * he decides the runs himself:
 *  - the phone says where the ball went and who is closing in, then he answers within [DECISION_SECONDS] seconds: swipe UP with two fingers to run or DOWN to stay
 *    (or the Run / Stay buttons, Settings > How you answer); no answer in time counts as no run;
 *  - after each run it says "One run completed", where the ball and the fielder are, and he may go again;
 *  - in career mode, when his player is the NON-STRIKER, the computer's striker calls and he accepts or refuses each run.
 * The simulated ball says how many runs it is worth and whether the last of them is a run out ([RunChance.dangerous]). Taking those runs gives exactly
 * what was simulated; staying back gives fewer (and saves the run out); going for MORE is an extra run that the fielder can stop: a run out with a chance
 * that depends on how far he is and his fielding rating ([extraRisk]). At most [MAX_RUNS] runs in all.
 * [MANUAL_RUNNING] false switches the whole feature off (Settings > Running between the wickets does it for the player).
 */
object RunDecisions {
    const val MANUAL_RUNNING = true
    const val DECISION_SECONDS = 5
    const val MAX_RUNS = 3

    private fun keeperOf(team: Team): Player? {
        val id = team.wicketkeeperId ?: team.players.find { it.role == PlayerRole.WICKETKEEPER }?.id
        return team.players.find { it.id == id }
    }

    private fun sectorWords(s: FieldingSector): String = when (s) {
        FieldingSector.SLIP_GULLY -> "past the slips"
        FieldingSector.LEG_SLIP -> "behind square on the leg side"
        FieldingSector.THIRD_MAN -> "down to third man"
        FieldingSector.POINT -> "towards point"
        FieldingSector.COVER -> "through the covers"
        FieldingSector.MID_OFF -> "towards mid off"
        FieldingSector.LONG_STOP -> "behind the wicketkeeper"
        FieldingSector.MID_ON -> "towards mid on"
        FieldingSector.MID_WICKET -> "through mid wicket"
        FieldingSector.COW_CORNER -> "towards cow corner"
        FieldingSector.SQUARE_LEG -> "to square leg"
        FieldingSector.FINE_LEG -> "down to fine leg"
    }

    /** The chance for the ball just simulated, with the keeper and the nearest inner fielder a throw from the deep can go to (patch 151). */
    fun build(before: MatchState, result: BallResult, partnerCall: Boolean): RunChance? {
        val c = buildChance(before, result, partnerCall) ?: return null
        c.keeperName = keeperOf(before.bowlingTeam)?.name ?: "the keeper"
        c.relayName = relayOf(before, before.bowlingTeam.players.find { it.name == c.fielderName }?.id)
        return c
    }

    /** The fielder of the inner ring nearest to the one chasing the ball: the man a throw from the deep can be relayed to. */
    private fun relayOf(before: MatchState, chaserId: String?): String {
        val keeperId = keeperOf(before.bowlingTeam)?.id
        val chaser = before.fieldPlacements.find { it.playerId == chaserId }
        val inner = before.fieldPlacements.filter {
            it.playerId.isNotEmpty() && it.playerId != chaserId && it.playerId != keeperId && it.playerId != before.currentBowler.id && it.depth != FieldingDepth.DEEP
        }
        val pick = if (chaser == null) inner.firstOrNull() else inner.minByOrNull { FieldingSystem.sectorGapDegrees(it.sector, chaser.sector) }
        return before.bowlingTeam.players.find { it.id == pick?.playerId }?.name ?: ""
    }

    /** The chance for the ball just simulated, or null if it is not a ball he runs on (a boundary, a dot, a wide, a wicket that is not a run out). */
    private fun buildChance(before: MatchState, result: BallResult, partnerCall: Boolean): RunChance? {
        if (!MANUAL_RUNNING) return null
        val o = result.outcome
        val byeTotal = o.byes + o.legByes
        if (o.isWide) return null
        // A ball left alone is not run on. A no ball that was played is (patch 139): the runs are his, and only a run out can get him out.
        if (byeTotal == 0 && o.leftAlone) return null
        val runOutBall = byeTotal == 0 && o.isWicket && o.dismissalType == DismissalType.RUN_OUT
        if (o.isWicket && !runOutBall) return null
        // A catch put down (patch 149) is worth the runs it scored, and at least one he may go for.
        val potential = if (byeTotal > 0) byeTotal else if (o.droppedCatch) maxOf(o.runs, 1) else o.runs + (if (runOutBall) 1 else 0)
        if (potential !in 1..MAX_RUNS) return null

        // A catch the fielding side put down (patch 149): he may run on it, with the fielder who dropped it closing in.
        if (o.droppedCatch) {
            val dropper = before.bowlingTeam.players.find { it.name == o.fielderName }
            if (dropper != null) {
                val spot = before.fieldPlacements.find { it.playerId == dropper.id }
                val where = if (spot != null) "at ${FieldingSystem.getPositionLabel(spot).lowercase()}" else "in the field"
                return RunChance(
                    potential, false, partnerCall, before.currentBatsmen.first.name,
                    dropper.name, where, spot?.depth ?: FieldingDepth.SHORT, dropper.fieldingRating, "loose after the dropped catch", noBall = o.isNoBall
                )
            }
        }

        val keeper = keeperOf(before.bowlingTeam)
        val keeperId = keeper?.id
        val placed = before.fieldPlacements.filter { it.playerId.isNotEmpty() && it.playerId != before.currentBowler.id && it.playerId != keeperId }

        if (byeTotal > 0) {
            // Byes: the keeper turns and chases it; leg byes: the fielder nearest to fine leg.
            val legBye = o.legByes > 0
            val pick = if (legBye) placed.minByOrNull { FieldingSystem.sectorGapDegrees(it.sector, FieldingSector.FINE_LEG) } else null
            val fielder = (if (legBye) before.bowlingTeam.players.find { it.id == pick?.playerId } else keeper)
                ?: keeper
                ?: before.bowlingTeam.players.firstOrNull { it.id != before.currentBowler.id }
                ?: return null
            val position = if (legBye && pick != null) "at ${FieldingSystem.getPositionLabel(pick).lowercase()}" else "behind the stumps"
            val phrase = if (legBye) "off the batter's pad and away behind square on the leg side" else "past the bat and past the keeper"
            return RunChance(
                potential, false, partnerCall, before.currentBatsmen.first.name,
                fielder.name, position, if (legBye) (pick?.depth ?: FieldingDepth.DEEP) else FieldingDepth.SHORT, fielder.fieldingRating, phrase,
                if (legBye) 2 else 1, o.isNoBall
            )
        }

        val shot = BattingSystem.getShotDirection(result.battingDecision.shot, result.battingDecision.intent)
        val sector = shot?.sector
        val aerial = shot?.isAerial == true
        // The fielder going after it: the one nearest to where it went (a deep fielder for a ball in the air, an inner one for a ball along the ground);
        // for a push or a block that ran, one close in front of the bat.
        val pick = when {
            placed.isEmpty() -> null
            sector == null -> placed.firstOrNull { it.depth == FieldingDepth.CLOSE }
                ?: placed.minByOrNull { FieldingSystem.sectorGapDegrees(it.sector, FieldingSector.MID_ON) }
            else -> {
                val inSector = placed.filter { it.sector == sector }
                val here = if (aerial) inSector.firstOrNull { it.depth == FieldingDepth.DEEP } ?: inSector.firstOrNull()
                else inSector.firstOrNull { it.depth != FieldingDepth.DEEP } ?: inSector.firstOrNull()
                here ?: placed.minByOrNull { FieldingSystem.sectorGapDegrees(it.sector, sector) }
            }
        }
        val fielder = before.bowlingTeam.players.find { it.id == pick?.playerId }
            ?: before.bowlingTeam.players.firstOrNull { it.id != before.currentBowler.id }
            ?: return null
        val position = if (pick != null) "at ${FieldingSystem.getPositionLabel(pick).lowercase()}" else "in the field"
        val phrase = if (sector == null) "just in front of the bat, into a gap"
        else "${if (aerial) "in the air" else "along the ground"} ${sectorWords(sector)}"
        return RunChance(
            potential, runOutBall, partnerCall, before.currentBatsmen.first.name,
            fielder.name, position, pick?.depth ?: FieldingDepth.SHORT, fielder.fieldingRating, phrase, noBall = o.isNoBall
        )
    }

    /** The dropped-catch line without the runs the game picked, then the runs he actually took (patch 149). */
    private fun droppedText(original: BallOutcome, runs: Int): String {
        val head = original.commentary.substringBefore(" They run").trim()
        return head + when (runs) {
            0 -> " No run."
            1 -> " They run one."
            2 -> " They run two."
            else -> " They run three."
        }
    }

    /** The chance that an EXTRA run (more than the ball is worth) ends in a run out; [extraIndex] is 1 for the first extra run, 2 for the next. */
    private fun extraRisk(c: RunChance, extraIndex: Int): Double {
        val base = when (c.depth) {
            FieldingDepth.DEEP -> 0.22
            FieldingDepth.SHORT -> 0.45
            FieldingDepth.CLOSE -> 0.65
        }
        return (base + (c.fielderRating - 60) / 300.0 + 0.10 * (extraIndex - 1)).coerceIn(0.12, 0.85)
    }

    /** Switches the throw model (patch 151) off: false is the old behaviour, a run out is always the fielder hitting the stumps, and there are no overthrows. */
    const val THROW_MODEL = true
    private const val MAX_OVERTHROWS = 3

    /** What the throw did: a run out (by [fielder], who broke the stumps), or not out with [overthrows] extra runs; [line] is what is said ("" when nothing is worth saying). */
    class ThrowResult(
        val out: Boolean, val overthrows: Int, val line: String, val fielder: String?,
        /** Patch 156, for an overthrow: the throw was straight at the stumps, who it was meant for when it was not, and what is said of the wild throw. */
        val direct: Boolean = false, val receiver: String = "", val astray: String = ""
    )

    private fun ratingOf(c: RunChance): Double = c.fielderRating.coerceIn(0, 100) / 100.0

    private fun chaserIsKeeper(c: RunChance): Boolean = c.fielderName == c.keeperName

    /**
     * How likely the fielder is to throw straight at the stumps instead of to the keeper or a fielder nearby. It comes from his confidence, his rating, and from where
     * he is throwing from: a man in the deep mostly relays it (about one in ten with a poor arm, one in three with a great one), a man in the inner ring mostly throws
     * at the stumps, and a man close in almost always does.
     */
    private fun directChance(c: RunChance): Double {
        if (chaserIsKeeper(c)) return 0.90
        val r = ratingOf(c)
        return when (c.depth) {
            FieldingDepth.DEEP -> 0.08 + 0.30 * r
            FieldingDepth.SHORT -> 0.45 + 0.35 * r
            FieldingDepth.CLOSE -> 0.75 + 0.20 * r
        }
    }

    /** The chance that a throw that does not run the batter out goes astray and gives an overthrow: a wild direct throw from the deep most of all. */
    private fun overthrowChance(c: RunChance, direct: Boolean): Double {
        val bad = 1.0 - ratingOf(c)
        return when (c.depth) {
            FieldingDepth.DEEP -> if (direct) 0.08 + 0.14 * bad else 0.02
            FieldingDepth.SHORT -> if (direct) 0.04 + 0.10 * bad else 0.015
            FieldingDepth.CLOSE -> if (direct) 0.02 + 0.07 * bad else 0.01
        }
    }

    /**
     * The throw that comes in as he sets off for the run after [completed] runs (patch 151). A throw only comes once the runs the ball is worth are being
     * completed (or at once when the fielder is close in). The fielder either throws straight at the stumps ([directChance]) or to the keeper or a fielder nearby.
     *  - The last run of a ball that was a run out: the batter is out (the ball was simulated that way); only HOW changes: a direct hit or a relay.
     *  - An extra run: out with the old chance ([extraRisk]), a little higher for a direct throw and lower for a relay.
     *  - Otherwise safe, but a throw that goes astray can give an overthrow of one or two runs.
     */
    fun resolveAttempt(c: RunChance, completed: Int, random: Random): ThrowResult {
        if (!THROW_MODEL) {
            val out = attemptIsOut(c, completed, random)
            return ThrowResult(out, 0, if (out) runOutLine(c) else "", if (out) c.fielderName else null)
        }
        val n = completed + 1
        val throwComes = n >= c.potential || c.depth == FieldingDepth.CLOSE
        if (!throwComes) return ThrowResult(false, 0, "", null)
        val direct = random.nextDouble() < directChance(c)
        val receiver = when {
            chaserIsKeeper(c) -> c.relayName.ifEmpty { "the bowler" }
            c.depth == FieldingDepth.DEEP && c.relayName.isNotEmpty() && random.nextDouble() < 0.6 -> c.relayName
            else -> c.keeperName
        }
        val f = c.fielderName
        val contestedLast = n == c.potential && c.dangerous
        val contestedExtra = n > c.potential
        val out = when {
            contestedLast -> true
            contestedExtra -> random.nextDouble() < (extraRisk(c, n - c.potential) * (if (direct) 1.35 else 0.85)).coerceIn(0.05, 0.90)
            else -> false
        }
        if (out) {
            val line = if (direct) {
                if (c.depth == FieldingDepth.DEEP) "A direct hit from the deep! $f hits the stumps and the batter is run out."
                else "No! $f hits the stumps and the batter is run out."
            } else {
                if (c.depth == FieldingDepth.DEEP) "$f throws it in from the deep to $receiver, who breaks the stumps. The batter is run out."
                else "$f throws to $receiver, who whips off the bails. The batter is run out."
            }
            return ThrowResult(true, 0, line, if (direct) f else receiver)
        }
        val missed = if (!contestedExtra) "" else if (direct) "$f throws at the stumps but misses." else "$f throws to $receiver, but it is too late."
        if (random.nextDouble() < overthrowChance(c, direct)) {
            val extra = if (random.nextDouble() < 0.2) 2 else 1
            val astray = if (direct) "$f's throw at the stumps is wild. Overthrow!" else "$receiver cannot gather the throw. Overthrow!"
            return ThrowResult(false, extra, (missed + " " + astray + " They take " + (if (extra == 1) "one more." else "two more.")).trim(), null,
                direct, receiver, (missed + " " + astray).trim())
        }
        return ThrowResult(false, 0, missed, null)
    }

    /** Does the run he sets off for, after [completed] runs, end in a run out? Runs the ball is worth are safe, except the last when the ball is a run out. */
    fun attemptIsOut(c: RunChance, completed: Int, random: Random): Boolean {
        val n = completed + 1
        return when {
            n < c.potential -> false
            n == c.potential -> c.dangerous
            else -> random.nextDouble() < extraRisk(c, n - c.potential)
        }
    }

    /** The most runs he can be asked about: all three, or (a partner's calls) only the runs the ball is worth. */
    fun maxRunsFor(c: RunChance): Int = if (c.partnerCall) c.potential else MAX_RUNS

    /** How risky the next run LOOKS (what he is told): the distance of the fielder, worse when it really is a run out, the true chance for an extra run. */
    private fun perceivedRisk(c: RunChance, completed: Int): Double {
        val n = completed + 1
        val byDepth = when (c.depth) {
            FieldingDepth.DEEP -> 0.05
            FieldingDepth.SHORT -> 0.25
            FieldingDepth.CLOSE -> 0.40
        }
        return when {
            n < c.potential -> byDepth
            n == c.potential -> if (c.dangerous) byDepth + 0.25 else byDepth
            else -> extraRisk(c, n - c.potential)
        }
    }

    private fun hint(c: RunChance, completed: Int): String {
        val extra = if (completed + 1 > c.potential) "That would be an extra run. " else ""
        val r = perceivedRisk(c, completed)
        val text = when {
            r < 0.15 -> "The fielder is a long way from it: a comfortable run."
            r < 0.35 -> "A fair chance of making it."
            r < 0.55 -> "It will be close."
            else -> "Very risky: the fielder is on top of it."
        }
        return extra + text
    }

    private fun fielderLine(c: RunChance): String =
        if (c.kind == 1) "${c.fielderName}, the keeper, fielding rating ${c.fielderRating}, is turning to chase it."
        else "${c.fielderName}, fielding rating ${c.fielderRating}, ${c.fielderPosition}, is closing in on the ball."

    /** How the user answers, in words: "Swipe up with two fingers to run, or down to stay." or "Choose Run or Stay." */
    private fun how(c: RunChance, byButtons: Boolean): String = when {
        byButtons && c.partnerCall -> "Choose Accept or Refuse."
        byButtons -> "Choose Run or Stay."
        c.partnerCall -> "Swipe up with two fingers to accept the run, or down to refuse."
        else -> "Swipe up with two fingers to run, or down to stay."
    }

    /** What the phone says after the shot: where the ball went, who is closing in, what to do. */
    fun opening(c: RunChance, byButtons: Boolean = false): String {
        val ball = (if (c.noBall) "It is a no ball, so only a run out can get you out. " else "") + "The ball went ${c.wherePhrase}."
        return if (c.partnerCall) {
            "$ball ${c.strikerName} calls: Yes! Run! ${fielderLine(c)} ${hint(c, 0)} ${how(c, byButtons)} You have five seconds."
        } else {
            "$ball ${fielderLine(c)} ${hint(c, 0)} ${how(c, byButtons)} You have five seconds."
        }
    }

    /** What the phone says after a run: the runs completed, where the ball and the fielder are, the next decision (none after the last run he may take). */
    fun afterRun(c: RunChance, completed: Int, last: Boolean, byButtons: Boolean = false, note: String = ""): String {
        val done = when (completed) {
            1 -> "One run completed."
            2 -> "Two runs completed."
            else -> "Three runs completed."
        }
        val f = c.fielderName
        val status = when (c.depth) {
            FieldingDepth.DEEP -> if (completed == 1) "$f is still running the ball down in the deep." else "$f has the ball in the deep and is throwing it in."
            FieldingDepth.SHORT -> "$f has picked it up ${c.fielderPosition} and is throwing it in."
            FieldingDepth.CLOSE -> "$f has the ball in his hand close in and is looking at the stumps."
        }
        val said = if (note.isEmpty()) "" else "$note "
        if (last) return "$done $said$status That is all the runs."
        val ask = if (c.partnerCall) {
            "${c.strikerName} calls for another. " + (if (byButtons) "Accept or refuse." else "Swipe up with two fingers to accept, down to refuse.") + " Five seconds."
        } else {
            (if (byButtons) "Choose Run or Stay." else "Swipe up with two fingers for another run, down to stay.") + " Five seconds."
        }
        return "$done $said$status ${hint(c, completed)} $ask"
    }

    fun runOutLine(c: RunChance): String = "No! ${c.fielderName} hits the stumps and the batter is run out."

    // ---- Overthrows (patch 156): the extra runs are the batter's to take, they are no longer added by themselves ----

    /** The most overthrow runs one wild throw can offer. */
    const val MAX_OVERTHROW_RUNS = 2

    private fun runsDoneText(completed: Int): String = when (completed) {
        0 -> ""
        1 -> "One run completed."
        2 -> "Two runs completed."
        else -> "Three runs completed."
    }

    /** Who is going after the loose ball: the man the throw was meant for, else the keeper (or, when the keeper threw it, the nearest fielder). */
    private fun chaserOf(c: RunChance, t: ThrowResult?): String = when {
        t != null && !t.direct && t.receiver.isNotEmpty() -> t.receiver
        chaserIsKeeper(c) -> c.relayName.ifEmpty { "a fielder" }
        else -> c.keeperName
    }

    /** Where the ball is after a throw that went astray, and who is chasing it (said once, when the overthrow happens). */
    fun overthrowWhere(c: RunChance, t: ThrowResult, random: Random): String {
        val chaser = chaserOf(c, t)
        if (!t.direct) return "The ball has got away from ${t.receiver.ifEmpty { chaser }} and is running towards the boundary. $chaser is chasing it."
        val place = listOf(
            "past the stumps and away towards the boundary behind the wicketkeeper",
            "past the stumps and away down the leg side",
            "past the stumps and away down the off side"
        )[random.nextInt(3)]
        return "The ball has gone $place. $chaser is chasing it."
    }

    /**
     * What is said when the overthrow happens ([taken] = 0: the run he was taking, the wild throw, where the ball is) and after each overthrow run he takes
     * (the ball is still loose), then how to answer. [left] is how many overthrow runs are still on offer. The ball is away from the fielders, so these runs are safe.
     */
    fun overthrowPrompt(c: RunChance, completed: Int, taken: Int, left: Int, where: String, t: ThrowResult?, byButtons: Boolean): String {
        val ask = if (c.partnerCall) {
            "${c.strikerName} calls for another. " + (if (byButtons) "Accept or refuse." else "Swipe up with two fingers to accept, down to refuse.") + " Five seconds."
        } else {
            (if (byButtons) "Choose Run or Stay." else "Swipe up with two fingers to run again, down to stay.") + " Five seconds."
        }
        if (taken == 0) {
            val count = if (left >= 2) " There could be two runs in this." else ""
            return listOf(
                runsDoneText(completed), t?.astray.orEmpty(), where,
                "The fielders are nowhere near it, so another run is safe.$count", ask
            ).filter { it.isNotEmpty() }.joinToString(" ")
        }
        return "Overthrow run completed. The ball is still loose and ${chaserOf(c, t)} has not got to it. Another run is safe. $ask"
    }

    /** After the last overthrow run: the ball is gathered and the page ends. */
    fun overthrowEnd(c: RunChance, taken: Int, t: ThrowResult?): String =
        "${if (taken == 1) "Overthrow run completed." else "Two overthrow runs completed."} ${chaserOf(c, t)} finally gathers the ball. That is all the runs."

    /**
     * The ball after what he did, or null if it stands exactly as simulated (he took the runs the ball was worth, and no run out). Fewer runs: not out, the
     * runs he completed (and no run out, if the simulated ball was one). A run out: either the simulated one (he went for its last run; the fielder is the one he
     * was told about) or an extra run that failed (the runs completed stand, one of the two batters is out, as in the engine's run outs). A ball worth byes or
     * leg byes keeps them as byes or leg byes, however many he runs (patch 129).
     */
    fun finalOutcome(before: MatchState, c: RunChance, original: BallOutcome, verdictIn: RunVerdict, random: Random = Random.Default): BallOutcome? {
        // Overthrows (patch 151) are runs too: they join the runs he completed.
        val verdict = RunVerdict(verdictIn.runs + verdictIn.overthrows, verdictIn.runOut, verdictIn.overthrows, verdictIn.fielder, verdictIn.line)
        val overNote = if (verdict.overthrows > 0) " Overthrow${if (verdict.overthrows > 1) "s" else ""}: ${verdict.overthrows} more run${if (verdict.overthrows > 1) "s" else ""}." else ""
        val runsRun = verdict.runs - verdict.overthrows
        val striker = before.currentBatsmen.first
        val bowlerName = before.currentBowler.name
        val byeTotal = original.byes + original.legByes
        if (byeTotal > 0) {
            val isBye = original.byes > 0
            val penalty = if (original.isNoBall) 1 else 0
            val byes = if (isBye) verdict.runs else 0
            val legByes = if (isBye) 0 else verdict.runs
            if (verdict.runOut) {
                val out = if (random.nextBoolean()) before.currentBatsmen.first else before.currentBatsmen.second
                val base = original.copy(
                    isWicket = true, dismissalType = DismissalType.RUN_OUT, isEdge = null, outBatsmanId = out.id,
                    fielderName = verdict.fielder ?: c.fielderName, fielderPosition = null,
                    byes = byes, legByes = legByes, extraRuns = penalty + verdict.runs
                )
                return base.copy(commentary = verdict.line ?: MatchEngine.generateCommentary(base, out.name, bowlerName))
            }
            if (verdict.runs == byeTotal) return null
            val base = original.copy(byes = byes, legByes = legByes, extraRuns = penalty + verdict.runs)
            val note = when {
                runsRun > byeTotal -> " They steal the extra run."
                runsRun == 0 && verdict.overthrows == 0 -> if (c.partnerCall) " You turn down the call." else " The batters stay put."
                runsRun < byeTotal -> if (c.partnerCall) " You turn down the call." else " The batters hold back."
                else -> ""
            }
            return base.copy(commentary = MatchEngine.generateCommentary(base, striker.name, bowlerName) + note + (verdict.line?.let { " $it" } ?: overNote))
        }

        val runOutBall = original.isWicket && original.dismissalType == DismissalType.RUN_OUT
        if (verdict.runOut) {
            if (runOutBall) return original.copy(fielderName = verdict.fielder ?: c.fielderName, commentary = verdict.line ?: original.commentary)
            val out = if (random.nextBoolean()) before.currentBatsmen.first else before.currentBatsmen.second
            val base = original.copy(
                runs = verdict.runs, isWicket = true, dismissalType = DismissalType.RUN_OUT, isEdge = null,
                outBatsmanId = out.id, fielderName = verdict.fielder ?: c.fielderName, fielderPosition = null, droppedCatch = false
            )
            return base.copy(commentary = verdict.line ?: MatchEngine.generateCommentary(base, out.name, bowlerName))
        }
        if (!runOutBall && verdict.runs == original.runs) return null
        val base = original.copy(
            runs = verdict.runs, isWicket = false, dismissalType = null, outBatsmanId = null, fielderName = null, fielderPosition = null,
            isEdge = if (runOutBall) null else original.isEdge
        )
        if (original.droppedCatch) return base.copy(commentary = droppedText(original, verdict.runs) + (verdict.line?.let { " $it" } ?: overNote))
        val note = when {
            runsRun > original.runs -> " They steal the extra run."
            runsRun < c.potential -> if (c.partnerCall) " You turn down the call." else " The batters hold back."
            else -> ""
        }
        return base.copy(commentary = MatchEngine.generateCommentary(base, striker.name, bowlerName) + note + (verdict.line?.let { " $it" } ?: overNote))
    }
}

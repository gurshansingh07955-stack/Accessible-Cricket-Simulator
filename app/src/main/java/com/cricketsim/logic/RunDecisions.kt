package com.cricketsim.logic

import com.cricketsim.ui.match.BallResult
import kotlin.random.Random

/** What the user is told and asked about one ball he can run on (Settings > Running between the wickets). */
class RunChance(
    /** The runs the shot is worth (1 to 3). When [dangerous] the last of them is a run out. */
    val potential: Int,
    /** The simulated ball is a run out: the last run of [potential] ends in it. */
    val dangerous: Boolean,
    /** Career mode, the player is the non-striker: his partner calls and he accepts or refuses each run. */
    val partnerCall: Boolean,
    val strikerName: String,
    val fielderName: String,
    /** "at long on", "at mid wicket", "in the field". */
    val fielderPosition: String,
    val depth: FieldingDepth,
    val fielderRating: Int,
    /** Where the ball went: "along the ground through the covers". */
    val wherePhrase: String
)

/** What he did: the runs he completed, and whether the next run he went for was a run out. */
class RunVerdict(val runs: Int, val runOut: Boolean)

/**
 * Running between the wickets (patch 126). When the user is batting and a ball is worth 1, 2 or 3 runs, he decides the runs himself:
 *  - the phone says where the ball went and who is closing in, then he swipes UP with two fingers to run or DOWN to stay; no swipe in
 *    [DECISION_SECONDS] seconds counts as no run;
 *  - after each run it says "One run completed", where the ball and the fielder are, and he may go again;
 *  - in career mode, when his player is the NON-STRIKER, the computer's striker calls and he accepts (up) or refuses (down) each run.
 * The simulated ball says how many runs the shot is worth and whether the last of them is a run out ([RunChance.dangerous]). Taking those runs gives exactly
 * what was simulated; staying back gives fewer runs (and saves the run out); going for MORE than the shot is worth is an extra run that the fielder can
 * stop: a run out with a chance that depends on how far he is and his fielding rating ([extraRisk]). At most [MAX_RUNS] runs in all.
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

    /** The chance for the ball just simulated, or null if it is not a ball he runs on (a boundary, a dot, a wide or no-ball, a wicket that is not a run out). */
    fun build(before: MatchState, result: BallResult, partnerCall: Boolean): RunChance? {
        if (!MANUAL_RUNNING) return null
        val o = result.outcome
        if (o.isWide || o.isNoBall || o.leftAlone || o.droppedCatch) return null
        val runOutBall = o.isWicket && o.dismissalType == DismissalType.RUN_OUT
        if (o.isWicket && !runOutBall) return null
        val potential = o.runs + (if (runOutBall) 1 else 0)
        if (potential !in 1..MAX_RUNS) return null

        val shot = BattingSystem.getShotDirection(result.battingDecision.shot, result.battingDecision.intent)
        val sector = shot?.sector
        val aerial = shot?.isAerial == true
        val keeperId = keeperOf(before.bowlingTeam)?.id
        val placed = before.fieldPlacements.filter { it.playerId.isNotEmpty() && it.playerId != before.currentBowler.id && it.playerId != keeperId }
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
            fielder.name, position, pick?.depth ?: FieldingDepth.SHORT, fielder.fieldingRating, phrase
        )
    }

    /** The chance that an EXTRA run (more than the shot is worth) ends in a run out; [extraIndex] is 1 for the first extra run, 2 for the next. */
    private fun extraRisk(c: RunChance, extraIndex: Int): Double {
        val base = when (c.depth) {
            FieldingDepth.DEEP -> 0.22
            FieldingDepth.SHORT -> 0.45
            FieldingDepth.CLOSE -> 0.65
        }
        return (base + (c.fielderRating - 60) / 300.0 + 0.10 * (extraIndex - 1)).coerceIn(0.12, 0.85)
    }

    /** Does the run he sets off for, after [completed] runs, end in a run out? Runs the shot is worth are safe, except the last when the ball is a run out. */
    fun attemptIsOut(c: RunChance, completed: Int, random: Random): Boolean {
        val n = completed + 1
        return when {
            n < c.potential -> false
            n == c.potential -> c.dangerous
            else -> random.nextDouble() < extraRisk(c, n - c.potential)
        }
    }

    /** The most runs he can be asked about: all three, or (a partner's calls) only the runs the shot is worth. */
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

    private fun fielderLine(c: RunChance): String = "${c.fielderName}, fielding rating ${c.fielderRating}, ${c.fielderPosition}, is closing in on the ball."

    /** What the phone says after the shot: where the ball went, who is closing in, what to do. */
    fun opening(c: RunChance): String {
        val ball = "The ball went ${c.wherePhrase}."
        return if (c.partnerCall) {
            "$ball ${c.strikerName} calls: Yes! Run! ${fielderLine(c)} ${hint(c, 0)} Swipe up with two fingers to accept the run, or down to refuse. You have five seconds."
        } else {
            "$ball ${fielderLine(c)} ${hint(c, 0)} Swipe up with two fingers to run, or down to stay. You have five seconds."
        }
    }

    /** What the phone says after a run: the runs completed, where the ball and the fielder are, the next decision (none after the last run he may take). */
    fun afterRun(c: RunChance, completed: Int, last: Boolean): String {
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
        if (last) return "$done $status That is all the runs."
        val ask = if (c.partnerCall) "${c.strikerName} calls for another. Swipe up with two fingers to accept, down to refuse. Five seconds."
        else "Swipe up with two fingers for another run, down to stay. Five seconds."
        return "$done $status ${hint(c, completed)} $ask"
    }

    fun runOutLine(c: RunChance): String = "No! ${c.fielderName} hits the stumps and the batter is run out."

    /**
     * The ball after what he did, or null if it stands exactly as simulated (he took the runs the shot was worth, and no run out). Fewer runs: not out, the
     * runs he completed (and no run out, if the simulated ball was one). A run out: either the simulated one (he went for its last run; the fielder is the one he
     * was told about) or an extra run that failed (the runs completed stand, one of the two batters is out, as in the engine's run outs).
     */
    fun finalOutcome(before: MatchState, c: RunChance, original: BallOutcome, verdict: RunVerdict, random: Random = Random.Default): BallOutcome? {
        val runOutBall = original.isWicket && original.dismissalType == DismissalType.RUN_OUT
        val striker = before.currentBatsmen.first
        val bowlerName = before.currentBowler.name
        if (verdict.runOut) {
            if (runOutBall) return original.copy(fielderName = c.fielderName)
            val out = if (random.nextBoolean()) before.currentBatsmen.first else before.currentBatsmen.second
            val base = original.copy(
                runs = verdict.runs, isWicket = true, dismissalType = DismissalType.RUN_OUT, isEdge = null,
                outBatsmanId = out.id, fielderName = c.fielderName, fielderPosition = null
            )
            return base.copy(commentary = MatchEngine.generateCommentary(base, out.name, bowlerName))
        }
        if (!runOutBall && verdict.runs == original.runs) return null
        val base = original.copy(
            runs = verdict.runs, isWicket = false, dismissalType = null, outBatsmanId = null, fielderName = null, fielderPosition = null,
            isEdge = if (runOutBall) null else original.isEdge
        )
        val note = when {
            verdict.runs > original.runs -> " They steal the extra run."
            verdict.runs < c.potential -> if (c.partnerCall) " You turn down the call." else " The batters hold back."
            else -> ""
        }
        return base.copy(commentary = MatchEngine.generateCommentary(base, striker.name, bowlerName) + note)
    }
}

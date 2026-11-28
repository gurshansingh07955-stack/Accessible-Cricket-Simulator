package com.cricketsim.logic

import kotlin.random.Random

/**
 * Cricbuzz-style text commentary for one ball (patch 158).
 *
 * The old lines ("FOUR! Beautiful cover drive by ...") ignored what had really happened, so a pull for four could be described as a cover
 * drive. These lines are built from the facts of the ball itself, which [MatchEngine.simulateBall] now stores on the [BallOutcome]:
 *
 *   "Bumrah to Warner, FOUR, 143 km/h, back of a length on off stump. Warner pulls, well timed, over mid-wicket and it races away to the boundary."
 *
 * The first sentence is the delivery (who bowled to whom, the result, the speed, the length and the line, a variation or swing when there is one).
 * The second says what the batter did: the shot he really played, how well it was timed, and where it went (the sector the game itself uses for
 * that shot, on the ground or in the air). Wickets say how the batter tried to play and who took the catch and where.
 *
 * A ball without the stored facts (an older save) gets the old lines back ([generate] returns null); [CRICBUZZ_STYLE] false does the same for all balls.
 */
object BallCommentary {

    const val CRICBUZZ_STYLE = true

    private fun <T> pick(items: List<T>): T = items[Random.nextInt(items.size)]

    private fun lengthPhrase(l: DeliveryLength): String = when (l) {
        BowlingLength.BOUNCER -> "a bouncer"
        BowlingLength.SHORT -> "short"
        BowlingLength.BACK_OF_LENGTH -> "back of a length"
        BowlingLength.GOOD_LENGTH -> "a good length"
        BowlingLength.FULL -> "full"
        BowlingLength.HALF_VOLLEY -> "a half-volley"
        BowlingLength.YORKER -> "a yorker"
        MisExecutedLength.FULL_TOSS -> "a full toss"
        MisExecutedLength.LOW_FULL_TOSS -> "a low full toss"
        MisExecutedLength.LONG_HOP -> "a long hop"
        else -> "a good length"
    }

    private fun linePhrase(l: BowlingLine): String = when (l) {
        BowlingLine.OUTSIDE_OFF -> "outside off"
        BowlingLine.OFF_STUMP -> "on off stump"
        BowlingLine.MIDDLE_STUMP -> "on middle stump"
        BowlingLine.LEG_STUMP -> "on leg stump"
        BowlingLine.OUTSIDE_LEG -> "down the leg side"
    }

    private fun variationName(v: BowlingVariation?): String? = when (v) {
        BowlingVariation.OFF_CUTTER -> "an off-cutter"
        BowlingVariation.LEG_CUTTER -> "a leg-cutter"
        BowlingVariation.SLOWER_BALL -> "a slower ball"
        BowlingVariation.KNUCKLEBALL -> "a knuckleball"
        BowlingVariation.CROSS_SEAM -> "a cross-seam delivery"
        BowlingVariation.DOOSRA -> "a doosra"
        BowlingVariation.TEESRA -> "a teesra"
        BowlingVariation.GOOGLY -> "a googly"
        BowlingVariation.ARM_BALL -> "an arm ball"
        BowlingVariation.FLIPPER -> "a flipper"
        BowlingVariation.CARROM_BALL -> "a carrom ball"
        BowlingVariation.TOP_SPINNER -> "a top-spinner"
        BowlingVariation.SLIDER -> "a slider"
        else -> null
    }

    /** "a slower ball, full on middle stump, swinging in". */
    private fun deliveryText(length: DeliveryLength, line: BowlingLine, variation: BowlingVariation?, swing: SwingType?): String {
        val special = variationName(variation)
        val swingTail = when (swing) {
            SwingType.IN_SWING -> ", swinging in"
            SwingType.OUT_SWING -> ", swinging away"
            else -> ""
        }
        return (if (special != null) "$special, " else "") + lengthPhrase(length) + " " + linePhrase(line) + swingTail
    }

    private fun resultWord(o: BallOutcome): String = when {
        o.isWicket -> "OUT"
        o.isWide -> "WIDE"
        o.byes > 0 -> if (o.byes == 1) "1 bye" else "${o.byes} byes"
        o.legByes > 0 -> if (o.legByes == 1) "1 leg bye" else "${o.legByes} leg byes"
        o.runs == 6 -> "SIX"
        o.runs == 4 -> "FOUR"
        o.runs == 0 -> "no run"
        o.runs == 1 -> "1 run"
        else -> "${o.runs} runs"
    }

    /** How the shot was timed; nothing is said of an ordinary one. */
    private fun timingWord(t: BowlingQualityTier?): String = when (t) {
        BowlingQualityTier.PERFECT -> "perfectly timed"
        BowlingQualityTier.IDEAL -> "well timed"
        BowlingQualityTier.BAD -> "a little mistimed"
        BowlingQualityTier.VERY_BAD -> "badly mistimed"
        else -> ""
    }

    private fun verbOf(shot: NamedShot): String = when (shot) {
        NamedShot.FORWARD_DEFENSE -> "defends off the front foot"
        NamedShot.BACKWARD_DEFENSE -> "defends off the back foot"
        NamedShot.STRAIGHT_DRIVE, NamedShot.OFF_DRIVE, NamedShot.ON_DRIVE -> "drives"
        NamedShot.COVER_DRIVE -> "cover drives"
        NamedShot.SQUARE_DRIVE -> "drives square"
        NamedShot.SHORT_PUNCH -> "punches"
        NamedShot.CUT_SHOT -> "cuts"
        NamedShot.LATE_CUT -> "late cuts"
        NamedShot.PULL_SHOT -> "pulls"
        NamedShot.HOOK_SHOT -> "hooks"
        NamedShot.LEG_GLANCE_FLICK -> "flicks"
        NamedShot.SWEEP_SHOT -> "sweeps"
        NamedShot.REVERSE_SWEEP -> "reverse sweeps"
        NamedShot.UPPER_CUT -> "upper cuts"
        NamedShot.SCOOP_SHOT -> "scoops"
        NamedShot.HELICOPTER_SHOT -> "plays the helicopter shot"
        NamedShot.SWITCH_HIT -> "switch hits"
        NamedShot.LEAVE -> "leaves"
    }

    /** The fielder's usual spot in a sector, for "the fielder at mid-wicket". */
    private fun spotName(s: FieldingSector): String = when (s) {
        FieldingSector.THIRD_MAN -> "third man"
        FieldingSector.POINT -> "point"
        FieldingSector.COVER -> "cover"
        FieldingSector.MID_OFF -> "mid-off"
        FieldingSector.MID_ON -> "mid-on"
        FieldingSector.MID_WICKET -> "mid-wicket"
        FieldingSector.COW_CORNER -> "cow corner"
        FieldingSector.SQUARE_LEG -> "square leg"
        FieldingSector.FINE_LEG -> "fine leg"
        FieldingSector.SLIP_GULLY -> "gully"
        FieldingSector.LEG_SLIP -> "leg slip"
        FieldingSector.LONG_STOP -> "the keeper"
    }

    /** Where the ball went, on the ground or in the air ("through the covers", "over mid-wicket"). */
    private fun placement(shot: NamedShot, sector: FieldingSector, aerial: Boolean): String {
        if (shot == NamedShot.STRAIGHT_DRIVE) {
            return if (aerial) pick(listOf("over the bowler's head", "straight back over the bowler"))
            else pick(listOf("straight down the ground", "back past the bowler"))
        }
        return if (aerial) {
            when (sector) {
                FieldingSector.THIRD_MAN -> "over third man"
                FieldingSector.POINT -> "over point"
                FieldingSector.COVER -> "over the covers"
                FieldingSector.MID_OFF -> "over mid-off"
                FieldingSector.MID_ON -> "over mid-on"
                FieldingSector.MID_WICKET -> "over mid-wicket"
                FieldingSector.COW_CORNER -> "over cow corner"
                FieldingSector.SQUARE_LEG -> "over square leg"
                FieldingSector.FINE_LEG -> "over fine leg"
                FieldingSector.SLIP_GULLY -> "over the slips"
                FieldingSector.LEG_SLIP -> "over leg slip"
                FieldingSector.LONG_STOP -> "over the keeper"
            }
        } else {
            when (sector) {
                FieldingSector.THIRD_MAN -> pick(listOf("down to third man", "past third man"))
                FieldingSector.POINT -> pick(listOf("past point", "square on the off side"))
                FieldingSector.COVER -> pick(listOf("through the covers", "past cover"))
                FieldingSector.MID_OFF -> pick(listOf("past mid-off", "wide of mid-off"))
                FieldingSector.MID_ON -> pick(listOf("past mid-on", "wide of mid-on"))
                FieldingSector.MID_WICKET -> pick(listOf("through mid-wicket", "wide of mid-wicket"))
                FieldingSector.COW_CORNER -> "towards cow corner"
                FieldingSector.SQUARE_LEG -> pick(listOf("past square leg", "square on the leg side"))
                FieldingSector.FINE_LEG -> pick(listOf("down to fine leg", "fine on the leg side"))
                FieldingSector.SLIP_GULLY -> "through the slips"
                FieldingSector.LEG_SLIP -> "past leg slip"
                FieldingSector.LONG_STOP -> "past the keeper"
            }
        }
    }

    private val LEG_SIDE = setOf(
        FieldingSector.LEG_SLIP, FieldingSector.SQUARE_LEG, FieldingSector.MID_WICKET,
        FieldingSector.COW_CORNER, FieldingSector.MID_ON, FieldingSector.FINE_LEG
    )

    /** Where an edge went: past the slips or down to third man for an off-side shot, fine or past the keeper for a leg-side one. */
    private fun edgePlace(sector: FieldingSector?, aerial: Boolean): String {
        val legSide = sector != null && sector in LEG_SIDE
        return if (legSide) {
            if (aerial) "over the keeper" else pick(listOf("down to fine leg", "past the keeper"))
        } else {
            if (aerial) "over the slips" else pick(listOf("past the slips", "down to third man", "wide of the slips"))
        }
    }

    private fun runWords(n: Int): String = when (n) {
        1 -> "a single"
        2 -> "two runs"
        3 -> "three runs"
        else -> "$n runs"
    }

    /** The commentary for one ball, or null when the ball has no stored delivery facts (an older save) or the style is switched off. */
    fun generate(o: BallOutcome, batter: String, bowler: String): String? {
        if (!CRICBUZZ_STYLE) return null
        val length = o.bowlingActualLength ?: return null
        val line = o.bowlingLine ?: return null
        val speed = o.speedKmh?.let { ", $it km/h" } ?: ""
        val delivery = deliveryText(length, line, o.bowlingVariation, o.bowlingSwing)
        val header = "$bowler to $batter, ${resultWord(o)}$speed, $delivery."
        return header + " " + body(o, batter, bowler, line)
    }

    private fun body(o: BallOutcome, b: String, bowler: String, line: BowlingLine): String {
        if (o.isWide) {
            return if (line == BowlingLine.OUTSIDE_LEG) pick(listOf("Down the leg side, and the umpire signals a wide.", "Too far down the leg side, that is a wide."))
            else pick(listOf("Too wide of the stumps, and the umpire signals a wide.", "Well wide of the off stump, that is called wide."))
        }
        if (o.isWicket) return wicketBody(o, b, bowler)
        if (o.byes > 0) {
            val w = when (o.byes) { 1 -> "a bye"; 2 -> "two byes"; else -> "three byes" }
            return pick(listOf(
                "The ball beats the bat and the keeper, and they run $w.",
                "Missed by $b and the keeper cannot gather it cleanly: $w."
            ))
        }
        if (o.legByes > 0) {
            val w = when (o.legByes) { 1 -> "a leg bye"; 2 -> "two leg byes"; else -> "three leg byes" }
            return pick(listOf(
                "Off the pads and away! They run $w.",
                "It strikes $b on the body and runs away: $w."
            ))
        }
        if (o.leftAlone) {
            return pick(listOf(
                "$b shoulders arms and lets it go through to the keeper.",
                "$b leaves it alone and the keeper gathers it cleanly.",
                "No shot offered by $b, the keeper takes it."
            ))
        }
        val shot = o.shot ?: return genericBody(o, b)
        return shotBody(o, b, bowler, shot) + flavour(o, bowler)
    }

    /** Without a stored shot (the batter's decision was not available): what happened, in the old plain way. */
    private fun genericBody(o: BallOutcome, b: String): String = when (o.runs) {
        6 -> "$b hits it all the way for six."
        4 -> "$b finds the boundary."
        0 -> "No run."
        else -> "They run ${runWords(o.runs)}."
    }

    /** A short extra remark on the bowling now and then. */
    private fun flavour(o: BallOutcome, bowler: String): String {
        if (Random.nextDouble() > 0.35) return ""
        val q = o.bowlingQualityTier
        return when {
            o.runs >= 4 && (q == BowlingQualityTier.BAD || q == BowlingQualityTier.VERY_BAD) -> " A loose delivery from $bowler."
            o.runs == 0 && (q == BowlingQualityTier.PERFECT || q == BowlingQualityTier.IDEAL) -> " Excellent bowling from $bowler."
            else -> ""
        }
    }

    private fun stepOut(o: BallOutcome, shot: NamedShot): String =
        if (o.shotIntent == IntentDirection.RIGHT && !BattingSystem.isDefensiveShot(shot)) "steps out and " else ""

    private fun shotBody(o: BallOutcome, b: String, bowler: String, shot: NamedShot): String {
        val defensive = BattingSystem.isDefensiveShot(shot)
        val act = stepOut(o, shot) + verbOf(shot)
        val sector = o.shotSector
        val runs = o.runs
        val aerial = o.shotAerial || runs == 6
        val where = if (sector != null) placement(shot, sector, aerial) else null
        val timing = timingWord(o.shotTiming)
        val t = if (timing.isEmpty()) "" else ", $timing"
        val edge = o.isEdge == true
        return when {
            defensive ->
                if (runs == 0) pick(listOf("$b $act.", "$b $act, no run.")) else "$b $act and sneaks ${runWords(runs)}."
            runs == 6 ->
                if (edge) "$b $act and gets a top edge, it sails ${edgePlace(sector, true)} for SIX!"
                else "$b $act$t, ${where ?: "high and handsome"} and that is gone for SIX!"
            runs == 4 ->
                if (edge) "$b $act but gets an edge, it runs away ${edgePlace(sector, false)} for FOUR."
                else pick(listOf(
                    "$b $act$t, ${where ?: "into the gap"} and it races away to the boundary.",
                    "$b $act$t, ${where ?: "into the gap"}, and that is FOUR."
                ))
            runs in 1..3 ->
                "$b $act$t, ${where ?: "into the gap"}, for ${runWords(runs)}."
            runs >= 5 ->
                "$b $act$t, ${where ?: "into the gap"}, and they run $runs."
            else -> {
                val mistimed = o.shotTiming == BowlingQualityTier.BAD || o.shotTiming == BowlingQualityTier.VERY_BAD
                if (mistimed) pick(listOf("$b $act but mistimes it, no run.", "$b $act but does not middle it, and there is no run."))
                else if (sector != null) "$b $act, but the fielder at ${spotName(sector)} cuts it off, no run."
                else "$b $act, but finds the fielder, no run."
            }
        }
    }

    private fun wicketBody(o: BallOutcome, b: String, bowler: String): String {
        val shot = o.shot
        val attempt = if (shot != null && !o.leftAlone && shot != NamedShot.LEAVE) "$b ${stepOut(o, shot)}${verbOf(shot)}" else null
        val a = attempt ?: b
        return when (o.dismissalType) {
            DismissalType.RUN_OUT -> pick(listOf(
                "A terrible mix-up and $b is short of the crease!",
                "Direct hit! $b is well short, a brilliant run out."
            ))
            DismissalType.STUMPED -> pick(listOf(
                "$a but is beaten, and the keeper whips off the bails! Stumped!",
                "Beaten in the flight, and $b is stumped!"
            ))
            DismissalType.CAUGHT_AND_BOWLED -> pick(listOf(
                "$a, straight back to the bowler and $bowler holds on! Caught and bowled.",
                "$a, and $bowler takes a sharp catch off his own bowling!"
            ))
            DismissalType.BOWLED ->
                if (o.leftAlone) "$b leaves it alone and it crashes into the stumps! Bowled!"
                else pick(listOf(
                    "$a but is beaten, and the stumps are shattered! Bowled, $bowler!",
                    "$a and misses, clean bowled! $bowler shatters the stumps!"
                ))
            DismissalType.LBW -> pick(listOf(
                "$a but is hit on the pad right in front of the stumps. LBW!",
                "$a and is trapped in front, the finger goes up. LBW!"
            ))
            else -> {
                val taker = o.fielderName ?: "a fielder"
                val pos = o.fielderPosition?.let { " $it" } ?: ""
                val sector = o.shotSector
                if (o.isEdge == true) {
                    pick(listOf(
                        "$a, gets a thick edge and $taker takes a good catch$pos.",
                        "$a, there is an edge and it carries to $taker$pos, who takes it safely."
                    ))
                } else {
                    val toward = if (sector != null) " towards ${spotName(sector)}" else ""
                    pick(listOf(
                        "$a, but gets it high in the air$toward and $taker takes the catch$pos.",
                        "$a and finds $taker$pos, who holds on to a good catch."
                    ))
                }
            }
        }
    }
}

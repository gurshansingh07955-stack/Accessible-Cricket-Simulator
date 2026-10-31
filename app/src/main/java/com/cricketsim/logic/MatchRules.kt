package com.cricketsim.logic

/**
 * The rules a custom tournament (or a game tournament given another format) plays its matches by. A match carries
 * them in [MatchState.rules]; a null means "the format's own rules".
 *
 * @param overs overs a side
 * @param restrictionBalls balls at the start of each innings with the fielding restriction (0 = none)
 * @param fieldersOutside the most fielders allowed outside the circle during that restriction
 * @param drsReviews reviews per innings: 0 = DRS off, 1 to 4, or [CustomTournamentDef.UNLIMITED_REVIEWS]
 * @param rainEnabled whether rain can interrupt a match
 * @param maxOversPerBowler the most overs one bowler may bowl
 */
data class MatchRules(
    val overs: Int,
    val restrictionBalls: Int,
    val fieldersOutside: Int,
    val drsReviews: Int,
    val rainEnabled: Boolean,
    val maxOversPerBowler: Int,
    /** True = a tied match is not settled by a Super Over (the result is a tie). */
    val noSuperOver: Boolean = false,
    /** 0 = not a Test; 4 or 5 = the days of a first-class match (logic/TestMatch.kt). */
    val testDays: Int = 0
)

/**
 * Which rules a tournament plays by: looked up by tournament id, so the same answer is used when the player's
 * match is played, when the others are simulated, and for net run rate.
 *
 *  - A custom tournament (id "ctour_...") plays its chosen format with its own DRS and rain settings.
 *  - A game tournament the player gave another format in Edit Tournament plays that format.
 *  - Anything else has no rules object: the game's own format rules apply exactly as before.
 */
object TournamentRules {
    const val CUSTOM_PREFIX = "ctour_"

    fun isCustom(id: String?): Boolean = id != null && id.startsWith(CUSTOM_PREFIX)

    /** League and group matches may end tied; everything after them (semi-finals, qualifiers, the Final...) must be settled. */
    fun isLeagueStage(stage: String?): Boolean = stage == CplData.LEAGUE || (stage != null && stage.startsWith("Group "))

    private fun builtIn(key: String): MatchFormat? = when (key) {
        "T20" -> MatchFormat.T20
        "ODI" -> MatchFormat.ODI
        "T10" -> MatchFormat.T10
        "FIVE_OVERS" -> MatchFormat.FIVE_OVERS
        "ONE_OVER" -> MatchFormat.ONE_OVER
        else -> null
    }

    /** The game format whose built-in constants (powerplay timing, caught-and-bowled odds...) suit this many overs best. */
    fun baseFormatForOvers(overs: Int): MatchFormat = when {
        overs >= 40 -> MatchFormat.ODI
        overs >= 15 -> MatchFormat.T20
        overs >= 8 -> MatchFormat.T10
        overs >= 3 -> MatchFormat.FIVE_OVERS
        else -> MatchFormat.ONE_OVER
    }

    /** The rules of a format key ("T20", "ODI"... or "custom:<id>"), with DRS on at its usual number of reviews and rain on. */
    fun rulesForFormat(key: String): MatchRules? {
        val format = builtIn(key)
        if (format != null) {
            return MatchRules(
                overs = MatchEngine.getFormatOvers(format),
                restrictionBalls = FieldingSystem.powerplayBalls(format),
                fieldersOutside = FieldingSystem.MAX_DEEP_FIELDERS_POWERPLAY,
                drsReviews = DrsSystem.reviewsPerInnings(format),
                rainEnabled = true,
                maxOversPerBowler = MatchStateMachine.getMaxOversPerBowler(format)
            )
        }
        if (key.startsWith("custom:")) {
            val id = key.removePrefix("custom:")
            val cf = Customisation.customFormats().find { it.id == id } ?: return null
            return MatchRules(
                overs = cf.overs,
                restrictionBalls = if (cf.restrictionsEnabled) cf.restrictionOvers * 6 else 0,
                fieldersOutside = cf.fieldersOutside,
                drsReviews = DrsSystem.reviewsPerInnings(baseFormatForOvers(cf.overs)),
                rainEnabled = true,
                maxOversPerBowler = cf.maxOversPerBowler
            )
        }
        return null
    }

    fun rulesFor(tournamentId: String?, stage: String? = null): MatchRules? {
        if (tournamentId == null) return null
        // The Ranji Trophy: first-class matches (four days, five in the knockouts), no DRS, no rain.
        DomesticFc.rulesFor(tournamentId, stage)?.let { return it }
        if (isCustom(tournamentId)) {
            val def = Customisation.customTournaments().find { it.id == tournamentId } ?: return null
            val base = rulesForFormat(def.formatKey) ?: return null
            return base.copy(
                drsReviews = if (def.drsEnabled) def.drsReviews else 0,
                rainEnabled = def.rainEnabled,
                noSuperOver = def.noSuperOver && isLeagueStage(stage)
            )
        }
        val key = Customisation.tournamentEdit(tournamentId)?.formatKey ?: return null
        return rulesForFormat(key)
    }

    /** The game format to play the tournament as (null = the tournament's own format, unchanged). */
    fun baseFormatFor(tournamentId: String?): MatchFormat? = rulesFor(tournamentId)?.let { baseFormatForOvers(it.overs) }

    /** Balls in a full innings, for net run rate (null = unchanged). */
    fun ballsFor(tournamentId: String?): Int? = rulesFor(tournamentId)?.let { it.overs * 6 }
}

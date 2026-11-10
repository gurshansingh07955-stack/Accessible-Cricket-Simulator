package com.cricketsim.logic

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * What a match does to the player: his form (the impact points of his last matches) and his ratings. A player who is in the XI grows
 * a little after every match, more after a good one and much more when he is young; a player who is not picked does not grow.
 * Growth is shared between batting, bowling and fielding by his role.
 *
 * Dials: [BASE_GROWTH] (a match of 20 impact points gives about this many rating points to a sixteen-year-old), [IMPACT_PAR]
 * (the impact of an ordinary match).
 */
object CareerProgress {
    const val BASE_GROWTH = 1.0
    const val IMPACT_PAR = 20.0

    /** The training focus: the skill he works on grows faster (x1.6), the other two a little slower (x0.85); no focus changes nothing. */
    private fun focusFactor(career: CareerProfile, skill: String): Double = when (career.trainingFocus) {
        null, "" -> 1.0
        skill -> 1.6
        else -> 0.85
    }

    class Result(val career: CareerProfile, val text: String)

    private fun gain(g: Double, random: Random): Int {
        val whole = floor(g).toInt()
        return whole + if (random.nextDouble() < g - whole) 1 else 0
    }

    /** [before] and [after] are the player's tournament statistics around the match; they differ only if he played. */
    fun afterMatch(career: CareerProfile, before: PlayerStats?, after: PlayerStats?, random: Random, format: String = "", league: Boolean = false): Result {
        val played = (after?.matches ?: 0) > (before?.matches ?: 0)
        if (after == null || !played) return CareerHealth.missedMatch(career)
        fun d(f: (PlayerStats) -> Int) = f(after) - (before?.let(f) ?: 0)
        val runs = d { it.runs }
        val balls = d { it.balls }
        val wickets = d { it.wickets }
        val conceded = d { it.runsConceded }
        val bowled = d { it.ballsBowled }
        val catches = d { it.catches }
        val impact = after.impact - (before?.impact ?: 0.0)

        val parts = ArrayList<String>()
        if (balls > 0) parts.add("You scored $runs off $balls balls.")
        if (bowled > 0) parts.add("You took $wickets wicket${if (wickets == 1) "" else "s"} for $conceded in ${bowled / 6}.${bowled % 6} overs.")
        if (catches > 0) parts.add("You took $catches catch${if (catches == 1) "" else "es"}.")
        if (parts.isEmpty()) parts.add("You did not bat or bowl.")

        // Growth: more after a good match, more for the young (a sixteen-year-old grows at full speed, a thirty-year-old barely).
        val score = ((impact - IMPACT_PAR) / 40.0).coerceIn(-0.5, 1.5)
        val ageFactor = (31 - career.age).coerceIn(1, 15) / 15.0
        val g = (0.5 + score).coerceAtLeast(0.1) * ageFactor * BASE_GROWTH
        val (batShare, bowlShare) = when (career.role) {
            "Batsman", "Wicketkeeper-batsman" -> 1.0 to 0.08
            "Bowler" -> 0.15 to 1.0
            "Batting all-rounder" -> 0.65 to 0.45
            else -> 0.45 to 0.65
        }
        val batGain = gain(g * batShare * focusFactor(career, "Batting"), random)
        val bowlGain = gain(g * bowlShare * focusFactor(career, "Bowling"), random)
        val fieldGain = gain((g * 0.2 + catches * 0.3) * focusFactor(career, "Fielding"), random)
        val newBat = (career.batting + batGain).coerceAtMost(95)
        val newBowl = (career.bowling + bowlGain).coerceAtMost(95)
        val newField = (career.fielding + fieldGain).coerceAtMost(95)

        val changes = listOfNotNull(
            if (newBat > career.batting) "batting +${newBat - career.batting}" else null,
            if (newBowl > career.bowling) "bowling +${newBowl - career.bowling}" else null,
            if (newField > career.fielding) "fielding +${newField - career.fielding}" else null
        )
        val growth = if (changes.isEmpty()) "Your ratings did not change." else "Your ratings grew: ${changes.joinToString(", ")}."

        val clubs = career.clubs.map { club ->
            if (club.name != career.club) club
            else club.copy(players = club.players.map { if (it.isUser) it.copy(batting = newBat, bowling = newBowl, fielding = newField) else it })
        }
        val played2 = career.copy(
            batting = newBat, bowling = newBowl, fielding = newField,
            clubTeams = clubs,
            matchesPlayed = career.matchesPlayed + 1,
            workload = career.workload + 1,
            money = career.money + CareerFinance.matchFee(career, format, league),
            seasonEarnings = career.seasonEarnings + CareerFinance.matchFee(career, format, league),
            stateMatches = if (career.isState && !league) career.stateMatches + 1 else career.stateMatches,
            internationalMatches = if (career.isInternational && !league) career.internationalMatches + 1 else career.internationalMatches,
            leagueMatches = if (league) career.leagueMatches + 1 else career.leagueMatches,
            records = CareerRecords.updated(career, before, after, format, league),
            form = (career.form.orEmpty() + impact).takeLast(5),
            formByFormat = if (format.isEmpty()) career.formByFormat
            else career.formByFormat.orEmpty() + (format to (career.formByFormat?.get(format).orEmpty() + impact).takeLast(5))
        )
        // A run of good matches is rewarded with the club's leadership.
        val rank = CareerLeadership.promotion(played2)
        val updated = if (rank != null) played2.copy(leadership = rank, leadershipSince = played2.matchesPlayed) else played2
        val news = if (rank != null) " " + CareerLeadership.announcement(rank, career.club) else ""
        // A chance of an injury after the match (more in Tests, for bowlers, for the old and the tired).
        val hurt = CareerHealth.roll(updated, random, format)
        val afterHurt = if (hurt == null) updated else updated.copy(injuredMatches = hurt.matches, injury = hurt.name)
        val hurtText = if (hurt == null) "" else " " + CareerHealth.injuryAnnouncement(hurt)
        return Result(afterHurt, "${parts.joinToString(" ")} Your impact was ${impact.roundToInt()}. $growth$news$hurtText")
    }
}

package com.cricketsim.logic

import java.util.Random

/**
 * My Player, part twenty-six: school and college.
 *
 * A NEW career whose age is 12 to 15 starts in SCHOOL cricket, one of 16 to 18 in COLLEGE cricket; from 19 it starts in district club cricket exactly as it always
 * did. (A career made before this part has no stage and is not touched.) A student plays the same three leagues as a club player (T20, 50-over, 3-day), but between the
 * THREE schools (or three colleges) of his state, each pair twice (home and away), so a season is 12 matches. His district club, chosen on the club page, waits for him:
 *  - school lasts until he is 15: the season after that he finishes school and is asked to choose one of the state's three colleges;
 *  - college lasts until he is 18: the season after that he finishes college and joins his district club (a squad of adults, made when he arrives);
 *  - the state selectors and the franchises look at him only once he plays district club cricket (from 19).
 *
 * RATINGS follow age. The rating he starts with is multiplied by [ageFactor] (a 12-year-old 0.55, a 15-year-old 0.78, an 18-year-old 0.96, 19 and over 1.0) and the made-up
 * squads of the schools by [squadScale] (0.66) and of the colleges (0.90). He then grows as he plays, as before (a young player grows at full speed).
 * The scale-down of school and college ratings is a model: how it changes the scoring of the simulated matches was not measured.
 */
object CareerStudent {
    const val SCHOOL_MAX_AGE = 15
    const val COLLEGE_MAX_AGE = 18

    fun stageForAge(age: Int): String = when {
        age in CareerWorld.MIN_AGE..SCHOOL_MAX_AGE -> "School"
        age in (SCHOOL_MAX_AGE + 1)..COLLEGE_MAX_AGE -> "College"
        else -> ""
    }

    /** What a starting rating is multiplied by at this age (1.0 from the age of 19). */
    fun ageFactor(age: Int): Double = when {
        age <= 12 -> 0.55
        age == 13 -> 0.62
        age == 14 -> 0.70
        age == 15 -> 0.78
        age == 16 -> 0.85
        age == 17 -> 0.91
        age == 18 -> 0.96
        else -> 1.0
    }

    /** What the ratings of the made-up players of a school or a college are multiplied by. */
    fun squadScale(stage: String): Double = when (stage) {
        "School" -> 0.66
        "College" -> 0.90
        else -> 1.0
    }

    /** The player's starting ratings [batting, bowling, fielding]: as always from 19, scaled by his age below it. */
    fun startingRatings(role: String, age: Int, random: Random): List<Int> {
        val base = CareerGenerator.startingRatings(role, random)
        if (stageForAge(age).isEmpty()) return base
        val f = ageFactor(age)
        return base.map { maxOf(3, Math.round(it * f).toInt()) }
    }

    /** The made-up teams of a school or college league: the three of the state, the player in his own team's squad. */
    fun generateTeams(country: String, state: String, stage: String, userTeam: String, user: CareerPlayer, random: Random): List<CareerClub> {
        val names = if (stage == "School") CareerWorld.schools(country, state) else CareerWorld.colleges(country, state)
        return CareerGenerator.generateClubs(country, state, userTeam, user, random, names, squadScale(stage))
    }

    private fun playerOf(c: CareerProfile): CareerPlayer = CareerPlayer(
        name = c.fullName, role = c.role, battingHand = c.battingHand, bowlingHand = c.bowlingHand, bowlingStyle = c.bowlingStyle,
        batting = c.batting, bowling = c.bowling, fielding = c.fielding, isUser = true
    )

    /** He joins a college (with his ratings as they are now): its league, its squads. */
    fun joinCollege(c: CareerProfile, college: String, random: Random): CareerProfile {
        val clubs = generateTeams(c.country, c.state, "College", college, playerOf(c), random)
        return c.copy(stage = "College", club = college, clubTeams = clubs, leadership = "", level = "District")
    }

    /** He finishes college and joins his district club: a squad of adults, made now, with him in it. */
    private fun graduate(c: CareerProfile, random: Random): CareerProfile {
        val names = CareerWorld.clubs(c.country, c.state)
        val club = if (c.homeClub in names) c.homeClub else names.firstOrNull() ?: c.club
        val clubs = CareerGenerator.generateClubs(c.country, c.state, club, playerOf(c), random)
        return c.copy(stage = "", homeClub = "", club = club, clubTeams = clubs, leadership = "", level = "District")
    }

    /** The next season of a student ([older] is the career a year older): school, college or the district club. */
    fun next(career: CareerProfile, older: CareerProfile): CareerSeason.NextSeason {
        val random = Random(career.id.hashCode().toLong() * 31L + older.season)
        var text = "A new season begins. You are ${older.age}. "
        var c = older.copy(level = "District")
        if (career.stage == "School" && older.age > SCHOOL_MAX_AGE) {
            // He finishes school. He is put in the first college until he chooses (the hub asks at once and rebuilds the season).
            val colleges = CareerWorld.colleges(c.country, c.state)
            c = joinCollege(c, colleges.firstOrNull() ?: c.club, random).copy(pendingCollege = true)
            text += "You finish school and move on to college. Choose your college."
        } else if (career.stage == "College" && older.age > COLLEGE_MAX_AGE) {
            c = graduate(c, random)
            text += "You finish college and join ${c.club}, your district club."
        } else {
            text += "You stay at ${c.club} for another season of ${career.stage.lowercase()} cricket."
        }
        val states = CareerSeason.newSeason(c) ?: emptyList()
        val keys = states.map { CareerSeason.keyOf(it) }
        return CareerSeason.NextSeason(c.copy(seasonKeys = keys), states, keys, text)
    }
}

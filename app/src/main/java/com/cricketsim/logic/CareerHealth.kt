package com.cricketsim.logic

import kotlin.random.Random

/**
 * My Player, part twelve: injuries, tiredness, ageing, retirement and the career summary.
 *
 * INJURIES. After every match he plays there is a small chance of an injury ([roll]): about 1.2% a match, more in Test cricket (x1.8) and one-day cricket (x1.2),
 * more for bowlers (x1.4) and all-rounders (x1.15), more after thirty (6% for every year), and more when he is tired (the workload counts the matches of
 * the season: +1/60 for every match). A minor injury keeps him out for two or three matches, a moderate one for four to eight, a serious one for ten to eighteen.
 * While injured he is not selected ([decision]); every match his side plays without him counts off one ([missedMatch]); between seasons he rests six matches.
 *
 * AGEING. When a season ends, from the age of 33 his batting and bowling fall by (age - 32) / 3 points a season (one at 35, two at 38, three at 41) and his fielding by half that.
 *
 * RETIREMENT. At the end of a season from the age of 36 he may retire: a 12% chance at 36 that grows by 12% a year (60% at 40, certain at 44), or 50% from 33 if his main
 * rating has fallen under 40. The chance is drawn from the career and the season, so it cannot be re-rolled by reopening the game. He can retire himself when a season is
 * finished (Career tab). A retired career keeps its Career tab with a [summaryLines] summary and cannot start a new season.
 */
object CareerHealth {

    class Injury(val name: String, val matches: Int)

    private val MINOR = listOf("a bruised hand", "a tight hamstring", "a sore shoulder", "a twisted ankle")
    private val MODERATE = listOf("a hamstring strain", "a side strain", "a fractured finger", "an ankle sprain", "a groin strain")
    private val SERIOUS = listOf("a stress fracture of the back", "a knee ligament injury", "a shoulder dislocation", "a torn hamstring")

    /** The injury he picks up in a match he has just played, or null. */
    fun roll(career: CareerProfile, random: Random, format: String): Injury? {
        if (career.injuredMatches > 0 || career.retired) return null
        var p = 0.012
        p *= when (format) {
            "test" -> 1.8
            "odi" -> 1.2
            else -> 1.0
        }
        p *= when (career.role) {
            "Bowler" -> 1.4
            "Batting all-rounder", "Bowling all-rounder" -> 1.15
            else -> 1.0
        }
        if (career.age >= 30) p *= 1.0 + (career.age - 29) * 0.06
        p *= 1.0 + career.workload / 60.0
        if (random.nextDouble() >= p.coerceAtMost(0.10)) return null
        val r = random.nextDouble()
        return when {
            r < 0.6 -> Injury(MINOR.random(random), 2 + random.nextInt(2))
            r < 0.9 -> Injury(MODERATE.random(random), 4 + random.nextInt(5))
            else -> Injury(SERIOUS.random(random), 10 + random.nextInt(9))
        }
    }

    fun injuryAnnouncement(h: Injury): String = "You picked up ${h.name} and will miss the next ${h.matches} matches."

    /** The selectors' decision while he is injured (the hub adds "You can simulate the match instead."). */
    fun decision(career: CareerProfile): CareerSelection.Decision {
        val n = career.injuredMatches
        return CareerSelection.Decision(
            false, 1, 11, 15, 0,
            "You are injured with ${career.injury ?: "an injury"} and will miss $n more ${if (n == 1) "match" else "matches"}."
        )
    }

    /** What a match his side plays without him does: counts off an injury, or says he was not in the XI. */
    fun missedMatch(career: CareerProfile): CareerProgress.Result {
        if (career.injuredMatches <= 0) return CareerProgress.Result(career, "You were not in the XI for this match.")
        val left = career.injuredMatches - 1
        val updated = career.copy(injuredMatches = left, injury = if (left == 0) null else career.injury)
        val text = if (left == 0) "You missed the match injured (${career.injury ?: "an injury"}). You have recovered and are fit again."
        else "You missed the match injured. $left more ${if (left == 1) "match" else "matches"} to miss."
        return CareerProgress.Result(updated, text)
    }

    /** A sentence about his fitness for the Home tab, or null when there is nothing to say. */
    fun statusLine(career: CareerProfile): String? = when {
        career.injuredMatches > 0 -> "Fitness: injured with ${career.injury ?: "an injury"}, ${career.injuredMatches} more matches to miss."
        career.workload >= 25 -> "Fitness: you are tired after ${career.workload} matches this season, which raises your risk of injury."
        else -> null
    }

    /** His ratings after a season of ageing (nothing before 33). */
    fun aged(career: CareerProfile): CareerProfile {
        val d = ((career.age - 32) / 3).coerceAtLeast(0)
        if (d == 0) return career
        val bat = (career.batting - d).coerceAtLeast(1)
        val bowl = (career.bowling - d).coerceAtLeast(1)
        val field = (career.fielding - (d + 1) / 2).coerceAtLeast(1)
        val clubs = career.clubs.map { club ->
            if (club.name != career.club) club
            else club.copy(players = club.players.map { if (it.isUser) it.copy(batting = bat, bowling = bowl, fielding = field) else it })
        }
        return career.copy(batting = bat, bowling = bowl, fielding = field, clubTeams = clubs)
    }

    /** The career with the retirement decision of the season made (retired or not). */
    fun retirementDecision(career: CareerProfile): CareerProfile {
        if (career.retired) return career
        val main = when (career.role) {
            "Bowler" -> career.bowling
            "Batsman", "Wicketkeeper-batsman" -> career.batting
            else -> maxOf(career.batting, career.bowling)
        }
        val chance = when {
            career.age >= 44 -> 1.0
            career.age >= 36 -> minOf(1.0, (career.age - 35) * 0.12)
            career.age >= 33 && main < 40 -> 0.5
            else -> 0.0
        }
        val random = Random(career.id.hashCode() * 17 + career.season)
        return if (chance > 0.0 && random.nextDouble() < chance) career.copy(retired = true, retiredSeason = career.season) else career
    }

    fun retirementText(career: CareerProfile): String =
        "At ${career.age} you announce your retirement from cricket. Your career summary is on the Career tab."

    /** The career in a few sentences, for a retired player. */
    fun summaryLines(career: CareerProfile): List<String> {
        val seasons = maxOf(1, career.season)
        val startAge = career.age - (seasons - 1)
        val out = ArrayList<String>()
        out.add("You played $seasons ${if (seasons == 1) "season" else "seasons"} of cricket, from the age of $startAge to ${career.age}.")
        val highest = when {
            career.internationalMatches > 0 -> "international cricket for ${career.country}, ${career.internationalMatches} matches"
            career.stateMatches > 0 -> "state cricket for ${career.state}, ${career.stateMatches} matches"
            else -> "district club cricket for ${career.club}"
        }
        out.add("The highest level you reached: $highest.")
        if (career.leagueMatches > 0) out.add("You also played ${career.leagueMatches} franchise league matches.")
        out.addAll(CareerRecords.recordLines(career))
        val honours = career.honours.orEmpty()
        out.add(if (honours.isEmpty()) "You won no honours." else "You won ${honours.size} ${if (honours.size == 1) "honour" else "honours"}.")
        out.add("Your final ratings: batting ${career.batting}, bowling ${career.bowling}, fielding ${career.fielding}.")
        val recs = career.records.orEmpty().values
        val points = career.internationalMatches * 2.0 + career.leagueMatches + career.stateMatches * 0.5 + honours.size * 5.0 +
            recs.sumOf { it.runs } / 100.0 + recs.sumOf { it.wickets } / 10.0
        out.add(
            when {
                points >= 300 -> "A true great of the game: your name will be remembered."
                points >= 150 -> "A career to be proud of, at the top level."
                points >= 60 -> "A solid, respected career."
                else -> "A short but honest career."
            }
        )
        return out
    }
}

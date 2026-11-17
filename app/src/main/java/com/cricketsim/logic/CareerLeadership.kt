package com.cricketsim.logic

import java.util.Locale

/**
 * Promotion to the leadership of the club, of the state squad. A player who keeps scoring runs and taking wickets is made VICE-CAPTAIN, and a vice-captain who keeps doing it is made CAPTAIN.
 * Judged on the average impact of his last five matches (the game's Player of the Match points; about 25 is an ordinary match in the XI):
 *  - club (also a school or a college): vice-captain at least five matches played and an average of 28 or more; captain five more matches as vice-captain and an average of 32 or more;
 *  - state squad (part twenty-seven): vice-captain at least five state matches and an average of 30 or more; captain five more state matches and an average of 34 or more.
 * The harder the career difficulty, the more the selectors ask (the averages are scaled from 0.92 on Very easy to 1.12 on Legend).
 * The captain makes the team decisions in his matches (the XI, the toss, the bowlers, the field, the batting order); the vice-captain plays as a player.
 */
object CareerLeadership {
    const val VICE = "Vice-captain"
    const val CAPTAIN = "Captain"

    /** The new rank if the player has earned one, else null. */
    fun promotion(career: CareerProfile): String? {
        if (career.isState || career.isInternational) return null // the club's captaincy is a district matter
        val form = career.form.orEmpty().takeLast(5)
        if (form.size < 5) return null
        val average = form.average()
        val need = 0.8 + 0.2 * CareerWorld.selectionFactor(career.difficulty)
        return when (career.leadership) {
            "" -> if (career.matchesPlayed >= 5 && average >= 28.0 * need) VICE else null
            VICE -> if (career.matchesPlayed - career.leadershipSince >= 5 && average >= 32.0 * need) CAPTAIN else null
            else -> null
        }
    }

    fun announcement(rank: String, club: String): String = when (rank) {
        CAPTAIN -> "Big news: you have been made captain of $club! From now on you pick the XI, take the toss, choose the bowlers, set the field and decide the batting order."
        else -> "Good news: after your performances you have been made vice-captain of $club."
    }

    // ------------------------------------------------------------ the state squad

    private fun need(career: CareerProfile): Double = 0.8 + 0.2 * CareerWorld.selectionFactor(career.difficulty)

    /** The new rank in the state squad if he has earned one (only while he plays for the state), else null. */
    fun statePromotion(career: CareerProfile): String? {
        if (!career.isState) return null
        val form = career.form.orEmpty().takeLast(5)
        if (form.size < 5) return null
        val average = form.average()
        val need = need(career)
        return when (career.stateRole) {
            "" -> if (career.stateMatches >= 5 && average >= 30.0 * need) VICE else null
            VICE -> if (career.stateMatches - career.stateLeadershipSince >= 5 && average >= 34.0 * need) CAPTAIN else null
            else -> null
        }
    }

    fun stateAnnouncement(rank: String, team: String): String = when (rank) {
        CAPTAIN -> "Big news: you have been made captain of $team! In the state's matches you now pick the XI, take the toss, choose the bowlers, set the field and decide the batting order."
        else -> "Good news: after your performances you have been made vice-captain of $team."
    }

    // ------------------------------------------------------------ the Captaincy page

    private fun one(x: Double): String = String.format(Locale.US, "%.1f", x)

    /** How far he is from the next rank: the matches it takes and the form the selectors read. */
    private fun progress(next: String, played: Int, form: List<Double>, target: Double): String {
        val matches = "You have played ${played.coerceIn(0, 5)} of the 5 matches it takes."
        val formText = if (form.size < 5) "Your form is read from your last five matches, and you have played ${form.size}."
        else "Your average impact over your last five matches is ${one(form.average())}; the selectors want about ${one(target)}."
        return "To be made $next: $matches $formText"
    }

    private fun clubLines(career: CareerProfile): List<String> {
        val title = when (career.stage) { "School" -> "School captaincy"; "College" -> "College captaincy"; else -> "Club captaincy" }
        val place = when (career.stage) { "School" -> "school"; "College" -> "college"; else -> "club" }
        val form = career.form.orEmpty().takeLast(5)
        val need = need(career)
        val away = if (career.atOrAboveState) " You are not playing $place cricket this season; you lead again when you do." else ""
        return when (career.leadership) {
            CAPTAIN -> listOf("$title: you are the captain of ${career.club}. In its matches you pick the XI, do the toss, choose the bowlers, set the field and decide the batting order.$away")
            VICE -> listOf(
                "$title: you are the vice-captain of ${career.club}. The vice-captain plays as a player; the captain makes the team decisions.$away",
                progress("captain", career.matchesPlayed - career.leadershipSince, form, 32.0 * need)
            )
            else -> listOf(
                "$title: you are not a captain yet. A player who keeps playing well is made vice-captain, then captain.",
                progress("vice-captain", career.matchesPlayed, form, 28.0 * need)
            )
        }
    }

    private fun stateLines(career: CareerProfile): List<String> {
        if (career.stage.isNotEmpty()) return listOf("State captaincy: the state selectors look at you once you play district club cricket, from the age of 19.")
        val team = career.domesticTeams.firstOrNull() ?: career.state
        val away = if (career.isState) "" else " You are not in the state squad this season; you lead it again if you are called up."
        val form = career.form.orEmpty().takeLast(5)
        val need = need(career)
        return when (career.stateRole) {
            CAPTAIN -> listOf("State captaincy: you are the captain of $team. In the state's matches you pick the XI, do the toss, choose the bowlers, set the field and decide the batting order.$away")
            VICE -> listOf(
                "State captaincy: you are the vice-captain of $team. The vice-captain plays as a player; the captain makes the team decisions.$away",
                progress("state captain", career.stateMatches - career.stateLeadershipSince, form, 34.0 * need)
            )
            else -> if (career.isState) listOf(
                "State captaincy: you are not a captain yet. A state player who keeps playing well is made vice-captain, then captain.",
                progress("state vice-captain", career.stateMatches, form, 30.0 * need)
            ) else listOf("State captaincy: it comes after you have played for the $team state squad.")
        }
    }

    /** Every captaincy he can hold, one or two sentences each: school, college or club; the state squad; the nation; a franchise. */
    fun lines(career: CareerProfile): List<String> {
        val out = ArrayList<String>()
        out.addAll(clubLines(career))
        out.addAll(stateLines(career))
        out.add(if (career.isInternational) CareerInternational.captaincyLine(career) else "National captaincy: it comes after you have played for your country.")
        out.add(
            if (career.isLeagueCaptain) "Franchise captaincy: you are the captain of a franchise this season. You pick the XI and do the toss in its matches."
            else "Franchise captaincy: after ${CareerLeagues.CAPTAIN_MATCHES} league matches a franchise may offer you its captaincy with its contract, if your form and your standing in its squad are good. You have played ${career.leagueMatches} league matches."
        )
        return out
    }
}

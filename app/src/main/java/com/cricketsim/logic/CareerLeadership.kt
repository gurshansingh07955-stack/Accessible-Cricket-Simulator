package com.cricketsim.logic

/**
 * Promotion to the club's leadership. A player who keeps scoring runs and taking wickets is made VICE-CAPTAIN, and a vice-captain who keeps doing it is made CAPTAIN.
 * Judged on the average impact of his last five matches (the game's Player of the Match points; about 25 is an ordinary match in the XI):
 *  - vice-captain: at least five matches played and an average of 28 or more;
 *  - captain: five more matches as vice-captain and an average of 32 or more.
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
}

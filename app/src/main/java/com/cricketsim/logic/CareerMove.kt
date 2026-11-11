package com.cricketsim.logic

import java.util.Random

/** A move to a district club in another country, planned for the start of the next season. */
data class MoveRequest(val country: String, val state: String, val club: String)

/**
 * My Player, part sixteen (second version): moving to another country. A player who has not played an international match can plan a move to a district club of
 * any of the countries that have domestic cricket in the game ([CareerWorld.COUNTRIES]). It happens when the next season starts ([apply]): he starts again at the district level of
 * the new club (made-up squads are generated for the new state, as when a career starts), keeping his ratings, age, money, honours, record and form. His state and club captaincy
 * start over, his franchise ties and his chances of league offers from the old country's links are cleared, and the national selectors of the new country want him to have lived there
 * for [CareerInternational.RESIDENCY_SEASONS] seasons and to have played their state matches ([CareerProfile.movedSeason]).
 */
object CareerMove {

    /** The career after a planned move, or unchanged if no move is planned. [seed] makes up the new state's clubs. */
    fun apply(career: CareerProfile, seed: Long): CareerProfile {
        val move = career.pendingMove ?: return career
        val state = CareerWorld.findState(move.country, move.state) ?: return career.copy(pendingMove = null)
        val me = CareerPlayer(
            name = career.fullName,
            role = career.role,
            battingHand = career.battingHand,
            bowlingHand = career.bowlingHand,
            bowlingStyle = career.bowlingStyle,
            batting = career.batting,
            bowling = career.bowling,
            fielding = career.fielding,
            isUser = true
        )
        val clubs = CareerGenerator.generateClubs(move.country, move.state, move.club, me, Random(seed))
        return career.copy(
            country = move.country,
            state = move.state,
            domesticTeams = state.domesticTeams,
            club = move.club,
            clubTeams = clubs,
            level = "",
            stateMatches = 0,
            leadership = "",
            leadershipSince = career.matchesPlayed,
            represents = null,
            nationalRole = null,
            leagueOffers = null,
            lastLeagueId = null,
            lastLeagueTeamId = null,
            pendingMove = null,
            movedSeason = maxOf(1, career.season) + 1
        )
    }

    /** A sentence for the new-season message when a move has just happened. */
    fun text(before: CareerProfile, after: CareerProfile): String =
        if (after.club != before.club || after.country != before.country) "You have moved to ${after.club}, ${after.state}, ${after.country}, and start again at district level. " else ""
}

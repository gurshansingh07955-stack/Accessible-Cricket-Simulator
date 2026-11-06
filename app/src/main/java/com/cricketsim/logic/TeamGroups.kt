package com.cricketsim.logic

/**
 * The squads the Edit teams and custom-tournament team pickers list besides the national and league teams:
 * the real international tournaments' squads, and every domestic competition's teams. Building them is slow (each one
 * looks the players up across every league), so the result is kept until the player changes something
 * ([Customisation.revision] moves on).
 */
object TeamGroups {
    private class Kept(val revision: Int, val groups: List<Pair<String, List<Team>>>)

    @Volatile private var tournament: Kept? = null
    @Volatile private var domestic: Kept? = null

    /** Each international tournament's name and its squads (the national team's id, the tournament's players). */
    fun tournamentGroups(): List<Pair<String, List<Team>>> {
        val rev = Customisation.revision
        tournament?.takeIf { it.revision == rev }?.let { return it.groups }
        val built = listOf(
            T20WorldCup.TOURNAMENT_NAME to T20WorldCup.buildTeams().first,
            ChampionsTrophy.TOURNAMENT_NAME to ChampionsTrophy.buildTeams().first,
            AsiaCup.TOURNAMENT_NAME to AsiaCup.buildTeams().first,
            AsianGames.TOURNAMENT_NAME to AsianGames.buildTeams().first
        )
        tournament = Kept(rev, built)
        return built
    }

    /**
     * Every domestic competition's teams, by country. The Syed Mushtaq Ali and Vijay Hazare Trophies use the same state teams
     * as the Ranji Trophy, and the Pakistani Gold and Silver tournaments the same teams, so each set is listed once.
     */
    fun domesticGroups(): List<Pair<String, List<Team>>> {
        val rev = Customisation.revision
        domestic?.takeIf { it.revision == rev }?.let { return it.groups }
        val built = listOf(
            "India: state teams" to Ranji.buildTeams().first,
            "India: Duleep Trophy zones" to DomesticFc.buildDuleep().first,
            "India: Irani Cup teams" to DomesticFc.buildIrani().first,
            "England: counties" to CountyCricket.buildTeams().first,
            "Australia: state teams" to AusDomestic.buildTeams().first,
            "South Africa: franchises" to SaDomestic.buildTeams().first,
            "New Zealand: major associations" to NzDomestic.buildTeams().first,
            "West Indies: territories" to WiDomestic.buildTeams().first,
            "Pakistan: domestic teams" to PakDomestic.buildTeams().first,
            "Bangladesh: divisions" to BdDomestic.buildTeams().first
        )
        domestic = Kept(rev, built)
        return built
    }
}

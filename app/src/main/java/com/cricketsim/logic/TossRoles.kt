package com.cricketsim.logic

/**
 * Who calls the coin and who flips it. At a ground that is one side's home (and not the other's) the HOME side
 * calls heads or tails and the visitors flip; at a neutral ground it is settled by chance (50-50).
 * Set HOME_TEAM_CALLS to false for the real-cricket custom instead (the home captain flips, the visitors call).
 */
object TossRoles {
    const val HOME_TEAM_CALLS = true

    /**
     * In Career Mode: true = the home/away mark of the fixture decides who calls (the old way: the player's team was marked away most of the time,
     * so he almost always only flipped the coin); false = chance decides, 50-50, each match.
     */
    const val CAREER_HOME_CALLS = false

    /** true = the user calls heads or tails (the other captain flips); false = the user flips (the other captain calls); null = neutral. */
    fun userCalls(userHome: Boolean, opponentHome: Boolean): Boolean? = when {
        userHome && !opponentHome -> HOME_TEAM_CALLS
        opponentHome && !userHome -> !HOME_TEAM_CALLS
        else -> null
    }

    fun userCalls(user: Team, opponent: Team, stadium: Stadium): Boolean? =
        userCalls(user.id in stadium.homeTeamIds, opponent.id in stadium.homeTeamIds)
}

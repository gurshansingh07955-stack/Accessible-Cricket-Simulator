package com.cricketsim.logic

/**
 * Every ground the game has, for the stadium picker of a custom tournament: the base grounds in [StadiumData] plus the
 * grounds each tournament added for itself (the IPL's, the domestic competitions', the international tournaments'...).
 * One entry per id.
 */
object AllStadiums {
    fun list(): List<Stadium> {
        val out = LinkedHashMap<String, Stadium>()
        fun add(grounds: List<Stadium>) {
            for (g in grounds) out.putIfAbsent(g.id, g)
        }
        add(StadiumData.getAllStadiums())
        add(CplData.stadiums)
        add(IplSeason.stadiums)
        add(AbuDhabiT10.stadiums)
        add(LankaPremierLeague.stadiums)
        add(InternationalLeagueT20.stadiums)
        add(BigBashLeague.stadiums)
        add(MajorLeagueCricket.stadiums)
        add(Sa20.stadiums)
        add(T20WorldCup.stadiums)
        add(ChampionsTrophy.stadiums)
        add(AsiaCup.stadiums)
        add(AsianGames.stadiums)
        add(Ranji.stadiums)
        add(DomesticFc.stadiumsFor(DomesticFc.DULEEP_ID))
        add(DomesticFc.stadiumsFor(DomesticFc.IRANI_ID))
        add(CountyCricket.stadiums)
        add(AusDomestic.stadiums)
        add(SaDomestic.stadiums)
        add(NzDomestic.stadiums)
        add(WiDomestic.stadiums)
        add(PakDomestic.stadiums)
        add(BdDomestic.stadiums)
        return out.values.toList()
    }
}

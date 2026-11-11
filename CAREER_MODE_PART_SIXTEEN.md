# My Player, part sixteen: international affiliation, retiring veterans, ageing domestic squads

**International affiliation** (Career tab, "International affiliation", for a player who has not played an international match). He can aim for the national team of another of the ten Test nations (`CareerProfile.represents`, `CareerProfile.nation`).
The national selectors, the series, the tournaments, the leagues' rules (an Indian player may only play the IPL, a Pakistani player not the IPL, overseas or home in a league) and the captaincy then all use that nation. Because of the residency period the other country's selectors want
twice as many state matches (16 instead of 8: `CareerInternational.neededMatches`). Switching resets his national captaincy; it can be changed until his first international match; choosing his own country again puts it back.

**Retiring veterans** (`SquadEvolution`). The computer's squads have no ages, so each player gets one from his name (24 to 37). When his age reaches 38 in the career's timeline he retires and a young player of the same role and bowling style takes his place, with a name from the country's list
(ten countries; franchise squads mix the lists) and ratings of 0.72 of the veteran's plus 14. The same veteran always has the same successor. This applies wherever `CareerInternational.evolved` is used: the series and tournaments, the leagues, the squads the selectors compare him with, the rankings and the state squads.

**Ageing domestic squads.** The state teams of the player's country now age and renew the same way (`CareerDomestic.newSeason` and `stateSquad`, with the country's names for the successors). The district club leagues are made up for each career and are not aged.

**Dials.** `SquadEvolution.RETIRE_AGE`, the rating formula in `successor`, `MIN_STATE_MATCHES` (the residency period is twice it).

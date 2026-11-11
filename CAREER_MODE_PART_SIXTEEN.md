# My Player, part sixteen: moving to another country, retiring veterans, ageing domestic squads

**Moving to another country** (Career tab, "Move to another country"; for a player who has not played an international match). The game has national teams for ten countries but a club and state ladder for only six (India, Pakistan, Australia, England, New Zealand, South Africa),
so a player cannot simply aim for another country's team while playing at home. He can instead plan a move to a district club in any of the six: choose the country, the state, then the club. The move happens when the next season starts (`CareerMove.apply`): he starts again at the district level of the new club
(clubs and squads for the new state are made up as when a career starts), keeping his ratings, age, money, honours, record and form. His club captaincy starts over, his franchise ties are cleared, his state matches count from zero, and the national selectors of the new country want him
to have lived there for 2 seasons (`CareerInternational.RESIDENCY_SEASONS`, from `CareerProfile.movedSeason`) as well as the usual state matches. A planned move can be cancelled until the season starts. A player who has played for his country cannot move.
(The first version of this part let a player aim for another country's team without moving; that is gone. A career saved with that choice drops it when the next season starts.)

**Retiring veterans** (`SquadEvolution`). The computer's squads have no ages, so each player gets one from his name (24 to 37). When his age reaches 38 in the career's timeline he retires and a young player of the same role and bowling style takes his place, with a name from the country's list
(ten countries; franchise squads mix the lists) and ratings of 0.72 of the veteran's plus 14. The same veteran always has the same successor. This applies wherever `CareerInternational.evolved` is used: the series and tournaments, the leagues, the squads the selectors compare him with, the rankings and the state squads.

**Ageing domestic squads.** The state teams of the player's country age and renew the same way (`CareerDomestic.newSeason` and `stateSquad`, with the country's names for the successors). The district club leagues are made up for each career and are not aged.

**Dials.** `SquadEvolution.RETIRE_AGE`, the rating formula in `successor`, `RESIDENCY_SEASONS`, `MIN_STATE_MATCHES`.

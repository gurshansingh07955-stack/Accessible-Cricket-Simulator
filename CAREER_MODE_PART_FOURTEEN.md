# My Player, part fourteen: ageing squads, the ODI World Cup, the World Test Championship final

**The season cycle** is now: even seasons the T20 World Cup; seasons 1, 5, 9 ... the ODI World Cup; seasons 3, 7, 11 ... the Asia Cup for India and Pakistan and the Champions Trophy for the others. In every odd season the World Test Championship final is added before the tournament when the player's country qualifies.

**Computer squads age** (`CareerInternational.evolved`). Players have no age in the game, so each gets one from his name (24 to 37). In every season of the career a player under 27 gains 1.5 rating points, one of 33 or over loses 1.5 (at most 12 in all), in batting and bowling, half of it in fielding;
the player himself is left alone. It is applied to the national teams in the career's series and tournaments, to the franchise squads in the league offers and league seasons, to the squad the national selectors compare him with, and to the rankings. A career of many seasons therefore meets a world that has moved on:
old stars fade and young players rise. Not done: real retirements and new players with names (there is no age to retire on, and no name generator), and the domestic squads of the state and club leagues.

**ODI World Cup** (`odiWorldCup`, `advanceWc`). The ten Test nations, 50 overs a side, every nation plays the other nine once (45 matches, at the grounds of a host country that changes every four seasons), then 1st v 4th and 2nd v 3rd in two semi-finals and the Final.
The game's real 2027 and 2031 World Cups have 14 teams (not built).

**World Test Championship final** (`wtcFinal`). In an odd season the two strongest Test teams of the season (the average of each team's best eleven, with a little luck drawn from the career and the season) play a one-match, five-day series in England. It only appears when the player's country is one of the two, and the Test can be drawn
(the result line says the series was drawn).

**Dials.** The ageing rates in `evolved`, the cycle in `tournamentFor`, the luck in `wtcFinal`.

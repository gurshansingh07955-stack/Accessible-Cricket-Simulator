# My Player, part nine: the season's tournament, form in each format, Player of the Series

**The tournament.** An international season now ends with one tournament, built by the game's own tournament code (`T20WorldCup`, `ChampionsTrophy`, `AsiaCup`) with the player written into his country's squad (`CareerInternational.tournamentFor`):
even seasons the T20 World Cup; odd seasons the Champions Trophy (50 overs); India and Pakistan play the Asia Cup in every second odd season (season 3, 7, 11 ...) instead of the Champions Trophy. The tournament is dated after the series and saved in the
season's own file. All the other teams' matches are simulated as the player's matches come up; knockout stages open when the table is settled.

**Form in each format.** After every international match the impact is kept in his form for that format (`CareerProfile.formByFormat`: "t20", "odi" or "test", the last five). The selectors (`matchDecision`) judge him by the last three
matches in the format of the match if he has played two or more there, else by his overall form. A Test batsman in form can be left out of the T20 side after failures in it.

**Player of the Series.** When a series or tournament is finished the Game play tab says what the player did (runs, wickets, catches) and whether he had the most impact of anyone (`CareerInternational.awardLine`).

**Dials.** The tournament cycle (`tournamentFor`), the form window (`formAdjustFor`), `MIN_STATE_MATCHES`.

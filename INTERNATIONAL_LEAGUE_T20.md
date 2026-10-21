# International League T20 2026 (tournament)

Tournament > League tournaments > International League T20 2026 (DP World ILT20 Season 5). Same pages, saving and tabs as the CPL.

**Real facts used** (published October 2026): six teams (Abu Dhabi Knight Riders, Desert Vipers, Dubai Capitals, Gulf Giants,
MI Emirates, Sharjah Warriorz), the 22 or 23 players each franchise ended the auction with, and the **real fixture list**:
30 league matches at Dubai, Sharjah and Abu Dhabi in the published order, then Qualifier 1 (Abu Dhabi), the Eliminator (Dubai),
Qualifier 2 (Sharjah) and the Final (Dubai): 34 matches, T20. The league fixtures are the same every time, as in real life.

**Rules used:** the XI needs at least two UAE players, at least two Afghanistan players and at least one further Associate player
(a third UAE player counts); no limit on overseas players. Squad tags `UAE`, `AFGHAN`, `ASSOCIATE`, `OVERSEAS` carry this
(`SquadTag` in `Tournament.kt`; `pickXI` and `xiProblem` in `TournamentSim.kt` enforce it, switched on by the presence of
`AFGHAN` tags). **Not built:** the ILT20 Super-sub, the under-23 squad rule.

**Players.** Anyone already in the game (roster, CPL, Abu Dhabi T10) is copied with his ratings. About 44 were not in the game and are
new; **their ratings are estimates** (the `nw(...)` lines in `logic/InternationalLeagueT20.kt`; several UAE and Associate players'
roles are guesses). Nationalities follow the Gulf News list of the auction. `al(...)` links Aayan Khan = Aayan Afzal Khan,
Masood Ul Rahman Gurbaz = Masood Gurbaz, Allah Ghazanfar = Allah Mohammad Ghazanfar, Raizal Nadir Nather Shah = Raizal Nadir,
Ali Aamer Naseer = Ali Naseer.

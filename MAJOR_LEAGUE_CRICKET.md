# Major League Cricket 2026 (tournament)

Tournament > League tournaments > Major League Cricket 2026 (MLC season 4, 18 June to 18 July 2026). Same pages, saving and tabs as the CPL.

**Real facts used:** six teams (Los Angeles Knight Riders, MI New York, San Francisco Unicorns, Seattle Orcas, Texas Super Kings,
Washington Freedom), their 2026 squads (18 to 21 players), and the published fixture list: 30 league matches in order, then the
Qualifier (1st v 2nd), the Eliminator (3rd v 4th), the Challenger (Qualifier loser v Eliminator winner) and the Final: 34 matches, T20.
The league fixtures are the same every time. Grounds: Grand Prairie Stadium (matches 1-7 and 23-30), Oakland Coliseum (8-15 and all
four play-offs) and the new Knight Riders Cricket Ground at Fairplex, Pomona (16-22; the March schedule had these at Broward County before
the move). None of the three was in the game: added to `StadiumData` with estimated pitch and weather figures.

**Rules used:** at most six overseas players in the XI (so at least five US players), the league's long-standing XI rule (not
re-checked for 2026). Tags `USA` and `OVERSEAS` carry it (`SquadTag`; `pickXI` and `xiProblem` use a cap of 6 when the squads have
`USA` tags). **Not built:** the mandatory under-23 US player; the league's points rules (the game's table gives 2 points a win).

**Players.** Anyone already in the game (roster, CPL, Abu Dhabi T10, LPL, ILT20, Big Bash) is copied with his ratings (73 of 116).
The rest are new with **estimated ratings** (the `nw(...)` lines in `logic/MajorLeagueCricket.kt`). Which players count as US players
follows the league's published lists where they exist; a few are guesses (for example Brody Couch, listed by his club as domestic).
`Allah Ghazanfar` is linked to the game's `Allah Mohammad Ghazanfar`.

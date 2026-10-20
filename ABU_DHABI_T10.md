# Abu Dhabi T10 2026 (tournament)

Tournament > League tournaments > Abu Dhabi T10 2026. Same pages, saving and tabs as the CPL.

**Real facts used** (announced October 2026): six teams (UAE Bulls, Yas Lions, Royal Desert Champions, Emirates Eagles,
United Tigers, Arabian Aces), 18 players each after the 2026 draft, 10 overs a side, a double round-robin (30 league
matches, three a day), then the Qualifier (2nd v 3rd) and the Final (1st v the Qualifier winner): 32 matches, all at
Sheikh Zayed Stadium, Abu Dhabi.

**Players.** Anyone already in the game (roster or CPL) is copied with his game ratings. About 45 players were not in the
game and are new; **their ratings are estimates** (the `nw(...)` lines in `logic/AbuDhabiT10.kt`; a handful of the lesser-known
UAE players have guessed roles). `al(...)` links a name the game spells differently (Muhammad Amir = Mohammad Amir,
Vriitya Arvind = Vriitya Aravind, Sufyan Moqim = Sufiyan Muqeem, Tajinder Singh Dhillon = Tajinder Singh).

**No overseas or breakout rule** is applied (none is known for this league).

**Balance.** Computer-v-computer T10 matches use `AiBallModel` with the T10 dials `T10_BOUNDARY`, `T10_WICKET`, `T10_DOT`,
`T10_RUNS`. Measured with `node tools/t10_twin.js` (a twin of the ball model with assumed player ratings): about 106 runs and
4.6 wickets an innings where the same twin gives 165 for T20 against the real model's calibrated ~150, so expect roughly 95-100.
Real Abu Dhabi T10 innings average about 100 for 5. Live matches you play use the normal engine.

# Super Over switch and custom teams in matches (patch 055)

* **Super Over on or off in a custom tournament** (a new switch on the DRS and rain page, and a line in the summary).
  Switched off, a tied league or group match is **shared** (1 point each, "Match tied. The points are shared.").
  A knockout match (a semi-final, a Final, a qualifier...) cannot end level, so it still goes to a Super Over.
  `CustomTournamentDef.noSuperOver`, `MatchRules.noSuperOver`, `SuperOver.isTieAtEnd`, `TournamentSim.buildResult`.
* **Custom teams on the Play match team screen.** A custom team is listed there (for you and for your opponent) once
  it is ready: **11 players, a wicketkeeper and at least 4 bowlers or all-rounders**. A custom team's page says what
  it still needs, and the custom tournament team list marks teams that are not ready.

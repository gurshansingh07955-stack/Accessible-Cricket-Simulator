# Play series (patch 146 = part 1; playing a series is part 2)

Home > **Play match** now opens a page with two choices: **Quick match** (the setup flow the game has always had) and **Play series**.

Play series > **New series** / **Resume series** (up to 5 series are kept, in `series.json`).
New series asks, one page at a time: type (One vs one = 2 teams, Triangular = 3, Quadrangular = 4); Single or Multi format; the format(s)
(Test, T20, ODI, T10, Five-over, One-over and the player's custom formats; a multi-format series needs at least two); the number of matches
(1 to 27; for each format in a multi-format series); the teams (national teams and the player's ready custom teams); the grounds (the countries of the
chosen teams first, a button shows the other countries; tick the grounds of each country); a summary; Save.

Part 1 does NOT play a series: a saved series can be read (Resume series) and deleted. Part 2 plays it.

Files: `logic/Series.kt` (SeriesKind, SeriesLeg, SeriesDef, SeriesRules), `persistence/SeriesStore.kt`, `ui/series/SeriesScreens.kt` (PlayMenuScreen, SeriesFlow),
`Screen.PlayMenu`, `Screen.Series` (MainActivity). The squads and the list of grounds are loaded off the main thread.

Assumptions: the matches of a triangular or quadrangular series are shared between the teams in turn; the matches of a leg count for the whole series (not per team);
a series name is made from its teams ("India v Australia series"); teams are saved as keys "int:<id>" / "custom:<id>".

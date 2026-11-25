# Play series, part 2: playing a series (patch 147)

Play match > Play series > Resume series > open a series > **Play this series**.

A series is played by the tournament screens (same pages as every tournament: start page with Start new / Resume, choose your team from the series' teams,
hub with Matches / Table / Venues / Stats, Playing XI, toss, live match or simulate, saved after every match, one saved series each). It does this by being a hidden
custom tournament: `SeriesPlay.defFor(series)` makes a `CustomTournamentDef` (id `ctour_<seriesId>`) whose new field `series` is set. The player's own custom tournament lists
filter these out (`series == null`).

What is specific to a series (`logic/SeriesPlay.kt`, with small branches in `CustomTournaments.kt` and `MatchRules.kt`):
- Fixtures: for each format in turn, that many matches; the pairs take turns (two teams swap home and away each match; three or four teams go round every pair, then again with home and away swapped); the ticked grounds are used in turn.
- Stages: a single-format series has one stage ("League"); a multi-format series has one per format ("Group T20", "Group ODI"...), each with its own table.
- Rules: each match is played by the rules of its stage's format (`SeriesPlay.rulesFor`): a Test is five days (`TestMatch.rulesForDays(5)`), a custom multi-day format keeps its own days, and a tied limited-overs match stands (no Super Over).
- `formatFor`, `fullInningsBalls`, `baseFormatFor` and `ballsFor` now take the stage, so the simulated matches and the net run rate use the format of the match.
- Winner: the team with most wins over the whole series; nobody if two teams share the most (a drawn series).
- Deleting a series also deletes its tournament and its saved match.

Not done / to check: the live Test inside a series uses the same Test code as custom multi-day formats in custom tournaments (not tried by ear or hand here); a drawn series shows no winner on the celebration screen.

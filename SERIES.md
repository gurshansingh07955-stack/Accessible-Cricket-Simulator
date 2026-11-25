# Play series (patches 146, 147, 148)

Home > **Play match** > **Quick match** (the old setup flow) or **Play series**.

## Setting a series up (Play series > New series)
1. Type: **One vs one** (2 teams), **Triangular** (3), **Quadrangular** (4).
2. **Single format** or **Multi format** (two or more formats). Formats: Test, T20, ODI, T10, Five-over, One-over and the player's custom formats.
3. **One vs one only:** how many matches, **1 to 7** for each format (patch 148; it was 27). The formats follow one another: all the matches of the first format, then the next.
   **Triangular and quadrangular: no number is asked** (patch 148). In each format every team plays every other team once (a round robin: 3 matches for three teams, 6 for four),
   then the top two of that format's table play a **Final**. In a multi-format series that repeats for the next format: round robin, Final, round robin, Final...
4. Teams: exactly 2, 3 or 4 (national teams and the player's ready custom teams).
5. **Host country and grounds** (patch 148): the countries of the chosen teams are listed (all countries if none of them has grounds); the player picks ONE country and ticks its
   grounds. Choosing another country clears the ticks, so every ground of a series is in one host country. The matches use the grounds in turn.
6. Summary, Save (up to 5 series, `series.json`).

## Playing (Resume series > a series > Play this series)
The tournament screens play it (see `logic/SeriesPlay.kt`): the series is a hidden custom tournament (`CustomTournamentDef.series`).
- Stages: a single-format series has "League" (and "Final"); a multi-format series has "Group T20", "T20 Final", "Group ODI", "ODI Final"... each "Group" stage has its own table; a Final has none.
- `SeriesPlay.advance` fills in a Final with the top two of its format's table once that round robin is played (called after every match, from `CustomTournaments.advance`).
- Rules per stage (`SeriesPlay.rulesFor`): the rules of the format; a Test is five days; a tied limited-overs match stands (no Super Over) except in a Final, which goes to a Super Over.
- Winner: with Finals, the team that won the Final (most Finals won in a multi-format series), then most wins over the series; one vs one: most wins; nobody if still level.
- Series saved before patch 148 keep the fixtures they were made with; their summaries use the new counting.

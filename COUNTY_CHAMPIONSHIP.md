# County Championship 2027

Tournament > Domestic tournament > County Championship 2027 (England). Part 2 of the county cricket work (part 1: `COUNTY_CRICKET.md`, the One-Day Cup and the Vitality Blast). Code in `logic/CountyCricket.kt` (the Championship section) and `ui/tournament/CountyTable.kt`; own saved tournament `saved_tournament_cc_2027.json`.

- **Divisions 2027**: Division One: Warwickshire, Somerset, Sussex, Nottinghamshire, Yorkshire, Glamorgan, Surrey, Essex, Durham, Kent. Division Two: Northamptonshire, Middlesex, Worcestershire, Derbyshire, Lancashire, Gloucestershire, Leicestershire, Hampshire. This is the 2026 final table with the top two of Division Two promoted (Durham, Kent) and the bottom two of Division One relegated (Leicestershire, Hampshire).
- **Fixtures**: not published yet (the ECB usually releases them in November). The game makes the schedule in the real format: 14 four-day matches per county. In Division One each county plays five others home and away and four once (a fixed circle design); in Division Two home and away against all seven. 70 + 56 = 126 matches.
- **Points** (the ECB's): 16 for a win, 8 each for a draw or a tie, batting bonus 1 to 5 (200, 250, 300, 350, 400 runs) and bowling bonus 1 to 3 (3, 6, 9 wickets) from the first innings. The real rule counts only the first 110 overs of an innings; the game counts the whole innings. Tables: points, then wins, then runs per wicket scored over conceded (assumption for the last tie-break).
- **Champions** are the top of Division One once all 126 matches are played; the Table tab then says who is promoted and relegated (two up, two down). The celebration screen is shown as for any tournament.
- Matches are four days, first-class rules (the same machinery as the Ranji Trophy, `TestMatch.kt`), no DRS, no rain. You can play your own match or simulate.
- Not built: the real fixtures, points deductions, the season's second part (the Championship's real split into stages), overseas limits.

# Ranji Trophy 2026-27 (part 1)

Tournament > Domestic tournament. File: `logic/Ranji.kt` (teams, squads, grounds, fixtures), `logic/FirstClass.kt` (points and tables), `ui/tournament/RanjiTable.kt` (the group tables). Own saved tournament: `saved_tournament_ranji_2026_27.json`.

## What is in part 1
- 38 real teams: Elite Groups A to D (eight each) and the Plate group (six). 127 real group matches (28 in each Elite group, 15 in the Plate group), in the real order (round by round, group by group). A match whose ground was not announced is at the first-named side's home ground.
- Squads: the first-round squads from Wisden (35 teams), Haryana's from Sportscafe, Arunachal Pradesh's from the BCCI-appointed panel's announcement (October 2026) and **Sikkim's is last season's** (the page did not have this season's). About 586 players.
- Players already in the game (the roster and every league) are copied with their ratings. The rest (most of them) are new with **estimated ratings**: from a strength guess for the team (tier 1 to 5, `TEAM_TEXT`), the role and a steady "random" number from the name. The role comes from Wikipedia's infobox where the player has a page (about half), otherwise from the order of the squad list (batters first, bowlers last), with at least one wicketkeeper and five bowlers or all-rounders a team. The Ranji teams are not yet in Advanced Customisation (a later part).
- Grounds: about 45, all **estimated** (pitch, boundary size, altitude) in `VENUE_TEXT`.
- Matches are four days (`Ranji.LEAGUE_DAYS`), played by the computer with the first-class rules of the five-day Test (`TEST_MATCH.md`): follow-on at a lead of 150, declarations, the new ball, a draw when 360 overs are bowled, the pitch wearing. The computer-v-computer ball model has a Test setting (`TEST_*` in `AiBallModel.kt`), measured on a model of the ball (mid-rated sides: about 290 runs in 90 overs at 3 an over; strong batters against a weak attack about 380; the reverse about 180).
- Points (the BCCI's): outright win 6, or 7 by an innings or ten wickets; tie 3 each; in a draw the first-innings leader 3 and the other side 1 (1 each when level or when a first innings was not finished); loss 0. Tables by points, then wins, then the quotient (runs per wicket scored over runs per wicket conceded).
- Tournament pages as the others: Matches, Table (one table a group), Venues, Stats, scorecards (four innings, one tab each).

## Not in part 1
- **Playing your own match** (the Matches page says the computer plays yours): part 2.
- The knockouts (the top two of each Elite group go to the quarter-finals, then the semi-finals and the Final, five-day matches, a drawn knockout going to the first-innings leader), the Plate semi-finals and Final, promotion and relegation, and the celebration: part 3.
- Real results (the game simulates them; the real season is already three rounds in).

# My Player, part nineteen: the full career record

**Career > Career record** now opens a list: **All cricket**, **Club cricket**, **State cricket**, **International cricket**, **Franchise leagues**, each with its matches, runs and wickets. Choosing one opens its record, with tabs:
- Club: All, Three-day, One-day, T20 (and "Earlier matches" if you have matches from before this part).
- State: All, then a tab for each tournament you have played (Ranji Trophy, Vijay Hazare Trophy, the T20 trophy ... whatever your state plays).
- International: All, Test, ODI, T20 (the World Cups, the Champions Trophy and the Asia Cup count in their format).
- Leagues: All, then a tab for each league you have played (IPL, Big Bash, SA20 ...).

**What a record shows** (`CareerStatistics.sections`), one statistic to a line, under headings:
- *Batting*: matches, innings, not outs, runs, balls faced, highest score (with * if not out), average, strike rate, fifties, hundreds, double hundreds, fours, sixes, runs from boundaries, ducks, golden ducks.
- *Bowling*: overs, maidens, runs conceded, wickets, average, economy, strike rate, best bowling in an innings and in a match, 3, 4 and 5 wickets or more in an innings, 10 wickets or more in a match, hat-tricks, double hat-tricks (four wickets in four balls in a row; it counts as a hat-trick too), wides, no-balls.
- *Fielding*: catches, run-outs, stumpings (for a wicketkeeper), dismissals in the field, most catches in a match.
- *Awards*: Player of the Match, Player of the Series.
- *Results for your team*: won, lost, tied, drawn, no result, win percentage.
- *Captaincy*: matches as captain, won, lost, tied, drawn, no result, win percentage, tosses won.

**Where it comes from.** After every match he plays, the match's scorecard is read (`CareerStatistics.record`): his batting and bowling in every innings (Tests have four), the catches and run-outs he took, hat-tricks from the fall of wickets, the result and the Player of the Match, and whether he was the captain. A match without a scorecard
(some simulated first-class matches) adds only what the tournament statistics hold, so ducks, maidens, hat-tricks, run-outs and stumpings can be a little low in long-form cricket. The Player of the Series awards are added when the season is settled. The records kept before this part are shown under "Earlier matches"
(Club, State, Leagues) or added to their format (Test, ODI, T20 internationals); they have no further detail, and the old record stops growing.

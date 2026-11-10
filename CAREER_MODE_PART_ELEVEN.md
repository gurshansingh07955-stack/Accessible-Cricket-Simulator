# My Player, part eleven: the Career tab

A fifth tab, **Career**, with a heading for each part (so a screen reader can jump between them).

**Season objectives.** Three goals by role and level (batsman: runs, fifties, Player of the Series; keeper-batsman: runs, catches, Player of the Series; bowler: wickets, catches, Player of the Series; all-rounder: runs, wickets, Player of the Series).
The targets are in `CareerRecords.newObjectives` (a state player's are 20% higher, an international's 10% lower). Progress is counted from the season's competitions (`tally`), so nothing is stored. At the end of the season, when you start the next one,
each objective reached gives +1 to the rating it is about (runs and fifties: batting; wickets: bowling; catches: fielding; Player of the Series: the main skill of your role).

**Training focus.** Balanced, Batting, Bowling or Fielding. The skill you work on grows 1.6 times as fast after matches, the other two 0.85 times (`CareerProgress.focusFactor`). You can change it at any time. This is the answer to "training does nothing" in the research: it is a real choice with a cost.

**Rankings.** Your batting and bowling rating against every player of the game's national teams, your fielding, and your place in your country's squad (`CareerRecords.rankings`).

**Career record.** After every match what you did is added to the record of the kind of cricket it was: Club, State, Test, ODI, T20I (international), or League (`CareerRecords.updated`). Each line gives matches, runs, innings, average, highest score, fifties and hundreds, wickets, best figures and catches, and there is a line for the totals.
The record starts counting with this patch: matches already played are not in it.

**Honours.** When a season ends, the competitions your team won (a club league, a series, a tournament, a franchise league) and the Player of the Series awards you won are added to your honours, with the season number. Reaching every objective is an honour too.

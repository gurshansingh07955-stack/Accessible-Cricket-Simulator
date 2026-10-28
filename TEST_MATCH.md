# Five-day Test match (part 1)

Switch: `TestMatch.FIVE_DAY_TEST` in `logic/TestMatch.kt`. `false` brings back the old two-innings, 90-over Test.
A Test is a match played with format Test and no custom rules (custom tournaments cannot use Test). Matches saved before this patch carry on as the old Test.

## The rules in the game
- Five days, 90 overs a day (450 overs), three sessions of 30 overs a day. Lunch, tea and stumps come when 30 overs of the match have been bowled (after the over that completes them). A drinks break comes in the middle of each session (the 15th over).
- Four innings: A, B, A, B. The side that batted first may make the other side follow on when it leads by 200 or more (A, B, B, A). You are asked when you are the side that may enforce it; the computer decides by the lead and the overs left.
- A side may declare in the first three innings. **Match info > Declare innings** (you batting, the ball dead), with a confirmation. The computer declares at the start of an over once its lead is safe for the time left (`TestMatch.aiShouldDeclare`).
- Results: win by an innings and runs, by runs, by wickets, tied, or drawn when the 450 overs run out ("Match drawn. X finished on 210 for 6, needing 40 more runs.").
- No free hit after a no-ball in a Test. No fielding restrictions. Three DRS reviews an innings (unchanged). The computer batter can leave the ball (unchanged).
- Player of the match counts every innings and ignores strike rate and economy in a Test.
- Rain is not played in a Test (unchanged: the rain rule skips Test).

## Pop-ups (all spoken as they appear)
1. When a session or day ends: "Lunch on day 1", "Tea on day 2", "Stumps on day 3": the batting side's score, the wickets the bowling side has taken, the runs and wickets of the session, the lead, the overs left. Continue.
2. Then when it starts again: "Day 1, second session begins", "Day 2 begins": the same score and wickets, the lead or the target, the overs left today and in the match (and a pitch report at the start of a day). Resume play.
3. Between innings: "End of the first innings", the side's total and how it ended, the position, who bats next.
4. The follow-on question and the declaration check.
Scorecards (screen, share, copy) list every innings, one tab each.

## Pace and the pitch
The ball model is a T20 one, so a Test used to be all out in 34 overs at 7.4 an over. `SCALE_*` in `TestMatch.kt` weigh wickets, fours, sixes, dots, twos and threes; with the twin (`tools/difficulty_twin.js`, AI v AI, Medium, no fielders) an innings is about 330 runs in 100 overs at 3.2 an over. Easy about 270, Hard about 410. This was measured on a model, not on people.
The pitch wears from 0 (day one) to about 1 (day five) by the overs bowled (`TestMatch.wearFactor`; dusty wears faster, batting slower): wickets up to 35% more likely, spinners a further 35%, fewer fours. Dials in `MatchEngine.simulateBall`.

## Not built yet (parts 2 and 3)
- The new ball after 80 overs (taking it from Match info, the computer taking it, its effect), over-rate, the last-hour rule, a nightwatchman.
- Commentary lines (and their ElevenLabs voices) for the start and end of a session or day, the new ball and a declaration.
- Test matches in tournaments.

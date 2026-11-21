# Net practice (patch 135)

Home > Net practice (placed before Settings). Part 1: batting practice. Bowling practice is shown, switched off, and comes in Part 2.

## Batting practice
Net practice > Batting practice > Spin or Fast. Fast asks for a speed band (110 to 120, 120 to 130, 130 to 140, 140 to 150, 150 and above). Then the first and second batsman, chosen from the game's batsmen rated 90 or more (12 today, read live from the squads, so Settings-style edits count). Then the instructions page, then the session.

- Spin bowlers: Kuldeep Yadav, Ravindra Jadeja, Rashid Khan, Sikandar Raza, Nathan Lyon, Adam Zampa. Fast bowlers: every pace bowler rated 90 or more. A bowler is chosen at random each over, never the same twice running.
- The batsmen take turns, an over each (six legal balls). Wides and no-balls do not count towards the six.
- Balls are real: footwork on "Face next ball" (or the Settings alternatives), then the real BattingScreen, then MatchEngine.simulateBall at the Settings difficulty. The state given to the engine never advances: a wicket is counted and the batter carries on.
- After every ball the coach speaks: the match's own ball summary, then footwork, shot choice and a timing fix (early or late). Before every ball, with "Coach tips" on, he names the foot and the shots that suit the ball (he reveals it after the delivery is described).
- Stop practice gives a summary: runs, balls, outs, fours, sixes, how many shots were perfectly timed, how many suited the ball, early or late habit.

## Where things are
- `logic/NetPractice.kt`: the lists, the practice state, the deliveries (`deliveryFor`), the coach (`coachTip`, `feedback`) and the summary.
- `ui/practice/NetPracticeScreens.kt`: `NetPracticeFlow` and every page.
- `NetPractice.NET_PRACTICE_ENABLED` switches the Home button off. `NetPractice.MIN_RATING` is the 90.

## Known limits
- The coach's tip is delivered through the "Field changes" slot of the delivery announcement, so the screen reader says "Field changes: Coach tip: ..." (a later patch can give it its own label).
- Fast bands use a stock delivery only (a slower ball would not fit a fast band).
- The practice ground is a balanced pitch with the bowler's national side in the field, in the T20 powerplay field.
